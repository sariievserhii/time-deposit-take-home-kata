package org.ikigaidigital.application.port.out;

import org.ikigaidigital.TimeDeposit;

import java.util.List;

public interface UpdateTimeDepositBalancesPort {

    /** Persists the current balance of each deposit, matched by id. Other fields are not written. */
    void updateBalances(List<TimeDeposit> deposits);
}
