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
 * <strong>事务传播行为：</strong>
 * <ul>
 *   <li>无活动事务时：使用 {@code PROPAGATION_REQUIRED}（创建新事务）</li>
 *   <li>有活动事务时：使用 {@code PROPAGATION_REQUIRES_NEW}（挂起外部事务，创建独立事务）</li>
 * </ul>
 *
 * <p>

 *
 * <p>
 * <strong>重要限制：</strong>
 * <p>
 * 当在已有活动事务中调用 {@code executeInNewTransaction} 时，操作会挂起外部事务并创建独立的新事务（REQUIRES_NEW）。
 * 这意味着：
 * <ul>
 *   <li>每个批次独立提交 — 一个批次失败不会导致其他批次回滚</li>
 *   <li>外部事务在新事务提交前被挂起，新事务提交后外部事务恢复</li>
 * </ul>
 *
 * <p>
 * 如需在 {@code @Transactional} 方法外调用以获得真正的批次隔离，
 * 请使用 {@link BulkOperationTemplate#executeBatchInSeparateTransactions}。
 *
 * <p>
 * <strong>使用示例：</strong>
 * <pre>{@code
 * // 正确用法：在 @Transactional 外调用以获得批次隔离
 * @Service
 * public class UserService {
 *     private final MyJpaTemplate jpa;
 *
 *     public void batchUpdateUsers() {
 *         // 在 @Transactional 方法外调用
 *         jpa.executeBatchInSeparateTransactions(
 *             jpa.update(User.class).set(User::getStatus, "INACTIVE"),
 *             100
 *         );
 *     }
 * }
 *
 * // 错误用法：在 @Transactional 内调用无法获得批次隔离
 * @Service
 * public class UserService {
 *     private final MyJpaTemplate jpa;
 *
 *     @Transactional
 *     public void batchUpdateUsers() {
 *         // 此调用会加入现有事务，批次不会独立提交
 *         jpa.executeBatchInSeparateTransactions(
 *             jpa.update(User.class).set(User::getStatus, "INACTIVE"),
 *             100
 *         );
 *     }
 * }
 * }</pre>
 */
class TransactionHelper {

    private static final Logger log = LoggerFactory.getLogger(TransactionHelper.class);

    private final EntityManager entityManager;
    private final EntityManagerFactory entityManagerFactory;
    private final ApplicationContext applicationContext;

    private volatile TransactionTemplate cachedRequiredTemplate;
    private volatile TransactionTemplate cachedRequiresNewTemplate;

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
     * <p>

     * 而非注释中错误描述的 REQUIRED（加入现有事务）。
     *
     * <p>
     * <strong>重要：</strong>当在已有活动事务中调用时，此方法使用 {@code PROPAGATION_REQUIRES_NEW}，
     * 挂起外部事务并创建独立事务。每个批次会独立提交。
     * 但注意：在某些数据库中，外层事务持有的表锁可能与新事务的锁冲突，
     * 导致死锁。建议在 {@code @Transactional} 方法外调用以避免锁冲突。
     *
     * @param operation 要执行的操作
     * @param <R> 返回类型
     * @return 操作结果
     * @throws IllegalStateException 如果 TransactionManager 不可用
     * @throws org.springframework.transaction.UnexpectedRollbackException 如果事务被标记为回滚
     */
    <R> R executeInNewTransaction(Function<EntityManager, R> operation) {
        PlatformTransactionManager txManager = getTransactionManager();
        if (txManager == null) {
            throw new IllegalStateException(
                "PlatformTransactionManager not available. Cannot execute in new transaction. "
                    + "Ensure @Transactional support is enabled or configure a PlatformTransactionManager bean. "
                    + "If running outside a Spring context, use MergeSpec.executeInTransaction() instead.");
        }
        boolean existingTransaction = TransactionSynchronizationManager.isActualTransactionActive();

        TransactionTemplate txTemplate;
        if (existingTransaction) {

            log.warn("executeInNewTransaction called within an active transaction. "
                + "Using PROPAGATION_REQUIRES_NEW to create an independent transaction. "
                + "The outer transaction will be suspended. "
                + "Note: on some databases (e.g., H2), locks held by the outer transaction "
                + "may conflict with the new transaction, causing deadlocks. "
                + "Consider calling outside @Transactional methods to avoid lock conflicts.");
            txTemplate = getOrCreateRequiresNewTemplate(txManager);
        } else {
            txTemplate = getOrCreateRequiredTemplate(txManager);
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

    private TransactionTemplate getOrCreateRequiredTemplate(PlatformTransactionManager txManager) {
        TransactionTemplate template = cachedRequiredTemplate;
        if (template == null) {
            synchronized (this) {
                template = cachedRequiredTemplate;
                if (template == null) {
                    template = new TransactionTemplate(txManager);
                    template.setPropagationBehavior(
                        org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRED);
                    cachedRequiredTemplate = template;
                }
            }
        }
        return template;
    }

    // 导致 executeInNewTransaction 在无活动事务时无法创建新事务。
    // 改为正确的 PROPAGATION_REQUIRES_NEW（始终创建新事务）。
    private TransactionTemplate getOrCreateRequiresNewTemplate(PlatformTransactionManager txManager) {
        TransactionTemplate template = cachedRequiresNewTemplate;
        if (template == null) {
            synchronized (this) {
                template = cachedRequiresNewTemplate;
                if (template == null) {
                    template = new TransactionTemplate(txManager);
                    template.setPropagationBehavior(
                        org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
                    cachedRequiresNewTemplate = template;
                }
            }
        }
        return template;
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
