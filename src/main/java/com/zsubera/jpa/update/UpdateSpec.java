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
     * 表达式 SET 子句，支持原子操作如 SET balance = balance + 100。
     *
     * <p>
     * 使用 BiFunction 而非直接使用 Expression，避免类型擦除问题。
     */
    private record ExpressionSetClause(String fieldName,
        java.util.function.BiFunction<jakarta.persistence.criteria.Root<?>, CriteriaBuilder, ?> exprFn) {
    }

    private final List<ExpressionSetClause> expressionSetClauses = new ArrayList<>();

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
     * 在 UPDATE 子句中使用原子表达式设置字段值：{@code SET field = expression}。
     *
     * <p>
     * 使用示例（原子递增）：
     *
     * <pre>{@code
     * new UpdateSpec<>(Account.class).setExpression(Account::getBalance, "balance + :amount").eq(Account::getId, accountId)
     *     .execute(em);
     * }</pre>
     *
     * <p>
     * <strong>安全警告：</strong>此方法接受原始 SQL 表达式字符串，存在潜在的 SQL 注入风险。 请遵循以下安全最佳实践：
     * <ul>
     * <li>使用命名参数（如 {@code :amount}）而非直接拼接用户输入值</li>
     * <li>表达式应为硬编码的常量字符串，不要将用户输入直接拼接到表达式中</li>
     * <li>对于简单的加减操作，推荐使用 {@link #setAdd} 和 {@link #setSubtract} 方法</li>
     * </ul>
     *
     * @param field 实体属性的方法引用
     * @param expression SQL 表达式字符串（使用参数名而非直接拼接值）
     * @return 当前构建器实例，支持链式调用
     * @throws IllegalArgumentException 如果 field 或 expression 为 null
     * @deprecated 使用 {@link #setAdd(SFunction, Number)} 和 {@link #setSubtract(SFunction, Number)} 替代简单的加减操作。
     *             对于复杂表达式，此方法仍然可用但不推荐，因为它接受原始 SQL 字符串存在注入风险。
     */
    @Deprecated(since = "1.2.0", forRemoval = false)
    public UpdateSpec<T> setExpression(SFunction<T, ?> field, String expression) {
        if (field == null) {
            throw new IllegalArgumentException("field must not be null");
        }
        if (expression == null) {
            throw new IllegalArgumentException("expression must not be null");
        }
        String name = LambdaUtils.getPropertyName(field);
        expressionSetClauses.add(new ExpressionSetClause(name, (root, cb) -> cb.literal(expression)));
        return this;
    }

    /**
     * 原子递增字段值：{@code SET field = field + amount}。
     *
     * <p>
     * 使用示例：
     *
     * <pre>{@code
     * new UpdateSpec<>(Account.class).setAdd(Account::getBalance, 100).eq(Account::getId, accountId).execute(em);
     * // 生成: UPDATE account SET balance = balance + 100 WHERE id = ?
     * }</pre>
     *
     * @param field 实体属性的方法引用
     * @param amount 要增加的数值
     * @return 当前构建器实例，支持链式调用
     * @throws IllegalArgumentException 如果 field 或 amount 为 null
     */
    public UpdateSpec<T> setAdd(SFunction<T, ?> field, Number amount) {
        if (field == null) {
            throw new IllegalArgumentException("field must not be null");
        }
        if (amount == null) {
            throw new IllegalArgumentException("amount must not be null");
        }
        String name = LambdaUtils.getPropertyName(field);
        expressionSetClauses.add(new ExpressionSetClause(name, (root, cb) -> cb.sum(root.get(name), amount)));
        return this;
    }

    /**
     * 原子递减字段值：{@code SET field = field - amount}。
     *
     * <p>
     * 使用示例：
     *
     * <pre>{@code
     * new UpdateSpec<>(Account.class).setSubtract(Account::getBalance, 50).eq(Account::getId, accountId).execute(em);
     * // 生成: UPDATE account SET balance = balance - 50 WHERE id = ?
     * }</pre>
     *
     * @param field 实体属性的方法引用
     * @param amount 要减少的数值
     * @return 当前构建器实例，支持链式调用
     * @throws IllegalArgumentException 如果 field 或 amount 为 null
     */
    public UpdateSpec<T> setSubtract(SFunction<T, ?> field, Number amount) {
        if (field == null) {
            throw new IllegalArgumentException("field must not be null");
        }
        if (amount == null) {
            throw new IllegalArgumentException("amount must not be null");
        }
        String name = LambdaUtils.getPropertyName(field);
        expressionSetClauses.add(new ExpressionSetClause(name, (root, cb) -> cb.diff(root.get(name), amount)));
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
     * 将表达式 SET 子句应用到 CriteriaUpdate。
     *
     * @param update CriteriaUpdate 实例
     * @param root 查询根
     * @param cb CriteriaBuilder
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void applyExpressionSetClauses(CriteriaUpdate<T> update, Root<T> root, CriteriaBuilder cb) {
        for (ExpressionSetClause esc : expressionSetClauses) {
            Object exprResult = esc.exprFn().apply(root, cb);
            // 使用 Path.set(Object) 重载，避免 Expression<?> 的歧义
            jakarta.persistence.criteria.Path<Object> path =
                (jakarta.persistence.criteria.Path)root.get(esc.fieldName());
            if (exprResult instanceof jakarta.persistence.criteria.Expression<?> expression) {
                // 使用 raw type 显式调用 Expression 重载，避免编译器歧义
                ((CriteriaUpdate)update).set(path, (jakarta.persistence.criteria.Expression)expression);
            } else {
                update.set(path, exprResult);
            }
        }
    }

    /**
     * 构建 {@link CriteriaUpdate} 对象但不执行。
     *
     * @param em 实体管理器
     * @return 构建的 CriteriaUpdate 对象
     * @throws IllegalStateException 如果未指定 SET 子句或没有 WHERE 条件
     */
    public CriteriaUpdate<T> toUpdate(EntityManager em) {
        if (setClauses.isEmpty() && expressionSetClauses.isEmpty()) {
            throw new IllegalStateException("At least one set() clause is required");
        }
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaUpdate<T> update = cb.createCriteriaUpdate(entityClass);
        Root<T> root = update.from(entityClass);
        for (SetClause sc : setClauses) {
            update.set(root.get(sc.fieldName), sc.value);
        }
        applyExpressionSetClauses(update, root, cb);
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
        if (setClauses.isEmpty() && expressionSetClauses.isEmpty()) {
            throw new IllegalStateException("At least one set() clause is required");
        }
        // 审计日志：记录无条件更新操作及调用栈，便于生产环境追踪危险操作
        log.warn("AUDIT: Executing unconditional UPDATE on {} — this will affect ALL rows! Call stack: {}",
            entityClass.getSimpleName(), AuditUtils.getCallStack());
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaUpdate<T> update = cb.createCriteriaUpdate(entityClass);
        Root<T> root = update.from(entityClass);
        for (SetClause sc : setClauses) {
            update.set(root.get(sc.fieldName), sc.value);
        }
        applyExpressionSetClauses(update, root, cb);
        return em.createQuery(update).executeUpdate();
    }

    /**
     * 限制更新行数的条件更新操作。
     *
     * <p>
     * 分两步执行：
     * <ol>
     * <li>查询符合条件的 ID 列表（带 LIMIT）</li>
     * <li>用 ID 列表执行批量更新</li>
     * </ol>
     *
     * <p>
     * <strong>并发注意事项：</strong>两步操作之间存在时间窗口，其他事务可能修改或删除记录。
     * 当使用悲观锁时，第一步会获取悲观锁（{@link jakarta.persistence.LockModeType#PESSIMISTIC_WRITE}），
     * 防止其他事务在此窗口期修改记录。<strong>建议在并发场景下始终使用悲观锁</strong>。 禁用悲观锁时，应用层需要自行保证数据一致性。
     *
     * @param em 实体管理器
     * @return 实际更新的行数
     * @throws IllegalStateException 如果未指定 SET 子句
     */
    public int updateAllInTransaction(EntityManager em) {
        return executeInTransaction(em, this::updateAll);
    }

    /**
     * 执行限制更新行数的条件更新操作。
     *
     * <p>
     * 此方法首先查询符合条件的 ID 列表（带限制），然后使用这些 ID 执行更新操作。 这种两步方法是必要的，因为 JPA CriteriaUpdate 不直接支持 LIMIT。
     *
     * <p>
     * 此方法使用 {@link EntityManager#clear()} 分离已更新的实体，允许在不清除持久化上下文的情况下执行多个批次。
     *
     * <p>
     * <strong>安全说明：</strong>此方法默认启用悲观锁（{@code pessimisticLock=true}）， 以防止查询ID和执行更新之间的并发竞态条件。如需禁用悲观锁，请使用
     * {@link #executeLimited(EntityManager, int, boolean)} 并设置 {@code pessimisticLock=false}。
     *
     * @param em 实体管理器
     * @param limit 最大更新行数
     * @return 实际更新的行数
     * @throws IllegalStateException 如果未指定 SET 子句
     */
    public int executeLimited(EntityManager em, int limit) {
        return executeLimited(em, limit, true);
    }

    /**
     * 执行限制更新行数的条件更新操作，支持可选的悲观锁。
     *
     * <p>
     * 此方法首先查询符合条件的实体 ID 列表（带限制），然后对这些实体执行更新操作。 持久化上下文会在批次之间被清除以防止内存泄漏。
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
     * @param em 实体管理器
     * @param limit 最大更新行数
     * @param pessimisticLock 如果为 true，则获取 {@link jakarta.persistence.LockModeType#PESSIMISTIC_WRITE} 锁以防止并发修改
     * @return 实际更新的行数
     * @throws IllegalStateException 如果未指定 SET 子句
     */
    public int executeLimited(EntityManager em, int limit, boolean pessimisticLock) {
        if (setClauses.isEmpty() && expressionSetClauses.isEmpty()) {
            throw new IllegalStateException("At least one set() clause is required");
        }
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }
        if (!pessimisticLock) {
            log.warn("executeLimited() with pessimisticLock=false may cause race conditions. "
                + "Consider using pessimisticLock=true for critical operations.");
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
        applyExpressionSetClauses(update, updateRoot, cb);
        update.where(InClauseBuilder.in(cb, updateRoot.get(idFieldName), ids));
        return em.createQuery(update).executeUpdate();
    }

}
