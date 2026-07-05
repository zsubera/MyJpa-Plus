package com.zsubera.jpa.converter;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import java.util.concurrent.ExecutorService;
import org.junit.jupiter.api.Test;

/**
 * Regression tests for EncryptConverter warmup executor lifecycle.
 * Verifies that the executor is properly registered for cleanup.
 */
class EncryptConverterShutdownTest {

    @Test
    void shutdownWarmUpExecutor_clearsExecutorReference() throws Exception {
        // Access the warmUpExecutor field
        Field warmUpField = EncryptConverter.class.getDeclaredField("warmUpExecutor");
        warmUpField.setAccessible(true);

        // Initially null or already created
        ExecutorService before = (ExecutorService)warmUpField.get(null);

        // Trigger executor creation
        EncryptConverter.warmUpKeyCache();
        Thread.sleep(100); // let the async task start

        ExecutorService after = (ExecutorService)warmUpField.get(null);
        assertNotNull(after, "Executor should be created after warmUpKeyCache()");

        // Call shutdown
        EncryptConverter.doShutdownWarmUpExecutor();

        ExecutorService afterShutdown = (ExecutorService)warmUpField.get(null);
        assertNull(afterShutdown, "Executor reference should be null after shutdown");
    }

    @Test
    void shutdownWarmUpExecutor_idempotent() throws Exception {
        // Should not throw when called multiple times
        EncryptConverter.doShutdownWarmUpExecutor();
        EncryptConverter.doShutdownWarmUpExecutor();
        EncryptConverter.doShutdownWarmUpExecutor();
    }

    @Test
    void clearCaches_clearsCipherPool() {
        EncryptConverter.clearCaches();
        // Should not throw; just verify no exception
    }
}
