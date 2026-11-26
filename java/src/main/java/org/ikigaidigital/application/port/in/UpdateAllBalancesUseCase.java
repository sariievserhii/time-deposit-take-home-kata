package org.ikigaidigital.application.port.in;

public interface UpdateAllBalancesUseCase {

    /**
     * Applies one month of interest to every stored time deposit that has not yet received interest for the
     * current month (UTC), and records an interest posting for each. Idempotent per month: repeated calls in
     * the same month change nothing.
     */
    void updateAllBalances();
}
