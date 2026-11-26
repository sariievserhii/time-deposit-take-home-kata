package org.ikigaidigital.domain.interest;

import org.ikigaidigital.domain.model.PlanType;

/** Premium plan: 5% a year, paid monthly, starting after 45 days. */
public final class PremiumInterestPolicy extends AnnualRateInterestPolicy {

    /** "Interest starts after 45 days": the legacy code requires {@code days > 45}. */
    static final int INTEREST_START_AFTER_DAYS = 45;

    @Override
    public PlanType planType() {
        return PlanType.PREMIUM;
    }

    @Override
    protected double annualRate() {
        return 0.05;
    }

    @Override
    protected boolean isEligible(int days) {
        return super.isEligible(days) && days > INTEREST_START_AFTER_DAYS;
    }
}
