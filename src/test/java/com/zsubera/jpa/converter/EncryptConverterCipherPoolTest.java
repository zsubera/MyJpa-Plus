package com.zsubera.jpa.converter;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import java.util.Queue;
import javax.crypto.Cipher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EncryptConverterCipherPoolTest {

    private static final String TEST_KEY = "1234567890123456";
    private EncryptConverter converter;
    private Queue<Cipher> pool;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() throws Exception {
        System.setProperty("myjpa.encrypt.key", TEST_KEY);
        System.setProperty("myjpa.encrypt.salt", "test-salt-value");
        System.setProperty("myjpa-plus.encrypt.skip-salt-check", "true");
        EncryptConverter.clearCaches();
        Field f = EncryptionKeyManager.class.getDeclaredField("KEY_VALIDATED");
        f.setAccessible(true);
        ((java.util.concurrent.atomic.AtomicBoolean)f.get(null)).set(false);
        converter = new EncryptConverter();
        Field poolField = EncryptConverter.class.getDeclaredField("CIPHER_POOL");
        poolField.setAccessible(true);
        pool = (Queue<Cipher>)poolField.get(null);
    }

    @AfterEach
    void tearDown() throws Exception {
        System.clearProperty("myjpa.encrypt.key");
        System.clearProperty("myjpa.encrypt.salt");
        System.clearProperty("myjpa-plus.encrypt.skip-salt-check");
        System.clearProperty("myjpa.encrypt.key.version");
        EncryptConverter.clearCaches();
        Field f = EncryptionKeyManager.class.getDeclaredField("KEY_VALIDATED");
        f.setAccessible(true);
        ((java.util.concurrent.atomic.AtomicBoolean)f.get(null)).set(false);
    }

    @Test
    void successfulEncrypt_returnsCipherToPool() {
        assertEquals(0, pool.size());
        converter.convertToDatabaseColumn("test");
        assertEquals(1, pool.size());
    }

    @Test
    void multipleEncrypts_reusesPooledCipher() {
        converter.convertToDatabaseColumn("test1");
        assertEquals(1, pool.size());
        converter.convertToDatabaseColumn("test2");
        assertEquals(1, pool.size());
    }

    @Test
    void failedDecrypt_doesNotReturnCipherToPool() {
        String encrypted = converter.convertToDatabaseColumn("secret");
        assertEquals(1, pool.size());
        pool.clear();
        assertEquals(0, pool.size());
        String oldKey = System.getProperty("myjpa.encrypt.key");
        System.setProperty("myjpa.encrypt.key", "9999999999999999");
        EncryptConverter.clearCaches();
        try {
            Field f = EncryptionKeyManager.class.getDeclaredField("KEY_VALIDATED");
            f.setAccessible(true);
            ((java.util.concurrent.atomic.AtomicBoolean)f.get(null)).set(false);
            EncryptConverter badConverter = new EncryptConverter();
            assertThrows(Exception.class, () -> badConverter.convertToEntityAttribute(encrypted));
            assertEquals(0, pool.size());
        } catch (Exception e) {
            fail(e);
        } finally {
            System.setProperty("myjpa.encrypt.key", oldKey);
            EncryptConverter.clearCaches();
        }
    }
}
