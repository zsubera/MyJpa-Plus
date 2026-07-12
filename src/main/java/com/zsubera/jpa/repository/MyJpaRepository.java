package com.zsubera.jpa.repository;

import com.zsubera.jpa.spec.QuerySpec;
import com.zsubera.jpa.update.DeleteSpec;
import com.zsubera.jpa.update.MergeSpec;
import com.zsubera.jpa.update.UpdateSpec;
import com.zsubera.jpa.util.EntityClassResolver;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.NoRepositoryBean;

/**
 * 用户面向的仓库接口，结合了 {@link JpaRepository} 和 {@link JpaSpecificationExecutor}，
 * 实现类为 {@code DefaultMyJpaRepository}。
 *
 * <p>
 * 查询用法（Lambda 模式，推荐）：
 *
 * <pre>{@code
 * public interface UserRepository extends MyJpaRepository<User, Long> {}
 *
 * List<User> users = repository.findAll(s -> s.eq(User::getStatus, "ACTIVE"));
 * Optional<User> user = repository.findOne(s -> s.eq(User::getEmail, "john@example.com"));
 * long count = repository.count(s -> s.eq(User::getStatus, "ACTIVE"));
 * }</pre>
 *
 * <p>
 * 查询用法（QuerySpec 模式）：
 *
 * <pre>{@code
 * List<User> users = repository.findAll(new QuerySpec<User>().eq(User::getStatus, "ACTIVE"));
 * }</pre>
 *
 * <p>
 * 批量操作用法（Lambda 模式）：
 *
 * <pre>{@code
 * repository.update(s -> s.set(User::getStatus, "INACTIVE").eq(User::getStatus, "ACTIVE"));
 * repository.delete(s -> s.lt(User::getCreatedAt, cutoffDate));
 * repository.merge(s -> s.withEntity(user).onConflict(User::getEmail).updateOnConflict(User::getName));
 * }</pre>
 *
 * <p>
 * 批量操作用法（execute 模式）：
 *
 * <pre>{@code
 * repository.execute(new UpdateSpec<>(User.class).set(User::getStatus, "INACTIVE"));
 * }</pre>
 *
 * @param <T> 实体类型
 * @param <ID> 实体ID类型
 */
@NoRepositoryBean
public interface MyJpaRepository<T, ID> extends JpaRepository<T, ID>, JpaSpecificationExecutor<T> {

    /** 缓存 getEntityClass() 的结果，避免每次调用都执行 EntityClassResolver.resolve()。 */
    com.zsubera.jpa.util.SampledEvictionCache<Class<?>, Class<?>> ENTITY_CLASS_CACHE =
        new com.zsubera.jpa.util.SampledEvictionCache<>(256, 0.75, 100, 64);

    /**
     * 使用 Lambda 表达式查找所有匹配的实体。
     *
     * <pre>{@code
     * repository.findAll(s -> s.eq(User::getStatus, "ACTIVE").orderByAsc(User::getName));
     * }</pre>
     *
     * @param config 查询条件配置
     * @return 匹配实体列表
     */
    default List<T> findAll(Consumer<QuerySpec<T>> config) {
        return findAll(QuerySpec.of(config));
    }

    /**
     * 查找所有匹配给定 {@link Specification} 的实体。
     *
     * <p>
     * <strong>安全建议：</strong>此方法不限制返回结果数量，可能导致大数据集查询时的内存溢出（OOM）。 在生产环境中，推荐使用
     * {@link com.zsubera.jpa.template.MyJpaTemplate#findAll(Class, com.zsubera.jpa.spec.QuerySpec)}
     * 进行查询，它提供了可配置的最大行数限制（默认 10000 条）。 或使用分页查询 {@link #findAll(Specification, Pageable)} 限制结果数量。
     *
     * @param spec 查询规格说明
     * @return 匹配实体列表
     */
    @Override
    List<T> findAll(Specification<T> spec);

    /**
     * 使用 Lambda 表达式分页查找所有匹配的实体。
     *
     * @param config 查询条件配置
     * @param pageable 分页参数
     * @return 包含匹配实体的分页结果
     */
    default Page<T> findAll(Consumer<QuerySpec<T>> config, Pageable pageable) {
        return findAll(QuerySpec.of(config), pageable);
    }

    /**
     * 分页查找所有匹配给定 {@link Specification} 的实体。
     *
     * @param spec 查询规格说明
     * @param pageable 分页参数
     * @return 包含匹配实体的分页结果
     */
    @Override
    Page<T> findAll(Specification<T> spec, Pageable pageable);

    /**
     * 使用 Lambda 表达式排序查找所有匹配的实体。
     *
     * @param config 查询条件配置
     * @param sort 排序参数
     * @return 匹配实体列表
     */
    default List<T> findAll(Consumer<QuerySpec<T>> config, Sort sort) {
        return findAll(QuerySpec.of(config), sort);
    }

    /**
     * 排序查找所有匹配给定 {@link Specification} 的实体。
     *
     * @param spec 查询规格说明
     * @param sort 排序参数
     * @return 匹配实体列表
     */
    @Override
    List<T> findAll(Specification<T> spec, Sort sort);

    /**
     * 使用 Lambda 表达式查找单个匹配的实体。
     *
     * @param config 查询条件配置
     * @return 匹配实体的 Optional 包装
     */
    default Optional<T> findOne(Consumer<QuerySpec<T>> config) {
        return findOne(QuerySpec.of(config));
    }

    /**
     * 查找单个匹配给定 {@link Specification} 的实体。
     *
     * @param spec 查询规格说明
     * @return 匹配实体的 Optional 包装
     */
    @Override
    Optional<T> findOne(Specification<T> spec);

    /**
     * 使用 Lambda 表达式统计匹配的实体数量。
     *
     * @param config 查询条件配置
     * @return 匹配实体数量
     */
    default long count(Consumer<QuerySpec<T>> config) {
        return count(QuerySpec.of(config));
    }

    /**
     * 统计匹配给定 {@link Specification} 的实体数量。
     *
     * @param spec 查询规格说明
     * @return 匹配实体数量
     */
    @Override
    long count(Specification<T> spec);

    /**
     * 使用 Lambda 表达式检查是否存在匹配的实体。
     *
     * @param config 查询条件配置
     * @return 如果存在匹配实体返回 true，否则返回 false
     */
    default boolean exists(Consumer<QuerySpec<T>> config) {
        return exists(QuerySpec.of(config));
    }

    /**
     * 检查是否存在匹配给定 {@link Specification} 的实体。
     *
     * @param spec 查询规格说明
     * @return 如果存在匹配实体返回 true，否则返回 false
     */
    @Override
    boolean exists(Specification<T> spec);

    // ---- 批量操作方法 ----
    // 这些 default 方法通过 EntityManagerHelper 获取事务性 EntityManager。
    // 开发者可直接调用：repository.update(s -> s.set(...));

    /**
     * 批量更新实体。使用 Lambda 表达式配置更新条件和操作。
     *
     * <p><strong>事务要求：</strong>调用方必须在 {@code @Transactional} 上下文中执行此方法，
     * 否则会抛出 {@link IllegalStateException}。
     *
     * <pre>{@code
     * @Transactional
     * public int deactivateUsers() {
     *     return repository.update(s -> s.set(User::getStatus, "INACTIVE").eq(User::getStatus, "ACTIVE"));
     * }
     * }</pre>
     *
     * @param config 更新配置
     * @return 受影响的行数
     * @throws IllegalStateException 如果无活动事务
     */
    default int update(Consumer<UpdateSpec<T>> config) {
        if (config == null) {
            throw new IllegalArgumentException("config must not be null");
        }
        Class<T> entityClass = getEntityClass();
        UpdateSpec<T> spec = new UpdateSpec<>(entityClass);
        config.accept(spec);
        jakarta.persistence.EntityManager em = EntityManagerHelper.getTransactionalEntityManager(entityClass);
        String softDeleteField = com.zsubera.jpa.softdelete.SoftDeleteHelper.findSoftDeleteField(entityClass);
        if (softDeleteField != null && isSoftDeleteAutoFilterEnabled()
            && !com.zsubera.jpa.repository.SoftDeleteContext.isIgnoreSoftDelete()) {
            spec.addCondition((root, cb) -> com.zsubera.jpa.softdelete.SoftDeleteHelper.buildNotDeleted(cb, root,
                softDeleteField, entityClass));
        }
        return spec.executeInTransaction(em);
    }

    /**
     * 批量删除实体。使用 Lambda 表达式配置删除条件。
     *
     * <p><strong>事务要求：</strong>调用方必须在 {@code @Transactional} 上下文中执行此方法。
     *
     * <pre>{@code
     * @Transactional
     * public int deleteOldRecords() {
     *     return repository.delete(s -> s.lt(User::getCreatedAt, cutoffDate));
     * }
     * }</pre>
     *
     * @param config 删除配置
     * @return 受影响的行数
     * @throws IllegalStateException 如果无活动事务
     */
    default int delete(Consumer<DeleteSpec<T>> config) {
        if (config == null) {
            throw new IllegalArgumentException("config must not be null");
        }
        Class<T> entityClass = getEntityClass();
        DeleteSpec<T> spec = new DeleteSpec<>(entityClass);
        config.accept(spec);
        jakarta.persistence.EntityManager em = EntityManagerHelper.getTransactionalEntityManager(entityClass);
        String softDeleteField = com.zsubera.jpa.softdelete.SoftDeleteHelper.findSoftDeleteField(entityClass);
        if (softDeleteField != null && isSoftDeleteAutoFilterEnabled()
            && !com.zsubera.jpa.repository.SoftDeleteContext.isIgnoreSoftDelete()) {
            java.lang.reflect.Field field =
                com.zsubera.jpa.softdelete.SoftDeleteHelper.getField(entityClass, softDeleteField);
            if (field == null) {
                throw new IllegalStateException("SoftDelete field '" + softDeleteField + "' not found on "
                    + entityClass.getName() + ". The field may have been removed or renamed.");
            }
            com.zsubera.jpa.annotation.SoftDelete annotation =
                field.getAnnotation(com.zsubera.jpa.annotation.SoftDelete.class);
            com.zsubera.jpa.softdelete.SoftDeleteHelper.ResolvedDeletedValue resolved =
                com.zsubera.jpa.softdelete.SoftDeleteHelper.resolveDeletedValue(entityClass, field, annotation);
            Object deletedVal = resolved.booleanField() ? Boolean.TRUE : resolved.dbValue();
            return spec.executeAsSoftDelete(em, softDeleteField, deletedVal);
        }
        if (softDeleteField != null && isBlockUnconditionalDelete()) {
            throw new IllegalStateException("Hard DELETE on " + entityClass.getSimpleName()
                + " is blocked because the entity has a @SoftDelete field. "
                + "Set myjpa-plus.soft-delete.auto-filter=false and "
                + "myjpa-plus.soft-delete.block-unconditional-delete=false to allow this operation.");
        }
        return spec.executeInTransaction(em);
    }

    /**
     * 批量合并（upsert）实体。使用 Lambda 表达式配置合并策略。
     *
     * <p><strong>事务要求：</strong>调用方必须在 {@code @Transactional} 上下文中执行此方法。
     *
     * <pre>{@code
     * @Transactional
     * public int upsertUser() {
     *     return repository.merge(s -> s.withEntity(user).onConflict(User::getEmail).updateOnConflict(User::getName));
     * }
     * }</pre>
     *
     * @param config 合并配置
     * @return 受影响的行数
     * @throws IllegalStateException 如果无活动事务
     */
    default int merge(Consumer<MergeSpec<T>> config) {
        if (config == null) {
            throw new IllegalArgumentException("config must not be null");
        }
        Class<T> entityClass = getEntityClass();
        MergeSpec<T> spec = new MergeSpec<>(entityClass);
        config.accept(spec);
        return spec.executeInTransaction(EntityManagerHelper.getTransactionalEntityManager(entityClass));
    }

    /**
     * 执行预构建的更新操作。
     *
     * <p><strong>事务要求：</strong>调用方必须在 {@code @Transactional} 上下文中执行此方法。
     *
     * @param spec 预构建的更新规格
     * @return 受影响的行数
     * @throws IllegalStateException 如果无活动事务
     */
    default int execute(UpdateSpec<T> spec) {
        if (spec == null) {
            throw new IllegalArgumentException("spec must not be null");
        }
        Class<T> entityClass = getEntityClass();
        jakarta.persistence.EntityManager em = EntityManagerHelper.getTransactionalEntityManager(entityClass);
        String softDeleteField = com.zsubera.jpa.softdelete.SoftDeleteHelper.findSoftDeleteField(entityClass);
        if (softDeleteField != null && isSoftDeleteAutoFilterEnabled()
            && !com.zsubera.jpa.repository.SoftDeleteContext.isIgnoreSoftDelete()) {
            spec.addCondition((root, cb) -> com.zsubera.jpa.softdelete.SoftDeleteHelper.buildNotDeleted(cb, root,
                softDeleteField, entityClass));
        }
        return spec.executeInTransaction(em);
    }

    /**
     * 执行预构建的删除操作。
     *
     * <p><strong>事务要求：</strong>调用方必须在 {@code @Transactional} 上下文中执行此方法。
     *
     * @param spec 预构建的删除规格
     * @return 受影响的行数
     * @throws IllegalStateException 如果无活动事务
     */
    default int execute(DeleteSpec<T> spec) {
        if (spec == null) {
            throw new IllegalArgumentException("spec must not be null");
        }
        Class<T> entityClass = getEntityClass();
        jakarta.persistence.EntityManager em = EntityManagerHelper.getTransactionalEntityManager(entityClass);
        String softDeleteField = com.zsubera.jpa.softdelete.SoftDeleteHelper.findSoftDeleteField(entityClass);
        if (softDeleteField != null && isSoftDeleteAutoFilterEnabled()
            && !com.zsubera.jpa.repository.SoftDeleteContext.isIgnoreSoftDelete()) {
            java.lang.reflect.Field field =
                com.zsubera.jpa.softdelete.SoftDeleteHelper.getField(entityClass, softDeleteField);
            if (field == null) {
                throw new IllegalStateException("SoftDelete field '" + softDeleteField + "' not found on "
                    + entityClass.getName() + ". The field may have been removed or renamed.");
            }
            com.zsubera.jpa.annotation.SoftDelete annotation =
                field.getAnnotation(com.zsubera.jpa.annotation.SoftDelete.class);
            com.zsubera.jpa.softdelete.SoftDeleteHelper.ResolvedDeletedValue resolved =
                com.zsubera.jpa.softdelete.SoftDeleteHelper.resolveDeletedValue(entityClass, field, annotation);
            Object deletedVal = resolved.booleanField() ? Boolean.TRUE : resolved.dbValue();
            return spec.executeAsSoftDelete(em, softDeleteField, deletedVal);
        }
        if (softDeleteField != null && isBlockUnconditionalDelete()) {
            throw new IllegalStateException("execute(DeleteSpec) on " + entityClass.getSimpleName()
                + " bypasses soft-delete filtering. The entity has a @SoftDelete field and "
                + "blockUnconditionalDelete is enabled. Use delete() or soft-delete API instead.");
        }
        return spec.executeInTransaction(em);
    }

    /**
     * 执行预构建的合并操作。
     *
     * <p><strong>事务要求：</strong>调用方必须在 {@code @Transactional} 上下文中执行此方法。
     *
     * @param spec 预构建的合并规格
     * @return 受影响的行数
     * @throws IllegalStateException 如果无活动事务
     */
    default int execute(MergeSpec<T> spec) {
        if (spec == null) {
            throw new IllegalArgumentException("spec must not be null");
        }
        return spec.executeInTransaction(EntityManagerHelper.getTransactionalEntityManager(getEntityClass()));
    }

    private static boolean isSoftDeleteAutoFilterEnabled() {
        return DefaultMyJpaRepository.isAutoFilterEnabled();
    }

    private static boolean isBlockUnconditionalDelete() {
        return DefaultMyJpaRepository.isBlockUnconditionalDelete();
    }

    /**
     * 返回与此仓库关联的领域类。结果按仓库类缓存以避免重复反射。
     *
     * @return 实体类
     * @throws IllegalStateException 如果无法解析实体类
     */
    @SuppressWarnings("unchecked")
    private Class<T> getEntityClass() {
        Class<?> repoClass = getClass();
        return (Class<T>)ENTITY_CLASS_CACHE.computeIfAbsent(repoClass, c -> {
            Class<T> entityClass = EntityClassResolver.resolve(c);
            if (entityClass == null) {
                throw new IllegalStateException("Cannot resolve entity class for repository: " + c.getName());
            }
            return entityClass;
        });
    }
}
