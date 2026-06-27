package com.zsubera.jpa.update;

import static org.junit.jupiter.api.Assertions.*;

import com.zsubera.jpa.update.EntityFieldExtractor.EntityFieldValue;
import java.util.List;
import org.junit.jupiter.api.Test;

class MysqlDialectTest {

    private final MysqlDialect dialect = new MysqlDialect();

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
    void escapeIdentifier_withSpecialChars() {
        assertEquals("`my-table`", dialect.escapeIdentifier("my-table"));
    }

    @Test
    void buildUpsertSql_withUpdateColumns() {
        List<String> insertCols = List.of("id", "name", "email");
        List<EntityFieldValue> fieldValues = List.of(new EntityFieldValue("id", "id", 1L),
            new EntityFieldValue("name", "name", "John"), new EntityFieldValue("email", "email", "john@example.com"));
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
    void buildUpsertSql_withoutUpdateColumns() {
        List<String> insertCols = List.of("id", "name");
        List<EntityFieldValue> fieldValues =
            List.of(new EntityFieldValue("id", "id", 1L), new EntityFieldValue("name", "name", "John"));
        List<String> conflictCols = List.of("id");
        List<String> updateCols = List.of();

        SqlWithParams result = dialect.buildUpsertSql("users", insertCols, fieldValues, conflictCols, updateCols);

        assertFalse(result.sql().contains("INSERT IGNORE"));
        assertTrue(result.sql().contains("ON DUPLICATE KEY UPDATE"));
        assertTrue(result.sql().contains("`id` = VALUES(`id`)"));
    }

    @Test
    void buildUpsertSql_multipleConflictColumns() {
        List<String> insertCols = List.of("id", "name", "region");
        List<EntityFieldValue> fieldValues = List.of(new EntityFieldValue("id", "id", 1L),
            new EntityFieldValue("name", "name", "John"), new EntityFieldValue("region", "region", "US"));
        List<String> conflictCols = List.of("id", "region");
        List<String> updateCols = List.of("name");

        SqlWithParams result = dialect.buildUpsertSql("users", insertCols, fieldValues, conflictCols, updateCols);

        assertTrue(result.sql().contains("ON DUPLICATE KEY UPDATE"));
        assertTrue(result.sql().contains("`name` = VALUES(`name`)"));
    }

    @Test
    void buildInsertPart_columnCountMismatch_throws() {
        List<String> cols = List.of("id", "name");
        List<EntityFieldValue> values = List.of(new EntityFieldValue("id", "id", 1L));

        assertThrows(IllegalArgumentException.class,
            () -> DialectStrategy.buildInsertPart("`users`", dialect, cols, values));
    }

    @Test
    void buildInsertPart_generatesCorrectSql() {
        List<String> cols = List.of("id", "name");
        List<EntityFieldValue> values =
            List.of(new EntityFieldValue("id", "id", 1L), new EntityFieldValue("name", "name", "John"));

        SqlWithParams result = DialectStrategy.buildInsertPart("`users`", dialect, cols, values);

        assertEquals("INSERT INTO `users` (`id`, `name`) VALUES (?, ?)", result.sql());
        assertEquals(2, result.params().size());
        assertEquals(1L, result.params().get(0));
        assertEquals("John", result.params().get(1));
    }
}
