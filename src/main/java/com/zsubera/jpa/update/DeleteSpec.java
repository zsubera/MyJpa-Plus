package com.zsubera.jpa.update;

import com.zsubera.jpa.repository.EntityClassResolver;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaDelete;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JPA {@link CriteriaDelete} 批量删除操作的类型安全构建器。
 *
 * <p>允许使用 Lambda 方法引用构建类型安全的 DELETE 查询。条件以延迟函数形式存储， 在执行时才进行解析。
 *
 * <p><strong>事务要求：</strong>{@link #execute(EntityManager)} 需要活动事务。 可使用 {@link
 * #executeInTransaction(EntityManager)} 进行自动事务管理。
 *
 * <p>示例：
 *
 * <pre>{@code
 * int deleted = new DeleteSpec<>(User.class)
 *     .lt(User::getLastLogin, cutoffDate)
 *     .eq(User::getStatus, "INACTIVE")
 *     .executeInTransaction(entityManager);
 * }</pre>
 *
 * @param <T> 要删除的实体类型
 */
public class DeleteSpec<T> extends AbstractBulkOperationSpec<T, DeleteSpec<T>> {

  private static final Logger log = LoggerFactory.getLogger(DeleteSpec.class);

  /**
   * 创建指定实体类型的删除规范构建器。
   *
   * @param entityClass 要删除的实体类
   * @throws IllegalArgumentException 如果 entityClass 为 null
   */
  public DeleteSpec(Class<T> entityClass) {
    super(entityClass);
  }

  /**
   * 执行 DELETE 语句并返回受影响的行数。
   *
   * <p><strong>需要活动事务。</strong>建议使用 {@link #executeInTransaction(EntityManager)}。
   *
   * @param em 实体管理器
   * @return 删除的实体数量
   * @throws jakarta.persistence.TransactionRequiredException 如果没有活动事务
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
   * 构建 {@link CriteriaDelete} 对象但不执行。
   *
   * @param em 实体管理器
   * @return 构建的 CriteriaDelete 对象
   * @throws IllegalStateException 如果没有添加任何条件。可使用 {@link #deleteAll(EntityManager)} 进行无条件删除
   */
  public CriteriaDelete<T> toDelete(EntityManager em) {
    CriteriaBuilder cb = em.getCriteriaBuilder();
    CriteriaDelete<T> delete = cb.createCriteriaDelete(entityClass);
    Root<T> root = delete.from(entityClass);
    Predicate[] predicates = buildPredicates(root, cb);
    if (predicates.length == 0) {
      throw new IllegalStateException(
          "No WHERE conditions specified for DELETE operation. "
              + "This would delete ALL rows in the table. "
              + "If unconditional deletion is intended, use deleteAll(EntityManager) instead.");
    }
    delete.where(cb.and(predicates));
    return delete;
  }

  /**
   * 执行无条件删除，删除该实体的所有行。
   *
   * <p>谨慎使用 — 此操作将删除表中的所有数据。
   *
   * @param em 实体管理器
   * @return 删除的实体数量
   */
  public int deleteAll(EntityManager em) {
    log.warn(
        "WARNING: Executing unconditional DELETE on {} — this will affect ALL rows!",
        entityClass.getSimpleName());
    CriteriaBuilder cb = em.getCriteriaBuilder();
    CriteriaDelete<T> delete = cb.createCriteriaDelete(entityClass);
    delete.from(entityClass);
    return em.createQuery(delete).executeUpdate();
  }

  /**
   * 在新事务或现有事务中执行无条件删除。
   *
   * @param em 实体管理器
   * @return 删除的实体数量
   */
  public int deleteAllInTransaction(EntityManager em) {
    return executeInTransaction(em, this::deleteAll);
  }

  /**
   * 执行 DELETE 语句并限制受影响的行数。
   *
   * <p>此方法适用于批处理场景。它会限制 SQL 影响的行数。请注意，DELETE 语句的 LIMIT 支持因数据库而异。
   *
   * <p><strong>注意：</strong>此方法需要活动事务。调用方负责在批次之间刷新和清除持久化上下文。
   *
   * @param em 实体管理器
   * @param limit 要删除的最大行数
   * @return 实际删除的行数
   */
  public int executeLimited(EntityManager em, int limit) {
    if (limit <= 0) {
      throw new IllegalArgumentException("limit must be positive");
    }
    CriteriaBuilder cb = em.getCriteriaBuilder();

    // Step 1: 查询符合条件的ID列表（带LIMIT）
    String idFieldName = EntityClassResolver.resolveIdFieldName(entityClass);
    CriteriaQuery<?> idQuery = cb.createQuery();
    Root<T> idRoot = idQuery.from(entityClass);
    idQuery.select(idRoot.get(idFieldName));
    Predicate[] predicates = buildPredicates(idRoot, cb);
    if (predicates.length == 0) {
      throw new IllegalStateException(
          "No WHERE conditions specified for DELETE operation. "
              + "Use deleteAll() for unconditional deletions.");
    }
    idQuery.where(cb.and(predicates));
    List<?> ids = em.createQuery(idQuery).setMaxResults(limit).getResultList();

    if (ids.isEmpty()) {
      return 0;
    }

    // Step 2: 用ID列表执行删除
    CriteriaDelete<T> delete = cb.createCriteriaDelete(entityClass);
    Root<T> deleteRoot = delete.from(entityClass);
    delete.where(deleteRoot.get(idFieldName).in(ids));
    return em.createQuery(delete).executeUpdate();
  }
}
