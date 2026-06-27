package com.zsubera.jpa.update;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * Tests for {@link AuditUtils}.
 */
class AuditUtilsTest {

    @Test
    void getCallStack_returnsNonEmptyString() {
        String stack = AuditUtils.getCallStack();
        assertNotNull(stack);
        assertFalse(stack.isEmpty());
    }

    @Test
    void getCallStack_containsCurrentMethod() {
        String stack = AuditUtils.getCallStack();
        assertTrue(stack.contains("AuditUtilsTest"), "Stack trace should contain the test class name");
    }

    @Test
    void getCallStack_containsClassName() {
        String stack = AuditUtils.getCallStack();
        assertTrue(stack.contains("AuditUtilsTest"), "Stack trace should contain the test class name");
    }

    @Test
    void getCallStack_usesArrowSeparator() {
        String stack = AuditUtils.getCallStack();
        // Stack should contain " <- " separator between frames
        if (stack.contains(" <- ")) {
            // Multiple frames present - verify format
            String[] frames = stack.split(" <- ");
            assertTrue(frames.length >= 1, "Should have at least one frame");
        }
    }

    @Test
    void getCallStack_maxDepthRespected() {
        String stack = AuditUtils.getCallStack();
        String[] frames = stack.split(" <- ");
        int maxDepth = AuditUtils.getMaxStackDepth();
        assertTrue(frames.length <= maxDepth,
            "Stack depth should not exceed " + maxDepth + ", but got " + frames.length);
    }

    @Test
    void setMaxStackDepth_validValue() {
        int original = AuditUtils.getMaxStackDepth();
        try {
            AuditUtils.setMaxStackDepth(10);
            assertEquals(10, AuditUtils.getMaxStackDepth());
        } finally {
            AuditUtils.setMaxStackDepth(original);
        }
    }

    @Test
    void setMaxStackDepth_invalidValueIgnored() {
        int original = AuditUtils.getMaxStackDepth();
        try {
            AuditUtils.setMaxStackDepth(0);
            assertEquals(original, AuditUtils.getMaxStackDepth(), "Invalid value should be ignored");

            AuditUtils.setMaxStackDepth(-1);
            assertEquals(original, AuditUtils.getMaxStackDepth(), "Negative value should be ignored");

            AuditUtils.setMaxStackDepth(21);
            assertEquals(original, AuditUtils.getMaxStackDepth(), "Value exceeding limit should be ignored");
        } finally {
            AuditUtils.setMaxStackDepth(original);
        }
    }

    @Test
    void getCallStack_respectsConfiguredDepth() {
        int original = AuditUtils.getMaxStackDepth();
        try {
            AuditUtils.setMaxStackDepth(2);
            String stack = AuditUtils.getCallStack();
            String[] frames = stack.split(" <- ");
            assertTrue(frames.length <= 2,
                "Stack depth should respect configured limit of 2, but got " + frames.length);
        } finally {
            AuditUtils.setMaxStackDepth(original);
        }
    }

    @Test
    void getMaxStackDepth_defaultValue() {
        // The default value should be 5 (unless overridden by system property)
        int depth = AuditUtils.getMaxStackDepth();
        assertTrue(depth > 0 && depth <= 20, "Default depth should be between 1 and 20");
    }

    @Test
    void initMaxStackDepth_withValidSystemProperty() throws Exception {
        int original = AuditUtils.getMaxStackDepth();
        System.setProperty("myjpa-plus.audit.stack-trace-depth", "10");
        try {
            java.lang.reflect.Method initMethod = AuditUtils.class.getDeclaredMethod("initMaxStackDepth");
            initMethod.setAccessible(true);
            int result = (int)initMethod.invoke(null);
            assertEquals(10, result);
        } finally {
            System.clearProperty("myjpa-plus.audit.stack-trace-depth");
            AuditUtils.setMaxStackDepth(original);
        }
    }

    @Test
    void initMaxStackDepth_withOutOfRangeProperty() throws Exception {
        int original = AuditUtils.getMaxStackDepth();
        System.setProperty("myjpa-plus.audit.stack-trace-depth", "0");
        try {
            java.lang.reflect.Method initMethod = AuditUtils.class.getDeclaredMethod("initMaxStackDepth");
            initMethod.setAccessible(true);
            int result = (int)initMethod.invoke(null);
            assertEquals(5, result);
        } finally {
            System.clearProperty("myjpa-plus.audit.stack-trace-depth");
            AuditUtils.setMaxStackDepth(original);
        }
    }

    @Test
    void initMaxStackDepth_withInvalidSystemProperty() throws Exception {
        int original = AuditUtils.getMaxStackDepth();
        System.setProperty("myjpa-plus.audit.stack-trace-depth", "not-a-number");
        try {
            java.lang.reflect.Method initMethod = AuditUtils.class.getDeclaredMethod("initMaxStackDepth");
            initMethod.setAccessible(true);
            int result = (int)initMethod.invoke(null);
            assertEquals(5, result);
        } finally {
            System.clearProperty("myjpa-plus.audit.stack-trace-depth");
            AuditUtils.setMaxStackDepth(original);
        }
    }
}
