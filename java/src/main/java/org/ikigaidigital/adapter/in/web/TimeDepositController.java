package org.ikigaidigital.adapter.in.web;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ikigaidigital.adapter.in.web.dto.TimeDepositResponse;
import org.ikigaidigital.application.port.in.GetAllTimeDepositsUseCase;
import org.ikigaidigital.application.port.in.UpdateAllBalancesUseCase;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping(TimeDepositApi.BASE_PATH)
class TimeDepositController implements TimeDepositApi {

    private final GetAllTimeDepositsUseCase getAllTimeDepositsUseCase;
    private final UpdateAllBalancesUseCase updateAllBalancesUseCase;

    @Override
    @GetMapping
    public List<TimeDepositResponse> getAllTimeDeposits() {
        return getAllTimeDepositsUseCase.getAllTimeDeposits().stream()
                .map(TimeDepositResponse::from)
                .toList();
    }

    /**
     * POST because the call triggers a process (an interest run that creates postings) rather than replacing a
     * resource with the request body, so neither PUT nor PATCH fit. It is still safe to retry: repeated calls
     * in the same month are no-ops. The path names the "balance update" run that the call creates.
     */
    @Override
    @PostMapping("/balance-updates")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateAllBalances(Principal principal) {
        // Audit: who triggered the interest run. The service logs the outcome after the commit.
        log.info("SECURITY interest run requested by user='{}'", principal.getName());
        updateAllBalancesUseCase.updateAllBalances();
    }
}
