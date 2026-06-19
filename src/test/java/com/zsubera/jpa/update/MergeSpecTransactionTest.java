package com.zsubera.jpa.update;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.zsubera.jpa.spec.TestEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;
import jakarta.persistence.Query;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class MergeSpecTransactionTest {

    private MergeSpec<TestEntity> createSpec() {
        return new MergeSpec<>(TestEntity.class).onConflict(TestEntity::getName);
    }

    private int invokeExecuteInManagedTransaction(EntityManager em, java.util.function.IntSupplier action)
        throws Exception {
        var method = MergeSpec.class.getDeclaredMethod("executeInManagedTransaction", EntityManager.class,
            java.util.function.IntSupplier.class);
        method.setAccessible(true);
        try {
            return (int)method.invoke(createSpec(), em, action);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException rte) {
                throw rte;
            }
            throw e;
        }
    }

    private void invokeSafeRollback(MergeSpec<TestEntity> spec, EntityTransaction tx, Exception original)
        throws Exception {
        var method = MergeSpec.class.getDeclaredMethod("safeRollback", EntityTransaction.class, Exception.class);
        method.setAccessible(true);
        try {
            method.invoke(spec, tx, original);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException rte) {
                throw rte;
            }
            throw e;
        }
    }

    // ===== executeInManagedTransaction: new tx → begin/commit =====

    @Test
    void testExecuteInManagedTransactionNewTx() throws Exception {
        EntityManager em = mock(EntityManager.class);
        EntityTransaction tx = mock(EntityTransaction.class);
        when(em.getTransaction()).thenReturn(tx);
        when(tx.isActive()).thenReturn(false);

        int result = invokeExecuteInManagedTransaction(em, () -> 7);
        assertEquals(7, result);
        verify(tx).begin();
        verify(tx).commit();
    }

    // ===== executeInManagedTransaction: existing tx → no begin/commit =====

    @Test
    void testExecuteInManagedTransactionExistingTx() throws Exception {
        EntityManager em = mock(EntityManager.class);
        EntityTransaction tx = mock(EntityTransaction.class);
        when(em.getTransaction()).thenReturn(tx);
        when(tx.isActive()).thenReturn(true);

        int result = invokeExecuteInManagedTransaction(em, () -> 99);
        assertEquals(99, result);
        verify(tx, never()).begin();
        verify(tx, never()).commit();
    }

    // ===== executeInManagedTransaction: RuntimeException → rollback =====

    @Test
    void testExecuteInManagedTransactionRuntimeException() throws Exception {
        EntityManager em = mock(EntityManager.class);
        EntityTransaction tx = mock(EntityTransaction.class);
        when(em.getTransaction()).thenReturn(tx);
        when(tx.isActive()).thenReturn(false, true);

        assertThrows(RuntimeException.class, () -> invokeExecuteInManagedTransaction(em, () -> {
            throw new RuntimeException("boom");
        }));
        verify(tx).rollback();
    }

    // ===== executeInManagedTransaction: rollback fails → suppressed =====

    @Test
    void testExecuteInManagedTransactionRollbackFails() throws Exception {
        EntityManager em = mock(EntityManager.class);
        EntityTransaction tx = mock(EntityTransaction.class);
        when(em.getTransaction()).thenReturn(tx);
        when(tx.isActive()).thenReturn(false, true);
        doThrow(new RuntimeException("rollback failed")).when(tx).rollback();

        RuntimeException ex = assertThrows(RuntimeException.class, () -> invokeExecuteInManagedTransaction(em, () -> {
            throw new RuntimeException("boom");
        }));
        assertEquals("boom", ex.getMessage());
    }

    // ===== isJtaTransactionActive =====

    @Test
    void testIsJtaTransactionActiveTrue() throws Exception {
        EntityManager em = mock(EntityManager.class);
        EntityTransaction tx = mock(EntityTransaction.class);
        when(em.getTransaction()).thenReturn(tx);
        when(tx.isActive()).thenReturn(true);

        var method = MergeSpec.class.getDeclaredMethod("isJtaTransactionActive", EntityManager.class);
        method.setAccessible(true);
        boolean result = (boolean)method.invoke(null, em);
        assertTrue(result);
    }

    @Test
    void testIsJtaTransactionActiveFalse() throws Exception {
        EntityManager em = mock(EntityManager.class);
        EntityTransaction tx = mock(EntityTransaction.class);
        when(em.getTransaction()).thenReturn(tx);
        when(tx.isActive()).thenReturn(false);

        var method = MergeSpec.class.getDeclaredMethod("isJtaTransactionActive", EntityManager.class);
        method.setAccessible(true);
        boolean result = (boolean)method.invoke(null, em);
        assertFalse(result);
    }

    @Test
    void testIsJtaTransactionActiveExceptionReturnsFalse() throws Exception {
        EntityManager em = mock(EntityManager.class);
        EntityTransaction tx = mock(EntityTransaction.class);
        when(em.getTransaction()).thenReturn(tx);
        when(tx.isActive()).thenThrow(new RuntimeException("jta error"));

        var method = MergeSpec.class.getDeclaredMethod("isJtaTransactionActive", EntityManager.class);
        method.setAccessible(true);
        boolean result = (boolean)method.invoke(null, em);
        assertFalse(result);
    }

    // ===== safeRollback =====

    @Test
    void testSafeRollbackTxActive() throws Exception {
        MergeSpec<TestEntity> spec = createSpec();
        EntityTransaction tx = mock(EntityTransaction.class);
        when(tx.isActive()).thenReturn(true);

        invokeSafeRollback(spec, tx, new RuntimeException("original"));
        verify(tx).rollback();
    }

    @Test
    void testSafeRollbackTxNotActive() throws Exception {
        MergeSpec<TestEntity> spec = createSpec();
        EntityTransaction tx = mock(EntityTransaction.class);
        when(tx.isActive()).thenReturn(false);

        invokeSafeRollback(spec, tx, new RuntimeException("original"));
        verify(tx, never()).rollback();
    }

    @Test
    void testSafeRollbackRollbackFails() throws Exception {
        MergeSpec<TestEntity> spec = createSpec();
        EntityTransaction tx = mock(EntityTransaction.class);
        when(tx.isActive()).thenReturn(true);
        doThrow(new RuntimeException("rollback failed")).when(tx).rollback();

        RuntimeException original = new RuntimeException("original");
        assertDoesNotThrow(() -> invokeSafeRollback(spec, tx, original));
        assertEquals(1, original.getSuppressed().length);
    }

    // ===== executeBatchInSeparateTransactions =====

    @Test
    void testExecuteBatchInSeparateTransactionsHappyPath() throws Exception {
        EntityManager em = mock(EntityManager.class);
        EntityTransaction tx = mock(EntityTransaction.class);
        Query mockQuery = mock(Query.class);
        when(em.getTransaction()).thenReturn(tx);
        when(tx.isActive()).thenReturn(false);
        when(em.createNativeQuery(anyString())).thenReturn(mockQuery);
        when(mockQuery.executeUpdate()).thenReturn(1);

        MergeSpec<TestEntity> spec = createSpec().dialect(new MysqlDialect());
        TestEntity e1 = new TestEntity();
        e1.setName("a");
        e1.setStatus(1);

        int result = spec.executeBatchInSeparateTransactions(List.of(e1), em, 100);
        verify(tx, atLeastOnce()).begin();
        verify(tx, atLeastOnce()).commit();
    }

    @Test
    void testExecuteBatchInSeparateTransactionsNullEntityThrows() throws Exception {
        EntityManager em = mock(EntityManager.class);
        EntityTransaction tx = mock(EntityTransaction.class);
        Query mockQuery = mock(Query.class);
        when(em.getTransaction()).thenReturn(tx);
        when(tx.isActive()).thenReturn(false);
        when(em.createNativeQuery(anyString())).thenReturn(mockQuery);

        MergeSpec<TestEntity> spec = createSpec().dialect(new MysqlDialect());
        List<TestEntity> entities = new ArrayList<>();
        entities.add(new TestEntity());
        entities.add(null);

        assertThrows(Exception.class, () -> spec.executeBatchInSeparateTransactions(entities, em, 1));
    }

    // ===== executeBatchInSeparateTransactions with rollback =====

    @Test
    void testExecuteBatchInSeparateTransactionsRollbackOnException() throws Exception {
        EntityManager em = mock(EntityManager.class);
        EntityTransaction tx = mock(EntityTransaction.class);
        when(em.getTransaction()).thenReturn(tx);
        when(tx.isActive()).thenReturn(false, true);
        when(em.createNativeQuery(anyString())).thenThrow(new RuntimeException("sql error"));

        MergeSpec<TestEntity> spec = createSpec().dialect(new MysqlDialect());
        TestEntity e1 = new TestEntity();
        e1.setName("a");

        assertThrows(RuntimeException.class, () -> spec.executeBatchInSeparateTransactions(List.of(e1), em, 100));
        verify(tx).rollback();
    }
}
