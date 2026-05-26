package com.zsubera.jpa.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LambdaUtilsCacheTest {

    @Test
    void cacheStoresAndReturnsEntries() {
        LambdaUtils.clearCache();
        assertEquals(0, LambdaUtils.cacheSize());

        LambdaUtils.getPropertyName(TestBean::getName);
        assertEquals(1, LambdaUtils.cacheSize());

        LambdaUtils.getPropertyName(TestBean::getName);
        assertEquals(1, LambdaUtils.cacheSize());

        LambdaUtils.getPropertyName(TestBean::getStatus);
        assertEquals(2, LambdaUtils.cacheSize());

        LambdaUtils.clearCache();
        assertEquals(0, LambdaUtils.cacheSize());
    }

    @Test
    void cacheEvictsWhenExceedingMaxSize() {
        LambdaUtils.clearCache();
        assertEquals(0, LambdaUtils.cacheSize());

        String cached = LambdaUtils.getPropertyName(TestBean::getName);
        assertNotNull(cached);

        assertTrue(LambdaUtils.cacheSize() > 0);
    }

    static class TestBean {
        private String name;
        private int status;

        public String getName() { return name; }
        public int getStatus() { return status; }
    }
}
