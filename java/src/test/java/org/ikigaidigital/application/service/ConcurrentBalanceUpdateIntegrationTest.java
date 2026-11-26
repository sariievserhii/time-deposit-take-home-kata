package org.ikigaidigital.application.service;

import org.ikigaidigital.TestcontainersConfiguration;
import org.ikigaidigital.application.port.in.GetAllTimeDepositsUseCase;
import org.ikigaidigital.application.port.in.UpdateAllBalancesUseCase;
import org.ikigaidigital.domain.model.TimeDepositSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Concurrent balance updates in the same month (double clicks, retries, several app instances) must post
 * interest exactly once, and none of the calls may fail. Runs against a real PostgreSQL, because the protection
 * is a database row lock plus a unique constraint.
 * <p>
 * Without the row lock, all calls would read "not yet posted", and 7 of the 8 would fail on the unique
 * (deposit, period) constraint. That failure mode was checked by temporarily removing the lock.
 */
@SpringBootTest(properties = "spring.liquibase.contexts=test")
@Import(TestcontainersConfiguration.class)
class ConcurrentBalanceUpdateIntegrationTest {

    private static final int CONCURRENT_CALLS = 8;

    @Autowired
    private UpdateAllBalancesUseCase updateAllBalances;

    @Autowired
    private GetAllTimeDepositsUseCase getAllTimeDeposits;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void seed() {
        jdbc.update("DELETE FROM interest_postings");
        jdbc.update("DELETE FROM withdrawals");
        jdbc.update("DELETE FROM time_deposits");
        jdbc.update("INSERT INTO time_deposits (id, plan_type, days, balance) VALUES (1, 'premium', 90, 100000.00)");
    }

    @Test
    void concurrentCallsInTheSameMonthPostInterestExactlyOnceWithoutErrors() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(CONCURRENT_CALLS);
        try {
            List<Future<?>> calls = new ArrayList<>();
            for (int i = 0; i < CONCURRENT_CALLS; i++) {
                calls.add(pool.submit(() -> {
                    start.await();
                    updateAllBalances.updateAllBalances();
                    return null;
                }));
            }
            start.countDown(); // release all threads at once to maximise overlap
            for (Future<?> call : calls) {
                call.get(); // rethrows any failure from the worker thread
            }
        } finally {
            pool.shutdownNow();
        }

        // premium 5%: 100000 * 0.05 / 12 = 416.67, applied once
        BigDecimal balance = jdbc.queryForObject("SELECT balance FROM time_deposits WHERE id = 1", BigDecimal.class);
        assertThat(balance).isEqualByComparingTo("100416.67");
        Integer postings = jdbc.queryForObject("SELECT count(*) FROM interest_postings", Integer.class);
        assertThat(postings).isEqualTo(1);
    }

    /**
     * No torn reads: a GET that runs while an interest run is in progress must see either all balances before
     * the run or all balances after it, never a mix (some deposits with this month's interest, some without).
     */
    @Test
    void readsDuringAnInterestRunSeeAllOldOrAllNewBalancesNeverAMix() throws Exception {
        jdbc.update("DELETE FROM time_deposits");
        // 5,000 basic deposits of 1200.00 at 60 days: each earns exactly 1.00 -> total 6,000,000.00 -> 6,005,000.00
        jdbc.update("""
                INSERT INTO time_deposits (id, plan_type, days, balance)
                SELECT g, 'basic', 60, 1200.00 FROM generate_series(1, 5000) AS g""");
        BigDecimal before = new BigDecimal("6000000.00");
        BigDecimal after = new BigDecimal("6005000.00");

        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<?> run = pool.submit(() -> updateAllBalances.updateAllBalances());
            List<BigDecimal> totalsSeen = new ArrayList<>();
            while (!run.isDone()) {
                totalsSeen.add(totalBalanceAsSeenByGet());
            }
            run.get(); // rethrows a failure of the run
            totalsSeen.add(totalBalanceAsSeenByGet());

            assertThat(totalsSeen)
                    .as("every read is a consistent snapshot")
                    .allSatisfy(total -> assertThat(total).isIn(before, after));
            assertThat(totalsSeen)
                    .as("at least one read overlapped the uncommitted run, so the test is not vacuous")
                    .contains(before);
            assertThat(totalsSeen.getLast()).isEqualByComparingTo(after);
        } finally {
            pool.shutdownNow();
        }
    }

    private BigDecimal totalBalanceAsSeenByGet() {
        return getAllTimeDeposits.getAllTimeDeposits().stream()
                .map(TimeDepositSummary::balance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
