package org.ikigaidigital.application.port.out;

import org.ikigaidigital.TimeDeposit;

import java.util.List;

/** Write side: load deposits for the interest run. */
public interface LoadTimeDepositsForUpdatePort {

    /**
     * All deposits as the shared domain model used by {@code TimeDepositCalculator}, locked against concurrent
     * changes until the caller's transaction ends. Must be called inside a transaction.
     */
    List<TimeDeposit> loadAllForUpdate();
}
