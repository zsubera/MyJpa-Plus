package com.zsubera.jpa.update;

import static org.junit.jupiter.api.Assertions.*;

import com.zsubera.jpa.update.EntityFieldExtractor.EntityFieldValue;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class MysqlDialectTest {

    private final MysqlDialect dialect = new MysqlDialect();

    @AfterEach
    void resetAliasSyntax() {
        dialect.setUseRowAliasSyntax(false);
    }

    @Test
    void name_returnsMysql() {
        assertEquals("mysql", dialect.name());
    }

    @Test
    void escapeIdentifier_simple() {
        assertEquals("`users`", dialect.escapeIdentifier("users"));
    }

    @Test
    void escapeIdentifier_withBacktick() {
        assertEquals("`user``name`", dialect.escapeIdentifier("user`name"));
    }

    @Test
    void escapeIdentifier_withDot() {
        assertEquals("`schema`.`table`", dialect.escapeIdentifier("schema.table"));
    }

    @Test
    void buildUpsertSql_withUpdateColumns() {
        List<String> insertCols = List.of("id", "name", "email");
        List<EntityFieldValue> fieldValues =
            List.of(new EntityFieldValue("id", "id", 1L, true), new EntityFieldValue("name", "name", "John", true),
                new EntityFieldValue("email", "email", "john@example.com", true));
        List<String> conflictCols = List.of("id");
        List<String> updateCols = List.of("name", "email");

        SqlWithParams result = dialect.buildUpsertSql("users", insertCols, fieldValues, conflictCols, updateCols);

        assertTrue(result.sql().contains("INSERT INTO `users`"));
        assertTrue(result.sql().contains("ON DUPLICATE KEY UPDATE"));
        assertTrue(result.sql().contains("`name` = VALUES(`name`)"));
        assertTrue(result.sql().contains("`email` = VALUES(`email`)"));
        assertEquals(3, result.params().size());
    }

    @Test
    void buildUpsertSql_withoutUpdateColumns_warnsNoop() {
        List<String> insertCols = List.of("id", "name");
        List<EntityFieldValue> fieldValues =
            List.of(new EntityFieldValue("id", "id", 1L, true), new EntityFieldValue("name", "name", "John", true));
        List<String> conflictCols = List.of("id");
        List<String> updateCols = List.of();

        SqlWithParams result = dialect.buildUpsertSql("users", insertCols, fieldValues, conflictCols, updateCols);

        assertTrue(result.sql().contains("ON DUPLICATE KEY UPDATE"));
        assertTrue(result.sql().contains("VALUES(`id`)"));
    }

    @Test
    void buildUpsertSql_emptyInsertColumns_throws() {
        assertThrows(com.zsubera.jpa.exception.MyJpaPlusException.class,
            () -> dialect.buildUpsertSql("users", List.of(), List.of(), List.of("id"), List.of()));
    }

    @Test
    void buildUpsertSql_rowAliasSyntax() {
        dialect.setUseRowAliasSyntax(true);
        List<String> insertCols = List.of("id", "name");
        List<EntityFieldValue> fieldValues =
            List.of(new EntityFieldValue("id", "id", 1L, true), new EntityFieldValue("name", "name", "John", true));
        List<String> conflictCols = List.of("id");
        List<String> updateCols = List.of("name");

        SqlWithParams result = dialect.buildUpsertSql("users", insertCols, fieldValues, conflictCols, updateCols);

        assertTrue(result.sql().contains("AS new ON DUPLICATE KEY UPDATE"));
        assertTrue(result.sql().contains("`name` = new.`name`"));
        assertFalse(result.sql().contains("VALUES("));
    }

    @Test
    void buildUpsertSql_rowAliasSyntax_noUpdateColumns() {
        dialect.setUseRowAliasSyntax(true);
        List<String> insertCols = List.of("id");
        List<EntityFieldValue> fieldValues = List.of(new EntityFieldValue("id", "id", 1L, true));
        List<String> conflictCols = List.of("id");
        List<String> updateCols = List.of();

        SqlWithParams result = dialect.buildUpsertSql("users", insertCols, fieldValues, conflictCols, updateCols);

        assertTrue(result.sql().contains("AS new ON DUPLICATE KEY UPDATE"));
        assertTrue(result.sql().contains("`id` = new.`id`"));
    }

    @Test
    void buildBatchUpsertSql_withUpdateColumns() {
        List<String> insertCols = List.of("id", "name");
        List<List<EntityFieldValue>> batch = List.of(
            List.of(new EntityFieldValue("id", "id", 1L, true), new EntityFieldValue("name", "name", "Alice", true)),
            List.of(new EntityFieldValue("id", "id", 2L, true), new EntityFieldValue("name", "name", "Bob", true)));
        List<String> conflictCols = List.of("id");
        List<String> updateCols = List.of("name");

        SqlWithParams result = dialect.buildBatchUpsertSql("users", insertCols, batch, conflictCols, updateCols);

        assertTrue(result.sql().contains("INSERT INTO `users`"));
        assertTrue(result.sql().contains("(?, ?), (?, ?)"));
        assertTrue(result.sql().contains("ON DUPLICATE KEY UPDATE"));
        assertEquals(4, result.params().size());
    }

    @Test
    void buildBatchUpsertSql_emptyUpdateColumns() {
        List<String> insertCols = List.of("id", "name");
        List<List<EntityFieldValue>> batch = List.of(
            List.of(new EntityFieldValue("id", "id", 1L, true), new EntityFieldValue("name", "name", "Alice", true)));
        List<String> conflictCols = List.of("id");
        List<String> updateCols = List.of();

        SqlWithParams result = dialect.buildBatchUpsertSql("users", insertCols, batch, conflictCols, updateCols);

        assertTrue(result.sql().contains("ON DUPLICATE KEY UPDATE"));
        assertTrue(result.sql().contains("VALUES(`id`)"));
    }

    @Test
    void buildBatchUpsertSql_columnCountMismatch_throws() {
        List<String> insertCols = List.of("id", "name");
        List<List<EntityFieldValue>> batch = List.of(List.of(new EntityFieldValue("id", "id", 1L, true)));
        List<String> conflictCols = List.of("id");
        List<String> updateCols = List.of("name");

        assertThrows(com.zsubera.jpa.exception.MyJpaPlusException.class,
            () -> dialect.buildBatchUpsertSql("users", insertCols, batch, conflictCols, updateCols));
    }

    @Test
    void buildBatchUpsertSql_rowAliasSyntax() {
        dialect.setUseRowAliasSyntax(true);
        List<String> insertCols = List.of("id", "name");
        List<List<EntityFieldValue>> batch = List.of(
            List.of(new EntityFieldValue("id", "id", 1L, true), new EntityFieldValue("name", "name", "Alice", true)));
        List<String> conflictCols = List.of("id");
        List<String> updateCols = List.of("name");

        SqlWithParams result = dialect.buildBatchUpsertSql("users", insertCols, batch, conflictCols, updateCols);

        assertTrue(result.sql().contains("AS new"));
        assertTrue(result.sql().contains("`name` = new.`name`"));
    }

    @Test
    void supportsBatchUpsert_true() {
        assertTrue(dialect.supportsBatchUpsert());
    }

    @Test
    void isUseRowAliasSyntax_defaultFalse() {
        assertFalse(dialect.isUseRowAliasSyntax());
    }
}
