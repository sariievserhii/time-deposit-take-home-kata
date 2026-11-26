package org.ikigaidigital.domain.interest;

import org.ikigaidigital.domain.model.PlanType;

/** Student plan: 3% a year, paid monthly after the grace period, and no interest after one year. */
public final class StudentInterestPolicy extends AnnualRateInterestPolicy {

    /**
     * "No interest after 1 year": the legacy code pays interest while {@code days < 366}, so day 365 still
     * earns interest and day 366 does not. That boundary is kept.
     */
    static final int LAST_INTEREST_DAY = 365;

    @Override
    public PlanType planType() {
        return PlanType.STUDENT;
    }

    @Override
    protected double annualRate() {
        return 0.03;
    }

    @Override
    protected boolean isEligible(int days) {
        return super.isEligible(days) && days <= LAST_INTEREST_DAY;
    }
}
