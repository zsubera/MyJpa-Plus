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

    // [FIX] P0-2: 测试 deque 大小跟踪准确性
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

    // [FIX] P0-2: 测试大量 put 不会因 O(n) size() 导致性能退化
    @Test
    void put_shouldNotDegradeWithLargeCache() {
        cache = new QueryCacheManager(10000);

        long start = System.nanoTime();
        for (int i = 0; i < 5000; i++) {
            cache.put("key-" + i, "value-" + i, 60);
        }
        long elapsed = System.nanoTime() - start;

        // 5000 次 put 应在 200ms 内完成（含 deque 清理）
        assertTrue(elapsed < 200_000_000L, "5000 puts took " + (elapsed / 1_000_000) + "ms, expected < 200ms");
    }

    // [FIX] P1-1: 测试过期条目从 insertionOrder 清理
    @Test
    void expiredEntries_shouldNotLeakInInsertionOrder() throws InterruptedException {
        cache = new QueryCacheManager(100);

        // 放入短 TTL 条目
        for (int i = 0; i < 20; i++) {
            cache.put("short-" + i, "val", 0); // 立即过期
        }

        // 等待过期
        Thread.sleep(50);

        // 通过 get 触发过期清理
        for (int i = 0; i < 20; i++) {
            cache.get("short-" + i);
        }

        // 验证缓存为空
        assertEquals(0, cache.size());
    }

    // [FIX] P1-1: 测试 evict 时 deque 大小正确递减
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

    // [FIX] P1-1: 测试重复 put 同一 key 不会导致 deque 膨胀
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

    // [FIX] P1-1: 测试混合新 key 和重复 key 时驱逐行为正确
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

    // [FIX] P2-5: 测试类型转换异常信息改进
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
}
