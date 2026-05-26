package com.zsubera.jpa.update;

import com.zsubera.jpa.spec.PredicateHelper;
import com.zsubera.jpa.spec.SFunction;
import com.zsubera.jpa.util.LambdaUtils;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Abstract base for type-safe JPA bulk operation builders
 * ({@link UpdateSpec} and {@link DeleteSpec}).
 * <p>
 * Provides common condition methods using deferred lambda evaluation.
 * Predicate construction is delegated to {@link PredicateHelper}
 * to share logic with other components.
 *
 * @param <T> the entity type
 * @param <SELF> the concrete builder type for fluent chaining
 */
public abstract class AbstractBulkOperationSpec<T, SELF extends AbstractBulkOperationSpec<T, SELF>> {

    protected final Class<T> entityClass;
    protected final List<BiFunction<Root<T>, CriteriaBuilder, Predicate>> conditionFunctions = new ArrayList<>();

    protected AbstractBulkOperationSpec(Class<T> entityClass) {
        this.entityClass = entityClass;
    }

    @SuppressWarnings("unchecked")
    protected SELF self() {
        return (SELF) this;
    }

    protected String property(SFunction<T, ?> field) {
        if (field == null) {
            throw new IllegalArgumentException("field must not be null");
        }
        return LambdaUtils.getPropertyName(field);
    }

    /**
     * Executes the bulk operation within a new transaction if none is active,
     * otherwise executes within the current transaction.
     *
     * @param em the EntityManager
     * @return the number of affected rows
     */
    public int executeInTransaction(EntityManager em) {
        return executeInTransaction(em, this::doExecute);
    }

    /**
     * Executes the given operation within a new transaction if none is active,
     * otherwise executes within the current transaction.
     * <p>
     * This overload allows subclasses to execute custom operations
     * (e.g., unconditional deleteAll) with proper transaction management.
     *
     * @param em        the EntityManager
     * @param operation the operation to execute
     * @return the number of affected rows
     */
    protected int executeInTransaction(EntityManager em, Function<EntityManager, Integer> operation) {
        EntityTransaction tx = em.getTransaction();
        boolean isNewTransaction = !tx.isActive();
        if (isNewTransaction) {
            tx.begin();
        }
        try {
            int result = operation.apply(em);
            if (isNewTransaction) {
                tx.commit();
            }
            return result;
        } catch (RuntimeException e) {
            if (isNewTransaction && tx.isActive()) {
                try {
                    tx.rollback();
                } catch (Exception rollbackEx) {
                    e.addSuppressed(rollbackEx);
                }
            }
            throw e;
        } catch (Exception e) {
            if (isNewTransaction && tx.isActive()) {
                try {
                    tx.rollback();
                } catch (Exception rollbackEx) {
                    e.addSuppressed(rollbackEx);
                }
            }
            throw new RuntimeException("Bulk operation failed", e);
        }
    }

    /**
     * Executes the bulk operation. Requires an active transaction in the
     * underlying {@link EntityManager}.
     *
     * @param em the EntityManager
     * @return the number of affected rows
     * @throws jakarta.persistence.TransactionRequiredException if no transaction is active
     */
    public abstract int execute(EntityManager em);

    protected abstract int doExecute(EntityManager em);

    public SELF eq(SFunction<T, ?> field, Object value) {
        String name = property(field);
        conditionFunctions.add((root, cb) -> PredicateHelper.eq(root, name, value, cb));
        return self();
    }

    public SELF ne(SFunction<T, ?> field, Object value) {
        String name = property(field);
        conditionFunctions.add((root, cb) -> PredicateHelper.ne(root, name, value, cb));
        return self();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public SELF gt(SFunction<T, ?> field, Comparable<?> value) {
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
        String name = property(field);
        conditionFunctions.add((root, cb) -> PredicateHelper.gt(root, name, value, cb));
        return self();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public SELF ge(SFunction<T, ?> field, Comparable<?> value) {
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
        String name = property(field);
        conditionFunctions.add((root, cb) -> PredicateHelper.ge(root, name, value, cb));
        return self();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public SELF lt(SFunction<T, ?> field, Comparable<?> value) {
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
        String name = property(field);
        conditionFunctions.add((root, cb) -> PredicateHelper.lt(root, name, value, cb));
        return self();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public SELF le(SFunction<T, ?> field, Comparable<?> value) {
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
        String name = property(field);
        conditionFunctions.add((root, cb) -> PredicateHelper.le(root, name, value, cb));
        return self();
    }

    public SELF like(SFunction<T, ?> field, String value) {
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
        String name = property(field);
        conditionFunctions.add((root, cb) -> PredicateHelper.like(root, name, value, cb));
        return self();
    }

    public SELF notLike(SFunction<T, ?> field, String value) {
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
        String name = property(field);
        conditionFunctions.add((root, cb) -> PredicateHelper.notLike(root, name, value, cb));
        return self();
    }

    public SELF startsWith(SFunction<T, ?> field, String value) {
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
        String name = property(field);
        conditionFunctions.add((root, cb) -> PredicateHelper.startsWith(root, name, value, cb));
        return self();
    }

    public SELF endsWith(SFunction<T, ?> field, String value) {
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
        String name = property(field);
        conditionFunctions.add((root, cb) -> PredicateHelper.endsWith(root, name, value, cb));
        return self();
    }

    public SELF contains(SFunction<T, ?> field, String value) {
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
        String name = property(field);
        conditionFunctions.add((root, cb) -> PredicateHelper.contains(root, name, value, cb));
        return self();
    }

    public SELF eqIgnoreCase(SFunction<T, ?> field, String value) {
        String name = property(field);
        conditionFunctions.add((root, cb) -> PredicateHelper.eqIgnoreCase(root, name, value, cb));
        return self();
    }

    public SELF likeIgnoreCase(SFunction<T, ?> field, String value) {
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
        String name = property(field);
        conditionFunctions.add((root, cb) -> PredicateHelper.likeIgnoreCase(root, name, value, cb));
        return self();
    }

    public SELF in(SFunction<T, ?> field, Object... values) {
        String name = property(field);
        if (values == null || values.length == 0) {
            throw new IllegalArgumentException("values must not be empty");
        }
        conditionFunctions.add((root, cb) -> PredicateHelper.in(root, name, values, cb));
        return self();
    }

    public SELF notIn(SFunction<T, ?> field, Object... values) {
        String name = property(field);
        if (values == null || values.length == 0) {
            throw new IllegalArgumentException("values must not be empty");
        }
        conditionFunctions.add((root, cb) -> PredicateHelper.notIn(root, name, values, cb));
        return self();
    }

    public SELF in(SFunction<T, ?> field, Collection<?> values) {
        String name = property(field);
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException("values must not be empty");
        }
        conditionFunctions.add((root, cb) -> PredicateHelper.in(root, name, values, cb));
        return self();
    }

    public SELF notIn(SFunction<T, ?> field, Collection<?> values) {
        String name = property(field);
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException("values must not be empty");
        }
        conditionFunctions.add((root, cb) -> PredicateHelper.notIn(root, name, values, cb));
        return self();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public SELF between(SFunction<T, ?> field, Comparable<?> start, Comparable<?> end) {
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
        conditionFunctions.add((root, cb) -> PredicateHelper.between(root, name, start, end, cb));
        return self();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public SELF notBetween(SFunction<T, ?> field, Comparable<?> start, Comparable<?> end) {
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
        conditionFunctions.add((root, cb) -> PredicateHelper.notBetween(root, name, start, end, cb));
        return self();
    }

    public SELF isNull(SFunction<T, ?> field) {
        String name = property(field);
        conditionFunctions.add((root, cb) -> PredicateHelper.isNull(root, name, cb));
        return self();
    }

    public SELF isNotNull(SFunction<T, ?> field) {
        String name = property(field);
        conditionFunctions.add((root, cb) -> PredicateHelper.isNotNull(root, name, cb));
        return self();
    }

    @SuppressWarnings("unchecked")
    public SELF where(Function<Root<T>, Predicate> condition) {
        if (condition == null) {
            throw new IllegalArgumentException("condition must not be null");
        }
        conditionFunctions.add((root, cb) -> condition.apply(root));
        return self();
    }

    protected Predicate[] buildPredicates(Root<T> root, CriteriaBuilder cb) {
        if (conditionFunctions.isEmpty()) {
            return null;
        }
        List<Predicate> predicates = new ArrayList<>();
        for (BiFunction<Root<T>, CriteriaBuilder, Predicate> fn : conditionFunctions) {
            predicates.add(fn.apply(root, cb));
        }
        return predicates.toArray(new Predicate[0]);
    }
}
