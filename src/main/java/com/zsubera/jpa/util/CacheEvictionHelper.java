package com.zsubera.jpa.util;

import jakarta.persistence.EntityManager;
import java.lang.reflect.Method;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JPA 缓存驱逐工具类，消除 UpdateSpec/DeleteSpec 之间的耦合。
 *
 * <p>Hibernate 环境下同时驱逐 L2 缓存（{@code SessionFactory.getCache().evictEntityData()}）
 * 和 L1 缓存（{@code em.clear()}）。非 Hibernate 环境回退到 {@code em.clear()}（会影响所有托管实体）。
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
            try {
                hibernateSessionClass = Class.forName("org.hibernate.Session");
                getSessionFactoryMethod = hibernateSessionClass.getMethod("getSessionFactory");
                Class<?> sessionFactoryClass = Class.forName("org.hibernate.SessionFactory");
                getCacheMethod = sessionFactoryClass.getMethod("getCache");
                Class<?> cacheClass = Class.forName("org.hibernate.Cache");
                evictEntityDataMethod = cacheClass.getMethod("evictEntityData", Class.class);
                hibernateAvailable = true;
            } catch (ClassNotFoundException | NoSuchMethodException e) {
                hibernateAvailable = false;
            }
            reflectionInitialized = true;
        }
    }

    /**
     * 选择性驱逐实体类型的缓存（L1 + L2）。
     *
     * @param em 实体管理器
     * @param entityClass 要驱逐缓存的实体类型，为 null 时执行 em.clear()
     */
    public static void evictEntityCache(EntityManager em, Class<?> entityClass) {
        if (entityClass == null) {
            em.clear();
            return;
        }
        ensureReflectionInitialized();
        if (hibernateAvailable && hibernateSessionClass.isInstance(em.getDelegate())) {
            try {
                Object session = em.unwrap(hibernateSessionClass);
                Object factory = getSessionFactoryMethod.invoke(session);
                Object cache = getCacheMethod.invoke(factory);
                evictEntityDataMethod.invoke(cache, entityClass);
            } catch (Exception e) {
                log.warn("Failed to evict entity L2 cache selectively for {}, falling back to em.clear()",
                    entityClass.getSimpleName(), e);
            }
        }
        em.clear();
    }
}
