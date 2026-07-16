package com.zsubera.jpa.spec;

import static org.junit.jupiter.api.Assertions.*;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

@DataJpaTest
@org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase(
    replace = org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE)
class CteSpecTest {

    @PersistenceContext
    private EntityManager em;

    @Test
    void testBasicCte() {
        TestEntity e1 = new TestEntity();
        e1.setName("active_user");
        e1.setStatus(1);
        em.persist(e1);

        TestEntity e2 = new TestEntity();
        e2.setName("inactive_user");
        e2.setStatus(0);
        em.persist(e2);
        em.flush();

        List<Object[]> results =
            CteSpec.with("active").columns("id", "name").as("SELECT id, name FROM test_entity WHERE status = 1")
                .select("SELECT id, name FROM active").getResultList(em);

        assertEquals(1, results.size());
        assertEquals("active_user", results.get(0)[1]);
    }

    @Test
    void testCteWithParameters() {
        TestEntity e1 = new TestEntity();
        e1.setName("Alice");
        e1.setStatus(1);
        em.persist(e1);

        TestEntity e2 = new TestEntity();
        e2.setName("Bob");
        e1.setStatus(2);
        em.persist(e2);
        em.flush();

        List<Object[]> results =
            CteSpec.with("filtered").as("SELECT id, name, status FROM test_entity WHERE status > :minStatus")
                .select("SELECT * FROM filtered WHERE name = :name").setParameter("minStatus", 0)
                .setParameter("name", "Alice").getResultList(em);

        assertEquals(1, results.size());
        assertEquals("Alice", results.get(0)[1]);
    }

    @Test
    void testRecursiveCte() {
        TestEntity parent = new TestEntity();
        parent.setName("parent");
        parent.setStatus(0);
        em.persist(parent);
        em.flush();

        TestEntity child = new TestEntity();
        child.setName("child");
        child.setStatus(1);
        child.setParent(null);
        em.persist(child);
        em.flush();

        List<Object[]> results = CteSpec.withRecursive("tree").columns("id", "name", "depth")
            .as("SELECT id, name, 0 AS depth FROM test_entity WHERE parent_id IS NULL" + " UNION ALL "
                + "SELECT te.id, te.name, t.depth + 1 FROM test_entity te " + "JOIN tree t ON te.parent_id = t.id")
            .select("SELECT id, name, depth FROM tree ORDER BY depth").getResultList(em);

        assertFalse(results.isEmpty());
    }

    @Test
    void testMultipleCtes() {
        TestEntity e1 = new TestEntity();
        e1.setName("Alice");
        e1.setStatus(1);
        em.persist(e1);
        em.flush();

        List<Object[]> results = CteSpec.with("active").as("SELECT id, name FROM test_entity WHERE status = 1")
            .and("active_count").as("SELECT COUNT(*) AS cnt FROM active")
            .select("SELECT a.name, ac.cnt FROM active a, active_count ac").getResultList(em);

        assertEquals(1, results.size());
        assertEquals("Alice", results.get(0)[0]);
    }

    @Test
    void testBuildSql() {
        String sql = CteSpec.with("cte1").columns("id", "name").as("SELECT id, name FROM test_entity")
            .select("SELECT * FROM cte1").buildSql();

        assertEquals("WITH cte1(id, name) AS (SELECT id, name FROM test_entity) SELECT * FROM cte1", sql);
    }

    @Test
    void testBuildSqlRecursive() {
        String sql = CteSpec.withRecursive("tree").columns("id", "depth")
            .as("SELECT id, 0 FROM test_entity WHERE parent_id IS NULL"
                + " UNION ALL SELECT te.id, t.depth + 1 FROM test_entity te JOIN tree t ON te.parent_id = t.id")
            .select("SELECT * FROM tree").buildSql();

        assertTrue(sql.startsWith("WITH RECURSIVE "));
    }

    @Test
    void testBuildSqlNoColumns() {
        String sql =
            CteSpec.with("cte1").as("SELECT id, name FROM test_entity").select("SELECT * FROM cte1").buildSql();

        assertEquals("WITH cte1 AS (SELECT id, name FROM test_entity) SELECT * FROM cte1", sql);
    }

    @Test
    void testMultipleCtesBuildSql() {
        String sql = CteSpec.with("a").as("SELECT 1").and("b").as("SELECT 2").select("SELECT * FROM a, b").buildSql();

        assertEquals("WITH a AS (SELECT 1), b AS (SELECT 2) SELECT * FROM a, b", sql);
    }

    @Test
    void testGetSingleResult() {
        TestEntity e1 = new TestEntity();
        e1.setName("solo");
        e1.setStatus(42);
        em.persist(e1);
        em.flush();

        var result = CteSpec.with("one").as("SELECT id, name FROM test_entity WHERE status = 42")
            .select("SELECT id, name FROM one").getSingleResult(em);

        assertTrue(result.isPresent());
        assertEquals("solo", result.get()[1]);
    }

    @Test
    void testGetSingleResultEmpty() {
        var result = CteSpec.with("empty_result").as("SELECT id, name FROM test_entity WHERE status = -999")
            .select("SELECT * FROM empty_result").getSingleResult(em);

        assertTrue(result.isEmpty());
    }

    @Test
    void testNullCteNameThrows() {
        assertThrows(IllegalArgumentException.class, () -> CteSpec.with(null));
        assertThrows(IllegalArgumentException.class, () -> CteSpec.withRecursive(null));
        assertThrows(IllegalArgumentException.class, () -> CteSpec.with(""));
    }

    @Test
    void testNullSqlThrows() {
        assertThrows(IllegalArgumentException.class, () -> CteSpec.with("x").as(null));
        assertThrows(IllegalArgumentException.class, () -> CteSpec.with("x").as(""));
    }

    @Test
    void testNullSelectThrows() {
        assertThrows(IllegalArgumentException.class, () -> CteSpec.with("x").as("SELECT 1").select(null));
    }

    @Test
    void testNullColumnsThrows() {
        assertThrows(IllegalArgumentException.class, () -> CteSpec.with("x").columns((String[])null));
    }

    @Test
    void testEmptyColumnsThrows() {
        assertThrows(IllegalArgumentException.class, () -> CteSpec.with("x").columns(new String[0]));
    }

    @Test
    void testNullColumnNameThrows() {
        assertThrows(IllegalArgumentException.class, () -> CteSpec.with("x").columns("a", null));
    }

    @Test
    void testBuildSqlWithoutCteSqlThrows() {
        assertThrows(IllegalStateException.class, () -> CteSpec.with("x").select("SELECT 1").buildSql());
    }

    @Test
    void testBuildSqlWithoutSelectThrows() {
        assertThrows(IllegalStateException.class, () -> CteSpec.with("x").as("SELECT 1").buildSql());
    }

    @Test
    void testNullEmThrows() {
        CteSpec spec = CteSpec.with("x").as("SELECT 1").select("SELECT * FROM x");
        assertThrows(IllegalArgumentException.class, () -> spec.getResultList(null));
        assertThrows(IllegalArgumentException.class, () -> spec.getSingleResult(null));
    }

    @Test
    void testNullParameterNameThrows() {
        assertThrows(IllegalArgumentException.class, () -> CteSpec.with("x").as("SELECT 1").setParameter(null, "v"));
    }

    @Test
    void testAndNullCteNameThrows() {
        assertThrows(IllegalArgumentException.class, () -> CteSpec.with("x").and(null));
    }

    @Test
    void testColonInStringLiteral_notFlaggedAsUnboundParam() {
        // Verify :test inside a string literal does not trigger unbound parameter error
        // SQL: SELECT * FROM t WHERE url = 'http://example.com:test'
        String sql = "SELECT * FROM t WHERE url = 'http://example.com:test'";
        CteSpec spec = CteSpec.with("cte").as(sql).select("SELECT * FROM cte");
        // Should not throw - the :test is inside a string literal, not a named parameter
        String built = spec.buildSql();
        assertTrue(built.contains("http://example.com:test"), "String literal should be preserved in SQL");
    }

    @Test
    void testColonInEscapedStringLiteral_notFlagged() {
        // Verify escaped single quotes ('') don't break the string-stripping logic
        String sql = "SELECT * FROM t WHERE val = 'it''s a :test'";
        CteSpec spec = CteSpec.with("cte").as(sql).select("SELECT * FROM cte");
        String built = spec.buildSql();
        assertTrue(built.contains("it''s a :test"), "Escaped string literal should be preserved");
    }

    @Test
    void testSingleQuoteRegex_detectsInjectionInMismatchedQuotes() {
        // The old atomic-group regex '(?>[^'\\]|\\\\.|'')* would consume injection content
        // between mismatched quotes, hiding DROP/semicolon from heuristic checks.
        // The fixed regex '(?:[^'\\]|\\\\.|'')*' matches complete string literals only.
        String injectionSql = "SELECT * FROM users WHERE name = '' OR 1=1; DROP TABLE users; --'";
        assertThrows(SecurityException.class,
            () -> CteSpec.with("cte").as(injectionSql).select("SELECT * from cte").buildSql(),
            "Injection pattern between mismatched quotes should be detected");
    }

    @Test
    void testSingleQuoteRegex_validStringLiteralsStillStripped() {
        // Complete string literals (opening ' through closing ') should still be stripped
        // to avoid false positives from keywords inside strings.
        String sql = "SELECT * FROM t WHERE name = 'DROP' AND status = 'DELETE'";
        CteSpec spec = CteSpec.with("cte").as(sql).select("SELECT * FROM cte");
        // Should not throw - DROP and DELETE are inside complete string literals
        String built = spec.buildSql();
        assertTrue(built.contains("'DROP'"), "String literal should be preserved");
    }
}
