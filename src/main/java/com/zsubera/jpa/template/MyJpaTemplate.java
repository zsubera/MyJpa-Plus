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
 * MyJpa-Plus 便捷模板类，用于执行查询和批量操作，自动注入 {@link EntityManager}。
 *
 * <p>使用此类无需手动向 {@link UpdateSpec} 和 {@link DeleteSpec} 传递 {@code EntityManager}。
 * 只需注入此模板并使用其方法即可。
 *
 * <p>使用示例：
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
 *
 * @author MyJpa-Plus
 * @see UpdateSpec
 * @see DeleteSpec
 * @see QuerySpec
 */
@Component
public class MyJpaTemplate {

  private static final Logger log = LoggerFactory.getLogger(MyJpaTemplate.class);

  @PersistenceContext private EntityManager entityManager;

  /**
   * 创建指定实体类的 {@link UpdateSpec}。
   * {@link EntityManager} 将在执行时通过 {@link #execute(UpdateSpec)} 自动提供。
   *
   * @param entityClass 要更新的实体类
   * @param <T> 实体类型
   * @return 新的 UpdateSpec 实例（尚未绑定 EntityManager）
   */
  public <T> UpdateSpec<T> update(Class<T> entityClass) {
    return new UpdateSpec<>(entityClass);
  }

  /**
   * 创建指定实体类的 {@link DeleteSpec}。
   * {@link EntityManager} 将在执行时通过 {@link #execute(DeleteSpec)} 自动提供。
   *
   * @param entityClass 要删除的实体类
   * @param <T> 实体类型
   * @return 新的 DeleteSpec 实例（尚未绑定 EntityManager）
   */
  public <T> DeleteSpec<T> delete(Class<T> entityClass) {
    return new DeleteSpec<>(entityClass);
  }

  // ---- Query methods ----

  /**
   * 根据 {@link QuerySpec} 查询指定类型的所有匹配实体。
   *
   * @param entityClass 实体类
   * @param spec 查询规格
   * @param <T> 实体类型
   * @return 匹配实体列表
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
   * 根据 {@link Specification} 查询指定类型的所有匹配实体。
   *
   * @param entityClass 实体类
   * @param spec Spring Data JPA 规格
   * @param <T> 实体类型
   * @return 匹配实体列表
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
   * 根据 {@link QuerySpec} 分页查询指定类型的匹配实体。
   *
   * @param entityClass 实体类
   * @param spec 查询规格
   * @param pageable 分页信息
   * @param <T> 实体类型
   * @return 匹配实体的分页结果
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
    if (pageable.getOffset() > Integer.MAX_VALUE) {
      throw new IllegalArgumentException("Offset too large: " + pageable.getOffset());
    }
    query.setFirstResult((int) pageable.getOffset());
    query.setMaxResults(pageable.getPageSize());
    querySpec.applyQuerySettings(query);
    List<T> content = query.getResultList();

    return new PageImpl<>(content, pageable, total);
  }

  /**
   * 根据 {@link Specification} 分页查询指定类型的匹配实体。
   *
   * @param entityClass 实体类
   * @param spec Spring Data JPA 规格
   * @param pageable 分页信息
   * @param <T> 实体类型
   * @return 匹配实体的分页结果
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
    if (pageable.getOffset() > Integer.MAX_VALUE) {
      throw new IllegalArgumentException("Offset too large: " + pageable.getOffset());
    }
    query.setFirstResult((int) pageable.getOffset());
    query.setMaxResults(pageable.getPageSize());
    List<T> content = query.getResultList();

    return new PageImpl<>(content, pageable, total);
  }

  /**
   * 执行批量更新操作。
   *
   * @param spec 要执行的 UpdateSpec
   * @param <T> 实体类型
   * @return 受影响的行数
   */
  @Transactional
  public <T> int execute(UpdateSpec<T> spec) {
    return spec.executeInTransaction(entityManager);
  }

  /**
   * 执行批量删除操作。
   *
   * @param spec 要执行的 DeleteSpec
   * @param <T> 实体类型
   * @return 受影响的行数
   */
  @Transactional
  public <T> int execute(DeleteSpec<T> spec) {
    return spec.executeInTransaction(entityManager);
  }
}
