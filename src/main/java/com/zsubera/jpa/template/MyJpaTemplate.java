package com.zsubera.jpa.template;

import com.zsubera.jpa.util.EntityClassResolver;
import com.zsubera.jpa.spec.QuerySpec;
import com.zsubera.jpa.update.DeleteSpec;
import com.zsubera.jpa.update.UpdateSpec;
import com.zsubera.jpa.util.EntityGraphHelper;
import com.zsubera.jpa.util.QueryTimeoutHelper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;
import jakarta.annotation.PostConstruct;
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

    /** 上次记录深度分页警告的时间戳。 */
    private final java.util.concurrent.atomic.AtomicLong lastDeepPaginationWarnTime =
        new java.util.concurrent.atomic.AtomicLong(0);

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired(required = false)
    private EntityManagerFactory entityManagerFactory;

    @Autowired(required = false)
    private ApplicationContext applicationContext;

    private volatile int maxResults = DEFAULT_MAX_RESULTS;
    private volatile int deepPaginationOffsetThreshold = DEFAULT_DEEP_PAGINATION_OFFSET_THRESHOLD;
    /** 深度分页硬限制。默认 1000000，{@code DISABLED} 表示禁用（仅记录警告）。 */
    private volatile int deepPaginationOffsetLimit = DEFAULT_DEEP_PAGINATION_OFFSET_LIMIT;
    /** 批量操作最大影响行数限制。默认 10000，{@code DISABLED} 表示不限制。 */
    private volatile int maxBulkOperationRows = DEFAULT_MAX_BULK_OPERATION_ROWS;

    /** 查询默认超时时间（秒）。设置后所有查询将自动应用此超时。默认 -1 表示不设置。 */
    private volatile int defaultTimeoutSeconds = -1;

    /** 可选的查询缓存管理器。注入后可使用 findAllCached() 方法。 */
    @Autowired(required = false)
    private QueryCacheManager cacheManager;

    /** 批量操作执行模板，封装 UpdateSpec/DeleteSpec/MergeSpec 的批量执行逻辑。 */
    private volatile BulkOperationTemplate bulkOperationTemplate;

    /** 批量保存操作模板，封装 persist/merge 的批量保存逻辑。 */
    private volatile BatchSaveTemplate batchSaveTemplate;

    /** Keyset 分页辅助类，封装游标分页的核心逻辑。 */
    private volatile KeysetPaginationHelper keysetPaginationHelper;

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
     * <p>
     * 设置为 {@code -1} 表示禁用限制（不限制返回行数）。 默认值为 {@value #DEFAULT_MAX_RESULTS}。
     *
     * @param maxResults 最大返回行数，或 {@code -1} 表示禁用
     * @throws IllegalArgumentException 如果值不是正数且不等于 -1
     */
    public void setMaxResults(int maxResults) {
        if (maxResults <= 0 && maxResults != -1) {
            throw new IllegalArgumentException("maxResults must be positive or -1 (disabled)");
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
        if (bulkOperationTemplate != null) {
            getBulkOperationTemplate().setMaxBulkOperationRows(maxBulkOperationRows);
        }
    }

    /**
     * 设置查询默认超时时间（秒）。设置后所有查询将自动应用此超时。
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
        // 转换为毫秒时防止溢出（int 最大值约 24.8 天）
        if (defaultTimeoutSeconds > Integer.MAX_VALUE / 1000) {
            throw new IllegalArgumentException("defaultTimeoutSeconds too large for millisecond conversion: "
                + defaultTimeoutSeconds + " (max " + (Integer.MAX_VALUE / 1000) + ")");
        }
        this.defaultTimeoutSeconds = defaultTimeoutSeconds;
        com.zsubera.jpa.util.QueryTimeoutHelper.setDefaultTimeoutSeconds(defaultTimeoutSeconds);
    }

    /**
     * 获取查询默认超时时间（秒）。
     *
     * @return 查询超时秒数，-1 表示不设置
     */
    public int getDefaultTimeoutSeconds() {
        return defaultTimeoutSeconds;
    }

    /**
     * 设置查询缓存管理器。注入后可使用 {@link #findAllCached} 方法。
     *
     * @param cacheManager 缓存管理器实例
     */
    public void setCacheManager(QueryCacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    /**
     * 获取查询缓存管理器。
     *
     * @return 缓存管理器实例，可能为 null
     */
    public QueryCacheManager getCacheManager() {
        return cacheManager;
    }

    /**
     * 初始化批量操作模板。在所有依赖注入完成后调用。
     */
    @PostConstruct
    private void initBulkOperationTemplate() {
        this.bulkOperationTemplate =
            new BulkOperationTemplate(entityManager, maxBulkOperationRows, entityManagerFactory, applicationContext);
        /* 事务工具类，用于在新事务中执行批量保存操作。 */
        TransactionHelper transactionHelper =
            new TransactionHelper(entityManager, entityManagerFactory, applicationContext);
        this.batchSaveTemplate = new BatchSaveTemplate(entityManager, transactionHelper);
        this.keysetPaginationHelper = new KeysetPaginationHelper(entityManager);
    }

    /**
     * 获取批量操作模板，确保已初始化。
     *
     * @return 批量操作模板
     * @throws IllegalStateException 如果 MyJpaTemplate 未完全初始化
     */
    private BulkOperationTemplate getBulkOperationTemplate() {
        if (bulkOperationTemplate == null) {
            throw new IllegalStateException(
                "MyJpaTemplate not fully initialized. This can happen if methods are called before "
                    + "Spring context refresh completes. Ensure all dependencies are injected and "
                    + "@PostConstruct has been invoked.");
        }
        return bulkOperationTemplate;
    }

    /**
     * 获取批量保存模板，确保已初始化。
     *
     * @return 批量保存模板
     * @throws IllegalStateException 如果 MyJpaTemplate 未完全初始化
     */
    private BatchSaveTemplate getBatchSaveTemplate() {
        if (batchSaveTemplate == null) {
            throw new IllegalStateException(
                "MyJpaTemplate not fully initialized. This can happen if methods are called before "
                    + "Spring context refresh completes. Ensure all dependencies are injected and "
                    + "@PostConstruct has been invoked.");
        }
        return batchSaveTemplate;
    }

    /**
     * 获取键集分页助手，确保已初始化。
     *
     * @return 键集分页助手
     * @throws IllegalStateException 如果 MyJpaTemplate 未完全初始化
     */
    private KeysetPaginationHelper getKeysetPaginationHelper() {
        if (keysetPaginationHelper == null) {
            throw new IllegalStateException(
                "MyJpaTemplate not fully initialized. This can happen if methods are called before "
                    + "Spring context refresh completes. Ensure all dependencies are injected and "
                    + "@PostConstruct has been invoked.");
        }
        return keysetPaginationHelper;
    }

    /**
     * 带缓存的查询方法。如果缓存命中则直接返回，否则执行查询并将结果缓存。
     *
     * <p>
     * 缓存键格式：{@code entityClassSimpleName:specHashCode}。如果需要更精确的缓存控制，请使用 {@link QueryCacheManager} 直接管理。
     *
     * @param entityClass 实体类
     * @param spec 查询规范
     * @param ttlSeconds 缓存过期时间（秒）
     * @param <T> 实体类型
     * @return 匹配实体列表
     * @throws IllegalStateException 如果未配置 QueryCacheManager
     * @throws IllegalArgumentException 如果参数为 null 或 ttlSeconds 为负数
     */
    @Transactional(readOnly = true)
    public <T> List<T> findAllCached(Class<T> entityClass, QuerySpec<T> spec, long ttlSeconds) {
        if (cacheManager == null) {
            throw new IllegalStateException("QueryCacheManager not configured. Call setCacheManager() first.");
        }
        if (entityClass == null) {
            throw new IllegalArgumentException("entityClass must not be null");
        }
        if (spec == null) {
            throw new IllegalArgumentException("spec must not be null");
        }
        if (ttlSeconds < 0) {
            throw new IllegalArgumentException("ttlSeconds must not be negative");
        }
        // 使用 cacheKey() 包含实际参数值，避免不同参数值的查询产生相同的缓存键
        String cacheKey = entityClass.getSimpleName() + "@" + spec.cacheKey() + "@" + spec.getSort();
        List<T> cached = cacheManager.get(cacheKey);
        if (cached != null) {
            log.debug("Cache hit for key: {}", cacheKey);
            return new ArrayList<>(cached);
        }
        log.debug("Cache miss for key: {}", cacheKey);
        List<T> result = findAll(entityClass, spec);
        List<T> immutableResult = Collections.unmodifiableList(new ArrayList<>(result));
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()) {
            org.springframework.transaction.support.TransactionSynchronizationManager
                .registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        cacheManager.put(cacheKey, immutableResult, ttlSeconds);
                        log.debug("Cache populated after transaction commit for key: {}", cacheKey);
                    }
                });
        } else {
            cacheManager.put(cacheKey, immutableResult, ttlSeconds);
        }
        return new ArrayList<>(result);
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
        return getBatchSaveTemplate().saveAllBatched(entities, batchSize);
    }

    /**
     * 纯 persist 批量保存实体，所有实体都使用 {@code persist()} 操作。
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
        return getBatchSaveTemplate().saveAllBatchedPure(entities, batchSize);
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
        return getBatchSaveTemplate().saveAllBatchedInSeparateTransactions(entities, batchSize);
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
        if (com.zsubera.jpa.softdelete.SoftDeleteHelper.findSoftDeleteField(entityClass) == null) {
            return Optional.ofNullable(entityManager.find(entityClass, id));
        }
        // 软删除场景：使用 Specification 查询以自动过滤已删除记录
        String idFieldName = EntityClassResolver.resolveIdFieldName(entityClass);
        Specification<T> idSpec = (root, query, cb) -> cb.equal(root.get(idFieldName), id);
        Specification<T> softDeleteSpec = com.zsubera.jpa.softdelete.SoftDeleteHelper.isNotDeleted(entityClass);
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
        QueryTimeoutHelper.applyTimeout(query);
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
        QueryTimeoutHelper.applyTimeout(query);
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
     * {@link #findAll(Class, QuerySpec, int)} 指定自定义限制，或使用 {@link #findAllStream(Class, QuerySpec, java.util.function.Consumer)} 进行无界流式查询。
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
        if (maxResults <= 0 && maxResults != -1) {
            throw new IllegalArgumentException("maxResults must be positive or -1 (disabled)");
        }
        Integer effectiveMaxResults = maxResults == -1 ? null : maxResults;
        TypedQuery<T> query = buildTypedQuery(entityClass, spec, null, effectiveMaxResults);
        return query.getResultList();
    }

    /**
     * 查找匹配给定 {@link QuerySpec} 的所有实体，支持自定义排序。
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
        if (maxResults <= 0 && maxResults != -1) {
            throw new IllegalArgumentException("maxResults must be positive or -1 (disabled)");
        }
        Integer effectiveMaxResults = maxResults == -1 ? null : maxResults;
        TypedQuery<T> query = buildTypedQuery(entityClass, spec, entityGraph, effectiveMaxResults);
        return query.getResultList();
    }

    /**
     * 安全版本的流式查询，自动管理 Stream 生命周期。
     *
     * <p>
     * 此方法在 try-with-resources 中执行 Stream，确保 Stream 被正确关闭，避免数据库连接泄漏。
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
     * 此方法保留用于向后兼容。在 2.0 版本中将抛出 UnsupportedOperationException。
     */
    private <T> Stream<T> doFindStream(Class<T> entityClass, QuerySpec<T> spec, EntityGraphHelper<T> entityGraph) {
        TypedQuery<T> query = buildTypedQuery(entityClass, spec, entityGraph, null);
        int fetchSize = com.zsubera.jpa.util.PageableHelper.determineFetchSize(entityManager);
        if (fetchSize != 0) {
            query.setHint("jakarta.persistence.query.fetchSize", fetchSize);
        }
        return query.getResultStream();
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
        QueryTimeoutHelper.applyTimeout(query);
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
        QueryTimeoutHelper.applyTimeout(query);
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
     * 检查深度分页，超过阈值时记录警告，超过硬限制时抛出异常。
     *
     * @param offset 分页偏移量
     * @throws IllegalArgumentException 如果 offset 超过硬限制
     */
    private void checkDeepPagination(long offset) {
        // 深度分页警告（限流：每分钟最多记录一次）
        if (offset > this.deepPaginationOffsetThreshold) {
            long now = System.currentTimeMillis();
            long lastWarn = lastDeepPaginationWarnTime.get();
            if (now - lastWarn > DEEP_PAGINATION_WARN_INTERVAL_MS
                && lastDeepPaginationWarnTime.compareAndSet(lastWarn, now)) {
                log.warn("Deep pagination detected (offset={}). This may cause slow queries. "
                    + "Consider using keyset pagination for better performance.", offset);
            }
        }

        // 深度分页硬限制
        if (this.deepPaginationOffsetLimit > 0 && offset > this.deepPaginationOffsetLimit) {
            throw new IllegalArgumentException("Pagination offset (" + offset + ") exceeds the configured hard limit ("
                + this.deepPaginationOffsetLimit
                + "). Use keyset pagination for better performance, or adjust myjpa-plus.query.deep-pagination-offset-limit.");
        }
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
        TypedQuery<Long> countQuery = entityManager.createQuery(countCq);
        QueryTimeoutHelper.applyTimeout(countQuery);
        return countQuery.getSingleResult();
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

        checkDeepPagination(pageable.getOffset());

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
        return getBulkOperationTemplate().execute(spec);
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
        return getBulkOperationTemplate().execute(spec);
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
        return getBulkOperationTemplate().execute(spec);
    }

    /**
     * 批量执行 UPSERT 操作，使用 EntityManager flush/clear 进行分批处理。
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
        return getBulkOperationTemplate().executeBatch(mergeSpec, entities, batchSize);
    }

    /**
     * 使用给定的 {@link UpdateSpec} 执行批量更新，限制最大影响行数。
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
        return getBulkOperationTemplate().executeWithMaxRows(spec, maxRows);
    }

    /**
     * 使用给定的 {@link DeleteSpec} 执行批量删除，限制最大影响行数。
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
        return getBulkOperationTemplate().executeWithMaxRows(spec, maxRows);
    }

    /**
     * 分批执行批量更新。通过分批处理减少内存占用（通过 {@link EntityManager#clear()} 清除一级缓存）， 但所有批次在同一个事务中执行，要么全部成功，要么全部回滚。
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
        return getBulkOperationTemplate().executeBatch(spec, batchSize);
    }

    /**
     * 分批执行批量删除。通过分批处理减少内存占用（通过 {@link EntityManager#clear()} 清除一级缓存）， 但所有批次在同一个事务中执行，要么全部成功，要么全部回滚。
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
        return getBulkOperationTemplate().executeBatch(spec, batchSize);
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
        BulkOperationTemplate.BatchResult result = getBulkOperationTemplate().executeBatchInSeparateTransactions(spec,
            batchSize, convertFailureStrategy(failureStrategy));
        return new BatchResult(result.totalRows(), result.batchCount(), result.success(), result.failedBatchIndex(),
            result.failureCause());
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
        BulkOperationTemplate.BatchResult result = getBulkOperationTemplate().executeBatchInSeparateTransactions(spec,
            batchSize, convertFailureStrategy(failureStrategy));
        return new BatchResult(result.totalRows(), result.batchCount(), result.success(), result.failedBatchIndex(),
            result.failureCause());
    }

    private static BulkOperationTemplate.BatchFailureStrategy convertFailureStrategy(BatchFailureStrategy strategy) {
        return switch (strategy) {
            case CONTINUE -> BulkOperationTemplate.BatchFailureStrategy.CONTINUE;
            case ABORT -> BulkOperationTemplate.BatchFailureStrategy.ABORT;
        };
    }

    /**
     * 分批执行批量更新，每批在独立事务中提交。
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
        return getBulkOperationTemplate().executeBatchInSeparateTransactions(spec, batchSize);
    }

    /**
     * 分批执行批量删除，每批在独立事务中提交。
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
        return getBulkOperationTemplate().executeBatchInSeparateTransactions(spec, batchSize);
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
        if (pageable.isPaged()) {
            checkDeepPagination(pageable.getOffset());
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
        QueryTimeoutHelper.applyTimeout(query);
        try {
            query.setFirstResult(Math.toIntExact(pageable.getOffset()));
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("Page offset exceeds Integer.MAX_VALUE.", e);
        }
        // 总是请求 pageSize+1 行以检测是否有下一页。
        // maxResults 仅作为安全上限：当 maxResults > 0 且小于 pageSize+1 时，
        // 无法准确判断是否有下一页，此时保守地认为有下一页（除非结果集为空或不足一页）。
        int fetchLimit = pageable.getPageSize() + 1;
        boolean maxResultsLimited = false;
        if (this.maxResults > 0 && this.maxResults < fetchLimit) {
            fetchLimit = this.maxResults;
            maxResultsLimited = true;
        }
        query.setMaxResults(fetchLimit);
        List<T> content = query.getResultList();
        boolean hasNext;
        if (maxResultsLimited) {
            // 当 maxResults 限制了请求行数时：
            // - 返回行数 == fetchLimit（即 maxResults）→ 可能还有更多数据，保守标记 hasNext=true
            // - 返回行数 < fetchLimit → 已到达数据末尾，没有下一页
            hasNext = content.size() >= this.maxResults;
        } else {
            hasNext = content.size() > pageable.getPageSize();
        }
        if (hasNext) {
            content = content.subList(0, Math.min(content.size(), pageable.getPageSize()));
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
        // 直接传递 ids Collection 以避免不必要的 toArray() 转换
        cq.where(com.zsubera.jpa.util.InClauseBuilder.in(cb, root.get(idFieldName), ids));
        TypedQuery<T> query = entityManager.createQuery(cq);
        QueryTimeoutHelper.applyTimeout(query);
        return query.getResultList();
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
        // 直接传递 ids Collection 以避免不必要的 toArray() 转换
        Specification<T> idSpec =
            (root, query, cb) -> com.zsubera.jpa.util.InClauseBuilder.in(cb, root.get(idFieldName), ids);
        Specification<T> softDeleteSpec = com.zsubera.jpa.softdelete.SoftDeleteHelper.isNotDeleted(entityClass);
        Specification<T> combinedSpec = idSpec.and(softDeleteSpec);
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<T> cq = cb.createQuery(entityClass);
        Root<T> root = cq.from(entityClass);
        jakarta.persistence.criteria.Predicate predicate = combinedSpec.toPredicate(root, cq, cb);
        if (predicate != null) {
            cq.where(predicate);
        }
        TypedQuery<T> query = entityManager.createQuery(cq);
        QueryTimeoutHelper.applyTimeout(query);
        return query.getResultList();
    }

    // ---- Keyset 分页 ----

    /**
     * 游标分页（Keyset Pagination）的结果记录。
     *
     * <p>
     * 相比传统 offset 分页，游标分页在大数据量场景下性能更优（O(log n) vs O(n)）， 因为它使用 WHERE 条件而非 OFFSET 跳过已读记录。
     *
     * @param content 当前页数据
     * @param hasNext 是否有下一页
     * @param lastSortValues 最后一条记录的排序字段值，用于请求下一页（可为 null 表示无更多数据）
     */
    public record KeysetPage<T>(List<T> content, boolean hasNext, Object[] lastSortValues) {
    }

    /**
     * 游标分页查询，避免大 offset 的性能退化。
     *
     * <p>
     * 使用上一页最后一条记录的排序字段值作为游标，通过 WHERE 条件定位下一页起始位置。 性能始终为 O(log n)，不受页码大小影响。
     *
     * <p>
     * 使用示例：
     *
     * <pre>{@code
     * // 第一页
     * KeysetPage<User> page1 = jpa.findKeysetPage(User.class, spec, Sort.by("id"), 20, null);
     *
     * // 下一页：使用上一页的 lastSortValues
     * KeysetPage<User> page2 = jpa.findKeysetPage(User.class, spec, Sort.by("id"), 20, page1.lastSortValues());
     * }</pre>
     *
     * <p>
     * <strong>限制：</strong>
     * <ul>
     * <li>排序字段必须有数据库索引以保证性能</li>
     * <li>排序字段组合必须唯一（或包含主键），否则可能遗漏记录</li>
     * <li>不支持跳页（只能前进/后退一页），需要跳页请使用 offset 分页</li>
     * </ul>
     *
     * @param entityClass 实体类
     * @param spec 查询规范
     * @param sort 排序规则（至少一个字段，建议包含主键以保证唯一性）
     * @param pageSize 每页大小
     * @param lastSortValues 上一页最后一条记录的排序字段值（null 表示查询第一页）
     * @param <T> 实体类型
     * @return 游标分页结果
     * @throws IllegalArgumentException 如果参数不合法
     */
    @Transactional(readOnly = true)
    public <T> KeysetPage<T> findKeysetPage(Class<T> entityClass, Specification<T> spec,
        org.springframework.data.domain.Sort sort, int pageSize, @Nullable Object[] lastSortValues) {
        if (entityClass == null) {
            throw new IllegalArgumentException("entityClass must not be null");
        }
        if (spec == null) {
            throw new IllegalArgumentException("spec must not be null");
        }
        if (sort == null || sort.isUnsorted()) {
            throw new IllegalArgumentException("sort must not be null or unsorted for keyset pagination");
        }
        if (pageSize <= 0) {
            throw new IllegalArgumentException("pageSize must be positive");
        }
        List<org.springframework.data.domain.Sort.Order> orders = sort.stream().toList();
        if (lastSortValues != null && lastSortValues.length != orders.size()) {
            throw new IllegalArgumentException("lastSortValues length (" + lastSortValues.length
                + ") must match sort fields count (" + orders.size() + ")");
        }

        return getKeysetPaginationHelper().findKeysetPage(entityClass, spec, sort, pageSize, lastSortValues);
    }
}
