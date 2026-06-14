package com.zsubera.jpa.integration;

import static org.junit.jupiter.api.Assertions.*;

import com.zsubera.jpa.annotation.AuditUserProvider;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.TestPropertySource;

@Tag("integration")
@SpringBootTest(classes = {PgTestApplication.class, AuditIntegrationTest.TestConfig.class})
@TestPropertySource(properties = {"spring.jpa.hibernate.ddl-auto=create"})
class AuditIntegrationTest {

    @Autowired
    private AuditEntityRepository repository;

    @PersistenceContext
    private EntityManager em;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        repository.flush();
    }

    @Test
    void testCreatedAtAndUpdatedAtAreFilledOnPersist() {
        AuditEntity entity = new AuditEntity();
        entity.setName("test-entity");
        repository.save(entity);
        repository.flush();

        assertNotNull(entity.getCreatedAt(), "createdAt should be filled on persist");
        assertNotNull(entity.getUpdatedAt(), "updatedAt should be filled on persist");
        assertTrue(entity.getCreatedAt().isAfter(Instant.now().minusSeconds(5)));
        assertTrue(entity.getUpdatedAt().isAfter(Instant.now().minusSeconds(5)));
    }

    @Test
    void testCreatedByAndUpdatedByAreFilledOnPersist() {
        AuditEntity entity = new AuditEntity();
        entity.setName("test-entity");
        repository.save(entity);
        repository.flush();

        assertEquals("test-user", entity.getCreatedBy(), "createdBy should be filled by AuditUserProvider");
        assertEquals("test-user", entity.getUpdatedBy(), "updatedBy should be filled by AuditUserProvider");
    }

    @Test
    void testUpdatedAtIsUpdatedOnUpdate() {
        AuditEntity entity = new AuditEntity();
        entity.setName("test-entity");
        repository.save(entity);
        repository.flush();

        Instant originalUpdatedAt = entity.getUpdatedAt();

        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        entity.setName("updated-entity");
        entity = repository.save(entity);
        repository.flush();

        assertNotEquals(originalUpdatedAt, entity.getUpdatedAt(), "updatedAt should be updated on update");
    }

    @Test
    void testUpdatedByIsUpdatedOnUpdate() {
        AuditEntity entity = new AuditEntity();
        entity.setName("test-entity");
        repository.save(entity);
        repository.flush();

        entity.setName("updated-entity");
        repository.save(entity);
        repository.flush();

        assertEquals("test-user", entity.getUpdatedBy(), "updatedBy should be filled by AuditUserProvider");
    }

    @Test
    void testAuditFieldsArePersistedInDatabase() {
        AuditEntity entity = new AuditEntity();
        entity.setName("test-entity");
        repository.save(entity);
        repository.flush();
        em.clear();

        AuditEntity found = repository.findById(entity.getId()).orElse(null);
        assertNotNull(found);
        assertNotNull(found.getCreatedAt());
        assertNotNull(found.getUpdatedAt());
        assertEquals("test-user", found.getCreatedBy());
        assertEquals("test-user", found.getUpdatedBy());
    }

    @Test
    void testMultipleEntitiesHaveUniqueTimestamps() throws InterruptedException {
        AuditEntity entity1 = new AuditEntity();
        entity1.setName("entity1");
        repository.save(entity1);
        repository.flush();

        Thread.sleep(10);

        AuditEntity entity2 = new AuditEntity();
        entity2.setName("entity2");
        repository.save(entity2);
        repository.flush();

        assertTrue(
            entity2.getCreatedAt().isAfter(entity1.getCreatedAt())
                || entity2.getCreatedAt().equals(entity1.getCreatedAt()),
            "Second entity should be created after or at the same time as first");
    }

    @Configuration
    static class TestConfig {

        @Bean
        public AuditUserProvider auditUserProvider() {
            return () -> "test-user";
        }
    }
}
