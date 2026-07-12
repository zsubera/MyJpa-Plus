package com.zsubera.jpa.template;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link DeepPaginationGuard} — warning throttling and hard limit enforcement.
 */
class DeepPaginationGuardTest {

    @Test
    void belowThresholdNoWarningNoException() {
        AtomicLong lastWarn = new AtomicLong(0);
        assertDoesNotThrow(() -> DeepPaginationGuard.check(100, 1000, 5000, lastWarn));
    }

    @Test
    void atThresholdDoesNotThrow() {
        AtomicLong lastWarn = new AtomicLong(0);
        assertDoesNotThrow(() -> DeepPaginationGuard.check(1000, 1000, 5000, lastWarn));
    }

    @Test
    void exceedsHardLimitThrows() {
        AtomicLong lastWarn = new AtomicLong(0);
        IllegalArgumentException ex =
            assertThrows(IllegalArgumentException.class, () -> DeepPaginationGuard.check(5001, 1000, 5000, lastWarn));
        assertTrue(ex.getMessage().contains("exceeds"));
        assertTrue(ex.getMessage().contains("5000"));
    }

    @Test
    void hardLimitDisabledByNegativeOne() {
        AtomicLong lastWarn = new AtomicLong(0);
        // hardLimit = -1 means disabled; even huge offset should not throw
        assertDoesNotThrow(() -> DeepPaginationGuard.check(999999, 1000, -1, lastWarn));
    }

    @Test
    void thresholdDisabledByZero() {
        AtomicLong lastWarn = new AtomicLong(0);
        // threshold = 0 means disabled
        assertDoesNotThrow(() -> DeepPaginationGuard.check(999999, 0, -1, lastWarn));
    }

    @Test
    void hardLimitExactlyAtBoundaryDoesNotThrow() {
        AtomicLong lastWarn = new AtomicLong(0);
        assertDoesNotThrow(() -> DeepPaginationGuard.check(5000, 1000, 5000, lastWarn));
    }

    @Test
    void hardLimitOneOverThrows() {
        AtomicLong lastWarn = new AtomicLong(0);
        assertThrows(IllegalArgumentException.class, () -> DeepPaginationGuard.check(5001, 1000, 5000, lastWarn));
    }

    @Test
    void warningThrottledOnRapidCalls() {
        AtomicLong lastWarn = new AtomicLong(0);
        // First call at threshold should set the timestamp
        DeepPaginationGuard.check(1000, 1000, 5000, lastWarn);
        long firstWarn = lastWarn.get();
        assertTrue(firstWarn > 0, "First call should set lastWarn timestamp");

        // Second call immediately after should NOT update timestamp (throttled)
        DeepPaginationGuard.check(1000, 1000, 5000, lastWarn);
        assertEquals(firstWarn, lastWarn.get(), "Second call should be throttled");
    }

    @Test
    void warningUpdatesAfterInterval() throws InterruptedException {
        AtomicLong lastWarn = new AtomicLong(0);
        DeepPaginationGuard.check(1000, 1000, 5000, lastWarn);
        long firstWarn = lastWarn.get();

        // Sleep briefly to simulate time passing (WARN_INTERVAL_MS is 60000, but we test the CAS logic)
        // We can't easily test the full interval without making the constant accessible,
        // but we can verify the CAS mechanism works
        Thread.sleep(10);

        // Force a new warn by manually resetting the timestamp far in the past
        lastWarn.set(0);
        DeepPaginationGuard.check(1000, 1000, 5000, lastWarn);
        assertTrue(lastWarn.get() > 0, "Should update after timestamp reset");
    }

    @Test
    void warningNotTriggeredBelowThreshold() {
        AtomicLong lastWarn = new AtomicLong(0);
        DeepPaginationGuard.check(500, 1000, 5000, lastWarn);
        assertEquals(0, lastWarn.get(), "No warning should be triggered below threshold");
    }

    @Test
    void zeroOffsetDoesNotTriggerWarning() {
        AtomicLong lastWarn = new AtomicLong(0);
        DeepPaginationGuard.check(0, 1000, 5000, lastWarn);
        assertEquals(0, lastWarn.get());
    }
}
