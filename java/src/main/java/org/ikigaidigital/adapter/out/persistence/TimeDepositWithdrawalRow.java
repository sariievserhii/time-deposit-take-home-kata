package org.ikigaidigital.adapter.out.persistence;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One row of the deposits LEFT JOIN withdrawals read query. The withdrawal fields are null for a deposit without
 * withdrawals. Used instead of entities on the read path: no persistence context, dirty-check snapshots or lazy
 * collections, which made up most of the GET latency in the load test (see README, "Performance").
 */
public record TimeDepositWithdrawalRow(
        Integer depositId, String planType, BigDecimal balance, Integer days,
        Integer withdrawalId, BigDecimal withdrawalAmount, LocalDate withdrawalDate) {
}
