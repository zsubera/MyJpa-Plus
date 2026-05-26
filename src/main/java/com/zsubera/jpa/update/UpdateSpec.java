package com.zsubera.jpa.update;

import com.zsubera.jpa.spec.SFunction;
import com.zsubera.jpa.util.LambdaUtils;

import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaUpdate;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

import java.util.ArrayList;
import java.util.List;

/**
 * Type-safe builder for JPA {@link CriteriaUpdate} bulk update operations.
 * <p>
 * Allows building type-safe UPDATE queries using lambda method references.
 * Conditions are stored as deferred functions and resolved at execution time.
 * <p>
 * <strong>Transaction requirement:</strong> {@link #execute(EntityManager)}
 * requires an active transaction. Use {@link #executeInTransaction(EntityManager)}
 * for automatic transaction management.
 * <p>
 * Example:
 * <pre>{@code
 * int updated = new UpdateSpec<>(User.class)
 *     .set(User::getStatus, "INACTIVE")
 *     .lt(User::getLastLogin, someDate)
 *     .executeInTransaction(entityManager);
 * }</pre>
 *
 * @param <T> the entity type to update
 */
public class UpdateSpec<T> extends AbstractBulkOperationSpec<T, UpdateSpec<T>> {

    private final List<SetClause> setClauses = new ArrayList<>();

    private record SetClause(String fieldName, Object value) {}

    public UpdateSpec(Class<T> entityClass) {
        super(entityClass);
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
     * <p>
     * <strong>Requires an active transaction.</strong>
     * Consider using {@link #executeInTransaction(EntityManager)} instead.
     *
     * @param em the EntityManager
     * @return the number of entities updated
     * @throws IllegalStateException if no SET clauses were specified
     * @throws jakarta.persistence.TransactionRequiredException if no transaction is active
     */
    @Override
    public int execute(EntityManager em) {
        return em.createQuery(toUpdate(em)).executeUpdate();
    }

    @Override
    protected int doExecute(EntityManager em) {
        return execute(em);
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
        Predicate[] predicates = buildPredicates(root, cb);
        if (predicates != null) {
            update.where(cb.and(predicates));
        }
        return update;
    }
}
