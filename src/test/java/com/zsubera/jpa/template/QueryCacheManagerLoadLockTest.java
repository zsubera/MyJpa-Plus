package com.zsubera.jpa.template;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Regression tests for QueryCacheManager.computeIfAbsent() loadLock race condition.
 * Verifies that lock removal happens before unlock to prevent cache stampede.
 */
class QueryCacheManagerLoadLockTest {

    private QueryCacheManager cache;

    @BeforeEach
    void setUp() {
        cache = new QueryCacheManager();
    }

    @Test
    void computeIfAbsent_concurrentThreadsSameKey_loadsOnce() throws Exception {
        int threadCount = 20;
        int expectedValue = 42;
        AtomicInteger loadCount = new AtomicInteger(0);
        CyclicBarrier barrier = new CyclicBarrier(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        try {
            java.util.List<Future<Integer>> futures = new java.util.ArrayList<>();
            for (int i = 0; i < threadCount; i++) {
                futures.add(executor.submit(() -> {
                    barrier.await(5, TimeUnit.SECONDS);
                    return cache.computeIfAbsent("same-key", () -> {
                        loadCount.incrementAndGet();
                        try {
                            Thread.sleep(50); // simulate slow load
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        return expectedValue;
                    }, 60);
                }));
            }

            for (Future<Integer> future : futures) {
                assertEquals(expectedValue, future.get(10, TimeUnit.SECONDS));
            }

            // With the fix (remove before unlock), only one thread should load.
            // Before the fix, the race could cause duplicate loads.
            assertEquals(1, loadCount.get(), "Loader should be called exactly once");
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void computeIfAbsent_sequentialCallsSameKey_loadsOnce() {
        AtomicInteger loadCount = new AtomicInteger(0);

        Integer first = cache.computeIfAbsent("key1", () -> {
            loadCount.incrementAndGet();
            return 100;
        }, 60);

        Integer second = cache.computeIfAbsent("key1", () -> {
            loadCount.incrementAndGet();
            return 200;
        }, 60);

        assertEquals(100, first);
        assertEquals(100, second);
        assertEquals(1, loadCount.get(), "Loader should be called only once for the same key");
    }

    @Test
    void computeIfAbsent_differentKeys_loadsIndependently() {
        AtomicInteger loadCount = new AtomicInteger(0);

        cache.computeIfAbsent("key-a", () -> {
            loadCount.incrementAndGet();
            return 1;
        }, 60);

        cache.computeIfAbsent("key-b", () -> {
            loadCount.incrementAndGet();
            return 2;
        }, 60);

        assertEquals(2, loadCount.get(), "Different keys should trigger separate loads");
    }

    @Test
    void computeIfAbsent_afterEviction_reloadsFromLoader() {
        // Put with 0 TTL to force immediate expiry
        cache.computeIfAbsent("expire-key", () -> 999, 0);

        // Verify it's expired
        assertNull(cache.get("expire-key"));

        // Should reload
        Integer result = cache.computeIfAbsent("expire-key", () -> 777, 60);
        assertEquals(777, result);
    }
}
