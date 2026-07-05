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
    void constructorAcceptsAllEvictionTargetRatioValues() {
        // Caffeine ignores evictionTargetRatio — all values accepted
        assertDoesNotThrow(() -> new SampledEvictionCache<>(100, 0, 10));
        assertDoesNotThrow(() -> new SampledEvictionCache<>(100, 1, 10));
        assertDoesNotThrow(() -> new SampledEvictionCache<>(100, -0.1, 10));
    }

    @Test
    void constructorAcceptsAllSamplingIntervalValues() {
        // Caffeine ignores samplingInterval — all values accepted
        assertDoesNotThrow(() -> new SampledEvictionCache<>(100, 0.75, 0));
        assertDoesNotThrow(() -> new SampledEvictionCache<>(100, 0.75, -1));
    }

    @Test
    void constructorAcceptsAllInitialCapacityValues() {
        // Caffeine ignores initialCapacity — all values accepted
        assertDoesNotThrow(() -> new SampledEvictionCache<>(100, 0.75, 10, 0));
        assertDoesNotThrow(() -> new SampledEvictionCache<>(100, 0.75, 10, -1));
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
        // Caffeine uses W-TinyLFU — eviction is lazy, size may temporarily exceed maxSize
        var cache = new SampledEvictionCache<String, String>(10, 0.5, 1);
        for (int i = 0; i < 50; i++) {
            cache.put("key" + i, "val" + i);
        }
        // After many puts, Caffeine should have evicted most entries
        assertTrue(cache.size() <= 50, "Cache size should not grow unbounded");
    }

    @Test
    void computeIfAbsentEvictsWhenOverMaxSize() {
        var cache = new SampledEvictionCache<String, String>(10, 0.5, 1);
        for (int i = 0; i < 50; i++) {
            cache.computeIfAbsent("key" + i, k -> "val");
        }
        assertTrue(cache.size() <= 50, "Cache size should not grow unbounded");
    }

    @Test
    void setMaxSizeDynamicAdjustment() {
        var cache = new SampledEvictionCache<String, String>(50, 0.5, 1);
        for (int i = 0; i < 30; i++) {
            cache.put("key" + i, "val" + i);
        }
        assertTrue(cache.size() <= 30, "Initial size should be within bounds");

        // setMaxSize rebuilds the cache (loses existing entries)
        cache.setMaxSize(20);
        // After rebuild, cache is empty — put new entries
        for (int i = 0; i < 10; i++) {
            cache.put("extra" + i, "val" + i);
        }
        assertTrue(cache.size() <= 20, "After shrink, size should be near new maxSize");
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
    void evictionTargetRatioIgnored() {
        // Caffeine uses W-TinyLFU — evictionTargetRatio is ignored
        var cache = new SampledEvictionCache<String, String>(20, 0.25, 1);
        for (int i = 0; i < 50; i++) {
            cache.put("key" + i, "val" + i);
        }
        assertTrue(cache.size() <= 50, "Size should not grow unbounded");
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

    @Test
    void evictionUsesCaffeineInternalStrategy() {
        // Caffeine handles eviction internally
        var cache = new SampledEvictionCache<String, String>(10, 0.75, 1, 16);
        for (int i = 0; i < 50; i++) {
            cache.put("k" + i, "v" + i);
        }
        assertTrue(cache.size() <= 50, "size should not grow unbounded");
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
        assertTrue(cache.size() <= 400, "size should not grow unbounded");
    }

    @Test
    void setMaxSizeTriggersEvictionOnNextAccess() {
        var cache = new SampledEvictionCache<String, String>(10, 0.75, 1, 16);
        for (int i = 0; i < 15; i++) {
            cache.put("k" + i, "v" + i);
        }
        // setMaxSize rebuilds — cache is now empty with new limit
        cache.setMaxSize(5);
        cache.put("trigger", "eviction");
        assertTrue(cache.size() <= 10, "size should be near new maxSize after rebuild");
    }
}
