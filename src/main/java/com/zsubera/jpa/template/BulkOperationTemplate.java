package com.zsubera.jpa.template;

import com.zsubera.jpa.update.DeleteSpec;
import com.zsubera.jpa.update.MergeSpec;
import com.zsubera.jpa.update.UpdateSpec;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 批量操作执行模板，封装 {@link UpdateSpec}、{@link DeleteSpec} 和 {@link MergeSpec} 的批量执行逻辑。
 *
 * <p>
 * 从 {@link MyJpaTemplate} 中提取，将批量操作的执行、分批、事务管理等逻辑集中在此类中， 使 {@link MyJpaTemplate} 专注于查询操作。
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
    private static final int MAX_BATCH_ITERATIONS = 10000;

    private final EntityManager entityManager;
    private volatile int maxBulkOperationRows;
    private final TransactionHelper transactionHelper;

    /**
     * 创建 BulkOperationTemplate 实例。
     *
     * @param entityManager 实体管理器
     * @param maxBulkOperationRows 批量操作最大影响行数限制（-1 表示不限制）
     * @param applicationContext Spring 应用上下文（用于获取 TransactionManager）
     */
    BulkOperationTemplate(EntityManager entityManager, int maxBulkOperationRows,
        org.springframework.context.ApplicationContext applicationContext) {
        this.entityManager = entityManager;
        this.maxBulkOperationRows = maxBulkOperationRows;
        this.transactionHelper = new TransactionHelper(entityManager, null, applicationContext);
    }

    /**
     * 更新最大批量操作影响行数限制。
     *
     * @param maxBulkOperationRows 最大影响行数（-1 表示不限制）
     */
    void setMaxBulkOperationRows(int maxBulkOperationRows) {
        this.maxBulkOperationRows = maxBulkOperationRows;
    }

    // ---- 单次执行 ----

    /**
     * 使用给定的 {@link UpdateSpec} 执行批量更新。
     *
     * @param spec 要执行的 UpdateSpec
     * @param <T> 实体类型
     * @return 受影响的行数
     */
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public <T> int execute(UpdateSpec<T> spec) {
        if (spec == null) {
            throw new IllegalArgumentException("spec must not be null");
        }
        return spec.executeInTransaction(entityManager);
    }

    /**
     * 使用给定的 {@link DeleteSpec} 执行批量删除。
     *
     * @param spec 要执行的 DeleteSpec
     * @param <T> 实体类型
     * @return 受影响的行数
     */
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public <T> int execute(DeleteSpec<T> spec) {
        if (spec == null) {
            throw new IllegalArgumentException("spec must not be null");
        }
        return spec.executeInTransaction(entityManager);
    }

    /**
     * 使用给定的 {@link MergeSpec} 执行 UPSERT 操作。
     *
     * @param spec 要执行的 MergeSpec
     * @param <T> 实体类型
     * @return 受影响的行数
     */
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
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
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public <T> int executeWithMaxRows(UpdateSpec<T> spec, int maxRows) {
        if (spec == null) {
            throw new IllegalArgumentException("spec must not be null");
        }
        if (maxRows <= 0 && maxRows != -1) {
            throw new IllegalArgumentException("maxRows must be positive or -1 (use global config)");
        }
        int effectiveLimit = maxRows == -1 ? maxBulkOperationRows : maxRows;
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
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public <T> int executeWithMaxRows(DeleteSpec<T> spec, int maxRows) {
        if (spec == null) {
            throw new IllegalArgumentException("spec must not be null");
        }
        if (maxRows <= 0 && maxRows != -1) {
            throw new IllegalArgumentException("maxRows must be positive or -1 (use global config)");
        }
        int effectiveLimit = maxRows == -1 ? maxBulkOperationRows : maxRows;
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
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
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
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public <T> int executeBatch(UpdateSpec<T> spec, int batchSize) {
        if (spec == null) {
            throw new IllegalArgumentException("spec must not be null");
        }
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be positive");
        }
        return executeBatchInternal(batchSize, "update", size -> spec.executeLimited(entityManager, size));
    }

    /**
     * 分批执行批量删除。
     *
     * @param spec 要执行的 DeleteSpec
     * @param batchSize 每批删除的行数
     * @param <T> 实体类型
     * @return 受影响的总行数
     */
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public <T> int executeBatch(DeleteSpec<T> spec, int batchSize) {
        if (spec == null) {
            throw new IllegalArgumentException("spec must not be null");
        }
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be positive");
        }
        return executeBatchInternal(batchSize, "delete", size -> spec.executeLimited(entityManager, size));
    }

    /**
     * 分批执行操作的通用实现。
     */
    private int executeBatchInternal(int batchSize, String operationName,
        java.util.function.IntUnaryOperator batchExecutor) {
        int total = 0;
        int batchResult;
        int iteration = 0;
        do {
            batchResult = batchExecutor.applyAsInt(batchSize);
            total += batchResult;
            if (batchResult > 0) {
                entityManager.flush();
                entityManager.clear();
                if (log.isDebugEnabled()) {
                    log.debug("Batch {}: {} rows {}ed in this batch (total: {})", operationName, batchResult,
                        operationName, total);
                }
            }
            iteration++;
            if (iteration >= MAX_BATCH_ITERATIONS) {
                log.error("Batch {} reached maximum iterations ({}). Possible infinite loop. Total rows: {}",
                    operationName, MAX_BATCH_ITERATIONS, total);
                break;
            }
        } while (batchResult > 0);
        return total;
    }

    // ---- 独立事务分批执行 ----

    /**
     * 批量操作的执行结果记录。
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
    }

    /**
     * 批次执行失败时的处理策略。
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
            size -> transactionHelper.executeInNewTransaction(em -> spec.executeLimited(em, size)), failureStrategy);
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
            size -> transactionHelper.executeInNewTransaction(em -> spec.executeLimited(em, size)), failureStrategy);
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
        while (shouldContinue) {
            int batchResult;
            try {
                batchResult = batchExecutor.applyAsInt(batchSize);
                total += batchResult;
                batchCount++;
                if (batchResult > 0 && log.isDebugEnabled()) {
                    log.debug("Batch {} committed: {} rows {}ed in this batch (total: {})", operationName, batchResult,
                        operationName, total);
                }
            } catch (RuntimeException e) {
                failedBatchIndex = batchCount;
                failureCause = e;
                batchCount++;
                log.error("Batch {} failed at batch index {}: {}", operationName, failedBatchIndex, e.getMessage(), e);
                if (failureStrategy == BatchFailureStrategy.ABORT) {
                    shouldContinue = false;
                    continue;
                }
                // CONTINUE 模式：记录失败但不中断，继续下一批
                continue;
            }
            if (batchResult < batchSize) {
                shouldContinue = false;
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
            size -> transactionHelper.executeInNewTransaction(em -> spec.executeLimited(em, size)));
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
            size -> transactionHelper.executeInNewTransaction(em -> spec.executeLimited(em, size)));
    }

    /**
     * 分批在独立事务中执行操作的通用实现。
     */
    private int executeBatchInSeparateTransactionsInternal(int batchSize, String operationName,
        java.util.function.IntUnaryOperator batchExecutor) {
        int total = 0;
        int batchResult;
        int iteration = 0;
        do {
            batchResult = batchExecutor.applyAsInt(batchSize);
            total += batchResult;
            if (batchResult > 0 && log.isDebugEnabled()) {
                log.debug("Batch {} committed: {} rows {}ed in this batch (total: {})", operationName, batchResult,
                    operationName, total);
            }
            iteration++;
            if (iteration >= MAX_BATCH_ITERATIONS) {
                log.error("Batch {} reached maximum iterations ({}). Possible infinite loop. Total rows: {}",
                    operationName, MAX_BATCH_ITERATIONS, total);
                break;
            }
        } while (batchResult > 0);
        return total;
    }
}
