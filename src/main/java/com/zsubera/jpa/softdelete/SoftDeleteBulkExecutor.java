package com.zsubera.jpa.softdelete;

import com.zsubera.jpa.annotation.SoftDelete;
import com.zsubera.jpa.exception.MyJpaPlusException;
import com.zsubera.jpa.update.AuditUtils;
import jakarta.persistence.EntityManager;
import java.lang.reflect.Field;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    static void publishEvent(Class<?> entityClass, int affectedRows) {
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

    private static void requireNonNull(Object obj, String name) {
        if (obj == null)
            throw new IllegalArgumentException(name + " must not be null");
    }

    public static <T> int softDeleteAll(EntityManager em, Class<T> entityClass, boolean allowUnconditional) {
        return softDeleteAll(em, entityClass, allowUnconditional, DEFAULT_MAX_ROWS);
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
        String escapedTable = SoftDeleteHelper.quoteIdentifier(SoftDeleteHelper.validateIdentifier(SoftDeleteHelper.resolveTableName(entityClass)), dialect);
        String escapedColumn =
            SoftDeleteHelper.quoteIdentifier(SoftDeleteHelper.validateIdentifier(SoftDeleteHelper.resolveColumnName(entityClass, ctx.fieldName())), dialect);
        String timestampColumn = resolveTimestampColumn(entityClass, ctx.annotation());
        String versionColumn = versionInfo != null ? versionInfo.columnName : null;

        String setClause =
            escapedColumn + " = :deletedValue" + (timestampColumn != null ? ", " + timestampColumn + " = CURRENT_TIMESTAMP" : "")
                + (versionColumn != null ? ", " + versionColumn + " = " + versionColumn + " + 1" : "");

        // 安全检查：先查询受影响行数，再执行 UPDATE，避免 UPDATE 后才发现超限导致脏持久化上下文
        if (maxRows > 0) {
            long count;
            if (ctx.resolved().booleanField()) {
                count = ((Number) em.createNativeQuery("SELECT COUNT(*) FROM " + escapedTable + " WHERE "
                    + escapedColumn + " = :deletedValue OR " + escapedColumn + " IS NULL")
                    .setParameter("deletedValue", Boolean.FALSE).getSingleResult()).longValue();
            } else {
                Object dv = ctx.resolved().dbValue();
                count = ((Number) em.createNativeQuery("SELECT COUNT(*) FROM " + escapedTable + " WHERE "
                    + escapedColumn + " != :deletedValue OR " + escapedColumn + " IS NULL")
                    .setParameter("deletedValue", dv).getSingleResult()).longValue();
            }
            if (count > maxRows) {
                throw new IllegalStateException("softDeleteAll would affect " + count
                    + " rows, exceeding the limit of " + maxRows
                    + ". Use softDeleteByIds() with explicit ID lists, or increase the limit.");
            }
        }

        int updated;
        Object deletedValue = ctx.resolved().booleanField() ? Boolean.TRUE : ctx.resolved().dbValue();
        updated = em.createNativeQuery("UPDATE " + escapedTable + " SET " + setClause + " WHERE " + escapedColumn
            + " != :deletedValue OR " + escapedColumn + " IS NULL").setParameter("deletedValue", deletedValue).executeUpdate();

        // ponytail: 后置检查处理并发导致超额删除的场景
        if (maxRows > 0 && updated > maxRows) {
            if (log.isWarnEnabled()) {
                log.warn("softDeleteAll affected {} rows, exceeding the pre-check limit of {}. "
                    + "Concurrent modifications detected.", updated, maxRows);
            }
            try {
                em.getTransaction().setRollbackOnly();
            } catch (IllegalStateException e) {
                // JTA environment: setRollbackOnly not available on EntityTransaction
                // The exception will propagate to the caller for transaction rollback
            }
            throw new MyJpaPlusException("softDeleteAll affected " + updated + " rows, exceeding the pre-check limit of "
                + maxRows + ". Concurrent modifications detected. Transaction will be rolled back.");
        }

        publishAfterUpdate(em, entityClass, updated);
        return updated;
    }

    public static <T, ID> int softDeleteByIds(EntityManager em, Class<T> entityClass, List<ID> ids) {
        requireNonNull(em, "em");
        requireNonNull(entityClass, "entityClass");
        if (ids == null || ids.isEmpty())
            return 0;
        requireActiveTransaction();
        int hardLimit = com.zsubera.jpa.util.InClauseBuilder.getHardLimit();
        if (ids.size() > hardLimit)
            throw new IllegalArgumentException("ID list size (" + ids.size() + ") exceeds the hard limit (" + hardLimit
                + "). " + "Consider processing in smaller batches or using a temporary table.");

        ExecContext ctx = resolveExecContext(entityClass);
        String dialect = SoftDeleteHelper.detectDialect(em);
        String escapedTable = SoftDeleteHelper.quoteIdentifier(SoftDeleteHelper.validateIdentifier(SoftDeleteHelper.resolveTableName(entityClass)), dialect);
        String escapedColumn =
            SoftDeleteHelper.quoteIdentifier(SoftDeleteHelper.validateIdentifier(SoftDeleteHelper.resolveColumnName(entityClass, ctx.fieldName())), dialect);
        String escapedIdColumn = SoftDeleteHelper.quoteIdentifier(SoftDeleteHelper.validateIdentifier(SoftDeleteHelper.resolveIdColumnName(entityClass)), dialect);
        String timestampColumn = resolveTimestampColumn(entityClass, ctx.annotation());
        String versionColumn = resolveVersionColumn(entityClass);
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
            var query = em.createNativeQuery("UPDATE " + escapedTable + " SET " + setClause + " WHERE "
                + escapedIdColumn + " IN (" + placeholders + ") AND " + whereClause);
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
        return softDeleteAllUsingCriteriaUpdate(em, entityClass, allowUnconditional, DEFAULT_MAX_ROWS);
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

        // 安全检查：先查询受影响行数，再执行 UPDATE
        if (maxRows > 0) {
            jakarta.persistence.criteria.CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
            jakarta.persistence.criteria.Root<T> countRoot = countQuery.from(entityClass);
            countQuery.select(cb.count(countRoot));
            if (ctx.resolved().booleanField()) {
                countQuery.where(cb.or(cb.isNull(countRoot.get(ctx.fieldName())),
                    cb.equal(countRoot.get(ctx.fieldName()), false)));
            } else {
                countQuery.where(cb.or(cb.isNull(countRoot.get(ctx.fieldName())),
                    cb.notEqual(countRoot.get(ctx.fieldName()), ctx.resolved().dbValue())));
            }
            long count = em.createQuery(countQuery).getSingleResult();
            if (count > maxRows) {
                throw new IllegalStateException("softDeleteAllUsingCriteriaUpdate would affect " + count
                    + " rows, exceeding the limit of " + maxRows
                    + ". Consider using softDeleteByIdsUsingEntityManager() with explicit ID lists.");
            }
        }

        int updated = em.createQuery(update).executeUpdate();
        // ponytail: 后置检查处理并发导致超额删除的场景，与 softDeleteAll（原生 SQL 版本）保持一致
        if (maxRows > 0 && updated > maxRows) {
            if (log.isWarnEnabled()) {
                log.warn("softDeleteAllUsingCriteriaUpdate affected {} rows, exceeding the pre-check limit of {}. "
                    + "Concurrent modifications detected.", updated, maxRows);
            }
            try {
                em.getTransaction().setRollbackOnly();
            } catch (IllegalStateException e) {
                // JTA environment: setRollbackOnly not available on EntityTransaction
                // The exception will propagate to the caller for transaction rollback
            }
            throw new MyJpaPlusException("softDeleteAllUsingCriteriaUpdate affected " + updated
                + " rows, exceeding the pre-check limit of " + maxRows
                + ". Concurrent modifications detected. Transaction will be rolled back.");
        }
        publishAfterUpdate(em, entityClass, updated);
        return updated;
    }

    public static <T, ID> int softDeleteByIdsUsingEntityManager(EntityManager em, Class<T> entityClass, List<ID> ids) {
        requireNonNull(em, "em");
        requireNonNull(entityClass, "entityClass");
        if (ids == null || ids.isEmpty())
            return 0;
        requireActiveTransaction();
        int hardLimit = com.zsubera.jpa.util.InClauseBuilder.getHardLimit();
        if (ids.size() > hardLimit)
            throw new IllegalArgumentException("ID list size (" + ids.size() + ") exceeds the hard limit (" + hardLimit
                + "). " + "Consider processing in smaller batches or using a temporary table.");

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

    static String resolveTimestampColumn(Class<?> entityClass, SoftDelete annotation) {
        if (annotation == null || annotation.deletedTimestampField().isEmpty())
            return null;
        return SoftDeleteHelper
            .validateIdentifier(SoftDeleteHelper.resolveColumnName(entityClass, annotation.deletedTimestampField()));
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

    static String resolveVersionColumn(Class<?> entityClass) {
        VersionFieldInfo info = resolveVersionFieldInfo(entityClass);
        return info != null ? info.columnName : null;
    }

    static Field resolveVersionField(Class<?> entityClass) {
        VersionFieldInfo info = resolveVersionFieldInfo(entityClass);
        return info != null ? info.field : null;
    }
}
