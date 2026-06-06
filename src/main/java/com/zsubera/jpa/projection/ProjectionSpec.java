package com.zsubera.jpa.projection;

import com.zsubera.jpa.exception.MyJpaPlusException;
import com.zsubera.jpa.spec.ConditionBuilder;
import com.zsubera.jpa.spec.ConditionNode;
import com.zsubera.jpa.spec.PredicateHelper;
import com.zsubera.jpa.spec.QuerySpec;
import com.zsubera.jpa.spec.SFunction;
import com.zsubera.jpa.template.MyJpaTemplate;
import com.zsubera.jpa.update.SoftDeleteHelper;
import com.zsubera.jpa.util.LambdaUtils;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

/**
 * DTO 投影查询的类型安全构建器。
 *
 * <p>
 * 从实体中选择特定字段，并以 {@link Tuple} 或通过 {@code CriteriaBuilder.construct()} 返回自定义 DTO 的形式返回结果。支持 JOIN 关联、ORDER BY 排序和分页查询。
 *
 * <p>
 * <strong>线程安全：</strong>此实例非线程安全。每个线程应使用独立的 {@code ProjectionSpec} 实例。
 *
 * <p>
 * 使用示例：
 *
 * <pre>{@code
 * List<Tuple> results = new ProjectionSpec<>(User.class).select(User::getName).select(User::getEmail)
 *     .join(User::getDepartment, j -> j.eq(Department::getName, "Engineering")).orderByAsc(User::getName)
 *     .where(q -> q.eq(User::getStatus, "ACTIVE")).toTupleQuery(entityManager).getResultList();
 * }</pre>
 *
 * @param <T> 根实体类型
 */
public class ProjectionSpec<T> {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ProjectionSpec.class);

    /** 聚合函数类型。 */
    private enum AggregateType {
        COUNT, COUNT_DISTINCT, SUM, AVG, MAX, MIN
    }

    /** 描述聚合选择的内部记录类。 */
    private record AggregateSelection(AggregateType type, String alias, String fieldName) {
    }

    private final Class<T> entityClass;
    private final Map<String, SFunction<T, ?>> selections = new LinkedHashMap<>();
    private final List<AggregateSelection> aggregateSelections = new ArrayList<>();
    private final QuerySpec<T> querySpec = new QuerySpec<>();
    private final List<JoinSpec> joins = new ArrayList<>();
    private final List<OrderSpec> orderSpecs = new ArrayList<>();
    private final List<String> groupByFields = new ArrayList<>();
    private Class<?> dtoClass;
    private boolean distinct = false;

    /** 是否启用软删除过滤。 */
    private boolean softDeleteEnabled = false;

    /**
     * 描述 JOIN 子句的内部记录类。
     *
     * <p>
     * <strong>线程安全说明：</strong>JoinSpec 不是线程安全的。{@code cachedConditions} 字段在查询构建期间被修改， 因此不能在多线程环境中并发使用同一个 ProjectionSpec
     * 实例。每个线程应使用独立的 ProjectionSpec 实例。
     */
    private static final class JoinSpec {
        final String fieldName;
        final Consumer<?> config;
        final boolean left;
        /** 缓存的条件列表，避免重复调用 Consumer 配置函数。 */
        List<ConditionNode> cachedConditions;

        <E> JoinSpec(String fieldName, Consumer<ProjectionJoinGroup<E>> config, boolean left) {
            this.fieldName = fieldName;
            this.config = config;
            this.left = left;
        }

        @SuppressWarnings("unchecked")
        List<ConditionNode> getConditions() {
            if (cachedConditions == null) {
                try {
                    @SuppressWarnings("unchecked")
                    Consumer<ProjectionJoinGroup<Object>> cfg =
                        (Consumer<ProjectionJoinGroup<Object>>)(Consumer<?>)config;
                    ProjectionJoinGroup<Object> group = ProjectionJoinGroup.create();
                    cfg.accept(group);
                    cachedConditions = group.conditions();
                } catch (IllegalArgumentException | MyJpaPlusException e) {
                    throw e;
                } catch (RuntimeException e) {
                    // 不设置 cachedConditions，让异常自然传播
                    // 重试时会重新执行配置 lambda
                    throw new MyJpaPlusException("Failed to configure join conditions", e);
                }
            }
            return cachedConditions;
        }
    }

    /**
     * JOIN 目标实体的嵌套条件构建器。
     *
     * <p>
     * 实现 {@link ConditionBuilder} 接口，复用所有类型安全的条件方法（eq、like、in 等）， 避免与主接口的代码重复。条件节点复用 {@link ConditionNode} 体系。
     *
     * @param <E> JOIN 目标实体类型
     */
    public static final class ProjectionJoinGroup<E> implements ConditionBuilder<E, ProjectionJoinGroup<E>> {

        private final List<ConditionNode> conditions = new ArrayList<>();

        private ProjectionJoinGroup() {}

        /**
         * 创建 ProjectionJoinGroup 实例。
         *
         * @param <E> JOIN 目标实体类型
         * @return 新的 ProjectionJoinGroup 实例
         */
        static <E> ProjectionJoinGroup<E> create() {
            return new ProjectionJoinGroup<>();
        }

        @Override
        @SuppressFBWarnings("EI_EXPOSE_REP")
        public List<ConditionNode> conditions() {
            return conditions;
        }
    }

    /**
     * 描述 ORDER BY 子句的内部记录类。
     *
     * @param fieldName 字段名称
     * @param asc 是否升序排列
     */
    private record OrderSpec(String fieldName, boolean asc) {
    }

    /**
     * 创建投影查询构建器实例。
     *
     * @param entityClass 要查询的实体类
     */
    public ProjectionSpec(Class<T> entityClass) {
        this.entityClass = entityClass;
    }

    /**
     * 创建默认启用软删除过滤的 ProjectionSpec 实例。
     *
     * <p>
     * 使用此工厂方法可避免手动调用 {@link #withSoftDeleteFilter()}， 减少开发者遗忘启用安全过滤的风险。软删除过滤仅在实体有 {@code @SoftDelete} 字段时启用。
     *
     * @param entityClass 要查询的实体类
     * @return 配置好的 ProjectionSpec 实例
     */
    public static <T> ProjectionSpec<T> withDefaults(Class<T> entityClass) {
        ProjectionSpec<T> spec = new ProjectionSpec<>(entityClass);
        // 如果实体有 @SoftDelete 字段则自动启用软删除过滤
        if (SoftDeleteHelper.findSoftDeleteField(entityClass) != null) {
            spec.softDeleteEnabled = true;
        }
        return spec;
    }

    /**
     * 向 SELECT 子句添加要查询的字段。
     *
     * @param field 实体属性的方法引用
     * @return 当前 ProjectionSpec 实例，支持链式调用
     */
    public ProjectionSpec<T> select(SFunction<T, ?> field) {
        if (field == null) {
            throw new IllegalArgumentException("field must not be null");
        }
        selections.put(LambdaUtils.getPropertyName(field), field);
        return this;
    }

    /**
     * 启用 SELECT DISTINCT 查询，去除重复结果。
     *
     * @return 当前 ProjectionSpec 实例，支持链式调用
     */
    public ProjectionSpec<T> distinct() {
        this.distinct = true;
        return this;
    }

    /**
     * 添加 COUNT(*) 聚合投影，别名为 {@code "count"}。
     *
     * @return 当前 ProjectionSpec 实例，支持链式调用
     */
    public ProjectionSpec<T> selectCount() {
        aggregateSelections.add(new AggregateSelection(AggregateType.COUNT, "count", null));
        return this;
    }

    /**
     * 添加 COUNT(DISTINCT *) 聚合投影，别名为 {@code "count"}。
     *
     * <p>
     * 与 {@link #selectCount()} 不同，此方法使用 {@code COUNT(DISTINCT root)} 进行去重计数， 适用于 JOIN 产生重复行的场景。
     *
     * @return 当前 ProjectionSpec 实例，支持链式调用
     */
    public ProjectionSpec<T> selectCountDistinct() {
        aggregateSelections.add(new AggregateSelection(AggregateType.COUNT_DISTINCT, "count", null));
        return this;
    }

    /**
     * 添加 SUM(field) 聚合投影，别名为 {@code "sum_<fieldName>"}。
     *
     * @param field 要求和的实体属性方法引用
     * @return 当前 ProjectionSpec 实例，支持链式调用
     * @throws IllegalArgumentException 如果 field 为 null
     */
    public ProjectionSpec<T> selectSum(SFunction<T, ?> field) {
        if (field == null) {
            throw new IllegalArgumentException("field must not be null");
        }
        String name = LambdaUtils.getPropertyName(field);
        aggregateSelections.add(new AggregateSelection(AggregateType.SUM, "sum_" + name, name));
        return this;
    }

    /**
     * 添加 AVG(field) 聚合投影，别名为 {@code "avg_<fieldName>"}。
     *
     * @param field 要求平均值的实体属性方法引用
     * @return 当前 ProjectionSpec 实例，支持链式调用
     * @throws IllegalArgumentException 如果 field 为 null
     */
    public ProjectionSpec<T> selectAvg(SFunction<T, ?> field) {
        if (field == null) {
            throw new IllegalArgumentException("field must not be null");
        }
        String name = LambdaUtils.getPropertyName(field);
        aggregateSelections.add(new AggregateSelection(AggregateType.AVG, "avg_" + name, name));
        return this;
    }

    /**
     * 添加 MAX(field) 聚合投影，别名为 {@code "max_<fieldName>"}。
     *
     * @param field 要求最大值的实体属性方法引用
     * @return 当前 ProjectionSpec 实例，支持链式调用
     * @throws IllegalArgumentException 如果 field 为 null
     */
    public ProjectionSpec<T> selectMax(SFunction<T, ?> field) {
        if (field == null) {
            throw new IllegalArgumentException("field must not be null");
        }
        String name = LambdaUtils.getPropertyName(field);
        aggregateSelections.add(new AggregateSelection(AggregateType.MAX, "max_" + name, name));
        return this;
    }

    /**
     * 添加 MIN(field) 聚合投影，别名为 {@code "min_<fieldName>"}。
     *
     * @param field 要求最小值的实体属性方法引用
     * @return 当前 ProjectionSpec 实例，支持链式调用
     * @throws IllegalArgumentException 如果 field 为 null
     */
    public ProjectionSpec<T> selectMin(SFunction<T, ?> field) {
        if (field == null) {
            throw new IllegalArgumentException("field must not be null");
        }
        String name = LambdaUtils.getPropertyName(field);
        aggregateSelections.add(new AggregateSelection(AggregateType.MIN, "min_" + name, name));
        return this;
    }

    /**
     * 指定 DTO 构造函数投影的目标类。DTO 类必须有一个接受所有选定字段类型的构造函数。
     *
     * @param dtoClass DTO 类
     * @return 当前 ProjectionSpec 实例，支持链式调用
     * @throws IllegalArgumentException 如果 dtoClass 为 null
     */
    public ProjectionSpec<T> asDto(Class<?> dtoClass) {
        if (dtoClass == null) {
            throw new IllegalArgumentException("dtoClass must not be null");
        }
        this.dtoClass = dtoClass;
        return this;
    }

    /**
     * 启用软删除过滤，自动排除已软删除的记录。
     *
     * <p>
     * 使用示例：
     *
     * <pre>{@code
     * new ProjectionSpec<>(User.class).select(User::getName).withSoftDeleteFilter().toTupleQuery(em);
     * }</pre>
     *
     * @return 当前 ProjectionSpec 实例，支持链式调用
     */
    public ProjectionSpec<T> withSoftDeleteFilter() {
        this.softDeleteEnabled = true;
        return this;
    }

    /**
     * 添加 GROUP BY 字段。
     *
     * @param field 要分组的实体属性方法引用
     * @return 当前 ProjectionSpec 实例，支持链式调用
     * @throws IllegalArgumentException 如果 field 为 null
     */
    public ProjectionSpec<T> groupBy(SFunction<T, ?> field) {
        if (field == null) {
            throw new IllegalArgumentException("field must not be null");
        }
        groupByFields.add(LambdaUtils.getPropertyName(field));
        return this;
    }

    /**
     * 添加 HAVING 条件。多个 HAVING 条件之间为 AND 关系。
     *
     * <p>
     * 使用示例：
     *
     * <pre>{@code
     * new ProjectionSpec<>(Order.class).select(Order::getCustomerId).selectCount(Order::getId)
     *     .groupBy(Order::getCustomerId).having((root, cb) -> cb.greaterThan(cb.count(root.get("id")), 5L))
     *     .toTupleQuery(em);
     * }</pre>
     *
     * @param predicate HAVING 条件函数
     * @return 当前 ProjectionSpec 实例，支持链式调用
     * @throws IllegalArgumentException 如果 predicate 为 null
     */
    public ProjectionSpec<T> having(java.util.function.BiFunction<jakarta.persistence.criteria.Root<T>, CriteriaBuilder,
        jakarta.persistence.criteria.Predicate> predicate) {
        if (predicate == null) {
            throw new IllegalArgumentException("predicate must not be null");
        }
        // 将 HAVING 谓词累积到列表中（AND 语义），
        // 而非覆盖前一个。
        this.havingPredicateFns.add(predicate);
        return this;
    }

    /** HAVING 条件函数列表，延迟应用。多个条件通过 AND 组合。 */
    @SuppressWarnings("rawtypes")
    private final List<java.util.function.BiFunction<jakarta.persistence.criteria.Root<T>, CriteriaBuilder,
        jakarta.persistence.criteria.Predicate>> havingPredicateFns = new ArrayList<>();

    /**
     * 添加 INNER JOIN 关联查询，可对关联实体设置条件。
     *
     * @param field 一对多或一对一关系的方法引用
     * @param config 用于配置 JOIN 条件的消费者函数
     * @param <E> JOIN 目标实体类型
     * @return 当前 ProjectionSpec 实例，支持链式调用
     */
    public <E> ProjectionSpec<T> join(SFunction<T, ?> field, Consumer<ProjectionJoinGroup<E>> config) {
        if (field == null) {
            throw new IllegalArgumentException("field must not be null");
        }
        if (config == null) {
            throw new IllegalArgumentException("config must not be null");
        }
        joins.add(new JoinSpec(LambdaUtils.getPropertyName(field), config, false));
        return this;
    }

    /**
     * 添加 LEFT JOIN 关联查询，可对关联实体设置条件。
     *
     * <p>
     * LEFT JOIN 会返回左表的所有记录，即使右表中没有匹配的记录。
     *
     * @param field 一对多或一对一关系的方法引用
     * @param config 用于配置 JOIN 条件的消费者函数
     * @param <E> JOIN 目标实体类型
     * @return 当前 ProjectionSpec 实例，支持链式调用
     */
    public <E> ProjectionSpec<T> leftJoin(SFunction<T, ?> field, Consumer<ProjectionJoinGroup<E>> config) {
        if (field == null) {
            throw new IllegalArgumentException("field must not be null");
        }
        if (config == null) {
            throw new IllegalArgumentException("config must not be null");
        }
        joins.add(new JoinSpec(LambdaUtils.getPropertyName(field), config, true));
        return this;
    }

    /**
     * 添加升序排序规则。
     *
     * @param field 实体属性的方法引用
     * @return 当前 ProjectionSpec 实例，支持链式调用
     */
    public ProjectionSpec<T> orderByAsc(SFunction<T, ?> field) {
        if (field == null) {
            throw new IllegalArgumentException("field must not be null");
        }
        orderSpecs.add(new OrderSpec(LambdaUtils.getPropertyName(field), true));
        return this;
    }

    /**
     * 添加降序排序规则。
     *
     * @param field 实体属性的方法引用
     * @return 当前 ProjectionSpec 实例，支持链式调用
     */
    public ProjectionSpec<T> orderByDesc(SFunction<T, ?> field) {
        if (field == null) {
            throw new IllegalArgumentException("field must not be null");
        }
        orderSpecs.add(new OrderSpec(LambdaUtils.getPropertyName(field), false));
        return this;
    }

    /**
     * 添加 WHERE 查询条件。
     *
     * @param config 用于配置查询条件的消费者函数
     * @return 当前 ProjectionSpec 实例，支持链式调用
     */
    public ProjectionSpec<T> where(Consumer<QuerySpec<T>> config) {
        if (config == null) {
            throw new IllegalArgumentException("config must not be null");
        }
        config.accept(querySpec);
        return this;
    }

    /**
     * 构建并返回以 {@link Tuple} 为结果类型的类型安全查询。
     *
     * <p>
     * <strong>安全提示：</strong>此方法使用默认最大行数限制（{@value MyJpaTemplate#DEFAULT_MAX_RESULTS}）。 如需自定义限制，请使用
     * {@link #toTupleQuery(EntityManager, int)} 重载方法。
     *
     * @param em JPA 实体管理器
     * @return 返回 Tuple 结果的 TypedQuery 实例
     */
    public TypedQuery<Tuple> toTupleQuery(EntityManager em) {
        return toTupleQuery(em, MyJpaTemplate.DEFAULT_MAX_RESULTS);
    }

    /**
     * 构建并返回以 {@link Tuple} 为结果类型的类型安全查询，限制最大返回行数。
     *
     * @param em JPA 实体管理器
     * @param maxResults 最大返回行数，{@code -1} 表示不限制
     * @return 返回 Tuple 结果的 TypedQuery 实例
     */
    public TypedQuery<Tuple> toTupleQuery(EntityManager em, int maxResults) {
        // 校验选择列表不为空
        if (selections.isEmpty() && aggregateSelections.isEmpty()) {
            throw new IllegalArgumentException("ProjectionSpec must have at least one selection. "
                + "Use select() or addAggregation() before executing.");
        }
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Tuple> query = cb.createTupleQuery();
        Root<T> root = query.from(entityClass);

        try {
            // 应用 JOIN（对 root 有副作用）
            resolveJoins(root, cb);

            // 应用选择列表
            List<jakarta.persistence.criteria.Selection<?>> selectionList = buildSelectionList(root, cb);
            query.multiselect(selectionList);

            // 应用 DISTINCT
            if (distinct) {
                query.distinct(true);
            }

            // 应用 WHERE
            applyPredicate(root, query, cb);

            // 应用 GROUP BY
            if (!groupByFields.isEmpty()) {
                List<jakarta.persistence.criteria.Expression<?>> groupByExpressions = new ArrayList<>();
                for (String field : groupByFields) {
                    groupByExpressions.add(root.get(field));
                }
                query.groupBy(groupByExpressions);
            }

            // 应用 HAVING
            applyHaving(root, cb, query);

            // 应用 ORDER BY
            applyOrderBy(root, cb, query);

            TypedQuery<Tuple> typedQuery = em.createQuery(query);
            if (maxResults > 0) {
                typedQuery.setMaxResults(maxResults);
                if (maxResults == MyJpaTemplate.DEFAULT_MAX_RESULTS && selections.size() > 0 && log.isDebugEnabled()) {
                    log.debug(
                        "ProjectionSpec query limited to {} rows by default. "
                            + "Use toTupleQuery(em, -1) for unlimited results or toTupleQuery(em, N) for custom limit.",
                        maxResults);
                }
            }
            return typedQuery;
        } finally {
            clearJoinCache();
        }
    }

    /**
     * 流式查询 Tuple 结果，适用于处理大数据集。
     *
     * <p>
     * <strong>重要：必须使用 try-with-resources 确保资源关闭：</strong>
     *
     * <pre>{@code
     * try (Stream<Tuple> stream = projection.getResultStream(em)) {
     *     stream.forEach(row -> process(row));
     * }
     * }</pre>
     *
     * @param em JPA 实体管理器
     * @return Tuple 结果的 Stream（必须由调用方关闭）
     */
    public java.util.stream.Stream<Tuple> getResultStream(EntityManager em) {
        if (em == null) {
            throw new IllegalArgumentException("em must not be null");
        }
        TypedQuery<Tuple> query = toTupleQuery(em, -1);
        // P-01：为流式查询设置 fetchSize 以启用服务端游标。
        // PostgreSQL 需要 fetchSize > 0 才能实现真正的流式传输；
        // MySQL 使用 Integer.MIN_VALUE 进入流式模式。
        int fetchSize = com.zsubera.jpa.util.PageableHelper.determineFetchSize(em);
        if (fetchSize != 0) {
            query.setHint("jakarta.persistence.query.fetchSize", fetchSize);
        }
        return query.getResultStream();
    }

    /**
     * 构建并返回以 DTO 为结果类型的类型安全查询。
     *
     * <p>
     * 必须先调用 {@link #asDto(Class)} 方法指定 DTO 类。
     *
     * <p>
     * <strong>安全提示：</strong>此方法使用默认最大行数限制（{@value MyJpaTemplate#DEFAULT_MAX_RESULTS}）。 如需自定义限制，请使用
     * {@link #toDtoQuery(EntityManager, int)} 重载方法。
     *
     * @param em JPA 实体管理器
     * @param <R> DTO 结果类型
     * @return 返回 DTO 结果的 TypedQuery 实例
     * @throws IllegalStateException 如果未调用 {@link #asDto(Class)} 方法
     */
    @SuppressWarnings("unchecked")
    public <R> TypedQuery<R> toDtoQuery(EntityManager em) {
        return toDtoQuery(em, MyJpaTemplate.DEFAULT_MAX_RESULTS);
    }

    /**
     * 构建并返回以 DTO 为结果类型的类型安全查询，限制最大返回行数。
     *
     * <p>
     * 必须先调用 {@link #asDto(Class)} 方法指定 DTO 类。
     *
     * @param em JPA 实体管理器
     * @param maxResults 最大返回行数，{@code -1} 表示不限制
     * @param <R> DTO 结果类型
     * @return 返回 DTO 结果的 TypedQuery 实例
     * @throws IllegalStateException 如果未调用 {@link #asDto(Class)} 方法
     */
    @SuppressWarnings("unchecked")
    public <R> TypedQuery<R> toDtoQuery(EntityManager em, int maxResults) {
        if (dtoClass == null) {
            throw new IllegalStateException("asDto() must be called before toDtoQuery()");
        }
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<R> query = (CriteriaQuery<R>)cb.createQuery(dtoClass);
        Root<T> root = query.from(entityClass);

        try {
            // 应用 JOIN
            resolveJoins(root, cb);

            // 将选择列表作为构造函数参数应用
            List<jakarta.persistence.criteria.Selection<?>> selectionList = buildSelectionList(root, cb);
            query.select((CompoundSelection<R>)cb.construct(dtoClass,
                selectionList.toArray(new jakarta.persistence.criteria.Selection[0])));

            // 应用 DISTINCT
            if (distinct) {
                query.distinct(true);
            }

            // 应用 WHERE
            applyPredicate(root, query, cb);

            // 应用 GROUP BY
            if (!groupByFields.isEmpty()) {
                List<jakarta.persistence.criteria.Expression<?>> groupByExpressions = new ArrayList<>();
                for (String gf : groupByFields) {
                    groupByExpressions.add(root.get(gf));
                }
                query.groupBy(groupByExpressions);
            }

            // 应用 HAVING
            applyHaving(root, cb, query);

            // 应用 ORDER BY
            applyOrderBy(root, cb, query);

            TypedQuery<R> typedQuery = em.createQuery(query);
            if (maxResults > 0) {
                typedQuery.setMaxResults(maxResults);
            }
            return typedQuery;
        } finally {
            clearJoinCache();
        }
    }

    /**
     * 分页执行投影查询，返回 Tuple 结果。
     *
     * <p>
     * <strong>性能说明：</strong>JOIN 描述符通过 {@link JoinSpec#getConditions()} 缓存，避免对每个 Root（count 和 data 查询）重复调用 Consumer。
     * 数据查询直接构建而非委托给 {@link #toTupleQuery(EntityManager)}，避免第二次 {@code resolveJoins()} 调用。
     *
     * <p>
     * <strong>计数说明：</strong>使用 {@code countDistinct(root)} 进行精确计数，避免 JOIN 产生重复行。 对于一对多 JOIN 和复杂主键的场景，考虑使用
     * {@code QuerySpec} 直接进行子查询计数。
     *
     * @param em JPA 实体管理器
     * @param pageable 分页信息
     * @return 分页投影查询结果
     * @throws IllegalArgumentException 如果分页 offset 过大
     */
    public Page<Tuple> findPage(EntityManager em, Pageable pageable) {
        if (em == null) {
            throw new IllegalArgumentException("em must not be null");
        }
        if (pageable == null) {
            throw new IllegalArgumentException("pageable must not be null");
        }
        CriteriaBuilder cb = em.getCriteriaBuilder();

        // 处理无分页情况
        if (pageable.isUnpaged()) {
            TypedQuery<Tuple> query = toTupleQuery(em);
            List<Tuple> allContent = query.getResultList();
            return new PageImpl<>(allContent);
        }

        try {
            // 构建计数和数据查询，每个 root 共享一次 JOIN 解析
            Long total;
            // 计数查询
            CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
            Root<T> countRoot = countQuery.from(entityClass);
            resolveJoins(countRoot, cb);
            if (!groupByFields.isEmpty() && !havingPredicateFns.isEmpty()) {
                // 当存在 GROUP BY + HAVING 时，使用单独的计数查询，
                // 按相同字段分组并应用 HAVING，然后对分组计数
                CriteriaQuery<Long> havingCountQuery = cb.createQuery(Long.class);
                Root<T> havingRoot = havingCountQuery.from(entityClass);
                resolveJoins(havingRoot, cb);
                // 应用 WHERE 谓词
                applyPredicate(havingRoot, havingCountQuery, cb);
                // 应用 GROUP BY
                List<jakarta.persistence.criteria.Expression<?>> groupByExpressions = new ArrayList<>();
                for (String gf : groupByFields) {
                    groupByExpressions.add(havingRoot.get(gf));
                }
                havingCountQuery.groupBy(groupByExpressions);
                // 应用 HAVING
                applyHavingPredicates(havingRoot, cb, havingCountQuery);
                havingCountQuery.select(cb.countDistinct(havingRoot));
                total = em.createQuery(havingCountQuery).getSingleResult();
            } else {
                // 仅在用户显式启用 distinct 时使用 countDistinct
                if (this.distinct) {
                    countQuery.select(cb.countDistinct(countRoot));
                } else {
                    countQuery.select(cb.count(countRoot));
                }
                applyPredicate(countRoot, countQuery, cb);
                total = em.createQuery(countQuery).getSingleResult();
            }

            // 数据查询——直接构建以避免调用 toTupleQuery() 导致再次 resolveJoins()
            CriteriaQuery<Tuple> dataQuery = cb.createTupleQuery();
            Root<T> dataRoot = dataQuery.from(entityClass);
            resolveJoins(dataRoot, cb);

            List<jakarta.persistence.criteria.Selection<?>> selectionList = buildSelectionList(dataRoot, cb);
            dataQuery.multiselect(selectionList);
            // 仅在用户显式启用时应用 DISTINCT
            if (this.distinct) {
                dataQuery.distinct(true);
            }
            applyPredicate(dataRoot, dataQuery, cb);

            // 应用 GROUP BY
            if (!groupByFields.isEmpty()) {
                List<jakarta.persistence.criteria.Expression<?>> groupByExpressions = new ArrayList<>();
                for (String gf : groupByFields) {
                    groupByExpressions.add(dataRoot.get(gf));
                }
                dataQuery.groupBy(groupByExpressions);
            }

            // 应用 HAVING
            applyHavingPredicates(dataRoot, cb, dataQuery);

            applyOrderBy(dataRoot, cb, dataQuery);

            TypedQuery<Tuple> query = em.createQuery(dataQuery);
            if (pageable.getOffset() > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("Offset too large: " + pageable.getOffset());
            }
            query.setFirstResult((int)pageable.getOffset());
            query.setMaxResults(pageable.getPageSize());
            List<Tuple> content = query.getResultList();

            return new PageImpl<>(content, pageable, total);
        } finally {
            // 清除 JOIN 条件缓存，释放 Consumer 引用防止内存泄漏
            clearJoinCache();
        }
    }

    /**
     * 清除 JOIN 条件缓存。在查询执行完成后调用，释放 Consumer 配置 lambda 的引用。
     */
    private void clearJoinCache() {
        for (JoinSpec js : joins) {
            js.cachedConditions = null;
        }
    }

    /**
     * 构建包含普通字段和聚合字段的选择列表。
     *
     * @param root 查询根实体
     * @param cb CriteriaBuilder 实例
     * @return 包含所有选择项的列表
     */
    @SuppressWarnings("unchecked")
    private List<jakarta.persistence.criteria.Selection<?>> buildSelectionList(Root<T> root, CriteriaBuilder cb) {
        List<jakarta.persistence.criteria.Selection<?>> selectionList = new ArrayList<>();
        for (String alias : selections.keySet()) {
            selectionList.add(root.get(alias).alias(alias));
        }
        for (AggregateSelection agg : aggregateSelections) {
            jakarta.persistence.criteria.Expression<?> expr = switch (agg.type()) {
                case COUNT -> cb.count(root);
                case COUNT_DISTINCT -> cb.countDistinct(root);
                case SUM -> {
                    if (agg.fieldName() == null) {
                        throw new IllegalArgumentException("SUM aggregate requires a field name");
                    }
                    yield cb.sum(root.get(agg.fieldName()));
                }
                case AVG -> {
                    if (agg.fieldName() == null) {
                        throw new IllegalArgumentException("AVG aggregate requires a field name");
                    }
                    yield cb.avg(root.get(agg.fieldName()));
                }
                case MAX -> {
                    if (agg.fieldName() == null) {
                        throw new IllegalArgumentException("MAX aggregate requires a field name");
                    }
                    yield cb.max(root.get(agg.fieldName()));
                }
                case MIN -> {
                    if (agg.fieldName() == null) {
                        throw new IllegalArgumentException("MIN aggregate requires a field name");
                    }
                    yield cb.min(root.get(agg.fieldName()));
                }
            };
            selectionList.add(expr.alias(agg.alias()));
        }
        return selectionList;
    }

    /**
     * 解析并应用所有 JOIN 子句。
     *
     * <p>
     * 使用 {@link JoinSpec#getConditions()} 缓存的条件，避免对每个 Root（count 和 data 查询）重复调用 Consumer 配置函数。
     *
     * <p>
     * 对于 INNER JOIN 和 LEFT JOIN，过滤条件都放置在 ON 子句中。 LEFT JOIN 的 ON 子句语义：当条件不匹配时，右表列为 NULL，左表行仍然保留。 这是 LEFT JOIN
     * 的标准行为，允许查询"没有关联记录"的数据。
     *
     * @param root 查询根实体
     * @param cb CriteriaBuilder 实例
     * @return JOIN 映射关系
     */
    @SuppressWarnings({"unchecked"})
    private Map<String, Join<?, ?>> resolveJoins(Root<T> root, CriteriaBuilder cb) {
        Map<String, Join<?, ?>> joinMap = new LinkedHashMap<>();
        for (JoinSpec js : joins) {
            Join<?, ?> join = joinMap.computeIfAbsent(js.fieldName, k -> js.left
                ? root.join(js.fieldName, jakarta.persistence.criteria.JoinType.LEFT) : root.join(js.fieldName));
            List<Predicate> onPredicates = new ArrayList<>();
            for (ConditionNode node : js.getConditions()) {
                onPredicates.add(resolveJoinCondition(node, join, cb));
            }
            if (!onPredicates.isEmpty()) {
                // INNER JOIN 和 LEFT JOIN 都将条件放在 ON 子句中。
                // LEFT JOIN ON 语义：条件不匹配时右表列为 NULL，左表行保留。
                join.on(cb.and(onPredicates.toArray(new Predicate[0])));
            }
        }
        return joinMap;
    }

    /**
     * 解析单个 JOIN 条件节点为 Predicate。
     *
     * <p>
     * 支持所有 ConditionNode 类型的递归解析：SimpleNode、CollectionNode、OrNode、AndNode、NegateNode。
     *
     * @param node 条件节点
     * @param join JOIN 路径
     * @param cb CriteriaBuilder 实例
     * @return 解析后的 Predicate
     * @throws IllegalArgumentException 如果遇到不支持的节点类型
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private Predicate resolveJoinCondition(ConditionNode node, Join<?, ?> join, CriteriaBuilder cb) {
        if (node instanceof ConditionNode.SimpleNode sn) {
            return resolveSimpleForJoin(sn, join, cb);
        }
        if (node instanceof ConditionNode.CollectionNode cn) {
            return switch (cn.op()) {
                case IS_EMPTY ->
                    cb.isEmpty((Expression<java.util.Collection<?>>)(Expression<?>)join.get(cn.fieldName()));
                case IS_NOT_EMPTY ->
                    cb.isNotEmpty((Expression<java.util.Collection<?>>)(Expression<?>)join.get(cn.fieldName()));
            };
        }
        if (node instanceof ConditionNode.OrNode or) {
            java.util.List<Predicate> preds = new java.util.ArrayList<>();
            for (ConditionNode child : or.nodes()) {
                preds.add(resolveJoinCondition(child, join, cb));
            }
            return preds.isEmpty() ? cb.disjunction() : cb.or(preds.toArray(new Predicate[0]));
        }
        if (node instanceof ConditionNode.AndNode and) {
            java.util.List<Predicate> preds = new java.util.ArrayList<>();
            for (ConditionNode child : and.nodes()) {
                preds.add(resolveJoinCondition(child, join, cb));
            }
            return preds.isEmpty() ? cb.conjunction() : cb.and(preds.toArray(new Predicate[0]));
        }
        if (node instanceof ConditionNode.NegateNode negate) {
            return cb.not(resolveJoinCondition(negate.inner(), join, cb));
        }
        throw new IllegalArgumentException(
            "Unsupported ConditionNode type in JOIN clause: " + node.getClass().getSimpleName()
                + ". Supported types: SimpleNode, CollectionNode, OrNode, AndNode, NegateNode.");
    }

    /**
     * 解析 SimpleNode 条件为 JOIN 上的 Predicate。通过 Op 枚举分派到对应的 CriteriaBuilder 操作。
     *
     * @param node 简单条件节点
     * @param join JOIN 路径
     * @param cb CriteriaBuilder 实例
     * @return 解析后的 Predicate
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private Predicate resolveSimpleForJoin(ConditionNode.SimpleNode node, Join<?, ?> join, CriteriaBuilder cb) {
        return PredicateHelper.resolveSimplePredicate(join, node, cb);
    }

    /**
     * 应用 WHERE 查询条件，包括安全过滤器（软删除）。
     *
     * @param root 查询根实体
     * @param query CriteriaQuery 实例
     * @param cb CriteriaBuilder 实例
     */
    @SuppressWarnings({"rawtypes"})
    private void applyPredicate(Root<T> root, CriteriaQuery<?> query, CriteriaBuilder cb) {
        List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();

        // 应用用户条件
        jakarta.persistence.criteria.Predicate userPredicate = querySpec.toPredicate(root, (CriteriaQuery)query, cb);
        if (userPredicate != null) {
            predicates.add(userPredicate);
        }

        // 应用软删除过滤
        if (softDeleteEnabled) {
            @SuppressWarnings("unchecked")
            Specification<T> softDeleteSpec = SoftDeleteHelper.isNotDeleted((Class<T>)entityClass);
            jakarta.persistence.criteria.Predicate softDeletePredicate = softDeleteSpec.toPredicate(root, query, cb);
            if (softDeletePredicate != null) {
                predicates.add(softDeletePredicate);
            }
        }

        if (!predicates.isEmpty()) {
            query.where(cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0])));
        }
    }

    /**
     * 应用 ORDER BY 排序规则。
     *
     * @param root 查询根实体
     * @param cb CriteriaBuilder 实例
     * @param query CriteriaQuery 实例
     */
    private void applyOrderBy(Root<T> root, CriteriaBuilder cb, CriteriaQuery<?> query) {
        if (!orderSpecs.isEmpty()) {
            List<jakarta.persistence.criteria.Order> orders = new ArrayList<>();
            for (OrderSpec os : orderSpecs) {
                Path<Object> path = root.get(os.fieldName());
                orders.add(os.asc() ? cb.asc(path) : cb.desc(path));
            }
            query.orderBy(orders);
        }
    }

    /**
     * 应用 HAVING 条件到 Tuple 类型的查询。将多个 HAVING 条件通过 AND 组合。
     *
     * @param root 查询根实体
     * @param cb CriteriaBuilder 实例
     * @param query CriteriaQuery 实例
     */
    private void applyHaving(Root<T> root, CriteriaBuilder cb, CriteriaQuery<?> query) {
        applyHavingPredicates(root, cb, query);
    }

    /**
     * 将所有累积的 HAVING 条件通过 AND 组合并应用到查询。
     *
     * @param root 查询根实体
     * @param cb CriteriaBuilder 实例
     * @param query CriteriaQuery 实例
     */
    @SuppressWarnings("unchecked")
    private void applyHavingPredicates(Root<T> root, CriteriaBuilder cb, CriteriaQuery<?> query) {
        if (havingPredicateFns.isEmpty()) {
            return;
        }
        List<jakarta.persistence.criteria.Predicate> havingPredicates = new ArrayList<>();
        for (var fn : havingPredicateFns) {
            jakarta.persistence.criteria.Predicate p = fn.apply(root, cb);
            if (p != null) {
                havingPredicates.add(p);
            }
        }
        if (!havingPredicates.isEmpty()) {
            query.having(cb.and(havingPredicates.toArray(new jakarta.persistence.criteria.Predicate[0])));
        }
    }
}
