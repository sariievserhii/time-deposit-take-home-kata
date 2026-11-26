package org.ikigaidigital.architecture;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.ikigaidigital.architecture.fixture.SqlInjectionProneExamples;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

import java.util.Set;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards against SQL injection being introduced later. Production code may reach the database only through static
 * JPQL ({@code @Query} on repository methods, which Java forces to be a compile-time constant) with bind
 * parameters, or through Spring Data's generated, parameterised queries. Everything that would allow building SQL
 * from strings at runtime is forbidden. See README, "Security review".
 * <p>
 * Each rule is also run against {@link SqlInjectionProneExamples} (test code, deliberately unsafe), to prove the rule
 * actually detects what it claims to.
 */
class SqlInjectionGuardTest {

    private static final JavaClasses PRODUCTION_CODE = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("org.ikigaidigital");

    private static final JavaClasses UNSAFE_EXAMPLES = new ClassFileImporter()
            .importClasses(SqlInjectionProneExamples.class, SqlInjectionProneExamples.NativeQueryRepository.class);

    /** Plain JDBC and JdbcTemplate run whatever SQL string they are given. */
    static final ArchRule NO_RAW_JDBC = noClasses()
            .should().dependOnClassesThat().haveFullyQualifiedName("org.springframework.jdbc.core.JdbcTemplate")
            .orShould().dependOnClassesThat().haveFullyQualifiedName("org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate")
            .orShould().dependOnClassesThat().haveFullyQualifiedName("org.springframework.jdbc.core.simple.JdbcClient")
            .orShould().dependOnClassesThat().haveFullyQualifiedName("java.sql.Statement")
            .because("raw JDBC runs arbitrary SQL strings; use static JPQL with bind parameters (SQL injection guard)");

    /** EntityManager/Session query factories take a query string that could be assembled at runtime. */
    static final ArchRule NO_STRING_QUERY_FACTORIES = noClasses()
            .should().callMethodWhere(queryFactoryOnEntityManagerOrSession())
            .because("query strings built at runtime can contain user input; use @Query with bind parameters (SQL injection guard)");

    /** Native SQL in @Query bypasses JPQL's entity model; keep all queries in JPQL. */
    static final ArchRule NO_NATIVE_QUERY_ANNOTATIONS = methods()
            .that().areAnnotatedWith(Query.class)
            .should(notBeNativeQueries())
            .because("native SQL is not allowed in production code (SQL injection guard)");

    @Test
    void productionCodeUsesNoRawJdbc() {
        NO_RAW_JDBC.check(PRODUCTION_CODE);
    }

    @Test
    void productionCodeBuildsNoQueriesFromStrings() {
        NO_STRING_QUERY_FACTORIES.check(PRODUCTION_CODE);
    }

    @Test
    void productionCodeHasNoNativeQueries() {
        NO_NATIVE_QUERY_ANNOTATIONS.check(PRODUCTION_CODE);
    }

    @Test
    void theRulesDetectUnsafeCode() {
        assertThat(NO_RAW_JDBC.evaluate(UNSAFE_EXAMPLES).hasViolation()).as("raw JDBC detected").isTrue();
        assertThat(NO_STRING_QUERY_FACTORIES.evaluate(UNSAFE_EXAMPLES).hasViolation()).as("string query detected").isTrue();
        assertThat(NO_NATIVE_QUERY_ANNOTATIONS.evaluate(UNSAFE_EXAMPLES).hasViolation()).as("native @Query detected").isTrue();
    }

    private static DescribedPredicate<JavaMethodCall> queryFactoryOnEntityManagerOrSession() {
        Set<String> factories = Set.of("createNativeQuery", "createQuery", "createSelectionQuery",
                "createMutationQuery", "createNativeMutationQuery");
        // Matched by name: library class hierarchies are not imported (see archunit.properties).
        Set<String> owners = Set.of("jakarta.persistence.EntityManager", "org.hibernate.Session",
                "org.hibernate.StatelessSession", "org.hibernate.SharedSessionContract",
                "org.hibernate.engine.spi.SessionImplementor", "org.hibernate.query.QueryProducer");
        return DescribedPredicate.describe("a query factory on EntityManager or a Hibernate Session",
                call -> factories.contains(call.getName()) && owners.contains(call.getTargetOwner().getName()));
    }

    private static ArchCondition<JavaMethod> notBeNativeQueries() {
        return new ArchCondition<>("not be native queries") {
            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                boolean nativeQuery = method.getAnnotationOfType(Query.class).nativeQuery();
                if (nativeQuery) {
                    events.add(SimpleConditionEvent.violated(method, method.getFullName() + " is a native @Query"));
                }
            }
        };
    }
}
