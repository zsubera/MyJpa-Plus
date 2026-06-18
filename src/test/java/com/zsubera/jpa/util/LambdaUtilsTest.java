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
}
