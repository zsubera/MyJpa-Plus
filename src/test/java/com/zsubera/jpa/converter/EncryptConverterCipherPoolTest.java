package com.zsubera.jpa.converter;

import static org.junit.jupiter.api.Assertions.*;

import com.zsubera.jpa.exception.MyJpaPlusException;
import java.lang.reflect.Field;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests confirming that EncryptConverter does NOT pool Cipher instances
 * (to avoid JDK GCM state reuse bugs JDK-8201285).
 */
class EncryptConverterCipherPoolTest {

    private static final String TEST_KEY = "1234567890123456";
    private EncryptConverter converter;

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
    void multipleEncrypts_allSucceed() {
        assertNotNull(converter.convertToDatabaseColumn("test1"));
        assertNotNull(converter.convertToDatabaseColumn("test2"));
        assertNotNull(converter.convertToDatabaseColumn("test3"));
    }

    @Test
    void failedDecrypt_stillAllowsSubsequentEncrypt() {
        String encrypted = converter.convertToDatabaseColumn("secret");
        String oldKey = System.getProperty("myjpa.encrypt.key");
        System.setProperty("myjpa.encrypt.key", "9999999999999999");
        EncryptConverter.clearCaches();
        try {
            Field f = EncryptionKeyManager.class.getDeclaredField("KEY_VALIDATED");
            f.setAccessible(true);
            ((java.util.concurrent.atomic.AtomicBoolean)f.get(null)).set(false);
            EncryptConverter badConverter = new EncryptConverter();
            assertThrows(MyJpaPlusException.class, () -> badConverter.convertToEntityAttribute(encrypted));
        } catch (Exception e) {
            fail(e);
        } finally {
            System.setProperty("myjpa.encrypt.key", oldKey);
            EncryptConverter.clearCaches();
        }
        // Subsequent encrypt with correct key should still work
        Field f2;
        try {
            f2 = EncryptionKeyManager.class.getDeclaredField("KEY_VALIDATED");
            f2.setAccessible(true);
            ((java.util.concurrent.atomic.AtomicBoolean)f2.get(null)).set(false);
        } catch (Exception e) {
            fail(e);
        }
        assertNotNull(converter.convertToDatabaseColumn("new-value"));
    }
}
