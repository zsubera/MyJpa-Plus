package com.zsubera.jpa.update;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.zsubera.jpa.exception.MyJpaPlusException;
import com.zsubera.jpa.spec.TestEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;
import java.lang.reflect.InvocationTargetException;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class AbstractBulkOperationSpecTransactionTest {

    private UpdateSpec<TestEntity> createSpec() {
        return new UpdateSpec<>(TestEntity.class).set(TestEntity::getName, "x");
    }

    private int callExecuteInTransaction(EntityManager em, Function<EntityManager, Integer> op) throws Exception {
        var method = AbstractBulkOperationSpec.class.getDeclaredMethod("executeInTransaction", EntityManager.class,
            Function.class);
        method.setAccessible(true);
        try {
            return (int)method.invoke(createSpec(), em, op);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException rte) {
                throw rte;
            }
            throw e;
        }
    }

    // ===== JTA 环境（tx=null）无活动事务 =====

    @Test
    void testExecuteInTransactionJtaEnvironment() throws Exception {
        EntityManager em = mock(EntityManager.class);
        when(em.getTransaction()).thenReturn(null);

        assertThrows(MyJpaPlusException.class, () -> callExecuteInTransaction(em, e -> 42));
    }

    // ===== JTA 环境 + TransactionRequiredException =====

    @Test
    void testExecuteInTransactionJtaTransactionRequired() throws Exception {
        EntityManager em = mock(EntityManager.class);
        when(em.getTransaction()).thenReturn(null);

        assertThrows(Exception.class, () -> callExecuteInTransaction(em, e -> {
            throw new jakarta.persistence.TransactionRequiredException("no tx");
        }));
    }

    // ===== EntityTransaction 成功路径（新事务） =====

    @Test
    void testExecuteInTransactionEntityTransactionSuccess() throws Exception {
        EntityManager em = mock(EntityManager.class);
        EntityTransaction tx = mock(EntityTransaction.class);
        when(em.getTransaction()).thenReturn(tx);
        when(tx.isActive()).thenReturn(false, false);

        int result = callExecuteInTransaction(em, e -> 7);
        assertEquals(7, result);
        verify(tx).begin();
        verify(tx).commit();
    }

    // ===== EntityTransaction：已有活动事务 =====

    @Test
    void testExecuteInTransactionEntityTransactionAlreadyActive() throws Exception {
        EntityManager em = mock(EntityManager.class);
        EntityTransaction tx = mock(EntityTransaction.class);
        when(em.getTransaction()).thenReturn(tx);
        when(tx.isActive()).thenReturn(true);

        int result = callExecuteInTransaction(em, e -> 99);
        assertEquals(99, result);
        verify(tx, never()).begin();
        verify(tx, never()).commit();
    }

    // ===== EntityTransaction：RuntimeException 回滚 =====

    @Test
    void testExecuteInTransactionEntityTransactionRuntimeException() throws Exception {
        EntityManager em = mock(EntityManager.class);
        EntityTransaction tx = mock(EntityTransaction.class);
        when(em.getTransaction()).thenReturn(tx);
        when(tx.isActive()).thenReturn(false, true);

        assertThrows(IllegalStateException.class, () -> callExecuteInTransaction(em, e -> {
            throw new IllegalStateException("boom");
        }));
        verify(tx).begin();
        verify(tx).rollback();
        verify(tx, never()).commit();
    }

    // ===== rollbackIfActive：rollback 本身抛异常 → suppressed =====

    @Test
    void testExecuteInTransactionRollbackFails() throws Exception {
        EntityManager em = mock(EntityManager.class);
        EntityTransaction tx = mock(EntityTransaction.class);
        when(em.getTransaction()).thenReturn(tx);
        when(tx.isActive()).thenReturn(false, true);
        doThrow(new RuntimeException("rollback failed")).when(tx).rollback();

        assertThrows(IllegalStateException.class, () -> callExecuteInTransaction(em, e -> {
            throw new IllegalStateException("boom");
        }));
        verify(tx).rollback();
    }

    // ===== rollbackIfActive：tx 非 active 时不调用 rollback =====

    @Test
    void testExecuteInTransactionTxNotActiveSkipsRollback() throws Exception {
        EntityManager em = mock(EntityManager.class);
        EntityTransaction tx = mock(EntityTransaction.class);
        when(em.getTransaction()).thenReturn(tx);
        when(tx.isActive()).thenReturn(false, false);

        assertThrows(IllegalStateException.class, () -> callExecuteInTransaction(em, e -> {
            throw new IllegalStateException("boom");
        }));
        verify(tx, never()).rollback();
    }
}
