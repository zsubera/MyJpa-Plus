package com.zsubera.jpa.template;

import com.zsubera.jpa.spec.QuerySpec;
import com.zsubera.jpa.update.DeleteSpec;
import com.zsubera.jpa.update.UpdateSpec;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Convenience template for executing MyJpa-Plus queries and bulk operations with automatic {@link
 * EntityManager} injection.
 *
 * <p>This eliminates the need to manually pass an {@code EntityManager} to {@link UpdateSpec} and
 * {@link DeleteSpec}. Simply inject this template and use its methods.
 *
 * <p>Example:
 *
 * <pre>{@code
 * &#64;Autowired
 * private MyJpaTemplate jpa;
 *
 * public void deactivateOldUsers() {
 *     int updated = jpa.update(User.class)
 *         .set(User::getStatus, "INACTIVE")
 *         .lt(User::getLastLogin, cutoffDate)
 *         .execute();
 *
 *     int deleted = jpa.delete(LogEntry.class)
 *         .lt(LogEntry::getTimestamp, oldDate)
 *         .execute();
 *
 *     List<User> activeUsers = jpa.findAll(
 *         new QuerySpec<User>().eq(User::getStatus, "ACTIVE")
 *     );
 * }
 * }</pre>
 */
@Component
public class MyJpaTemplate {

  private static final Logger log = LoggerFactory.getLogger(MyJpaTemplate.class);

  @PersistenceContext private EntityManager entityManager;

  /**
   * Creates an {@link UpdateSpec} for the given entity class. The {@link EntityManager} is provided
   * at execution time via {@link #execute(UpdateSpec)}.
   *
   * @param entityClass the entity class to update
   * @param <T> the entity type
   * @return a new UpdateSpec (not yet bound to an EntityManager)
   */
  public <T> UpdateSpec<T> update(Class<T> entityClass) {
    return new UpdateSpec<>(entityClass);
  }

  /**
   * Creates a {@link DeleteSpec} for the given entity class. The {@link EntityManager} is provided
   * at execution time via {@link #execute(DeleteSpec)}.
   *
   * @param entityClass the entity class to delete from
   * @param <T> the entity type
   * @return a new DeleteSpec (not yet bound to an EntityManager)
   */
  public <T> DeleteSpec<T> delete(Class<T> entityClass) {
    return new DeleteSpec<>(entityClass);
  }

  // ---- Query methods ----

  /**
   * Finds all entities of the given type matching the given {@link QuerySpec}.
   *
   * @param entityClass the entity class
   * @param spec the QuerySpec
   * @param <T> the entity type
   * @return list of matching entities
   */
  @Transactional(readOnly = true)
  public <T> List<T> findAll(Class<T> entityClass, QuerySpec<T> spec) {
    CriteriaBuilder cb = entityManager.getCriteriaBuilder();
    CriteriaQuery<T> cq = cb.createQuery(entityClass);
    Root<T> root = cq.from(entityClass);
    jakarta.persistence.criteria.Predicate predicate = spec.toPredicate(root, cq, cb);
    if (predicate != null) {
      cq.where(predicate);
    }
    TypedQuery<T> query = entityManager.createQuery(cq);
    spec.applyQuerySettings(query);
    return query.getResultList();
  }

  /**
   * Finds all entities of the given type matching the given {@link Specification}.
   *
   * @param entityClass the entity class
   * @param spec the Specification
   * @param <T> the entity type
   * @return list of matching entities
   */
  @Transactional(readOnly = true)
  public <T> List<T> find(Class<T> entityClass, Specification<T> spec) {
    CriteriaBuilder cb = entityManager.getCriteriaBuilder();
    CriteriaQuery<T> cq = cb.createQuery(entityClass);
    Root<T> root = cq.from(entityClass);
    jakarta.persistence.criteria.Predicate predicate = spec.toPredicate(root, cq, cb);
    if (predicate != null) {
      cq.where(predicate);
    }
    return entityManager.createQuery(cq).getResultList();
  }

  /**
   * Finds a page of entities of the given type matching the given {@link QuerySpec}.
   *
   * @param entityClass the entity class
   * @param spec the QuerySpec
   * @param pageable pagination information
   * @param <T> the entity type
   * @return a Page of matching entities
   */
  @Transactional(readOnly = true)
  public <T> Page<T> findAll(Class<T> entityClass, QuerySpec<T> spec, Pageable pageable) {
    return findPageInternal(entityClass, spec.toSpecification(), pageable, spec);
  }

  private <T> Page<T> findPageInternal(
      Class<T> entityClass, Specification<T> spec, Pageable pageable, QuerySpec<T> querySpec) {
    CriteriaBuilder cb = entityManager.getCriteriaBuilder();

    if (pageable.isUnpaged()) {
      CriteriaQuery<T> cq = cb.createQuery(entityClass);
      Root<T> root = cq.from(entityClass);
      jakarta.persistence.criteria.Predicate predicate = spec.toPredicate(root, cq, cb);
      if (predicate != null) {
        cq.where(predicate);
      }
      TypedQuery<T> typedQuery = entityManager.createQuery(cq);
      querySpec.applyQuerySettings(typedQuery);
      List<T> allContent = typedQuery.getResultList();
      return new PageImpl<>(allContent);
    }

    CriteriaQuery<Long> countCq = cb.createQuery(Long.class);
    Root<T> countRoot = countCq.from(entityClass);
    countCq.select(cb.count(countRoot));
    jakarta.persistence.criteria.Predicate countPredicate = spec.toPredicate(countRoot, null, cb);
    if (countPredicate != null) {
      countCq.where(countPredicate);
    }
    long total = entityManager.createQuery(countCq).getSingleResult();

    CriteriaQuery<T> cq = cb.createQuery(entityClass);
    Root<T> root = cq.from(entityClass);
    jakarta.persistence.criteria.Predicate predicate = spec.toPredicate(root, cq, cb);
    if (predicate != null) {
      cq.where(predicate);
    }
    if (pageable.getSort().isSorted()) {
      cq.orderBy(
          pageable.getSort().stream()
              .map(
                  order ->
                      order.isAscending()
                          ? cb.asc(root.get(order.getProperty()))
                          : cb.desc(root.get(order.getProperty())))
              .toList());
    }
    TypedQuery<T> query = entityManager.createQuery(cq);
    query.setFirstResult((int) pageable.getOffset());
    query.setMaxResults(pageable.getPageSize());
    querySpec.applyQuerySettings(query);
    List<T> content = query.getResultList();

    return new PageImpl<>(content, pageable, total);
  }

  /**
   * Finds a page of entities of the given type matching the given {@link Specification}.
   *
   * @param entityClass the entity class
   * @param spec the Specification
   * @param pageable pagination information
   * @param <T> the entity type
   * @return a Page of matching entities
   */
  @Transactional(readOnly = true)
  public <T> Page<T> findPage(Class<T> entityClass, Specification<T> spec, Pageable pageable) {
    CriteriaBuilder cb = entityManager.getCriteriaBuilder();

    // Handle Pageable.unpaged() — return all results without count/distinct pollution
    if (pageable.isUnpaged()) {
      CriteriaQuery<T> cq = cb.createQuery(entityClass);
      Root<T> root = cq.from(entityClass);
      jakarta.persistence.criteria.Predicate predicate = spec.toPredicate(root, cq, cb);
      if (predicate != null) {
        cq.where(predicate);
      }
      List<T> allContent = entityManager.createQuery(cq).getResultList();
      return new PageImpl<>(allContent);
    }

    // Count query — pass null CriteriaQuery to avoid distinct/groupBy/orderBy
    // side effects from spec.toPredicate() leaking into the count query.
    // The returned Predicate is used only for WHERE filtering.
    CriteriaQuery<Long> countCq = cb.createQuery(Long.class);
    Root<T> countRoot = countCq.from(entityClass);
    countCq.select(cb.count(countRoot));
    jakarta.persistence.criteria.Predicate countPredicate = spec.toPredicate(countRoot, null, cb);
    if (countPredicate != null) {
      countCq.where(countPredicate);
    }
    long total = entityManager.createQuery(countCq).getSingleResult();

    // Data query
    CriteriaQuery<T> cq = cb.createQuery(entityClass);
    Root<T> root = cq.from(entityClass);
    jakarta.persistence.criteria.Predicate predicate = spec.toPredicate(root, cq, cb);
    if (predicate != null) {
      cq.where(predicate);
    }
    if (pageable.getSort().isSorted()) {
      cq.orderBy(
          pageable.getSort().stream()
              .map(
                  order ->
                      order.isAscending()
                          ? cb.asc(root.get(order.getProperty()))
                          : cb.desc(root.get(order.getProperty())))
              .toList());
    }
    TypedQuery<T> query = entityManager.createQuery(cq);
    query.setFirstResult((int) pageable.getOffset());
    query.setMaxResults(pageable.getPageSize());
    List<T> content = query.getResultList();

    return new PageImpl<>(content, pageable, total);
  }

  /**
   * Executes a bulk update with the given {@link UpdateSpec}.
   *
   * @param spec the UpdateSpec to execute
   * @param <T> the entity type
   * @return the number of affected rows
   */
  @Transactional
  public <T> int execute(UpdateSpec<T> spec) {
    return spec.executeInTransaction(entityManager);
  }

  /**
   * Executes a bulk delete with the given {@link DeleteSpec}.
   *
   * @param spec the DeleteSpec to execute
   * @param <T> the entity type
   * @return the number of affected rows
   */
  @Transactional
  public <T> int execute(DeleteSpec<T> spec) {
    return spec.executeInTransaction(entityManager);
  }
}
