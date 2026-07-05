package com.zsubera.jpa.repository;

import jakarta.persistence.EntityManagerFactory;

/**
 * 实体管理器解析策略，用于多数据源场景。
 *
 * <p>
 * 当实体类型映射到不同的 {@link EntityManagerFactory} 时， 通过此接口按实体类型解析对应的 EMF。
 *
 * <p>
 * <strong>使用示例：</strong>
 *
 * <pre>{@code
 * // 按实体类型注册解析器
 * EntityManagerHelper.registerResolver(Order.class, type -> orderEmf);
 * EntityManagerHelper.registerResolver(User.class, type -> userEmf);
 *
 * // 获取对应实体类型的事务性 EntityManager
 * EntityManager em = EntityManagerHelper.getTransactionalEntityManager(Order.class);
 * }</pre>
 *
 * @see EntityManagerHelper#registerResolver(Class, EntityManagerResolver)
 */
@FunctionalInterface
public interface EntityManagerResolver {

    /**
     * 根据实体类型解析 {@link EntityManagerFactory}。
     *
     * @param entityType 实体类类型
     * @return 对应的 EntityManagerFactory
     */
    EntityManagerFactory resolve(Class<?> entityType);
}
