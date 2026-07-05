package com.zsubera.jpa.repository;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class SoftDeleteContextTest {

    @AfterEach
    void cleanup() {
        SoftDeleteContext.reset();
    }

    @Test
    void defaultStateIsNotIgnoring() {
        assertFalse(SoftDeleteContext.isIgnoreSoftDelete());
        assertEquals(0, SoftDeleteContext.getIgnoreCount());
    }

    @Test
    void pushPopSingleLevel() {
        SoftDeleteContext.pushIgnore();
        assertTrue(SoftDeleteContext.isIgnoreSoftDelete());
        assertEquals(1, SoftDeleteContext.getIgnoreCount());

        SoftDeleteContext.popIgnore();
        assertFalse(SoftDeleteContext.isIgnoreSoftDelete());
        assertEquals(0, SoftDeleteContext.getIgnoreCount());
    }

    @Test
    void pushPopNestedLevels() {
        SoftDeleteContext.pushIgnore();
        SoftDeleteContext.pushIgnore();
        assertEquals(2, SoftDeleteContext.getIgnoreCount());

        SoftDeleteContext.popIgnore();
        assertTrue(SoftDeleteContext.isIgnoreSoftDelete());
        assertEquals(1, SoftDeleteContext.getIgnoreCount());

        SoftDeleteContext.popIgnore();
        assertFalse(SoftDeleteContext.isIgnoreSoftDelete());
    }

    @Test
    void popBeyondZeroLogsWarningAndResets() {
        SoftDeleteContext.popIgnore();
        assertFalse(SoftDeleteContext.isIgnoreSoftDelete());
        assertEquals(0, SoftDeleteContext.getIgnoreCount());
    }

    @Test
    void pushExceedingMaxThrows() {
        int max = SoftDeleteContext.getMaxIgnoreCount();
        for (int i = 0; i < max; i++) {
            SoftDeleteContext.pushIgnore();
        }
        assertThrows(IllegalStateException.class, SoftDeleteContext::pushIgnore);
    }

    @Test
    void withIgnoreRunnableExecutesAndCleansUp() {
        SoftDeleteContext.withIgnore(() -> {
            assertTrue(SoftDeleteContext.isIgnoreSoftDelete());
        });
        assertFalse(SoftDeleteContext.isIgnoreSoftDelete());
    }

    @Test
    void withIgnoreRunnableCleansUpOnException() {
        try {
            SoftDeleteContext.withIgnore(() -> {
                throw new RuntimeException("boom");
            });
        } catch (RuntimeException ignored) {
        }
        assertFalse(SoftDeleteContext.isIgnoreSoftDelete());
    }

    @Test
    void withIgnoreSupplierReturnsValue() {
        String result = SoftDeleteContext.withIgnore(() -> "hello");
        assertEquals("hello", result);
        assertFalse(SoftDeleteContext.isIgnoreSoftDelete());
    }

    @Test
    void withIgnoreSupplierCleansUpOnException() {
        try {
            SoftDeleteContext.withIgnore((java.util.function.Supplier<String>)() -> {
                throw new RuntimeException("boom");
            });
        } catch (RuntimeException ignored) {
        }
        assertFalse(SoftDeleteContext.isIgnoreSoftDelete());
    }

    @Test
    void ignoreScopeAutoCloseable() throws Exception {
        try (AutoCloseable scope = SoftDeleteContext.ignoreScope()) {
            assertTrue(SoftDeleteContext.isIgnoreSoftDelete());
        }
        assertFalse(SoftDeleteContext.isIgnoreSoftDelete());
    }

    @Test
    void captureAndResetForAsync() {
        SoftDeleteContext.pushIgnore();
        SoftDeleteContext.pushIgnore();
        int captured = SoftDeleteContext.captureAndResetForAsync();
        assertEquals(2, captured);
        assertFalse(SoftDeleteContext.isIgnoreSoftDelete());

        SoftDeleteContext.restoreForAsync(captured);
        assertTrue(SoftDeleteContext.isIgnoreSoftDelete());
        assertEquals(2, SoftDeleteContext.getIgnoreCount());
    }

    @Test
    void captureAndResetForAsyncZeroDoesNothing() {
        int captured = SoftDeleteContext.captureAndResetForAsync();
        assertEquals(0, captured);
        assertFalse(SoftDeleteContext.isIgnoreSoftDelete());
    }

    @Test
    void restoreForAsyncZeroDoesNothing() {
        SoftDeleteContext.restoreForAsync(0);
        assertFalse(SoftDeleteContext.isIgnoreSoftDelete());
    }

    @Test
    void resetClearsAll() {
        SoftDeleteContext.pushIgnore();
        SoftDeleteContext.pushIgnore();
        SoftDeleteContext.reset();
        assertFalse(SoftDeleteContext.isIgnoreSoftDelete());
        assertEquals(0, SoftDeleteContext.getIgnoreCount());
    }

    @Test
    void threadIsolation() throws InterruptedException {
        AtomicBoolean otherThreadSawIgnore = new AtomicBoolean(false);
        CountDownLatch latch1 = new CountDownLatch(1);
        CountDownLatch latch2 = new CountDownLatch(1);

        SoftDeleteContext.pushIgnore();

        Thread t = new Thread(() -> {
            latch1.countDown();
            try {
                latch2.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            otherThreadSawIgnore.set(SoftDeleteContext.isIgnoreSoftDelete());
        });
        t.start();
        latch1.await();
        latch2.countDown();
        t.join();

        assertFalse(otherThreadSawIgnore.get());
        assertTrue(SoftDeleteContext.isIgnoreSoftDelete());
    }
}
