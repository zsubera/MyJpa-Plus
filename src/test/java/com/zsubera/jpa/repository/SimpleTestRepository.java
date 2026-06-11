package com.zsubera.jpa.repository;

/**
 * Repository for SimpleTestEntity (no @SoftDelete), extending MyJpaRepository.
 */
public interface SimpleTestRepository extends MyJpaRepository<SimpleTestEntity, Long> {}
