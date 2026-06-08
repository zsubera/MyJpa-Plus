package com.zsubera.jpa.update;

import com.zsubera.jpa.util.EntityClassResolver;
import com.zsubera.jpa.util.InClauseBuilder;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.TypedQuery;
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
 * <p>
 * 允许使用 Lambda 方法引用构建类型安全的 DELETE 查询。条件以延迟函数形式存储， 在执行时才进行解析。
 *
 * <p>
 * <strong>事务要求：</strong>{@link #execute(EntityManager)} 需要活动事务。 可使用 {@link #executeInTransaction(EntityManager)}
 * 进行自动事务管理。
 *
 * <p>
 * 示例：
 *
 * <pre>{@code
 * int deleted = new DeleteSpec<>(User.class).lt(User::getLastLogin, cutoffDate).eq(User::getStatus, "INACTIVE")
 *     .executeInTransaction(entityManager);
 * }</pre>
 *
 * @param <T> 要删除的实体类型
 */
public class DeleteSpec<T> extends AbstractBulkOperationSpec<T, DeleteSpec<T>> {

    private static final Logger log = LoggerFactory.getLogger(DeleteSpec.class);

    private boolean allowUnconditional = false;

    /**
     * 创建指定实体类型的删除规范构建器。
     *
     * @param entityClass 要删除的实体类
     */
    public DeleteSpec(Class<T> entityClass) {
        super(entityClass);
    }

    /**
     * 显式允许无条件操作（deleteAll）。 在调用 {@link #deleteAll(EntityManager)} 前必须先调用此方法。
     *
     * @param allow 是否允许无条件操作
     * @return 当前构建器实例，支持链式调用
     */
    public DeleteSpec<T> allowUnconditional(boolean allow) {
        this.allowUnconditional = allow;
        return this;
    }

    /**
     * 执行 DELETE 语句并返回受影响的行数。
     *
     * <p>
     * <strong>需要活动事务。</strong>建议使用 {@link #executeInTransaction(EntityManager)}。
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
     * @throws IllegalStateException 如果没有添加任何条件且未调用 allowUnconditional(true)
     */
    public CriteriaDelete<T> toDelete(EntityManager em) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaDelete<T> delete = cb.createCriteriaDelete(entityClass);
        Root<T> root = delete.from(entityClass);
        Predicate[] predicates = buildPredicates(root, cb);
        if (predicates.length == 0) {
            if (!allowUnconditional) {
                throw new IllegalStateException("No WHERE conditions specified for DELETE operation. "
                    + "This would delete ALL rows in the table. "
                    + "If unconditional deletion is intended, use allowUnconditional(true) then deleteAll(EntityManager).");
            }
            // allowUnconditional=true，返回不带WHERE子句的delete
            return delete;
        }
        delete.where(cb.and(predicates));
        return delete;
    }

    /**
     * 执行无条件删除，删除该实体的所有行。
     *
     * <p>
     * <strong>安全要求：</strong>必须先调用 {@link #allowUnconditional(boolean)} 显式确认， 否则将抛出
     * {@link IllegalStateException}。此机制防止误调用导致全表数据被意外删除。
     *
     * @param em 实体管理器
     * @return 删除的实体数量
     * @throws IllegalStateException 如果未调用 allowUnconditional(true)
     */
    public int deleteAll(EntityManager em) {
        if (!allowUnconditional) {
            throw new IllegalStateException("Unconditional DELETE is not allowed. "
                + "Call .allowUnconditional(true) to explicitly confirm this operation.");
        }
        // 审计日志：记录无条件删除操作及调用栈，便于生产环境追踪危险操作
        log.warn("AUDIT: Executing unconditional DELETE on {} — this will affect ALL rows! Call stack: {}",
            entityClass.getSimpleName(), AuditUtils.getCallStack());
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaDelete<T> delete = cb.createCriteriaDelete(entityClass);
        delete.from(entityClass);
        return em.createQuery(delete).executeUpdate();
    }

    /**
     * 在事务中执行无条件删除，删除该实体的所有行。
     *
     * <p>
     * <strong>安全要求：</strong>必须先调用 {@link #allowUnconditional(boolean)} 显式确认， 否则将抛出
     * {@link IllegalStateException}。此机制防止误调用导致全表数据被意外删除。
     *
     * @param em 实体管理器
     * @return 删除的实体数量
     * @throws IllegalStateException 如果未调用 allowUnconditional(true)
     */
    public int deleteAllInTransaction(EntityManager em) {
        return executeInTransaction(em, this::deleteAll);
    }

    /**
     * 执行限制删除行数的条件删除操作。
     *
     * <p>
     * 此方法适用于批处理场景，通过限制 SQL 影响的行数来控制操作范围。 请注意，不同数据库对 DELETE 语句的 LIMIT 支持程度不同。
     *
     * <p>
     * <strong>注意：</strong>此方法需要活动事务。调用方负责在批次之间刷新和清除持久化上下文。
     *
     * <p>
     * <strong>安全说明：</strong>此方法默认启用悲观锁（{@code pessimisticLock=true}）， 以防止查询ID和执行删除之间的并发竞态条件。如需禁用悲观锁，请使用
     * {@link #executeLimited(EntityManager, int, boolean)} 并设置 {@code pessimisticLock=false}。
     *
     * @param em 实体管理器
     * @param limit 最大删除行数
     * @return 实际删除的行数
     */
    public int executeLimited(EntityManager em, int limit) {
        return executeLimited(em, limit, true);
    }

    /**
     * 执行限制删除行数的条件删除操作，支持可选的悲观锁。
     *
     * <p>
     * 此方法首先查询符合条件的实体 ID 列表（带限制），然后对这些实体执行删除操作。
     *
     * <p>
     * <strong>并发风险警告：</strong>此方法分两步执行（先查询 ID，再删除），在高并发场景下存在竞态条件。 在查询ID和执行删除之间，其他事务可能修改或删除记录，导致数据不一致。
     *
     * <p>
     * <strong>安全使用建议（按推荐程度排序）：</strong>
     * <ol>
     * <li>使用 {@code pessimisticLock=true}，在单个数据库事务中持有行锁，防止并发修改：
     *
     * <pre>{@code
     * DeleteSpec.of(LogEntry.class).lt(LogEntry::getTimestamp, cutoffDate).executeLimited(entityManager, 1000, true); // 悲观锁
     * }</pre>
     *
     * <li>在已有的 {@code @Transactional} 方法内调用，确保查询和更新在同一事务中执行
     * <li>对于支持 {@code DELETE ... LIMIT} 的数据库（如 MySQL），考虑使用原生 SQL 作为替代方案
     * <li>在应用层使用分布式锁保护整个操作流程
     * </ol>
     *
     * <p>
     * <strong>注意：</strong>此方法使用 CriteriaDelete 绕过 JPA 生命周期回调，不会触发 {@code @PreRemove}/{@code @PostRemove}。
     * 如果实体有 L1 缓存中的托管实例，删除后可能返回过时数据。如需确保一致性，请在调用后手动执行 {@code em.clear()}。
     *
     * @param em 实体管理器
     * @param limit 最大删除行数
     * @param pessimisticLock 如果为 true，则获取 {@link jakarta.persistence.LockModeType#PESSIMISTIC_WRITE} 锁以防止并发修改
     * @return 实际删除的行数
     */
    public int executeLimited(EntityManager em, int limit, boolean pessimisticLock) {
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }
        if (EntityClassResolver.hasCompositeKey(entityClass)) {
            throw new UnsupportedOperationException(
                "executeLimited() does not support entities with composite primary keys (@EmbeddedId or @IdClass). "
                    + "Entity: " + entityClass.getName() + ". "
                    + "Use delete(entityManager).executeInTransaction(entityManager) instead.");
        }
        if (!pessimisticLock) {
            log.warn("executeLimited() with pessimisticLock=false may cause race conditions. "
                + "Consider using pessimisticLock=true for critical operations. "
                + "The two-step approach (SELECT IDs then DELETE) has a time window where concurrent "
                + "transactions may modify or delete records, leading to inconsistent results.");
        }
        CriteriaBuilder cb = em.getCriteriaBuilder();

        // Step 1: 查询符合条件的ID列表（带LIMIT）
        String idFieldName = EntityClassResolver.resolveIdFieldName(entityClass);
        CriteriaQuery<?> idQuery = cb.createQuery();
        Root<T> idRoot = idQuery.from(entityClass);
        idQuery.select(idRoot.get(idFieldName));
        Predicate[] predicates = buildPredicates(idRoot, cb);
        if (predicates.length == 0) {
            // 与 deleteAll() 保持一致的安全检查
            if (!allowUnconditional) {
                throw new IllegalStateException("No WHERE conditions specified for DELETE operation. "
                    + "Call .allowUnconditional(true) to explicitly confirm this operation, "
                    + "or use deleteAll(EntityManager) instead.");
            }
            log.warn("WARNING: Executing limited DELETE without conditions on {} — this will affect up to {} rows!",
                entityClass.getSimpleName(), limit);
        }
        idQuery.where(predicates.length > 0 ? cb.and(predicates) : cb.conjunction());
        TypedQuery<?> query = em.createQuery(idQuery).setMaxResults(limit);
        if (pessimisticLock) {
            query.setLockMode(LockModeType.PESSIMISTIC_WRITE);
        }
        List<?> ids = query.getResultList();

        if (ids.isEmpty()) {
            return 0;
        }

        // Step 2: 用ID列表执行删除
        CriteriaDelete<T> delete = cb.createCriteriaDelete(entityClass);
        Root<T> deleteRoot = delete.from(entityClass);
        delete.where(InClauseBuilder.in(cb, deleteRoot.get(idFieldName), ids));
        return em.createQuery(delete).executeUpdate();
    }

}
