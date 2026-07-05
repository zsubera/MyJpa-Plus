package com.zsubera.jpa.template;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class DeepPaginationGuardTest {

    @Test
    void check_belowThreshold_noException() {
        assertDoesNotThrow(() -> DeepPaginationGuard.check(50, 100, 500, new AtomicLong(0)));
    }

    @Test
    void check_atThreshold_noException() {
        assertDoesNotThrow(() -> DeepPaginationGuard.check(100, 100, 500, new AtomicLong(0)));
    }

    @Test
    void check_aboveHardLimit_throws() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> DeepPaginationGuard.check(501, 100, 500, new AtomicLong(0)));
        assertTrue(ex.getMessage().contains("501"));
        assertTrue(ex.getMessage().contains("500"));
    }

    @Test
    void check_hardLimitDisabled_noException() {
        assertDoesNotThrow(() -> DeepPaginationGuard.check(10000, 100, -1, new AtomicLong(0)));
    }

    @Test
    void check_thresholdDisabled_noException() {
        assertDoesNotThrow(() -> DeepPaginationGuard.check(10000, 0, -1, new AtomicLong(0)));
    }

    @Test
    void check_justBelowHardLimit_noException() {
        assertDoesNotThrow(() -> DeepPaginationGuard.check(500, 100, 500, new AtomicLong(0)));
    }

    @Test
    void check_exceedsBothThresholdAndHardLimit_throws() {
        assertThrows(IllegalArgumentException.class, () -> DeepPaginationGuard.check(600, 100, 500, new AtomicLong(0)));
    }

    @Test
    void check_rateLimiting_preventsLogFlood() {
        AtomicLong lastWarn = new AtomicLong(0);
        assertDoesNotThrow(() -> {
            DeepPaginationGuard.check(200, 100, -1, lastWarn);
            DeepPaginationGuard.check(300, 100, -1, lastWarn);
            DeepPaginationGuard.check(400, 100, -1, lastWarn);
        });
    }
}
