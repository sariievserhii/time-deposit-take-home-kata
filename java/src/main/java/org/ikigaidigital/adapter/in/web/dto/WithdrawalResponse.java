package org.ikigaidigital.adapter.in.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.ikigaidigital.domain.model.Withdrawal;

import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(name = "Withdrawal")
public record WithdrawalResponse(
        @Schema(example = "1") int id,
        @Schema(example = "100.00") BigDecimal amount,
        @Schema(example = "2026-01-15") LocalDate date) {

    public static WithdrawalResponse from(Withdrawal withdrawal) {
        return new WithdrawalResponse(withdrawal.id(), withdrawal.amount(), withdrawal.date());
    }
}
