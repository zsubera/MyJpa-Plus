package com.zsubera.jpa.converter;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class EncryptionKeyManagerAdditionalTest {

    @Test
    void setPbkdf2Iterations_validBounds_succeeds() {
        assertDoesNotThrow(() -> EncryptionKeyManager.setPbkdf2Iterations(100_000));
        assertDoesNotThrow(() -> EncryptionKeyManager.setPbkdf2Iterations(10_000_000));
        assertDoesNotThrow(() -> EncryptionKeyManager.setPbkdf2Iterations(1_000_000));
    }

    @Test
    void setPbkdf2Iterations_belowMinimum_throws() {
        assertThrows(IllegalArgumentException.class, () -> EncryptionKeyManager.setPbkdf2Iterations(99_999));
    }

    @Test
    void setPbkdf2Iterations_aboveMaximum_throws() {
        assertThrows(IllegalArgumentException.class, () -> EncryptionKeyManager.setPbkdf2Iterations(10_000_001));
    }

    @Test
    void setSkipSaltCheck_true_doesNotThrow() {
        assertDoesNotThrow(() -> EncryptionKeyManager.setSkipSaltCheck(true));
        EncryptionKeyManager.setSkipSaltCheck(false);
    }
}
