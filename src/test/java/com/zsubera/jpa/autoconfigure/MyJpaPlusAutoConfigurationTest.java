package com.zsubera.jpa.autoconfigure;

import static org.junit.jupiter.api.Assertions.*;

import com.zsubera.jpa.template.MyJpaTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MyJpaPlusAutoConfigurationTest {

    @BeforeEach
    void setUp() {
        GlobalConfigHolder.reset();
    }

    @Test
    void properties_defaultValues() {
        MyJpaPlusProperties properties = new MyJpaPlusProperties();
        assertTrue(properties.getSoftDelete().isAutoFilter());
        assertEquals(10000, properties.getQuery().getMaxResults());
        assertEquals(100000, properties.getQuery().getDeepPaginationOffsetThreshold());
    }

    @Test
    void properties_softDelete_setterGetter() {
        MyJpaPlusProperties properties = new MyJpaPlusProperties();
        properties.getSoftDelete().setAutoFilter(false);
        assertFalse(properties.getSoftDelete().isAutoFilter());
    }

    @Test
    void properties_query_setMaxResults() {
        MyJpaPlusProperties properties = new MyJpaPlusProperties();
        properties.getQuery().setMaxResults(5000);
        assertEquals(5000, properties.getQuery().getMaxResults());
    }

    @Test
    void properties_query_setMaxResults_zero_throwsException() {
        MyJpaPlusProperties properties = new MyJpaPlusProperties();
        assertThrows(IllegalArgumentException.class, () -> properties.getQuery().setMaxResults(0));
    }

    @Test
    void properties_query_setMaxResults_negative_throwsException() {
        MyJpaPlusProperties properties = new MyJpaPlusProperties();
        assertThrows(IllegalArgumentException.class, () -> properties.getQuery().setMaxResults(-2));
    }

    @Test
    void properties_query_setDeepPaginationOffsetThreshold() {
        MyJpaPlusProperties properties = new MyJpaPlusProperties();
        properties.getQuery().setDeepPaginationOffsetThreshold(50000);
        assertEquals(50000, properties.getQuery().getDeepPaginationOffsetThreshold());
    }

    @Test
    void properties_query_setDeepPaginationOffsetThreshold_zero_throwsException() {
        MyJpaPlusProperties properties = new MyJpaPlusProperties();
        assertThrows(IllegalArgumentException.class, () -> properties.getQuery().setDeepPaginationOffsetThreshold(0));
    }

    @Test
    void properties_query_setDeepPaginationOffsetThreshold_negative_throwsException() {
        MyJpaPlusProperties properties = new MyJpaPlusProperties();
        assertThrows(IllegalArgumentException.class, () -> properties.getQuery().setDeepPaginationOffsetThreshold(-1));
    }

    @Test
    void autoConfiguration_constructor_acceptsProperties() {
        MyJpaPlusProperties properties = new MyJpaPlusProperties();
        assertDoesNotThrow(() -> new MyJpaPlusAutoConfiguration(properties, null));
    }

    @Test
    void autoConfiguration_myJpaTemplate_returnsConfiguredTemplate() {
        MyJpaPlusProperties properties = new MyJpaPlusProperties();
        properties.getQuery().setMaxResults(5000);
        properties.getQuery().setDeepPaginationOffsetThreshold(50000);
        MyJpaPlusAutoConfiguration configuration = new MyJpaPlusAutoConfiguration(properties, null);
        MyJpaTemplate template = configuration.myJpaTemplate(properties);
        assertNotNull(template);
    }

    @Test
    void properties_topLevel_setSoftDelete() {
        MyJpaPlusProperties properties = new MyJpaPlusProperties();
        MyJpaPlusProperties.SoftDelete sd = new MyJpaPlusProperties.SoftDelete();
        sd.setAutoFilter(false);
        properties.setSoftDelete(sd);
        assertFalse(properties.getSoftDelete().isAutoFilter());
    }

    @Test
    void properties_topLevel_setQuery() {
        MyJpaPlusProperties properties = new MyJpaPlusProperties();
        MyJpaPlusProperties.Query q = new MyJpaPlusProperties.Query();
        q.setMaxResults(500);
        q.setDeepPaginationOffsetThreshold(1000);
        properties.setQuery(q);
        assertEquals(500, properties.getQuery().getMaxResults());
        assertEquals(1000, properties.getQuery().getDeepPaginationOffsetThreshold());
    }

    @Test
    void properties_query_setDeepPaginationOffsetLimit() {
        MyJpaPlusProperties.Query q = new MyJpaPlusProperties.Query();
        q.setDeepPaginationOffsetLimit(500000);
        assertEquals(500000, q.getDeepPaginationOffsetLimit());
    }

    @Test
    void properties_query_setDeepPaginationOffsetLimit_disabled() {
        MyJpaPlusProperties.Query q = new MyJpaPlusProperties.Query();
        q.setDeepPaginationOffsetLimit(-1);
        assertEquals(-1, q.getDeepPaginationOffsetLimit());
    }

    @Test
    void properties_query_setDeepPaginationOffsetLimit_invalid_throws() {
        MyJpaPlusProperties.Query q = new MyJpaPlusProperties.Query();
        assertThrows(IllegalArgumentException.class, () -> q.setDeepPaginationOffsetLimit(0));
        assertThrows(IllegalArgumentException.class, () -> q.setDeepPaginationOffsetLimit(-2));
    }

    @Test
    void properties_query_setInClauseMaxSize() {
        MyJpaPlusProperties.Query q = new MyJpaPlusProperties.Query();
        q.setInClauseMaxSize(2000);
        assertEquals(2000, q.getInClauseMaxSize());
    }

    @Test
    void properties_query_setInClauseMaxSize_invalid_throws() {
        MyJpaPlusProperties.Query q = new MyJpaPlusProperties.Query();
        assertThrows(IllegalArgumentException.class, () -> q.setInClauseMaxSize(0));
    }

    @Test
    void properties_query_setInClauseHardLimit() {
        MyJpaPlusProperties.Query q = new MyJpaPlusProperties.Query();
        q.setInClauseHardLimit(10000);
        assertEquals(10000, q.getInClauseHardLimit());
    }

    @Test
    void properties_query_setInClauseHardLimit_invalid_throws() {
        MyJpaPlusProperties.Query q = new MyJpaPlusProperties.Query();
        assertThrows(IllegalArgumentException.class, () -> q.setInClauseHardLimit(0));
    }

    @Test
    void properties_query_setLambdaCacheSize() {
        MyJpaPlusProperties.Query q = new MyJpaPlusProperties.Query();
        q.setLambdaCacheSize(8192);
        assertEquals(8192, q.getLambdaCacheSize());
    }

    @Test
    void properties_query_setLambdaCacheSize_invalid_throws() {
        MyJpaPlusProperties.Query q = new MyJpaPlusProperties.Query();
        assertThrows(IllegalArgumentException.class, () -> q.setLambdaCacheSize(0));
    }

    @Test
    void properties_query_setMaxBulkOperationRows() {
        MyJpaPlusProperties.Query q = new MyJpaPlusProperties.Query();
        q.setMaxBulkOperationRows(5000);
        assertEquals(5000, q.getMaxBulkOperationRows());
    }

    @Test
    void properties_query_setMaxBulkOperationRows_zero() {
        MyJpaPlusProperties.Query q = new MyJpaPlusProperties.Query();
        q.setMaxBulkOperationRows(0);
        assertEquals(0, q.getMaxBulkOperationRows());
    }

    @Test
    void properties_query_setMaxBulkOperationRows_invalid_throws() {
        MyJpaPlusProperties.Query q = new MyJpaPlusProperties.Query();
        assertThrows(IllegalArgumentException.class, () -> q.setMaxBulkOperationRows(-2));
    }

    @Test
    void properties_query_setExtraSafeFunctions() {
        MyJpaPlusProperties.Query q = new MyJpaPlusProperties.Query();
        q.setExtraSafeFunctions(java.util.List.of("FUNC1", "FUNC2"));
        assertEquals(2, q.getExtraSafeFunctions().size());
    }

    @Test
    void properties_query_setExtraSafeFunctions_null() {
        MyJpaPlusProperties.Query q = new MyJpaPlusProperties.Query();
        q.setExtraSafeFunctions(null);
        assertNotNull(q.getExtraSafeFunctions());
        assertTrue(q.getExtraSafeFunctions().isEmpty());
    }

    @Test
    void properties_query_setExtraBooleanFunctions() {
        MyJpaPlusProperties.Query q = new MyJpaPlusProperties.Query();
        q.setExtraBooleanFunctions(java.util.List.of("BF1"));
        assertEquals(1, q.getExtraBooleanFunctions().size());
    }

    @Test
    void properties_query_setExtraBooleanFunctions_null() {
        MyJpaPlusProperties.Query q = new MyJpaPlusProperties.Query();
        q.setExtraBooleanFunctions(null);
        assertNotNull(q.getExtraBooleanFunctions());
        assertTrue(q.getExtraBooleanFunctions().isEmpty());
    }

    @Test
    void properties_query_validate_passes() {
        MyJpaPlusProperties properties = new MyJpaPlusProperties();
        assertDoesNotThrow(() -> properties.getQuery().validate());
    }

    @Test
    void properties_monitoring_validate_passes() {
        MyJpaPlusProperties properties = new MyJpaPlusProperties();
        assertDoesNotThrow(() -> properties.getMonitoring().validate());
    }

    @Test
    void properties_validate_passes() {
        MyJpaPlusProperties properties = new MyJpaPlusProperties();
        assertDoesNotThrow(properties::validate);
    }

    @Test
    void properties_topLevel_setMonitoring() {
        MyJpaPlusProperties properties = new MyJpaPlusProperties();
        MyJpaPlusProperties.Monitoring m = new MyJpaPlusProperties.Monitoring();
        m.setEnabled(true);
        m.setSlowQueryThresholdMs(500);
        properties.setMonitoring(m);
        assertTrue(properties.getMonitoring().isEnabled());
        assertEquals(500, properties.getMonitoring().getSlowQueryThresholdMs());
    }

    @Test
    void properties_cache_setAutoInvalidationEnabled() {
        MyJpaPlusProperties.Cache c = new MyJpaPlusProperties.Cache();
        c.setAutoInvalidationEnabled(false);
        assertFalse(c.isAutoInvalidationEnabled());
    }

    @Test
    void properties_topLevel_setCache() {
        MyJpaPlusProperties properties = new MyJpaPlusProperties();
        MyJpaPlusProperties.Cache c = new MyJpaPlusProperties.Cache();
        c.setAutoInvalidationEnabled(false);
        properties.setCache(c);
        assertFalse(properties.getCache().isAutoInvalidationEnabled());
    }

    @Test
    void autoConfiguration_myJpaTemplate_defaultConfig() {
        MyJpaPlusProperties properties = new MyJpaPlusProperties();
        MyJpaPlusAutoConfiguration config = new MyJpaPlusAutoConfiguration(properties, null);
        MyJpaTemplate template = config.myJpaTemplate(properties);
        assertNotNull(template);
    }

    @Test
    void softDelete_filterBean_default() {
        MyJpaPlusProperties properties = new MyJpaPlusProperties();
        SoftDeleteFilterBean bean = new SoftDeleteFilterBean(properties);
        assertNotNull(bean);
    }

    @Test
    void softDelete_filterBean_disabled() {
        MyJpaPlusProperties properties = new MyJpaPlusProperties();
        properties.getSoftDelete().setAutoFilter(false);
        SoftDeleteFilterBean bean = new SoftDeleteFilterBean(properties);
        assertNotNull(bean);
    }

    @Test
    void globalConfigHolder_defaultConfig() {
        MyJpaPlusGlobalConfig config = GlobalConfigHolder.getConfig();
        assertNotNull(config);
        assertFalse(GlobalConfigHolder.isConfigured());
    }

    @Test
    void globalConfigHolder_setConfig() {
        MyJpaPlusGlobalConfig config = new MyJpaPlusGlobalConfig();
        GlobalConfigHolder.setConfig(config);
        try {
            assertTrue(GlobalConfigHolder.isConfigured());
            assertSame(config, GlobalConfigHolder.getConfig());
        } finally {
            GlobalConfigHolder.setConfig(null);
        }
    }

    @Test
    void globalConfigHolder_backoffAfterLookupFailure() {
        org.springframework.context.ApplicationContext failingCtx =
            org.mockito.Mockito.mock(org.springframework.context.ApplicationContext.class);
        org.mockito.Mockito.when(failingCtx.getBean(MyJpaPlusGlobalConfig.class))
            .thenThrow(new org.springframework.beans.factory.NoSuchBeanDefinitionException("not found"));

        GlobalConfigHolder.reset();
        GlobalConfigHolder.setApplicationContext(failingCtx);
        // First call triggers lookup failure
        MyJpaPlusGlobalConfig result1 = GlobalConfigHolder.getConfig();
        assertNotNull(result1, "Should fall back to default config");

        // Second call should use backoff (not retry immediately)
        MyJpaPlusGlobalConfig result2 = GlobalConfigHolder.getConfig();
        assertNotNull(result2, "Should still return config during backoff");
        // Verify the mock was only called once (backoff prevents second lookup)
        org.mockito.Mockito.verify(failingCtx, org.mockito.Mockito.times(1))
            .getBean(MyJpaPlusGlobalConfig.class);
        GlobalConfigHolder.reset();
    }
}
