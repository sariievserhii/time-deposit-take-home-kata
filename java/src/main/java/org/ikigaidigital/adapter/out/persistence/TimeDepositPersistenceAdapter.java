package org.ikigaidigital.adapter.out.persistence;

import lombok.RequiredArgsConstructor;
import org.ikigaidigital.TimeDeposit;
import org.ikigaidigital.application.port.out.LoadTimeDepositSummariesPort;
import org.ikigaidigital.application.port.out.LoadTimeDepositsForUpdatePort;
import org.ikigaidigital.application.port.out.UpdateTimeDepositBalancesPort;
import org.ikigaidigital.domain.model.Money;
import org.ikigaidigital.domain.model.TimeDepositSummary;
import org.ikigaidigital.domain.model.Withdrawal;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
/** One adapter for the time_deposits table, implementing the read port and the two write-side ports. */
class TimeDepositPersistenceAdapter
        implements LoadTimeDepositSummariesPort, LoadTimeDepositsForUpdatePort, UpdateTimeDepositBalancesPort {

    private final TimeDepositJpaRepository repository;

    @Override
    public List<TimeDeposit> loadAllForUpdate() {
        return repository.findAllForUpdate().stream()
                .map(TimeDepositPersistenceAdapter::toDomain)
                .toList();
    }

    @Override
    public List<TimeDepositSummary> loadAllWithWithdrawals() {
        // Rows arrive ordered by deposit id, so each deposit's rows are adjacent: group them in one pass.
        List<TimeDepositSummary> summaries = new ArrayList<>();
        TimeDepositWithdrawalRow current = null;
        List<Withdrawal> withdrawals = new ArrayList<>();
        for (TimeDepositWithdrawalRow row : repository.findAllWithWithdrawalsAsRows()) {
            if (current != null && !current.depositId().equals(row.depositId())) {
                summaries.add(toSummary(current, withdrawals));
                withdrawals = new ArrayList<>();
            }
            current = row;
            if (row.withdrawalId() != null) { // null when the deposit has no withdrawals (LEFT JOIN)
                withdrawals.add(new Withdrawal(row.withdrawalId(), row.withdrawalAmount(), row.withdrawalDate()));
            }
        }
        if (current != null) {
            summaries.add(toSummary(current, withdrawals));
        }
        return summaries;
    }

    @Override
    public void updateBalances(List<TimeDeposit> deposits) {
        // One SELECT for all ids. (Resolving each id from the persistence context instead was measured: no gain at
        // 40,000 deposits, and it would only work inside the transaction that loaded them, so it was not kept.)
        Map<Integer, TimeDepositJpaEntity> entitiesById = repository
                .findAllById(deposits.stream().map(TimeDeposit::getId).toList())
                .stream()
                .collect(Collectors.toMap(TimeDepositJpaEntity::getId, Function.identity()));

        for (TimeDeposit deposit : deposits) {
            TimeDepositJpaEntity entity = entitiesById.get(deposit.getId());
            if (entity != null) {
                entity.setBalance(Money.fromLegacyBalance(deposit.getBalance()));
            }
        }
        // Managed entities are flushed by dirty checking when the surrounding transaction commits.
    }

    private static TimeDeposit toDomain(TimeDepositJpaEntity entity) {
        // The shared TimeDeposit model keeps its legacy Double balance. Amounts with up to ~15 significant
        // digits convert exactly, which covers NUMERIC(19, 2) values up to about 10^13.
        return new TimeDeposit(entity.getId(), entity.getPlanType(), entity.getBalance().doubleValue(), entity.getDays());
    }

    private static TimeDepositSummary toSummary(TimeDepositWithdrawalRow deposit, List<Withdrawal> withdrawals) {
        return new TimeDepositSummary(deposit.depositId(), deposit.planType(), deposit.balance(), deposit.days(), withdrawals);
    }
}
