package com.zsubera.jpa.autoconfigure;

import static org.junit.jupiter.api.Assertions.*;

import com.zsubera.jpa.template.MyJpaTemplateOperations;
import com.zsubera.jpa.template.QueryCacheManager;
import com.zsubera.jpa.template.CacheInvalidationListener;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.data.domain.AuditorAware;

@SpringBootTest(classes = MyJpaPlusAutoConfigurationIntegrationTest.TestConfig.class)
class MyJpaPlusAutoConfigurationIntegrationTest {

    @org.springframework.boot.autoconfigure.SpringBootApplication
    @org.springframework.boot.autoconfigure.EnableAutoConfiguration
    static class TestConfig {}

    @Autowired
    private ApplicationContext context;

    @Autowired
    private MyJpaPlusProperties properties;

    @AfterEach
    void cleanup() {
        GlobalConfigHolder.setConfig(null);
    }

    @Test
    void contextLoads() {
        assertNotNull(context);
    }

    @Test
    void myJpaPlusGlobalConfig_exists() {
        MyJpaPlusGlobalConfig config = context.getBean(MyJpaPlusGlobalConfig.class);
        assertNotNull(config);
        assertTrue(config.isSoftDeleteAutoFilter());
        assertTrue(config.isBlockUnconditionalDelete());
        assertEquals(10000, config.getMaxResults());
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
    void auditorAware_exists() {
        AuditorAware<?> auditorAware = context.getBean(AuditorAware.class);
        assertNotNull(auditorAware);
    }

    @Test
    void softDeleteFilterBean_exists() {
        SoftDeleteFilterBean bean = context.getBean(SoftDeleteFilterBean.class);
        assertNotNull(bean);
        assertTrue(bean.hasSoftDeleteField(com.zsubera.jpa.spec.SoftDeleteTestEntity.class));
    }

    @Test
    void onContextClosed_doesNotThrow() {
        MyJpaPlusAutoConfiguration config = new MyJpaPlusAutoConfiguration(properties, null);
        assertDoesNotThrow(() -> config.onContextClosed(null));
    }

    @Test
    void globalConfigHolder_hasDefaultOrConfigured() {
        assertNotNull(GlobalConfigHolder.getConfig());
    }

    @Test
    void properties_areBound() {
        assertNotNull(properties);
        assertNotNull(properties.getSoftDelete());
        assertNotNull(properties.getQuery());
        assertNotNull(properties.getMonitoring());
        assertNotNull(properties.getCache());
    }
}
