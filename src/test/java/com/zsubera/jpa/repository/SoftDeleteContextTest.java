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
        SoftDeleteContext.clear();
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
        SoftDeleteContext.clear();

        assertFalse(SoftDeleteContext.isIgnoreSoftDelete());
    }

    @Test
    void setIgnoreSoftDelete_true_pushesIgnore() {
        SoftDeleteContext.setIgnoreSoftDelete(true);
        assertTrue(SoftDeleteContext.isIgnoreSoftDelete());
    }

    @Test
    void setIgnoreSoftDelete_false_popsIgnore() {
        SoftDeleteContext.pushIgnore();
        SoftDeleteContext.setIgnoreSoftDelete(false);
        assertFalse(SoftDeleteContext.isIgnoreSoftDelete());
    }

    @Test
    void setIgnoreSoftDelete_supportsNesting() {
        SoftDeleteContext.setIgnoreSoftDelete(true);
        SoftDeleteContext.setIgnoreSoftDelete(true);

        assertTrue(SoftDeleteContext.isIgnoreSoftDelete());

        SoftDeleteContext.setIgnoreSoftDelete(false);
        assertTrue(SoftDeleteContext.isIgnoreSoftDelete());

        SoftDeleteContext.setIgnoreSoftDelete(false);
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
}
