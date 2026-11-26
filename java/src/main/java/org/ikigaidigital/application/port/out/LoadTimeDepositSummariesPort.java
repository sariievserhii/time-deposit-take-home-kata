package org.ikigaidigital.application.port.out;

import org.ikigaidigital.domain.model.TimeDepositSummary;

import java.util.List;

/** Read side: deposits with their withdrawals, for the GET endpoint. Never locks anything. */
public interface LoadTimeDepositSummariesPort {

    /** All deposits with their withdrawals, ordered by id. */
    List<TimeDepositSummary> loadAllWithWithdrawals();
}
