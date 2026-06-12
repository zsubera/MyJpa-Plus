package com.zsubera.jpa.integration;

import static org.junit.jupiter.api.Assertions.*;

import com.zsubera.jpa.spec.QuerySpec;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Tag("integration")
@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PostgreSQLIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("myjpa_test").withUsername("test").withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create");
    }

    @Autowired
    private PgTestEntityRepository repository;

    @Autowired
    private PgParentEntityRepository parentRepository;

    @Test
    void testSimpleEqOnPostgreSQL() {
        PgTestEntity entity = new PgTestEntity();
        entity.setName("hello");
        entity.setStatus(1);
        repository.save(entity);

        QuerySpec<PgTestEntity> qs = new QuerySpec<>();
        qs.eq(PgTestEntity::getName, "hello");
        List<PgTestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
        assertEquals("hello", result.get(0).getName());
    }

    @Test
    void testStartsWithOnPostgreSQL() {
        PgTestEntity e1 = new PgTestEntity();
        e1.setName("hello_world");
        e1.setStatus(0);
        repository.save(e1);

        PgTestEntity e2 = new PgTestEntity();
        e2.setName("hello_test%");
        e2.setStatus(0);
        repository.save(e2);

        PgTestEntity e3 = new PgTestEntity();
        e3.setName("other");
        e3.setStatus(0);
        repository.save(e3);

        QuerySpec<PgTestEntity> qs = new QuerySpec<>();
        qs.startsWith(PgTestEntity::getName, "hello");
        List<PgTestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(2, result.size());
    }

    @Test
    void testContainsWithSpecialCharsOnPostgreSQL() {
        PgTestEntity e1 = new PgTestEntity();
        e1.setName("test%_value");
        e1.setStatus(0);
        repository.save(e1);

        PgTestEntity e2 = new PgTestEntity();
        e2.setName("test%_other");
        e2.setStatus(0);
        repository.save(e2);

        PgTestEntity e3 = new PgTestEntity();
        e3.setName("normal");
        e3.setStatus(0);
        repository.save(e3);

        QuerySpec<PgTestEntity> qs = new QuerySpec<>();
        qs.like(PgTestEntity::getName, "%_");
        List<PgTestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(2, result.size());
    }

    @Test
    void testLikeEscapeDoesNotMatchWildcard() {
        PgTestEntity e1 = new PgTestEntity();
        e1.setName("hello_world");
        e1.setStatus(0);
        repository.save(e1);

        PgTestEntity e2 = new PgTestEntity();
        e2.setName("helloXworld");
        e2.setStatus(0);
        repository.save(e2);

        QuerySpec<PgTestEntity> qs = new QuerySpec<>();
        qs.like(PgTestEntity::getName, "_wor");
        List<PgTestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
        assertEquals("hello_world", result.get(0).getName());
    }
}
