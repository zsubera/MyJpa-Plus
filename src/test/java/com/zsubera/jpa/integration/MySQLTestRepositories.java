package com.zsubera.jpa.integration;

import org.springframework.data.jpa.repository.JpaRepository;

interface MySQLTestEntityRepository extends JpaRepository<MySQLTestEntity, Long> {}

interface MySQLParentEntityRepository extends JpaRepository<MySQLParentEntity, Long> {}
