package com.zsubera.jpa.spec;

import com.zsubera.jpa.util.LambdaUtils;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;

import java.util.Collection;
import java.util.List;
import java.util.function.BiFunction;

/**
 * Common interface for building type-safe JPA query conditions using
 * lambda method references.
 * <p>
 * Implementations provide the target condition list via {@link #conditions()}.
 * All condition methods are {@code default} methods that create
 * {@link QuerySpec.ConditionNode} entries and append them to that list.
 * <p>
 * The self-type parameter {@code SELF} enables fluent chaining where each
 * method returns the concrete builder type rather than the interface.
 * <p>
 * Implementors: {@link QuerySpec}, {@link JoinGroup}, {@link OrGroup}, {@link OrJoinGroup}.
 *
 * @param <E>    the entity type on which conditions operate
 * @param <SELF> the concrete builder type for fluent chaining
 */
public interface ConditionBuilder<E, SELF extends ConditionBuilder<E, SELF>> {

    /**
     * Returns the mutable list to which condition nodes are appended.
     *
     * @return the active condition node list for the current builder context
     */
    List<QuerySpec.ConditionNode> conditions();

    /**
     * Casts {@code this} to the concrete builder type for method chaining.
     *
     * @return {@code this} as type {@code SELF}
     */
    @SuppressWarnings("unchecked")
    default SELF self() {
        return (SELF) this;
    }

    // ---- Comparison operators ----

    /**
     * Adds an equality condition: {@code field = value}.
     *
     * @param field a method reference to the entity property
     * @param value the value to compare against
     * @return this builder for chaining
     * @throws IllegalArgumentException if {@code field} is null
     */
    default SELF eq(SFunction<E, ?> field, Object value) {
        if (field == null) {
            throw new IllegalArgumentException("field must not be null");
        }
        if (value == null) {
            conditions().add(new QuerySpec.SimpleNode(LambdaUtils.getPropertyName(field), null, QuerySpec.Op.IS_NULL));
        } else {
            conditions().add(new QuerySpec.SimpleNode(LambdaUtils.getPropertyName(field), value, QuerySpec.Op.EQ));
        }
        return self();
    }

    /**
     * Adds a not-equal condition: {@code field != value}.
     * If {@code value} is null, generates {@code field IS NOT NULL}.
     */
    default SELF ne(SFunction<E, ?> field, Object value) {
        if (field == null) {
            throw new IllegalArgumentException("field must not be null");
        }
        if (value == null) {
            conditions().add(new QuerySpec.SimpleNode(LambdaUtils.getPropertyName(field), null, QuerySpec.Op.IS_NOT_NULL));
        } else {
            conditions().add(new QuerySpec.SimpleNode(LambdaUtils.getPropertyName(field), value, QuerySpec.Op.NE));
        }
        return self();
    }

    /**
     * Adds a greater-than condition: {@code field > value}.
     */
    default SELF gt(SFunction<E, ?> field, Comparable<?> value) {
        if (field == null) {
            throw new IllegalArgumentException("field must not be null");
        }
        conditions().add(new QuerySpec.SimpleNode(LambdaUtils.getPropertyName(field), value, QuerySpec.Op.GT));
        return self();
    }

    /**
     * Adds a greater-than-or-equal condition: {@code field >= value}.
     */
    default SELF ge(SFunction<E, ?> field, Comparable<?> value) {
        if (field == null) {
            throw new IllegalArgumentException("field must not be null");
        }
        conditions().add(new QuerySpec.SimpleNode(LambdaUtils.getPropertyName(field), value, QuerySpec.Op.GE));
        return self();
    }

    /**
     * Adds a less-than condition: {@code field < value}.
     */
    default SELF lt(SFunction<E, ?> field, Comparable<?> value) {
        if (field == null) {
            throw new IllegalArgumentException("field must not be null");
        }
        conditions().add(new QuerySpec.SimpleNode(LambdaUtils.getPropertyName(field), value, QuerySpec.Op.LT));
        return self();
    }

    /**
     * Adds a less-than-or-equal condition: {@code field <= value}.
     */
    default SELF le(SFunction<E, ?> field, Comparable<?> value) {
        if (field == null) {
            throw new IllegalArgumentException("field must not be null");
        }
        conditions().add(new QuerySpec.SimpleNode(LambdaUtils.getPropertyName(field), value, QuerySpec.Op.LE));
        return self();
    }

    // ---- String operators ----

    /**
     * Adds a LIKE condition: {@code field LIKE value}.
     * The caller is responsible for including wildcards (e.g. {@code "%keyword%"}).
     */
    default SELF like(SFunction<E, ?> field, String value) {
        if (field == null) {
            throw new IllegalArgumentException("field must not be null");
        }
        conditions().add(new QuerySpec.SimpleNode(LambdaUtils.getPropertyName(field), value, QuerySpec.Op.LIKE));
        return self();
    }

    /**
     * Adds a NOT LIKE condition: {@code field NOT LIKE value}.
     */
    default SELF notLike(SFunction<E, ?> field, String value) {
        if (field == null) {
            throw new IllegalArgumentException("field must not be null");
        }
        conditions().add(new QuerySpec.SimpleNode(LambdaUtils.getPropertyName(field), value, QuerySpec.Op.NOT_LIKE));
        return self();
    }

    /**
     * Adds a LIKE condition for prefix matching: {@code field LIKE 'value%'}.
     */
    default SELF startsWith(SFunction<E, ?> field, String value) {
        if (field == null) {
            throw new IllegalArgumentException("field must not be null");
        }
        conditions().add(new QuerySpec.SimpleNode(LambdaUtils.getPropertyName(field), value + "%", QuerySpec.Op.LIKE));
        return self();
    }

    /**
     * Adds a LIKE condition for suffix matching: {@code field LIKE '%value'}.
     */
    default SELF endsWith(SFunction<E, ?> field, String value) {
        if (field == null) {
            throw new IllegalArgumentException("field must not be null");
        }
        conditions().add(new QuerySpec.SimpleNode(LambdaUtils.getPropertyName(field), "%" + value, QuerySpec.Op.LIKE));
        return self();
    }

    /**
     * Adds a LIKE condition for substring matching: {@code field LIKE '%value%'}.
     */
    default SELF contains(SFunction<E, ?> field, String value) {
        if (field == null) {
            throw new IllegalArgumentException("field must not be null");
        }
        conditions().add(new QuerySpec.SimpleNode(LambdaUtils.getPropertyName(field), "%" + value + "%", QuerySpec.Op.LIKE));
        return self();
    }

    // ---- Collection operators ----

    /**
     * Adds an IN condition: {@code field IN (values)}.
     *
     * @param field  a method reference to the entity property
     * @param values the set of values
     * @return this builder for chaining
     */
    default SELF in(SFunction<E, ?> field, Object... values) {
        if (field == null) {
            throw new IllegalArgumentException("field must not be null");
        }
        conditions().add(new QuerySpec.SimpleNode(LambdaUtils.getPropertyName(field), values, QuerySpec.Op.IN));
        return self();
    }

    /**
     * Adds a NOT IN condition: {@code field NOT IN (values)}.
     */
    default SELF notIn(SFunction<E, ?> field, Object... values) {
        if (field == null) {
            throw new IllegalArgumentException("field must not be null");
        }
        conditions().add(new QuerySpec.SimpleNode(LambdaUtils.getPropertyName(field), values, QuerySpec.Op.NOT_IN));
        return self();
    }

    /**
     * Adds an IN condition with a {@link Collection} of values:
     * {@code field IN (values)}.
     *
     * @param field  a method reference to the entity property
     * @param values the collection of values
     * @return this builder for chaining
     */
    default SELF in(SFunction<E, ?> field, Collection<?> values) {
        if (field == null) {
            throw new IllegalArgumentException("field must not be null");
        }
        conditions().add(new QuerySpec.SimpleNode(LambdaUtils.getPropertyName(field), values, QuerySpec.Op.IN));
        return self();
    }

    /**
     * Adds a NOT IN condition with a {@link Collection} of values:
     * {@code field NOT IN (values)}.
     */
    default SELF notIn(SFunction<E, ?> field, Collection<?> values) {
        if (field == null) {
            throw new IllegalArgumentException("field must not be null");
        }
        conditions().add(new QuerySpec.SimpleNode(LambdaUtils.getPropertyName(field), values, QuerySpec.Op.NOT_IN));
        return self();
    }

    /**
     * Adds a BETWEEN condition: {@code field BETWEEN start AND end}.
     *
     * @param field a method reference to the entity property
     * @param start the lower bound (inclusive)
     * @param end   the upper bound (inclusive)
     * @return this builder for chaining
     */
    default SELF between(SFunction<E, ?> field, Comparable<?> start, Comparable<?> end) {
        if (field == null) {
            throw new IllegalArgumentException("field must not be null");
        }
        conditions().add(new QuerySpec.SimpleNode(LambdaUtils.getPropertyName(field),
                new Comparable<?>[]{start, end}, QuerySpec.Op.BETWEEN));
        return self();
    }

    // ---- Null operators ----

    /**
     * Adds an IS NULL condition: {@code field IS NULL}.
     */
    default SELF isNull(SFunction<E, ?> field) {
        if (field == null) {
            throw new IllegalArgumentException("field must not be null");
        }
        conditions().add(new QuerySpec.SimpleNode(LambdaUtils.getPropertyName(field), null, QuerySpec.Op.IS_NULL));
        return self();
    }

    /**
     * Adds an IS NOT NULL condition: {@code field IS NOT NULL}.
     */
    default SELF isNotNull(SFunction<E, ?> field) {
        if (field == null) {
            throw new IllegalArgumentException("field must not be null");
        }
        conditions().add(new QuerySpec.SimpleNode(LambdaUtils.getPropertyName(field), null, QuerySpec.Op.IS_NOT_NULL));
        return self();
    }

    // ---- Collection empty checks ----

    /**
     * Adds an IS EMPTY condition for to-many associations.
     * Use with {@code @OneToMany} or {@code @ManyToMany} fields.
     */
    default SELF isEmpty(SFunction<E, ?> field) {
        if (field == null) {
            throw new IllegalArgumentException("field must not be null");
        }
        conditions().add(new QuerySpec.CollectionNode(LambdaUtils.getPropertyName(field), QuerySpec.CollectionOp.IS_EMPTY));
        return self();
    }

    /**
     * Adds an IS NOT EMPTY condition for to-many associations.
     */
    default SELF isNotEmpty(SFunction<E, ?> field) {
        if (field == null) {
            throw new IllegalArgumentException("field must not be null");
        }
        conditions().add(new QuerySpec.CollectionNode(LambdaUtils.getPropertyName(field), QuerySpec.CollectionOp.IS_NOT_EMPTY));
        return self();
    }

    /**
     * Adds a raw {@link Predicate} using the current entity {@link Path} and {@link CriteriaBuilder}.
     * This is the escape hatch for conditions not covered by the builder API.
     *
     * @param fn function receiving the entity path and criteria builder, returning a predicate
     * @return this builder for chaining
     */
    @SuppressWarnings("unchecked")
    default SELF where(BiFunction<Path<E>, CriteriaBuilder, Predicate> fn) {
        if (fn == null) {
            throw new IllegalArgumentException("fn must not be null");
        }
        conditions().add(new QuerySpec.RawNode((BiFunction<Path<?>, CriteriaBuilder, Predicate>) (Object) fn));
        return self();
    }

    // ---- Multi-field search ----

    /**
     * Adds a multi-field LIKE search. The keyword is wrapped in {@code %keyword%}
     * and matched against each given field, joined with OR.
     *
     * @param keyword the search keyword
     * @param fields  one or more method references to string properties
     * @return this builder for chaining
     */
    @SuppressWarnings("unchecked")
    default SELF multiLike(String keyword, SFunction<E, ?>... fields) {
        if (keyword != null && !keyword.isEmpty() && fields.length > 0) {
            String[] fieldNames = new String[fields.length];
            for (int i = 0; i < fields.length; i++) {
                fieldNames[i] = LambdaUtils.getPropertyName(fields[i]);
            }
            conditions().add(new QuerySpec.MultiLikeNode(keyword, fieldNames));
        }
        return self();
    }
}
