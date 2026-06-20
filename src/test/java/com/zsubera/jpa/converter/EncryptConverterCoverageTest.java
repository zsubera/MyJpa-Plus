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

class EncryptConverterCoverageTest {

    private static final String TEST_KEY = "1234567890123456";

    @BeforeEach
    void setUp() throws Exception {
        System.setProperty("myjpa.encrypt.key", TEST_KEY);
        System.setProperty("myjpa-plus.encrypt.skip-salt-check", "true");
        EncryptConverter.clearCacheForTesting();
        Field f = EncryptConverter.class.getDeclaredField("keyValidated");
        f.setAccessible(true);
        f.set(null, false);
    }

    @AfterEach
    void tearDown() throws Exception {
        System.clearProperty("myjpa.encrypt.key");
        System.clearProperty("myjpa-plus.encrypt.skip-salt-check");
        System.clearProperty("myjpa.encrypt.key.version");
        EncryptConverter.clearCacheForTesting();
        Field f = EncryptConverter.class.getDeclaredField("keyValidated");
        f.setAccessible(true);
        f.set(null, false);
    }

    // ---- validateKeyConfiguration: synchronized block ----

    @Test
    void validateKeyConfiguration_validKey_synchronizedPath() throws Exception {
        Field f = EncryptConverter.class.getDeclaredField("keyValidated");
        f.setAccessible(true);
        f.set(null, false);
        EncryptConverter.validateKeyConfiguration();
        assertTrue((boolean)f.get(null));
        // Second call should hit synchronized block early return
        EncryptConverter.validateKeyConfiguration();
        assertTrue((boolean)f.get(null));
    }

    @Test
    void validateKeyConfiguration_withSystemProperty() throws Exception {
        Field f = EncryptConverter.class.getDeclaredField("keyValidated");
        f.setAccessible(true);
        f.set(null, false);
        String oldEnv = System.getenv("MYJPA_ENCRYPT_KEY");
        System.clearProperty("myjpa.encrypt.key");
        try {
            System.setProperty("myjpa.encrypt.key", "1234567890123456");
            EncryptConverter.validateKeyConfiguration();
        } finally {
            System.clearProperty("myjpa.encrypt.key");
            if (oldEnv != null)
                System.setProperty("myjpa.encrypt.key", oldEnv);
        }
    }

    @Test
    void validateKeyConfiguration_withEnvProperty() throws Exception {
        Field f = EncryptConverter.class.getDeclaredField("keyValidated");
        f.setAccessible(true);
        f.set(null, false);
        System.clearProperty("myjpa.encrypt.key");
        // Can't set env vars, but we can test the path where both are null
        try {
            assertThrows(IllegalStateException.class, () -> EncryptConverter.validateKeyConfiguration());
        } finally {
            System.setProperty("myjpa.encrypt.key", TEST_KEY);
        }
    }

    // ---- getKeyVersion: synchronized block cache hit ----

    @Test
    void getKeyVersion_cacheHitInSynchronizedBlock() throws Exception {
        Method m = EncryptConverter.class.getDeclaredMethod("getKeyVersion");
        m.setAccessible(true);
        String v1 = (String)m.invoke(null);
        String v2 = (String)m.invoke(null);
        assertEquals(v1, v2);
    }

    // ---- convertToDatabaseColumn: encryption error path ----

    @Test
    void convertToDatabaseColumn_wrongKey_throws() {
        EncryptConverter converter = new EncryptConverter();
        String encrypted = converter.convertToDatabaseColumn("test");
        String oldKey = System.getProperty("myjpa.encrypt.key");
        System.setProperty("myjpa.encrypt.key", "9999999999999999");
        EncryptConverter.clearCacheForTesting();
        try {
            Field f = EncryptConverter.class.getDeclaredField("keyValidated");
            f.setAccessible(true);
            f.set(null, false);
            EncryptConverter converter2 = new EncryptConverter();
            assertThrows(Exception.class, () -> converter2.convertToEntityAttribute(encrypted));
        } catch (Exception e) {
            fail(e);
        } finally {
            System.setProperty("myjpa.encrypt.key", oldKey);
            EncryptConverter.clearCacheForTesting();
        }
    }

    // ---- getKeySpec: cache full throw ----

    @Test
    void getKeySpec_cacheFull_throw() throws Exception {
        Field cacheField = EncryptConverter.class.getDeclaredField("KEY_CACHE");
        cacheField.setAccessible(true);
        @SuppressWarnings("unchecked")
        ConcurrentHashMap<String, SecretKeySpec> cache = (ConcurrentHashMap<String, SecretKeySpec>)cacheField.get(null);
        cache.clear();
        try {
            for (int i = 0; i < 16; i++) {
                cache.put("v" + i, new SecretKeySpec(new byte[16], "AES"));
            }
            EncryptConverter converter = new EncryptConverter();
            assertThrows(Exception.class, () -> converter.convertToEntityAttribute("v999:AAAA"));
        } finally {
            cache.clear();
        }
    }

    // ---- getKeySpec: computeIfAbsent lambda v != null path ----

    @Test
    void getKeySpec_computeIfAbsent_existingKey() {
        EncryptConverter converter = new EncryptConverter();
        String encrypted = converter.convertToDatabaseColumn("test");
        // Second call should hit the cache path
        String encrypted2 = converter.convertToDatabaseColumn("test");
        assertNotNull(encrypted2);
    }

    // ---- isUsingDevSalt: SALT_ENV check ----

    @Test
    void isUsingDevSalt_withSaltEnv() throws Exception {
        Method m = EncryptConverter.class.getDeclaredMethod("isUsingDevSalt");
        m.setAccessible(true);
        // Without SALT_ENV set, should check SALT_PROPERTY
        System.clearProperty("myjpa.encrypt.salt");
        System.setProperty("myjpa-plus.encrypt.skip-salt-check", "true");
        assertTrue((boolean)m.invoke(null));
    }

    @Test
    void isUsingDevSalt_withSaltProperty() throws Exception {
        Method m = EncryptConverter.class.getDeclaredMethod("isUsingDevSalt");
        m.setAccessible(true);
        System.setProperty("myjpa.encrypt.salt", "my-salt");
        try {
            assertFalse((boolean)m.invoke(null));
        } finally {
            System.clearProperty("myjpa.encrypt.salt");
        }
    }

    // ---- isProductionEnvironment: various paths ----

    @Test
    void isProductionEnvironment_withRequireSaltEnv() throws Exception {
        Method m = EncryptConverter.class.getDeclaredMethod("isProductionEnvironment");
        m.setAccessible(true);
        // Can't set env vars, but we can test the property path
        System.setProperty("myjpa-plus.encrypt.require-salt", "true");
        try {
            assertTrue((boolean)m.invoke(null));
        } finally {
            System.clearProperty("myjpa-plus.encrypt.require-salt");
        }
    }

    @Test
    void isProductionEnvironment_withProdProfile() throws Exception {
        Method m = EncryptConverter.class.getDeclaredMethod("isProductionEnvironment");
        m.setAccessible(true);
        System.setProperty("spring.profiles.active", "prod");
        try {
            assertTrue((boolean)m.invoke(null));
        } finally {
            System.clearProperty("spring.profiles.active");
        }
    }

    @Test
    void isProductionEnvironment_withNonProdProfile() throws Exception {
        Method m = EncryptConverter.class.getDeclaredMethod("isProductionEnvironment");
        m.setAccessible(true);
        System.setProperty("spring.profiles.active", "dev");
        try {
            assertFalse((boolean)m.invoke(null));
        } finally {
            System.clearProperty("spring.profiles.active");
        }
    }

    // ---- getSalt: production environment check ----

    @Test
    void getSalt_skipCheckProduction_throws() throws Exception {
        Method m = EncryptConverter.class.getDeclaredMethod("getSalt");
        m.setAccessible(true);
        System.clearProperty("myjpa.encrypt.salt");
        System.setProperty("myjpa-plus.encrypt.skip-salt-check", "true");
        System.setProperty("myjpa-plus.encrypt.require-salt", "true");
        try {
            assertThrows(Exception.class, () -> m.invoke(null));
        } finally {
            System.clearProperty("myjpa-plus.encrypt.skip-salt-check");
            System.clearProperty("myjpa-plus.encrypt.require-salt");
        }
    }

    @Test
    void getSalt_skipCheckDevMode() throws Exception {
        Method m = EncryptConverter.class.getDeclaredMethod("getSalt");
        m.setAccessible(true);
        System.clearProperty("myjpa.encrypt.salt");
        System.setProperty("myjpa-plus.encrypt.skip-salt-check", "true");
        System.clearProperty("myjpa-plus.encrypt.require-salt");
        try {
            byte[] salt = (byte[])m.invoke(null);
            assertNotNull(salt);
            assertArrayEquals("myjpa-plus-dev-salt-2024".getBytes(StandardCharsets.UTF_8), salt);
        } finally {
            System.clearProperty("myjpa-plus.encrypt.skip-salt-check");
        }
    }

    // ---- reEncrypt: keyValidated path ----

    @Test
    void reEncrypt_validatesKeyFirst() throws Exception {
        Field f = EncryptConverter.class.getDeclaredField("keyValidated");
        f.setAccessible(true);
        f.set(null, false);
        EncryptConverter converter = new EncryptConverter();
        String encrypted = converter.convertToDatabaseColumn("test");
        // reEncrypt should validate key first
        String reEncrypted = EncryptConverter.reEncrypt(encrypted);
        assertNotNull(reEncrypted);
        assertTrue((boolean)f.get(null));
    }

    // ---- warmUpKeyCacheSync: exception path ----

    @Test
    void warmUpKeyCacheSync_exceptionPath() {
        String oldKey = System.getProperty("myjpa.encrypt.key");
        System.clearProperty("myjpa.encrypt.key");
        EncryptConverter.clearCacheForTesting();
        try {
            Field f = EncryptConverter.class.getDeclaredField("keyValidated");
            f.setAccessible(true);
            f.set(null, false);
            // Without a key configured, warmUpKeyCacheSync should handle the exception
            assertDoesNotThrow(() -> EncryptConverter.warmUpKeyCacheSync());
        } catch (Exception e) {
            fail(e);
        } finally {
            if (oldKey != null)
                System.setProperty("myjpa.encrypt.key", oldKey);
            EncryptConverter.clearCacheForTesting();
        }
    }

    // ---- getKeyVersion: synchronized block with property ----

    @Test
    void getKeyVersion_withProperty() throws Exception {
        System.setProperty("myjpa.encrypt.key.version", "v3");
        EncryptConverter.clearCacheForTesting();
        Method m = EncryptConverter.class.getDeclaredMethod("getKeyVersion");
        m.setAccessible(true);
        String version = (String)m.invoke(null);
        assertEquals("v3", version);
        System.clearProperty("myjpa.encrypt.key.version");
    }

    @Test
    void getKeyVersion_defaultWhenNoProperty() throws Exception {
        System.clearProperty("myjpa.encrypt.key.version");
        EncryptConverter.clearCacheForTesting();
        Method m = EncryptConverter.class.getDeclaredMethod("getKeyVersion");
        m.setAccessible(true);
        String version = (String)m.invoke(null);
        assertEquals("v1", version);
    }

    // ---- isUsingDevSalt: with SKIP_SALT_ENV ----

    @Test
    void isUsingDevSalt_withSkipSaltEnv() throws Exception {
        Method m = EncryptConverter.class.getDeclaredMethod("isUsingDevSalt");
        m.setAccessible(true);
        System.clearProperty("myjpa.encrypt.salt");
        System.clearProperty("myjpa-plus.encrypt.skip-salt-check");
        // Can't set env vars, but we can test the property path
        System.setProperty("myjpa-plus.encrypt.skip-salt-check", "true");
        try {
            assertTrue((boolean)m.invoke(null));
        } finally {
            System.setProperty("myjpa-plus.encrypt.skip-salt-check", "true");
        }
    }

    // ---- isProductionEnvironment: with SPRING_PROFILES env ----

    @Test
    void isProductionEnvironment_withSpringProfilesEnv() throws Exception {
        Method m = EncryptConverter.class.getDeclaredMethod("isProductionEnvironment");
        m.setAccessible(true);
        // Can't set env vars, but we can test the property path
        System.setProperty("spring.profiles.active", "prod");
        try {
            assertTrue((boolean)m.invoke(null));
        } finally {
            System.clearProperty("spring.profiles.active");
        }
    }

    // ---- reEncrypt: with validated key ----

    @Test
    void reEncrypt_withValidatedKey() throws Exception {
        EncryptConverter.validateKeyConfiguration();
        EncryptConverter converter = new EncryptConverter();
        String encrypted = converter.convertToDatabaseColumn("test");
        String reEncrypted = EncryptConverter.reEncrypt(encrypted);
        assertNotNull(reEncrypted);
        assertEquals("test", converter.convertToEntityAttribute(reEncrypted));
    }

    // ---- getKeySpec: cache hit path ----

    @Test
    void getKeySpec_cacheHit() {
        EncryptConverter converter = new EncryptConverter();
        // First call populates cache
        String encrypted1 = converter.convertToDatabaseColumn("test");
        // Second call should hit cache
        String encrypted2 = converter.convertToDatabaseColumn("test2");
        assertNotNull(encrypted1);
        assertNotNull(encrypted2);
        assertNotEquals(encrypted1, encrypted2);
    }

    // ---- resolveRawKey: single key with version mismatch ----

    @Test
    void resolveRawKey_singleKeyVersionMismatch() {
        System.setProperty("myjpa.encrypt.key", TEST_KEY);
        System.setProperty("myjpa.encrypt.key.version", "v99");
        EncryptConverter.clearCacheForTesting();
        try {
            Field f = EncryptConverter.class.getDeclaredField("keyValidated");
            f.setAccessible(true);
            f.set(null, false);
            EncryptConverter converter = new EncryptConverter();
            // Should trigger logVersionMismatch
            String encrypted = converter.convertToDatabaseColumn("test");
            assertNotNull(encrypted);
        } catch (Exception e) {
            fail(e);
        } finally {
            System.clearProperty("myjpa.encrypt.key.version");
            EncryptConverter.clearCacheForTesting();
        }
    }

    // ---- convertToEntityAttribute: decryption error path ----

    @Test
    void convertToEntityAttribute_decryptionError() {
        EncryptConverter converter = new EncryptConverter();
        String encrypted = converter.convertToDatabaseColumn("test");
        // Use wrong key
        String oldKey = System.getProperty("myjpa.encrypt.key");
        System.setProperty("myjpa.encrypt.key", "9999999999999999");
        EncryptConverter.clearCacheForTesting();
        try {
            Field f = EncryptConverter.class.getDeclaredField("keyValidated");
            f.setAccessible(true);
            f.set(null, false);
            EncryptConverter converter2 = new EncryptConverter();
            assertThrows(Exception.class, () -> converter2.convertToEntityAttribute(encrypted));
        } catch (Exception e) {
            fail(e);
        } finally {
            System.setProperty("myjpa.encrypt.key", oldKey);
            EncryptConverter.clearCacheForTesting();
        }
    }

    // ---- convertToEntityAttribute: invalid Base64 path ----

    @Test
    void convertToEntityAttribute_invalidBase64() {
        EncryptConverter converter = new EncryptConverter();
        assertThrows(Exception.class, () -> converter.convertToEntityAttribute("v1:not-valid-base64!!!"));
    }

    // ---- convertToEntityAttribute: short data path ----

    @Test
    void convertToEntityAttribute_shortData() {
        EncryptConverter converter = new EncryptConverter();
        String shortData = Base64.getEncoder().encodeToString(new byte[] {1, 2, 3});
        assertThrows(Exception.class, () -> converter.convertToEntityAttribute("v1:" + shortData));
    }

    // ---- convertToEntityAttribute: unversioned format ----

    @Test
    void convertToEntityAttribute_unversionedFormat() {
        EncryptConverter converter = new EncryptConverter();
        String encrypted = converter.convertToDatabaseColumn("test");
        String unversioned = encrypted.substring(encrypted.indexOf(':') + 1);
        String decrypted = converter.convertToEntityAttribute(unversioned);
        assertEquals("test", decrypted);
    }

    // ---- resolveRawKey: multi-key format ----

    @Test
    void resolveRawKey_multiKeyValidEntries() {
        System.setProperty("myjpa.encrypt.key", "v1:key11111111111111,v2:key22222222222222");
        EncryptConverter.clearCacheForTesting();
        try {
            Field f = EncryptConverter.class.getDeclaredField("keyValidated");
            f.setAccessible(true);
            f.set(null, false);
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
            EncryptConverter.clearCacheForTesting();
        }
    }

    @Test
    void resolveRawKey_multiKeyInvalidEntries() {
        System.setProperty("myjpa.encrypt.key", "v1:key11111111111111,invalid-entry");
        EncryptConverter.clearCacheForTesting();
        try {
            Field f = EncryptConverter.class.getDeclaredField("keyValidated");
            f.setAccessible(true);
            f.set(null, false);
            EncryptConverter converter = new EncryptConverter();
            assertThrows(Exception.class, () -> converter.convertToDatabaseColumn("test"));
        } catch (Exception e) {
            fail(e);
        } finally {
            System.setProperty("myjpa.encrypt.key", TEST_KEY);
            EncryptConverter.clearCacheForTesting();
        }
    }

    @Test
    void resolveRawKey_multiKeyVersionNotFound() {
        System.setProperty("myjpa.encrypt.key", "v1:key11111111111111,v2:key22222222222222");
        EncryptConverter.clearCacheForTesting();
        try {
            Field f = EncryptConverter.class.getDeclaredField("keyValidated");
            f.setAccessible(true);
            f.set(null, false);
            System.setProperty("myjpa.encrypt.key.version", "v99");
            EncryptConverter converter = new EncryptConverter();
            assertThrows(Exception.class, () -> converter.convertToDatabaseColumn("test"));
        } catch (Exception e) {
            fail(e);
        } finally {
            System.setProperty("myjpa.encrypt.key", TEST_KEY);
            System.clearProperty("myjpa.encrypt.key.version");
            EncryptConverter.clearCacheForTesting();
        }
    }

    // ---- isProdProfile: various ----

    @Test
    void isProdProfile_various() throws Exception {
        Method m = EncryptConverter.class.getDeclaredMethod("isProdProfile", String.class);
        m.setAccessible(true);
        assertTrue((boolean)m.invoke(null, "prod"));
        assertTrue((boolean)m.invoke(null, "production"));
        assertTrue((boolean)m.invoke(null, "dev,prod,qa"));
        assertTrue((boolean)m.invoke(null, "PROD"));
        assertFalse((boolean)m.invoke(null, "dev"));
        assertFalse((boolean)m.invoke(null, "reproduction"));
    }
}
