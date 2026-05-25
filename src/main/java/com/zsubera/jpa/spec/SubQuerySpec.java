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

public class SubQuerySpec<S> {

    private final Subquery<S> subquery;
    private final Root<S> root;
    private final CriteriaBuilder cb;
    private final List<Predicate> predicates = new ArrayList<>();

    SubQuerySpec(Subquery<S> subquery, Root<S> root, CriteriaBuilder cb) {
        this.subquery = subquery;
        this.root = root;
        this.cb = cb;
    }

    void applyWhere() {
        if (!predicates.isEmpty()) {
            subquery.where(cb.and(predicates.toArray(new Predicate[0])));
        }
    }

    private String property(SFunction<S, ?> field) {
        if (field == null) {
            throw new IllegalArgumentException("field must not be null");
        }
        return LambdaUtils.getPropertyName(field);
    }

    public SubQuerySpec<S> eq(SFunction<S, ?> field, Object value) {
        String name = property(field);
        predicates.add(value == null ? cb.isNull(root.get(name)) : cb.equal(root.get(name), value));
        return this;
    }

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

    @SuppressWarnings({"unchecked", "rawtypes"})
    public SubQuerySpec<S> between(SFunction<S, ?> field, Comparable<?> start, Comparable<?> end) {
        predicates.add(cb.between((Expression) root.get(property(field)), (Comparable) start, (Comparable) end));
        return this;
    }

    public SubQuerySpec<S> isNull(SFunction<S, ?> field) {
        predicates.add(cb.isNull(root.get(property(field))));
        return this;
    }

    public SubQuerySpec<S> isNotNull(SFunction<S, ?> field) {
        predicates.add(cb.isNotNull(root.get(property(field))));
        return this;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public SubQuerySpec<S> in(SFunction<S, ?> field, Object... values) {
        CriteriaBuilder.In<Object> in = cb.in(root.get(property(field)));
        for (Object v : values) {
            in.value(v);
        }
        predicates.add(in);
        return this;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public SubQuerySpec<S> notIn(SFunction<S, ?> field, Object... values) {
        CriteriaBuilder.In<Object> in = cb.in(root.get(property(field)));
        for (Object v : values) {
            in.value(v);
        }
        predicates.add(cb.not(in));
        return this;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public SubQuerySpec<S> in(SFunction<S, ?> field, Collection<?> values) {
        CriteriaBuilder.In<Object> in = cb.in(root.get(property(field)));
        for (Object v : values) {
            in.value(v);
        }
        predicates.add(in);
        return this;
    }

    public SubQuerySpec<S> where(java.util.function.Function<Root<S>, Predicate> condition) {
        predicates.add(condition.apply(root));
        return this;
    }

    public SubQuerySpec<S> select(SFunction<S, ?> field) {
        subquery.select(root.get(LambdaUtils.getPropertyName(field)));
        return this;
    }
}
