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
    void shouldEvictCacheWhenSizeExceedsMax() throws Exception {
        int oldMax = LambdaUtils.getMaxCacheSize();
        try {
            LambdaUtils.setMaxCacheSize(10);
            LambdaUtils.clearCache();

            // Fill cache beyond maxCacheSize
            java.lang.reflect.Field cacheField = LambdaUtils.class.getDeclaredField("CACHE");
            cacheField.setAccessible(true);
            @SuppressWarnings("unchecked")
            java.util.concurrent.ConcurrentHashMap<String, String> cache =
                (java.util.concurrent.ConcurrentHashMap<String, String>)cacheField.get(null);

            for (int i = 0; i < 20; i++) {
                cache.put("class" + i + "#method" + i, "prop" + i);
            }
            assertTrue(cache.size() > 10);

            // Reset call counter to trigger eviction check on next call
            java.lang.reflect.Field counterField = LambdaUtils.class.getDeclaredField("CALL_COUNTER");
            counterField.setAccessible(true);
            java.util.concurrent.atomic.AtomicInteger counter =
                (java.util.concurrent.atomic.AtomicInteger)counterField.get(null);
            // Set counter so next increment hits multiple of 100
            counter.set(99);

            // This call triggers evictCacheIfNeeded via getPropertyName
            LambdaUtils.getPropertyName(TestBean::getName);

            // Cache should have been evicted
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
    void shouldNotEvictWhenCacheBelowThreshold() throws Exception {
        LambdaUtils.clearCache();
        int oldMax = LambdaUtils.getMaxCacheSize();
        try {
            LambdaUtils.setMaxCacheSize(10000);
            LambdaUtils.getPropertyName(TestBean::getName);
            int sizeBefore = LambdaUtils.cacheSize();

            // Reset counter to trigger check
            java.lang.reflect.Field counterField = LambdaUtils.class.getDeclaredField("CALL_COUNTER");
            counterField.setAccessible(true);
            java.util.concurrent.atomic.AtomicInteger counter =
                (java.util.concurrent.atomic.AtomicInteger)counterField.get(null);
            counter.set(99);

            LambdaUtils.getPropertyName(TestBean::getName);
            assertEquals(sizeBefore, LambdaUtils.cacheSize());
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
    void shouldEvictMethodCacheWhenSizeExceedsMax() throws Exception {
        java.lang.reflect.Field methodCacheField = LambdaUtils.class.getDeclaredField("METHOD_CACHE");
        methodCacheField.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.concurrent.ConcurrentHashMap<Class<?>, java.lang.reflect.Method> methodCache =
            (java.util.concurrent.ConcurrentHashMap<Class<?>, java.lang.reflect.Method>)methodCacheField.get(null);

        int originalSize = methodCache.size();

        try {
            // Fill METHOD_CACHE beyond METHOD_CACHE_MAX_SIZE (4096) using dynamic proxy classes
            ClassLoader cl = LambdaUtilsTest.class.getClassLoader();
            java.lang.reflect.Method runMethod = Runnable.class.getMethod("run");

            int added = 0;
            for (int i = 0; i < 4200; i++) {
                java.net.URLClassLoader ucl = new java.net.URLClassLoader(new java.net.URL[0], cl);
                Class<?> proxyClass = java.lang.reflect.Proxy.getProxyClass(ucl, Runnable.class);
                if (methodCache.putIfAbsent(proxyClass, runMethod) == null) {
                    added++;
                }
            }
            assertTrue(methodCache.size() > 4096,
                "METHOD_CACHE should have > 4096 entries, got " + methodCache.size() + " (added " + added + ")");

            // Reset call counter so next getPropertyName triggers eviction check
            java.lang.reflect.Field counterField = LambdaUtils.class.getDeclaredField("CALL_COUNTER");
            counterField.setAccessible(true);
            java.util.concurrent.atomic.AtomicInteger counter =
                (java.util.concurrent.atomic.AtomicInteger)counterField.get(null);
            counter.set(99);

            // Trigger eviction via getPropertyName
            LambdaUtils.getPropertyName(TestBean::getName);

            assertTrue(methodCache.size() < 4200,
                "METHOD_CACHE should have been evicted, but size is " + methodCache.size());
        } finally {
            // Restore original cache size
            while (methodCache.size() > originalSize) {
                var it = methodCache.keySet().iterator();
                while (it.hasNext() && methodCache.size() > originalSize) {
                    it.next();
                    it.remove();
                }
            }
        }
    }
}
