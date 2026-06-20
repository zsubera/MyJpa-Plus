package com.zsubera.jpa.autoconfigure;

import static org.junit.jupiter.api.Assertions.*;

import com.zsubera.jpa.repository.DefaultMyJpaRepository;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;

class AutoconfigureExtendedCoverageTest {

    @BeforeEach
    void setUp() {
        GlobalConfigHolder.setConfig(null);
        DefaultMyJpaRepository.setAutoFilterEnabled(true);
        DefaultMyJpaRepository.setBlockUnconditionalDelete(true);
    }

    @AfterEach
    void tearDown() {
        GlobalConfigHolder.setConfig(null);
        DefaultMyJpaRepository.setAutoFilterEnabled(true);
        DefaultMyJpaRepository.setBlockUnconditionalDelete(true);
    }

    @Test
    void configInitializer_debugLogging_enabled() {
        Logger logger = (Logger)LoggerFactory.getLogger("com.zsubera.jpa.autoconfigure.MyJpaPlusAutoConfiguration");
        Level oldLevel = logger.getLevel();
        try {
            logger.setLevel(Level.DEBUG);
            MyJpaPlusProperties props = new MyJpaPlusProperties();
            props.getQuery().setExtraSafeFunctions(List.of());
            props.getQuery().setExtraBooleanFunctions(List.of());
            MyJpaPlusGlobalConfig globalConfig = new MyJpaPlusGlobalConfig();
            new MyJpaPlusAutoConfiguration.MyJpaPlusConfigInitializer(props, globalConfig);
        } finally {
            logger.setLevel(oldLevel);
        }
    }

    @Test
    void configInitializer_debugLogging_noGlobalConfig() {
        Logger logger = (Logger)LoggerFactory.getLogger("com.zsubera.jpa.autoconfigure.MyJpaPlusAutoConfiguration");
        Level oldLevel = logger.getLevel();
        try {
            logger.setLevel(Level.DEBUG);
            MyJpaPlusProperties props = new MyJpaPlusProperties();
            props.getQuery().setExtraSafeFunctions(List.of());
            props.getQuery().setExtraBooleanFunctions(List.of());
            new MyJpaPlusAutoConfiguration.MyJpaPlusConfigInitializer(props, null);
        } finally {
            logger.setLevel(oldLevel);
        }
    }

    @Test
    void softDeleteFilterBean_afterPropertiesSet_debugLogging() {
        Logger logger = (Logger)LoggerFactory.getLogger(SoftDeleteFilterBean.class);
        Level oldLevel = logger.getLevel();
        try {
            logger.setLevel(Level.DEBUG);
            MyJpaPlusProperties props = new MyJpaPlusProperties();
            SoftDeleteFilterBean bean = new SoftDeleteFilterBean(props);
            bean.afterPropertiesSet();
        } finally {
            logger.setLevel(oldLevel);
        }
    }

    @Test
    void softDeleteFilterBean_registerEntity_debugLogging() {
        Logger logger = (Logger)LoggerFactory.getLogger(SoftDeleteFilterBean.class);
        Level oldLevel = logger.getLevel();
        try {
            logger.setLevel(Level.DEBUG);
            MyJpaPlusProperties props = new MyJpaPlusProperties();
            SoftDeleteFilterBean bean = new SoftDeleteFilterBean(props);
            bean.registerEntity(com.zsubera.jpa.spec.SoftDeleteTestEntity.class);
        } finally {
            logger.setLevel(oldLevel);
        }
    }

    @Test
    void repositoryBaseClassPostProcessor_debugLogging() {
        Logger logger = (Logger)LoggerFactory
            .getLogger("com.zsubera.jpa.autoconfigure.MyJpaPlusAutoConfiguration$RepositoryBaseClassPostProcessor");
        Level oldLevel = logger.getLevel();
        try {
            logger.setLevel(Level.DEBUG);
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
        } finally {
            logger.setLevel(oldLevel);
        }
    }

    @Test
    void myJpaTemplate_withAllConfig() {
        MyJpaPlusProperties props = new MyJpaPlusProperties();
        props.getQuery().setMaxResults(5000);
        props.getQuery().setDeepPaginationOffsetThreshold(50000);
        props.getQuery().setDeepPaginationOffsetLimit(1000000);
        props.getQuery().setDefaultTimeoutSeconds(60);
        MyJpaPlusAutoConfiguration config = new MyJpaPlusAutoConfiguration(props);
        assertNotNull(config.myJpaTemplate(props));
    }

    @Test
    void myJpaTemplate_defaultConfig() {
        MyJpaPlusProperties props = new MyJpaPlusProperties();
        MyJpaPlusAutoConfiguration config = new MyJpaPlusAutoConfiguration(props);
        assertNotNull(config.myJpaTemplate(props));
    }

    @Test
    void myJpaPlusGlobalConfig_fromProperties() {
        MyJpaPlusProperties props = new MyJpaPlusProperties();
        props.getSoftDelete().setAutoFilter(false);
        props.getSoftDelete().setBlockUnconditionalDelete(false);
        props.getQuery().setDefaultTimeoutSeconds(60);
        props.getQuery().setMaxResults(5000);
        props.getQuery().setMaxBulkOperationRows(2000);
        props.getQuery().setDeepPaginationOffsetThreshold(50000);
        props.getQuery().setDeepPaginationOffsetLimit(1000000);
        MyJpaPlusAutoConfiguration config = new MyJpaPlusAutoConfiguration(props);
        MyJpaPlusGlobalConfig globalConfig = config.myJpaPlusGlobalConfig(props);
        assertFalse(globalConfig.isSoftDeleteAutoFilter());
        assertFalse(globalConfig.isBlockUnconditionalDelete());
        assertEquals(60, globalConfig.getDefaultTimeoutSeconds());
        assertEquals(5000, globalConfig.getMaxResults());
        assertEquals(2000, globalConfig.getMaxBulkOperationRows());
        assertEquals(50000, globalConfig.getDeepPaginationOffsetThreshold());
        assertEquals(1000000, globalConfig.getDeepPaginationOffsetLimit());
    }

    @Test
    void configInitializer_encryptKeyCatchException() {
        String oldProp = System.getProperty("myjpa.encrypt.key");
        try {
            System.setProperty("myjpa.encrypt.key", "invalid-key");
            MyJpaPlusProperties props = new MyJpaPlusProperties();
            assertDoesNotThrow(() -> new MyJpaPlusAutoConfiguration.MyJpaPlusConfigInitializer(props, null));
        } finally {
            if (oldProp != null) {
                System.setProperty("myjpa.encrypt.key", oldProp);
            } else {
                System.clearProperty("myjpa.encrypt.key");
            }
        }
    }

    @Test
    void configInitializer_timeoutZero() {
        MyJpaPlusProperties props = new MyJpaPlusProperties();
        props.getQuery().setDefaultTimeoutSeconds(-1);
        assertDoesNotThrow(() -> new MyJpaPlusAutoConfiguration.MyJpaPlusConfigInitializer(props, null));
    }

    @Test
    void allBeanMethods_coverage() {
        MyJpaPlusProperties props = new MyJpaPlusProperties();
        MyJpaPlusAutoConfiguration config = new MyJpaPlusAutoConfiguration(props);
        assertNotNull(config.myJpaPlusGlobalConfig(props));
        assertNotNull(config.myJpaTemplate(props));
        assertNotNull(config.queryCacheManager());
        assertNotNull(config.auditEntityListener());
        assertNotNull(config.cacheInvalidationListener(config.queryCacheManager()));
    }

    @Test
    void onContextClosed_coverage() {
        MyJpaPlusProperties props = new MyJpaPlusProperties();
        MyJpaPlusAutoConfiguration config = new MyJpaPlusAutoConfiguration(props);
        assertDoesNotThrow(() -> config.onContextClosed(null));
    }

    @Test
    void securityContextAuditUserProvider_coverage() {
        MyJpaPlusAutoConfiguration.SecurityContextAuditUserProvider provider =
            new MyJpaPlusAutoConfiguration.SecurityContextAuditUserProvider();
        assertEquals("ANONYMOUS", provider.getCurrentUser());
    }

    @Test
    void moduleCompatibilityChecker_coverage() {
        MyJpaPlusAutoConfiguration.ModuleCompatibilityChecker checker =
            new MyJpaPlusAutoConfiguration.ModuleCompatibilityChecker();
        assertDoesNotThrow(checker::check);
    }

    @Test
    void globalConfigHolder_coverage() {
        MyJpaPlusGlobalConfig config = new MyJpaPlusGlobalConfig();
        GlobalConfigHolder.setConfig(config);
        assertTrue(GlobalConfigHolder.isConfigured());
        assertSame(config, GlobalConfigHolder.getConfig());
        GlobalConfigHolder.setConfig(null);
        assertFalse(GlobalConfigHolder.isConfigured());
        assertNotNull(GlobalConfigHolder.getConfig());
    }

    @Test
    void softDeleteFilterBean_allPaths_coverage() {
        MyJpaPlusProperties props = new MyJpaPlusProperties();
        SoftDeleteFilterBean bean = new SoftDeleteFilterBean(props);
        bean.afterPropertiesSet();
        bean.registerEntity(com.zsubera.jpa.spec.SoftDeleteTestEntity.class);
        assertTrue(bean.hasSoftDeleteField(com.zsubera.jpa.spec.SoftDeleteTestEntity.class));
        assertFalse(bean.hasSoftDeleteField(com.zsubera.jpa.spec.TestEntity.class));
        assertNull(bean.apply(null, com.zsubera.jpa.spec.TestEntity.class));
        assertNotNull(bean.apply(null, com.zsubera.jpa.spec.SoftDeleteTestEntity.class));
    }

    @Test
    void myJpaPlusProperties_validate_allPaths() {
        MyJpaPlusProperties props = new MyJpaPlusProperties();
        assertDoesNotThrow(props::validate);
    }

    @Test
    void myJpaPlusProperties_setters_coverage() {
        MyJpaPlusProperties props = new MyJpaPlusProperties();
        MyJpaPlusProperties.SoftDelete sd = new MyJpaPlusProperties.SoftDelete();
        sd.setAutoFilter(false);
        sd.setBlockUnconditionalDelete(false);
        props.setSoftDelete(sd);
        MyJpaPlusProperties.Query q = new MyJpaPlusProperties.Query();
        q.setMaxResults(500);
        q.setDeepPaginationOffsetThreshold(1000);
        q.setDeepPaginationOffsetLimit(100);
        q.setInClauseMaxSize(500);
        q.setInClauseHardLimit(1000);
        q.setLambdaCacheSize(1024);
        q.setDefaultTimeoutSeconds(30);
        q.setMaxBulkOperationRows(5000);
        q.setExtraSafeFunctions(List.of("FUNC1"));
        q.setExtraBooleanFunctions(List.of("BFUNC1"));
        props.setQuery(q);
        MyJpaPlusProperties.Monitoring m = new MyJpaPlusProperties.Monitoring();
        m.setEnabled(true);
        m.setSlowQueryThresholdMs(500);
        props.setMonitoring(m);
        MyJpaPlusProperties.Cache c = new MyJpaPlusProperties.Cache();
        c.setAutoInvalidationEnabled(false);
        props.setCache(c);
    }
}
