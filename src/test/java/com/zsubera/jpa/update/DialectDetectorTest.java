package com.zsubera.jpa.update;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class DialectDetectorTest {

    // ---- mapDialect ----

    @Test
    void mapDialect_null_returnsUnknown() {
        assertEquals("unknown", DialectDetector.mapDialect(null));
    }

    @Test
    void mapDialect_postgresql() {
        assertEquals("postgresql", DialectDetector.mapDialect("postgresql 14.2"));
    }

    @Test
    void mapDialect_mysql() {
        assertEquals("mysql", DialectDetector.mapDialect("mysql 8.0"));
    }

    @Test
    void mapDialect_mariadb_mapsToMysql() {
        assertEquals("mysql", DialectDetector.mapDialect("mariadb 10.6"));
    }

    @Test
    void mapDialect_oracle() {
        assertEquals("oracle", DialectDetector.mapDialect("oracle 19c"));
    }

    @Test
    void mapDialect_sqlserver_microsoft() {
        assertEquals("sqlserver", DialectDetector.mapDialect("microsoft sql server 2022"));
    }

    @Test
    void mapDialect_sqlserver_directName() {
        assertEquals("sqlserver", DialectDetector.mapDialect("sqlserver"));
    }

    @Test
    void mapDialect_unknown_returnsInput() {
        assertEquals("h2", DialectDetector.mapDialect("h2"));
    }

    // ---- registerDialect / removeDialect / getDialectStrategy ----

    @Test
    void registerDialect_and_getDialectStrategy() {
        DialectStrategy custom = new PostgresDialect();
        DialectDetector.registerDialect("h2", custom);
        assertSame(custom, DialectDetector.getDialectStrategy("h2"));
        DialectDetector.removeDialect("h2");
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
    void removeDialect_unknownName_returnsFalse() {
        assertFalse(DialectDetector.removeDialect("nonexistent_dialect_xyz"));
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
    void getDialectStrategy_registeredBuiltin_returnsInstance() {
        assertNotNull(DialectDetector.getDialectStrategy("postgresql"));
        assertNotNull(DialectDetector.getDialectStrategy("mysql"));
        assertNotNull(DialectDetector.getDialectStrategy("oracle"));
        assertNotNull(DialectDetector.getDialectStrategy("sqlserver"));
    }

    @Test
    void getDialectStrategy_unknown_returnsNull() {
        assertNull(DialectDetector.getDialectStrategy("h2"));
    }
}
