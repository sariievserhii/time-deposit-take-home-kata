package org.ikigaidigital.application.service;

import lombok.RequiredArgsConstructor;
import org.ikigaidigital.application.port.in.GetAllTimeDepositsUseCase;
import org.ikigaidigital.application.port.out.LoadTimeDepositsPort;
import org.ikigaidigital.domain.model.TimeDepositSummary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GetAllTimeDepositsService implements GetAllTimeDepositsUseCase {

    private final LoadTimeDepositsPort loadTimeDepositsPort;

    @Override
    @Transactional(readOnly = true)
    public List<TimeDepositSummary> getAllTimeDeposits() {
        return loadTimeDepositsPort.loadAllWithWithdrawals();
    }
}
