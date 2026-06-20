package com.zsubera.jpa.template;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link CacheAdapter} SPI and {@link DisabledCacheAdapter}.
 */
class CacheAdapterTest {

    private CacheAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new SimpleCacheAdapter();
    }

    @Test
    void get_returnsCachedValue() {
        adapter.put("key1", "value1", 60);

        String result = adapter.get("key1");
        assertEquals("value1", result);
    }

    @Test
    void get_unknownKey_returnsNull() {
        assertNull(adapter.get("nonexistent"));
    }

    @Test
    void put_overwritesExistingKey() {
        adapter.put("key1", "old", 60);
        adapter.put("key1", "new", 60);

        assertEquals("new", adapter.get("key1"));
    }

    @Test
    void evict_removesEntry() {
        adapter.put("key1", "value1", 60);
        assertEquals(1, adapter.size());

        adapter.evict("key1");

        assertNull(adapter.get("key1"));
        assertEquals(0, adapter.size());
    }

    @Test
    void clear_removesAllEntries() {
        adapter.put("key1", "value1", 60);
        adapter.put("key2", "value2", 60);

        adapter.clear();

        assertEquals(0, adapter.size());
    }

    @Test
    void evictByPrefix_removesMatchingEntries() {
        adapter.put("User:q1", "v1", 60);
        adapter.put("User:q2", "v2", 60);
        adapter.put("Order:q1", "v3", 60);

        int evicted = adapter.evictByPrefix("User:");

        assertEquals(2, evicted);
        assertNull(adapter.get("User:q1"));
        assertNull(adapter.get("User:q2"));
        assertNotNull(adapter.get("Order:q1"));
    }

    @Test
    void disabledAdapter_allOperationsAreNoop() {
        CacheAdapter disabled = CacheAdapter.disabled();

        assertNull(disabled.get("key1"));
        assertFalse(disabled.put("key1", "value1", 60));
        disabled.evict("key1");
        assertEquals(0, disabled.evictByPrefix("prefix"));
        disabled.clear();
        assertEquals(0, disabled.size());
        assertEquals(0.0, disabled.getHitRate(), 0.001);
        assertEquals(0, disabled.getHitCount());
        assertEquals(0, disabled.getMissCount());
        disabled.resetStats();
    }

    @Test
    void disabledAdapter_singleton() {
        CacheAdapter a1 = CacheAdapter.disabled();
        CacheAdapter a2 = CacheAdapter.disabled();
        assertSame(a1, a2);
    }

    @Test
    void queryCacheManager_implementsCacheAdapter() {
        QueryCacheManager qcm = new QueryCacheManager();
        assertTrue(qcm instanceof CacheAdapter);

        qcm.put("key1", "value1", 60);
        assertEquals("value1", ((CacheAdapter)qcm).get("key1"));
    }

    @Test
    void stats_tracking() {
        adapter.put("key1", "value1", 60);

        adapter.get("key1"); // hit
        adapter.get("key1"); // hit
        adapter.get("miss"); // miss

        assertTrue(adapter.getHitCount() >= 2);
        assertTrue(adapter.getMissCount() >= 1);
        double hitRate = adapter.getHitRate();
        assertTrue(hitRate > 0.5 && hitRate <= 1.0);
    }

    @Test
    void resetStats_clearsCounters() {
        adapter.put("key1", "value1", 60);
        adapter.get("key1");
        adapter.get("miss");

        adapter.resetStats();

        assertEquals(0, adapter.getHitCount());
        assertEquals(0, adapter.getMissCount());
        assertEquals(0.0, adapter.getHitRate(), 0.001);
    }

    /**
     * 简单的 CacheAdapter 实现，用于测试 SPI 接口。
     */
    private static class SimpleCacheAdapter implements CacheAdapter {
        private final Map<String, Object> store = new ConcurrentHashMap<>();
        private long hitCount = 0;
        private long missCount = 0;

        @Override
        public <T> T get(String key) {
            Object value = store.get(key);
            if (value != null) {
                hitCount++;
                @SuppressWarnings("unchecked")
                T result = (T)value;
                return result;
            }
            missCount++;
            return null;
        }

        @Override
        public <T> boolean put(String key, T value, long ttlSeconds) {
            store.put(key, value);
            return true;
        }

        @Override
        public void evict(String key) {
            store.remove(key);
        }

        @Override
        public int evictByPrefix(String keyPrefix) {
            int count = 0;
            var it = store.keySet().iterator();
            while (it.hasNext()) {
                if (it.next().startsWith(keyPrefix)) {
                    it.remove();
                    count++;
                }
            }
            return count;
        }

        @Override
        public void clear() {
            store.clear();
        }

        @Override
        public int size() {
            return store.size();
        }

        @Override
        public double getHitRate() {
            long total = hitCount + missCount;
            return total == 0 ? 0.0 : (double)hitCount / total;
        }

        @Override
        public long getHitCount() {
            return hitCount;
        }

        @Override
        public long getMissCount() {
            return missCount;
        }

        @Override
        public void resetStats() {
            hitCount = 0;
            missCount = 0;
        }
    }
}
