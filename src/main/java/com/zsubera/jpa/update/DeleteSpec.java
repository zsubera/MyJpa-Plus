package com.zsubera.jpa.update;

import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaDelete;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

/**
 * Type-safe builder for JPA {@link CriteriaDelete} bulk delete operations.
 * <p>
 * Allows building type-safe DELETE queries using lambda method references.
 * Conditions are stored as deferred functions and resolved at execution time.
 * <p>
 * <strong>Transaction requirement:</strong> {@link #execute(EntityManager)}
 * requires an active transaction. Use {@link #executeInTransaction(EntityManager)}
 * for automatic transaction management.
 * <p>
 * Example:
 * <pre>{@code
 * int deleted = new DeleteSpec<>(User.class)
 *     .lt(User::getLastLogin, cutoffDate)
 *     .eq(User::getStatus, "INACTIVE")
 *     .executeInTransaction(entityManager);
 * }</pre>
 *
 * @param <T> the entity type to delete
 */
public class DeleteSpec<T> extends AbstractBulkOperationSpec<T, DeleteSpec<T>> {

    public DeleteSpec(Class<T> entityClass) {
        super(entityClass);
    }

    /**
     * Executes the DELETE statement and returns the number of affected rows.
     * <p>
     * <strong>Requires an active transaction.</strong>
     * Consider using {@link #executeInTransaction(EntityManager)} instead.
     *
     * @param em the EntityManager
     * @return the number of entities deleted
     * @throws jakarta.persistence.TransactionRequiredException if no transaction is active
     */
    @Override
    public int execute(EntityManager em) {
        return em.createQuery(toDelete(em)).executeUpdate();
    }

    @Override
    protected int doExecute(EntityManager em) {
        return execute(em);
    }

    /**
     * Builds the {@link CriteriaDelete} without executing it.
     */
    public CriteriaDelete<T> toDelete(EntityManager em) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaDelete<T> delete = cb.createCriteriaDelete(entityClass);
        Root<T> root = delete.from(entityClass);
        Predicate[] predicates = buildPredicates(root, cb);
        if (predicates != null) {
            delete.where(cb.and(predicates));
        }
        return delete;
    }
}
