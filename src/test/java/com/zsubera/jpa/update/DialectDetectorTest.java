package com.zsubera.jpa.update;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.util.HashMap;
import java.util.Map;
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

    // ===== detectDialect 多优先级路径测试 =====

    @Test
    void detectDialect_fromJdbcUrlPostgresql() {
        EntityManager em = mock(EntityManager.class);
        EntityManagerFactory emf = mock(EntityManagerFactory.class);
        Map<String, Object> props = new HashMap<>();
        props.put("jakarta.persistence.jdbc.url", "jdbc:postgresql://localhost:5432/test");
        when(emf.getProperties()).thenReturn(props);
        when(em.getEntityManagerFactory()).thenReturn(emf);

        String dialect = DialectDetector.detectDialect(em);
        assertEquals("postgresql", dialect);
    }

    @Test
    void detectDialect_fromJdbcUrlMysql() {
        EntityManager em = mock(EntityManager.class);
        EntityManagerFactory emf = mock(EntityManagerFactory.class);
        Map<String, Object> props = new HashMap<>();
        props.put("jakarta.persistence.jdbc.url", "jdbc:mysql://localhost:3306/test");
        when(emf.getProperties()).thenReturn(props);
        when(em.getEntityManagerFactory()).thenReturn(emf);

        String dialect = DialectDetector.detectDialect(em);
        assertEquals("mysql", dialect);
    }

    @Test
    void detectDialect_fromHibernateUrl() {
        EntityManager em = mock(EntityManager.class);
        EntityManagerFactory emf = mock(EntityManagerFactory.class);
        Map<String, Object> props = new HashMap<>();
        props.put("hibernate.connection.url", "jdbc:postgresql://localhost/db");
        when(emf.getProperties()).thenReturn(props);
        when(em.getEntityManagerFactory()).thenReturn(emf);

        String dialect = DialectDetector.detectDialect(em);
        assertEquals("postgresql", dialect);
    }

    @Test
    void detectDialect_fromJdbcConnectionPostgresql() throws Exception {
        EntityManager em = mock(EntityManager.class);
        EntityManagerFactory emf = mock(EntityManagerFactory.class);
        when(emf.getProperties()).thenReturn(new HashMap<>());
        when(em.getEntityManagerFactory()).thenReturn(emf);

        Connection conn = mock(Connection.class);
        DatabaseMetaData meta = mock(DatabaseMetaData.class);
        when(meta.getDatabaseProductName()).thenReturn("PostgreSQL");
        when(conn.getMetaData()).thenReturn(meta);
        when(em.unwrap(Connection.class)).thenReturn(conn);

        String dialect = DialectDetector.detectDialect(em);
        assertEquals("postgresql", dialect);
    }

    @Test
    void detectDialect_fromJdbcConnectionMysql() throws Exception {
        EntityManager em = mock(EntityManager.class);
        EntityManagerFactory emf = mock(EntityManagerFactory.class);
        when(emf.getProperties()).thenReturn(new HashMap<>());
        when(em.getEntityManagerFactory()).thenReturn(emf);

        Connection conn = mock(Connection.class);
        DatabaseMetaData meta = mock(DatabaseMetaData.class);
        when(meta.getDatabaseProductName()).thenReturn("MySQL");
        when(conn.getMetaData()).thenReturn(meta);
        when(em.unwrap(Connection.class)).thenReturn(conn);

        String dialect = DialectDetector.detectDialect(em);
        assertEquals("mysql", dialect);
    }

    @Test
    void detectDialect_fallbackToManualSystemProperty() {
        EntityManager em = mock(EntityManager.class);
        EntityManagerFactory emf = mock(EntityManagerFactory.class);
        when(emf.getProperties()).thenReturn(new HashMap<>());
        when(em.getEntityManagerFactory()).thenReturn(emf);

        String oldProp = System.getProperty("myjpa-plus.dialect");
        try {
            System.setProperty("myjpa-plus.dialect", "postgresql");
            String dialect = DialectDetector.detectDialect(em);
            assertEquals("postgresql", dialect);
        } finally {
            if (oldProp != null) {
                System.setProperty("myjpa-plus.dialect", oldProp);
            } else {
                System.clearProperty("myjpa-plus.dialect");
            }
        }
    }

    @Test
    void detectDialect_throwsWhenNothingConfigured() {
        EntityManager em = mock(EntityManager.class);
        EntityManagerFactory emf = mock(EntityManagerFactory.class);
        when(emf.getProperties()).thenReturn(new HashMap<>());
        when(em.getEntityManagerFactory()).thenReturn(emf);

        String oldProp = System.getProperty("myjpa-plus.dialect");
        try {
            System.clearProperty("myjpa-plus.dialect");
            assertThrows(com.zsubera.jpa.exception.MyJpaPlusException.class, () -> DialectDetector.detectDialect(em));
        } finally {
            if (oldProp != null) {
                System.setProperty("myjpa-plus.dialect", oldProp);
            }
        }
    }

    @Test
    void detectDialect_cachesResult() {
        EntityManager em = mock(EntityManager.class);
        EntityManagerFactory emf = mock(EntityManagerFactory.class);
        Map<String, Object> props = new HashMap<>();
        props.put("jakarta.persistence.jdbc.url", "jdbc:mysql://localhost/db");
        when(emf.getProperties()).thenReturn(props);
        when(em.getEntityManagerFactory()).thenReturn(emf);

        String d1 = DialectDetector.detectDialect(em);
        String d2 = DialectDetector.detectDialect(em);
        assertEquals("mysql", d1);
        assertSame(d1, d2);
    }

    @Test
    void detectDialect_fromJdbcUrlOracle() {
        EntityManager em = mock(EntityManager.class);
        EntityManagerFactory emf = mock(EntityManagerFactory.class);
        Map<String, Object> props = new HashMap<>();
        props.put("jakarta.persistence.jdbc.url", "jdbc:oracle:thin:@localhost:1521:ORCL");
        when(emf.getProperties()).thenReturn(props);
        when(em.getEntityManagerFactory()).thenReturn(emf);

        String dialect = DialectDetector.detectDialect(em);
        assertEquals("oracle", dialect);
    }

    @Test
    void detectDialect_fromJdbcUrlSqlServer() {
        EntityManager em = mock(EntityManager.class);
        EntityManagerFactory emf = mock(EntityManagerFactory.class);
        Map<String, Object> props = new HashMap<>();
        props.put("jakarta.persistence.jdbc.url", "jdbc:sqlserver://localhost:1433;databaseName=test");
        when(emf.getProperties()).thenReturn(props);
        when(em.getEntityManagerFactory()).thenReturn(emf);

        String dialect = DialectDetector.detectDialect(em);
        assertEquals("sqlserver", dialect);
    }

    @Test
    void detectDialect_fromHibernateDialectPropertyOracle() {
        EntityManager em = mock(EntityManager.class);
        EntityManagerFactory emf = mock(EntityManagerFactory.class);
        Map<String, Object> props = new HashMap<>();
        props.put("hibernate.dialect", "org.hibernate.dialect.OracleDialect");
        when(emf.getProperties()).thenReturn(props);
        when(em.getEntityManagerFactory()).thenReturn(emf);

        String dialect = DialectDetector.detectDialect(em);
        assertEquals("oracle", dialect);
    }

    @Test
    void detectDialect_connectionReturnsNull() throws Exception {
        EntityManager em = mock(EntityManager.class);
        EntityManagerFactory emf = mock(EntityManagerFactory.class);
        when(emf.getProperties()).thenReturn(new HashMap<>());
        when(em.getEntityManagerFactory()).thenReturn(emf);
        when(em.unwrap(Connection.class)).thenReturn(null);

        String oldProp = System.getProperty("myjpa-plus.dialect");
        try {
            System.setProperty("myjpa-plus.dialect", "mysql");
            String dialect = DialectDetector.detectDialect(em);
            assertEquals("mysql", dialect);
        } finally {
            if (oldProp != null) {
                System.setProperty("myjpa-plus.dialect", oldProp);
            } else {
                System.clearProperty("myjpa-plus.dialect");
            }
        }
    }
}
