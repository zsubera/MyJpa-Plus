package com.zsubera.jpa.template;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link QueryCacheManager}.
 */
class QueryCacheManagerTest {

    private QueryCacheManager cache;

    @BeforeEach
    void setUp() {
        cache = new QueryCacheManager();
    }

    @Test
    void putAndGet_returnsCachedValue() {
        cache.put("key1", "value1", 60);

        String result = cache.get("key1");
        assertEquals("value1", result);
    }

    @Test
    void get_unknownKey_returnsNull() {
        assertNull(cache.get("nonexistent"));
    }

    @Test
    void get_expiredEntry_returnsNull() throws InterruptedException {
        cache.put("key1", "value1", 0);

        Thread.sleep(50);

        assertNull(cache.get("key1"));
    }

    @Test
    void evict_removesEntry() {
        cache.put("key1", "value1", 60);
        assertEquals(1, cache.size());

        cache.evict("key1");

        assertNull(cache.get("key1"));
        assertEquals(0, cache.size());
    }

    @Test
    void clear_removesAllEntries() {
        cache.put("key1", "value1", 60);
        cache.put("key2", "value2", 60);
        assertEquals(2, cache.size());

        cache.clear();

        assertEquals(0, cache.size());
        assertNull(cache.get("key1"));
        assertNull(cache.get("key2"));
    }

    @Test
    void put_overwritesExistingKey() {
        cache.put("key1", "old", 60);
        cache.put("key1", "new", 60);

        String result = cache.get("key1");
        assertEquals("new", result);
    }

    @Test
    void get_listValue() {
        List<String> list = List.of("a", "b", "c");
        cache.put("list-key", list, 60);

        List<String> result = cache.get("list-key");
        assertEquals(list, result);
    }

    @Test
    void size_returnsCorrectCount() {
        assertEquals(0, cache.size());

        cache.put("a", 1, 60);
        assertEquals(1, cache.size());

        cache.put("b", 2, 60);
        assertEquals(2, cache.size());

        cache.evict("a");
        assertEquals(1, cache.size());
    }

    @Test
    void expiredEntry_removedOnGet() throws InterruptedException {
        cache.put("key1", "value1", 0);
        Thread.sleep(50);

        assertNull(cache.get("key1"));
        assertEquals(0, cache.size());
    }

    @Test
    void cachedQueryResult_isExpired() {
        CachedQueryResult<String> result = new CachedQueryResult<>("test", 0);

        assertTrue(result.isExpired());
        assertEquals("test", result.getValue());
        assertEquals(0, result.getTtlSeconds());
    }

    @Test
    void cachedQueryResult_notExpired() {
        CachedQueryResult<String> result = new CachedQueryResult<>("test", 3600);

        assertFalse(result.isExpired());
        assertEquals("test", result.getValue());
    }
}
