package com.zsubera.jpa.converter;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EncryptConverterTest {

    private static final String TEST_KEY = "1234567890123456";
    private EncryptConverter converter;

    @BeforeEach
    void setUp() {
        System.setProperty("myjpa.encrypt.key", TEST_KEY);
        converter = new EncryptConverter();
    }

    @AfterEach
    void tearDown() {
        System.clearProperty("myjpa.encrypt.key");
    }

    @Test
    @DisplayName("encrypted value differs from original")
    void shouldEncryptValue() {
        String encrypted = converter.convertToDatabaseColumn("sensitive-data");
        assertNotNull(encrypted);
        assertNotEquals("sensitive-data", encrypted);
    }

    @Test
    @DisplayName("round-trip encrypt/decrypt")
    void shouldDecryptToOriginal() {
        String original = "sensitive-data";
        String encrypted = converter.convertToDatabaseColumn(original);
        String decrypted = converter.convertToEntityAttribute(encrypted);
        assertEquals(original, decrypted);
    }

    @Test
    @DisplayName("null encrypt returns null")
    void shouldReturnNullForNullEncrypt() {
        assertNull(converter.convertToDatabaseColumn(null));
    }

    @Test
    @DisplayName("null decrypt returns null")
    void shouldReturnNullForNullDecrypt() {
        assertNull(converter.convertToEntityAttribute(null));
    }

    @Test
    @DisplayName("same input produces different ciphertext (random IV)")
    void shouldProduceDifferentCiphertextForSameInput() {
        String encrypted1 = converter.convertToDatabaseColumn("same-value");
        String encrypted2 = converter.convertToDatabaseColumn("same-value");
        assertNotEquals(encrypted1, encrypted2);
    }

    @Test
    @DisplayName("different ciphertexts decrypt to same value")
    void shouldDecryptDifferentCiphertextToSameValue() {
        String original = "same-value";
        String encrypted1 = converter.convertToDatabaseColumn(original);
        String encrypted2 = converter.convertToDatabaseColumn(original);
        assertEquals(original, converter.convertToEntityAttribute(encrypted1));
        assertEquals(original, converter.convertToEntityAttribute(encrypted2));
    }

    @Test
    @DisplayName("Chinese content round-trip")
    void shouldHandleChineseContent() {
        String original = "中文敏感信息";
        String encrypted = converter.convertToDatabaseColumn(original);
        String decrypted = converter.convertToEntityAttribute(encrypted);
        assertEquals(original, decrypted);
    }

    @Test
    @DisplayName("empty string round-trip")
    void shouldHandleEmptyString() {
        String encrypted = converter.convertToDatabaseColumn("");
        String decrypted = converter.convertToEntityAttribute(encrypted);
        assertEquals("", decrypted);
    }

    @Test
    @DisplayName("missing key throws NullPointerException")
    void shouldThrowWhenKeyNotSet() {
        System.clearProperty("myjpa.encrypt.key");
        EncryptConverter noKeyConverter = new EncryptConverter();
        assertThrows(NullPointerException.class, () -> noKeyConverter.convertToDatabaseColumn("test"));
    }

    @Test
    @DisplayName("invalid key length throws IllegalStateException")
    void shouldThrowForInvalidKeyLength() {
        System.setProperty("myjpa.encrypt.key", "short");
        EncryptConverter badKeyConverter = new EncryptConverter();
        assertThrows(IllegalStateException.class, () -> badKeyConverter.convertToDatabaseColumn("test"));
    }

    @Test
    @DisplayName("256-bit key supported")
    void shouldSupport256BitKey() {
        System.setProperty("myjpa.encrypt.key", "12345678901234561234567890123456");
        EncryptConverter aes256 = new EncryptConverter();
        String original = "test-data";
        String encrypted = aes256.convertToDatabaseColumn(original);
        String decrypted = aes256.convertToEntityAttribute(encrypted);
        assertEquals(original, decrypted);
    }

    @Test
    @DisplayName("env variable key supported")
    void shouldSupportEnvVariableKey() {
        System.clearProperty("myjpa.encrypt.key");
        EncryptConverter envConverter = new EncryptConverter();
        String envKey = System.getenv("MYJPA_ENCRYPT_KEY");
        if (envKey != null && (envKey.length() == 16 || envKey.length() == 24 || envKey.length() == 32)) {
            String original = "env-test";
            String encrypted = envConverter.convertToDatabaseColumn(original);
            String decrypted = envConverter.convertToEntityAttribute(encrypted);
            assertEquals(original, decrypted);
        }
    }
}
