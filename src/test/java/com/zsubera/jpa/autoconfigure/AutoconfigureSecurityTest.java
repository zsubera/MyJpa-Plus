package com.zsubera.jpa.autoconfigure;

import static org.junit.jupiter.api.Assertions.*;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.zsubera.jpa.converter.EncryptConverter;
import com.zsubera.jpa.monitor.SqlSlowQueryInterceptor;
import com.zsubera.jpa.monitor.SlowQueryDataSourceProxyPostProcessor;
import com.zsubera.jpa.monitor.SlowQueryDataSourceProxy;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.List;

import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest(classes = AutoconfigureIntegrationTest.TestConfig.class)
@TestPropertySource(
    properties = {"myjpa-plus.monitoring.enabled=true", "myjpa-plus.monitoring.slow-query-threshold-ms=500"})
class AutoconfigureSecurityTest {

    @Autowired
    private org.springframework.context.ApplicationContext context;

    // ---- SecurityContextAuditUserProvider: with Spring Security ----

    @Test
    void securityContextAuditUserProvider_authenticatedUser() {
        MyJpaPlusAutoConfiguration.SecurityContextAuditUserProvider provider =
            new MyJpaPlusAutoConfiguration.SecurityContextAuditUserProvider();

        SecurityContext secCtx = SecurityContextHolder.createEmptyContext();
        Authentication auth = new UsernamePasswordAuthenticationToken("testuser", "password", List.of());
        secCtx.setAuthentication(auth);
        SecurityContextHolder.setContext(secCtx);

        try {
            String user = provider.getCurrentUser();
            assertEquals("testuser", user);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void securityContextAuditUserProvider_unauthenticatedUser() {
        MyJpaPlusAutoConfiguration.SecurityContextAuditUserProvider provider =
            new MyJpaPlusAutoConfiguration.SecurityContextAuditUserProvider();

        SecurityContext secCtx = SecurityContextHolder.createEmptyContext();
        // 2-arg constructor creates unauthenticated token
        Authentication auth = new UsernamePasswordAuthenticationToken("testuser", null);
        secCtx.setAuthentication(auth);
        SecurityContextHolder.setContext(secCtx);

        try {
            String user = provider.getCurrentUser();
            assertEquals("ANONYMOUS", user);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void securityContextAuditUserProvider_nullAuthentication() {
        MyJpaPlusAutoConfiguration.SecurityContextAuditUserProvider provider =
            new MyJpaPlusAutoConfiguration.SecurityContextAuditUserProvider();

        SecurityContext secCtx = SecurityContextHolder.createEmptyContext();
        secCtx.setAuthentication(null);
        SecurityContextHolder.setContext(secCtx);

        try {
            String user = provider.getCurrentUser();
            assertEquals("ANONYMOUS", user);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void securityContextAuditUserProvider_noSecurityContext() {
        MyJpaPlusAutoConfiguration.SecurityContextAuditUserProvider provider =
            new MyJpaPlusAutoConfiguration.SecurityContextAuditUserProvider();

        SecurityContextHolder.clearContext();

        String user = provider.getCurrentUser();
        assertEquals("ANONYMOUS", user);
    }

    // ---- EncryptConverter exception: RejectedExecutionException ----

    @Test
    void configInitializer_encryptKeyRejectedExecution() throws Exception {
        // Get the WARM_UP_EXECUTOR field from EncryptConverter (now AtomicReference<ExecutorService>)
        Field executorField = EncryptConverter.class.getDeclaredField("WARM_UP_EXECUTOR");
        executorField.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.concurrent.atomic.AtomicReference<java.util.concurrent.ExecutorService> executorRef =
            (java.util.concurrent.atomic.AtomicReference<java.util.concurrent.ExecutorService>)executorField.get(null);
        java.util.concurrent.ExecutorService originalExecutor = executorRef.get();

        try {
            // Shut down the executor to cause RejectedExecutionException
            originalExecutor.shutdownNow();
            originalExecutor.awaitTermination(1, TimeUnit.SECONDS);

            // Set a valid encrypt key to trigger the warmUpKeyCache path
            String oldProp = System.getProperty("myjpa.encrypt.key");
            try {
                System.setProperty("myjpa.encrypt.key", "test-key-12345678");
                MyJpaPlusProperties props = new MyJpaPlusProperties();
                // This should trigger RejectedExecutionException in warmUpKeyCache
                assertDoesNotThrow(() -> new MyJpaPlusAutoConfiguration.MyJpaPlusConfigInitializer(props, null));
            } finally {
                if (oldProp != null) {
                    System.setProperty("myjpa.encrypt.key", oldProp);
                } else {
                    System.clearProperty("myjpa.encrypt.key");
                }
            }
        } finally {
            // Restore the executor
            executorRef.set(originalExecutor);
        }
    }

    // ---- ModuleCompatibilityChecker: debug logging ----

    @Test
    void moduleCompatibilityChecker_debugLogging() {
        Logger logger = (Logger)LoggerFactory
            .getLogger("com.zsubera.jpa.autoconfigure.MyJpaPlusAutoConfiguration$ModuleCompatibilityChecker");
        Level oldLevel = logger.getLevel();
        try {
            logger.setLevel(Level.DEBUG);
            MyJpaPlusAutoConfiguration.ModuleCompatibilityChecker checker =
                new MyJpaPlusAutoConfiguration.ModuleCompatibilityChecker();
            assertDoesNotThrow(checker::check);
        } finally {
            logger.setLevel(oldLevel);
        }
    }

    // ---- SlowQueryDataSourceProxy: isWrapped ----

    @Test
    void dataSourceProxy_isAlreadyWrapped_reflection() throws Exception {
        SqlSlowQueryInterceptor interceptor = context.getBean(SqlSlowQueryInterceptor.class);
        SlowQueryDataSourceProxyPostProcessor processor = new SlowQueryDataSourceProxyPostProcessor(1000L);

        // Test with non-proxy DataSource
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
        assertFalse(SlowQueryDataSourceProxy.isWrapped(rawDs));

        // Test with JDK proxy (not DataSourceProxyHandler)
        DataSource jdkProxy = (DataSource)Proxy.newProxyInstance(getClass().getClassLoader(),
            new Class<?>[] {DataSource.class}, (p, m, args) -> null);
        assertFalse(SlowQueryDataSourceProxy.isWrapped(jdkProxy));

        // Test with wrapped DataSource (has DataSourceProxyHandler)
        DataSource wrapped = SlowQueryDataSourceProxy.wrap(rawDs, 1000L);
        assertTrue(SlowQueryDataSourceProxy.isWrapped(wrapped));
    }
}
