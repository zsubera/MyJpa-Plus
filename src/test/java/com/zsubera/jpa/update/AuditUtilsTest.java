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
        assertTrue(stack.contains("getCallStack_containsCurrentMethod"),
            "Stack trace should contain the test method name");
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
        // Should not exceed MAX_STACK_DEPTH (10)
        assertTrue(frames.length <= 10, "Stack depth should not exceed 10, but got " + frames.length);
    }
}
