package com.zsubera.jpa.update;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class AuditUtilsTest {

    @Test
    void getCallStack_returnsNonEmptyString() {
        String stack = AuditUtils.getCallStack();
        assertNotNull(stack);
        assertFalse(stack.isEmpty());
    }

    @Test
    void getCallStack_containsClassName() {
        String stack = AuditUtils.getCallStack();
        assertTrue(stack.contains("AuditUtilsTest"));
    }

    @Test
    void setMaxStackDepth_validValue_updates() {
        AuditUtils.setMaxStackDepth(3);
        assertEquals(3, AuditUtils.getMaxStackDepth());
        AuditUtils.setMaxStackDepth(5);
        assertEquals(5, AuditUtils.getMaxStackDepth());
    }

    @Test
    void setMaxStackDepth_zero_doesNotUpdate() {
        int before = AuditUtils.getMaxStackDepth();
        AuditUtils.setMaxStackDepth(0);
        assertEquals(before, AuditUtils.getMaxStackDepth());
    }

    @Test
    void setMaxStackDepth_negative_doesNotUpdate() {
        int before = AuditUtils.getMaxStackDepth();
        AuditUtils.setMaxStackDepth(-1);
        assertEquals(before, AuditUtils.getMaxStackDepth());
    }

    @Test
    void setMaxStackDepth_exceedsLimit_doesNotUpdate() {
        int before = AuditUtils.getMaxStackDepth();
        AuditUtils.setMaxStackDepth(21);
        assertEquals(before, AuditUtils.getMaxStackDepth());
    }

    @Test
    void setMaxStackDepth_atUpperLimit_updates() {
        AuditUtils.setMaxStackDepth(20);
        assertEquals(20, AuditUtils.getMaxStackDepth());
    }

    @Test
    void initMaxStackDepth_defaultValue() {
        int depth = AuditUtils.initMaxStackDepth();
        assertTrue(depth > 0);
        assertTrue(depth <= 20);
    }

    @Test
    void getCallStack_respectsDepth() {
        AuditUtils.setMaxStackDepth(1);
        String stack = AuditUtils.getCallStack();
        assertFalse(stack.contains(" <- "), "depth=1 should only show one frame");
        AuditUtils.setMaxStackDepth(5);
    }

    @Test
    void setMaxStackDepth_overwritesPreviousValue() {
        AuditUtils.setMaxStackDepth(10);
        assertEquals(10, AuditUtils.getMaxStackDepth());
        // Subsequent set should overwrite
        AuditUtils.setMaxStackDepth(3);
        assertEquals(3, AuditUtils.getMaxStackDepth());
        AuditUtils.setMaxStackDepth(5);
    }

    @Test
    void setMaxStackDepth_concurrentWrites_noException() throws InterruptedException {
        // Verify that concurrent setMaxStackDepth calls don't throw or leave state corrupted
        Thread[] threads = new Thread[10];
        for (int i = 0; i < threads.length; i++) {
            final int depth = i + 1;
            threads[i] = new Thread(() -> AuditUtils.setMaxStackDepth(depth));
            threads[i].start();
        }
        for (Thread t : threads) {
            t.join();
        }
        int finalDepth = AuditUtils.getMaxStackDepth();
        assertTrue(finalDepth >= 1 && finalDepth <= 10, "Final depth should be between 1 and 10, got: " + finalDepth);
        AuditUtils.setMaxStackDepth(5);
    }
}
