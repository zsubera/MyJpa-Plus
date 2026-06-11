package com.zsubera.jpa.update;

import com.zsubera.jpa.util.EntityClassResolver;
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
import java.util.concurrent.ThreadLocalRandom;
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

    /** 缓存已验证的字段类型，避免重复反射查找。使用大小限制防止内存泄漏。 */
    private static final java.util.concurrent.ConcurrentMap<String, Boolean> NUMERIC_FIELD_CACHE =
        new java.util.concurrent.ConcurrentHashMap<>();

    /** 缓存实体类的 @Version 字段名，避免每次 UPDATE 执行时重复反射遍历类层次。 */
    private static final java.util.concurrent.ConcurrentMap<String, String> VERSION_FIELD_CACHE =
        new java.util.concurrent.ConcurrentHashMap<>();

    /** 缓存最大容量限制 */
    private static final int MAX_CACHE_SIZE = 256;

    /**
     * 缓存驱逐辅助方法：使用概率采样而非全局锁，避免多线程竞争。
     *
     * <p>
     * 当缓存超过 {@link #MAX_CACHE_SIZE} 时，每次检查有 10% 的概率触发驱逐，消除全局锁竞争。
     * 驱逐操作使用 Iterator.remove() 保证并发安全。缓存可能暂时超过 MAX_CACHE_SIZE，
     * 但不会无限增长。
     */
    private static void evictCacheIfNeeded(java.util.concurrent.ConcurrentMap<?, ?> cache) {
        // [FIX] P1-5: 使用同一快照值避免两次 size() 调用之间的竞态窗口
        int currentSize = cache.size();
        if (currentSize > MAX_CACHE_SIZE && ThreadLocalRandom.current().nextInt(10) == 0) {
            int toRemove = currentSize / 2;
            int removed = 0;
            java.util.Iterator<?> it = cache.keySet().iterator();
            while (it.hasNext() && removed < toRemove) {
                it.next();
                it.remove();
                removed++;
            }
        }
    }

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
    private boolean versionIncrementEnabled = false;

    /**
     * 创建指定实体类型的更新规范构建器。
     *
     * @param entityClass 要更新的实体类
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
     * 控制 UPDATE 操作是否自动递增 {@code @Version} 字段。
     *
     * <p>
     * 默认禁用。当实体有 {@code @Version} 字段时，调用 {@code withVersionIncrement(true)} 可启用此行为。 批量 UPDATE
     * 操作中启用版本递增会触发大量 {@code OptimisticLockException}，仅在确实需要时启用。
     *
     * <p>
     * 使用示例：
     *
     * <pre>{@code
     * new UpdateSpec<>(User.class).set(User::getName, "new name").withVersionIncrement(false) // 不递增 version
     *     .eq(User::getId, userId).execute(em);
     * }</pre>
     *
     * @param enabled 是否启用版本自动递增
     * @return 当前构建器实例，支持链式调用
     */
    public UpdateSpec<T> withVersionIncrement(boolean enabled) {
        this.versionIncrementEnabled = enabled;
        return this;
    }

    /**
     * 原子递增字段值：{@code SET field = field + amount}。
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
        // 构建时校验字段类型是否为数值类型
        validateNumericField(name, "setAdd");
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
        // 构建时校验字段类型是否为数值类型
        validateNumericField(name, "setSubtract");
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
        var query = em.createQuery(toUpdate(em));
        applyTimeout(query);
        return query.executeUpdate();
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
     * @throws IllegalStateException 如果未指定 SET 子句，或没有 WHERE 条件且未调用 allowUnconditional(true)
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
        applyVersionIncrement(update, root, cb);
        Predicate[] predicates = buildPredicates(root, cb);
        if (predicates.length == 0 && !allowUnconditional) {
            throw new IllegalStateException("No WHERE conditions specified for UPDATE operation. "
                + "This would update ALL rows in the table. "
                + "If unconditional update is intended, use allowUnconditional(true) then updateAll(EntityManager).");
        }
        if (predicates.length > 0) {
            update.where(cb.and(predicates));
        }
        return update;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void applyVersionIncrement(CriteriaUpdate<T> update, Root<T> root, CriteriaBuilder cb) {
        if (!versionIncrementEnabled) {
            return;
        }
        String versionFieldName = resolveVersionFieldName(entityClass);
        if (versionFieldName != null) {
            ((CriteriaUpdate)update).set(root.get(versionFieldName),
                (jakarta.persistence.criteria.Expression)cb.sum(root.get(versionFieldName), 1));
        }
    }

    /**
     * 解析实体类的 {@code @Version} 字段名，使用静态缓存避免重复反射。
     *
     * @param clazz 实体类
     * @return {@code @Version} 字段名，如果不存在则返回 null
     */
    private static String resolveVersionFieldName(Class<?> clazz) {
        String cacheKey = clazz.getName();
        String cached = VERSION_FIELD_CACHE.get(cacheKey);
        if (cached != null) {
            return "__NONE__".equals(cached) ? null : cached;
        }
        // 使用 computeIfAbsent 原子操作避免缓存竞争
        String result = VERSION_FIELD_CACHE.computeIfAbsent(cacheKey, k -> {
            for (Class<?> c = clazz; c != null && c != Object.class; c = c.getSuperclass()) {
                for (java.lang.reflect.Field f : c.getDeclaredFields()) {
                    if (f.isAnnotationPresent(jakarta.persistence.Version.class)) {
                        return f.getName();
                    }
                }
            }
            return "__NONE__";
        });
        evictCacheIfNeeded(VERSION_FIELD_CACHE);
        return "__NONE__".equals(result) ? null : result;
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
        applyVersionIncrement(update, root, cb);
        var q = em.createQuery(update);
        applyTimeout(q);
        return q.executeUpdate();
    }

    /**
     * 在事务中执行无条件更新，更新该实体的所有行。
     *
     * <p>
     * <strong>安全要求：</strong>必须先调用 {@link #allowUnconditional(boolean)} 显式确认， 否则将抛出
     * {@link IllegalStateException}。此机制防止误调用导致全表数据被意外修改。
     *
     * @param em 实体管理器
     * @return 更新的实体数量
     * @throws IllegalStateException 如果未调用 allowUnconditional(true) 或未指定 SET 子句
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
     * <strong>重要副作用：</strong>{@code em.clear()} 会分离当前事务中<strong>所有</strong>托管实体，
     * 包括调用方在同一事务中持有的其他实体。调用方应在 {@code executeLimited} 返回后重新查询需要的实体。
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
        if (EntityClassResolver.hasCompositeKey(entityClass)) {
            throw new UnsupportedOperationException(
                "executeLimited() does not support entities with composite primary keys (@EmbeddedId or @IdClass). "
                    + "Entity: " + entityClass.getName() + ". "
                    + "Use toUpdate(entityManager).executeInTransaction(entityManager) instead.");
        }
        if (!pessimisticLock) {
            log.warn("executeLimited() with pessimisticLock=false may cause race conditions. "
                + "Consider using pessimisticLock=true for critical operations.");
        }
        CriteriaBuilder cb = em.getCriteriaBuilder();

        // 步骤 1：查询符合条件的ID列表（带LIMIT）
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
        TypedQuery<?> query = em.createQuery(idQuery);
        applyTimeout(query);
        query.setMaxResults(limit);
        if (pessimisticLock) {
            query.setLockMode(LockModeType.PESSIMISTIC_WRITE);
        }
        List<?> ids = query.getResultList();

        if (ids.isEmpty()) {
            return 0;
        }

        // [FIX] P0-1: 清除持久化上下文，分离已查询的实体
        // 这防止持久化上下文持有过期数据，并为批量更新释放内存。
        // 警告：此操作会分离当前事务中的所有托管实体，包括调用者可能持有的其他托管实例。
        // 调用方应在 executeLimited 返回后重新查询需要的实体，或使用独立的 EntityManager。
        log.warn(
            "executeLimited() calling em.clear() — all managed entities in current persistence context will be detached. "
                + "Entity: {}, IDs affected: {}. Re-query any needed entities after this call.",
            entityClass.getSimpleName(), ids.size());
        em.clear();

        // 步骤 2：用ID列表执行更新
        CriteriaUpdate<T> update = cb.createCriteriaUpdate(entityClass);
        Root<T> updateRoot = update.from(entityClass);
        for (SetClause sc : setClauses) {
            update.set(updateRoot.get(sc.fieldName), sc.value);
        }
        applyExpressionSetClauses(update, updateRoot, cb);
        applyVersionIncrement(update, updateRoot, cb);
        update.where(InClauseBuilder.in(cb, updateRoot.get(idFieldName), ids));
        var uq = em.createQuery(update);
        applyTimeout(uq);
        return uq.executeUpdate();
    }

    /**
     * 校验指定字段是否为数值类型。
     *
     * @param fieldName 要校验的字段名
     * @param operation 操作名称，用于错误消息
     * @throws IllegalArgumentException 如果字段不是数值类型
     */
    private void validateNumericField(String fieldName, String operation) {
        // 使用缓存的校验结果，避免重复反射
        String cacheKey = entityClass.getName() + "#" + fieldName;
        Boolean cachedResult = NUMERIC_FIELD_CACHE.get(cacheKey);
        if (cachedResult != null) {
            if (!cachedResult) {
                throw new IllegalArgumentException(
                    operation + "() requires a numeric field, but field '" + fieldName + "' in "
                        + entityClass.getSimpleName() + " is not numeric." + " Use set() for non-numeric fields.");
            }
            return;
        }
        // 使用 computeIfAbsent 原子操作避免缓存竞争
        Boolean isNumeric = NUMERIC_FIELD_CACHE.computeIfAbsent(cacheKey, k -> {
            for (Class<?> c = entityClass; c != null && c != Object.class; c = c.getSuperclass()) {
                try {
                    java.lang.reflect.Field f = c.getDeclaredField(fieldName);
                    Class<?> type = f.getType();
                    return Number.class.isAssignableFrom(type) || type == int.class || type == long.class
                        || type == double.class || type == float.class || type == short.class || type == byte.class;
                } catch (NoSuchFieldException e) {
                    // 继续检查父类
                }
            }
            return false;
        });
        evictCacheIfNeeded(NUMERIC_FIELD_CACHE);
        if (!isNumeric) {
            // 尝试获取字段类型用于错误消息
            String fieldType = "unknown";
            for (Class<?> c = entityClass; c != null && c != Object.class; c = c.getSuperclass()) {
                try {
                    java.lang.reflect.Field f = c.getDeclaredField(fieldName);
                    fieldType = f.getType().getSimpleName();
                    break;
                } catch (NoSuchFieldException e) {
                    // 继续检查父类
                }
            }
            if ("unknown".equals(fieldType)) {
                throw new IllegalArgumentException(operation + "() cannot validate field '" + fieldName + "' in "
                    + entityClass.getSimpleName() + ". Field not found via reflection. "
                    + "Ensure the field exists as a direct class field (not a getter-only property). "
                    + "For computed expressions, use setExpr() instead.");
            } else {
                throw new IllegalArgumentException(operation + "() requires a numeric field, but field '" + fieldName
                    + "' in " + entityClass.getSimpleName() + " has type: " + fieldType
                    + ". Use set() for non-numeric fields.");
            }
        }
    }

}
