package com.zsubera.jpa.update;

import com.zsubera.jpa.spec.PredicateHelper;
import com.zsubera.jpa.spec.SFunction;
import com.zsubera.jpa.update.AbstractBulkOperationSpec.BulkConditionNode;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

import java.util.Collection;
import java.util.List;
import java.util.function.BiFunction;

/**
 * Builder for OR groups within bulk operations ({@link UpdateSpec} and {@link DeleteSpec}).
 * <p>
 * All conditions added via this builder are combined with OR.
 * Created implicitly by {@link AbstractBulkOperationSpec#or(java.util.function.Consumer)}.
 *
 * @param <T>    the entity type
 * @param <SELF> the parent builder type
 */
public class OrConditionBuilder<T, SELF extends AbstractBulkOperationSpec<T, SELF>> {

    private final SELF parent;
    private final List<BulkConditionNode> nodes;

    OrConditionBuilder(SELF parent, List<BulkConditionNode> nodes) {
        this.parent = parent;
        this.nodes = nodes;
    }

    private BiFunction<Root<T>, CriteriaBuilder, Predicate> leaf(BiFunction<Root<T>, CriteriaBuilder, Predicate> fn) {
        return fn;
    }

    public OrConditionBuilder<T, SELF> eq(SFunction<T, ?> field, Object value) {
        String name = parent.property(field);
        nodes.add(new BulkConditionNode.LeafNode((root, cb) -> PredicateHelper.eq(root, name, value, cb)));
        return this;
    }

    public OrConditionBuilder<T, SELF> ne(SFunction<T, ?> field, Object value) {
        String name = parent.property(field);
        nodes.add(new BulkConditionNode.LeafNode((root, cb) -> PredicateHelper.ne(root, name, value, cb)));
        return this;
    }

    public OrConditionBuilder<T, SELF> gt(SFunction<T, ?> field, Comparable<?> value) {
        if (value == null) throw new IllegalArgumentException("value must not be null");
        String name = parent.property(field);
        nodes.add(new BulkConditionNode.LeafNode((root, cb) -> PredicateHelper.gt(root, name, value, cb)));
        return this;
    }

    public OrConditionBuilder<T, SELF> ge(SFunction<T, ?> field, Comparable<?> value) {
        if (value == null) throw new IllegalArgumentException("value must not be null");
        String name = parent.property(field);
        nodes.add(new BulkConditionNode.LeafNode((root, cb) -> PredicateHelper.ge(root, name, value, cb)));
        return this;
    }

    public OrConditionBuilder<T, SELF> lt(SFunction<T, ?> field, Comparable<?> value) {
        if (value == null) throw new IllegalArgumentException("value must not be null");
        String name = parent.property(field);
        nodes.add(new BulkConditionNode.LeafNode((root, cb) -> PredicateHelper.lt(root, name, value, cb)));
        return this;
    }

    public OrConditionBuilder<T, SELF> le(SFunction<T, ?> field, Comparable<?> value) {
        if (value == null) throw new IllegalArgumentException("value must not be null");
        String name = parent.property(field);
        nodes.add(new BulkConditionNode.LeafNode((root, cb) -> PredicateHelper.le(root, name, value, cb)));
        return this;
    }

    public OrConditionBuilder<T, SELF> like(SFunction<T, ?> field, String value) {
        if (value == null) throw new IllegalArgumentException("value must not be null");
        String name = parent.property(field);
        nodes.add(new BulkConditionNode.LeafNode((root, cb) -> PredicateHelper.like(root, name, value, cb)));
        return this;
    }

    public OrConditionBuilder<T, SELF> in(SFunction<T, ?> field, Object... values) {
        String name = parent.property(field);
        if (values == null || values.length == 0) throw new IllegalArgumentException("values must not be empty");
        nodes.add(new BulkConditionNode.LeafNode((root, cb) -> PredicateHelper.in(root, name, values, cb)));
        return this;
    }

    public OrConditionBuilder<T, SELF> notIn(SFunction<T, ?> field, Object... values) {
        String name = parent.property(field);
        if (values == null || values.length == 0) throw new IllegalArgumentException("values must not be empty");
        nodes.add(new BulkConditionNode.LeafNode((root, cb) -> PredicateHelper.notIn(root, name, values, cb)));
        return this;
    }

    public OrConditionBuilder<T, SELF> isNull(SFunction<T, ?> field) {
        String name = parent.property(field);
        nodes.add(new BulkConditionNode.LeafNode((root, cb) -> PredicateHelper.isNull(root, name, cb)));
        return this;
    }

    public OrConditionBuilder<T, SELF> isNotNull(SFunction<T, ?> field) {
        String name = parent.property(field);
        nodes.add(new BulkConditionNode.LeafNode((root, cb) -> PredicateHelper.isNotNull(root, name, cb)));
        return this;
    }

    public OrConditionBuilder<T, SELF> between(SFunction<T, ?> field, Comparable<?> start, Comparable<?> end) {
        if (start == null) throw new IllegalArgumentException("start must not be null");
        if (end == null) throw new IllegalArgumentException("end must not be null");
        String name = parent.property(field);
        nodes.add(new BulkConditionNode.LeafNode((root, cb) -> PredicateHelper.between(root, name, start, end, cb)));
        return this;
    }
}
