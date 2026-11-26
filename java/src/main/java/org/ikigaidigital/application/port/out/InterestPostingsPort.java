package org.ikigaidigital.application.port.out;

import org.ikigaidigital.domain.model.InterestPosting;

import java.time.YearMonth;
import java.util.List;
import java.util.Set;

public interface InterestPostingsPort {

    /** Ids of the deposits that already received interest for the given month. */
    Set<Integer> findDepositIdsPostedIn(YearMonth period);

    /** Appends postings. They are never updated or deleted. */
    void saveAll(List<InterestPosting> postings);
}
