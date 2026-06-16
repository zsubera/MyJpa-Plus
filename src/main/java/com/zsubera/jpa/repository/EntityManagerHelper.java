package com.zsubera.jpa.repository;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.orm.jpa.EntityManagerFactoryUtils;

/**
 * 静态持有者，提供对当前事务性 {@link EntityManager} 的访问。
 *
 * <p>
 * 由 {@code MyJpaPlusAutoConfiguration} 初始化。用于 {@link MyJpaRepository} 默认方法中的批量操作，
 * 使它们无需 Spring Data 自定义工厂即可工作。
 *
 * <p>
 * <strong>多数据源限制：</strong>此类存储单一 {@link EntityManagerFactory}，
 * 在多数据源场景下所有仓库共享同一个 EMF。如需多数据源支持，请使用
 * {@link com.zsubera.jpa.template.MyJpaTemplate} 进行批量操作。
 */
public final class EntityManagerHelper {

    private static volatile EntityManagerFactory entityManagerFactory;

    private EntityManagerHelper() {}

    /**
     * 由自动配置调用以设置 {@link EntityManagerFactory} 引用。
     */
    @SuppressFBWarnings(value = "EI_EXPOSE_STATIC_REP2",
        justification = "EntityManagerFactory is thread-safe and stateless")
    public static void setEntityManagerFactory(EntityManagerFactory emf) {
        entityManagerFactory = emf;
    }

    /**
     * 返回绑定到当前 JPA 事务的 {@link EntityManager}（如果有），否则返回 {@code null}。
     */
    public static EntityManager getTransactionalEntityManager() {
        if (entityManagerFactory == null) {
            throw new IllegalStateException(
                "EntityManagerFactory not initialized. Ensure MyJpaPlusAutoConfiguration is registered.");
        }
        EntityManager em = EntityManagerFactoryUtils.getTransactionalEntityManager(entityManagerFactory);
        if (em == null) {
            throw new IllegalStateException(
                "No transactional EntityManager available. Ensure the operation is running within a transaction.");
        }
        return em;
    }
}
