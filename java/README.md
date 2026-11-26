# Time Deposit API (Java solution)

Spring Boot 4 / Java 21 implementation of the kata described in the [root README](../README.md).

## Quick start (one command)

Requirements: **JDK 21+ and a running Docker**. Maven comes with the wrapper (`./mvnw`, or `mvnw.cmd` on Windows).

```bash
cd java
./mvnw spring-boot:test-run
```

Then open <http://localhost:8080/swagger-ui.html>, click **Authorize**, log in as `operator` / `operator-demo-password`, and call the two endpoints (step by step: "Trigger the endpoints with Swagger" below).

> **Start the app this way, or with one of the IntelliJ run configurations below.** Running
> `TimeDepositKataApplication` directly without a profile, or plain `mvn spring-boot:run`, stops with
> "No database is configured", together with these same instructions. That's deliberate: the default configuration
> has no database, no users and no Swagger, as it would in production, and every environment must supply them
> explicitly (see "Security"). The command above starts PostgreSQL in Docker and activates the `dev` profile with demo
> users and demo data.

```bash
./mvnw test     # all 56 tests (needs Docker)
```

### Run and debug in IntelliJ IDEA

The repository ships shared run configurations (folder [`.run/`](../.run) at the repository root). They appear in IntelliJ's run/debug dropdown automatically. They use the plain *Application* type, so they also work in IntelliJ Community Edition.

| Run configuration | What it starts |
|---|---|
| **Time Deposit (Testcontainers, dev profile)** | The app, with PostgreSQL in Docker and demo data. **Use this to run or debug.** Breakpoints work anywhere. Each start gets a fresh database |
| Time Deposit (Testcontainers, 10k load-test data) | The same, with 10,000 deposits, for the k6 load test |
| Time Deposit (dev profile, Docker PostgreSQL on 55432) | The production main class with the `dev` profile, against your own Docker database from Option B below. Its data survives restarts |

One-time setup, if IntelliJ didn't do it for you:
- Open the **repository root**, then right-click `java/pom.xml` → *Add as Maven Project*.
- Project SDK: JDK 21+.
- Enable annotation processing (for Lombok).
- Have Docker running.

To debug, pick **Time Deposit (Testcontainers, dev profile)** and press *Debug*. Then call the endpoints from Swagger and execution stops at your breakpoints. Debugging a single test: right-click it → *Debug*.

## Run it

Requirements: JDK 21+, Docker (for Testcontainers). Maven 3.9+ is optional, because the wrapper `./mvnw` downloads it.

**Option A: no local database needed.** Starts PostgreSQL in Docker via Testcontainers and loads demo data:

```bash
cd java
./mvnw spring-boot:test-run
```

**Option B: your own PostgreSQL, which keeps its data between restarts.** No database credentials are committed for real environments. For local development, the `dev` profile (`application-dev.yaml`) matches this container. It uses **port 55432**, not 5432, so it never collides with a PostgreSQL installed directly on your machine:

```bash
docker run -d --name time-deposit-db -p 55432:5432 \
  -e POSTGRES_DB=time_deposit_kata_db \
  -e POSTGRES_USER=time_deposit_kata_user \
  -e POSTGRES_PASSWORD=time_deposit_kata_password \
  postgres:17-alpine
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

The interest posting runs once per deposit per month. So with this persistent database, a second POST in the same month changes nothing until you recreate the container: `docker rm -f time-deposit-db`, then the `docker run` again.

Any other environment supplies `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME` and `SPRING_DATASOURCE_PASSWORD`, plus its API users (`app.security.users`), from environment variables or a secret manager. Without configured users, every API call is rejected.

Both options activate the `dev` profile, which provides the demo API users listed below. Option A does it automatically.

Liquibase creates the schema on startup. With the default `demo` context it also inserts six sample deposits, covering each plan and the 30, 45 and 365-day boundaries.

### Troubleshooting

The app recognises the two most common local setup problems and prints project-specific instructions instead of Spring's generic error (`config/diagnostics`).

| Message at startup | Cause | Fix |
|---|---|---|
| **"No database is configured"** | You started `TimeDepositKataApplication` without a profile (or plain `spring-boot:run`) | Use the quick start, the IntelliJ run configuration, or Option B |
| **"The database user may not create the schema: permission denied for schema public"** (from Liquibase: *"…make sure that you have both read and write permissions for the 'public.databasechangelog' table"*) | The app reached a PostgreSQL **15+** where its user exists but doesn't own the database. Usually a locally installed PostgreSQL on port 5432 | Use Option B's Docker database (port 55432), or the quick start. Or, as a PostgreSQL superuser: `ALTER DATABASE time_deposit_kata_db OWNER TO time_deposit_kata_user;` then `\c time_deposit_kata_db` and `ALTER SCHEMA public OWNER TO time_deposit_kata_user;`. If an earlier start left tables behind: `DROP DATABASE time_deposit_kata_db; CREATE DATABASE time_deposit_kata_db OWNER time_deposit_kata_user;` |
| `Could not find a valid Docker environment` | Docker isn't running (Testcontainers) | Start Docker Desktop |
| `Port 8080 was already in use` | Another instance is still running. After stopping Maven, the app's own JVM can keep running | Stop the other process, or add `--server.port=8081` |

Both diagnostics were checked against real failures. Without a profile, the first message appears. On a PostgreSQL 17 where the user doesn't own the database, the second appears, and after the suggested `ALTER … OWNER` the app starts and serves requests.

## Trigger the endpoints with Swagger

The endpoints are secured with HTTP Basic and roles (see "Security"). Both run options above come with two **local demo users**:

| User | Password | Roles | Can call |
|---|---|---|---|
| `viewer` | `viewer-demo-password` | VIEWER | GET |
| `operator` | `operator-demo-password` | VIEWER, OPERATOR | GET and POST |

1. Start the app, then open <http://localhost:8080/swagger-ui.html>. The raw contract is at <http://localhost:8080/v3/api-docs>. Both are public.
2. Click **Authorize** (top right), enter `operator` / `operator-demo-password`, and confirm. Swagger then sends the credentials with every call.
3. **GET `/api/v1/time-deposits`**: click *Try it out*, then *Execute*. You get every deposit with `id`, `planType`, `balance`, `days` and `withdrawals`.
4. **POST `/api/v1/time-deposits/balance-updates`**: click *Try it out*, then *Execute*. It returns `204 No Content`, applies one month of interest to every deposit, and records an audit posting for each one. (Logged in as `viewer`, it returns `403 Forbidden`.)
5. Run the GET again to see the new balances.
6. A second POST in the same calendar month (UTC) returns `204` but **changes nothing**. That is deliberate: interest is posted at most once per deposit per month, so retries and double clicks are safe (see "Idempotency and audit trail"). To post again, wait for the next month, or restart Option A, which starts with a fresh database.

Or with curl:

```bash
curl -u viewer:viewer-demo-password     http://localhost:8080/api/v1/time-deposits
curl -u operator:operator-demo-password -X POST http://localhost:8080/api/v1/time-deposits/balance-updates
```

## Tests

```bash
mvn test                                    # everything; the Spring/API tests need Docker running
mvn test -Dtest=TimeDepositCalculatorTest   # interest logic only, no Docker
```

- `TimeDepositCalculatorTest`: a golden-master test that compares the refactored calculator with a verbatim copy of the original code (`LegacyTimeDepositCalculator`) across every plan, the day boundaries and about 200 balances, plus readable boundary cases and an extensibility test.
- `UpdateAllBalancesServiceTest`: use-case tests with in-memory fake ports and a fixed `Clock`. Covers the audit postings, idempotency within a month, posting again in the next month, and partially posted months.
- `TimeDepositApiIntegrationTest`: HTTP, service, JPA, Liquibase and a real PostgreSQL via Testcontainers. It also checks that:
  - the OpenAPI contract has exactly two endpoints;
  - a retried POST in the same month changes nothing;
  - each deposit gets an audit row;
  - the database rejects invalid money data (CHECK constraints);
  - there is no N+1 (via Hibernate statistics). GET runs exactly 1 SQL statement and POST a fixed 5–6, all JDBC-batched, no matter how many deposits there are. Without batching it measured 50 for 23 deposits.
- `ConcurrentBalanceUpdateIntegrationTest`: 8 simultaneous balance updates post interest exactly once, with no errors.
- `OptimisticLockingIntegrationTest`: a write based on a stale read is rejected (`@Version`) instead of overwriting a newer balance.
- `OpenInViewDisabledTest`: `spring.jpa.open-in-view` is off in the default (production) configuration and in the `dev` profile. It checks the running context, not the config file. With the setting on, a database session would stay open for the whole HTTP request, including JSON serialisation, so lazy associations could load silently outside the transaction (hidden N+1 queries) and each request would hold a pooled connection longer than needed. I checked that the test fails when the setting is forced on.
- `SqlInjectionGuardTest` (ArchUnit, no Docker needed): production code may not use raw JDBC, string-built queries or native SQL. It also checks that each rule really fires on deliberately unsafe example code.
- `LargeVolumeBalanceUpdateIntegrationTest`: the monthly run over 40,000 deposits, above PostgreSQL's 32,767 bind-parameter limit. It prints the run time.
- `ApiSecurityIntegrationTest`: real HTTP Basic logins with the documented demo users. It checks:
  - anonymous → `401`, wrong password → `401`, both **without** a Basic challenge (so browsers can't cache credentials, which prevents CSRF);
  - failed logins and interest runs appear in the `SECURITY` audit log, and a username containing a newline can't forge log lines;
  - security headers (`no-store`, `nosniff`, `DENY`), and Swagger is off outside the `dev` profile;
  - viewer: GET `200`, POST `403`; operator: both allowed;
  - any other method or path → `403`;
  - Swagger and the contract are public;
  - **fail closed**: with no users configured, every call is `401`.

## Design

Hexagonal (ports and adapters):

```
adapter.in.web            REST controller + OpenAPI contract (TimeDepositApi) + response DTOs
application.port.in       use cases: GetAllTimeDepositsUseCase, UpdateAllBalancesUseCase
application.service       use-case implementations (transaction boundary)
application.port.out      one port per need (interface segregation):
                          LoadTimeDepositSummariesPort (read), LoadTimeDepositsForUpdatePort (locks),
                          UpdateTimeDepositBalancesPort, InterestPostingsPort
adapter.out.persistence   JPA entities, Spring Data repositories, port implementations
domain                    TimeDeposit, TimeDepositCalculator (original package), interest policies,
                          read models, InterestPosting (audit record), Money
config                    wires the framework-free domain objects (and the Clock) as Spring beans
```

- **Interest rules are a Strategy.** Each plan is an `InterestPolicy`. `AnnualRateInterestPolicy` is a template that holds the shared 30-day grace period and the `balance * rate / 12` formula, and `Basic`, `Student` and `Premium` only state their rate and extra eligibility rules. Plan codes (`basic`, `student`, `premium`) are defined once, in the `PlanType` enum. To add a plan, add a `PlanType` constant and a policy bean. `TimeDepositCalculator` does not change (Open/Closed). `TimeDeposit.planType` and the database keep the plain string code, which is resolved with `PlanType.fromCode`.
- **No breaking changes.** `TimeDeposit` keeps its original package, constructor, accessors and `Double` balance. `TimeDepositCalculator.updateBalance(List<TimeDeposit>)` keeps its signature, its no-arg constructor and its exact numeric results. The golden-master test enforces this.
- **Lombok outside the domain, records for data.**
  - **Services and adapters:** constructor injection via `@RequiredArgsConstructor`, logging via `@Slf4j`.
  - **JPA entities:** only the safe subset (`@Getter`, a single `@Setter`, the protected no-arg constructor JPA needs). The audit entity has a `@Builder`, so its three `BigDecimal` values can't be swapped by position.
  - **Blocked in `lombok.config`:** `@Data`, `@EqualsAndHashCode` and `@ToString` fail the build. On entities they would read lazy collections and break hash-based sets.
  - **The domain stays plain Java:** `TimeDeposit` must keep its exact legacy API, and immutable data uses Java records.
- **Withdrawals** come back through a separate read model (`TimeDepositSummary`) instead of a new field on the shared `TimeDeposit`.

### Money: `BigDecimal` everywhere except the legacy calculation

Money is `BigDecimal` in every part of the app I own: the database (`NUMERIC(19, 2)`), the JPA entities, the domain read models (`TimeDepositSummary`, `Withdrawal`) and the API responses. `double` remains in one place only: the legacy interest calculation (`TimeDeposit.balance`, `TimeDepositCalculator.updateBalance` and `InterestPolicy.monthlyInterest`). The persistence adapter converts at the boundary (`BigDecimal` to `Double` before the calculation, then back to `BigDecimal` with 2 decimals when saving).

This is a deliberate choice, forced by two README rules:

1. **No breaking changes to `TimeDeposit`.** Its constructor and `getBalance()`/`setBalance()` use `Double`, and changing them would break existing callers.
2. **`updateBalance` must behave exactly as before.** Doing the calculation in `BigDecimal` would change results, because the legacy code rounds a binary `double` rather than an exact decimal. For example:

   | Plan | Balance | Monthly interest today (`double`) | Interest with `BigDecimal` |
   |---|---|---|---|
   | basic | 18.00 | 0.01 | 0.02 |
   | basic | 30.00 | 0.02 | 0.03 |
   | student | 6.00 | 0.01 | 0.02 |
   | premium | 25.20 | 0.10 | 0.11 |

   18.00 × 1% / 12 is exactly 0.015, which should round half-up to 0.02. As a `double`, though, it is 0.01499999…, which rounds to 0.01.

So switching to `BigDecimal` would be a visible behaviour change of up to one cent per month for some balances, which the brief forbids. `TimeDepositCalculatorTest` pins the current behaviour. It compares the refactored calculator with a verbatim copy of the original code over about 10,000 combinations, and includes the 18.00 case explicitly.

**In production I would** raise the one-cent difference with the business. If they approve a fix, I'd ship it as a deliberate, versioned behaviour change rather than a silent refactoring: `InterestPolicy` would return `BigDecimal`, a `BigDecimal` balance would be added next to the old one (not in place of it), and the golden-master test would be updated on purpose. I did not add a second, `BigDecimal`-based calculator next to the legacy one, because two calculators that disagree by a cent would be worse than one that is consistently legacy.

### Security

Both endpoints require authentication, and the access rules are role-based (`config.SecurityConfiguration`):

| Request | Who | Why |
|---|---|---|
| `GET /api/v1/time-deposits` | role `VIEWER` | it exposes every customer's balances and withdrawals |
| `POST /api/v1/time-deposits/balance-updates` | role `OPERATOR` | it posts interest to every deposit. In production this would be the scheduler's service account |
| Swagger UI, `/v3/api-docs` | public | the contract is not sensitive, and reviewers can read it without logging in |
| anything else | denied | the brief allows exactly two endpoints |

- **HTTP Basic, deliberately kept simple.** It works with Swagger's Authorize button, so the reviewer steps stay one click. The rules are written in terms of roles, so moving to **OAuth2/JWT** (the production choice: a Spring Security resource server against the bank's identity provider) replaces only the authentication part, `httpBasic` and the user store. The access rules stay.
- **Fail closed.** Users come only from configuration (`app.security.users`). The default configuration has none, so a deployment that forgets to configure users rejects every call instead of opening up. Spring Boot's generated default user is not used.
- **Passwords are stored only as BCrypt hashes** (`{bcrypt}...`, with a delegating encoder so they can be moved to a stronger algorithm later). The two demo users exist only in the `dev` profile, for local use.
- **Stateless, and no browser-cached credentials, so no CSRF.** There's no session and no cookie, and credentials come with every request. A 401 is sent **without** a `WWW-Authenticate: Basic` challenge. My first version did send it, and the security review below caught why that was wrong. With the challenge, a browser that opens the API URL shows its login dialog and then *caches* the credentials, attaching them automatically to later requests, including a hidden cross-site form POST to `/balance-updates`. That's CSRF, even without a cookie. Without the challenge, browsers never prompt or cache. Swagger's Authorize, `curl -u` and k6 send the header up front, so they're unaffected. With nothing sent automatically, CSRF tokens aren't needed, so CSRF protection stays disabled. (This deviates from RFC 9110, which expects a challenge on 401; many browser-facing APIs deviate in the same way for the same reason.)
- **Audit log** (prefix `SECURITY`): every interest run is logged with the caller's username, and every failed login with the attempted username, the client address and the reason (never the password). The attempted username is attacker-controlled, so control characters are replaced. Otherwise a username containing a newline could forge extra log lines (log injection).
- **Swagger and `/v3/api-docs` are off by default** and only on in the `dev` profile, which both local run options use. A production deployment doesn't hand out a map of the API.
- **Cost, measured:** BCrypt is slow on purpose, and Basic auth checks the password on every request. That adds about 65 ms per call (an auth-only request takes ~65 ms). In the load test the GET median rose from about 250 ms to about 340 ms, and all thresholds still pass (GET p95 417 ms). With JWT the check is a cheap signature verification instead, which is another reason to use it in production.

### Security review

I probed the running app as an attacker would. All of these are checked in `ApiSecurityIntegrationTest` unless noted.

| Attack | Result |
|---|---|
| No credentials, wrong password, unknown user | `401`. No stack trace or internal message in the body |
| Viewer calling the interest posting | `403` |
| Other methods or paths (DELETE, PUT, OPTIONS, unknown paths) | `403` (`denyAll`). TRACE is `405` (checked manually) |
| Username enumeration by timing | not possible: a wrong password for a real user and for a non-existent user both take ~58 ms, because Spring hashes a dummy password for unknown users (checked manually) |
| CSRF via browser-cached Basic credentials | prevented: no Basic challenge, so browsers never cache credentials (see "Security") |
| Cross-origin JavaScript | blocked: no CORS headers, so browsers deny cross-origin reads and preflighted requests (checked manually) |
| SQL injection, mass assignment | no attack surface. The endpoints read no parameters, headers or request bodies (no `@RequestParam`, `@PathVariable` or `@RequestBody` anywhere), and a query string like `?id=1' OR 1=1--` or a JSON body on the POST is ignored (checked manually). All three queries are fixed JPQL in annotations, which Java requires to be compile-time constants. The only bind parameter (`:period`) comes from the server's clock. Everything else is Spring Data's generated, parameterised SQL. **Guarded for the future** by `SqlInjectionGuardTest`, see below |
| Path tricks (`;x=1`, `%2e%2e`, trailing slash, case changes) | rejected (`400`) or normalised before the access check (checked manually) |
| Log injection through the username | prevented: control characters replaced. The test fails if sanitising is removed (checked) |
| Caching of financial data by browsers or proxies | prevented: `Cache-Control: no-store`, plus `X-Content-Type-Options: nosniff` and `X-Frame-Options: DENY` |
| API contract exposed in production | Swagger and the OpenAPI document are off outside `dev` (`404`) |

**SQL injection guard.** "There is no injection today" says nothing about the next change, so `SqlInjectionGuardTest` ([ArchUnit](https://www.archunit.org)) fails the build if production code introduces any way of building SQL from strings:
- raw JDBC (`JdbcTemplate`, `NamedParameterJdbcTemplate`, `JdbcClient`, `java.sql.Statement`);
- query factories on `EntityManager` or `Session` (`createQuery(String)`, `createNativeQuery`, and so on);
- native SQL in `@Query`.

What remains allowed is static JPQL with bind parameters, plus Spring Data's generated queries. The test also runs each rule against deliberately unsafe example code in the test sources (`SqlInjectionProneExamples`, for example `"… WHERE plan_type = '" + planType + "'"`) and asserts that every rule fires. A guard that could never fail would prove nothing. The same rule is written down for future work in `CLAUDE.md`.

**Open risks, which need infrastructure or a decision rather than more code here:**
- **Brute force and BCrypt as a DoS lever: deliberately left to the gateway.** The app has no rate limiting or lockout. Every wrong password costs about 58 ms of server CPU, so with 20 cores roughly 300+ guesses per second would saturate the machine, without any valid credentials.

  **Decision:** rate limiting belongs at the API gateway or WAF in front of the service, not in the app. Reasons:
  - **The app can't identify clients reliably.** Behind a load balancer every request comes from the balancer's address. The real client address would have to come from `X-Forwarded-For`, which an attacker can forge unless only trusted proxies may set it, and that's infrastructure configuration.
  - **An in-memory limit doesn't hold across instances.** With N instances an attacker gets N times the allowance, unless the counters move to shared storage such as Redis, which adds a dependency for something the gateway already does.
  - **The gateway rejects excess requests before they cost any BCrypt CPU.** The app can't avoid paying for the password check.
  - **A per-account lockout would create its own DoS.** Anyone could lock out the operator account just by guessing wrong.

  **What the gateway should do:** limit requests per client to `/api/v1/time-deposits/**`, with a much stricter limit on responses that fail authentication (401), and block clients that keep failing. **What the app already provides:** a `SECURITY authentication failed: user='…' client=… reason=…` log line for every failed login, to alert on and to feed the gateway's blocking. With OAuth2/JWT in production, the per-request password check, and with it the CPU lever, goes away entirely.
- **TLS.** HTTP Basic credentials are only Base64-encoded, so production needs TLS (usually terminated at the ingress) and HSTS. Spring Security adds the HSTS header automatically on HTTPS requests.
- **Dependency vulnerabilities** were not scanned here. That belongs in CI (OWASP Dependency-Check or Dependabot), together with container image scanning.
- **Authorisation is per role, not per customer.** A VIEWER sees every deposit, which matches the brief ("retrieve all time deposits"). There is no customer-ownership model to enforce.

### Idempotency and audit trail

A bank must be able to explain every balance change, and must never pay the same interest twice. So the balance update writes an **interest posting** for each deposit it processes. The posting goes to the `interest_postings` table (`schema/003-interest-postings.sql`), in the same transaction as the balance change. Each row records the deposit, the month, the balance before and after, the interest, and when it was posted.

- **Idempotent per month.** The table has a unique `(time_deposit_id, period)` key. The service skips deposits that already have a posting for the current month, so a retried or double-clicked POST is a safe no-op. If two calls race past the service check, the unique key still rejects the second posting.
- **Every evaluated deposit is recorded, including zero interest** (for example, a student deposit past one year). The audit then shows the deposit was considered, not forgotten, and the month is marked as done for it.
- **The database enforces consistency:** `CHECK (interest = balance_after - balance_before)`, and `period` must be the first day of a month.
- **The month comes from an injected `Clock` (UTC)**, so tests can pin dates and the bank's booking zone is explicit.
- **Every run is logged after it commits.** The log line says the run was posted only once that's true, and its duration includes the batched writes. For example: `Interest run for 2026-11: posted 10000 deposits (8400 earned interest, 0 already posted this month), total interest 1050391.61, took 6672 ms including commit`.

**Assumption:** "update the balances" means "post this month's interest", so running it twice in a month must not pay twice. The brief doesn't say. The alternative reading, one more month of interest per call, would make every retry a double payment.

### Why there is no double-entry ledger (on purpose)

Adding ledger tables wouldn't break any README rule: no new endpoint, `TimeDeposit` untouched, the calculator unchanged. I still decided against it.

**`interest_postings` already is the journal for what this service does.** Each row records which deposit, which month, the balance before and after, and the interest. It's append-only, written in the same transaction as the balance change, checked by the database (`interest = balance_after - balance_before`), and unique per deposit and month. In accounting terms, each row is the customer side of a double entry.

**What a full ledger would add**, per posting, two entry lines that sum to zero:

| Account | Debit | Credit |
|---|---|---|
| Bank: interest expense (general ledger) | 1,028.81 | |
| Customer: time deposit #1 | | 1,028.81 |

**Why it doesn't belong in this service:**
- **Two sources of truth.** In a real ledger the balance is *derived* from the entries, but the brief requires `balance` to be stored on `timeDeposits`. A ledger next to it would mean two balances that can drift apart, plus reconciliation to catch it. That's more risk, not less.
- **It would be incomplete.** Withdrawals also move money, but the brief's `withdrawals` table isn't linked to the balance (see "Assumptions"), and nothing in this API creates them. A ledger that only knows about interest isn't balanced bookkeeping.
- **The general ledger is the core banking system's job.** A product service like this one records what it did and publishes it. In production I'd add a **transactional outbox**: an event row (for example `InterestPosted`, with the amounts and the posting id) written in the same transaction as the posting, and relayed to the core ledger asynchronously. The ledger gets every posting exactly once, even if this service or the message broker is down at commit time. The posting id makes the hand-over idempotent on the ledger side.

### Concurrency

The risk is two simultaneous `POST /balance-updates` calls (a double click, a client retry, or two app instances). Both would read the same balances and see "not yet posted this month", so both would post.

**Two layers prevent it:**
1. **A row lock.** The update loads the deposits with `SELECT ... FOR UPDATE` (`@Lock(PESSIMISTIC_WRITE)` on `TimeDepositJpaRepository.findAllForUpdate`), inside the service's single transaction. A second call waits until the first commits, then sees its postings and skips those deposits. It returns 204 and changes nothing.
2. **The unique `(deposit, period)` constraint** as a safety net. Even if the lock were missing, a second posting for the same month could never be stored.

**Optimistic locking for every other writer.** `time_deposits` has a `version` column (`schema/001-time-deposits.sql`), mapped with `@Version`. Every UPDATE Hibernate issues becomes `... WHERE id = ? AND version = ?` and increments the version. Any write based on a stale read fails with an optimistic-lock exception instead of silently overwriting a newer balance. The balance update never hits this check, because it holds the row lock. It's there for every other writer, present or future: for example, a withdrawal feature that loads a deposit, changes its balance and saves it without the lock. Using both is deliberate:

| | Used for | On conflict |
|---|---|---|
| Pessimistic (`SELECT ... FOR UPDATE`) | the monthly batch, where the expected conflict is "the same run twice" | the second caller **waits**, then succeeds as a no-op. No errors |
| Optimistic (`@Version`) | any other writer that changes a balance | the stale writer **fails loudly** and can retry. No silent lost update |

`OptimisticLockingIntegrationTest` shows a stale write being rejected while the newer balance survives. I checked that it fails without `@Version`: the stale value then silently overwrites the newer one. The test also shows that the batch increments the version only of deposits whose balance actually changed. JDBC batching still works with versioned entities; the statement-count test is unchanged.

Details:
- The lock is held by PostgreSQL, so it also works across several app instances. A Java `synchronized` would not.
- Rows are locked in `id` order, so two updates can't deadlock each other.
- GET doesn't lock anything. It's a single query, and PostgreSQL shows it either the balances before an update or after it, never a mix.

`ConcurrentBalanceUpdateIntegrationTest` starts 8 updates at the same moment and checks that interest is posted exactly once (100,000.00 premium becomes 100,416.67, with 1 posting) and that no call fails. I checked that the test catches a missing lock: without it, the extra calls fail with `duplicate key value violates unique constraint "uq_interest_postings_deposit_period"`. The safety net works, but callers would get errors.

**Thread safety, end to end:**

| Level | Guarantee | How | Checked by |
|---|---|---|---|
| Beans (one instance shared by all request threads) | no shared mutable state | every bean holds only `private final` references to immutable or thread-safe objects: stateless policies, an unmodifiable `EnumMap` in the calculator, `Clock.systemUTC()`. Per-request state (`TimeDeposit`, entities, lists) lives in local variables and the transaction | `TimeDepositCalculatorTest.sharedInstanceIsThreadSafe`: 16 threads × 2,000 deposits on one calculator, results identical to the original code |
| Read models / DTOs | immutable | records over immutable types (`BigDecimal`, `LocalDate`), with defensive `List.copyOf` | — |
| Writes: the same monthly run twice | posted exactly once, no errors | row lock + unique `(deposit, period)` | `ConcurrentBalanceUpdateIntegrationTest` (8 concurrent runs) |
| Writes: any other writer | no silent lost update | `@Version` | `OptimisticLockingIntegrationTest` |
| Reads during a run | never a half-applied run | GET is one SQL statement, and PostgreSQL gives each statement a consistent snapshot (MVCC). The run commits atomically | `ConcurrentBalanceUpdateIntegrationTest`: repeated GETs during a run over 5,000 deposits each see the total before or after, never in between |

Caveats:
- The legacy `TimeDeposit` is mutable and not thread-safe, as it always was. It's only ever used inside one request. A legacy caller that shared one list between threads would have to synchronise itself.
- The design relies on PostgreSQL's default isolation, **READ COMMITTED**. After the lock is granted, the "already posted?" query sees the postings the first run just committed. Under REPEATABLE READ or SERIALIZABLE the waiting run would fail with a serialization error instead. That's still safe (nothing is double-posted), but callers would get errors.
- Lock waits have no timeout: a second run waits as long as the first one takes, holding a DB connection. That's fine for a monthly batch (a few seconds). With other writers I'd add a lock timeout so they fail fast.

**Known limit:** the update locks every deposit for one transaction. With millions of rows I'd process them in pages (one transaction per page, resumable, because postings already record progress), and set a lock timeout so other writers fail fast instead of waiting.

### Performance and load testing

The load test is [`load-test/time-deposits.js`](load-test/time-deposits.js), a [k6](https://k6.io) script run through Docker, so the build gets no extra dependencies. It sends a steady 20 GET/s (fixed arrival rate, so slowness shows up as latency instead of fewer requests) plus one balance update every 10 s that competes for the row locks. Thresholds: GET p95 < 500 ms and p99 < 1 s, under 1% errors. For POST: the monthly batch must finish under 10 s, and the no-op calls must have a median under 1 s. (A POST percentile over six calls would only measure the one batch.)

```bash
# 1. Start the app with 10,000 deposits and 30,000 withdrawals (Liquibase context "loadtest")
mvn spring-boot:test-run -Dspring-boot.run.arguments="--spring.liquibase.contexts=loadtest"

# 2. In another terminal (Docker Desktop; on Linux use --network host and http://localhost:8080)
docker run --rm -i -e BASE_URL=http://host.docker.internal:8080 grafana/k6:1.3.0 run - < load-test/time-deposits.js
# Optional: -e READ_RPS=50 -e DURATION=30s
```

**Results** (60 s, 10,000 deposits). Environment: a 20-core Windows laptop, with the app, PostgreSQL (Testcontainers) and k6 (Docker Desktop) on the same machine, so the numbers are relative, not absolute.

| | GET p95 | GET p99 | POST p95 | Throughput | Errors |
|---|---|---|---|---|---|
| Before tuning, 20 GET/s | **16.07 s** ✗ | 19.12 s ✗ | 5.39 s ✗ | 11.5 req/s (480 dropped) | 0% |
| **After tuning, 20 GET/s** | **201 ms** ✓ | 267 ms ✓ | 1.32 s ✓ | 19.9 req/s (none dropped) | 0% |
| After tuning, stress at 50 GET/s | 3.64 s ✗ | 3.70 s ✗ | 4.77 s ✗ | ~29 req/s (saturated) | 0% |
| **With audit postings and idempotency, 20 GET/s** | **432 ms** ✓ | 540 ms ✓ | batch: 6.7 s ✓, no-op median: 143 ms ✓ | 20.1 req/s | 0% |
| **Plus Spring Security (Basic + BCrypt), 20 GET/s** | **417 ms** ✓ | 503 ms ✓ | batch: 4.1 s ✓, no-op median: 244 ms ✓ | 20.0 req/s | 0% |

In the last run, the first POST of the month posted interest for all 10,000 deposits: 10,000 UPDATEs and 10,000 audit INSERTs, batched, in one transaction, under full read load. That one call took 6.7 s. Every later POST that month is a no-op and takes about 140 ms. The earlier rows predate the audit table, when every POST rewrote all balances.

**What the measurements showed, and what changed:**
- The query itself takes 15 ms in PostgreSQL (`EXPLAIN ANALYZE`). About 90% of the ~150 ms GET time was in the app: Hibernate built 40,000 managed entities, with a lazily loaded collection per deposit, only to throw them away after serialising.
  → The read path now uses a **flat JPQL projection** (`select new TimeDepositWithdrawalRow(...)`). It is still one SQL statement, but it has no persistence context, snapshots or lazy collections. The write path keeps entities, because it needs dirty checking and row locks.
- The response is about 2.2 MB of JSON for 10,000 deposits. That much data, sent at 20 requests a second, overloaded the network path.
  → **Gzip** for JSON responses (`server.compression`): 2.2 MB becomes 326 KB, about 7 times smaller.
- **Virtual threads were measured and left off.** With `spring.threads.virtual.enabled=true`, under the same conditions (30 s runs, back to back), there was no measurable difference:

  | 30 s runs | GET p95 at 20/s | GET p95 at 50/s | Max throughput |
  |---|---|---|---|
  | Platform threads (default) | 418 ms | 4.28 s | 25.5 req/s |
  | Virtual threads | 372 ms | 4.20 s | 24.4 req/s |

  Why no difference: the bottleneck is CPU (serialising and compressing about 2 MB per response), not threads blocked on I/O. Even when saturated, only a handful of requests are in flight, far below Tomcat's 200 platform threads, and the 10-connection Hikari pool limits database concurrency in both cases. Turning them on would also remove Tomcat's thread cap, which today acts as natural backpressure: under overload, requests would pile up waiting for a DB connection (30 s timeout) instead of queueing. Virtual threads would be worth it if the service made many slow blocking calls (downstream HTTP, long lock waits) with hundreds of concurrent requests. They are then a one-line switch, and on JDK 24+ `synchronized` no longer pins carrier threads (JEP 491). On the Java 21 baseline this project targets, pinning would also need checking.
- **Caching** is not needed at this volume and would make balances stale between balance updates.
- **Run-to-run variance is large on a laptop.** The same configuration gave GET p95 between 201 ms and 418 ms in different runs. Compare configurations only with back-to-back runs of the same length, as above.

**The monthly interest run at bank scale.** `LargeVolumeBalanceUpdateIntegrationTest` runs the posting for 40,000 deposits. It also guards against a suspected bug: a statement with one bind parameter per deposit would exceed PostgreSQL's limit of 32,767. Measured, Hibernate 7 sends the id list as a single array parameter, so it works. I profiled the run by phase (warm, 40,000 deposits, ~4.5 s): load + lock 281 ms, "already posted" query 103 ms, calculator 37 ms, re-select 381 ms, creating postings 695 ms, **commit ~3 s**.

| Change (each measured, 2–3 runs) | 40k run |
|---|---|
| Baseline: JDBC batch 50 | 5.36 s |
| PostgreSQL driver `reWriteBatchedInserts` (multi-row INSERTs) | 4.90 s |
| JDBC batch 500 | 4.71 s |
| Both | ~4.2 s |
| **Kept: both, plus skipping a pointless auto-flush (103 ms) and reserving posting ids 500 at a time instead of 50 (80 sequence round trips instead of 800)** | **~3.9 s (−27%)** |
| Tried and reverted: resolving deposits from the persistence context instead of re-selecting them | no measurable gain, and a hidden "same transaction" requirement |

**Where the remaining time goes:** the same lock, UPDATE and INSERT of 40,000 rows in plain SQL, with the same constraints and indexes, takes **~0.8 s** in PostgreSQL (13 ms + 107 ms + 661 ms). So about 3 s of the 3.9 s is ORM overhead: 40,000 entities built, dirty-checked and written row by row, even in batches. The next step would be a bulk write path in the persistence adapter: `SELECT … FOR UPDATE` straight into `TimeDeposit` objects, then one `UPDATE … FROM unnest(?, ?)` that also bumps `version` for changed rows, and one `INSERT … SELECT unnest(…)` for the postings. That's an estimated ~3–4× faster, with the calculator, the locking and the idempotency unchanged. I didn't do it: it gives up JPA's safety nets (dirty checking, automatic `@Version` handling) on the most sensitive write in the system, and 3.9 s for 40,000 deposits once a month doesn't justify that. For millions of deposits, paging (see "Concurrency") matters more than raw speed.

**Where the GET time goes:** about 15 ms is SQL, about 100 ms is turning rows into JSON and gzipping 2.2 MB, and about 65 ms is BCrypt (see "Security"). The BCrypt part goes away with JWT in production. Caching verified passwords in memory would remove it now, but that's a security trade-off I wouldn't make in a bank. Streaming the JSON straight from the result set would save some allocations, at the cost of bypassing the domain model. With "return all deposits" fixed by the brief, the real fix at scale is pagination or ETags (below).

### Startup time

Measured with the jar against a plain PostgreSQL (not Testcontainers, whose container start would dominate), reading Spring Boot's "Started … in X seconds" line, 3 runs each:

| Variant | Startup |
|---|---|
| `java -jar` fat jar (baseline) | 6.1 s |
| Extracted jar (`java -Djarmode=tools -jar … extract`) | 5.15 s (−16%) |
| **Extracted + JDK AOT cache** (`scripts/build-aot-cache.sh`) | **~2.2 s (−64%)** |
| AOT cache + migrations run separately (`SPRING_LIQUIBASE_ENABLED=false`) | ~1.7 s (−72%) |

- **JDK AOT cache** (JEP 483/514, JDK 25+). A training run starts the Spring context once and exits, and the JVM records the classes it loaded and linked. Later starts reuse them. No code changes. Most of startup was the JVM loading and initialising Spring, Hibernate and Liquibase classes, which is exactly what the cache removes. [`scripts/build-aot-cache.sh`](scripts/build-aot-cache.sh) builds, extracts, trains and prints the run command. I checked that the app started from the cache behaves normally (401/200/204, interest run, Swagger). The cache must be rebuilt when the jar or the JDK changes. A mismatched cache is detected by the JVM and ignored, so startup is slower but still correct. The training run needs a reachable database, because Liquibase and Hibernate validation run at startup. On the project's Java 21 baseline, Spring Boot's CDS support is the equivalent (smaller gain).
- **Migrations as a separate step** (−1.0 s). Liquibase checks the changelog on every start. In production I'd turn it off in the app (`SPRING_LIQUIBASE_ENABLED=false`, no code change needed) and run the same changelog once per release, from a CI step or an init container. That also means several instances never race to migrate, and the app's DB user doesn't need DDL rights. Locally and in tests it stays on.
- **Measured and rejected:** turning off Hibernate schema validation, JDBC metadata access or springdoc made no measurable difference, so validation stays on as a safety net. Lazy initialisation (−0.4 s) moves wiring errors and first-request latency from startup into the first requests, which is a bad trade for a bank service. C1-only JIT (−0.5 s) makes peak throughput worse.
- **Not tried:** Spring AOT processing or a GraalVM native image. A native image would start in well under a second, but it needs GraalVM, longer builds and reachability metadata for Hibernate, Liquibase and springdoc. For a service that starts once per deploy, the AOT cache gives most of the benefit at almost no cost.

**Where the limit is:** about 29 GET/s on this machine. Every GET returns *all* deposits, as the README requires, so the cost grows with the number of deposits. With much more data or traffic, the next steps would be:
1. Pagination or streaming (`?page=&size=` or NDJSON). That changes the contract, which the brief fixes.
2. HTTP caching with an `ETag` that changes only when a balance update runs, so unchanged data returns `304 Not Modified`.
3. More app instances behind a load balancer. GET is stateless, and the row lock already makes POST safe across instances.

### Database migrations (Liquibase)

```
src/main/resources/db/changelog/
├── db.changelog-master.yaml      includes schema first, then data
├── schema/                       one changeset per table, in its final shape, always applied
│   ├── 001-time-deposits.sql     table + CHECK (days >= 0) + version column (optimistic locking)
│   ├── 002-withdrawals.sql       table + CHECK (amount > 0) + index on time_deposit_id
│   └── 003-interest-postings.sql sequence (step 500) + audit table + unique (deposit, month) + CHECKs + index
└── data/                         only with a Liquibase context, never in production
    ├── demo-data.sql             context "demo": 6 deposits covering each plan and the 30/45/365-day boundaries
    └── load-test-data.sql        context "loadtest": 10,000 deposits, 30,000 withdrawals
```

- **Schema and data are separate.** Data runs only after the full schema, so every constraint applies to it. Production runs without a context, so it gets the schema only.
- **Consolidated before the first release.** The schema first grew as a series of small `ALTER` changesets, one per design step: constraints, the version column, the sequence step. Nothing had been released, so I folded them into one changeset per table, which is much easier to review. I proved the change is purely structural: the old and new changesets, applied to an empty PostgreSQL, give **identical** `pg_dump --schema-only` output.
- **From now on, the usual rule applies:** never edit a changeset that may have been applied; add a new numbered file under `schema/`. Liquibase stores a checksum per changeset and refuses to start if an applied one changed.
- **SQL-formatted changesets** (not YAML or XML): the schema is PostgreSQL-specific anyway (identity columns, `generate_series`, `TIMESTAMPTZ`), and plain SQL is what reviewers and DBAs read most easily. Each changeset has a rollback.

### Assumptions

- Tables and columns use snake_case (`time_deposits.plan_type`, `withdrawals.time_deposit_id`) instead of the README's camelCase, because PostgreSQL folds unquoted identifiers to lower case. The API uses the camelCase field names from the README.
- Money is stored as `NUMERIC(19, 2)`. The shared model's legacy `Double` balance is converted at the persistence boundary, which is exact for amounts up to about 10^13.
- `days` is stored as given and is not advanced by the balance update. The brief does not say how a deposit ages.
- `balance` is the current balance, with withdrawals already taken into account. `withdrawals` is history, returned by GET but not subtracted again when interest is calculated. The brief lists withdrawals without saying how they affect the balance, and subtracting them would change the legacy calculation's input.
- There is one currency. The brief has no currency field, so none is stored.
- Monthly interest is the annual rate / 12, as in the legacy code. Real products often use a day-count convention (for example actual/365). That would be a business change, like the rounding in "Money" above.
- Interest is posted once per deposit per calendar month (UTC). See "Idempotency and audit trail".
- The balance update is a `POST` because it triggers a process (an interest run that creates postings) rather than replacing a resource. It is still safe to retry. It runs in a single transaction.
- Even invalid input behaves exactly as before, because the brief says `updateBalance`'s behaviour must not change. A `null` plan type earns nothing up to 30 days and throws a `NullPointerException` after that, as the original did. The golden-master tests check this against the verbatim legacy code. The README says invalid input doesn't need handling, so I kept the legacy behaviour rather than "fixing" it.
- The database enforces `withdrawals.amount > 0` and `time_deposits.days >= 0` (in the table definitions under `schema/`). It deliberately does **not** enforce `balance >= 0`, because the legacy calculator accepts negative balances, so that would be a new business rule. It also does not enforce a fixed list of `plan_type` values, because unknown plans must remain storable (they earn no interest), and new plans must be addable.

## Production readiness

These are deliberately outside the kata's scope (the README asks for exactly two endpoints and says error handling isn't required), but I would do them before going live:

- **OAuth2/JWT instead of HTTP Basic.** Role-based access is in place (see "Security"). For production I'd switch authentication to an OAuth2 resource server against the bank's identity provider, with short-lived tokens, the scheduler as a service account with the OPERATOR role, and audit logging of who triggered each interest run. I'd also add TLS everywhere, because Basic credentials are only Base64-encoded.
- **Secrets.** Done in this solution: no credentials are committed for real environments. In production they would come from a secret manager such as Vault or AWS Secrets Manager, with rotation.
- **Enforce append-only at the database level.** Give the application's DB user `INSERT` and `SELECT` on `interest_postings`, but not `UPDATE` or `DELETE`. Today append-only is enforced only by the code (`updatable = false`, no update path).
- **Run the interest posting as a scheduled job**, for example with ShedLock or a Kubernetes CronJob on the first day of each month, instead of relying on someone calling the endpoint. The per-month idempotency already makes a missed run, a retried run, or two schedulers safe.
- **Dependency and container scanning** (OWASP Dependency-Check or Dependabot, Trivy) in CI.
- **Rate limiting at the gateway or WAF** (a deliberate decision not to do it in the app; see "Security review"): per-client limits against password guessing and the BCrypt CPU cost, with a stricter limit on failed logins, and a limit on the POST, which is harmless thanks to idempotency but locks every deposit.
- **Observability:** metrics and traces (Micrometer, OpenTelemetry) for run duration, deposits posted and total interest, plus alerting when a month's run is missing. Actuator health and metrics would go on a separate management port, because the brief allows only two API endpoints.
- **Error responses** in RFC 9457 Problem Details format (`spring.mvc.problemdetails.enabled`), once error handling is in scope.
- **Hand-over to the core ledger** through a transactional outbox (see "Why there is no double-entry ledger").
- **Scale:** paging through the batch, and pagination or ETags for GET. See "Concurrency" and "Performance".

## AI-assisted development

### Tools and setup

| | |
|---|---|
| Agent | [Claude Code](https://claude.com/claude-code), an agentic coding assistant, used from the JetBrains IDE integration and its terminal |
| Model | Claude Opus 5.5 |
| Agent configuration | [`CLAUDE.md`](../CLAUDE.md) at the repository root, the only configuration (see below) |
| What the agent could run | Maven (`./mvnw`), Git, Docker (Testcontainers, throwaway PostgreSQL containers, k6 via `grafana/k6`), `curl`, and file edits. Every file edit was shown as a diff in the IDE and accepted, or changed, by me before it was applied |
| Not used | no custom system prompt, no subagents, no MCP servers, no hooks, no plugins. Claude Code's default system prompt plus `CLAUDE.md` is the whole setup |

### Reproducing the setup

1. Install [Claude Code](https://docs.claude.com/en/docs/claude-code) (CLI or IDE extension) and sign in.
2. Have **JDK 21+ and Docker** available, which the agent needs to build and run the tests. Maven comes with `./mvnw`.
3. Open the repository root in Claude Code. `CLAUDE.md` is loaded automatically into every session. There is nothing else to install or configure.
4. Verify the baseline the agent works from: `cd java && ./mvnw test` (56 tests, green).

### Custom rules: `CLAUDE.md`

The agent configuration is a plain Markdown file, versioned with the code. It started from Claude Code's `/init` command (a codebase analysis) and was then maintained alongside every design decision. It contains:
- **The kata's hard constraints, so the agent can't drift from them:**
  - exactly two endpoints (also enforced by a test);
  - `TimeDeposit`'s public API and `updateBalance`'s signature and behaviour frozen;
  - the golden-master test's legacy copy must never be edited to make a test pass.
- **Commands:** build, single test, the app with Testcontainers, load test, AOT cache.
- **Architecture rules:** hexagonal layers, domain without Spring or Lombok, beans stateless, Liquibase layout and "never edit an applied changeset".
- **Safety rules that came out of the work:**
  - idempotency and locking must not be weakened;
  - `open-in-view` stays off;
  - no SQL built from strings (enforced by ArchUnit);
  - a 401 must carry no Basic challenge (CSRF);
  - rate limiting is left to the gateway, by decision;
  - performance changes must be measured.

### How it was used: the workflow

The agent was used throughout, from the first analysis to the commit history, in a loop:
1. **I set the direction**, usually as a question ("is the app thread-safe?", "is N+1 handled?", "is it protected from SQL injection?").
2. **The agent investigated before changing anything**: it read the code, ran the tests, and measured (load tests, phase timings, `EXPLAIN ANALYZE`, a schema diff via `pg_dump`).
3. **It proposed a fix, or a reason not to make one.** I decided.
4. **Every change was verified, including that its guard test fails without the fix.** For example: the concurrency test with the row lock removed, the stale-write test without `@Version`, the batching test with batching off, the SQL-injection rules against deliberately unsafe code, and the log-injection test without sanitising.
5. **Docs and `CLAUDE.md` were updated in the same step**, so the next session starts with the decision on record.

### What was AI-assisted, and why

**Written by the agent, then reviewed and accepted by me diff by diff:** all code, tests, Liquibase changesets, the k6 script, the AOT script and the documentation. That covers:
- the golden-master tests and the Strategy refactoring;
- the hexagonal layers, the REST API and OpenAPI contract;
- the audit trail and per-month idempotency;
- pessimistic and optimistic locking;
- Spring Security;
- the performance and startup work;
- the security review and the SQL-injection guard;
- the Liquibase consolidation;
- the split into atomic commits.

**Why:** most of it is well-understood engineering, where the agent is fast, and I could spend my time on review and decisions. More importantly, the agent made *verification* cheap. Golden-master comparisons over 10,000 cases, load tests, schema diffs and "does this test fail without the fix?" checks are tedious by hand, so they're often skipped. Here every claim in this README comes from such a check.

**Decided by me** (the agent proposed options; several were rejected or changed):
- **Domain modelling:** plan codes as an enum, and Lombok outside the domain only.
- **Keeping legacy behaviour over correctness:** the `double` rounding stays and is documented, rather than switched to `BigDecimal`.
- **Not doing a feature toggle (Unleash)** for a `BigDecimal` version: a documented rollout plan instead.
- **Adding optimistic locking** on top of the row lock.
- **Adding Spring Security**, in the lightweight form.
- **Leaving rate limiting to the gateway.**
- **No double-entry ledger in this service:** a documented outbox design instead.
- **Consolidating the Liquibase changesets** before release.
- **The final pre-submission review.**

**Where the human-in-the-loop mattered.** The agent's own checks caught several of its mistakes, which are documented rather than hidden:
- **CSRF:** the first "no cookie, so no CSRF" reasoning was wrong, because browsers cache Basic credentials. It was found in the security review and fixed.
- **Bind-parameter limit:** a suspected 32,767-parameter bug turned out not to exist (measured).
- **An optimisation that didn't help** was measured and reverted.
- **A flaky statement-count test** caused by sequence pooling was found and fixed.
- **Missing executable bit:** the scripts were committed from Windows without it, which was caught before submission.
