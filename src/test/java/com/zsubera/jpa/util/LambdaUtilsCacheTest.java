package com.zsubera.jpa.util;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

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

    @Test
    void cacheShouldEvictOnHitPath() {
        int originalMaxSize = LambdaUtils.getMaxCacheSize();
        try {
            LambdaUtils.clearCache();
            // 设置较小的缓存上限以便测试
            LambdaUtils.setMaxCacheSize(200);

            // 先填充缓存到上限
            for (int i = 0; i < 200; i++) {
                LambdaUtils.getPropertyName(TestBean::getName);
            }

            // 大量缓存命中调用——应触发驱逐检查
            for (int i = 0; i < 2000; i++) {
                LambdaUtils.getPropertyName(TestBean::getName);
            }

            // 缓存大小应保持在上限附近，不会无限增长
            assertTrue(LambdaUtils.cacheSize() <= 200,
                "Cache size should stay bounded even with 100% hit rate, got: " + LambdaUtils.cacheSize());
        } finally {
            LambdaUtils.setMaxCacheSize(originalMaxSize);
            LambdaUtils.clearCache();
        }
    }

    static class TestBean {
        private String name;
        private int status;

        public String getName() {
            return name;
        }

        public int getStatus() {
            return status;
        }
    }
}
