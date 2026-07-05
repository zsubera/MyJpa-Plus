package com.zsubera.jpa.converter;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Null-handling edge case tests for {@link EncryptConverter}.
 * Verifies the converter correctly handles null plaintext and null ciphertext.
 */
class EncryptConverterNullHandlingTest {

    private static final String TEST_KEY = "1234567890123456";

    @BeforeEach
    void setUp() {
        System.setProperty("myjpa.encrypt.key", TEST_KEY);
        System.setProperty("myjpa.encrypt.salt", "test-salt-value");
        EncryptConverter.clearCaches();
    }

    @AfterEach
    void tearDown() {
        System.clearProperty("myjpa.encrypt.key");
        System.clearProperty("myjpa.encrypt.salt");
        EncryptConverter.clearCaches();
    }

    @Test
    void convertToDatabaseColumnNullPlaintextReturnsNull() {
        EncryptConverter converter = new EncryptConverter();
        assertNull(converter.convertToDatabaseColumn(null));
    }

    @Test
    void convertToEntityAttributeNullCiphertextReturnsNull() {
        EncryptConverter converter = new EncryptConverter();
        assertNull(converter.convertToEntityAttribute(null));
    }

    @Test
    void convertToDatabaseColumnEmptyStringReturnsNonNull() {
        EncryptConverter converter = new EncryptConverter();
        String encrypted = converter.convertToDatabaseColumn("");
        assertNotNull(encrypted);
        assertFalse(encrypted.isEmpty());
    }

    @Test
    void roundTripEmptyString() {
        EncryptConverter converter = new EncryptConverter();
        String encrypted = converter.convertToDatabaseColumn("");
        String decrypted = converter.convertToEntityAttribute(encrypted);
        assertEquals("", decrypted);
    }

    @Test
    void roundTripSpecialCharacters() {
        EncryptConverter converter = new EncryptConverter();
        String original = "パスワード: p@$$w0rd! 你好世界 🎉";
        String encrypted = converter.convertToDatabaseColumn(original);
        String decrypted = converter.convertToEntityAttribute(encrypted);
        assertEquals(original, decrypted);
    }

    @Test
    void roundTripLongString() {
        EncryptConverter converter = new EncryptConverter();
        String original = "x".repeat(10000);
        String encrypted = converter.convertToDatabaseColumn(original);
        String decrypted = converter.convertToEntityAttribute(encrypted);
        assertEquals(original, decrypted);
    }

    @Test
    void differentEncryptionsOfSamePlaintextProduceDifferentCiphertext() {
        EncryptConverter converter = new EncryptConverter();
        String enc1 = converter.convertToDatabaseColumn("secret");
        String enc2 = converter.convertToDatabaseColumn("secret");
        // AES-GCM uses random IV, so same plaintext should produce different ciphertext
        assertNotEquals(enc1, enc2);
    }
}
