package org.ikigaidigital.application.service;

import lombok.AllArgsConstructor;
import org.ikigaidigital.TimeDeposit;
import org.ikigaidigital.TimeDepositCalculator;
import org.ikigaidigital.application.port.out.InterestPostingsPort;
import org.ikigaidigital.application.port.out.LoadTimeDepositsPort;
import org.ikigaidigital.application.port.out.UpdateTimeDepositBalancesPort;
import org.ikigaidigital.domain.model.InterestPosting;
import org.ikigaidigital.domain.model.TimeDepositSummary;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

/** Plain unit test with in-memory port fakes and a fixed clock; no Spring context or database. */
class UpdateAllBalancesServiceTest {

    private static final Instant JAN_15 = Instant.parse("2026-01-15T10:00:00Z");
    private static final Instant JAN_31 = Instant.parse("2026-01-31T23:59:59Z");
    private static final Instant FEB_01 = Instant.parse("2026-02-01T00:00:00Z");

    private final InMemoryPorts ports = new InMemoryPorts(List.of(
            new TimeDeposit(1, "basic", 1234567.00, 45),
            new TimeDeposit(2, "premium", 20000.00, 45))); // premium before day 46: no interest

    @Test
    void appliesInterestPersistsBalancesAndRecordsAnAuditPostingPerDeposit() {
        serviceAt(JAN_15).updateAllBalances();

        assertThat(ports.deposits).extracting(TimeDeposit::getId, TimeDeposit::getBalance)
                .containsExactly(tuple(1, 1235595.81), tuple(2, 20000.00));
        assertThat(ports.postings)
                .extracting(InterestPosting::timeDepositId, InterestPosting::period, InterestPosting::balanceBefore,
                        InterestPosting::balanceAfter, InterestPosting::postedAt)
                .containsExactly(
                        tuple(1, YearMonth.of(2026, 1), new BigDecimal("1234567.00"), new BigDecimal("1235595.81"), JAN_15),
                        tuple(2, YearMonth.of(2026, 1), new BigDecimal("20000.00"), new BigDecimal("20000.00"), JAN_15));
        assertThat(ports.postings.get(0).interest()).isEqualByComparingTo("1028.81");
    }

    @Test
    void isIdempotentWithinTheSameMonth() {
        serviceAt(JAN_15).updateAllBalances();
        serviceAt(JAN_31).updateAllBalances(); // retry / double click, same month

        assertThat(ports.deposits.get(0).getBalance()).isEqualTo(1235595.81);
        assertThat(ports.postings).hasSize(2);
    }

    @Test
    void postsAgainInTheNextMonth() {
        serviceAt(JAN_15).updateAllBalances();
        serviceAt(FEB_01).updateAllBalances();

        // 1235595.81 * 0.01 / 12 = 1029.66...
        assertThat(ports.deposits.get(0).getBalance()).isEqualTo(1236625.47);
        assertThat(ports.postings).extracting(InterestPosting::period)
                .containsExactly(YearMonth.of(2026, 1), YearMonth.of(2026, 1), YearMonth.of(2026, 2), YearMonth.of(2026, 2));
    }

    @Test
    void onlyPostsDepositsThatAreNotYetPostedThisMonth() {
        ports.postings.add(new InterestPosting(1, YearMonth.of(2026, 1), BigDecimal.ONE, BigDecimal.ONE, JAN_15));

        serviceAt(JAN_31).updateAllBalances();

        assertThat(ports.deposits.get(0).getBalance()).as("already posted, untouched").isEqualTo(1234567.00);
        assertThat(ports.postings).extracting(InterestPosting::timeDepositId).containsExactly(1, 2);
    }

    private UpdateAllBalancesService serviceAt(Instant now) {
        return new UpdateAllBalancesService(ports, ports, ports, new TimeDepositCalculator(), Clock.fixed(now, ZoneOffset.UTC));
    }

    /** Behaves like the database: loads copies, and only persisted balances survive between runs. */
    @AllArgsConstructor
    private static final class InMemoryPorts implements LoadTimeDepositsPort, UpdateTimeDepositBalancesPort, InterestPostingsPort {
        private List<TimeDeposit> deposits; // reassigned by updateBalances, like rows in a table
        private final List<InterestPosting> postings = new ArrayList<>(); // initialised, so not a constructor parameter

        @Override
        public List<TimeDeposit> loadAllForUpdate() {
            return deposits.stream()
                    .map(d -> new TimeDeposit(d.getId(), d.getPlanType(), d.getBalance(), d.getDays()))
                    .toList();
        }

        @Override
        public List<TimeDepositSummary> loadAllWithWithdrawals() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void updateBalances(List<TimeDeposit> updated) {
            deposits = deposits.stream()
                    .map(d -> updated.stream().filter(u -> u.getId() == d.getId()).findFirst().orElse(d))
                    .toList();
        }

        @Override
        public Set<Integer> findDepositIdsPostedIn(YearMonth period) {
            return postings.stream().filter(p -> p.period().equals(period))
                    .map(InterestPosting::timeDepositId).collect(Collectors.toSet());
        }

        @Override
        public void saveAll(List<InterestPosting> newPostings) {
            postings.addAll(newPostings);
        }
    }
}
