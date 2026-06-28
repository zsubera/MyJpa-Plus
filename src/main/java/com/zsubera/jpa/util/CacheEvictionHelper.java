package com.zsubera.jpa.util;

import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JPA L1 缓存驱逐工具类，消除 UpdateSpec/DeleteSpec 之间的耦合。
 *
 * <p>优先使用 Hibernate 的 {@code SessionFactory.getCache().evictEntityData()} 选择性驱逐，
 * 非 Hibernate 环境回退到 {@code em.clear()}（会影响所有托管实体）。
 */
public final class CacheEvictionHelper {

    private static final Logger log = LoggerFactory.getLogger(CacheEvictionHelper.class);

    private CacheEvictionHelper() {}

    /**
     * 选择性驱逐实体类型的 L1 缓存。
     *
     * @param em 实体管理器
     * @param entityClass 要驱逐缓存的实体类型，为 null 时执行 em.clear()
     */
    public static void evictEntityCache(EntityManager em, Class<?> entityClass) {
        if (entityClass == null) {
            em.clear();
            return;
        }
        try {
            Class<?> sessionClass = Class.forName("org.hibernate.Session");
            if (sessionClass.isInstance(em.getDelegate())) {
                Object session = em.unwrap(sessionClass);
                Object factory = session.getClass().getMethod("getSessionFactory").invoke(session);
                Object cache = factory.getClass().getMethod("getCache").invoke(factory);
                cache.getClass().getMethod("evictEntityData", Class.class).invoke(cache, entityClass);
                return;
            }
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            // 非 Hibernate 环境，回退到 em.clear()
        } catch (Exception e) {
            log.warn("Failed to evict entity cache selectively for {}, falling back to em.clear()",
                entityClass.getSimpleName(), e);
        }
        log.warn("Non-selective em.clear() fallback for {} — all managed entities will be detached",
            entityClass.getSimpleName());
        em.clear();
    }
}
