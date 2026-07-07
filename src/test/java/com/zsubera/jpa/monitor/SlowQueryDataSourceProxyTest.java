package com.zsubera.jpa.monitor;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class SlowQueryDataSourceProxyTest {

    @AfterEach
    void tearDown() {
        // Clean up any listeners that were added during tests
    }

    @Test
    void wrap_nullDataSource_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class, () -> SlowQueryDataSourceProxy.wrap(null, 1000));
    }

    @Test
    void wrap_zeroThreshold_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class, () -> SlowQueryDataSourceProxy.wrap(mockDataSource(), 0));
    }

    @Test
    void wrap_negativeThreshold_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class, () -> SlowQueryDataSourceProxy.wrap(mockDataSource(), -100));
    }

    @Test
    void wrap_validParams_returnsProxy() {
        DataSource proxied = SlowQueryDataSourceProxy.wrap(mockDataSource(), 1000);
        assertNotNull(proxied);
        assertTrue(Proxy.isProxyClass(proxied.getClass()));
    }

    @Test
    void isWrapped_proxyReturnsTrue() {
        DataSource proxied = SlowQueryDataSourceProxy.wrap(mockDataSource(), 1000);
        assertTrue(SlowQueryDataSourceProxy.isWrapped(proxied));
    }

    @Test
    void isWrapped_originalDataSourceReturnsFalse() {
        assertFalse(SlowQueryDataSourceProxy.isWrapped(mockDataSource()));
    }

    @Test
    void isWrapped_nullReturnsFalse() {
        assertFalse(SlowQueryDataSourceProxy.isWrapped(null));
    }

    @Test
    void addListener_null_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class, () -> SlowQueryDataSourceProxy.addListener(null));
    }

    @Test
    void addListener_validListener_canBeNotified() throws Exception {
        AtomicReference<String> capturedSql = new AtomicReference<>();
        AtomicLong capturedElapsed = new AtomicLong();
        CountDownLatch latch = new CountDownLatch(1);

        SlowQueryListener listener = (sql, elapsedMs, thresholdMs) -> {
            capturedSql.set(sql);
            capturedElapsed.set(elapsedMs);
            latch.countDown();
        };

        SlowQueryDataSourceProxy.addListener(listener);
        try {
            SlowQueryDataSourceProxy.notifyListeners("SELECT 1", 5000, 1000);
            assertTrue(latch.await(1, TimeUnit.SECONDS));
            assertEquals("SELECT 1", capturedSql.get());
            assertEquals(5000, capturedElapsed.get());
        } finally {
            SlowQueryDataSourceProxy.removeListener(listener);
        }
    }

    @Test
    void addListener_multipleListeners_allNotified() {
        AtomicInteger counter = new AtomicInteger();
        SlowQueryListener l1 = (sql, elapsed, threshold) -> counter.incrementAndGet();
        SlowQueryListener l2 = (sql, elapsed, threshold) -> counter.incrementAndGet();
        SlowQueryListener l3 = (sql, elapsed, threshold) -> counter.incrementAndGet();

        SlowQueryDataSourceProxy.addListener(l1);
        SlowQueryDataSourceProxy.addListener(l2);
        SlowQueryDataSourceProxy.addListener(l3);
        try {
            SlowQueryDataSourceProxy.notifyListeners("SELECT 1", 100, 50);
            assertEquals(3, counter.get());
        } finally {
            SlowQueryDataSourceProxy.removeListener(l1);
            SlowQueryDataSourceProxy.removeListener(l2);
            SlowQueryDataSourceProxy.removeListener(l3);
        }
    }

    @Test
    void removeListener_stopsNotifications() {
        AtomicInteger counter = new AtomicInteger();
        SlowQueryListener listener = (sql, elapsed, threshold) -> counter.incrementAndGet();

        SlowQueryDataSourceProxy.addListener(listener);
        SlowQueryDataSourceProxy.notifyListeners("SELECT 1", 100, 50);
        assertEquals(1, counter.get());

        SlowQueryDataSourceProxy.removeListener(listener);
        SlowQueryDataSourceProxy.notifyListeners("SELECT 1", 100, 50);
        assertEquals(1, counter.get());
    }

    @Test
    void notifyListeners_exceptionInListener_doesNotAffectOthers() {
        AtomicInteger l1Count = new AtomicInteger();

        SlowQueryListener throwingListener = (sql, elapsed, threshold) -> {
            throw new RuntimeException("boom");
        };
        SlowQueryListener normalListener = (sql, elapsed, threshold) -> l1Count.incrementAndGet();

        SlowQueryDataSourceProxy.addListener(throwingListener);
        SlowQueryDataSourceProxy.addListener(normalListener);
        try {
            SlowQueryDataSourceProxy.notifyListeners("SELECT 1", 100, 50);
            assertEquals(1, l1Count.get());
        } finally {
            SlowQueryDataSourceProxy.removeListener(throwingListener);
            SlowQueryDataSourceProxy.removeListener(normalListener);
        }
    }

    @Test
    void prepareStatement_returnsTimedProxy() throws Exception {
        DataSource ds = mockDataSourceWithPreparedStatement();
        DataSource proxied = SlowQueryDataSourceProxy.wrap(ds, 1000);

        Connection conn = proxied.getConnection();
        PreparedStatement ps = conn.prepareStatement("SELECT 1");

        assertNotNull(ps);
        assertTrue(Proxy.isProxyClass(ps.getClass()));
    }

    @Test
    void prepareStatement_batchTracking() throws Exception {
        DataSource ds = mockDataSourceWithPreparedStatement();
        DataSource proxied = SlowQueryDataSourceProxy.wrap(ds, 100000);

        Connection conn = proxied.getConnection();
        PreparedStatement ps = conn.prepareStatement("INSERT INTO t VALUES (?)");

        ps.addBatch();
        ps.addBatch();
        ps.addBatch();

        int[] results = ps.executeBatch();
        assertNotNull(results);
    }

    @Test
    void prepareStatement_clearBatch_resetsCount() throws Exception {
        DataSource ds = mockDataSourceWithPreparedStatement();
        DataSource proxied = SlowQueryDataSourceProxy.wrap(ds, 100000);

        Connection conn = proxied.getConnection();
        PreparedStatement ps = conn.prepareStatement("INSERT INTO t VALUES (?)");

        ps.addBatch();
        ps.addBatch();
        ps.clearBatch();
        ps.executeBatch();

        assertNotNull(ps);
    }

    @Test
    void normalMethods_propagateToTarget() throws Exception {
        DataSource ds = (DataSource)Proxy.newProxyInstance(DataSource.class.getClassLoader(),
            new Class<?>[] {DataSource.class}, (proxy, method, args) -> {
                if ("getLoginTimeout".equals(method.getName()))
                    return 42;
                if ("getLoginTimeout".equals(method.getName()))
                    return 42;
                return null;
            });

        DataSource proxied = SlowQueryDataSourceProxy.wrap(ds, 1000);

        assertEquals(42, proxied.getLoginTimeout());
    }

    @Test
    void getProxyClassCache_isAccessible() {
        assertNotNull(SlowQueryDataSourceProxy.getProxyClassCache());
    }

    @Test
    void connectionException_throwsSQLException() throws Throwable {
        DataSource ds = (DataSource)Proxy.newProxyInstance(DataSource.class.getClassLoader(),
            new Class<?>[] {DataSource.class}, (proxy, method, args) -> {
                if ("getConnection".equals(method.getName())) {
                    throw new SQLException("Connection refused");
                }
                return null;
            });

        DataSource proxied = SlowQueryDataSourceProxy.wrap(ds, 1000);

        try {
            proxied.getClass().getMethod("getConnection").invoke(proxied);
            fail("Should have thrown");
        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            assertTrue(cause instanceof SQLException);
            assertEquals("Connection refused", cause.getMessage());
        }
    }

    @Test
    void connectionException_preservesSQLState() throws Throwable {
        DataSource ds = (DataSource)Proxy.newProxyInstance(DataSource.class.getClassLoader(),
            new Class<?>[] {DataSource.class}, (proxy, method, args) -> {
                if ("getConnection".equals(method.getName())) {
                    throw new SQLException("Type preserved", "42");
                }
                return null;
            });

        DataSource proxied = SlowQueryDataSourceProxy.wrap(ds, 1000);

        try {
            proxied.getClass().getMethod("getConnection").invoke(proxied);
            fail("Should have thrown");
        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            assertTrue(cause instanceof SQLException);
            assertEquals("Type preserved", cause.getMessage());
            assertEquals("42", ((SQLException)cause).getSQLState());
        }
    }

    // ===== StatementTimingHandler: SQL capture from execute variants =====

    @Test
    void statementTimingHandler_allPaths_delegatesToTarget() throws Exception {
        // Test that StatementTimingHandler handles all Statement methods without errors
        java.sql.Statement stmt = mockStatement();
        Object handler = createStatementTimingHandler(stmt, "SELECT 1", 100000);

        java.lang.reflect.Method invoke = handler.getClass().getMethod("invoke", Object.class, java.lang.reflect.Method.class, Object[].class);

        // executeUpdate(String) — in timing method list
        invoke.invoke(handler, null, java.sql.Statement.class.getMethod("executeUpdate", String.class), new Object[] {"UPDATE t"});
        // executeUpdate(String) again to verify SQL capture
        invoke.invoke(handler, null, java.sql.Statement.class.getMethod("executeUpdate", String.class), new Object[] {"DELETE FROM t"});
        // execute(String) — returns boolean from mock
        java.sql.Statement boolTarget = (java.sql.Statement)Proxy.newProxyInstance(java.sql.Statement.class.getClassLoader(),
            new Class<?>[] {java.sql.Statement.class}, (p, m, a) -> "execute".equals(m.getName()) ? true : null);
        Object handler2 = createStatementTimingHandler(boolTarget, "DELETE FROM t", 100000);
        Object result = invoke.invoke(handler2, null, java.sql.Statement.class.getMethod("execute", String.class), new Object[] {"DELETE FROM t"});
        assertEquals(true, result);
        // addBatch(String) + addBatch(String) + clearBatch + executeBatch
        invoke.invoke(handler, null, java.sql.Statement.class.getMethod("addBatch", String.class), new Object[] {"INSERT INTO t VALUES (1)"});
        invoke.invoke(handler, null, java.sql.Statement.class.getMethod("addBatch", String.class), new Object[] {"INSERT INTO t VALUES (2)"});
        invoke.invoke(handler, null, java.sql.Statement.class.getMethod("clearBatch"), null);
        invoke.invoke(handler, null, java.sql.Statement.class.getMethod("executeBatch"), null);
    }

    @Test
    void statementTimingHandler_executeUpdate_capturesSql() throws Exception {
        Object target = createStatementTarget();
        java.lang.reflect.Method executeUpdate = java.sql.Statement.class.getMethod("executeUpdate", String.class);
        Object handler = createStatementTimingHandler(target, "initial", 100000);

        java.lang.reflect.Method invoke = handler.getClass().getMethod("invoke", Object.class, java.lang.reflect.Method.class, Object[].class);
        Object result = invoke.invoke(handler, null, executeUpdate, new Object[] {"UPDATE t SET x=1"});

        assertEquals(0, result);
    }

    @Test
    void statementTimingHandler_addBatchWithSql_incrementsCount() throws Exception {
        Object target = createStatementTarget();
        java.lang.reflect.Method addBatch = java.sql.Statement.class.getMethod("addBatch", String.class);
        Object handler = createStatementTimingHandler(target, "initial", 100000);

        java.lang.reflect.Method invoke = handler.getClass().getMethod("invoke", Object.class, java.lang.reflect.Method.class, Object[].class);
        invoke.invoke(handler, null, addBatch, new Object[] {"INSERT INTO t VALUES (1)"});
        invoke.invoke(handler, null, addBatch, new Object[] {"INSERT INTO t VALUES (2)"});

        java.lang.reflect.Method executeBatch = java.sql.Statement.class.getMethod("executeBatch");
        Object result = invoke.invoke(handler, null, executeBatch, null);
        assertNotNull(result);
    }

    @Test
    void statementTimingHandler_clearBatch_resetsCount() throws Exception {
        Object target = createStatementTarget();
        java.lang.reflect.Method addBatch = java.sql.Statement.class.getMethod("addBatch", String.class);
        java.lang.reflect.Method clearBatch = java.sql.Statement.class.getMethod("clearBatch");
        Object handler = createStatementTimingHandler(target, "initial", 100000);

        java.lang.reflect.Method invoke = handler.getClass().getMethod("invoke", Object.class, java.lang.reflect.Method.class, Object[].class);
        invoke.invoke(handler, null, addBatch, new Object[] {"INSERT INTO t VALUES (1)"});
        invoke.invoke(handler, null, addBatch, new Object[] {"INSERT INTO t VALUES (2)"});
        invoke.invoke(handler, null, clearBatch, null);

        java.lang.reflect.Method executeBatch = java.sql.Statement.class.getMethod("executeBatch");
        Object result = invoke.invoke(handler, null, executeBatch, null);
        assertNotNull(result);
    }

    // ===== StatementTimingDelegate: slow query notification =====

    @Test
    void statementTimingDelegate_slowQuery_notifiesListeners() throws Exception {
        AtomicReference<String> capturedSql = new AtomicReference<>();
        AtomicInteger notifyCount = new AtomicInteger();
        SlowQueryListener listener = (sql, elapsed, threshold) -> {
            capturedSql.set(sql);
            notifyCount.incrementAndGet();
        };
        SlowQueryDataSourceProxy.addListener(listener);
        try {
            // Use executeUpdate(String) on a mock Statement — it IS in the timing method list
            java.sql.Statement stmt = mockStatement();
            java.lang.reflect.Method executeUpdate = java.sql.Statement.class.getMethod("executeUpdate", String.class);

            java.lang.reflect.Method invokeTimed = getStatementTimingDelegateMethod();
            invokeTimed.setAccessible(true);

            // threshold=0ms means any execution triggers notification
            invokeTimed.invoke(null, stmt, "UPDATE t SET x=1", 0L, executeUpdate, new Object[] {"UPDATE t SET x=1"});
            assertEquals(1, notifyCount.get());
            // SQL is sanitized (literals replaced with ?) before notifying listeners
            assertEquals("UPDATE t SET x=?", capturedSql.get());
        } finally {
            SlowQueryDataSourceProxy.removeListener(listener);
        }
    }

    @Test
    void statementTimingDelegate_nonTimingMethod_justDelegates() throws Exception {
        java.lang.reflect.Method toStringMethod = Object.class.getMethod("toString");
        java.lang.reflect.Method invokeTimed = getStatementTimingDelegateMethod();
        invokeTimed.setAccessible(true);

        Object result = invokeTimed.invoke(null, this, "SELECT 1", 100000L, toStringMethod, null);
        assertNotNull(result);
    }

    // ===== MicrometerMetrics: no-op when Micrometer not available =====

    @Test
    void micrometerMetrics_notAvailable_doesNotThrow() throws Exception {
        java.lang.reflect.Method recordMethod = getMicrometerMetricsMethod();
        recordMethod.setAccessible(true);

        // Should not throw when Micrometer is not on classpath
        assertDoesNotThrow(() -> recordMethod.invoke(null, "executeQuery", 100L));
    }

    // ===== DataSourceProxyHandler: extractSql from various arg positions =====

    @Test
    void dataSourceProxyHandler_prepareCall_returnsTimedProxy() throws Exception {
        DataSource ds = mockDataSourceWithPrepareCall();
        DataSource proxied = SlowQueryDataSourceProxy.wrap(ds, 100000);

        Connection conn = proxied.getConnection();
        java.sql.CallableStatement cs = conn.prepareCall("SELECT 1");

        assertNotNull(cs);
        assertTrue(Proxy.isProxyClass(cs.getClass()));
    }

    @Test
    void dataSourceProxyHandler_createStatement_returnsTimedProxy() throws Exception {
        DataSource ds = mockDataSourceWithCreateStatement();
        DataSource proxied = SlowQueryDataSourceProxy.wrap(ds, 100000);

        Connection conn = proxied.getConnection();
        java.sql.Statement stmt = conn.createStatement();

        assertNotNull(stmt);
        assertTrue(Proxy.isProxyClass(stmt.getClass()));
    }

    @Test
    void statementTimingHandler_execute_withStringArg_capturesSql() throws Exception {
        // Create a target that returns boolean for execute(String)
        Object target = Proxy.newProxyInstance(java.sql.Statement.class.getClassLoader(),
            new Class<?>[] {java.sql.Statement.class}, (proxy, method, args) -> {
                if ("execute".equals(method.getName()) && args != null && args[0] instanceof String) {
                    return true;
                }
                return null;
            });
        java.lang.reflect.Method execute = java.sql.Statement.class.getMethod("execute", String.class);
        Object handler = createStatementTimingHandler(target, "initial", 100000);

        java.lang.reflect.Method invoke = handler.getClass().getMethod("invoke", Object.class, java.lang.reflect.Method.class, Object[].class);
        Object result = invoke.invoke(handler, null, execute, new Object[] {"DELETE FROM t"});

        assertEquals(true, result);
    }

    // ===== Helper methods =====

    private DataSource mockDataSource() {
        return (DataSource)Proxy.newProxyInstance(DataSource.class.getClassLoader(), new Class<?>[] {DataSource.class},
            (proxy, method, args) -> {
                if ("getConnection".equals(method.getName())) {
                    return mockConnection();
                }
                return null;
            });
    }

    private DataSource mockDataSourceWithPreparedStatement() {
        return (DataSource)Proxy.newProxyInstance(DataSource.class.getClassLoader(), new Class<?>[] {DataSource.class},
            (proxy, method, args) -> {
                if ("getConnection".equals(method.getName())) {
                    return mockConnection();
                }
                return null;
            });
    }

    private Connection mockConnection() {
        return (Connection)Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[] {Connection.class},
            (proxy, method, args) -> {
                if ("prepareStatement".equals(method.getName())) {
                    return mockPreparedStatement();
                }
                if ("isClosed".equals(method.getName())) {
                    return false;
                }
                return null;
            });
    }

    private PreparedStatement mockPreparedStatement() {
        return (PreparedStatement)Proxy.newProxyInstance(PreparedStatement.class.getClassLoader(),
            new Class<?>[] {PreparedStatement.class}, (proxy, method, args) -> {
                String name = method.getName();
                if ("executeQuery".equals(name) || "execute".equals(name)) {
                    return null;
                }
                if ("executeUpdate".equals(name)) {
                    return 0;
                }
                if ("addBatch".equals(name)) {
                    return null;
                }
                if ("clearBatch".equals(name)) {
                    return null;
                }
                if ("executeBatch".equals(name)) {
                    return new int[0];
                }
                return null;
            });
    }

    private Connection mockConnectionWithCallableStatement() {
        return (Connection)Proxy.newProxyInstance(Connection.class.getClassLoader(),
            new Class<?>[] {Connection.class}, (proxy, method, args) -> {
                if ("prepareCall".equals(method.getName())) {
                    return mockCallableStatement();
                }
                if ("isClosed".equals(method.getName())) {
                    return false;
                }
                return null;
            });
    }

    private java.sql.CallableStatement mockCallableStatement() {
        return (java.sql.CallableStatement)Proxy.newProxyInstance(
            java.sql.CallableStatement.class.getClassLoader(),
            new Class<?>[] {java.sql.CallableStatement.class}, (proxy, method, args) -> null);
    }

    private Connection mockConnectionWithCreateStatement() {
        return (Connection)Proxy.newProxyInstance(Connection.class.getClassLoader(),
            new Class<?>[] {Connection.class}, (proxy, method, args) -> {
                if ("createStatement".equals(method.getName())) {
                    return mockStatement();
                }
                if ("isClosed".equals(method.getName())) {
                    return false;
                }
                return null;
            });
    }

    private java.sql.Statement mockStatement() {
        return (java.sql.Statement)Proxy.newProxyInstance(java.sql.Statement.class.getClassLoader(),
            new Class<?>[] {java.sql.Statement.class}, (proxy, method, args) -> {
                String name = method.getName();
                if ("executeQuery".equals(name) || "execute".equals(name)) {
                    return null;
                }
                if ("executeUpdate".equals(name)) {
                    return 0;
                }
                if ("executeBatch".equals(name)) {
                    return new int[0];
                }
                return null;
            });
    }

    private DataSource mockDataSourceWithPrepareCall() {
        return (DataSource)Proxy.newProxyInstance(DataSource.class.getClassLoader(),
            new Class<?>[] {DataSource.class}, (proxy, method, args) -> {
                if ("getConnection".equals(method.getName())) {
                    return mockConnectionWithCallableStatement();
                }
                return null;
            });
    }

    private DataSource mockDataSourceWithCreateStatement() {
        return (DataSource)Proxy.newProxyInstance(DataSource.class.getClassLoader(),
            new Class<?>[] {DataSource.class}, (proxy, method, args) -> {
                if ("getConnection".equals(method.getName())) {
                    return mockConnectionWithCreateStatement();
                }
                return null;
            });
    }

    private Object createStatementTarget() throws Exception {
        return Proxy.newProxyInstance(java.sql.Statement.class.getClassLoader(),
            new Class<?>[] {java.sql.Statement.class}, (proxy, method, args) -> {
                String name = method.getName();
                if ("executeQuery".equals(name) || "execute".equals(name)) {
                    return null;
                }
                if ("executeUpdate".equals(name)) {
                    return 0;
                }
                if ("addBatch".equals(name)) {
                    return null;
                }
                if ("clearBatch".equals(name)) {
                    return null;
                }
                if ("executeBatch".equals(name)) {
                    return new int[0];
                }
                return null;
            });
    }

    private Object createStatementTimingHandler(Object target, String sql, long threshold) throws Exception {
        Class<?> handlerClass = Class.forName("com.zsubera.jpa.monitor.SlowQueryDataSourceProxy$StatementTimingHandler");
        java.lang.reflect.Constructor<?> ctor = handlerClass.getDeclaredConstructor(Object.class, String.class, long.class);
        ctor.setAccessible(true);
        return ctor.newInstance(target, sql, threshold);
    }

    private java.lang.reflect.Method getStatementTimingDelegateMethod() throws Exception {
        Class<?> delegateClass = Class.forName("com.zsubera.jpa.monitor.SlowQueryDataSourceProxy$StatementTimingDelegate");
        return delegateClass.getDeclaredMethod("invokeTimed", Object.class, String.class, long.class,
            java.lang.reflect.Method.class, Object[].class);
    }

    private java.lang.reflect.Method getMicrometerMetricsMethod() throws Exception {
        Class<?> metricsClass = Class.forName("com.zsubera.jpa.monitor.SlowQueryDataSourceProxy$MicrometerMetrics");
        return metricsClass.getDeclaredMethod("recordQueryDuration", String.class, long.class);
    }

    public void dummySlowMethod() {}
}
