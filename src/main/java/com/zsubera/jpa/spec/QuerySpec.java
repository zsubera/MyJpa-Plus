package com.zsubera.jpa.spec;

import com.zsubera.jpa.util.LambdaUtils;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.persistence.LockModeType;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.*;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

/**
 * 基于 Lambda 的类型安全 JPA {@link Specification} 查询构建器。
 *
 * <p>
 * 使用方法引用（如 {@code Entity::getField}）代替硬编码的属性名字符串。支持等值比较、 范围比较、字符串匹配、集合操作、JOIN、EXISTS 子查询以及任意嵌套的 AND/OR 条件组。
 *
 * <p>
 * <strong>此类是可变的且非线程安全。</strong>实例不应在多线程间共享。 每次查询操作应创建新的 {@code QuerySpec} 实例。
 *
 * <p>
 * <strong>序列化说明：</strong>{@code Specification} 接口继承了 {@code Serializable}， 但 {@code QuerySpec}
 * 的内部状态不适合序列化（如不可序列化的条件节点）。 实际使用中无需序列化 {@code QuerySpec}，SpotBugs 的 SE_BAD_FIELD 警告已被有意抑制。
 *
 * <p>
 * <strong>安全建议：</strong>直接使用 {@code Repository.findAll(spec)} 可能导致全表查询和内存溢出。 推荐使用
 * {@link com.zsubera.jpa.template.MyJpaTemplate} 进行查询，它提供了内置的结果数量限制和分页支持。
 *
 * <pre>{@code
 * // 推荐：使用 MyJpaTemplate（自动限制结果数量）
 * MyJpaTemplate template = ...;
 * List<User> users = template.findAll(User.class, spec);
 *
 * // 或使用分页
 * Page<User> page = template.findPage(User.class, spec, pageable);
 *
 * // 不推荐：直接使用 Repository（可能导致 OOM）
 * // repository.findAll(spec); // 无结果数量限制
 * }</pre>
 *
 * <p>
 * 示例：
 *
 * <pre>{@code
 * new QuerySpec<User>().eq(User::getStatus, "ACTIVE").or().like(User::getName, "%John%").like(User::getEmail, "%john%")
 *     .endOr().toSpecification();
 * }</pre>
 *
 * @param <T> 被查询的根实体类型
 * @see com.zsubera.jpa.template.MyJpaTemplate#findAll(Class, QuerySpec)
 * @see com.zsubera.jpa.template.MyJpaTemplate#findPage(Class, Specification, org.springframework.data.domain.Pageable)
 */
@SuppressFBWarnings("SE_BAD_FIELD")
public class QuerySpec<T> implements Specification<T>, ConditionBuilder<T, QuerySpec<T>> {

    private static final Logger log = LoggerFactory.getLogger(QuerySpec.class);

    private final List<ConditionNode> conditions = new ArrayList<>();
    private final Deque<List<ConditionNode>> groupStack = new ArrayDeque<>();
    private boolean distinct = false;
    private final List<String> groupByFields = new ArrayList<>();
    private final List<BiFunction<Path<T>, CriteriaBuilder, Predicate>> havingConditions = new ArrayList<>();
    private final List<ConditionNode.OrderNode> orderNodes = new ArrayList<>();
    private Integer queryTimeout;
    private LockModeType lockMode;

    /**
     * 获取当前活跃的条件组。
     *
     * @return 当前条件组的节点列表
     */
    List<ConditionNode> currentGroup() {
        return groupStack.isEmpty() ? conditions : groupStack.peek();
    }

    /**
     * 获取当前条件节点列表。
     *
     * @return 条件节点列表
     */
    @Override
    public List<ConditionNode> conditions() {
        return currentGroup();
    }

    /**
     * 将此 QuerySpec 上定义的排序暴露为 Spring Data {@link Sort} 对象。 如果未设置排序，则返回 {@link Sort#unsorted()}。
     *
     * <p>
     * 此方法允许 {@link com.zsubera.jpa.util.PageableHelper} 和其他工具类 将 QuerySpec 排序与外部排序进行合并。
     *
     * @return 排序对象
     */
    public Sort getSort() {
        if (orderNodes.isEmpty()) {
            return Sort.unsorted();
        }
        List<Sort.Order> orders = new ArrayList<>();
        for (ConditionNode.OrderNode node : orderNodes) {
            orders.add(node.asc ? Sort.Order.asc(node.fieldName) : Sort.Order.desc(node.fieldName));
        }
        return Sort.by(orders);
    }

    /**
     * 设置生成查询的超时时间（秒）。 由 {@link #applyQuerySettings(TypedQuery)} 和 {@link com.zsubera.jpa.template.MyJpaTemplate} 应用。
     *
     * @param seconds 超时时间（秒）
     * @return 当前 QuerySpec 实例，支持链式调用
     */
    public QuerySpec<T> timeout(int seconds) {
        if (seconds <= 0) {
            throw new IllegalArgumentException("timeout must be positive, got: " + seconds);
        }
        this.queryTimeout = seconds;
        return this;
    }

    /**
     * 获取查询超时时间。
     *
     * @return 超时时间（秒），如果未设置则返回 null
     */
    public Integer getQueryTimeout() {
        return queryTimeout;
    }

    /**
     * 设置生成查询的悲观锁模式。 由 {@link #applyQuerySettings(TypedQuery)} 和 {@link com.zsubera.jpa.template.MyJpaTemplate} 应用。
     *
     * @param lockMode 锁模式类型
     * @return 当前 QuerySpec 实例，支持链式调用
     */
    public QuerySpec<T> lockMode(LockModeType lockMode) {
        if (lockMode == null) {
            throw new IllegalArgumentException("lockMode must not be null");
        }
        this.lockMode = lockMode;
        return this;
    }

    /**
     * 获取锁模式。
     *
     * @return 锁模式类型，如果未设置则返回 null
     */
    public LockModeType getLockMode() {
        return lockMode;
    }

    /**
     * 将配置的查询超时和锁模式应用到给定的 {@link TypedQuery}。 由 {@link com.zsubera.jpa.template.MyJpaTemplate} 自动调用。
     *
     * @param query 要应用设置的 TypedQuery
     */
    public void applyQuerySettings(TypedQuery<?> query) {
        if (queryTimeout != null) {
            query.setHint("jakarta.persistence.query.timeout", queryTimeout * 1000);
        }
        if (lockMode != null) {
            query.setLockMode(lockMode);
        }
    }

    /**
     * 启用 DISTINCT 查询，去除重复结果。
     *
     * @return 当前 QuerySpec 实例，支持链式调用
     */
    public QuerySpec<T> distinct() {
        this.distinct = true;
        log.debug("QuerySpec: DISTINCT enabled");
        return this;
    }

    /**
     * 添加 GROUP BY 子句，按给定字段进行分组。
     *
     * @param fields 要分组的字段方法引用
     * @return 当前 QuerySpec 实例，支持链式调用
     */
    @SafeVarargs
    public final QuerySpec<T> groupBy(SFunction<T, ?>... fields) {
        if (fields == null) {
            throw new IllegalArgumentException("fields must not be null");
        }
        for (SFunction<T, ?> f : fields) {
            groupByFields.add(LambdaUtils.getPropertyName(f));
        }
        log.debug("QuerySpec: GROUP BY {}", groupByFields);
        return this;
    }

    /**
     * 添加 HAVING 条件，与 {@link #groupBy} 配合使用。 该函数在执行时接收查询根对象和 CriteriaBuilder。
     *
     * @param condition HAVING 条件函数
     * @return 当前 QuerySpec 实例，支持链式调用
     */
    public QuerySpec<T> having(BiFunction<Path<T>, CriteriaBuilder, Predicate> condition) {
        if (condition == null) {
            throw new IllegalArgumentException("condition must not be null");
        }
        havingConditions.add(condition);
        log.debug("QuerySpec: HAVING condition added ({} total)", havingConditions.size());
        return this;
    }

    /**
     * 添加 HAVING 条件，使用 {@link Root} 参数。此重载避免了 {@link #having(BiFunction)} 的类型推断问题。
     *
     * <p>
     * 推荐使用此方法代替 {@link #having(BiFunction)}，因为 {@code Root<T>} 的类型推断更可靠：
     *
     * <pre>{@code
     * qs.groupBy(User::getStatus).having(root -> cb.greaterThan(cb.count(root.get("id")), 5));
     * }</pre>
     *
     * @param condition HAVING 条件函数，接收 Root 返回 Predicate
     * @return 当前 QuerySpec 实例，支持链式调用
     */
    public QuerySpec<T> having(Function<Path<T>, Predicate> condition) {
        if (condition == null) {
            throw new IllegalArgumentException("condition must not be null");
        }
        havingConditions.add((root, cb) -> condition.apply(root));
        log.debug("QuerySpec: HAVING condition added ({} total)", havingConditions.size());
        return this;
    }

    /**
     * 添加升序 ORDER BY 排序。
     *
     * <p>
     * <strong>注意：</strong>当使用 {@code findAll(Specification, Pageable)} 时，Spring Data 会使用
     * {@link org.springframework.data.domain.Pageable Pageable} 的排序覆盖此处的排序。 使用 {@code findAll(spec,
     * Sort.unsorted())} 或不带 {@code Pageable} 的 {@link #orderByAsc(SFunction[])} 以保留此处设置的排序。
     *
     * @param fields 要排序的字段方法引用
     * @return 当前 QuerySpec 实例，支持链式调用
     */
    @SafeVarargs
    public final QuerySpec<T> orderByAsc(SFunction<T, ?>... fields) {
        if (fields == null) {
            throw new IllegalArgumentException("fields must not be null");
        }
        for (SFunction<T, ?> f : fields) {
            orderNodes.add(new ConditionNode.OrderNode(LambdaUtils.getPropertyName(f), true));
        }
        log.debug("QuerySpec: ORDER BY ASC {}", Arrays.toString(fields));
        return this;
    }

    /**
     * 添加降序 ORDER BY 排序。
     *
     * <p>
     * <strong>注意：</strong>当使用 {@code findAll(Specification, Pageable)} 时，Spring Data 会覆盖此处的排序。详见
     * {@link #orderByAsc(SFunction[])}。
     *
     * @param fields 要排序的字段方法引用
     * @return 当前 QuerySpec 实例，支持链式调用
     */
    @SafeVarargs
    public final QuerySpec<T> orderByDesc(SFunction<T, ?>... fields) {
        if (fields == null) {
            throw new IllegalArgumentException("fields must not be null");
        }
        for (SFunction<T, ?> f : fields) {
            orderNodes.add(new ConditionNode.OrderNode(LambdaUtils.getPropertyName(f), false));
        }
        log.debug("QuerySpec: ORDER BY DESC {}", Arrays.toString(fields));
        return this;
    }

    /**
     * 添加 INNER JOIN 关联。
     *
     * @param field 关联字段的方法引用
     * @param <J> 关联实体类型
     * @return JoinGroup 实例，用于配置关联条件
     */
    public <J> JoinGroup<T, J> join(SFunction<T, ?> field) {
        return internalJoin(field, ConditionNode.JoinType.INNER);
    }

    /**
     * 添加 INNER JOIN 关联，通过显式指定实体类辅助类型推断。
     *
     * <p>
     * 当链式调用导致类型丢失时，使用此方法显式指定关联实体类型：
     *
     * <pre>{@code
     * JoinGroup<User, Role> roleJoin = qs.join(User::getRoles, Role.class);
     * roleJoin.eq(Role::getName, "ADMIN");
     * }</pre>
     *
     * @param field 关联字段的方法引用
     * @param joinEntityClass 关联实体类（仅用于类型推断，不影响运行时行为）
     * @param <J> 关联实体类型
     * @return JoinGroup 实例，用于配置关联条件
     */
    public <J> JoinGroup<T, J> join(SFunction<T, ?> field, Class<J> joinEntityClass) {
        return internalJoin(field, ConditionNode.JoinType.INNER);
    }

    /**
     * 添加 LEFT JOIN 关联。
     *
     * @param field 关联字段的方法引用
     * @param <J> 关联实体类型
     * @return JoinGroup 实例，用于配置关联条件
     */
    public <J> JoinGroup<T, J> leftJoin(SFunction<T, ?> field) {
        return internalJoin(field, ConditionNode.JoinType.LEFT);
    }

    /**
     * 添加 LEFT JOIN 关联，通过显式指定实体类辅助类型推断。
     *
     * @param field 关联字段的方法引用
     * @param joinEntityClass 关联实体类（仅用于类型推断，不影响运行时行为）
     * @param <J> 关联实体类型
     * @return JoinGroup 实例，用于配置关联条件
     */
    public <J> JoinGroup<T, J> leftJoin(SFunction<T, ?> field, Class<J> joinEntityClass) {
        return internalJoin(field, ConditionNode.JoinType.LEFT);
    }

    /**
     * 添加 FETCH JOIN 以急切加载关联关系。
     *
     * @param field 关联字段的方法引用
     * @param <J> 关联实体类型
     * @return JoinGroup 实例，用于配置关联条件
     */
    public <J> JoinGroup<T, J> fetchJoin(SFunction<T, ?> field) {
        return internalJoin(field, ConditionNode.JoinType.FETCH);
    }

    /**
     * 添加 FETCH JOIN 以急切加载关联关系，通过显式指定实体类辅助类型推断。
     *
     * @param field 关联字段的方法引用
     * @param joinEntityClass 关联实体类（仅用于类型推断，不影响运行时行为）
     * @param <J> 关联实体类型
     * @return JoinGroup 实例，用于配置关联条件
     */
    public <J> JoinGroup<T, J> fetchJoin(SFunction<T, ?> field, Class<J> joinEntityClass) {
        return internalJoin(field, ConditionNode.JoinType.FETCH);
    }

    /**
     * 添加 LEFT FETCH JOIN 关联。
     *
     * @param field 关联字段的方法引用
     * @param <J> 关联实体类型
     * @return JoinGroup 实例，用于配置关联条件
     */
    public <J> JoinGroup<T, J> leftFetchJoin(SFunction<T, ?> field) {
        return internalJoin(field, ConditionNode.JoinType.LEFT_FETCH);
    }

    /**
     * 添加 LEFT FETCH JOIN 关联，通过显式指定实体类辅助类型推断。
     *
     * @param field 关联字段的方法引用
     * @param joinEntityClass 关联实体类（仅用于类型推断，不影响运行时行为）
     * @param <J> 关联实体类型
     * @return JoinGroup 实例，用于配置关联条件
     */
    public <J> JoinGroup<T, J> leftFetchJoin(SFunction<T, ?> field, Class<J> joinEntityClass) {
        return internalJoin(field, ConditionNode.JoinType.LEFT_FETCH);
    }

    /**
     * 内部 JOIN 实现方法，消除重复代码。
     *
     * @param field 关联字段的方法引用
     * @param joinType JOIN 类型
     * @param <J> 关联实体类型
     * @return JoinGroup 实例，用于配置关联条件
     */
    private <J> JoinGroup<T, J> internalJoin(SFunction<T, ?> field, ConditionNode.JoinType joinType) {
        if (field == null) {
            throw new IllegalArgumentException("field must not be null");
        }
        ConditionNode.JoinNode joinNode = new ConditionNode.JoinNode(LambdaUtils.getPropertyName(field), joinType);
        currentGroup().add(joinNode);
        return new JoinGroup<>(this, joinNode);
    }

    /**
     * 添加 EXISTS 子查询条件。
     *
     * @param subEntity 子查询实体类型
     * @param config 子查询配置消费者
     * @param <S> 子查询实体类型
     * @return 当前 QuerySpec 实例，支持链式调用
     */
    public <S> QuerySpec<T> exists(Class<S> subEntity, Consumer<SubQuerySpec<S>> config) {
        if (subEntity == null) {
            throw new IllegalArgumentException("subEntity must not be null");
        }
        if (config == null) {
            throw new IllegalArgumentException("config must not be null");
        }
        currentGroup().add(new ConditionNode.ExistsNode<>(subEntity, config, false));
        return this;
    }

    /**
     * 添加 NOT EXISTS 子查询条件。
     *
     * @param subEntity 子查询实体类型
     * @param config 子查询配置消费者
     * @param <S> 子查询实体类型
     * @return 当前 QuerySpec 实例，支持链式调用
     */
    public <S> QuerySpec<T> notExists(Class<S> subEntity, Consumer<SubQuerySpec<S>> config) {
        if (subEntity == null) {
            throw new IllegalArgumentException("subEntity must not be null");
        }
        if (config == null) {
            throw new IllegalArgumentException("config must not be null");
        }
        currentGroup().add(new ConditionNode.ExistsNode<>(subEntity, config, true));
        return this;
    }

    /**
     * 打开一个 OR 条件组。
     *
     * @return OrGroup 实例，用于添加 OR 条件
     */
    public OrGroup<T> or() {
        ConditionNode.OrNode orNode = new ConditionNode.OrNode();
        currentGroup().add(orNode);
        groupStack.push(orNode.nodes);
        return new OrGroup<>(this);
    }

    /**
     * 关闭 OR 条件组。
     *
     * @throws IllegalStateException 如果没有匹配的 or() 调用
     */
    void endOr() {
        if (groupStack.isEmpty()) {
            throw new IllegalStateException("endOr() called without a matching or()");
        }
        groupStack.pop();
    }

    /**
     * 将条件节点列表压入组栈。
     *
     * @param nodes 要压入的条件节点列表
     */
    void pushGroupStack(List<ConditionNode> nodes) {
        groupStack.push(nodes);
    }

    // ---- Consumer-based API (self-closing) ----

    /**
     * 使用消费者构建 OR 条件组，自动关闭组。
     *
     * @param config OR 组配置消费者
     * @return 当前 QuerySpec 实例，支持链式调用
     */
    public QuerySpec<T> or(Consumer<OrGroup<T>> config) {
        if (config == null) {
            throw new IllegalArgumentException("config must not be null");
        }
        ConditionNode.OrNode orNode = new ConditionNode.OrNode();
        currentGroup().add(orNode);
        groupStack.push(orNode.nodes);
        config.accept(new OrGroup<>(this));
        groupStack.pop();
        return this;
    }

    /**
     * 使用消费者构建 JOIN 关联，自动关闭关联组。
     *
     * @param field 关联字段的方法引用
     * @param config JoinGroup 配置消费者
     * @param <J> 关联实体类型
     * @return 当前 QuerySpec 实例，支持链式调用
     */
    public <J> QuerySpec<T> join(SFunction<T, ?> field, Consumer<JoinGroup<T, J>> config) {
        return internalJoinWithConsumer(field, ConditionNode.JoinType.INNER, config);
    }

    /**
     * 使用消费者构建 LEFT JOIN 关联，自动关闭关联组。
     *
     * @param field 关联字段的方法引用
     * @param config JoinGroup 配置消费者
     * @param <J> 关联实体类型
     * @return 当前 QuerySpec 实例，支持链式调用
     */
    public <J> QuerySpec<T> leftJoin(SFunction<T, ?> field, Consumer<JoinGroup<T, J>> config) {
        return internalJoinWithConsumer(field, ConditionNode.JoinType.LEFT, config);
    }

    /**
     * 使用消费者构建 FETCH JOIN 关联，自动关闭关联组。
     *
     * @param field 关联字段的方法引用
     * @param config JoinGroup 配置消费者
     * @param <J> 关联实体类型
     * @return 当前 QuerySpec 实例，支持链式调用
     */
    public <J> QuerySpec<T> fetchJoin(SFunction<T, ?> field, Consumer<JoinGroup<T, J>> config) {
        return internalJoinWithConsumer(field, ConditionNode.JoinType.FETCH, config);
    }

    /**
     * 使用消费者构建 LEFT FETCH JOIN 关联，自动关闭关联组。
     *
     * @param field 关联字段的方法引用
     * @param config JoinGroup 配置消费者
     * @param <J> 关联实体类型
     * @return 当前 QuerySpec 实例，支持链式调用
     */
    public <J> QuerySpec<T> leftFetchJoin(SFunction<T, ?> field, Consumer<JoinGroup<T, J>> config) {
        return internalJoinWithConsumer(field, ConditionNode.JoinType.LEFT_FETCH, config);
    }

    /**
     * 内部 JOIN Consumer 模式实现方法，消除重复代码。
     *
     * @param field 关联字段的方法引用
     * @param joinType JOIN 类型
     * @param config JoinGroup 配置消费者
     * @param <J> 关联实体类型
     * @return 当前 QuerySpec 实例，支持链式调用
     */
    private <J> QuerySpec<T> internalJoinWithConsumer(SFunction<T, ?> field, ConditionNode.JoinType joinType,
        Consumer<JoinGroup<T, J>> config) {
        if (field == null) {
            throw new IllegalArgumentException("field must not be null");
        }
        if (config == null) {
            throw new IllegalArgumentException("config must not be null");
        }
        ConditionNode.JoinNode joinNode = new ConditionNode.JoinNode(LambdaUtils.getPropertyName(field), joinType);
        currentGroup().add(joinNode);
        config.accept(new JoinGroup<>(this, joinNode));
        return this;
    }

    /**
     * 添加 NOT 条件组，对组合条件取反。
     *
     * <p>
     * <strong>注意：</strong>组内多个条件之间为 AND 关系，整体取反。 即 {@code not(b -> b.eq(A).eq(B))} 生成 {@code NOT (A AND B)}，根据德摩根定律等价于
     * {@code NOT A OR NOT B}。
     *
     * @param config 条件组配置消费者
     * @return 当前 QuerySpec 实例，支持链式调用
     * @throws IllegalArgumentException 如果 config 为 null
     */
    public QuerySpec<T> not(Consumer<OrGroup<T>> config) {
        if (config == null) {
            throw new IllegalArgumentException("config must not be null");
        }
        ConditionNode.AndNode andNode = new ConditionNode.AndNode();
        ConditionNode.NegateNode negate = new ConditionNode.NegateNode(andNode);
        currentGroup().add(negate);
        groupStack.push(andNode.nodes);
        config.accept(new OrGroup<>(this));
        groupStack.pop();
        return this;
    }

    /**
     * 验证所有条件组已正确关闭。
     *
     * @throws IllegalStateException 如果存在未关闭的 or() 组
     */
    private void validateCleanState() {
        if (!groupStack.isEmpty()) {
            throw new IllegalStateException("Not all or() groups were closed with endOr() before building the query");
        }
    }

    /**
     * 将此 QuerySpec 转换为 Spring Data {@link Specification}。
     *
     * @return Specification 实例
     */
    public Specification<T> toSpecification() {
        return this;
    }

    /**
     * 将此 QuerySpec 与外部 {@link Specification} 使用 AND 组合。
     *
     * @param external 外部 Specification，可为 null
     * @return 组合后的 Specification 实例
     */
    public Specification<T> toSpecification(@Nullable Specification<T> external) {
        if (external == null) {
            return this;
        }
        return this.and(external);
    }

    /**
     * 将此 QuerySpec 与另一个 QuerySpec 使用 AND 组合，返回新的组合 {@link Specification}。 使用 {@link #then(QuerySpec)} 可在保留 QuerySpec
     * 类型的同时组合条件以支持链式调用。
     *
     * @param other 另一个 QuerySpec 实例
     * @return 组合后的 Specification 实例
     */
    public Specification<T> and(QuerySpec<T> other) {
        if (other == null) {
            return this;
        }
        return this.and(other.toSpecification());
    }

    /**
     * 将另一个 QuerySpec 的条件以 AND 语义合并到当前实例。 另一个 spec 的条件、分组、排序和 distinct 标志将追加到当前 spec， 保留 QuerySpec 类型以支持链式调用。
     *
     * @param other 另一个 QuerySpec 实例
     * @return 当前 QuerySpec 实例，支持链式调用
     */
    public QuerySpec<T> then(QuerySpec<T> other) {
        if (other == null) {
            return this;
        }
        this.conditions.addAll(other.conditions);
        if (other.distinct) {
            this.distinct = true;
        }
        this.groupByFields.addAll(other.groupByFields);
        this.havingConditions.addAll(other.havingConditions);
        this.orderNodes.addAll(other.orderNodes);
        // 复制查询设置：仅当当前实例未设置时，采用另一个实例的值
        if (other.queryTimeout != null && this.queryTimeout == null) {
            this.queryTimeout = other.queryTimeout;
        }
        if (other.lockMode != null && this.lockMode == null) {
            this.lockMode = other.lockMode;
        }
        return this;
    }

    /**
     * 将此 QuerySpec 与另一个 QuerySpec 使用 OR 组合，返回新的组合 {@link Specification}。
     *
     * @param other 另一个 QuerySpec 实例
     * @return 组合后的 Specification 实例
     */
    public Specification<T> or(QuerySpec<T> other) {
        if (other == null) {
            return this;
        }
        return this.or(other.toSpecification());
    }

    /**
     * 将此 QuerySpec 转换为 JPA Criteria API 的 Predicate。
     *
     * @param root 查询根对象
     * @param query Criteria 查询对象
     * @param cb Criteria 构建器
     * @return 生成的 Predicate
     */
    @Override
    public Predicate toPredicate(@NonNull Root<T> root, @Nullable CriteriaQuery<?> query, @NonNull CriteriaBuilder cb) {
        validateCleanState();
        log.debug("QuerySpec: building predicate for {} with {} conditions, {} order nodes, distinct={}",
            root.getModel().getName(), conditions.size(), orderNodes.size(), distinct);
        if (query != null) {
            if (distinct) {
                query.distinct(true);
            }
            if (!groupByFields.isEmpty()) {
                List<Path<?>> paths = new ArrayList<>();
                for (String field : groupByFields) {
                    paths.add(root.get(field));
                }
                query.groupBy(paths.toArray(new Expression[0]));
                for (BiFunction<Path<T>, CriteriaBuilder, Predicate> having : havingConditions) {
                    query.having(having.apply(root, cb));
                }
            }
            if (!orderNodes.isEmpty()) {
                List<jakarta.persistence.criteria.Order> orders = new ArrayList<>();
                for (ConditionNode.OrderNode node : orderNodes) {
                    if (node.asc) {
                        orders.add(cb.asc(root.get(node.fieldName)));
                    } else {
                        orders.add(cb.desc(root.get(node.fieldName)));
                    }
                }
                query.orderBy(orders);
            }
        }
        Map<String, Join<?, ?>> joinCache = new LinkedHashMap<>();
        List<Predicate> predicates = new ArrayList<>();
        for (ConditionNode node : conditions) {
            Predicate p = resolveNode(node, root, query, cb, joinCache, null);
            if (p != null) {
                predicates.add(p);
            }
        }
        Predicate result = predicates.isEmpty() ? cb.conjunction() : cb.and(predicates.toArray(new Predicate[0]));
        log.debug("QuerySpec: predicate built with {} conditions", predicates.size());
        return result;
    }

    /**
     * 解析条件节点并转换为 Predicate。
     *
     * @param node 条件节点
     * @param path 当前路径
     * @param query Criteria 查询对象
     * @param cb Criteria 构建器
     * @param joinCache JOIN 缓存
     * @param pathPrefix 路径前缀
     * @return 生成的 Predicate，如果节点无条件则返回 null
     */
    private Predicate resolveNode(ConditionNode node, Path<?> path, CriteriaQuery<?> query, CriteriaBuilder cb,
        Map<String, Join<?, ?>> joinCache, String pathPrefix) {
        if (node instanceof ConditionNode.SimpleNode) {
            return resolveSimple((ConditionNode.SimpleNode)node, path, cb);
        }
        if (node instanceof ConditionNode.JoinNode) {
            return resolveJoin((ConditionNode.JoinNode)node, path, query, cb, joinCache, pathPrefix);
        }
        if (node instanceof ConditionNode.OrNode) {
            return resolveOr((ConditionNode.OrNode)node, path, query, cb, joinCache, pathPrefix);
        }
        if (node instanceof ConditionNode.AndNode) {
            return resolveAnd((ConditionNode.AndNode)node, path, query, cb, joinCache, pathPrefix);
        }
        if (node instanceof ConditionNode.MultiLikeNode) {
            return resolveMultiLike((ConditionNode.MultiLikeNode)node, path, cb);
        }
        if (node instanceof ConditionNode.CollectionNode) {
            return resolveCollection((ConditionNode.CollectionNode)node, path, cb);
        }
        if (node instanceof ConditionNode.ExistsNode) {
            return resolveExists((ConditionNode.ExistsNode<?>)node, path, query, cb);
        }
        if (node instanceof ConditionNode.RawNode) {
            return ((ConditionNode.RawNode)node).fn.apply(path, cb);
        }
        if (node instanceof ConditionNode.NegateNode) {
            Predicate inner =
                resolveNode(((ConditionNode.NegateNode)node).inner, path, query, cb, joinCache, pathPrefix);
            return inner != null ? cb.not(inner) : null;
        }
        throw new IllegalArgumentException("Unknown ConditionNode type: " + node.getClass().getName());
    }

    /**
     * 解析简单条件节点。
     *
     * @param node 简单条件节点
     * @param path 当前路径
     * @param cb Criteria 构建器
     * @return 生成的 Predicate
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private Predicate resolveSimple(ConditionNode.SimpleNode node, Path<?> path, CriteriaBuilder cb) {
        return PredicateHelper.resolveSimplePredicate(path, node, cb);
    }

    /**
     * 解析 JOIN 节点。
     *
     * @param node JOIN 节点
     * @param path 当前路径
     * @param query Criteria 查询对象
     * @param cb Criteria 构建器
     * @param joinCache JOIN 缓存
     * @param pathPrefix 路径前缀
     * @return 生成的 Predicate
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private Predicate resolveJoin(ConditionNode.JoinNode node, Path<?> path, CriteriaQuery<?> query, CriteriaBuilder cb,
        Map<String, Join<?, ?>> joinCache, String pathPrefix) {
        String fullPath = (pathPrefix != null && !pathPrefix.isEmpty() ? pathPrefix + "." : "") + node.fieldName;

        Join<?, ?> join = joinCache.computeIfAbsent(fullPath, k -> {
            boolean isFetch =
                node.joinType == ConditionNode.JoinType.FETCH || node.joinType == ConditionNode.JoinType.LEFT_FETCH;
            jakarta.persistence.criteria.JoinType jt =
                (node.joinType == ConditionNode.JoinType.LEFT || node.joinType == ConditionNode.JoinType.LEFT_FETCH)
                    ? jakarta.persistence.criteria.JoinType.LEFT : jakarta.persistence.criteria.JoinType.INNER;
            if (isFetch) {
                return (Join<?, ?>)((From<?, ?>)path).fetch(node.fieldName, jt);
            }
            return ((From<?, ?>)path).join(node.fieldName, jt);
        });

        List<Predicate> innerPredicates = new ArrayList<>();
        for (ConditionNode inner : node.innerConditions) {
            Predicate p = resolveNode(inner, join, query, cb, joinCache, fullPath);
            if (p != null) {
                innerPredicates.add(p);
            }
        }
        return innerPredicates.isEmpty() ? cb.conjunction() : cb.and(innerPredicates.toArray(new Predicate[0]));
    }

    /**
     * 解析 OR 节点。
     *
     * @param node OR 节点
     * @param path 当前路径
     * @param query Criteria 查询对象
     * @param cb Criteria 构建器
     * @param joinCache JOIN 缓存
     * @param pathPrefix 路径前缀
     * @return 生成的 Predicate
     */
    private Predicate resolveOr(ConditionNode.OrNode node, Path<?> path, CriteriaQuery<?> query, CriteriaBuilder cb,
        Map<String, Join<?, ?>> joinCache, String pathPrefix) {
        List<Predicate> childPredicates = new ArrayList<>();
        for (ConditionNode child : node.nodes) {
            Predicate p = resolveNode(child, path, query, cb, joinCache, pathPrefix);
            if (p != null) {
                childPredicates.add(p);
            }
        }
        if (childPredicates.isEmpty()) {
            return cb.disjunction();
        }
        if (childPredicates.size() == 1) {
            return childPredicates.get(0);
        }
        return cb.or(childPredicates.toArray(new Predicate[0]));
    }

    /**
     * 解析 AND 节点。
     *
     * @param node AND 节点
     * @param path 当前路径
     * @param query Criteria 查询对象
     * @param cb Criteria 构建器
     * @param joinCache JOIN 缓存
     * @param pathPrefix 路径前缀
     * @return 生成的 Predicate
     */
    private Predicate resolveAnd(ConditionNode.AndNode node, Path<?> path, CriteriaQuery<?> query, CriteriaBuilder cb,
        Map<String, Join<?, ?>> joinCache, String pathPrefix) {
        List<Predicate> childPredicates = new ArrayList<>();
        for (ConditionNode child : node.nodes) {
            Predicate p = resolveNode(child, path, query, cb, joinCache, pathPrefix);
            if (p != null) {
                childPredicates.add(p);
            }
        }
        if (childPredicates.isEmpty()) {
            return cb.conjunction();
        }
        if (childPredicates.size() == 1) {
            return childPredicates.get(0);
        }
        return cb.and(childPredicates.toArray(new Predicate[0]));
    }

    /**
     * 解析多字段模糊匹配节点。
     *
     * @param node 多字段模糊匹配节点
     * @param path 当前路径
     * @param cb Criteria 构建器
     * @return 生成的 Predicate
     */
    private Predicate resolveMultiLike(ConditionNode.MultiLikeNode node, Path<?> path, CriteriaBuilder cb) {
        List<Predicate> likes = new ArrayList<>();
        String pattern = "%" + PredicateHelper.escapeLikeWildcards(node.keyword) + "%";
        for (String fieldName : node.fieldNames) {
            likes.add(cb.like(path.get(fieldName).as(String.class), pattern, PredicateHelper.LIKE_ESCAPE_CHAR));
        }
        return cb.or(likes.toArray(new Predicate[0]));
    }

    /**
     * 解析集合操作节点。
     *
     * @param node 集合操作节点
     * @param path 当前路径
     * @param cb Criteria 构建器
     * @return 生成的 Predicate
     */
    @SuppressWarnings("unchecked")
    private Predicate resolveCollection(ConditionNode.CollectionNode node, Path<?> path, CriteriaBuilder cb) {
        Path<?> fieldPath = path.get(node.fieldName);
        if (node.op == ConditionNode.CollectionOp.IS_EMPTY) {
            return cb.isEmpty((Expression<Collection<?>>)fieldPath);
        }
        return cb.isNotEmpty((Expression<Collection<?>>)fieldPath);
    }

    /**
     * 解析 EXISTS 子查询节点。
     *
     * @param node EXISTS 子查询节点
     * @param outerPath 外部查询路径
     * @param query Criteria 查询对象
     * @param cb Criteria 构建器
     * @param <S> 子查询实体类型
     * @return 生成的 Predicate
     */
    @SuppressWarnings("unchecked")
    private <S> Predicate resolveExists(ConditionNode.ExistsNode<S> node, Path<?> outerPath, CriteriaQuery<?> query,
        CriteriaBuilder cb) {
        if (query == null) {
            log.debug("EXISTS subquery used in count query context (query=null). "
                + "Creating temporary CriteriaQuery for subquery construction.");
            CriteriaQuery<S> tempQuery = cb.createQuery(node.subEntity);
            return resolveExistsWithTempQuery(node, outerPath, tempQuery, cb);
        }
        jakarta.persistence.criteria.Subquery<S> subquery = query.subquery(node.subEntity);
        Root<S> subRoot = subquery.from(node.subEntity);
        if (!(outerPath instanceof Root<?>)) {
            throw new IllegalArgumentException("EXISTS correlation requires a Root path, but got "
                + outerPath.getClass().getSimpleName() + ". Nested JOIN correlation is not supported.");
        }
        Root<?> correlatedOuter = subquery.correlate((Root<?>)outerPath);
        SubQuerySpec<S> subSpec = SubQuerySpec.create(subquery, subRoot, correlatedOuter, cb);
        node.config.accept(subSpec);
        subSpec.applyWhere();
        if (!subSpec.isSelectSet()) {
            subquery.select(subRoot);
        }
        return node.negate ? cb.not(cb.exists(subquery)) : cb.exists(subquery);
    }

    /**
     * 在 COUNT 查询上下文中解析 EXISTS 子查询（当外部 query 为 null 时）。
     *
     * <p>
     * <strong>实现说明：</strong>此方法创建一个临时的 {@code CriteriaQuery<S>} 作为子查询的载体，该临时 query 从未被 {@link EntityManager}
     * 执行。子查询的构建仅依赖 JPA Criteria API 的对象模型（{@link Subquery}、{@link Root} 等）， 不依赖于查询执行。此行为在主流 JPA
     * 实现（Hibernate、EclipseLink）中均可正常工作。
     *
     * <p>
     * 临时 query 的唯一用途是提供类型化的 {@code Subquery.from()} 调用上下文，因为 JPA Criteria API 要求子查询从其父查询派生。
     *
     * @param <S> 子查询实体类型
     * @param node EXISTS 条件节点
     * @param outerPath 外部查询的实体路径
     * @param tempQuery 临时 CriteriaQuery（仅用于创建子查询对象）
     * @param cb CriteriaBuilder 实例
     * @return 构建好的 EXISTS 或 NOT EXISTS 谓词
     */
    private <S> Predicate resolveExistsWithTempQuery(ConditionNode.ExistsNode<S> node, Path<?> outerPath,
        CriteriaQuery<S> tempQuery, CriteriaBuilder cb) {
        jakarta.persistence.criteria.Subquery<S> subquery = tempQuery.subquery(node.subEntity);
        Root<S> subRoot = subquery.from(node.subEntity);
        if (!(outerPath instanceof Root<?>)) {
            throw new IllegalArgumentException("EXISTS correlation requires a Root path, but got "
                + outerPath.getClass().getSimpleName() + ". Nested JOIN correlation is not supported.");
        }
        Root<?> correlatedOuter = subquery.correlate((Root<?>)outerPath);
        SubQuerySpec<S> subSpec = SubQuerySpec.create(subquery, subRoot, correlatedOuter, cb);
        node.config.accept(subSpec);
        subSpec.applyWhere();
        if (!subSpec.isSelectSet()) {
            subquery.select(subRoot);
        }
        return node.negate ? cb.not(cb.exists(subquery)) : cb.exists(subquery);
    }
}
