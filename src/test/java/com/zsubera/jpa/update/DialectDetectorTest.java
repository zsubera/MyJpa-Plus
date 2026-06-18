package com.zsubera.jpa.update;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class DialectDetectorTest {

    @Test
    void getDialectStrategy_returnsRegisteredDialect() {
        assertNotNull(DialectDetector.getDialectStrategy("postgresql"));
        assertNotNull(DialectDetector.getDialectStrategy("mysql"));
        assertNotNull(DialectDetector.getDialectStrategy("oracle"));
        assertNotNull(DialectDetector.getDialectStrategy("sqlserver"));
    }

    @Test
    void getDialectStrategy_returnsNullForUnknown() {
        assertNull(DialectDetector.getDialectStrategy("unknown"));
    }

    @Test
    void registerDialect_addsNewDialect() {
        DialectStrategy mockStrategy = new PostgresDialect();
        try {
            DialectDetector.registerDialect("h2", mockStrategy);
            assertSame(mockStrategy, DialectDetector.getDialectStrategy("h2"));
        } finally {
            DialectDetector.removeDialect("h2");
        }
    }

    @Test
    void registerDialect_nullName_throws() {
        assertThrows(IllegalArgumentException.class,
            () -> DialectDetector.registerDialect(null, new PostgresDialect()));
    }

    @Test
    void registerDialect_blankName_throws() {
        assertThrows(IllegalArgumentException.class,
            () -> DialectDetector.registerDialect("  ", new PostgresDialect()));
    }

    @Test
    void registerDialect_nullStrategy_throws() {
        assertThrows(IllegalArgumentException.class, () -> DialectDetector.registerDialect("h2", null));
    }

    @Test
    void removeDialect_removesExistingDialect() {
        DialectStrategy mockStrategy = new PostgresDialect();
        DialectDetector.registerDialect("h2", mockStrategy);
        assertTrue(DialectDetector.removeDialect("h2"));
        assertNull(DialectDetector.getDialectStrategy("h2"));
    }

    @Test
    void removeDialect_returnsFalseForNonExistent() {
        assertFalse(DialectDetector.removeDialect("nonexistent"));
    }

    @Test
    void removeDialect_nullName_returnsFalse() {
        assertFalse(DialectDetector.removeDialect(null));
    }

    @Test
    void removeDialect_blankName_returnsFalse() {
        assertFalse(DialectDetector.removeDialect("  "));
    }

    @Test
    void mapDialect_identifiesPostgresql() {
        assertEquals("postgresql", DialectDetector.mapDialect("postgresql"));
        assertEquals("postgresql", DialectDetector.mapDialect("postgresql 14.0"));
    }

    @Test
    void mapDialect_identifiesMysql() {
        assertEquals("mysql", DialectDetector.mapDialect("mysql"));
        assertEquals("mysql", DialectDetector.mapDialect("mysql 8.0"));
    }

    @Test
    void mapDialect_identifiesOracle() {
        assertEquals("oracle", DialectDetector.mapDialect("oracle"));
        assertEquals("oracle", DialectDetector.mapDialect("oracle database 19c"));
    }

    @Test
    void mapDialect_identifiesSqlserver() {
        assertEquals("sqlserver", DialectDetector.mapDialect("microsoft sql server"));
        assertEquals("sqlserver", DialectDetector.mapDialect("sqlserver"));
        assertEquals("sqlserver", DialectDetector.mapDialect("sql server"));
    }

    @Test
    void mapDialect_unknownDatabase_returnsProductName() {
        String result = DialectDetector.mapDialect("UnknownDB");
        assertEquals("UnknownDB", result);
    }

    @Test
    void mapDialect_nullInput_returnsUnknown() {
        assertEquals("unknown", DialectDetector.mapDialect(null));
    }
}
