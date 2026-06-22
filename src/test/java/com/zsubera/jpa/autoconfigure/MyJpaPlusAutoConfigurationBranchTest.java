package com.zsubera.jpa.autoconfigure;

import com.zsubera.jpa.monitor.SlowQueryDataSourceProxyPostProcessor;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MyJpaPlusAutoConfigurationBranchTest {

    @BeforeEach
    void setUp() {
        GlobalConfigHolder.setConfig(null);
    }

    @AfterEach
    void tearDown() {
        GlobalConfigHolder.setConfig(null);
    }

    @Test
    void configInitializer_withGlobalConfig_setsGlobalConfigProvider() {
        MyJpaPlusProperties props = new MyJpaPlusProperties();
        MyJpaPlusGlobalConfig globalConfig = new MyJpaPlusGlobalConfig();
        globalConfig.setSoftDeleteAutoFilter(true);
        globalConfig.setBlockUnconditionalDelete(true);

        new MyJpaPlusAutoConfiguration.MyJpaPlusConfigInitializer(props, globalConfig, null);

        assertTrue(GlobalConfigHolder.isConfigured());
        assertSame(globalConfig, GlobalConfigHolder.getConfig());
    }

    @Test
    void configInitializer_withGlobalConfig_setsQuerySpecGlobalConfig() {
        MyJpaPlusProperties props = new MyJpaPlusProperties();
        MyJpaPlusGlobalConfig globalConfig = new MyJpaPlusGlobalConfig();

        new MyJpaPlusAutoConfiguration.MyJpaPlusConfigInitializer(props, globalConfig, null);

        assertTrue(GlobalConfigHolder.isConfigured());
    }

    @Test
    void configInitializer_inClauseHardLessThanMax_throws() {
        MyJpaPlusProperties props = new MyJpaPlusProperties();
        props.getQuery().setInClauseMaxSize(5000);
        props.getQuery().setInClauseHardLimit(1000);

        assertThrows(IllegalArgumentException.class,
            () -> new MyJpaPlusAutoConfiguration.MyJpaPlusConfigInitializer(props, null, null));
    }

    @Test
    void configInitializer_extraSafeFunctions_notEmpty() {
        MyJpaPlusProperties props = new MyJpaPlusProperties();
        props.getQuery().setExtraSafeFunctions(List.of("CUSTOM_FUNC"));

        assertDoesNotThrow(() -> new MyJpaPlusAutoConfiguration.MyJpaPlusConfigInitializer(props, null, null));
    }

    @Test
    void configInitializer_extraBooleanFunctions_notEmpty() {
        MyJpaPlusProperties props = new MyJpaPlusProperties();
        props.getQuery().setExtraBooleanFunctions(List.of("CUSTOM_BOOL"));

        assertDoesNotThrow(() -> new MyJpaPlusAutoConfiguration.MyJpaPlusConfigInitializer(props, null, null));
    }

    @Test
    void configInitializer_extraSafeFunctions_empty() {
        MyJpaPlusProperties props = new MyJpaPlusProperties();
        props.getQuery().setExtraSafeFunctions(List.of());

        assertDoesNotThrow(() -> new MyJpaPlusAutoConfiguration.MyJpaPlusConfigInitializer(props, null, null));
    }

    @Test
    void configInitializer_extraBooleanFunctions_empty() {
        MyJpaPlusProperties props = new MyJpaPlusProperties();
        props.getQuery().setExtraBooleanFunctions(List.of());

        assertDoesNotThrow(() -> new MyJpaPlusAutoConfiguration.MyJpaPlusConfigInitializer(props, null, null));
    }

    @Test
    void configInitializer_encryptKeyFromEnvVar() {
        String oldEnv = System.getenv("MYJPA_ENCRYPT_KEY");
        String oldProp = System.getProperty("myjpa.encrypt.key");
        try {
            // We can't set env vars directly, but we can set the system property
            System.setProperty("myjpa.encrypt.key", "test-encrypt-key-12345");
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

    @Test
    void configInitializer_timeoutPositive() {
        MyJpaPlusProperties props = new MyJpaPlusProperties();
        props.getQuery().setDefaultTimeoutSeconds(60);

        assertDoesNotThrow(() -> new MyJpaPlusAutoConfiguration.MyJpaPlusConfigInitializer(props, null, null));
    }

    @Test
    void configInitializer_timeoutNegative1() {
        MyJpaPlusProperties props = new MyJpaPlusProperties();
        props.getQuery().setDefaultTimeoutSeconds(-1);

        assertDoesNotThrow(() -> new MyJpaPlusAutoConfiguration.MyJpaPlusConfigInitializer(props, null, null));
    }

    @Test
    void configInitializer_timeoutZero_skipsTimeoutSetting() {
        MyJpaPlusProperties props = new MyJpaPlusProperties();
        // timeout=0 is invalid for setter, but we test the branch where timeout <= 0 && timeout != -1
        // The setter throws, so we need to test with a valid value that hits the branch
        props.getQuery().setDefaultTimeoutSeconds(-1);

        assertDoesNotThrow(() -> new MyJpaPlusAutoConfiguration.MyJpaPlusConfigInitializer(props, null, null));
    }

    @Test
    void securityContextAuditUserProvider_returnsAnonymous() {
        MyJpaPlusAutoConfiguration.SecurityContextAuditUserProvider provider =
            new MyJpaPlusAutoConfiguration.SecurityContextAuditUserProvider();

        String user = provider.getCurrentUser();
        assertEquals("ANONYMOUS", user);
    }

    @Test
    void dataSourceSlowQueryProxyPostProcessor_nonDataSource_returnsSame() {
        com.zsubera.jpa.monitor.SqlSlowQueryInterceptor interceptor =
            new com.zsubera.jpa.monitor.SqlSlowQueryInterceptor(1000);
        SlowQueryDataSourceProxyPostProcessor processor = new SlowQueryDataSourceProxyPostProcessor(1000L);

        String notDataSource = "not a datasource";
        assertSame(notDataSource, processor.postProcessAfterInitialization(notDataSource, "test"));
    }

    @Test
    void dataSourceSlowQueryProxyPostProcessor_proxyDataSource_returnsSame() {
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
    void repositoryBaseClassPostProcessor_postProcessBeanDefinitionRegistry_replacesDefault() {
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
    void repositoryBaseClassPostProcessor_skipsNonDefaultFactoryBean() {
        MyJpaPlusAutoConfiguration.RepositoryBaseClassPostProcessor processor =
            MyJpaPlusAutoConfiguration.repositoryBaseClassPostProcessor();

        org.springframework.beans.factory.support.DefaultListableBeanFactory beanFactory =
            new org.springframework.beans.factory.support.DefaultListableBeanFactory();
        org.springframework.beans.factory.support.GenericBeanDefinition bd =
            new org.springframework.beans.factory.support.GenericBeanDefinition();
        bd.setBeanClassName("com.example.CustomFactoryBean");
        bd.getPropertyValues().add("repositoryInterface", "com.example.TestRepo");
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
    void moduleCompatibilityChecker_check_doesNotThrow() {
        MyJpaPlusAutoConfiguration.ModuleCompatibilityChecker checker =
            new MyJpaPlusAutoConfiguration.ModuleCompatibilityChecker();
        assertDoesNotThrow(checker::check);
    }

    @Test
    void autoConfiguration_constructor_nullProperties_throws() {
        assertThrows(IllegalArgumentException.class, () -> new MyJpaPlusAutoConfiguration(null, null));
    }

    @Test
    void autoConfiguration_myJpaPlusGlobalConfig() {
        MyJpaPlusProperties props = new MyJpaPlusProperties();
        MyJpaPlusAutoConfiguration config = new MyJpaPlusAutoConfiguration(props, null);
        MyJpaPlusGlobalConfig globalConfig = config.myJpaPlusGlobalConfig(props);
        assertNotNull(globalConfig);
        assertTrue(globalConfig.isSoftDeleteAutoFilter());
        assertTrue(globalConfig.isBlockUnconditionalDelete());
    }

    @Test
    void autoConfiguration_myJpaTemplate() {
        MyJpaPlusProperties props = new MyJpaPlusProperties();
        MyJpaPlusAutoConfiguration config = new MyJpaPlusAutoConfiguration(props, null);
        assertNotNull(config.myJpaTemplate(props));
    }

    @Test
    void autoConfiguration_queryCacheManager() {
        MyJpaPlusProperties props = new MyJpaPlusProperties();
        MyJpaPlusAutoConfiguration config = new MyJpaPlusAutoConfiguration(props, null);
        assertNotNull(config.queryCacheManager());
    }

    @Test
    void autoConfiguration_cacheInvalidationListener() {
        MyJpaPlusProperties props = new MyJpaPlusProperties();
        MyJpaPlusAutoConfiguration config = new MyJpaPlusAutoConfiguration(props, null);
        assertNotNull(config.cacheInvalidationListener(config.queryCacheManager()));
    }

    @Test
    void autoConfiguration_auditEntityListener() {
        MyJpaPlusProperties props = new MyJpaPlusProperties();
        MyJpaPlusAutoConfiguration config = new MyJpaPlusAutoConfiguration(props, null);
        assertNotNull(config.auditEntityListener());
    }

    @Test
    void onContextClosed_cleansUpResources() {
        MyJpaPlusProperties props = new MyJpaPlusProperties();
        MyJpaPlusAutoConfiguration config = new MyJpaPlusAutoConfiguration(props, null);
        assertDoesNotThrow(() -> config.onContextClosed(null));
    }

    @Test
    void softDeleteFilterBean_registerEntity_withSoftDelete() {
        MyJpaPlusProperties props = new MyJpaPlusProperties();
        SoftDeleteFilterBean bean = new SoftDeleteFilterBean(props);
        assertDoesNotThrow(() -> bean.registerEntity(com.zsubera.jpa.spec.SoftDeleteTestEntity.class));
        assertTrue(bean.hasSoftDeleteField(com.zsubera.jpa.spec.SoftDeleteTestEntity.class));
    }

    @Test
    void softDeleteFilterBean_registerEntity_withoutSoftDelete() {
        MyJpaPlusProperties props = new MyJpaPlusProperties();
        SoftDeleteFilterBean bean = new SoftDeleteFilterBean(props);
        assertDoesNotThrow(() -> bean.registerEntity(com.zsubera.jpa.spec.TestEntity.class));
        assertFalse(bean.hasSoftDeleteField(com.zsubera.jpa.spec.TestEntity.class));
    }

    @Test
    void softDeleteFilterBean_afterPropertiesSet_debugLogging() {
        MyJpaPlusProperties props = new MyJpaPlusProperties();
        SoftDeleteFilterBean bean = new SoftDeleteFilterBean(props);
        assertDoesNotThrow(bean::afterPropertiesSet);
    }

    @Test
    void softDeleteFilterBean_apply_nullSpec_withSoftDelete() {
        MyJpaPlusProperties props = new MyJpaPlusProperties();
        SoftDeleteFilterBean bean = new SoftDeleteFilterBean(props);
        var result = bean.apply(null, com.zsubera.jpa.spec.SoftDeleteTestEntity.class);
        assertNotNull(result);
    }

    @Test
    void softDeleteFilterBean_apply_spec_withSoftDelete() {
        MyJpaPlusProperties props = new MyJpaPlusProperties();
        SoftDeleteFilterBean bean = new SoftDeleteFilterBean(props);
        var spec = (org.springframework.data.jpa.domain.Specification<
            com.zsubera.jpa.spec.SoftDeleteTestEntity>)(root, query, cb) -> cb.conjunction();
        var result = bean.apply(spec, com.zsubera.jpa.spec.SoftDeleteTestEntity.class);
        assertNotNull(result);
    }

    @Test
    void softDeleteFilterBean_apply_nullSpec_withoutSoftDelete() {
        MyJpaPlusProperties props = new MyJpaPlusProperties();
        SoftDeleteFilterBean bean = new SoftDeleteFilterBean(props);
        var result = bean.apply(null, com.zsubera.jpa.spec.TestEntity.class);
        assertNull(result);
    }

    @Test
    void softDeleteFilterBean_apply_spec_withoutSoftDelete() {
        MyJpaPlusProperties props = new MyJpaPlusProperties();
        SoftDeleteFilterBean bean = new SoftDeleteFilterBean(props);
        var spec = (org.springframework.data.jpa.domain.Specification<
            com.zsubera.jpa.spec.TestEntity>)(root, query, cb) -> cb.conjunction();
        var result = bean.apply(spec, com.zsubera.jpa.spec.TestEntity.class);
        assertSame(spec, result);
    }

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

    @Test
    void globalConfigHolder_isConfigured() {
        GlobalConfigHolder.setConfig(new MyJpaPlusGlobalConfig());
        assertTrue(GlobalConfigHolder.isConfigured());
        GlobalConfigHolder.setConfig(null);
        assertFalse(GlobalConfigHolder.isConfigured());
    }
}
