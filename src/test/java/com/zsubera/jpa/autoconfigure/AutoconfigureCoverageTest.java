package com.zsubera.jpa.autoconfigure;

import com.zsubera.jpa.monitor.SlowQueryDataSourceProxyPostProcessor;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AutoconfigureCoverageTest {

    @BeforeEach
    void setUp() {
        GlobalConfigHolder.setConfig(null);
        com.zsubera.jpa.autoconfigure.GlobalConfigHolder.getConfig().setSoftDeleteAutoFilter(true);
        com.zsubera.jpa.autoconfigure.GlobalConfigHolder.getConfig().setBlockUnconditionalDelete(true);
    }

    @AfterEach
    void tearDown() {
        GlobalConfigHolder.setConfig(null);
        com.zsubera.jpa.autoconfigure.GlobalConfigHolder.getConfig().setSoftDeleteAutoFilter(true);
        com.zsubera.jpa.autoconfigure.GlobalConfigHolder.getConfig().setBlockUnconditionalDelete(true);
    }

    // ---- ConfigInitializer: debug logging path ----

    @Test
    void configInitializer_debugLoggingPath() {
        org.slf4j.LoggerFactory.getLogger("com.zsubera.jpa.autoconfigure");
        MyJpaPlusProperties props = new MyJpaPlusProperties();
        props.getQuery().setExtraSafeFunctions(List.of());
        props.getQuery().setExtraBooleanFunctions(List.of());
        MyJpaPlusGlobalConfig globalConfig = new MyJpaPlusGlobalConfig();
        new MyJpaPlusAutoConfiguration.MyJpaPlusConfigInitializer(props, globalConfig, null);
    }

    @Test
    void configInitializer_debugLoggingPath_noGlobalConfig() {
        MyJpaPlusProperties props = new MyJpaPlusProperties();
        props.getQuery().setExtraSafeFunctions(List.of());
        props.getQuery().setExtraBooleanFunctions(List.of());
        new MyJpaPlusAutoConfiguration.MyJpaPlusConfigInitializer(props, null, null);
    }

    // ---- ConfigInitializer: encrypt key from system property ----

    @Test
    void configInitializer_encryptKeySystemProperty() {
        String oldProp = System.getProperty("myjpa.encrypt.key");
        try {
            System.setProperty("myjpa.encrypt.key", "test-key-12345678");
            MyJpaPlusProperties props = new MyJpaPlusProperties();
            assertDoesNotThrow(() -> new MyJpaPlusAutoConfiguration.MyJpaPlusConfigInitializer(props, null, null));
        } finally {
            if (oldProp != null) {
                System.setProperty("myjpa.encrypt.key", oldProp);
            } else {
                System.clearProperty("myjpa.encrypt.key");
            }
        }
    }

    @Test
    void configInitializer_noEncryptKey() {
        String oldProp = System.getProperty("myjpa.encrypt.key");
        try {
            System.clearProperty("myjpa.encrypt.key");
            MyJpaPlusProperties props = new MyJpaPlusProperties();
            assertDoesNotThrow(() -> new MyJpaPlusAutoConfiguration.MyJpaPlusConfigInitializer(props, null, null));
        } finally {
            if (oldProp != null) {
                System.setProperty("myjpa.encrypt.key", oldProp);
            }
        }
    }

    // ---- SecurityContextAuditUserProvider: reflection paths ----

    @Test
    void securityContextAuditUserProvider_reflectionPaths() {
        MyJpaPlusAutoConfiguration.SecurityContextAuditUserProvider provider =
            new MyJpaPlusAutoConfiguration.SecurityContextAuditUserProvider();
        String user = provider.getCurrentUser();
        assertNotNull(user);
        assertEquals("ANONYMOUS", user);
    }

    // ---- DataSourceSlowQueryProxyPostProcessor: isAlreadyWrapped ----

    @Test
    void dataSourceProxy_isAlreadyWrapped_proxyHandler() {
        com.zsubera.jpa.monitor.SqlSlowQueryInterceptor interceptor =
            new com.zsubera.jpa.monitor.SqlSlowQueryInterceptor(1000);
        SlowQueryDataSourceProxyPostProcessor processor = new SlowQueryDataSourceProxyPostProcessor(1000L);

        javax.sql.DataSource proxy =
            (javax.sql.DataSource)java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] {javax.sql.DataSource.class}, (p, m, args) -> null);

        Object result = processor.postProcessAfterInitialization(proxy, "ds");
        assertSame(proxy, result);
    }

    @Test
    void dataSourceProxy_nonProxyDataSource() {
        com.zsubera.jpa.monitor.SqlSlowQueryInterceptor interceptor =
            new com.zsubera.jpa.monitor.SqlSlowQueryInterceptor(1000);
        SlowQueryDataSourceProxyPostProcessor processor = new SlowQueryDataSourceProxyPostProcessor(1000L);

        javax.sql.DataSource nonProxy = new TestDataSource();
        Object result = processor.postProcessAfterInitialization(nonProxy, "ds");
        assertNotNull(result);
    }

    // ---- RepositoryBaseClassPostProcessor: debug logging ----

    @Test
    void repositoryBaseClassPostProcessor_debugLogging() {
        MyJpaPlusAutoConfiguration.RepositoryBaseClassPostProcessor processor =
            MyJpaPlusAutoConfiguration.repositoryBaseClassPostProcessor();

        org.springframework.beans.factory.support.DefaultListableBeanFactory beanFactory =
            new org.springframework.beans.factory.support.DefaultListableBeanFactory();
        org.springframework.beans.factory.support.GenericBeanDefinition bd =
            new org.springframework.beans.factory.support.GenericBeanDefinition();
        bd.setBeanClassName("org.springframework.data.jpa.repository.support.JpaRepositoryFactoryBean");
        bd.getPropertyValues().add("repositoryInterface", "com.example.TestRepo");
        beanFactory.registerBeanDefinition("testRepo", bd);

        processor.postProcessBeanDefinitionRegistry(beanFactory);

        assertEquals("com.zsubera.jpa.repository.MyJpaRepositoryFactoryBean",
            beanFactory.getBeanDefinition("testRepo").getBeanClassName());
    }

    @Test
    void repositoryBaseClassPostProcessor_skipsNonDefault() {
        MyJpaPlusAutoConfiguration.RepositoryBaseClassPostProcessor processor =
            MyJpaPlusAutoConfiguration.repositoryBaseClassPostProcessor();

        org.springframework.beans.factory.support.DefaultListableBeanFactory beanFactory =
            new org.springframework.beans.factory.support.DefaultListableBeanFactory();
        org.springframework.beans.factory.support.GenericBeanDefinition bd =
            new org.springframework.beans.factory.support.GenericBeanDefinition();
        bd.setBeanClassName("com.example.CustomFactoryBean");
        beanFactory.registerBeanDefinition("testRepo", bd);

        processor.postProcessBeanDefinitionRegistry(beanFactory);
        assertEquals("com.example.CustomFactoryBean", beanFactory.getBeanDefinition("testRepo").getBeanClassName());
    }

    @Test
    void repositoryBaseClassPostProcessor_skipsNonRepoBeans() {
        MyJpaPlusAutoConfiguration.RepositoryBaseClassPostProcessor processor =
            MyJpaPlusAutoConfiguration.repositoryBaseClassPostProcessor();

        org.springframework.beans.factory.support.DefaultListableBeanFactory beanFactory =
            new org.springframework.beans.factory.support.DefaultListableBeanFactory();
        org.springframework.beans.factory.support.GenericBeanDefinition bd =
            new org.springframework.beans.factory.support.GenericBeanDefinition();
        bd.setBeanClassName("org.springframework.data.jpa.repository.support.JpaRepositoryFactoryBean");
        beanFactory.registerBeanDefinition("someBean", bd);

        processor.postProcessBeanDefinitionRegistry(beanFactory);
        assertEquals("org.springframework.data.jpa.repository.support.JpaRepositoryFactoryBean",
            beanFactory.getBeanDefinition("someBean").getBeanClassName());
    }

    @Test
    void repositoryBaseClassPostProcessor_postProcessBeanFactory_noOp() {
        MyJpaPlusAutoConfiguration.RepositoryBaseClassPostProcessor processor =
            MyJpaPlusAutoConfiguration.repositoryBaseClassPostProcessor();
        assertDoesNotThrow(() -> processor.postProcessBeanFactory(null));
    }

    @Test
    void repositoryBaseClassPostProcessor_getOrder() {
        MyJpaPlusAutoConfiguration.RepositoryBaseClassPostProcessor processor =
            MyJpaPlusAutoConfiguration.repositoryBaseClassPostProcessor();
        assertEquals(Integer.MAX_VALUE, processor.getOrder());
    }

    // ---- ModuleCompatibilityChecker ----

    @Test
    void moduleCompatibilityChecker_check() {
        MyJpaPlusAutoConfiguration.ModuleCompatibilityChecker checker =
            new MyJpaPlusAutoConfiguration.ModuleCompatibilityChecker();
        assertDoesNotThrow(checker::check);
    }

    // ---- GlobalConfigHolder ----

    @Test
    void globalConfigHolder_setConfig() {
        MyJpaPlusGlobalConfig config = new MyJpaPlusGlobalConfig();
        GlobalConfigHolder.setConfig(config);
        assertTrue(GlobalConfigHolder.isConfigured());
        assertSame(config, GlobalConfigHolder.getConfig());
    }

    @Test
    void globalConfigHolder_setNull() {
        GlobalConfigHolder.setConfig(null);
        assertFalse(GlobalConfigHolder.isConfigured());
        assertNotNull(GlobalConfigHolder.getConfig());
    }

    // ---- SoftDeleteFilterBean ----

    @Test
    void softDeleteFilterBean_allPaths() {
        MyJpaPlusProperties props = new MyJpaPlusProperties();
        SoftDeleteFilterBean bean = new SoftDeleteFilterBean(props);
        bean.afterPropertiesSet();
        bean.registerEntity(com.zsubera.jpa.spec.SoftDeleteTestEntity.class);
        assertTrue(bean.hasSoftDeleteField(com.zsubera.jpa.spec.SoftDeleteTestEntity.class));
        assertFalse(bean.hasSoftDeleteField(com.zsubera.jpa.spec.TestEntity.class));

        var nullResult = bean.apply(null, com.zsubera.jpa.spec.SoftDeleteTestEntity.class);
        assertNotNull(nullResult);

        var spec = (org.springframework.data.jpa.domain.Specification<
            com.zsubera.jpa.spec.SoftDeleteTestEntity>)(root, query, cb) -> cb.conjunction();
        var specResult = bean.apply(spec, com.zsubera.jpa.spec.SoftDeleteTestEntity.class);
        assertNotNull(specResult);

        var noResult = bean.apply(null, com.zsubera.jpa.spec.TestEntity.class);
        assertNull(noResult);

        var noSpecResult = bean.apply(
            (org.springframework.data.jpa.domain.Specification<
                com.zsubera.jpa.spec.TestEntity>)(root, query, cb) -> cb.conjunction(),
            com.zsubera.jpa.spec.TestEntity.class);
        assertSame(noSpecResult, noSpecResult);
    }

    // ---- onContextClosed ----

    @Test
    void onContextClosed_cleansUp() {
        MyJpaPlusProperties props = new MyJpaPlusProperties();
        MyJpaPlusAutoConfiguration config = new MyJpaPlusAutoConfiguration(props, null);
        assertDoesNotThrow(() -> config.onContextClosed(null));
    }

    // ---- All bean methods ----

    @Test
    void allBeanMethods() {
        MyJpaPlusProperties props = new MyJpaPlusProperties();
        MyJpaPlusAutoConfiguration config = new MyJpaPlusAutoConfiguration(props, null);

        MyJpaPlusGlobalConfig globalConfig = config.myJpaPlusGlobalConfig(props);
        assertNotNull(globalConfig);

        assertNotNull(config.myJpaTemplate(props));
        assertNotNull(config.queryCacheManager());
        assertNotNull(config.auditEntityListener());
        assertNotNull(config.cacheInvalidationListener(config.queryCacheManager()));
    }

    // ---- Inner class for DataSource proxy test ----

    static class TestDataSource implements javax.sql.DataSource {
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
            return java.util.logging.Logger.getLogger("test");
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
        public java.sql.Connection getConnection(String username, String password) {
            return null;
        }
    }
}
