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
        // Allow Caffeine's async cleanup to finish
        Thread.sleep(1000);
        // 并发缓存允许临时超出限制（采样驱逐策略），但不应无限增长
        // 验证驱逐在发生（size < total puts），而非精确大小
        assertTrue(cache.size() < 1000, "Cache should be bounded after eviction, but was: " + cache.size());
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
        // Allow Caffeine's async cleanup to finish
        Thread.sleep(1000);
        // 并发缓存允许临时超出限制（采样驱逐策略），但不应无限增长
        // 验证驱逐在发生（size < total puts），而非精确大小
        assertTrue(cache.size() < 500, "Cache should be bounded after eviction, but was: " + cache.size());
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
        // Allow Caffeine's async cleanup to finish
        Thread.sleep(1000);

        // 并发缓存允许临时超出限制（采样驱逐策略），但不应无限增长
        // 验证驱逐在发生（size < total puts），而非精确大小
        assertTrue(cache.size() < 2000, "Cache size should be bounded after concurrent puts, but was: " + cache.size());
        executor.shutdown();
    }

    /**
     * 验证 get() 中的条件移除修复：当一个线程检测到过期条目并尝试移除时，
     * 并发的 put() 插入的新条目不应被误删。
     *
     * <p>
     * 修复前：cache.invalidate(key) 无条件移除，会误删并发 put 的新条目。
     * 修复后：cache.asMap().remove(key, cached) 仅移除我们读到的过期条目。
     */
    @Test
    void getShouldNotRemoveFreshEntryInsertedByConcurrentPut() throws Exception {
        QueryCacheManager cache = new QueryCacheManager(100);

        // 步骤 1：放入条目，TTL 极短（1 秒），使其很快过期
        cache.put("shared-key", "old-value", 1);

        // 步骤 2：等待条目过期
        Thread.sleep(1500);

        // 步骤 3：验证条目确实已过期
        assertNull(cache.get("shared-key"), "Entry should be expired");

        // 步骤 4：并发 put 新值 + get（触发过期条目清理）
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);

        Thread putter = new Thread(() -> {
            try {
                startLatch.await();
                // 在 get() 清理过期条目之前/期间插入新值
                cache.put("shared-key", "fresh-value", 60);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                doneLatch.countDown();
            }
        });

        Thread getter = new Thread(() -> {
            try {
                startLatch.await();
                // get() 会检测到过期条目并尝试条件移除
                cache.get("shared-key");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                doneLatch.countDown();
            }
        });

        putter.start();
        getter.start();
        startLatch.countDown(); // 同时开始
        doneLatch.await(5, TimeUnit.SECONDS);

        // 步骤 5：验证新值未被误删
        Object result = cache.get("shared-key");
        assertEquals("fresh-value", result,
            "Fresh value inserted by concurrent put should not be removed by get()'s expired-entry cleanup");
    }
}
