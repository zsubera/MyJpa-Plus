package com.zsubera.jpa.repository;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class DefaultMyJpaRepositoryTest {

    @AfterEach
    void cleanup() {
        DefaultMyJpaRepository.clearThreadLocal();
    }

    @Test
    void withAutoFilterOverrideTrue() {
        DefaultMyJpaRepository.withAutoFilterOverride(true, () -> {
            assertTrue(AUTO_FILTER_OVERRIDE.get());
        });
    }

    @Test
    void withAutoFilterOverrideFalse() {
        DefaultMyJpaRepository.withAutoFilterOverride(false, () -> {
            assertFalse(AUTO_FILTER_OVERRIDE.get());
        });
    }

    @Test
    void withAutoFilterOverrideRestoresPrevious() {
        DefaultMyJpaRepository.withAutoFilterOverride(false, () -> {
            assertFalse(AUTO_FILTER_OVERRIDE.get());
            DefaultMyJpaRepository.withAutoFilterOverride(true, () -> {
                assertTrue(AUTO_FILTER_OVERRIDE.get());
            });
            assertFalse(AUTO_FILTER_OVERRIDE.get());
        });
    }

    @Test
    void withAutoFilterOverrideCleansUpOnException() {
        try {
            DefaultMyJpaRepository.withAutoFilterOverride(false, () -> {
                throw new RuntimeException("boom");
            });
        } catch (RuntimeException ignored) {
        }
        assertNull(AUTO_FILTER_OVERRIDE.get());
    }

    @Test
    void withAutoFilterOverrideNullRemovesOverride() {
        DefaultMyJpaRepository.withAutoFilterOverride(false, () -> {
            assertNotNull(AUTO_FILTER_OVERRIDE.get());
        });
        assertNull(AUTO_FILTER_OVERRIDE.get());
    }

    @Test
    void captureAndResetForAsync() {
        DefaultMyJpaRepository.withAutoFilterOverride(false, () -> {
            Boolean captured = DefaultMyJpaRepository.captureAndResetForAsync();
            assertEquals(false, captured);
            assertNull(AUTO_FILTER_OVERRIDE.get());
            DefaultMyJpaRepository.restoreForAsync(captured);
            assertFalse(AUTO_FILTER_OVERRIDE.get());
        });
    }

    @Test
    void captureAndResetForAsyncNullDoesNothing() {
        Boolean captured = DefaultMyJpaRepository.captureAndResetForAsync();
        assertNull(captured);
    }

    @Test
    void restoreForAsyncNullDoesNothing() {
        DefaultMyJpaRepository.restoreForAsync(null);
        assertNull(AUTO_FILTER_OVERRIDE.get());
    }

    @Test
    void clearThreadLocal() {
        DefaultMyJpaRepository.withAutoFilterOverride(false, () -> {
            DefaultMyJpaRepository.clearThreadLocal();
            assertNull(AUTO_FILTER_OVERRIDE.get());
        });
    }

    @Test
    void createTaskDecorator() {
        org.springframework.core.task.TaskDecorator decorator = DefaultMyJpaRepository.createTaskDecorator();
        assertNotNull(decorator);
    }

    @Test
    void configProviderCreation() {
        DefaultMyJpaRepository.ConfigProvider provider =
            DefaultMyJpaRepository.createMutableConfigProvider(false, false);
        assertFalse(provider.isAutoFilterEnabled());
        assertFalse(provider.isBlockUnconditionalDelete());
    }

    @SuppressWarnings("unchecked")
    private static final ThreadLocal<Boolean> AUTO_FILTER_OVERRIDE;

    static {
        try {
            java.lang.reflect.Field f = DefaultMyJpaRepository.class.getDeclaredField("AUTO_FILTER_OVERRIDE");
            f.setAccessible(true);
            AUTO_FILTER_OVERRIDE = (ThreadLocal<Boolean>)f.get(null);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
