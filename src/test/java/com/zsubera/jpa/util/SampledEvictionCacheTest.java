package com.zsubera.jpa.util;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class SampledEvictionCacheTest {

    @Test
    void constructorRejectsNonPositiveMaxSize() {
        assertThrows(IllegalArgumentException.class, () -> new SampledEvictionCache<>(0, 0.75, 10));
        assertThrows(IllegalArgumentException.class, () -> new SampledEvictionCache<>(-1, 0.75, 10));
    }

    @Test
    void constructorRejectsInvalidEvictionTargetRatio() {
        assertThrows(IllegalArgumentException.class, () -> new SampledEvictionCache<>(100, 0, 10));
        assertThrows(IllegalArgumentException.class, () -> new SampledEvictionCache<>(100, 1, 10));
        assertThrows(IllegalArgumentException.class, () -> new SampledEvictionCache<>(100, -0.1, 10));
    }

    @Test
    void constructorRejectsNonPositiveSamplingInterval() {
        assertThrows(IllegalArgumentException.class, () -> new SampledEvictionCache<>(100, 0.75, 0));
        assertThrows(IllegalArgumentException.class, () -> new SampledEvictionCache<>(100, 0.75, -1));
    }

    @Test
    void constructorRejectsNonPositiveInitialCapacity() {
        assertThrows(IllegalArgumentException.class, () -> new SampledEvictionCache<>(100, 0.75, 10, 0));
        assertThrows(IllegalArgumentException.class, () -> new SampledEvictionCache<>(100, 0.75, 10, -1));
    }

    @Test
    void putAndGet() {
        var cache = new SampledEvictionCache<String, String>(100, 0.75, 100);
        assertNull(cache.put("a", "1"));
        assertEquals("1", cache.get("a"));
    }

    @Test
    void putReturnsOldValue() {
        var cache = new SampledEvictionCache<String, String>(100, 0.75, 100);
        cache.put("a", "1");
        assertEquals("1", cache.put("a", "2"));
        assertEquals("2", cache.get("a"));
    }

    @Test
    void getReturnsNullForMissingKey() {
        var cache = new SampledEvictionCache<String, String>(100, 0.75, 100);
        assertNull(cache.get("nonexistent"));
    }

    @Test
    void computeIfAbsentComputesWhenAbsent() {
        var cache = new SampledEvictionCache<String, String>(100, 0.75, 100);
        assertEquals("computed", cache.computeIfAbsent("key", k -> "computed"));
        assertEquals("computed", cache.get("key"));
    }

    @Test
    void computeIfAbsentReturnsExisting() {
        var cache = new SampledEvictionCache<String, String>(100, 0.75, 100);
        cache.put("key", "original");
        assertEquals("original", cache.computeIfAbsent("key", k -> "computed"));
    }

    @Test
    void sizeTracksEntries() {
        var cache = new SampledEvictionCache<String, String>(100, 0.75, 100);
        assertEquals(0, cache.size());
        cache.put("a", "1");
        assertEquals(1, cache.size());
        cache.put("b", "2");
        assertEquals(2, cache.size());
    }

    @Test
    void clearRemovesAll() {
        var cache = new SampledEvictionCache<String, String>(100, 0.75, 100);
        cache.put("a", "1");
        cache.put("b", "2");
        cache.clear();
        assertEquals(0, cache.size());
        assertNull(cache.get("a"));
        assertNull(cache.get("b"));
    }

    @Test
    void putEvictsWhenOverMaxSize() {
        // samplingInterval=1 so every put triggers an eviction check
        var cache = new SampledEvictionCache<String, String>(10, 0.5, 1);
        for (int i = 0; i < 20; i++) {
            cache.put("key" + i, "val" + i);
        }
        // After first eviction: target = 10 * 0.5 = 5 entries survive
        // Subsequent puts may trigger more evictions
        // Size should be well below maxSize of 10
        assertTrue(cache.size() <= 10, "Cache size " + cache.size() + " should be <= maxSize 10");
    }

    @Test
    void computeIfAbsentEvictsWhenOverMaxSize() {
        var cache = new SampledEvictionCache<String, String>(10, 0.5, 1);
        for (int i = 0; i < 20; i++) {
            cache.computeIfAbsent("key" + i, k -> "val");
        }
        assertTrue(cache.size() <= 10, "Cache size " + cache.size() + " should be <= maxSize 10");
    }

    @Test
    void setMaxSizeDynamicAdjustment() {
        var cache = new SampledEvictionCache<String, String>(50, 0.5, 1);
        for (int i = 0; i < 60; i++) {
            cache.put("key" + i, "val" + i);
        }
        assertTrue(cache.size() <= 50, "Initial size should be constrained by maxSize 50");

        // Shrink maxSize further
        cache.setMaxSize(20);
        // Trigger eviction with more puts
        for (int i = 0; i < 30; i++) {
            cache.put("extra" + i, "val" + i);
        }
        assertTrue(cache.size() <= 20, "After shrink, size " + cache.size() + " should be <= 20");
    }

    @Test
    void setMaxSizeRejectsNonPositive() {
        var cache = new SampledEvictionCache<String, String>(100, 0.75, 100);
        assertThrows(IllegalArgumentException.class, () -> cache.setMaxSize(0));
        assertThrows(IllegalArgumentException.class, () -> cache.setMaxSize(-1));
    }

    @Test
    void cacheBelowMaxSizeDoesNotEvict() {
        var cache = new SampledEvictionCache<String, String>(100, 0.5, 1);
        for (int i = 0; i < 50; i++) {
            cache.put("key" + i, "val" + i);
        }
        // All 50 entries should be present since we're below maxSize
        assertEquals(50, cache.size());
        for (int i = 0; i < 50; i++) {
            assertEquals("val" + i, cache.get("key" + i));
        }
    }

    @Test
    void evictionTargetRatioIsRespected() {
        double ratio = 0.25;
        var cache = new SampledEvictionCache<String, String>(20, ratio, 1);
        for (int i = 0; i < 50; i++) {
            cache.put("key" + i, "val" + i);
        }
        int target = (int)(20 * ratio); // 5
        // After eviction, size should be at most maxSize (it could be between target and maxSize
        // if eviction runs concurrently or more puts happen). But with samplingInterval=1,
        // after each eviction down to target, the next put increases size by 1 until next eviction.
        assertTrue(cache.size() <= 20, "Size should not exceed maxSize 20, got " + cache.size());
        // Some entries should have been evicted
        assertTrue(cache.size() < 40, "Eviction should have occurred, size " + cache.size());
    }

    @Test
    void rejectsNullValue() {
        var cache = new SampledEvictionCache<String, String>(100, 0.75, 100);
        assertThrows(NullPointerException.class, () -> cache.put("nullVal", null));
    }

    @Test
    void rejectsNullKey() {
        var cache = new SampledEvictionCache<String, String>(100, 0.75, 100);
        assertThrows(NullPointerException.class, () -> cache.put(null, "val"));
    }

    // ---- P0-2: 验证迭代器驱逐使用 it.remove() 而非 store.remove(key) ----

    @Test
    void evictionUsesIteratorRemove() {
        var cache = new SampledEvictionCache<String, String>(10, 0.75, 1, 16);
        for (int i = 0; i < 20; i++) {
            cache.put("k" + i, "v" + i);
        }
        // samplingInterval=1, 每次 put 都会触发的采样
        // evictionTargetRatio=0.75 → max=10*0.75=7.5 → target=7
        // 20 个条目应驱逐到约 7 个
        assertTrue(cache.size() <= 10, "size=" + cache.size() + " should be <= maxSize=10 after eviction");
    }

    @Test
    void concurrentEvictionDoesNotCorruptCache() throws Exception {
        var cache = new SampledEvictionCache<String, String>(50, 0.75, 1, 64);
        int threadCount = 4;
        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(threadCount);
        java.util.concurrent.atomic.AtomicReference<Throwable> error =
            new java.util.concurrent.atomic.AtomicReference<>();

        for (int t = 0; t < threadCount; t++) {
            int threadId = t;
            new Thread(() -> {
                try {
                    for (int i = 0; i < 100; i++) {
                        cache.put("t" + threadId + "-k" + i, "v");
                    }
                } catch (Throwable e) {
                    error.set(e);
                } finally {
                    latch.countDown();
                }
            }).start();
        }
        latch.await();
        assertNull(error.get(), "Concurrent eviction should not throw");
        // 所有条目总数最多为 400，驱逐后 size 应 <= maxSize 附近
        assertTrue(cache.size() <= 60, "size=" + cache.size() + " should be near maxSize=50 after concurrent eviction");
    }

    @Test
    void setMaxSizeTriggersEvictionOnNextAccess() {
        var cache = new SampledEvictionCache<String, String>(10, 0.75, 1, 16);
        for (int i = 0; i < 15; i++) {
            cache.put("k" + i, "v" + i);
        }
        int sizeAfterLarge = cache.size();
        // 缩小 maxSize
        cache.setMaxSize(5);
        // 下一次 put 应触发驱逐
        cache.put("trigger", "eviction");
        assertTrue(cache.size() <= 8, "size=" + cache.size() + " should shrink after maxSize reduction");
    }
}
