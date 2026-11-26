package org.ikigaidigital.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ikigaidigital.TimeDeposit;
import org.ikigaidigital.TimeDepositCalculator;
import org.ikigaidigital.application.port.in.UpdateAllBalancesUseCase;
import org.ikigaidigital.application.port.out.InterestPostingsPort;
import org.ikigaidigital.application.port.out.LoadTimeDepositsPort;
import org.ikigaidigital.application.port.out.UpdateTimeDepositBalancesPort;
import org.ikigaidigital.domain.model.InterestPosting;
import org.ikigaidigital.domain.model.Money;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Reuses the existing {@link TimeDepositCalculator#updateBalance(List)} unchanged, so the API and the legacy
 * callers share one source of truth for interest rules.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UpdateAllBalancesService implements UpdateAllBalancesUseCase {

    private final LoadTimeDepositsPort loadTimeDepositsPort;
    private final UpdateTimeDepositBalancesPort updateTimeDepositBalancesPort;
    private final InterestPostingsPort interestPostingsPort;
    private final TimeDepositCalculator calculator;
    private final Clock clock;

    /**
     * One transaction: balances and their audit postings are written together, or not at all.
     * <ul>
     *   <li><b>Idempotent per month:</b> deposits that already have a posting for the current month (by the
     *   injected clock, UTC) are skipped, so retries and double clicks don't pay interest twice. The unique
     *   (deposit, period) key in the database backs this up.</li>
     *   <li><b>Concurrency:</b> the deposits are locked while loading, so a concurrent call waits, then sees
     *   the postings of the first call and skips those deposits instead of overwriting them.</li>
     * </ul>
     * Assumption: the data set fits in memory. For very large tables this would be done in pages.
     */
    @Override
    @Transactional
    public void updateAllBalances() {
        Instant startedAt = clock.instant();
        YearMonth period = YearMonth.now(clock);

        List<TimeDeposit> deposits = loadTimeDepositsPort.loadAllForUpdate();
        Set<Integer> alreadyPosted = interestPostingsPort.findDepositIdsPostedIn(period);
        List<TimeDeposit> due = deposits.stream()
                .filter(deposit -> !alreadyPosted.contains(deposit.getId()))
                .toList();

        if (due.isEmpty()) {
            log.info("Interest run for {}: nothing to post, all {} deposits already posted this month", period, deposits.size());
            return;
        }

        List<BigDecimal> balancesBefore = due.stream().map(d -> Money.fromLegacyBalance(d.getBalance())).toList();
        calculator.updateBalance(due);
        updateTimeDepositBalancesPort.updateBalances(due);

        List<InterestPosting> postings = new ArrayList<>(due.size());
        for (int i = 0; i < due.size(); i++) {
            TimeDeposit deposit = due.get(i);
            postings.add(new InterestPosting(deposit.getId(), period, balancesBefore.get(i),
                    Money.fromLegacyBalance(deposit.getBalance()), startedAt));
        }
        interestPostingsPort.saveAll(postings);

        BigDecimal totalInterest = postings.stream().map(InterestPosting::interest).reduce(BigDecimal.ZERO, BigDecimal::add);
        long earningInterest = postings.stream().filter(p -> p.interest().signum() != 0).count();
        Runnable logRun = () -> log.info("Interest run for {}: posted {} deposits ({} earned interest, {} already "
                        + "posted this month), total interest {}, took {} ms including commit",
                period, due.size(), earningInterest, alreadyPosted.size(), totalInterest,
                Duration.between(startedAt, clock.instant()).toMillis());
        afterCommit(logRun);
    }

    /**
     * The batched UPDATEs and INSERTs only run when the transaction commits. Logging after the commit means the
     * line is only written if the postings really were stored, and the duration includes the writes.
     */
    private static void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run(); // no transaction, e.g. a plain unit test
        }
    }
}
