package com.zsubera.jpa.repository;

/**
 * Test repository using {@link DefaultMyJpaRepository} as base class.
 */
interface SoftDeleteRepoTestRepository extends MyJpaRepository<SoftDeleteRepoTestEntity, Long> {}
