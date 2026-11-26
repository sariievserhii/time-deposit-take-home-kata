package org.ikigaidigital.config.diagnostics;

import org.springframework.boot.diagnostics.FailureAnalysis;
import org.springframework.boot.diagnostics.FailureAnalyzer;
import org.springframework.core.Ordered;

/**
 * Replaces Spring Boot's generic "Failed to configure a DataSource" startup error with instructions for this
 * project. The default configuration deliberately has no database (fail closed, like production), so starting
 * {@code TimeDepositKataApplication} directly without the dev profile is the most common first-run mistake.
 * Registered in META-INF/spring.factories, ahead of Boot's own analyzer.
 */
public class MissingDatabaseFailureAnalyzer implements FailureAnalyzer, Ordered {

    /** Boot 4's DataSourceProperties.DataSourceBeanCreationException is package-private, so match it by name. */
    private static final String MISSING_DATASOURCE_EXCEPTION = "DataSourceBeanCreationException";

    @Override
    public FailureAnalysis analyze(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (MISSING_DATASOURCE_EXCEPTION.equals(cause.getClass().getSimpleName())) {
                return new FailureAnalysis(DESCRIPTION, ACTION, cause);
            }
        }
        return null;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    private static final String DESCRIPTION = """
            No database is configured. This is deliberate: the default configuration has no database, no API users \
            and no Swagger, as in production, so every environment must provide them explicitly.""";

    private static final String ACTION = """
            To run locally, use one of:
              1. ./mvnw spring-boot:test-run   (from java/; PostgreSQL in Docker via Testcontainers, dev profile)
              2. IntelliJ: run/debug "Time Deposit (Testcontainers, dev profile)",
                 or run TestTimeDepositKataApplication (src/test/java)
              3. The dev profile with your own PostgreSQL on port 55432 (see java/README.md, "Run it", Option B)
            In other environments, set SPRING_DATASOURCE_URL, SPRING_DATASOURCE_USERNAME and SPRING_DATASOURCE_PASSWORD.""";
}
