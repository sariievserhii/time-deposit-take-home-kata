package org.ikigaidigital.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * README table {@code withdrawals}; see {@link TimeDepositJpaEntity} for the naming assumption and for why only a
 * safe subset of Lombok is used on entities. Read-only for the app: no setters.
 */
@Entity
@Table(name = "withdrawals")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // for JPA
public class WithdrawalJpaEntity {

    @Id
    private Integer id;

    /** No getter: the app never navigates from a withdrawal to its deposit, and doing so would be a lazy load. */
    @Getter(AccessLevel.NONE)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "time_deposit_id", nullable = false)
    private TimeDepositJpaEntity timeDeposit;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false)
    private LocalDate date;
}
