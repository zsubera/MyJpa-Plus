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
    void get_expiredEntry_returnsNull() {
        cache.put("key1", "value1", 0);

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
    void expiredEntry_removedOnGet() {
        cache.put("key1", "value1", 0);

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

    @Test
    void put_shouldTrackDequeSizeCorrectly() {
        cache = new QueryCacheManager(100);

        for (int i = 0; i < 50; i++) {
            cache.put("key-" + i, "value-" + i, 60);
        }

        // 验证缓存大小正确
        assertEquals(50, cache.size());

        // 清除后大小为 0
        cache.clear();
        assertEquals(0, cache.size());
    }

    @Test
    void put_shouldNotDegradeWithLargeCache() {
        cache = new QueryCacheManager(10000);

        long start = System.nanoTime();
        for (int i = 0; i < 5000; i++) {
            cache.put("key-" + i, "value-" + i, 60);
        }
        long elapsed = System.nanoTime() - start;

        // 5000 次 put 应在 1000ms 内完成（含驱逐检查和索引维护）
        // ponytail: 放宽阈值以适应 Windows/GC 抖动，仍可检测 O(n²) 退化
        assertTrue(elapsed < 1_000_000_000L, "5000 puts took " + (elapsed / 1_000_000) + "ms, expected < 1000ms");
    }

    @Test
    void expiredEntries_shouldNotLeakInInsertionOrder() {
        cache = new QueryCacheManager(100);

        // 放入短 TTL 条目（TTL=0 立即过期）
        for (int i = 0; i < 20; i++) {
            cache.put("short-" + i, "val", 0);
        }

        // 通过 get 触发过期清理
        for (int i = 0; i < 20; i++) {
            cache.get("short-" + i);
        }

        // 验证缓存为空
        assertEquals(0, cache.size());
    }

    @Test
    void evict_shouldDecrementDequeSize() {
        cache = new QueryCacheManager(100);

        cache.put("a", 1, 60);
        cache.put("b", 2, 60);
        cache.put("c", 3, 60);

        cache.evict("b");

        assertEquals(2, cache.size());
        assertNull(cache.get("b"));
        assertEquals(Integer.valueOf(1), cache.get("a"));
        assertEquals(Integer.valueOf(3), cache.get("c"));
    }

    @Test
    void put_repeatedSameKey_shouldNotGrowDeque() {
        cache = new QueryCacheManager(100);

        // 重复 put 同一 key 100 次
        for (int i = 0; i < 100; i++) {
            cache.put("same-key", "value-" + i, 60);
        }

        // 缓存中只有 1 个条目
        assertEquals(1, cache.size());
        // 最终值应为最后一次 put 的值
        assertEquals("value-99", cache.get("same-key"));
    }

    @Test
    void put_mixedNewAndDuplicateKeys_evictionShouldWork() {
        cache = new QueryCacheManager(100);

        // 写入 10 个不同 key
        for (int i = 0; i < 10; i++) {
            cache.put("key-" + i, "value-" + i, 60);
        }

        // 重复写入已有 key——不应导致缓存条目数增加
        for (int i = 0; i < 10; i++) {
            cache.put("key-" + i, "updated-" + i, 60);
        }
        assertEquals(10, cache.size(), "Updating existing keys should not change cache size");

        // 所有 key 的值应更新
        for (int i = 0; i < 10; i++) {
            assertEquals("updated-" + i, cache.get("key-" + i), "Key key-" + i + " should have updated value");
        }
    }

    @Test
    void get_typeMismatch_shouldThrowInformativeException() {
        cache = new QueryCacheManager(100);
        cache.put("key1", "stringValue", 60);

        // 存储 String，以 Integer 类型获取
        assertThrows(ClassCastException.class, () -> {
            @SuppressWarnings("unchecked")
            Integer result = cache.get("key1");
        });
    }

    @Test
    void put_overCapacity_evictsOldestEntries() {
        cache = new QueryCacheManager(10);

        // 写入超过容量的条目
        for (int i = 0; i < 20; i++) {
            cache.put("key-" + i, "value-" + i, 60);
        }

        // 缓存大小不应超过 maxEntries
        assertTrue(cache.size() <= 10, "Cache size should not exceed maxEntries, got: " + cache.size());

        // 最新的条目应该存在
        assertNotNull(cache.get("key-19"), "Most recent entry should exist");
    }

    @Test
    void put_highVolume_noInfiniteLoop() {
        cache = new QueryCacheManager(50);

        // 大量写入，验证驱逐不会导致无限循环或长时间阻塞
        long start = System.currentTimeMillis();
        for (int i = 0; i < 500; i++) {
            cache.put("bulk-" + i, "val-" + i, 60);
        }
        long elapsed = System.currentTimeMillis() - start;

        // 应在合理时间内完成（< 2秒）
        assertTrue(elapsed < 2000, "High-volume put should complete quickly, took: " + elapsed + "ms");
        assertTrue(cache.size() <= 50, "Cache should respect maxEntries after high-volume writes");
    }

    @Test
    void evictByPrefix_removesMatchingEntries() {
        cache.put("User:q1", "v1", 60);
        cache.put("User:q2", "v2", 60);
        cache.put("Order:q1", "v3", 60);

        int evicted = cache.evictByPrefix("User:");

        assertEquals(2, evicted);
        assertNull(cache.get("User:q1"));
        assertNull(cache.get("User:q2"));
        assertNotNull(cache.get("Order:q1"));
    }

    @Test
    void evictByPrefix_noMatch_returnsZero() {
        cache.put("User:q1", "v1", 60);

        int evicted = cache.evictByPrefix("Order:");

        assertEquals(0, evicted);
        assertNotNull(cache.get("User:q1"));
    }

    @Test
    void evictByPrefix_nullPrefix_returnsZero() {
        assertEquals(0, cache.evictByPrefix(null));
    }

    @Test
    void evictByPrefix_emptyPrefix_returnsZero() {
        assertEquals(0, cache.evictByPrefix(""));
    }

    @Test
    void getHitRate_returnsCorrectRate() {
        cache.put("key1", "value1", 60);

        cache.get("key1"); // hit
        cache.get("key1"); // hit
        cache.get("nonexistent"); // miss

        double hitRate = cache.getHitRate();
        assertTrue(hitRate > 0.5 && hitRate <= 1.0);
    }

    @Test
    void getHitRate_emptyCache_returnsZero() {
        assertEquals(0.0, cache.getHitRate(), 0.001);
    }

    @Test
    void getHitCount_returnsCorrectCount() {
        cache.put("key1", "value1", 60);

        cache.get("key1");
        cache.get("key1");

        assertTrue(cache.getHitCount() >= 2);
    }

    @Test
    void getMissCount_returnsCorrectCount() {
        cache.get("nonexistent");

        assertTrue(cache.getMissCount() >= 1);
    }

    @Test
    void resetStats_clearsCounters() {
        cache.put("key1", "value1", 60);
        cache.get("key1");
        cache.get("nonexistent");

        cache.resetStats();

        assertEquals(0, cache.getHitCount());
        assertEquals(0, cache.getMissCount());
        assertEquals(0.0, cache.getHitRate(), 0.001);
    }

    @Test
    void evictByPrefixAfterTransactionCommit_evictsImmediately() {
        cache.put("User:q1", "v1", 60);
        cache.put("Order:q1", "v2", 60);

        cache.evictByPrefixAfterTransactionCommit("User:");

        assertNull(cache.get("User:q1"));
        assertNotNull(cache.get("Order:q1"));
    }

    @Test
    void evictByPrefixAfterTransactionCommit_nullPrefix_noop() {
        cache.put("key1", "v1", 60);
        cache.evictByPrefixAfterTransactionCommit(null);
        assertNotNull(cache.get("key1"));
    }

    @Test
    void clearAfterTransactionCommit_clearsAll() {
        cache.put("key1", "v1", 60);
        cache.put("key2", "v2", 60);

        cache.clearAfterTransactionCommit();

        assertEquals(0, cache.size());
    }

    @Test
    void setMaxEntries_updatesMaxEntries() {
        cache.setMaxEntries(50);
        assertEquals(50, cache.getMaxEntries());
    }

    @Test
    void setMaxEntries_invalid_throws() {
        assertThrows(IllegalArgumentException.class, () -> cache.setMaxEntries(0));
        assertThrows(IllegalArgumentException.class, () -> cache.setMaxEntries(-1));
    }

    @Test
    void constructor_invalidMaxEntries_throws() {
        assertThrows(IllegalArgumentException.class, () -> new QueryCacheManager(0));
        assertThrows(IllegalArgumentException.class, () -> new QueryCacheManager(-1));
    }

    @Test
    void put_nullKey_returnsFalse() {
        assertFalse(cache.put(null, "value", 60));
    }

    @Test
    void put_emptyKey_returnsFalse() {
        assertFalse(cache.put("", "value", 60));
    }

    @Test
    void put_negativeTtl_throws() {
        assertThrows(IllegalArgumentException.class, () -> cache.put("key", "value", -1));
    }

    @Test
    void put_keyTooLong_returnsFalse() {
        String longKey = "a".repeat(1025);
        assertFalse(cache.put(longKey, "value", 60));
    }

    @Test
    void put_keyAtMaxLength_succeeds() {
        String maxKey = "a".repeat(1024);
        assertTrue(cache.put(maxKey, "value", 60));
    }

    @Test
    void cachedQueryResult_nullValue_throws() {
        assertThrows(IllegalArgumentException.class, () -> new CachedQueryResult<>(null, 60));
    }

    @Test
    void cachedQueryResult_negativeTtl_throws() {
        assertThrows(IllegalArgumentException.class, () -> new CachedQueryResult<>("val", -1));
    }

    @Test
    void cachedQueryResult_exceedsOneYear_throws() {
        long oneYearPlus = 365L * 24 * 3600 + 1;
        assertThrows(IllegalArgumentException.class, () -> new CachedQueryResult<>("val", oneYearPlus));
    }

    @Test
    void cachedQueryResult_maxOneYear_succeeds() {
        long oneYear = 365L * 24 * 3600;
        assertDoesNotThrow(() -> new CachedQueryResult<>("val", oneYear));
    }

    @Test
    void evictByPrefixAfterTransactionCommit_emptyPrefix_noop() {
        cache.put("key1", "v1", 60);
        cache.evictByPrefixAfterTransactionCommit("");
        assertNotNull(cache.get("key1"));
    }

    @Test
    void clearAfterTransactionCommit_emptyPrefix() {
        cache.put("key1", "v1", 60);
        cache.clearAfterTransactionCommit();
        assertEquals(0, cache.size());
    }

    @Test
    void evictionSampling_triggersOnPutInterval() {
        cache = new QueryCacheManager(10);
        for (int i = 0; i < 25; i++) {
            cache.put("evict-" + i, "val-" + i, 60);
        }
        assertTrue(cache.size() <= 20);
    }

    @Test
    void evictionSampling_triggersOnGetInterval() {
        cache = new QueryCacheManager(10);
        for (int i = 0; i < 10; i++) {
            cache.put("key-" + i, "val-" + i, 60);
        }
        for (int i = 0; i < 15; i++) {
            cache.get("key-" + i);
        }
        assertTrue(cache.size() <= 10);
    }

    @Test
    void evictByPrefix_removesAllMatchingAndDequeEntry() {
        cache = new QueryCacheManager(100);
        cache.put("User:a", "v1", 60);
        cache.put("User:b", "v2", 60);
        cache.put("Order:a", "v3", 60);
        int evicted = cache.evictByPrefix("User:");
        assertEquals(2, evicted);
        assertEquals(1, cache.size());
    }

    @Test
    void driftCleanup_fullScan() {
        cache = new QueryCacheManager(10);
        for (int i = 0; i < 30; i++) {
            cache.put("k" + i, "v" + i, 60);
        }
        cache.clear();
        for (int i = 0; i < 30; i++) {
            cache.put("n" + i, "v" + i, 60);
        }
        assertTrue(cache.size() <= 10);
    }

    // ---- evictByPrefix prefixIndex race condition ----

    @Test
    void evictByPrefix_concurrentAdd_doesNotLeakPrefixEntry() {
        cache = new QueryCacheManager(100);
        cache.put("TestEntity:a", "v1", 60);
        cache.put("TestEntity:b", "v2", 60);

        // 模拟并发 addToPrefixIndex：前缀下的所有 key 被 evict 后并发添加新 key
        cache.evictByPrefix("TestEntity:");
        assertEquals(0, cache.size(), "All keys under prefix should be evicted");

        // 并发添加应能创建新的 prefixIndex 条目
        cache.put("TestEntity:c", "v3", 60);
        cache.put("TestEntity:d", "v4", 60);

        // 再次 evict 应正常工作
        int evicted = cache.evictByPrefix("TestEntity:");
        assertEquals(2, evicted, "Should evict entries added after first evict");
    }

    @Test
    void evictByPrefix_multiLevelPrefix_worksCorrectly() {
        cache = new QueryCacheManager(100);
        cache.put("com.example.User:1:name", "v1", 60);
        cache.put("com.example.User:1:email", "v2", 60);
        cache.put("com.example.Order:1", "v3", 60);

        int evicted = cache.evictByPrefix("com.example.User");
        assertEquals(2, evicted, "Should evict all User entries despite multi-level prefix");
        assertEquals(1, cache.size(), "Order entry should remain");
    }

    // ---- eviction generation tests (Fix #1: cache race condition) ----

    @Test
    void evictionGeneration_initialValueIsZero() {
        assertEquals(0, cache.getEvictionGeneration());
    }

    @Test
    void evictionGeneration_incrementsOnEvictByPrefix() {
        cache.put("User:q1", "v1", 60);

        cache.evictByPrefix("User:");

        assertEquals(1, cache.getEvictionGeneration());
    }

    @Test
    void evictionGeneration_incrementsOnClear() {
        cache.put("key1", "v1", 60);

        cache.clear();

        assertEquals(1, cache.getEvictionGeneration());
    }

    @Test
    void evictionGeneration_incrementsMultipleTimes() {
        cache.put("User:q1", "v1", 60);
        cache.put("Order:q1", "v2", 60);

        cache.evictByPrefix("User:");
        cache.evictByPrefix("Order:");
        cache.clear();

        assertEquals(3, cache.getEvictionGeneration());
    }

    @Test
    void evictionGeneration_noIncrementOnEmptyEvict() {
        cache.put("User:q1", "v1", 60);

        // Evict non-existent prefix — should not increment
        cache.evictByPrefix("Nonexistent:");

        assertEquals(0, cache.getEvictionGeneration());
    }
}
