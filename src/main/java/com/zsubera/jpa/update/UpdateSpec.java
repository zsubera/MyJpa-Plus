package com.zsubera.jpa.update;

import com.zsubera.jpa.repository.EntityClassResolver;
import com.zsubera.jpa.spec.SFunction;
import com.zsubera.jpa.util.InClauseBuilder;
import com.zsubera.jpa.util.LambdaUtils;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.CriteriaUpdate;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;

/**
 * JPA {@link CriteriaUpdate} 批量更新操作的类型安全构建器。
 *
 * <p>
 * 允许使用 Lambda 方法引用构建类型安全的 UPDATE 查询。条件以延迟函数形式存储， 在执行时才进行解析。
 *
 * <p>
 * <strong>事务要求：</strong>{@link #execute(EntityManager)} 需要活动事务。 可使用 {@link #executeInTransaction(EntityManager)}
 * 进行自动事务管理。
 *
 * <p>
 * 示例：
 *
 * <pre>{@code
 * int updated = new UpdateSpec<>(User.class).set(User::getStatus, "INACTIVE").lt(User::getLastLogin, someDate)
 *     .executeInTransaction(entityManager);
 * }</pre>
 *
 * @param <T> 要更新的实体类型
 */
public class UpdateSpec<T> extends AbstractBulkOperationSpec<T, UpdateSpec<T>> {

    private static final Logger log = LoggerFactory.getLogger(UpdateSpec.class);

    private final List<SetClause> setClauses = new ArrayList<>();
    private boolean allowUnconditional = false;

    private record SetClause(String fieldName, Object value) {
    }

    /**
     * 创建指定实体类型的更新规范构建器。
     *
     * @param entityClass 要更新的实体类
     * @throws IllegalArgumentException 如果 entityClass 为 null
     */
    public UpdateSpec(Class<T> entityClass) {
        super(entityClass);
    }

    /**
     * 显式允许无条件操作（updateAll）。 在调用 {@link #updateAll(EntityManager)} 前必须先调用此方法。
     *
     * @param allow 是否允许无条件操作
     * @return 当前构建器实例，支持链式调用
     */
    public UpdateSpec<T> allowUnconditional(boolean allow) {
        this.allowUnconditional = allow;
        return this;
    }

    /**
     * 在 UPDATE 子句中设置字段值。
     *
     * @param field 实体属性的方法引用
     * @param value 新值（可为 null）
     * @return 当前构建器实例，支持链式调用
     * @throws IllegalArgumentException 如果 field 为 null
     */
    public UpdateSpec<T> set(SFunction<T, ?> field, @Nullable Object value) {
        if (field == null) {
            throw new IllegalArgumentException("field must not be null");
        }
        setClauses.add(new SetClause(LambdaUtils.getPropertyName(field), value));
        return this;
    }

    /**
     * 仅当条件为 true 时，在 UPDATE 子句中设置字段值。
     *
     * @param condition 执行条件
     * @param field 实体属性的方法引用
     * @param value 新值
     * @return 当前构建器实例，支持链式调用
     */
    public UpdateSpec<T> set(boolean condition, SFunction<T, ?> field, Object value) {
        if (condition) {
            set(field, value);
        }
        return this;
    }

    /**
     * 执行 UPDATE 语句并返回受影响的行数。
     *
     * <p>
     * <strong>需要活动事务。</strong>建议使用 {@link #executeInTransaction(EntityManager)}。
     *
     * @param em 实体管理器
     * @return 更新的实体数量
     * @throws IllegalStateException 如果未指定 SET 子句
     * @throws jakarta.persistence.TransactionRequiredException 如果没有活动事务
     */
    @Override
    public int execute(EntityManager em) {
        return em.createQuery(toUpdate(em)).executeUpdate();
    }

    @Override
    protected int doExecute(EntityManager em) {
        return execute(em);
    }

    /**
     * 构建 {@link CriteriaUpdate} 对象但不执行。
     *
     * @param em 实体管理器
     * @return 构建的 CriteriaUpdate 对象
     * @throws IllegalStateException 如果未指定 SET 子句或没有 WHERE 条件
     */
    public CriteriaUpdate<T> toUpdate(EntityManager em) {
        if (setClauses.isEmpty()) {
            throw new IllegalStateException("At least one set() clause is required");
        }
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaUpdate<T> update = cb.createCriteriaUpdate(entityClass);
        Root<T> root = update.from(entityClass);
        for (SetClause sc : setClauses) {
            update.set(root.get(sc.fieldName), sc.value);
        }
        Predicate[] predicates = buildPredicates(root, cb);
        if (predicates.length == 0) {
            throw new IllegalStateException(
                "No WHERE conditions specified for UPDATE operation. " + "This would update ALL rows in the table. "
                    + "If unconditional update is intended, use updateAll(EntityManager) instead.");
        }
        update.where(cb.and(predicates));
        return update;
    }

    /**
     * 执行无条件更新，更新该实体的所有行。
     *
     * <p>
     * <strong>安全要求：</strong>必须先调用 {@link #allowUnconditional(boolean)} 显式确认， 否则将抛出
     * {@link IllegalStateException}。此机制防止误调用导致全表数据被意外修改。
     *
     * @param em 实体管理器
     * @return 更新的实体数量
     * @throws IllegalStateException 如果未调用 allowUnconditional(true) 或未指定 SET 子句
     */
    public int updateAll(EntityManager em) {
        if (!allowUnconditional) {
            throw new IllegalStateException("Unconditional UPDATE is not allowed. "
                + "Call .allowUnconditional(true) to explicitly confirm this operation.");
        }
        if (setClauses.isEmpty()) {
            throw new IllegalStateException("At least one set() clause is required");
        }
        log.warn("WARNING: Executing unconditional UPDATE on {} — this will affect ALL rows!",
            entityClass.getSimpleName());
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaUpdate<T> update = cb.createCriteriaUpdate(entityClass);
        Root<T> root = update.from(entityClass);
        for (SetClause sc : setClauses) {
            update.set(root.get(sc.fieldName), sc.value);
        }
        return em.createQuery(update).executeUpdate();
    }

    /**
     * 在新事务或现有事务中执行无条件更新。
     *
     * @param em 实体管理器
     * @return 更新的实体数量
     */
    public int updateAllInTransaction(EntityManager em) {
        return executeInTransaction(em, this::updateAll);
    }

    /**
     * Execute UPDATE statement limiting the number of affected rows.
     *
     * <p>
     * This method first queries a list of IDs matching the WHERE conditions (with LIMIT), then executes the update
     * using those IDs. This two-step approach is necessary because JPA CriteriaUpdate does not support LIMIT directly.
     *
     * <p>
     * The method uses {@link EntityManager#clear()} to detach updated entities, allowing multiple batches without
     * clearing the persistence context between batches.
     *
     * <p>
     * <p>
     * <strong>安全说明：</strong>此方法默认启用悲观锁（{@code pessimisticLock=true}）， 以防止查询ID和执行更新之间的并发竞态条件。如需禁用悲观锁，请使用
     * {@link #executeLimited(EntityManager, int, boolean)} 并设置 {@code pessimisticLock=false}。
     *
     * @param em entity manager
     * @param limit maximum number of rows to update
     * @return actual number of rows updated
     * @throws IllegalStateException if no SET clauses are specified
     */
    public int executeLimited(EntityManager em, int limit) {
        return executeLimited(em, limit, true);
    }

    /**
     * Execute UPDATE statement limiting the number of affected rows, with optional pessimistic locking.
     *
     * <p>
     * This method first queries for matching entity IDs with the specified limit, then performs the update on those
     * entities. The persistence context is cleared between batches to prevent memory leaks.
     *
     * <p>
     * <strong>并发风险警告：</strong>此方法分两步执行（先查询 ID，再更新），在高并发场景下存在竞态条件。 在查询ID和执行更新之间，其他事务可能修改或删除记录，导致数据不一致。
     *
     * <p>
     * <strong>安全使用建议（按推荐程度排序）：</strong>
     * <ol>
     * <li>使用 {@code pessimisticLock=true}，在单个数据库事务中持有行锁，防止并发修改：
     *
     * <pre>{@code
     * UpdateSpec.of(User.class).set(User::getStatus, "ARCHIVED").eq(User::getActive, false).executeLimited(entityManager,
     *     1000, true); // 悲观锁
     * }</pre>
     *
     * <li>在已有的 {@code @Transactional} 方法内调用，确保查询和更新在同一事务中执行
     * <li>对于支持 {@code UPDATE ... LIMIT} 的数据库（如 MySQL），考虑使用原生 SQL 作为替代方案
     * <li>在应用层使用分布式锁保护整个操作流程
     * </ol>
     *
     * @param em entity manager
     * @param limit maximum number of rows to update
     * @param pessimisticLock if true, acquire {@link jakarta.persistence.LockModeType#PESSIMISTIC_WRITE} on the
     *            selected IDs to prevent concurrent modifications
     * @return actual number of rows updated
     * @throws IllegalStateException if no SET clauses are specified
     */
    public int executeLimited(EntityManager em, int limit, boolean pessimisticLock) {
        if (setClauses.isEmpty()) {
            throw new IllegalStateException("At least one set() clause is required");
        }
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
            // 与 updateAll() 保持一致的安全检查
            if (!allowUnconditional) {
                throw new IllegalStateException("No WHERE conditions specified for UPDATE operation. "
                    + "Call .allowUnconditional(true) to explicitly confirm this operation, "
                    + "or use updateAll(EntityManager) instead.");
            }
            log.warn("WARNING: Executing limited UPDATE without conditions on {} — this will affect up to {} rows!",
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

        // Step 2: 用ID列表执行更新
        CriteriaUpdate<T> update = cb.createCriteriaUpdate(entityClass);
        Root<T> updateRoot = update.from(entityClass);
        for (SetClause sc : setClauses) {
            update.set(updateRoot.get(sc.fieldName), sc.value);
        }
        update.where(InClauseBuilder.in(cb, updateRoot.get(idFieldName), ids));
        return em.createQuery(update).executeUpdate();
    }
}
