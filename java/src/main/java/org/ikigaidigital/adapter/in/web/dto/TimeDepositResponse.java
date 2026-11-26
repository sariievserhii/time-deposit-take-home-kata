package org.ikigaidigital.adapter.in.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.ikigaidigital.domain.model.TimeDepositSummary;

import java.math.BigDecimal;
import java.util.List;

@Schema(name = "TimeDeposit")
public record TimeDepositResponse(
        @Schema(example = "1") int id,
        @Schema(example = "basic", description = "basic, student or premium") String planType,
        @Schema(example = "1234567.00") BigDecimal balance,
        @Schema(example = "45", description = "Age of the deposit in days") int days,
        List<WithdrawalResponse> withdrawals) {

    public static TimeDepositResponse from(TimeDepositSummary summary) {
        return new TimeDepositResponse(
                summary.id(),
                summary.planType(),
                summary.balance(),
                summary.days(),
                summary.withdrawals().stream().map(WithdrawalResponse::from).toList());
    }
}
