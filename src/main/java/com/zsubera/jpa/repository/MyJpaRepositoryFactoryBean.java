package com.zsubera.jpa.repository;

import com.zsubera.jpa.util.EntityClassResolver;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactoryBean;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.core.support.RepositoryFactorySupport;

/**
 * 自定义 RepositoryFactoryBean，创建 {@link MyJpaRepositoryFactory} 实例。
 *
 * <p>
 * 此 FactoryBean 使得所有仓库默认使用 {@link DefaultMyJpaRepository} 作为基类，
 * 无需在 {@code @EnableJpaRepositories} 中手动指定 {@code repositoryBaseClass}。
 *
 * <p>
 * <strong>多数据源支持：</strong>在创建仓库工厂时，自动将实体类型到 EMF 的映射注册到
 * {@link EntityManagerHelper}，支持按实体类型解析不同的 EMF。
 *
 * @see MyJpaRepositoryFactory
 * @see DefaultMyJpaRepository
 * @see EntityManagerHelper
 */
public class MyJpaRepositoryFactoryBean<T extends Repository<S, ID>, S, ID> extends JpaRepositoryFactoryBean<T, S, ID> {

    private static final Logger log = LoggerFactory.getLogger(MyJpaRepositoryFactoryBean.class);

    private final Class<?> entityType;

    public MyJpaRepositoryFactoryBean(Class<? extends T> repositoryInterface) {
        super(repositoryInterface);
        this.entityType = resolveEntityType(repositoryInterface);
    }

    @Override
    protected RepositoryFactorySupport createRepositoryFactory(EntityManager entityManager) {
        // 向后兼容：设置默认 EMF（单数据源场景）
        EntityManagerHelper.setEntityManagerFactory(entityManager.getEntityManagerFactory());

        // 多数据源场景：按实体类型注册 EMF（仅在未手动注册时设置默认值）
        if (entityType != null) {
            EntityManagerHelper.registerEntityManagerFactoryIfAbsent(
                entityType, entityManager.getEntityManagerFactory());
            if (log.isDebugEnabled()) {
                log.debug("Registered EntityManagerFactory for entity type: {}", entityType.getSimpleName());
            }
        }

        return new MyJpaRepositoryFactory(entityManager);
    }

    /**
     * 从 Repository 接口解析关联的实体类型。
     *
     * @param repositoryInterface Repository 接口类
     * @return 实体类型，如果无法解析则返回 null
     */
    private Class<?> resolveEntityType(Class<? extends T> repositoryInterface) {
        try {
            return EntityClassResolver.resolve(repositoryInterface);
        } catch (Exception e) {
            log.debug("Could not resolve entity type for repository: {}", repositoryInterface.getSimpleName());
            return null;
        }
    }
}
