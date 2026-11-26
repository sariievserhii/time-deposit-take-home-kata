package org.ikigaidigital.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * README table {@code timeDeposits}. Assumption: names are mapped to snake_case ({@code time_deposits},
 * {@code plan_type}) because PostgreSQL folds unquoted identifiers to lower case, and camelCase would force
 * quoting everywhere. The columns and types are the ones the README asks for.
 * <p>
 * Lombok: only {@code @Getter}, a single {@code @Setter} and the protected no-arg constructor JPA needs. Deliberately
 * no {@code @Data}, {@code @EqualsAndHashCode} or {@code @ToString}: they read every field, including the lazy
 * {@code withdrawals} collection, which would trigger extra queries or a {@code LazyInitializationException}, and
 * field-based equals/hashCode breaks entities in hash-based collections when the id is assigned.
 */
@Entity
@Table(name = "time_deposits")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // for JPA
public class TimeDepositJpaEntity {

    @Id
    private Integer id;

    @Column(name = "plan_type", nullable = false)
    private String planType;

    @Column(nullable = false)
    private Integer days;

    /** The only mutable field: the balance update writes it. Everything else is read-only for the app. */
    @Setter
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balance;

    /**
     * Optimistic locking. Hibernate adds {@code AND version = ?} to every UPDATE and increments the version, so a
     * write based on a stale read fails with an optimistic-lock exception instead of overwriting a newer balance.
     * The balance update additionally takes a pessimistic row lock (see {@code findAllForUpdate}), so for that path
     * the version check never fires. It protects every other writer, present or future.
     */
    @Version
    @Column(nullable = false)
    private Long version;

    @OneToMany(mappedBy = "timeDeposit", fetch = FetchType.LAZY)
    @OrderBy("id")
    private List<WithdrawalJpaEntity> withdrawals = new ArrayList<>();
}
