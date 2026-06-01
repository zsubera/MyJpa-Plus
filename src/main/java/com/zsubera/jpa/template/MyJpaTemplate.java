package com.zsubera.jpa.template;

import com.zsubera.jpa.repository.EntityClassResolver;
import com.zsubera.jpa.spec.QuerySpec;
import com.zsubera.jpa.update.DeleteSpec;
import com.zsubera.jpa.update.UpdateSpec;
import com.zsubera.jpa.util.EntityGraphHelper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.lang.Nullable;
import org.springframework.transaction.annotation.Transactional;

/**
 * MyJpa-Plus 查询和批量操作的便捷模板，支持自动注入 {@link EntityManager}。
 *
 * <p>
 * 使用此模板可以避免手动将 {@code EntityManager} 传递给 {@link UpdateSpec} 和 {@link DeleteSpec}。 只需注入此模板即可使用其方法。
 *
 * <p>
 * <strong>API 选择指南：</strong>
 * <ul>
 * <li><strong>简单查询</strong>：使用 {@link com.zsubera.jpa.repository.MyJpaRepository}，直接调用 {@code findAll(spec)} 等方法</li>
 * <li><strong>需要安全限制的查询</strong>：使用 {@code MyJpaTemplate}，提供内置的结果数量限制、深度分页保护和批量操作限制</li>
 * <li><strong>大数据量操作</strong>：使用 {@link #findAllStream(Class, QuerySpec, Consumer)} 进行流式查询，避免内存溢出</li>
 * </ul>
 *
 * <p>
 * <strong>注意：</strong>直接使用 {@code Repository.findAll(spec)} 可能导致全表查询和内存溢出（OOM）。 推荐使用此模板进行查询，它提供了内置的结果数量限制和分页支持。
 *
 * <p>
 * <strong>生产安全：</strong>此模板为生产环境强制执行安全限制：
 *
 * <ul>
 * <li>{@link #findAll} 和 {@link #find} 方法有可配置的最大行数限制
 * <li>深度分页（大 offset）会触发警告日志
 * <li>使用 {@link #findAllStream} 处理大数据集可避免内存问题
 * </ul>
 *
 * <p>
 * 示例：
 *
 * <pre>{@code
 * &#64;Autowired
 * private MyJpaTemplate jpa;
 *
 * public void deactivateOldUsers() {
 *     int updated =
 *         jpa.execute(jpa.update(User.class).set(User::getStatus, "INACTIVE").lt(User::getLastLogin, cutoffDate));
 *
 *     int deleted = jpa.execute(jpa.delete(LogEntry.class).lt(LogEntry::getTimestamp, oldDate));
 *
 *     List<User> activeUsers = jpa.findAll(User.class, new QuerySpec<User>().eq(User::getStatus, "ACTIVE"));
 * }
 * }</pre>
 *
 * <p>
 * 配置示例（application.yml）：
 *
 * <pre>{@code
 * myjpa-plus:
 *   query:
 *     max-results: 50000
 *     deep-pagination-offset-threshold: 500000
 * }</pre>
 */
public class MyJpaTemplate {

    private static final Logger log = LoggerFactory.getLogger(MyJpaTemplate.class);

    /** {@link #findAll} 和 {@link #find} 方法返回的默认最大行数。 */
    public static final int DEFAULT_MAX_RESULTS = 10000;

    /** 深度分页的 offset 阈值，超过此值会记录警告日志。 */
    public static final int DEFAULT_DEEP_PAGINATION_OFFSET_THRESHOLD = 100000;

    /** 表示禁用限制的特殊值，用于 {@link #deepPaginationOffsetLimit} 和 {@link #maxBulkOperationRows}。 */
    public static final int DISABLED = -1;

    /** 批量操作默认最大影响行数。 */
    public static final int DEFAULT_MAX_BULK_OPERATION_ROWS = 10000;

    /** 深度分页默认硬限制（offset）。 */
    public static final int DEFAULT_DEEP_PAGINATION_OFFSET_LIMIT = 1000000;

    /** 深度分页警告日志的最小间隔（毫秒），防止日志泛滥。 */
    private static final long DEEP_PAGINATION_WARN_INTERVAL_MS = 60_000; // 1 分钟

    /** 批量执行最大迭代次数保护，防止无限循环。 */
    private static final int MAX_BATCH_ITERATIONS = 10000;

    /** 上次记录深度分页警告的时间戳。 */
    private final java.util.concurrent.atomic.AtomicLong lastDeepPaginationWarnTime =
        new java.util.concurrent.atomic.AtomicLong(0);

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired(required = false)
    private ApplicationContext applicationContext;

    private volatile int maxResults = DEFAULT_MAX_RESULTS;
    private volatile int deepPaginationOffsetThreshold = DEFAULT_DEEP_PAGINATION_OFFSET_THRESHOLD;
    /** 深度分页硬限制。默认 1000000，{@code DISABLED} 表示禁用（仅记录警告）。 */
    private volatile int deepPaginationOffsetLimit = DEFAULT_DEEP_PAGINATION_OFFSET_LIMIT;
    /** 批量操作最大影响行数限制。默认 10000，{@code DISABLED} 表示不限制。 */
    private volatile int maxBulkOperationRows = DEFAULT_MAX_BULK_OPERATION_ROWS;

    /** P1-15: 查询默认超时时间（秒）。设置后所有查询将自动应用此超时。默认 -1 表示不设置。 */
    private volatile int defaultTimeoutSeconds = -1;

    /** 创建 MyJpaTemplate 实例，使用默认配置。 */
    public MyJpaTemplate() {
        // 使用默认值
    }

    /**
     * 创建配置了自定义参数的 MyJpaTemplate 实例。 参数验证在 {@link #setMaxResults(int)} 和 {@link #setDeepPaginationOffsetThreshold(int)}
     * 中进行。
     *
     * @param maxResults 最大返回行数
     * @param deepPaginationOffsetThreshold 深度分页警告阈值
     */
    public MyJpaTemplate(int maxResults, int deepPaginationOffsetThreshold) {
        setMaxResults(maxResults);
        setDeepPaginationOffsetThreshold(deepPaginationOffsetThreshold);
    }

    /**
     * 设置最大返回行数。
     *
     * @param maxResults 最大返回行数
     * @throws IllegalArgumentException 如果值不是正数
     */
    public void setMaxResults(int maxResults) {
        if (maxResults <= 0) {
            throw new IllegalArgumentException("maxResults must be positive");
        }
        this.maxResults = maxResults;
    }

    /**
     * 设置深度分页警告阈值。
     *
     * @param deepPaginationOffsetThreshold 深度分页警告阈值
     * @throws IllegalArgumentException 如果值不是正数
     */
    public void setDeepPaginationOffsetThreshold(int deepPaginationOffsetThreshold) {
        if (deepPaginationOffsetThreshold <= 0) {
            throw new IllegalArgumentException("deepPaginationOffsetThreshold must be positive");
        }
        this.deepPaginationOffsetThreshold = deepPaginationOffsetThreshold;
    }

    /**
     * 设置深度分页硬限制。超过此 offset 值将抛出 {@link IllegalArgumentException}，阻止执行。
     *
     * <p>
     * 设置为 {@code -1} 表示禁用硬限制（仅记录警告日志，不阻止执行）。
     *
     * @param deepPaginationOffsetLimit 深度分页硬限制值，或 {@code -1} 表示禁用
     * @throws IllegalArgumentException 如果值不是正数且不等于 -1
     */
    public void setDeepPaginationOffsetLimit(int deepPaginationOffsetLimit) {
        if (deepPaginationOffsetLimit <= 0 && deepPaginationOffsetLimit != -1) {
            throw new IllegalArgumentException("deepPaginationOffsetLimit must be positive or -1 (disabled)");
        }
        this.deepPaginationOffsetLimit = deepPaginationOffsetLimit;
    }

    /**
     * 设置批量操作最大影响行数限制。
     *
     * <p>
     * 设置为 {@code -1} 表示禁用限制（不限制影响行数）。
     *
     * @param maxBulkOperationRows 最大影响行数，或 {@code -1} 表示禁用
     * @throws IllegalArgumentException 如果值不是正数且不等于 -1
     */
    public void setMaxBulkOperationRows(int maxBulkOperationRows) {
        if (maxBulkOperationRows <= 0 && maxBulkOperationRows != -1) {
            throw new IllegalArgumentException("maxBulkOperationRows must be positive or -1 (disabled)");
        }
        this.maxBulkOperationRows = maxBulkOperationRows;
    }

    /**
     * P1-15: 设置查询默认超时时间（秒）。设置后所有查询将自动应用此超时。
     *
     * <p>
     * 设置为 {@code -1} 表示不设置默认超时。
     *
     * @param defaultTimeoutSeconds 查询超时秒数，或 {@code -1} 表示不设置
     * @throws IllegalArgumentException 如果值不是正数且不等于 -1
     */
    public void setDefaultTimeoutSeconds(int defaultTimeoutSeconds) {
        if (defaultTimeoutSeconds <= 0 && defaultTimeoutSeconds != -1) {
            throw new IllegalArgumentException("defaultTimeoutSeconds must be positive or -1 (disabled)");
        }
        this.defaultTimeoutSeconds = defaultTimeoutSeconds;
    }

    /**
     * P1-15: 获取查询默认超时时间（秒）。
     *
     * @return 查询超时秒数，-1 表示不设置
     */
    public int getDefaultTimeoutSeconds() {
        return defaultTimeoutSeconds;
    }

    /**
     * 创建指定实体类型的 {@link UpdateSpec}。{@link EntityManager} 将在执行时通过 {@link #execute(UpdateSpec)} 提供。
     *
     * @param entityClass 要更新的实体类
     * @param <T> 实体类型
     * @return 新的 UpdateSpec（尚未绑定 EntityManager）
     */
    public <T> UpdateSpec<T> update(Class<T> entityClass) {
        return new UpdateSpec<>(entityClass);
    }

    /**
     * 创建指定实体类型的 {@link DeleteSpec}。{@link EntityManager} 将在执行时通过 {@link #execute(DeleteSpec)} 提供。
     *
     * @param entityClass 要删除的实体类
     * @param <T> 实体类型
     * @return 新的 DeleteSpec（尚未绑定 EntityManager）
     */
    public <T> DeleteSpec<T> delete(Class<T> entityClass) {
        return new DeleteSpec<>(entityClass);
    }

    // ---- 批量保存方法 ----

    /**
     * 批量保存实体，使用 EntityManager flush/clear 进行分批处理。
     *
     * <p>
     * 此方法适用于大批量插入场景，通过定期 flush 和 clear EntityManager 来：
     * <ul>
     * <li>减少内存占用（清除一级缓存中的实体）</li>
     * <li>提高数据库交互效率（批量发送 INSERT 语句）</li>
     * </ul>
     *
     * <p>
     * <strong>注意：</strong>此方法使用 {@code merge()} 操作，对于新实体会执行 INSERT，对于已存在的实体会执行 UPDATE。 如果确定所有实体都是新建的，可以考虑使用原生 JDBC
     * 批处理以获得更好的性能。
     *
     * <p>
     * <strong>返回值说明：</strong>返回的实体处于 detached 状态（一级缓存已清除）。 访问延迟加载的关联属性将抛出 {@code LazyInitializationException}。
     * 如需使用返回的实体，请先通过 {@code entityManager.merge()} 重新关联到持久化上下文。
     *
     * @param entities 要保存的实体列表
     * @param batchSize 每批大小，建议值为 50-200
     * @param <T> 实体类型
     * @return 保存后的实体列表（detached 状态）
     * @throws IllegalArgumentException 如果 entities 为 null 或 batchSize 不是正数
     */
    @Transactional(rollbackFor = Exception.class)
    public <T> List<T> saveAllBatched(Iterable<T> entities, int batchSize) {
        if (entities == null) {
            throw new IllegalArgumentException("entities must not be null");
        }
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be positive");
        }
        java.util.ArrayList<T> result = new java.util.ArrayList<>();
        int count = 0;
        for (T entity : entities) {
            // P-03: Use persist() for new entities (ID is null) instead of merge(),
            // which avoids the extra SELECT query that merge() performs to check existence.
            // This significantly improves performance for pure insert scenarios.
            if (isNewEntity(entity)) {
                entityManager.persist(entity);
                result.add(entity);
            } else {
                result.add(entityManager.merge(entity));
            }
            count++;
            if (count % batchSize == 0) {
                entityManager.flush();
                entityManager.clear();
            }
        }
        entityManager.flush();
        entityManager.clear();
        return result;
    }

    /**
     * P1-20: 纯 persist 批量保存实体，所有实体都使用 {@code persist()} 操作。
     *
     * <p>
     * 与 {@link #saveAllBatched(Iterable, int)} 不同，此方法对所有实体使用 {@code persist()}， 避免了 {@code merge()} 产生的额外 SELECT
     * 查询。适用于确定所有实体都是新建的场景（如手动赋 ID 的 UUID 实体）。
     *
     * <p>
     * <strong>注意：</strong>如果实体已存在于数据库中，将抛出 {@code EntityExistsException}。
     *
     * @param entities 要保存的实体列表
     * @param batchSize 每批大小，建议值为 50-200
     * @param <T> 实体类型
     * @return 保存后的实体列表（detached 状态）
     * @throws IllegalArgumentException 如果 entities 为 null 或 batchSize 不是正数
     */
    @Transactional(rollbackFor = Exception.class)
    public <T> List<T> saveAllBatchedPure(Iterable<T> entities, int batchSize) {
        if (entities == null) {
            throw new IllegalArgumentException("entities must not be null");
        }
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be positive");
        }
        java.util.ArrayList<T> result = new java.util.ArrayList<>();
        int count = 0;
        for (T entity : entities) {
            entityManager.persist(entity);
            result.add(entity);
            count++;
            if (count % batchSize == 0) {
                entityManager.flush();
                entityManager.clear();
            }
        }
        entityManager.flush();
        entityManager.clear();
        return result;
    }

    /**
     * 批量保存实体，每批在独立事务中提交，避免长事务导致的数据库锁等待超时问题。
     *
     * <p>
     * 与 {@link #saveAllBatched(Iterable, int)} 不同，此方法每批操作完成后立即提交事务。 适用于大数据量保存场景（如 100000+ 实体），避免长事务导致的问题：
     * <ul>
     * <li>数据库锁等待超时</li>
     * <li>事务日志撑爆</li>
     * <li>回滚段空间不足</li>
     * </ul>
     *
     * <p>
     * <strong>注意：</strong>如果某批操作失败，已提交的批次不会回滚。调用方需要自行处理部分成功的情况。
     *
     * @param entities 要保存的实体列表
     * @param batchSize 每批大小，建议值为 50-200
     * @param <T> 实体类型
     * @return 保存后的实体列表
     * @throws IllegalArgumentException 如果 entities 为 null 或 batchSize 不是正数
     */
    public <T> List<T> saveAllBatchedInSeparateTransactions(Iterable<T> entities, int batchSize) {
        if (entities == null) {
            throw new IllegalArgumentException("entities must not be null");
        }
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be positive");
        }
        java.util.ArrayList<T> result = new java.util.ArrayList<>();
        java.util.ArrayList<T> batch = new java.util.ArrayList<>();
        for (T entity : entities) {
            batch.add(entity);
            if (batch.size() >= batchSize) {
                List<T> batchResult = executeInNewTransaction(em -> {
                    java.util.ArrayList<T> batchRes = new java.util.ArrayList<>();
                    for (T e : batch) {
                        if (isNewEntity(e)) {
                            em.persist(e);
                            batchRes.add(e);
                        } else {
                            batchRes.add(em.merge(e));
                        }
                    }
                    em.flush();
                    em.clear();
                    return batchRes;
                });
                result.addAll(batchResult);
                batch.clear();
            }
        }
        if (!batch.isEmpty()) {
            List<T> batchResult = executeInNewTransaction(em -> {
                java.util.ArrayList<T> batchRes = new java.util.ArrayList<>();
                for (T e : batch) {
                    if (isNewEntity(e)) {
                        em.persist(e);
                        batchRes.add(e);
                    } else {
                        batchRes.add(em.merge(e));
                    }
                }
                em.flush();
                em.clear();
                return batchRes;
            });
            result.addAll(batchResult);
        }
        return result;
    }

    /**
     * P-03: 判断实体是否为新实体（未持久化）。通过检查 @Id 字段是否为 null 来判断。
     *
     * @param entity 实体实例
     * @return 如果 @Id 字段为 null 返回 true
     */
    private boolean isNewEntity(Object entity) {
        // Use PersistenceUnitUtil for accurate ID check
        try {
            jakarta.persistence.PersistenceUnitUtil puu =
                entityManager.getEntityManagerFactory().getPersistenceUnitUtil();
            return puu.getIdentifier(entity) == null;
        } catch (RuntimeException ignored) {
            // fallback to reflection
        }
        // Fallback: check common patterns
        try {
            java.lang.reflect.Method getId = entity.getClass().getMethod("getId");
            return getId.invoke(entity) == null;
        } catch (ReflectiveOperationException ignored) {
            // If we can't determine, assume not new (use merge for safety)
            return false;
        }
    }

    // ---- 便捷查询方法 ----

    /**
     * 根据 ID 查找实体。
     *
     * <p>
     * 对于非软删除实体，直接使用 {@link EntityManager#find(Class, Object)} 以获得最佳性能。 对于软删除实体，使用 Specification 查询以自动过滤已删除记录。
     *
     * @param entityClass 实体类
     * @param id 实体 ID
     * @param <T> 实体类型
     * @param <ID> ID 类型
     * @return 匹配实体的 Optional 包装
     */
    @Transactional(readOnly = true)
    public <T, ID> Optional<T> findById(Class<T> entityClass, ID id) {
        if (entityClass == null) {
            throw new IllegalArgumentException("entityClass must not be null");
        }
        if (id == null) {
            throw new IllegalArgumentException("id must not be null");
        }
        // 非软删除场景：直接使用 entityManager.find()，性能最优
        if (com.zsubera.jpa.update.SoftDeleteHelper.findSoftDeleteField(entityClass) == null) {
            return Optional.ofNullable(entityManager.find(entityClass, id));
        }
        // 软删除场景：使用 Specification 查询以自动过滤已删除记录
        String idFieldName = EntityClassResolver.resolveIdFieldName(entityClass);
        Specification<T> idSpec = (root, query, cb) -> cb.equal(root.get(idFieldName), id);
        Specification<T> softDeleteSpec = com.zsubera.jpa.update.SoftDeleteHelper.isNotDeleted(entityClass);
        Specification<T> combinedSpec = idSpec.and(softDeleteSpec);
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<T> cq = cb.createQuery(entityClass);
        Root<T> root = cq.from(entityClass);
        jakarta.persistence.criteria.Predicate predicate = combinedSpec.toPredicate(root, cq, cb);
        if (predicate != null) {
            cq.where(predicate);
        }
        TypedQuery<T> query = entityManager.createQuery(cq);
        query.setMaxResults(1);
        List<T> results = query.getResultList();
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    /**
     * 查找匹配给定 {@link QuerySpec} 的单个实体。
     *
     * @param entityClass 实体类
     * @param spec 查询规范
     * @param <T> 实体类型
     * @return 匹配实体的 Optional 包装
     */
    @Transactional(readOnly = true)
    public <T> Optional<T> findOne(Class<T> entityClass, QuerySpec<T> spec) {
        if (entityClass == null) {
            throw new IllegalArgumentException("entityClass must not be null");
        }
        if (spec == null) {
            throw new IllegalArgumentException("spec must not be null");
        }
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<T> cq = cb.createQuery(entityClass);
        Root<T> root = cq.from(entityClass);
        jakarta.persistence.criteria.Predicate predicate = spec.toPredicate(root, cq, cb);
        if (predicate != null) {
            cq.where(predicate);
        }
        TypedQuery<T> query = entityManager.createQuery(cq);
        query.setMaxResults(1);
        spec.applyQuerySettings(query);
        List<T> results = query.getResultList();
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    // ---- 计数方法 ----

    /**
     * 统计匹配给定 {@link QuerySpec} 的实体数量。
     *
     * @param entityClass 实体类
     * @param spec 查询规范
     * @param <T> 实体类型
     * @return 匹配实体数量
     */
    @Transactional(readOnly = true)
    public <T> long count(Class<T> entityClass, QuerySpec<T> spec) {
        if (entityClass == null) {
            throw new IllegalArgumentException("entityClass must not be null");
        }
        if (spec == null) {
            throw new IllegalArgumentException("spec must not be null");
        }
        return count(entityClass, spec.toSpecification());
    }

    /**
     * 统计匹配给定 {@link Specification} 的实体数量。
     *
     * @param entityClass 实体类
     * @param spec 查询规范
     * @param <T> 实体类型
     * @return 匹配实体数量
     */
    @Transactional(readOnly = true)
    public <T> long count(Class<T> entityClass, Specification<T> spec) {
        if (entityClass == null) {
            throw new IllegalArgumentException("entityClass must not be null");
        }
        if (spec == null) {
            throw new IllegalArgumentException("spec must not be null");
        }
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        return executeCountQuery(entityClass, spec, cb);
    }

    // ---- 查询方法 ----

    /**
     * 查找匹配给定 {@link QuerySpec} 的所有实体。
     *
     * <p>
     * <strong>生产说明：</strong>此方法将结果限制为可配置的最大行数（默认 {@value #DEFAULT_MAX_RESULTS}）。使用
     * {@link #findAll(Class, QuerySpec, int)} 指定自定义限制，或使用 {@link #findAllStream(Class, QuerySpec)} 进行无界流式查询。
     *
     * @param entityClass 实体类
     * @param spec 查询规范
     * @param <T> 实体类型
     * @return 匹配实体列表（限制为最大行数）
     */
    @Transactional(readOnly = true)
    public <T> List<T> findAll(Class<T> entityClass, QuerySpec<T> spec) {
        if (entityClass == null) {
            throw new IllegalArgumentException("entityClass must not be null");
        }
        if (spec == null) {
            throw new IllegalArgumentException("spec must not be null");
        }
        return findAll(entityClass, spec, this.maxResults);
    }

    /**
     * 查找匹配给定 {@link QuerySpec} 的所有实体，支持自定义最大行数限制。
     *
     * @param entityClass 实体类
     * @param spec 查询规范
     * @param maxResults 返回的最大结果数
     * @param <T> 实体类型
     * @return 匹配实体列表
     */
    @Transactional(readOnly = true)
    public <T> List<T> findAll(Class<T> entityClass, QuerySpec<T> spec, int maxResults) {
        if (entityClass == null) {
            throw new IllegalArgumentException("entityClass must not be null");
        }
        if (spec == null) {
            throw new IllegalArgumentException("spec must not be null");
        }
        if (maxResults <= 0) {
            throw new IllegalArgumentException("maxResults must be positive");
        }
        TypedQuery<T> query = buildTypedQuery(entityClass, spec, null, maxResults);
        return query.getResultList();
    }

    /**
     * P2-16: 查找匹配给定 {@link QuerySpec} 的所有实体，支持自定义排序。
     *
     * @param entityClass 实体类
     * @param spec 查询规范
     * @param sort 排序规则
     * @param <T> 实体类型
     * @return 匹配实体列表（限制为最大行数）
     * @throws IllegalArgumentException 如果任何参数为 null
     */
    @Transactional(readOnly = true)
    public <T> List<T> findAll(Class<T> entityClass, QuerySpec<T> spec, org.springframework.data.domain.Sort sort) {
        if (entityClass == null) {
            throw new IllegalArgumentException("entityClass must not be null");
        }
        if (spec == null) {
            throw new IllegalArgumentException("spec must not be null");
        }
        if (sort == null) {
            throw new IllegalArgumentException("sort must not be null");
        }
        TypedQuery<T> query = buildSpecificationQuery(entityClass, spec.toSpecification(), sort, this.maxResults);
        spec.applyQuerySettings(query);
        return query.getResultList();
    }

    /**
     * 查找匹配给定 {@link QuerySpec} 的所有实体，支持可选的 EntityGraph 用于急切加载。
     *
     * @param entityClass 实体类
     * @param spec 查询规范
     * @param entityGraph 用于急切加载的实体图（可为 null）
     * @param <T> 实体类型
     * @return 匹配实体列表
     */
    @Transactional(readOnly = true)
    public <T> List<T> findAll(Class<T> entityClass, QuerySpec<T> spec, EntityGraphHelper<T> entityGraph) {
        return findAll(entityClass, spec, entityGraph, this.maxResults);
    }

    /**
     * 查找匹配给定 {@link QuerySpec} 的所有实体，支持可选的 EntityGraph 和自定义最大行数限制。
     *
     * @param entityClass 实体类
     * @param spec 查询规范
     * @param entityGraph 用于急切加载的实体图（可为 null）
     * @param maxResults 返回的最大结果数
     * @param <T> 实体类型
     * @return 匹配实体列表
     */
    @Transactional(readOnly = true)
    public <T> List<T> findAll(Class<T> entityClass, QuerySpec<T> spec, EntityGraphHelper<T> entityGraph,
        int maxResults) {
        if (entityClass == null) {
            throw new IllegalArgumentException("entityClass must not be null");
        }
        if (spec == null) {
            throw new IllegalArgumentException("spec must not be null");
        }
        if (maxResults <= 0) {
            throw new IllegalArgumentException("maxResults must be positive");
        }
        TypedQuery<T> query = buildTypedQuery(entityClass, spec, entityGraph, maxResults);
        return query.getResultList();
    }

    /**
     * 流式查询匹配给定 {@link QuerySpec} 的所有实体。适用于处理大数据集而无需将所有数据加载到内存。
     *
     * <p>
     * <strong>重要：必须使用 try-with-resources 确保资源关闭：</strong>
     *
     * <pre>{@code
     * try (Stream<User> stream = jpa.findAllStream(User.class, spec)) {
     *     stream.filter(u -> u.getAge() > 18).forEach(this::processUser);
     * }
     * }</pre>
     *
     * <p>
     * <strong>警告：未关闭 Stream 会导致数据库连接泄漏！</strong>底层的 EntityManager 和事务必须在 Stream 处理的整个期间保持活动状态。 推荐使用
     * {@link #findAllStream(Class, QuerySpec, Consumer)} 安全版本，它会自动管理 Stream 生命周期。
     *
     * <p>
     * <strong>风险说明：</strong>此方法直接返回 Stream，调用方必须负责关闭。 如果调用方忘记关闭 Stream 或在关闭前发生异常，数据库连接将泄漏。 安全版本
     * {@link #findAllStream(Class, QuerySpec, Consumer)} 使用 try-with-resources 自动管理资源， 应优先使用。
     *
     * @param entityClass 实体类
     * @param spec 查询规范
     * @param <T> 实体类型
     * @return 匹配实体的 Stream（必须由调用方关闭）
     * @deprecated 使用 {@link #findAllStream(Class, QuerySpec, Consumer)} 安全版本替代，该版本自动管理 Stream 生命周期，避免资源泄漏。 此方法将在 2.0
     *             版本中移除。
     */
    @Deprecated(since = "1.0.1", forRemoval = true)
    @Transactional(readOnly = true)
    public <T> Stream<T> findAllStream(Class<T> entityClass, QuerySpec<T> spec) {
        if (entityClass == null) {
            throw new IllegalArgumentException("entityClass must not be null");
        }
        if (spec == null) {
            throw new IllegalArgumentException("spec must not be null");
        }
        log.warn("findAllStream(Class, QuerySpec) is deprecated and will be removed in 2.0. "
            + "Use findAllStream(Class, QuerySpec, Consumer) for safe Stream lifecycle management. "
            + "This method returns a Stream that must be closed by the caller to avoid connection leaks.");
        // P1-5: This method is kept for backward compatibility. In 2.0, it will throw UnsupportedOperationException.
        return doFindStream(entityClass, spec, null);
    }

    /**
     * 流式查询匹配给定 {@link QuerySpec} 的所有实体，支持可选的 EntityGraph。
     *
     * @param entityClass 实体类
     * @param spec 查询规范
     * @param entityGraph 用于急切加载的实体图（可为 null）
     * @param <T> 实体类型
     * @return 匹配实体的 Stream（必须由调用方关闭）
     * @deprecated 使用 {@link #findAllStream(Class, QuerySpec, Consumer)} 安全版本替代，该版本自动管理 Stream 生命周期，避免资源泄漏。 此方法将在 2.0
     *             版本中移除。
     */
    @Deprecated(since = "1.0.1", forRemoval = true)
    @Transactional(readOnly = true)
    public <T> Stream<T> findAllStream(Class<T> entityClass, QuerySpec<T> spec, EntityGraphHelper<T> entityGraph) {
        if (entityClass == null) {
            throw new IllegalArgumentException("entityClass must not be null");
        }
        if (spec == null) {
            throw new IllegalArgumentException("spec must not be null");
        }
        TypedQuery<T> query = buildTypedQuery(entityClass, spec, entityGraph, null);
        return query.getResultStream();
    }

    /**
     * 安全版本的流式查询，自动管理 Stream 生命周期。推荐使用此方法替代 {@link #findAllStream(Class, QuerySpec)}， 以避免忘记关闭 Stream 导致的数据库连接泄漏。
     *
     * <p>
     * 示例：
     *
     * <pre>{@code
     * jpa.findAllStream(User.class, spec, stream -> {
     *     stream.filter(u -> u.getAge() > 18).forEach(this::processUser);
     * });
     * }</pre>
     *
     * @param entityClass 实体类
     * @param spec 查询规范
     * @param consumer Stream 消费者（在 try-with-resources 中执行）
     * @param <T> 实体类型
     * @throws IllegalArgumentException 如果任何参数为 null
     */
    @Transactional(readOnly = true)
    public <T> void findAllStream(Class<T> entityClass, QuerySpec<T> spec, Consumer<Stream<T>> consumer) {
        if (entityClass == null) {
            throw new IllegalArgumentException("entityClass must not be null");
        }
        if (spec == null) {
            throw new IllegalArgumentException("spec must not be null");
        }
        if (consumer == null) {
            throw new IllegalArgumentException("consumer must not be null");
        }
        try (Stream<T> stream = doFindStream(entityClass, spec, null)) {
            consumer.accept(stream);
        }
    }

    /**
     * 安全版本的流式查询，支持 EntityGraph 急切加载。
     *
     * <p>
     * 与 {@link #findAllStream(Class, QuerySpec, Consumer)} 相同，但额外支持通过 {@link EntityGraphHelper} 指定急切加载的关联关系。
     *
     * <pre>{@code
     * EntityGraphHelper<User> graph = EntityGraphHelper.forEntity(User.class).add("roles");
     * jpa.findAllStream(User.class, spec, graph, stream -> {
     *     stream.forEach(this::processUser);
     * });
     * }</pre>
     *
     * @param entityClass 实体类
     * @param spec 查询规范
     * @param entityGraph 实体图（可为 null，null 时不应用实体图）
     * @param consumer Stream 消费者（在 try-with-resources 中执行）
     * @param <T> 实体类型
     * @throws IllegalArgumentException 如果 entityClass、spec 或 consumer 为 null
     */
    @Transactional(readOnly = true)
    public <T> void findAllStream(Class<T> entityClass, QuerySpec<T> spec, EntityGraphHelper<T> entityGraph,
        Consumer<Stream<T>> consumer) {
        if (entityClass == null) {
            throw new IllegalArgumentException("entityClass must not be null");
        }
        if (spec == null) {
            throw new IllegalArgumentException("spec must not be null");
        }
        if (consumer == null) {
            throw new IllegalArgumentException("consumer must not be null");
        }
        try (Stream<T> stream = doFindStream(entityClass, spec, entityGraph)) {
            consumer.accept(stream);
        }
    }

    /**
     * 内部流式查询实现，供安全版本和 deprecated 版本共用。
     *
     * @param entityClass 实体类
     * @param spec 查询规范
     * @param entityGraph 实体图（可为 null）
     * @param <T> 实体类型
     * @return 匹配实体的 Stream（必须由调用方关闭）
     */
    private <T> Stream<T> doFindStream(Class<T> entityClass, QuerySpec<T> spec, EntityGraphHelper<T> entityGraph) {
        TypedQuery<T> query = buildTypedQuery(entityClass, spec, entityGraph, null);
        // P0: Set fetchSize for streaming queries. PostgreSQL requires fetchSize > 0 for true streaming;
        // MySQL uses Integer.MIN_VALUE for streaming mode; other databases use default.
        int fetchSize = determineFetchSize();
        if (fetchSize != 0) {
            query.setHint("jakarta.persistence.query.fetchSize", fetchSize);
        }
        return query.getResultStream();
    }

    /**
     * Determine appropriate fetchSize for streaming queries based on database dialect.
     *
     * <p>
     * PostgreSQL requires fetchSize > 0 to enable server-side cursors for streaming. MySQL uses Integer.MIN_VALUE to
     * enable streaming mode. Other databases use default (no hint).
     *
     * @return fetchSize value, 0 means no hint will be set
     */
    private int determineFetchSize() {
        try {
            Object urlObj = entityManager.getEntityManagerFactory().getProperties().get("jakarta.persistence.jdbc.url");
            if (urlObj == null) {
                urlObj = entityManager.getEntityManagerFactory().getProperties().get("hibernate.connection.url");
            }
            if (urlObj != null) {
                String lower = urlObj.toString().toLowerCase();
                if (lower.contains("postgresql")) {
                    return 100;
                }
                if (lower.contains("mysql")) {
                    return Integer.MIN_VALUE;
                }
            }
        } catch (Exception e) {
            log.debug("Failed to determine fetchSize from JDBC URL: {}", e.getMessage());
        }
        return 0;
    }

    /**
     * 构建 TypedQuery 的公共方法，消除查询构建逻辑的重复。
     *
     * @param entityClass 实体类
     * @param spec 查询规范
     * @param entityGraph 用于急切加载的实体图（可为 null）
     * @param maxResults 最大结果数（null 表示不限制）
     * @param <T> 实体类型
     * @return 构建的 TypedQuery
     */
    private <T> TypedQuery<T> buildTypedQuery(Class<T> entityClass, QuerySpec<T> spec, EntityGraphHelper<T> entityGraph,
        Integer maxResults) {
        TypedQuery<T> query = buildSpecificationQuery(entityClass, spec.toSpecification(), null, maxResults);
        if (entityGraph != null) {
            entityGraph.apply(query, entityManager);
        }
        spec.applyQuerySettings(query);
        return query;
    }

    /**
     * 查找匹配给定 {@link Specification} 的单个实体。
     *
     * @param entityClass 实体类
     * @param spec 查询规范
     * @param <T> 实体类型
     * @return 匹配实体的 Optional 包装
     */
    @Transactional(readOnly = true)
    public <T> Optional<T> findOne(Class<T> entityClass, Specification<T> spec) {
        if (entityClass == null) {
            throw new IllegalArgumentException("entityClass must not be null");
        }
        if (spec == null) {
            throw new IllegalArgumentException("spec must not be null");
        }
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<T> cq = cb.createQuery(entityClass);
        Root<T> root = cq.from(entityClass);
        jakarta.persistence.criteria.Predicate predicate = spec.toPredicate(root, cq, cb);
        if (predicate != null) {
            cq.where(predicate);
        }
        TypedQuery<T> query = entityManager.createQuery(cq);
        query.setMaxResults(1);
        List<T> results = query.getResultList();
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    /**
     * 查找匹配给定 {@link Specification} 的所有实体。
     *
     * @param entityClass 实体类
     * @param spec 查询规范
     * @param <T> 实体类型
     * @return 匹配实体列表（限制为最大行数）
     */
    @Transactional(readOnly = true)
    public <T> List<T> find(Class<T> entityClass, Specification<T> spec) {
        if (entityClass == null) {
            throw new IllegalArgumentException("entityClass must not be null");
        }
        if (spec == null) {
            throw new IllegalArgumentException("spec must not be null");
        }
        return find(entityClass, spec, this.maxResults);
    }

    /**
     * 查找匹配给定 {@link Specification} 的所有实体，支持自定义最大行数限制。
     *
     * @param entityClass 实体类
     * @param spec 查询规范
     * @param maxResults 返回的最大结果数
     * @param <T> 实体类型
     * @return 匹配实体列表
     */
    @Transactional(readOnly = true)
    public <T> List<T> find(Class<T> entityClass, Specification<T> spec, int maxResults) {
        if (entityClass == null) {
            throw new IllegalArgumentException("entityClass must not be null");
        }
        if (spec == null) {
            throw new IllegalArgumentException("spec must not be null");
        }
        if (maxResults <= 0) {
            throw new IllegalArgumentException("maxResults must be positive");
        }
        TypedQuery<T> query = buildSpecificationQuery(entityClass, spec, null, maxResults);
        return query.getResultList();
    }

    /**
     * 构建基于 Specification 的 TypedQuery 的公共方法。
     *
     * @param entityClass 实体类
     * @param spec 查询规范
     * @param sort 排序规则（可为 null）
     * @param maxResults 最大结果数（null 表示不限制）
     * @param <T> 实体类型
     * @return 构建的 TypedQuery
     */
    private <T> TypedQuery<T> buildSpecificationQuery(Class<T> entityClass, Specification<T> spec,
        @Nullable org.springframework.data.domain.Sort sort, Integer maxResults) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<T> cq = cb.createQuery(entityClass);
        Root<T> root = cq.from(entityClass);
        jakarta.persistence.criteria.Predicate predicate = spec.toPredicate(root, cq, cb);
        if (predicate != null) {
            cq.where(predicate);
        }
        applySort(cq, root, cb, sort);
        TypedQuery<T> query = entityManager.createQuery(cq);
        if (maxResults != null) {
            query.setMaxResults(maxResults);
        }
        // P1-15: Apply default query timeout if configured
        if (defaultTimeoutSeconds > 0) {
            query.setHint("jakarta.persistence.query.timeout", defaultTimeoutSeconds * 1000);
        }
        return query;
    }

    /**
     * 将 Spring Data {@link org.springframework.data.domain.Sort} 应用到 JPA CriteriaQuery。
     *
     * @param query CriteriaQuery 实例
     * @param root 查询根实体
     * @param cb CriteriaBuilder 实例
     * @param sort 排序规则（可为 null 或未排序）
     * @param <T> 实体类型
     */
    private <T> void applySort(CriteriaQuery<T> query, Root<T> root, CriteriaBuilder cb,
        @Nullable org.springframework.data.domain.Sort sort) {
        if (sort != null && sort.isSorted()) {
            query.orderBy(sort.stream().map(order -> order.isAscending() ? cb.asc(root.get(order.getProperty()))
                : cb.desc(root.get(order.getProperty()))).toList());
        }
    }

    /**
     * 分页查找匹配给定 {@link QuerySpec} 的实体。
     *
     * @param entityClass 实体类
     * @param spec 查询规范
     * @param pageable 分页信息
     * @param <T> 实体类型
     * @return 匹配实体的分页结果
     */
    @Transactional(readOnly = true)
    public <T> Page<T> findAll(Class<T> entityClass, QuerySpec<T> spec, Pageable pageable) {
        if (entityClass == null) {
            throw new IllegalArgumentException("entityClass must not be null");
        }
        if (spec == null) {
            throw new IllegalArgumentException("spec must not be null");
        }
        if (pageable == null) {
            throw new IllegalArgumentException("pageable must not be null");
        }
        return findPageInternal(entityClass, spec.toSpecification(), pageable, spec);
    }

    private <T> Page<T> findPageInternal(Class<T> entityClass, Specification<T> spec, Pageable pageable,
        QuerySpec<T> querySpec) {
        return doFindPage(entityClass, spec, pageable, querySpec);
    }

    /**
     * 执行计数查询的公共方法。
     *
     * @param entityClass 实体类
     * @param spec 查询规范
     * @param cb CriteriaBuilder
     * @param <T> 实体类型
     * @return 总记录数
     */
    private <T> long executeCountQuery(Class<T> entityClass, Specification<T> spec, CriteriaBuilder cb) {
        CriteriaQuery<Long> countCq = cb.createQuery(Long.class);
        Root<T> countRoot = countCq.from(entityClass);
        countCq.select(cb.count(countRoot));
        jakarta.persistence.criteria.Predicate countPredicate = spec.toPredicate(countRoot, countCq, cb);
        if (countPredicate != null) {
            countCq.where(countPredicate);
        }
        return entityManager.createQuery(countCq).getSingleResult();
    }

    /**
     * 分页查找匹配给定 {@link Specification} 的实体。
     *
     * @param entityClass 实体类
     * @param spec 查询规范
     * @param pageable 分页信息
     * @param <T> 实体类型
     * @return 匹配实体的分页结果
     */
    @Transactional(readOnly = true)
    public <T> Page<T> findPage(Class<T> entityClass, Specification<T> spec, Pageable pageable) {
        if (entityClass == null) {
            throw new IllegalArgumentException("entityClass must not be null");
        }
        if (spec == null) {
            throw new IllegalArgumentException("spec must not be null");
        }
        if (pageable == null) {
            throw new IllegalArgumentException("pageable must not be null");
        }
        return doFindPage(entityClass, spec, pageable, null);
    }

    /**
     * 公共分页逻辑，消除 findPageInternal 和 findPage 的代码重复。
     *
     * @param entityClass 实体类
     * @param spec 查询规范
     * @param pageable 分页信息
     * @param querySpec QuerySpec 实例（用于应用查询设置，可为 null）
     * @param <T> 实体类型
     * @return 匹配实体的分页结果
     */
    private <T> Page<T> doFindPage(Class<T> entityClass, Specification<T> spec, Pageable pageable,
        @Nullable QuerySpec<T> querySpec) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();

        if (pageable.isUnpaged()) {
            log.debug(
                "Pageable.unpaged() used with pagination method - applying maxResults limit ({}). "
                    + "Consider using findAll() with explicit maxResults or findAllStream() for large datasets.",
                this.maxResults);
            TypedQuery<T> typedQuery = buildSpecificationQuery(entityClass, spec, null, this.maxResults);
            if (querySpec != null) {
                querySpec.applyQuerySettings(typedQuery);
            }
            List<T> allContent = typedQuery.getResultList();
            return new PageImpl<>(allContent);
        }

        // 深度分页警告（限流：每分钟最多记录一次）
        if (pageable.getOffset() > this.deepPaginationOffsetThreshold) {
            long now = System.currentTimeMillis();
            long lastWarn = lastDeepPaginationWarnTime.get();
            if (now - lastWarn > DEEP_PAGINATION_WARN_INTERVAL_MS
                && lastDeepPaginationWarnTime.compareAndSet(lastWarn, now)) {
                log.warn("Deep pagination detected (offset={}). This may cause slow queries. "
                    + "Consider using keyset pagination for better performance.", pageable.getOffset());
            }
        }

        // 深度分页硬限制
        if (this.deepPaginationOffsetLimit > 0 && pageable.getOffset() > this.deepPaginationOffsetLimit) {
            throw new IllegalArgumentException("Pagination offset (" + pageable.getOffset()
                + ") exceeds the configured hard limit (" + this.deepPaginationOffsetLimit
                + "). Use keyset pagination for better performance, or adjust myjpa-plus.query.deep-pagination-offset-limit.");
        }

        // 计数查询
        long total = executeCountQuery(entityClass, spec, cb);

        // 数据查询 - 复用 buildSpecificationQuery 避免重复的查询构建逻辑
        TypedQuery<T> query = buildSpecificationQuery(entityClass, spec, pageable.getSort(), pageable.getPageSize());
        try {
            query.setFirstResult(Math.toIntExact(pageable.getOffset()));
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("Page offset (" + pageable.getOffset() + ") exceeds Integer.MAX_VALUE. "
                + "JPA setFirstResult() does not support offsets larger than Integer.MAX_VALUE.", e);
        }
        if (querySpec != null) {
            querySpec.applyQuerySettings(query);
        }
        List<T> content = query.getResultList();

        return new PageImpl<>(content, pageable, total);
    }

    /**
     * 使用给定的 {@link UpdateSpec} 执行批量更新。
     *
     * @param spec 要执行的 UpdateSpec
     * @param <T> 实体类型
     * @return 受影响的行数
     */
    @Transactional(rollbackFor = Exception.class)
    public <T> int execute(UpdateSpec<T> spec) {
        if (spec == null) {
            throw new IllegalArgumentException("spec must not be null");
        }
        return spec.executeInTransaction(entityManager);
    }

    /**
     * 使用给定的 {@link DeleteSpec} 执行批量删除。
     *
     * @param spec 要执行的 DeleteSpec
     * @param <T> 实体类型
     * @return 受影响的行数
     */
    @Transactional(rollbackFor = Exception.class)
    public <T> int execute(DeleteSpec<T> spec) {
        if (spec == null) {
            throw new IllegalArgumentException("spec must not be null");
        }
        return spec.executeInTransaction(entityManager);
    }

    /**
     * 使用给定的 {@link com.zsubera.jpa.update.MergeSpec} 执行 UPSERT 操作。
     *
     * @param spec 要执行的 MergeSpec
     * @param <T> 实体类型
     * @return 受影响的行数
     * @throws IllegalArgumentException 如果 spec 为 null
     */
    @Transactional(rollbackFor = Exception.class)
    public <T> int execute(com.zsubera.jpa.update.MergeSpec<T> spec) {
        if (spec == null) {
            throw new IllegalArgumentException("spec must not be null");
        }
        return spec.executeInTransaction(entityManager);
    }

    /**
     * P2-6: 批量执行 UPSERT 操作，使用 EntityManager flush/clear 进行分批处理。
     *
     * <p>
     * 此方法在单个事务中执行所有批次，通过定期 flush 和 clear EntityManager 减少内存占用。
     *
     * @param mergeSpec MergeSpec 实例（已配置冲突列和更新列）
     * @param entities 要 UPSERT 的实体列表
     * @param batchSize 每批大小，建议值为 50-200
     * @param <T> 实体类型
     * @return 受影响的总行数
     * @throws IllegalArgumentException 如果任何参数为 null 或 batchSize 不是正数
     */
    @Transactional(rollbackFor = Exception.class)
    public <T> int executeBatch(com.zsubera.jpa.update.MergeSpec<T> mergeSpec, List<T> entities, int batchSize) {
        if (mergeSpec == null) {
            throw new IllegalArgumentException("mergeSpec must not be null");
        }
        if (entities == null || entities.isEmpty()) {
            throw new IllegalArgumentException("entities must not be null or empty");
        }
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be positive");
        }
        return mergeSpec.executeBatch(entities, entityManager, batchSize);
    }

    /**
     * 使用给定的 {@link UpdateSpec} 执行批量更新，限制最大影响行数。
     *
     * <p>
     * 如果配置了 {@link #maxBulkOperationRows} 且大于 0，则使用 {@link UpdateSpec#executeLimited} 限制影响行数。 否则，直接执行
     * {@link #execute(UpdateSpec)}。
     *
     * @param spec 要执行的 UpdateSpec
     * @param maxRows 最大影响行数，如果为 -1 则使用全局配置
     * @param <T> 实体类型
     * @return 受影响的行数
     * @throws IllegalArgumentException 如果 maxRows 不是正数且不等于 -1
     */
    @Transactional(rollbackFor = Exception.class)
    public <T> int executeWithMaxRows(UpdateSpec<T> spec, int maxRows) {
        if (spec == null) {
            throw new IllegalArgumentException("spec must not be null");
        }
        if (maxRows <= 0 && maxRows != -1) {
            throw new IllegalArgumentException("maxRows must be positive or -1 (use global config)");
        }
        int effectiveLimit = maxRows == -1 ? maxBulkOperationRows : maxRows;
        if (effectiveLimit <= 0) {
            return spec.executeInTransaction(entityManager);
        }
        return spec.executeLimited(entityManager, effectiveLimit);
    }

    /**
     * 使用给定的 {@link DeleteSpec} 执行批量删除，限制最大影响行数。
     *
     * <p>
     * 如果配置了 {@link #maxBulkOperationRows} 且大于 0，则使用 {@link DeleteSpec#executeLimited} 限制影响行数。 否则，直接执行
     * {@link #execute(DeleteSpec)}。
     *
     * @param spec 要执行的 DeleteSpec
     * @param maxRows 最大影响行数，如果为 -1 则使用全局配置
     * @param <T> 实体类型
     * @return 受影响的行数
     * @throws IllegalArgumentException 如果 maxRows 不是正数且不等于 -1
     */
    @Transactional(rollbackFor = Exception.class)
    public <T> int executeWithMaxRows(DeleteSpec<T> spec, int maxRows) {
        if (spec == null) {
            throw new IllegalArgumentException("spec must not be null");
        }
        if (maxRows <= 0 && maxRows != -1) {
            throw new IllegalArgumentException("maxRows must be positive or -1 (use global config)");
        }
        int effectiveLimit = maxRows == -1 ? maxBulkOperationRows : maxRows;
        if (effectiveLimit <= 0) {
            return spec.executeInTransaction(entityManager);
        }
        return spec.executeLimited(entityManager, effectiveLimit);
    }

    /**
     * 分批执行批量更新。通过分批处理减少内存占用（通过 {@link EntityManager#clear()} 清除一级缓存）， 但所有批次在同一个事务中执行，要么全部成功，要么全部回滚。
     *
     * <p>
     * <strong>注意：</strong>此方法不会分批提交事务。所有批次在同一个事务中执行， 只有在整个方法完成后才会提交。如果需要分批提交，请使用外部事务管理。
     *
     * <p>
     * <strong>长事务风险：</strong>如果数据量非常大，事务日志可能撑爆，导致数据库锁等待超时。 对于大数据量操作，建议：
     * <ul>
     * <li>使用较小的 batchSize（如 1000-5000）</li>
     * <li><strong>推荐：</strong>使用 {@link #executeBatchInSeparateTransactions(UpdateSpec, int)} 进行分批提交，避免长事务</li>
     * <li>监控数据库事务日志使用情况</li>
     * </ul>
     *
     * @param spec 要执行的 UpdateSpec
     * @param batchSize 每批更新的行数
     * @param <T> 实体类型
     * @return 受影响的总行数
     */
    @Transactional(rollbackFor = Exception.class)
    public <T> int executeBatch(UpdateSpec<T> spec, int batchSize) {
        if (spec == null) {
            throw new IllegalArgumentException("spec must not be null");
        }
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be positive");
        }
        return executeBatchInternal(batchSize, "update", size -> spec.executeLimited(entityManager, size));
    }

    /**
     * 分批执行批量删除。通过分批处理减少内存占用（通过 {@link EntityManager#clear()} 清除一级缓存）， 但所有批次在同一个事务中执行，要么全部成功，要么全部回滚。
     *
     * <p>
     * <strong>注意：</strong>此方法不会分批提交事务。所有批次在同一个事务中执行， 只有在整个方法完成后才会提交。如果需要分批提交，请使用外部事务管理。
     *
     * <p>
     * <strong>长事务风险：</strong>如果数据量非常大，事务日志可能撑爆，导致数据库锁等待超时。 对于大数据量操作，建议：
     * <ul>
     * <li>使用较小的 batchSize（如 1000-5000）</li>
     * <li><strong>推荐：</strong>使用 {@link #executeBatchInSeparateTransactions(DeleteSpec, int)} 进行分批提交，避免长事务</li>
     * <li>监控数据库事务日志使用情况</li>
     * </ul>
     *
     * @param spec 要执行的 DeleteSpec
     * @param batchSize 每批删除的行数
     * @param <T> 实体类型
     * @return 受影响的总行数
     */
    @Transactional(rollbackFor = Exception.class)
    public <T> int executeBatch(DeleteSpec<T> spec, int batchSize) {
        if (spec == null) {
            throw new IllegalArgumentException("spec must not be null");
        }
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be positive");
        }
        return executeBatchInternal(batchSize, "delete", size -> spec.executeLimited(entityManager, size));
    }

    /**
     * 分批执行操作的通用实现。
     *
     * @param batchSize 每批操作的行数
     * @param operationName 操作名称（用于日志）
     * @param batchExecutor 批次执行器，接收 batchSize 返回受影响行数
     * @return 受影响的总行数
     */
    private int executeBatchInternal(int batchSize, String operationName,
        java.util.function.IntUnaryOperator batchExecutor) {
        int total = 0;
        int batchResult;
        int iteration = 0;
        do {
            batchResult = batchExecutor.applyAsInt(batchSize);
            total += batchResult;
            if (batchResult > 0) {
                entityManager.flush();
                entityManager.clear();
                if (log.isDebugEnabled()) {
                    log.debug("Batch {}: {} rows {}ed in this batch (total: {})", operationName, batchResult,
                        operationName, total);
                }
            }
            iteration++;
            if (iteration >= MAX_BATCH_ITERATIONS) {
                log.error("Batch {} reached maximum iterations ({}). Possible infinite loop. Total rows: {}",
                    operationName, MAX_BATCH_ITERATIONS, total);
                break;
            }
        } while (batchResult >= batchSize);
        return total;
    }

    // ---- 分批提交事务的批量操作 ----

    /**
     * 批量操作的执行结果记录。
     *
     * @param totalRows 受影响的总行数
     * @param batchCount 执行的批次数
     * @param success 是否全部成功
     * @param failedBatchIndex 失败的批次索引（从 0 开始），如果全部成功则为 -1
     * @param failureCause 失败原因，如果全部成功则为 null
     */
    @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(value = {"EI_EXPOSE_REP", "EI_EXPOSE_REP2"},
        justification = "Record components are inherently exposed; failureCause is intentionally part of the result")
    public record BatchResult(int totalRows, int batchCount, boolean success, int failedBatchIndex,
        Throwable failureCause) {
    }

    /**
     * 批次执行失败时的处理策略。
     */
    public enum BatchFailureStrategy {
        /** 继续执行剩余批次（默认）。 */
        CONTINUE,
        /** 立即中止，已提交的批次不会回滚。 */
        ABORT,
    }

    /**
     * 分批执行批量更新，每批在独立事务中提交，支持失败回调。
     *
     * <p>
     * 与 {@link #executeBatch(UpdateSpec, int)} 不同，此方法每批操作完成后立即提交事务， 避免长事务导致的数据库锁等待超时问题。适用于大数据量操作场景。
     *
     * <p>
     * <strong>注意：</strong>如果某批操作失败，已提交的批次不会回滚。调用方可通过 {@link BatchResult} 获取执行状态。
     *
     * @param spec 要执行的 UpdateSpec
     * @param batchSize 每批更新的行数
     * @param failureStrategy 失败时的处理策略
     * @param <T> 实体类型
     * @return 批量执行结果
     */
    public <T> BatchResult executeBatchInSeparateTransactions(UpdateSpec<T> spec, int batchSize,
        BatchFailureStrategy failureStrategy) {
        if (spec == null) {
            throw new IllegalArgumentException("spec must not be null");
        }
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be positive");
        }
        if (failureStrategy == null) {
            throw new IllegalArgumentException("failureStrategy must not be null");
        }
        return executeBatchInSeparateTransactionsWithResult(batchSize, "update",
            size -> executeInNewTransaction(em -> spec.executeLimited(em, size)), failureStrategy);
    }

    /**
     * 分批执行批量删除，每批在独立事务中提交，支持失败回调。
     *
     * @param spec 要执行的 DeleteSpec
     * @param batchSize 每批删除的行数
     * @param failureStrategy 失败时的处理策略
     * @param <T> 实体类型
     * @return 批量执行结果
     */
    public <T> BatchResult executeBatchInSeparateTransactions(DeleteSpec<T> spec, int batchSize,
        BatchFailureStrategy failureStrategy) {
        if (spec == null) {
            throw new IllegalArgumentException("spec must not be null");
        }
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be positive");
        }
        if (failureStrategy == null) {
            throw new IllegalArgumentException("failureStrategy must not be null");
        }
        return executeBatchInSeparateTransactionsWithResult(batchSize, "delete",
            size -> executeInNewTransaction(em -> spec.executeLimited(em, size)), failureStrategy);
    }

    /**
     * 分批在独立事务中执行操作的通用实现，返回详细结果。
     *
     * @param batchSize 每批操作的行数
     * @param operationName 操作名称（用于日志）
     * @param batchExecutor 批次执行器
     * @param failureStrategy 失败时的处理策略
     * @return 批量执行结果
     */
    private BatchResult executeBatchInSeparateTransactionsWithResult(int batchSize, String operationName,
        java.util.function.IntUnaryOperator batchExecutor, BatchFailureStrategy failureStrategy) {
        int total = 0;
        int batchCount = 0;
        int failedBatchIndex = -1;
        Throwable failureCause = null;
        boolean shouldContinue = true;
        int batchResult;
        while (shouldContinue) {
            try {
                batchResult = batchExecutor.applyAsInt(batchSize);
                total += batchResult;
                batchCount++;
                if (batchResult > 0 && log.isDebugEnabled()) {
                    log.debug("Batch {} committed: {} rows {}ed in this batch (total: {})", operationName, batchResult,
                        operationName, total);
                }
            } catch (RuntimeException e) {
                failedBatchIndex = batchCount;
                failureCause = e;
                batchCount++;
                log.error("Batch {} failed at batch index {}: {}", operationName, failedBatchIndex, e.getMessage(), e);
                if (failureStrategy == BatchFailureStrategy.ABORT) {
                    shouldContinue = false;
                    continue;
                }
                // CONTINUE: skip this batch and try next
                batchResult = 0;
            }
            if (batchResult < batchSize) {
                shouldContinue = false;
            }
        }
        return new BatchResult(total, batchCount, failedBatchIndex == -1, failedBatchIndex, failureCause);
    }

    /**
     * 分批执行批量更新，每批在独立事务中提交。
     *
     * <p>
     * 与 {@link #executeBatch(UpdateSpec, int)} 不同，此方法每批操作完成后立即提交事务， 避免长事务导致的数据库锁等待超时问题。适用于大数据量操作场景。
     *
     * <p>
     * <strong>注意：</strong>如果某批操作失败，已提交的批次不会回滚。调用方需要自行处理部分成功的情况。
     *
     * @param spec 要执行的 UpdateSpec
     * @param batchSize 每批更新的行数
     * @param <T> 实体类型
     * @return 受影响的总行数
     */
    public <T> int executeBatchInSeparateTransactions(UpdateSpec<T> spec, int batchSize) {
        if (spec == null) {
            throw new IllegalArgumentException("spec must not be null");
        }
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be positive");
        }
        return executeBatchInSeparateTransactionsInternal(batchSize, "update",
            size -> executeInNewTransaction(em -> spec.executeLimited(em, size)));
    }

    /**
     * 分批执行批量删除，每批在独立事务中提交。
     *
     * <p>
     * 与 {@link #executeBatch(DeleteSpec, int)} 不同，此方法每批操作完成后立即提交事务， 避免长事务导致的数据库锁等待超时问题。适用于大数据量操作场景。
     *
     * <p>
     * <strong>注意：</strong>如果某批操作失败，已提交的批次不会回滚。调用方需要自行处理部分成功的情况。
     *
     * @param spec 要执行的 DeleteSpec
     * @param batchSize 每批删除的行数
     * @param <T> 实体类型
     * @return 受影响的总行数
     */
    public <T> int executeBatchInSeparateTransactions(DeleteSpec<T> spec, int batchSize) {
        if (spec == null) {
            throw new IllegalArgumentException("spec must not be null");
        }
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be positive");
        }
        return executeBatchInSeparateTransactionsInternal(batchSize, "delete",
            size -> executeInNewTransaction(em -> spec.executeLimited(em, size)));
    }

    /**
     * 分批在独立事务中执行操作的通用实现。
     *
     * @param batchSize 每批操作的行数
     * @param operationName 操作名称（用于日志）
     * @param batchExecutor 批次执行器
     * @return 受影响的总行数
     */
    private int executeBatchInSeparateTransactionsInternal(int batchSize, String operationName,
        java.util.function.IntUnaryOperator batchExecutor) {
        int total = 0;
        int batchResult;
        int iteration = 0;
        do {
            batchResult = batchExecutor.applyAsInt(batchSize);
            total += batchResult;
            if (batchResult > 0 && log.isDebugEnabled()) {
                log.debug("Batch {} committed: {} rows {}ed in this batch (total: {})", operationName, batchResult,
                    operationName, total);
            }
            iteration++;
            if (iteration >= MAX_BATCH_ITERATIONS) {
                log.error("Batch {} reached maximum iterations ({}). Possible infinite loop. Total rows: {}",
                    operationName, MAX_BATCH_ITERATIONS, total);
                break;
            }
        } while (batchResult >= batchSize);
        return total;
    }

    /**
     * 在新事务中执行操作。
     *
     * <p>
     * 使用 {@link org.springframework.transaction.support.TransactionTemplate} 创建独立事务， 每次调用都会创建新的事务上下文。 需要注入
     * {@link org.springframework.transaction.PlatformTransactionManager}。
     *
     * <p>
     * <strong>P1 事务传播行为说明：</strong>
     * <ul>
     * <li>当已有活动事务时，使用 {@code PROPAGATION_REQUIRED}（加入现有事务），所有批次在同一事务中执行。 这意味着如果某个批次失败，所有批次都会回滚。</li>
     * <li>当没有活动事务时，使用 {@code PROPAGATION_REQUIRES_NEW}（创建新事务），每个批次在独立事务中执行。</li>
     * <li>如果需要严格的批次隔离，请确保在没有外层事务的情况下调用此方法。</li>
     * </ul>
     *
     * @param operation 要执行的操作
     * @param <R> 返回类型
     * @return 操作结果
     * @throws IllegalStateException 如果 TransactionManager 不可用
     */
    private <R> R executeInNewTransaction(java.util.function.Function<EntityManager, R> operation) {
        org.springframework.transaction.PlatformTransactionManager txManager = getTransactionManager();
        if (txManager == null) {
            throw new IllegalStateException(
                "PlatformTransactionManager not available. Cannot execute in new transaction. "
                    + "Ensure @Transactional support is enabled or configure a PlatformTransactionManager bean. "
                    + "If running outside a Spring context, use MergeSpec.executeInTransaction() instead.");
        }
        org.springframework.transaction.support.TransactionTemplate txTemplate =
            new org.springframework.transaction.support.TransactionTemplate(txManager);
        // B-02: Use REQUIRES_NEW when possible to ensure each batch runs in an independent transaction.
        // When already in a transaction, REQUIRES_NEW suspends it and creates a new one.
        // However, in environments with limited connections (e.g., test H2), suspension may fail
        // or the new transaction may not see uncommitted data from the outer transaction.
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()) {
            // Already in a transaction: use REQUIRED to join it.
            // Batches execute within the caller's transaction but still flush/clear for memory management.
            txTemplate
                .setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRED);
        } else {
            // No active transaction: use REQUIRES_NEW for true isolation.
            txTemplate
                .setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        }
        return txTemplate.execute(status -> operation.apply(entityManager));
    }

    /**
     * 获取 PlatformTransactionManager。
     *
     * <p>
     * 通过 ApplicationContext 获取，如果不存在则返回 null。
     *
     * @return PlatformTransactionManager 实例，或 null
     */
    private org.springframework.transaction.PlatformTransactionManager getTransactionManager() {
        if (applicationContext == null) {
            log.debug("ApplicationContext not available, cannot resolve TransactionManager");
            return null;
        }
        try {
            return applicationContext.getBean(org.springframework.transaction.PlatformTransactionManager.class);
        } catch (org.springframework.beans.factory.NoSuchBeanDefinitionException e) {
            log.debug("PlatformTransactionManager bean not found: {}", e.getMessage());
            return null;
        } catch (org.springframework.beans.BeansException e) {
            log.debug("Failed to resolve TransactionManager: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 分页查找匹配给定 {@link Specification} 的实体，不执行 count 查询。 使用多查一条记录的方式判断是否有下一页，适用于不需要总记录数的场景。
     *
     * @param entityClass 实体类
     * @param spec 查询规范
     * @param pageable 分页信息
     * @param <T> 实体类型
     * @return 匹配实体的 Slice 结果（无 count 查询）
     * @throws IllegalArgumentException 如果任何参数为 null
     */
    @Transactional(readOnly = true)
    public <T> org.springframework.data.domain.Slice<T> findSlice(Class<T> entityClass, Specification<T> spec,
        Pageable pageable) {
        if (entityClass == null) {
            throw new IllegalArgumentException("entityClass must not be null");
        }
        if (spec == null) {
            throw new IllegalArgumentException("spec must not be null");
        }
        if (pageable == null) {
            throw new IllegalArgumentException("pageable must not be null");
        }
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<T> cq = cb.createQuery(entityClass);
        Root<T> root = cq.from(entityClass);
        jakarta.persistence.criteria.Predicate predicate = spec.toPredicate(root, cq, cb);
        if (predicate != null) {
            cq.where(predicate);
        }
        applySort(cq, root, cb, pageable.getSort());
        TypedQuery<T> query = entityManager.createQuery(cq);
        try {
            query.setFirstResult(Math.toIntExact(pageable.getOffset()));
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("Page offset exceeds Integer.MAX_VALUE.", e);
        }
        query.setMaxResults(pageable.getPageSize() + 1);
        List<T> content = query.getResultList();
        boolean hasNext = content.size() > pageable.getPageSize();
        if (hasNext) {
            content = content.subList(0, pageable.getPageSize());
        }
        return new org.springframework.data.domain.SliceImpl<>(content, pageable, hasNext);
    }

    /**
     * 根据 ID 集合批量查找实体。
     *
     * <p>
     * 使用 {@link com.zsubera.jpa.util.InClauseBuilder} 自动处理大型 IN 子句的分批， 避免超出数据库的参数数量限制（Oracle: 1000, SQL Server: 2100）。
     *
     * @param entityClass 实体类
     * @param ids ID 集合
     * @param <T> 实体类型
     * @param <ID> ID 类型
     * @return 匹配实体列表
     * @throws IllegalArgumentException 如果任何参数为 null 或 ids 为空
     */
    @Transactional(readOnly = true)
    public <T, ID> List<T> findAllById(Class<T> entityClass, Collection<ID> ids) {
        if (entityClass == null) {
            throw new IllegalArgumentException("entityClass must not be null");
        }
        if (ids == null || ids.isEmpty()) {
            throw new IllegalArgumentException("ids must not be null or empty");
        }
        String idFieldName = EntityClassResolver.resolveIdFieldName(entityClass);
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<T> cq = cb.createQuery(entityClass);
        Root<T> root = cq.from(entityClass);
        // P2: Pass ids Collection directly to avoid unnecessary toArray() conversion
        cq.where(com.zsubera.jpa.util.InClauseBuilder.in(cb, root.get(idFieldName), ids));
        return entityManager.createQuery(cq).getResultList();
    }

    /**
     * 根据 ID 集合批量查找未被软删除的实体。
     *
     * <p>
     * 使用 {@link com.zsubera.jpa.util.InClauseBuilder} 自动处理大型 IN 子句的分批， 避免超出数据库的参数数量限制。
     *
     * @param entityClass 实体类
     * @param ids ID 集合
     * @param <T> 实体类型
     * @param <ID> ID 类型
     * @return 匹配的未删除实体列表
     * @throws IllegalArgumentException 如果任何参数为 null 或 ids 为空
     */
    @Transactional(readOnly = true)
    public <T, ID> List<T> findNotDeletedAllById(Class<T> entityClass, Collection<ID> ids) {
        if (entityClass == null) {
            throw new IllegalArgumentException("entityClass must not be null");
        }
        if (ids == null || ids.isEmpty()) {
            throw new IllegalArgumentException("ids must not be null or empty");
        }
        String idFieldName = EntityClassResolver.resolveIdFieldName(entityClass);
        // P2: Pass ids Collection directly to avoid unnecessary toArray() conversion
        Specification<T> idSpec =
            (root, query, cb) -> com.zsubera.jpa.util.InClauseBuilder.in(cb, root.get(idFieldName), ids);
        Specification<T> softDeleteSpec = com.zsubera.jpa.update.SoftDeleteHelper.isNotDeleted(entityClass);
        Specification<T> combinedSpec = idSpec.and(softDeleteSpec);
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<T> cq = cb.createQuery(entityClass);
        Root<T> root = cq.from(entityClass);
        jakarta.persistence.criteria.Predicate predicate = combinedSpec.toPredicate(root, cq, cb);
        if (predicate != null) {
            cq.where(predicate);
        }
        return entityManager.createQuery(cq).getResultList();
    }
}
