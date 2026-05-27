package com.zsubera.jpa.integration;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EntityScan(basePackageClasses = PgTestApplication.class)
@EnableJpaRepositories(basePackageClasses = PgTestApplication.class)
class PgTestApplication {}

interface PgTestEntityRepository extends JpaRepository<PgTestEntity, Long>, JpaSpecificationExecutor<PgTestEntity> {}

interface PgParentEntityRepository
        extends JpaRepository<PgParentEntity, Long>, JpaSpecificationExecutor<PgParentEntity> {}
