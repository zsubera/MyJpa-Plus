package com.zsubera.jpa.converter;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EncryptConverterExtendedTest {

    private static final String TEST_KEY = "1234567890123456";

    @BeforeEach
    void setUp() throws Exception {
        System.setProperty("myjpa.encrypt.key", TEST_KEY);
        System.setProperty("myjpa.encrypt.salt", "test-salt-value");
        EncryptConverter.clearCaches();
        Field f = EncryptionKeyManager.class.getDeclaredField("KEY_VALIDATED");
        f.setAccessible(true);
        ((java.util.concurrent.atomic.AtomicBoolean)f.get(null)).set(false);
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
    void refreshKeyVersion_clearsCache() {
        EncryptConverter converter = new EncryptConverter();
        String encrypted = converter.convertToDatabaseColumn("test");
        EncryptConverter.refreshKeyVersion();
        String decrypted = converter.convertToEntityAttribute(encrypted);
        assertEquals("test", decrypted);
    }

    @Test
    void validateKeyConfiguration_noKey_throws() {
        System.clearProperty("myjpa.encrypt.key");
        EncryptConverter.clearCaches();
        try {
            Field f = EncryptionKeyManager.class.getDeclaredField("KEY_VALIDATED");
            f.setAccessible(true);
            ((java.util.concurrent.atomic.AtomicBoolean)f.get(null)).set(false);
            assertThrows(com.zsubera.jpa.exception.MyJpaPlusException.class,
                () -> EncryptConverter.validateKeyConfiguration());
        } catch (Exception e) {
            fail(e);
        }
    }

    @Test
    void validateKeyConfiguration_shortKey_throws() {
        System.setProperty("myjpa.encrypt.key", "short");
        EncryptConverter.clearCaches();
        try {
            Field f = EncryptionKeyManager.class.getDeclaredField("KEY_VALIDATED");
            f.setAccessible(true);
            ((java.util.concurrent.atomic.AtomicBoolean)f.get(null)).set(false);
            assertThrows(Exception.class, () -> EncryptConverter.validateKeyConfiguration());
        } catch (Exception e) {
            fail(e);
        }
    }

    @Test
    void validateKeyConfiguration_validKey_passes() {
        assertDoesNotThrow(() -> EncryptConverter.validateKeyConfiguration());
    }

    @Test
    void validateKeyConfiguration_alreadyValidated_skips() throws Exception {
        EncryptConverter.validateKeyConfiguration();
        assertDoesNotThrow(() -> EncryptConverter.validateKeyConfiguration());
    }

    @Test
    void warmUpKeyCacheSync_withValidKey() {
        assertDoesNotThrow(() -> EncryptConverter.warmUpKeyCacheSync());
    }

    @Test
    void convertToEntityAttribute_unversionedFormat() {
        EncryptConverter converter = new EncryptConverter();
        String encrypted = converter.convertToDatabaseColumn("test");
        String unversioned = encrypted.substring(encrypted.indexOf(':') + 1);
        String decrypted = converter.convertToEntityAttribute(unversioned);
        assertEquals("test", decrypted);
    }

    @Test
    void convertToEntityAttribute_invalidBase64() {
        EncryptConverter converter = new EncryptConverter();
        assertNull(converter.convertToEntityAttribute("v1:not-valid-base64!!!"));
    }

    @Test
    void convertToEntityAttribute_shortData() {
        EncryptConverter converter = new EncryptConverter();
        String shortData = Base64.getEncoder().encodeToString(new byte[] {1, 2, 3});
        assertNull(converter.convertToEntityAttribute("v1:" + shortData));
    }

    @Test
    void convertToEntityAttribute_wrongKey() {
        EncryptConverter converter1 = new EncryptConverter();
        String encrypted = converter1.convertToDatabaseColumn("secret");
        System.setProperty("myjpa.encrypt.key", "9999999999999999");
        EncryptConverter.clearCaches();
        try {
            Field f = EncryptionKeyManager.class.getDeclaredField("KEY_VALIDATED");
            f.setAccessible(true);
            ((java.util.concurrent.atomic.AtomicBoolean)f.get(null)).set(false);
            EncryptConverter converter2 = new EncryptConverter();
            assertNull(converter2.convertToEntityAttribute(encrypted));
        } catch (Exception e) {
            fail(e);
        } finally {
            System.setProperty("myjpa.encrypt.key", TEST_KEY);
            EncryptConverter.clearCaches();
        }
    }

    @Test
    void getKeySpec_cacheFull_throws() throws Exception {
        Field cacheField = EncryptionKeyManager.class.getDeclaredField("KEY_CACHE");
        cacheField.setAccessible(true);
        @SuppressWarnings("unchecked")
        ConcurrentHashMap<String, SecretKeySpec> cache = (ConcurrentHashMap<String, SecretKeySpec>)cacheField.get(null);
        cache.clear();
        for (int i = 0; i < 16; i++) {
            cache.put("v" + i, new SecretKeySpec(new byte[16], "AES"));
        }
        EncryptConverter converter = new EncryptConverter();
        assertNull(converter.convertToEntityAttribute("v999:AAAA"));
        cache.clear();
    }

    @Test
    void isSaltCheckSkipped_withSaltProperty() throws Exception {
        System.setProperty("myjpa.encrypt.salt", "my-salt");
        System.clearProperty("myjpa-plus.encrypt.skip-salt-check");
        try {
            Method m = EncryptionKeyManager.class.getDeclaredMethod("isSaltCheckSkipped");
            m.setAccessible(true);
            // isSaltCheckSkipped only checks the skip flag, not whether salt is configured
            assertFalse((boolean)m.invoke(null));
        } finally {
            System.clearProperty("myjpa.encrypt.salt");
            System.setProperty("myjpa-plus.encrypt.skip-salt-check", "true");
        }
    }

    @Test
    void isSaltCheckSkipped_withSkipCheck() throws Exception {
        System.clearProperty("myjpa.encrypt.salt");
        System.setProperty("myjpa-plus.encrypt.skip-salt-check", "true");
        try {
            Method m = EncryptionKeyManager.class.getDeclaredMethod("isSaltCheckSkipped");
            m.setAccessible(true);
            assertTrue((boolean)m.invoke(null));
        } finally {
            System.clearProperty("myjpa-plus.encrypt.skip-salt-check");
        }
    }

    @Test
    void isSaltCheckSkipped_noSaltNoSkip() throws Exception {
        System.clearProperty("myjpa.encrypt.salt");
        System.clearProperty("myjpa-plus.encrypt.skip-salt-check");
        try {
            Method m = EncryptionKeyManager.class.getDeclaredMethod("isSaltCheckSkipped");
            m.setAccessible(true);
            assertFalse((boolean)m.invoke(null));
        } finally {
            System.clearProperty("myjpa-plus.encrypt.skip-salt-check");
        }
    }

    @Test
    void isProductionEnvironment_requireSaltProperty() throws Exception {
        System.setProperty("myjpa-plus.encrypt.require-salt", "true");
        try {
            Method m =
                com.zsubera.jpa.autoconfigure.EnvironmentHelper.class.getDeclaredMethod("isProductionEnvironment");
            m.setAccessible(true);
            assertTrue((boolean)m.invoke(null));
        } finally {
            System.clearProperty("myjpa-plus.encrypt.require-salt");
        }
    }

    @Test
    void isProductionEnvironment_prodProfile() throws Exception {
        System.setProperty("spring.profiles.active", "prod");
        try {
            Method m =
                com.zsubera.jpa.autoconfigure.EnvironmentHelper.class.getDeclaredMethod("isProductionEnvironment");
            m.setAccessible(true);
            assertTrue((boolean)m.invoke(null));
        } finally {
            System.clearProperty("spring.profiles.active");
        }
    }

    @Test
    void isProductionEnvironment_notProd() throws Exception {
        System.setProperty("spring.profiles.active", "dev");
        try {
            Method m =
                com.zsubera.jpa.autoconfigure.EnvironmentHelper.class.getDeclaredMethod("isProductionEnvironment");
            m.setAccessible(true);
            assertFalse((boolean)m.invoke(null));
        } finally {
            System.clearProperty("spring.profiles.active");
        }
    }

    @Test
    void reEncrypt_null_throws() {
        assertThrows(IllegalArgumentException.class, () -> EncryptConverter.reEncrypt(null));
    }

    @Test
    void reEncrypt_reEncryptsValue() {
        EncryptConverter converter = new EncryptConverter();
        String original = "sensitive-data";
        String encrypted = converter.convertToDatabaseColumn(original);
        String reEncrypted = EncryptConverter.reEncrypt(encrypted);
        assertNotNull(reEncrypted);
        assertNotEquals(encrypted, reEncrypted);
        assertEquals(original, converter.convertToEntityAttribute(reEncrypted));
    }

    @Test
    void getKeyVersion_cachesValue() throws Exception {
        System.setProperty("myjpa.encrypt.key.version", "v2");
        EncryptConverter.clearCaches();
        Method m = EncryptionKeyManager.class.getDeclaredMethod("getKeyVersion");
        m.setAccessible(true);
        String v1 = (String)m.invoke(null);
        String v2 = (String)m.invoke(null);
        assertEquals(v1, v2);
        assertEquals("v2", v1);
        System.clearProperty("myjpa.encrypt.key.version");
    }

    @Test
    void convertToDatabaseColumn_devSaltWarning() {
        System.clearProperty("myjpa.encrypt.salt");
        System.setProperty("myjpa-plus.encrypt.skip-salt-check", "true");
        EncryptConverter.clearCaches();
        EncryptConverter converter = new EncryptConverter();
        // ponytail: skipSaltCheck=true now allows encryption with dev fallback salt
        // Set a key so that the dev salt can be used for derivation
        System.setProperty("myjpa.encrypt.key", "test-key-16-bytes!");
        try {
            String result = converter.convertToDatabaseColumn("test");
            assertNotNull(result);
            assertTrue(result.startsWith("v1:"));
        } finally {
            System.clearProperty("myjpa.encrypt.key");
            System.clearProperty("myjpa-plus.encrypt.skip-salt-check");
            EncryptConverter.clearCaches();
        }
    }

    @Test
    void resolveRawKey_multiKeyFormat() {
        System.setProperty("myjpa.encrypt.key", "v1:key11111111111111,v2:key22222222222222");
        EncryptConverter.clearCaches();
        try {
            Field f = EncryptionKeyManager.class.getDeclaredField("KEY_VALIDATED");
            f.setAccessible(true);
            ((java.util.concurrent.atomic.AtomicBoolean)f.get(null)).set(false);
            System.setProperty("myjpa.encrypt.key.version", "v2");
            EncryptConverter converter = new EncryptConverter();
            String encrypted = converter.convertToDatabaseColumn("test");
            assertNotNull(encrypted);
            assertTrue(encrypted.startsWith("v2:"));
        } catch (Exception e) {
            fail(e);
        } finally {
            System.setProperty("myjpa.encrypt.key", TEST_KEY);
            System.clearProperty("myjpa.encrypt.key.version");
            EncryptConverter.clearCaches();
        }
    }

    @Test
    void resolveRawKey_multiKeyVersionNotFound() {
        System.setProperty("myjpa.encrypt.key", "v1:key11111111111111,v2:key22222222222222");
        EncryptConverter.clearCaches();
        try {
            Field f = EncryptionKeyManager.class.getDeclaredField("KEY_VALIDATED");
            f.setAccessible(true);
            ((java.util.concurrent.atomic.AtomicBoolean)f.get(null)).set(false);
            System.setProperty("myjpa.encrypt.key.version", "v99");
            EncryptConverter converter = new EncryptConverter();
            assertThrows(Exception.class, () -> converter.convertToDatabaseColumn("test"));
        } catch (Exception e) {
            fail(e);
        } finally {
            System.setProperty("myjpa.encrypt.key", TEST_KEY);
            System.clearProperty("myjpa.encrypt.key.version");
            EncryptConverter.clearCaches();
        }
    }

    @Test
    void resolveRawKey_multiKeyInvalidEntries() {
        System.setProperty("myjpa.encrypt.key", "v1:key11111111111111,invalid-entry");
        EncryptConverter.clearCaches();
        try {
            Field f = EncryptionKeyManager.class.getDeclaredField("KEY_VALIDATED");
            f.setAccessible(true);
            ((java.util.concurrent.atomic.AtomicBoolean)f.get(null)).set(false);
            // ponytail: 修正后的 MULTI_KEY_PATTERN 不再将 'invalid-entry' 匹配为多键格式，
            // 而是将其视为单键 'v1:key11111111111111,invalid-entry'（有效）
            EncryptConverter converter = new EncryptConverter();
            String encrypted = converter.convertToDatabaseColumn("test");
            assertNotNull(encrypted);
        } catch (Exception e) {
            fail(e);
        } finally {
            System.setProperty("myjpa.encrypt.key", TEST_KEY);
            EncryptConverter.clearCaches();
        }
    }

    @Test
    void getSalt_withSaltProperty() throws Exception {
        System.setProperty("myjpa.encrypt.salt", "my-salt-value");
        try {
            Method m = EncryptionKeyManager.class.getDeclaredMethod("getSalt");
            m.setAccessible(true);
            byte[] salt = (byte[])m.invoke(null);
            assertNotNull(salt);
            assertArrayEquals("my-salt-value".getBytes(StandardCharsets.UTF_8), salt);
        } finally {
            System.clearProperty("myjpa.encrypt.salt");
        }
    }

    @Test
    void getSalt_noSaltNoSkip_throws() throws Exception {
        System.clearProperty("myjpa.encrypt.salt");
        System.clearProperty("myjpa-plus.encrypt.skip-salt-check");
        try {
            Method m = EncryptionKeyManager.class.getDeclaredMethod("getSalt");
            m.setAccessible(true);
            assertThrows(Exception.class, () -> m.invoke(null));
        } finally {
            System.setProperty("myjpa-plus.encrypt.skip-salt-check", "true");
        }
    }

    @Test
    void isProdProfile_various() {
        assertTrue(com.zsubera.jpa.autoconfigure.EnvironmentHelper.isProdProfile("prod"));
        assertTrue(com.zsubera.jpa.autoconfigure.EnvironmentHelper.isProdProfile("production"));
        assertTrue(com.zsubera.jpa.autoconfigure.EnvironmentHelper.isProdProfile("dev,prod,qa"));
        assertTrue(com.zsubera.jpa.autoconfigure.EnvironmentHelper.isProdProfile("PROD"));
        assertFalse(com.zsubera.jpa.autoconfigure.EnvironmentHelper.isProdProfile("dev"));
        assertFalse(com.zsubera.jpa.autoconfigure.EnvironmentHelper.isProdProfile("reproduction"));
    }
}
