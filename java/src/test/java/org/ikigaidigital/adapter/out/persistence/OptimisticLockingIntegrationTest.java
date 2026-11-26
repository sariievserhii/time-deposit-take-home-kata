package org.ikigaidigital.adapter.out.persistence;

import org.ikigaidigital.TestcontainersConfiguration;
import org.ikigaidigital.application.port.in.UpdateAllBalancesUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Optimistic locking (@Version) protects balances from writers that don't take the pessimistic row lock:
 * a write based on a stale read must fail instead of silently overwriting a newer balance (lost update).
 */
@SpringBootTest(properties = "spring.liquibase.contexts=test")
@Import(TestcontainersConfiguration.class)
class OptimisticLockingIntegrationTest {

    @Autowired
    private TimeDepositJpaRepository repository;

    @Autowired
    private UpdateAllBalancesUseCase updateAllBalances;

    @Autowired
    private TransactionTemplate tx;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void seed() {
        jdbc.update("DELETE FROM interest_postings");
        jdbc.update("DELETE FROM withdrawals");
        jdbc.update("DELETE FROM time_deposits");
        jdbc.update("""
                INSERT INTO time_deposits (id, plan_type, days, balance) VALUES
                    (1, 'basic',   60,  1200.00),
                    (2, 'student', 400, 5000.00)""");
    }

    @Test
    void staleWriteIsRejectedInsteadOfOverwritingANewerBalance() {
        // Writer A reads the deposit (version 0) and works on it outside the lock.
        TimeDepositJpaEntity staleCopy = tx.execute(status -> repository.findById(1).orElseThrow());

        // Meanwhile writer B changes the balance and commits (version 0 -> 1).
        tx.executeWithoutResult(status -> repository.findById(1).orElseThrow().setBalance(new BigDecimal("1300.00")));

        // Writer A now saves its stale copy. Without @Version this would silently overwrite 1300.00.
        staleCopy.setBalance(new BigDecimal("999.99"));
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> repository.save(staleCopy)))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);

        assertThat(jdbc.queryForObject("SELECT balance FROM time_deposits WHERE id = 1", BigDecimal.class))
                .isEqualByComparingTo("1300.00");
    }

    @Test
    void balanceUpdateIncrementsTheVersionOnlyOfDepositsWhoseBalanceChanged() {
        updateAllBalances.updateAllBalances();

        assertThat(versionOf(1)).as("basic earned interest").isEqualTo(1L);
        assertThat(versionOf(2)).as("student past one year: balance unchanged, no UPDATE").isEqualTo(0L);
        assertThat(jdbc.queryForObject("SELECT balance FROM time_deposits WHERE id = 1", BigDecimal.class))
                .isEqualByComparingTo("1201.00");
    }

    private Long versionOf(int id) {
        return jdbc.queryForObject("SELECT version FROM time_deposits WHERE id = ?", Long.class, id);
    }
}
