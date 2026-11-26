package org.ikigaidigital;

import org.springframework.jdbc.core.JdbcTemplate;

/** Shared test-data helpers for the Testcontainers integration tests. */
public final class TestDatabase {

    private TestDatabase() {
    }

    /**
     * Deletes all rows, children first so no foreign key is violated: interest postings, then withdrawals, then
     * deposits. When a table is added to the schema, add it here, in the right order, once.
     */
    public static void deleteAll(JdbcTemplate jdbc) {
        jdbc.update("DELETE FROM interest_postings");
        jdbc.update("DELETE FROM withdrawals");
        jdbc.update("DELETE FROM time_deposits");
    }
}
