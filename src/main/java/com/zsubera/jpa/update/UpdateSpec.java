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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;

/**
 * JPA {@link CriteriaUpdate} 批量更新操作的类型安全构建器。
 *
 * <p>允许使用 Lambda 方法引用构建类型安全的 UPDATE 查询。条件以延迟函数形式存储，
 * 在执行时才进行解析。
 *
 * <p><strong>事务要求：</strong>{@link #execute(EntityManager)} 需要活动事务。
 * 可使用 {@link #executeInTransaction(EntityManager)} 进行自动事务管理。
 *
 * <p>示例：
 *
 * <pre>{@code
 * int updated = new UpdateSpec<>(User.class)
 *     .set(User::getStatus, "INACTIVE")
 *     .lt(User::getLastLogin, someDate)
 *     .executeInTransaction(entityManager);
 * }</pre>
 *
 * @param <T> 要更新的实体类型
 */
public class UpdateSpec<T> extends AbstractBulkOperationSpec<T, UpdateSpec<T>> {

  private static final Logger log = LoggerFactory.getLogger(UpdateSpec.class);

  private final List<SetClause> setClauses = new ArrayList<>();

  private record SetClause(String fieldName, Object value) {}

  /**
   * 创建指定实体类型的更新规范构建器。
   *
   * @param entityClass 要更新的实体类
   * @throws IllegalArgumentException 如果 entityClass 为 null
   */
  public UpdateSpec(Class<T> entityClass) {
    super(entityClass);
  }

  /**
   * 在 UPDATE 子句中设置字段值。
   *
   * @param field 实体属性的方法引用
   * @param value 新值（可为 null）
   * @return 当前构建器实例，支持链式调用
   * @throws IllegalArgumentException 如果 field 为 null
   */
  public UpdateSpec<T> set(SFunction<T, ?> field, @Nullable Object value) {
    if (field == null) {
      throw new IllegalArgumentException("field must not be null");
    }
    setClauses.add(new SetClause(LambdaUtils.getPropertyName(field), value));
    return this;
  }

  /**
   * 仅当条件为 true 时，在 UPDATE 子句中设置字段值。
   *
   * @param condition 执行条件
   * @param field 实体属性的方法引用
   * @param value 新值
   * @return 当前构建器实例，支持链式调用
   */
  public UpdateSpec<T> set(boolean condition, SFunction<T, ?> field, Object value) {
    if (condition) {
      set(field, value);
    }
    return this;
  }

  /**
   * 执行 UPDATE 语句并返回受影响的行数。
   *
   * <p><strong>需要活动事务。</strong>建议使用 {@link #executeInTransaction(EntityManager)}。
   *
   * @param em 实体管理器
   * @return 更新的实体数量
   * @throws IllegalStateException 如果未指定 SET 子句
   * @throws jakarta.persistence.TransactionRequiredException 如果没有活动事务
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
   * 构建 {@link CriteriaUpdate} 对象但不执行。
   *
   * @param em 实体管理器
   * @return 构建的 CriteriaUpdate 对象
   * @throws IllegalStateException 如果未指定 SET 子句或没有 WHERE 条件
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
    if (predicates.length == 0) {
      throw new IllegalStateException(
          "No WHERE conditions specified for UPDATE operation. "
              + "This would update ALL rows in the table. "
              + "If unconditional update is intended, use updateAll(EntityManager) instead.");
    }
    update.where(cb.and(predicates));
    return update;
  }

  /**
   * 执行无条件更新，更新该实体的所有行。
   *
   * <p>谨慎使用 — 此操作将更新表中的所有数据。
   *
   * @param em 实体管理器
   * @return 更新的实体数量
   * @throws IllegalStateException 如果未指定 SET 子句
   */
  public int updateAll(EntityManager em) {
    if (setClauses.isEmpty()) {
      throw new IllegalStateException("At least one set() clause is required");
    }
    log.warn(
        "WARNING: Executing unconditional UPDATE on {} — this will affect ALL rows!",
        entityClass.getSimpleName());
    CriteriaBuilder cb = em.getCriteriaBuilder();
    CriteriaUpdate<T> update = cb.createCriteriaUpdate(entityClass);
    Root<T> root = update.from(entityClass);
    for (SetClause sc : setClauses) {
      update.set(root.get(sc.fieldName), sc.value);
    }
    return em.createQuery(update).executeUpdate();
  }

  /**
   * 在新事务或现有事务中执行无条件更新。
   *
   * @param em 实体管理器
   * @return 更新的实体数量
   */
  public int updateAllInTransaction(EntityManager em) {
    return executeInTransaction(em, this::updateAll);
  }

  /**
   * 执行 UPDATE 语句并限制受影响的行数。
   *
   * <p>此方法适用于批处理场景。它会限制 SQL 影响的行数。请注意，UPDATE 语句的 LIMIT 支持因数据库而异。
   *
   * <p><strong>注意：</strong>此方法需要活动事务。调用方负责在批次之间刷新和清除持久化上下文。
   *
   * @param em 实体管理器
   * @param limit 要更新的最大行数
   * @return 实际更新的行数
   * @throws IllegalStateException 如果未指定 SET 子句
   */
  public int executeLimited(EntityManager em, int limit) {
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
    if (predicates.length == 0) {
      throw new IllegalStateException(
          "No WHERE conditions specified for UPDATE operation. "
              + "Use updateAll() for unconditional updates.");
    }
    update.where(cb.and(predicates));
    // 使用 JPA 查询执行更新
    jakarta.persistence.Query query = em.createQuery(update);
    return query.executeUpdate();
  }
}
