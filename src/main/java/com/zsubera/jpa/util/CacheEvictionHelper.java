package com.zsubera.jpa.util;

import jakarta.persistence.EntityManager;
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
        // ponytail: Hibernate 路径同时驱逐 L2（SessionFactory 级）和 L1（EntityManager 级）
        // L2 驱逐通过反射调用 evictEntityData，L1 驱逐通过 em.clear()。
        // 无法单独驱逐 L1 中特定实体类型，因此 em.clear() 影响所有托管实体。
        try {
            Class<?> sessionClass = Class.forName("org.hibernate.Session");
            if (sessionClass.isInstance(em.getDelegate())) {
                Object session = em.unwrap(sessionClass);
                Object factory = session.getClass().getMethod("getSessionFactory").invoke(session);
                Object cache = factory.getClass().getMethod("getCache").invoke(factory);
                cache.getClass().getMethod("evictEntityData", Class.class).invoke(cache, entityClass);
            }
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            // 非 Hibernate 环境，回退到 em.clear()
        } catch (Exception e) {
            log.warn("Failed to evict entity L2 cache selectively for {}, falling back to em.clear()",
                entityClass.getSimpleName(), e);
        }
        em.clear();
    }
}
