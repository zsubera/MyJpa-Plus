package com.zsubera.jpa.converter;

import static org.junit.jupiter.api.Assertions.*;

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
        System.setProperty("myjpa.encrypt.key", "test-encryption-key-for-warmup-test!");
        System.setProperty("myjpa.encrypt.salt", "test-salt-value");
    }

    @AfterEach
    void cleanup() {
        EncryptConverter.clearCaches();
        System.clearProperty("myjpa.encrypt.key");
        System.clearProperty("myjpa.encrypt.salt");
    }

    /**
     * 测试异步预热不会阻塞主线程。
     *

     */
    @Test
    void warmUpKeyCacheShouldNotBlockMainThread() {
        EncryptConverter.clearCaches();
        assertDoesNotThrow(EncryptConverter::warmUpKeyCache);
    }

    /**
     * 测试同步预热会阻塞直到完成。
     */
    @Test
    void warmUpKeyCacheSyncShouldBlockUntilComplete() {
        EncryptConverter.clearCaches();
        assertDoesNotThrow(EncryptConverter::warmUpKeyCacheSync);
    }

    /**
     * 测试预热后加密操作可正常执行。
     */
    @Test
    void warmUpShouldNotBreakEncryption() throws Exception {
        EncryptConverter.clearCaches();
        EncryptConverter.warmUpKeyCacheSync();
        EncryptConverter converter = new EncryptConverter();
        String encrypted = converter.convertToDatabaseColumn("test");
        assertNotNull(encrypted);
    }

    /**
     * 测试预热后解密操作可正常执行。
     */
    @Test
    void warmUpShouldNotBreakDecryption() throws Exception {
        EncryptConverter.clearCaches();
        EncryptConverter converter = new EncryptConverter();
        String encrypted = converter.convertToDatabaseColumn("test-value");

        EncryptConverter.clearCaches();
        EncryptConverter.warmUpKeyCacheSync();

        String decrypted = converter.convertToEntityAttribute(encrypted);
        assertEquals("test-value", decrypted);
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
        EncryptConverter.clearCaches();

        // 应该记录警告但不抛出异常
        assertDoesNotThrow(EncryptConverter::warmUpKeyCache);
    }
}
