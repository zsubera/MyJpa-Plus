package com.zsubera.jpa.repository;

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
}
