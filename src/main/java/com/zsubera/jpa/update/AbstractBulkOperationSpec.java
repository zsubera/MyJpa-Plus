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
import org.springframework.lang.Nullable;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * JPA 批量操作构建器（{@link UpdateSpec} 和 {@link DeleteSpec}）的抽象基类。
 *
 * <p>
 * 使用延迟 Lambda 求值提供通用条件方法。谓词构造委托给 {@link PredicateHelper} 以与其他组件共享逻辑。
 *
 * @param <T> 实体类型
 * @param <SELF> 具体构建器类型，用于流式链式调用
 */
public abstract class AbstractBulkOperationSpec<T, SELF extends AbstractBulkOperationSpec<T, SELF>> {

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
        // Check if Spring manages the transaction first (container-managed JTA or
        // Spring tx)
        boolean springTxActive = TransactionSynchronizationManager.isActualTransactionActive();
        if (springTxActive) {
            // Spring transaction is active — delegate to it, don't touch EntityTransaction
            // directly
            return operation.apply(em);
        }
        // No Spring transaction — use JPA's EntityTransaction for standalone scenarios
        EntityTransaction tx = em.getTransaction();
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
            if (isNewTransaction && tx.isActive()) {
                try {
                    tx.rollback();
                } catch (Exception rollbackEx) {
                    e.addSuppressed(rollbackEx);
                }
            }
            throw e;
        } catch (Exception e) {
            if (isNewTransaction && tx.isActive()) {
                try {
                    tx.rollback();
                } catch (Exception rollbackEx) {
                    e.addSuppressed(rollbackEx);
                }
            }
            throw new MyJpaPlusException("Bulk operation failed", e);
        }
    }

    /**
     * 执行批量操作。要求底层 {@link EntityManager} 中存在活动事务。
     *
     * @param em 实体管理器
     * @return 受影响的行数
     * @throws jakarta.persistence.TransactionRequiredException 如果没有活动事务
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
        List<BulkConditionNode> children = new ArrayList<>();
        config.accept(new OrConditionBuilder<>(self(), children));
        conditionNodes.add(new BulkConditionNode.OrNode(List.copyOf(children)));
        return self();
    }

    /**
     * Execute a bulk operation within a transaction.
     *
     * <p>
     * If a Spring-managed transaction is active, the operation is delegated to it directly. Otherwise, a JPA
     * {@link EntityTransaction} is used for standalone scenarios.
     *
     * <p>
     * This overload allows subclasses to execute custom operations (e.g., unconditional deleteAll) with proper
     * transaction management.
     *
     * <p>
     * <strong>异常处理语义：</strong>
     * <ul>
     * <li>{@link RuntimeException} 及其子类将被直接重新抛出，保留原始异常类型</li>
     * <li>其他 {@link Exception}（checked exception）将被包装为 {@link MyJpaPlusException}</li>
     * </ul>
     *
     * @param em the EntityManager
     * @param operation the operation to execute
     * @return the number of affected rows
     * @throws RuntimeException 如果操作抛出运行时异常
     * @throws MyJpaPlusException 如果操作抛出受检异常
     */
    public SELF not(Consumer<OrConditionBuilder<T, SELF>> config) {
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
     * @param field 实体属性引用
     * @param value 匹配模式
     * @return 当前构建器实例
     * @throws IllegalArgumentException 如果 value 为 null
     */
    public SELF like(SFunction<T, ?> field, String value) {
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
        String name = property(field);
        conditionNodes.add(leaf((root, cb) -> PredicateHelper.like(root, name, value, cb)));
        return self();
    }

    /**
     * 添加 NOT LIKE 条件：{@code field NOT LIKE value}。
     *
     * @param field 实体属性引用
     * @param value 匹配模式
     * @return 当前构建器实例
     * @throws IllegalArgumentException 如果 value 为 null
     */
    public SELF notLike(SFunction<T, ?> field, String value) {
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
        String name = property(field);
        conditionNodes.add(leaf((root, cb) -> PredicateHelper.notLike(root, name, value, cb)));
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
        String name = property(field);
        conditionNodes.add(leaf((root, cb) -> PredicateHelper.eqIgnoreCase(root, name, value, cb)));
        return self();
    }

    /**
     * 添加忽略大小写的 LIKE 条件：{@code UPPER(field) LIKE UPPER(value)}。
     *
     * @param field 实体属性引用
     * @param value 匹配模式
     * @return 当前构建器实例
     * @throws IllegalArgumentException 如果 value 为 null
     */
    public SELF likeIgnoreCase(SFunction<T, ?> field, String value) {
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
        String name = property(field);
        conditionNodes.add(leaf((root, cb) -> PredicateHelper.likeIgnoreCase(root, name, value, cb)));
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
        if (start == null) {
            throw new IllegalArgumentException("start must not be null");
        }
        if (end == null) {
            throw new IllegalArgumentException("end must not be null");
        }
        if (start.getClass() != end.getClass()) {
            throw new IllegalArgumentException("start and end must be of the same type, but got "
                + start.getClass().getName() + " and " + end.getClass().getName());
        }
        if (((Comparable)start).compareTo(end) > 0) {
            throw new IllegalArgumentException("start must not be greater than end");
        }
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
        if (start == null) {
            throw new IllegalArgumentException("start must not be null");
        }
        if (end == null) {
            throw new IllegalArgumentException("end must not be null");
        }
        if (start.getClass() != end.getClass()) {
            throw new IllegalArgumentException("start and end must be of the same type, but got "
                + start.getClass().getName() + " and " + end.getClass().getName());
        }
        if (((Comparable)start).compareTo(end) > 0) {
            throw new IllegalArgumentException("start must not be greater than end");
        }
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
     * 添加自定义条件。
     *
     * @param condition 自定义条件函数，接收 Root 返回 Predicate
     * @return 当前构建器实例
     * @throws IllegalArgumentException 如果 condition 为 null
     */
    public SELF where(Function<Root<T>, Predicate> condition) {
        if (condition == null) {
            throw new IllegalArgumentException("condition must not be null");
        }
        conditionNodes.add(leaf((root, cb) -> condition.apply(root)));
        return self();
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
