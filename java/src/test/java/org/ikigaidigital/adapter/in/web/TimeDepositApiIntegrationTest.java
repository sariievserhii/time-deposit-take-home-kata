package org.ikigaidigital.adapter.in.web;

import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.ikigaidigital.TestcontainersConfiguration;
import org.ikigaidigital.TestDatabase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** End-to-end through HTTP, the services, JPA and Liquibase against a real PostgreSQL (Testcontainers). */
@SpringBootTest(properties = {
        "spring.liquibase.contexts=test",
        // Statistics let the tests count SQL statements, to prove there is no N+1.
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "logging.level.org.hibernate.engine.internal.StatisticalLoggingSessionEventListener=WARN",
        // The contract test below reads the generated OpenAPI document, which is off by default (dev profile only).
        "springdoc.api-docs.enabled=true"
})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class TimeDepositApiIntegrationTest {

    private static final String BASE = TimeDepositApi.BASE_PATH;

    /** Authenticated callers, independent of the configured user store (see ApiSecurityIntegrationTest for that). */
    private static final RequestPostProcessor AS_VIEWER = user("test-viewer").roles("VIEWER");
    private static final RequestPostProcessor AS_OPERATOR = user("test-operator").roles("VIEWER", "OPERATOR");

    /** See postUsesAFixedNumberOfStatementsRegardlessOfHowManyDepositsThereAre. */
    private static final long MIN_POST_STATEMENTS = 5;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @BeforeEach
    void seed() {
        TestDatabase.deleteAll(jdbc);
        jdbc.update("""
                INSERT INTO time_deposits (id, plan_type, days, balance) VALUES
                    (1, 'basic',   45, 1234567.00),
                    (2, 'student', 400,   5000.00),
                    (3, 'premium', 90,   20000.00)""");
        jdbc.update("""
                INSERT INTO withdrawals (id, time_deposit_id, amount, date) VALUES
                    (10, 3, 100.00, DATE '2026-02-01'),
                    (11, 3,  50.00, DATE '2026-03-10')""");
    }

    private void addDepositsWithTwoWithdrawalsEach(int count) {
        for (int id = 100; id < 100 + count; id++) {
            jdbc.update("INSERT INTO time_deposits (id, plan_type, days, balance) VALUES (?, 'basic', 60, 1000.00)", id);
            jdbc.update("INSERT INTO withdrawals (id, time_deposit_id, amount, date) VALUES (?, ?, 10.00, DATE '2026-01-01')", id * 10, id);
            jdbc.update("INSERT INTO withdrawals (id, time_deposit_id, amount, date) VALUES (?, ?, 20.00, DATE '2026-02-01')", id * 10 + 1, id);
        }
    }

    private Statistics hibernateStatistics() {
        return entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    }

    @Test
    void getReturnsAllDepositsWithTheirWithdrawals() throws Exception {
        mockMvc.perform(get(BASE).with(AS_VIEWER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].planType").value("basic"))
                .andExpect(jsonPath("$[0].balance").value(1234567.00))
                .andExpect(jsonPath("$[0].days").value(45))
                .andExpect(jsonPath("$[0].withdrawals", hasSize(0)))
                .andExpect(jsonPath("$[2].withdrawals", hasSize(2)))
                .andExpect(jsonPath("$[2].withdrawals[0].id").value(10))
                .andExpect(jsonPath("$[2].withdrawals[0].amount").value(100.00))
                .andExpect(jsonPath("$[2].withdrawals[0].date").value("2026-02-01"));
    }

    @Test
    void postAppliesOneMonthOfInterestToEveryDeposit() throws Exception {
        mockMvc.perform(post(BASE + "/balance-updates").with(AS_OPERATOR))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(BASE).with(AS_VIEWER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].balance").value(1235595.81)) // basic: +1028.81
                .andExpect(jsonPath("$[1].balance").value(5000.00))    // student past one year: no interest
                .andExpect(jsonPath("$[2].balance").value(20083.33));  // premium: +83.33
    }

    @Test
    void postRecordsAnAuditPostingPerDepositAndIsIdempotentWithinTheMonth() throws Exception {
        mockMvc.perform(post(BASE + "/balance-updates").with(AS_OPERATOR)).andExpect(status().isNoContent());
        mockMvc.perform(post(BASE + "/balance-updates").with(AS_OPERATOR)).andExpect(status().isNoContent()); // retry, same month

        mockMvc.perform(get(BASE).with(AS_VIEWER))
                .andExpect(jsonPath("$[0].balance").value(1235595.81)); // one month only, not two

        assertThat(jdbc.queryForList("""
                SELECT time_deposit_id, balance_before, balance_after, interest
                FROM interest_postings ORDER BY time_deposit_id"""))
                .extracting(row -> row.get("time_deposit_id"), row -> row.get("balance_before").toString(),
                        row -> row.get("balance_after").toString(), row -> row.get("interest").toString())
                .containsExactly(
                        tuple(1, "1234567.00", "1235595.81", "1028.81"),
                        tuple(2, "5000.00", "5000.00", "0.00"),       // evaluated, no interest: still audited
                        tuple(3, "20000.00", "20083.33", "83.33"));
    }

    @Test
    void databaseRejectsInvalidMoneyData() {
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO withdrawals (id, time_deposit_id, amount, date) VALUES (99, 1, -5.00, DATE '2026-01-01')"))
                .hasMessageContaining("chk_withdrawals_amount_positive");
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO time_deposits (id, plan_type, days, balance) VALUES (99, 'basic', -1, 10.00)"))
                .hasMessageContaining("chk_time_deposits_days_non_negative");
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO interest_postings (id, time_deposit_id, period, balance_before, balance_after, interest, posted_at)
                VALUES (1, 1, DATE '2026-01-01', 100.00, 101.00, 5.00, now())"""))
                .hasMessageContaining("chk_interest_postings_interest");
    }

    @Test
    void getUsesOneQueryRegardlessOfHowManyDepositsAndWithdrawalsThereAre() throws Exception {
        addDepositsWithTwoWithdrawalsEach(20);
        Statistics statistics = hibernateStatistics();
        statistics.clear();

        mockMvc.perform(get(BASE).with(AS_VIEWER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(23)))
                .andExpect(jsonPath("$[22].withdrawals", hasSize(2)));

        assertThat(statistics.getPrepareStatementCount()).as("SQL statements for GET").isEqualTo(1);
    }

    @Test
    void postUsesAFixedNumberOfStatementsRegardlessOfHowManyDepositsThereAre() throws Exception {
        addDepositsWithTwoWithdrawalsEach(20);
        Statistics statistics = hibernateStatistics();
        statistics.clear();

        mockMvc.perform(post(BASE + "/balance-updates").with(AS_OPERATOR))
                .andExpect(status().isNoContent());

        // Fixed cost, independent of the number of deposits (up to the batch size of 50): load + lock the deposits,
        // find this month's postings, re-select by id to apply balances, 1 batched UPDATE and 1 batched INSERT of
        // the audit postings = 5. Plus 1 sequence call only when Hibernate's pooled sequence has used up its
        // in-memory block of 50 ids, which depends on what earlier tests in this Spring context inserted, hence
        // 5 or 6. Without JDBC batching it grows with every deposit: measured 50 statements here (22 UPDATEs, as the
        // student deposit's balance doesn't change, plus 23 INSERTs, plus the SELECTs).
        assertThat(statistics.getPrepareStatementCount()).as("SQL statements for POST")
                .isBetween(MIN_POST_STATEMENTS, MIN_POST_STATEMENTS + 1);
        // 23 deposits, but the student one past a year earns no interest. Its balance is unchanged, so dirty
        // checking skips it.
        assertThat(statistics.getEntityUpdateCount()).as("deposits updated").isEqualTo(22);
    }

    @Test
    void openApiContractExposesExactlyTheTwoEndpoints() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths.length()").value(2))
                .andExpect(jsonPath("$.paths['" + BASE + "'].get").exists())
                .andExpect(jsonPath("$.paths['" + BASE + "/balance-updates'].post").exists());
    }
}
