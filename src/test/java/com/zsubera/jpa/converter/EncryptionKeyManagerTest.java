package com.zsubera.jpa.converter;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class EncryptionKeyManagerTest {

    @Test
    void refreshKeyVersion_resetsKeyValidated() throws Exception {
        Field validatedField = EncryptionKeyManager.class.getDeclaredField("KEY_VALIDATED");
        validatedField.setAccessible(true);
        AtomicBoolean keyValidated = (AtomicBoolean)validatedField.get(null);

        keyValidated.set(true);
        assertTrue(keyValidated.get());

        EncryptionKeyManager.refreshKeyVersion();

        assertFalse(keyValidated.get());
    }

    @Test
    void clearCaches_resetsKeyValidated() throws Exception {
        Field validatedField = EncryptionKeyManager.class.getDeclaredField("KEY_VALIDATED");
        validatedField.setAccessible(true);
        AtomicBoolean keyValidated = (AtomicBoolean)validatedField.get(null);

        keyValidated.set(true);
        EncryptionKeyManager.clearCaches();
        assertFalse(keyValidated.get());
    }
}
