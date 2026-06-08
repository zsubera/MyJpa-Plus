package com.zsubera.jpa.template;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Function;

/**
 * 事务工具类，封装 {@link TransactionTemplate} 的创建和传播行为判断逻辑。
 *
 * <p>
 * 从 {@link MyJpaTemplate} 和 {@link BulkOperationTemplate} 中提取的共享事务管理逻辑，
 * 避免两个类各自维护相同的 {@code executeInNewTransaction} 实现。
 *
 * <p>
 * <strong>事务传播行为：</strong>始终使用 {@code PROPAGATION_REQUIRES_NEW}（挂起当前事务，创建新事务），
 * 确保批量操作在独立事务中执行。当已有活动事务时会记录警告日志，因为挂起外层事务可能影响外层事务的连接池行为。
 */
class TransactionHelper {

    private static final Logger log = LoggerFactory.getLogger(TransactionHelper.class);

    private final EntityManager entityManager;
    private final EntityManagerFactory entityManagerFactory;
    private final ApplicationContext applicationContext;

    TransactionHelper(EntityManager entityManager, EntityManagerFactory entityManagerFactory,
        ApplicationContext applicationContext) {
        this.entityManager = entityManager;
        this.entityManagerFactory = entityManagerFactory;
        this.applicationContext = applicationContext;
    }

    /**
     * 在新事务中执行操作。
     *
     * <p>
     * 使用 {@link TransactionTemplate} 创建独立事务，每次调用都会创建新的事务上下文。
     *
     * @param operation 要执行的操作
     * @param <R> 返回类型
     * @return 操作结果
     * @throws IllegalStateException 如果 TransactionManager 不可用
     */
    <R> R executeInNewTransaction(Function<EntityManager, R> operation) {
        PlatformTransactionManager txManager = getTransactionManager();
        if (txManager == null) {
            throw new IllegalStateException(
                "PlatformTransactionManager not available. Cannot execute in new transaction. "
                    + "Ensure @Transactional support is enabled or configure a PlatformTransactionManager bean. "
                    + "If running outside a Spring context, use MergeSpec.executeInTransaction() instead.");
        }
        TransactionTemplate txTemplate = new TransactionTemplate(txManager);
        boolean existingTransaction = TransactionSynchronizationManager.isActualTransactionActive();
        if (existingTransaction) {
            log.warn("executeInNewTransaction called within an active transaction. "
                + "Batch operations will join the existing transaction (PROPAGATION_REQUIRED). "
                + "For true per-batch commit isolation, call outside of @Transactional methods.");
            txTemplate
                .setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRED);
        } else {
            txTemplate
                .setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        }
        return txTemplate.execute(status -> {
            // Spring 的 @PersistenceContext 代理会自动为每个事务创建独立的底层 EntityManager，
            // 无需手动创建新 EM。直接使用代理即可，Spring 会通过事务同步机制确保正确的 EM 绑定。
            R r = operation.apply(entityManager);
            if (status.isRollbackOnly()) {
                log.warn("Transaction marked as rollback-only. Result will be discarded.");
                // 抛出异常让调用方感知事务回滚状态，避免基于回滚结果做出错误决策
                throw new org.springframework.transaction.UnexpectedRollbackException(
                    "Transaction was unexpectedly rolled back. "
                        + "The operation result should not be used as the data has been rolled back.");
            }
            return r;
        });
    }

    private PlatformTransactionManager getTransactionManager() {
        if (applicationContext == null) {
            log.debug("ApplicationContext not available, cannot resolve TransactionManager");
            return null;
        }
        try {
            return applicationContext.getBean(PlatformTransactionManager.class);
        } catch (org.springframework.beans.BeansException e) {
            log.debug("PlatformTransactionManager bean not found: {}", e.getMessage());
            return null;
        }
    }
}
