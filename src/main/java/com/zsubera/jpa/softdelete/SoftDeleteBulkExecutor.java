package com.zsubera.jpa.softdelete;

import com.zsubera.jpa.annotation.SoftDelete;
import com.zsubera.jpa.exception.MyJpaPlusException;
import com.zsubera.jpa.update.AuditUtils;
import com.zsubera.jpa.update.UpdateSpec;
import jakarta.persistence.EntityManager;
import java.lang.reflect.Field;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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

    public static <T> int softDeleteAll(EntityManager em, Class<T> entityClass, boolean allowUnconditional) {
        return softDeleteAll(em, entityClass, allowUnconditional, DEFAULT_MAX_ROWS);
    }

    public static <T> int softDeleteAll(EntityManager em, Class<T> entityClass, boolean allowUnconditional,
        int maxRows) {
        if (em == null)
            throw new IllegalArgumentException("em must not be null");
        if (entityClass == null)
            throw new IllegalArgumentException("entityClass must not be null");
        if (!allowUnconditional)
            throw new IllegalStateException(
                "softDeleteAll without conditions is dangerous. Pass allowUnconditional=true to confirm.");
        if (!TransactionSynchronizationManager.isActualTransactionActive())
            throw new MyJpaPlusException(
                "softDeleteAll requires an active transaction. Ensure the calling method is annotated with @Transactional.");
        if (log.isWarnEnabled())
            log.warn("AUDIT: Executing unconditional soft DELETE on {} — this will affect ALL rows! Call stack: {}",
                entityClass.getSimpleName(), AuditUtils.getCallStack());

        String fieldName = SoftDeleteHelper.findSoftDeleteField(entityClass);
        if (fieldName == null)
            throw new IllegalArgumentException("Entity " + entityClass.getSimpleName() + " has no @SoftDelete field");
        if (hasVersionField(entityClass))
            log.warn("AUDIT: Entity {} has @Version field. softDeleteAll() bypasses optimistic lock checking. "
                + "Consider using softDeleteByIds() with specific IDs or UpdateSpec.withVersionIncrement(true) for safe concurrent updates.",
                entityClass.getSimpleName());

        String tableName = SoftDeleteHelper.resolveTableName(entityClass);
        String columnName = SoftDeleteHelper.resolveColumnName(entityClass, fieldName);
        Field field = SoftDeleteHelper.getField(entityClass, fieldName);
        if (field == null)
            throw new IllegalArgumentException("Cannot resolve @SoftDelete field: " + fieldName);
        jakarta.persistence.Column colAnn = field.getAnnotation(jakarta.persistence.Column.class);
        SoftDelete annotation = field.getAnnotation(SoftDelete.class);
        String escapedTable = SoftDeleteHelper.validateIdentifier(tableName);
        String escapedColumn = SoftDeleteHelper.validateIdentifier(columnName);
        SoftDeleteHelper.ResolvedDeletedValue resolved =
            SoftDeleteHelper.resolveDeletedValue(entityClass, field, annotation);
        String timestampColumn = resolveTimestampColumn(entityClass, annotation);

        if (maxRows > 0) {
            String wherePart = resolved.booleanField() ? "(" + escapedColumn + " = ?1 OR " + escapedColumn + " IS NULL)"
                : "(" + escapedColumn + " != ?1 OR " + escapedColumn + " IS NULL)";
            var probeQuery = em.createNativeQuery("SELECT 1 FROM " + escapedTable + " WHERE " + wherePart);
            if (resolved.booleanField())
                probeQuery.setParameter(1, Boolean.FALSE);
            else
                probeQuery.setParameter(1, resolved.dbValue());
            probeQuery.setMaxResults(maxRows + 1);
            List<?> probeResults = probeQuery.getResultList();
            if (probeResults.size() > maxRows) {
                var countQuery = em.createNativeQuery("SELECT COUNT(*) FROM " + escapedTable + " WHERE " + wherePart);
                if (resolved.booleanField())
                    countQuery.setParameter(1, Boolean.FALSE);
                else
                    countQuery.setParameter(1, resolved.dbValue());
                long rowCount = ((Number)countQuery.getSingleResult()).longValue();
                if (rowCount > maxRows)
                    throw new IllegalStateException(
                        "softDeleteAll would affect " + rowCount + " rows, which exceeds the limit of " + maxRows
                            + ". Use softDeleteByIds() with explicit ID lists, or increase the limit.");
            }
        }

        int updated;
        if (resolved.booleanField()) {
            String setClause = escapedColumn + " = ?1"
                + (timestampColumn != null ? ", " + timestampColumn + " = CURRENT_TIMESTAMP" : "");
            updated = em
                .createNativeQuery("UPDATE " + escapedTable + " SET " + setClause + " WHERE " + escapedColumn
                    + " = ?2 OR " + escapedColumn + " IS NULL")
                .setParameter(1, Boolean.TRUE).setParameter(2, Boolean.FALSE).executeUpdate();
        } else {
            String setClause = escapedColumn + " = ?1"
                + (timestampColumn != null ? ", " + timestampColumn + " = CURRENT_TIMESTAMP" : "");
            updated = em.createNativeQuery("UPDATE " + escapedTable + " SET " + setClause + " WHERE " + escapedColumn
                + " != ?1 OR " + escapedColumn + " IS NULL").setParameter(1, resolved.dbValue()).executeUpdate();
        }

        if (maxRows > 0 && updated > maxRows)
            throw new IllegalStateException("softDeleteAll affected " + updated
                + " rows, exceeding the pre-check limit of " + maxRows
                + ". This indicates a race condition between COUNT and UPDATE. "
                + "Consider using a transaction with pessimistic locking or softDeleteByIds() with explicit ID lists.");
        if (updated > 0) {
            em.flush();
            em.clear();
            UpdateSpec.evictEntityCache(em, entityClass);
            publishEvent(entityClass, updated);
        }
        return updated;
    }

    public static <T, ID> int softDeleteByIds(EntityManager em, Class<T> entityClass, List<ID> ids) {
        if (em == null)
            throw new IllegalArgumentException("em must not be null");
        if (entityClass == null)
            throw new IllegalArgumentException("entityClass must not be null");
        if (ids == null || ids.isEmpty())
            return 0;
        if (!TransactionSynchronizationManager.isActualTransactionActive())
            throw new MyJpaPlusException(
                "softDeleteByIds requires an active transaction. Use @Transactional on the calling method.");
        int hardLimit = com.zsubera.jpa.util.InClauseBuilder.getHardLimit();
        if (ids.size() > hardLimit)
            throw new IllegalArgumentException("ID list size (" + ids.size() + ") exceeds the hard limit (" + hardLimit
                + "). " + "Consider processing in smaller batches or using a temporary table.");

        String fieldName = SoftDeleteHelper.findSoftDeleteField(entityClass);
        if (fieldName == null)
            throw new IllegalArgumentException("Entity " + entityClass.getSimpleName() + " has no @SoftDelete field");
        String tableName = SoftDeleteHelper.resolveTableName(entityClass);
        String columnName = SoftDeleteHelper.resolveColumnName(entityClass, fieldName);
        String idFieldName = SoftDeleteHelper.resolveIdColumnName(entityClass);
        Field field = SoftDeleteHelper.getField(entityClass, fieldName);
        if (field == null)
            throw new IllegalArgumentException("Cannot resolve @SoftDelete field: " + fieldName);
        SoftDelete annotation = field.getAnnotation(SoftDelete.class);
        String escapedTable = SoftDeleteHelper.validateIdentifier(tableName);
        String escapedColumn = SoftDeleteHelper.validateIdentifier(columnName);
        String escapedIdColumn = SoftDeleteHelper.validateIdentifier(idFieldName);
        SoftDeleteHelper.ResolvedDeletedValue resolved =
            SoftDeleteHelper.resolveDeletedValue(entityClass, field, annotation);
        String timestampColumn = resolveTimestampColumn(entityClass, annotation);

        String setClause = escapedColumn + " = :deletedValue"
            + (timestampColumn != null ? ", " + timestampColumn + " = CURRENT_TIMESTAMP" : "");
        String whereClause =
            resolved.booleanField() ? "(" + escapedColumn + " = FALSE OR " + escapedColumn + " IS NULL)"
                : "(" + escapedColumn + " != :deletedValue OR " + escapedColumn + " IS NULL)";
        int batchSize = com.zsubera.jpa.util.InClauseBuilder.getMaxInClauseSize();
        int total = 0;
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
            query.setParameter("deletedValue", resolved.booleanField() ? Boolean.TRUE : resolved.dbValue());
            for (int j = 0; j < batch.size(); j++)
                query.setParameter("id" + j, batch.get(j));
            total += query.executeUpdate();
        }
        if (total > 0) {
            em.flush();
            em.clear();
            UpdateSpec.evictEntityCache(em, entityClass);
            publishEvent(entityClass, total);
        }
        return total;
    }

    public static <T> int softDeleteAllUsingCriteriaUpdate(EntityManager em, Class<T> entityClass,
        boolean allowUnconditional) {
        return softDeleteAllUsingCriteriaUpdate(em, entityClass, allowUnconditional, DEFAULT_MAX_ROWS);
    }

    public static <T> int softDeleteAllUsingCriteriaUpdate(EntityManager em, Class<T> entityClass,
        boolean allowUnconditional, int maxRows) {
        if (em == null)
            throw new IllegalArgumentException("em must not be null");
        if (entityClass == null)
            throw new IllegalArgumentException("entityClass must not be null");
        if (!allowUnconditional)
            throw new IllegalStateException(
                "softDeleteAllUsingCriteriaUpdate without conditions is dangerous. Pass allowUnconditional=true to confirm.");
        if (!TransactionSynchronizationManager.isActualTransactionActive())
            throw new MyJpaPlusException(
                "softDeleteAllUsingCriteriaUpdate requires an active transaction. Ensure the calling method is annotated with @Transactional.");

        String fieldName = SoftDeleteHelper.findSoftDeleteField(entityClass);
        if (fieldName == null)
            throw new IllegalArgumentException("Entity " + entityClass.getSimpleName() + " has no @SoftDelete field");
        if (log.isWarnEnabled())
            log.warn("AUDIT: Executing CriteriaUpdate soft DELETE on {} — this will affect ALL rows! Call stack: {}",
                entityClass.getSimpleName(), AuditUtils.getCallStack());

        Field field = SoftDeleteHelper.getField(entityClass, fieldName);
        if (field == null)
            throw new IllegalArgumentException("Cannot resolve @SoftDelete field: " + fieldName);
        SoftDelete annotation = field.getAnnotation(SoftDelete.class);
        SoftDeleteHelper.ResolvedDeletedValue resolved =
            SoftDeleteHelper.resolveDeletedValue(entityClass, field, annotation);
        jakarta.persistence.criteria.CriteriaBuilder cb = em.getCriteriaBuilder();
        jakarta.persistence.criteria.CriteriaUpdate<T> update = cb.createCriteriaUpdate(entityClass);
        jakarta.persistence.criteria.Root<T> root = update.from(entityClass);
        if (resolved.booleanField()) {
            update.set(fieldName, Boolean.TRUE);
            update.where(cb.or(cb.isNull(root.get(fieldName)), cb.equal(root.get(fieldName), false)));
        } else {
            update.set(fieldName, (Comparable)resolved.dbValue());
            update.where(cb.or(cb.isNull(root.get(fieldName)), cb.notEqual(root.get(fieldName), resolved.dbValue())));
        }
        int updated = em.createQuery(update).executeUpdate();
        if (maxRows > 0 && updated > maxRows)
            throw new IllegalStateException(
                "softDeleteAllUsingCriteriaUpdate affected " + updated + " rows, exceeding the limit of " + maxRows
                    + ". Consider using softDeleteByIdsUsingEntityManager() with explicit ID lists.");
        if (updated > 0) {
            UpdateSpec.evictEntityCache(em, entityClass);
            publishEvent(entityClass, updated);
        }
        return updated;
    }

    public static <T, ID> int softDeleteByIdsUsingEntityManager(EntityManager em, Class<T> entityClass, List<ID> ids) {
        if (em == null)
            throw new IllegalArgumentException("em must not be null");
        if (entityClass == null)
            throw new IllegalArgumentException("entityClass must not be null");
        if (ids == null || ids.isEmpty())
            return 0;
        if (!TransactionSynchronizationManager.isActualTransactionActive())
            throw new MyJpaPlusException(
                "softDeleteByIdsUsingEntityManager requires an active transaction. Use @Transactional on the calling method.");

        String fieldName = SoftDeleteHelper.findSoftDeleteField(entityClass);
        if (fieldName == null)
            throw new IllegalArgumentException("Entity " + entityClass.getSimpleName() + " has no @SoftDelete field");
        Field field = SoftDeleteHelper.getField(entityClass, fieldName);
        if (field == null)
            throw new IllegalArgumentException("Cannot resolve @SoftDelete field: " + fieldName);
        SoftDelete annotation = field.getAnnotation(SoftDelete.class);
        SoftDeleteHelper.ResolvedDeletedValue resolved =
            SoftDeleteHelper.resolveDeletedValue(entityClass, field, annotation);
        jakarta.persistence.criteria.CriteriaBuilder cb = em.getCriteriaBuilder();
        Field timestampField = resolveTimestampField(entityClass, annotation);
        int batchSize = com.zsubera.jpa.util.InClauseBuilder.getMaxInClauseSize();
        String idFieldName = com.zsubera.jpa.util.EntityClassResolver.resolveIdFieldName(entityClass);
        int total = 0;
        for (int i = 0; i < ids.size(); i += batchSize) {
            List<ID> batch = ids.subList(i, Math.min(i + batchSize, ids.size()));
            jakarta.persistence.criteria.CriteriaUpdate<T> batchUpdate = cb.createCriteriaUpdate(entityClass);
            jakarta.persistence.criteria.Root<T> batchRoot = batchUpdate.from(entityClass);
            if (resolved.booleanField())
                batchUpdate.set(fieldName, Boolean.TRUE);
            else
                batchUpdate.set(fieldName, (Comparable)resolved.dbValue());
            if (timestampField != null)
                batchUpdate.set(annotation.deletedTimestampField(), cb.currentTimestamp());
            batchUpdate.where(batchRoot.get(idFieldName).in(batch));
            total += em.createQuery(batchUpdate).executeUpdate();
        }
        if (total > 0) {
            em.flush();
            publishEvent(entityClass, total);
        }
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

    private static boolean hasVersionField(Class<?> entityClass) {
        for (Class<?> c = entityClass; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (f.isAnnotationPresent(jakarta.persistence.Version.class))
                    return true;
            }
        }
        return false;
    }
}
