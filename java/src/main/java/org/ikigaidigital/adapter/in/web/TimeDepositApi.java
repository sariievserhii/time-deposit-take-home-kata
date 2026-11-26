package org.ikigaidigital.adapter.in.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.ikigaidigital.adapter.in.web.dto.TimeDepositResponse;

import java.security.Principal;
import java.util.List;

/**
 * OpenAPI contract of the two endpoints the README allows. The documentation lives here, so the
 * controller only holds the delegation code. Generated spec: {@code /v3/api-docs}, UI: {@code /swagger-ui.html}.
 */
@Tag(name = "Time deposits")
public interface TimeDepositApi {

    String BASE_PATH = "/api/v1/time-deposits";

    @Operation(summary = "List all time deposits",
            description = "Returns every time deposit with its withdrawals, ordered by id. Requires role VIEWER.")
    @ApiResponse(responseCode = "200", description = "All time deposits")
    @ApiResponse(responseCode = "401", description = "Missing or invalid credentials", content = @Content)
    @ApiResponse(responseCode = "403", description = "Authenticated, but without role VIEWER", content = @Content)
    List<TimeDepositResponse> getAllTimeDeposits();

    @Operation(summary = "Update the balances of all time deposits",
            description = """
                    Applies one month of interest to every time deposit and stores the new balances, recording an
                    audit posting per deposit. Idempotent per calendar month (UTC): the first call in a month posts
                    interest, and further calls in the same month change nothing.
                    Rules: no interest for the first 30 days; basic 1% a year; student 3% a year up to day 365;
                    premium 5% a year after day 45. Monthly interest = balance * rate / 12, rounded HALF_UP to cents.
                    Requires role OPERATOR.""")
    @ApiResponse(responseCode = "204", description = "Balances updated")
    @ApiResponse(responseCode = "401", description = "Missing or invalid credentials", content = @Content)
    @ApiResponse(responseCode = "403", description = "Authenticated, but without role OPERATOR", content = @Content)
    /** @param principal the authenticated caller, for the audit log. Supplied by Spring, not part of the contract. */
    void updateAllBalances(@Parameter(hidden = true) Principal principal);
}
