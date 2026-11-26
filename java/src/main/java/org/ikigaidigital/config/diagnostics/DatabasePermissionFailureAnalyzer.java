package org.ikigaidigital.config.diagnostics;

import org.springframework.boot.diagnostics.FailureAnalysis;
import org.springframework.boot.diagnostics.FailureAnalyzer;
import org.springframework.core.Ordered;

import java.sql.SQLException;

/**
 * Explains the startup failure when the database user may not create tables, typically on PostgreSQL 15+, where
 * ordinary users no longer have CREATE on the "public" schema unless they own the database. Liquibase then fails with
 * a misleading "make sure you have read and write permissions for the databasechangelog table".
 * Registered in META-INF/spring.factories, ahead of Boot's own analyzers.
 */
public class DatabasePermissionFailureAnalyzer implements FailureAnalyzer, Ordered {

    /** PostgreSQL SQLSTATE "insufficient_privilege" (e.g. "permission denied for schema public"). */
    static final String INSUFFICIENT_PRIVILEGE = "42501";

    @Override
    public FailureAnalysis analyze(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql && INSUFFICIENT_PRIVILEGE.equals(sql.getSQLState())) {
                return new FailureAnalysis(
                        "The database user may not create the schema: " + sql.getMessage() + System.lineSeparator()
                                + "On PostgreSQL 15+ ordinary users can't create tables in the \"public\" schema unless "
                                + "they own the database. The app's user needs to own its database so Liquibase can "
                                + "create its tables, the sequence and its own changelog tables.",
                        ACTION, cause);
            }
        }
        return null;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    private static final String ACTION = """
            Either use a database the app's user owns, e.g. the Docker container from java/README.md ("Run it", \
            Option B, port 55432), or ./mvnw spring-boot:test-run. Or, as a PostgreSQL superuser, make the user the owner:
              ALTER DATABASE time_deposit_kata_db OWNER TO time_deposit_kata_user;
              \\c time_deposit_kata_db
              ALTER SCHEMA public OWNER TO time_deposit_kata_user;
            If an earlier start left databasechangelog tables owned by someone else, drop and recreate the database:
              DROP DATABASE time_deposit_kata_db;
              CREATE DATABASE time_deposit_kata_db OWNER time_deposit_kata_user;
            (See java/README.md, "Troubleshooting".)""";
}
