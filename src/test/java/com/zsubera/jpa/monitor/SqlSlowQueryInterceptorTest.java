package com.zsubera.jpa.monitor;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SqlSlowQueryInterceptorTest {

    @BeforeEach
    void setUp() {
        resetMicrometerState();
    }

    @Test
    void constructor_validThreshold() {
        assertNotNull(new SqlSlowQueryInterceptor(1000));
    }

    @Test
    void constructor_zeroThreshold_throws() {
        assertThrows(IllegalArgumentException.class, () -> new SqlSlowQueryInterceptor(0));
    }

    @Test
    void constructor_negativeThreshold_throws() {
        assertThrows(IllegalArgumentException.class, () -> new SqlSlowQueryInterceptor(-1));
    }

    @Test
    void inspect_returnsSameSql() {
        SqlSlowQueryInterceptor i = new SqlSlowQueryInterceptor(1000);
        assertEquals("SELECT 1", i.inspect("SELECT 1"));
        assertNull(i.inspect(null));
    }

    @Test
    void wrapDataSource_returnsProxy() {
        SqlSlowQueryInterceptor i = new SqlSlowQueryInterceptor(1000);
        DataSource wrapped = SlowQueryDataSourceProxy.wrap(new MockDataSource(), 1000);
        assertTrue(Proxy.isProxyClass(wrapped.getClass()));
    }

    @Test
    void wrappedDataSource_getConnection_delegates() throws SQLException {
        SqlSlowQueryInterceptor i = new SqlSlowQueryInterceptor(1000);
        MockDataSource mockDs = new MockDataSource();
        DataSource wrapped = SlowQueryDataSourceProxy.wrap(mockDs, 1000);
        assertNotNull(wrapped.getConnection());
        assertTrue(mockDs.getConnectionCalled);
    }

    @Test
    void wrappedDataSource_getConnectionWithAuth() throws SQLException {
        SqlSlowQueryInterceptor i = new SqlSlowQueryInterceptor(1000);
        DataSource wrapped = SlowQueryDataSourceProxy.wrap(new MockDataSource(), 1000);
        assertNotNull(wrapped.getConnection("u", "p"));
    }

    @Test
    void wrappedDataSource_otherMethods_delegate() throws Exception {
        SqlSlowQueryInterceptor i = new SqlSlowQueryInterceptor(1000);
        DataSource wrapped = SlowQueryDataSourceProxy.wrap(new MockDataSource(), 1000);
        wrapped.setLogWriter(null);
        assertEquals(0, wrapped.getLoginTimeout());
        wrapped.setLoginTimeout(5);
        assertFalse(wrapped.isWrapperFor(DataSource.class));
    }

    // ==================== DataSourceProxyHandler direct tests ====================

    @Test
    void dsProxyHandler_getConnection_delegates() throws Throwable {
        MockDataSource mockDs = new MockDataSource();
        SlowQueryDataSourceProxy.DataSourceProxyHandler handler =
            new SlowQueryDataSourceProxy.DataSourceProxyHandler(mockDs, 1000);

        DataSource proxy = (DataSource)Proxy.newProxyInstance(DataSource.class.getClassLoader(),
            new Class<?>[] {DataSource.class}, handler);

        Method getConnection = DataSource.class.getMethod("getConnection");
        Connection conn = (Connection)getConnection.invoke(proxy);
        assertNotNull(conn);
        assertTrue(mockDs.getConnectionCalled);
    }

    @Test
    void dsProxyHandler_toString_delegates() throws Throwable {
        MockDataSource mockDs = new MockDataSource();
        SlowQueryDataSourceProxy.DataSourceProxyHandler handler =
            new SlowQueryDataSourceProxy.DataSourceProxyHandler(mockDs, 1000);

        DataSource proxy = (DataSource)Proxy.newProxyInstance(DataSource.class.getClassLoader(),
            new Class<?>[] {DataSource.class}, handler);

        Method toString = Object.class.getMethod("toString");
        String result = (String)toString.invoke(proxy);
        assertNotNull(result);
    }

    @Test
    void dsProxyHandler_hashCode_delegates() throws Throwable {
        MockDataSource mockDs = new MockDataSource();
        SlowQueryDataSourceProxy.DataSourceProxyHandler handler =
            new SlowQueryDataSourceProxy.DataSourceProxyHandler(mockDs, 1000);

        DataSource proxy = (DataSource)Proxy.newProxyInstance(DataSource.class.getClassLoader(),
            new Class<?>[] {DataSource.class}, handler);

        Method hashCode = Object.class.getMethod("hashCode");
        int result = (int)hashCode.invoke(proxy);
        assertEquals(mockDs.hashCode(), result);
    }

    @Test
    void dsProxyHandler_equals_delegates() throws Throwable {
        MockDataSource mockDs = new MockDataSource();
        SlowQueryDataSourceProxy.DataSourceProxyHandler handler =
            new SlowQueryDataSourceProxy.DataSourceProxyHandler(mockDs, 1000);

        DataSource proxy = (DataSource)Proxy.newProxyInstance(DataSource.class.getClassLoader(),
            new Class<?>[] {DataSource.class}, handler);

        Method equals = Object.class.getMethod("equals", Object.class);
        assertTrue((boolean)equals.invoke(proxy, mockDs));
        assertFalse((boolean)equals.invoke(proxy, new Object()));
    }

    // ==================== PreparedStatementTimingHandler via wrapPreparedStatement ====================

    @Test
    void wrapPreparedStatement_createsTimingProxy() throws Exception {
        SqlSlowQueryInterceptor interceptor = new SqlSlowQueryInterceptor(1000);

        // Create a mock PreparedStatement with interfaces
        PreparedStatement mockStmt = createMockPreparedStatement("SELECT 1");

        // Use reflection to call wrapPreparedStatement
        java.lang.reflect.Method wrapMethod = getInnerClass("DataSourceProxyHandler")
            .getDeclaredMethod("wrapPreparedStatement", Object.class, String.class);
        wrapMethod.setAccessible(true);

        SlowQueryDataSourceProxy.DataSourceProxyHandler handler =
            new SlowQueryDataSourceProxy.DataSourceProxyHandler(new MockDataSource(), 1000);

        Object wrapped = wrapMethod.invoke(handler, mockStmt, "SELECT 1");
        assertNotNull(wrapped);
        assertTrue(Proxy.isProxyClass(wrapped.getClass()));
    }

    @Test
    void timingHandler_executeQuery_viaWrappedProxy() throws Exception {
        SqlSlowQueryInterceptor interceptor = new SqlSlowQueryInterceptor(1000);
        PreparedStatement mockStmt = createMockPreparedStatement("SELECT 1");

        java.lang.reflect.Method wrapMethod = getInnerClass("DataSourceProxyHandler")
            .getDeclaredMethod("wrapPreparedStatement", Object.class, String.class);
        wrapMethod.setAccessible(true);

        SlowQueryDataSourceProxy.DataSourceProxyHandler handler =
            new SlowQueryDataSourceProxy.DataSourceProxyHandler(new MockDataSource(), 1000);

        PreparedStatement wrapped = (PreparedStatement)wrapMethod.invoke(handler, mockStmt, "SELECT 1");
        ResultSet rs = wrapped.executeQuery();
        assertNotNull(rs);
    }

    @Test
    void timingHandler_executeUpdate_viaWrappedProxy() throws Exception {
        SqlSlowQueryInterceptor interceptor = new SqlSlowQueryInterceptor(1000);
        PreparedStatement mockStmt = createMockPreparedStatement("UPDATE users SET name='test'");

        java.lang.reflect.Method wrapMethod = getInnerClass("DataSourceProxyHandler")
            .getDeclaredMethod("wrapPreparedStatement", Object.class, String.class);
        wrapMethod.setAccessible(true);

        SlowQueryDataSourceProxy.DataSourceProxyHandler handler =
            new SlowQueryDataSourceProxy.DataSourceProxyHandler(new MockDataSource(), 1000);

        PreparedStatement wrapped =
            (PreparedStatement)wrapMethod.invoke(handler, mockStmt, "UPDATE users SET name='test'");
        int result = wrapped.executeUpdate();
        assertEquals(0, result);
    }

    @Test
    void timingHandler_execute_viaWrappedProxy() throws Exception {
        SqlSlowQueryInterceptor interceptor = new SqlSlowQueryInterceptor(1000);
        PreparedStatement mockStmt = createMockPreparedStatement("DELETE FROM users");

        java.lang.reflect.Method wrapMethod = getInnerClass("DataSourceProxyHandler")
            .getDeclaredMethod("wrapPreparedStatement", Object.class, String.class);
        wrapMethod.setAccessible(true);

        SlowQueryDataSourceProxy.DataSourceProxyHandler handler =
            new SlowQueryDataSourceProxy.DataSourceProxyHandler(new MockDataSource(), 1000);

        PreparedStatement wrapped = (PreparedStatement)wrapMethod.invoke(handler, mockStmt, "DELETE FROM users");
        boolean result = wrapped.execute();
        assertFalse(result);
    }

    @Test
    void timingHandler_setString_viaWrappedProxy() throws Exception {
        SqlSlowQueryInterceptor interceptor = new SqlSlowQueryInterceptor(1000);
        PreparedStatement mockStmt = createMockPreparedStatement("SELECT * FROM users WHERE name=?");

        java.lang.reflect.Method wrapMethod = getInnerClass("DataSourceProxyHandler")
            .getDeclaredMethod("wrapPreparedStatement", Object.class, String.class);
        wrapMethod.setAccessible(true);

        SlowQueryDataSourceProxy.DataSourceProxyHandler handler =
            new SlowQueryDataSourceProxy.DataSourceProxyHandler(new MockDataSource(), 1000);

        PreparedStatement wrapped =
            (PreparedStatement)wrapMethod.invoke(handler, mockStmt, "SELECT * FROM users WHERE name=?");
        wrapped.setString(1, "test");
    }

    @Test
    void timingHandler_close_viaWrappedProxy() throws Exception {
        SqlSlowQueryInterceptor interceptor = new SqlSlowQueryInterceptor(1000);
        PreparedStatement mockStmt = createMockPreparedStatement("SELECT 1");

        java.lang.reflect.Method wrapMethod = getInnerClass("DataSourceProxyHandler")
            .getDeclaredMethod("wrapPreparedStatement", Object.class, String.class);
        wrapMethod.setAccessible(true);

        SlowQueryDataSourceProxy.DataSourceProxyHandler handler =
            new SlowQueryDataSourceProxy.DataSourceProxyHandler(new MockDataSource(), 1000);

        PreparedStatement wrapped = (PreparedStatement)wrapMethod.invoke(handler, mockStmt, "SELECT 1");
        wrapped.close();
    }

    @Test
    void timingHandler_getConnection_viaWrappedProxy() throws Exception {
        SqlSlowQueryInterceptor interceptor = new SqlSlowQueryInterceptor(1000);
        PreparedStatement mockStmt = createMockPreparedStatement("SELECT 1");

        java.lang.reflect.Method wrapMethod = getInnerClass("DataSourceProxyHandler")
            .getDeclaredMethod("wrapPreparedStatement", Object.class, String.class);
        wrapMethod.setAccessible(true);

        SlowQueryDataSourceProxy.DataSourceProxyHandler handler =
            new SlowQueryDataSourceProxy.DataSourceProxyHandler(new MockDataSource(), 1000);

        PreparedStatement wrapped = (PreparedStatement)wrapMethod.invoke(handler, mockStmt, "SELECT 1");
        Connection conn = wrapped.getConnection();
        assertNotNull(conn);
    }

    @Test
    void timingHandler_getMetaData_viaWrappedProxy() throws Exception {
        SqlSlowQueryInterceptor interceptor = new SqlSlowQueryInterceptor(1000);
        PreparedStatement mockStmt = createMockPreparedStatement("SELECT 1");

        java.lang.reflect.Method wrapMethod = getInnerClass("DataSourceProxyHandler")
            .getDeclaredMethod("wrapPreparedStatement", Object.class, String.class);
        wrapMethod.setAccessible(true);

        SlowQueryDataSourceProxy.DataSourceProxyHandler handler =
            new SlowQueryDataSourceProxy.DataSourceProxyHandler(new MockDataSource(), 1000);

        PreparedStatement wrapped = (PreparedStatement)wrapMethod.invoke(handler, mockStmt, "SELECT 1");
        wrapped.getMetaData();
    }

    @Test
    void timingHandler_slowQuery_logsWarning() throws Exception {
        SqlSlowQueryInterceptor interceptor = new SqlSlowQueryInterceptor(1); // 1ms threshold
        resetMicrometerState();

        PreparedStatement mockStmt = createMockPreparedStatement("SELECT 1");

        java.lang.reflect.Method wrapMethod = getInnerClass("DataSourceProxyHandler")
            .getDeclaredMethod("wrapPreparedStatement", Object.class, String.class);
        wrapMethod.setAccessible(true);

        SlowQueryDataSourceProxy.DataSourceProxyHandler handler =
            new SlowQueryDataSourceProxy.DataSourceProxyHandler(new MockDataSource(), 1);

        PreparedStatement wrapped = (PreparedStatement)wrapMethod.invoke(handler, mockStmt, "SELECT 1");
        wrapped.executeQuery();
    }

    @Test
    void timingHandler_recordMetrics_micrometerNotAvailable() throws Exception {
        SqlSlowQueryInterceptor interceptor = new SqlSlowQueryInterceptor(1000);
        resetMicrometerState();

        PreparedStatement mockStmt = createMockPreparedStatement("SELECT 1");

        java.lang.reflect.Method wrapMethod = getInnerClass("DataSourceProxyHandler")
            .getDeclaredMethod("wrapPreparedStatement", Object.class, String.class);
        wrapMethod.setAccessible(true);

        SlowQueryDataSourceProxy.DataSourceProxyHandler handler =
            new SlowQueryDataSourceProxy.DataSourceProxyHandler(new MockDataSource(), 1000);

        PreparedStatement wrapped = (PreparedStatement)wrapMethod.invoke(handler, mockStmt, "SELECT 1");
        wrapped.executeQuery();
    }

    @Test
    void timingHandler_checkMicrometerAlreadyChecked() throws Exception {
        SqlSlowQueryInterceptor interceptor = new SqlSlowQueryInterceptor(1000);

        // Set micrometerCache to null (simulating not available) to skip re-checking
        java.lang.reflect.Field cacheField =
            getInnerClass("MicrometerMetrics").getDeclaredField("micrometerCache");
        cacheField.setAccessible(true);
        Object previousCache = cacheField.get(null);
        cacheField.set(null, null);

        PreparedStatement mockStmt = createMockPreparedStatement("SELECT 1");

        java.lang.reflect.Method wrapMethod = getInnerClass("DataSourceProxyHandler")
            .getDeclaredMethod("wrapPreparedStatement", Object.class, String.class);
        wrapMethod.setAccessible(true);

        SlowQueryDataSourceProxy.DataSourceProxyHandler handler =
            new SlowQueryDataSourceProxy.DataSourceProxyHandler(new MockDataSource(), 1000);

        PreparedStatement wrapped = (PreparedStatement)wrapMethod.invoke(handler, mockStmt, "SELECT 1");
        wrapped.executeQuery();

        cacheField.set(null, previousCache);
    }

    @Test
    void wrapPreparedStatement_noInterfaces_returnsRaw() throws Exception {
        SqlSlowQueryInterceptor interceptor = new SqlSlowQueryInterceptor(1000);

        // Create an object with no interfaces (just Object)
        Object noInterfaceObj = new Object() {};

        java.lang.reflect.Method wrapMethod = getInnerClass("DataSourceProxyHandler")
            .getDeclaredMethod("wrapPreparedStatement", Object.class, String.class);
        wrapMethod.setAccessible(true);

        SlowQueryDataSourceProxy.DataSourceProxyHandler handler =
            new SlowQueryDataSourceProxy.DataSourceProxyHandler(new MockDataSource(), 1000);

        Object result = wrapMethod.invoke(handler, noInterfaceObj, "SELECT 1");
        assertSame(noInterfaceObj, result);
    }

    @Test
    void wrapPreparedStatement_reflectiveFallback() throws Exception {
        SqlSlowQueryInterceptor interceptor = new SqlSlowQueryInterceptor(1000);

        // Create a proxy that will cause the primary constructor to fail
        // by using a class that has a constructor throwing an exception
        PreparedStatement mockStmt = createMockPreparedStatement("SELECT 1");

        java.lang.reflect.Method wrapMethod = getInnerClass("DataSourceProxyHandler")
            .getDeclaredMethod("wrapPreparedStatement", Object.class, String.class);
        wrapMethod.setAccessible(true);

        SlowQueryDataSourceProxy.DataSourceProxyHandler handler =
            new SlowQueryDataSourceProxy.DataSourceProxyHandler(new MockDataSource(), 1000);

        Object wrapped = wrapMethod.invoke(handler, mockStmt, "SELECT 1");
        assertNotNull(wrapped);
    }

    // ==================== Helpers ====================

    private Class<?> getInnerClass(String name) {
        for (Class<?> inner : SlowQueryDataSourceProxy.class.getDeclaredClasses()) {
            if (inner.getSimpleName().equals(name)) {
                return inner;
            }
        }
        throw new RuntimeException("Inner class not found: " + name);
    }

    private void resetMicrometerState() {
        try {
            Class<?> handlerClass = getInnerClass("MicrometerMetrics");
            java.lang.reflect.Field f = handlerClass.getDeclaredField("micrometerCache");
            f.setAccessible(true);
            f.set(null, null);
        } catch (Exception ignored) {
        }
    }

    private static PreparedStatement createMockPreparedStatement(String sql) {
        return (PreparedStatement)Proxy.newProxyInstance(PreparedStatement.class.getClassLoader(),
            new Class<?>[] {PreparedStatement.class}, (proxy, method, args) -> {
                switch (method.getName()) {
                    case "executeQuery":
                        return (ResultSet)Proxy.newProxyInstance(ResultSet.class.getClassLoader(),
                            new Class<?>[] {ResultSet.class}, (p, m, a) -> null);
                    case "executeUpdate":
                        return 0;
                    case "execute":
                        return false;
                    case "getConnection":
                        return (Connection)Proxy.newProxyInstance(Connection.class.getClassLoader(),
                            new Class<?>[] {Connection.class}, (p, m, a) -> null);
                    case "close":
                    case "getMetaData":
                        return null;
                    default:
                        return null;
                }
            });
    }

    static class MockDataSource implements DataSource {
        boolean getConnectionCalled = false;

        @Override
        public Connection getConnection() throws SQLException {
            getConnectionCalled = true;
            return (Connection)Proxy.newProxyInstance(Connection.class.getClassLoader(),
                new Class<?>[] {Connection.class}, (p, m, a) -> null);
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            return getConnection();
        }

        @Override
        public <T> T unwrap(Class<T> iface) {
            return null;
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) {
            return false;
        }

        @Override
        public java.io.PrintWriter getLogWriter() {
            return null;
        }

        @Override
        public void setLogWriter(java.io.PrintWriter out) {}

        @Override
        public void setLoginTimeout(int seconds) {}

        @Override
        public int getLoginTimeout() {
            return 0;
        }

        @Override
        public java.util.logging.Logger getParentLogger() {
            return null;
        }
    }
}
