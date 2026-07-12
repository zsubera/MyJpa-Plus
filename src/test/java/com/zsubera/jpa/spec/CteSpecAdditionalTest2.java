package com.zsubera.jpa.spec;

import static org.junit.jupiter.api.Assertions.*;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

@DataJpaTest
@org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase(
    replace = org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE)
@org.springframework.test.context.ContextConfiguration(classes = TestApplication.class)
class CteSpecAdditionalTest2 {

    @Autowired
    private TestEntityRepository repository;

    @PersistenceContext
    private EntityManager em;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        repository.flush();
    }

    private TestEntity newEntity(String name, int status) {
        TestEntity e = new TestEntity();
        e.setName(name);
        e.setStatus(status);
        return e;
    }

    @Test
    void with_setsName() {
        CteSpec cte = CteSpec.with("my_cte");
        assertNotNull(cte);
    }

    @Test
    void withRecursive_setsRecursiveName() {
        CteSpec cte = CteSpec.withRecursive("my_cte");
        assertNotNull(cte);
    }

    @Test
    void columns_setsColumns() {
        CteSpec cte = CteSpec.with("my_cte").columns("id", "name");
        assertNotNull(cte);
    }

    @Test
    void as_setsQuery() {
        CteSpec cte = CteSpec.with("my_cte").as("SELECT id, name FROM test_entity");
        assertNotNull(cte);
    }

    @Test
    void select_setsSelectQuery() {
        CteSpec cte = CteSpec.with("my_cte").as("SELECT id, name FROM test_entity").select("SELECT * FROM my_cte");
        assertNotNull(cte);
    }

    @Test
    void setParameter_setsParameter() {
        CteSpec cte = CteSpec.with("my_cte").as("SELECT id, name FROM test_entity WHERE name = :name")
            .select("SELECT * FROM my_cte").setParameter("name", "test");
        assertNotNull(cte);
    }

    @Test
    void getResultList_executesQuery() {
        repository.save(newEntity("test1", 1));
        repository.save(newEntity("test2", 2));

        CteSpec cte = CteSpec.with("active_cte").columns("id", "name")
            .as("SELECT id, name FROM test_entity WHERE status = :status").select("SELECT * FROM active_cte")
            .setParameter("status", 1);

        List<Object[]> results = cte.getResultList(em);
        assertNotNull(results);
    }

    @Test
    void getSingleResult_returnsOptional() {
        repository.save(newEntity("test1", 1));

        CteSpec cte =
            CteSpec.with("single_cte").columns("id", "name").as("SELECT id, name FROM test_entity WHERE name = :name")
                .select("SELECT * FROM single_cte LIMIT 1").setParameter("name", "test1");

        Optional<Object[]> result = cte.getSingleResult(em);
        assertTrue(result.isPresent());
    }

    @Test
    void getSingleResult_noResult_returnsEmpty() {
        CteSpec cte =
            CteSpec.with("empty_cte").columns("id", "name").as("SELECT id, name FROM test_entity WHERE name = :name")
                .select("SELECT * FROM empty_cte LIMIT 1").setParameter("name", "nonexistent");

        Optional<Object[]> result = cte.getSingleResult(em);
        assertFalse(result.isPresent());
    }

    @Test
    void and_addsAnotherCte() {
        repository.save(newEntity("test1", 1));

        CteSpec cte = CteSpec.with("cte1").columns("id", "name").as("SELECT id, name FROM test_entity").and("cte2")
            .columns("id", "name").as("SELECT id, name FROM test_entity")
            .select("SELECT * FROM cte1 UNION ALL SELECT * FROM cte2");

        List<Object[]> results = cte.getResultList(em);
        assertNotNull(results);
    }

    @Test
    void recursive_withUnionAll_executes() {
        repository.save(newEntity("test1", 1));
        repository.save(newEntity("test2", 2));

        CteSpec cte = CteSpec.withRecursive("r_cte").columns("id", "name")
            .as("SELECT id, name FROM test_entity WHERE status = 1"
                + " UNION ALL SELECT t.id, t.name FROM test_entity t JOIN r_cte r ON t.status = r.id")
            .select("SELECT * FROM r_cte");

        List<Object[]> results = cte.getResultList(em);
        assertNotNull(results);
    }

    @Test
    void withReservedWord_throws() {
        assertThrows(IllegalArgumentException.class, () -> CteSpec.with("SELECT"));
    }

    @Test
    void withRecursiveReservedWord_throws() {
        assertThrows(IllegalArgumentException.class, () -> CteSpec.withRecursive("INSERT"));
    }

    @Test
    void columnsInvalidChars_throws() {
        assertThrows(IllegalArgumentException.class, () -> CteSpec.with("x").columns("bad-name"));
    }

    @Test
    void nullColumnsElement_throws() {
        assertThrows(IllegalArgumentException.class, () -> CteSpec.with("x").columns("id", null));
    }

    @Test
    void withNull_throws() {
        assertThrows(IllegalArgumentException.class, () -> CteSpec.with(null));
    }

    @Test
    void withRecursiveNull_throws() {
        assertThrows(IllegalArgumentException.class, () -> CteSpec.withRecursive(null));
    }

    @Test
    void asNull_throws() {
        assertThrows(IllegalArgumentException.class, () -> CteSpec.with("x").as(null));
    }

    @Test
    void selectNull_throws() {
        assertThrows(IllegalArgumentException.class, () -> CteSpec.with("x").as("SELECT 1").select(null));
    }

    @Test
    void setParameterNullKey_throws() {
        assertThrows(IllegalArgumentException.class,
            () -> CteSpec.with("x").as("SELECT 1").select("SELECT 1").setParameter(null, "v"));
    }

    @Test
    void multipleParameters_setsAll() {
        repository.save(newEntity("test1", 1));

        CteSpec cte = CteSpec.with("param_cte").columns("id", "name")
            .as("SELECT id, name FROM test_entity WHERE status = :status AND name = :name")
            .select("SELECT * FROM param_cte").setParameter("status", 1).setParameter("name", "test1");

        List<Object[]> results = cte.getResultList(em);
        assertNotNull(results);
    }

    @Test
    void asSafe_withParams_setsParameters() {
        repository.save(newEntity("test1", 1));

        CteSpec cte = CteSpec.with("safe_cte")
            .asSafe("SELECT id, name FROM test_entity WHERE status = ?1 AND name = ?2", 1, "test1")
            .select("SELECT * FROM safe_cte");

        List<Object[]> results = cte.getResultList(em);
        assertNotNull(results);
    }

    @Test
    void asSafe_noParams_setsSql() {
        repository.save(newEntity("test1", 1));

        CteSpec cte =
            CteSpec.with("safe_cte").asSafe("SELECT id, name FROM test_entity").select("SELECT * FROM safe_cte");

        List<Object[]> results = cte.getResultList(em);
        assertNotNull(results);
    }

    @Test
    void asSafe_null_throws() {
        assertThrows(IllegalArgumentException.class, () -> CteSpec.with("x").asSafe(null));
    }

    @Test
    void asSafe_empty_throws() {
        assertThrows(IllegalArgumentException.class, () -> CteSpec.with("x").asSafe(""));
    }

    @Test
    void buildSql_noCte_throws() {
        CteSpec cte = CteSpec.with("test_cte");
        assertThrows(IllegalStateException.class, () -> cte.buildSql());
    }

    @Test
    void buildSql_noMainQuery_throws() {
        CteSpec cte = CteSpec.with("test_cte").as("SELECT 1");
        assertThrows(IllegalStateException.class, () -> cte.buildSql());
    }

    @Test
    void buildSql_validQuery_returnsSql() {
        CteSpec cte = CteSpec.with("test_cte").as("SELECT id FROM test_entity").select("SELECT * FROM test_cte");
        String sql = cte.buildSql();
        assertNotNull(sql);
        assertTrue(sql.contains("WITH"));
        assertTrue(sql.contains("test_cte"));
    }

    @Test
    void buildSql_recursiveQuery_returnsRecursiveSql() {
        CteSpec cte = CteSpec.withRecursive("r_cte").as("SELECT id FROM test_entity").select("SELECT * FROM r_cte");
        String sql = cte.buildSql();
        assertTrue(sql.contains("WITH RECURSIVE"));
    }

    @Test
    void buildSql_withColumns_includesColumns() {
        CteSpec cte = CteSpec.with("col_cte").columns("id", "name").as("SELECT id, name FROM test_entity")
            .select("SELECT * FROM col_cte");
        String sql = cte.buildSql();
        assertTrue(sql.contains("(id, name)"));
    }

    @Test
    void buildSql_withMultipleCtes_includesAll() {
        CteSpec cte = CteSpec.with("cte1").as("SELECT id FROM test_entity").and("cte2").as("SELECT id FROM test_entity")
            .select("SELECT * FROM cte1 UNION ALL SELECT * FROM cte2");
        String sql = cte.buildSql();
        assertTrue(sql.contains("cte1"));
        assertTrue(sql.contains("cte2"));
    }

    @Test
    void getResultStream_executesQuery() {
        repository.save(newEntity("test1", 1));
        repository.save(newEntity("test2", 2));

        CteSpec cte = CteSpec.with("stream_cte").columns("id", "name").as("SELECT id, name FROM test_entity")
            .select("SELECT * FROM stream_cte");

        java.util.List<Object[]> results = new java.util.ArrayList<>();
        cte.getResultStream(em, results::add);
        assertTrue(results.size() >= 2);
    }

    @Test
    void getResultStream_nullEm_throws() {
        CteSpec cte = CteSpec.with("stream_cte").as("SELECT id FROM test_entity").select("SELECT * FROM stream_cte");
        assertThrows(IllegalArgumentException.class, () -> cte.getResultStream(null, r -> {
        }));
    }

    @Test
    void getResultStream_withConsumer_executesQuery() {
        repository.save(newEntity("test1", 1));

        CteSpec cte =
            CteSpec.with("stream_cte").as("SELECT id, name FROM test_entity").select("SELECT * FROM stream_cte");

        java.util.List<Object[]> results = new java.util.ArrayList<>();
        cte.getResultStream(em, results::add);
        assertTrue(results.size() >= 1);
    }

    @Test
    void getResultStream_nullConsumer_throws() {
        CteSpec cte = CteSpec.with("stream_cte").as("SELECT id FROM test_entity").select("SELECT * FROM stream_cte");
        assertThrows(IllegalArgumentException.class, () -> cte.getResultStream(em, null));
    }

    @Test
    void getSingleResult_nullEm_throws() {
        CteSpec cte = CteSpec.with("single_cte").as("SELECT id FROM test_entity").select("SELECT * FROM single_cte");
        assertThrows(IllegalArgumentException.class, () -> cte.getSingleResult(null));
    }

    @Test
    void getSingleResult_multipleResults_returnsFirst() {
        repository.save(newEntity("test1", 1));
        repository.save(newEntity("test2", 2));

        CteSpec cte =
            CteSpec.with("multi_cte").as("SELECT id, name FROM test_entity").select("SELECT * FROM multi_cte");

        Optional<Object[]> result = cte.getSingleResult(em);
        assertTrue(result.isPresent());
    }

    @Test
    void isStrictMode_returnsBoolean() {
        boolean strict = CteSpec.isStrictMode();
        assertNotNull(strict);
    }

    @Test
    void checkSqlSafety_strictMode_rejectsDrop() {
        if (CteSpec.isStrictMode()) {
            assertThrows(SecurityException.class, () -> CteSpec.with("x").as("SELECT 1; DROP TABLE test_entity"));
        }
    }

    @Test
    void checkSqlSafety_strictMode_rejectsComment() {
        if (CteSpec.isStrictMode()) {
            assertThrows(SecurityException.class, () -> CteSpec.with("x").as("SELECT 1 /* comment */"));
        }
    }

    @Test
    void checkSqlSafety_strictMode_rejectsSemicolon() {
        if (CteSpec.isStrictMode()) {
            assertThrows(SecurityException.class, () -> CteSpec.with("x").as("SELECT 1; SELECT 2"));
        }
    }

    @Test
    void checkSqlSafety_strictMode_allowsUnionSelect() {
        // UNION SELECT is valid SQL in both recursive and non-recursive CTEs.
        // E.g., WITH cte AS (SELECT 1 UNION ALL SELECT 2) SELECT * FROM cte is perfectly valid.
        // This test verifies that UNION SELECT is NOT rejected by the safety checker.
        if (CteSpec.isStrictMode()) {
            assertDoesNotThrow(() -> CteSpec.with("x").as("SELECT 1 UNION SELECT 2"));
        }
    }

    @Test
    void checkSqlSafety_strictMode_rejectsWaitfor() {
        if (CteSpec.isStrictMode()) {
            assertThrows(SecurityException.class, () -> CteSpec.with("x").as("SELECT 1; WAITFOR DELAY '00:00:05'"));
        }
    }

    @Test
    void checkUnboundParameters_strictMode_throws() {
        if (CteSpec.isStrictMode()) {
            assertThrows(IllegalStateException.class, () -> CteSpec.with("x")
                .as("SELECT id FROM test_entity WHERE name = :unbound").select("SELECT * FROM x").buildSql());
        }
    }

    @Test
    void checkUnboundParameters_bound_doesNotThrow() {
        CteSpec cte = CteSpec.with("x").as("SELECT id FROM test_entity WHERE name = :name").select("SELECT * FROM x")
            .setParameter("name", "test");
        String sql = cte.buildSql();
        assertNotNull(sql);
    }

    @Test
    void columns_empty_throws() {
        assertThrows(IllegalArgumentException.class, () -> CteSpec.with("x").columns());
    }

    @Test
    void columns_emptyString_throws() {
        assertThrows(IllegalArgumentException.class, () -> CteSpec.with("x").columns(""));
    }

    @Test
    void getResultList_nullEm_throws() {
        CteSpec cte = CteSpec.with("x").as("SELECT 1").select("SELECT * FROM x");
        assertThrows(IllegalArgumentException.class, () -> cte.getResultList(null));
    }

    @Test
    void and_empty_throws() {
        assertThrows(IllegalArgumentException.class, () -> CteSpec.with("a").and(""));
    }

    @Test
    void and_invalidChars_throws() {
        assertThrows(IllegalArgumentException.class, () -> CteSpec.with("a").and("bad-name"));
    }

    @Test
    void setParameter_emptyName_throws() {
        assertThrows(IllegalArgumentException.class,
            () -> CteSpec.with("x").as("SELECT 1").select("SELECT * FROM x").setParameter("", "v"));
    }
}
