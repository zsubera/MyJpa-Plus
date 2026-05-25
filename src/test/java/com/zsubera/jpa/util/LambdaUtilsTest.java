package com.zsubera.jpa.util;

import com.zsubera.jpa.spec.SFunction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

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

        public String getName() { return name; }
        public boolean isActive() { return active; }
        public int getStatus() { return status; }
        public String getId() { return id; }
    }
}
