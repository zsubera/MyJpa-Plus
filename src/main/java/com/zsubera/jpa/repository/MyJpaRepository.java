package com.zsubera.jpa.repository;

import com.zsubera.jpa.update.SoftDeleteHelper;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.NoRepositoryBean;

import java.util.List;
import java.util.Optional;

/**
 * Base repository interface that combines {@link JpaRepository} and
 * {@link JpaSpecificationExecutor} so consumers only need to extend a
 * single interface.
 * <p>
 * Adds convenience overloads that accept {@code QuerySpec} directly:
 * <pre>{@code
 * public interface UserRepository extends MyJpaRepository<User, Long> {
 * }
 *
 * List<User> users = repository.findAll(
 *     new QuerySpec<User>().eq(User::getStatus, "ACTIVE")
 * );
 * }</pre>
 *
 * @param <T>  the entity type
 * @param <ID> the entity ID type
 */
@NoRepositoryBean
public interface MyJpaRepository<T, ID> extends JpaRepository<T, ID>, JpaSpecificationExecutor<T> {

    /**
     * Finds all entities matching the given {@link Specification}.
     */
    List<T> findAll(Specification<T> spec);

    /**
     * Finds all entities matching the given {@link Specification} with pagination.
     */
    Page<T> findAll(Specification<T> spec, Pageable pageable);

    /**
     * Finds all entities matching the given {@link Specification} with sorting.
     */
    List<T> findAll(Specification<T> spec, Sort sort);

    /**
     * Finds a single entity matching the given {@link Specification}.
     */
    Optional<T> findOne(Specification<T> spec);

    /**
     * Counts entities matching the given {@link Specification}.
     */
    long count(Specification<T> spec);

    /**
     * Checks whether any entity matches the given {@link Specification}.
     */
    boolean exists(Specification<T> spec);

    /**
     * Finds all non-soft-deleted entities matching the given {@link Specification}.
     * Automatically applies the soft-delete filter if the entity has a
     * {@link com.zsubera.jpa.annotation.SoftDelete @SoftDelete} annotated field.
     *
     * @param spec additional filtering specification
     * @return list of non-deleted entities matching the specification
     */
    default List<T> findNotDeletedAll(Specification<T> spec) {
        return findAll(spec.and(SoftDeleteHelper.isNotDeleted(getEntityClass())));
    }

    /**
     * Finds all non-soft-deleted entities without additional conditions.
     *
     * @return list of all non-deleted entities
     */
    default List<T> findNotDeletedAll() {
        return findAll(SoftDeleteHelper.isNotDeleted(getEntityClass()));
    }

    /**
     * Finds all non-soft-deleted entities matching the specification with pagination.
     */
    default Page<T> findNotDeletedAll(Specification<T> spec, Pageable pageable) {
        return findAll(spec.and(SoftDeleteHelper.isNotDeleted(getEntityClass())), pageable);
    }

    /**
     * Finds a single non-soft-deleted entity matching the specification.
     */
    default Optional<T> findNotDeletedOne(Specification<T> spec) {
        return findOne(spec.and(SoftDeleteHelper.isNotDeleted(getEntityClass())));
    }

    /**
     * Finds a single non-soft-deleted entity by ID.
     * Uses a query-level filter to avoid fetching deleted entities.
     */
    default Optional<T> findNotDeletedById(ID id) {
        Class<T> entityClass = getEntityClass();
        String idFieldName = EntityClassResolver.resolveIdFieldName(entityClass);
        return findOne(SoftDeleteHelper.isNotDeleted(entityClass).and(
                (root, query, cb) -> cb.equal(root.get(idFieldName), id)));
    }

    /**
     * Counts non-soft-deleted entities matching the specification.
     */
    default long countNotDeleted(Specification<T> spec) {
        return count(spec.and(SoftDeleteHelper.isNotDeleted(getEntityClass())));
    }

    /**
     * Counts all non-soft-deleted entities without additional conditions.
     */
    default long countNotDeleted() {
        return count(SoftDeleteHelper.isNotDeleted(getEntityClass()));
    }

    /**
     * Returns the domain class associated with this repository.
     * The result is cached per repository class to avoid repeated reflection.
     *
     * @return the entity class
     */
    @SuppressWarnings("unchecked")
    private Class<T> getEntityClass() {
        return EntityClassResolver.resolve(getClass());
    }
}
