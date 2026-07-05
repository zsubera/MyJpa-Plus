package com.zsubera.jpa.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(EntityManagerHelper.class);

    private static volatile EntityManagerFactory defaultEntityManagerFactory;

    /**
     * Spring ApplicationContext 引用，用于延迟解析 EMF。
     * 当 defaultEntityManagerFactory 为 null 时（例如 MyJpaRepositoryFactoryBean 尚未创建），
     * 可通过 ApplicationContext 获取。
     */
    private static volatile org.springframework.context.ApplicationContext applicationContext;

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

    /**
     * 用于保护 allResolversUseDefault 标志的更新。
     * 解决 recheckAllResolversUseDefault() 迭代中并发 registerResolver() 导致标志被错误覆盖的问题。
     */
    private static final Object resolverCheckLock = new Object();

    private EntityManagerHelper() {}

    // ---- 初始化 ----

    /**
     * 由自动配置调用以设置默认 {@link EntityManagerFactory}（单数据源场景）。
     *
     * @param emf 默认的 EntityManagerFactory
     */
    public static void setEntityManagerFactory(EntityManagerFactory emf) {
        defaultEntityManagerFactory = emf;
    }

    /**
     * 设置 Spring ApplicationContext。由 {@link com.zsubera.jpa.autoconfigure.MyJpaPlusAutoConfiguration} 在启动时调用。
     *
     * <p>
     * 当 {@link #setEntityManagerFactory} 尚未被调用时（例如 MyJpaRepositoryFactoryBean 尚未创建），
     * 可通过 ApplicationContext 按需获取 EntityManagerFactory。
     *
     * @param ctx Spring 应用上下文
     */
    public static void setApplicationContext(org.springframework.context.ApplicationContext ctx) {
        applicationContext = ctx;
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
        boolean usesNonDefault = true;
        EntityManagerFactory defaultEmf = defaultEntityManagerFactory;
        if (defaultEmf != null) {
            try {
                usesNonDefault = resolver.resolve(entityType) != defaultEmf;
            } catch (Exception e) {
                usesNonDefault = true;
            }
        }
        synchronized (resolverCheckLock) {
            resolvers.put(entityType, resolver);
            if (usesNonDefault) {
                allResolversUseDefault = false;
            }
        }
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
        synchronized (resolverCheckLock) {
            resolvers.put(entityType, type -> emf);
            // 如果注册的 EMF 与默认 EMF 不同，标记为非单数据源
            if (defaultEntityManagerFactory == null || emf != defaultEntityManagerFactory) {
                allResolversUseDefault = false;
            }
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
        synchronized (resolverCheckLock) {
            boolean wasAbsent = resolvers.putIfAbsent(entityType, type -> emf) == null;
            // 仅当实际插入了新 resolver 且该 EMF 与默认 EMF 不同时，标记为非单数据源
            if (wasAbsent && defaultEntityManagerFactory != null && emf != defaultEntityManagerFactory) {
                allResolversUseDefault = false;
            }
        }
    }

    /**
     * 移除指定实体类型的解析器注册。
     *
     * @param entityType 实体类类型
     */
    public static void removeResolver(Class<?> entityType) {
        Objects.requireNonNull(entityType, "entityType must not be null");
        synchronized (resolverCheckLock) {
            resolvers.remove(entityType);
            recheckAllResolversUseDefault();
        }
    }

    /**
     * 重新检查 allResolversUseDefault 标志。调用方必须持有 resolverCheckLock。
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
        // 对每个 resolver 使用其注册的原始实体类型作为探针
        // 避免单 probe 类型漏判——当 resolver 为不同类型返回不同 EMF 时
        for (java.util.Map.Entry<Class<?>, EntityManagerResolver> entry : resolvers.entrySet()) {
            try {
                if (entry.getValue().resolve(entry.getKey()) != defaultEmf) {
                    allResolversUseDefault = false;
                    return;
                }
            } catch (Exception e) {
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
            throw new IllegalStateException("No transactional EntityManager available. "
                + "Ensure the operation is running within a @Transactional context. "
                + "Example: annotate the calling method with @Transactional, "
                + "or use MyJpaTemplate which manages transactions automatically.");
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
        // ponytail: allResolversUseDefault 的快速路径不在锁保护下读取，存在微小竞态：
        // registerResolver 刚写入 false 但其他线程可能尚未看到。对于生产环境可忽略。
        // 这里保留快速路径以优化单数据源（95%+ 场景），但通过 volatile 保证最终可见性。
        if (allResolversUseDefault) {
            EntityManagerFactory emf = defaultEntityManagerFactory;
            if (emf == null) {
                emf = resolveFromApplicationContext();
                if (emf != null) {
                    return emf;
                }
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
            emf = resolveFromApplicationContext();
            if (emf != null) {
                return emf;
            }
            throw new IllegalStateException(
                "EntityManagerFactory not initialized. Ensure MyJpaPlusAutoConfiguration is registered.");
        }
        return emf;
    }

    /**
     * 从 ApplicationContext 按需获取 EntityManagerFactory（延迟解析）。
     *
     * <p>
     * 当 {@link #setEntityManagerFactory} 尚未被调用时作为 fallback。
     * 解析成功后自动缓存到 {@link #defaultEntityManagerFactory}，后续调用不再查询容器。
     *
     * @return 解析到的 EMF，如果 ApplicationContext 不可用或未找到则返回 null
     */
    @SuppressWarnings("unchecked")
    private static EntityManagerFactory resolveFromApplicationContext() {
        org.springframework.context.ApplicationContext ctx = applicationContext;
        if (ctx == null) {
            return null;
        }
        try {
            // 尝试从容器获取 LocalContainerEntityManagerFactoryBean 管理的 EMF
            var emfBeans = ctx.getBeansOfType(EntityManagerFactory.class);
            if (!emfBeans.isEmpty()) {
                EntityManagerFactory emf = emfBeans.values().iterator().next();
                // 缓存到 defaultEntityManagerFactory，后续快速路径直接命中
                defaultEntityManagerFactory = emf;
                log.debug("Resolved EntityManagerFactory from ApplicationContext: {}", emf);
                return emf;
            }
        } catch (Exception e) {
            log.debug("Failed to resolve EntityManagerFactory from ApplicationContext: {}", e.getMessage());
        }
        return null;
    }

    // ---- 清理 ----

    /**
     * 清理所有注册的 resolver 和默认 EMF。用于应用关闭时的资源清理。
     *
     * <p>
     * <strong>⚠️ 调用约束：</strong>此方法必须仅在没有活动事务时调用。 在活动事务中调用会导致后续
     * {@link #getTransactionalEntityManager(Class)} 抛出 {@link IllegalStateException}。
     */
    public static void reset() {
        synchronized (resolverCheckLock) {
            defaultEntityManagerFactory = null;
            resolvers.clear();
            allResolversUseDefault = true;
        }
        applicationContext = null;
    }
}
