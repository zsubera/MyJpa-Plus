package com.zsubera.jpa.autoconfigure;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class MyJpaPlusGlobalConfigTest {

    @Test
    void defaultValues() {
        MyJpaPlusGlobalConfig config = new MyJpaPlusGlobalConfig();
        assertTrue(config.isSoftDeleteAutoFilter());
        assertTrue(config.isBlockUnconditionalDelete());
        assertEquals(30, config.getDefaultTimeoutSeconds());
        assertEquals(300, config.getMaxTimeoutSeconds());
        assertEquals(10000, config.getMaxResults());
        assertEquals(10000, config.getMaxBulkOperationRows());
        assertEquals(100000, config.getDeepPaginationOffsetThreshold());
        assertEquals(-1, config.getDeepPaginationOffsetLimit());
    }

    @Test
    void setSoftDeleteAutoFilter() {
        MyJpaPlusGlobalConfig config = new MyJpaPlusGlobalConfig();
        config.setSoftDeleteAutoFilter(false);
        assertFalse(config.isSoftDeleteAutoFilter());
    }

    @Test
    void setAutoFilterEnabled_deprecated() {
        MyJpaPlusGlobalConfig config = new MyJpaPlusGlobalConfig();
        config.setAutoFilterEnabled(false);
        assertFalse(config.isSoftDeleteAutoFilter());
    }

    @Test
    void setBlockUnconditionalDelete() {
        MyJpaPlusGlobalConfig config = new MyJpaPlusGlobalConfig();
        config.setBlockUnconditionalDelete(false);
        assertFalse(config.isBlockUnconditionalDelete());
    }

    @Test
    void setDefaultTimeoutSeconds() {
        MyJpaPlusGlobalConfig config = new MyJpaPlusGlobalConfig();
        config.setDefaultTimeoutSeconds(60);
        assertEquals(60, config.getDefaultTimeoutSeconds());
    }

    @Test
    void setMaxTimeoutSeconds() {
        MyJpaPlusGlobalConfig config = new MyJpaPlusGlobalConfig();
        config.setMaxTimeoutSeconds(600);
        assertEquals(600, config.getMaxTimeoutSeconds());
    }

    @Test
    void setMaxTimeoutSeconds_invalid_throws() {
        MyJpaPlusGlobalConfig config = new MyJpaPlusGlobalConfig();
        assertThrows(IllegalArgumentException.class, () -> config.setMaxTimeoutSeconds(0));
        assertThrows(IllegalArgumentException.class, () -> config.setMaxTimeoutSeconds(-1));
    }

    @Test
    void setMaxResults() {
        MyJpaPlusGlobalConfig config = new MyJpaPlusGlobalConfig();
        config.setMaxResults(5000);
        assertEquals(5000, config.getMaxResults());
    }

    @Test
    void setMaxBulkOperationRows() {
        MyJpaPlusGlobalConfig config = new MyJpaPlusGlobalConfig();
        config.setMaxBulkOperationRows(2000);
        assertEquals(2000, config.getMaxBulkOperationRows());
    }

    @Test
    void setDeepPaginationOffsetThreshold() {
        MyJpaPlusGlobalConfig config = new MyJpaPlusGlobalConfig();
        config.setDeepPaginationOffsetThreshold(50000);
        assertEquals(50000, config.getDeepPaginationOffsetThreshold());
    }

    @Test
    void setDeepPaginationOffsetLimit() {
        MyJpaPlusGlobalConfig config = new MyJpaPlusGlobalConfig();
        config.setDeepPaginationOffsetLimit(1000000);
        assertEquals(1000000, config.getDeepPaginationOffsetLimit());
    }
}
