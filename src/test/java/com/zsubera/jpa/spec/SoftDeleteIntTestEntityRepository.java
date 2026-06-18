package com.zsubera.jpa.spec;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface SoftDeleteIntTestEntityRepository
    extends JpaRepository<SoftDeleteIntTestEntity, Long>, JpaSpecificationExecutor<SoftDeleteIntTestEntity> {}
