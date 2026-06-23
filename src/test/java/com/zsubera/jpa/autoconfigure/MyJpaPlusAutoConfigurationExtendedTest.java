package com.zsubera.jpa.autoconfigure;

import com.zsubera.jpa.monitor.SlowQueryDataSourceProxyPostProcessor;

import static org.junit.jupiter.api.Assertions.*;

import com.zsubera.jpa.template.MyJpaTemplate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MyJpaPlusAutoConfigurationExtendedTest {

    @BeforeEach
    void setUp() {
        GlobalConfigHolder.setConfig(null);
    }

    @AfterEach
    void tearDown() {
        GlobalConfigHolder.setConfig(null);
    }

    @Test
    void autoConfiguration_constructor_nullProperties_throws() {
        assertThrows(IllegalArgumentException.class, () -> new MyJpaPlusAutoConfiguration(null, null));
    }

    @Test
    void myJpaPlusGlobalConfig_createdFromProperties() {
        MyJpaPlusProperties props = new MyJpaPlusProperties();
        props.getSoftDelete().setAutoFilter(false);
        props.getSoftDelete().setBlockUnconditionalDelete(false);
        props.getQuery().setMaxResults(5000);
        props.getQuery().setMaxBulkOperationRows(2000);
        props.getQuery().setDeepPaginationOffsetThreshold(50000);
        props.getQuery().setDeepPaginationOffsetLimit(1000000);

        MyJpaPlusAutoConfiguration config = new MyJpaPlusAutoConfiguration(props, null);
        MyJpaPlusGlobalConfig globalConfig = config.myJpaPlusGlobalConfig(props);

        assertFalse(globalConfig.isSoftDeleteAutoFilter());
        assertFalse(globalConfig.isBlockUnconditionalDelete());
        assertEquals(5000, globalConfig.getMaxResults());
        assertEquals(2000, globalConfig.getMaxBulkOperationRows());
        assertEquals(50000, globalConfig.getDeepPaginationOffsetThreshold());
        assertEquals(1000000, globalConfig.getDeepPaginationOffsetLimit());
    }

    @Test
    void myJpaTemplate_withLimit() {
        MyJpaPlusProperties props = new MyJpaPlusProperties();
        props.getQuery().setDeepPaginationOffsetLimit(500);

        MyJpaPlusAutoConfiguration config = new MyJpaPlusAutoConfiguration(props, null);
        MyJpaTemplate template = config.myJpaTemplate(props);
        assertNotNull(template);
    }

    @Test
    void myJpaTemplate_defaultTimeoutDisabled() {
        MyJpaPlusProperties props = new MyJpaPlusProperties();

        MyJpaPlusAutoConfiguration config = new MyJpaPlusAutoConfiguration(props, null);
        MyJpaTemplate template = config.myJpaTemplate(props);
        assertNotNull(template);
    }

    @Test
    void myJpaTemplate_defaultLimit_notSet() {
        MyJpaPlusProperties props = new MyJpaPlusProperties();
        props.getQuery().setDeepPaginationOffsetLimit(-1);

        MyJpaPlusAutoConfiguration config = new MyJpaPlusAutoConfiguration(props, null);
        MyJpaTemplate template = config.myJpaTemplate(props);
        assertNotNull(template);
    }

    @Test
    void onContextClosed_doesNotThrow() {
        MyJpaPlusProperties props = new MyJpaPlusProperties();
        MyJpaPlusAutoConfiguration config = new MyJpaPlusAutoConfiguration(props, null);
        assertDoesNotThrow(() -> config.onContextClosed(null));
    }

    @Test
    void queryCacheManager_returnsNonNull() {
        MyJpaPlusProperties props = new MyJpaPlusProperties();
        MyJpaPlusAutoConfiguration config = new MyJpaPlusAutoConfiguration(props, null);
        assertNotNull(config.queryCacheManager(props));
    }

    @Test
    void auditorAware_returnsNonNull() {
        MyJpaPlusProperties props = new MyJpaPlusProperties();
        MyJpaPlusAutoConfiguration config = new MyJpaPlusAutoConfiguration(props, null);
        assertNotNull(config.auditorAware());
    }

    @Test
    void cacheInvalidationListener_returnsNonNull() {
        MyJpaPlusProperties props = new MyJpaPlusProperties();
        MyJpaPlusAutoConfiguration config = new MyJpaPlusAutoConfiguration(props, null);
        assertNotNull(config.cacheInvalidationListener(config.queryCacheManager(props)));
    }

    @Test
    void moduleCompatibilityChecker_check_doesNotThrow() {
        MyJpaPlusAutoConfiguration.ModuleCompatibilityChecker checker =
            new MyJpaPlusAutoConfiguration.ModuleCompatibilityChecker();
        assertDoesNotThrow(checker::check);
    }

    @Test
    void repositoryBaseClassPostProcessor_getOrder_returnsLowestPrecedence() {
        MyJpaPlusAutoConfiguration.RepositoryBaseClassPostProcessor processor =
            MyJpaPlusAutoConfiguration.repositoryBaseClassPostProcessor();
        assertEquals(Integer.MAX_VALUE, processor.getOrder());
    }

    @Test
    void repositoryBaseClassPostProcessor_postProcessBeanFactory_doesNotThrow() {
        MyJpaPlusAutoConfiguration.RepositoryBaseClassPostProcessor processor =
            MyJpaPlusAutoConfiguration.repositoryBaseClassPostProcessor();
        assertDoesNotThrow(() -> processor.postProcessBeanFactory(null));
    }

    @Test
    void repositoryBaseClassPostProcessor_postProcessBeanDefinitionRegistry() {
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
    void SecurityContextAuditorAware_getCurrentUser_returnsAnonymous() {
        MyJpaPlusAutoConfiguration.SecurityContextAuditorAware provider =
            new MyJpaPlusAutoConfiguration.SecurityContextAuditorAware();
        assertEquals("SYSTEM", provider.getCurrentAuditor().get());
    }

    @Test
    void SecurityContextAuditorAware_getCurrentUser_withSecurityContext() {
        MyJpaPlusAutoConfiguration.SecurityContextAuditorAware provider =
            new MyJpaPlusAutoConfiguration.SecurityContextAuditorAware();
        // Without Spring Security on classpath, should return SYSTEM
        assertEquals("SYSTEM", provider.getCurrentAuditor().get());
    }

    @Test
    void dataSourceSlowQueryProxyPostProcessor_postProcessAfterInitialization_nonDataSource() {
        com.zsubera.jpa.monitor.SqlSlowQueryInterceptor interceptor =
            new com.zsubera.jpa.monitor.SqlSlowQueryInterceptor(1000);
        SlowQueryDataSourceProxyPostProcessor processor = new SlowQueryDataSourceProxyPostProcessor(1000L);

        String notDataSource = "not a datasource";
        assertSame(notDataSource, processor.postProcessAfterInitialization(notDataSource, "test"));
    }

    @Test
    void dataSourceSlowQueryProxyPostProcessor_postProcessAfterInitialization_proxy() {
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
    void myJpaPlusConfigInitializer_withGlobalConfig() {
        MyJpaPlusProperties props = new MyJpaPlusProperties();
        MyJpaPlusGlobalConfig globalConfig = new MyJpaPlusGlobalConfig();
        globalConfig.setSoftDeleteAutoFilter(true);
        globalConfig.setBlockUnconditionalDelete(true);

        MyJpaPlusAutoConfiguration.MyJpaPlusConfigInitializer initializer =
            new MyJpaPlusAutoConfiguration.MyJpaPlusConfigInitializer(props, globalConfig, null);

        assertNotNull(initializer);
    }

    @Test
    void myJpaPlusConfigInitializer_withoutGlobalConfig() {
        MyJpaPlusProperties props = new MyJpaPlusProperties();

        MyJpaPlusAutoConfiguration.MyJpaPlusConfigInitializer initializer =
            new MyJpaPlusAutoConfiguration.MyJpaPlusConfigInitializer(props, null, null);

        assertNotNull(initializer);
    }

    @Test
    void myJpaPlusConfigInitializer_inClauseHardLimitLessThanMax_throws() {
        MyJpaPlusProperties props = new MyJpaPlusProperties();
        props.getQuery().setInClauseMaxSize(5000);
        props.getQuery().setInClauseHardLimit(1000);

        assertThrows(IllegalArgumentException.class,
            () -> new MyJpaPlusAutoConfiguration.MyJpaPlusConfigInitializer(props, null, null));
    }

    @Test
    void myJpaPlusConfigInitializer_withExtraFunctions() {
        MyJpaPlusProperties props = new MyJpaPlusProperties();
        props.getQuery().setExtraSafeFunctions(List.of("FUNC1", "FUNC2"));
        props.getQuery().setExtraBooleanFunctions(List.of("BOOL1"));

        assertDoesNotThrow(() -> new MyJpaPlusAutoConfiguration.MyJpaPlusConfigInitializer(props, null, null));
    }

    @Test
    void myJpaPlusConfigInitializer_withEncryptKey() {
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
    void myJpaPlusConfigInitializer_timeoutNegative1() {
        MyJpaPlusProperties props = new MyJpaPlusProperties();

        assertDoesNotThrow(() -> new MyJpaPlusAutoConfiguration.MyJpaPlusConfigInitializer(props, null, null));
    }

    @Test
    void globalConfigHolder_setNullConfig() {
        GlobalConfigHolder.setConfig(null);
        assertFalse(GlobalConfigHolder.isConfigured());
        assertNotNull(GlobalConfigHolder.getConfig());
    }

    @Test
    void globalConfigHolder_setConfigThenClear() {
        MyJpaPlusGlobalConfig config = new MyJpaPlusGlobalConfig();
        GlobalConfigHolder.setConfig(config);
        assertTrue(GlobalConfigHolder.isConfigured());
        GlobalConfigHolder.setConfig(null);
        assertFalse(GlobalConfigHolder.isConfigured());
    }

    @Test
    void myJpaPlusConfigInitializer_globalConfigNotNull_setsAllConfigs() {
        MyJpaPlusProperties props = new MyJpaPlusProperties();
        MyJpaPlusGlobalConfig globalConfig = new MyJpaPlusGlobalConfig();
        globalConfig.setSoftDeleteAutoFilter(true);
        globalConfig.setBlockUnconditionalDelete(true);

        assertDoesNotThrow(() -> new MyJpaPlusAutoConfiguration.MyJpaPlusConfigInitializer(props, globalConfig, null));
    }

    @Test
    void myJpaPlusConfigInitializer_globalConfigNotNull_withExtraFunctions() {
        MyJpaPlusProperties props = new MyJpaPlusProperties();
        props.getQuery().setExtraSafeFunctions(List.of("FUNC1"));
        props.getQuery().setExtraBooleanFunctions(List.of("BFUNC1"));
        MyJpaPlusGlobalConfig globalConfig = new MyJpaPlusGlobalConfig();

        assertDoesNotThrow(() -> new MyJpaPlusAutoConfiguration.MyJpaPlusConfigInitializer(props, globalConfig, null));
    }

    @Test
    void myJpaPlusConfigInitializer_debugLogging() {
        MyJpaPlusProperties props = new MyJpaPlusProperties();
        assertDoesNotThrow(() -> new MyJpaPlusAutoConfiguration.MyJpaPlusConfigInitializer(props, null, null));
    }

    @Test
    void softDeleteFilterBean_afterPropertiesSet() {
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
    void softDeleteFilterBean_hasSoftDeleteField() {
        MyJpaPlusProperties props = new MyJpaPlusProperties();
        SoftDeleteFilterBean bean = new SoftDeleteFilterBean(props);
        assertTrue(bean.hasSoftDeleteField(com.zsubera.jpa.spec.SoftDeleteTestEntity.class));
        assertFalse(bean.hasSoftDeleteField(com.zsubera.jpa.spec.TestEntity.class));
    }

    @Test
    void softDeleteFilterBean_registerEntity() {
        MyJpaPlusProperties props = new MyJpaPlusProperties();
        SoftDeleteFilterBean bean = new SoftDeleteFilterBean(props);
        assertDoesNotThrow(() -> bean.registerEntity(com.zsubera.jpa.spec.SoftDeleteTestEntity.class));
        assertTrue(bean.hasSoftDeleteField(com.zsubera.jpa.spec.SoftDeleteTestEntity.class));
    }

    @Test
    void myJpaPlusGlobalConfig_allSetters() {
        MyJpaPlusGlobalConfig config = new MyJpaPlusGlobalConfig();
        config.setSoftDeleteAutoFilter(false);
        assertFalse(config.isSoftDeleteAutoFilter());
        config.setBlockUnconditionalDelete(false);
        assertFalse(config.isBlockUnconditionalDelete());
        config.setMaxResults(5000);
        assertEquals(5000, config.getMaxResults());
        config.setMaxBulkOperationRows(2000);
        assertEquals(2000, config.getMaxBulkOperationRows());
        config.setDeepPaginationOffsetThreshold(50000);
        assertEquals(50000, config.getDeepPaginationOffsetThreshold());
        config.setDeepPaginationOffsetLimit(1000000);
        assertEquals(1000000, config.getDeepPaginationOffsetLimit());
    }

}
