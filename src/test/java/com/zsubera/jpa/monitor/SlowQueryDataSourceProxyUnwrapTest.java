package com.zsubera.jpa.monitor;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Proxy;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

/**
 * Regression tests for SlowQueryDataSourceProxy InvocationTargetException unwrapping.
 * Verifies that JDBC exceptions from the underlying DataSource are
 * thrown directly as their original type (not wrapped in InvocationTargetException).
 */
class SlowQueryDataSourceProxyUnwrapTest {

    @Test
    void dataSourceProxy_connectionException_throwsSQLException() throws Throwable {
        DataSource mockDs = (DataSource)Proxy.newProxyInstance(DataSource.class.getClassLoader(),
            new Class<?>[] {DataSource.class}, (proxy, method, args) -> {
                if ("getConnection".equals(method.getName())) {
                    throw new SQLException("Connection refused");
                }
                return null;
            });

        DataSource proxied = SlowQueryDataSourceProxy.wrap(mockDs, 1000);

        try {
            // getConnection() throws checked exception declared on DataSource interface,
            // so we must use reflection to test what actually arrives.
            proxied.getClass().getMethod("getConnection").invoke(proxied);
            fail("Should have thrown");
        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            assertTrue(cause instanceof SQLException, "Should throw SQLException, got: " + cause.getClass().getName());
            assertEquals("Connection refused", cause.getMessage());
        }
    }

    @Test
    void dataSourceProxy_normalMethods_propagate() throws Throwable {
        DataSource mockDs = (DataSource)Proxy.newProxyInstance(DataSource.class.getClassLoader(),
            new Class<?>[] {DataSource.class}, (proxy, method, args) -> {
                if ("getLoginTimeout".equals(method.getName())) {
                    return 30;
                }
                return null;
            });

        DataSource proxied = SlowQueryDataSourceProxy.wrap(mockDs, 1000);
        int timeout = proxied.getLoginTimeout();
        assertEquals(30, timeout);
    }

    @Test
    void dataSourceProxy_unwrappedType_matchesOriginalException() throws Throwable {
        // Verify that the original exception type is preserved, not wrapped
        // in a generic RuntimeException or InvocationTargetException
        DataSource mockDs = (DataSource)Proxy.newProxyInstance(DataSource.class.getClassLoader(),
            new Class<?>[] {DataSource.class}, (proxy, method, args) -> {
                if ("getConnection".equals(method.getName())) {
                    throw new SQLException("Type preserved", "42");
                }
                return null;
            });

        DataSource proxied = SlowQueryDataSourceProxy.wrap(mockDs, 1000);

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
}
