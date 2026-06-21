package com.zsubera.jpa.repository;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link SoftDeleteContext} ThreadLocal counter behavior.
 */
class SoftDeleteContextTest {

    @AfterEach
    void cleanup() {
        // Ensure ThreadLocal is cleaned after each test
        SoftDeleteContext.reset();
    }

    @Test
    void isIgnoreSoftDelete_default_returnsFalse() {
        assertFalse(SoftDeleteContext.isIgnoreSoftDelete());
    }

    @Test
    void pushIgnore_setsIgnoreFlag() {
        SoftDeleteContext.pushIgnore();
        assertTrue(SoftDeleteContext.isIgnoreSoftDelete());
    }

    @Test
    void popIgnore_clearsIgnoreFlag() {
        SoftDeleteContext.pushIgnore();
        SoftDeleteContext.popIgnore();
        assertFalse(SoftDeleteContext.isIgnoreSoftDelete());
    }

    @Test
    void pushIgnore_supportsNesting() {
        SoftDeleteContext.pushIgnore();
        SoftDeleteContext.pushIgnore();

        assertTrue(SoftDeleteContext.isIgnoreSoftDelete());

        SoftDeleteContext.popIgnore();
        // Still ignoring because of outer push
        assertTrue(SoftDeleteContext.isIgnoreSoftDelete());

        SoftDeleteContext.popIgnore();
        assertFalse(SoftDeleteContext.isIgnoreSoftDelete());
    }

    @Test
    void popIgnore_onZeroCount_doesNothing() {
        // Should not throw when popping without push
        SoftDeleteContext.popIgnore();
        assertFalse(SoftDeleteContext.isIgnoreSoftDelete());
    }

    @Test
    void clear_removesAllIgnores() {
        SoftDeleteContext.pushIgnore();
        SoftDeleteContext.pushIgnore();
        SoftDeleteContext.reset();

        assertFalse(SoftDeleteContext.isIgnoreSoftDelete());
    }

    @Test
    void setIgnoreSoftDelete_true_pushesIgnore() {
        SoftDeleteContext.pushIgnore();
        assertTrue(SoftDeleteContext.isIgnoreSoftDelete());
    }

    @Test
    void setIgnoreSoftDelete_false_popsIgnore() {
        SoftDeleteContext.pushIgnore();
        SoftDeleteContext.popIgnore();
        assertFalse(SoftDeleteContext.isIgnoreSoftDelete());
    }

    @Test
    void setIgnoreSoftDelete_supportsNesting() {
        SoftDeleteContext.pushIgnore();
        SoftDeleteContext.pushIgnore();

        assertTrue(SoftDeleteContext.isIgnoreSoftDelete());

        SoftDeleteContext.popIgnore();
        assertTrue(SoftDeleteContext.isIgnoreSoftDelete());

        SoftDeleteContext.popIgnore();
        assertFalse(SoftDeleteContext.isIgnoreSoftDelete());
    }

    @Test
    void threadLocal_isThreadIsolated() throws InterruptedException {
        SoftDeleteContext.pushIgnore();
        assertTrue(SoftDeleteContext.isIgnoreSoftDelete());

        // Check from another thread
        Thread otherThread = new Thread(() -> {
            assertFalse(SoftDeleteContext.isIgnoreSoftDelete());
            SoftDeleteContext.pushIgnore();
            assertTrue(SoftDeleteContext.isIgnoreSoftDelete());
            SoftDeleteContext.popIgnore();
        });
        otherThread.start();
        otherThread.join();

        // Original thread still has ignore
        assertTrue(SoftDeleteContext.isIgnoreSoftDelete());
        SoftDeleteContext.popIgnore();
    }

    @Test
    void getIgnoreCount_returnsCorrectValue() {
        assertEquals(0, SoftDeleteContext.getIgnoreCount());
        SoftDeleteContext.pushIgnore();
        assertEquals(1, SoftDeleteContext.getIgnoreCount());
        SoftDeleteContext.pushIgnore();
        assertEquals(2, SoftDeleteContext.getIgnoreCount());
        SoftDeleteContext.popIgnore();
        assertEquals(1, SoftDeleteContext.getIgnoreCount());
        SoftDeleteContext.popIgnore();
        assertEquals(0, SoftDeleteContext.getIgnoreCount());
    }

    @Test
    void getMaxIgnoreCount_returnsDefaultValue() {
        int defaultVal = SoftDeleteContext.getMaxIgnoreCount();
        assertTrue(defaultVal > 0);
    }

    @Test
    void setMaxIgnoreCount_validValue_updates() {
        int original = SoftDeleteContext.getMaxIgnoreCount();
        try {
            SoftDeleteContext.setMaxIgnoreCount(128);
            assertEquals(128, SoftDeleteContext.getMaxIgnoreCount());
        } finally {
            SoftDeleteContext.setMaxIgnoreCount(original);
        }
    }

    @Test
    void setMaxIgnoreCount_zeroValue_doesNotUpdate() {
        int original = SoftDeleteContext.getMaxIgnoreCount();
        SoftDeleteContext.setMaxIgnoreCount(0);
        assertEquals(original, SoftDeleteContext.getMaxIgnoreCount());
    }

    @Test
    void setMaxIgnoreCount_negativeValue_doesNotUpdate() {
        int original = SoftDeleteContext.getMaxIgnoreCount();
        SoftDeleteContext.setMaxIgnoreCount(-1);
        assertEquals(original, SoftDeleteContext.getMaxIgnoreCount());
    }

    @Test
    void setMaxIgnoreCount_exceedsMax_doesNotUpdate() {
        int original = SoftDeleteContext.getMaxIgnoreCount();
        SoftDeleteContext.setMaxIgnoreCount(2000);
        assertEquals(original, SoftDeleteContext.getMaxIgnoreCount());
    }

    @Test
    void pushIgnore_exceedsMax_throwsIllegalState() {
        int original = SoftDeleteContext.getMaxIgnoreCount();
        try {
            SoftDeleteContext.setMaxIgnoreCount(2);
            SoftDeleteContext.pushIgnore();
            SoftDeleteContext.pushIgnore();
            assertThrows(IllegalStateException.class, SoftDeleteContext::pushIgnore);
        } finally {
            SoftDeleteContext.setMaxIgnoreCount(original);
        }
    }

    @Test
    void withIgnoreRunnable_executesAndClears() {
        SoftDeleteContext.withIgnore(() -> {
            assertTrue(SoftDeleteContext.isIgnoreSoftDelete());
        });
        assertFalse(SoftDeleteContext.isIgnoreSoftDelete());
    }

    @Test
    void withIgnoreRunnable_clearsOnException() {
        try {
            SoftDeleteContext.withIgnore(() -> {
                assertTrue(SoftDeleteContext.isIgnoreSoftDelete());
                throw new RuntimeException("test");
            });
            fail("Should have thrown");
        } catch (RuntimeException e) {
            assertFalse(SoftDeleteContext.isIgnoreSoftDelete());
        }
    }

    @Test
    void withIgnoreSupplier_returnsValue() {
        String result = SoftDeleteContext.withIgnore(() -> {
            assertTrue(SoftDeleteContext.isIgnoreSoftDelete());
            return "hello";
        });
        assertEquals("hello", result);
        assertFalse(SoftDeleteContext.isIgnoreSoftDelete());
    }

    @Test
    void withIgnoreSupplier_clearsOnException() {
        try {
            SoftDeleteContext.withIgnore(() -> {
                assertTrue(SoftDeleteContext.isIgnoreSoftDelete());
                throw new RuntimeException("test");
            });
            fail("Should have thrown");
        } catch (RuntimeException e) {
            assertFalse(SoftDeleteContext.isIgnoreSoftDelete());
        }
    }

    @Test
    void captureAndResetForAsync_whenZero_returnsZero() {
        int captured = SoftDeleteContext.captureAndResetForAsync();
        assertEquals(0, captured);
        assertFalse(SoftDeleteContext.isIgnoreSoftDelete());
    }

    @Test
    void captureAndResetForAsync_whenNonZero_capturesAndResets() {
        SoftDeleteContext.pushIgnore();
        SoftDeleteContext.pushIgnore();
        int captured = SoftDeleteContext.captureAndResetForAsync();
        assertEquals(2, captured);
        assertFalse(SoftDeleteContext.isIgnoreSoftDelete());
    }

    @Test
    void restoreForAsync_whenZero_doesNothing() {
        SoftDeleteContext.restoreForAsync(0);
        assertFalse(SoftDeleteContext.isIgnoreSoftDelete());
    }

    @Test
    void restoreForAsync_whenNonZero_restoresState() {
        SoftDeleteContext.restoreForAsync(3);
        assertTrue(SoftDeleteContext.isIgnoreSoftDelete());
        assertEquals(3, SoftDeleteContext.getIgnoreCount());
    }
}
