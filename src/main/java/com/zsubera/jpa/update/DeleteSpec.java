package com.zsubera.jpa.update;

import com.zsubera.jpa.spec.SFunction;
import com.zsubera.jpa.util.LambdaUtils;

import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaDelete;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.BiFunction;

/**
 * Type-safe builder for JPA {@link CriteriaDelete} bulk delete operations.
 * <p>
 * Allows building type-safe DELETE queries using lambda method references.
 * Conditions are stored as deferred functions and resolved at execution time.
 * <p>
 * Example:
 * <pre>{@code
 * int deleted = new DeleteSpec<>(User.class)
 *     .lt(User::getLastLogin, cutoffDate)
 *     .eq(User::getStatus, "INACTIVE")
 *     .execute(entityManager);
 * }</pre>
 *
 * @param <T> the entity type to delete
 */
public class DeleteSpec<T> {

    private final Class<T> entityClass;
    private final List<BiFunction<Root<T>, CriteriaBuilder, Predicate>> conditionFunctions = new ArrayList<>();

    public DeleteSpec(Class<T> entityClass) {
        this.entityClass = entityClass;
    }

    /**
     * Executes the DELETE statement and returns the number of affected rows.
     *
     * @param em the EntityManager
     * @return the number of entities deleted
     */
    public int execute(EntityManager em) {
        return em.createQuery(toDelete(em)).executeUpdate();
    }

    /**
     * Builds the {@link CriteriaDelete} without executing it.
     */
    public CriteriaDelete<T> toDelete(EntityManager em) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaDelete<T> delete = cb.createCriteriaDelete(entityClass);
        Root<T> root = delete.from(entityClass);
        if (!conditionFunctions.isEmpty()) {
            List<Predicate> predicates = new ArrayList<>();
            for (BiFunction<Root<T>, CriteriaBuilder, Predicate> fn : conditionFunctions) {
                predicates.add(fn.apply(root, cb));
            }
            delete.where(cb.and(predicates.toArray(new Predicate[0])));
        }
        return delete;
    }

    private String property(SFunction<T, ?> field) {
        if (field == null) {
            throw new IllegalArgumentException("field must not be null");
        }
        return LambdaUtils.getPropertyName(field);
    }

    public DeleteSpec<T> eq(SFunction<T, ?> field, Object value) {
        String name = property(field);
        if (value == null) {
            conditionFunctions.add((root, cb) -> cb.isNull(root.get(name)));
        } else {
            conditionFunctions.add((root, cb) -> cb.equal(root.get(name), value));
        }
        return this;
    }

    public DeleteSpec<T> ne(SFunction<T, ?> field, Object value) {
        String name = property(field);
        if (value == null) {
            conditionFunctions.add((root, cb) -> cb.isNotNull(root.get(name)));
        } else {
            conditionFunctions.add((root, cb) -> cb.notEqual(root.get(name), value));
        }
        return this;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public DeleteSpec<T> gt(SFunction<T, ?> field, Comparable<?> value) {
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
        String name = property(field);
        conditionFunctions.add((root, cb) -> cb.greaterThan((Expression) root.get(name), (Comparable) value));
        return this;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public DeleteSpec<T> ge(SFunction<T, ?> field, Comparable<?> value) {
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
        String name = property(field);
        conditionFunctions.add((root, cb) -> cb.greaterThanOrEqualTo((Expression) root.get(name), (Comparable) value));
        return this;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public DeleteSpec<T> lt(SFunction<T, ?> field, Comparable<?> value) {
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
        String name = property(field);
        conditionFunctions.add((root, cb) -> cb.lessThan((Expression) root.get(name), (Comparable) value));
        return this;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public DeleteSpec<T> le(SFunction<T, ?> field, Comparable<?> value) {
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
        String name = property(field);
        conditionFunctions.add((root, cb) -> cb.lessThanOrEqualTo((Expression) root.get(name), (Comparable) value));
        return this;
    }

    public DeleteSpec<T> like(SFunction<T, ?> field, String value) {
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
        String name = property(field);
        conditionFunctions.add((root, cb) -> cb.like(root.get(name).as(String.class), value));
        return this;
    }

    public DeleteSpec<T> in(SFunction<T, ?> field, Object... values) {
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

    public DeleteSpec<T> in(SFunction<T, ?> field, Collection<?> values) {
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
    public DeleteSpec<T> between(SFunction<T, ?> field, Comparable<?> start, Comparable<?> end) {
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

    public DeleteSpec<T> isNull(SFunction<T, ?> field) {
        String name = property(field);
        conditionFunctions.add((root, cb) -> cb.isNull(root.get(name)));
        return this;
    }

    public DeleteSpec<T> isNotNull(SFunction<T, ?> field) {
        String name = property(field);
        conditionFunctions.add((root, cb) -> cb.isNotNull(root.get(name)));
        return this;
    }
}
