package com.zsubera.jpa.update;

import com.zsubera.jpa.spec.SFunction;
import com.zsubera.jpa.util.LambdaUtils;

import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaUpdate;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.BiFunction;

/**
 * Type-safe builder for JPA {@link CriteriaUpdate} bulk update operations.
 * <p>
 * Allows building type-safe UPDATE queries using lambda method references.
 * Conditions are stored as deferred functions and resolved at execution time.
 * <p>
 * Example:
 * <pre>{@code
 * int updated = new UpdateSpec<>(User.class)
 *     .set(User::getStatus, "INACTIVE")
 *     .lt(User::getLastLogin, someDate)
 *     .execute(entityManager);
 * }</pre>
 *
 * @param <T> the entity type to update
 */
public class UpdateSpec<T> {

    private final Class<T> entityClass;
    private final List<SetClause> setClauses = new ArrayList<>();
    private final List<BiFunction<Root<T>, CriteriaBuilder, Predicate>> conditionFunctions = new ArrayList<>();

    private record SetClause(String fieldName, Object value) {}

    public UpdateSpec(Class<T> entityClass) {
        this.entityClass = entityClass;
    }

    /**
     * Sets a field to a given value in the UPDATE clause.
     *
     * @param field a method reference to the entity property
     * @param value the new value (can be null)
     * @return this builder for chaining
     */
    public UpdateSpec<T> set(SFunction<T, ?> field, Object value) {
        if (field == null) {
            throw new IllegalArgumentException("field must not be null");
        }
        setClauses.add(new SetClause(LambdaUtils.getPropertyName(field), value));
        return this;
    }

    /**
     * Sets a field to a given value only if {@code condition} is true.
     */
    public UpdateSpec<T> set(boolean condition, SFunction<T, ?> field, Object value) {
        if (condition) {
            set(field, value);
        }
        return this;
    }

    /**
     * Executes the UPDATE statement and returns the number of affected rows.
     *
     * @param em the EntityManager
     * @return the number of entities updated
     * @throws IllegalStateException if no SET clauses were specified
     */
    public int execute(EntityManager em) {
        return em.createQuery(toUpdate(em)).executeUpdate();
    }

    /**
     * Builds the {@link CriteriaUpdate} without executing it.
     */
    public CriteriaUpdate<T> toUpdate(EntityManager em) {
        if (setClauses.isEmpty()) {
            throw new IllegalStateException("At least one set() clause is required");
        }
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaUpdate<T> update = cb.createCriteriaUpdate(entityClass);
        Root<T> root = update.from(entityClass);
        for (SetClause sc : setClauses) {
            update.set(root.get(sc.fieldName), sc.value);
        }
        if (!conditionFunctions.isEmpty()) {
            List<Predicate> predicates = new ArrayList<>();
            for (BiFunction<Root<T>, CriteriaBuilder, Predicate> fn : conditionFunctions) {
                predicates.add(fn.apply(root, cb));
            }
            update.where(cb.and(predicates.toArray(new Predicate[0])));
        }
        return update;
    }

    private String property(SFunction<T, ?> field) {
        if (field == null) {
            throw new IllegalArgumentException("field must not be null");
        }
        return LambdaUtils.getPropertyName(field);
    }

    public UpdateSpec<T> eq(SFunction<T, ?> field, Object value) {
        String name = property(field);
        if (value == null) {
            conditionFunctions.add((root, cb) -> cb.isNull(root.get(name)));
        } else {
            conditionFunctions.add((root, cb) -> cb.equal(root.get(name), value));
        }
        return this;
    }

    public UpdateSpec<T> ne(SFunction<T, ?> field, Object value) {
        String name = property(field);
        if (value == null) {
            conditionFunctions.add((root, cb) -> cb.isNotNull(root.get(name)));
        } else {
            conditionFunctions.add((root, cb) -> cb.notEqual(root.get(name), value));
        }
        return this;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public UpdateSpec<T> gt(SFunction<T, ?> field, Comparable<?> value) {
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
        String name = property(field);
        conditionFunctions.add((root, cb) -> cb.greaterThan((Expression) root.get(name), (Comparable) value));
        return this;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public UpdateSpec<T> ge(SFunction<T, ?> field, Comparable<?> value) {
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
        String name = property(field);
        conditionFunctions.add((root, cb) -> cb.greaterThanOrEqualTo((Expression) root.get(name), (Comparable) value));
        return this;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public UpdateSpec<T> lt(SFunction<T, ?> field, Comparable<?> value) {
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
        String name = property(field);
        conditionFunctions.add((root, cb) -> cb.lessThan((Expression) root.get(name), (Comparable) value));
        return this;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public UpdateSpec<T> le(SFunction<T, ?> field, Comparable<?> value) {
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
        String name = property(field);
        conditionFunctions.add((root, cb) -> cb.lessThanOrEqualTo((Expression) root.get(name), (Comparable) value));
        return this;
    }

    public UpdateSpec<T> like(SFunction<T, ?> field, String value) {
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
        String name = property(field);
        conditionFunctions.add((root, cb) -> cb.like(root.get(name).as(String.class), value));
        return this;
    }

    public UpdateSpec<T> in(SFunction<T, ?> field, Object... values) {
        String name = property(field);
        if (values == null || values.length == 0) {
            throw new IllegalArgumentException("values must not be empty");
        }
        conditionFunctions.add((root, cb) -> {
            CriteriaBuilder.In<Object> in = cb.in(root.get(name));
            for (Object v : values) {
                in.value(v);
            }
            return in;
        });
        return this;
    }

    public UpdateSpec<T> in(SFunction<T, ?> field, Collection<?> values) {
        String name = property(field);
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException("values must not be empty");
        }
        conditionFunctions.add((root, cb) -> {
            CriteriaBuilder.In<Object> in = cb.in(root.get(name));
            for (Object v : values) {
                in.value(v);
            }
            return in;
        });
        return this;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public UpdateSpec<T> between(SFunction<T, ?> field, Comparable<?> start, Comparable<?> end) {
        if (start == null) {
            throw new IllegalArgumentException("start must not be null");
        }
        if (end == null) {
            throw new IllegalArgumentException("end must not be null");
        }
        if (((Comparable) start).compareTo(end) > 0) {
            throw new IllegalArgumentException("start must not be greater than end");
        }
        String name = property(field);
        conditionFunctions.add((root, cb) -> cb.between((Expression) root.get(name), (Comparable) start, (Comparable) end));
        return this;
    }

    public UpdateSpec<T> isNull(SFunction<T, ?> field) {
        String name = property(field);
        conditionFunctions.add((root, cb) -> cb.isNull(root.get(name)));
        return this;
    }

    public UpdateSpec<T> isNotNull(SFunction<T, ?> field) {
        String name = property(field);
        conditionFunctions.add((root, cb) -> cb.isNotNull(root.get(name)));
        return this;
    }
}
