package com.zsubera.jpa.update;

import com.zsubera.jpa.exception.MyJpaPlusException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 批量操作的共享事务管理工具类。
 *
 * <p>
 * 提供统一的事务管理逻辑，被 {@link AbstractBulkOperationSpec} 和 {@link MergeSpec} 共享，
 * 消除两者之间重复的事务管理代码。
 *
 * <p>
 * <strong>事务管理策略：</strong>
 * <ul>
 * <li>如果当前存在活动事务（Spring 管理或 JTA），直接在该事务中执行</li>
 * <li>如果没有活动事务，创建新的 JPA EntityTransaction 并在完成后提交</li>
 * <li>执行过程中发生异常时，如果是新创建的事务则回滚</li>
 * </ul>
 */
final class BulkTransactionHelper {

    private static final Logger log = LoggerFactory.getLogger(BulkTransactionHelper.class);

    private BulkTransactionHelper() {}

    /**
     * 在托管事务中执行操作。如果没有活动事务，自动创建新事务。
     *
     * @param em 实体管理器
     * @param action 要执行的操作（接收 EntityManager 并返回行数）
     * @return 操作结果
     */
    static int executeInManagedTransaction(EntityManager em,
        java.util.function.Function<EntityManager, Integer> action) {
        return executeInManagedTransactionInternal(em, () -> action.apply(em));
    }

    /**
     * 在托管事务中执行操作（IntSupplier 重载，兼容无 EntityManager 参数场景）。
     *
     * @param em 实体管理器
     * @param action 要执行的操作
     * @return 操作结果
     */
    static int executeInManagedTransaction(EntityManager em, java.util.function.IntSupplier action) {
        return executeInManagedTransactionInternal(em, action);
    }

    private static int executeInManagedTransactionInternal(EntityManager em, java.util.function.IntSupplier action) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            return action.getAsInt();
        }
        EntityTransaction tx;
        try {
            tx = em.getTransaction();
        } catch (IllegalStateException e) {
            if (!TransactionSynchronizationManager.isActualTransactionActive()) {
                throw new MyJpaPlusException("JTA environment detected but no active transaction. "
                    + "Use @Transactional annotation or manually begin a transaction.", e);
            }
            return action.getAsInt();
        }
        if (tx == null) {
            if (!TransactionSynchronizationManager.isActualTransactionActive()) {
                throw new MyJpaPlusException("JTA environment detected but no active transaction. "
                    + "Use @Transactional annotation or manually begin a transaction.");
            }
            return action.getAsInt();
        }
        boolean isNewTransaction = !tx.isActive();
        if (isNewTransaction) {
            tx.begin();
        }
        try {
            int result = action.getAsInt();
            if (isNewTransaction) {
                tx.commit();
            }
            return result;
        } catch (RuntimeException | Error e) {
            if (isNewTransaction) {
                safeRollback(tx, e);
            }
            throw e;
        }
    }

    /**
     * 安全回滚事务。如果回滚失败，将回滚异常添加为原始异常的抑制异常。
     *
     * @param tx 实体事务
     * @param original 原始异常
     */
    static void safeRollback(EntityTransaction tx, Throwable original) {
        if (tx != null && tx.isActive()) {
            try {
                tx.rollback();
            } catch (Exception rollbackEx) {
                log.error("Transaction rollback failed", rollbackEx);
                original.addSuppressed(rollbackEx);
            }
        }
    }

    /**
     * 检查是否为 JTA 环境且有活动事务。
     *
     * <p>JTA 环境下 {@link EntityManager#getTransaction()} 抛出 {@link IllegalStateException}，
     * 因此通过 Spring 的 {@link TransactionSynchronizationManager} 判断。
    /**
     * 检测当前是否为 JTA 环境且有活动事务。
     *
     * <p>
     * 检测逻辑：尝试调用 {@code em.getTransaction()}，如果抛出 {@code IllegalStateException}
     * 则说明是 JTA 环境（JTA EntityManager 不支持 getTransaction()），此时委托给 Spring 的
     * {@code TransactionSynchronizationManager} 判断事务状态。
     *
     * <p>
     * 注意：在 RESOURCE_LOCAL 环境中即使有活动事务也返回 false，因为 RESOURCE_LOCAL 的
     * {@code em.getTransaction()} 不会抛异常。
     *
     * @param em 实体管理器
     * @return 如果是 JTA 环境且有活动事务返回 true
     */
    static boolean isJtaTransactionActive(EntityManager em) {
        try {
            em.getTransaction();
            return false;
        } catch (Exception e) {
            return TransactionSynchronizationManager.isActualTransactionActive();
        }
    }
}
