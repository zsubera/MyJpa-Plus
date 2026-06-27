package com.zsubera.jpa.template;

import com.zsubera.jpa.update.DeleteSpec;
import com.zsubera.jpa.update.MergeSpec;
import com.zsubera.jpa.update.UpdateSpec;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 批量操作执行模板，封装 {@link UpdateSpec}、{@link DeleteSpec} 和 {@link MergeSpec} 的批量执行逻辑。
 *
 * <p>
 * 从 {@link MyJpaTemplate} 中提取，将批量操作的执行、分批、事务管理等逻辑集中在此类中，
 * 使 {@link MyJpaTemplate} 专注于查询操作。
 *
 * <p>
 * <strong>⚠️ 警告：此类不是 Spring Bean（由 {@link MyJpaTemplate} 手动实例化），
 * 因此 {@code @Transactional} 注解在本类的任何方法上均不会生效 —— Spring AOP 代理无法介入非 Bean 对象。
 * <strong>绝对不要</strong>在此类的方法上添加 {@code @Transactional} 注解，它不会起到任何作用。
 *
 * <p>
 * 事务管理职责归属于调用方 {@link MyJpaTemplate}，其方法通过 {@code @Transactional} 注解由 Spring 管理事务。
 * 如需在事务内开启独立的新事务（例如分批提交以避免大事务锁表），
 * 请使用 {@link TransactionTemplate} 配合 {@code REQUIRES_NEW} 传播行为创建独立事务。
 *
 * <p>
 * <strong>功能：</strong>
 * <ul>
 * <li>单次执行：{@link #execute(UpdateSpec)}、{@link #execute(DeleteSpec)}、{@link #execute(MergeSpec)}</li>
 * <li>限制行数执行：{@link #executeWithMaxRows(UpdateSpec, int)}、{@link #executeWithMaxRows(DeleteSpec, int)}</li>
 * <li>分批执行：{@link #executeBatch(UpdateSpec, int)}、{@link #executeBatch(DeleteSpec, int)}</li>
 * <li>独立事务分批执行：{@link #executeBatchInSeparateTransactions(UpdateSpec, int)} 等</li>
 * </ul>
 *
 * @see MyJpaTemplate
 */
class BulkOperationTemplate {

    private static final Logger log = LoggerFactory.getLogger(BulkOperationTemplate.class);

    /** 批量执行最大迭代次数保护，防止无限循环。 */
    private static final int DEFAULT_MAX_BATCH_ITERATIONS = 10000;

    private volatile int maxBatchIterations = DEFAULT_MAX_BATCH_ITERATIONS;

    private final EntityManager entityManager;
    private final jakarta.persistence.EntityManagerFactory entityManagerFactory;
    private volatile int maxBulkOperationRows;
    private final TransactionTemplate requiredTxTemplate;
    private final TransactionTemplate requiresNewTxTemplate;

    /**
     * 创建 BulkOperationTemplate 实例。
     *
     * @param entityManager 实体管理器
     * @param maxBulkOperationRows 批量操作最大影响行数限制（-1 表示不限制）
     * @param txManager Spring 事务管理器
     */
    BulkOperationTemplate(EntityManager entityManager, int maxBulkOperationRows,
        PlatformTransactionManager txManager) {
        this.entityManager = entityManager;
        this.entityManagerFactory = entityManager.getEntityManagerFactory();
        this.maxBulkOperationRows = maxBulkOperationRows;
        this.requiredTxTemplate = new TransactionTemplate(txManager);
        this.requiredTxTemplate.setPropagationBehavior(
            org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRED);
        this.requiresNewTxTemplate = new TransactionTemplate(txManager);
        this.requiresNewTxTemplate.setPropagationBehavior(
            org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * 获取当前生效的最大批量操作行数。优先从 GlobalConfigHolder 读取，
     * 保证运行时配置变更立即生效。
     */
    private int resolveMaxBulkOperationRows() {
        return com.zsubera.jpa.autoconfigure.GlobalConfigHolder.resolveMaxBulkOperationRows(maxBulkOperationRows);
    }

    /**
     * ponytail: 在新事务中使用 EMF 创建新 EM，而非复用构造时捕获的代理 EM，
     * 避免 REQUIRES_NEW 事务中使用外层 persistence context 的 EM。
     */
    private <R> R executeInNewTransaction(java.util.function.Function<EntityManager, R> operation) {
        TransactionTemplate template = TransactionSynchronizationManager.isActualTransactionActive()
            ? requiresNewTxTemplate : requiredTxTemplate;
        return template.execute(status -> {
            EntityManager em = entityManagerFactory.createEntityManager();
            try {
                R r = operation.apply(em);
                if (status.isRollbackOnly()) {
                    throw new org.springframework.transaction.UnexpectedRollbackException(
                        "Transaction was unexpectedly rolled back.");
                }
                return r;
            } finally {
                em.close();
            }
        });
    }

    /**
     * 更新最大批量操作影响行数限制。
     *
     * @param maxBulkOperationRows 最大影响行数（-1 表示不限制）
     */
    void setMaxBulkOperationRows(int maxBulkOperationRows) {
        this.maxBulkOperationRows = maxBulkOperationRows;
    }

    void setMaxBatchIterations(int maxBatchIterations) {
        if (maxBatchIterations <= 0) {
            throw new IllegalArgumentException("maxBatchIterations must be positive");
        }
        this.maxBatchIterations = maxBatchIterations;
    }

    // ---- 单次执行 ----

    /**
     * 使用给定的 {@link UpdateSpec} 执行批量更新。
     *
     * <p>
     * 事务由调用方（{@link MyJpaTemplate}）的 {@code @Transactional} 注解管理。
     *
     * @param spec 要执行的 UpdateSpec
     * @param <T> 实体类型
     * @return 受影响的行数
     */
    public <T> int execute(UpdateSpec<T> spec) {
        if (spec == null) {
            throw new IllegalArgumentException("spec must not be null");
        }
        return spec.executeInTransaction(entityManager);
    }

    /**
     * 使用给定的 {@link DeleteSpec} 执行批量删除。
     *
     * <p>
     * 事务由调用方（{@link MyJpaTemplate}）的 {@code @Transactional} 注解管理。
     *
     * @param spec 要执行的 DeleteSpec
     * @param <T> 实体类型
     * @return 受影响的行数
     */
    public <T> int execute(DeleteSpec<T> spec) {
        if (spec == null) {
            throw new IllegalArgumentException("spec must not be null");
        }
        return spec.executeInTransaction(entityManager);
    }

    /**
     * 使用给定的 {@link MergeSpec} 执行 UPSERT 操作。
     *
     * <p>
     * 事务由调用方（{@link MyJpaTemplate}）的 {@code @Transactional} 注解管理。
     *
     * @param spec 要执行的 MergeSpec
     * @param <T> 实体类型
     * @return 受影响的行数
     */
    public <T> int execute(MergeSpec<T> spec) {
        if (spec == null) {
            throw new IllegalArgumentException("spec must not be null");
        }
        return spec.executeInTransaction(entityManager);
    }

    // ---- 限制行数执行 ----

    /**
     * 使用给定的 {@link UpdateSpec} 执行批量更新，限制最大影响行数。
     *
     * @param spec 要执行的 UpdateSpec
     * @param maxRows 最大影响行数，如果为 -1 则使用全局配置
     * @param <T> 实体类型
     * @return 受影响的行数
     */
    public <T> int executeWithMaxRows(UpdateSpec<T> spec, int maxRows) {
        if (spec == null) {
            throw new IllegalArgumentException("spec must not be null");
        }
        if (maxRows <= 0 && maxRows != -1) {
            throw new IllegalArgumentException("maxRows must be positive or -1 (use global config)");
        }
        int effectiveLimit = maxRows == -1 ? resolveMaxBulkOperationRows() : maxRows;
        if (effectiveLimit <= 0) {
            return spec.executeInTransaction(entityManager);
        }
        return spec.executeLimited(entityManager, effectiveLimit);
    }

    /**
     * 使用给定的 {@link DeleteSpec} 执行批量删除，限制最大影响行数。
     *
     * @param spec 要执行的 DeleteSpec
     * @param maxRows 最大影响行数，如果为 -1 则使用全局配置
     * @param <T> 实体类型
     * @return 受影响的行数
     */
    public <T> int executeWithMaxRows(DeleteSpec<T> spec, int maxRows) {
        if (spec == null) {
            throw new IllegalArgumentException("spec must not be null");
        }
        if (maxRows <= 0 && maxRows != -1) {
            throw new IllegalArgumentException("maxRows must be positive or -1 (use global config)");
        }
        int effectiveLimit = maxRows == -1 ? resolveMaxBulkOperationRows() : maxRows;
        if (effectiveLimit <= 0) {
            return spec.executeInTransaction(entityManager);
        }
        return spec.executeLimited(entityManager, effectiveLimit);
    }

    // ---- MergeSpec 批量执行 ----

    /**
     * 批量执行 UPSERT 操作，使用 EntityManager flush/clear 进行分批处理。
     *
     * @param mergeSpec MergeSpec 实例
     * @param entities 要 UPSERT 的实体列表
     * @param batchSize 每批大小
     * @param <T> 实体类型
     * @return 受影响的总行数
     */
    public <T> int executeBatch(MergeSpec<T> mergeSpec, java.util.List<T> entities, int batchSize) {
        if (mergeSpec == null) {
            throw new IllegalArgumentException("mergeSpec must not be null");
        }
        if (entities == null || entities.isEmpty()) {
            throw new IllegalArgumentException("entities must not be null or empty");
        }
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be positive");
        }
        return mergeSpec.executeBatch(entities, entityManager, batchSize);
    }

    // ---- 分批执行（同一事务） ----

    /**
     * 分批执行批量更新。
     *
     * @param spec 要执行的 UpdateSpec
     * @param batchSize 每批更新的行数
     * @param <T> 实体类型
     * @return 受影响的总行数
     */
    public <T> int executeBatch(UpdateSpec<T> spec, int batchSize) {
        if (spec == null) {
            throw new IllegalArgumentException("spec must not be null");
        }
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be positive");
        }
        return executeBatchInternal(batchSize, "update", size -> spec.executeLimited(entityManager, size), true);
    }

    /**
     * 分批执行批量删除。
     *
     * @param spec 要执行的 DeleteSpec
     * @param batchSize 每批删除的行数
     * @param <T> 实体类型
     * @return 受影响的总行数
     */
    public <T> int executeBatch(DeleteSpec<T> spec, int batchSize) {
        if (spec == null) {
            throw new IllegalArgumentException("spec must not be null");
        }
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be positive");
        }
        return executeBatchInternal(batchSize, "delete", size -> spec.executeLimited(entityManager, size), true);
    }

    /**
     * 分批执行操作的通用实现。
     *
     * <p>
     * <strong>副作用：</strong>当 {@code clearContext=true} 时，每批执行后调用
     * {@code entityManager.flush()} + {@code entityManager.clear()}。
     * {@code em.clear()} 会分离当前事务中<strong>所有</strong>托管实体，包括调用方在同一事务中持有的其他实体。
     * 调用方应在批次执行完成后重新查询需要的实体。
     *
     * @param batchSize 每批大小
     * @param operationName 操作名称（用于日志）
     * @param batchExecutor 批次执行器
     * @param clearContext 是否在每批后清除持久化上下文
     * @return 受影响的总行数
     */
    private int executeBatchInternal(int batchSize, String operationName,
        java.util.function.IntUnaryOperator batchExecutor, boolean clearContext) {
        int effectiveLimit = resolveMaxBulkOperationRows();
        int total = 0;
        int batchResult;
        int iteration = 0;
        do {
            batchResult = batchExecutor.applyAsInt(batchSize);
            total += batchResult;
            if (batchResult > 0) {
                entityManager.flush();
                if (clearContext) {
                    entityManager.clear();
                }
                if (log.isDebugEnabled()) {
                    log.debug("Batch {}: {} rows {}ed in this batch (total: {})", operationName, batchResult,
                        operationName, total);
                }
            }
            iteration++;
            if (iteration >= maxBatchIterations) {
                log.error("Batch {} reached maximum iterations ({}). Possible infinite loop. Total rows: {}",
                    operationName, maxBatchIterations, total);
                break;
            }
        } while (batchResult > 0 && (effectiveLimit <= 0 || total < effectiveLimit));
        return total;
    }

    // ---- 独立事务分批执行 ----

    /**
     * 批量操作的执行结果记录。复用 {@link MyJpaTemplateOperations.BatchResult} 以避免类型重复。
     *
     * @param totalRows 受影响的总行数
     * @param batchCount 执行的批次数
     * @param success 是否全部成功
     * @param failedBatchIndex 失败的批次索引（从 0 开始），如果全部成功则为 -1
     * @param failureCause 失败原因，如果全部成功则为 null
     */
    @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(value = {"EI_EXPOSE_REP", "EI_EXPOSE_REP2"},
        justification = "Record components are inherently exposed; failureCause is intentionally part of the result")
    public record BatchResult(int totalRows, int batchCount, boolean success, int failedBatchIndex,
        Throwable failureCause) {
        /**
         * 转换为公开 API 的 {@link MyJpaTemplateOperations.BatchResult}。
         */
        public MyJpaTemplateOperations.BatchResult toPublicResult() {
            return new MyJpaTemplateOperations.BatchResult(totalRows, batchCount, success, failedBatchIndex,
                failureCause);
        }
    }

    /**
     * 批次执行失败时的处理策略。复用 {@link MyJpaTemplateOperations.BatchFailureStrategy} 以避免类型重复。
     */
    public enum BatchFailureStrategy {
        /** 继续执行剩余批次（默认）。 */
        CONTINUE,
        /** 立即中止，已提交的批次不会回滚。 */
        ABORT,
    }

    /**
     * 分批执行批量更新，每批在独立事务中提交，支持失败回调。
     *
     * @param spec 要执行的 UpdateSpec
     * @param batchSize 每批更新的行数
     * @param failureStrategy 失败时的处理策略
     * @param <T> 实体类型
     * @return 批量执行结果
     */
    public <T> BatchResult executeBatchInSeparateTransactions(UpdateSpec<T> spec, int batchSize,
        BatchFailureStrategy failureStrategy) {
        if (spec == null) {
            throw new IllegalArgumentException("spec must not be null");
        }
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be positive");
        }
        if (failureStrategy == null) {
            throw new IllegalArgumentException("failureStrategy must not be null");
        }
        return executeBatchInSeparateTransactionsWithResult(batchSize, "update",
            size -> executeInNewTransaction(em -> spec.executeLimited(em, size)), failureStrategy);
    }

    /**
     * 分批执行批量删除，每批在独立事务中提交，支持失败回调。
     *
     * @param spec 要执行的 DeleteSpec
     * @param batchSize 每批删除的行数
     * @param failureStrategy 失败时的处理策略
     * @param <T> 实体类型
     * @return 批量执行结果
     */
    public <T> BatchResult executeBatchInSeparateTransactions(DeleteSpec<T> spec, int batchSize,
        BatchFailureStrategy failureStrategy) {
        if (spec == null) {
            throw new IllegalArgumentException("spec must not be null");
        }
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be positive");
        }
        if (failureStrategy == null) {
            throw new IllegalArgumentException("failureStrategy must not be null");
        }
        return executeBatchInSeparateTransactionsWithResult(batchSize, "delete",
            size -> executeInNewTransaction(em -> spec.executeLimited(em, size)), failureStrategy);
    }

    /**
     * 分批在独立事务中执行操作的通用实现，返回详细结果。
     */
    private BatchResult executeBatchInSeparateTransactionsWithResult(int batchSize, String operationName,
        java.util.function.IntUnaryOperator batchExecutor, BatchFailureStrategy failureStrategy) {
        int total = 0;
        int batchCount = 0;
        int failedBatchIndex = -1;
        Throwable failureCause = null;
        boolean shouldContinue = true;
        int iteration = 0;
        int consecutiveFailures = 0;
        while (shouldContinue) {
            int batchResult;
            batchCount++;
            try {
                batchResult = batchExecutor.applyAsInt(batchSize);
                total += batchResult;
                consecutiveFailures = 0;
                if (batchResult > 0 && log.isDebugEnabled()) {
                    log.debug("Batch {} committed: {} rows {}ed in this batch (total: {})", operationName, batchResult,
                        operationName, total);
                }
                if (batchResult == 0) {
                    shouldContinue = false;
                }
                int effectiveLimit = resolveMaxBulkOperationRows();
                if (effectiveLimit > 0 && total >= effectiveLimit) {
                    shouldContinue = false;
                }
            } catch (RuntimeException e) {
                failedBatchIndex = batchCount;
                failureCause = e;
                consecutiveFailures++;
                log.error("Batch {} failed at batch index {} (consecutive failures: {}): {}",
                    operationName, failedBatchIndex, consecutiveFailures, e.getMessage(), e);
                if (failureStrategy == BatchFailureStrategy.ABORT || consecutiveFailures >= 3) {
                    shouldContinue = false;
                }
            }
            iteration++;
            if (iteration >= maxBatchIterations) {
                log.error("Batch {} reached maximum iterations ({}). Possible infinite loop. Total rows: {}",
                    operationName, maxBatchIterations, total);
                break;
            }
        }
        return new BatchResult(total, batchCount, failedBatchIndex == -1, failedBatchIndex, failureCause);
    }

    /**
     * 分批执行批量更新，每批在独立事务中提交。
     *
     * @param spec 要执行的 UpdateSpec
     * @param batchSize 每批更新的行数
     * @param <T> 实体类型
     * @return 受影响的总行数
     */
    public <T> int executeBatchInSeparateTransactions(UpdateSpec<T> spec, int batchSize) {
        if (spec == null) {
            throw new IllegalArgumentException("spec must not be null");
        }
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be positive");
        }
        return executeBatchInSeparateTransactionsInternal(batchSize, "update",
            size -> executeInNewTransaction(em -> spec.executeLimited(em, size)));
    }

    /**
     * 分批执行批量删除，每批在独立事务中提交。
     *
     * @param spec 要执行的 DeleteSpec
     * @param batchSize 每批删除的行数
     * @param <T> 实体类型
     * @return 受影响的总行数
     */
    public <T> int executeBatchInSeparateTransactions(DeleteSpec<T> spec, int batchSize) {
        if (spec == null) {
            throw new IllegalArgumentException("spec must not be null");
        }
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be positive");
        }
        return executeBatchInSeparateTransactionsInternal(batchSize, "delete",
            size -> executeInNewTransaction(em -> spec.executeLimited(em, size)));
    }

    /**
     * 分批在独立事务中执行操作的通用实现。
     */
    private int executeBatchInSeparateTransactionsInternal(int batchSize, String operationName,
        java.util.function.IntUnaryOperator batchExecutor) {
        int total = 0;
        int batchResult;
        int iteration = 0;
        int effectiveLimit = resolveMaxBulkOperationRows();
        do {
            batchResult = batchExecutor.applyAsInt(batchSize);
            total += batchResult;
            if (batchResult > 0 && log.isDebugEnabled()) {
                log.debug("Batch {} committed: {} rows {}ed in this batch (total: {})", operationName, batchResult,
                    operationName, total);
            }
            iteration++;
            if (iteration >= maxBatchIterations) {
                log.error("Batch {} reached maximum iterations ({}). Possible infinite loop. Total rows: {}",
                    operationName, maxBatchIterations, total);
                break;
            }
        } while (batchResult > 0 && (effectiveLimit <= 0 || total < effectiveLimit));
        return total;
    }
}
