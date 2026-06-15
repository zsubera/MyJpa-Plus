package com.zsubera.jpa.integration;

import com.zsubera.jpa.repository.MyJpaRepository;
import com.zsubera.jpa.spec.QuerySpec;
import java.util.List;
import java.util.Optional;

interface MySQLTestEntityRepository extends MyJpaRepository<MySQLTestEntity, Long> {
    default List<MySQLTestEntity> findAll(QuerySpec<MySQLTestEntity> qs) {
        return findAll(qs.toSpecification());
    }

    Optional<MySQLTestEntity> findByName(String name);
}

interface MySQLParentEntityRepository extends MyJpaRepository<MySQLParentEntity, Long> {}
