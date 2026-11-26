package org.ikigaidigital.adapter.out.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

interface TimeDepositJpaRepository extends JpaRepository<TimeDepositJpaEntity, Integer> {

    /**
     * Read path: deposits and their withdrawals in one SQL statement (no N+1), returned as flat rows instead of
     * entities. Skipping entity hydration was the main GET latency win in the load test. Ordered so all rows of
     * one deposit are adjacent, with its withdrawals in id order. {@code TimeDepositApiIntegrationTest} asserts
     * the single statement.
     */
    @Query("""
            select new org.ikigaidigital.adapter.out.persistence.TimeDepositWithdrawalRow(
                t.id, t.planType, t.balance, t.days, w.id, w.amount, w.date)
            from TimeDepositJpaEntity t left join t.withdrawals w
            order by t.id, w.id""")
    List<TimeDepositWithdrawalRow> findAllWithWithdrawalsAsRows();

    /**
     * SELECT ... FOR UPDATE: locks every deposit until the surrounding transaction ends, so concurrent balance
     * updates run one after another instead of overwriting each other (lost update). Ordered by id so that
     * transactions always take the locks in the same order and cannot deadlock each other.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from TimeDepositJpaEntity t order by t.id")
    List<TimeDepositJpaEntity> findAllForUpdate();
}
