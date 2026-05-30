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
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    @PersistenceContext
    private EntityManager entityManager;

    private volatile int maxResults = DEFAULT_MAX_RESULTS;
    private volatile int deepPaginationOffsetThreshold = DEFAULT_DEEP_PAGINATION_OFFSET_THRESHOLD;
    /** 深度分页硬限制。{@code -1} 表示禁用（仅记录警告）。 */
    private volatile int deepPaginationOffsetLimit = -1;

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

    // ---- 便捷查询方法 ----

    /**
     * 根据 ID 查找实体。
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
        String idFieldName = EntityClassResolver.resolveIdFieldName(entityClass);
        Specification<T> idSpec = (root, query, cb) -> cb.equal(root.get(idFieldName), id);
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<T> cq = cb.createQuery(entityClass);
        Root<T> root = cq.from(entityClass);
        jakarta.persistence.criteria.Predicate predicate = idSpec.toPredicate(root, cq, cb);
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
     * @param entityClass 实体类
     * @param spec 查询规范
     * @param <T> 实体类型
     * @return 匹配实体的 Stream（必须由调用方关闭）
     * @deprecated 使用 {@link #findAllStream(Class, QuerySpec, Consumer)} 安全版本替代，该版本自动管理 Stream 生命周期，避免资源泄漏。 此方法将在 2.0
     *             版本中移除。
     */
    @Deprecated(since = "1.0.1", forRemoval = true)
    public <T> Stream<T> findAllStream(Class<T> entityClass, QuerySpec<T> spec) {
        if (log.isWarnEnabled()) {
            log.warn("DEPRECATED: findAllStream(Class, QuerySpec) may cause resource leaks and will be removed in 2.0. "
                + "Use findAllStream(Class, QuerySpec, Consumer) instead, which automatically manages the Stream lifecycle.");
        }
        if (entityClass == null) {
            throw new IllegalArgumentException("entityClass must not be null");
        }
        if (spec == null) {
            throw new IllegalArgumentException("spec must not be null");
        }
        return doFindStream(entityClass, spec);
    }

    /**
     * 流式查询匹配给定 {@link QuerySpec} 的所有实体，支持可选的 EntityGraph。
     *
     * @param entityClass 实体类
     * @param spec 查询规范
     * @param entityGraph 用于急切加载的实体图（可为 null）
     * @param <T> 实体类型
     * @return 匹配实体的 Stream
     * @deprecated 使用 {@link #findAllStream(Class, QuerySpec, Consumer)} 安全版本替代，该版本自动管理 Stream 生命周期，避免资源泄漏。 此方法将在 2.0
     *             版本中移除。
     */
    @Deprecated(since = "1.0.1", forRemoval = true)
    public <T> Stream<T> findAllStream(Class<T> entityClass, QuerySpec<T> spec, EntityGraphHelper<T> entityGraph) {
        if (log.isWarnEnabled()) {
            log.warn(
                "DEPRECATED: findAllStream(Class, QuerySpec, EntityGraph) may cause resource leaks and will be removed in 2.0. "
                    + "Use findAllStream(Class, QuerySpec, Consumer) instead.");
        }
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
        try (Stream<T> stream = doFindStream(entityClass, spec)) {
            consumer.accept(stream);
        }
    }

    /**
     * 内部流式查询实现，供安全版本和 deprecated 版本共用。
     *
     * @param entityClass 实体类
     * @param spec 查询规范
     * @param <T> 实体类型
     * @return 匹配实体的 Stream（必须由调用方关闭）
     */
    private <T> Stream<T> doFindStream(Class<T> entityClass, QuerySpec<T> spec) {
        TypedQuery<T> query = buildTypedQuery(entityClass, spec, null, null);
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
        if (sort != null && sort.isSorted()) {
            cq.orderBy(sort.stream().map(order -> order.isAscending() ? cb.asc(root.get(order.getProperty()))
                : cb.desc(root.get(order.getProperty()))).toList());
        }
        TypedQuery<T> query = entityManager.createQuery(cq);
        if (maxResults != null) {
            query.setMaxResults(maxResults);
        }
        return query;
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
            log.warn(
                "Pageable.unpaged() used with pagination method - returning all results up to {} limit. "
                    + "Consider using findAll() with explicit maxResults or findAllStream() for large datasets.",
                this.maxResults);
            TypedQuery<T> typedQuery = buildSpecificationQuery(entityClass, spec, null, this.maxResults);
            if (querySpec != null) {
                querySpec.applyQuerySettings(typedQuery);
            }
            List<T> allContent = typedQuery.getResultList();
            return new PageImpl<>(allContent);
        }

        // 深度分页警告
        if (pageable.getOffset() > this.deepPaginationOffsetThreshold) {
            log.warn("Deep pagination detected (offset={}). This may cause slow queries. "
                + "Consider using keyset pagination for better performance.", pageable.getOffset());
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
        query.setFirstResult(Math.toIntExact(pageable.getOffset()));
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
     * 分批执行批量更新。通过分批处理减少内存占用（通过 {@link EntityManager#clear()} 清除一级缓存）， 但所有批次在同一个事务中执行，要么全部成功，要么全部回滚。
     *
     * <p>
     * <strong>注意：</strong>此方法不会分批提交事务。所有批次在同一个事务中执行， 只有在整个方法完成后才会提交。如果需要分批提交，请使用外部事务管理。
     *
     * <p>
     * <strong>长事务风险：</strong>如果数据量非常大，事务日志可能撑爆，导致数据库锁等待超时。 对于大数据量操作，建议：
     * <ul>
     * <li>使用较小的 batchSize（如 1000-5000）</li>
     * <li>考虑使用外部事务管理进行分批提交</li>
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
        int totalUpdated = 0;
        int batchUpdated;
        do {
            batchUpdated = spec.executeLimited(entityManager, batchSize);
            totalUpdated += batchUpdated;
            if (batchUpdated > 0) {
                entityManager.flush();
                entityManager.clear();
                if (log.isDebugEnabled()) {
                    log.debug("Batch update: {} rows updated in this batch (total: {})", batchUpdated, totalUpdated);
                }
            }
        } while (batchUpdated >= batchSize);
        return totalUpdated;
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
     * <li>考虑使用外部事务管理进行分批提交</li>
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
        int totalDeleted = 0;
        int batchDeleted;
        do {
            batchDeleted = spec.executeLimited(entityManager, batchSize);
            totalDeleted += batchDeleted;
            if (batchDeleted > 0) {
                entityManager.flush();
                entityManager.clear();
                if (log.isDebugEnabled()) {
                    log.debug("Batch delete: {} rows deleted in this batch (total: {})", batchDeleted, totalDeleted);
                }
            }
        } while (batchDeleted >= batchSize);
        return totalDeleted;
    }
}
