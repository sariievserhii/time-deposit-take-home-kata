package org.ikigaidigital.adapter.out.persistence;

import jakarta.persistence.QueryHint;
import org.hibernate.jpa.HibernateHints;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Set;

interface InterestPostingJpaRepository extends JpaRepository<InterestPostingJpaEntity, Long> {

    /**
     * FlushMode COMMIT skips Hibernate's automatic flush before this query. That flush would dirty-check every deposit
     * loaded by the balance update (103 ms for 40,000 deposits) only to find nothing to write. At this point the
     * transaction has made no changes yet, so the result is the same.
     */
    @QueryHints(@QueryHint(name = HibernateHints.HINT_FLUSH_MODE, value = "COMMIT"))
    @Query("select p.timeDepositId from InterestPostingJpaEntity p where p.period = :period")
    Set<Integer> findDepositIdsByPeriod(@Param("period") LocalDate period);
}
