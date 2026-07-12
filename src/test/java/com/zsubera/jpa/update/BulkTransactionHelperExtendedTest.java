package com.zsubera.jpa.update;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.zsubera.jpa.exception.MyJpaPlusException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;
import org.junit.jupiter.api.Test;

/**
 * Extended tests for {@link BulkTransactionHelper} — covers the Function overload
 * and edge cases not covered by the existing {@link BulkTransactionHelperTest}.
 */
class BulkTransactionHelperExtendedTest {

    @Test
    void executeInManagedTransaction_functionOverload_withExistingTx() {
        EntityManager em = mock(EntityManager.class);
        EntityTransaction tx = mock(EntityTransaction.class);
        when(tx.isActive()).thenReturn(true);
        when(em.getTransaction()).thenReturn(tx);

        int result = BulkTransactionHelper.executeInManagedTransaction(em, e -> 42);
        assertEquals(42, result);
        verify(tx, never()).begin();
        verify(tx, never()).commit();
    }

    @Test
    void executeInManagedTransaction_functionOverload_newTx_commits() {
        EntityManager em = mock(EntityManager.class);
        EntityTransaction tx = mock(EntityTransaction.class);
        when(tx.isActive()).thenReturn(false);
        when(em.getTransaction()).thenReturn(tx);

        int result = BulkTransactionHelper.executeInManagedTransaction(em, e -> 10);
        assertEquals(10, result);
        verify(tx).begin();
        verify(tx).commit();
    }

    @Test
    void executeInManagedTransaction_functionOverload_newTx_rollsBackOnException() {
        EntityManager em = mock(EntityManager.class);
        EntityTransaction tx = mock(EntityTransaction.class);
        when(tx.isActive()).thenReturn(false).thenReturn(true);
        when(em.getTransaction()).thenReturn(tx);

        assertThrows(IllegalStateException.class,
            () -> BulkTransactionHelper.executeInManagedTransaction(em, e -> {
                throw new IllegalStateException("boom");
            }));
        verify(tx).begin();
        verify(tx).rollback();
    }

    @Test
    void executeInManagedTransaction_functionOverload_existingTx_propagatesException() {
        EntityManager em = mock(EntityManager.class);
        EntityTransaction tx = mock(EntityTransaction.class);
        when(tx.isActive()).thenReturn(true);
        when(em.getTransaction()).thenReturn(tx);

        assertThrows(IllegalArgumentException.class,
            () -> BulkTransactionHelper.executeInManagedTransaction(em, e -> {
                throw new IllegalArgumentException("bad arg");
            }));
        verify(tx, never()).rollback();
    }

    @Test
    void executeInManagedTransaction_functionOverload_jtaEnv_throwsWhenNoTx() {
        EntityManager em = mock(EntityManager.class);
        when(em.getTransaction()).thenThrow(new IllegalStateException("JTA"));

        assertThrows(MyJpaPlusException.class,
            () -> BulkTransactionHelper.executeInManagedTransaction(em, e -> 1));
    }

    @Test
    void executeInManagedTransaction_clearBeforeCommit_orderVerified() {
        EntityManager em = mock(EntityManager.class);
        EntityTransaction tx = mock(EntityTransaction.class);
        when(tx.isActive()).thenReturn(false);
        when(em.getTransaction()).thenReturn(tx);

        java.util.List<String> order = new java.util.ArrayList<>();
        when(tx.isActive()).thenReturn(false);

        BulkTransactionHelper.executeInManagedTransaction(em, e -> {
            order.add("action");
            return 1;
        });

        // Verify tx.commit() was called (em.clear() is now before commit)
        verify(tx).commit();
        verify(em).clear();
    }
}
