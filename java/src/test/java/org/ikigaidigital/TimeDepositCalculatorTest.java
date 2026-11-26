package org.ikigaidigital;

import org.ikigaidigital.domain.interest.AnnualRateInterestPolicy;
import org.ikigaidigital.domain.interest.BasicInterestPolicy;
import org.ikigaidigital.domain.interest.InterestPolicy;
import org.ikigaidigital.domain.interest.PremiumInterestPolicy;
import org.ikigaidigital.domain.interest.StudentInterestPolicy;
import org.ikigaidigital.domain.model.PlanType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.within;

class TimeDepositCalculatorTest {

    private final TimeDepositCalculator calculator = new TimeDepositCalculator();

    @Test
    void updateBalance_Test() {
        List<TimeDeposit> plans = Arrays.asList(
                new TimeDeposit(1, "basic", 1234567.00, 45)
        );
        calculator.updateBalance(plans);

        // 1234567 * 0.01 / 12 = 1028.8058... -> 1028.81
        assertThat(plans.get(0).getBalance()).isEqualTo(1235595.81);
    }

    @ParameterizedTest(name = "{0}, {1} days, {2} -> {3}")
    @CsvSource({
            // grace period: no interest for the first 30 days, for every plan
            "basic,    30,  10000.00, 10000.00",
            "student,  30,  10000.00, 10000.00",
            "premium,  30,  10000.00, 10000.00",
            // basic: 1% a year from day 31
            "basic,    31,  10000.00, 10008.33",
            // student: 3% a year, only up to day 365
            "student,  31,  10000.00, 10025.00",
            "student, 365,  10000.00, 10025.00",
            "student, 366,  10000.00, 10000.00",
            // premium: 5% a year, only after day 45
            "premium,  45,  10000.00, 10000.00",
            "premium,  46,  10000.00, 10041.67",
            // legacy double rounding is kept on purpose: 18.00 * 0.01 / 12 = 0.015 exactly, but as a double it is
            // 0.01499..., so it rounds to 0.01 (BigDecimal maths would give 0.02). See README, section "Money".
            "basic,    31,     18.00,    18.01",
            // unknown plan types earn nothing
            "gold,    100,  10000.00, 10000.00",
            // codes are case-sensitive, like the legacy planType.equals("basic")
            "BASIC,   100,  10000.00, 10000.00",
    })
    void appliesPlanRules(String planType, int days, double balance, double expected) {
        List<TimeDeposit> deposits = List.of(new TimeDeposit(1, planType, balance, days));

        calculator.updateBalance(deposits);

        assertThat(deposits.get(0).getBalance()).isEqualTo(expected);
    }

    /**
     * Golden master: the refactored calculator must give bit-identical results to the original code across
     * every plan, the day boundaries, and many balances, including ones that stress double rounding.
     */
    @Test
    void behavesExactlyLikeTheLegacyImplementation() {
        String[] planTypes = {"basic", "student", "premium", "unknown"};
        int[] days = {0, 1, 29, 30, 31, 44, 45, 46, 364, 365, 366, 367, 1000};
        List<Double> balances = new ArrayList<>(List.of(
                0.0, 0.01, 0.5, 1.0, 5.99, 99.99, 600.0, 1234.56, 10000.0, 1234567.0, 98765432.1, -500.0));
        Random random = new Random(42);
        for (int i = 0; i < 200; i++) {
            balances.add(Math.round(random.nextDouble() * 10_000_000_00L) / 100.0);
        }

        List<TimeDeposit> actual = new ArrayList<>();
        List<TimeDeposit> expected = new ArrayList<>();
        int id = 0;
        for (String planType : planTypes) {
            for (int day : days) {
                for (double balance : balances) {
                    actual.add(new TimeDeposit(id, planType, balance, day));
                    expected.add(new TimeDeposit(id, planType, balance, day));
                    id++;
                }
            }
        }

        calculator.updateBalance(actual);
        new LegacyTimeDepositCalculator().updateBalance(expected);

        for (int i = 0; i < actual.size(); i++) {
            TimeDeposit a = actual.get(i);
            assertThat(a.getBalance())
                    .as("%s, %d days", a.getPlanType(), a.getDays())
                    .isEqualTo(expected.get(i).getBalance());
        }
    }

    /**
     * Even invalid input must behave exactly as before. A null plan type: no interest up to 30 days. After that, a
     * NullPointerException that aborts the loop, with earlier deposits already updated and later ones untouched.
     */
    @Test
    void nullPlanTypeBehavesExactlyLikeTheLegacyImplementation() {
        for (int days : new int[] {0, 30, 31, 400}) {
            List<TimeDeposit> actual = List.of(
                    new TimeDeposit(1, "basic", 1200.0, 60),
                    new TimeDeposit(2, null, 1000.0, days),
                    new TimeDeposit(3, "basic", 1200.0, 60));
            List<TimeDeposit> expected = List.of(
                    new TimeDeposit(1, "basic", 1200.0, 60),
                    new TimeDeposit(2, null, 1000.0, days),
                    new TimeDeposit(3, "basic", 1200.0, 60));

            Throwable actualError = catchThrowable(() -> calculator.updateBalance(actual));
            Throwable expectedError = catchThrowable(() -> new LegacyTimeDepositCalculator().updateBalance(expected));

            assertThat(actualError == null ? null : actualError.getClass())
                    .as("exception for null plan type at %d days", days)
                    .isEqualTo(expectedError == null ? null : expectedError.getClass());
            assertThat(actual).extracting(TimeDeposit::getBalance)
                    .as("balances after the call, %d days", days)
                    .containsExactlyElementsOf(expected.stream().map(TimeDeposit::getBalance).toList());
        }
    }

    @Test
    void updatesEveryDepositInTheList() {
        List<TimeDeposit> deposits = List.of(
                new TimeDeposit(1, "basic", 1200.0, 60),
                new TimeDeposit(2, "student", 1200.0, 60),
                new TimeDeposit(3, "premium", 1200.0, 60));

        calculator.updateBalance(deposits);

        assertThat(deposits).extracting(TimeDeposit::getBalance).containsExactly(1201.0, 1203.0, 1205.0);
    }

    @Test
    void appliesWhicheverPoliciesItIsGivenWithoutChangingTheCalculator() {
        InterestPolicy promotionalBasic = new AnnualRateInterestPolicy() {
            @Override
            public PlanType planType() {
                return PlanType.BASIC;
            }

            @Override
            protected double annualRate() {
                return 0.12;
            }
        };
        TimeDepositCalculator custom = new TimeDepositCalculator(List.of(promotionalBasic));
        List<TimeDeposit> deposits = List.of(
                new TimeDeposit(1, "basic", 1000.0, 31),
                new TimeDeposit(2, "premium", 1000.0, 90)); // no policy registered -> no interest

        custom.updateBalance(deposits);

        assertThat(deposits).extracting(TimeDeposit::getBalance).containsExactly(1010.0, 1000.0);
    }

    /**
     * Extensibility beyond the annual-rate template: a future, more complex rule implemented directly against the
     * InterestPolicy interface. Here, a tiered premium rate (5% a year on the first 50,000, 6% above it) with the
     * rates supplied from outside (a rate table, configuration, a regulator's publication) instead of hard-coded.
     * The calculator is not changed.
     */
    @Test
    void complexRulesPlugInThroughThePolicyInterfaceWithoutChangingTheCalculator() {
        Map<String, Double> rateTable = Map.of("upTo50k", 0.05, "above50k", 0.06);
        InterestPolicy tieredPremium = new InterestPolicy() {
            @Override
            public PlanType planType() {
                return PlanType.PREMIUM;
            }

            @Override
            public double monthlyInterest(TimeDeposit deposit) {
                if (deposit.getDays() <= 45) {
                    return 0;
                }
                double lowerTier = Math.min(deposit.getBalance(), 50_000);
                double upperTier = Math.max(deposit.getBalance() - 50_000, 0);
                return (lowerTier * rateTable.get("upTo50k") + upperTier * rateTable.get("above50k")) / 12;
            }
        };
        TimeDepositCalculator calculator = new TimeDepositCalculator(
                List.of(new BasicInterestPolicy(), new StudentInterestPolicy(), tieredPremium));
        List<TimeDeposit> deposits = List.of(
                new TimeDeposit(1, "premium", 100_000.0, 90),  // (50,000 * 5% + 50,000 * 6%) / 12 = 458.33
                new TimeDeposit(2, "basic", 1_200.0, 60));     // other plans unaffected: +1.00

        calculator.updateBalance(deposits);

        assertThat(deposits.get(0).getBalance()).isCloseTo(100_458.33, within(0.001));
        assertThat(deposits.get(1).getBalance()).isEqualTo(1_201.0);
    }

    /**
     * Rules compose: a decorator wraps any existing policy, e.g. a regulatory cap on monthly interest, again without
     * changing the calculator or the wrapped policy.
     */
    @Test
    void rulesComposeThroughDecoratorsWithoutChangingTheCalculator() {
        InterestPolicy premium = new PremiumInterestPolicy();
        InterestPolicy cappedPremium = new InterestPolicy() {
            @Override
            public PlanType planType() {
                return premium.planType();
            }

            @Override
            public double monthlyInterest(TimeDeposit deposit) {
                return Math.min(premium.monthlyInterest(deposit), 100.0); // cap: at most 100.00 per month
            }
        };
        TimeDepositCalculator calculator = new TimeDepositCalculator(List.of(cappedPremium));
        List<TimeDeposit> deposits = List.of(
                new TimeDeposit(1, "premium", 1_000_000.0, 90), // uncapped: 4,166.67 -> capped at 100.00
                new TimeDeposit(2, "premium", 12_000.0, 90));   // 50.00, below the cap

        calculator.updateBalance(deposits);

        assertThat(deposits).extracting(TimeDeposit::getBalance).containsExactly(1_000_100.0, 12_050.0);
    }

    /**
     * In the app the calculator is a singleton shared by all request threads. It must hold no per-call state:
     * many threads using one instance at once must give exactly the results of single-threaded use.
     */
    @Test
    void sharedInstanceIsThreadSafe() throws Exception {
        int threads = 16;
        int depositsPerThread = 2_000;
        String[] plans = {"basic", "student", "premium"};
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Boolean>> results = new ArrayList<>();
            for (int t = 0; t < threads; t++) {
                int seed = t;
                results.add(pool.submit(() -> {
                    Random random = new Random(seed);
                    List<TimeDeposit> mine = new ArrayList<>();
                    List<TimeDeposit> expected = new ArrayList<>();
                    for (int i = 0; i < depositsPerThread; i++) {
                        String plan = plans[random.nextInt(plans.length)];
                        int days = random.nextInt(500);
                        double balance = random.nextInt(10_000_000) / 100.0;
                        mine.add(new TimeDeposit(i, plan, balance, days));
                        expected.add(new TimeDeposit(i, plan, balance, days));
                    }
                    new LegacyTimeDepositCalculator().updateBalance(expected);
                    start.await();
                    calculator.updateBalance(mine); // the shared instance
                    for (int i = 0; i < mine.size(); i++) {
                        if (!mine.get(i).getBalance().equals(expected.get(i).getBalance())) {
                            return false;
                        }
                    }
                    return true;
                }));
            }
            start.countDown();
            for (Future<Boolean> result : results) {
                assertThat(result.get()).isTrue();
            }
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void rejectsTwoPoliciesForTheSamePlanType() {
        assertThatThrownBy(() -> new TimeDepositCalculator(List.of(new BasicInterestPolicy(), new BasicInterestPolicy())))
                .isInstanceOf(IllegalStateException.class);
    }
}
