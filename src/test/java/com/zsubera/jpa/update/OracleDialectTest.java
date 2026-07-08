package com.zsubera.jpa.update;

import static org.junit.jupiter.api.Assertions.*;

import com.zsubera.jpa.update.EntityFieldExtractor.EntityFieldValue;
import java.util.List;
import org.junit.jupiter.api.Test;

class OracleDialectTest {

    private final OracleDialect dialect = new OracleDialect();

    @Test
    void name_returnsOracle() {
        assertEquals("oracle", dialect.name());
    }

    @Test
    void escapeIdentifier_simple() {
        assertEquals("\"users\"", dialect.escapeIdentifier("users"));
    }

    @Test
    void escapeIdentifier_withDoubleQuote() {
        assertEquals("\"user\"\"name\"", dialect.escapeIdentifier("user\"name"));
    }

    @Test
    void escapeIdentifier_withDot() {
        assertEquals("\"schema\".\"table\"", dialect.escapeIdentifier("schema.table"));
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

        assertTrue(result.sql().contains("MERGE INTO \"users\""));
        assertTrue(result.sql().contains("USING (SELECT"));
        assertTrue(result.sql().contains("FROM DUAL"));
        assertTrue(result.sql().contains("WHEN MATCHED THEN UPDATE SET"));
        assertTrue(result.sql().contains("\"name\" = source.\"name\""));
        assertTrue(result.sql().contains("WHEN NOT MATCHED THEN INSERT"));
        assertEquals(3, result.params().size());
    }

    @Test
    void buildUpsertSql_withoutUpdateColumns() {
        List<String> insertCols = List.of("id", "name");
        List<EntityFieldValue> fieldValues =
            List.of(new EntityFieldValue("id", "id", 1L, true), new EntityFieldValue("name", "name", "John", true));
        List<String> conflictCols = List.of("id");
        List<String> updateCols = List.of();

        SqlWithParams result = dialect.buildUpsertSql("users", insertCols, fieldValues, conflictCols, updateCols);

        assertTrue(result.sql().contains("MERGE INTO"));
        assertFalse(result.sql().contains("WHEN MATCHED THEN UPDATE"));
        assertTrue(result.sql().contains("WHEN NOT MATCHED THEN INSERT"));
    }

    @Test
    void buildUpsertSql_multipleConflictColumns() {
        List<String> insertCols = List.of("id", "region", "name");
        List<EntityFieldValue> fieldValues = List.of(new EntityFieldValue("id", "id", 1L, true),
            new EntityFieldValue("region", "region", "US", true), new EntityFieldValue("name", "name", "John", true));
        List<String> conflictCols = List.of("id", "region");
        List<String> updateCols = List.of("name");

        SqlWithParams result = dialect.buildUpsertSql("users", insertCols, fieldValues, conflictCols, updateCols);

        assertTrue(result.sql().contains("ON ("));
        assertTrue(result.sql().contains("target.\"id\" = source.\"id\""));
        assertTrue(result.sql().contains("target.\"region\" = source.\"region\""));
        assertTrue(result.sql().contains("IS NULL AND source.\""));
    }

    @Test
    void buildUpsertSql_nullSafeOnClause() {
        List<String> insertCols = List.of("id");
        List<EntityFieldValue> fieldValues = List.of(new EntityFieldValue("id", "id", 1L, true));
        List<String> conflictCols = List.of("id");
        List<String> updateCols = List.of();

        SqlWithParams result = dialect.buildUpsertSql("users", insertCols, fieldValues, conflictCols, updateCols);

        assertTrue(result.sql().contains("(target.\"id\" = source.\"id\""));
        assertTrue(result.sql().contains("IS NULL AND source.\"id\" IS NULL)"));
    }

    @Test
    void buildUpsertSql_emptyConflictColumns_throws() {
        List<String> insertCols = List.of("id", "name");
        List<EntityFieldValue> fieldValues =
            List.of(new EntityFieldValue("id", "id", 1L, true), new EntityFieldValue("name", "name", "John", true));
        List<String> conflictCols = List.of();
        List<String> updateCols = List.of("name");

        assertThrows(IllegalArgumentException.class,
            () -> dialect.buildUpsertSql("users", insertCols, fieldValues, conflictCols, updateCols));
    }
}
