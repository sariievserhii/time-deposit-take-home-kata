package org.ikigaidigital.application.port.in;

import org.ikigaidigital.domain.model.TimeDepositSummary;

import java.util.List;

public interface GetAllTimeDepositsUseCase {

    List<TimeDepositSummary> getAllTimeDeposits();
}
