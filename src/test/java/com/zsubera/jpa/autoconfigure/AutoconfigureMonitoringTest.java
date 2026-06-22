package com.zsubera.jpa.autoconfigure;

import static org.junit.jupiter.api.Assertions.*;

import com.zsubera.jpa.monitor.SqlSlowQueryInterceptor;
import com.zsubera.jpa.monitor.SlowQueryDataSourceProxyPostProcessor;
import com.zsubera.jpa.monitor.SlowQueryDataSourceProxy;
import java.lang.reflect.Proxy;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest(classes = AutoconfigureIntegrationTest.TestConfig.class)
@TestPropertySource(
    properties = {"myjpa-plus.monitoring.enabled=true", "myjpa-plus.monitoring.slow-query-threshold-ms=500"})
class AutoconfigureMonitoringTest {

    @Autowired
    private org.springframework.context.ApplicationContext context;

    @Test
    void sqlSlowQueryInterceptor_beanExists() {
        SqlSlowQueryInterceptor interceptor = context.getBean(SqlSlowQueryInterceptor.class);
        assertNotNull(interceptor);
    }

    @Test
    void dataSourceSlowQueryProxyPostProcessor_beanExists() {
        SlowQueryDataSourceProxyPostProcessor processor = context.getBean(SlowQueryDataSourceProxyPostProcessor.class);
        assertNotNull(processor);
    }

    @Test
    void sqlSlowQueryInterceptor_wrapDataSource() {
        SqlSlowQueryInterceptor interceptor = context.getBean(SqlSlowQueryInterceptor.class);
        DataSource realDataSource = context.getBean(DataSource.class);
        DataSource wrapped = SlowQueryDataSourceProxy.wrap(realDataSource, 1000L);
        assertNotNull(wrapped);
        assertNotSame(realDataSource, wrapped);
    }

    @Test
    void dataSourceProxy_isAlreadyWrapped_withProxyHandler() {
        SqlSlowQueryInterceptor interceptor = context.getBean(SqlSlowQueryInterceptor.class);
        SlowQueryDataSourceProxyPostProcessor processor = new SlowQueryDataSourceProxyPostProcessor(1000L);

        DataSource realDataSource = context.getBean(DataSource.class);
        DataSource wrapped = SlowQueryDataSourceProxy.wrap(realDataSource, 1000L);

        Object result = processor.postProcessAfterInitialization(wrapped, "ds");
        assertSame(wrapped, result, "Should not double-wrap already wrapped DataSource");
    }

    @Test
    void isAlreadyWrapped_withDataSourceProxyHandler() throws Exception {
        SqlSlowQueryInterceptor interceptor = context.getBean(SqlSlowQueryInterceptor.class);
        SlowQueryDataSourceProxyPostProcessor processor = new SlowQueryDataSourceProxyPostProcessor(1000L);

        // Create a proxy with DataSourceProxyHandler
        DataSource rawDs = new javax.sql.DataSource() {
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

            @Override
            public <T> T unwrap(java.lang.Class<T> iface) {
                return null;
            }

            @Override
            public boolean isWrapperFor(java.lang.Class<?> iface) {
                return false;
            }

            @Override
            public java.sql.Connection getConnection() {
                return null;
            }

            @Override
            public java.sql.Connection getConnection(String u, String p) {
                return null;
            }
        };
        DataSource wrapped = SlowQueryDataSourceProxy.wrap(rawDs, 1000L);

        // Test with wrapped DataSource (has DataSourceProxyHandler)
        boolean result1 = SlowQueryDataSourceProxy.isWrapped(wrapped);
        assertTrue(result1, "Should detect DataSourceProxyHandler");

        // Test with non-proxy DataSource
        boolean result2 = SlowQueryDataSourceProxy.isWrapped(rawDs);
        assertFalse(result2, "Should return false for non-proxy DataSource");

        // Test with JDK proxy (not DataSourceProxyHandler)
        DataSource jdkProxy = (DataSource)java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),
            new Class<?>[] {DataSource.class}, (p, m, args) -> null);
        boolean result3 = SlowQueryDataSourceProxy.isWrapped(jdkProxy);
        assertFalse(result3, "Should return false for non-DataSourceProxyHandler proxy");
    }

    @Test
    void SecurityContextAuditorAware_reflectionPaths() {
        MyJpaPlusAutoConfiguration.SecurityContextAuditorAware provider =
            new MyJpaPlusAutoConfiguration.SecurityContextAuditorAware();
        // Without Spring Security, should return SYSTEM
        java.util.Optional<String> user = provider.getCurrentAuditor();
        assertNotNull(user);
        assertEquals("SYSTEM", user.get());
    }

    @Test
    void dataSourceProxy_jdkProxy_skipsWrap() {
        SqlSlowQueryInterceptor interceptor = context.getBean(SqlSlowQueryInterceptor.class);
        SlowQueryDataSourceProxyPostProcessor processor = new SlowQueryDataSourceProxyPostProcessor(1000L);

        DataSource proxy = (DataSource)Proxy.newProxyInstance(getClass().getClassLoader(),
            new Class<?>[] {DataSource.class}, (p, m, args) -> null);
        Object result = processor.postProcessAfterInitialization(proxy, "ds");
        assertSame(proxy, result, "Should not wrap JDK proxy DataSource");
    }

    @Test
    void dataSourceProxy_nonDataSource_returnsSame() {
        SqlSlowQueryInterceptor interceptor = context.getBean(SqlSlowQueryInterceptor.class);
        SlowQueryDataSourceProxyPostProcessor processor = new SlowQueryDataSourceProxyPostProcessor(1000L);

        String notDataSource = "not a datasource";
        assertSame(notDataSource, processor.postProcessAfterInitialization(notDataSource, "test"));
    }

    @Test
    void dataSourceProxy_realDataSource_wrapsIt() {
        SqlSlowQueryInterceptor interceptor = context.getBean(SqlSlowQueryInterceptor.class);
        SlowQueryDataSourceProxyPostProcessor processor = new SlowQueryDataSourceProxyPostProcessor(1000L);

        // Create a non-proxy DataSource to test wrapping
        DataSource rawDs = new javax.sql.DataSource() {
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

            @Override
            public <T> T unwrap(java.lang.Class<T> iface) {
                return null;
            }

            @Override
            public boolean isWrapperFor(java.lang.Class<?> iface) {
                return false;
            }

            @Override
            public java.sql.Connection getConnection() {
                return null;
            }

            @Override
            public java.sql.Connection getConnection(String u, String p) {
                return null;
            }
        };
        Object result = processor.postProcessAfterInitialization(rawDs, "ds");
        assertNotSame(rawDs, result, "Should wrap the raw DataSource");
    }
}
