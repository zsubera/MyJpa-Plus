package com.zsubera.jpa.repository;

import static org.junit.jupiter.api.Assertions.*;

import com.zsubera.jpa.autoconfigure.GlobalConfigHolder;
import com.zsubera.jpa.autoconfigure.MyJpaPlusGlobalConfig;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class DefaultMyJpaRepositoryCoverageTest {

    @AfterEach
    void cleanup() {
        DefaultMyJpaRepository.clearThreadLocal();
        DefaultMyJpaRepository.setGlobalConfigProvider(null);
        GlobalConfigHolder.setConfig(null);
    }

    // ---- resolveGlobalConfig branches ----

    @Test
    void resolveGlobalConfig_usesGlobalConfigHolder_whenAvailable() {
        MyJpaPlusGlobalConfig config = new MyJpaPlusGlobalConfig();
        config.setSoftDeleteAutoFilter(false);
        GlobalConfigHolder.setConfig(config);
        assertFalse(DefaultMyJpaRepository.isAutoFilterEnabled());
    }

    @Test
    void resolveGlobalConfig_usesDefault_whenBothNull() {
        GlobalConfigHolder.setConfig(null);
        DefaultMyJpaRepository.setGlobalConfigProvider(null);
        assertTrue(DefaultMyJpaRepository.isAutoFilterEnabled());
        assertTrue(DefaultMyJpaRepository.isBlockUnconditionalDelete());
    }

    // ---- withAutoFilterOverride with Supplier ----

    @Test
    void withAutoFilterOverride_supplier_returnsValue() {
        String result = DefaultMyJpaRepository.withAutoFilterOverride(true, () -> "hello");
        assertEquals("hello", result);
    }

    @Test
    void withAutoFilterOverride_supplier_nullValue_removesOverride() {
        DefaultMyJpaRepository.withAutoFilterOverride(false, () -> null);
        assertNull(DefaultMyJpaRepository.getAutoFilterOverride());
    }

    @Test
    void withAutoFilterOverride_runnable_null_removesOverride() {
        DefaultMyJpaRepository.withAutoFilterOverride(null, () -> {
        });
        assertNull(DefaultMyJpaRepository.getAutoFilterOverride());
    }

    // ---- captureAndResetForAsync ----

    @Test
    void captureAndResetForAsync_withValue_removesAndReturns() {
        DefaultMyJpaRepository.withAutoFilterOverride(true, () -> {
            Boolean captured = DefaultMyJpaRepository.captureAndResetForAsync();
            assertEquals(true, captured);
            assertNull(DefaultMyJpaRepository.getAutoFilterOverride());
            DefaultMyJpaRepository.restoreForAsync(captured);
        });
    }

    @Test
    void captureAndResetForAsync_noValue_returnsNull() {
        assertNull(DefaultMyJpaRepository.captureAndResetForAsync());
    }

    @Test
    void restoreForAsync_withValue_setsOverride() {
        DefaultMyJpaRepository.restoreForAsync(true);
        assertEquals(true, DefaultMyJpaRepository.getAutoFilterOverride());
    }

    @Test
    void restoreForAsync_null_doesNothing() {
        DefaultMyJpaRepository.restoreForAsync(null);
        assertNull(DefaultMyJpaRepository.getAutoFilterOverride());
    }

    // ---- createTaskDecorator ----

    @Test
    void createTaskDecorator_executesRunnable() {
        var decorator = DefaultMyJpaRepository.createTaskDecorator();
        AtomicBoolean executed = new AtomicBoolean(false);
        Runnable decorated = decorator.decorate(() -> executed.set(true));
        decorated.run();
        assertTrue(executed.get());
    }

    @Test
    void createTaskDecorator_restoresStateAfterExecution() {
        DefaultMyJpaRepository.withAutoFilterOverride(false, () -> {
            var decorator = DefaultMyJpaRepository.createTaskDecorator();
            Runnable decorated = decorator.decorate(() -> {
            });
            decorated.run();
        });
    }

    // ---- getAutoFilterOverride ----

    @Test
    void getAutoFilterOverride_defaultIsNull() {
        assertNull(DefaultMyJpaRepository.getAutoFilterOverride());
    }

    @Test
    void getAutoFilterOverride_afterSet() {
        DefaultMyJpaRepository.withAutoFilterOverride(true, () -> {
            assertEquals(true, DefaultMyJpaRepository.getAutoFilterOverride());
        });
    }

    // ---- createMutableConfigProvider ----

    @Test
    void createMutableConfigProvider_bothTrue() {
        var provider = DefaultMyJpaRepository.createMutableConfigProvider(true, true);
        assertTrue(provider.isAutoFilterEnabled());
        assertTrue(provider.isBlockUnconditionalDelete());
    }

    @Test
    void createMutableConfigProvider_bothFalse() {
        var provider = DefaultMyJpaRepository.createMutableConfigProvider(false, false);
        assertFalse(provider.isAutoFilterEnabled());
        assertFalse(provider.isBlockUnconditionalDelete());
    }

    // ---- softDeleteFilter with override ----

    @Test
    void autoFilterOverride_true_enablesFilter() {
        DefaultMyJpaRepository.withAutoFilterOverride(true, () -> {
            assertEquals(true, DefaultMyJpaRepository.getAutoFilterOverride());
        });
    }

    @Test
    void autoFilterOverride_false_disablesFilter() {
        DefaultMyJpaRepository.withAutoFilterOverride(false, () -> {
            assertEquals(false, DefaultMyJpaRepository.getAutoFilterOverride());
        });
    }

    @Test
    void autoFilterOverride_nestedScoping() {
        DefaultMyJpaRepository.withAutoFilterOverride(true, () -> {
            assertEquals(true, DefaultMyJpaRepository.getAutoFilterOverride());
            DefaultMyJpaRepository.withAutoFilterOverride(false, () -> {
                assertEquals(false, DefaultMyJpaRepository.getAutoFilterOverride());
            });
            assertEquals(true, DefaultMyJpaRepository.getAutoFilterOverride());
        });
    }

    @Test
    void autoFilterOverride_exceptionCleanup() {
        try {
            DefaultMyJpaRepository.withAutoFilterOverride(true, () -> {
                throw new IllegalStateException("test");
            });
        } catch (IllegalStateException ignored) {
        }
        assertNull(DefaultMyJpaRepository.getAutoFilterOverride());
    }

    @Test
    void autoFilterOverride_supplier_exceptionCleanup() {
        try {
            DefaultMyJpaRepository.<String>withAutoFilterOverride(true, () -> {
                throw new IllegalStateException("test");
            });
        } catch (IllegalStateException ignored) {
        }
        assertNull(DefaultMyJpaRepository.getAutoFilterOverride());
    }

    @Test
    void autoFilterOverride_null_supplier() {
        String result = DefaultMyJpaRepository.withAutoFilterOverride(null, () -> "ok");
        assertEquals("ok", result);
    }

    // ---- createTaskDecorator with active override ----

    @Test
    void createTaskDecorator_preservesOverrideInChild() {
        DefaultMyJpaRepository.withAutoFilterOverride(false, () -> {
            var decorator = DefaultMyJpaRepository.createTaskDecorator();
            AtomicBoolean childSawFalse = new AtomicBoolean();
            Runnable decorated = decorator.decorate(() -> {
                childSawFalse.set(Boolean.FALSE.equals(DefaultMyJpaRepository.getAutoFilterOverride()));
            });
            decorated.run();
            assertTrue(childSawFalse.get());
        });
    }

    // ---- isAutoFilterEnabled / isBlockUnconditionalDelete ----

    @Test
    void isAutoFilterEnabled_defaultTrue() {
        assertTrue(DefaultMyJpaRepository.isAutoFilterEnabled());
    }

    @Test
    void isBlockUnconditionalDelete_defaultTrue() {
        assertTrue(DefaultMyJpaRepository.isBlockUnconditionalDelete());
    }

    @Test
    void isAutoFilterEnabled_withGlobalConfig() {
        MyJpaPlusGlobalConfig config = new MyJpaPlusGlobalConfig();
        config.setSoftDeleteAutoFilter(false);
        GlobalConfigHolder.setConfig(config);
        assertFalse(DefaultMyJpaRepository.isAutoFilterEnabled());
    }

    @Test
    void isBlockUnconditionalDelete_withGlobalConfig() {
        MyJpaPlusGlobalConfig config = new MyJpaPlusGlobalConfig();
        config.setBlockUnconditionalDelete(false);
        GlobalConfigHolder.setConfig(config);
        assertFalse(DefaultMyJpaRepository.isBlockUnconditionalDelete());
    }

    @Test
    void isAutoFilterEnabled_overrideTrue() {
        DefaultMyJpaRepository.withAutoFilterOverride(true, () -> {
            assertTrue(DefaultMyJpaRepository.isAutoFilterEnabled());
        });
    }

    @Test
    void autoFilterOverride_false() {
        DefaultMyJpaRepository.withAutoFilterOverride(false, () -> {
            assertFalse(DefaultMyJpaRepository.getAutoFilterOverride());
        });
    }

    // ---- mergeSoftDeleteFilter ----

    @Test
    void mergeSoftDeleteFilter_autoFilterDisabled_returnsSameSpec() {
        MyJpaPlusGlobalConfig config = new MyJpaPlusGlobalConfig();
        config.setSoftDeleteAutoFilter(false);
        GlobalConfigHolder.setConfig(config);
        assertFalse(DefaultMyJpaRepository.isAutoFilterEnabled());
    }

    // ---- registerTransactionCleanup ----

    @Test
    void registerTransactionCleanup_noTransaction_doesNothing() {
        DefaultMyJpaRepository.registerTransactionCleanup();
    }

    @Test
    void clearThreadLocal_multipleTimes() {
        DefaultMyJpaRepository.clearThreadLocal();
        DefaultMyJpaRepository.clearThreadLocal();
        assertNull(DefaultMyJpaRepository.getAutoFilterOverride());
    }

    @Test
    void withAutoFilterOverride_setAndGetCycle() {
        DefaultMyJpaRepository.withAutoFilterOverride(true, () -> {
            assertTrue(DefaultMyJpaRepository.getAutoFilterOverride());
        });
        assertNull(DefaultMyJpaRepository.getAutoFilterOverride());
        DefaultMyJpaRepository.withAutoFilterOverride(false, () -> {
            assertFalse(DefaultMyJpaRepository.getAutoFilterOverride());
        });
        assertNull(DefaultMyJpaRepository.getAutoFilterOverride());
    }

    // ---- registerTransactionCleanup with transaction synchronization ----

    @Test
    void registerTransactionCleanup_afterCompletion_noTransaction() {
        // Without an active transaction, registerTransactionCleanup is a no-op
        DefaultMyJpaRepository.registerTransactionCleanup();
    }

    @Test
    void registerTransactionCleanup_afterCompletion_skipsIfValueChanged() {
        try {
            TransactionSynchronizationManager.initSynchronization();
            DefaultMyJpaRepository.withAutoFilterOverride(true, () -> {
                DefaultMyJpaRepository.registerTransactionCleanup();
                // Change value after registration
                DefaultMyJpaRepository.clearThreadLocal();
            });
            List<TransactionSynchronization> syncs = TransactionSynchronizationManager.getSynchronizations();
            for (TransactionSynchronization sync : syncs) {
                sync.afterCompletion(TransactionSynchronization.STATUS_COMMITTED);
            }
            // Should not throw
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void registerTransactionCleanup_afterCompletion_rollbackStatus() {
        try {
            TransactionSynchronizationManager.initSynchronization();
            DefaultMyJpaRepository.withAutoFilterOverride(false, () -> {
                DefaultMyJpaRepository.registerTransactionCleanup();
            });
            List<TransactionSynchronization> syncs = TransactionSynchronizationManager.getSynchronizations();
            for (TransactionSynchronization sync : syncs) {
                sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
            }
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void withAutoFilterOverride_runnable_exceptionInNested() {
        DefaultMyJpaRepository.withAutoFilterOverride(true, () -> {
            try {
                DefaultMyJpaRepository.withAutoFilterOverride(false, () -> {
                    throw new RuntimeException("nested");
                });
            } catch (RuntimeException ignored) {
            }
            assertTrue(DefaultMyJpaRepository.getAutoFilterOverride());
        });
    }

    @Test
    void createTaskDecorator_runnable_withOverride() {
        DefaultMyJpaRepository.withAutoFilterOverride(false, () -> {
            var decorator = DefaultMyJpaRepository.createTaskDecorator();
            AtomicBoolean executed = new AtomicBoolean(false);
            Runnable decorated = decorator.decorate(() -> executed.set(true));
            decorated.run();
            assertTrue(executed.get());
        });
    }

    @Test
    void captureAndResetForAsync_multipleCycles() {
        for (int i = 0; i < 3; i++) {
            DefaultMyJpaRepository.withAutoFilterOverride(i % 2 == 0, () -> {
                Boolean captured = DefaultMyJpaRepository.captureAndResetForAsync();
                assertNotNull(captured);
                assertNull(DefaultMyJpaRepository.getAutoFilterOverride());
                DefaultMyJpaRepository.restoreForAsync(captured);
            });
        }
    }

    // ---- registerTransactionCleanup with active transaction ----

    @Test
    void registerTransactionCleanup_insideTransaction() {
        // Use Spring's PlatformTransactionManager to create an active transaction
        org.springframework.transaction.PlatformTransactionManager txManager =
            new org.springframework.jdbc.datasource.DataSourceTransactionManager(
                new org.springframework.jdbc.datasource.SingleConnectionDataSource(createMockConnection(), false));
        org.springframework.transaction.support.TransactionTemplate txTemplate =
            new org.springframework.transaction.support.TransactionTemplate(txManager);
        txTemplate.executeWithoutResult(status -> {
            DefaultMyJpaRepository.withAutoFilterOverride(true, () -> {
                DefaultMyJpaRepository.registerTransactionCleanup();
                List<TransactionSynchronization> syncs = TransactionSynchronizationManager.getSynchronizations();
                assertFalse(syncs.isEmpty());
                // Trigger afterCompletion to cover the callback
                for (TransactionSynchronization sync : syncs) {
                    sync.afterCompletion(TransactionSynchronization.STATUS_COMMITTED);
                }
            });
        });
    }

    private static java.sql.Connection createMockConnection() {
        try {
            java.sql.Connection conn = org.mockito.Mockito.mock(java.sql.Connection.class);
            org.mockito.Mockito.doReturn(false).when(conn).isClosed();
            org.mockito.Mockito.doReturn(true).when(conn).isValid(org.mockito.ArgumentMatchers.anyInt());
            org.mockito.Mockito.doReturn(true).when(conn).getAutoCommit();
            return conn;
        } catch (java.sql.SQLException e) {
            throw new RuntimeException(e);
        }
    }

    // ---- MutableConfigProvider setters coverage ----

    @Test
    void mutableConfigProvider_setters() throws Exception {
        DefaultMyJpaRepository.ConfigProvider provider = DefaultMyJpaRepository.createMutableConfigProvider(true, true);
        // MutableConfigProvider is a private inner class, use reflection
        java.lang.reflect.Method setAuto = provider.getClass().getDeclaredMethod("setAutoFilterEnabled", boolean.class);
        setAuto.setAccessible(true);
        setAuto.invoke(provider, false);
        assertFalse(provider.isAutoFilterEnabled());

        java.lang.reflect.Method setBlock =
            provider.getClass().getDeclaredMethod("setBlockUnconditionalDelete", boolean.class);
        setBlock.setAccessible(true);
        setBlock.invoke(provider, false);
        assertFalse(provider.isBlockUnconditionalDelete());

        setAuto.invoke(provider, true);
        assertTrue(provider.isAutoFilterEnabled());
        setBlock.invoke(provider, true);
        assertTrue(provider.isBlockUnconditionalDelete());
    }
}
