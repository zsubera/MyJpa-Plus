package com.zsubera.jpa.spec;

import com.zsubera.jpa.util.LambdaUtils;

import org.springframework.data.jpa.domain.Specification;

import jakarta.persistence.criteria.*;
import java.util.*;
import java.util.function.Consumer;

/**
 * A type-safe, lambda-based builder for JPA {@link Specification} queries.
 * <p>
 * Uses method references (e.g. {@code Entity::getField}) instead of hardcoded
 * property name strings. Supports equality, comparison, string matching,
 * collection operations, JOINs, EXISTS subqueries, and arbitrarily nested
 * AND/OR groups.
 * <p>
 * Example:
 * <pre>{@code
 * new QuerySpec<User>()
 *     .eq(User::getStatus, "ACTIVE")
 *     .or()
 *         .like(User::getName, "%John%")
 *         .like(User::getEmail, "%john%")
 *     .endOr()
 *     .toSpecification();
 * }</pre>
 *
 * @param <T> the root entity type being queried
 */
public class QuerySpec<T> implements Specification<T>, ConditionBuilder<T, QuerySpec<T>> {

    /** Default HTTP request parameter name for search keywords. */
    public static final String SEARCH_PARAM_NAME = "s";

    private final List<ConditionNode> conditions = new ArrayList<>();
    private final Deque<List<ConditionNode>> groupStack = new ArrayDeque<>();
    private boolean distinct = false;

    List<ConditionNode> currentGroup() {
        return groupStack.isEmpty() ? conditions : groupStack.peek();
    }

    @Override
    public List<ConditionNode> conditions() {
        return currentGroup();
    }

    /**
     * Creates a {@code QuerySpec} from pipe-delimited field names and a keyword,
     * producing a {@link #multiLike multiLike} condition across all fields.
     *
     * @param fieldNames pipe-delimited field names, e.g. {@code "name|email|phone"}
     * @param keyword    the search keyword (wrapped in {@code %keyword%})
     * @param <T>        the root entity type
     * @return a new QuerySpec with the multiLike condition, or empty if keyword is blank
     */
    public static <T> QuerySpec<T> fromSearchParam(String fieldNames, String keyword) {
        QuerySpec<T> qs = new QuerySpec<>();
        if (fieldNames != null && !fieldNames.isEmpty() && keyword != null && !keyword.isEmpty()) {
            qs.conditions.add(new MultiLikeNode(keyword, fieldNames.split("\\|")));
        }
        return qs;
    }

    /**
     * Enables {@code SELECT DISTINCT} for this query.
     *
     * @return this QuerySpec for chaining
     */
    public QuerySpec<T> distinct() {
        this.distinct = true;
        return this;
    }

    /**
     * Adds an INNER JOIN on the given relationship field. Use the returned
     * {@link JoinGroup} to add conditions on the joined entity, then call
     * {@link JoinGroup#endJoin()} to return to this QuerySpec.
     *
     * @param field a method reference to the relationship, e.g. {@code Order::getCustomer}
     * @param <J>   the joined entity type
     * @return a JoinGroup for building conditions on the joined entity
     */
    public <J> JoinGroup<T, J> join(SFunction<T, ?> field) {
        JoinNode joinNode = new JoinNode(LambdaUtils.getPropertyName(field), JoinType.INNER);
        currentGroup().add(joinNode);
        return new JoinGroup<>(this, joinNode);
    }

    /**
     * Adds a LEFT JOIN on the given relationship field.
     *
     * @param field a method reference to the relationship
     * @param <J>   the joined entity type
     * @return a JoinGroup for building conditions on the joined entity
     */
    public <J> JoinGroup<T, J> leftJoin(SFunction<T, ?> field) {
        JoinNode joinNode = new JoinNode(LambdaUtils.getPropertyName(field), JoinType.LEFT);
        currentGroup().add(joinNode);
        return new JoinGroup<>(this, joinNode);
    }

    /**
     * Adds an EXISTS subquery condition.
     *
     * @param subEntity the subquery entity class
     * @param config    a consumer to configure the {@link SubQuerySpec}
     * @param <S>       the subquery entity type
     * @return this QuerySpec for chaining
     */
    public <S> QuerySpec<T> exists(Class<S> subEntity, Consumer<SubQuerySpec<S>> config) {
        currentGroup().add(new ExistsNode<>(subEntity, config, false));
        return this;
    }

    /**
     * Adds a NOT EXISTS subquery condition.
     *
     * @param subEntity the subquery entity class
     * @param config    a consumer to configure the {@link SubQuerySpec}
     * @param <S>       the subquery entity type
     * @return this QuerySpec for chaining
     */
    public <S> QuerySpec<T> notExists(Class<S> subEntity, Consumer<SubQuerySpec<S>> config) {
        currentGroup().add(new ExistsNode<>(subEntity, config, true));
        return this;
    }

    /**
     * Opens an OR group. Subsequent conditions added via the returned
     * {@link OrGroup} will be joined with OR instead of AND.
     * Call {@link OrGroup#endOr()} to close the group.
     *
     * @return an OrGroup for building OR conditions
     */
    public OrGroup<T> or() {
        OrNode orNode = new OrNode();
        currentGroup().add(orNode);
        groupStack.push(orNode.nodes);
        return new OrGroup<>(this);
    }

    void endOr() {
        if (!groupStack.isEmpty()) {
            groupStack.pop();
        }
    }

    void pushGroupStack(List<ConditionNode> nodes) {
        groupStack.push(nodes);
    }

    /**
     * Returns this QuerySpec as a Spring Data {@link Specification}.
     *
     * @return this QuerySpec (it implements Specification directly)
     */
    public Specification<T> toSpecification() {
        return this;
    }

    /**
     * Combines this QuerySpec with an external {@link Specification} using AND.
     *
     * @param external an external Specification, may be null
     * @return the combined Specification
     */
    public Specification<T> toSpecification(Specification<T> external) {
        if (external == null) {
            return this;
        }
        return external.and(this);
    }

    @Override
    public Predicate toPredicate(Root<T> root, CriteriaQuery<?> query, CriteriaBuilder cb) {
        if (distinct) {
            query.distinct(true);
        }
        Map<String, Join<?, ?>> joinCache = new LinkedHashMap<>();
        List<Predicate> predicates = new ArrayList<>();
        for (ConditionNode node : conditions) {
            Predicate p = resolveNode(node, root, query, cb, joinCache, null);
            if (p != null) {
                predicates.add(p);
            }
        }
        return predicates.isEmpty() ? cb.conjunction() : cb.and(predicates.toArray(new Predicate[0]));
    }

    private Predicate resolveNode(ConditionNode node, Path<?> path, CriteriaQuery<?> query,
                                   CriteriaBuilder cb, Map<String, Join<?, ?>> joinCache, String pathPrefix) {
        if (node instanceof SimpleNode) {
            return resolveSimple((SimpleNode) node, path, cb);
        }
        if (node instanceof JoinNode) {
            return resolveJoin((JoinNode) node, path, query, cb, joinCache, pathPrefix);
        }
        if (node instanceof OrNode) {
            return resolveOr((OrNode) node, path, query, cb, joinCache, pathPrefix);
        }
        if (node instanceof MultiLikeNode) {
            return resolveMultiLike((MultiLikeNode) node, path, cb);
        }
        if (node instanceof CollectionNode) {
            return resolveCollection((CollectionNode) node, path, cb);
        }
        if (node instanceof ExistsNode) {
            return resolveExists((ExistsNode<?>) node, query, cb);
        }
        return null;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Predicate resolveSimple(SimpleNode node, Path<?> path, CriteriaBuilder cb) {
        Path<?> fieldPath = path.get(node.fieldName);
        switch (node.op) {
            case EQ:
                return cb.equal(fieldPath, node.value);
            case NE:
                return cb.notEqual(fieldPath, node.value);
            case GT:
                return cb.greaterThan((Expression<Comparable>) fieldPath, (Comparable) node.value);
            case GE:
                return cb.greaterThanOrEqualTo((Expression<Comparable>) fieldPath, (Comparable) node.value);
            case LT:
                return cb.lessThan((Expression<Comparable>) fieldPath, (Comparable) node.value);
            case LE:
                return cb.lessThanOrEqualTo((Expression<Comparable>) fieldPath, (Comparable) node.value);
            case LIKE:
                return cb.like(fieldPath.as(String.class), (String) node.value);
            case NOT_LIKE:
                return cb.notLike(fieldPath.as(String.class), (String) node.value);
            case IS_NULL:
                return cb.isNull(fieldPath);
            case IS_NOT_NULL:
                return cb.isNotNull(fieldPath);
            case IN: {
                CriteriaBuilder.In<Object> in = cb.in(fieldPath);
                for (Object v : (Object[]) node.value) {
                    in.value(v);
                }
                return in;
            }
            case NOT_IN: {
                CriteriaBuilder.In<Object> in = cb.in(fieldPath);
                for (Object v : (Object[]) node.value) {
                    in.value(v);
                }
                return cb.not(in);
            }
            case BETWEEN: {
                Comparable<?>[] range = (Comparable<?>[]) node.value;
                return cb.between((Expression<Comparable>) fieldPath,
                        (Comparable) range[0], (Comparable) range[1]);
            }
            default:
                return cb.conjunction();
        }
    }

    private Predicate resolveJoin(JoinNode node, Path<?> path, CriteriaQuery<?> query,
                                   CriteriaBuilder cb, Map<String, Join<?, ?>> joinCache, String pathPrefix) {
        String fullPath = (pathPrefix != null && !pathPrefix.isEmpty() ? pathPrefix + "." : "") + node.fieldName;

        Join<?, ?> join = joinCache.computeIfAbsent(fullPath, k -> {
            jakarta.persistence.criteria.JoinType jt =
                    node.joinType == JoinType.LEFT
                            ? jakarta.persistence.criteria.JoinType.LEFT
                            : jakarta.persistence.criteria.JoinType.INNER;
            return ((From<?, ?>) path).join(node.fieldName, jt);
        });

        List<Predicate> innerPredicates = new ArrayList<>();
        for (ConditionNode inner : node.innerConditions) {
            Predicate p = resolveNode(inner, join, query, cb, joinCache, fullPath);
            if (p != null) {
                innerPredicates.add(p);
            }
        }
        return innerPredicates.isEmpty()
                ? cb.conjunction()
                : cb.and(innerPredicates.toArray(new Predicate[0]));
    }

    private Predicate resolveOr(OrNode node, Path<?> path, CriteriaQuery<?> query,
                                 CriteriaBuilder cb, Map<String, Join<?, ?>> joinCache, String pathPrefix) {
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
        return cb.or(childPredicates.toArray(new Predicate[0]));
    }

    private Predicate resolveMultiLike(MultiLikeNode node, Path<?> path, CriteriaBuilder cb) {
        List<Predicate> likes = new ArrayList<>();
        String pattern = "%" + node.keyword + "%";
        for (String fieldName : node.fieldNames) {
            likes.add(cb.like(path.get(fieldName).as(String.class), pattern));
        }
        return cb.or(likes.toArray(new Predicate[0]));
    }

    @SuppressWarnings("unchecked")
    private Predicate resolveCollection(CollectionNode node, Path<?> path, CriteriaBuilder cb) {
        Path<?> fieldPath = path.get(node.fieldName);
        if (node.op == CollectionOp.IS_EMPTY) {
            return cb.isEmpty((Expression<Collection<?>>) (Expression<?>) fieldPath);
        }
        return cb.isNotEmpty((Expression<Collection<?>>) (Expression<?>) fieldPath);
    }

    @SuppressWarnings("unchecked")
    private <S> Predicate resolveExists(ExistsNode<S> node, CriteriaQuery<?> query, CriteriaBuilder cb) {
        jakarta.persistence.criteria.Subquery<S> subquery = query.subquery(node.subEntity);
        Root<S> subRoot = subquery.from(node.subEntity);
        SubQuerySpec<S> subSpec = new SubQuerySpec<>(subquery, subRoot, cb);
        node.config.accept(subSpec);
        subSpec.applyWhere();
        subquery.select(subRoot);
        return node.negate ? cb.not(cb.exists(subquery)) : cb.exists(subquery);
    }

    enum Op {EQ, NE, GT, GE, LT, LE, LIKE, NOT_LIKE, IN, NOT_IN, BETWEEN, IS_NULL, IS_NOT_NULL}

    enum JoinType {INNER, LEFT}

    enum CollectionOp {IS_EMPTY, IS_NOT_EMPTY}

    interface ConditionNode {
    }

    static class SimpleNode implements ConditionNode {
        final String fieldName;
        final Object value;
        final Op op;

        SimpleNode(String fieldName, Object value, Op op) {
            this.fieldName = fieldName;
            this.value = value;
            this.op = op;
        }
    }

    static class JoinNode implements ConditionNode {
        final String fieldName;
        final JoinType joinType;
        final List<ConditionNode> innerConditions = new ArrayList<>();

        JoinNode(String fieldName, JoinType joinType) {
            this.fieldName = fieldName;
            this.joinType = joinType;
        }
    }

    static class OrNode implements ConditionNode {
        final List<ConditionNode> nodes = new ArrayList<>();
    }

    static class MultiLikeNode implements ConditionNode {
        final String keyword;
        final String[] fieldNames;

        MultiLikeNode(String keyword, String[] fieldNames) {
            this.keyword = keyword;
            this.fieldNames = fieldNames;
        }
    }

    static class CollectionNode implements ConditionNode {
        final String fieldName;
        final CollectionOp op;

        CollectionNode(String fieldName, CollectionOp op) {
            this.fieldName = fieldName;
            this.op = op;
        }
    }

    static class ExistsNode<S> implements ConditionNode {
        final Class<S> subEntity;
        final Consumer<SubQuerySpec<S>> config;
        final boolean negate;

        ExistsNode(Class<S> subEntity, Consumer<SubQuerySpec<S>> config, boolean negate) {
            this.subEntity = subEntity;
            this.config = config;
            this.negate = negate;
        }
    }
}
