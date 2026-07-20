package com.zsubera.jpa.converter;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Edge case tests for EncryptionKeyManager:
 * - LRU eviction behavior
 * - Multi-key format validation
 * - Salt caching
 * - PBKDF2 iterations configuration
 */
class EncryptionKeyManagerEdgeCaseTest {

    @BeforeEach
    void setUp() {
        System.setProperty("myjpa.encrypt.key", "1234567890123456");
        System.setProperty("myjpa.encrypt.salt", "test-salt-value");
        EncryptionKeyManager.resetIterationsConfigured();
        EncryptionKeyManager.clearCaches();
    }

    @AfterEach
    void tearDown() {
        System.clearProperty("myjpa.encrypt.key");
        System.clearProperty("myjpa.encrypt.salt");
        System.clearProperty("myjpa.encrypt.key.version");
        EncryptionKeyManager.resetIterationsConfigured();
        EncryptionKeyManager.clearCaches();
    }

    @Test
    @DisplayName("default key version is v1 when not configured")
    void shouldDefaultToV1() {
        String version = EncryptionKeyManager.getKeyVersion();
        assertEquals("v1", version);
    }

    @Test
    @DisplayName("key version from system property")
    void shouldReadKeyVersionFromProperty() {
        System.setProperty("myjpa.encrypt.key.version", "v2");
        String version = EncryptionKeyManager.getKeyVersion();
        assertEquals("v2", version);
    }

    @Test
    @DisplayName("getKeySpec with null version uses default")
    void shouldHandleNullVersion() {
        var keySpec = EncryptionKeyManager.getKeySpec(null);
        assertNotNull(keySpec);
        assertEquals("AES", keySpec.getAlgorithm());
    }

    @Test
    @DisplayName("getKeySpec with 'default' version uses default key")
    void shouldHandleDefaultVersion() {
        var keySpec = EncryptionKeyManager.getKeySpec("default");
        assertNotNull(keySpec);
    }

    @Test
    @DisplayName("getKeySpec caches derived keys")
    void shouldCacheDerivedKeys() {
        var key1 = EncryptionKeyManager.getKeySpec("v1");
        var key2 = EncryptionKeyManager.getKeySpec("v1");
        assertSame(key1, key2, "Should return cached key spec");
    }

    @Test
    @DisplayName("setPbkdf2Iterations rejects out-of-range values")
    void shouldRejectInvalidIterations() {
        assertThrows(IllegalArgumentException.class, () -> EncryptionKeyManager.setPbkdf2Iterations(50)); // too low
        assertThrows(IllegalArgumentException.class, () -> EncryptionKeyManager.setPbkdf2Iterations(20_000_000)); // too
                                                                                                                  // high
    }

    @Test
    @DisplayName("setPbkdf2Iterations accepts valid range")
    void shouldAcceptValidIterations() {
        assertDoesNotThrow(() -> EncryptionKeyManager.setPbkdf2Iterations(100_000));
        EncryptionKeyManager.resetIterationsConfigured();
        assertDoesNotThrow(() -> EncryptionKeyManager.setPbkdf2Iterations(10_000_000));
        EncryptionKeyManager.resetIterationsConfigured();
        assertDoesNotThrow(() -> EncryptionKeyManager.setPbkdf2Iterations(600_000));
        EncryptionKeyManager.resetIterationsConfigured();
    }

    @Test
    @DisplayName("validateKeyConfiguration fails without key")
    void shouldFailWithoutKey() {
        System.clearProperty("myjpa.encrypt.key");
        EncryptionKeyManager.clearCaches();
        assertThrows(com.zsubera.jpa.exception.MyJpaPlusException.class,
            () -> EncryptionKeyManager.validateKeyConfiguration());
    }

    @Test
    @DisplayName("validateKeyConfiguration fails with short key")
    void shouldFailWithShortKey() {
        System.setProperty("myjpa.encrypt.key", "short");
        EncryptionKeyManager.clearCaches();
        assertThrows(com.zsubera.jpa.exception.MyJpaPlusException.class,
            () -> EncryptionKeyManager.validateKeyConfiguration());
    }

    @Test
    @DisplayName("clearCaches resets KEY_VALIDATED flag")
    void shouldResetValidatedFlag() {
        EncryptionKeyManager.validateKeyConfiguration();
        assertTrue(EncryptionKeyManager.KEY_VALIDATED.get());

        EncryptionKeyManager.clearCaches();
        assertFalse(EncryptionKeyManager.KEY_VALIDATED.get());
    }

    @Test
    @DisplayName("refreshKeyVersion clears caches")
    void shouldRefreshKeyVersion() {
        EncryptionKeyManager.getKeySpec("v1");
        EncryptionKeyManager.refreshKeyVersion();
        assertFalse(EncryptionKeyManager.KEY_VALIDATED.get());
    }
}
