package com.zsubera.jpa.template;

import com.zsubera.jpa.update.DeleteSpec;
import com.zsubera.jpa.update.MergeSpec;
import com.zsubera.jpa.update.UpdateSpec;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.PlatformTransactionManager;

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

    /** 无限制模式下的绝对安全行数上限。即使 effectiveLimit <= 0，也不会超过此值。 */
    private static final int ABSOLUTE_MAX_BATCH_ROWS = 1_000_000;

    private volatile int maxBatchIterations = DEFAULT_MAX_BATCH_ITERATIONS;

    private final EntityManager entityManager;
    private final jakarta.persistence.EntityManagerFactory entityManagerFactory;
    private volatile int maxBulkOperationRows;

    /**
     * 创建 BulkOperationTemplate 实例。
     *
     * @param entityManager 实体管理器
     * @param maxBulkOperationRows 批量操作最大影响行数限制（-1 表示不限制）
     * @param txManager Spring 事务管理器
     */
    BulkOperationTemplate(EntityManager entityManager, int maxBulkOperationRows, PlatformTransactionManager txManager) {
        this.entityManager = entityManager;
        this.entityManagerFactory = entityManager.getEntityManagerFactory();
        this.maxBulkOperationRows = maxBulkOperationRows;
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
        try (EntityManager em = entityManagerFactory.createEntityManager()) {
            jakarta.persistence.EntityTransaction tx = em.getTransaction();
            tx.begin();
            try {
                R r = operation.apply(em);
                tx.commit();
                return r;
            } catch (RuntimeException | Error e) {
                if (tx.isActive()) {
                    tx.rollback();
                }
                throw e;
            }
        }
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
        return executeBatchWithCursor(batchSize, "update", (size, lastId) -> {
            UpdateSpec.BatchCursor cursor = spec.executeLimitedCursor(entityManager, size, lastId);
            return new Object[]{cursor.affected(), cursor.lastId()};
        });
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
        return executeBatchWithCursor(batchSize, "delete", (size, lastId) -> {
            DeleteSpec.BatchCursor cursor = spec.executeLimitedCursor(entityManager, size, lastId);
            return new Object[]{cursor.affected(), cursor.lastId()};
        });
    }

    private static boolean isWithinLimit(int total, int effectiveLimit) {
        if (effectiveLimit > 0) {
            return total < effectiveLimit;
        }
        return total < ABSOLUTE_MAX_BATCH_ROWS;
    }

    /**
     * 使用游标分页的批量执行，确保每批处理不同的行集。
     * 当 SET 子句不改变 WHERE 条件列时，避免重复处理相同行。
     */
    @FunctionalInterface
    interface CursorExecutor {
        /**
         * @param batchSize 批次大小
         * @param lastId 上一批最后的 ID（首批为 null）
         * @return Object[]{affectedRows, lastId}
         */
        Object[] execute(int batchSize, Object lastId);
    }

    private int executeBatchWithCursor(int batchSize, String operationName, CursorExecutor executor) {
        int total = 0;
        int iteration = 0;
        Object lastId = null;
        int effectiveLimit = resolveMaxBulkOperationRows();
        while (true) {
            Object[] result = executor.execute(batchSize, lastId);
            int affected = (Integer)result[0];
            if (affected == 0) {
                break;
            }
            lastId = result[1];
            total += affected;
            if (log.isDebugEnabled()) {
                log.debug("Batch {}: {} rows {}ed in this batch (total: {})", operationName, affected,
                    operationName, total);
            }
            iteration++;
            if (iteration >= maxBatchIterations) {
                log.error("Batch {} reached maximum iterations ({}). Possible infinite loop. Total rows: {}",
                    operationName, maxBatchIterations, total);
                break;
            }
            effectiveLimit = resolveMaxBulkOperationRows();
            if (!isWithinLimit(total, effectiveLimit)) {
                break;
            }
        }
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
        return executeBatchInSeparateTransactionsWithCursor(batchSize, "update", failureStrategy,
            (size, lastId) -> executeInNewTransaction(em -> {
                UpdateSpec.BatchCursor cursor = spec.executeLimitedCursor(em, size, lastId);
                return new Object[]{cursor.affected(), cursor.lastId()};
            }));
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
        return executeBatchInSeparateTransactionsWithCursor(batchSize, "delete", failureStrategy,
            (size, lastId) -> executeInNewTransaction(em -> {
                DeleteSpec.BatchCursor cursor = spec.executeLimitedCursor(em, size, lastId);
                return new Object[]{cursor.affected(), cursor.lastId()};
            }));
    }

    /**
     * 带游标的独立事务批量执行，返回详细结果。
     */
    private BatchResult executeBatchInSeparateTransactionsWithCursor(int batchSize, String operationName,
        BatchFailureStrategy failureStrategy, CursorExecutor executor) {
        int total = 0;
        int batchCount = 0;
        int failedBatchIndex = -1;
        Throwable failureCause = null;
        boolean shouldContinue = true;
        int iteration = 0;
        int consecutiveFailures = 0;
        Object lastId = null;
        while (shouldContinue) {
            batchCount++;
            try {
                Object[] result = executor.execute(batchSize, lastId);
                int batchResult = (Integer)result[0];
                lastId = result[1];
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
                } else if (!isWithinLimit(total, effectiveLimit)) {
                    log.warn("Batch {} reached safety limit ({} rows). Stopping.", operationName,
                        ABSOLUTE_MAX_BATCH_ROWS);
                    shouldContinue = false;
                }
            } catch (RuntimeException | Error e) {
                failedBatchIndex = batchCount - 1;
                failureCause = e;
                consecutiveFailures++;
                log.error("Batch {} failed at batch index {} (consecutive failures: {}): {}", operationName,
                    failedBatchIndex, consecutiveFailures, e.getMessage(), e);
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
        return executeBatchWithCursor(batchSize, "update-separate-tx", (size, lastId) -> {
            @SuppressWarnings("unchecked")
            Object[] r = (Object[])executeInNewTransaction(em -> {
                UpdateSpec.BatchCursor c = spec.executeLimitedCursor(em, size, lastId);
                return new Object[]{c.affected(), c.lastId()};
            });
            return r;
        });
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
        return executeBatchWithCursor(batchSize, "delete-separate-tx", (size, lastId) -> {
            @SuppressWarnings("unchecked")
            Object[] r = (Object[])executeInNewTransaction(em -> {
                DeleteSpec.BatchCursor c = spec.executeLimitedCursor(em, size, lastId);
                return new Object[]{c.affected(), c.lastId()};
            });
            return r;
        });
    }

    /**
     * 分批在独立事务中执行操作的通用实现。
     *
     * <p>
     * ponytail: 每批事务独立提交。批次 N 失败时，前 N-1 批已提交数据不会回滚。
     * 异常中携带已提交行数信息，调用方可据此判断部分提交状态。
     */
    private int executeBatchInSeparateTransactionsInternal(int batchSize, String operationName,
        java.util.function.IntUnaryOperator batchExecutor) {
        int total = 0;
        int batchResult;
        int iteration = 0;
        int effectiveLimit = resolveMaxBulkOperationRows();
        do {
            try {
                batchResult = batchExecutor.applyAsInt(batchSize);
            } catch (RuntimeException | Error e) {
                log.error("Batch {} failed after {} committed rows ({} iterations): {}", operationName, total,
                    iteration, e.getMessage());
                throw new BatchPartialCommitException(operationName, total, iteration, e);
            }
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
            effectiveLimit = resolveMaxBulkOperationRows();
        } while (batchResult > 0 && isWithinLimit(total, effectiveLimit));
        return total;
    }

    /**
     * 批量操作部分提交异常。当独立事务批量执行中某批失败时抛出，
     * 携带已成功提交的行数和批次信息，便于调用方了解部分提交状态。
     */
    public static class BatchPartialCommitException extends RuntimeException {
        private final String operationName;
        private final int committedRows;
        private final int completedBatches;

        public BatchPartialCommitException(String operationName, int committedRows, int completedBatches,
            Throwable cause) {
            super("Batch " + operationName + " failed after " + completedBatches + " batches committed " + committedRows
                + " rows. Remaining rows were NOT committed.", cause);
            this.operationName = operationName;
            this.committedRows = committedRows;
            this.completedBatches = completedBatches;
        }

        public String getOperationName() {
            return operationName;
        }

        public int getCommittedRows() {
            return committedRows;
        }

        public int getCompletedBatches() {
            return completedBatches;
        }
    }
}
