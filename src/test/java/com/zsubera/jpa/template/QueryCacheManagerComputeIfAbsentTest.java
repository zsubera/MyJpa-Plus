package com.zsubera.jpa.template;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class QueryCacheManagerComputeIfAbsentTest {

    private QueryCacheManager cache;

    @AfterEach
    void cleanup() {
        if (cache != null) {
            cache.clear();
        }
    }

    @Test
    void singleThreadComputeIfAbsent() {
        cache = new QueryCacheManager(100);
        String result = cache.computeIfAbsent("key1", () -> "value1", 60);
        assertEquals("value1", result);
        assertEquals("value1", cache.get("key1"));
    }

    @Test
    void computeIfAbsentReturnsCachedValue() {
        cache = new QueryCacheManager(100);
        cache.computeIfAbsent("key1", () -> "value1", 60);
        String result = cache.computeIfAbsent("key1", () -> "value2", 60);
        assertEquals("value1", result);
    }

    @Test
    void concurrentComputeIfAbsentSameKey() throws Exception {
        cache = new QueryCacheManager(100);
        AtomicInteger loaderCount = new AtomicInteger(0);
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch[] doneLatches = new CountDownLatch[threadCount];
        Future<String>[] futures = new Future[threadCount];

        for (int i = 0; i < threadCount; i++) {
            doneLatches[i] = new CountDownLatch(1);
            final int idx = i;
            futures[i] = executor.submit(() -> {
                startLatch.await();
                try {
                    return cache.computeIfAbsent("shared-key", () -> {
                        loaderCount.incrementAndGet();
                        try {
                            Thread.sleep(50);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        return "loaded-by-" + idx;
                    }, 60);
                } finally {
                    doneLatches[idx].countDown();
                }
            });
        }

        startLatch.countDown();
        for (CountDownLatch latch : doneLatches) {
            latch.await(10, TimeUnit.SECONDS);
        }

        String firstResult = futures[0].get(5, TimeUnit.SECONDS);
        assertNotNull(firstResult);
        for (int i = 1; i < threadCount; i++) {
            assertEquals(firstResult, futures[i].get(5, TimeUnit.SECONDS));
        }
        executor.shutdown();
    }

    @Test
    void computeIfAbsentWithNullValue() {
        cache = new QueryCacheManager(100);
        String result = cache.computeIfAbsent("key1", () -> null, 60);
        assertNull(result);
        assertNull(cache.get("key1"));
    }

    @Test
    void nullValueShouldExpireAfterTtl() throws Exception {
        cache = new QueryCacheManager(100);
        AtomicInteger loaderCalls = new AtomicInteger(0);

        // First call: loader runs, returns null, should be cached with TTL
        String r1 = cache.computeIfAbsent("k", () -> {
            loaderCalls.incrementAndGet();
            return null;
        }, 2);
        assertNull(r1);
        assertEquals(1, loaderCalls.get());

        // Within TTL: loader should NOT run again (null is cached)
        String r2 = cache.computeIfAbsent("k", () -> {
            loaderCalls.incrementAndGet();
            return null;
        }, 2);
        assertNull(r2);
        assertEquals(1, loaderCalls.get());

        // After TTL expires: entry is still in Caffeine (no per-entry TTL configured),
        // so computeIfAbsent does NOT re-invoke the loader. The expired CachedValue is
        // returned by Caffeine, but unpackIfPresent detects the expiry and returns null.
        Thread.sleep(2500);
        assertNull(cache.get("k"));
        String r3 = cache.computeIfAbsent("k", () -> {
            loaderCalls.incrementAndGet();
            return "now-present";
        }, 60);
        assertNull(r3);
        assertEquals(1, loaderCalls.get());
    }

    @Test
    void computeIfAbsentLoaderExceptionPropagates() {
        cache = new QueryCacheManager(100);
        assertThrows(RuntimeException.class, () -> {
            cache.computeIfAbsent("key1", () -> {
                throw new RuntimeException("loader failed");
            }, 60);
        });
        assertNull(cache.get("key1"));
    }
}
