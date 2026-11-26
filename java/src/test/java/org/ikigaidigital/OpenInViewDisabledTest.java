package org.ikigaidigital;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.orm.jpa.support.OpenEntityManagerInViewFilter;
import org.springframework.orm.jpa.support.OpenEntityManagerInViewInterceptor;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code spring.jpa.open-in-view} must be off in every configuration. With it on, Spring keeps a database session
 * open for the whole web request, including JSON serialisation. Lazy associations then load silently outside the
 * transaction (hidden N+1 queries), and each request holds a pooled connection longer than needed.
 * <p>
 * Checked on the running context, not the config file: Spring Boot registers the interceptor only when the setting is
 * on, so its absence proves the effective value whatever profile, file or environment variable supplied it. The
 * configurations match existing tests, so the cached contexts are reused.
 */
class OpenInViewDisabledTest {

    @Nested
    @SpringBootTest(properties = "spring.liquibase.contexts=test")
    @Import(TestcontainersConfiguration.class)
    class DefaultConfiguration {

        @Autowired
        private ApplicationContext context;

        @Test
        void noOpenSessionInView() {
            assertNoOpenSessionInView(context);
        }
    }

    @Nested
    @SpringBootTest(properties = "spring.liquibase.contexts=test")
    @AutoConfigureMockMvc
    @ActiveProfiles("dev")
    @Import(TestcontainersConfiguration.class)
    class DevProfile {

        @Autowired
        private ApplicationContext context;

        @Test
        void noOpenSessionInView() {
            assertNoOpenSessionInView(context);
        }
    }

    private static void assertNoOpenSessionInView(ApplicationContext context) {
        assertThat(context.getEnvironment().getProperty("spring.jpa.open-in-view")).isEqualTo("false");
        assertThat(context.getBeanNamesForType(OpenEntityManagerInViewInterceptor.class)).isEmpty();
        assertThat(context.getBeanNamesForType(OpenEntityManagerInViewFilter.class)).isEmpty();
    }
}
