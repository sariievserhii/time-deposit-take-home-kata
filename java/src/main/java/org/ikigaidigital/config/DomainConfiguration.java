package org.ikigaidigital.config;

import org.ikigaidigital.TimeDepositCalculator;
import org.ikigaidigital.domain.interest.BasicInterestPolicy;
import org.ikigaidigital.domain.interest.InterestPolicy;
import org.ikigaidigital.domain.interest.PremiumInterestPolicy;
import org.ikigaidigital.domain.interest.StudentInterestPolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.util.List;

/**
 * Registers the framework-free domain objects as beans, so the domain itself has no Spring dependency.
 * To add a plan, add a {@code PlanType} constant and declare another {@link InterestPolicy} bean.
 * The calculator picks up every policy bean.
 */
@Configuration(proxyBeanMethods = false)
class DomainConfiguration {

    @Bean
    BasicInterestPolicy basicInterestPolicy() {
        return new BasicInterestPolicy();
    }

    @Bean
    StudentInterestPolicy studentInterestPolicy() {
        return new StudentInterestPolicy();
    }

    @Bean
    PremiumInterestPolicy premiumInterestPolicy() {
        return new PremiumInterestPolicy();
    }

    @Bean
    TimeDepositCalculator timeDepositCalculator(List<InterestPolicy> interestPolicies) {
        return new TimeDepositCalculator(interestPolicies);
    }

    /**
     * Decides which month an interest run belongs to. Assumption: months follow UTC, the zone the bank books in.
     * Injected (not {@code Instant.now()}) so tests can pin the date.
     */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
