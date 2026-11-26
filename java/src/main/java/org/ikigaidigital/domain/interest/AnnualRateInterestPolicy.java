package org.ikigaidigital.domain.interest;

import org.ikigaidigital.TimeDeposit;

/**
 * Template for plans that pay a fixed annual rate monthly ({@code balance * rate / 12}) once the deposit is
 * eligible. Subclasses only state their rate and, optionally, tighten eligibility.
 */
public abstract class AnnualRateInterestPolicy implements InterestPolicy {

    /** README: no interest is applied for the first 30 days for any existing plan. */
    static final int GRACE_PERIOD_DAYS = 30;

    private static final int MONTHS_PER_YEAR = 12;

    protected abstract double annualRate();

    /**
     * Subclasses that add conditions should combine them with {@code super.isEligible(days)}, so the
     * common grace period still applies.
     */
    protected boolean isEligible(int days) {
        return days > GRACE_PERIOD_DAYS;
    }

    @Override
    public final double monthlyInterest(TimeDeposit deposit) {
        if (!isEligible(deposit.getDays())) {
            return 0;
        }
        // Deliberately double, not BigDecimal: the README requires updateBalance to behave exactly as before, and
        // exact decimal maths changes some results by a cent (e.g. 18.00 * 0.01 / 12 = 0.015 rounds to 0.02 in
        // BigDecimal but to 0.01 in double, which is 0.01499...). See java/README.md, section "Money".
        // Same evaluation order as the legacy code ((balance * rate) / 12) to keep results bit-identical.
        return deposit.getBalance() * annualRate() / MONTHS_PER_YEAR;
    }
}
