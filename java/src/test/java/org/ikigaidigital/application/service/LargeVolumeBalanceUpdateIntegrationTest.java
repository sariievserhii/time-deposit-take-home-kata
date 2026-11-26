package org.ikigaidigital.application.service;

import org.ikigaidigital.TestcontainersConfiguration;
import org.ikigaidigital.TestDatabase;
import org.ikigaidigital.application.port.in.UpdateAllBalancesUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The monthly run must work for a bank-sized portfolio. In particular it must not put one bind parameter per deposit
 * into a single SQL statement: PostgreSQL allows at most 32,767 bind parameters per statement.
 */
@SpringBootTest(properties = "spring.liquibase.contexts=test")
@Import(TestcontainersConfiguration.class)
class LargeVolumeBalanceUpdateIntegrationTest {

    /** Above PostgreSQL's 32,767 bind-parameter limit. */
    private static final int DEPOSITS = 40_000;

    @Autowired
    private UpdateAllBalancesUseCase updateAllBalances;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void seed() {
        TestDatabase.deleteAll(jdbc);
        // basic, 60 days, 1200.00: each earns exactly 1.00
        jdbc.update("INSERT INTO time_deposits (id, plan_type, days, balance) "
                + "SELECT g, 'basic', 60, 1200.00 FROM generate_series(1, ?) AS g", DEPOSITS);
    }

    @Test
    void postsInterestForMoreDepositsThanPostgresAllowsBindParametersInOneStatement() {
        long start = System.nanoTime();
        updateAllBalances.updateAllBalances();
        long millis = (System.nanoTime() - start) / 1_000_000;

        assertThat(jdbc.queryForObject("SELECT sum(balance) FROM time_deposits", BigDecimal.class))
                .isEqualByComparingTo(BigDecimal.valueOf(DEPOSITS * 1201L));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM interest_postings", Integer.class)).isEqualTo(DEPOSITS);
        System.out.printf("[perf] interest run for %,d deposits took %,d ms%n", DEPOSITS, millis);
    }
}
