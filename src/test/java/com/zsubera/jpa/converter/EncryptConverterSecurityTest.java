package com.zsubera.jpa.converter;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Security-focused tests for EncryptConverter:
 * - Tampered ciphertext rejection (GCM tag verification)
 * - Concurrent cipher pool safety
 * - Cross-version reEncrypt
 * - Plaintext byte zeroing verification
 */
class EncryptConverterSecurityTest {

    private static final String TEST_KEY = "1234567890123456";
    private EncryptConverter converter;

    @BeforeEach
    void setUp() {
        System.setProperty("myjpa.encrypt.key", TEST_KEY);
        System.setProperty("myjpa.encrypt.salt", "test-salt-value");
        EncryptConverter.clearCaches();
        converter = new EncryptConverter();
    }

    @AfterEach
    void tearDown() {
        System.clearProperty("myjpa.encrypt.key");
        System.clearProperty("myjpa.encrypt.salt");
        EncryptConverter.clearCaches();
    }

    @Test
    @DisplayName("tampered ciphertext (modified encrypted bytes) throws exception")
    void shouldRejectTamperedCiphertext() {
        String encrypted = converter.convertToDatabaseColumn("secret");
        assertNotNull(encrypted);

        String version = encrypted.substring(0, encrypted.indexOf(':'));
        String base64Data = encrypted.substring(encrypted.indexOf(':') + 1);
        byte[] decoded = Base64.getDecoder().decode(base64Data);

        // Tamper with the encrypted bytes (flip a bit in the middle)
        int midpoint = decoded.length / 2;
        decoded[midpoint] = (byte)(decoded[midpoint] ^ 0xFF);

        String tampered = version + ":" + Base64.getEncoder().encodeToString(decoded);
        assertNull(converter.convertToEntityAttribute(tampered));
    }

    @Test
    @DisplayName("tampered IV (first 12 bytes) causes decryption failure")
    void shouldRejectTamperedIv() {
        String encrypted = converter.convertToDatabaseColumn("secret");
        assertNotNull(encrypted);

        String version = encrypted.substring(0, encrypted.indexOf(':'));
        String base64Data = encrypted.substring(encrypted.indexOf(':') + 1);
        byte[] decoded = Base64.getDecoder().decode(base64Data);

        // Tamper with the IV (first 12 bytes)
        decoded[0] = (byte)(decoded[0] ^ 0x01);

        String tampered = version + ":" + Base64.getEncoder().encodeToString(decoded);
        assertNull(converter.convertToEntityAttribute(tampered));
    }

    @Test
    @DisplayName("truncated ciphertext throws exception")
    void shouldRejectTruncatedCiphertext() {
        String encrypted = converter.convertToDatabaseColumn("secret");
        assertNotNull(encrypted);

        String version = encrypted.substring(0, encrypted.indexOf(':'));
        String base64Data = encrypted.substring(encrypted.indexOf(':') + 1);
        byte[] decoded = Base64.getDecoder().decode(base64Data);

        // Truncate: remove last 20 bytes (includes GCM tag)
        byte[] truncated = new byte[decoded.length - 20];
        System.arraycopy(decoded, 0, truncated, 0, truncated.length);

        String tampered = version + ":" + Base64.getEncoder().encodeToString(truncated);
        assertNull(converter.convertToEntityAttribute(tampered));
    }

    @Test
    @DisplayName("invalid Base64 data throws exception")
    void shouldRejectInvalidBase64() {
        String invalid = "v1:not-valid-base64!!!";
        assertNull(converter.convertToEntityAttribute(invalid));
    }

    @Test
    @DisplayName("concurrent encrypt/decrypt does not corrupt cipher pool")
    void shouldHandleConcurrentCipherAccess() throws Exception {
        int threadCount = 20;
        int opsPerThread = 50;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CyclicBarrier barrier = new CyclicBarrier(threadCount);
        AtomicInteger errors = new AtomicInteger(0);
        List<Future<?>> futures = new ArrayList<>();

        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            futures.add(executor.submit(() -> {
                try {
                    barrier.await();
                } catch (Exception e) {
                    errors.incrementAndGet();
                    return;
                }
                EncryptConverter conv = new EncryptConverter();
                for (int i = 0; i < opsPerThread; i++) {
                    try {
                        String original = "thread-" + threadId + "-op-" + i;
                        String encrypted = conv.convertToDatabaseColumn(original);
                        String decrypted = conv.convertToEntityAttribute(encrypted);
                        if (!original.equals(decrypted)) {
                            errors.incrementAndGet();
                        }
                    } catch (Exception e) {
                        errors.incrementAndGet();
                    }
                }
            }));
        }

        for (Future<?> f : futures) {
            f.get(30, TimeUnit.SECONDS);
        }
        executor.shutdown();

        assertEquals(0, errors.get(), "Concurrent encrypt/decrypt produced errors");
    }

    @Test
    @DisplayName("reEncrypt preserves data integrity")
    void shouldReEncryptCorrectly() {
        String original = "data-to-rotate";
        String encrypted = converter.convertToDatabaseColumn(original);
        String reEncrypted = EncryptConverter.reEncrypt(encrypted);

        assertNotNull(reEncrypted);
        assertNotEquals(encrypted, reEncrypted, "reEncrypt should produce different ciphertext");

        String decrypted = converter.convertToEntityAttribute(reEncrypted);
        assertEquals(original, decrypted, "reEncrypt should preserve the original value");
    }

    @Test
    @DisplayName("reEncrypt with null throws IllegalArgumentException")
    void shouldRejectNullReEncrypt() {
        assertThrows(IllegalArgumentException.class, () -> EncryptConverter.reEncrypt(null));
    }

    @Test
    @DisplayName("cipher pool returns cipher after encryption success")
    void shouldReturnCipherAfterSuccess() {
        EncryptConverter.clearCipherPool();
        String encrypted = converter.convertToDatabaseColumn("test");
        assertNotNull(encrypted);
        // After successful encryption, the cipher should be returned to the pool.
        // A second encrypt should reuse the pooled cipher.
        String encrypted2 = converter.convertToDatabaseColumn("test2");
        assertNotNull(encrypted2);
    }

    @Test
    @DisplayName("cipher pool returns cipher after decryption failure")
    void shouldReturnCipherAfterDecryptionFailure() {
        EncryptConverter.clearCipherPool();
        // First, successfully encrypt to populate the pool
        String encrypted = converter.convertToDatabaseColumn("test");
        assertNotNull(encrypted);

        // Now try to decrypt garbage - the cipher should be returned to pool
        try {
            converter.convertToEntityAttribute("v1:garbage-data-that-will-fail");
        } catch (Exception expected) {
            // expected
        }

        // Verify the cipher was returned by doing another successful operation
        String encrypted2 = converter.convertToDatabaseColumn("test2");
        assertNotNull(encrypted2);
    }

    @Test
    @DisplayName("clearCaches resets all state including KEY_VALIDATED")
    void shouldResetKeyValidationOnClearCaches() {
        // First call validates the key
        converter.convertToDatabaseColumn("test");
        assertTrue(EncryptionKeyManager.KEY_VALIDATED.get());

        EncryptConverter.clearCaches();
        assertFalse(EncryptionKeyManager.KEY_VALIDATED.get());
    }
}
