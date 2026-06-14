package com.zsubera.jpa.integration;

import com.zsubera.jpa.repository.MyJpaRepository;
import com.zsubera.jpa.spec.QuerySpec;
import java.util.List;

interface MySQLTestEntityRepository extends MyJpaRepository<MySQLTestEntity, Long> {
    default List<MySQLTestEntity> findAll(QuerySpec<MySQLTestEntity> qs) {
        return findAll(qs.toSpecification());
    }
}

interface MySQLParentEntityRepository extends MyJpaRepository<MySQLParentEntity, Long> {}
