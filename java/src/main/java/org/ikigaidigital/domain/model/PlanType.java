package org.ikigaidigital.domain.model;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The plan types the bank offers, and the single place where their codes are defined.
 * <p>
 * {@code TimeDeposit.planType} and the database keep the plain code (e.g. {@code "basic"}), because changing the
 * shared {@code TimeDeposit} to use this enum would be a breaking change. Use {@link #fromCode(String)} to resolve it.
 * To add a plan: add a constant here and an {@code InterestPolicy} for it.
 */
public enum PlanType {
    BASIC("basic"),
    STUDENT("student"),
    PREMIUM("premium");

    private static final Map<String, PlanType> BY_CODE = Arrays.stream(values())
            .collect(Collectors.toUnmodifiableMap(PlanType::code, Function.identity()));

    private final String code;

    PlanType(String code) {
        this.code = code;
    }

    /** The code stored in {@code TimeDeposit.planType} and the {@code plan_type} column. */
    public String code() {
        return code;
    }

    /**
     * Case-sensitive, like the legacy {@code planType.equals("basic")} checks.
     * Empty for unknown or null codes.
     */
    public static Optional<PlanType> fromCode(String code) {
        return code == null ? Optional.empty() : Optional.ofNullable(BY_CODE.get(code));
    }
}
