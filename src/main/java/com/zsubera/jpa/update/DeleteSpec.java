package com.zsubera.jpa.update;

import com.zsubera.jpa.repository.EntityClassResolver;
import com.zsubera.jpa.util.InClauseBuilder;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaDelete;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
     * @throws IllegalArgumentException 如果 entityClass 为 null
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
     * @param em 实体管理器
     * @return 删除的实体数量
     * @throws jakarta.persistence.TransactionRequiredException 如果没有活动事务
     */
    @Override
    public int execute(EntityManager em) {
        return em.createQuery(toDelete(em)).executeUpdate();
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
     * @throws IllegalStateException 如果没有添加任何条件。可使用 {@link #deleteAll(EntityManager)} 进行无条件删除
     */
    public CriteriaDelete<T> toDelete(EntityManager em) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaDelete<T> delete = cb.createCriteriaDelete(entityClass);
        Root<T> root = delete.from(entityClass);
        Predicate[] predicates = buildPredicates(root, cb);
        if (predicates.length == 0) {
            throw new IllegalStateException(
                "No WHERE conditions specified for DELETE operation. " + "This would delete ALL rows in the table. "
                    + "If unconditional deletion is intended, use deleteAll(EntityManager) instead.");
        }
        delete.where(cb.and(predicates));
        return delete;
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
        log.warn("WARNING: Executing unconditional DELETE on {} — this will affect ALL rows!",
            entityClass.getSimpleName());
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaDelete<T> delete = cb.createCriteriaDelete(entityClass);
        delete.from(entityClass);
        return em.createQuery(delete).executeUpdate();
    }

    /**
     * 在新事务或现有事务中执行无条件删除。
     *
     * @param em 实体管理器
     * @return 删除的实体数量
     */
    public int deleteAllInTransaction(EntityManager em) {
        return executeInTransaction(em, this::deleteAll);
    }

    /**
     * Execute DELETE statement limiting the number of affected rows.
     *
     * <p>
     * This method is suitable for batch processing scenarios. It limits the number of rows affected by SQL. Note that
     * LIMIT support for DELETE statements varies by database.
     *
     * <p>
     * <strong>Note:</strong> This method requires an active transaction. The caller is responsible for flushing and
     * clearing the persistence context between batches.
     *
     * <p>
     * <strong>并发风险警告：</strong>此方法存在并发时间窗口。在查询ID和执行删除之间，其他事务可能修改或删除记录。 对于高并发场景，建议：
     * <ul>
     * <li>使用 {@link #executeLimited(EntityManager, int, boolean)} 并设置 {@code pessimisticLock=true}</li>
     * <li>或者在应用层使用分布式锁</li>
     * <li>监控数据库锁等待情况</li>
     * </ul>
     *
     * @param em entity manager
     * @param limit maximum number of rows to delete
     * @return actual number of rows deleted
     */
    public int executeLimited(EntityManager em, int limit) {
        return executeLimited(em, limit, false);
    }

    /**
     * Execute DELETE statement limiting the number of affected rows, with optional pessimistic locking.
     *
     * <p>
     * When {@code pessimisticLock} is {@code true}, the ID query uses {@code SELECT ... FOR UPDATE} to lock the rows
     * before deleting, preventing lost updates in concurrent scenarios.
     *
     * @param em entity manager
     * @param limit maximum number of rows to delete
     * @param pessimisticLock whether to use pessimistic locking on the ID query
     * @return actual number of rows deleted
     */
    public int executeLimited(EntityManager em, int limit, boolean pessimisticLock) {
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }
        CriteriaBuilder cb = em.getCriteriaBuilder();

        // Step 1: 查询符合条件的ID列表（带LIMIT）
        String idFieldName = EntityClassResolver.resolveIdFieldName(entityClass);
        CriteriaQuery<?> idQuery = cb.createQuery();
        Root<T> idRoot = idQuery.from(entityClass);
        idQuery.select(idRoot.get(idFieldName));
        Predicate[] predicates = buildPredicates(idRoot, cb);
        if (predicates.length == 0) {
            // 与 deleteAll() 保持一致的安全检查
            if (!allowUnconditional) {
                throw new IllegalStateException("No WHERE conditions specified for DELETE operation. "
                    + "Call .allowUnconditional(true) to explicitly confirm this operation, "
                    + "or use deleteAll(EntityManager) instead.");
            }
            log.warn("WARNING: Executing limited DELETE without conditions on {} — this will affect up to {} rows!",
                entityClass.getSimpleName(), limit);
        }
        idQuery.where(predicates.length > 0 ? cb.and(predicates) : cb.conjunction());
        TypedQuery<?> query = em.createQuery(idQuery).setMaxResults(limit);
        if (pessimisticLock) {
            query.setLockMode(LockModeType.PESSIMISTIC_WRITE);
        }
        List<?> ids = query.getResultList();

        if (ids.isEmpty()) {
            return 0;
        }

        // Step 2: 用ID列表执行删除
        CriteriaDelete<T> delete = cb.createCriteriaDelete(entityClass);
        Root<T> deleteRoot = delete.from(entityClass);
        delete.where(InClauseBuilder.in(cb, deleteRoot.get(idFieldName), ids));
        return em.createQuery(delete).executeUpdate();
    }
}
