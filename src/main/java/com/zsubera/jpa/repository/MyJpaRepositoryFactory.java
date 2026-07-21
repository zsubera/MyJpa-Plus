package com.zsubera.jpa.repository;

import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * @see MyJpaRepositoryFactoryBean
 * @see DefaultMyJpaRepository
 */
class MyJpaRepositoryFactory extends JpaRepositoryFactory {

    private static final Logger log = LoggerFactory.getLogger(MyJpaRepositoryFactory.class);

    MyJpaRepositoryFactory(EntityManager entityManager) {
        super(entityManager);
        addRepositoryProxyPostProcessor((factory, metadata) -> {
            try {
                Object target = factory.getTargetSource().getTarget();
                if (target == null) {
                    log.warn("Target is null for {}, skipping DefaultMethodOverrideInterceptor", metadata.getRepositoryInterface().getSimpleName());
                    return;
                }
                factory.addAdvice(new DefaultMethodOverrideInterceptor(target));
                log.debug("Installed DefaultMethodOverrideInterceptor for {}", metadata.getRepositoryInterface().getSimpleName());
            } catch (Exception e) {
                log.error("Failed to install DefaultMethodOverrideInterceptor", e);
                throw new RuntimeException("Failed to install DefaultMethodOverrideInterceptor", e);
            }
        });
    }

    @Override
    protected Class<?> getRepositoryBaseClass(RepositoryMetadata metadata) {
        return DefaultMyJpaRepository.class;
    }
}
