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
 * {@link ConditionNode} entries and append them to that list.
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

    List<ConditionNode> conditions();

    private String fieldName(SFunction<E, ?> field) {
        if (field == null) {
            throw new IllegalArgumentException("field must not be null");
        }
        return LambdaUtils.getPropertyName(field);
    }

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
            conditions().add(new ConditionNode.SimpleNode(LambdaUtils.getPropertyName(field), null, ConditionNode.Op.IS_NULL));
        } else {
            conditions().add(new ConditionNode.SimpleNode(LambdaUtils.getPropertyName(field), value, ConditionNode.Op.EQ));
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
            conditions().add(new ConditionNode.SimpleNode(LambdaUtils.getPropertyName(field), null, ConditionNode.Op.IS_NOT_NULL));
        } else {
            conditions().add(new ConditionNode.SimpleNode(LambdaUtils.getPropertyName(field), value, ConditionNode.Op.NE));
        }
        return self();
    }

    /**
     * Adds a greater-than condition: {@code field > value}.
     *
     * @throws IllegalArgumentException if {@code field} or {@code value} is null
     */
    default SELF gt(SFunction<E, ?> field, Comparable<?> value) {
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
        conditions().add(new ConditionNode.SimpleNode(fieldName(field), value, ConditionNode.Op.GT));
        return self();
    }

    /**
     * Adds a greater-than-or-equal condition: {@code field >= value}.
     *
     * @throws IllegalArgumentException if {@code field} or {@code value} is null
     */
    default SELF ge(SFunction<E, ?> field, Comparable<?> value) {
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
        conditions().add(new ConditionNode.SimpleNode(fieldName(field), value, ConditionNode.Op.GE));
        return self();
    }

    /**
     * Adds a less-than condition: {@code field < value}.
     *
     * @throws IllegalArgumentException if {@code field} or {@code value} is null
     */
    default SELF lt(SFunction<E, ?> field, Comparable<?> value) {
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
        conditions().add(new ConditionNode.SimpleNode(fieldName(field), value, ConditionNode.Op.LT));
        return self();
    }

    /**
     * Adds a less-than-or-equal condition: {@code field <= value}.
     *
     * @throws IllegalArgumentException if {@code field} or {@code value} is null
     */
    default SELF le(SFunction<E, ?> field, Comparable<?> value) {
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
        conditions().add(new ConditionNode.SimpleNode(fieldName(field), value, ConditionNode.Op.LE));
        return self();
    }

    // ---- String operators ----

    /**
     * Adds a LIKE condition: {@code field LIKE value}.
     * The caller is responsible for including wildcards (e.g. {@code "%keyword%"}).
     *
     * @throws IllegalArgumentException if {@code field} or {@code value} is null
     */
    default SELF like(SFunction<E, ?> field, String value) {
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
        conditions().add(new ConditionNode.SimpleNode(fieldName(field), value, ConditionNode.Op.LIKE));
        return self();
    }

    /**
     * Adds a NOT LIKE condition: {@code field NOT LIKE value}.
     *
     * @throws IllegalArgumentException if {@code field} or {@code value} is null
     */
    default SELF notLike(SFunction<E, ?> field, String value) {
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
        conditions().add(new ConditionNode.SimpleNode(fieldName(field), value, ConditionNode.Op.NOT_LIKE));
        return self();
    }

    /**
     * Adds a LIKE condition for prefix matching: {@code field LIKE 'value%'}.
     *
     * @throws IllegalArgumentException if {@code field} or {@code value} is null
     */
    default SELF startsWith(SFunction<E, ?> field, String value) {
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
        conditions().add(new ConditionNode.SimpleNode(fieldName(field), value + "%", ConditionNode.Op.LIKE));
        return self();
    }

    /**
     * Adds a LIKE condition for suffix matching: {@code field LIKE '%value'}.
     *
     * @throws IllegalArgumentException if {@code field} or {@code value} is null
     */
    default SELF endsWith(SFunction<E, ?> field, String value) {
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
        conditions().add(new ConditionNode.SimpleNode(fieldName(field), "%" + value, ConditionNode.Op.LIKE));
        return self();
    }

    /**
     * Adds a LIKE condition for substring matching: {@code field LIKE '%value%'}.
     *
     * @throws IllegalArgumentException if {@code field} or {@code value} is null
     */
    default SELF contains(SFunction<E, ?> field, String value) {
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
        conditions().add(new ConditionNode.SimpleNode(fieldName(field), "%" + value + "%", ConditionNode.Op.LIKE));
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
        if (values == null || values.length == 0) {
            throw new IllegalArgumentException("values must not be empty");
        }
        conditions().add(new ConditionNode.SimpleNode(LambdaUtils.getPropertyName(field), values, ConditionNode.Op.IN));
        return self();
    }

    /**
     * Adds a NOT IN condition: {@code field NOT IN (values)}.
     */
    default SELF notIn(SFunction<E, ?> field, Object... values) {
        if (field == null) {
            throw new IllegalArgumentException("field must not be null");
        }
        if (values == null || values.length == 0) {
            throw new IllegalArgumentException("values must not be empty");
        }
        conditions().add(new ConditionNode.SimpleNode(LambdaUtils.getPropertyName(field), values, ConditionNode.Op.NOT_IN));
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
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException("values must not be empty");
        }
        conditions().add(new ConditionNode.SimpleNode(LambdaUtils.getPropertyName(field), values, ConditionNode.Op.IN));
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
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException("values must not be empty");
        }
        conditions().add(new ConditionNode.SimpleNode(LambdaUtils.getPropertyName(field), values, ConditionNode.Op.NOT_IN));
        return self();
    }

    /**
     * Adds a BETWEEN condition: {@code field BETWEEN start AND end}.
     *
     * @param field a method reference to the entity property
     * @param start the lower bound (inclusive)
     * @param end   the upper bound (inclusive)
     * @return this builder for chaining
     * @throws IllegalArgumentException if {@code field}, {@code start}, or {@code end} is null,
     *         or if start is greater than end
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    default SELF between(SFunction<E, ?> field, Comparable<?> start, Comparable<?> end) {
        if (start == null) {
            throw new IllegalArgumentException("start must not be null");
        }
        if (end == null) {
            throw new IllegalArgumentException("end must not be null");
        }
        if (((Comparable) start).compareTo(end) > 0) {
            throw new IllegalArgumentException("start must not be greater than end");
        }
        conditions().add(new ConditionNode.SimpleNode(fieldName(field),
                new Comparable<?>[]{start, end}, ConditionNode.Op.BETWEEN));
        return self();
    }

    /**
     * Adds a NOT BETWEEN condition: {@code field NOT BETWEEN start AND end}.
     *
     * @throws IllegalArgumentException if {@code field}, {@code start}, or {@code end} is null,
     *         or if start is greater than end
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    default SELF notBetween(SFunction<E, ?> field, Comparable<?> start, Comparable<?> end) {
        if (start == null) {
            throw new IllegalArgumentException("start must not be null");
        }
        if (end == null) {
            throw new IllegalArgumentException("end must not be null");
        }
        if (((Comparable) start).compareTo(end) > 0) {
            throw new IllegalArgumentException("start must not be greater than end");
        }
        conditions().add(new ConditionNode.SimpleNode(fieldName(field),
                new Comparable<?>[]{start, end}, ConditionNode.Op.NOT_BETWEEN));
        return self();
    }

    // ---- Null operators ----

    /**
     * Adds an IS NULL condition: {@code field IS NULL}.
     */
    default SELF isNull(SFunction<E, ?> field) {
        conditions().add(new ConditionNode.SimpleNode(fieldName(field), null, ConditionNode.Op.IS_NULL));
        return self();
    }

    /**
     * Adds an IS NOT NULL condition: {@code field IS NOT NULL}.
     */
    default SELF isNotNull(SFunction<E, ?> field) {
        conditions().add(new ConditionNode.SimpleNode(fieldName(field), null, ConditionNode.Op.IS_NOT_NULL));
        return self();
    }

    /**
     * Case-insensitive equality: {@code UPPER(field) = UPPER(value)}.
     * Useful for case-insensitive username/email lookups.
     */
    default SELF eqIgnoreCase(SFunction<E, ?> field, String value) {
        if (field == null) {
            throw new IllegalArgumentException("field must not be null");
        }
        if (value == null) {
            return isNull(field);
        }
        conditions().add(new ConditionNode.SimpleNode(LambdaUtils.getPropertyName(field), value, ConditionNode.Op.EQ_IGNORE_CASE));
        return self();
    }

    /**
     * Case-insensitive LIKE: {@code UPPER(field) LIKE UPPER('%value%')}.
     *
     * @throws IllegalArgumentException if {@code field} or {@code value} is null
     */
    default SELF likeIgnoreCase(SFunction<E, ?> field, String value) {
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
        conditions().add(new ConditionNode.SimpleNode(fieldName(field), value, ConditionNode.Op.LIKE_IGNORE_CASE));
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
        conditions().add(new ConditionNode.CollectionNode(fieldName(field), ConditionNode.CollectionOp.IS_EMPTY));
        return self();
    }

    /**
     * Adds an IS NOT EMPTY condition for to-many associations.
     */
    default SELF isNotEmpty(SFunction<E, ?> field) {
        if (field == null) {
            throw new IllegalArgumentException("field must not be null");
        }
        conditions().add(new ConditionNode.CollectionNode(fieldName(field), ConditionNode.CollectionOp.IS_NOT_EMPTY));
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
        conditions().add(new ConditionNode.RawNode((BiFunction<Path<?>, CriteriaBuilder, Predicate>) (Object) fn));
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
        if (fields == null) {
            throw new IllegalArgumentException("fields must not be null");
        }
        if (keyword != null && !keyword.isEmpty() && fields.length > 0) {
            String[] fieldNames = new String[fields.length];
            for (int i = 0; i < fields.length; i++) {
                if (fields[i] == null) {
                    throw new IllegalArgumentException("fields[" + i + "] must not be null");
                }
                fieldNames[i] = LambdaUtils.getPropertyName(fields[i]);
            }
            conditions().add(new ConditionNode.MultiLikeNode(keyword, fieldNames));
        }
        return self();
    }

    // ---- Conditional convenience methods ----

    /**
     * Adds an equality condition only if {@code condition} is true.
     */
    default SELF eq(boolean condition, SFunction<E, ?> field, Object value) {
        return condition ? eq(field, value) : self();
    }

    /**
     * Adds a not-equal condition only if {@code condition} is true.
     */
    default SELF ne(boolean condition, SFunction<E, ?> field, Object value) {
        return condition ? ne(field, value) : self();
    }

    /**
     * Adds a greater-than condition only if {@code condition} is true.
     */
    default SELF gt(boolean condition, SFunction<E, ?> field, Comparable<?> value) {
        return condition ? gt(field, value) : self();
    }

    /**
     * Adds a greater-than-or-equal condition only if {@code condition} is true.
     */
    default SELF ge(boolean condition, SFunction<E, ?> field, Comparable<?> value) {
        return condition ? ge(field, value) : self();
    }

    /**
     * Adds a less-than condition only if {@code condition} is true.
     */
    default SELF lt(boolean condition, SFunction<E, ?> field, Comparable<?> value) {
        return condition ? lt(field, value) : self();
    }

    /**
     * Adds a less-than-or-equal condition only if {@code condition} is true.
     */
    default SELF le(boolean condition, SFunction<E, ?> field, Comparable<?> value) {
        return condition ? le(field, value) : self();
    }

    /**
     * Adds a LIKE condition only if {@code condition} is true.
     */
    default SELF like(boolean condition, SFunction<E, ?> field, String value) {
        return condition ? like(field, value) : self();
    }

    /**
     * Adds a NOT LIKE condition only if {@code condition} is true.
     */
    default SELF notLike(boolean condition, SFunction<E, ?> field, String value) {
        return condition ? notLike(field, value) : self();
    }

    /**
     * Adds a startsWith condition only if {@code condition} is true.
     */
    default SELF startsWith(boolean condition, SFunction<E, ?> field, String value) {
        return condition ? startsWith(field, value) : self();
    }

    /**
     * Adds an endsWith condition only if {@code condition} is true.
     */
    default SELF endsWith(boolean condition, SFunction<E, ?> field, String value) {
        return condition ? endsWith(field, value) : self();
    }

    /**
     * Adds a contains condition only if {@code condition} is true.
     */
    default SELF contains(boolean condition, SFunction<E, ?> field, String value) {
        return condition ? contains(field, value) : self();
    }

    /**
     * Adds an IN condition only if {@code condition} is true.
     */
    default SELF in(boolean condition, SFunction<E, ?> field, Object... values) {
        return condition ? in(field, values) : self();
    }

    /**
     * Adds an IN condition with a Collection only if {@code condition} is true.
     */
    default SELF in(boolean condition, SFunction<E, ?> field, Collection<?> values) {
        return condition ? in(field, values) : self();
    }

    /**
     * Adds a NOT IN condition only if {@code condition} is true.
     */
    default SELF notIn(boolean condition, SFunction<E, ?> field, Object... values) {
        return condition ? notIn(field, values) : self();
    }

    /**
     * Adds a BETWEEN condition only if {@code condition} is true.
     */
    default SELF between(boolean condition, SFunction<E, ?> field, Comparable<?> start, Comparable<?> end) {
        return condition ? between(field, start, end) : self();
    }

    /**
     * Adds a multi-field LIKE search only if {@code condition} is true.
     */
    @SuppressWarnings("unchecked")
    default SELF multiLike(boolean condition, String keyword, SFunction<E, ?>... fields) {
        return condition ? multiLike(keyword, fields) : self();
    }
}
