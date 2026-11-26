package org.ikigaidigital.config.diagnostics;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.boot.diagnostics.FailureAnalysis;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

/** Unit tests; the real startup failures are checked by hand (see README, "Troubleshooting"). */
class SetupFailureAnalyzersTest {

    /** Stand-in with the same simple name as Boot's package-private DataSourceBeanCreationException. */
    static class DataSourceBeanCreationException extends BeanCreationException {
        DataSourceBeanCreationException() {
            super("Failed to determine a suitable driver class");
        }
    }

    @Test
    void missingDatabaseIsExplainedWithLocalRunInstructions() {
        Throwable failure = new BeanCreationException("entityManagerFactory",
                "dependency failed", new BeanCreationException("dataSource", "x", new DataSourceBeanCreationException()));

        FailureAnalysis analysis = new MissingDatabaseFailureAnalyzer().analyze(failure);

        assertThat(analysis).isNotNull();
        assertThat(analysis.getDescription()).contains("No database is configured").contains("deliberate");
        assertThat(analysis.getAction()).contains("./mvnw spring-boot:test-run").contains("TestTimeDepositKataApplication")
                .contains("SPRING_DATASOURCE_URL");
    }

    @Test
    void missingPermissionOnPublicSchemaIsExplainedWithTheFix() {
        Throwable failure = new RuntimeException("liquibase failed",
                new SQLException("ERROR: permission denied for schema public", "42501"));

        FailureAnalysis analysis = new DatabasePermissionFailureAnalyzer().analyze(failure);

        assertThat(analysis).isNotNull();
        assertThat(analysis.getDescription()).contains("permission denied for schema public").contains("PostgreSQL 15+");
        assertThat(analysis.getAction()).contains("ALTER DATABASE time_deposit_kata_db OWNER TO time_deposit_kata_user");
    }

    @Test
    void unrelatedFailuresAreLeftToSpringBoot() {
        Throwable unrelated = new BeanCreationException("other", "boom", new SQLException("connection refused", "08001"));

        assertThat(new MissingDatabaseFailureAnalyzer().analyze(unrelated)).isNull();
        assertThat(new DatabasePermissionFailureAnalyzer().analyze(unrelated)).isNull();
    }
}
