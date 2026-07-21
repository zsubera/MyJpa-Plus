package com.zsubera.jpa.integration;

import com.zsubera.jpa.repository.MyJpaRepository;
import java.util.Optional;

interface MySQLTestEntityRepository extends MyJpaRepository<MySQLTestEntity, Long> {
    // 注意：不定义 findAll(QuerySpec) 默认方法，直接使用 MyJpaRepository 的 findAll(Specification)
    Optional<MySQLTestEntity> findByName(String name);
}

interface MySQLParentEntityRepository extends MyJpaRepository<MySQLParentEntity, Long> {}
