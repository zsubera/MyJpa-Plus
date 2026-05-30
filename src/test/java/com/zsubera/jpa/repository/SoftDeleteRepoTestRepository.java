package com.zsubera.jpa.repository;

/**
 * Test repository using {@link SoftDeleteJpaRepository} as base class.
 */
interface SoftDeleteRepoTestRepository extends MyJpaRepository<SoftDeleteRepoTestEntity, Long> {}
