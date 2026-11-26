package org.ikigaidigital.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;

/**
 * Audit record of one interest posting: which deposit, for which month, and the balance before and after.
 * Postings are append-only. At most one exists per deposit and month, which is what makes the balance update
 * idempotent per month.
 */
public record InterestPosting(int timeDepositId, YearMonth period, BigDecimal balanceBefore, BigDecimal balanceAfter,
                              Instant postedAt) {

    public BigDecimal interest() {
        return balanceAfter.subtract(balanceBefore);
    }
}
