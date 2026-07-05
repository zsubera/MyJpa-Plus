package com.zsubera.jpa.template;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class DisabledCacheAdapterTest {

    private final CacheAdapter adapter = CacheAdapter.disabled();

    @Test
    void singleton_sameInstance() {
        assertSame(CacheAdapter.disabled(), CacheAdapter.disabled());
    }

    @Test
    void get_returnsNull() {
        assertNull(adapter.get("any-key"));
    }

    @Test
    void put_returnsFalse() {
        assertFalse(adapter.put("key", "value", 60));
    }

    @Test
    void evict_noOp() {
        assertDoesNotThrow(() -> adapter.evict("key"));
    }

    @Test
    void evictByPrefix_returnsZero() {
        assertEquals(0, adapter.evictByPrefix("prefix:"));
    }

    @Test
    void clear_noOp() {
        assertDoesNotThrow(adapter::clear);
    }

    @Test
    void size_returnsZero() {
        assertEquals(0, adapter.size());
    }

    @Test
    void getHitRate_returnsZero() {
        assertEquals(0.0, adapter.getHitRate());
    }

    @Test
    void getHitCount_returnsZero() {
        assertEquals(0L, adapter.getHitCount());
    }

    @Test
    void getMissCount_returnsZero() {
        assertEquals(0L, adapter.getMissCount());
    }

    @Test
    void resetStats_noOp() {
        assertDoesNotThrow(adapter::resetStats);
    }
}
