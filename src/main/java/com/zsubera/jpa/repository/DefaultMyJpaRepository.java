package com.zsubera.jpa.repository;

import com.zsubera.jpa.softdelete.SoftDeleteHelper;
import com.zsubera.jpa.update.AuditUtils;
import com.zsubera.jpa.update.DeleteSpec;
import com.zsubera.jpa.update.MergeSpec;
import com.zsubera.jpa.update.UpdateSpec;
import com.zsubera.jpa.util.EntityClassResolver;
import com.zsubera.jpa.util.QueryTimeoutHelper;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
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
 * <strong>自动注册：</strong>引入 myjpa-plus 依赖后，此基类通过
 * {@link com.zsubera.jpa.autoconfigure.MyJpaPlusAutoConfiguration} 自动注册，
 * 无需在 {@code @EnableJpaRepositories} 中手动指定 {@code repositoryBaseClass}。
 *
 * <p>
 * 如需自定义基类，可通过 {@code @EnableJpaRepositories(repositoryBaseClass = ...)} 覆盖。
 *
 * @param <T> 实体类型
 * @param <ID> ID 类型
 * @see com.zsubera.jpa.annotation.SoftDelete
 * @see com.zsubera.jpa.annotation.IgnoreSoftDelete
 * @see com.zsubera.jpa.repository.MyJpaRepositoryFactoryBean
 */
@NoRepositoryBean
public class DefaultMyJpaRepository<T, ID> extends SimpleJpaRepository<T, ID> implements MyJpaRepository<T, ID> {

    private static final Logger log = LoggerFactory.getLogger(DefaultMyJpaRepository.class);

    private final Class<T> domainClass;
    private final EntityManager entityManager;
    private final JpaEntityInformation<T, ?> entityInformation;

    /**
     * 全局自动过滤开关，由自动配置类设置。当为 false 时，所有 Repository 层面的软删除过滤将被禁用。
     * <p>
     * 默认值为 true，与 {@link com.zsubera.jpa.autoconfigure.SoftDeleteFilterBean} 的行为保持一致。
     */
    private static volatile boolean autoFilterEnabled = true;

    /**
     * 全局无条件硬删除阻断开关。当为 true 时，对拥有 {@code @SoftDelete} 字段的实体， 在软删除过滤被禁用的情况下执行 {@code deleteAll()} 等无条件硬删除操作将被阻断。
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
     * autoFilter 的 ThreadLocal 覆盖值。设置后（非 null）会覆盖全局配置，支持按请求控制软删除过滤行为。
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
     * 检查是否应该阻断无条件硬删除操作。
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
     * 使用 autoFilterOverride 执行操作，自动在 finally 块中清理。防止异常发生时 ThreadLocal 泄漏。
     *
     * @param value 覆盖值（null 表示清除）
     * @param action 要执行的操作
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
     * 使用 autoFilterOverride 执行 Supplier，自动在 finally 块中清理。防止异常发生时 ThreadLocal 泄漏。
     *
     * @param value 覆盖值（null 表示清除）
     * @param supplier 要执行的 Supplier
     * @param <R> 返回类型
     * @return Supplier 的返回结果
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
    public DefaultMyJpaRepository(JpaEntityInformation<T, ?> entityInformation, EntityManager entityManager) {
        super(entityInformation, entityManager);
        this.domainClass = entityInformation.getJavaType();
        this.entityManager = entityManager;
        this.entityInformation = entityInformation;
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
        // 优先检查 ThreadLocal 覆盖值，再检查全局配置
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
        if (softDeleteSpec == null) {
            return findOne(idSpec);
        }
        return findOne(idSpec.and(softDeleteSpec));
    }

    /**
     * 覆写 findAllById() 以支持软删除过滤。
     *
     * <p>
     * 默认的 {@link SimpleJpaRepository#findAllById(Iterable)} 不会过滤软删除记录。 此实现会自动追加软删除过滤条件，确保已删除的实体不被返回。
     *
     * @param ids 实体 ID 集合
     * @return 未被软删除的实体列表
     */
    @Override
    public List<T> findAllById(Iterable<ID> ids) {
        if (ids == null) {
            throw new IllegalArgumentException("ids must not be null");
        }
        if (!shouldApplySoftDeleteFilter()) {
            return super.findAllById(ids);
        }
        java.util.List<ID> idList = new java.util.ArrayList<>();
        ids.forEach(idList::add);
        if (idList.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        String idFieldName = EntityClassResolver.resolveIdFieldName(domainClass);
        Specification<T> idSpec = Specification.where((root, query, cb) -> root.get(idFieldName).in(idList));
        Specification<T> softDeleteSpec = SoftDeleteHelper.isNotDeleted(domainClass);
        return findAll(idSpec.and(softDeleteSpec));
    }

    /**
     * 覆写 existsById() 以支持软删除过滤。
     *
     * <p>
     * 默认的 {@link SimpleJpaRepository#existsById(Object)} 不会过滤软删除记录。 此实现会自动追加软删除过滤条件，确保已软删除的实体返回 {@code false}。
     *
     * @param id 实体 ID
     * @return 如果实体存在且未被软删除返回 true
     */
    @Override
    public boolean existsById(ID id) {
        if (id == null) {
            return false;
        }
        if (!shouldApplySoftDeleteFilter()) {
            return super.existsById(id);
        }
        String idFieldName = EntityClassResolver.resolveIdFieldName(domainClass);
        Specification<T> idSpec = Specification.where((root, query, cb) -> cb.equal(root.get(idFieldName), id));
        Specification<T> softDeleteSpec = SoftDeleteHelper.isNotDeleted(domainClass);
        return count(softDeleteSpec != null ? idSpec.and(softDeleteSpec) : idSpec) > 0;
    }

    // ---- 查询超时保护 ----

    @Override
    protected jakarta.persistence.TypedQuery<T> getQuery(Specification<T> spec,
        org.springframework.data.domain.Pageable pageable) {
        jakarta.persistence.TypedQuery<T> query = super.getQuery(spec, pageable);
        QueryTimeoutHelper.applyTimeout(query);
        return query;
    }

    @Override
    protected <S extends T> jakarta.persistence.TypedQuery<S> getQuery(Specification<S> spec, Class<S> domainClass,
        org.springframework.data.domain.Pageable pageable) {
        jakarta.persistence.TypedQuery<S> query = super.getQuery(spec, domainClass, pageable);
        QueryTimeoutHelper.applyTimeout(query);
        return query;
    }

    @Override
    protected jakarta.persistence.TypedQuery<T> getQuery(Specification<T> spec,
        org.springframework.data.domain.Sort sort) {
        jakarta.persistence.TypedQuery<T> query = super.getQuery(spec, sort);
        QueryTimeoutHelper.applyTimeout(query);
        return query;
    }

    @Override
    protected <S extends T> jakarta.persistence.TypedQuery<S> getQuery(Specification<S> spec, Class<S> domainClass,
        org.springframework.data.domain.Sort sort) {
        jakarta.persistence.TypedQuery<S> query = super.getQuery(spec, domainClass, sort);
        QueryTimeoutHelper.applyTimeout(query);
        return query;
    }

    @Override
    protected jakarta.persistence.TypedQuery<Long> getCountQuery(Specification<T> spec) {
        jakarta.persistence.TypedQuery<Long> query = super.getCountQuery(spec);
        QueryTimeoutHelper.applyTimeout(query);
        return query;
    }

    @Override
    protected <S extends T> jakarta.persistence.TypedQuery<Long> getCountQuery(Specification<S> spec,
        Class<S> domainClass) {
        jakarta.persistence.TypedQuery<Long> query = super.getCountQuery(spec, domainClass);
        QueryTimeoutHelper.applyTimeout(query);
        return query;
    }

    /**
     * 覆写 delete(entity) 以支持软删除。当实体有 @SoftDelete 字段且软删除过滤启用时，
     * 执行软删除（UPDATE 设置删除标记）而非硬删除（DELETE）。
     *
     * <p>
     * 这确保了 deleteById()（调用 delete()）与 deleteAll() 行为一致。
     *
     * @param entity 要删除的实体
     */
    @Override
    public void delete(T entity) {
        if (entity == null) {
            throw new IllegalArgumentException("Entity must not be null");
        }
        String softField = SoftDeleteHelper.findSoftDeleteField(domainClass);
        if (shouldApplySoftDeleteFilter() && softField != null) {
            SoftDeleteHelper.softDeleteByIds(entityManager, domainClass, List.of(entityInformation.getId(entity)));
        } else if (shouldBlockHardDelete() && softField != null) {
            throw new IllegalStateException("Hard DELETE on " + domainClass.getSimpleName()
                + " entity is blocked because it has a @SoftDelete field. "
                + "Set DefaultMyJpaRepository.setBlockUnconditionalDelete(false) to allow this operation.");
        } else {
            super.delete(entity);
        }
    }

    /**
     * 覆写 deleteById() 以支持软删除过滤。
     *
     * <p>
     * 此方法在实体不存在或已被软删除时抛出 {@link org.springframework.dao.EmptyResultDataAccessException}。 如需静默版本（不抛异常），请使用
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
        // findById 在软删除过滤启用时会排除已删除实体，此时结果安全。
        // 在 @IgnoreSoftDelete 上下文中，findById 可能返回已软删除的实体，
        // 需要额外检查以避免隐式硬删除绕过 blockUnconditionalDelete 保护。
        Optional<T> entity = findById(id);
        if (entity.isPresent()) {
            if (SoftDeleteContext.isIgnoreSoftDelete() && SoftDeleteHelper.isSoftDeleted(domainClass, entity.get())) {
                throw new org.springframework.dao.EmptyResultDataAccessException(String.format(
                    "No %s entity with id %s exists (already soft-deleted)!", domainClass.getSimpleName(), id), 1);
            }
            delete(entity.get());
        } else {
            throw new org.springframework.dao.EmptyResultDataAccessException(
                String.format("No %s entity with id %s exists!", domainClass.getSimpleName(), id), 1);
        }
    }

    /**
     * 静默版本的按 ID 删除。实体不存在或已软删除时不抛异常。
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
        deleteById(id);
    }

    /**
     * 覆写 deleteAll() 以支持软删除。使用批量 UPDATE 替代逐条操作以避免 N+1 查询。
     *
     * <p>
     * 当软删除过滤启用时，使用 SoftDeleteHelper.softDeleteAll() 批量更新。 当软删除过滤禁用时，执行标准的硬删除。
     */
    @Override
    public void deleteAll() {
        if (shouldApplySoftDeleteFilter()) {
            if (log.isWarnEnabled()) {
                log.warn("AUDIT: Executing soft DELETE ALL on {} (autoFilter enabled). Call stack: {}",
                    domainClass.getSimpleName(), AuditUtils.getCallStack());
            }
            SoftDeleteHelper.softDeleteAll(entityManager, domainClass, true);
        } else if (shouldBlockHardDelete()) {
            throw new IllegalStateException("Unconditional hard DELETE ALL on " + domainClass.getSimpleName()
                + " is blocked because the entity has a @SoftDelete field. "
                + "Set DefaultMyJpaRepository.setBlockUnconditionalDelete(false) to allow this operation.");
        } else {
            if (SoftDeleteHelper.findSoftDeleteField(domainClass) != null && log.isWarnEnabled()) {
                log.warn("AUDIT: Executing unconditional hard DELETE ALL on {} with @SoftDelete field "
                    + "(autoFilter=false). Call stack: {}", domainClass.getSimpleName(), AuditUtils.getCallStack());
            }
            super.deleteAll();
        }
    }

    /**
     * 覆写 deleteAllById() 以支持软删除。使用批量 UPDATE 替代逐条操作以避免 N+1 查询。
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
                + "Set DefaultMyJpaRepository.setBlockUnconditionalDelete(false) to allow this operation.");
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
            // 使用批量 UPDATE 替代 N+1 次单独删除
            // 使用 PersistenceUnitUtil 替代反射，兼容 Java 17+
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
                + "Set DefaultMyJpaRepository.setBlockUnconditionalDelete(false) to allow this operation.");
        } else {
            super.deleteInBatch(entities);
        }
    }

    /**
     * 覆写 deleteAllInBatch() 以支持软删除。使用批量 UPDATE 替代逐条操作以避免 N+1 查询。
     */
    @Override
    public void deleteAllInBatch() {
        if (shouldApplySoftDeleteFilter()) {
            if (log.isWarnEnabled()) {
                log.warn("AUDIT: Executing soft DELETE ALL IN BATCH on {} (autoFilter enabled). Call stack: {}",
                    domainClass.getSimpleName(), AuditUtils.getCallStack());
            }
            SoftDeleteHelper.softDeleteAll(entityManager, domainClass, true);
        } else if (shouldBlockHardDelete()) {
            throw new IllegalStateException("Unconditional hard DELETE ALL IN BATCH on " + domainClass.getSimpleName()
                + " is blocked because the entity has a @SoftDelete field. "
                + "Set DefaultMyJpaRepository.setBlockUnconditionalDelete(false) to allow this operation.");
        } else {
            super.deleteAllInBatch();
        }
    }

    // ---- 批量操作 Lambda 方法 ----

    @Override
    public int update(Consumer<UpdateSpec<T>> config) {
        UpdateSpec<T> spec = new UpdateSpec<>(domainClass);
        config.accept(spec);
        return spec.executeInTransaction(entityManager);
    }

    @Override
    public int delete(Consumer<DeleteSpec<T>> config) {
        DeleteSpec<T> spec = new DeleteSpec<>(domainClass);
        config.accept(spec);
        return spec.executeInTransaction(entityManager);
    }

    @Override
    public int merge(Consumer<MergeSpec<T>> config) {
        MergeSpec<T> spec = new MergeSpec<>(domainClass);
        config.accept(spec);
        return spec.executeInTransaction(entityManager);
    }

    // ---- 批量操作 execute 方法 ----

    @Override
    public int execute(UpdateSpec<T> spec) {
        if (spec == null) {
            throw new IllegalArgumentException("spec must not be null");
        }
        return spec.executeInTransaction(entityManager);
    }

    @Override
    public int execute(DeleteSpec<T> spec) {
        if (spec == null) {
            throw new IllegalArgumentException("spec must not be null");
        }
        return spec.executeInTransaction(entityManager);
    }

    @Override
    public int execute(MergeSpec<T> spec) {
        if (spec == null) {
            throw new IllegalArgumentException("spec must not be null");
        }
        return spec.executeInTransaction(entityManager);
    }
}
