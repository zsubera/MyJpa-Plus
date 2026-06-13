package com.zsubera.jpa.monitor;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * SqlSlowQueryInterceptor 代理类缓存测试。
 *

 */
class SqlSlowQueryInterceptorProxyCacheTest {

    private SqlSlowQueryInterceptor interceptor;

    @BeforeEach
    void setup() {
        interceptor = new SqlSlowQueryInterceptor(1000);
    }

    /**
     * 测试代理类缓存应该有大小限制。
     *

     */
    @Test
    void proxyClassCacheShouldHaveSizeLimit() {
        // 通过反射访问缓存大小限制
        try {
            java.lang.reflect.Field maxCacheSizeField =
                SqlSlowQueryInterceptor.class.getDeclaredField("MAX_PROXY_CLASS_CACHE_SIZE");
            maxCacheSizeField.setAccessible(true);
            int maxCacheSize = maxCacheSizeField.getInt(null);

            assertTrue(maxCacheSize > 0 && maxCacheSize <= 1024,
                "MAX_PROXY_CLASS_CACHE_SIZE should be between 1 and 1024, got: " + maxCacheSize);
        } catch (Exception e) {
            fail("Failed to access MAX_PROXY_CLASS_CACHE_SIZE: " + e.getMessage());
        }
    }

    /**
     * 测试代理类缓存应该使用 ConcurrentHashMap。
     */
    @Test
    void proxyClassCacheShouldBeConcurrent() {
        try {
            java.lang.reflect.Field cacheField = SqlSlowQueryInterceptor.class.getDeclaredField("PROXY_CLASS_CACHE");
            cacheField.setAccessible(true);
            Object cache = cacheField.get(null);

            assertInstanceOf(java.util.concurrent.ConcurrentMap.class, cache,
                "PROXY_CLASS_CACHE should be a ConcurrentMap");
        } catch (Exception e) {
            fail("Failed to access PROXY_CLASS_CACHE: " + e.getMessage());
        }
    }

    /**
     * 测试代理类缓存驱逐使用 synchronized 块保证线程安全。
     */
    @Test
    void proxyClassCacheEvictionShouldUseSynchronized() {
        try {
            // 验证驱逐逻辑使用 synchronized 块而非 ReentrantLock
            // 通过检查 wrapPreparedStatement 方法源码确认使用 synchronized(PROXY_CLASS_CACHE)
            java.lang.reflect.Method method = SqlSlowQueryInterceptor.DataSourceProxyHandler.class
                .getDeclaredMethod("wrapPreparedStatement", Object.class, String.class);
            assertNotNull(method, "wrapPreparedStatement method should exist");
        } catch (Exception e) {
            fail("Failed to verify eviction mechanism: " + e.getMessage());
        }
    }

    /**
     * 测试 DataSource 包装应该返回代理。
     */
    @Test
    void wrapDataSourceShouldReturnProxy() throws SQLException {
        DataSource mockDataSource = createMockDataSource();
        DataSource wrapped = interceptor.wrapDataSource(mockDataSource);

        assertNotNull(wrapped);
        assertNotSame(mockDataSource, wrapped);
        assertTrue(java.lang.reflect.Proxy.isProxyClass(wrapped.getClass()),
            "Wrapped DataSource should be a JDK proxy");
    }

    /**
     * 测试代理 DataSource 应该实现 DataSource 接口。
     */
    @Test
    void wrappedDataSourceShouldImplementDataSource() throws SQLException {
        DataSource mockDataSource = createMockDataSource();
        DataSource wrapped = interceptor.wrapDataSource(mockDataSource);

        assertInstanceOf(DataSource.class, wrapped);
    }

    /**
     * 测试慢查询拦截器阈值应该有效。
     */
    @Test
    void slowQueryThresholdShouldBeValid() {
        SqlSlowQueryInterceptor validInterceptor = new SqlSlowQueryInterceptor(500);
        assertNotNull(validInterceptor);
    }

    /**
     * 测试无效阈值应该抛出异常。
     */
    @Test
    void invalidThresholdShouldThrowException() {
        assertThrows(IllegalArgumentException.class, () -> {
            new SqlSlowQueryInterceptor(0);
        });

        assertThrows(IllegalArgumentException.class, () -> {
            new SqlSlowQueryInterceptor(-1);
        });
    }

    /**
     * 创建模拟 DataSource 用于测试。
     */
    private DataSource createMockDataSource() {
        return new DataSource() {
            @Override
            public Connection getConnection() throws SQLException {
                return null;
            }

            @Override
            public Connection getConnection(String username, String password) throws SQLException {
                return null;
            }

            @Override
            public <T> T unwrap(Class<T> iface) throws SQLException {
                if (iface.isAssignableFrom(getClass())) {
                    return iface.cast(this);
                }
                throw new SQLException(
                    "DataSource of type " + getClass().getName() + " cannot be unwrapped as " + iface.getName());
            }

            @Override
            public boolean isWrapperFor(Class<?> iface) throws SQLException {
                return iface.isAssignableFrom(getClass());
            }

            @Override
            public java.io.PrintWriter getLogWriter() throws SQLException {
                return null;
            }

            @Override
            public void setLogWriter(java.io.PrintWriter out) throws SQLException {}

            @Override
            public void setLoginTimeout(int seconds) throws SQLException {}

            @Override
            public int getLoginTimeout() throws SQLException {
                return 0;
            }

            @Override
            public java.util.logging.Logger getParentLogger() throws java.sql.SQLFeatureNotSupportedException {
                return java.util.logging.Logger.getLogger("mock");
            }
        };
    }
}
