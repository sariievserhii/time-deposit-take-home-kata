package org.ikigaidigital.architecture.fixture;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

/**
 * DELIBERATELY UNSAFE code, used only to prove that {@code SqlInjectionGuardTest}'s rules detect injection-prone
 * patterns. It is never executed or registered as a bean (no {@code @Component}, and the repository interface does
 * not extend a Spring Data {@code Repository}). Never copy this into production code.
 */
@RequiredArgsConstructor
public class SqlInjectionProneExamples {

    private final JdbcTemplate jdbc;
    private final EntityManager entityManager;

    /** Classic injection: user input concatenated into SQL. */
    public List<?> rawJdbc(String planType) {
        return jdbc.queryForList("SELECT * FROM time_deposits WHERE plan_type = '" + planType + "'");
    }

    /** Same problem through JPA: a query string assembled at runtime. */
    public List<?> stringQuery(String planType) {
        return entityManager.createNativeQuery("SELECT * FROM time_deposits WHERE plan_type = '" + planType + "'")
                .getResultList();
    }

    public interface NativeQueryRepository {
        @Query(value = "SELECT * FROM time_deposits", nativeQuery = true)
        List<Object[]> nativeQuery();
    }
}
