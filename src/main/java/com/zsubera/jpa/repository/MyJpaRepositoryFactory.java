package com.zsubera.jpa.repository;

import jakarta.persistence.EntityManager;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.data.repository.core.RepositoryMetadata;

/**
 * 自定义 Repository 工厂，将 {@link DefaultMyJpaRepository} 设为所有仓库的默认基类。
 *
 * <p>
 * 此工厂覆盖了 {@link JpaRepositoryFactory#getRepositoryBaseClass(RepositoryMetadata)}，
 * 返回 {@link DefaultMyJpaRepository} 而非 Spring Data JPA 默认的
 * {@code SimpleJpaRepository}。这使得用户无需在 {@code @EnableJpaRepositories} 中手动指定
 * {@code repositoryBaseClass}。
 *
 * <p>
 * 用户仍可通过 {@code @EnableJpaRepositories(repositoryBaseClass = ...)} 覆盖此默认值。
 *
 * @see MyJpaRepositoryFactoryBean
 * @see DefaultMyJpaRepository
 */
class MyJpaRepositoryFactory extends JpaRepositoryFactory {

    MyJpaRepositoryFactory(EntityManager entityManager) {
        super(entityManager);
    }

    @Override
    protected Class<?> getRepositoryBaseClass(RepositoryMetadata metadata) {
        return DefaultMyJpaRepository.class;
    }
}
