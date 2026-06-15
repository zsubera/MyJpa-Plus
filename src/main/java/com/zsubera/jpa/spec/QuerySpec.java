package com.zsubera.jpa.spec;

import com.zsubera.jpa.autoconfigure.MyJpaPlusGlobalConfig;
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

    /**
     * 设置全局配置。由自动配置类在启动时调用。
     *
     * @param config 全局配置实例
     * @deprecated 请使用 {@link com.zsubera.jpa.autoconfigure.GlobalConfigHolder#setConfig(MyJpaPlusGlobalConfig)} 代替。
     * 此方法保留以兼容现有调用方，内部委托给 GlobalConfigHolder。
     */
    @Deprecated(since = "2.1.0")
    public static void setGlobalConfig(MyJpaPlusGlobalConfig config) {
        com.zsubera.jpa.autoconfigure.GlobalConfigHolder.setConfig(config);
    }

    /**
     * 获取全局配置。如果未配置则使用默认值。
     *
     * @return 全局配置实例
     */
    private static com.zsubera.jpa.autoconfigure.MyJpaPlusGlobalConfig getGlobalConfig() {
        return com.zsubera.jpa.autoconfigure.GlobalConfigHolder.getConfig();
    }

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
     * 创建此 QuerySpec 的防御性拷贝。
     *
     * <p>
     * 返回的副本包含所有条件、排序、分组等配置的独立副本，修改副本不会影响原始实例。
     * 这对于将 QuerySpec 定义为单例 Bean 并在多线程间共享的场景至关重要。
     *
     * <p>
     * <strong>线程安全建议：</strong>如果 QuerySpec 实例会在多线程间共享，应使用此方法在每次查询前创建副本：
     *
     * <pre>{@code
     * // 定义为单例 Bean
     * @Bean
     * public QuerySpec<User> activeUserSpec() {
     *     return new QuerySpec<User>().eq(User::getStatus, "ACTIVE");
     * }
     *
     * // 使用时创建防御性拷贝
     * List<User> users = repository.findAll(activeUserSpec.copy());
     * }</pre>
     *
     * @return QuerySpec 的独立副本
     */
    @SuppressWarnings("unchecked")
    public QuerySpec<T> copy() {
        QuerySpec<T> copy = new QuerySpec<>();
        copy.conditions.addAll(this.conditions);
        // groupStack 是构建过程中的临时状态（or/not/join 的 Consumer 作用域），
        // 不属于查询定义的一部分。构建完成后应为空，拷贝时不复制。
        copy.distinct = this.distinct;
        copy.groupByFields.addAll(this.groupByFields);
        copy.havingConditions.addAll(this.havingConditions);
        copy.orderNodes.addAll(this.orderNodes);
        copy.queryTimeout = this.queryTimeout;
        copy.lockMode = this.lockMode;
        return copy;
    }

    /**
     * 设置查询超时时间上限。默认值为 300 秒。
     *
     * <p>
     * 此方法影响所有后续创建的 {@code QuerySpec} 实例的 {@link #timeout(int)} 验证。
     * 适用于需要更长查询超时的分析型查询场景。
     *
     * @param seconds 超时上限（秒），必须为正数
     * @throws IllegalArgumentException 如果 seconds 不是正数
     * @deprecated 请使用 {@code myjpa-plus.query.max-timeout-seconds} 配置属性替代
     */
    @Deprecated
    public static void setMaxTimeoutSeconds(int seconds) {
        if (seconds <= 0) {
            throw new IllegalArgumentException("maxTimeoutSeconds must be positive, got: " + seconds);
        }
        getGlobalConfig().setMaxTimeoutSeconds(seconds);
    }

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
     * 生成包含实际参数值的缓存键。与 {@link #conditions()} 不同，此方法返回的字符串包含
     * 条件值的哈希（而非掩码），确保不同参数值的查询产生不同的缓存键。
     *
     * <p>
     * <strong>安全说明：</strong>值通过 {@code hashCode()} 哈希后写入缓存键，原始值不暴露。
     *
     * @return 包含参数值哈希的缓存键字符串
     */
    public String cacheKey() {

        StringBuilder sb = new StringBuilder("Q:");
        if (!groupStack.isEmpty()) {
            sb.append("ROOT(");
            for (ConditionNode node : conditions) {
                appendCacheKey(sb, node);
            }
            sb.append(")#NESTED(");
        }
        for (ConditionNode node : currentGroup()) {
            appendCacheKey(sb, node);
        }
        if (!groupStack.isEmpty()) {
            sb.append(")");
        }
        if (distinct) {
            sb.append("#DISTINCT");
        }
        if (!groupByFields.isEmpty()) {
            sb.append("#GROUPBY(");
            for (int i = 0; i < groupByFields.size(); i++) {
                if (i > 0) {
                    sb.append(",");
                }
                sb.append(groupByFields.get(i));
            }
            sb.append(")");
        }
        if (!havingConditions.isEmpty()) {
            sb.append("#HAVING(");
            for (int i = 0; i < havingConditions.size(); i++) {
                if (i > 0) {
                    sb.append(",");
                }
                sb.append(havingConditions.get(i).hashCode());
            }
            sb.append(")");
        }
        if (!orderNodes.isEmpty()) {
            sb.append("#ORDERBY(");
            for (int i = 0; i < orderNodes.size(); i++) {
                if (i > 0) {
                    sb.append(",");
                }
                ConditionNode.OrderNode node = orderNodes.get(i);
                sb.append(node.fieldName).append(node.asc ? "ASC" : "DESC");
            }
            sb.append(")");
        }
        if (queryTimeout != null) {
            sb.append("#TIMEOUT(").append(queryTimeout).append(")");
        }
        if (lockMode != null) {
            sb.append("#LOCK(").append(lockMode).append(")");
        }
        return sb.toString();
    }

    private static void appendCacheKey(StringBuilder sb, ConditionNode node) {
        if (node instanceof ConditionNode.SimpleNode sn) {
            sb.append(sn.fieldName).append(sn.op);
            appendValue(sb, sn.value);
            sb.append(";");
        } else if (node instanceof ConditionNode.FuncNode fn) {
            sb.append("FUNC(").append(fn.functionName);
            for (Object p : fn.params) {
                sb.append(",");
                appendValue(sb, p);
            }
            sb.append(")");
        } else if (node instanceof ConditionNode.JoinNode jn) {
            sb.append("JOIN(").append(jn.fieldName).append(",").append(jn.joinType).append(",");
            for (ConditionNode inner : jn.innerConditions) {
                appendCacheKey(sb, inner);
            }
            sb.append(")");
        } else if (node instanceof ConditionNode.OrNode on) {
            sb.append("OR(");
            for (ConditionNode inner : on.nodes()) {
                appendCacheKey(sb, inner);
            }
            sb.append(")");
        } else if (node instanceof ConditionNode.AndNode an) {
            sb.append("AND(");
            for (ConditionNode inner : an.nodes()) {
                appendCacheKey(sb, inner);
            }
            sb.append(")");
        } else if (node instanceof ConditionNode.MultiLikeNode mln) {
            sb.append("MULTILIKE(").append(mln.keyword);
            for (String f : mln.fieldNames) {
                sb.append(",").append(f);
            }
            sb.append(")");
        } else if (node instanceof ConditionNode.CollectionNode cn) {
            sb.append("COLLECTION(").append(cn.fieldName).append(",").append(cn.op).append(")");
        } else if (node instanceof ConditionNode.ExistsNode<?> en) {
            sb.append(en.negate ? "NOTEXISTS(" : "EXISTS(");
            sb.append(en.subEntity.getSimpleName());
            sb.append(",condHash=").append(en.config.hashCode()).append(")");
        } else if (node instanceof ConditionNode.InSubQueryNode<?> isn) {
            sb.append(isn.negate ? "NOTINSUBQUERY(" : "INSUBQUERY(");
            sb.append(isn.outerFieldName).append(",").append(isn.subEntity.getSimpleName());
            sb.append(",condHash=").append(isn.config.hashCode()).append(")");
        } else if (node instanceof ConditionNode.NegateNode nn) {
            sb.append("NOT(");
            appendCacheKey(sb, nn.inner());
            sb.append(")");
        } else if (node instanceof ConditionNode.RawNode rn) {
            sb.append("RAW(").append(System.identityHashCode(rn.fn)).append(")");
        } else {
            sb.append(node.getClass().getSimpleName()).append("@").append(node.hashCode());
        }
    }

    private static void appendValue(StringBuilder sb, Object value) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof Collection<?> col) {
            sb.append("COLLECTION[").append(col.size()).append("]");
            for (Object item : col) {
                appendHashedValue(sb, item);
            }
        } else if (value instanceof Object[] arr) {
            sb.append("ARRAY[").append(arr.length).append("]");
            for (Object item : arr) {
                appendHashedValue(sb, item);
            }
        } else {
            appendHashedValue(sb, value);
        }
    }

    /**
     * 将值的哈希码写入缓存键，而非原始值。防止密码、token 等敏感数据泄露到缓存键中。
     * hashCode 碰撞概率极低（字段名+运算符+哈希的组合），对缓存键唯一性无实际影响。
     */
    private static void appendHashedValue(StringBuilder sb, Object value) {
        if (value instanceof String s) {
            sb.append("H[").append(s.length()).append(":").append(s.hashCode()).append("]");
        } else {
            String s = String.valueOf(value);
            sb.append("H[").append(s.length()).append(":").append(s.hashCode()).append("]");
        }
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
        int maxTimeout = getGlobalConfig().getMaxTimeoutSeconds();
        if (seconds > maxTimeout) {
            throw new IllegalArgumentException("timeout must not exceed " + maxTimeout + " seconds, got: " + seconds);
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
     * <strong>推荐使用类型安全的替代方法：</strong> {@link #havingCount(SFunction, ConditionNode.Op, long)} 等。
     * 此方法仅作为逃生舱供高级用户使用。
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
     * Sort.unsorted())} 以保留此处设置的排序。
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
        if (log.isDebugEnabled()) {
            log.debug("QuerySpec: ORDER BY ASC {}",
                Arrays.stream(fields).map(LambdaUtils::getPropertyName).collect(Collectors.joining(", ")));
        }
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
        if (log.isDebugEnabled()) {
            log.debug("QuerySpec: ORDER BY DESC {}",
                Arrays.stream(fields).map(LambdaUtils::getPropertyName).collect(Collectors.joining(", ")));
        }
        return this;
    }

    /**
     * 添加 INNER JOIN 关联。
     *
     * @param field 关联字段的方法引用
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
    @Override
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
    @Override
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

    // ---- 内部方法：Consumer 模式自动关闭 ----

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
     * 将此 QuerySpec 与另一个 Specification 使用 AND 组合。
     *
     * <p>
     * 如果 {@code external} 为 null，则返回自身（等价于无额外条件）。
     *
     * @param external 要组合的外部 Specification（可以为 null）
     * @return 组合后的 Specification 实例
     */
    public Specification<T> toSpecification(@Nullable Specification<T> external) {
        if (external == null) {
            return this;
        }
        return this.and(external);
    }

    /**
     * 将此 QuerySpec 与另一个 QuerySpec 使用 OR 组合，返回组合后的 {@link Specification}。
     *
     * <p>
     * 如果需要在保持 {@link QuerySpec} 类型的同时构建 OR 条件，请使用
     * {@link #or(java.util.function.Consumer)} 消费者模式：
     * <pre>{@code
     * qs.or(o -> o.eq(User::getStatus, "PENDING").eq(User::getStatus, "REVIEW"));
     * }</pre>
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
     * <p>
     * <strong>所有权转移：</strong>调用 {@code then()} 后，{@code other} 的条件节点列表被追加到当前 spec。
     * 虽然顶层列表是浅拷贝，但 {@link ConditionNode} 对象本身是共享引用。
     * 因此，调用后不应再修改 {@code other} 的条件节点，否则会影响当前 spec。
     * 推荐用法：创建新的 {@code other} 并在调用 {@code then()} 后不再使用它。
     *
     * @param other 另一个 QuerySpec 实例
     * @return 当前 QuerySpec 实例，支持链式调用
     * @throws IllegalStateException 如果另一个 spec 存在未关闭的 or() 组
     */
    public QuerySpec<T> then(QuerySpec<T> other) {
        if (other == null) {
            return this;
        }
        // 验证两个 spec 的组都已正确关闭，防止状态不一致
        if (!this.groupStack.isEmpty()) {
            throw new IllegalStateException(
                "Cannot merge into a QuerySpec with unclosed or() groups. Close all groups with endOr() before calling then().");
        }
        if (!other.groupStack.isEmpty()) {
            throw new IllegalStateException(
                "Cannot merge a QuerySpec with unclosed or() groups. Close all groups with endOr() before calling then().");
        }
        this.conditions.addAll(new ArrayList<>(other.conditions));
        if (other.distinct) {
            this.distinct = true;
        }
        this.groupByFields.addAll(other.groupByFields);
        this.havingConditions.addAll(other.havingConditions);
        this.orderNodes.addAll(other.orderNodes);
        // 复制查询设置：仅当当前实例未设置时，采用另一个实例的值
        if (other.queryTimeout != null && this.queryTimeout == null) {
            // 从另一个 spec 复制时验证超时范围（必须与 timeout() 验证一致）
            int maxTimeout = getGlobalConfig().getMaxTimeoutSeconds();
            if (other.queryTimeout <= 0 || other.queryTimeout > maxTimeout) {
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
     * 将另一个 QuerySpec 的条件以 AND 语义合并到当前实例。
     *
     * <p>
     * 等价于 {@link #then(QuerySpec)}，保留 {@link QuerySpec} 类型以支持链式调用。
     * 如果需要得到 {@link Specification} 类型，使用 {@link #toSpecification()} 或直接传入
     * {@code repository.findAll(this.and(other.toSpecification()))}。
     *
     * @param other 另一个 QuerySpec 实例
     * @return 当前 QuerySpec 实例（条件已合并），支持链式调用
     */
    public QuerySpec<T> and(QuerySpec<T> other) {
        return then(other);
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
            applyDistinctAndGroupBy(root, query, cb);
            applyOrderBy(root, query, cb);
        }
        Map<String, Join<?, ?>> joinCache = new LinkedHashMap<>();
        java.util.Set<String> fetchPaths = java.util.Collections.newSetFromMap(new java.util.HashMap<>());
        List<Predicate> predicates = new ArrayList<>();
        for (ConditionNode node : conditions) {
            Predicate p = NodeResolver.resolveNode(node, root, root, query, cb, joinCache, null, fetchPaths);
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
     *     .toSql();
     * // 返回: "Query{SELECT DISTINCT, WHERE: [status = EQ, age = GT], ORDER BY: [name ASC]}"
     * }</pre>
     *
     * @return 查询条件描述字符串
     */
    public String toSql() {
        StringBuilder sb = new StringBuilder("Query{");

        if (distinct) {
            sb.append("SELECT DISTINCT, ");
        }

        // WHERE 条件
        if (!conditions.isEmpty()) {
            sb.append("WHERE: ").append(conditions).append(", ");
        }

        // GROUP BY
        if (!groupByFields.isEmpty()) {
            sb.append("GROUP BY: ").append(groupByFields).append(", ");
        }

        // HAVING
        if (!havingConditions.isEmpty()) {
            sb.append("HAVING: ").append(havingConditions.size()).append(" conditions, ");
        }

        // ORDER BY
        if (!orderNodes.isEmpty()) {
            sb.append("ORDER BY: ").append(orderNodes).append(", ");
        }

        // 超时和锁模式
        if (queryTimeout != null) {
            sb.append("timeout=").append(queryTimeout).append("s, ");
        }
        if (lockMode != null) {
            sb.append("lockMode=").append(lockMode).append(", ");
        }

        // 移除末尾的逗号和空格
        int len = sb.length();
        if (len > 6 && sb.charAt(len - 2) == ',') {
            sb.setLength(len - 2);
        }
        sb.append('}');
        return sb.toString();
    }
}
