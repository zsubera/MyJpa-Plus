package com.zsubera.jpa.update;

import com.zsubera.jpa.softdelete.SoftDeleteHelper;
import com.zsubera.jpa.util.EntityClassResolver;
import com.zsubera.jpa.util.InClauseBuilder;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaDelete;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;

/**
 * JPA {@link CriteriaDelete} 批量删除操作的类型安全构建器。
 *
 * <p>
 * 允许使用 Lambda 方法引用构建类型安全的 DELETE 查询。条件以延迟函数形式存储， 在执行时才进行解析。
 *
 * <p>
 * <strong>事务要求：</strong>{@link #execute(EntityManager)} 需要活动事务。 可使用 {@link #executeInTransaction(EntityManager)}
 * 进行自动事务管理。
 *
 * <p>
 * 示例：
 *
 * <pre>{@code
 * int deleted = new DeleteSpec<>(User.class).lt(User::getLastLogin, cutoffDate).eq(User::getStatus, "INACTIVE")
 *     .executeInTransaction(entityManager);
 * }</pre>
 *
 * @param <T> 要删除的实体类型
 */
public class DeleteSpec<T> extends AbstractBulkOperationSpec<T, DeleteSpec<T>> {

    private static final Logger log = LoggerFactory.getLogger(DeleteSpec.class);

    private boolean allowUnconditional = false;

    /**
     * 创建指定实体类型的删除规范构建器。
     *
     * @param entityClass 要删除的实体类
     */
    public DeleteSpec(Class<T> entityClass) {
        super(entityClass);
    }

    /**
     * 显式允许无条件操作（deleteAll）。 在调用 {@link #deleteAll(EntityManager)} 前必须先调用此方法。
     *
     * @param allow 是否允许无条件操作
     * @return 当前构建器实例，支持链式调用
     */
    public DeleteSpec<T> allowUnconditional(boolean allow) {
        this.allowUnconditional = allow;
        return this;
    }

    /**
     * 执行 DELETE 语句并返回受影响的行数。
     *
     * <p>
     * <strong>需要活动事务。</strong>建议使用 {@link #executeInTransaction(EntityManager)}。
     *
     * <p>
     * <strong>安全保护：</strong>如果配置了最大批量操作行数限制，执行前会先计数验证，
     * 超过限制时抛出 {@link IllegalStateException} 阻止执行。
     *
     * <p><strong>副作用：</strong>当 affected > 0 时，会调用 {@code em.flush()} 和 {@code em.clear()}，
     * 清空持久化上下文中的所有托管实体。在此方法之前通过 {@code em.find()} 等加载的实体将变为游离状态，
     * 后续访问其延迟加载属性可能导致 {@code LazyInitializationException}。
     *
     * @param em 实体管理器
     * @return 删除的实体数量
     * @throws IllegalStateException 如果超过最大行数限制
     * @throws jakarta.persistence.TransactionRequiredException 如果没有活动事务
     */
    @Override
    public int execute(EntityManager em) {
        String softDeleteField = SoftDeleteHelper.findSoftDeleteField(entityClass);
        if (softDeleteField != null) {
            log.warn(
                "AUDIT: Physical DELETE on @SoftDelete entity {} (field '{}'). "
                    + "Consider using softDeleteAll() or executeAsSoftDelete() instead. Call stack: {}",
                entityClass.getSimpleName(), softDeleteField, AuditUtils.getCallStack());
        }
        return executeWithLimitCheck(em, "DELETE", e -> {
            CriteriaDelete<T> delete = toDelete(e);
            if (log.isDebugEnabled()) {
                log.debug("Executing DELETE on {} with {} conditions", entityClass.getSimpleName(),
                    conditionNodes.size());
            }
            int affected = e.createQuery(delete).executeUpdate();
            if (affected > 0) {
                afterBulkOperation(e, entityClass);
            }
            return affected;
        });
    }

    @Override
    protected int doExecute(EntityManager em) {
        return execute(em);
    }

    /**
     * 构建 {@link CriteriaDelete} 对象但不执行。
     *
     * @param em 实体管理器
     * @return 构建的 CriteriaDelete 对象
     * @throws IllegalStateException 如果没有添加任何条件且未调用 allowUnconditional(true)
     */
    public CriteriaDelete<T> toDelete(EntityManager em) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaDelete<T> delete = cb.createCriteriaDelete(entityClass);
        Root<T> root = delete.from(entityClass);
        Predicate[] predicates = buildPredicates(root, cb);
        if (predicates.length == 0) {
            if (!allowUnconditional) {
                throw new IllegalStateException("No WHERE conditions specified for DELETE operation. "
                    + "This would delete ALL rows in the table. "
                    + "If unconditional deletion is intended, use allowUnconditional(true) then deleteAll(EntityManager).");
            }
            // allowUnconditional=true，返回不带WHERE子句的delete
            return delete;
        }
        delete.where(cb.and(predicates));
        return delete;
    }

    /**
     * 将已构建的 DELETE 条件转换为软删除 UPDATE 执行。
     *
     * <p>
     * 复用 {@link #buildPredicates(Root, CriteriaBuilder)} 构建的 WHERE 条件，
     * 构建 {@link CriteriaUpdate} 设置软删除字段值，而非物理删除行。
     *
     * @param em 实体管理器
     * @param fieldName 软删除字段名
     * @param deletedValue 软删除字段值
     * @return 受影响的行数
     */
    public int executeAsSoftDelete(EntityManager em, String fieldName, Object deletedValue) {
        jakarta.persistence.criteria.CriteriaBuilder cb = em.getCriteriaBuilder();
        jakarta.persistence.criteria.CriteriaUpdate<T> update = cb.createCriteriaUpdate(entityClass);
        jakarta.persistence.criteria.Root<T> root = update.from(entityClass);
        List<Predicate> predicates = new ArrayList<>();
        // Guard: exclude already-soft-deleted rows to avoid inflating affected counts
        String softDeleteField = SoftDeleteHelper.findSoftDeleteField(entityClass);
        if (softDeleteField != null) {
            Predicate notDeleted = SoftDeleteHelper.buildNotDeleted(cb, root, softDeleteField, entityClass);
            predicates.add(notDeleted);
        }
        Predicate[] userPredicates = buildPredicates(root, cb);
        if (userPredicates.length == 0) {
            if (!allowUnconditional) {
                throw new IllegalStateException("No WHERE conditions specified for soft-delete operation. "
                    + "This would soft-delete ALL active rows in the table. "
                    + "If unconditional soft-delete is intended, use allowUnconditional(true).");
            }
            log.warn(
                "AUDIT: Executing unconditional soft-delete on {} — this will affect ALL active rows! Call stack: {}",
                entityClass.getSimpleName(), AuditUtils.getCallStack());
        } else {
            for (Predicate p : userPredicates) {
                predicates.add(p);
            }
        }
        // ponytail: COUNT must include the soft-delete filter to count only active rows,
        // matching the actual UPDATE's affected row count.
        int limit = resolveMaxBulkOperationRows();
        if (limit > 0) {
            jakarta.persistence.criteria.CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
            jakarta.persistence.criteria.Root<T> countRoot = countQuery.from(entityClass);
            countQuery.select(cb.count(countRoot));
            List<Predicate> countPredicates = new ArrayList<>();
            if (softDeleteField != null) {
                countPredicates.add(SoftDeleteHelper.buildNotDeleted(cb, countRoot, softDeleteField, entityClass));
            }
            Predicate[] countUserPredicates = buildPredicates(countRoot, cb);
            for (Predicate p : countUserPredicates) {
                countPredicates.add(p);
            }
            if (!countPredicates.isEmpty()) {
                countQuery.where(cb.and(countPredicates.toArray(new Predicate[0])));
            }
            long count = em.createQuery(countQuery).getSingleResult();
            if (count > limit) {
                throw new IllegalStateException("Soft-delete would affect " + count
                    + " rows, which exceeds the configured limit of " + limit + " rows. "
                    + "Use executeLimited() with an explicit limit, or adjust myjpa-plus.query.max-bulk-operation-rows.");
            }
        }
        update.where(cb.and(predicates.toArray(new Predicate[0])));
        update.set(fieldName, deletedValue);
        int affected = em.createQuery(update).executeUpdate();
        // ponytail: 后置检查处理并发导致超额删除的场景。预检查 COUNT 与 UPDATE 之间
        // 存在竞态窗口，并发的 INSERT 或其他软删除操作可能导致实际影响行数超过限制。
        // 后置检查 + 回滚作为安全网。
        if (limit > 0 && affected > limit) {
            if (log.isWarnEnabled()) {
                log.warn("executeAsSoftDelete affected {} rows, exceeding the pre-check limit of {}. "
                    + "Concurrent modifications detected.", affected, limit);
            }
            boolean rolledBack = AbstractBulkOperationSpec.rollbackOrMarkRollbackOnly(em, "executeAsSoftDelete");
            if (!rolledBack) {
                log.error("CRITICAL: Rollback FAILED. The soft-delete may have been committed. Data corruption risk.");
            }
            throw new IllegalStateException("executeAsSoftDelete affected " + affected
                + " rows, exceeding the limit of " + limit + ". Concurrent modifications detected. "
                + (rolledBack ? "Transaction has been rolled back or marked rollback-only."
                    : "WARNING: Rollback FAILED. The soft-delete may have been committed. Data corruption risk."));
        }
        if (affected > 0) {
            afterBulkOperation(em, entityClass);
        }
        return affected;
    }

    /**
     * 执行无条件删除，删除该实体的所有行。
     *
     * <p>
     * <strong>安全要求：</strong>必须先调用 {@link #allowUnconditional(boolean)} 显式确认， 否则将抛出
     * {@link IllegalStateException}。此机制防止误调用导致全表数据被意外删除。
     *
     * @param em 实体管理器
     * @return 删除的实体数量
     * @throws IllegalStateException 如果未调用 allowUnconditional(true)
     */
    public int deleteAll(EntityManager em) {
        if (!allowUnconditional) {
            throw new IllegalStateException("Unconditional DELETE is not allowed. "
                + "Call .allowUnconditional(true) to explicitly confirm this operation.");
        }
        int limit = resolveMaxBulkOperationRows();
        if (limit > 0) {
            long count = countBeforeExecute(em);
            if (count > limit) {
                throw new IllegalStateException("Bulk DELETE would affect " + count
                    + " rows, which exceeds the configured limit of " + limit + " rows. "
                    + "Use executeLimited() with an explicit limit, or adjust myjpa-plus.query.max-bulk-operation-rows.");
            }
        }
        // 审计日志：记录无条件删除操作及调用栈，便于生产环境追踪危险操作
        log.warn("AUDIT: Executing unconditional DELETE on {} — this will affect ALL rows! Call stack: {}",
            entityClass.getSimpleName(), AuditUtils.getCallStack());
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaDelete<T> delete = cb.createCriteriaDelete(entityClass);
        delete.from(entityClass);
        var q = em.createQuery(delete);
        int affected = q.executeUpdate();
        // ponytail: 后置检查处理并发导致超额删除的场景。预检查 COUNT 与 DELETE 之间
        // 存在竞态窗口，并发的 INSERT 操作可能导致实际影响行数超过限制。
        // 后置检查 + 回滚作为安全网。
        if (limit > 0 && affected > limit) {
            if (log.isWarnEnabled()) {
                log.warn("deleteAll affected {} rows, exceeding the pre-check limit of {}. "
                    + "Concurrent modifications detected.", affected, limit);
            }
            boolean rolledBack = AbstractBulkOperationSpec.rollbackOrMarkRollbackOnly(em, "deleteAll");
            if (!rolledBack) {
                log.error("CRITICAL: Rollback FAILED. The DELETE may be committed. Data corruption risk.");
            }
            throw new IllegalStateException("deleteAll affected " + affected + " rows, exceeding the limit of " + limit
                + ". Concurrent modifications detected. "
                + (rolledBack ? "Transaction has been rolled back or marked rollback-only."
                    : "WARNING: Rollback FAILED. The DELETE may have been committed. Data corruption risk."));
        }
        if (affected > 0) {
            afterBulkOperation(em, entityClass);
        }
        return affected;
    }

    /**
     * 在事务中执行无条件删除，删除该实体的所有行。
     *
     * <p>
     * <strong>安全要求：</strong>必须先调用 {@link #allowUnconditional(boolean)} 显式确认， 否则将抛出
     * {@link IllegalStateException}。此机制防止误调用导致全表数据被意外删除。
     *
     * @param em 实体管理器
     * @return 删除的实体数量
     * @throws IllegalStateException 如果未调用 allowUnconditional(true)
     */
    public int deleteAllInTransaction(EntityManager em) {
        return executeInTransaction(em, this::deleteAll);
    }

    /**
     * 执行限制删除行数的条件删除操作。
     *
     * <p>
     * 此方法适用于批处理场景，通过限制 SQL 影响的行数来控制操作范围。 请注意，不同数据库对 DELETE 语句的 LIMIT 支持程度不同。
     *
     * <p>
     * <strong>注意：</strong>此方法需要活动事务。调用方负责在批次之间刷新和清除持久化上下文。
     *
     * <p>
     * <strong>安全说明：</strong>此方法默认启用悲观锁（{@code pessimisticLock=true}）， 以防止查询ID和执行删除之间的并发竞态条件。如需禁用悲观锁，请使用
     * {@link #executeLimited(EntityManager, int, boolean)} 并设置 {@code pessimisticLock=false}。
     *
     * @param em 实体管理器
     * @param limit 最大删除行数
     * @return 实际删除的行数
     */
    public int executeLimited(EntityManager em, int limit) {
        return executeLimited(em, limit, true);
    }

    /**
     * 带游标的限制删除，确保每次迭代处理不同的行集。
     */
    public int executeLimited(EntityManager em, int limit, @Nullable Object lastId) {
        return executeLimitedWithCursor(em, limit, true, lastId);
    }

    /**
     * 执行限制删除并返回游标信息，供批量循环使用。
     */
    public record BatchCursor(int affected, @Nullable Object lastId) {
    }

    public BatchCursor executeLimitedCursor(EntityManager em, int limit, @Nullable Object lastId) {
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }
        if (lastId != null && !(lastId instanceof Comparable)) {
            throw new IllegalArgumentException(
                "lastId must implement Comparable for cursor-based pagination. Type: " + lastId.getClass().getName());
        }
        // ponytail: 与 executeLimitedWithCursor 保持一致，应用全局上限和无条件删除守卫
        int globalMax = resolveMaxBulkOperationRows();
        if (globalMax > 0 && limit > globalMax) {
            throw new IllegalArgumentException("limit (" + limit + ") exceeds global max (" + globalMax
                + "). Adjust myjpa-plus.query.max-bulk-operation-rows or use a smaller limit.");
        }
        if (EntityClassResolver.hasCompositeKey(entityClass)) {
            throw new UnsupportedOperationException(
                "executeLimited() does not support entities with composite primary keys.");
        }
        CriteriaBuilder cb = em.getCriteriaBuilder();
        String idFieldName = EntityClassResolver.resolveIdFieldName(entityClass);
        CriteriaQuery<?> idQuery = cb.createQuery();
        Root<T> idRoot = idQuery.from(entityClass);
        idQuery.select(idRoot.get(idFieldName));
        List<Predicate> predicateList = new ArrayList<>();
        Predicate[] predicates = buildPredicates(idRoot, cb);
        // ponytail: 与 executeLimitedWithCursor 保持一致，应用无条件删除守卫
        if (predicates.length == 0 && !allowUnconditional) {
            throw new IllegalStateException("No WHERE conditions specified for DELETE operation. "
                + "Call .allowUnconditional(true) to explicitly confirm this operation.");
        }
        for (Predicate p : predicates) {
            predicateList.add(p);
        }
        if (lastId != null) {
            predicateList.add(cb.greaterThan(idRoot.get(idFieldName), (Comparable)lastId));
        }
        idQuery.where(predicateList.isEmpty() ? cb.conjunction() : cb.and(predicateList.toArray(new Predicate[0])));
        idQuery.orderBy(cb.asc(idRoot.get(idFieldName)));
        List<?> ids = em.createQuery(idQuery).setMaxResults(limit).getResultList();
        if (ids.isEmpty()) {
            return new BatchCursor(0, lastId);
        }
        CriteriaDelete<T> delete = cb.createCriteriaDelete(entityClass);
        Root<T> deleteRoot = delete.from(entityClass);
        delete.where(InClauseBuilder.in(cb, deleteRoot.get(idFieldName), ids));
        int affected = em.createQuery(delete).executeUpdate();
        if (affected > 0) {
            afterBulkOperation(em, entityClass);
        }
        return new BatchCursor(affected, ids.get(ids.size() - 1));
    }

    public int executeLimited(EntityManager em, int limit, boolean pessimisticLock) {
        return executeLimitedWithCursor(em, limit, pessimisticLock, null);
    }

    private int executeLimitedWithCursor(EntityManager em, int limit, boolean pessimisticLock,
        @Nullable Object lastId) {
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }
        if (lastId != null && !(lastId instanceof Comparable)) {
            throw new IllegalArgumentException(
                "lastId must implement Comparable for cursor-based pagination. Type: " + lastId.getClass().getName());
        }
        int globalMax = resolveMaxBulkOperationRows();
        if (globalMax > 0 && limit > globalMax) {
            throw new IllegalArgumentException("limit (" + limit + ") exceeds global max (" + globalMax
                + "). Adjust myjpa-plus.query.max-bulk-operation-rows or use a smaller limit.");
        }
        if (EntityClassResolver.hasCompositeKey(entityClass)) {
            throw new UnsupportedOperationException(
                "executeLimited() does not support entities with composite primary keys (@EmbeddedId or @IdClass). "
                    + "Entity: " + entityClass.getName() + ". "
                    + "Use delete(entityManager).executeInTransaction(entityManager) instead.");
        }
        if (!pessimisticLock) {
            log.warn("executeLimited() with pessimisticLock=false may cause race conditions. "
                + "Consider using pessimisticLock=true for critical operations.");
        }
        CriteriaBuilder cb = em.getCriteriaBuilder();

        String idFieldName = EntityClassResolver.resolveIdFieldName(entityClass);
        CriteriaQuery<?> idQuery = cb.createQuery();
        Root<T> idRoot = idQuery.from(entityClass);
        idQuery.select(idRoot.get(idFieldName));
        List<Predicate> predicateList = new ArrayList<>();
        Predicate[] predicates = buildPredicates(idRoot, cb);
        if (predicates.length == 0) {
            if (!allowUnconditional) {
                throw new IllegalStateException("No WHERE conditions specified for DELETE operation. "
                    + "Call .allowUnconditional(true) to explicitly confirm this operation, "
                    + "or use deleteAll(EntityManager) instead.");
            }
            log.warn("WARNING: Executing limited DELETE without conditions on {} — this will affect up to {} rows!",
                entityClass.getSimpleName(), limit);
        } else {
            for (Predicate p : predicates) {
                predicateList.add(p);
            }
        }
        if (lastId != null) {
            predicateList.add(cb.greaterThan(idRoot.get(idFieldName), (Comparable)lastId));
        }
        idQuery.where(predicateList.isEmpty() ? cb.conjunction() : cb.and(predicateList.toArray(new Predicate[0])));
        idQuery.orderBy(cb.asc(idRoot.get(idFieldName)));
        TypedQuery<?> query = em.createQuery(idQuery);
        query.setMaxResults(limit);
        if (pessimisticLock) {
            query.setLockMode(LockModeType.PESSIMISTIC_WRITE);
        }
        List<?> ids = query.getResultList();

        if (ids.isEmpty()) {
            return 0;
        }

        CriteriaDelete<T> delete = cb.createCriteriaDelete(entityClass);
        Root<T> deleteRoot = delete.from(entityClass);
        delete.where(InClauseBuilder.in(cb, deleteRoot.get(idFieldName), ids));
        var dq = em.createQuery(delete);
        int deleted = dq.executeUpdate();
        if (deleted > 0) {
            afterBulkOperation(em, entityClass);
        }
        return deleted;
    }

}
