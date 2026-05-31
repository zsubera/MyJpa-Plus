package com.zsubera.jpa.repository;

import com.zsubera.jpa.update.SoftDeleteHelper;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.support.JpaEntityInformation;
import org.springframework.data.jpa.repository.support.SimpleJpaRepository;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.lang.Nullable;

/**
 * 支持软删除自动过滤的 {@link SimpleJpaRepository} 实现。
 *
 * <p>
 * 当实体有 {@link com.zsubera.jpa.annotation.SoftDelete @SoftDelete} 字段时，查询会自动追加过滤条件。 使用
 * {@link com.zsubera.jpa.annotation.IgnoreSoftDelete @IgnoreSoftDelete} 注解可跳过自动过滤。
 *
 * <p>
 * 使用方式：在 {@code @EnableJpaRepositories} 中指定 {@code repositoryBaseClass}：
 *
 * <pre>
 * {
 *     &#64;code
 *     &#64;Configuration
 *     @EnableJpaRepositories(basePackages = "com.example.repository",
 *         repositoryBaseClass = SoftDeleteJpaRepository.class)
 *     public class JpaConfig {}
 * }
 * </pre>
 *
 * @param <T> 实体类型
 * @param <ID> ID 类型
 * @see com.zsubera.jpa.annotation.SoftDelete
 * @see com.zsubera.jpa.annotation.IgnoreSoftDelete
 */
@NoRepositoryBean
public class SoftDeleteJpaRepository<T, ID> extends SimpleJpaRepository<T, ID> {

    private static final Logger log = LoggerFactory.getLogger(SoftDeleteJpaRepository.class);

    private final Class<T> domainClass;
    private final EntityManager entityManager;

    /**
     * 全局自动过滤开关，由自动配置类设置。当为 false 时，所有 Repository 层面的软删除过滤将被禁用。
     * <p>
     * 默认值为 true，与 {@link com.zsubera.jpa.autoconfigure.SoftDeleteFilterBean} 的行为保持一致。
     */
    private static volatile boolean autoFilterEnabled = true;

    /**
     * P1: Thread-local override for auto-filter. When set (non-null), overrides the global setting. This allows
     * per-request control of soft delete filtering behavior.
     */
    private static final ThreadLocal<Boolean> autoFilterOverride = new ThreadLocal<>();

    /**
     * 设置全局自动过滤开关。由自动配置类在启动时调用。
     *
     * @param enabled 是否启用自动过滤
     */
    public static void setAutoFilterEnabled(boolean enabled) {
        autoFilterEnabled = enabled;
    }

    /**
     * 获取当前全局自动过滤开关状态。
     *
     * @return 如果自动过滤已启用返回 true
     */
    public static boolean isAutoFilterEnabled() {
        return autoFilterEnabled;
    }

    /**
     * P1: Set thread-local auto-filter override for the current thread. When set, this overrides the global
     * autoFilterEnabled setting.
     *
     * @param enabled 是否启用自动过滤，传 null 清除覆盖
     */
    public static void setAutoFilterOverride(Boolean enabled) {
        if (enabled == null) {
            autoFilterOverride.remove();
        } else {
            autoFilterOverride.set(enabled);
        }
    }

    @SuppressFBWarnings("EI_EXPOSE_REP2")
    public SoftDeleteJpaRepository(JpaEntityInformation<T, ?> entityInformation, EntityManager entityManager) {
        super(entityInformation, entityManager);
        this.domainClass = entityInformation.getJavaType();
        this.entityManager = entityManager;
    }

    /**
     * 检查当前调用是否应该应用软删除过滤。
     *
     * <p>
     * 使用 {@link SoftDeleteContext} 的 ThreadLocal 标志判断， 由 {@link IgnoreSoftDeleteAdvisor} 在方法调用前自动设置。替代了原先基于栈遍历的检测方案。
     *
     * <p>
     * 同时检查全局 {@code auto-filter} 配置，与 {@link com.zsubera.jpa.autoconfigure.SoftDeleteFilterBean} 保持一致。 当
     * {@code auto-filter=false} 时，Repository 层面也停止自动过滤。
     *
     * @return 如果应该应用过滤返回 true
     */
    private boolean shouldApplySoftDeleteFilter() {
        // P1: Check thread-local override first, then global setting
        Boolean override = autoFilterOverride.get();
        boolean effectiveAutoFilter = (override != null) ? override : autoFilterEnabled;
        return effectiveAutoFilter && SoftDeleteHelper.findSoftDeleteField(domainClass) != null
            && !SoftDeleteContext.isIgnoreSoftDelete();
    }

    private Specification<T> mergeSoftDeleteFilter(@Nullable Specification<T> spec) {
        if (!shouldApplySoftDeleteFilter()) {
            return spec == null ? (root, query, cb) -> cb.conjunction() : spec;
        }
        Specification<T> softDeleteSpec = SoftDeleteHelper.isNotDeleted(domainClass);
        if (softDeleteSpec == null) {
            log.debug("No soft delete field found for {}, skipping filter", domainClass.getSimpleName());
            return spec == null ? (root, query, cb) -> cb.conjunction() : spec;
        }
        return spec == null ? softDeleteSpec : spec.and(softDeleteSpec);
    }

    // ---- 覆盖查询方法，自动注入软删除条件 ----

    @Override
    public List<T> findAll() {
        return super.findAll(mergeSoftDeleteFilter(null));
    }

    @Override
    public List<T> findAll(Sort sort) {
        return super.findAll(mergeSoftDeleteFilter(null), sort);
    }

    @Override
    public Page<T> findAll(Pageable pageable) {
        return super.findAll(mergeSoftDeleteFilter(null), pageable);
    }

    @Override
    public List<T> findAll(Specification<T> spec) {
        return super.findAll(mergeSoftDeleteFilter(spec));
    }

    @Override
    public Page<T> findAll(Specification<T> spec, Pageable pageable) {
        return super.findAll(mergeSoftDeleteFilter(spec), pageable);
    }

    @Override
    public List<T> findAll(Specification<T> spec, Sort sort) {
        return super.findAll(mergeSoftDeleteFilter(spec), sort);
    }

    @Override
    public Optional<T> findOne(Specification<T> spec) {
        return super.findOne(mergeSoftDeleteFilter(spec));
    }

    @Override
    public long count() {
        return super.count(mergeSoftDeleteFilter(null));
    }

    @Override
    public long count(Specification<T> spec) {
        return super.count(mergeSoftDeleteFilter(spec));
    }

    @Override
    public boolean exists(Specification<T> spec) {
        return super.exists(mergeSoftDeleteFilter(spec));
    }

    /**
     * 覆盖 findById() 以支持软删除过滤。
     *
     * <p>
     * 默认的 {@link SimpleJpaRepository#findById(Object)} 不会过滤软删除记录， 导致用户可能通过 {@code repository.findById(id)} 获取已删除的实体。
     * 此实现会自动追加软删除过滤条件，确保已删除的实体返回 {@link Optional#empty()}。
     *
     * @param id 实体 ID，不能为 {@code null}
     * @return 包含实体的 {@link Optional}，如果实体不存在或已软删除则返回 {@link Optional#empty()}
     */
    @Override
    public Optional<T> findById(ID id) {
        if (id == null) {
            return Optional.empty();
        }
        if (!shouldApplySoftDeleteFilter()) {
            return Optional.ofNullable(entityManager.find(domainClass, id));
        }
        // P0-5: Use Specification-based query to atomically filter soft-deleted records,
        // avoiding TOCTOU race condition with EntityManager.find() + post-check.
        String idFieldName = EntityClassResolver.resolveIdFieldName(domainClass);
        Specification<T> spec = (root, query, cb) -> {
            jakarta.persistence.criteria.Predicate idPredicate = cb.equal(root.get(idFieldName), id);
            jakarta.persistence.criteria.Predicate softDeletePredicate =
                SoftDeleteHelper.isNotDeleted(domainClass).toPredicate(root, query, cb);
            if (softDeletePredicate != null) {
                return cb.and(idPredicate, softDeletePredicate);
            }
            return idPredicate;
        };
        return findOne(spec);
    }

    /**
     * 覆盖 deleteById() 以支持软删除过滤。
     *
     * <p>
     * 默认的 {@link SimpleJpaRepository#deleteById(Object)} 会尝试删除实体， 但如果实体已被软删除，{@link #findById(Object)} 会返回
     * {@link Optional#empty()}，导致抛出 {@link org.springframework.dao.EmptyResultDataAccessException}。
     * 此实现会在删除前检查实体是否存在（未被软删除），如果不存在则记录警告并跳过删除。
     *
     * <p>
     * <strong>注意：</strong>当实体不存在或已被软删除时，此方法静默返回（不抛出异常）。 如果需要区分"实体不存在"和"实体已被软删除"的情况，请使用
     * {@link #deleteByIdOrThrow(Object)} 方法。
     *
     * @param id 实体 ID，不能为 {@code null}
     */
    @Override
    public void deleteById(ID id) {
        if (id == null) {
            throw new IllegalArgumentException("ID must not be null");
        }
        Optional<T> entity = findById(id);
        if (entity.isPresent()) {
            delete(entity.get());
        } else if (shouldApplySoftDeleteFilter()) {
            log.warn("Attempted to delete entity with id {} but it was not found (possibly soft-deleted). "
                + "Use @IgnoreSoftDelete to bypass soft-delete filtering if needed.", id);
        } else {
            throw new org.springframework.dao.EmptyResultDataAccessException(
                String.format("No %s entity with id %s exists!", domainClass.getSimpleName(), id), 1);
        }
    }

    /**
     * 根据 ID 删除实体，如果实体不存在则抛出异常。
     *
     * <p>
     * 与 {@link #deleteById(Object)} 不同，此方法在实体不存在时始终抛出 {@link org.springframework.dao.EmptyResultDataAccessException}，
     * 无论软删除过滤是否启用。
     *
     * @param id 实体 ID，不能为 {@code null}
     * @throws org.springframework.dao.EmptyResultDataAccessException 如果实体不存在
     */
    public void deleteByIdOrThrow(ID id) {
        if (id == null) {
            throw new IllegalArgumentException("ID must not be null");
        }
        Optional<T> entity = findById(id);
        if (entity.isPresent()) {
            delete(entity.get());
        } else {
            throw new org.springframework.dao.EmptyResultDataAccessException(
                String.format("No %s entity with id %s exists!", domainClass.getSimpleName(), id), 1);
        }
    }

    // P0: Override bulk delete methods to use soft delete

    /**
     * 覆写 deleteAll() 以支持软删除。遍历所有实体并逐个执行软删除。
     *
     * <p>
     * 当软删除过滤启用时，仅删除未被软删除的实体。当软删除过滤禁用时，执行标准的硬删除。
     */
    @Override
    public void deleteAll() {
        if (shouldApplySoftDeleteFilter()) {
            findAll().forEach(this::delete);
        } else {
            super.deleteAll();
        }
    }

    /**
     * 覆写 deleteAllById() 以支持软删除。逐个查找并软删除指定 ID 的实体。
     *
     * @param ids 要删除的实体 ID 集合
     */
    @Override
    public void deleteAllById(Iterable<? extends ID> ids) {
        if (ids == null) {
            throw new IllegalArgumentException("ids must not be null");
        }
        for (ID id : ids) {
            deleteById(id);
        }
    }

    /**
     * 覆写 deleteInBatch() 以支持软删除。使用 SoftDeleteHelper 批量软删除。
     *
     * @param entities 要删除的实体集合
     */
    @Override
    public void deleteInBatch(Iterable<T> entities) {
        if (entities == null) {
            throw new IllegalArgumentException("entities must not be null");
        }
        if (shouldApplySoftDeleteFilter()) {
            java.util.List<T> entityList = new java.util.ArrayList<>();
            entities.forEach(entityList::add);
            if (!entityList.isEmpty()) {
                for (T entity : entityList) {
                    delete(entity);
                }
            }
        } else {
            super.deleteInBatch(entities);
        }
    }

    /**
     * 覆写 deleteAllInBatch() 以支持软删除。查找所有实体并逐个软删除。
     */
    @Override
    public void deleteAllInBatch() {
        if (shouldApplySoftDeleteFilter()) {
            findAll().forEach(this::delete);
        } else {
            super.deleteAllInBatch();
        }
    }
}
