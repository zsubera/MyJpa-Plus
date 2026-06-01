package com.zsubera.jpa.update;

import com.zsubera.jpa.exception.MyJpaPlusException;
import com.zsubera.jpa.spec.PredicateHelper;
import com.zsubera.jpa.spec.SFunction;
import com.zsubera.jpa.util.LambdaUtils;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * JPA 批量操作构建器（{@link UpdateSpec} 和 {@link DeleteSpec}）的抽象基类。
 *
 * <p>
 * 使用延迟 Lambda 求值提供通用条件方法。谓词构造委托给 {@link PredicateHelper} 以与其他组件共享逻辑。
 *
 * <p>
 * <strong>设计说明：</strong>此抽象类独立提供条件方法（eq、ne、gt 等），而非实现 {@link com.zsubera.jpa.spec.ConditionBuilder} 接口。原因是两者使用不同的求值模型：
 * <ul>
 * <li>{@code ConditionBuilder}：延迟执行——构建 {@link com.zsubera.jpa.spec.ConditionNode} 树，在查询时统一求值
 * <li>{@code AbstractBulkOperationSpec}：延迟 Lambda——直接构建 {@code BiFunction<Root, CriteriaBuilder, Predicate>}
 * </ul>
 *
 * <p>
 * 条件方法的签名和行为与 {@link com.zsubera.jpa.spec.ConditionBuilder} 保持一致。 新增条件类型时，需同步更新以下位置：
 * <ol>
 * <li>{@link com.zsubera.jpa.spec.ConditionBuilder} — 查询构建器</li>
 * <li>{@link com.zsubera.jpa.spec.ConditionNode.Op} — 运算符枚举</li>
 * <li>{@link com.zsubera.jpa.spec.QuerySpec#resolveSimple} — 查询条件解析</li>
 * <li>{@link com.zsubera.jpa.projection.ProjectionSpec.ProjectionJoinGroup} — 投影 JOIN 条件</li>
 * <li>此类（AbstractBulkOperationSpec）— 批量操作条件</li>
 * <li>{@link com.zsubera.jpa.spec.SubQuerySpec} — 子查询条件</li>
 * </ol>
 *
 * @param <T> 实体类型
 * @param <SELF> 具体构建器类型，用于流式链式调用
 */
public abstract class AbstractBulkOperationSpec<T, SELF extends AbstractBulkOperationSpec<T, SELF>> {

    private static final Logger log = LoggerFactory.getLogger(AbstractBulkOperationSpec.class);

    protected final Class<T> entityClass;
    protected final List<BulkConditionNode> conditionNodes = new ArrayList<>();

    /**
     * 构造函数，初始化实体类类型。
     *
     * @param entityClass 实体类类型
     */
    protected AbstractBulkOperationSpec(Class<T> entityClass) {
        this.entityClass = entityClass;
    }

    /**
     * 返回当前构建器实例，用于链式调用。
     *
     * @return 当前构建器实例
     */
    @SuppressWarnings("unchecked")
    protected SELF self() {
        return (SELF)this;
    }

    /**
     * 从 Lambda 方法引用中提取属性名称。
     *
     * @param field 实体属性的 Lambda 方法引用
     * @return 属性名称
     * @throws IllegalArgumentException 如果 field 为 null
     */
    protected String property(SFunction<T, ?> field) {
        if (field == null) {
            throw new IllegalArgumentException("field must not be null");
        }
        return LambdaUtils.getPropertyName(field);
    }

    /**
     * Executes the bulk operation within a new transaction if none is active, otherwise executes within the current
     * transaction.
     *
     * @param em the EntityManager
     * @return the number of affected rows
     */
    public int executeInTransaction(EntityManager em) {
        return executeInTransaction(em, this::doExecute);
    }

    /**
     * Executes the given operation within a new transaction if none is active, otherwise executes within the current
     * transaction.
     *
     * <p>
     * This overload allows subclasses to execute custom operations (e.g., unconditional deleteAll) with proper
     * transaction management.
     *
     * @param em the EntityManager
     * @param operation the operation to execute
     * @return the number of affected rows
     */
    protected int executeInTransaction(EntityManager em, Function<EntityManager, Integer> operation) {
        // Check if Spring manages the transaction first (container-managed JTA or Spring tx)
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            return operation.apply(em);
        }
        // No Spring transaction — use JPA's EntityTransaction for standalone scenarios.
        EntityTransaction tx = em.getTransaction();
        if (tx == null) {
            return executeInJtaEnvironment(em, operation);
        }
        return executeWithEntityTransaction(em, tx, operation);
    }

    /**
     * 在 JTA 环境中执行操作。
     *
     * @param em 实体管理器
     * @param operation 要执行的操作
     * @return 受影响的行数
     */
    private int executeInJtaEnvironment(EntityManager em, Function<EntityManager, Integer> operation) {
        if (log.isDebugEnabled()) {
            log.debug(
                "JTA environment detected (getTransaction() returned null), executing with container-managed transaction");
        }
        try {
            return operation.apply(em);
        } catch (jakarta.persistence.TransactionRequiredException e) {
            throw new MyJpaPlusException("No active transaction in JTA environment. "
                + "Ensure a container-managed transaction is active, or use @Transactional annotation.", e);
        }
    }

    /**
     * 使用 JPA EntityTransaction 执行操作。
     *
     * @param em 实体管理器
     * @param tx 实体事务
     * @param operation 要执行的操作
     * @return 受影响的行数
     */
    private int executeWithEntityTransaction(EntityManager em, EntityTransaction tx,
        Function<EntityManager, Integer> operation) {
        boolean isNewTransaction = !tx.isActive();
        if (isNewTransaction) {
            tx.begin();
        }
        try {
            int result = operation.apply(em);
            if (isNewTransaction) {
                tx.commit();
            }
            return result;
        } catch (RuntimeException e) {
            if (isNewTransaction) {
                rollbackIfActive(tx, e);
            }
            throw e;
        } catch (Exception e) {
            if (isNewTransaction) {
                rollbackIfActive(tx, e);
            }
            log.error("Unexpected checked exception in bulk operation (type: {}): {}", e.getClass().getName(),
                e.getMessage(), e);
            throw new MyJpaPlusException("Bulk operation failed: " + e.getClass().getSimpleName(), e);
        }
    }

    /**
     * 如果事务处于活动状态则回滚，并将回滚异常添加为原始异常的抑制异常。
     *
     * @param tx 实体事务
     * @param original 原始异常
     */
    private void rollbackIfActive(EntityTransaction tx, Exception original) {
        if (tx.isActive()) {
            try {
                tx.rollback();
            } catch (Exception rollbackEx) {
                original.addSuppressed(rollbackEx);
            }
        }
    }

    /**
     * 执行批量操作。要求底层 {@link EntityManager} 中存在活动事务。
     *
     * <p>
     * <strong>注意：</strong>此方法不会自动管理事务。调用方需确保：
     * <ul>
     * <li>在调用前开启事务</li>
     * <li>在调用后提交或回滚事务</li>
     * <li>捕获异常并处理事务回滚</li>
     * </ul>
     *
     * <p>
     * 当在已有活动事务的环境中调用时（嵌套调用），此方法直接执行操作但不提交也不回滚。 操作失败时异常会向上传播，由外层事务管理器处理。如需自动事务管理，请使用
     * {@link #executeInTransaction(EntityManager)} 方法。
     *
     * @param em 实体管理器
     * @return 受影响的行数
     * @throws jakarta.persistence.TransactionRequiredException 如果没有活动事务
     * @see #executeInTransaction(EntityManager)
     */
    public abstract int execute(EntityManager em);

    /**
     * 执行实际的批量操作逻辑，由子类实现。
     *
     * @param em 实体管理器
     * @return 受影响的行数
     */
    protected abstract int doExecute(EntityManager em);

    /** 批量操作条件树的密封节点类型。支持 AND（默认）、OR、NOT 和叶子谓词节点。 */
    sealed interface BulkConditionNode {
        /** 叶子谓词函数节点。 */
        record LeafNode(BiFunction<Root<?>, CriteriaBuilder, Predicate> fn) implements BulkConditionNode {
        }

        /** AND 子节点组。 */
        @SuppressFBWarnings({"EI_EXPOSE_REP", "EI_EXPOSE_REP2"})
        record AndNode(List<BulkConditionNode> children) implements BulkConditionNode {
        }

        /** OR 子节点组。 */
        @SuppressFBWarnings({"EI_EXPOSE_REP", "EI_EXPOSE_REP2"})
        record OrNode(List<BulkConditionNode> children) implements BulkConditionNode {
        }

        /** NOT 包装节点。 */
        record NotNode(BulkConditionNode child) implements BulkConditionNode {
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private BulkConditionNode leaf(BiFunction<Root<T>, CriteriaBuilder, Predicate> fn) {
        return new BulkConditionNode.LeafNode((BiFunction)fn);
    }

    /**
     * Adds an OR group of conditions. All conditions added inside the consumer will be combined with OR instead of AND.
     *
     * <p>
     * Example:
     *
     * <pre>{@code
     * new DeleteSpec<>(User.class).or(o -> o.eq(User::getStatus, "INACTIVE").eq(User::getStatus, "SUSPENDED"))
     *     .execute();
     * // WHERE (status = 'INACTIVE' OR status = 'SUSPENDED')
     * }</pre>
     */
    public SELF or(Consumer<OrConditionBuilder<T, SELF>> config) {
        if (config == null) {
            throw new IllegalArgumentException("config must not be null");
        }
        List<BulkConditionNode> children = new ArrayList<>();
        config.accept(new OrConditionBuilder<>(self(), children));
        conditionNodes.add(new BulkConditionNode.OrNode(List.copyOf(children)));
        return self();
    }

    /**
     * 对内部条件组取反。通过 {@link OrConditionBuilder} 添加的所有条件将以 OR 方式组合，然后整体取反（NOT）。
     *
     * <pre>{@code
     * // 示例: 删除状态不是 ACTIVE 的记录
     * deleteSpec.not(not -> not.eq(User::getStatus, Status.ACTIVE));
     *
     * // 示例: 删除既不是 ACTIVE 也不是 PENDING 的记录
     * deleteSpec.not(not -> not.eq(User::getStatus, Status.ACTIVE).eq(User::getStatus, Status.PENDING));
     * }</pre>
     *
     * @param config 配置函数，接收 {@link OrConditionBuilder} 以添加取反条件
     * @return 当前构建器实例，支持链式调用
     * @throws IllegalArgumentException 如果 {@code config} 为 null
     */
    public SELF not(Consumer<OrConditionBuilder<T, SELF>> config) {
        if (config == null) {
            throw new IllegalArgumentException("config must not be null");
        }
        List<BulkConditionNode> children = new ArrayList<>();
        config.accept(new OrConditionBuilder<>(self(), children));
        BulkConditionNode combined =
            children.size() == 1 ? children.get(0) : new BulkConditionNode.OrNode(List.copyOf(children));
        conditionNodes.add(new BulkConditionNode.NotNode(combined));
        return self();
    }

    /**
     * 添加等于条件：{@code field = value}。
     *
     * @param field 实体属性引用
     * @param value 比较值
     * @return 当前构建器实例
     */
    public SELF eq(SFunction<T, ?> field, @Nullable Object value) {
        String name = property(field);
        conditionNodes.add(leaf((root, cb) -> PredicateHelper.eq(root, name, value, cb)));
        return self();
    }

    /**
     * 添加不等于条件：{@code field != value}。
     *
     * @param field 实体属性引用
     * @param value 比较值
     * @return 当前构建器实例
     */
    public SELF ne(SFunction<T, ?> field, @Nullable Object value) {
        String name = property(field);
        conditionNodes.add(leaf((root, cb) -> PredicateHelper.ne(root, name, value, cb)));
        return self();
    }

    /**
     * 添加大于条件：{@code field > value}。
     *
     * @param field 实体属性引用
     * @param value 比较值
     * @return 当前构建器实例
     * @throws IllegalArgumentException 如果 value 为 null
     */
    public SELF gt(SFunction<T, ?> field, Comparable<?> value) {
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
        String name = property(field);
        conditionNodes.add(leaf((root, cb) -> PredicateHelper.gt(root, name, value, cb)));
        return self();
    }

    /**
     * 添加大于等于条件：{@code field >= value}。
     *
     * @param field 实体属性引用
     * @param value 比较值
     * @return 当前构建器实例
     * @throws IllegalArgumentException 如果 value 为 null
     */
    public SELF ge(SFunction<T, ?> field, Comparable<?> value) {
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
        String name = property(field);
        conditionNodes.add(leaf((root, cb) -> PredicateHelper.ge(root, name, value, cb)));
        return self();
    }

    /**
     * 添加小于条件：{@code field < value}。
     *
     * @param field 实体属性引用
     * @param value 比较值
     * @return 当前构建器实例
     * @throws IllegalArgumentException 如果 value 为 null
     */
    public SELF lt(SFunction<T, ?> field, Comparable<?> value) {
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
        String name = property(field);
        conditionNodes.add(leaf((root, cb) -> PredicateHelper.lt(root, name, value, cb)));
        return self();
    }

    /**
     * 添加小于等于条件：{@code field <= value}。
     *
     * @param field 实体属性引用
     * @param value 比较值
     * @return 当前构建器实例
     * @throws IllegalArgumentException 如果 value 为 null
     */
    public SELF le(SFunction<T, ?> field, Comparable<?> value) {
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
        String name = property(field);
        conditionNodes.add(leaf((root, cb) -> PredicateHelper.le(root, name, value, cb)));
        return self();
    }

    /**
     * 添加 LIKE 条件：{@code field LIKE value}。
     *
     * <p>
     * <b>安全警告</b>: 此方法不转义 {@code %} 和 {@code _} 通配符。如果 {@code value} 来自用户输入， 请使用 {@link #likeSafe(SFunction, String)}
     * 方法，该方法会自动转义通配符。
     * </p>
     *
     * @param field 实体属性引用
     * @param value 匹配模式
     * @return 当前构建器实例
     * @throws IllegalArgumentException 如果 value 为 null
     * @see #likeSafe(SFunction, String)
     * @deprecated 使用 {@link #likeSafe(SFunction, String)} 替代，该方法会自动转义通配符防止 LIKE 注入
     */
    @Deprecated(since = "1.1.0", forRemoval = true)
    public SELF like(SFunction<T, ?> field, String value) {
        throw new UnsupportedOperationException(
            "like() 已在 1.1.0 版本移除，请使用 likeSafe()、contains()、startsWith() 或 endsWith()。");
    }

    /**
     * 添加 NOT LIKE 条件：{@code field NOT LIKE value}。
     *
     * <p>
     * <b>安全警告</b>: 此方法不转义 {@code %} 和 {@code _} 通配符。如果 {@code value} 来自用户输入， 请使用
     * {@link #notLikeSafe(SFunction, String)} 方法，该方法会自动转义通配符。
     * </p>
     *
     * @param field 实体属性引用
     * @param value 匹配模式
     * @return 当前构建器实例
     * @throws IllegalArgumentException 如果 value 为 null
     * @see #notLikeSafe(SFunction, String)
     * @deprecated 使用 {@link #notLikeSafe(SFunction, String)} 替代，该方法会自动转义通配符防止 LIKE 注入
     */
    @Deprecated(since = "1.1.0", forRemoval = true)
    public SELF notLike(SFunction<T, ?> field, String value) {
        throw new UnsupportedOperationException("notLike() 已在 1.1.0 版本移除，请使用 notLikeSafe() 或其他安全方法。");
    }

    /**
     * 添加带自动通配符转义的 LIKE 条件：{@code field LIKE value}。 值中的 {@code %} 或 {@code _} 字符会被转义，作为字面量处理。
     *
     * <p>
     * 此方法是 {@link #like(SFunction, String)} 的安全版本，适用于处理用户输入。
     *
     * @param field 实体属性引用
     * @param value 要匹配的原始字符串值（通配符会被转义）
     * @return 当前构建器实例
     * @throws IllegalArgumentException 如果 value 为 null
     * @see #like(SFunction, String)
     */
    public SELF likeSafe(SFunction<T, ?> field, String value) {
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
        String name = property(field);
        conditionNodes.add(leaf((root, cb) -> PredicateHelper.like(root, name,
            PredicateHelper.escapeLikeWildcards(value), cb, PredicateHelper.LIKE_ESCAPE_CHAR)));
        return self();
    }

    /**
     * 添加带自动通配符转义的 NOT LIKE 条件：{@code field NOT LIKE value}。 值中的 {@code %} 或 {@code _} 字符会被转义，作为字面量处理。
     *
     * <p>
     * 此方法是 {@link #notLike(SFunction, String)} 的安全版本，适用于处理用户输入。
     *
     * @param field 实体属性引用
     * @param value 要匹配的原始字符串值（通配符会被转义）
     * @return 当前构建器实例
     * @throws IllegalArgumentException 如果 value 为 null
     * @see #notLike(SFunction, String)
     */
    public SELF notLikeSafe(SFunction<T, ?> field, String value) {
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
        String name = property(field);
        conditionNodes.add(leaf((root, cb) -> PredicateHelper.notLike(root, name,
            PredicateHelper.escapeLikeWildcards(value), cb, PredicateHelper.LIKE_ESCAPE_CHAR)));
        return self();
    }

    /**
     * 添加前缀匹配条件：{@code field LIKE 'value%'}。
     *
     * @param field 实体属性引用
     * @param value 前缀值
     * @return 当前构建器实例
     * @throws IllegalArgumentException 如果 value 为 null
     */
    public SELF startsWith(SFunction<T, ?> field, String value) {
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
        String name = property(field);
        conditionNodes.add(leaf((root, cb) -> PredicateHelper.startsWith(root, name, value, cb)));
        return self();
    }

    /**
     * 添加后缀匹配条件：{@code field LIKE '%value'}。
     *
     * @param field 实体属性引用
     * @param value 后缀值
     * @return 当前构建器实例
     * @throws IllegalArgumentException 如果 value 为 null
     */
    public SELF endsWith(SFunction<T, ?> field, String value) {
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
        String name = property(field);
        conditionNodes.add(leaf((root, cb) -> PredicateHelper.endsWith(root, name, value, cb)));
        return self();
    }

    /**
     * 添加包含条件：{@code field LIKE '%value%'}。
     *
     * @param field 实体属性引用
     * @param value 包含的值
     * @return 当前构建器实例
     * @throws IllegalArgumentException 如果 value 为 null
     */
    public SELF contains(SFunction<T, ?> field, String value) {
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
        String name = property(field);
        conditionNodes.add(leaf((root, cb) -> PredicateHelper.contains(root, name, value, cb)));
        return self();
    }

    /**
     * 添加忽略大小写的等于条件：{@code UPPER(field) = UPPER(value)}。
     *
     * @param field 实体属性引用
     * @param value 比较值
     * @return 当前构建器实例
     */
    public SELF eqIgnoreCase(SFunction<T, ?> field, String value) {
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
        String name = property(field);
        conditionNodes.add(leaf((root, cb) -> PredicateHelper.eqIgnoreCase(root, name, value, cb)));
        return self();
    }

    /**
     * 添加忽略大小写的不等于条件：{@code UPPER(field) <> UPPER(value)}。 与 {@link #eqIgnoreCase} 对称。
     *
     * @param field 实体属性引用
     * @param value 比较值
     * @return 当前构建器实例
     */
    public SELF neIgnoreCase(SFunction<T, ?> field, String value) {
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
        String name = property(field);
        conditionNodes.add(leaf((root, cb) -> PredicateHelper.neIgnoreCase(root, name, value, cb)));
        return self();
    }

    /**
     * 添加忽略大小写的 LIKE 条件：{@code UPPER(field) LIKE UPPER('%value%')}。
     *
     * <p>
     * 值中的 {@code %} 或 {@code _} 字符会被转义，作为字面量处理，防止 LIKE 注入。
     *
     * @param field 实体属性引用
     * @param value 要匹配的原始字符串值（通配符会被转义）
     * @return 当前构建器实例
     * @throws IllegalArgumentException 如果 value 为 null
     */
    public SELF likeIgnoreCase(SFunction<T, ?> field, String value) {
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
        String name = property(field);
        String escaped = PredicateHelper.escapeLikeWildcards(value);
        conditionNodes.add(leaf((root, cb) -> PredicateHelper.likeIgnoreCase(root, name, "%" + escaped + "%", cb,
            PredicateHelper.LIKE_ESCAPE_CHAR)));
        return self();
    }

    /**
     * 添加 IN 条件：{@code field IN (values)}。
     *
     * @param field 实体属性引用
     * @param values 值数组
     * @return 当前构建器实例
     * @throws IllegalArgumentException 如果 values 为 null 或空
     */
    public SELF in(SFunction<T, ?> field, Object... values) {
        String name = property(field);
        if (values == null || values.length == 0) {
            throw new IllegalArgumentException("values must not be empty");
        }
        conditionNodes.add(leaf((root, cb) -> PredicateHelper.in(root, name, values, cb)));
        return self();
    }

    /**
     * 添加 NOT IN 条件：{@code field NOT IN (values)}。
     *
     * @param field 实体属性引用
     * @param values 值数组
     * @return 当前构建器实例
     * @throws IllegalArgumentException 如果 values 为 null 或空
     */
    public SELF notIn(SFunction<T, ?> field, Object... values) {
        String name = property(field);
        if (values == null || values.length == 0) {
            throw new IllegalArgumentException("values must not be empty");
        }
        conditionNodes.add(leaf((root, cb) -> PredicateHelper.notIn(root, name, values, cb)));
        return self();
    }

    /**
     * 添加 IN 条件：{@code field IN (values)}。
     *
     * @param field 实体属性引用
     * @param values 值集合
     * @return 当前构建器实例
     * @throws IllegalArgumentException 如果 values 为 null 或空
     */
    public SELF in(SFunction<T, ?> field, Collection<?> values) {
        String name = property(field);
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException("values must not be empty");
        }
        conditionNodes.add(leaf((root, cb) -> PredicateHelper.in(root, name, values, cb)));
        return self();
    }

    /**
     * 添加 NOT IN 条件：{@code field NOT IN (values)}。
     *
     * @param field 实体属性引用
     * @param values 值集合
     * @return 当前构建器实例
     * @throws IllegalArgumentException 如果 values 为 null 或空
     */
    public SELF notIn(SFunction<T, ?> field, Collection<?> values) {
        String name = property(field);
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException("values must not be empty");
        }
        conditionNodes.add(leaf((root, cb) -> PredicateHelper.notIn(root, name, values, cb)));
        return self();
    }

    /**
     * 添加 BETWEEN 条件：{@code field BETWEEN start AND end}。
     *
     * @param field 实体属性引用
     * @param start 范围起始值
     * @param end 范围结束值
     * @return 当前构建器实例
     * @throws IllegalArgumentException 如果 start 或 end 为 null，或 start 大于 end
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public SELF between(SFunction<T, ?> field, Comparable<?> start, Comparable<?> end) {
        PredicateHelper.validateRange(start, end);
        String name = property(field);
        conditionNodes.add(leaf((root, cb) -> PredicateHelper.between(root, name, start, end, cb)));
        return self();
    }

    /**
     * 添加 NOT BETWEEN 条件：{@code field NOT BETWEEN start AND end}。
     *
     * @param field 实体属性引用
     * @param start 范围起始值
     * @param end 范围结束值
     * @return 当前构建器实例
     * @throws IllegalArgumentException 如果 start 或 end 为 null，或 start 大于 end
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public SELF notBetween(SFunction<T, ?> field, Comparable<?> start, Comparable<?> end) {
        PredicateHelper.validateRange(start, end);
        String name = property(field);
        conditionNodes.add(leaf((root, cb) -> PredicateHelper.notBetween(root, name, start, end, cb)));
        return self();
    }

    /**
     * 添加 IS NULL 条件：{@code field IS NULL}。
     *
     * @param field 实体属性引用
     * @return 当前构建器实例
     */
    public SELF isNull(SFunction<T, ?> field) {
        String name = property(field);
        conditionNodes.add(leaf((root, cb) -> PredicateHelper.isNull(root, name, cb)));
        return self();
    }

    /**
     * 添加 IS NOT NULL 条件：{@code field IS NOT NULL}。
     *
     * @param field 实体属性引用
     * @return 当前构建器实例
     */
    public SELF isNotNull(SFunction<T, ?> field) {
        String name = property(field);
        conditionNodes.add(leaf((root, cb) -> PredicateHelper.isNotNull(root, name, cb)));
        return self();
    }

    /**
     * 添加 EXISTS 子查询条件。
     *
     * @param subEntity 子查询实体类型
     * @param config 子查询配置消费者
     * @param <S> 子查询实体类型
     * @return 当前构建器实例
     * @throws IllegalArgumentException 如果任何参数为 null
     */
    public <S> SELF exists(Class<S> subEntity, Consumer<com.zsubera.jpa.spec.SubQuerySpec<S>> config) {
        if (subEntity == null) {
            throw new IllegalArgumentException("subEntity must not be null");
        }
        if (config == null) {
            throw new IllegalArgumentException("config must not be null");
        }
        conditionNodes.add(leaf((root, cb) -> {
            jakarta.persistence.criteria.CriteriaQuery<?> tempQuery = cb.createQuery(entityClass);
            jakarta.persistence.criteria.Subquery<S> subquery = tempQuery.subquery(subEntity);
            Root<S> subRoot = subquery.from(subEntity);
            // P0-2: Use subquery.correlate() to establish correct correlation with outer query
            Root<?> correlatedOuter = subquery.correlate(root);
            com.zsubera.jpa.spec.SubQuerySpec<S> subSpec =
                com.zsubera.jpa.spec.SubQuerySpec.create(subquery, subRoot, correlatedOuter, cb);
            config.accept(subSpec);
            subSpec.applyWhere();
            if (!subSpec.isSelectSet()) {
                subquery.select(subRoot);
            }
            return cb.exists(subquery);
        }));
        return self();
    }

    /**
     * 添加 NOT EXISTS 子查询条件。
     *
     * @param subEntity 子查询实体类型
     * @param config 子查询配置消费者
     * @param <S> 子查询实体类型
     * @return 当前构建器实例
     * @throws IllegalArgumentException 如果任何参数为 null
     */
    public <S> SELF notExists(Class<S> subEntity, Consumer<com.zsubera.jpa.spec.SubQuerySpec<S>> config) {
        if (subEntity == null) {
            throw new IllegalArgumentException("subEntity must not be null");
        }
        if (config == null) {
            throw new IllegalArgumentException("config must not be null");
        }
        conditionNodes.add(leaf((root, cb) -> {
            jakarta.persistence.criteria.CriteriaQuery<?> tempQuery = cb.createQuery(entityClass);
            jakarta.persistence.criteria.Subquery<S> subquery = tempQuery.subquery(subEntity);
            Root<S> subRoot = subquery.from(subEntity);
            // P0-2: Use subquery.correlate() to establish correct correlation with outer query
            Root<?> correlatedOuter = subquery.correlate(root);
            com.zsubera.jpa.spec.SubQuerySpec<S> subSpec =
                com.zsubera.jpa.spec.SubQuerySpec.create(subquery, subRoot, correlatedOuter, cb);
            config.accept(subSpec);
            subSpec.applyWhere();
            if (!subSpec.isSelectSet()) {
                subquery.select(subRoot);
            }
            return cb.not(cb.exists(subquery));
        }));
        return self();
    }

    /**
     * 添加集合为空条件：{@code field IS EMPTY}。
     *
     * @param field 实体集合属性引用
     * @return 当前构建器实例
     * @throws IllegalArgumentException 如果 field 为 null
     */
    public SELF isEmpty(SFunction<T, ?> field) {
        if (field == null) {
            throw new IllegalArgumentException("field must not be null");
        }
        String name = property(field);
        conditionNodes.add(leaf((root, cb) -> PredicateHelper.isEmpty(root, name, cb)));
        return self();
    }

    /**
     * 添加集合不为空条件：{@code field IS NOT EMPTY}。
     *
     * @param field 实体集合属性引用
     * @return 当前构建器实例
     * @throws IllegalArgumentException 如果 field 为 null
     */
    public SELF isNotEmpty(SFunction<T, ?> field) {
        if (field == null) {
            throw new IllegalArgumentException("field must not be null");
        }
        String name = property(field);
        conditionNodes.add(leaf((root, cb) -> PredicateHelper.isNotEmpty(root, name, cb)));
        return self();
    }

    /**
     * 添加自定义条件。
     *
     * <p>
     * <strong>安全警告：此方法绕过类型安全机制，存在潜在的SQL注入风险！</strong>
     * <ul>
     * <li>请勿使用用户输入的字符串拼接字段名，如 {@code root.get(userInput)}，这可能导致 SQL 注入</li>
     * <li>建议优先使用类型安全的方法引用 API（如 {@code eq(Entity::getField, value)}）</li>
     * <li>如果必须使用字符串字面量，请确保是硬编码的常量，而非运行时拼接</li>
     * </ul>
     *
     * @param condition 自定义条件函数，接收 Root 返回 Predicate
     * @return 当前构建器实例
     * @throws IllegalArgumentException 如果 condition 为 null
     * @deprecated 推荐使用类型安全的 {@link #eq(SFunction, Object)}、{@link #like(SFunction, String)} 等方法替代。 此方法绕过类型安全机制，存在潜在的
     *             SQL 注入风险。此方法将在 2.0 版本中移除。
     */
    @Deprecated(since = "1.1.0", forRemoval = true)
    @SuppressWarnings("unchecked")
    public SELF where(Function<Root<T>, Predicate> condition) {
        throw new UnsupportedOperationException("where(Function) 已在 1.1.0 版本移除，请使用类型安全的条件方法 (eq/ne/likeSafe 等)。"
            + "如确需使用原始 Predicate，请使用 allowUnsafePredicate() 方法.");
    }

    // ---- 条件便捷方法 ----

    /**
     * 仅在 {@code condition} 为 true 时添加等值条件。
     *
     * @param condition 是否添加条件的标志
     * @param field 实体属性的方法引用
     * @param value 要比较的值
     * @return 当前构建器实例，支持链式调用
     */
    public SELF eq(boolean condition, SFunction<T, ?> field, @Nullable Object value) {
        return condition ? eq(field, value) : self();
    }

    /**
     * 仅在 {@code condition} 为 true 时添加不等条件。
     *
     * @param condition 是否添加条件的标志
     * @param field 实体属性的方法引用
     * @param value 要比较的值
     * @return 当前构建器实例，支持链式调用
     */
    public SELF ne(boolean condition, SFunction<T, ?> field, @Nullable Object value) {
        return condition ? ne(field, value) : self();
    }

    /**
     * 仅在 {@code condition} 为 true 时添加大于条件。
     *
     * @param condition 是否添加条件的标志
     * @param field 实体属性的方法引用
     * @param value 要比较的值
     * @return 当前构建器实例，支持链式调用
     */
    public SELF gt(boolean condition, SFunction<T, ?> field, Comparable<?> value) {
        return condition ? gt(field, value) : self();
    }

    /**
     * 仅在 {@code condition} 为 true 时添加大于等于条件。
     *
     * @param condition 是否添加条件的标志
     * @param field 实体属性的方法引用
     * @param value 要比较的值
     * @return 当前构建器实例，支持链式调用
     */
    public SELF ge(boolean condition, SFunction<T, ?> field, Comparable<?> value) {
        return condition ? ge(field, value) : self();
    }

    /**
     * 仅在 {@code condition} 为 true 时添加小于条件。
     *
     * @param condition 是否添加条件的标志
     * @param field 实体属性的方法引用
     * @param value 要比较的值
     * @return 当前构建器实例，支持链式调用
     */
    public SELF lt(boolean condition, SFunction<T, ?> field, Comparable<?> value) {
        return condition ? lt(field, value) : self();
    }

    /**
     * 仅在 {@code condition} 为 true 时添加小于等于条件。
     *
     * @param condition 是否添加条件的标志
     * @param field 实体属性的方法引用
     * @param value 要比较的值
     * @return 当前构建器实例，支持链式调用
     */
    public SELF le(boolean condition, SFunction<T, ?> field, Comparable<?> value) {
        return condition ? le(field, value) : self();
    }

    /**
     * 仅在 {@code condition} 为 true 时添加前缀匹配条件。
     *
     * @param condition 是否添加条件的标志
     * @param field 实体属性的方法引用
     * @param value 前缀字符串值
     * @return 当前构建器实例，支持链式调用
     */
    public SELF startsWith(boolean condition, SFunction<T, ?> field, String value) {
        return condition ? startsWith(field, value) : self();
    }

    /**
     * 仅在 {@code condition} 为 true 时添加后缀匹配条件。
     *
     * @param condition 是否添加条件的标志
     * @param field 实体属性的方法引用
     * @param value 后缀字符串值
     * @return 当前构建器实例，支持链式调用
     */
    public SELF endsWith(boolean condition, SFunction<T, ?> field, String value) {
        return condition ? endsWith(field, value) : self();
    }

    /**
     * 仅在 {@code condition} 为 true 时添加包含匹配条件。
     *
     * @param condition 是否添加条件的标志
     * @param field 实体属性的方法引用
     * @param value 要包含的子字符串值
     * @return 当前构建器实例，支持链式调用
     */
    public SELF contains(boolean condition, SFunction<T, ?> field, String value) {
        return condition ? contains(field, value) : self();
    }

    /**
     * 仅在 {@code condition} 为 true 时添加 IN 条件。
     *
     * @param condition 是否添加条件的标志
     * @param field 实体属性的方法引用
     * @param values 值集合
     * @return 当前构建器实例，支持链式调用
     */
    public SELF in(boolean condition, SFunction<T, ?> field, Object... values) {
        return condition ? in(field, values) : self();
    }

    /**
     * 仅在 {@code condition} 为 true 时添加 NOT IN 条件。
     *
     * @param condition 是否添加条件的标志
     * @param field 实体属性的方法引用
     * @param values 值集合
     * @return 当前构建器实例，支持链式调用
     */
    public SELF notIn(boolean condition, SFunction<T, ?> field, Object... values) {
        return condition ? notIn(field, values) : self();
    }

    /**
     * 仅在 {@code condition} 为 true 时添加 BETWEEN 条件。
     *
     * @param condition 是否添加条件的标志
     * @param field 实体属性的方法引用
     * @param start 范围起始值
     * @param end 范围结束值
     * @return 当前构建器实例，支持链式调用
     */
    public SELF between(boolean condition, SFunction<T, ?> field, Comparable<?> start, Comparable<?> end) {
        return condition ? between(field, start, end) : self();
    }

    /**
     * 仅在 {@code condition} 为 true 时添加 NOT BETWEEN 条件。
     *
     * @param condition 是否添加条件的标志
     * @param field 实体属性的方法引用
     * @param start 范围起始值
     * @param end 范围结束值
     * @return 当前构建器实例，支持链式调用
     */
    public SELF notBetween(boolean condition, SFunction<T, ?> field, Comparable<?> start, Comparable<?> end) {
        return condition ? notBetween(field, start, end) : self();
    }

    /**
     * 仅在 {@code condition} 为 true 时添加 IS NULL 条件。
     *
     * @param condition 是否添加条件的标志
     * @param field 实体属性的方法引用
     * @return 当前构建器实例，支持链式调用
     */
    public SELF isNull(boolean condition, SFunction<T, ?> field) {
        return condition ? isNull(field) : self();
    }

    /**
     * 仅在 {@code condition} 为 true 时添加 IS NOT NULL 条件。
     *
     * @param condition 是否添加条件的标志
     * @param field 实体属性的方法引用
     * @return 当前构建器实例，支持链式调用
     */
    public SELF isNotNull(boolean condition, SFunction<T, ?> field) {
        return condition ? isNotNull(field) : self();
    }

    /**
     * 解析条件节点为 JPA Predicate。
     *
     * @param node 条件节点
     * @param root 查询根对象
     * @param cb 条件构建器
     * @return 解析后的 Predicate
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private Predicate resolveNode(BulkConditionNode node, Root<T> root, CriteriaBuilder cb) {
        if (node instanceof BulkConditionNode.LeafNode l) {
            return ((BiFunction<Root<T>, CriteriaBuilder, Predicate>)(BiFunction)l.fn()).apply(root, cb);
        }
        if (node instanceof BulkConditionNode.AndNode a) {
            List<Predicate> childPredicates = new ArrayList<>();
            for (BulkConditionNode child : a.children()) {
                childPredicates.add(resolveNode(child, root, cb));
            }
            if (childPredicates.isEmpty()) {
                return cb.conjunction();
            }
            if (childPredicates.size() == 1) {
                return childPredicates.get(0);
            }
            return cb.and(childPredicates.toArray(new Predicate[0]));
        }
        if (node instanceof BulkConditionNode.OrNode o) {
            List<Predicate> childPredicates = new ArrayList<>();
            for (BulkConditionNode child : o.children()) {
                childPredicates.add(resolveNode(child, root, cb));
            }
            if (childPredicates.isEmpty()) {
                return cb.disjunction();
            }
            if (childPredicates.size() == 1) {
                return childPredicates.get(0);
            }
            return cb.or(childPredicates.toArray(new Predicate[0]));
        }
        if (node instanceof BulkConditionNode.NotNode n) {
            return cb.not(resolveNode(n.child(), root, cb));
        }
        throw new IllegalArgumentException("Unknown BulkConditionNode type: " + node.getClass().getName());
    }

    /**
     * 构建所有条件节点的 Predicate 数组。
     *
     * @param root 查询根对象
     * @param cb 条件构建器
     * @return Predicate 数组
     */
    protected Predicate[] buildPredicates(Root<T> root, CriteriaBuilder cb) {
        if (conditionNodes.isEmpty()) {
            return new Predicate[0];
        }
        List<Predicate> predicates = new ArrayList<>();
        for (BulkConditionNode node : conditionNodes) {
            predicates.add(resolveNode(node, root, cb));
        }
        return predicates.toArray(new Predicate[0]);
    }
}
