package com.zsubera.jpa.spec;

import com.zsubera.jpa.util.LambdaUtils;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.util.ArrayList;
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

    public SubQuerySpec<S> eq(SFunction<S, ?> field, Object value) {
        predicates.add(cb.equal(root.get(LambdaUtils.getPropertyName(field)), value));
        return this;
    }

    public SubQuerySpec<S> ne(SFunction<S, ?> field, Object value) {
        predicates.add(cb.notEqual(root.get(LambdaUtils.getPropertyName(field)), value));
        return this;
    }

    public SubQuerySpec<S> isNull(SFunction<S, ?> field) {
        predicates.add(cb.isNull(root.get(LambdaUtils.getPropertyName(field))));
        return this;
    }

    public SubQuerySpec<S> isNotNull(SFunction<S, ?> field) {
        predicates.add(cb.isNotNull(root.get(LambdaUtils.getPropertyName(field))));
        return this;
    }

    public SubQuerySpec<S> in(SFunction<S, ?> field, Object... values) {
        CriteriaBuilder.In<Object> in = cb.in(root.get(LambdaUtils.getPropertyName(field)));
        for (Object v : values) {
            in.value(v);
        }
        predicates.add(in);
        return this;
    }

    public <R> SubQuerySpec<S> where(java.util.function.Function<Root<S>, Predicate> condition) {
        predicates.add(condition.apply(root));
        return this;
    }

    public SubQuerySpec<S> select(SFunction<S, ?> field) {
        subquery.select(root.get(LambdaUtils.getPropertyName(field)));
        return this;
    }
}