package com.zsubera.jpa.spec;

import com.zsubera.jpa.exception.QueryBuildException;
import com.zsubera.jpa.util.LambdaUtils;
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
@SuppressWarnings("ALL")
public class QuerySpec<T> implements Specification<T>, ConditionBuilder<T, QuerySpec<T>> {

    private static final Logger log = LoggerFactory.getLogger(QuerySpec.class);

    /**
     * 获取全局配置。如果未配置则使用默认值。
     *
     * @return 全局配置实例
     */
    private static com.zsubera.jpa.autoconfigure.MyJpaPlusGlobalConfig getGlobalConfig() {
        return com.zsubera.jpa.autoconfigure.GlobalConfigHolder.getConfig();
    }

    /**
     * 使用 Lambda 创建并配置 QuerySpec 的便捷工厂方法。
     *
     * <p>
     * 等价于 {@code new QuerySpec<>()} + {@code config.accept(spec)}，将 3 行代码简化为 1 行：
     *
     * <pre>{@code
     * // 之前
     * QuerySpec<User> spec = new QuerySpec<>();
     * spec.eq(User::getStatus, "ACTIVE");
     * repository.findAll(spec);
     *
     * // 之后
     * repository.findAll(QuerySpec.of(s -> s.eq(User::getStatus, "ACTIVE")));
     * }</pre>
     *
     * @param config 查询条件配置消费者
     * @param <T> 实体类型
     * @return 配置完成的 QuerySpec 实例
     */
    public static <T> QuerySpec<T> of(Consumer<QuerySpec<T>> config) {
        QuerySpec<T> spec = new QuerySpec<>();
        if (config != null) {
            config.accept(spec);
        }
        return spec;
    }

    // ---- 聚合辅助方法（用于投影查询的 SELECT 列表） ----

    /**
     * 聚合表达式：COUNT(*)。
     */
    public static <T> AggregateSFunction<T, Long> count() {
        return new AggregateSFunction<>(AggregateSFunction.AggregateType.COUNT, null, "count");
    }

    /**
     * 聚合表达式：COUNT(field)。
     */
    public static <T> AggregateSFunction<T, Long> count(SFunction<T, ?> field) {
        String name = LambdaUtils.getPropertyName(field);
        return new AggregateSFunction<>(AggregateSFunction.AggregateType.COUNT_FIELD, name, name + "_count");
    }

    /**
     * 聚合表达式：SUM(field)。
     */
    public static <T, N extends Number> AggregateSFunction<T, N> sum(SFunction<T, N> field) {
        String name = LambdaUtils.getPropertyName(field);
        return new AggregateSFunction<>(AggregateSFunction.AggregateType.SUM, name, name + "_sum");
    }

    /**
     * 聚合表达式：AVG(field)。
     */
    public static <T, N extends Number> AggregateSFunction<T, Double> avg(SFunction<T, N> field) {
        String name = LambdaUtils.getPropertyName(field);
        return new AggregateSFunction<>(AggregateSFunction.AggregateType.AVG, name, name + "_avg");
    }

    /**
     * 聚合表达式：MAX(field)。
     */
    public static <T, N extends Comparable<? super N>> AggregateSFunction<T, N> max(SFunction<T, N> field) {
        String name = LambdaUtils.getPropertyName(field);
        return new AggregateSFunction<>(AggregateSFunction.AggregateType.MAX, name, name + "_max");
    }

    /**
     * 聚合表达式：MIN(field)。
     */
    public static <T, N extends Comparable<? super N>> AggregateSFunction<T, N> min(SFunction<T, N> field) {
        String name = LambdaUtils.getPropertyName(field);
        return new AggregateSFunction<>(AggregateSFunction.AggregateType.MIN, name, name + "_min");
    }

    private final List<ConditionNode> conditions = new ArrayList<>();
    private final Deque<List<ConditionNode>> groupStack = new ArrayDeque<>();
    private boolean distinct = false;
    private final List<String> groupByFields = new ArrayList<>();
    private final List<BiFunction<Path<T>, CriteriaBuilder, Predicate>> havingConditions = new ArrayList<>();
    private final List<ConditionNode.OrderNode> orderNodes = new ArrayList<>();
    private Integer queryTimeout;
    private LockModeType lockMode;

    // ---- 投影状态 ----
    private final List<ProjectionField> projectionFields = new ArrayList<>();
    private Class<?> projectionDtoClass;

    // ---- 投影配置方法 ----

    /**
     * 添加投影字段，使用默认别名（字段名）。
     *
     * @param fields 投影字段
     * @return 当前 QuerySpec 实例
     */
    @SafeVarargs
    public final QuerySpec<T> select(SFunction<T, ?>... fields) {
        if (fields != null) {
            for (SFunction<T, ?> f : fields) {
                projectionFields.add(new ProjectionField(f, null));
            }
        }
        return this;
    }

    /**
     * 添加投影字段并指定自定义别名。
     *
     * @param field 投影字段
     * @param alias 自定义别名
     * @return 当前 QuerySpec 实例
     */
    public QuerySpec<T> selectAs(SFunction<T, ?> field, String alias) {
        if (alias == null || alias.isBlank()) {
            throw new IllegalArgumentException("alias must not be null or blank");
        }
        projectionFields.add(new ProjectionField(field, alias));
        return this;
    }

    /**
     * 设置 DTO 构造函数投影的目标类。
     *
     * @param dtoClass DTO 类
     * @return 当前 QuerySpec 实例
     */
    public QuerySpec<T> asDto(Class<?> dtoClass) {
        if (dtoClass == null) {
            throw new IllegalArgumentException("dtoClass must not be null");
        }
        this.projectionDtoClass = dtoClass;
        return this;
    }

    /**
     * 判断是否处于投影模式。
     */
    public boolean isProjectionMode() {
        return !projectionFields.isEmpty();
    }

    /**
     * 获取投影字段列表（仅包含 SFunction，不含别名）。
     */
    public List<SFunction<T, ?>> getProjectionFields() {
        List<SFunction<T, ?>> result = new ArrayList<>();
        for (ProjectionField pf : projectionFields) {
            @SuppressWarnings("unchecked")
            SFunction<T, ?> sf = (SFunction<T, ?>)pf.field;
            result.add(sf);
        }
        return result;
    }

    /**
     * 获取投影字段列表（包含别名信息）。
     */
    @SuppressWarnings("rawtypes")
    public List getProjectionFieldsWithAlias() {
        return projectionFields;
    }

    /**
     * 投影字段定义：字段 + 可选别名。
     */
    static final class ProjectionField {
        final SFunction<?, ?> field;
        @Nullable
        final String alias;

        ProjectionField(SFunction<?, ?> field, @Nullable String alias) {
            this.field = field;
            this.alias = alias;
        }
    }

    /**
     * 获取 DTO 投影类。
     */
    public Class<?> getProjectionDtoClass() {
        return projectionDtoClass;
    }

    /** HAVING 条件辅助类，提供类型安全的聚合 HAVING 方法。 */
    private final QueryHavingSupport<T> havingSupport = new QueryHavingSupport<>(this, havingConditions);

    /** 条件节点辅助类，提供子查询、JOIN、OR/NOT 和条件组合方法。 */
    private final QueryConditionSupport<T> conditionSupport = new QueryConditionSupport<>(this);

    /** ORDER BY 辅助类，提供排序字段管理和查询排序应用逻辑。 */
    private final QueryOrderBySupport<T> orderBySupport = new QueryOrderBySupport<>(this, orderNodes);

    /** 聚合查询辅助类，提供 GROUP BY 字段管理和 DISTINCT/GROUP BY/HAVING 的查询应用逻辑。 */
    private final QueryAggregateSupport<T> aggregateSupport =
        new QueryAggregateSupport<>(this, groupByFields, havingSupport);

    /**
     * 获取当前活跃的条件组。
     *
     * @return 当前条件组的节点列表
     */
    List<ConditionNode> currentGroup() {
        return groupStack.isEmpty() ? conditions : groupStack.peek();
    }

    /**
     * 创建此 QuerySpec 的防御性拷贝。
     *
     * <p>
     * <strong>注意：</strong>不能在 or() 或 not() 消费者内部调用此方法，因为 groupStack 是可变状态，
     * 拷贝会导致后续条件添加到根节点而非当前条件组。
     *
     * @return QuerySpec 的独立副本
     * @throws QueryBuildException 如果在 or()/not() 消费者内部调用
     */
    public QuerySpec<T> copy() {
        if (!groupStack.isEmpty()) {
            throw new QueryBuildException("Cannot copy QuerySpec inside or() or not() consumer. "
                + "The copy's groupStack is empty, so subsequent conditions would be added to the root (AND), "
                + "not to the current group. Complete the or()/not() group before copying.");
        }
        if (conditions.isEmpty() && orderBySupport.isEmpty() && groupByFields.isEmpty() && havingSupport.isEmpty()
            && queryTimeout == null && lockMode == null && projectionFields.isEmpty()) {
            QuerySpec<T> copy = new QuerySpec<>();
            copy.distinct = this.distinct;
            copy.projectionDtoClass = this.projectionDtoClass;
            copy.projectionFields.addAll(this.projectionFields);
            return copy;
        }
        QuerySpec<T> copy = new QuerySpec<>();
        for (ConditionNode node : this.conditions) {
            copy.conditions.add(deepCopyNode(node));
        }
        copy.distinct = this.distinct;
        if (!groupByFields.isEmpty()) {
            copy.groupByFields.addAll(this.groupByFields);
        }
        if (!havingSupport.isEmpty()) {
            copy.havingSupport.addAll(this.havingConditions);
        }
        if (!orderBySupport.isEmpty()) {
            copy.orderBySupport.addAll(this.orderNodes);
        }
        copy.queryTimeout = this.queryTimeout;
        copy.lockMode = this.lockMode;
        copy.projectionDtoClass = this.projectionDtoClass;
        // ponytail: ProjectionField 是不可变类（所有字段 final），共享引用安全。
        // 与 condition 树的深拷贝策略不同，此处无需防御性拷贝。
        copy.projectionFields.addAll(this.projectionFields);
        return copy;
    }

    // ---- 包级私有访问器（供辅助类使用） ----

    List<ConditionNode> getConditions() {
        return conditions;
    }

    Deque<List<ConditionNode>> getGroupStack() {
        return groupStack;
    }

    boolean isDistinct() {
        return distinct;
    }

    void setDistinct(boolean distinct) {
        this.distinct = distinct;
    }

    List<String> getGroupByFields() {
        return groupByFields;
    }

    QueryAggregateSupport<T> getAggregateSupport() {
        return aggregateSupport;
    }

    QueryHavingSupport<T> getHavingSupport() {
        return havingSupport;
    }

    List<BiFunction<Path<T>, CriteriaBuilder, Predicate>> getHavingConditions() {
        return havingConditions;
    }

    List<ConditionNode.OrderNode> getOrderNodes() {
        return orderNodes;
    }

    QueryOrderBySupport<T> getOrderBySupport() {
        return orderBySupport;
    }

    void setQueryTimeout(Integer queryTimeout) {
        this.queryTimeout = queryTimeout;
    }

    void setLockMode(LockModeType lockMode) {
        this.lockMode = lockMode;
    }

    /**
     * 深拷贝条件节点树。对包含可变子节点列表的节点类型（JoinNode、OrNode、AndNode、NegateNode）
     * 递归拷贝，确保修改副本不会影响原始实例。
     *
     * @param node 要拷贝的条件节点
     * @return 深拷贝后的节点
     */
    static ConditionNode deepCopyNode(ConditionNode node) {
        if (node instanceof ConditionNode.JoinNode jn) {
            ConditionNode.JoinNode copy = new ConditionNode.JoinNode(jn.fieldName, jn.joinType);
            for (ConditionNode inner : jn.innerConditions) {
                copy.innerConditions.add(deepCopyNode(inner));
            }
            return copy;
        } else if (node instanceof ConditionNode.OrNode on) {
            ConditionNode.OrNode copy = new ConditionNode.OrNode();
            for (ConditionNode inner : on.nodes) {
                copy.nodes.add(deepCopyNode(inner));
            }
            return copy;
        } else if (node instanceof ConditionNode.AndNode an) {
            ConditionNode.AndNode copy = new ConditionNode.AndNode();
            for (ConditionNode inner : an.nodes) {
                copy.nodes.add(deepCopyNode(inner));
            }
            return copy;
        } else if (node instanceof ConditionNode.NegateNode nn) {
            return new ConditionNode.NegateNode(deepCopyNode(nn.inner()));
        } else if (node instanceof ConditionNode.MultiLikeNode mln) {
            return new ConditionNode.MultiLikeNode(mln.keyword, mln.fieldNames.clone());
        } else if (node instanceof ConditionNode.FuncNode fn) {
            // ponytail: FuncNode constructor already clones params — the .clone() here is
            // redundant but harmless; keeping both because FuncNode is package-private
            // and the constructor's clone is a defensive invariant we don't want to relax.
            return new ConditionNode.FuncNode(fn.functionName, fn.params.clone());
        }
        // ponytail: CollectionNode、ExistsNode、InSubQueryNode、RawNode 为不可变/共享节点，按引用返回
        return node;
    }

    // ---- 内部方法：供 OrGroup 使用 ----

    /**
     * 关闭 OR 条件组（仅限内部使用，由 Consumer 模式自动调用）。
     */
    void endOr() {
        if (groupStack.isEmpty()) {
            throw new IllegalStateException("endOr() called without a matching or()");
        }
        groupStack.pop();
    }

    /**
     * 创建 OR 节点并压入组栈，供 {@link OrGroup} 共享使用。
     *
     * @return 新的 OrGroup 实例
     */
    OrGroup<T> pushOrGroup() {
        ConditionNode.OrNode orNode = new ConditionNode.OrNode();
        currentGroup().add(orNode);
        groupStack.push(orNode.nodes);
        return new OrGroup<>(this);
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
     * 生成包含实际参数值的缓存键。与 {@link #conditions()} 不同，此方法返回的字符串包含
     * 条件值的哈希（而非掩码），确保不同参数值的查询产生不同的缓存键。
     *
     * <p>
     * <strong>安全说明：</strong>值通过 {@code hashCode()} 哈希后写入缓存键，原始值不暴露。
     *
     * @return 包含参数值哈希的缓存键字符串
     */
    public String cacheKey() {
        if (!groupStack.isEmpty()) {
            throw new IllegalStateException("Cannot generate cache key while or()/not() groups are open");
        }
        return CacheKeyBuilder.buildCacheKey(conditions, currentGroup(), groupStack, distinct, groupByFields,
            havingConditions, orderNodes, queryTimeout, lockMode);
    }

    // ---- ORDER BY 方法（委托给 QueryOrderBySupport） ----

    /**
     * 将此 QuerySpec 上定义的排序暴露为 Spring Data {@link Sort} 对象。
     *
     * @return 排序对象
     */
    public Sort getSort() {
        return orderBySupport.getSort();
    }

    /**
     * 添加升序 ORDER BY 排序。
     *
     * @param fields 要排序的字段方法引用
     * @return 当前 QuerySpec 实例，支持链式调用
     */
    @SafeVarargs
    public final QuerySpec<T> orderByAsc(SFunction<T, ?>... fields) {
        return orderBySupport.orderByAsc(fields);
    }

    /**
     * 添加降序 ORDER BY 排序。
     *
     * @param fields 要排序的字段方法引用
     * @return 当前 QuerySpec 实例，支持链式调用
     */
    @SafeVarargs
    public final QuerySpec<T> orderByDesc(SFunction<T, ?>... fields) {
        return orderBySupport.orderByDesc(fields);
    }

    /**
     * 设置生成查询的超时时间（秒）。 由 {@link #applyQuerySettings(TypedQuery)} 和 {@link com.zsubera.jpa.template.MyJpaTemplate} 应用。
     *
     * <p>
     * <strong>数据库兼容性说明：</strong>不同数据库对查询超时的支持方式不同：
     * <ul>
     * <li>PostgreSQL: 通过 {@code jakarta.persistence.query.timeout} hint 设置（毫秒），支持语句级超时</li>
     * <li>MySQL: 通过 {@code jakarta.persistence.query.timeout} hint 设置，但支持有限</li>
     * <li>Oracle: 通过 {@code jakarta.persistence.query.timeout} hint 设置</li>
     * </ul>
     * 超时值通过 JPA hint {@code jakarta.persistence.query.timeout} 传递（转换为毫秒）， 实际行为取决于 JPA 提供者和数据库的实现。
     *
     * @param seconds 超时时间（秒），必须为正数且不超过上限（默认 300 秒，可通过配置属性调整）
     * @return 当前 QuerySpec 实例，支持链式调用
     * @throws IllegalArgumentException 如果 seconds 不是正数或超过上限
     */
    public QuerySpec<T> timeout(int seconds) {
        if (seconds <= 0) {
            throw new IllegalArgumentException("timeout must be positive, got: " + seconds);
        }
        if (seconds > 86400) {
            throw new IllegalArgumentException("timeout must not exceed 86400 seconds (24h), got: " + seconds);
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
            // ponytail: Math.toIntExact throws on overflow (>2.1B ms ≈ 24 days timeout).
            // Acceptable since queryTimeout is capped at 86400s (24h) by the timeout() setter.
            query.setHint("jakarta.persistence.query.timeout",
                Math.toIntExact(java.util.concurrent.TimeUnit.SECONDS.toMillis(queryTimeout)));
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

    // ---- GROUP BY 方法（委托给 QueryAggregateSupport） ----

    /**
     * 添加 GROUP BY 子句，按给定字段进行分组。
     *
     * @param fields 要分组的字段方法引用
     * @return 当前 QuerySpec 实例，支持链式调用
     */
    @SafeVarargs
    public final QuerySpec<T> groupBy(SFunction<T, ?>... fields) {
        return aggregateSupport.groupBy(fields);
    }

    /**
     * 添加 HAVING 条件，与 {@link #groupBy} 配合使用。 多个 HAVING 条件之间为 AND 关系。
     *
     * <p>
     * <strong>推荐使用类型安全的替代方法：</strong>
     * <ul>
     * <li>{@link #havingCount(SFunction, ConditionNode.Op, long)} — COUNT 聚合条件</li>
     * <li>{@link #havingSum(SFunction, ConditionNode.Op, Number)} — SUM 聚合条件</li>
     * <li>{@link #havingAvg(SFunction, ConditionNode.Op, Number)} — AVG 聚合条件</li>
     * <li>{@link #havingMax(SFunction, ConditionNode.Op, Comparable)} — MAX 聚合条件</li>
     * <li>{@link #havingMin(SFunction, ConditionNode.Op, Comparable)} — MIN 聚合条件</li>
     * </ul>
     *
     * <p>
     * 此方法暴露了 JPA Criteria API 的 {@link CriteriaBuilder} 和 {@link Path} 类型，
     * 仅作为逃生舱供高级用户使用。类型安全方法无法覆盖的场景才应使用此方法。
     *
     * @param condition HAVING 条件函数，接收 {@link Path} 和 {@link CriteriaBuilder} 返回 {@link Predicate}
     * @return 当前 QuerySpec 实例，支持链式调用
     */
    public QuerySpec<T> having(BiFunction<Path<T>, CriteriaBuilder, Predicate> condition) {
        return havingSupport.having(condition);
    }

    /**
     * 添加 HAVING 条件，使用 {@link Root} 参数。此重载避免了 {@link #having(BiFunction)} 的类型推断问题。
     *
     * <p>
     * <strong>推荐使用类型安全的替代方法：</strong> {@link #havingCount(SFunction, ConditionNode.Op, long)} 等。
     * 此方法仅作为逃生舱供高级用户使用。
     *
     * @param condition HAVING 条件函数，接收 Root 返回 Predicate
     * @return 当前 QuerySpec 实例，支持链式调用
     */
    public QuerySpec<T> having(Function<Path<T>, Predicate> condition) {
        return havingSupport.having(condition);
    }

    // ---- OR/NOT 方法（委托给 QueryConditionSupport） ----

    /**
     * 使用消费者构建 OR 条件组，自动关闭组。
     *
     * @param config OR 组配置消费者
     * @return 当前 QuerySpec 实例，支持链式调用
     */
    public QuerySpec<T> or(Consumer<OrGroup<T>> config) {
        return conditionSupport.or(config);
    }

    /**
     * 添加 NOT 条件组，对组合条件取反。
     *
     * @param config 条件组配置消费者
     * @return 当前 QuerySpec 实例，支持链式调用
     * @throws IllegalArgumentException 如果 config 为 null
     */
    public QuerySpec<T> not(Consumer<NotGroup<T>> config) {
        return conditionSupport.not(config);
    }

    // ---- 子查询方法（委托给 QueryConditionSupport） ----

    /**
     * 添加 EXISTS 子查询条件。
     *
     * @param subEntity 子查询实体类型
     * @param config 子查询配置消费者
     * @param <S> 子查询实体类型
     * @return 当前 QuerySpec 实例，支持链式调用
     */
    public <S> QuerySpec<T> exists(Class<S> subEntity, Consumer<SubQuerySpec<S>> config) {
        return conditionSupport.exists(subEntity, config);
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
        return conditionSupport.notExists(subEntity, config);
    }

    /**
     * 添加 IN 子查询条件：{@code field IN (SELECT ...)}。
     *
     * @param outerField 外部实体的字段方法引用
     * @param subEntity 子查询实体类型
     * @param config 子查询配置消费者
     * @param <S> 子查询实体类型
     * @return 当前 QuerySpec 实例，支持链式调用
     * @throws IllegalArgumentException 如果任何参数为 null
     */
    @Override
    public <S> QuerySpec<T> inSubQuery(SFunction<T, ?> outerField, Class<S> subEntity,
        java.util.function.Consumer<SubQuerySpec<S>> config) {
        return conditionSupport.inSubQuery(outerField, subEntity, config);
    }

    /**
     * 添加 NOT IN 子查询条件：{@code field NOT IN (SELECT ...)}。
     *
     * @param outerField 外部实体的字段方法引用
     * @param subEntity 子查询实体类型
     * @param config 子查询配置消费者
     * @param <S> 子查询实体类型
     * @return 当前 QuerySpec 实例，支持链式调用
     * @throws IllegalArgumentException 如果任何参数为 null
     */
    @Override
    public <S> QuerySpec<T> notInSubQuery(SFunction<T, ?> outerField, Class<S> subEntity,
        java.util.function.Consumer<SubQuerySpec<S>> config) {
        return conditionSupport.notInSubQuery(outerField, subEntity, config);
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
        return conditionSupport.join(field, config);
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
        return conditionSupport.leftJoin(field, config);
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
        return conditionSupport.fetchJoin(field, config);
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
        return conditionSupport.leftFetchJoin(field, config);
    }

    /**
     * 将此 QuerySpec 转换为 Spring Data {@link Specification}。
     *
     * @return Specification 实例
     * @throws IllegalStateException 如果存在未关闭的 or() 组
     */
    public Specification<T> toSpecification() {
        return conditionSupport.toSpecification();
    }

    /**
     * 将此 QuerySpec 与另一个 Specification 使用 AND 组合。
     *
     * @param external 要组合的外部 Specification（可以为 null）
     * @return 组合后的 Specification 实例
     */
    public Specification<T> toSpecification(@Nullable Specification<T> external) {
        return conditionSupport.toSpecification(external);
    }

    /**
     * 将此 QuerySpec 与另一个 QuerySpec 使用 OR 组合，返回组合后的 {@link Specification}。
     *
     * @param other 另一个 QuerySpec 实例
     * @return 组合后的 Specification 实例
     */
    public Specification<T> orCombine(QuerySpec<T> other) {
        return conditionSupport.orCombine(other);
    }

    /**
     * 将另一个 QuerySpec 的条件以 AND 语义合并到当前实例。
     *
     * @param other 另一个 QuerySpec 实例
     * @return 当前 QuerySpec 实例，支持链式调用
     * @throws IllegalStateException 如果另一个 spec 存在未关闭的 or() 组
     */
    public QuerySpec<T> then(QuerySpec<T> other) {
        return conditionSupport.then(other);
    }

    /**
     * 将另一个 QuerySpec 的条件以 AND 语义合并到当前实例。
     *
     * @param other 另一个 QuerySpec 实例
     * @return 当前 QuerySpec 实例（条件已合并），支持链式调用
     */
    public QuerySpec<T> and(QuerySpec<T> other) {
        return conditionSupport.then(other);
    }

    /**
     * 将此 QuerySpec 转换为 JPA Criteria API 的 Predicate。
     *
     * @param root 查询根对象
     * @param query Criteria 查询对象
     * @param cb Criteria 构建器
     * @return 生成的 Predicate
     */
    /**
     * ponytail: 使用初始容量预分配减少 resize 开销。条件数通常较少（<16），避免 HashMap 默认 16 的过度分配。
     */
    private static final int INITIAL_JOIN_CACHE_CAPACITY = 8;
    private static final int INITIAL_PREDICATE_CAPACITY = 8;

    @Override
    public Predicate toPredicate(@NonNull Root<T> root, @Nullable CriteriaQuery<?> query, @NonNull CriteriaBuilder cb) {
        if (log.isDebugEnabled()) {
            log.debug("QuerySpec: building predicate for {} with {} conditions, {} order nodes, distinct={}",
                root.getModel().getName(), conditions.size(), orderNodes.size(), distinct);
        }
        if (query != null) {
            applyDistinctAndGroupBy(root, query, cb);
            applyOrderBy(root, query, cb);
        }
        // ponytail: 预分配初始容量减少 HashMap/ArrayList 的 resize 开销，高频查询场景下可降低 GC 压力
        Map<String, Join<?, ?>> joinCache = new java.util.HashMap<>(INITIAL_JOIN_CACHE_CAPACITY);
        java.util.Set<String> fetchPaths =
            java.util.Collections.newSetFromMap(new java.util.HashMap<>(INITIAL_JOIN_CACHE_CAPACITY));
        List<Predicate> predicates = new java.util.ArrayList<>(Math.max(conditions.size(), INITIAL_PREDICATE_CAPACITY));
        for (ConditionNode node : conditions) {
            Predicate p = NodeResolver.resolveNode(node, root, root, query, cb, joinCache, null, fetchPaths);
            if (p != null) {
                predicates.add(p);
            }
        }
        Predicate result = predicates.isEmpty() ? cb.conjunction() : cb.and(predicates.toArray(new Predicate[0]));
        if (log.isDebugEnabled()) {
            log.debug("QuerySpec: predicate built with {} conditions", predicates.size());
        }
        return result;
    }

    /**
     * 应用 DISTINCT 和 GROUP BY/HAVING 子句到查询。
     *
     * @param root 根实体路径
     * @param query Criteria 查询对象
     * @param cb Criteria 构建器
     */
    void applyDistinctAndGroupBy(Root<T> root, CriteriaQuery<?> query, CriteriaBuilder cb) {
        aggregateSupport.applyDistinctAndGroupBy(distinct, root, query, cb);
    }

    /**
     * 应用 ORDER BY 子句到查询。
     *
     * @param root 根实体路径
     * @param query Criteria 查询对象
     * @param cb Criteria 构建器
     */
    void applyOrderBy(Root<T> root, CriteriaQuery<?> query, CriteriaBuilder cb) {
        orderBySupport.applyOrderBy(root, query, cb);
    }

    // ---- 类型安全的 HAVING 辅助方法（委托给 QueryHavingSupport） ----

    /**
     * 添加 HAVING COUNT 条件：{@code HAVING COUNT(field) op value}。
     *
     * @param field 要计数的字段方法引用
     * @param op 比较运算符
     * @param value 比较值
     * @return 当前 QuerySpec 实例，支持链式调用
     * @throws IllegalArgumentException 如果任何参数为 null
     */
    public QuerySpec<T> havingCount(SFunction<T, ?> field, ConditionNode.Op op, long value) {
        return havingSupport.havingCount(field, op, value);
    }

    /**
     * 添加 HAVING SUM 条件：{@code HAVING SUM(field) op value}。
     *
     * @param field 要求和的字段方法引用
     * @param op 比较运算符
     * @param value 比较值
     * @return 当前 QuerySpec 实例，支持链式调用
     * @throws IllegalArgumentException 如果任何参数为 null
     */
    public QuerySpec<T> havingSum(SFunction<T, ?> field, ConditionNode.Op op, Number value) {
        return havingSupport.havingSum(field, op, value);
    }

    /**
     * 添加 HAVING AVG 条件：{@code HAVING AVG(field) op value}。
     *
     * @param field 要求平均值的字段方法引用
     * @param op 比较运算符
     * @param value 比较值
     * @return 当前 QuerySpec 实例，支持链式调用
     * @throws IllegalArgumentException 如果任何参数为 null
     */
    public QuerySpec<T> havingAvg(SFunction<T, ?> field, ConditionNode.Op op, Number value) {
        return havingSupport.havingAvg(field, op, value);
    }

    /**
     * 添加 HAVING MAX 条件：{@code HAVING MAX(field) op value}。
     *
     * @param field 要求最大值的字段方法引用
     * @param op 比较运算符
     * @param value 比较值
     * @return 当前 QuerySpec 实例，支持链式调用
     * @throws IllegalArgumentException 如果任何参数为 null
     */
    public <Y extends Comparable<? super Y>> QuerySpec<T> havingMax(SFunction<T, ?> field, ConditionNode.Op op,
        Y value) {
        return havingSupport.havingMax(field, op, value);
    }

    /**
     * 添加 HAVING MIN 条件：{@code HAVING MIN(field) op value}。
     *
     * @param field 要求最小值的字段方法引用
     * @param op 比较运算符
     * @param value 比较值
     * @return 当前 QuerySpec 实例，支持链式调用
     * @throws IllegalArgumentException 如果任何参数为 null
     */
    public <Y extends Comparable<? super Y>> QuerySpec<T> havingMin(SFunction<T, ?> field, ConditionNode.Op op,
        Y value) {
        return havingSupport.havingMin(field, op, value);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("QuerySpec{");
        sb.append("conditions=").append(conditions.size());
        if (distinct) {
            sb.append(", distinct");
        }
        if (!groupByFields.isEmpty()) {
            sb.append(", groupBy=").append(groupByFields);
        }
        if (!havingConditions.isEmpty()) {
            sb.append(", having=").append(havingConditions.size()).append(" conditions");
        }
        if (!orderNodes.isEmpty()) {
            sb.append(", orderBy=").append(orderNodes);
        }
        if (queryTimeout != null) {
            sb.append(", timeout=").append(queryTimeout).append("s");
        }
        if (lockMode != null) {
            sb.append(", lockMode=").append(lockMode);
        }
        sb.append('}');
        return sb.toString();
    }

    /**
     * 生成查询条件的可读描述，用于调试和分析。
     *
     * <p>
     * 返回条件树的结构化描述，包括 WHERE 条件、GROUP BY、HAVING、ORDER BY 等子句。
     * 此方法不生成可执行的 JPQL，而是生成人类可读的查询结构描述。
     *
     * <p>
     * <strong>使用示例：</strong>
     *
     * <pre>{@code
     * String desc = new QuerySpec<User>()
     *     .eq(User::getStatus, "ACTIVE")
     *     .gt(User::getAge, 18)
     *     .orderByAsc(User::getName)
     *     .toDescription();
     * // 返回: "Query{WHERE: [status = EQ, age = GT], ORDER BY: [name ASC]}"
     * }</pre>
     *
     * @return 查询条件描述字符串
     */
    public String toDescription() {
        java.util.StringJoiner sj = new java.util.StringJoiner(", ", "Query{", "}");

        if (distinct) {
            sj.add("SELECT DISTINCT");
        }
        if (!conditions.isEmpty()) {
            sj.add("WHERE: " + conditions);
        }
        if (!groupByFields.isEmpty()) {
            sj.add("GROUP BY: " + groupByFields);
        }
        if (!havingConditions.isEmpty()) {
            sj.add("HAVING: " + havingConditions.size() + " conditions");
        }
        if (!orderNodes.isEmpty()) {
            sj.add("ORDER BY: " + orderNodes);
        }
        if (queryTimeout != null) {
            sj.add("timeout=" + queryTimeout + "s");
        }
        if (lockMode != null) {
            sj.add("lockMode=" + lockMode);
        }
        return sj.toString();
    }
}
