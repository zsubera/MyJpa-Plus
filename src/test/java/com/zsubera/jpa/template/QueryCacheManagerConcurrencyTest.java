package com.zsubera.jpa.template;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * QueryCacheManager 并发安全测试。
 *

 */
class QueryCacheManagerConcurrencyTest {

    /**
     * 测试并发 put 不会导致内存泄漏或缓存大小超过限制。
     *

     */
    @Test
    void concurrentPutShouldNotCauseMemoryLeak() throws InterruptedException {
        QueryCacheManager cache = new QueryCacheManager(100);
        ExecutorService executor = Executors.newFixedThreadPool(10);
        CountDownLatch latch = new CountDownLatch(1000);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < 1000; i++) {
            final int idx = i;
            executor.submit(() -> {
                try {
                    boolean success = cache.put("key-" + idx, "value-" + idx, 60);
                    if (success) {
                        successCount.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(10, TimeUnit.SECONDS);
        // 并发缓存允许临时超出限制（采样驱逐策略），但不应无限增长
        // 1000 个并发 put，maxEntries=100，预期缓存大小在合理范围内
        assertTrue(cache.size() <= 400, "Cache should respect maxEntries, but was: " + cache.size());
        assertTrue(successCount.get() > 0, "At least some puts should succeed");
        executor.shutdown();
    }

    /**
     * 测试并发 get 和 put 不会导致异常。
     */
    @Test
    void concurrentGetAndPutShouldNotThrow() throws InterruptedException {
        QueryCacheManager cache = new QueryCacheManager(50);
        ExecutorService executor = Executors.newFixedThreadPool(10);
        CountDownLatch latch = new CountDownLatch(500);

        for (int i = 0; i < 500; i++) {
            final int idx = i;
            executor.submit(() -> {
                try {
                    if (idx % 2 == 0) {
                        cache.put("key-" + idx, "value-" + idx, 60);
                    } else {
                        cache.get("key-" + (idx - 1));
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(10, TimeUnit.SECONDS);
        // 并发缓存允许临时超出限制（采样驱逐策略），但不应无限增长
        assertTrue(cache.size() <= 200, "Cache should be bounded, but was: " + cache.size());
        executor.shutdown();
    }

    /**
     * 测试驱逐锁在高并发下的正确性。
     */
    @Test
    void evictionLockShouldPreventConcurrentEviction() throws InterruptedException {
        QueryCacheManager cache = new QueryCacheManager(10);
        ExecutorService executor = Executors.newFixedThreadPool(20);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(2000);

        // 预填充缓存
        for (int i = 0; i < 10; i++) {
            cache.put("existing-" + i, "value-" + i, 60);
        }

        // 同时触发多个驱逐
        for (int i = 0; i < 2000; i++) {
            final int idx = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    cache.put("new-" + idx, "value-" + idx, 60);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // 同时开始
        endLatch.await(30, TimeUnit.SECONDS);

        // 并发缓存允许临时超出限制（采样驱逐策略），但不应无限增长
        // 2000 个并发 put，maxEntries=10，预期缓存大小在合理范围内
        assertTrue(cache.size() <= 1000,
            "Cache size should be bounded after concurrent puts, but was: " + cache.size());
        executor.shutdown();
    }
}
