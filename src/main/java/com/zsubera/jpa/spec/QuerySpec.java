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
import java.util.stream.Collectors;
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

    /** 查询超时时间上限（秒） */
    private static final int MAX_TIMEOUT_SECONDS = 300;

    /**
     * 阻止序列化。QuerySpec 包含不可序列化的内部状态（如 lambda、SFunction）， 不应被序列化。在分布式会话或缓存场景中，请使用可序列化的查询参数重新构建 QuerySpec。
     *
     * @param oos 对象输出流
     * @throws java.io.IOException 始终抛出，表示不支持序列化
     */
    private void writeObject(java.io.ObjectOutputStream oos) throws java.io.IOException {
        throw new java.io.NotSerializableException(
            "QuerySpec is not serializable. It contains non-serializable lambda references. "
                + "Reconstruct QuerySpec from serializable query parameters instead.");
    }

    /**
     * 阻止反序列化。
     *
     * @param ois 对象输入流
     * @throws java.io.IOException 始终抛出，表示不支持序列化
     */
    private void readObject(java.io.ObjectInputStream ois) throws java.io.IOException {
        throw new java.io.NotSerializableException("QuerySpec is not serializable.");
    }

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
     * <p>
     * <strong>数据库兼容性说明：</strong>不同数据库对查询超时的支持方式不同：
     * <ul>
     * <li>PostgreSQL: 通过 {@code jakarta.persistence.query.timeout} hint 设置（毫秒），支持语句级超时</li>
     * <li>MySQL: 通过 {@code jakarta.persistence.query.timeout} hint 设置，但支持有限</li>
     * <li>H2: 支持查询超时</li>
     * <li>Oracle: 通过 {@code jakarta.persistence.query.timeout} hint 设置</li>
     * </ul>
     * 超时值通过 JPA hint {@code jakarta.persistence.query.timeout} 传递（转换为毫秒）， 实际行为取决于 JPA 提供者和数据库的实现。
     *
     * @param seconds 超时时间（秒），必须为正数且不超过 300 秒
     * @return 当前 QuerySpec 实例，支持链式调用
     * @throws IllegalArgumentException 如果 seconds 不是正数或超过 300 秒
     */
    public QuerySpec<T> timeout(int seconds) {
        if (seconds <= 0) {
            throw new IllegalArgumentException("timeout must be positive, got: " + seconds);
        }
        if (seconds > MAX_TIMEOUT_SECONDS) {
            throw new IllegalArgumentException(
                "timeout must not exceed " + MAX_TIMEOUT_SECONDS + " seconds, got: " + seconds);
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
            // 使用 Math.toIntExact() 防止大超时值的整数溢出
            query.setHint("jakarta.persistence.query.timeout", Math.toIntExact(queryTimeout * 1000L));
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
            if (f == null) {
                throw new IllegalArgumentException("fields must not contain null elements");
            }
            groupByFields.add(LambdaUtils.getPropertyName(f));
        }
        log.debug("QuerySpec: GROUP BY {}", groupByFields);
        return this;
    }

    /**
     * 添加 HAVING 条件，与 {@link #groupBy} 配合使用。 多个 HAVING 条件之间为 AND 关系。
     *
     * <p>
     * 如需 OR 关系的 HAVING 条件，请使用 {@link #having(java.util.function.Function)} 方法。
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
     * qs.groupBy(User::getStatus).having(root -> cb.greaterThan(cb.count(root), 5L));
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
            if (f == null) {
                throw new IllegalArgumentException("fields must not contain null elements");
            }
            orderNodes.add(new ConditionNode.OrderNode(LambdaUtils.getPropertyName(f), true));
        }
        log.debug("QuerySpec: ORDER BY ASC {}",
            Arrays.stream(fields).map(LambdaUtils::getPropertyName).collect(Collectors.joining(", ")));
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
            if (f == null) {
                throw new IllegalArgumentException("fields must not contain null elements");
            }
            orderNodes.add(new ConditionNode.OrderNode(LambdaUtils.getPropertyName(f), false));
        }
        log.debug("QuerySpec: ORDER BY DESC {}",
            Arrays.stream(fields).map(LambdaUtils::getPropertyName).collect(Collectors.joining(", ")));
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
     * 添加 IN 子查询条件：{@code field IN (SELECT ...)}。
     *
     * <p>
     * 使用示例：
     *
     * <pre>{@code
     * qs.inSubQuery(User::getDepartmentId, Department.class,
     *     sub -> sub.eq(Department::getActive, true).select(Department::getId));
     * }</pre>
     *
     * <p>
     * 生成：{@code user.department_id IN (SELECT d.id FROM department d WHERE d.active = true)}
     *
     * @param outerField 外部实体的字段方法引用
     * @param subEntity 子查询实体类型
     * @param config 子查询配置消费者
     * @param <S> 子查询实体类型
     * @return 当前 QuerySpec 实例，支持链式调用
     * @throws IllegalArgumentException 如果任何参数为 null
     */
    public <S> QuerySpec<T> inSubQuery(SFunction<T, ?> outerField, Class<S> subEntity,
        java.util.function.Consumer<SubQuerySpec<S>> config) {
        if (outerField == null) {
            throw new IllegalArgumentException("outerField must not be null");
        }
        if (subEntity == null) {
            throw new IllegalArgumentException("subEntity must not be null");
        }
        if (config == null) {
            throw new IllegalArgumentException("config must not be null");
        }
        currentGroup()
            .add(new ConditionNode.InSubQueryNode<>(LambdaUtils.getPropertyName(outerField), subEntity, config, false));
        return this;
    }

    /**
     * 添加 NOT IN 子查询条件：{@code field NOT IN (SELECT ...)}。
     *
     * <p>
     * 使用示例：
     *
     * <pre>{@code
     * qs.notInSubQuery(User::getDepartmentId, Department.class,
     *     sub -> sub.eq(Department::getArchived, true).select(Department::getId));
     * }</pre>
     *
     * <p>
     * 生成：{@code user.department_id NOT IN (SELECT d.id FROM department d WHERE d.archived = true)}
     *
     * @param outerField 外部实体的字段方法引用
     * @param subEntity 子查询实体类型
     * @param config 子查询配置消费者
     * @param <S> 子查询实体类型
     * @return 当前 QuerySpec 实例，支持链式调用
     * @throws IllegalArgumentException 如果任何参数为 null
     */
    public <S> QuerySpec<T> notInSubQuery(SFunction<T, ?> outerField, Class<S> subEntity,
        java.util.function.Consumer<SubQuerySpec<S>> config) {
        if (outerField == null) {
            throw new IllegalArgumentException("outerField must not be null");
        }
        if (subEntity == null) {
            throw new IllegalArgumentException("subEntity must not be null");
        }
        if (config == null) {
            throw new IllegalArgumentException("config must not be null");
        }
        currentGroup()
            .add(new ConditionNode.InSubQueryNode<>(LambdaUtils.getPropertyName(outerField), subEntity, config, true));
        return this;
    }

    /**
     * 打开一个 OR 条件组。
     *
     * @return OrGroup 实例，用于添加 OR 条件
     */
    public OrGroup<T> or() {
        return pushOrGroup();
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

    // ---- 基于 Consumer 的 API（自动关闭） ----

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
        try {
            config.accept(new OrGroup<>(this));
        } finally {
            groupStack.pop();
        }
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
     * <strong>重要语义说明：</strong>组内多个条件之间为 AND 关系，整体取反。 即 {@code not(b -> b.eq(A).eq(B))} 生成 {@code NOT (A AND B)}，根据德摩根定律等价于
     * {@code NOT A OR NOT B}。
     *
     * <p>
     * 使用 {@link NotGroup} 类型明确表达 NOT 语义，组内条件以 AND 组合后整体取反。
     *
     * @param config 条件组配置消费者
     * @return 当前 QuerySpec 实例，支持链式调用
     * @throws IllegalArgumentException 如果 config 为 null
     */
    public QuerySpec<T> not(Consumer<NotGroup<T>> config) {
        if (config == null) {
            throw new IllegalArgumentException("config must not be null");
        }
        ConditionNode.AndNode andNode = new ConditionNode.AndNode();
        ConditionNode.NegateNode negate = new ConditionNode.NegateNode(andNode);
        currentGroup().add(negate);
        groupStack.push(andNode.nodes);
        try {
            config.accept(NotGroup.create(this));
        } finally {
            groupStack.pop();
        }
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
     * <p>
     * 转换前会验证所有条件组已正确关闭（通过 {@link #validateCleanState()}）。
     *
     * @return Specification 实例
     * @throws IllegalStateException 如果存在未关闭的 or() 组
     */
    public Specification<T> toSpecification() {
        validateCleanState();
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
     * <p>
     * <strong>合并策略说明：</strong>
     * <ul>
     * <li>条件（conditions）：追加到当前条件列表（AND 语义）</li>
     * <li>DISTINCT：如果任一 spec 启用了 DISTINCT，则合并后启用</li>
     * <li>GROUP BY：追加到当前分组字段列表</li>
     * <li>HAVING：追加到当前 HAVING 条件列表（AND 语义）</li>
     * <li>ORDER BY：追加到当前排序列表（另一个 spec 的排序在当前排序之后）</li>
     * <li>查询超时/锁模式：仅当当前实例未设置时，采用另一个实例的值</li>
     * </ul>
     *
     * @param other 另一个 QuerySpec 实例
     * @return 当前 QuerySpec 实例，支持链式调用
     * @throws IllegalStateException 如果另一个 spec 存在未关闭的 or() 组
     */
    public QuerySpec<T> then(QuerySpec<T> other) {
        if (other == null) {
            return this;
        }
        // 验证另一个 spec 的组已正确关闭，防止状态不一致
        if (!other.groupStack.isEmpty()) {
            throw new IllegalStateException(
                "Cannot merge a QuerySpec with unclosed or() groups. Close all groups with endOr() before calling then().");
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
            // 从另一个 spec 复制时验证超时范围（必须与 timeout() 验证一致）
            if (other.queryTimeout <= 0 || other.queryTimeout > MAX_TIMEOUT_SECONDS) {
                throw new IllegalArgumentException(
                    "queryTimeout from source spec is out of range: " + other.queryTimeout);
            }
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
        log.debug("QuerySpec: building predicate for {} with {} conditions, {} order nodes, distinct={}",
            root.getModel().getName(), conditions.size(), orderNodes.size(), distinct);
        if (query != null) {
            applyDistinctAndGroupBy(root, query, cb);
            applyOrderBy(root, query, cb);
        }
        Map<String, Join<?, ?>> joinCache = new LinkedHashMap<>();
        List<Predicate> predicates = new ArrayList<>();
        for (ConditionNode node : conditions) {
            Predicate p = NodeResolver.resolveNode(node, root, root, query, cb, joinCache, null);
            if (p != null) {
                predicates.add(p);
            }
        }
        Predicate result = predicates.isEmpty() ? cb.conjunction() : cb.and(predicates.toArray(new Predicate[0]));
        log.debug("QuerySpec: predicate built with {} conditions", predicates.size());
        return result;
    }

    /**
     * 应用 DISTINCT 和 GROUP BY/HAVING 子句到查询。
     *
     * @param root 根实体路径
     * @param query Criteria 查询对象
     * @param cb Criteria 构建器
     */
    private void applyDistinctAndGroupBy(Root<T> root, CriteriaQuery<?> query, CriteriaBuilder cb) {
        if (distinct) {
            query.distinct(true);
        }
        if (!groupByFields.isEmpty()) {
            List<Path<?>> paths = new ArrayList<>();
            for (String field : groupByFields) {
                paths.add(root.get(field));
            }
            query.groupBy(paths.toArray(new Expression[0]));
            if (!havingConditions.isEmpty()) {
                List<Predicate> havingPredicates = new ArrayList<>();
                for (BiFunction<Path<T>, CriteriaBuilder, Predicate> having : havingConditions) {
                    havingPredicates.add(having.apply(root, cb));
                }
                if (havingPredicates.size() == 1) {
                    query.having(havingPredicates.get(0));
                } else {
                    query.having(cb.and(havingPredicates.toArray(new Predicate[0])));
                }
            }
        }
    }

    /**
     * 应用 ORDER BY 子句到查询。
     *
     * @param root 根实体路径
     * @param query Criteria 查询对象
     * @param cb Criteria 构建器
     */
    private void applyOrderBy(Root<T> root, CriteriaQuery<?> query, CriteriaBuilder cb) {
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

    // ---- 条件节点解析已提取到 NodeResolver 类 ----

    // ---- 聚合函数便捷 API ----

    /**
     * 创建 COUNT 聚合表达式，用于 HAVING 子句中。
     *
     * <p>
     * 使用示例：
     *
     * <pre>{@code
     * qs.groupBy(User::getDepartment).having((root, cb) -> cb.greaterThan(QuerySpec.count(root, cb), 5L));
     * }</pre>
     *
     * @param root 查询根路径
     * @param cb CriteriaBuilder 实例
     * @return COUNT 聚合表达式
     * @param <T> 实体类型
     */
    public static <T> Expression<Long> count(Path<T> root, CriteriaBuilder cb) {
        return AggregateHelper.count(root, cb);
    }

    /**
     * 创建指定字段的 COUNT 聚合表达式，用于 HAVING 子句中。
     *
     * <p>
     * 使用示例：
     *
     * <pre>{@code
     * qs.groupBy(User::getDepartment).having((root, cb) -> cb.greaterThan(QuerySpec.count(root, User::getEmail, cb), 10L));
     * }</pre>
     *
     * @param root 查询根路径
     * @param field 要计数的字段方法引用
     * @param cb CriteriaBuilder 实例
     * @return COUNT 聚合表达式
     * @param <T> 实体类型
     */
    public static <T> Expression<Long> count(Path<T> root, SFunction<T, ?> field, CriteriaBuilder cb) {
        return AggregateHelper.count(root, field, cb);
    }

    /**
     * 创建 COUNT DISTINCT 聚合表达式，用于 HAVING 子句中。
     *
     * @param root 查询根路径
     * @param field 要计数的字段方法引用
     * @param cb CriteriaBuilder 实例
     * @return COUNT DISTINCT 聚合表达式
     * @param <T> 实体类型
     */
    public static <T> Expression<Long> countDistinct(Path<T> root, SFunction<T, ?> field, CriteriaBuilder cb) {
        return AggregateHelper.countDistinct(root, field, cb);
    }

    /**
     * 创建 SUM 聚合表达式，用于 HAVING 子句中。
     *
     * <p>
     * 使用示例：
     *
     * <pre>{@code
     * qs.groupBy(Order::getCustomerId)
     *     .having((root, cb) -> cb.greaterThan(QuerySpec.sum(root, Order::getAmount, cb), 1000.0));
     * }</pre>
     *
     * @param root 查询根路径
     * @param field 要求和的字段方法引用
     * @param cb CriteriaBuilder 实例
     * @return SUM 聚合表达式
     * @param <T> 实体类型
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static <T> Expression<? extends Number> sum(Path<T> root, SFunction<T, ?> field, CriteriaBuilder cb) {
        return AggregateHelper.sum(root, field, cb);
    }

    /**
     * 创建 AVG 聚合表达式，用于 HAVING 子句中。
     *
     * <p>
     * 使用示例：
     *
     * <pre>{@code
     * qs.groupBy(Product::getCategory)
     *     .having((root, cb) -> cb.greaterThan(QuerySpec.avg(root, Product::getPrice, cb), 50.0));
     * }</pre>
     *
     * @param root 查询根路径
     * @param field 要求平均值的字段方法引用
     * @param cb CriteriaBuilder 实例
     * @return AVG 聚合表达式
     * @param <T> 实体类型
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static <T> Expression<Double> avg(Path<T> root, SFunction<T, ?> field, CriteriaBuilder cb) {
        return AggregateHelper.avg(root, field, cb);
    }

    /**
     * 创建 MAX 聚合表达式，用于 HAVING 子句中。
     *
     * <p>
     * 使用示例：
     *
     * <pre>{@code
     * qs.groupBy(Order::getCustomerId)
     *     .having((root, cb) -> cb.greaterThan(QuerySpec.max(root, Order::getAmount, cb), 500.0));
     * }</pre>
     *
     * @param root 查询根路径
     * @param field 要求最大值的字段方法引用
     * @param cb CriteriaBuilder 实例
     * @return MAX 聚合表达式
     * @param <T> 实体类型
     * @param <Y> 可比较类型
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static <T, Y extends Comparable<? super Y>> Expression<Y> max(Path<T> root, SFunction<T, ?> field,
        CriteriaBuilder cb) {
        return AggregateHelper.max(root, field, cb);
    }

    /**
     * 创建 MIN 聚合表达式，用于 HAVING 子句中。
     *
     * <p>
     * 使用示例：
     *
     * <pre>{@code
     * qs.groupBy(Product::getCategory).having((root, cb) -> cb.lessThan(QuerySpec.min(root, Product::getPrice, cb), 10.0));
     * }</pre>
     *
     * @param root 查询根路径
     * @param field 要求最小值的字段方法引用
     * @param cb CriteriaBuilder 实例
     * @return MIN 聚合表达式
     * @param <T> 实体类型
     * @param <Y> 可比较类型
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static <T, Y extends Comparable<? super Y>> Expression<Y> min(Path<T> root, SFunction<T, ?> field,
        CriteriaBuilder cb) {
        return AggregateHelper.min(root, field, cb);
    }

    // ---- 类型安全的 HAVING 辅助方法 ----

    /**
     * 添加 HAVING COUNT 条件：{@code HAVING COUNT(field) op value}。
     *
     * <p>
     * 使用示例：
     *
     * <pre>{@code
     * qs.groupBy(User::getDepartment).havingCount(User::getId, ConditionNode.Op.GT, 5L);
     * // 生成: HAVING COUNT(user.id) > 5
     * }</pre>
     *
     * @param field 要计数的字段方法引用
     * @param op 比较运算符
     * @param value 比较值
     * @return 当前 QuerySpec 实例，支持链式调用
     * @throws IllegalArgumentException 如果任何参数为 null
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public QuerySpec<T> havingCount(SFunction<T, ?> field, ConditionNode.Op op, long value) {
        if (field == null) {
            throw new IllegalArgumentException("field must not be null");
        }
        if (op == null) {
            throw new IllegalArgumentException("op must not be null");
        }
        AggregateHelper.validateHavingOperator(op);
        String fieldName = LambdaUtils.getPropertyName(field);
        havingConditions.add((root, cb) -> {
            Expression<Long> countExpr = cb.count(root.get(fieldName));
            return AggregateHelper.compareExpression(cb, countExpr, op, value);
        });
        return this;
    }

    /**
     * 添加 HAVING SUM 条件：{@code HAVING SUM(field) op value}。
     *
     * <p>
     * 使用示例：
     *
     * <pre>{@code
     * qs.groupBy(Order::getCustomerId).havingSum(Order::getAmount, ConditionNode.Op.GT, 1000.0);
     * // 生成: HAVING SUM(order.amount) > 1000.0
     * }</pre>
     *
     * @param field 要求和的字段方法引用
     * @param op 比较运算符
     * @param value 比较值
     * @return 当前 QuerySpec 实例，支持链式调用
     * @throws IllegalArgumentException 如果任何参数为 null
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public QuerySpec<T> havingSum(SFunction<T, ?> field, ConditionNode.Op op, Number value) {
        if (field == null) {
            throw new IllegalArgumentException("field must not be null");
        }
        if (op == null) {
            throw new IllegalArgumentException("op must not be null");
        }
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
        AggregateHelper.validateHavingOperator(op);
        String fieldName = LambdaUtils.getPropertyName(field);
        havingConditions.add((root, cb) -> {
            Expression<? extends Number> sumExpr = cb.sum((Expression)root.get(fieldName));
            return AggregateHelper.compareExpression(cb, sumExpr, op, value);
        });
        return this;
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
    @SuppressWarnings({"unchecked", "rawtypes"})
    public QuerySpec<T> havingAvg(SFunction<T, ?> field, ConditionNode.Op op, Number value) {
        if (field == null) {
            throw new IllegalArgumentException("field must not be null");
        }
        if (op == null) {
            throw new IllegalArgumentException("op must not be null");
        }
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
        AggregateHelper.validateHavingOperator(op);
        String fieldName = LambdaUtils.getPropertyName(field);
        havingConditions.add((root, cb) -> {
            Expression<Double> avgExpr = cb.avg((Expression)root.get(fieldName));
            return AggregateHelper.compareExpression(cb, avgExpr, op, value);
        });
        return this;
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
    @SuppressWarnings({"unchecked", "rawtypes"})
    public <Y extends Comparable<? super Y>> QuerySpec<T> havingMax(SFunction<T, ?> field, ConditionNode.Op op,
        Y value) {
        if (field == null) {
            throw new IllegalArgumentException("field must not be null");
        }
        if (op == null) {
            throw new IllegalArgumentException("op must not be null");
        }
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
        AggregateHelper.validateHavingOperator(op);
        String fieldName = LambdaUtils.getPropertyName(field);
        havingConditions.add((root, cb) -> {
            Expression<Y> maxExpr = cb.max((Expression)root.get(fieldName));
            return AggregateHelper.compareComparable(cb, maxExpr, op, value);
        });
        return this;
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
    @SuppressWarnings({"unchecked", "rawtypes"})
    public <Y extends Comparable<? super Y>> QuerySpec<T> havingMin(SFunction<T, ?> field, ConditionNode.Op op,
        Y value) {
        if (field == null) {
            throw new IllegalArgumentException("field must not be null");
        }
        if (op == null) {
            throw new IllegalArgumentException("op must not be null");
        }
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
        AggregateHelper.validateHavingOperator(op);
        String fieldName = LambdaUtils.getPropertyName(field);
        havingConditions.add((root, cb) -> {
            Expression<Y> minExpr = cb.min((Expression)root.get(fieldName));
            return AggregateHelper.compareComparable(cb, minExpr, op, value);
        });
        return this;
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
}
