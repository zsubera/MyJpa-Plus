package com.zsubera.jpa.projection;

import com.zsubera.jpa.spec.QuerySpec;
import com.zsubera.jpa.spec.SFunction;
import com.zsubera.jpa.util.LambdaUtils;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Type-safe builder for DTO projection queries.
 * <p>
 * Selects specific fields from an entity and returns them as {@link Tuple}
 * or as a custom DTO via {@code CriteriaBuilder.construct()}.
 * Supports JOIN associations, ORDER BY, and pagination.
 * <p>
 * Example:
 * <pre>{@code
 * List<Tuple> results = new ProjectionSpec<>(User.class)
 *     .select(User::getName)
 *     .select(User::getEmail)
 *     .join(User::getDepartment, j -> j.eq(Department::getName, "Engineering"))
 *     .orderByAsc(User::getName)
 *     .where(q -> q.eq(User::getStatus, "ACTIVE"))
 *     .toTupleQuery(entityManager)
 *     .getResultList();
 * }</pre>
 *
 * @param <T> the root entity type
 */
public class ProjectionSpec<T> {

    private final Class<T> entityClass;
    private final Map<String, SFunction<T, ?>> selections = new LinkedHashMap<>();
    private final QuerySpec<T> querySpec = new QuerySpec<>();
    private final List<JoinSpec> joins = new ArrayList<>();
    private final List<OrderSpec> orderSpecs = new ArrayList<>();
    private Class<?> dtoClass;

    /**
     * Internal record describing a JOIN clause.
     */
    private static final class JoinSpec {
        final String fieldName;
        final Consumer<?> config;
        final boolean left;

        <E> JoinSpec(String fieldName, Consumer<JoinGroup<E>> config, boolean left) {
            this.fieldName = fieldName;
            this.config = config;
            this.left = left;
        }
    }

    /**
     * Builder for nested conditions on a JOIN target. Mirrors the
     * {@link com.zsubera.jpa.spec.ConditionBuilder} API.
     */
    public static final class JoinGroup<E> {

        private final List<ConditionNode> conditions = new ArrayList<>();

        private JoinGroup() {
        }

        private static <E> JoinGroup<E> create() {
            return new JoinGroup<>();
        }

        /** @see com.zsubera.jpa.spec.ConditionBuilder#eq(SFunction, Object) */
        public JoinGroup<E> eq(SFunction<E, ?> field, Object value) {
            conditions.add(new ConditionNode.Eq(LambdaUtils.getPropertyName(field), value));
            return this;
        }

        /**
         * Internal condition node for projection join conditions.
         */
        sealed interface ConditionNode {
            record Eq(String fieldName, Object value) implements ConditionNode {}
        }

        List<ConditionNode> getConditions() {
            return conditions;
        }
    }

    /**
     * Internal record describing an ORDER BY clause.
     */
    private record OrderSpec(String fieldName, boolean asc) {
    }

    public ProjectionSpec(Class<T> entityClass) {
        this.entityClass = entityClass;
    }

    /**
     * Adds a field to the SELECT clause.
     */
    public ProjectionSpec<T> select(SFunction<T, ?> field) {
        selections.put(LambdaUtils.getPropertyName(field), field);
        return this;
    }

    /**
     * Specifies a DTO class for constructor-based projection.
     * The DTO must have a constructor whose parameters match the order
     * and types of the selected fields.
     */
    public ProjectionSpec<T> asDto(Class<?> dtoClass) {
        this.dtoClass = dtoClass;
        return this;
    }

    /**
     * Adds an INNER JOIN on the given relationship field with optional
     * conditions on the joined entity.
     *
     * @param field  a method reference to a to-one relationship
     * @param config consumer to add conditions on the joined entity
     * @param <E>    the joined entity type
     * @return this ProjectionSpec for chaining
     */
    public <E> ProjectionSpec<T> join(SFunction<T, ?> field, Consumer<JoinGroup<E>> config) {
        joins.add(new JoinSpec(LambdaUtils.getPropertyName(field), config, false));
        return this;
    }

    /**
     * Adds a LEFT JOIN on the given relationship field with optional
     * conditions on the joined entity.
     *
     * @param field  a method reference to a to-one relationship
     * @param config consumer to add conditions on the joined entity
     * @param <E>    the joined entity type
     * @return this ProjectionSpec for chaining
     */
    public <E> ProjectionSpec<T> leftJoin(SFunction<T, ?> field, Consumer<JoinGroup<E>> config) {
        joins.add(new JoinSpec(LambdaUtils.getPropertyName(field), config, true));
        return this;
    }

    /**
     * Adds ascending ORDER BY on the given field.
     *
     * @param field a method reference to the entity property
     * @return this ProjectionSpec for chaining
     */
    public ProjectionSpec<T> orderByAsc(SFunction<T, ?> field) {
        orderSpecs.add(new OrderSpec(LambdaUtils.getPropertyName(field), true));
        return this;
    }

    /**
     * Adds descending ORDER BY on the given field.
     *
     * @param field a method reference to the entity property
     * @return this ProjectionSpec for chaining
     */
    public ProjectionSpec<T> orderByDesc(SFunction<T, ?> field) {
        orderSpecs.add(new OrderSpec(LambdaUtils.getPropertyName(field), false));
        return this;
    }

    /**
     * Adds WHERE conditions to the projection query.
     */
    public ProjectionSpec<T> where(Consumer<QuerySpec<T>> config) {
        config.accept(querySpec);
        return this;
    }

    /**
     * Access the underlying {@link QuerySpec} directly for chaining.
     */
    @SuppressFBWarnings("EI_EXPOSE_REP")
    public QuerySpec<T> conditions() {
        return querySpec;
    }

    /**
     * Builds and returns a {@link TypedQuery} with {@link Tuple} results.
     */
    public TypedQuery<Tuple> toTupleQuery(EntityManager em) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Tuple> query = cb.createTupleQuery();
        Root<T> root = query.from(entityClass);

        // Apply joins
        Map<String, Join<?, ?>> joinMap = resolveJoins(root, cb);

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
     * Builds and returns a {@link TypedQuery} with DTO results.
     * Requires {@link #asDto(Class)} to be called first.
     */
    @SuppressWarnings("unchecked")
    public <R> TypedQuery<R> toDtoQuery(EntityManager em) {
        if (dtoClass == null) {
            throw new IllegalStateException("asDto() must be called before toDtoQuery()");
        }
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<R> query = (CriteriaQuery<R>) cb.createQuery(dtoClass);
        Root<T> root = query.from(entityClass);

        // Apply joins
        resolveJoins(root, cb);

        // Apply selections as constructor arguments
        List<jakarta.persistence.criteria.Selection<?>> selectionList = new ArrayList<>();
        for (String fieldName : selections.keySet()) {
            selectionList.add(root.get(fieldName));
        }
        query.select((CompoundSelection<R>) cb.construct(dtoClass,
                selectionList.toArray(new jakarta.persistence.criteria.Selection[0])));

        // Apply WHERE
        applyPredicate(root, query, cb);

        // Apply ORDER BY
        applyOrderBy(root, cb, query);

        return em.createQuery(query);
    }

    /**
     * Finds a page of projection results.
     *
     * @param em       the EntityManager
     * @param pageable the pagination information
     * @return a page of projection results
     */
    public Page<Tuple> findPage(EntityManager em, Pageable pageable) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        // Count query
        CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
        Root<T> countRoot = countQuery.from(entityClass);
        countQuery.select(cb.count(countRoot));
        applyPredicate(countRoot, countQuery, cb);
        Long total = em.createQuery(countQuery).getSingleResult();

        // Data query
        TypedQuery<Tuple> query = toTupleQuery(em);
        query.setFirstResult((int) pageable.getOffset());
        query.setMaxResults(pageable.getPageSize());
        List<Tuple> content = query.getResultList();

        return new PageImpl<>(content, pageable, total);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Map<String, Join<?, ?>> resolveJoins(Root<T> root, CriteriaBuilder cb) {
        Map<String, Join<?, ?>> joinMap = new LinkedHashMap<>();
        for (JoinSpec js : joins) {
            Join<?, ?> join = joinMap.computeIfAbsent(js.fieldName, k ->
                    js.left
                            ? root.join(js.fieldName, jakarta.persistence.criteria.JoinType.LEFT)
                            : root.join(js.fieldName)
            );
            @SuppressWarnings("unchecked")
            Consumer<JoinGroup<Object>> cfg = (Consumer<JoinGroup<Object>>) js.config;
            JoinGroup<Object> group = JoinGroup.create();
            cfg.accept(group);
            for (JoinGroup.ConditionNode node : group.getConditions()) {
                if (node instanceof JoinGroup.ConditionNode.Eq eq) {
                    join.on(cb.equal(join.get(eq.fieldName()), eq.value()));
                }
            }
        }
        return joinMap;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void applyPredicate(Root<T> root, CriteriaQuery<?> query, CriteriaBuilder cb) {
        jakarta.persistence.criteria.Predicate predicate = querySpec.toPredicate(root,
                (CriteriaQuery) query, cb);
        if (predicate != null) {
            query.where(predicate);
        }
    }

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
