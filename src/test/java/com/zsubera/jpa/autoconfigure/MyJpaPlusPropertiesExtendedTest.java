package com.zsubera.jpa.autoconfigure;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class MyJpaPlusPropertiesExtendedTest {

    @Test
    void validate_passesWithDefaults() {
        MyJpaPlusProperties props = new MyJpaPlusProperties();
        assertDoesNotThrow(props::validate);
    }

    @Test
    void validate_query_passes() {
        MyJpaPlusProperties props = new MyJpaPlusProperties();
        assertDoesNotThrow(() -> props.getQuery().validate());
    }

    @Test
    void validate_monitoring_passes() {
        MyJpaPlusProperties props = new MyJpaPlusProperties();
        assertDoesNotThrow(() -> props.getMonitoring().validate());
    }

    @Test
    void monitoring_setterGetter() {
        MyJpaPlusProperties.Monitoring m = new MyJpaPlusProperties.Monitoring();
        m.setSlowQueryThresholdMs(500);
        assertEquals(500, m.getSlowQueryThresholdMs());
        m.setEnabled(true);
        assertTrue(m.isEnabled());
    }

    @Test
    void monitoring_setSlowQueryThresholdMs_invalid_throws() {
        MyJpaPlusProperties.Monitoring m = new MyJpaPlusProperties.Monitoring();
        assertThrows(IllegalArgumentException.class, () -> m.setSlowQueryThresholdMs(0));
        assertThrows(IllegalArgumentException.class, () -> m.setSlowQueryThresholdMs(-1));
    }

    @Test
    void softDelete_setterGetter() {
        MyJpaPlusProperties.SoftDelete sd = new MyJpaPlusProperties.SoftDelete();
        sd.setAutoFilter(false);
        assertFalse(sd.isAutoFilter());
        sd.setBlockUnconditionalDelete(false);
        assertFalse(sd.isBlockUnconditionalDelete());
    }

    @Test
    void cache_setterGetter() {
        MyJpaPlusProperties.Cache c = new MyJpaPlusProperties.Cache();
        c.setAutoInvalidationEnabled(false);
        assertFalse(c.isAutoInvalidationEnabled());
    }

    @Test
    void topLevel_setSoftDelete() {
        MyJpaPlusProperties props = new MyJpaPlusProperties();
        MyJpaPlusProperties.SoftDelete sd = new MyJpaPlusProperties.SoftDelete();
        sd.setAutoFilter(false);
        props.setSoftDelete(sd);
        assertFalse(props.getSoftDelete().isAutoFilter());
    }

    @Test
    void topLevel_setQuery() {
        MyJpaPlusProperties props = new MyJpaPlusProperties();
        MyJpaPlusProperties.Query q = new MyJpaPlusProperties.Query();
        q.setMaxResults(500);
        props.setQuery(q);
        assertEquals(500, props.getQuery().getMaxResults());
    }

    @Test
    void topLevel_setMonitoring() {
        MyJpaPlusProperties props = new MyJpaPlusProperties();
        MyJpaPlusProperties.Monitoring m = new MyJpaPlusProperties.Monitoring();
        m.setEnabled(true);
        props.setMonitoring(m);
        assertTrue(props.getMonitoring().isEnabled());
    }

    @Test
    void topLevel_setCache() {
        MyJpaPlusProperties props = new MyJpaPlusProperties();
        MyJpaPlusProperties.Cache c = new MyJpaPlusProperties.Cache();
        c.setAutoInvalidationEnabled(false);
        props.setCache(c);
        assertFalse(props.getCache().isAutoInvalidationEnabled());
    }

    @Test
    void query_setMaxResults_invalid() {
        MyJpaPlusProperties.Query q = new MyJpaPlusProperties.Query();
        assertThrows(IllegalArgumentException.class, () -> q.setMaxResults(0));
        assertThrows(IllegalArgumentException.class, () -> q.setMaxResults(-1));
    }

    @Test
    void query_setDeepPaginationOffsetThreshold_invalid() {
        MyJpaPlusProperties.Query q = new MyJpaPlusProperties.Query();
        assertThrows(IllegalArgumentException.class, () -> q.setDeepPaginationOffsetThreshold(0));
        assertThrows(IllegalArgumentException.class, () -> q.setDeepPaginationOffsetThreshold(-1));
    }

    @Test
    void query_setDeepPaginationOffsetLimit_invalid() {
        MyJpaPlusProperties.Query q = new MyJpaPlusProperties.Query();
        assertThrows(IllegalArgumentException.class, () -> q.setDeepPaginationOffsetLimit(0));
        assertThrows(IllegalArgumentException.class, () -> q.setDeepPaginationOffsetLimit(-2));
    }

    @Test
    void query_setInClauseMaxSize_invalid() {
        MyJpaPlusProperties.Query q = new MyJpaPlusProperties.Query();
        assertThrows(IllegalArgumentException.class, () -> q.setInClauseMaxSize(0));
        assertThrows(IllegalArgumentException.class, () -> q.setInClauseMaxSize(-1));
    }

    @Test
    void query_setInClauseHardLimit_invalid() {
        MyJpaPlusProperties.Query q = new MyJpaPlusProperties.Query();
        assertThrows(IllegalArgumentException.class, () -> q.setInClauseHardLimit(0));
        assertThrows(IllegalArgumentException.class, () -> q.setInClauseHardLimit(-1));
    }

    @Test
    void query_setLambdaCacheSize_invalid() {
        MyJpaPlusProperties.Query q = new MyJpaPlusProperties.Query();
        assertThrows(IllegalArgumentException.class, () -> q.setLambdaCacheSize(0));
        assertThrows(IllegalArgumentException.class, () -> q.setLambdaCacheSize(-1));
    }

    @Test
    void query_setDefaultTimeoutSeconds_invalid() {
        MyJpaPlusProperties.Query q = new MyJpaPlusProperties.Query();
        assertThrows(IllegalArgumentException.class, () -> q.setDefaultTimeoutSeconds(0));
        assertThrows(IllegalArgumentException.class, () -> q.setDefaultTimeoutSeconds(-2));
    }

    @Test
    void query_setMaxBulkOperationRows_invalid() {
        MyJpaPlusProperties.Query q = new MyJpaPlusProperties.Query();
        assertThrows(IllegalArgumentException.class, () -> q.setMaxBulkOperationRows(-1));
    }

    @Test
    void query_setExtraSafeFunctions() {
        MyJpaPlusProperties.Query q = new MyJpaPlusProperties.Query();
        q.setExtraSafeFunctions(List.of("FUNC1", "FUNC2"));
        assertEquals(2, q.getExtraSafeFunctions().size());
    }

    @Test
    void query_setExtraSafeFunctions_null() {
        MyJpaPlusProperties.Query q = new MyJpaPlusProperties.Query();
        q.setExtraSafeFunctions(null);
        assertNotNull(q.getExtraSafeFunctions());
        assertTrue(q.getExtraSafeFunctions().isEmpty());
    }

    @Test
    void query_setExtraBooleanFunctions() {
        MyJpaPlusProperties.Query q = new MyJpaPlusProperties.Query();
        q.setExtraBooleanFunctions(List.of("BOOL1"));
        assertEquals(1, q.getExtraBooleanFunctions().size());
    }

    @Test
    void query_setExtraBooleanFunctions_null() {
        MyJpaPlusProperties.Query q = new MyJpaPlusProperties.Query();
        q.setExtraBooleanFunctions(null);
        assertNotNull(q.getExtraBooleanFunctions());
        assertTrue(q.getExtraBooleanFunctions().isEmpty());
    }
}
