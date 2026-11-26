package org.ikigaidigital.adapter.out.persistence;

import lombok.RequiredArgsConstructor;
import org.ikigaidigital.application.port.out.InterestPostingsPort;
import org.ikigaidigital.domain.model.InterestPosting;
import org.springframework.stereotype.Component;

import java.time.YearMonth;
import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
class InterestPostingPersistenceAdapter implements InterestPostingsPort {

    private final InterestPostingJpaRepository repository;

    @Override
    public Set<Integer> findDepositIdsPostedIn(YearMonth period) {
        return repository.findDepositIdsByPeriod(period.atDay(1));
    }

    @Override
    public void saveAll(List<InterestPosting> postings) {
        // Inserted at commit, in JDBC batches (sequence ids + hibernate.jdbc.batch_size).
        repository.saveAll(postings.stream()
                .map(p -> InterestPostingJpaEntity.builder()
                        .timeDepositId(p.timeDepositId())
                        .period(p.period().atDay(1))
                        .balanceBefore(p.balanceBefore())
                        .balanceAfter(p.balanceAfter())
                        .interest(p.interest())
                        .postedAt(p.postedAt())
                        .build())
                .toList());
    }
}
