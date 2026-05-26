package com.zsubera.jpa.spec;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface SoftDeleteTestEntityRepository
    extends JpaRepository<SoftDeleteTestEntity, Long>,
        JpaSpecificationExecutor<SoftDeleteTestEntity> {}
