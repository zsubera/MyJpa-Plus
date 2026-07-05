package com.zsubera.jpa.template;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class CachedQueryResultTest {

    @Test
    void constructor_validArgs_succeeds() {
        CachedQueryResult<String> result = new CachedQueryResult<>("value", 60);
        assertEquals("value", result.getValue());
        assertEquals(60, result.getTtlSeconds());
    }

    @Test
    void constructor_zeroTtl_succeeds() {
        CachedQueryResult<String> result = new CachedQueryResult<>("value", 0);
        assertEquals(0, result.getTtlSeconds());
        assertTrue(result.isExpired());
    }

    @Test
    void constructor_nullValue_throws() {
        assertThrows(IllegalArgumentException.class, () -> new CachedQueryResult<>(null, 60));
    }

    @Test
    void constructor_negativeTtl_throws() {
        assertThrows(IllegalArgumentException.class, () -> new CachedQueryResult<>("value", -1));
    }

    @Test
    void constructor_exceedsMaxTtl_throws() {
        long oneYearSeconds = 365L * 24 * 3600;
        assertThrows(IllegalArgumentException.class, () -> new CachedQueryResult<>("value", oneYearSeconds + 1));
    }

    @Test
    void constructor_exactMaxTtl_succeeds() {
        long oneYearSeconds = 365L * 24 * 3600;
        assertDoesNotThrow(() -> new CachedQueryResult<>("value", oneYearSeconds));
    }

    @Test
    void isExpired_withLargeTtl_notExpired() {
        CachedQueryResult<String> result = new CachedQueryResult<>("value", 3600);
        assertFalse(result.isExpired());
    }

    @Test
    void getValue_returnsCorrectValue() {
        CachedQueryResult<Integer> result = new CachedQueryResult<>(42, 60);
        assertEquals(42, result.getValue());
    }
}
