package com.zsubera.jpa.autoconfigure;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GlobalConfigHolderTest {

    @BeforeEach
    @AfterEach
    void cleanup() {
        GlobalConfigHolder.reset();
    }

    @Test
    void getConfig_withoutAnything_returnsDefaultConfig() {
        MyJpaPlusGlobalConfig config = GlobalConfigHolder.getConfig();
        assertNotNull(config);
    }

    @Test
    void setConfig_thenGetConfig_returnsSetConfig() {
        MyJpaPlusGlobalConfig custom = new MyJpaPlusGlobalConfig();
        custom.setMaxBulkOperationRows(100);
        GlobalConfigHolder.setConfig(custom);

        MyJpaPlusGlobalConfig retrieved = GlobalConfigHolder.getConfig();
        assertSame(custom, retrieved);
        assertEquals(100, retrieved.getMaxBulkOperationRows());
    }

    @Test
    void reset_clearsConfig() {
        MyJpaPlusGlobalConfig custom = new MyJpaPlusGlobalConfig();
        GlobalConfigHolder.setConfig(custom);
        assertNotNull(GlobalConfigHolder.getConfig());

        GlobalConfigHolder.reset();
        MyJpaPlusGlobalConfig config = GlobalConfigHolder.getConfig();
        assertNotSame(custom, config);
    }

    @Test
    void isConfigured_withoutAnything_returnsFalse() {
        assertFalse(GlobalConfigHolder.isConfigured());
    }

    @Test
    void isConfigured_withConfig_returnsTrue() {
        GlobalConfigHolder.setConfig(new MyJpaPlusGlobalConfig());
        assertTrue(GlobalConfigHolder.isConfigured());
    }

    @Test
    void resolveConfigValue_withConfig_returnsConfigValue() {
        MyJpaPlusGlobalConfig custom = new MyJpaPlusGlobalConfig();
        custom.setMaxBulkOperationRows(500);
        GlobalConfigHolder.setConfig(custom);

        int result = GlobalConfigHolder.resolveConfigValue(MyJpaPlusGlobalConfig::getMaxBulkOperationRows, -1);
        assertEquals(500, result);
    }

    @Test
    void resolveConfigValue_withoutConfig_returnsDefault() {
        int result = GlobalConfigHolder.resolveConfigValue(MyJpaPlusGlobalConfig::getMaxBulkOperationRows, -1);
        assertEquals(-1, result);
    }

    @Test
    void resolveMaxBulkOperationRows_withoutConfig_returnsFallback() {
        assertEquals(-1, GlobalConfigHolder.resolveMaxBulkOperationRows(-1));
    }

    @Test
    void resolveMaxBulkOperationRows_withConfig_returnsConfigValue() {
        MyJpaPlusGlobalConfig custom = new MyJpaPlusGlobalConfig();
        custom.setMaxBulkOperationRows(2000);
        GlobalConfigHolder.setConfig(custom);

        assertEquals(2000, GlobalConfigHolder.resolveMaxBulkOperationRows(-1));
    }

    @Test
    void resolveMaxUpsertBatchIterations_withoutConfig_returnsFallback() {
        assertEquals(50, GlobalConfigHolder.resolveMaxUpsertBatchIterations(50));
    }

    @Test
    void resolveMaxUpsertBatchIterations_withConfig_returnsConfigValue() {
        MyJpaPlusGlobalConfig custom = new MyJpaPlusGlobalConfig();
        custom.setMaxUpsertBatchIterations(100);
        GlobalConfigHolder.setConfig(custom);

        assertEquals(100, GlobalConfigHolder.resolveMaxUpsertBatchIterations(50));
    }

    @Test
    void setApplicationContext_doesNotAffectNonSpringPath() {
        GlobalConfigHolder.setApplicationContext(null);
        MyJpaPlusGlobalConfig config = GlobalConfigHolder.getConfig();
        assertNotNull(config);
    }
}
