package com.zsubera.jpa.softdelete;

import com.zsubera.jpa.annotation.SoftDelete;
import com.zsubera.jpa.autoconfigure.GlobalConfigHolder;
import com.zsubera.jpa.exception.MyJpaPlusException;
import com.zsubera.jpa.update.AuditUtils;
import jakarta.persistence.EntityManager;
import java.lang.reflect.Field;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;

/**
 * 批量软删除操作的执行器。从 {@link SoftDeleteHelper} 中提取，聚焦于
 * 通过原生 SQL / CriteriaUpdate 执行批量软删除的职责。
 *
 * <p>
 * 所有方法均要求活动事务。
 *
 * <p>
 * 线程安全：所有状态为不可变或线程安全（volatile 发布）。
 */
public final class SoftDeleteBulkExecutor {

    private static final Logger log = LoggerFactory.getLogger(SoftDeleteBulkExecutor.class);

    static final int DEFAULT_MAX_ROWS = 10000;

    record VersionFieldInfo(String columnName, Field field) {
    }

    @FunctionalInterface
    public interface EventPublisher {
        void publish(Class<?> entityClass, int affectedRows);
    }

    private static volatile EventPublisher eventPublisher;

    static EventPublisher getEventPublisher() {
        return eventPublisher;
    }

    public static void setEventPublisher(EventPublisher publisher) {
        eventPublisher = publisher;
    }

    public static void publishEvent(Class<?> entityClass, int affectedRows) {
        EventPublisher publisher = eventPublisher;
        if (publisher != null && affectedRows > 0) {
            try {
                publisher.publish(entityClass, affectedRows);
            } catch (Exception e) {
                log.debug("Failed to publish entity modified event for {}: {}", entityClass.getSimpleName(),
                    e.getMessage());
            }
        }
    }

    private SoftDeleteBulkExecutor() {}

    private record ExecContext(String fieldName, Field field, SoftDelete annotation,
        SoftDeleteHelper.ResolvedDeletedValue resolved) {
    }

    private static ExecContext resolveExecContext(Class<?> entityClass) {
        String fieldName = SoftDeleteHelper.findSoftDeleteField(entityClass);
        if (fieldName == null)
            throw new IllegalArgumentException("Entity " + entityClass.getSimpleName() + " has no @SoftDelete field");
        Field field = SoftDeleteHelper.getField(entityClass, fieldName);
        if (field == null)
            throw new IllegalArgumentException("Cannot resolve @SoftDelete field: " + fieldName);
        SoftDelete annotation = field.getAnnotation(SoftDelete.class);
        SoftDeleteHelper.ResolvedDeletedValue resolved =
            SoftDeleteHelper.resolveDeletedValue(entityClass, field, annotation);
        return new ExecContext(fieldName, field, annotation, resolved);
    }

    /**
     * 检查指定方言是否支持 UPDATE ... LIMIT 语法。
     * 仅 MySQL 支持此语法，可在 UPDATE 语句中直接限制影响行数，
     * 消除预检查 COUNT 与 UPDATE 之间的竞态条件。
     * PostgreSQL 不支持 UPDATE ... LIMIT，会回退到 COUNT + 后置检查模式。
     */
    private static boolean supportsUpdateLimit(String dialect) {
        return "mysql".equals(dialect);
    }

    /**
     * 使用 UPDATE ... LIMIT 执行软删除，原子性地限制影响行数。
     *
     * <p>仅 MySQL 支持 {@code UPDATE ... SET ... WHERE ... LIMIT :limit} 语法。
     * PostgreSQL、Oracle、SQL Server 不支持此语法，会回退到 COUNT + 后置检查模式。
     * 数据库引擎在单条语句内同时完成"筛选行"和"更新行"，
     * 不存在其他事务在此期间修改行数的竞态窗口。
     *
     * <p>当 updated == maxRows 时，执行额外的 COUNT 查询判断是否还有更多行需要更新，
     * 如果有则抛出 IllegalStateException，引导用户使用更安全的批量操作方式。
     *
     * @return 实际更新的行数（<= maxRows）
     */
    private static int executeSoftDeleteWithLimit(EntityManager em, String escapedTable, String setClause,
        String whereClause, Object deletedValue, int maxRows, String dialect) {
        String sql = "UPDATE " + escapedTable + " SET " + setClause + " WHERE " + whereClause + " LIMIT :limit";
        validateGeneratedSql(sql, "softDeleteWithLimit UPDATE");
        jakarta.persistence.Query query = em.createNativeQuery(sql);
        query.setParameter("deletedValue", deletedValue);
        query.setParameter("limit", maxRows);
        int updated = query.executeUpdate();
        // 当 updated >= maxRows 时，可能还有更多行未被更新，需要额外 COUNT 判断。
        // 即使 UPDATE LIMIT 已原子性地限制了本次更新行数，仍需检查是否还有更多待处理行，
        // 以便在超过限制时抛出异常，引导用户使用更安全的批量操作方式。
        if (updated >= maxRows) {
            long remaining =
                ((Number)em.createNativeQuery("SELECT COUNT(*) FROM " + escapedTable + " WHERE " + whereClause)
                    .setParameter("deletedValue", deletedValue).getSingleResult()).longValue();
            if (remaining > 0) {
                throw new IllegalStateException("softDeleteAll partially completed: affected " + updated + " rows, but "
                    + remaining + " more rows still need soft-deleting. Total would exceed the limit of " + maxRows
                    + ". Use softDeleteByIds() with explicit ID lists, or increase the limit.");
            }
        }
        return updated;
    }

    private static void publishAfterUpdate(EntityManager em, Class<?> entityClass, int updated) {
        if (updated > 0) {
            com.zsubera.jpa.util.CacheEvictionHelper.evictEntityCache(em, entityClass);
            publishEvent(entityClass, updated);
        }
    }

    // ponytail: 两个 validate 方法消除重复的 null/事务检查
    private static void requireActiveTransaction() {
        if (!org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())
            throw new MyJpaPlusException("Operation requires an active transaction. "
                + "Ensure the calling method is annotated with @Transactional.");
    }

    /**
     * 尝试回滚当前事务。支持 RESOURCE_LOCAL 和 JTA 环境。
     *
     * <p>RESOURCE_LOCAL 环境下使用 {@code em.getTransaction().rollback()}，
     * JTA 环境下使用 {@code TransactionAspectSupport.currentTransactionStatus().setRollbackOnly()}。
     *
     * @return true 如果回滚成功（或标记为 rollback-only），false 如果无法执行回滚
     */
    private static boolean rollbackCurrentTransaction(EntityManager em) {
        // 尝试 RESOURCE_LOCAL rollback（最可靠的方式）
        jakarta.persistence.EntityTransaction tx;
        try {
            tx = em.getTransaction();
        } catch (IllegalStateException ignored) {
            // JTA 环境：getTransaction() 抛出 IllegalStateException，跳过本地事务回滚
            tx = null;
        }
        if (tx != null) {
            boolean active;
            try {
                active = tx.isActive();
            } catch (IllegalStateException e) {
                // JTA 环境：isActive() 不受支持，仍尝试回滚
                active = true;
            }
            if (active) {
                try {
                    tx.rollback();
                    return true;
                } catch (Exception rollbackEx) {
                    log.error("Transaction rollback failed", rollbackEx);
                }
            }
        }
        // 尝试 Spring TransactionAspectSupport（适用于 Spring 管理的事务）
        try {
            org.springframework.transaction.interceptor.TransactionAspectSupport.currentTransactionStatus()
                .setRollbackOnly();
            return true;
        } catch (Exception e) {
            log.debug("Failed to set rollback-only via TransactionAspectSupport: {}", e.getMessage());
        }
        log.error("CRITICAL: Unable to rollback transaction. "
            + "The UPDATE has been executed but the transaction may not roll back. "
            + "Ensure the calling @Transactional method has proper rollback configuration. "
            + "Data corruption risk: the UPDATE may be committed at transaction commit time.");
        return false;
    }

    private static void requireNonNull(Object obj, String name) {
        if (obj == null)
            throw new IllegalArgumentException(name + " must not be null");
    }

    public static <T> int softDeleteAll(EntityManager em, Class<T> entityClass, boolean allowUnconditional) {
        int maxRows = GlobalConfigHolder.resolveMaxBulkOperationRows(DEFAULT_MAX_ROWS);
        return softDeleteAll(em, entityClass, allowUnconditional, maxRows);
    }

    public static <T> int softDeleteAll(EntityManager em, Class<T> entityClass, boolean allowUnconditional,
        int maxRows) {
        requireNonNull(em, "em");
        requireNonNull(entityClass, "entityClass");
        if (!allowUnconditional)
            throw new IllegalStateException(
                "softDeleteAll without conditions is dangerous. Pass allowUnconditional=true to confirm.");
        requireActiveTransaction();
        if (log.isWarnEnabled())
            log.warn("AUDIT: Executing unconditional soft DELETE on {} — this will affect ALL rows! Call stack: {}",
                entityClass.getSimpleName(), AuditUtils.getCallStack());

        ExecContext ctx = resolveExecContext(entityClass);
        VersionFieldInfo versionInfo = resolveVersionFieldInfo(entityClass);
        if (versionInfo != null)
            log.warn("AUDIT: Entity {} has @Version field. softDeleteAll() bypasses optimistic lock checking. "
                + "Consider using softDeleteByIds() with specific IDs or UpdateSpec.withVersionIncrement(true) for safe concurrent updates.",
                entityClass.getSimpleName());

        String dialect = SoftDeleteHelper.detectDialect(em);
        if (log.isDebugEnabled()) {
            log.debug("Detected dialect: {}, entity: {}", dialect, entityClass.getSimpleName());
        }
        String escapedTable = SoftDeleteHelper.quoteIdentifier(
            SoftDeleteHelper.validateIdentifier(SoftDeleteHelper.resolveTableName(entityClass)), dialect);
        String escapedColumn = SoftDeleteHelper.quoteIdentifier(
            SoftDeleteHelper.validateIdentifier(SoftDeleteHelper.resolveColumnName(entityClass, ctx.fieldName())),
            dialect);
        String timestampColumn = resolveTimestampColumn(entityClass, ctx.annotation(), dialect);
        String versionColumn =
            versionInfo != null ? SoftDeleteHelper.quoteIdentifier(versionInfo.columnName, dialect) : null;

        String setClause = escapedColumn + " = :deletedValue"
            + (timestampColumn != null ? ", " + timestampColumn + " = CURRENT_TIMESTAMP" : "")
            + (versionColumn != null ? ", " + versionColumn + " = " + versionColumn + " + 1" : "");

        int updated;
        Object deletedValue = ctx.resolved().booleanField() ? Boolean.TRUE : ctx.resolved().dbValue();
        String whereClause = escapedColumn + " != :deletedValue OR " + escapedColumn + " IS NULL";

        if (maxRows > 0 && supportsUpdateLimit(dialect)) {
            // ponytail: 对支持 UPDATE LIMIT 的数据库（仅 MySQL），直接在 UPDATE 语句中限制行数。
            // 这消除了预检查 COUNT 与 UPDATE 之间的竞态条件窗口——数据库引擎在单条语句内
            // 同时完成"筛选行"和"更新行"，不存在其他事务在此期间修改行数的可能。
            updated =
                executeSoftDeleteWithLimit(em, escapedTable, setClause, whereClause, deletedValue, maxRows, dialect);
        } else {
            // ponytail: 对不支持 UPDATE LIMIT 的数据库（Oracle、SQL Server），使用预检查 COUNT + 后置验证模式。
            // 预检查与 UPDATE 之间存在竞态窗口，后置检查 + 回滚作为安全网。
            if (maxRows > 0) {
                long count;
                if (ctx.resolved().booleanField()) {
                    count =
                        ((Number)em.createNativeQuery("SELECT COUNT(*) FROM " + escapedTable + " WHERE " + whereClause)
                            .setParameter("deletedValue", Boolean.TRUE).getSingleResult()).longValue();
                } else {
                    count =
                        ((Number)em.createNativeQuery("SELECT COUNT(*) FROM " + escapedTable + " WHERE " + whereClause)
                            .setParameter("deletedValue", deletedValue).getSingleResult()).longValue();
                }
                if (count > maxRows) {
                    throw new IllegalStateException(
                        "softDeleteAll would affect " + count + " rows, exceeding the limit of " + maxRows
                            + ". Use softDeleteByIds() with explicit ID lists, or increase the limit.");
                }
            }
            String updateSql = "UPDATE " + escapedTable + " SET " + setClause + " WHERE " + whereClause;
            validateGeneratedSql(updateSql, "softDeleteAll UPDATE");
            updated = em.createNativeQuery(updateSql).setParameter("deletedValue", deletedValue).executeUpdate();
            if (maxRows > 0 && updated > maxRows) {
                if (log.isWarnEnabled()) {
                    log.warn("softDeleteAll affected {} rows, exceeding the pre-check limit of {}. "
                        + "Concurrent modifications detected.", updated, maxRows);
                }
                boolean rolledBack = rollbackCurrentTransaction(em);
                String rollbackStatus = rolledBack ? "Transaction has been rolled back."
                    : "CRITICAL: Rollback FAILED. The UPDATE may be committed. Data corruption risk.";
                throw new MyJpaPlusException(
                    "softDeleteAll affected " + updated + " rows, exceeding the pre-check limit of " + maxRows
                        + ". Concurrent modifications detected. " + rollbackStatus);
            }
        }

        publishAfterUpdate(em, entityClass, updated);
        return updated;
    }

    /**
     * 按 ID 列表执行软删除，支持可选的乐观锁版本检查。
     *
     * <p>当提供 expectedVersion 时，WHERE 子句会包含 {@code version = :expectedVersion} 条件，
     * 确保只有版本匹配的行才会被更新。如果 0 行受影响（版本不匹配），抛出 OptimisticLockException。
     *
     * <p>当 expectedVersion 为 null 时，行为与 {@link #softDeleteByIds(EntityManager, Class, List)} 相同，
     * 不进行版本检查。
     *
     * @param em 实体管理器
     * @param entityClass 实体类
     * @param ids 要软删除的 ID 列表
     * @param expectedVersion 期望的版本号，null 表示不检查版本
     * @return 受影响的行数
     * @throws jakarta.persistence.OptimisticLockException 如果版本不匹配
     */
    public static <T, ID> int softDeleteByIdsWithVersionCheck(EntityManager em, Class<T> entityClass, List<ID> ids,
        Object expectedVersion) {
        if (expectedVersion == null) {
            return softDeleteByIds(em, entityClass, ids);
        }
        requireNonNull(em, "em");
        requireNonNull(entityClass, "entityClass");
        if (ids == null || ids.isEmpty())
            return 0;
        // ponytail: Native SQL WHERE id IN (...) cannot represent composite keys (@IdClass/@EmbeddedId).
        // No Criteria API version-checked path exists, so throw explicitly.
        if (com.zsubera.jpa.util.EntityClassResolver.hasCompositeKey(entityClass)) {
            throw new UnsupportedOperationException(
                "softDeleteByIdsWithVersionCheck does not support composite key entities ("
                    + entityClass.getSimpleName()
                    + "). Use softDeleteByIds() without version check, or implement a custom version-checked Criteria API path.");
        }
        for (int i = 0; i < ids.size(); i++) {
            if (ids.get(i) == null) {
                throw new IllegalArgumentException("ids[" + i + "] must not be null");
            }
        }
        requireActiveTransaction();
        int hardLimit = com.zsubera.jpa.util.InClauseBuilder.getHardLimit();
        if (ids.size() > hardLimit)
            throw new IllegalArgumentException("ID list size (" + ids.size() + ") exceeds the hard limit (" + hardLimit
                + "). " + "Consider processing in smaller batches or using a temporary table.");

        ExecContext ctx = resolveExecContext(entityClass);
        VersionFieldInfo versionInfo = resolveVersionFieldInfo(entityClass);
        if (versionInfo == null) {
            throw new IllegalArgumentException("Entity " + entityClass.getSimpleName()
                + " does not have a @Version field. Cannot perform version-checked soft delete.");
        }
        String dialect = SoftDeleteHelper.detectDialect(em);
        String escapedTable = SoftDeleteHelper.quoteIdentifier(
            SoftDeleteHelper.validateIdentifier(SoftDeleteHelper.resolveTableName(entityClass)), dialect);
        String escapedColumn = SoftDeleteHelper.quoteIdentifier(
            SoftDeleteHelper.validateIdentifier(SoftDeleteHelper.resolveColumnName(entityClass, ctx.fieldName())),
            dialect);
        String escapedIdColumn = SoftDeleteHelper.quoteIdentifier(
            SoftDeleteHelper.validateIdentifier(SoftDeleteHelper.resolveIdColumnName(entityClass)), dialect);
        String escapedVersionColumn =
            SoftDeleteHelper.quoteIdentifier(SoftDeleteHelper.validateIdentifier(versionInfo.columnName()), dialect);
        String timestampColumn = resolveTimestampColumn(entityClass, ctx.annotation(), dialect);
        String setClause = escapedColumn + " = :deletedValue"
            + (timestampColumn != null ? ", " + timestampColumn + " = CURRENT_TIMESTAMP" : "") + ", "
            + escapedVersionColumn + " = " + escapedVersionColumn + " + 1";
        String whereClause =
            ctx.resolved().booleanField() ? "(" + escapedColumn + " = FALSE OR " + escapedColumn + " IS NULL)"
                : "(" + escapedColumn + " != :deletedValue OR " + escapedColumn + " IS NULL)";
        int batchSize = com.zsubera.jpa.util.InClauseBuilder.getMaxInClauseSize();
        int total = 0;
        Object deletedValue = ctx.resolved().booleanField() ? Boolean.TRUE : ctx.resolved().dbValue();
        for (int i = 0; i < ids.size(); i += batchSize) {
            List<ID> batch = ids.subList(i, Math.min(i + batchSize, ids.size()));
            StringBuilder placeholders = new StringBuilder();
            for (int j = 0; j < batch.size(); j++) {
                if (j > 0)
                    placeholders.append(", ");
                placeholders.append(":id").append(j);
            }
            String batchSql = "UPDATE " + escapedTable + " SET " + setClause + " WHERE " + escapedIdColumn + " IN ("
                + placeholders + ") AND " + whereClause + " AND " + escapedVersionColumn + " = :expectedVersion";
            validateGeneratedSql(batchSql, "softDeleteByIdsWithVersionCheck batch UPDATE");
            var query = em.createNativeQuery(batchSql);
            query.setParameter("deletedValue", deletedValue);
            query.setParameter("expectedVersion", expectedVersion);
            for (int j = 0; j < batch.size(); j++)
                query.setParameter("id" + j, batch.get(j));
            total += query.executeUpdate();
        }
        if (total == 0 && !ids.isEmpty()) {
            throw new jakarta.persistence.OptimisticLockException("Soft delete returned 0 affected rows for "
                + ids.size() + " IDs. Possible causes: entities already soft-deleted, or version mismatch (expected: "
                + expectedVersion + ").");
        }
        if (total > 0 && total < ids.size()) {
            log.warn(
                "Soft delete with version check: only {}/{} rows affected (expectedVersion={}). "
                    + "Some entities may have been modified by another transaction.",
                total, ids.size(), expectedVersion);
        }
        publishAfterUpdate(em, entityClass, total);
        return total;
    }

    public static <T, ID> int softDeleteByIds(EntityManager em, Class<T> entityClass, List<ID> ids) {
        requireNonNull(em, "em");
        requireNonNull(entityClass, "entityClass");
        if (ids == null || ids.isEmpty())
            return 0;
        for (int i = 0; i < ids.size(); i++) {
            if (ids.get(i) == null) {
                throw new IllegalArgumentException("ids[" + i + "] must not be null");
            }
        }
        // ponytail: Native SQL WHERE id IN (...) cannot represent composite keys (@IdClass/@EmbeddedId).
        // Fall back to Criteria API path which correctly handles composite key comparison.
        if (com.zsubera.jpa.util.EntityClassResolver.hasCompositeKey(entityClass)) {
            return softDeleteByIdsUsingEntityManager(em, entityClass, ids);
        }
        requireActiveTransaction();
        int hardLimit = com.zsubera.jpa.util.InClauseBuilder.getHardLimit();
        if (ids.size() > hardLimit)
            throw new IllegalArgumentException("ID list size (" + ids.size() + ") exceeds the hard limit (" + hardLimit
                + "). " + "Consider processing in smaller batches or using a temporary table.");

        ExecContext ctx = resolveExecContext(entityClass);
        String dialect = SoftDeleteHelper.detectDialect(em);
        String escapedTable = SoftDeleteHelper.quoteIdentifier(
            SoftDeleteHelper.validateIdentifier(SoftDeleteHelper.resolveTableName(entityClass)), dialect);
        String escapedColumn = SoftDeleteHelper.quoteIdentifier(
            SoftDeleteHelper.validateIdentifier(SoftDeleteHelper.resolveColumnName(entityClass, ctx.fieldName())),
            dialect);
        String escapedIdColumn = SoftDeleteHelper.quoteIdentifier(
            SoftDeleteHelper.validateIdentifier(SoftDeleteHelper.resolveIdColumnName(entityClass)), dialect);
        String timestampColumn = resolveTimestampColumn(entityClass, ctx.annotation(), dialect);
        String versionColumn = resolveVersionColumn(entityClass, dialect);
        String setClause = escapedColumn + " = :deletedValue"
            + (timestampColumn != null ? ", " + timestampColumn + " = CURRENT_TIMESTAMP" : "")
            + (versionColumn != null ? ", " + versionColumn + " = " + versionColumn + " + 1" : "");
        String whereClause =
            ctx.resolved().booleanField() ? "(" + escapedColumn + " = FALSE OR " + escapedColumn + " IS NULL)"
                : "(" + escapedColumn + " != :deletedValue OR " + escapedColumn + " IS NULL)";
        int batchSize = com.zsubera.jpa.util.InClauseBuilder.getMaxInClauseSize();
        int total = 0;
        Object deletedValue = ctx.resolved().booleanField() ? Boolean.TRUE : ctx.resolved().dbValue();
        for (int i = 0; i < ids.size(); i += batchSize) {
            List<ID> batch = ids.subList(i, Math.min(i + batchSize, ids.size()));
            StringBuilder placeholders = new StringBuilder();
            for (int j = 0; j < batch.size(); j++) {
                if (j > 0)
                    placeholders.append(", ");
                placeholders.append(":id").append(j);
            }
            String batchSql = "UPDATE " + escapedTable + " SET " + setClause + " WHERE " + escapedIdColumn + " IN ("
                + placeholders + ") AND " + whereClause;
            validateGeneratedSql(batchSql, "softDeleteByIds batch UPDATE");
            var query = em.createNativeQuery(batchSql);
            query.setParameter("deletedValue", deletedValue);
            for (int j = 0; j < batch.size(); j++)
                query.setParameter("id" + j, batch.get(j));
            total += query.executeUpdate();
        }
        publishAfterUpdate(em, entityClass, total);
        return total;
    }

    public static <T> int softDeleteAllUsingCriteriaUpdate(EntityManager em, Class<T> entityClass,
        boolean allowUnconditional) {
        int maxRows = GlobalConfigHolder.resolveMaxBulkOperationRows(DEFAULT_MAX_ROWS);
        return softDeleteAllUsingCriteriaUpdate(em, entityClass, allowUnconditional, maxRows);
    }

    public static <T> int softDeleteAllUsingCriteriaUpdate(EntityManager em, Class<T> entityClass,
        boolean allowUnconditional, int maxRows) {
        requireNonNull(em, "em");
        requireNonNull(entityClass, "entityClass");
        if (!allowUnconditional)
            throw new IllegalStateException(
                "softDeleteAllUsingCriteriaUpdate without conditions is dangerous. Pass allowUnconditional=true to confirm.");
        requireActiveTransaction();
        if (log.isWarnEnabled())
            log.warn("AUDIT: Executing CriteriaUpdate soft DELETE on {} — this will affect ALL rows! Call stack: {}",
                entityClass.getSimpleName(), AuditUtils.getCallStack());

        ExecContext ctx = resolveExecContext(entityClass);
        VersionFieldInfo versionInfo = resolveVersionFieldInfo(entityClass);
        if (versionInfo != null)
            log.warn(
                "AUDIT: Entity {} has @Version field. softDeleteAllUsingCriteriaUpdate() bypasses optimistic lock checking. "
                    + "Consider using softDeleteByIdsUsingEntityManager() with specific IDs.",
                entityClass.getSimpleName());
        jakarta.persistence.criteria.CriteriaBuilder cb = em.getCriteriaBuilder();
        jakarta.persistence.criteria.CriteriaUpdate<T> update = cb.createCriteriaUpdate(entityClass);
        jakarta.persistence.criteria.Root<T> root = update.from(entityClass);
        if (ctx.resolved().booleanField()) {
            update.set(ctx.fieldName(), Boolean.TRUE);
            update.where(cb.or(cb.isNull(root.get(ctx.fieldName())), cb.equal(root.get(ctx.fieldName()), false)));
        } else {
            update.set(ctx.fieldName(), ctx.resolved().dbValue());
            update.where(cb.or(cb.isNull(root.get(ctx.fieldName())),
                cb.notEqual(root.get(ctx.fieldName()), ctx.resolved().dbValue())));
        }
        Field timestampField = resolveTimestampField(entityClass, ctx.annotation());
        if (timestampField != null)
            update.set(ctx.annotation().deletedTimestampField(), cb.currentTimestamp());
        if (versionInfo != null)
            update.set(versionInfo.field().getName(), cb.sum(root.get(versionInfo.field().getName()), 1));

        int updated;
        if (maxRows > 0) {
            // ponytail: 先执行 COUNT 预检查，确保 count <= maxRows 后再执行修改。
            // 这保留了原有的"超限即抛异常"语义，防止意外的大量行被修改。
            // 预检查与后续 UPDATE 之间存在竞态窗口，但对于 CriteriaUpdate 路径
            // （不支持 UPDATE LIMIT），这是唯一可行的方案。
            jakarta.persistence.criteria.CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
            jakarta.persistence.criteria.Root<T> countRoot = countQuery.from(entityClass);
            countQuery.select(cb.count(countRoot));
            if (ctx.resolved().booleanField()) {
                countQuery.where(
                    cb.or(cb.isNull(countRoot.get(ctx.fieldName())), cb.equal(countRoot.get(ctx.fieldName()), false)));
            } else {
                countQuery.where(cb.or(cb.isNull(countRoot.get(ctx.fieldName())),
                    cb.notEqual(countRoot.get(ctx.fieldName()), ctx.resolved().dbValue())));
            }
            long count = em.createQuery(countQuery).getSingleResult();
            if (count > maxRows) {
                throw new IllegalStateException(
                    "softDeleteAllUsingCriteriaUpdate would affect " + count + " rows, exceeding the limit of "
                        + maxRows + ". Consider using softDeleteByIdsUsingEntityManager() with explicit ID lists.");
            }

            // ponytail: 使用 SELECT ... FOR UPDATE 获取 ID 列表（带悲观锁），然后按 ID 更新。
            // 悲观锁持有至事务结束，消除了 COUNT/SELECT 与 UPDATE 之间的竞态条件窗口。
            // JPA CriteriaUpdate 不支持 setLockMode()，因此无法直接对 UPDATE 加锁，
            // 但通过先锁定 ID 再按 ID 更新，确保了操作的原子性。
            //
            // 锁定范围：一次锁定最多 maxRows 行（默认 10000）。高并发场景下可能产生锁争用。
            // 建议：并发写入频繁的表使用较小的 maxRows 值，或通过分批调用 softDeleteByIds() 控制锁粒度。
            String idFieldName = com.zsubera.jpa.util.EntityClassResolver.resolveIdFieldName(entityClass);
            jakarta.persistence.criteria.CriteriaQuery<?> idQuery = cb.createQuery();
            jakarta.persistence.criteria.Root<T> idRoot = idQuery.from(entityClass);
            idQuery.select(idRoot.get(idFieldName));
            if (ctx.resolved().booleanField()) {
                idQuery
                    .where(cb.or(cb.isNull(idRoot.get(ctx.fieldName())), cb.equal(idRoot.get(ctx.fieldName()), false)));
            } else {
                idQuery.where(cb.or(cb.isNull(idRoot.get(ctx.fieldName())),
                    cb.notEqual(idRoot.get(ctx.fieldName()), ctx.resolved().dbValue())));
            }
            idQuery.orderBy(cb.asc(idRoot.get(idFieldName)));
            jakarta.persistence.TypedQuery<?> lockQuery = em.createQuery(idQuery);
            lockQuery.setMaxResults(maxRows);
            lockQuery.setLockMode(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
            java.util.List<?> ids = lockQuery.getResultList();
            if (ids.isEmpty()) {
                return 0;
            }
            // 按锁定的 ID 更新，确保 UPDATE 只影响已锁定的行
            jakarta.persistence.criteria.CriteriaUpdate<T> boundedUpdate = cb.createCriteriaUpdate(entityClass);
            jakarta.persistence.criteria.Root<T> boundedRoot = boundedUpdate.from(entityClass);
            if (ctx.resolved().booleanField()) {
                boundedUpdate.set(ctx.fieldName(), Boolean.TRUE);
            } else {
                boundedUpdate.set(ctx.fieldName(), ctx.resolved().dbValue());
            }
            if (timestampField != null)
                boundedUpdate.set(ctx.annotation().deletedTimestampField(), cb.currentTimestamp());
            if (versionInfo != null)
                boundedUpdate.set(versionInfo.field().getName(),
                    cb.sum(boundedRoot.get(versionInfo.field().getName()), 1));
            boundedUpdate.where(boundedRoot.get(idFieldName).in(ids));
            updated = em.createQuery(boundedUpdate).executeUpdate();
        } else {
            updated = em.createQuery(update).executeUpdate();
        }
        publishAfterUpdate(em, entityClass, updated);
        return updated;
    }

    public static <T, ID> int softDeleteByIdsUsingEntityManager(EntityManager em, Class<T> entityClass, List<ID> ids) {
        requireNonNull(em, "em");
        requireNonNull(entityClass, "entityClass");
        if (ids == null || ids.isEmpty())
            return 0;
        for (int i = 0; i < ids.size(); i++) {
            if (ids.get(i) == null) {
                throw new IllegalArgumentException("ids[" + i + "] must not be null");
            }
        }
        requireActiveTransaction();
        int hardLimit = com.zsubera.jpa.util.InClauseBuilder.getHardLimit();
        if (ids.size() > hardLimit)
            throw new IllegalArgumentException("ID list size (" + ids.size() + ") exceeds the hard limit (" + hardLimit
                + "). " + "Consider processing in smaller batches or using a temporary table.");
        // ponytail: @IdClass entities cannot use single-field IN clause in Criteria API.
        // root.get(idFieldName) returns only the first @Id field, but batch contains @IdClass
        // objects — type mismatch causes runtime exception. Throw explicitly.
        if (entityClass.getAnnotation(jakarta.persistence.IdClass.class) != null) {
            throw new UnsupportedOperationException(
                "softDeleteByIdsUsingEntityManager does not support @IdClass composite key entities ("
                    + entityClass.getSimpleName()
                    + "). Use softDeleteAll() or implement a custom per-entity soft-delete path.");
        }

        ExecContext ctx = resolveExecContext(entityClass);
        jakarta.persistence.criteria.CriteriaBuilder cb = em.getCriteriaBuilder();
        Field timestampField = resolveTimestampField(entityClass, ctx.annotation());
        Field versionField = resolveVersionField(entityClass);
        int batchSize = com.zsubera.jpa.util.InClauseBuilder.getMaxInClauseSize();
        String idFieldName = com.zsubera.jpa.util.EntityClassResolver.resolveIdFieldName(entityClass);
        int total = 0;
        for (int i = 0; i < ids.size(); i += batchSize) {
            List<ID> batch = ids.subList(i, Math.min(i + batchSize, ids.size()));
            jakarta.persistence.criteria.CriteriaUpdate<T> batchUpdate = cb.createCriteriaUpdate(entityClass);
            jakarta.persistence.criteria.Root<T> batchRoot = batchUpdate.from(entityClass);
            if (ctx.resolved().booleanField())
                batchUpdate.set(ctx.fieldName(), Boolean.TRUE);
            else
                batchUpdate.set(ctx.fieldName(), ctx.resolved().dbValue());
            if (timestampField != null)
                batchUpdate.set(ctx.annotation().deletedTimestampField(), cb.currentTimestamp());
            if (versionField != null)
                batchUpdate.set(versionField.getName(), cb.sum(batchRoot.get(versionField.getName()), 1));
            jakarta.persistence.criteria.Predicate idPredicate = batchRoot.get(idFieldName).in(batch);
            jakarta.persistence.criteria.Predicate notDeletedPredicate;
            if (ctx.resolved().booleanField()) {
                notDeletedPredicate =
                    cb.or(cb.isNull(batchRoot.get(ctx.fieldName())), cb.equal(batchRoot.get(ctx.fieldName()), false));
            } else {
                notDeletedPredicate = cb.or(cb.isNull(batchRoot.get(ctx.fieldName())),
                    cb.notEqual(batchRoot.get(ctx.fieldName()), ctx.resolved().dbValue()));
            }
            batchUpdate.where(cb.and(idPredicate, notDeletedPredicate));
            total += em.createQuery(batchUpdate).executeUpdate();
        }
        publishAfterUpdate(em, entityClass, total);
        return total;
    }

    static String resolveTimestampColumn(Class<?> entityClass, SoftDelete annotation, String dialect) {
        if (annotation == null || annotation.deletedTimestampField().isEmpty())
            return null;
        // 检查字段是否存在，避免生成引用不存在列的 SQL
        if (SoftDeleteHelper.getField(entityClass, annotation.deletedTimestampField()) == null) {
            log.warn("SoftDelete deletedTimestampField '{}' not found in {}. Ignoring timestamp in native SQL.",
                annotation.deletedTimestampField(), entityClass.getName());
            return null;
        }
        String col = SoftDeleteHelper
            .validateIdentifier(SoftDeleteHelper.resolveColumnName(entityClass, annotation.deletedTimestampField()));
        return SoftDeleteHelper.quoteIdentifier(col, dialect);
    }

    static Field resolveTimestampField(Class<?> entityClass, SoftDelete annotation) {
        if (annotation == null || annotation.deletedTimestampField().isEmpty())
            return null;
        Field f = SoftDeleteHelper.getField(entityClass, annotation.deletedTimestampField());
        if (f == null)
            log.warn("SoftDelete deletedTimestampField '{}' not found in {}. Ignoring timestamp.",
                annotation.deletedTimestampField(), entityClass.getName());
        return f;
    }

    private static final com.zsubera.jpa.util.SampledEvictionCache<String, VersionFieldInfo> VERSION_FIELD_INFO_CACHE =
        new com.zsubera.jpa.util.SampledEvictionCache<>(256, 0.75, 100, 64);

    static VersionFieldInfo resolveVersionFieldInfo(Class<?> entityClass) {
        String cacheKey = SoftDeleteHelper.getEntityBaseName(entityClass);
        VersionFieldInfo cached = VERSION_FIELD_INFO_CACHE.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        for (Class<?> c = entityClass; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (f.isAnnotationPresent(jakarta.persistence.Version.class)) {
                    jakarta.persistence.Column colAnn = f.getAnnotation(jakarta.persistence.Column.class);
                    String colName;
                    if (colAnn != null && !colAnn.name().isEmpty()) {
                        colName = SoftDeleteHelper.validateIdentifier(colAnn.name());
                    } else {
                        colName = SoftDeleteHelper.resolveColumnName(entityClass, f.getName());
                    }
                    VersionFieldInfo result = new VersionFieldInfo(colName, f);
                    VERSION_FIELD_INFO_CACHE.put(cacheKey, result);
                    return result;
                }
            }
        }
        return null;
    }

    static String resolveVersionColumn(Class<?> entityClass, String dialect) {
        VersionFieldInfo info = resolveVersionFieldInfo(entityClass);
        return info != null ? SoftDeleteHelper.quoteIdentifier(info.columnName, dialect) : null;
    }

    static Field resolveVersionField(Class<?> entityClass) {
        VersionFieldInfo info = resolveVersionFieldInfo(entityClass);
        return info != null ? info.field : null;
    }

    /**
     * 使用 JSqlParser 验证生成的 SQL 语法。解析失败时记录警告但不阻断执行。
     */
    static void validateGeneratedSql(String sql, String context) {
        try {
            CCJSqlParserUtil.parse(sql);
        } catch (JSQLParserException e) {
            log.warn("Generated SQL syntax warning ({}): {} — {}", context, e.getMessage(),
                sql.length() > 100 ? sql.substring(0, 100) + "..." : sql);
        }
    }
}
