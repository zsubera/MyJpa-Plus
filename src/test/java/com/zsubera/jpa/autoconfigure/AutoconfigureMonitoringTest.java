package com.zsubera.jpa.autoconfigure;

import static org.junit.jupiter.api.Assertions.*;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.zsubera.jpa.monitor.SqlSlowQueryInterceptor;
import java.lang.reflect.Proxy;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest(classes = AutoconfigureIntegrationTest.TestConfig.class)
@TestPropertySource(properties = {
    "myjpa-plus.monitoring.enabled=true",
    "myjpa-plus.monitoring.slow-query-threshold-ms=500"
})
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
        MyJpaPlusAutoConfiguration.DataSourceSlowQueryProxyPostProcessor processor =
            context.getBean(MyJpaPlusAutoConfiguration.DataSourceSlowQueryProxyPostProcessor.class);
        assertNotNull(processor);
    }

    @Test
    void sqlSlowQueryInterceptor_wrapDataSource() {
        SqlSlowQueryInterceptor interceptor = context.getBean(SqlSlowQueryInterceptor.class);
        DataSource realDataSource = context.getBean(DataSource.class);
        DataSource wrapped = interceptor.wrapDataSource(realDataSource);
        assertNotNull(wrapped);
        assertNotSame(realDataSource, wrapped);
    }

    @Test
    void dataSourceProxy_isAlreadyWrapped_withProxyHandler() {
        SqlSlowQueryInterceptor interceptor = context.getBean(SqlSlowQueryInterceptor.class);
        MyJpaPlusAutoConfiguration.DataSourceSlowQueryProxyPostProcessor processor =
            new MyJpaPlusAutoConfiguration.DataSourceSlowQueryProxyPostProcessor(interceptor);

        DataSource realDataSource = context.getBean(DataSource.class);
        DataSource wrapped = interceptor.wrapDataSource(realDataSource);

        Object result = processor.postProcessAfterInitialization(wrapped, "ds");
        assertSame(wrapped, result, "Should not double-wrap already wrapped DataSource");
    }

    @Test
    void isAlreadyWrapped_withDataSourceProxyHandler() throws Exception {
        SqlSlowQueryInterceptor interceptor = context.getBean(SqlSlowQueryInterceptor.class);
        MyJpaPlusAutoConfiguration.DataSourceSlowQueryProxyPostProcessor processor =
            new MyJpaPlusAutoConfiguration.DataSourceSlowQueryProxyPostProcessor(interceptor);

        // Create a proxy with DataSourceProxyHandler
        DataSource rawDs = new javax.sql.DataSource() {
            @Override public java.io.PrintWriter getLogWriter() { return null; }
            @Override public void setLogWriter(java.io.PrintWriter out) {}
            @Override public void setLoginTimeout(int seconds) {}
            @Override public int getLoginTimeout() { return 0; }
            @Override public java.util.logging.Logger getParentLogger() { return null; }
            @Override public <T> T unwrap(java.lang.Class<T> iface) { return null; }
            @Override public boolean isWrapperFor(java.lang.Class<?> iface) { return false; }
            @Override public java.sql.Connection getConnection() { return null; }
            @Override public java.sql.Connection getConnection(String u, String p) { return null; }
        };
        DataSource wrapped = interceptor.wrapDataSource(rawDs);

        // Call isAlreadyWrapped directly via reflection
        java.lang.reflect.Method isAlreadyWrappedMethod =
            MyJpaPlusAutoConfiguration.DataSourceSlowQueryProxyPostProcessor.class
                .getDeclaredMethod("isAlreadyWrapped", DataSource.class);
        isAlreadyWrappedMethod.setAccessible(true);

        // Test with wrapped DataSource (has DataSourceProxyHandler)
        boolean result1 = (boolean) isAlreadyWrappedMethod.invoke(null, wrapped);
        assertTrue(result1, "Should detect DataSourceProxyHandler");

        // Test with non-proxy DataSource
        boolean result2 = (boolean) isAlreadyWrappedMethod.invoke(null, rawDs);
        assertFalse(result2, "Should return false for non-proxy DataSource");

        // Test with JDK proxy (not DataSourceProxyHandler)
        DataSource jdkProxy = (DataSource) java.lang.reflect.Proxy.newProxyInstance(
            getClass().getClassLoader(),
            new Class<?>[]{DataSource.class},
            (p, m, args) -> null);
        boolean result3 = (boolean) isAlreadyWrappedMethod.invoke(null, jdkProxy);
        assertFalse(result3, "Should return false for non-DataSourceProxyHandler proxy");
    }

    @Test
    void securityContextAuditUserProvider_reflectionPaths() {
        MyJpaPlusAutoConfiguration.SecurityContextAuditUserProvider provider =
            new MyJpaPlusAutoConfiguration.SecurityContextAuditUserProvider();
        // Without Spring Security, should return ANONYMOUS
        String user = provider.getCurrentUser();
        assertNotNull(user);
        assertEquals("ANONYMOUS", user);
    }

    @Test
    void dataSourceProxy_jdkProxy_skipsWrap() {
        SqlSlowQueryInterceptor interceptor = context.getBean(SqlSlowQueryInterceptor.class);
        MyJpaPlusAutoConfiguration.DataSourceSlowQueryProxyPostProcessor processor =
            new MyJpaPlusAutoConfiguration.DataSourceSlowQueryProxyPostProcessor(interceptor);

        DataSource proxy = (DataSource) Proxy.newProxyInstance(
            getClass().getClassLoader(),
            new Class<?>[]{DataSource.class},
            (p, m, args) -> null);
        Object result = processor.postProcessAfterInitialization(proxy, "ds");
        assertSame(proxy, result, "Should not wrap JDK proxy DataSource");
    }

    @Test
    void dataSourceProxy_nonDataSource_returnsSame() {
        SqlSlowQueryInterceptor interceptor = context.getBean(SqlSlowQueryInterceptor.class);
        MyJpaPlusAutoConfiguration.DataSourceSlowQueryProxyPostProcessor processor =
            new MyJpaPlusAutoConfiguration.DataSourceSlowQueryProxyPostProcessor(interceptor);

        String notDataSource = "not a datasource";
        assertSame(notDataSource, processor.postProcessAfterInitialization(notDataSource, "test"));
    }

    @Test
    void dataSourceProxy_realDataSource_wrapsIt() {
        SqlSlowQueryInterceptor interceptor = context.getBean(SqlSlowQueryInterceptor.class);
        MyJpaPlusAutoConfiguration.DataSourceSlowQueryProxyPostProcessor processor =
            new MyJpaPlusAutoConfiguration.DataSourceSlowQueryProxyPostProcessor(interceptor);

        // Create a non-proxy DataSource to test wrapping
        DataSource rawDs = new javax.sql.DataSource() {
            @Override public java.io.PrintWriter getLogWriter() { return null; }
            @Override public void setLogWriter(java.io.PrintWriter out) {}
            @Override public void setLoginTimeout(int seconds) {}
            @Override public int getLoginTimeout() { return 0; }
            @Override public java.util.logging.Logger getParentLogger() { return null; }
            @Override public <T> T unwrap(java.lang.Class<T> iface) { return null; }
            @Override public boolean isWrapperFor(java.lang.Class<?> iface) { return false; }
            @Override public java.sql.Connection getConnection() { return null; }
            @Override public java.sql.Connection getConnection(String u, String p) { return null; }
        };
        Object result = processor.postProcessAfterInitialization(rawDs, "ds");
        assertNotSame(rawDs, result, "Should wrap the raw DataSource");
    }
}
