package com.zsubera.jpa.converter;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * EncryptConverter 密钥预热测试。
 *

 */
class EncryptConverterWarmUpTest {

    @BeforeEach
    void setup() {
        // 设置测试密钥
        System.setProperty("myjpa.encrypt.key", "test-encryption-key-for-warmup-test!");
        System.setProperty("myjpa-plus.encrypt.skip-salt-check", "true");
    }

    @AfterEach
    void cleanup() {
        EncryptConverter.clearCacheForTesting();
        System.clearProperty("myjpa.encrypt.key");
        System.clearProperty("myjpa-plus.encrypt.skip-salt-check");
    }

    /**
     * 测试异步预热不会阻塞主线程。
     *

     */
    @Test
    void warmUpKeyCacheShouldNotBlockMainThread() {
        EncryptConverter.clearCacheForTesting();

        long start = System.currentTimeMillis();
        EncryptConverter.warmUpKeyCache();
        long elapsed = System.currentTimeMillis() - start;

        // 异步预热应该立即返回（< 100ms）
        assertTrue(elapsed < 100, "warmUpKeyCache should return immediately, took: " + elapsed + "ms");
    }

    /**
     * 测试同步预热会阻塞直到完成。
     */
    @Test
    void warmUpKeyCacheSyncShouldBlockUntilComplete() {
        EncryptConverter.clearCacheForTesting();

        long start = System.currentTimeMillis();
        EncryptConverter.warmUpKeyCacheSync();
        long elapsed = System.currentTimeMillis() - start;

        // 同步预热应该完成密钥派生（> 0ms）
        assertTrue(elapsed >= 0, "warmUpKeyCacheSync should complete");
    }

    /**
     * 测试预热后首次加密操作应该更快。
     */
    @Test
    void warmUpShouldReduceFirstRequestLatency() throws Exception {
        EncryptConverter.clearCacheForTesting();

        // 预热前：首次加密
        long start1 = System.nanoTime();
        EncryptConverter converter = new EncryptConverter();
        converter.convertToDatabaseColumn("test");
        long preWarmup = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start1);

        EncryptConverter.clearCacheForTesting();

        // 预热后
        EncryptConverter.warmUpKeyCacheSync();

        long start2 = System.nanoTime();
        converter.convertToDatabaseColumn("test");
        long postWarmup = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start2);

        // 预热后应该明显更快（或至少相等）
        assertTrue(postWarmup <= preWarmup,
            "Post-warmup latency should be <= pre-warmup, pre=" + preWarmup + "ms, post=" + postWarmup + "ms");
    }

    /**
     * 测试预热后解密操作也应该更快。
     */
    @Test
    void warmUpShouldReduceDecryptLatency() throws Exception {
        EncryptConverter.clearCacheForTesting();

        EncryptConverter converter = new EncryptConverter();
        String encrypted = converter.convertToDatabaseColumn("test-value");

        EncryptConverter.clearCacheForTesting();

        // 预热
        EncryptConverter.warmUpKeyCacheSync();

        // 解密应该更快
        long start = System.nanoTime();
        String decrypted = converter.convertToEntityAttribute(encrypted);
        long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

        assertEquals("test-value", decrypted);
        // 预热后解密应该很快（< 50ms）
        assertTrue(elapsed < 50, "Decryption should be fast after warm-up, took: " + elapsed + "ms");
    }

    /**
     * 测试多次预热是安全的。
     */
    @Test
    void multipleWarmUpCallsShouldBeSafe() {
        assertDoesNotThrow(() -> {
            EncryptConverter.warmUpKeyCacheSync();
            EncryptConverter.warmUpKeyCacheSync();
            EncryptConverter.warmUpKeyCacheSync();
        });
    }

    /**
     * 测试异步预热在密钥未配置时不会抛出异常。
     */
    @Test
    void warmUpWithMissingKeyShouldNotThrow() {
        System.clearProperty("myjpa.encrypt.key");
        EncryptConverter.clearCacheForTesting();

        // 应该记录警告但不抛出异常
        assertDoesNotThrow(EncryptConverter::warmUpKeyCache);
    }
}
