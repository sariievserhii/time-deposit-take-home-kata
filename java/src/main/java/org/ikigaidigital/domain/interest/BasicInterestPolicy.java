package org.ikigaidigital.domain.interest;

import org.ikigaidigital.domain.model.PlanType;

/** Basic plan: 1% a year, paid monthly after the grace period. */
public final class BasicInterestPolicy extends AnnualRateInterestPolicy {

    @Override
    public PlanType planType() {
        return PlanType.BASIC;
    }

    @Override
    protected double annualRate() {
        return 0.01;
    }
}
