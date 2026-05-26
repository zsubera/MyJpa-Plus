package com.zsubera.jpa.spec;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface TestEntityRepository
    extends JpaRepository<TestEntity, Long>, JpaSpecificationExecutor<TestEntity> {}
