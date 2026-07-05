package com.zsubera.jpa.autoconfigure;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class MyJpaPlusGlobalConfigTest {

    @Test
    void defaultBooleanValues() {
        MyJpaPlusGlobalConfig config = new MyJpaPlusGlobalConfig();
        assertTrue(config.isSoftDeleteAutoFilter());
        assertTrue(config.isBlockUnconditionalDelete());
    }

    @Test
    void defaultIntegerValuesAreNull() {
        MyJpaPlusGlobalConfig config = new MyJpaPlusGlobalConfig();
        assertNull(config.getMaxResults());
        assertNull(config.getMaxBulkOperationRows());
        assertNull(config.getDeepPaginationOffsetThreshold());
        assertNull(config.getDeepPaginationOffsetLimit());
        assertNull(config.getInClauseMaxSize());
        assertNull(config.getInClauseHardLimit());
        assertNull(config.getLambdaCacheSize());
        assertNull(config.getCacheMaxEntries());
    }

    @Test
    void setters() {
        MyJpaPlusGlobalConfig config = new MyJpaPlusGlobalConfig();
        config.setSoftDeleteAutoFilter(false);
        assertFalse(config.isSoftDeleteAutoFilter());

        config.setBlockUnconditionalDelete(false);
        assertFalse(config.isBlockUnconditionalDelete());

        config.setMaxResults(5000);
        assertEquals(5000, config.getMaxResults());

        config.setMaxBulkOperationRows(5000);
        assertEquals(5000, config.getMaxBulkOperationRows());

        config.setDeepPaginationOffsetThreshold(50000);
        assertEquals(50000, config.getDeepPaginationOffsetThreshold());

        config.setDeepPaginationOffsetLimit(500000);
        assertEquals(500000, config.getDeepPaginationOffsetLimit());
    }

    @Test
    void inClauseSettings() {
        MyJpaPlusGlobalConfig config = new MyJpaPlusGlobalConfig();
        config.setInClauseMaxSize(2000);
        assertEquals(2000, config.getInClauseMaxSize());

        config.setInClauseHardLimit(10000);
        assertEquals(10000, config.getInClauseHardLimit());
    }

    @Test
    void cacheSettings() {
        MyJpaPlusGlobalConfig config = new MyJpaPlusGlobalConfig();
        config.setCacheMaxEntries(5000);
        assertEquals(5000, config.getCacheMaxEntries());
    }

    @Test
    void lambdaCacheSize() {
        MyJpaPlusGlobalConfig config = new MyJpaPlusGlobalConfig();
        config.setLambdaCacheSize(2048);
        assertEquals(2048, config.getLambdaCacheSize());
    }
}
