package com.zsubera.jpa.repository;

import jakarta.persistence.EntityManager;
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
 * @see MyJpaRepositoryFactory
 * @see DefaultMyJpaRepository
 */
public class MyJpaRepositoryFactoryBean<T extends Repository<S, ID>, S, ID> extends JpaRepositoryFactoryBean<T, S, ID> {

    public MyJpaRepositoryFactoryBean(Class<? extends T> repositoryInterface) {
        super(repositoryInterface);
    }

    @Override
    protected RepositoryFactorySupport createRepositoryFactory(EntityManager entityManager) {
        return new MyJpaRepositoryFactory(entityManager);
    }
}
