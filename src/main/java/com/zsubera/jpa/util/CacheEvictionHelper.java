package com.zsubera.jpa.util;

import jakarta.persistence.EntityManager;
import java.lang.reflect.Method;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JPA 缓存驱逐工具类，消除 UpdateSpec/DeleteSpec 之间的耦合。
 *
 * <p>Hibernate 环境下驱逐 L2 缓存（{@code SessionFactory.getCache().evictEntityData()}），
 * 然后通过 {@code em.clear()} 清除 L1 缓存（JPA 持久化上下文）。
 * 非 Hibernate 环境仅执行 {@code em.clear()}。
 *
 * <p><strong>L1 缓存说明：</strong>{@code em.clear()} 会清除 <em>所有</em> 托管实体（全局 L1 驱逐），
 * 而非仅清除指定 entityClass 的实例。这是 JPA 规范的限制——没有类型级 L1 驱逐的标准 API。
 * 如果需要选择性 L1 驱逐，可使用 Hibernate 的 {@code Session.evict(entity)} 逐实例驱逐。
 */
public final class CacheEvictionHelper {

    private static final Logger log = LoggerFactory.getLogger(CacheEvictionHelper.class);

    private CacheEvictionHelper() {}

    /** 缓存 Hibernate Session 类引用，避免每次调用都 Class.forName。 */
    private static volatile Class<?> hibernateSessionClass;

    /** 缓存已解析的反射方法，避免重复查找。 */
    private static volatile Method getSessionFactoryMethod;
    private static volatile Method getCacheMethod;
    private static volatile Method evictEntityDataMethod;
    private static volatile boolean hibernateAvailable;
    private static volatile boolean reflectionInitialized;

    /** 反射初始化失败重试计数器，超过上限后停止重试 */
    private static int reflectionRetryCount;
    private static final int MAX_REFLECTION_RETRIES = 3;

    /** 反射初始化最后失败时间戳，用于退避重试 */
    private static long lastReflectionFailureTime;
    private static final long REFLECTION_RETRY_BACKOFF_NANOS = java.util.concurrent.TimeUnit.SECONDS.toNanos(30);

    /**
     * 初始化 Hibernate 反射缓存。仅在首次调用时执行。
     */
    private static void ensureReflectionInitialized() {
        if (reflectionInitialized) {
            return;
        }
        synchronized (CacheEvictionHelper.class) {
            if (reflectionInitialized) {
                return;
            }
            // 退避重试：超过重试上限或在退避窗口内跳过重试
            if (reflectionRetryCount >= MAX_REFLECTION_RETRIES) {
                long now = System.nanoTime();
                if (now - lastReflectionFailureTime < REFLECTION_RETRY_BACKOFF_NANOS) {
                    return;
                }
                // 退避窗口过后重置计数器，允许再次尝试
                reflectionRetryCount = 0;
            }
            try {
                hibernateSessionClass = Class.forName("org.hibernate.Session");
                getSessionFactoryMethod = hibernateSessionClass.getMethod("getSessionFactory");
                Class<?> sessionFactoryClass = Class.forName("org.hibernate.SessionFactory");
                getCacheMethod = sessionFactoryClass.getMethod("getCache");
                Class<?> cacheClass = Class.forName("org.hibernate.Cache");
                evictEntityDataMethod = cacheClass.getMethod("evictEntityData", Class.class);
                hibernateAvailable = true;
                reflectionInitialized = true;
            } catch (ClassNotFoundException | NoSuchMethodException e) {
                hibernateAvailable = false;
                reflectionRetryCount++;
                lastReflectionFailureTime = System.nanoTime();
                log.debug("Hibernate L2 cache reflection not available, will retry on next call (attempt {}/{}): {}",
                    reflectionRetryCount, MAX_REFLECTION_RETRIES, e.getMessage());
            }
        }
    }

    /**
     * 驱逐实体类型的缓存。L2 缓存选择性驱逐（仅指定 entityClass），
     * L1 缓存全局清除（em.clear() 清除所有托管实体）。
     *
     * @param em 实体管理器
     * @param entityClass 要驱逐 L2 缓存的实体类型，为 null 时仅执行 em.clear()
     */
    public static void evictEntityCache(EntityManager em, Class<?> entityClass) {
        if (entityClass == null) {
            em.clear();
            return;
        }
        evictL2CacheOnly(em, entityClass);
        em.clear();
    }

    /**
     * 仅驱逐 L2 缓存，不清除 L1 持久化上下文。
     * 用于 {@code PersistenceContextStrategy.DEFER_TO_CALLER} 场景，
     * 由调用方自行管理持久化上下文生命周期。
     *
     * @param em 实体管理器
     * @param entityClass 要驱逐 L2 缓存的实体类型
     */
    public static void evictL2CacheOnly(EntityManager em, Class<?> entityClass) {
        ensureReflectionInitialized();
        if (hibernateAvailable) {
            try {
                if (hibernateSessionClass.isInstance(em.getDelegate())) {
                    Object session = em.unwrap(hibernateSessionClass);
                    Object factory = getSessionFactoryMethod.invoke(session);
                    Object cache = getCacheMethod.invoke(factory);
                    evictEntityDataMethod.invoke(cache, entityClass);
                }
            } catch (Exception e) {
                log.warn("Failed to evict entity L2 cache selectively for {}: {}", entityClass.getSimpleName(),
                    e.getMessage());
            }
        }
    }
}
