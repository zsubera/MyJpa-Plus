package com.zsubera.jpa.repository;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.lang.Nullable;
import org.springframework.orm.jpa.EntityManagerFactoryUtils;

/**
 * 静态持有者，提供对当前事务性 {@link EntityManager} 的访问。
 *
 * <p>
 * 由 {@code MyJpaPlusAutoConfiguration} 初始化。用于 {@link MyJpaRepository} 默认方法中的批量操作，
 * 使它们无需 Spring Data 自定义工厂即可工作。
 *
 * <p>
 * <strong>多数据源支持：</strong>此类支持按实体类型解析不同的 {@link EntityManagerFactory}。
 * 通过 {@link #registerResolver(Class, EntityManagerResolver)} 或
 * {@link #registerEntityManagerFactory(Class, EntityManagerFactory)} 注册实体类型到 EMF 的映射。
 * 未注册的实体类型将回退到默认 EMF（单数据源场景）。
 *
 * <p>
 * <strong>向后兼容：</strong>单数据源场景无需任何额外配置，现有代码正常工作。
 */
public final class EntityManagerHelper {

    private static volatile EntityManagerFactory defaultEntityManagerFactory;

    /**
     * 实体类型到解析器的映射。优先级高于 defaultEntityManagerFactory。
     */
    private static final ConcurrentHashMap<Class<?>, EntityManagerResolver> resolvers = new ConcurrentHashMap<>();

    /**
     * 标记是否所有 resolver 都指向默认 EMF（单数据源场景）。
     * 为 true 时 resolveEntityManagerFactory 可跳过 resolver 查询，直接返回默认 EMF。
     * 在 register/remove 时更新。
     */
    private static volatile boolean allResolversUseDefault = true;

    private EntityManagerHelper() {}

    // ---- 初始化 ----

    /**
     * 由自动配置调用以设置默认 {@link EntityManagerFactory}（单数据源场景）。
     *
     * @param emf 默认的 EntityManagerFactory
     */
    @SuppressFBWarnings(value = "EI_EXPOSE_STATIC_REP2",
        justification = "EntityManagerFactory is thread-safe and stateless")
    public static void setEntityManagerFactory(EntityManagerFactory emf) {
        defaultEntityManagerFactory = emf;
    }

    // ---- 多数据源注册 ----

    /**
     * 按实体类型注册自定义解析器（多数据源场景）。
     *
     * <p>
     * 注册后，调用 {@link #getTransactionalEntityManager(Class)} 时将使用此解析器解析对应的 EMF。
     *
     * @param entityType 实体类类型
     * @param resolver 解析器
     * @throws IllegalArgumentException 如果 entityType 或 resolver 为 null
     */
    public static void registerResolver(Class<?> entityType, EntityManagerResolver resolver) {
        Objects.requireNonNull(entityType, "entityType must not be null");
        Objects.requireNonNull(resolver, "resolver must not be null");
        resolvers.put(entityType, resolver);
        allResolversUseDefault = false;
    }

    /**
     * 按实体类型直接注册 {@link EntityManagerFactory}（多数据源场景的便捷 API）。
     *
     * <p>
     * 等价于 {@code registerResolver(entityType, type -> emf)}。
     *
     * @param entityType 实体类类型
     * @param emf 对应的 EntityManagerFactory
     * @throws IllegalArgumentException 如果 entityType 或 emf 为 null
     */
    public static void registerEntityManagerFactory(Class<?> entityType, EntityManagerFactory emf) {
        Objects.requireNonNull(entityType, "entityType must not be null");
        Objects.requireNonNull(emf, "entityManagerFactory must not be null");
        resolvers.put(entityType, type -> emf);
        // 如果注册的 EMF 与默认 EMF 不同，标记为非单数据源
        if (defaultEntityManagerFactory == null || emf != defaultEntityManagerFactory) {
            allResolversUseDefault = false;
        }
    }

    /**
     * 仅在尚未注册时，按实体类型注册 {@link EntityManagerFactory}。
     *
     * <p>
     * 用于 {@link MyJpaRepositoryFactoryBean} 在启动时自动注册实体类型到 EMF 的映射，
     * 不覆盖用户手动注册的解析器。
     *
     * @param entityType 实体类类型
     * @param emf 对应的 EntityManagerFactory
     */
    public static void registerEntityManagerFactoryIfAbsent(Class<?> entityType, EntityManagerFactory emf) {
        Objects.requireNonNull(entityType, "entityType must not be null");
        Objects.requireNonNull(emf, "entityManagerFactory must not be null");
        resolvers.putIfAbsent(entityType, type -> emf);
        // 如果注册的 EMF 与默认 EMF 相同，保持 allResolversUseDefault 标志
        // putIfAbsent 可能未实际插入（已存在），此时不修改标志
    }

    /**
     * 移除指定实体类型的解析器注册。
     *
     * @param entityType 实体类类型
     */
    public static void removeResolver(Class<?> entityType) {
        Objects.requireNonNull(entityType, "entityType must not be null");
        resolvers.remove(entityType);
        // 移除后重新检查是否所有 resolver 都指向默认 EMF
        recheckAllResolversUseDefault();
    }

    /**
     * 重新检查 allResolversUseDefault 标志。移除 resolver 后调用。
     */
    private static void recheckAllResolversUseDefault() {
        if (resolvers.isEmpty()) {
            allResolversUseDefault = true;
            return;
        }
        EntityManagerFactory defaultEmf = defaultEntityManagerFactory;
        if (defaultEmf == null) {
            allResolversUseDefault = false;
            return;
        }
        for (EntityManagerResolver resolver : resolvers.values()) {
            if (resolver.resolve(null) != defaultEmf) {
                allResolversUseDefault = false;
                return;
            }
        }
        allResolversUseDefault = true;
    }

    // ---- EM 获取 ----

    /**
     * 返回绑定到当前 JPA 事务的 {@link EntityManager}（使用默认 EMF）。
     *
     * <p>
     * 向后兼容：单数据源场景无需修改。
     *
     * @return 当前事务的 EntityManager
     * @throws IllegalStateException 如果 EMF 未初始化或无活动事务
     */
    public static EntityManager getTransactionalEntityManager() {
        return getTransactionalEntityManager((Class<?>)null);
    }

    /**
     * 按实体类型返回绑定到当前 JPA 事务的 {@link EntityManager}（多数据源场景）。
     *
     * <p>
     * 解析优先级：
     * <ol>
     * <li>实体类型特定的 resolver（通过 {@link #registerResolver} 注册）</li>
     * <li>默认 EMF（通过 {@link #setEntityManagerFactory} 设置）</li>
     * </ol>
     *
     * @param entityType 实体类类型，用于解析对应的 EMF。如果为 null，则使用默认 EMF。
     * @return 当前事务的 EntityManager
     * @throws IllegalStateException 如果 EMF 未初始化或无活动事务
     */
    public static EntityManager getTransactionalEntityManager(@Nullable Class<?> entityType) {
        EntityManagerFactory emf = resolveEntityManagerFactory(entityType);
        EntityManager em = EntityManagerFactoryUtils.getTransactionalEntityManager(emf);
        if (em == null) {
            throw new IllegalStateException(
                "No transactional EntityManager available. Ensure the operation is running within a transaction.");
        }
        return em;
    }

    /**
     * 按实体实例返回绑定到当前 JPA 事务的 {@link EntityManager}。
     *
     * <p>
     * 通过 {@code entity.getClass()} 解析实体类型，然后调用
     * {@link #getTransactionalEntityManager(Class)}。
     *
     * @param entity 实体实例
     * @return 当前事务的 EntityManager
     * @throws IllegalArgumentException 如果 entity 为 null
     */
    public static EntityManager getTransactionalEntityManager(Object entity) {
        Objects.requireNonNull(entity, "entity must not be null");
        return getTransactionalEntityManager(entity.getClass());
    }

    // ---- 内部解析逻辑 ----

    /**
     * 解析实体类型对应的 {@link EntityManagerFactory}。
     *
     * @param entityType 实体类类型
     * @return 解析到的 EntityManagerFactory
     * @throws IllegalStateException 如果未找到 EMF
     */
    private static EntityManagerFactory resolveEntityManagerFactory(@Nullable Class<?> entityType) {
        // 快速路径：单数据源场景，所有 resolver 都指向默认 EMF，跳过 ConcurrentHashMap 查询
        if (allResolversUseDefault) {
            EntityManagerFactory emf = defaultEntityManagerFactory;
            if (emf == null) {
                throw new IllegalStateException(
                    "EntityManagerFactory not initialized. Ensure MyJpaPlusAutoConfiguration is registered.");
            }
            return emf;
        }

        // 1. 优先：实体类型特定的 resolver
        if (entityType != null) {
            EntityManagerResolver resolver = resolvers.get(entityType);
            if (resolver != null) {
                return resolver.resolve(entityType);
            }
        }

        // 2. fallback：默认 EMF
        EntityManagerFactory emf = defaultEntityManagerFactory;
        if (emf == null) {
            throw new IllegalStateException(
                "EntityManagerFactory not initialized. Ensure MyJpaPlusAutoConfiguration is registered.");
        }
        return emf;
    }

    // ---- 清理 ----

    /**
     * 清理所有注册的 resolver 和默认 EMF。用于应用关闭时的资源清理。
     */
    public static void reset() {
        defaultEntityManagerFactory = null;
        resolvers.clear();
        allResolversUseDefault = true;
    }
}
