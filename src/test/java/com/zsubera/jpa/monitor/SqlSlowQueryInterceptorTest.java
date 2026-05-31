package com.zsubera.jpa.monitor;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

class SqlSlowQueryInterceptorTest {

    @Test
    void inspect_returnsSqlUnchanged() {
        SqlSlowQueryInterceptor interceptor = new SqlSlowQueryInterceptor(1000);
        String sql = "SELECT * FROM users WHERE id = ?";
        assertEquals(sql, interceptor.inspect(sql));
    }

    @Test
    void inspect_nullSql_returnsNull() {
        SqlSlowQueryInterceptor interceptor = new SqlSlowQueryInterceptor(1000);
        assertNull(interceptor.inspect(null));
    }

    @Test
    void constructor_zeroThreshold_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> new SqlSlowQueryInterceptor(0));
    }

    @Test
    void constructor_negativeThreshold_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> new SqlSlowQueryInterceptor(-1));
    }

    @Test
    void wrapDataSource_returnsProxy() {
        SqlSlowQueryInterceptor interceptor = new SqlSlowQueryInterceptor(1000);
        DataSource original = mockDataSource(0);
        DataSource wrapped = interceptor.wrapDataSource(original);

        assertNotSame(original, wrapped);
        assertTrue(Proxy.isProxyClass(wrapped.getClass()));
    }

    @Test
    void executeQuery_completesWithinThreshold() throws Exception {
        SqlSlowQueryInterceptor interceptor = new SqlSlowQueryInterceptor(5000);
        DataSource wrapped = interceptor.wrapDataSource(mockDataSource(0));

        try (Connection conn = wrapped.getConnection(); PreparedStatement ps = conn.prepareStatement("SELECT 1")) {
            ResultSet rs = ps.executeQuery();
            assertNull(rs);
        }
    }

    @Test
    void executeQuery_completesExceedingThreshold() throws Exception {
        SqlSlowQueryInterceptor interceptor = new SqlSlowQueryInterceptor(50);
        DataSource wrapped = interceptor.wrapDataSource(mockDataSource(100));

        long start = System.currentTimeMillis();
        try (Connection conn = wrapped.getConnection();
            PreparedStatement ps = conn.prepareStatement("SELECT * FROM slow_table")) {
            ps.executeQuery();
        }
        long elapsed = System.currentTimeMillis() - start;

        assertTrue(elapsed >= 100, "Expected execution to take at least 100ms, took: " + elapsed);
    }

    @Test
    void executeUpdate_completes() throws Exception {
        SqlSlowQueryInterceptor interceptor = new SqlSlowQueryInterceptor(5000);
        DataSource wrapped = interceptor.wrapDataSource(mockDataSource(0));

        try (Connection conn = wrapped.getConnection();
            PreparedStatement ps = conn.prepareStatement("UPDATE t SET val = 1")) {
            int result = ps.executeUpdate();
            assertEquals(0, result);
        }
    }

    @Test
    void execute_completes() throws Exception {
        SqlSlowQueryInterceptor interceptor = new SqlSlowQueryInterceptor(5000);
        DataSource wrapped = interceptor.wrapDataSource(mockDataSource(0));

        try (Connection conn = wrapped.getConnection(); PreparedStatement ps = conn.prepareStatement("DELETE FROM t")) {
            boolean result = ps.execute();
            assertFalse(result);
        }
    }

    @Test
    void multipleStatements_independentTiming() throws Exception {
        SqlSlowQueryInterceptor interceptor = new SqlSlowQueryInterceptor(5000);
        DataSource wrapped = interceptor.wrapDataSource(mockDataSource(0));

        try (Connection conn = wrapped.getConnection()) {
            try (PreparedStatement ps1 = conn.prepareStatement("SELECT 1")) {
                ps1.executeQuery();
            }
            try (PreparedStatement ps2 = conn.prepareStatement("SELECT 2")) {
                ps2.executeUpdate();
            }
            try (PreparedStatement ps3 = conn.prepareStatement("SELECT 3")) {
                ps3.execute();
            }
        }
    }

    private static DataSource mockDataSource(long sleepMs) {
        return (DataSource)Proxy.newProxyInstance(SqlSlowQueryInterceptorTest.class.getClassLoader(),
            new Class<?>[] {DataSource.class}, (proxy, method, args) -> {
                if ("getConnection".equals(method.getName())) {
                    return mockConnection(sleepMs);
                }
                return null;
            });
    }

    private static Connection mockConnection(long sleepMs) {
        return (Connection)Proxy.newProxyInstance(SqlSlowQueryInterceptorTest.class.getClassLoader(),
            new Class<?>[] {Connection.class}, (proxy, method, args) -> {
                if ("prepareStatement".equals(method.getName())) {
                    return mockPreparedStatement(sleepMs);
                }
                return null;
            });
    }

    private static PreparedStatement mockPreparedStatement(long sleepMs) {
        return (PreparedStatement)Proxy.newProxyInstance(SqlSlowQueryInterceptorTest.class.getClassLoader(),
            new Class<?>[] {PreparedStatement.class}, (proxy, method, args) -> {
                String name = method.getName();
                if ("executeQuery".equals(name) || "executeUpdate".equals(name) || "execute".equals(name)) {
                    if (sleepMs > 0) {
                        Thread.sleep(sleepMs);
                    }
                    if ("executeQuery".equals(name)) {
                        return null;
                    }
                    if ("execute".equals(name)) {
                        return false;
                    }
                    return 0;
                }
                return null;
            });
    }
}
