package org.ikigaidigital.domain.model;

import java.math.BigDecimal;
import java.util.List;

/**
 * Read model for listing deposits together with their withdrawals.
 * <p>
 * A separate type is used because adding {@code withdrawals} to the shared {@code TimeDeposit} class could
 * break its consumers (README constraint). The balance is a {@link BigDecimal} so the stored 2-decimal
 * amount is returned exactly.
 */
public record TimeDepositSummary(int id, String planType, BigDecimal balance, int days, List<Withdrawal> withdrawals) {

    public TimeDepositSummary {
        withdrawals = List.copyOf(withdrawals);
    }
}
