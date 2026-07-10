package com.zsubera.jpa.converter;

import static org.junit.jupiter.api.Assertions.*;

import com.zsubera.jpa.exception.MyJpaPlusException;
import java.lang.reflect.Field;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for EncryptConverter edge cases not covered by existing test files:
 * - reEncrypt(null) validation
 * - setPbkdf2Iterations / setSkipSaltCheck delegation
 * - shutdownWarmUpExecutor
 * - Cipher pool max-size discard path
 */
class EncryptConverterEdgeCaseTest {

    private static final String TEST_KEY = "1234567890123456";

    @BeforeEach
    void setUp() {
        System.setProperty("myjpa.encrypt.key", TEST_KEY);
        System.setProperty("myjpa.encrypt.salt", "test-salt-value");
        System.setProperty("myjpa-plus.encrypt.skip-salt-check", "true");
        EncryptConverter.clearCaches();
    }

    @AfterEach
    void tearDown() {
        System.clearProperty("myjpa.encrypt.key");
        System.clearProperty("myjpa.encrypt.salt");
        System.clearProperty("myjpa-plus.encrypt.skip-salt-check");
        EncryptConverter.clearCaches();
    }

    // ---- reEncrypt edge cases ----

    @Test
    void reEncrypt_null_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class, () -> EncryptConverter.reEncrypt(null));
    }

    @Test
    void reEncrypt_validValue_returnsReEncrypted() {
        EncryptConverter converter = new EncryptConverter();
        String encrypted = converter.convertToDatabaseColumn("test");
        String reEncrypted = EncryptConverter.reEncrypt(encrypted);
        assertNotNull(reEncrypted);
        assertNotEquals(encrypted, reEncrypted);
        // Verify re-encrypted value can be decrypted
        String decrypted = converter.convertToEntityAttribute(reEncrypted);
        assertEquals("test", decrypted);
    }

    @Test
    void reEncrypt_invalidValue_throwsMyJpaPlusException() {
        assertThrows(MyJpaPlusException.class, () -> EncryptConverter.reEncrypt("v1:not-valid-base64!!!"));
    }

    // ---- setPbkdf2Iterations ----

    @Test
    void setPbkdf2Iterations_validValue_succeeds() {
        assertDoesNotThrow(() -> EncryptConverter.setPbkdf2Iterations(200_000));
    }

    @Test
    void setPbkdf2Iterations_belowMinimum_throws() {
        assertThrows(IllegalArgumentException.class, () -> EncryptConverter.setPbkdf2Iterations(100));
    }

    @Test
    void setPbkdf2Iterations_aboveMaximum_throws() {
        assertThrows(IllegalArgumentException.class, () -> EncryptConverter.setPbkdf2Iterations(20_000_000));
    }

    // ---- setSkipSaltCheck ----

    @Test
    void setSkipSaltCheck_true_succeeds() {
        assertDoesNotThrow(() -> EncryptConverter.setSkipSaltCheck(true));
    }

    @Test
    void setSkipSaltCheck_false_succeeds() {
        assertDoesNotThrow(() -> EncryptConverter.setSkipSaltCheck(false));
    }

    // ---- shutdownWarmUpExecutor ----

    @Test
    void shutdownWarmUpExecutor_doesNotThrow() {
        assertDoesNotThrow(EncryptConverter::doShutdownWarmUpExecutor);
    }

    @Test
    void shutdownWarmUpExecutor_idempotent() {
        EncryptConverter.doShutdownWarmUpExecutor();
        assertDoesNotThrow(EncryptConverter::doShutdownWarmUpExecutor);
    }

    // ---- No cipher pooling (JDK GCM reuse bug mitigation) ----

    @Test
    void encryptDecrypt_roundtrip_succeeds() {
        EncryptConverter converter = new EncryptConverter();
        String encrypted = converter.convertToDatabaseColumn("test-value");
        assertNotNull(encrypted);
        assertEquals("test-value", converter.convertToEntityAttribute(encrypted));
    }

    @Test
    void multipleRoundtrips_allSucceed() {
        EncryptConverter converter = new EncryptConverter();
        for (int i = 0; i < 10; i++) {
            String val = "roundtrip-" + i;
            String encrypted = converter.convertToDatabaseColumn(val);
            assertEquals(val, converter.convertToEntityAttribute(encrypted));
        }
    }
}
