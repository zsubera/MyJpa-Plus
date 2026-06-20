package com.zsubera.jpa.autoconfigure;

import static org.junit.jupiter.api.Assertions.*;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.zsubera.jpa.repository.DefaultMyJpaRepository;
import com.zsubera.jpa.monitor.SqlSlowQueryInterceptor;
import com.zsubera.jpa.template.MyJpaTemplateOperations;
import com.zsubera.jpa.template.QueryCacheManager;
import com.zsubera.jpa.template.CacheInvalidationListener;
import com.zsubera.jpa.annotation.AuditEntityListener;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

@SpringBootTest(classes = AutoconfigureIntegrationTest.TestConfig.class)
class AutoconfigureIntegrationTest {

    @org.springframework.boot.autoconfigure.SpringBootApplication
    @org.springframework.boot.autoconfigure.EnableAutoConfiguration
    static class TestConfig {}

    @Autowired
    private ApplicationContext context;

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

    // ---- Context loads ----

    @Test
    void contextLoads() {
        assertNotNull(context);
    }

    // ---- All beans exist ----

    @Test
    void myJpaPlusGlobalConfig_exists() {
        MyJpaPlusGlobalConfig config = context.getBean(MyJpaPlusGlobalConfig.class);
        assertNotNull(config);
        assertTrue(config.isSoftDeleteAutoFilter());
        assertTrue(config.isBlockUnconditionalDelete());
    }

    @Test
    void myJpaTemplate_exists() {
        MyJpaTemplateOperations template = context.getBean(MyJpaTemplateOperations.class);
        assertNotNull(template);
    }

    @Test
    void queryCacheManager_exists() {
        QueryCacheManager manager = context.getBean(QueryCacheManager.class);
        assertNotNull(manager);
    }

    @Test
    void cacheInvalidationListener_exists() {
        CacheInvalidationListener listener = context.getBean(CacheInvalidationListener.class);
        assertNotNull(listener);
    }

    @Test
    void auditEntityListener_exists() {
        AuditEntityListener listener = context.getBean(AuditEntityListener.class);
        assertNotNull(listener);
    }

    @Test
    void softDeleteFilterBean_exists() {
        SoftDeleteFilterBean bean = context.getBean(SoftDeleteFilterBean.class);
        assertNotNull(bean);
        assertTrue(bean.hasSoftDeleteField(com.zsubera.jpa.spec.SoftDeleteTestEntity.class));
    }

    // ---- GlobalConfigHolder ----

    @Test
    void globalConfigHolder_isConfigured() {
        assertNotNull(GlobalConfigHolder.getConfig());
    }

    // ---- MyJpaPlusConfigInitializer: debug logging path ----

    @Test
    void configInitializer_debugLogging() {
        Logger logger = (Logger) LoggerFactory.getLogger("com.zsubera.jpa.autoconfigure.MyJpaPlusAutoConfiguration");
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
        Logger logger = (Logger) LoggerFactory.getLogger("com.zsubera.jpa.autoconfigure.MyJpaPlusAutoConfiguration");
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

    // ---- SecurityContextAuditUserProvider ----

    @Test
    void securityContextAuditUserProvider_returnsAnonymous() {
        MyJpaPlusAutoConfiguration.SecurityContextAuditUserProvider provider =
            new MyJpaPlusAutoConfiguration.SecurityContextAuditUserProvider();
        assertEquals("ANONYMOUS", provider.getCurrentUser());
    }

    // ---- DataSourceSlowQueryProxyPostProcessor ----

    @Test
    void dataSourceProxy_nonDataSource_returnsSame() {
        SqlSlowQueryInterceptor interceptor = new SqlSlowQueryInterceptor(1000);
        MyJpaPlusAutoConfiguration.DataSourceSlowQueryProxyPostProcessor processor =
            new MyJpaPlusAutoConfiguration.DataSourceSlowQueryProxyPostProcessor(interceptor);
        String notDataSource = "not a datasource";
        assertSame(notDataSource, processor.postProcessAfterInitialization(notDataSource, "test"));
    }

    @Test
    void dataSourceProxy_alreadyWrapped_skipsDoubleWrap() {
        SqlSlowQueryInterceptor interceptor = new SqlSlowQueryInterceptor(1000);
        MyJpaPlusAutoConfiguration.DataSourceSlowQueryProxyPostProcessor processor =
            new MyJpaPlusAutoConfiguration.DataSourceSlowQueryProxyPostProcessor(interceptor);

        DataSource realDataSource = context.getBean(DataSource.class);
        DataSource wrapped = interceptor.wrapDataSource(realDataSource);
        Object result = processor.postProcessAfterInitialization(wrapped, "ds");
        assertSame(wrapped, result, "Should not double-wrap already wrapped DataSource");
    }

    @Test
    void dataSourceProxy_realDataSource_wrapsIt() {
        SqlSlowQueryInterceptor interceptor = new SqlSlowQueryInterceptor(1000);
        MyJpaPlusAutoConfiguration.DataSourceSlowQueryProxyPostProcessor processor =
            new MyJpaPlusAutoConfiguration.DataSourceSlowQueryProxyPostProcessor(interceptor);

        DataSource realDataSource = context.getBean(DataSource.class);
        Object result = processor.postProcessAfterInitialization(realDataSource, "ds");
        assertNotSame(realDataSource, result, "Should wrap the real DataSource");
    }

    @Test
    void dataSourceProxy_jdkProxy_skipsWrap() {
        SqlSlowQueryInterceptor interceptor = new SqlSlowQueryInterceptor(1000);
        MyJpaPlusAutoConfiguration.DataSourceSlowQueryProxyPostProcessor processor =
            new MyJpaPlusAutoConfiguration.DataSourceSlowQueryProxyPostProcessor(interceptor);

        DataSource proxy = (DataSource) java.lang.reflect.Proxy.newProxyInstance(
            getClass().getClassLoader(),
            new Class<?>[]{DataSource.class},
            (p, m, args) -> null);
        Object result = processor.postProcessAfterInitialization(proxy, "ds");
        assertSame(proxy, result, "Should not wrap JDK proxy DataSource");
    }

    // ---- SoftDeleteFilterBean: debug logging ----

    @Test
    void softDeleteFilterBean_afterPropertiesSet_debugLogging() {
        Logger logger = (Logger) LoggerFactory.getLogger(SoftDeleteFilterBean.class);
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
        Logger logger = (Logger) LoggerFactory.getLogger(SoftDeleteFilterBean.class);
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

    // ---- RepositoryBaseClassPostProcessor: debug logging ----

    @Test
    void repositoryBaseClassPostProcessor_debugLogging() {
        Logger logger = (Logger) LoggerFactory.getLogger(
            "com.zsubera.jpa.autoconfigure.MyJpaPlusAutoConfiguration$RepositoryBaseClassPostProcessor");
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

    // ---- ModuleCompatibilityChecker ----

    @Test
    void moduleCompatibilityChecker_check() {
        MyJpaPlusAutoConfiguration.ModuleCompatibilityChecker checker =
            new MyJpaPlusAutoConfiguration.ModuleCompatibilityChecker();
        assertDoesNotThrow(checker::check);
    }

    // ---- onContextClosed ----

    @Test
    void onContextClosed_cleansUp() {
        MyJpaPlusAutoConfiguration config = new MyJpaPlusAutoConfiguration(
            context.getBean(MyJpaPlusProperties.class));
        assertDoesNotThrow(() -> config.onContextClosed(null));
    }

    // ---- All bean methods ----

    @Test
    void allBeanMethods() {
        MyJpaPlusProperties props = context.getBean(MyJpaPlusProperties.class);
        MyJpaPlusAutoConfiguration config = new MyJpaPlusAutoConfiguration(props);
        assertNotNull(config.myJpaPlusGlobalConfig(props));
        assertNotNull(config.myJpaTemplate(props));
        assertNotNull(config.queryCacheManager());
        assertNotNull(config.auditEntityListener());
        assertNotNull(config.cacheInvalidationListener(config.queryCacheManager()));
    }

    // ---- MyJpaPlusProperties: validation ----

    @Test
    void properties_validate_passes() {
        MyJpaPlusProperties props = context.getBean(MyJpaPlusProperties.class);
        assertDoesNotThrow(props::validate);
    }

    @Test
    void properties_setters_coverage() {
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

    // ---- MyJpaPlusGlobalConfig: all setters ----

    @Test
    void globalConfig_allSetters() {
        MyJpaPlusGlobalConfig config = new MyJpaPlusGlobalConfig();
        config.setSoftDeleteAutoFilter(false);
        assertFalse(config.isSoftDeleteAutoFilter());
        config.setBlockUnconditionalDelete(false);
        assertFalse(config.isBlockUnconditionalDelete());
        config.setDefaultTimeoutSeconds(60);
        assertEquals(60, config.getDefaultTimeoutSeconds());
        config.setMaxTimeoutSeconds(600);
        assertEquals(600, config.getMaxTimeoutSeconds());
        config.setMaxResults(5000);
        assertEquals(5000, config.getMaxResults());
        config.setMaxBulkOperationRows(2000);
        assertEquals(2000, config.getMaxBulkOperationRows());
        config.setDeepPaginationOffsetThreshold(50000);
        assertEquals(50000, config.getDeepPaginationOffsetThreshold());
        config.setDeepPaginationOffsetLimit(1000000);
        assertEquals(1000000, config.getDeepPaginationOffsetLimit());
    }

    @Test
    void globalConfig_maxTimeoutSeconds_invalid() {
        MyJpaPlusGlobalConfig config = new MyJpaPlusGlobalConfig();
        assertThrows(IllegalArgumentException.class, () -> config.setMaxTimeoutSeconds(0));
        assertThrows(IllegalArgumentException.class, () -> config.setMaxTimeoutSeconds(-1));
    }

    @Test
    void globalConfig_deprecated_setAutoFilterEnabled() {
        MyJpaPlusGlobalConfig config = new MyJpaPlusGlobalConfig();
        config.setAutoFilterEnabled(false);
        assertFalse(config.isSoftDeleteAutoFilter());
    }

    // ---- SqlSlowQueryInterceptor: wrapDataSource ----

    @Test
    void sqlSlowQueryInterceptor_wrapDataSource() {
        SqlSlowQueryInterceptor interceptor = new SqlSlowQueryInterceptor(1000);
        DataSource realDataSource = context.getBean(DataSource.class);
        DataSource wrapped = interceptor.wrapDataSource(realDataSource);
        assertNotNull(wrapped);
        assertNotSame(realDataSource, wrapped);
    }
}
