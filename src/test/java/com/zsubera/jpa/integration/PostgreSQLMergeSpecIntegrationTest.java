package com.zsubera.jpa.integration;

import static org.junit.jupiter.api.Assertions.*;

import com.zsubera.jpa.update.MergeSpec;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
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
class PostgreSQLMergeSpecIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("myjpa_test").withUsername("test").withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create");
    }

    @Autowired
    private PgTestEntityRepository repository;

    @PersistenceContext
    private EntityManager em;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        repository.flush();
    }

    @Test
    void testMergeInsertNew() {
        PgTestEntity entity = new PgTestEntity();
        entity.setName("new");
        entity.setStatus(1);

        int count = new MergeSpec<>(PgTestEntity.class).withEntity(entity).execute(em);
        em.flush();
        em.clear();

        assertEquals(1, count);
        List<PgTestEntity> all = repository.findAll();
        assertEquals(1, all.size());
        assertEquals("new", all.get(0).getName());
        assertEquals(Integer.valueOf(1), all.get(0).getStatus());
    }

    @Test
    void testMergeUpdateExistingByDefaultId() {
        PgTestEntity saved = new PgTestEntity();
        saved.setName("original");
        saved.setStatus(1);
        repository.save(saved);
        em.flush();
        em.clear();

        PgTestEntity entity = new PgTestEntity();
        entity.setId(saved.getId());
        entity.setName("updated");
        entity.setStatus(99);

        int count = new MergeSpec<>(PgTestEntity.class).withEntity(entity).execute(em);
        em.flush();
        em.clear();

        assertEquals(1, count);
        PgTestEntity found = repository.findById(saved.getId()).orElseThrow();
        assertEquals("updated", found.getName());
        assertEquals(Integer.valueOf(99), found.getStatus());
    }

    @Test
    void testMergeWithExplicitConflictColumns() {
        PgTestEntity existing = new PgTestEntity();
        existing.setName("unique_name");
        existing.setStatus(1);
        repository.save(existing);
        em.flush();
        em.clear();

        PgTestEntity entity = new PgTestEntity();
        entity.setName("unique_name");
        entity.setStatus(99);

        int count =
            new MergeSpec<>(PgTestEntity.class).withEntity(entity).onConflict(PgTestEntity::getName).execute(em);
        em.flush();
        em.clear();

        assertEquals(1, count);
        List<PgTestEntity> all = repository.findAll();
        assertEquals(1, all.size());
        assertEquals(Integer.valueOf(99), all.get(0).getStatus());
    }

    @Test
    void testMergeWithUpdateOnConflictSpecificColumns() {
        PgTestEntity existing = new PgTestEntity();
        existing.setName("conflict");
        existing.setStatus(1);
        repository.save(existing);
        em.flush();
        em.clear();

        PgTestEntity entity = new PgTestEntity();
        entity.setName("conflict");
        entity.setStatus(2);

        int count = new MergeSpec<>(PgTestEntity.class).withEntity(entity).onConflict(PgTestEntity::getName)
            .updateOnConflict(PgTestEntity::getStatus).execute(em);
        em.flush();
        em.clear();

        assertEquals(1, count);
        PgTestEntity found = repository.findById(existing.getId()).orElseThrow();
        assertEquals("conflict", found.getName());
        assertEquals(Integer.valueOf(2), found.getStatus());
    }

    @Test
    void testMergeBatchInsert() {
        List<PgTestEntity> entities = new java.util.ArrayList<>();
        for (int i = 0; i < 5; i++) {
            PgTestEntity e = new PgTestEntity();
            e.setName("batch_" + i);
            e.setStatus(i);
            entities.add(e);
        }

        int count = new MergeSpec<>(PgTestEntity.class).executeBatch(entities, em, 100);
        em.flush();
        em.clear();

        assertEquals(5, count);
        assertEquals(5, repository.count());
    }

    @Test
    void testMergeBatchUpsertMixed() {
        PgTestEntity existing = new PgTestEntity();
        existing.setName("existing");
        existing.setStatus(1);
        repository.save(existing);
        em.flush();
        em.clear();

        List<PgTestEntity> entities = new java.util.ArrayList<>();

        PgTestEntity update = new PgTestEntity();
        update.setName("existing");
        update.setStatus(99);
        entities.add(update);

        PgTestEntity insert = new PgTestEntity();
        insert.setName("new_entry");
        insert.setStatus(2);
        entities.add(insert);

        int count = new MergeSpec<>(PgTestEntity.class).onConflict(PgTestEntity::getName).executeBatch(entities, em, 100);
        em.flush();
        em.clear();

        assertEquals(2, count);
        assertEquals(2, repository.count());

        PgTestEntity updated =
            repository.findAll().stream().filter(e -> "existing".equals(e.getName())).findFirst().orElseThrow();
        assertEquals(Integer.valueOf(99), updated.getStatus());
    }
}
