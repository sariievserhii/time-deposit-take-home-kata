package org.ikigaidigital.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Append-only audit row; see {@code db/changelog/schema/003-interest-postings.sql}. Never updated after insert:
 * no setters, and every column is {@code updatable = false}.
 * <p>
 * Created through the package-private {@code @Builder}: three of the values are {@code BigDecimal}s (balance before,
 * balance after, interest), and named builder calls can't swap them by accident the way positional constructor
 * arguments can. The id is left unset and assigned by the sequence.
 */
@Entity
@Table(name = "interest_postings")
@Builder(access = AccessLevel.PACKAGE)
@AllArgsConstructor(access = AccessLevel.PRIVATE) // for the builder only
@NoArgsConstructor(access = AccessLevel.PROTECTED) // for JPA
public class InterestPostingJpaEntity {

    /**
     * Sequence instead of IDENTITY: Hibernate cannot batch IDENTITY inserts, and a run inserts one posting per
     * deposit. allocationSize must match the sequence's INCREMENT BY 500 (schema/003-interest-postings.sql): one round
     * trip per 500 ids.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "interest_postings_seq")
    @SequenceGenerator(name = "interest_postings_seq", sequenceName = "interest_postings_seq", allocationSize = 500)
    private Long id;

    @Column(name = "time_deposit_id", nullable = false, updatable = false)
    private Integer timeDepositId;

    /** First day of the month the interest is for. */
    @Column(nullable = false, updatable = false)
    private LocalDate period;

    @Column(name = "balance_before", nullable = false, updatable = false, precision = 19, scale = 2)
    private BigDecimal balanceBefore;

    @Column(name = "balance_after", nullable = false, updatable = false, precision = 19, scale = 2)
    private BigDecimal balanceAfter;

    @Column(nullable = false, updatable = false, precision = 19, scale = 2)
    private BigDecimal interest;

    @Column(name = "posted_at", nullable = false, updatable = false)
    private Instant postedAt;
}
