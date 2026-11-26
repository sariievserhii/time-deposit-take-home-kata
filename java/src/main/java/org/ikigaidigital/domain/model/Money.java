package org.ikigaidigital.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Conversion between the legacy {@code Double} balance of {@code TimeDeposit} and stored money amounts. */
public final class Money {

    /** Cents. Matches NUMERIC(19, 2) in the schema. */
    public static final int SCALE = 2;

    private Money() {
    }

    /**
     * BigDecimal.valueOf uses the shortest decimal form of the double, so 1235595.81 stays 1235595.81 instead of
     * carrying binary noise. Exact for amounts up to about 10^13. See README, section "Money".
     */
    public static BigDecimal fromLegacyBalance(double balance) {
        return BigDecimal.valueOf(balance).setScale(SCALE, RoundingMode.HALF_UP);
    }
}
