package org.ikigaidigital;

import org.ikigaidigital.domain.interest.BasicInterestPolicy;
import org.ikigaidigital.domain.interest.InterestPolicy;
import org.ikigaidigital.domain.interest.PremiumInterestPolicy;
import org.ikigaidigital.domain.interest.StudentInterestPolicy;
import org.ikigaidigital.domain.model.PlanType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Applies one month of interest to each deposit.
 * <p>
 * The plan-specific rules live in {@link InterestPolicy} implementations (Strategy pattern), so a new plan
 * or rule is added by writing a new policy, without changing this class (Open/Closed principle).
 * <p>
 * Behaviour is kept identical to the original implementation, including the {@code double} arithmetic and
 * the {@code new BigDecimal(double)} HALF_UP rounding to 2 decimals. {@code TimeDepositCalculatorTest}
 * pins this behaviour against a verbatim copy of the legacy code.
 */
public class TimeDepositCalculator {

    /** The legacy code only looked at the plan type for deposits older than this; see monthlyInterest. */
    private static final int LEGACY_PLAN_TYPE_CHECK_AFTER_DAYS = 30;

    private final Map<PlanType, InterestPolicy> policiesByPlanType;

    /** Default rules, as in the original kata. Keeps the original no-arg constructor working. */
    public TimeDepositCalculator() {
        this(List.of(new BasicInterestPolicy(), new StudentInterestPolicy(), new PremiumInterestPolicy()));
    }

    public TimeDepositCalculator(Collection<? extends InterestPolicy> policies) {
        this.policiesByPlanType = Collections.unmodifiableMap(policies.stream()
                .collect(Collectors.toMap(
                        InterestPolicy::planType,
                        Function.identity(),
                        (first, second) -> {
                            throw new IllegalStateException("More than one interest policy for plan " + first.planType());
                        },
                        () -> new EnumMap<>(PlanType.class))));
    }

    public void updateBalance(List<TimeDeposit> xs) {
        for (TimeDeposit deposit : xs) {
            double interest = monthlyInterest(deposit);

            double a2d = deposit.getBalance() + (new BigDecimal(interest).setScale(2, RoundingMode.HALF_UP)).doubleValue();
            deposit.setBalance(a2d);
        }
    }

    private double monthlyInterest(TimeDeposit deposit) {
        // README: updateBalance's behaviour must stay unchanged, so this keeps even the legacy reaction to a null plan
        // type. The original only called planType.equals(...) after its "days > 30" check, so a null plan type threw a
        // NullPointerException for days > 30 (aborting the loop, earlier deposits already updated) and earned nothing
        // otherwise. Pinned by TimeDepositCalculatorTest against the verbatim legacy code.
        if (deposit.getPlanType() == null && deposit.getDays() > LEGACY_PLAN_TYPE_CHECK_AFTER_DAYS) {
            throw new NullPointerException("planType is null for time deposit " + deposit.getId());
        }
        // Unknown plan types, and plans without a registered policy, earn no interest, as in the original if/else chain.
        return PlanType.fromCode(deposit.getPlanType())
                .map(policiesByPlanType::get)
                .map(policy -> policy.monthlyInterest(deposit))
                .orElse(0.0);
    }
}
