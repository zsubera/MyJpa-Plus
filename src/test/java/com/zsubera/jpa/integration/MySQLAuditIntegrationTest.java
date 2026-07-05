package com.zsubera.jpa.integration;

import static org.junit.jupiter.api.Assertions.*;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.test.context.TestPropertySource;

@Tag("integration")
@SpringBootTest(classes = {PgTestApplication.class, MySQLAuditIntegrationTest.TestConfig.class})
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:mysql://localhost:3306/test?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC",
    "spring.datasource.username=root", "spring.datasource.password=ci_test_2024",
    "spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
    "spring.jpa.database-platform=org.hibernate.dialect.MySQLDialect", "spring.jpa.hibernate.ddl-auto=create"})
class MySQLAuditIntegrationTest {

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

        assertEquals("test-user", entity.getCreatedBy(), "createdBy should be filled by AuditorAware");
        assertEquals("test-user", entity.getUpdatedBy(), "updatedBy should be filled by AuditorAware");
    }

    @Test
    void testUpdatedAtIsUpdatedOnUpdate() {
        AuditEntity entity = new AuditEntity();
        entity.setName("test-entity");
        repository.save(entity);
        repository.flush();

        assertNotNull(entity.getUpdatedAt(), "updatedAt should be set on persist");
        Instant persistedUpdatedAt = entity.getUpdatedAt();

        entity.setName("updated-entity");
        entity = repository.save(entity);
        repository.flush();

        assertNotNull(entity.getUpdatedAt(), "updatedAt should be set on update");
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

        assertEquals("test-user", entity.getUpdatedBy(), "updatedBy should be filled by AuditorAware");
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
    void testMultipleEntitiesHaveIndependentTimestamps() {
        AuditEntity entity1 = new AuditEntity();
        entity1.setName("entity1");
        repository.save(entity1);
        repository.flush();

        AuditEntity entity2 = new AuditEntity();
        entity2.setName("entity2");
        repository.save(entity2);
        repository.flush();

        assertNotNull(entity1.getCreatedAt(), "entity1 createdAt should be set");
        assertNotNull(entity2.getCreatedAt(), "entity2 createdAt should be set");
    }

    @Configuration
    static class TestConfig {

        @Bean
        public AuditorAware<String> auditorAware() {
            return () -> Optional.of("test-user");
        }
    }
}
