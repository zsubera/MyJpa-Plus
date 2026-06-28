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

    /** 缓存最大容量限制 */
    private static final int MAX_CACHE_SIZE = 256;

    /** 缓存已验证的字段类型，避免重复反射查找。使用采样驱逐防止内存泄漏。 */
    private static final com.zsubera.jpa.util.SampledEvictionCache<String, Boolean> NUMERIC_FIELD_CACHE =
        new com.zsubera.jpa.util.SampledEvictionCache<>(MAX_CACHE_SIZE, 0.75, 100, 64);

    /** 缓存实体类的 @Version 字段名，避免每次 UPDATE 执行时重复反射遍历类层次。 */
    private static final com.zsubera.jpa.util.SampledEvictionCache<String, String> VERSION_FIELD_CACHE =
        new com.zsubera.jpa.util.SampledEvictionCache<>(MAX_CACHE_SIZE, 0.75, 100, 64);

    /** 记录已知无 @Version 字段的实体类，避免哨兵值碰撞风险。 */
    private static final com.zsubera.jpa.util.SampledEvictionCache<String, Boolean> NO_VERSION_CACHE =
        new com.zsubera.jpa.util.SampledEvictionCache<>(MAX_CACHE_SIZE, 0.75, 100, 64);

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
     * <p>
     * <strong>安全保护：</strong>如果配置了最大批量操作行数限制，执行前会先计数验证，
     * 超过限制时抛出 {@link IllegalStateException} 阻止执行。
     *
     * @param em 实体管理器
     * @return 更新的实体数量
     * @throws IllegalStateException 如果未指定 SET 子句或超过最大行数限制
     * @throws jakarta.persistence.TransactionRequiredException 如果没有活动事务
     */
    @Override
    public int execute(EntityManager em) {
        return executeWithLimitCheck(em, "UPDATE", e -> {
            CriteriaUpdate<T> update = toUpdate(e);
            if (log.isDebugEnabled()) {
                log.debug("Executing UPDATE on {} with {} set clauses and {} conditions", entityClass.getSimpleName(),
                    setClauses.size() + expressionSetClauses.size(), conditionNodes.size());
            }
            return e.createQuery(update).executeUpdate();
        });
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
        CriteriaUpdate<T> update = buildCriteriaUpdate(em);
        Predicate[] predicates = buildPredicates(update.getRoot(), em.getCriteriaBuilder());
        if (predicates.length == 0 && !allowUnconditional) {
            throw new IllegalStateException("No WHERE conditions specified for UPDATE operation. "
                + "This would update ALL rows in the table. "
                + "If unconditional update is intended, use allowUnconditional(true) then updateAll(EntityManager).");
        }
        if (predicates.length > 0) {
            update.where(em.getCriteriaBuilder().and(predicates));
        }
        return update;
    }

    /**
     * 构建包含 SET 子句、表达式子句和版本递增的 CriteriaUpdate（不含 WHERE 条件）。
     */
    private CriteriaUpdate<T> buildCriteriaUpdate(EntityManager em) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaUpdate<T> update = cb.createCriteriaUpdate(entityClass);
        Root<T> root = update.from(entityClass);
        for (SetClause sc : setClauses) {
            update.set(root.get(sc.fieldName), sc.value);
        }
        applyExpressionSetClauses(update, root, cb);
        applyVersionIncrement(update, root, cb);
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
        if (Boolean.TRUE.equals(NO_VERSION_CACHE.get(cacheKey))) {
            return null;
        }
        String cached = VERSION_FIELD_CACHE.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        for (Class<?> c = clazz; c != null && c != Object.class; c = c.getSuperclass()) {
            for (java.lang.reflect.Field f : c.getDeclaredFields()) {
                if (f.isAnnotationPresent(jakarta.persistence.Version.class)) {
                    String result = f.getName();
                    VERSION_FIELD_CACHE.put(cacheKey, result);
                    return result;
                }
            }
        }
        NO_VERSION_CACHE.put(cacheKey, Boolean.TRUE);
        return null;
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
        log.warn("AUDIT: Executing unconditional UPDATE on {} — this will affect ALL rows! Call stack: {}",
            entityClass.getSimpleName(), AuditUtils.getCallStack());
        if (log.isDebugEnabled()) {
            log.debug("Executing unconditional UPDATE on {} with {} set clauses", entityClass.getSimpleName(),
                setClauses.size() + expressionSetClauses.size());
        }
        CriteriaUpdate<T> update = buildCriteriaUpdate(em);
        var q = em.createQuery(update);
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
     * <strong>⚠️ 重要副作用：</strong>{@code em.clear()} 会分离当前事务中<strong>所有</strong>托管实体，
     * 包括调用方在同一事务中持有的其他实体。调用方应在 {@code executeLimited} 返回后重新查询需要的实体。
     * 此副作用在 {@link DeleteSpec#executeLimited} 中同样存在。
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
        int globalMax = resolveMaxBulkOperationRows();
        if (globalMax > 0 && limit > globalMax) {
            throw new IllegalArgumentException("limit (" + limit + ") exceeds global max (" + globalMax
                + "). Adjust myjpa-plus.query.max-bulk-operation-rows or use a smaller limit.");
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
        query.setMaxResults(limit);
        if (pessimisticLock) {
            query.setLockMode(LockModeType.PESSIMISTIC_WRITE);
        }
        List<?> ids = query.getResultList();

        if (ids.isEmpty()) {
            return 0;
        }

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
        // ponytail: TOCTOU 防护——SELECT 和 UPDATE 在同一 RESOURCE_LOCAL 事务中执行。
        // SELECT 使用 PESSIMISTIC_WRITE 锁定匹配行，事务内的 UPDATE 会等待这些行锁释放，
        // 有效序列化 SELECT 和 UPDATE 操作。当 pessimisticLock=false 时无此保护，
        // 并发事务可能在 SELECT 和 UPDATE 之间修改/删除行。
        int updated = uq.executeUpdate();
        // 选择性失效 L1 缓存：仅驱逐当前实体类型的缓存数据，
        // 避免 em.clear() 脱管同一事务中调用方持有的其他实体。
        com.zsubera.jpa.util.CacheEvictionHelper.evictEntityCache(em, entityClass);
        return updated;
    }

    /**
     * 校验指定字段是否为数值类型。
     *
     * @param fieldName 要校验的字段名
     * @param operation 操作名称，用于错误消息
     * @throws IllegalArgumentException 如果字段不是数值类型
     */
    private void validateNumericField(String fieldName, String operation) {
        String cacheKey = entityClass.getName() + "#" + fieldName;
        Boolean cachedResult = NUMERIC_FIELD_CACHE.get(cacheKey);
        if (cachedResult != null) {
            if (!cachedResult) {
                java.lang.reflect.Field f = resolveFieldFromClassHierarchy(fieldName);
                if (f == null) {
                    throw new IllegalArgumentException(operation + "() cannot resolve field '" + fieldName + "' in "
                        + entityClass.getSimpleName() + ". The field does not exist as a declared class field. "
                        + "Check the field name spelling or ensure it is declared (not inherited via getter-only).");
                }
                throw new IllegalArgumentException(operation + "() requires a numeric field, but field '" + fieldName
                    + "' in " + entityClass.getSimpleName() + " has type: " + f.getType().getSimpleName()
                    + ". Use set() for non-numeric fields.");
            }
            return;
        }
        java.lang.reflect.Field resolvedField = resolveFieldFromClassHierarchy(fieldName);
        Boolean isNumeric = NUMERIC_FIELD_CACHE.computeIfAbsent(cacheKey, k -> {
            if (resolvedField == null) {
                return false;
            }
            Class<?> type = resolvedField.getType();
            return Number.class.isAssignableFrom(type) || type == int.class || type == long.class
                || type == double.class || type == float.class || type == short.class || type == byte.class;
        });
        if (!isNumeric) {
            if (resolvedField == null) {
                throw new IllegalArgumentException(operation + "() cannot resolve field '" + fieldName + "' in "
                    + entityClass.getSimpleName() + ". The field does not exist as a declared class field. "
                    + "Check the field name spelling or ensure it is declared (not inherited via getter-only).");
            } else {
                throw new IllegalArgumentException(operation + "() requires a numeric field, but field '" + fieldName
                    + "' in " + entityClass.getSimpleName() + " has type: " + resolvedField.getType().getSimpleName()
                    + ". Use set() for non-numeric fields.");
            }
        }
    }

    /**
     * 选择性驱逐实体类型的 L1 缓存。优先使用 Hibernate 的 SessionFactory 缓存驱逐（仅影响指定实体类型），
     * 非 Hibernate 环境回退到 {@code em.clear()}（会影响所有托管实体）。
     */
    public static void evictEntityCache(EntityManager em, Class<?> entityClass) {
        com.zsubera.jpa.util.CacheEvictionHelper.evictEntityCache(em, entityClass);
    }

    private java.lang.reflect.Field resolveFieldFromClassHierarchy(String fieldName) {
        for (Class<?> c = entityClass; c != null && c != Object.class; c = c.getSuperclass()) {
            try {
                return c.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                // 继续检查父类
            }
        }
        return null;
    }

}
