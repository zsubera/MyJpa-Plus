package com.zsubera.jpa.projection;

import com.zsubera.jpa.spec.ConditionNode;
import com.zsubera.jpa.spec.PredicateHelper;
import com.zsubera.jpa.spec.QuerySpec;
import com.zsubera.jpa.spec.SFunction;
import com.zsubera.jpa.util.InClauseBuilder;
import com.zsubera.jpa.util.LambdaUtils;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

/**
 * DTO 投影查询的类型安全构建器。
 *
 * <p>
 * 从实体中选择特定字段，并以 {@link Tuple} 或通过 {@code CriteriaBuilder.construct()} 返回自定义 DTO 的形式返回结果。支持 JOIN 关联、ORDER BY 排序和分页查询。
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

    private final Class<T> entityClass;
    private final Map<String, SFunction<T, ?>> selections = new LinkedHashMap<>();
    private final QuerySpec<T> querySpec = new QuerySpec<>();
    private final List<JoinSpec> joins = new ArrayList<>();
    private final List<OrderSpec> orderSpecs = new ArrayList<>();
    private Class<?> dtoClass;

    /** 描述 JOIN 子句的内部记录类。 */
    private static final class JoinSpec {
        final String fieldName;
        final Consumer<?> config;
        final boolean left;
        /** Cached conditions from the first Consumer invocation to avoid re-computation. */
        List<ConditionNode> cachedConditions;

        <E> JoinSpec(String fieldName, Consumer<JoinGroup<E>> config, boolean left) {
            this.fieldName = fieldName;
            this.config = config;
            this.left = left;
        }

        @SuppressWarnings("unchecked")
        List<ConditionNode> getConditions() {
            if (cachedConditions == null) {
                @SuppressWarnings("unchecked")
                Consumer<JoinGroup<Object>> cfg = (Consumer<JoinGroup<Object>>)(Consumer<?>)config;
                JoinGroup<Object> group = JoinGroup.create();
                cfg.accept(group);
                cachedConditions = group.getConditions();
            }
            return cachedConditions;
        }
    }

    /**
     * JOIN 目标实体的嵌套条件构建器。
     *
     * <p>
     * 提供类似于 {@link com.zsubera.jpa.spec.ConditionBuilder} 的 API， 用于在 JOIN 子句中添加 ON 条件。 条件节点复用
     * {@link com.zsubera.jpa.spec.ConditionNode} 体系，避免重复定义。
     *
     * @param <E> JOIN 目标实体类型
     */
    public static final class JoinGroup<E> {

        private final List<ConditionNode> conditions = new ArrayList<>();

        private JoinGroup() {}

        private static <E> JoinGroup<E> create() {
            return new JoinGroup<>();
        }

        public JoinGroup<E> eq(SFunction<E, ?> field, Object value) {
            if (field == null) {
                throw new IllegalArgumentException("field must not be null");
            }
            conditions
                .add(new ConditionNode.SimpleNode(LambdaUtils.getPropertyName(field), value, ConditionNode.Op.EQ));
            return this;
        }

        public JoinGroup<E> ne(SFunction<E, ?> field, Object value) {
            if (field == null) {
                throw new IllegalArgumentException("field must not be null");
            }
            conditions
                .add(new ConditionNode.SimpleNode(LambdaUtils.getPropertyName(field), value, ConditionNode.Op.NE));
            return this;
        }

        public JoinGroup<E> like(SFunction<E, ?> field, String value) {
            if (field == null) {
                throw new IllegalArgumentException("field must not be null");
            }
            if (value == null) {
                throw new IllegalArgumentException("value must not be null");
            }
            conditions
                .add(new ConditionNode.SimpleNode(LambdaUtils.getPropertyName(field), value, ConditionNode.Op.LIKE));
            return this;
        }

        public JoinGroup<E> notLike(SFunction<E, ?> field, String value) {
            if (field == null) {
                throw new IllegalArgumentException("field must not be null");
            }
            if (value == null) {
                throw new IllegalArgumentException("value must not be null");
            }
            conditions.add(
                new ConditionNode.SimpleNode(LambdaUtils.getPropertyName(field), value, ConditionNode.Op.NOT_LIKE));
            return this;
        }

        public JoinGroup<E> gt(SFunction<E, ?> field, Comparable<?> value) {
            if (field == null) {
                throw new IllegalArgumentException("field must not be null");
            }
            if (value == null) {
                throw new IllegalArgumentException("value must not be null");
            }
            conditions
                .add(new ConditionNode.SimpleNode(LambdaUtils.getPropertyName(field), value, ConditionNode.Op.GT));
            return this;
        }

        public JoinGroup<E> ge(SFunction<E, ?> field, Comparable<?> value) {
            if (field == null) {
                throw new IllegalArgumentException("field must not be null");
            }
            if (value == null) {
                throw new IllegalArgumentException("value must not be null");
            }
            conditions
                .add(new ConditionNode.SimpleNode(LambdaUtils.getPropertyName(field), value, ConditionNode.Op.GE));
            return this;
        }

        public JoinGroup<E> lt(SFunction<E, ?> field, Comparable<?> value) {
            if (field == null) {
                throw new IllegalArgumentException("field must not be null");
            }
            if (value == null) {
                throw new IllegalArgumentException("value must not be null");
            }
            conditions
                .add(new ConditionNode.SimpleNode(LambdaUtils.getPropertyName(field), value, ConditionNode.Op.LT));
            return this;
        }

        public JoinGroup<E> le(SFunction<E, ?> field, Comparable<?> value) {
            if (field == null) {
                throw new IllegalArgumentException("field must not be null");
            }
            if (value == null) {
                throw new IllegalArgumentException("value must not be null");
            }
            conditions
                .add(new ConditionNode.SimpleNode(LambdaUtils.getPropertyName(field), value, ConditionNode.Op.LE));
            return this;
        }

        public JoinGroup<E> isNull(SFunction<E, ?> field) {
            if (field == null) {
                throw new IllegalArgumentException("field must not be null");
            }
            conditions
                .add(new ConditionNode.SimpleNode(LambdaUtils.getPropertyName(field), null, ConditionNode.Op.IS_NULL));
            return this;
        }

        public JoinGroup<E> isNotNull(SFunction<E, ?> field) {
            if (field == null) {
                throw new IllegalArgumentException("field must not be null");
            }
            conditions.add(
                new ConditionNode.SimpleNode(LambdaUtils.getPropertyName(field), null, ConditionNode.Op.IS_NOT_NULL));
            return this;
        }

        public JoinGroup<E> likeSafe(SFunction<E, ?> field, String value) {
            if (field == null) {
                throw new IllegalArgumentException("field must not be null");
            }
            if (value == null) {
                throw new IllegalArgumentException("value must not be null");
            }
            conditions.add(new ConditionNode.SimpleNode(LambdaUtils.getPropertyName(field),
                PredicateHelper.escapeLikeWildcards(value), ConditionNode.Op.LIKE, PredicateHelper.LIKE_ESCAPE_CHAR));
            return this;
        }

        public JoinGroup<E> notLikeSafe(SFunction<E, ?> field, String value) {
            if (field == null) {
                throw new IllegalArgumentException("field must not be null");
            }
            if (value == null) {
                throw new IllegalArgumentException("value must not be null");
            }
            conditions.add(new ConditionNode.SimpleNode(LambdaUtils.getPropertyName(field),
                PredicateHelper.escapeLikeWildcards(value), ConditionNode.Op.NOT_LIKE,
                PredicateHelper.LIKE_ESCAPE_CHAR));
            return this;
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        public JoinGroup<E> between(SFunction<E, ?> field, Comparable<?> start, Comparable<?> end) {
            if (field == null) {
                throw new IllegalArgumentException("field must not be null");
            }
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
            conditions.add(new ConditionNode.SimpleNode(LambdaUtils.getPropertyName(field),
                new Comparable<?>[] {start, end}, ConditionNode.Op.BETWEEN));
            return this;
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        public JoinGroup<E> notBetween(SFunction<E, ?> field, Comparable<?> start, Comparable<?> end) {
            if (field == null) {
                throw new IllegalArgumentException("field must not be null");
            }
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
            conditions.add(new ConditionNode.SimpleNode(LambdaUtils.getPropertyName(field),
                new Comparable<?>[] {start, end}, ConditionNode.Op.NOT_BETWEEN));
            return this;
        }

        public JoinGroup<E> in(SFunction<E, ?> field, Object... values) {
            if (field == null) {
                throw new IllegalArgumentException("field must not be null");
            }
            if (values == null || values.length == 0) {
                throw new IllegalArgumentException("values must not be empty");
            }
            conditions
                .add(new ConditionNode.SimpleNode(LambdaUtils.getPropertyName(field), values, ConditionNode.Op.IN));
            return this;
        }

        public JoinGroup<E> notIn(SFunction<E, ?> field, Object... values) {
            if (field == null) {
                throw new IllegalArgumentException("field must not be null");
            }
            if (values == null || values.length == 0) {
                throw new IllegalArgumentException("values must not be empty");
            }
            conditions
                .add(new ConditionNode.SimpleNode(LambdaUtils.getPropertyName(field), values, ConditionNode.Op.NOT_IN));
            return this;
        }

        public JoinGroup<E> startsWith(SFunction<E, ?> field, String value) {
            if (field == null) {
                throw new IllegalArgumentException("field must not be null");
            }
            if (value == null) {
                throw new IllegalArgumentException("value must not be null");
            }
            conditions.add(new ConditionNode.SimpleNode(LambdaUtils.getPropertyName(field),
                PredicateHelper.escapeLikeWildcards(value) + "%", ConditionNode.Op.LIKE,
                PredicateHelper.LIKE_ESCAPE_CHAR));
            return this;
        }

        public JoinGroup<E> endsWith(SFunction<E, ?> field, String value) {
            if (field == null) {
                throw new IllegalArgumentException("field must not be null");
            }
            if (value == null) {
                throw new IllegalArgumentException("value must not be null");
            }
            conditions.add(new ConditionNode.SimpleNode(LambdaUtils.getPropertyName(field),
                "%" + PredicateHelper.escapeLikeWildcards(value), ConditionNode.Op.LIKE,
                PredicateHelper.LIKE_ESCAPE_CHAR));
            return this;
        }

        public JoinGroup<E> contains(SFunction<E, ?> field, String value) {
            if (field == null) {
                throw new IllegalArgumentException("field must not be null");
            }
            if (value == null) {
                throw new IllegalArgumentException("value must not be null");
            }
            conditions.add(new ConditionNode.SimpleNode(LambdaUtils.getPropertyName(field),
                "%" + PredicateHelper.escapeLikeWildcards(value) + "%", ConditionNode.Op.LIKE,
                PredicateHelper.LIKE_ESCAPE_CHAR));
            return this;
        }

        public JoinGroup<E> eqIgnoreCase(SFunction<E, ?> field, String value) {
            if (field == null) {
                throw new IllegalArgumentException("field must not be null");
            }
            if (value == null) {
                return isNull(field);
            }
            conditions.add(new ConditionNode.SimpleNode(LambdaUtils.getPropertyName(field), value,
                ConditionNode.Op.EQ_IGNORE_CASE));
            return this;
        }

        public JoinGroup<E> likeIgnoreCase(SFunction<E, ?> field, String value) {
            if (field == null) {
                throw new IllegalArgumentException("field must not be null");
            }
            if (value == null) {
                throw new IllegalArgumentException("value must not be null");
            }
            conditions.add(new ConditionNode.SimpleNode(LambdaUtils.getPropertyName(field), value,
                ConditionNode.Op.LIKE_IGNORE_CASE));
            return this;
        }

        public JoinGroup<E> isEmpty(SFunction<E, ?> field) {
            if (field == null) {
                throw new IllegalArgumentException("field must not be null");
            }
            conditions.add(new ConditionNode.CollectionNode(LambdaUtils.getPropertyName(field),
                ConditionNode.CollectionOp.IS_EMPTY));
            return this;
        }

        public JoinGroup<E> isNotEmpty(SFunction<E, ?> field) {
            if (field == null) {
                throw new IllegalArgumentException("field must not be null");
            }
            conditions.add(new ConditionNode.CollectionNode(LambdaUtils.getPropertyName(field),
                ConditionNode.CollectionOp.IS_NOT_EMPTY));
            return this;
        }

        /**
         * 获取所有条件节点列表。
         *
         * @return 条件节点列表
         */
        List<ConditionNode> getConditions() {
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
     * 指定用于构造函数投影的 DTO 类。
     *
     * <p>
     * DTO 必须有一个构造函数，其参数顺序和类型与选定的字段匹配。
     *
     * @param dtoClass DTO 类
     * @return 当前 ProjectionSpec 实例，支持链式调用
     */
    public ProjectionSpec<T> asDto(Class<?> dtoClass) {
        if (dtoClass == null) {
            throw new IllegalArgumentException("dtoClass must not be null");
        }
        this.dtoClass = dtoClass;
        return this;
    }

    /**
     * 添加 INNER JOIN 关联查询，可对关联实体设置条件。
     *
     * @param field 一对多或一对一关系的方法引用
     * @param config 用于配置 JOIN 条件的消费者函数
     * @param <E> JOIN 目标实体类型
     * @return 当前 ProjectionSpec 实例，支持链式调用
     */
    public <E> ProjectionSpec<T> join(SFunction<T, ?> field, Consumer<JoinGroup<E>> config) {
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
    public <E> ProjectionSpec<T> leftJoin(SFunction<T, ?> field, Consumer<JoinGroup<E>> config) {
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
     * 直接访问底层的 {@link QuerySpec} 以进行链式调用。
     *
     * @return 底层的 QuerySpec 实例
     */
    @SuppressFBWarnings("EI_EXPOSE_REP")
    public QuerySpec<T> conditions() {
        return querySpec;
    }

    /**
     * 构建并返回以 {@link Tuple} 为结果类型的类型安全查询。
     *
     * @param em JPA 实体管理器
     * @return 返回 Tuple 结果的 TypedQuery 实例
     */
    public TypedQuery<Tuple> toTupleQuery(EntityManager em) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Tuple> query = cb.createTupleQuery();
        Root<T> root = query.from(entityClass);

        // Apply joins (side effects on root)
        resolveJoins(root, cb);

        // Apply selections
        List<jakarta.persistence.criteria.Selection<?>> selectionList = new ArrayList<>();
        for (String alias : selections.keySet()) {
            selectionList.add(root.get(alias).alias(alias));
        }
        query.multiselect(selectionList);

        // Apply WHERE
        applyPredicate(root, query, cb);

        // Apply ORDER BY
        applyOrderBy(root, cb, query);

        return em.createQuery(query);
    }

    /**
     * 构建并返回以 DTO 为结果类型的类型安全查询。
     *
     * <p>
     * 必须先调用 {@link #asDto(Class)} 方法指定 DTO 类。
     *
     * @param em JPA 实体管理器
     * @param <R> DTO 结果类型
     * @return 返回 DTO 结果的 TypedQuery 实例
     * @throws IllegalStateException 如果未调用 {@link #asDto(Class)} 方法
     */
    @SuppressWarnings("unchecked")
    public <R> TypedQuery<R> toDtoQuery(EntityManager em) {
        if (dtoClass == null) {
            throw new IllegalStateException("asDto() must be called before toDtoQuery()");
        }
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<R> query = (CriteriaQuery<R>)cb.createQuery(dtoClass);
        Root<T> root = query.from(entityClass);

        // Apply joins
        resolveJoins(root, cb);

        // Apply selections as constructor arguments
        List<jakarta.persistence.criteria.Selection<?>> selectionList = new ArrayList<>();
        for (String fieldName : selections.keySet()) {
            selectionList.add(root.get(fieldName));
        }
        query.select((CompoundSelection<R>)cb.construct(dtoClass,
            selectionList.toArray(new jakarta.persistence.criteria.Selection[0])));

        // Apply WHERE
        applyPredicate(root, query, cb);

        // Apply ORDER BY
        applyOrderBy(root, cb, query);

        return em.createQuery(query);
    }

    /**
     * Paginate projection results.
     *
     * <p>
     * <strong>PERF-3 note:</strong> Join descriptors are extracted once and reused for both count and data queries to
     * avoid redundant JOIN resolution.
     *
     * <p>
     * <strong>PERF-4 note:</strong> Uses {@code countDistinct(root)} for accurate counting when JOINs may produce
     * duplicate rows. For one-to-many JOINs with complex primary keys, consider using subquery counting via
     * {@code QuerySpec} directly.
     *
     * @param em JPA entity manager
     * @param pageable pagination info
     * @return paginated projection results
     * @throws IllegalArgumentException if pagination offset is too large
     */
    public Page<Tuple> findPage(EntityManager em, Pageable pageable) {
        CriteriaBuilder cb = em.getCriteriaBuilder();

        // Handle unpaged
        if (pageable.isUnpaged()) {
            TypedQuery<Tuple> query = toTupleQuery(em);
            List<Tuple> allContent = query.getResultList();
            return new PageImpl<>(allContent);
        }

        // Count query - need to apply joins to get accurate count
        CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
        Root<T> countRoot = countQuery.from(entityClass);
        // Apply joins to count query to ensure accurate results
        resolveJoins(countRoot, cb);
        countQuery.select(cb.countDistinct(countRoot));
        applyPredicate(countRoot, countQuery, cb);
        Long total = em.createQuery(countQuery).getSingleResult();

        // Data query
        TypedQuery<Tuple> query = toTupleQuery(em);
        if (pageable.getOffset() > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Offset too large: " + pageable.getOffset());
        }
        query.setFirstResult((int)pageable.getOffset());
        query.setMaxResults(pageable.getPageSize());
        List<Tuple> content = query.getResultList();

        return new PageImpl<>(content, pageable, total);
    }

    /**
     * 解析并应用所有 JOIN 子句。
     *
     * <p>
     * Uses cached conditions from {@link JoinSpec#getConditions()} to avoid re-invoking the Consumer for each Root
     * (count vs data query).
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
                join.on(cb.and(onPredicates.toArray(new Predicate[0])));
            }
        }
        return joinMap;
    }

    /**
     * 解析单个 JOIN 条件节点为 Predicate。
     *
     * <p>
     * 支持 {@link ConditionNode.SimpleNode} 和 {@link ConditionNode.CollectionNode} 两种类型。 SimpleNode 通过 Op 枚举分派到对应的
     * CriteriaBuilder 操作。
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
            return switch (cn.op) {
                case IS_EMPTY -> cb.isEmpty((Expression<java.util.Collection<?>>)(Expression<?>)join.get(cn.fieldName));
                case IS_NOT_EMPTY ->
                    cb.isNotEmpty((Expression<java.util.Collection<?>>)(Expression<?>)join.get(cn.fieldName));
            };
        }
        throw new IllegalArgumentException("Unsupported ConditionNode type in JOIN clause: "
            + node.getClass().getSimpleName() + ". Only SimpleNode and CollectionNode are supported.");
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
        Path<?> fieldPath = join.get(node.fieldName);
        return switch (node.op) {
            case EQ -> node.value == null ? cb.isNull(fieldPath) : cb.equal(fieldPath, node.value);
            case NE -> node.value == null ? cb.isNotNull(fieldPath) : cb.notEqual(fieldPath, node.value);
            case GT -> cb.greaterThan((Expression<Comparable>)fieldPath, (Comparable)node.value);
            case GE -> cb.greaterThanOrEqualTo((Expression<Comparable>)fieldPath, (Comparable)node.value);
            case LT -> cb.lessThan((Expression<Comparable>)fieldPath, (Comparable)node.value);
            case LE -> cb.lessThanOrEqualTo((Expression<Comparable>)fieldPath, (Comparable)node.value);
            case LIKE ->
                node.escapeChar != '\0' ? cb.like(fieldPath.as(String.class), (String)node.value, node.escapeChar)
                    : cb.like(fieldPath.as(String.class), (String)node.value);
            case NOT_LIKE ->
                node.escapeChar != '\0' ? cb.notLike(fieldPath.as(String.class), (String)node.value, node.escapeChar)
                    : cb.notLike(fieldPath.as(String.class), (String)node.value);
            case IN -> {
                if (node.value instanceof Collection) {
                    yield InClauseBuilder.in(cb, fieldPath, (Collection<?>)node.value);
                }
                yield InClauseBuilder.in(cb, fieldPath, (Object[])node.value);
            }
            case NOT_IN -> {
                if (node.value instanceof Collection) {
                    yield InClauseBuilder.notIn(cb, fieldPath, (Collection<?>)node.value);
                }
                yield InClauseBuilder.notIn(cb, fieldPath, (Object[])node.value);
            }
            case BETWEEN -> {
                Comparable<?>[] range = (Comparable<?>[])node.value;
                yield cb.between((Expression<Comparable>)fieldPath, (Comparable)range[0], (Comparable)range[1]);
            }
            case NOT_BETWEEN -> {
                Comparable<?>[] range = (Comparable<?>[])node.value;
                yield cb.not(cb.between((Expression<Comparable>)fieldPath, (Comparable)range[0], (Comparable)range[1]));
            }
            case IS_NULL -> cb.isNull(fieldPath);
            case IS_NOT_NULL -> cb.isNotNull(fieldPath);
            case EQ_IGNORE_CASE ->
                cb.equal(cb.upper(fieldPath.as(String.class)), ((String)node.value).toUpperCase(java.util.Locale.ROOT));
            case LIKE_IGNORE_CASE ->
                cb.like(cb.upper(fieldPath.as(String.class)), ((String)node.value).toUpperCase(java.util.Locale.ROOT));
        };
    }

    /**
     * 应用 WHERE 查询条件。
     *
     * @param root 查询根实体
     * @param query CriteriaQuery 实例
     * @param cb CriteriaBuilder 实例
     */
    @SuppressWarnings({"rawtypes"})
    private void applyPredicate(Root<T> root, CriteriaQuery<?> query, CriteriaBuilder cb) {
        jakarta.persistence.criteria.Predicate predicate = querySpec.toPredicate(root, (CriteriaQuery)query, cb);
        if (predicate != null) {
            query.where(predicate);
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
}
