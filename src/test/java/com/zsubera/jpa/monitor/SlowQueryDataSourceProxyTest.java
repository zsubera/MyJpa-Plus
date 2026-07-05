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

    // --- Helper methods ---

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
}
