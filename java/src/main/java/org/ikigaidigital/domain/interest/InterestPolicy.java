package org.ikigaidigital.domain.interest;

import org.ikigaidigital.TimeDeposit;
import org.ikigaidigital.domain.model.PlanType;

/**
 * Strategy for the monthly interest of one plan type.
 * To support a new plan, add its {@link PlanType} constant, implement this interface, and register the
 * implementation as a bean (or pass it to the calculator).
 */
public interface InterestPolicy {

    /** The plan type this policy handles. */
    PlanType planType();

    /** Unrounded monthly interest for the deposit; 0 when the deposit is not eligible. */
    double monthlyInterest(TimeDeposit deposit);
}
