package com.zsubera.jpa.util;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class LambdaUtilsTest {

    @Test
    void shouldExtractPropertyFromGetter() {
        String prop = LambdaUtils.getPropertyName(TestBean::getName);
        assertEquals("name", prop);
    }

    @Test
    void shouldExtractPropertyFromBooleanIs() {
        String prop = LambdaUtils.getPropertyName(TestBean::isActive);
        assertEquals("active", prop);
    }

    @Test
    void shouldExtractPropertyFromPlainGetter() {
        String prop = LambdaUtils.getPropertyName(TestBean::getStatus);
        assertEquals("status", prop);
    }

    @Test
    void shouldThrowOnNullFunction() {
        assertThrows(IllegalArgumentException.class, () -> LambdaUtils.getPropertyName(null));
    }

    @Test
    void shouldDecapitalizeGetterName() {
        assertEquals("status", LambdaUtils.getPropertyName(TestBean::getStatus));
    }

    static class TestBean {
        private String name;
        private boolean active;
        private int status;
        private String id;

        public String getName() {
            return name;
        }

        public boolean isActive() {
            return active;
        }

        public int getStatus() {
            return status;
        }

        public String getId() {
            return id;
        }
    }

    @Test
    void shouldExtractPropertyFromProperty() {
        assertEquals("name", LambdaUtils.property(TestBean::getName));
    }

    @Test
    void shouldThrowOnNullProperty() {
        assertThrows(IllegalArgumentException.class, () -> LambdaUtils.property(null));
    }

    @Test
    void shouldSetAndGetMaxCacheSize() {
        int old = LambdaUtils.getMaxCacheSize();
        try {
            LambdaUtils.setMaxCacheSize(8192);
            assertEquals(8192, LambdaUtils.getMaxCacheSize());
        } finally {
            LambdaUtils.setMaxCacheSize(old);
        }
    }

    @Test
    void shouldIgnoreMaxCacheSizeExceedingLimit() {
        int old = LambdaUtils.getMaxCacheSize();
        try {
            LambdaUtils.setMaxCacheSize(999999);
            assertNotEquals(999999, LambdaUtils.getMaxCacheSize());
        } finally {
            LambdaUtils.setMaxCacheSize(old);
        }
    }

    @Test
    void shouldCallShutdown() {
        assertDoesNotThrow(LambdaUtils::shutdown);
    }

    @Test
    void shouldGetCacheSize() {
        assertDoesNotThrow(LambdaUtils::cacheSize);
    }

    @Test
    void shouldClearCache() {
        LambdaUtils.getPropertyName(TestBean::getName);
        LambdaUtils.clearCache();
        assertEquals(0, LambdaUtils.cacheSize());
    }

    @Test
    void shouldWorkWithCaching() {
        String name1 = LambdaUtils.getPropertyName(TestBean::getName);
        String name2 = LambdaUtils.getPropertyName(TestBean::getName);
        assertEquals("name", name1);
        assertEquals(name1, name2);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldEvictCacheWhenSizeExceedsMax() throws Exception {
        int oldMax = LambdaUtils.getMaxCacheSize();
        try {
            LambdaUtils.setMaxCacheSize(10);
            LambdaUtils.clearCache();

            java.lang.reflect.Field cacheField = LambdaUtils.class.getDeclaredField("CACHE");
            cacheField.setAccessible(true);
            SampledEvictionCache<String, String> cache = (SampledEvictionCache<String, String>)cacheField.get(null);

            // Fill cache beyond maxSize via put, which also triggers sampling evict
            // Sampling interval is 100, so we need enough puts to trigger check
            for (int i = 0; i < 20; i++) {
                cache.put("class" + i + "#method" + i, "prop" + i);
            }
            int sizeAfterFill = cache.size();
            assertTrue(sizeAfterFill > 10, "Cache should exceed maxSize, got " + sizeAfterFill);

            // Continue putting until sampling eviction triggers
            int evictTriggerPuts = 0;
            while (cache.size() > 10 && evictTriggerPuts < 200) {
                cache.put("evict_" + evictTriggerPuts++ + "#m", "v");
            }
            assertTrue(cache.size() <= 10, "Cache should be evicted, but size is " + cache.size());
        } finally {
            LambdaUtils.setMaxCacheSize(oldMax);
            LambdaUtils.clearCache();
        }
    }

    @Test
    void shouldRejectObjectMethodsInMethodToProperty() throws Exception {
        java.lang.reflect.Method m = LambdaUtils.class.getDeclaredMethod("methodToProperty", String.class);
        m.setAccessible(true);

        assertThrows(java.lang.reflect.InvocationTargetException.class, () -> m.invoke(null, "hashCode"));
        assertThrows(java.lang.reflect.InvocationTargetException.class, () -> m.invoke(null, "toString"));
        assertThrows(java.lang.reflect.InvocationTargetException.class, () -> m.invoke(null, "notify"));
        assertThrows(java.lang.reflect.InvocationTargetException.class, () -> m.invoke(null, "notifyAll"));
        assertThrows(java.lang.reflect.InvocationTargetException.class, () -> m.invoke(null, "wait"));
    }

    @Test
    void shouldHandleMethodToPropertyEdgeCases() throws Exception {
        java.lang.reflect.Method m = LambdaUtils.class.getDeclaredMethod("methodToProperty", String.class);
        m.setAccessible(true);

        assertEquals("name", m.invoke(null, "getName"));
        assertEquals("active", m.invoke(null, "isActive"));
        assertEquals("status", m.invoke(null, "getStatus"));
        assertEquals("class", m.invoke(null, "getClass"));
        assertEquals("count", m.invoke(null, "count"));
    }

    @Test
    void shouldNotEvictWhenCacheBelowThreshold() {
        LambdaUtils.clearCache();
        int oldMax = LambdaUtils.getMaxCacheSize();
        try {
            LambdaUtils.setMaxCacheSize(10000);
            int sizeBefore = LambdaUtils.cacheSize();

            LambdaUtils.getPropertyName(TestBean::getName);
            LambdaUtils.getPropertyName(TestBean::getName);
            int sizeAfter = LambdaUtils.cacheSize();

            assertTrue(sizeAfter >= sizeBefore, "Cache size should not decrease when below threshold");
        } finally {
            LambdaUtils.setMaxCacheSize(oldMax);
            LambdaUtils.clearCache();
        }
    }

    @Test
    void shouldIgnoreCacheSizeSetToZero() {
        int old = LambdaUtils.getMaxCacheSize();
        try {
            LambdaUtils.setMaxCacheSize(0);
            assertEquals(old, LambdaUtils.getMaxCacheSize());
        } finally {
            LambdaUtils.setMaxCacheSize(old);
        }
    }

    @Test
    void shouldIgnoreCacheSizeSetToNegative() {
        int old = LambdaUtils.getMaxCacheSize();
        try {
            LambdaUtils.setMaxCacheSize(-1);
            assertEquals(old, LambdaUtils.getMaxCacheSize());
        } finally {
            LambdaUtils.setMaxCacheSize(old);
        }
    }

    @Test
    void shouldUseMethodCacheForReflection() {
        // Verifies getPropertyName works with METHOD_CACHE integration
        String name = LambdaUtils.getPropertyName(TestBean::getName);
        assertEquals("name", name);
        // Second call should hit METHOD_CACHE (writeReplace lookup cached)
        assertEquals("name", LambdaUtils.getPropertyName(TestBean::getName));
    }
}
