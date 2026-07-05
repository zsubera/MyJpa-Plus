package com.zsubera.jpa.update;

import static org.junit.jupiter.api.Assertions.*;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class BulkTransactionHelperTest {

    @Test
    void safeRollback_activeTransaction_rollsBack() {
        EntityTransaction tx = Mockito.mock(EntityTransaction.class);
        Mockito.when(tx.isActive()).thenReturn(true);
        RuntimeException original = new RuntimeException("error");

        BulkTransactionHelper.safeRollback(tx, original);

        Mockito.verify(tx).rollback();
    }

    @Test
    void safeRollback_inactiveTransaction_noRollback() {
        EntityTransaction tx = Mockito.mock(EntityTransaction.class);
        Mockito.when(tx.isActive()).thenReturn(false);
        RuntimeException original = new RuntimeException("error");

        BulkTransactionHelper.safeRollback(tx, original);

        Mockito.verify(tx, Mockito.never()).rollback();
    }

    @Test
    void safeRollback_nullTransaction_noException() {
        assertDoesNotThrow(() -> BulkTransactionHelper.safeRollback(null, new RuntimeException()));
    }

    @Test
    void safeRollback_rollbackFails_addsSuppressed() {
        EntityTransaction tx = Mockito.mock(EntityTransaction.class);
        Mockito.when(tx.isActive()).thenReturn(true);
        Mockito.doThrow(new RuntimeException("rollback failed")).when(tx).rollback();
        RuntimeException original = new RuntimeException("original");

        BulkTransactionHelper.safeRollback(tx, original);

        assertEquals(1, original.getSuppressed().length);
        assertTrue(original.getSuppressed()[0].getMessage().contains("rollback failed"));
    }

    @Test
    void isJtaTransactionActive_nonJtaEnvironment_returnsFalse() {
        EntityManager em = Mockito.mock(EntityManager.class);
        Mockito.when(em.getTransaction()).thenReturn(Mockito.mock(EntityTransaction.class));

        assertFalse(BulkTransactionHelper.isJtaTransactionActive(em));
    }
}
