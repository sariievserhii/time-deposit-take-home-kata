package org.ikigaidigital.application.port.out;

import org.ikigaidigital.TimeDeposit;
import org.ikigaidigital.domain.model.TimeDepositSummary;

import java.util.List;

public interface LoadTimeDepositsPort {

    /**
     * All deposits as the shared domain model used by {@code TimeDepositCalculator}, locked against concurrent
     * changes until the caller's transaction ends. Must be called inside a transaction.
     */
    List<TimeDeposit> loadAllForUpdate();

    /** All deposits with their withdrawals, ordered by id. */
    List<TimeDepositSummary> loadAllWithWithdrawals();
}
