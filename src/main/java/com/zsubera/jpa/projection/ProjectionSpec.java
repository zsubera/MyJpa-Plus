package com.zsubera.jpa.projection;

import com.zsubera.jpa.spec.QuerySpec;
import com.zsubera.jpa.spec.SFunction;
import com.zsubera.jpa.util.LambdaUtils;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.CompoundSelection;
import jakarta.persistence.criteria.Root;

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
 * <p>
 * Example:
 * <pre>{@code
 * List<Tuple> results = new ProjectionSpec<>(User.class)
 *     .select(User::getName)
 *     .select(User::getEmail)
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
    private Class<?> dtoClass;

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

        List<jakarta.persistence.criteria.Selection<?>> selectionList = new ArrayList<>();
        for (String alias : selections.keySet()) {
            selectionList.add(root.get(alias).alias(alias));
        }
        query.multiselect(selectionList);

        applyPredicate(root, query, cb);
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

        List<jakarta.persistence.criteria.Selection<?>> selectionList = new ArrayList<>();
        for (String fieldName : selections.keySet()) {
            selectionList.add(root.get(fieldName));
        }
        query.select((CompoundSelection<R>) cb.construct(dtoClass,
                selectionList.toArray(new jakarta.persistence.criteria.Selection[0])));

        applyPredicate(root, query, cb);
        return em.createQuery(query);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void applyPredicate(Root<T> root, CriteriaQuery<?> query, CriteriaBuilder cb) {
        jakarta.persistence.criteria.Predicate predicate = querySpec.toPredicate(root,
                (CriteriaQuery) query, cb);
        if (predicate != null) {
            query.where(predicate);
        }
    }
}
