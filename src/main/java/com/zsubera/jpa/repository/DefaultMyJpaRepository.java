package com.zsubera.jpa.repository;

import com.zsubera.jpa.softdelete.SoftDeleteBulkExecutor;
import com.zsubera.jpa.softdelete.SoftDeleteHelper;
import com.zsubera.jpa.update.AuditUtils;
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
    private final @Nullable String softDeleteFieldName;

    /**
     * 全局配置提供者。通过 {@link #setGlobalConfigProvider(ConfigProvider)} 注入。
     */
    private static volatile ConfigProvider globalConfigProvider;

    /**
     * autoFilter 的 ThreadLocal 覆盖值。设置后（非 null）会覆盖全局配置，支持按请求控制软删除过滤行为。
     *
     * <p>
     * 与 Java 21+ 虚拟线程兼容：每个虚拟线程拥有独立的 ThreadLocal 映射，
     * 因此状态不会在虚拟线程之间泄漏。推荐使用 {@link #withAutoFilterOverride(Boolean, Runnable)}
     * 自动管理生命周期以确保异常安全。
     */
    private static final ThreadLocal<Boolean> AUTO_FILTER_OVERRIDE = new ThreadLocal<>();

    /**
     * 配置提供者接口，用于替代静态可变状态。
     */
    public interface ConfigProvider {
        boolean isAutoFilterEnabled();

        boolean isBlockUnconditionalDelete();
    }

    /**
     * 创建可变的配置提供者实例。
     *
     * @param autoFilter 自动过滤开关初始值
     * @param blockDelete 阻断无条件删除开关初始值
     * @return 可变的配置提供者实例
     */
    public static ConfigProvider createMutableConfigProvider(boolean autoFilter, boolean blockDelete) {
        return new MutableConfigProvider(autoFilter, blockDelete);
    }

    /**
     * 可变的配置提供者实现，支持运行时更新配置值。
     */
    private static class MutableConfigProvider implements ConfigProvider {
        private volatile boolean autoFilter;
        private volatile boolean blockDelete;

        MutableConfigProvider(boolean autoFilter, boolean blockDelete) {
            this.autoFilter = autoFilter;
            this.blockDelete = blockDelete;
        }

        @Override
        public boolean isAutoFilterEnabled() {
            return autoFilter;
        }

        @Override
        public boolean isBlockUnconditionalDelete() {
            return blockDelete;
        }

        void setAutoFilterEnabled(boolean enabled) {
            this.autoFilter = enabled;
        }

        void setBlockUnconditionalDelete(boolean blocked) {
            this.blockDelete = blocked;
        }
    }

    /**
     * 设置全局配置提供者。由自动配置类在启动时调用。
     *
     * @param provider 配置提供者
     */
    public static void setGlobalConfigProvider(ConfigProvider provider) {
        globalConfigProvider = provider;
    }

    private static <R> R resolveGlobalConfig(
        java.util.function.Function<com.zsubera.jpa.autoconfigure.MyJpaPlusGlobalConfig, R> globalGetter,
        java.util.function.Function<ConfigProvider, R> providerGetter, R defaultValue) {
        com.zsubera.jpa.autoconfigure.MyJpaPlusGlobalConfig config =
            com.zsubera.jpa.autoconfigure.GlobalConfigHolder.getConfig();
        if (config != null) {
            return globalGetter.apply(config);
        }
        ConfigProvider provider = globalConfigProvider;
        if (provider != null) {
            return providerGetter.apply(provider);
        }
        return defaultValue;
    }

    /**
     * 获取当前全局自动过滤开关状态。
     *
     * @return 如果自动过滤已启用返回 true
     */
    public static boolean isAutoFilterEnabled() {
        return resolveGlobalConfig(com.zsubera.jpa.autoconfigure.MyJpaPlusGlobalConfig::isSoftDeleteAutoFilter,
            ConfigProvider::isAutoFilterEnabled, true);
    }

    /**
     * 获取当前无条件硬删除阻断开关状态。
     *
     * @return 如果阻断已启用返回 true
     */
    public static boolean isBlockUnconditionalDelete() {
        return resolveGlobalConfig(com.zsubera.jpa.autoconfigure.MyJpaPlusGlobalConfig::isBlockUnconditionalDelete,
            ConfigProvider::isBlockUnconditionalDelete, true);
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
        return isBlockUnconditionalDelete() && softDeleteFieldName != null;
    }

    /**
     * 使用 AUTO_FILTER_OVERRIDE 执行操作，自动在 finally 块中清理。防止异常发生时 ThreadLocal 泄漏。
     *
     * <p>
     * 与 Java 21+ 虚拟线程兼容。每个虚拟线程拥有独立的 ThreadLocal 映射，
     * 因此覆盖状态不会泄漏到其他虚拟线程或平台线程。
     *
     * @param value 覆盖值（null 表示清除）
     * @param action 要执行的操作
     */
    public static void withAutoFilterOverride(Boolean value, Runnable action) {
        runWithOverride(value, () -> {
            action.run();
            return null;
        });
    }

    /**
     * 使用 AUTO_FILTER_OVERRIDE 执行 Supplier，自动在 finally 块中清理。防止异常发生时 ThreadLocal 泄漏。
     *
     * @param value 覆盖值（null 表示清除）
     * @param supplier 要执行的 Supplier
     * @param <R> 返回类型
     * @return Supplier 的返回结果
     */
    public static <R> R withAutoFilterOverride(Boolean value, java.util.function.Supplier<R> supplier) {
        return runWithOverride(value, supplier);
    }

    private static <R> R runWithOverride(Boolean value, java.util.function.Supplier<R> supplier) {
        Boolean previous = AUTO_FILTER_OVERRIDE.get();
        if (value == null) {
            AUTO_FILTER_OVERRIDE.remove();
        } else {
            AUTO_FILTER_OVERRIDE.set(value);
        }
        try {
            return supplier.get();
        } finally {
            if (previous != null) {
                AUTO_FILTER_OVERRIDE.set(previous);
            } else {
                AUTO_FILTER_OVERRIDE.remove();
            }
        }
    }

    /**
     * 清除当前线程的 AUTO_FILTER_OVERRIDE ThreadLocal 值。 在应用关闭或 ServletContext 销毁时调用以防止 ThreadLocal 泄漏。
     *
     * <p>
     * 注意：在虚拟线程环境下，每个虚拟线程的 ThreadLocal 是独立的，
     * 因此此方法仅清除当前虚拟线程/平台线程的状态。
     */
    public static void clearThreadLocal() {
        AUTO_FILTER_OVERRIDE.remove();
    }

    /**
     * ponytail: 在事务完成后自动清理 AUTO_FILTER_OVERRIDE ThreadLocal，防止泄漏。
     * 当使用 withAutoFilterOverride() 时，try/finally 已保证清理。
     * 此方法作为安全网，处理直接设置 ThreadLocal 但忘记清理的场景。
     */
    public static void registerTransactionCleanup() {
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()) {
            org.springframework.transaction.support.TransactionSynchronizationManager
                .registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        AUTO_FILTER_OVERRIDE.remove();
                    }
                });
        }
    }

    @SuppressFBWarnings(value = "EI_EXPOSE_REP2",
        justification = "Stores Spring-managed EntityManager and JpaEntityInformation; no defensive copy appropriate")
    public DefaultMyJpaRepository(JpaEntityInformation<T, ?> entityInformation, EntityManager entityManager) {
        super(entityInformation, entityManager);
        this.domainClass = entityInformation.getJavaType();
        this.entityManager = entityManager;
        this.entityInformation = entityInformation;
        this.softDeleteFieldName = SoftDeleteHelper.findSoftDeleteField(domainClass);
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
        Boolean override = AUTO_FILTER_OVERRIDE.get();
        boolean effectiveAutoFilter = (override != null) ? override : isAutoFilterEnabled();
        return effectiveAutoFilter && softDeleteFieldName != null && !SoftDeleteContext.isIgnoreSoftDelete();
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
    /**
     * 构建按单个 ID 匹配并合并软删除过滤器的 Specification。
     *
     * @param id 实体 ID
     * @return 合并后的 Specification
     */
    private Specification<T> withIdAndSoftDelete(ID id) {
        String idFieldName = EntityClassResolver.resolveIdFieldName(domainClass);
        Specification<T> idSpec = Specification.where((root, query, cb) -> cb.equal(root.get(idFieldName), id));
        Specification<T> softDeleteSpec = SoftDeleteHelper.isNotDeleted(domainClass);
        return softDeleteSpec != null ? idSpec.and(softDeleteSpec) : idSpec;
    }

    @Override
    public Optional<T> findById(ID id) {
        if (id == null) {
            return Optional.empty();
        }
        if (!shouldApplySoftDeleteFilter()) {
            return Optional.ofNullable(entityManager.find(domainClass, id));
        }
        return findOne(withIdAndSoftDelete(id));
    }

    @Override
    public boolean existsById(ID id) {
        if (id == null) {
            return false;
        }
        if (!shouldApplySoftDeleteFilter()) {
            return super.existsById(id);
        }
        return exists(withIdAndSoftDelete(id));
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
        Specification<T> idSpec = Specification
            .where((root, query, cb) -> com.zsubera.jpa.util.InClauseBuilder.in(cb, root.get(idFieldName), idList));
        Specification<T> softDeleteSpec = SoftDeleteHelper.isNotDeleted(domainClass);
        if (softDeleteSpec == null) {
            return super.findAll(idSpec);
        }
        return super.findAll(idSpec.and(softDeleteSpec));
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
        if (!shouldApplySoftDeleteFilter()) {
            if (shouldBlockHardDelete()) {
                throw new IllegalStateException("Hard DELETE on " + domainClass.getSimpleName()
                    + " is blocked because the entity has a @SoftDelete field. "
                    + "Set DefaultMyJpaRepository.setBlockUnconditionalDelete(false) to allow this operation.");
            }
            String idFieldName = EntityClassResolver.resolveIdFieldName(domainClass);
            jakarta.persistence.criteria.CriteriaBuilder cb = entityManager.getCriteriaBuilder();
            jakarta.persistence.criteria.CriteriaDelete<T> delete = cb.createCriteriaDelete(domainClass);
            jakarta.persistence.criteria.Root<T> root = delete.from(domainClass);
            delete.where(cb.equal(root.get(idFieldName), id));
            int deleted = entityManager.createQuery(delete).executeUpdate();
            return deleted > 0;
        }
        @SuppressWarnings("unchecked")
        ID castId = id;
        int updated = SoftDeleteBulkExecutor.softDeleteByIds(entityManager, domainClass, java.util.List.of(castId));
        return updated > 0;
    }

    /**
     * 执行删除操作的统一入口：软删除 → 硬删除阻断 → 硬删除回退。
     *
     * <p>
     * 三路分支逻辑：
     * <ol>
     * <li>软删除过滤启用 → 执行 {@code softAction}（如批量 UPDATE deleted=true）</li>
     * <li>实体有 @SoftDelete 且阻断开关启用 → 抛出 IllegalStateException</li>
     * <li>其他情况 → 执行 {@code hardAction}（如标准 DELETE）</li>
     * </ol>
     *
     * @param softAction 软删除操作（在 shouldApplySoftDeleteFilter() 为 true 时执行）
     * @param hardAction 硬删除回退操作（在不满足软删除和阻断条件时执行）
     */
    private void executeDeleteOrBlock(Runnable softAction, Runnable hardAction) {
        if (shouldApplySoftDeleteFilter()) {
            softAction.run();
        } else if (shouldBlockHardDelete()) {
            throw new IllegalStateException("Unconditional hard DELETE on " + domainClass.getSimpleName()
                + " is blocked because the entity has a @SoftDelete field. "
                + "Set DefaultMyJpaRepository.setBlockUnconditionalDelete(false) to allow this operation.");
        } else {
            if (softDeleteFieldName != null && log.isWarnEnabled()) {
                log.warn("AUDIT: Executing unconditional hard DELETE on {} with @SoftDelete field "
                    + "(autoFilter=false). Call stack: {}", domainClass.getSimpleName(), AuditUtils.getCallStack());
            }
            hardAction.run();
        }
    }

    /**
     * 覆写 deleteAll() 以支持软删除。使用批量 UPDATE 替代逐条操作以避免 N+1 查询。
     *
     * <p>
     * 当软删除过滤启用时，使用 SoftDeleteHelper.softDeleteAll() 批量更新。 当软删除过滤禁用时，执行标准的硬删除。
     */
    @Override
    public void deleteAll() {
        executeDeleteOrBlock(() -> {
            if (log.isWarnEnabled()) {
                log.warn("AUDIT: Executing soft DELETE ALL on {} (autoFilter enabled). Call stack: {}",
                    domainClass.getSimpleName(), AuditUtils.getCallStack());
            }
            SoftDeleteBulkExecutor.softDeleteAll(entityManager, domainClass, true);
        }, super::deleteAll);
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
        executeDeleteOrBlock(() -> SoftDeleteBulkExecutor.softDeleteByIds(entityManager, domainClass, idList),
            () -> super.deleteAllById(idList));
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
        executeDeleteOrBlock(() -> {
            java.util.List<ID> idList = new java.util.ArrayList<>();
            jakarta.persistence.PersistenceUnitUtil util =
                entityManager.getEntityManagerFactory().getPersistenceUnitUtil();
            for (T entity : entities) {
                @SuppressWarnings("unchecked")
                ID id = (ID)util.getIdentifier(entity);
                if (id != null) {
                    idList.add(id);
                } else {
                    log.warn("deleteInBatch: Could not extract ID from entity {}. "
                        + "Entity may be detached. Load the entity within a transaction before calling deleteInBatch().",
                        entity.getClass().getSimpleName());
                }
            }
            if (!idList.isEmpty()) {
                SoftDeleteBulkExecutor.softDeleteByIds(entityManager, domainClass, idList);
            }
        }, () -> super.deleteInBatch(entities));
    }

    /**
     * 覆写 deleteAllInBatch() 以支持软删除。使用批量 UPDATE 替代逐条操作以避免 N+1 查询。
     */
    @Override
    public void deleteAllInBatch() {
        executeDeleteOrBlock(() -> {
            if (log.isWarnEnabled()) {
                log.warn("AUDIT: Executing soft DELETE ALL IN BATCH on {} (autoFilter enabled). Call stack: {}",
                    domainClass.getSimpleName(), AuditUtils.getCallStack());
            }
            SoftDeleteBulkExecutor.softDeleteAll(entityManager, domainClass, true);
        }, super::deleteAllInBatch);
    }

    /**
     * 覆写 deleteById(ID) 以支持软删除过滤。
     *
     * <p>
     * 默认的 {@link SimpleJpaRepository#deleteById(Object)} 直接调用 {@code entityManager.remove()}，
     * 绕过软删除逻辑。此实现通过 {@link #executeDeleteOrBlock} 统一处理软删除/硬删除/阻断三路分支。
     *
     * @param id 要删除的实体 ID
     */
    @Override
    public void deleteById(ID id) {
        if (id == null) {
            throw new IllegalArgumentException("ID must not be null");
        }
        executeDeleteOrBlock(
            () -> SoftDeleteBulkExecutor.softDeleteByIds(entityManager, domainClass, java.util.List.of(id)),
            () -> super.deleteById(id));
    }
}
