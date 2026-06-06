package com.zsubera.jpa.repository;

import com.zsubera.jpa.update.SoftDeleteHelper;
import com.zsubera.jpa.util.EntityClassResolver;
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
     * P1-7: 全局无条件硬删除阻断开关。当为 true 时，对拥有 {@code @SoftDelete} 字段的实体， 在软删除过滤被禁用的情况下执行 {@code deleteAll()} 等无条件硬删除操作将被阻断。
     *
     * <p>
     * 此开关防止在 {@code autoFilter=false} 或 {@code @IgnoreSoftDelete} 上下文中意外执行全表硬删除。 仅对拥有 {@code @SoftDelete}
     * 字段的实体生效，无该字段的实体不受影响。
     *
     * <p>
     * 默认值为 {@code true}（生产环境最安全）。如需临时放开（如数据迁移），可设为 {@code false}。
     */
    private static volatile boolean blockUnconditionalDelete = true;

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
     * 设置全局无条件硬删除阻断开关。由自动配置类在启动时调用。
     *
     * @param blocked 是否阻断无条件硬删除
     */
    public static void setBlockUnconditionalDelete(boolean blocked) {
        blockUnconditionalDelete = blocked;
    }

    /**
     * 获取当前无条件硬删除阻断开关状态。
     *
     * @return 如果阻断已启用返回 true
     */
    public static boolean isBlockUnconditionalDelete() {
        return blockUnconditionalDelete;
    }

    /**
     * P1-7: 检查是否应该阻断无条件硬删除操作。
     *
     * <p>
     * 同时满足以下条件时返回 true：
     * <ul>
     * <li>{@code blockUnconditionalDelete} 全局开关为 true</li>
     * <li>当前实体拥有 {@code @SoftDelete} 字段</li>
     * </ul>
     *
     * @return 如果应该阻断返回 true
     */
    private boolean shouldBlockHardDelete() {
        return blockUnconditionalDelete && SoftDeleteHelper.findSoftDeleteField(domainClass) != null;
    }

    /**
     * P1: Execute an action with auto-filter override, automatically cleaning up in finally block. Prevents ThreadLocal
     * leaks when exceptions occur.
     *
     * @param value the override value (null to clear)
     * @param action the action to execute
     */
    public static void withAutoFilterOverride(Boolean value, Runnable action) {
        Boolean previous = autoFilterOverride.get();
        if (value == null) {
            autoFilterOverride.remove();
        } else {
            autoFilterOverride.set(value);
        }
        try {
            action.run();
        } finally {
            // 恢复之前的值，支持嵌套调用
            if (previous != null) {
                autoFilterOverride.set(previous);
            } else {
                autoFilterOverride.remove();
            }
        }
    }

    /**
     * P1: Execute a supplier with auto-filter override, automatically cleaning up in finally block. Prevents
     * ThreadLocal leaks when exceptions occur.
     *
     * @param value the override value (null to clear)
     * @param supplier the supplier to execute
     * @param <R> the return type
     * @return the supplier result
     */
    public static <R> R withAutoFilterOverride(Boolean value, java.util.function.Supplier<R> supplier) {
        Boolean previous = autoFilterOverride.get();
        if (value == null) {
            autoFilterOverride.remove();
        } else {
            autoFilterOverride.set(value);
        }
        try {
            return supplier.get();
        } finally {
            // 恢复之前的值，支持嵌套调用
            if (previous != null) {
                autoFilterOverride.set(previous);
            } else {
                autoFilterOverride.remove();
            }
        }
    }

    /**
     * 清除当前线程的 autoFilterOverride ThreadLocal 值。 在应用关闭或 ServletContext 销毁时调用以防止 ThreadLocal 泄漏。
     */
    public static void clearThreadLocal() {
        autoFilterOverride.remove();
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
     * <p>
     * 对于软删除实体，直接使用 Specification 查询以避免将已删除实体加载到持久化上下文中（防止 PC 污染）。
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
        // 软删除场景：始终使用 Specification 查询，避免将已删除实体加载到持久化上下文。
        // 直接使用 entityManager.find() 会将已删除实体加载到 PC，导致 PC 污染。
        String idFieldName = EntityClassResolver.resolveIdFieldName(domainClass);
        Specification<T> idSpec = Specification.where((root, query, cb) -> cb.equal(root.get(idFieldName), id));
        Specification<T> softDeleteSpec = SoftDeleteHelper.isNotDeleted(domainClass);
        return findOne(idSpec.and(softDeleteSpec));
    }

    /**
     * 覆写 deleteById() 以支持软删除过滤。
     *
     * <p>
     * P0-6: 此方法在实体不存在或已被软删除时抛出 {@link org.springframework.dao.EmptyResultDataAccessException}。 如需静默版本（不抛异常），请使用
     * {@link #deleteByIdIfExists(Object)}。
     *
     * @param id 实体 ID，不能为 {@code null}
     * @throws org.springframework.dao.EmptyResultDataAccessException 如果实体不存在或已被软删除
     */
    @Override
    public void deleteById(ID id) {
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

    /**
     * P0-6: 静默版本的按 ID 删除。实体不存在或已软删除时不抛异常。
     *
     * @param id 实体 ID，不能为 {@code null}
     * @return 如果成功删除返回 true，如果实体不存在或已软删除返回 false
     */
    public boolean deleteByIdIfExists(ID id) {
        if (id == null) {
            throw new IllegalArgumentException("ID must not be null");
        }
        Optional<T> entity = findById(id);
        if (entity.isPresent()) {
            delete(entity.get());
            return true;
        }
        return false;
    }

    /**
     * 根据 ID 删除实体，如果实体不存在则抛出异常。
     *
     * @deprecated 此方法与 {@link #deleteById(Object)} 行为完全相同（均在实体不存在时抛出
     *             {@link org.springframework.dao.EmptyResultDataAccessException}）。请直接使用 {@link #deleteById(Object)}。
     *
     * @param id 实体 ID，不能为 {@code null}
     * @throws org.springframework.dao.EmptyResultDataAccessException 如果实体不存在
     */
    @Deprecated
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

    /**
     * P0-5: 覆写 deleteAll() 以支持软删除。使用批量 UPDATE 替代逐条操作以避免 N+1 查询。
     *
     * <p>
     * 当软删除过滤启用时，使用 SoftDeleteHelper.softDeleteAll() 批量更新。 当软删除过滤禁用时，执行标准的硬删除。
     */
    @Override
    public void deleteAll() {
        if (shouldApplySoftDeleteFilter()) {
            SoftDeleteHelper.softDeleteAll(entityManager, domainClass, true);
        } else if (shouldBlockHardDelete()) {
            throw new IllegalStateException("Unconditional hard DELETE ALL on " + domainClass.getSimpleName()
                + " is blocked because the entity has a @SoftDelete field. "
                + "Set SoftDeleteJpaRepository.setBlockUnconditionalDelete(false) to allow this operation.");
        } else {
            super.deleteAll();
        }
    }

    /**
     * P0-5: 覆写 deleteAllById() 以支持软删除。使用批量 UPDATE 替代逐条操作以避免 N+1 查询。
     *
     * @param ids 要删除的实体 ID 集合
     */
    @Override
    public void deleteAllById(Iterable<? extends ID> ids) {
        if (ids == null) {
            throw new IllegalArgumentException("ids must not be null");
        }
        java.util.List<ID> idList = new java.util.ArrayList<>();
        ids.forEach(idList::add);
        if (idList.isEmpty()) {
            return;
        }
        if (shouldApplySoftDeleteFilter()) {
            SoftDeleteHelper.softDeleteByIds(entityManager, domainClass, idList);
        } else if (shouldBlockHardDelete()) {
            throw new IllegalStateException("Hard DELETE ALL BY ID on " + domainClass.getSimpleName()
                + " is blocked because the entity has a @SoftDelete field. "
                + "Set SoftDeleteJpaRepository.setBlockUnconditionalDelete(false) to allow this operation.");
        } else {
            super.deleteAllById(idList);
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
            // B-12: Use batch UPDATE instead of N+1 individual deletes
            // P1-4: Use PersistenceUnitUtil instead of reflection for Java 17+ compatibility
            java.util.List<ID> idList = new java.util.ArrayList<>();
            jakarta.persistence.PersistenceUnitUtil util =
                entityManager.getEntityManagerFactory().getPersistenceUnitUtil();
            for (T entity : entities) {
                @SuppressWarnings("unchecked")
                ID id = (ID)util.getIdentifier(entity);
                if (id != null) {
                    idList.add(id);
                }
            }
            if (!idList.isEmpty()) {
                SoftDeleteHelper.softDeleteByIds(entityManager, domainClass, idList);
            }
        } else if (shouldBlockHardDelete()) {
            throw new IllegalStateException("Hard DELETE IN BATCH on " + domainClass.getSimpleName()
                + " is blocked because the entity has a @SoftDelete field. "
                + "Set SoftDeleteJpaRepository.setBlockUnconditionalDelete(false) to allow this operation.");
        } else {
            super.deleteInBatch(entities);
        }
    }

    /**
     * P0-5: 覆写 deleteAllInBatch() 以支持软删除。使用批量 UPDATE 替代逐条操作以避免 N+1 查询。
     */
    @Override
    public void deleteAllInBatch() {
        if (shouldApplySoftDeleteFilter()) {
            SoftDeleteHelper.softDeleteAll(entityManager, domainClass, true);
        } else if (shouldBlockHardDelete()) {
            throw new IllegalStateException("Unconditional hard DELETE ALL IN BATCH on " + domainClass.getSimpleName()
                + " is blocked because the entity has a @SoftDelete field. "
                + "Set SoftDeleteJpaRepository.setBlockUnconditionalDelete(false) to allow this operation.");
        } else {
            super.deleteAllInBatch();
        }
    }
}
