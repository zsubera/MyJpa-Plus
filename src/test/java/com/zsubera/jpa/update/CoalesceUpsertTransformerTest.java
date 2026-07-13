package com.zsubera.jpa.update;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * CoalesceUpsertTransformer 单元测试。
 *
 * <p>验证 JSqlParser AST 后处理能正确将
 * {@code SET col = EXCLUDED.col} 替换为 {@code SET col = COALESCE(EXCLUDED.col, col)}。
 */
class CoalesceUpsertTransformerTest {

    // ==================== PostgreSQL ====================

    @Test
    void postgresql_singleRow_coalesceApplied() {
        String sql = "INSERT INTO \"t\" (\"id\", \"name\", \"street\", \"city\") " + "VALUES (1, 'Alice', NULL, NULL) "
            + "ON CONFLICT (\"id\") DO UPDATE SET "
            + "\"name\" = EXCLUDED.\"name\", \"street\" = EXCLUDED.\"street\", \"city\" = EXCLUDED.\"city\"";

        String result = CoalesceUpsertTransformer.applyCoalesce(sql, Set.of("street", "city"));

        assertTrue(result.contains("COALESCE(EXCLUDED.\"street\", \"street\")"),
            "street should be wrapped in COALESCE");
        assertTrue(result.contains("COALESCE(EXCLUDED.\"city\", \"city\")"), "city should be wrapped in COALESCE");
        assertTrue(result.contains("\"name\" = EXCLUDED.\"name\""), "name should NOT be wrapped in COALESCE");
    }

    @Test
    void postgresql_multiRow_coalesceApplied() {
        String sql = "INSERT INTO \"t\" (\"id\", \"name\", \"street\", \"city\") "
            + "VALUES (1, 'Alice', NULL, NULL), (2, 'Bob', '123 Main St', 'NYC') "
            + "ON CONFLICT (\"id\") DO UPDATE SET "
            + "\"name\" = EXCLUDED.\"name\", \"street\" = EXCLUDED.\"street\", \"city\" = EXCLUDED.\"city\"";

        String result = CoalesceUpsertTransformer.applyCoalesce(sql, Set.of("street", "city"));

        assertTrue(result.contains("COALESCE(EXCLUDED.\"street\", \"street\")"));
        assertTrue(result.contains("COALESCE(EXCLUDED.\"city\", \"city\")"));
        // name should NOT be wrapped in COALESCE
        assertTrue(result.contains("\"name\" = EXCLUDED.\"name\""));
        assertFalse(result.contains("COALESCE(EXCLUDED.\"name\""), "name should not be wrapped in COALESCE");
    }

    @Test
    void postgresql_noCoalesceColumns_sqlUnchanged() {
        String sql = "INSERT INTO \"t\" (\"id\", \"name\") " + "VALUES (1, 'Alice') "
            + "ON CONFLICT (\"id\") DO UPDATE SET \"name\" = EXCLUDED.\"name\"";

        String result = CoalesceUpsertTransformer.applyCoalesce(sql, Set.of());

        assertEquals(sql, result, "SQL should be unchanged when coalesceColumns is empty");
    }

    // ==================== MySQL ====================

    /**
     * MySQL AS new 语法在 JSqlParser 5.3 中不被支持，
     * 当 coalesceColumns 非空时抛出异常（防止数据损坏）。
     */
    @Test
    void mysql_withAlias_throwsWhenCoalesceNeeded() {
        String sql = "INSERT INTO `t` (`id`, `name`, `street`, `city`) "
            + "VALUES (1, 'Alice', NULL, NULL), (2, 'Bob', '123 Main St', 'NYC') " + "AS new ON DUPLICATE KEY UPDATE "
            + "`name` = new.`name`, `street` = new.`street`, `city` = new.`city`";

        assertThrows(com.zsubera.jpa.exception.MyJpaPlusException.class,
            () -> CoalesceUpsertTransformer.applyCoalesce(sql, Set.of("street", "city")),
            "Should throw when COALESCE is needed but SQL cannot be parsed (prevents data corruption)");
    }

    @Test
    void mysql_withoutAlias_coalesceApplied() {
        String sql = "INSERT INTO `t` (`id`, `name`, `street`, `city`) "
            + "VALUES (1, 'Alice', NULL, NULL), (2, 'Bob', '123 Main St', 'NYC') " + "ON DUPLICATE KEY UPDATE "
            + "`name` = VALUES(`name`), `street` = VALUES(`street`), `city` = VALUES(`city`)";

        String result = CoalesceUpsertTransformer.applyCoalesce(sql, Set.of("street", "city"));

        assertTrue(result.contains("COALESCE(VALUES(`street`), `street`)"),
            "street should be wrapped in COALESCE with VALUES");
        assertTrue(result.contains("COALESCE(VALUES(`city`), `city`)"),
            "city should be wrapped in COALESCE with VALUES");
        assertTrue(result.contains("`name` = VALUES(`name`)"), "name should NOT be wrapped in COALESCE");
    }

    // ==================== 边界情况 ====================

    @Test
    void emptyCoalesceColumns_returnsOriginalSql() {
        String sql = "INSERT INTO t (id, name) VALUES (1, 'test')";
        String result = CoalesceUpsertTransformer.applyCoalesce(sql, Set.of());
        assertEquals(sql, result);
    }

    @Test
    void nullCoalesceColumns_returnsOriginalSql() {
        String sql = "INSERT INTO t (id, name) VALUES (1, 'test')";
        String result = CoalesceUpsertTransformer.applyCoalesce(sql, null);
        assertEquals(sql, result);
    }

    @Test
    void nonInsertStatement_returnsOriginalSql() {
        String sql = "SELECT * FROM t WHERE id = 1";
        String result = CoalesceUpsertTransformer.applyCoalesce(sql, Set.of("name"));
        assertEquals(sql, result, "Non-INSERT SQL should be returned unchanged");
    }

    @Test
    void malformedSql_throwsWhenCoalesceColumnsNonEmpty() {
        String sql = "THIS IS NOT VALID SQL !!!";
        assertThrows(com.zsubera.jpa.exception.MyJpaPlusException.class,
            () -> CoalesceUpsertTransformer.applyCoalesce(sql, Set.of("name")),
            "Should throw when COALESCE is needed but SQL cannot be parsed");
    }

    @Test
    void malformedSql_noCoalesceColumns_returnsOriginalSql() {
        String sql = "THIS IS NOT VALID SQL !!!";
        String result = CoalesceUpsertTransformer.applyCoalesce(sql, Set.of());
        assertEquals(sql, result, "Malformed SQL with no coalesce columns should be returned unchanged");
    }

    @Test
    void doNothingConflict_returnsOriginalSql() {
        String sql = "INSERT INTO t (id, name) VALUES (1, 'test') " + "ON CONFLICT (id) DO NOTHING";
        String result = CoalesceUpsertTransformer.applyCoalesce(sql, Set.of("name"));
        assertEquals(sql, result, "DO NOTHING should not be transformed");
    }
}
