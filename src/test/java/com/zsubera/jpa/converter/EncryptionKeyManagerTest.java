package com.zsubera.jpa.converter;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class EncryptionKeyManagerTest {

    @Test
    void setPbkdf2IterationsValidRange() {
        // Should not throw
        EncryptionKeyManager.setPbkdf2Iterations(100000);
        EncryptionKeyManager.setPbkdf2Iterations(1000000);
        EncryptionKeyManager.setPbkdf2Iterations(10000000);
    }

    @Test
    void setPbkdf2IterationsBelowMinThrows() {
        assertThrows(IllegalArgumentException.class, () -> EncryptionKeyManager.setPbkdf2Iterations(99999));
    }

    @Test
    void setPbkdf2IterationsAboveMaxThrows() {
        assertThrows(IllegalArgumentException.class, () -> EncryptionKeyManager.setPbkdf2Iterations(10000001));
    }

    @Test
    void setSkipSaltCheck() {
        EncryptionKeyManager.setSkipSaltCheck(true);
        assertTrue(EncryptionKeyManager.isSaltCheckSkipped());
        EncryptionKeyManager.setSkipSaltCheck(false);
        assertFalse(EncryptionKeyManager.isSaltCheckSkipped());
    }
}
