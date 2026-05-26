package com.zsubera.jpa.spec;

import com.zsubera.jpa.util.LambdaUtils;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Type-safe builder for JPA EXISTS / NOT EXISTS subqueries.
 * <p>
 * Provides condition methods for the subquery entity and access to the
 * correlated outer query root for building correlation predicates.
 * <p>
 * Usage via {@link QuerySpec#exists(Class, java.util.function.Consumer)}
 * or {@link QuerySpec#notExists(Class, java.util.function.Consumer)}.
 *
 * @param <S> the subquery entity type
 */
public class SubQuerySpec<S> {

    private final Subquery<S> subquery;
    private final Root<S> root;
    private final CriteriaBuilder cb;
    private final Root<?> correlatedRoot;
    private final List<Predicate> predicates = new ArrayList<>();
    private boolean selectSet;

    SubQuerySpec(Subquery<S> subquery, Root<S> root, Root<?> correlatedRoot, CriteriaBuilder cb) {
        this.subquery = subquery;
        this.root = root;
        this.correlatedRoot = correlatedRoot;
        this.cb = cb;
    }

    void applyWhere() {
        if (!predicates.isEmpty()) {
            subquery.where(cb.and(predicates.toArray(new Predicate[0])));
        }
    }

    boolean isSelectSet() {
        return selectSet;
    }

    /**
     * Returns the correlated outer query root for building correlation predicates.
     * Use inside {@link #where(java.util.function.Function)} to reference the outer entity.
     *
     * @param <T> the outer entity type
     * @return the outer query {@link Root}
     */
    @SuppressWarnings("unchecked")
    public <T> Root<T> correlated() {
        return (Root<T>) correlatedRoot;
    }

    private String property(SFunction<S, ?> field) {
        if (field == null) {
            throw new IllegalArgumentException("field must not be null");
        }
        return LambdaUtils.getPropertyName(field);
    }

    // ---- Comparison operators ----

    /** Adds an equality condition on the subquery entity. */
    public SubQuerySpec<S> eq(SFunction<S, ?> field, Object value) {
        String name = property(field);
        predicates.add(value == null ? cb.isNull(root.get(name)) : cb.equal(root.get(name), value));
        return this;
    }

    /** Adds a not-equal condition on the subquery entity. */
    public SubQuerySpec<S> ne(SFunction<S, ?> field, Object value) {
        String name = property(field);
        predicates.add(value == null ? cb.isNotNull(root.get(name)) : cb.notEqual(root.get(name), value));
        return this;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public SubQuerySpec<S> gt(SFunction<S, ?> field, Comparable<?> value) {
        predicates.add(cb.greaterThan((Expression) root.get(property(field)), (Comparable) value));
        return this;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public SubQuerySpec<S> ge(SFunction<S, ?> field, Comparable<?> value) {
        predicates.add(cb.greaterThanOrEqualTo((Expression) root.get(property(field)), (Comparable) value));
        return this;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public SubQuerySpec<S> lt(SFunction<S, ?> field, Comparable<?> value) {
        predicates.add(cb.lessThan((Expression) root.get(property(field)), (Comparable) value));
        return this;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public SubQuerySpec<S> le(SFunction<S, ?> field, Comparable<?> value) {
        predicates.add(cb.lessThanOrEqualTo((Expression) root.get(property(field)), (Comparable) value));
        return this;
    }

    // ---- String operators ----

    public SubQuerySpec<S> like(SFunction<S, ?> field, String value) {
        predicates.add(cb.like(root.get(property(field)).as(String.class), value));
        return this;
    }

    public SubQuerySpec<S> notLike(SFunction<S, ?> field, String value) {
        predicates.add(cb.notLike(root.get(property(field)).as(String.class), value));
        return this;
    }

    public SubQuerySpec<S> startsWith(SFunction<S, ?> field, String value) {
        predicates.add(cb.like(root.get(property(field)).as(String.class), value + "%"));
        return this;
    }

    public SubQuerySpec<S> endsWith(SFunction<S, ?> field, String value) {
        predicates.add(cb.like(root.get(property(field)).as(String.class), "%" + value));
        return this;
    }

    public SubQuerySpec<S> contains(SFunction<S, ?> field, String value) {
        predicates.add(cb.like(root.get(property(field)).as(String.class), "%" + value + "%"));
        return this;
    }

    // ---- Collection operators ----

    @SuppressWarnings({"unchecked", "rawtypes"})
    public SubQuerySpec<S> between(SFunction<S, ?> field, Comparable<?> start, Comparable<?> end) {
        predicates.add(cb.between((Expression) root.get(property(field)), (Comparable) start, (Comparable) end));
        return this;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public SubQuerySpec<S> in(SFunction<S, ?> field, Object... values) {
        String name = property(field);
        if (values == null || values.length == 0) {
            throw new IllegalArgumentException("values must not be empty");
        }
        CriteriaBuilder.In<Object> in = cb.in(root.get(name));
        for (Object v : values) {
            in.value(v);
        }
        predicates.add(in);
        return this;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public SubQuerySpec<S> notIn(SFunction<S, ?> field, Object... values) {
        String name = property(field);
        if (values == null || values.length == 0) {
            throw new IllegalArgumentException("values must not be empty");
        }
        CriteriaBuilder.In<Object> in = cb.in(root.get(name));
        for (Object v : values) {
            in.value(v);
        }
        predicates.add(cb.not(in));
        return this;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public SubQuerySpec<S> in(SFunction<S, ?> field, Collection<?> values) {
        String name = property(field);
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException("values must not be empty");
        }
        CriteriaBuilder.In<Object> in = cb.in(root.get(name));
        for (Object v : values) {
            in.value(v);
        }
        predicates.add(in);
        return this;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public SubQuerySpec<S> notIn(SFunction<S, ?> field, Collection<?> values) {
        String name = property(field);
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException("values must not be empty");
        }
        CriteriaBuilder.In<Object> in = cb.in(root.get(name));
        for (Object v : values) {
            in.value(v);
        }
        predicates.add(cb.not(in));
        return this;
    }

    // ---- Null operators ----

    public SubQuerySpec<S> isNull(SFunction<S, ?> field) {
        predicates.add(cb.isNull(root.get(property(field))));
        return this;
    }

    public SubQuerySpec<S> isNotNull(SFunction<S, ?> field) {
        predicates.add(cb.isNotNull(root.get(property(field))));
        return this;
    }

    // ---- Collection empty checks ----

    @SuppressWarnings("unchecked")
    public SubQuerySpec<S> isEmpty(SFunction<S, ?> field) {
        predicates.add(cb.isEmpty((Expression<java.util.Collection<?>>) (Expression<?>) root.get(property(field))));
        return this;
    }

    @SuppressWarnings("unchecked")
    public SubQuerySpec<S> isNotEmpty(SFunction<S, ?> field) {
        predicates.add(cb.isNotEmpty((Expression<java.util.Collection<?>>) (Expression<?>) root.get(property(field))));
        return this;
    }

    // ---- Multi-field search ----

    @SuppressWarnings("unchecked")
    public SubQuerySpec<S> multiLike(String keyword, SFunction<S, ?>... fields) {
        if (keyword != null && !keyword.isEmpty() && fields.length > 0) {
            String pattern = "%" + keyword + "%";
            List<Predicate> likes = new ArrayList<>();
            for (SFunction<S, ?> field : fields) {
                likes.add(cb.like(root.get(property(field)).as(String.class), pattern));
            }
            predicates.add(likes.size() == 1 ? likes.get(0) : cb.or(likes.toArray(new Predicate[0])));
        }
        return this;
    }

    /**
     * Adds a raw predicate using the subquery root and criteria builder.
     * Use this as an escape hatch for complex conditions or correlation predicates.
     * To reference the outer query root, use {@link #correlated()}.
     *
     * <pre>{@code
     * qs.exists(Child.class, sub -> sub
     *     .where(r -> cb.and(
     *         cb.equal(r.get("parent"), sub.correlated()),
     *         cb.greaterThan(r.get("amount"), 0)
     *     ))
     * );
     * }</pre>
     */
    public SubQuerySpec<S> where(java.util.function.Function<Root<S>, Predicate> condition) {
        predicates.add(condition.apply(root));
        return this;
    }

    /**
     * Sets the SELECT clause for this subquery.
     * If not called, the subquery selects the subquery root by default.
     */
    public SubQuerySpec<S> select(SFunction<S, ?> field) {
        subquery.select(root.get(LambdaUtils.getPropertyName(field)));
        selectSet = true;
        return this;
    }
}
