# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this repo is

A take-home refactoring kata ("XA Bank Time Deposit"). The upstream repo ships the same starter logic in several languages (`c#/`, `java/`, `kotlin/`, `python/`, `typescript/`), but **the solution is being built only in `java/`**. Leave the other language folders untouched.

The full brief is in `README.md`. The requirements that constrain every change:

- **Exactly two REST endpoints**, no more: one that updates the balances of all time deposits in the DB, and one that returns all time deposits as `id`, `planType`, `balance`, `days`, `withdrawals`.
- **Database tables**: `timeDeposits` (`id` PK, `planType`, `days`, `balance` decimal) and `withdrawals` (`id` PK, `timeDepositId` FK, `amount` decimal, `date`).
- **`TimeDepositCalculator.updateBalance` behaviour must not change**, and its signature must stay the same. The shared `TimeDeposit` class must not get breaking changes. Interest rules (monthly interest, rate/12, rounded HALF_UP to 2 decimals): no interest at 30 days or less for any plan; `basic` 1%; `student` 3% only while `days < 366`; `premium` 5% only when `days > 45`.
- The interest-calculation design must be easy to extend (e.g. a strategy per plan type), using SOLID and clean code.
- Preferred stack: OpenAPI/Swagger contract, hexagonal architecture, Testcontainers, atomic commits.
- Invalid input and exception handling are **not** required. Where the brief is ambiguous, write down the assumption in a code comment.
- AI usage must be documented in the deliverable: tools, setup, rules/prompts, and which parts were AI-assisted.
- Work happens in the user's fork. Never open PRs against `ikigai-digital`.

## Java module (`java/`)

Spring Boot 4.1 (parent POM), Java 21, Maven, Spring Data JPA, Spring MVC, springdoc-openapi (Swagger UI), Liquibase, PostgreSQL, Lombok, Testcontainers.

The module has no Maven wrapper, so use the system `mvn` and run it from `java/`:

```bash
mvn compile
mvn test                                              # all tests; Spring context tests need Docker running
mvn test -Dtest=TimeDepositCalculatorTest             # single class
mvn test -Dtest=TimeDepositCalculatorTest#updateBalance_Test   # single method
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev  # needs the Docker Postgres from README Option B on localhost:55432 (not 5432, to avoid local installs)
mvn spring-boot:test-run                              # runs TestTimeDepositKataApplication, which starts Postgres via Testcontainers

# Load test (k6 via Docker): start the app with 10k deposits, then run the script from java/
mvn spring-boot:test-run -Dspring-boot.run.arguments="--spring.liquibase.contexts=loadtest"
docker run --rm -i -e BASE_URL=http://host.docker.internal:8080 grafana/k6:1.3.0 run - < load-test/time-deposits.js
```

Fast startup (JDK 25+): `scripts/build-aot-cache.sh --spring.profiles.active=dev` needs a reachable DB (`SPRING_DATASOURCE_URL`). It builds, extracts the jar, trains a JDK AOT cache, and prints the `java -XX:AOTCache=...` run command. Startup goes from 6.1 s to ~2.2 s. Rebuild the cache after any jar or JDK change.

IntelliJ: shared run configurations live in `.run/` at the repo root (module `time-deposit-kata`, plain *Application* type). "Time Deposit (Testcontainers, dev profile)" is the one to run and debug with. Keep them in sync if main classes, profiles or contexts change. XML comments in them must not contain `--`.

Startup diagnostics (`config/diagnostics`, registered in `META-INF/spring.factories`): `MissingDatabaseFailureAnalyzer` (no datasource, usually no profile) and `DatabasePermissionFailureAnalyzer` (SQLSTATE 42501, PostgreSQL 15+ public schema). Both run ahead of Boot's own analyzers.

`spring-boot:test-run` forks a JVM that keeps running if Maven is killed. Free port 8080 by stopping the `TestTimeDepositKataApplication` java process.

Once the app is running, Swagger UI is served by springdoc at `/swagger-ui.html`.

`java/README.md` covers how to run the app, use Swagger, the design, the assumptions and AI usage. Keep it in sync when behaviour changes.

### Architecture (hexagonal)

Package root is `org.ikigaidigital`. `TimeDepositKataApplication` lives there so scanning covers every layer.

- **Domain (framework-free):** `TimeDeposit` and `TimeDepositCalculator` stay in the root package with their original public API, because moving them or changing a signature would be a breaking change. The interest rules are `InterestPolicy` strategies in `domain.interest`. `AnnualRateInterestPolicy` holds the shared 30-day grace period and the `balance * rate / 12` formula. `domain.model.TimeDepositSummary` is the read model that carries withdrawals, so `TimeDeposit` doesn't need a new field.
- **Application:** use-case ports in `application.port.in`, persistence ports in `application.port.out`, and `@Transactional` services in `application.service`. `UpdateAllBalancesService` locks the deposits, skips those already posted this month (`InterestPostingsPort`), runs the calculator, and writes balances plus one `InterestPosting` audit record per deposit in one transaction. It logs the run after the commit.
- **Adapters:** `adapter.in.web` has the controller. Its OpenAPI annotations sit on the `TimeDepositApi` interface. `adapter.out.persistence` has the JPA entities and implements the out-ports. `domain.model.Money.fromLegacyBalance` converts the domain's legacy `Double` back to 2-decimal `BigDecimal`.
- **Wiring:** `config.DomainConfiguration` registers each policy as a bean, and the calculator receives `List<InterestPolicy>`, keyed by `domain.model.PlanType`. To add a plan, add a `PlanType` constant (its code is the string stored in `TimeDeposit.planType` and the DB) and a policy bean.

Rules for changing it:

- **Lombok** is used everywhere outside the domain:
  - `@RequiredArgsConstructor` (with `private final` fields) for constructor injection in adapters and services;
  - `@Slf4j` for logging;
  - on JPA entities only `@Getter`, single-field `@Setter`, `@NoArgsConstructor(access = PROTECTED)`, and `@Builder` (package-private, plus a private `@AllArgsConstructor`) for `InterestPostingJpaEntity`;
  - test helpers.

  `lombok.config` fails the build on `@Data`, `@Value`, `@EqualsAndHashCode`, `@ToString`, `val`/`var` and experimental features. On entities, those would read lazy associations and break hash-based collections. **The domain stays Lombok-free**: `TimeDeposit` must keep its exact hand-written legacy API (README constraint), and the calculator, policies, `PlanType` and `Money` stay plain Java as the hexagonal core. Records are used for immutable data instead of Lombok.

- `TimeDepositCalculatorTest.behavesExactlyLikeTheLegacyImplementation` compares the calculator with `LegacyTimeDepositCalculator`, a verbatim copy of the original code in the test sources. Never edit the legacy copy to make the test pass. Calculator results must stay bit-identical, including the `double` arithmetic and the `new BigDecimal(double)` rounding.
- The schema is owned by Liquibase (`src/main/resources/db/changelog/`), and Hibernate runs with `ddl-auto: validate`. Layout: `schema/NNN-<table>.sql` (one changeset per table, always applied) and `data/*.sql` (context-only), both included explicitly, in order, from `db.changelog-master.yaml`. The schema was consolidated before the first release. From now on, change it only by **adding** a new numbered file under `schema/` and including it, never by editing an existing changeset (checksums). Tables and columns are snake_case. Hibernate validation does not check constraints, indexes or sequences, so verify schema changes against a real PostgreSQL.
- `spring.jpa.open-in-view` must stay `false` in every profile (set once in `application.yaml`). Never override it. `OpenInViewDisabledTest` checks the running context (no `OpenEntityManagerInViewInterceptor` bean) for the default configuration and the `dev` profile. Everything a response needs must be loaded inside the service's transaction.
- The GET read path uses a flat JPQL projection (`TimeDepositWithdrawalRow`), not entities. That choice came from load-test measurements (see README, "Performance"), so don't switch it back to a fetch join of entities.
- `data/load-test-data.sql` (10k deposits) runs only in the `loadtest` context.
- `data/demo-data.sql` runs only in the `demo` Liquibase context, which `application.yaml` enables. Tests set `spring.liquibase.contexts=test` and seed their own rows.
- The balance update is **idempotent per deposit per calendar month (UTC)**. This rests on the unique `(time_deposit_id, period)` key in `interest_postings`, plus the `SELECT ... FOR UPDATE` in `findAllForUpdate`. Removing either breaks `ConcurrentBalanceUpdateIntegrationTest`. Get "now" from the injected `Clock` bean, never from `Instant.now()`.
- `time_deposits` uses **both** kinds of lock: pessimistic (`findAllForUpdate`) for the monthly batch, so callers wait instead of failing, and optimistic (`@Version` on `TimeDepositJpaEntity`) so any other writer fails on a stale read instead of silently overwriting a balance. New code that writes balances must go through the JPA entity, never through bulk JPQL or plain SQL `UPDATE`, which bypass the version check. If that's unavoidable, include `AND version = ?` and increment the version yourself.
- Beans are shared across request threads, so they must stay stateless: only `private final` fields holding immutable or thread-safe objects, and per-request state in local variables. Never cache a `TimeDeposit` or a mutable collection in a field. The interest run relies on PostgreSQL's default READ COMMITTED isolation; don't raise it for that transaction.
- No double-entry ledger in this service, by decision (README, "Why there is no double-entry ledger"). `interest_postings` is the journal, and the general ledger belongs to the core banking system, fed through a transactional outbox in production.
- `interest_postings` is append-only (audit). Never add an update or delete path. Its ids come from a pooled sequence (`allocationSize = 500`, which must match `INCREMENT BY 500` in `schema/003-interest-postings.sql`) so that inserts are JDBC-batched with few sequence round trips. JDBC batch size is 500, with `reWriteBatchedInserts=true`.
- Performance changes must be measured, not assumed. Use `LargeVolumeBalanceUpdateIntegrationTest` (40k deposits, prints the run time) for the interest run and the k6 script for GET. The README's "Performance" section records what was tried, including what didn't help.
- No datasource credentials or API users in `application.yaml`. Local ones (including the BCrypt-hashed demo users `viewer` and `operator`) live only in `application-dev.yaml`. `TestTimeDepositKataApplication` activates `dev` automatically.
- Security (`config.SecurityConfiguration`): GET needs role VIEWER, POST needs OPERATOR, Swagger and `/v3/api-docs` are public, and everything else is `denyAll`. It fails closed: no configured users means every call gets 401. MockMvc tests must authenticate, using `SecurityMockMvcRequestPostProcessors.user(...)`, or `httpBasic(...)` with `@ActiveProfiles("dev")`. The k6 script sends the demo credentials. Keep the access rules role-based, so that switching to OAuth2/JWT only replaces the authentication part.
- A 401 must **not** carry `WWW-Authenticate: Basic` (it uses `HttpStatusEntryPoint`). The challenge would make browsers cache credentials and re-enable CSRF, which is disabled precisely because nothing is sent automatically. Swagger and `/v3/api-docs` are on only in the `dev` profile; tests that need them set `springdoc.api-docs.enabled=true`. Security-relevant log lines use the `SECURITY` prefix, and any attacker-controlled value must go through `SecurityAuditListener.sanitize`.
- **SQL injection rule:** production code reaches the database only through static JPQL in `@Query` with bind parameters (`:name`), or through Spring Data's generated queries. Never concatenate request data or any other runtime string into a query. No native SQL (`nativeQuery = true`, `createNativeQuery`), no `createQuery(String)` on `EntityManager` or `Session`, no `JdbcTemplate`, `JdbcClient` or `java.sql.Statement`. `SqlInjectionGuardTest` (ArchUnit) fails the build on any of these. If a new endpoint takes input (`@RequestParam`, `@PathVariable`, a body), it must reach queries only as a bind parameter. `architecture/fixture/SqlInjectionProneExamples` is deliberately unsafe test code that proves the rules work; never move it to main or make it a bean.
- Rate limiting and brute-force protection are deliberately left to the API gateway or WAF, not done in the app (decision recorded in the README, "Security review"). Don't add an in-app limiter or account lockout without revisiting that decision.
- Integration tests clean up with `TestDatabase.deleteAll(jdbc)`, which deletes in foreign-key order. When a table is added, add it there, once.
- `TimeDepositApiIntegrationTest` asserts that the OpenAPI spec has exactly two paths. Adding an endpoint breaks the kata rules.
- Spring Boot 4 moved test annotations. For example, `AutoConfigureMockMvc` is in `org.springframework.boot.webmvc.test.autoconfigure`. Testcontainers 2 uses `org.testcontainers.postgresql.PostgreSQLContainer`.
- Tests that start Spring use `@Import(TestcontainersConfiguration.class)` and need Docker running.
