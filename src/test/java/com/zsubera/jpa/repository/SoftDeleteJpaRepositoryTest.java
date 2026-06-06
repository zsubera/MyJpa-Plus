package com.zsubera.jpa.repository;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ContextConfiguration;

/**
 * Tests for {@link SoftDeleteJpaRepository} auto-filtering behavior.
 */
@DataJpaTest
@ContextConfiguration(classes = SoftDeleteJpaRepositoryTest.TestConfig.class)
class SoftDeleteJpaRepositoryTest {

    @SpringBootApplication
    @EntityScan(basePackageClasses = SoftDeleteRepoTestEntity.class)
    @EnableJpaRepositories(basePackages = "com.zsubera.jpa.repository",
        repositoryBaseClass = SoftDeleteJpaRepository.class)
    static class TestConfig {}

    @Autowired
    private SoftDeleteRepoTestRepository repository;

    @BeforeEach
    void setup() {
        // Ensure auto-filter is enabled
        SoftDeleteJpaRepository.setAutoFilterEnabled(true);
        SoftDeleteContext.reset();
    }

    @AfterEach
    void cleanup() {
        SoftDeleteContext.reset();
        SoftDeleteJpaRepository.setAutoFilterEnabled(true);
    }

    @Test
    void findAll_filtersDeletedEntities() {
        saveEntity("active", false);
        saveEntity("deleted", true);

        List<SoftDeleteRepoTestEntity> result = repository.findAll();
        assertEquals(1, result.size());
        assertEquals("active", result.get(0).getName());
    }

    @Test
    void findAll_withSort_filtersDeletedEntities() {
        saveEntity("active1", false);
        saveEntity("active2", false);
        saveEntity("deleted", true);

        List<SoftDeleteRepoTestEntity> result = repository.findAll(Sort.by("name"));
        assertEquals(2, result.size());
        assertEquals("active1", result.get(0).getName());
    }

    @Test
    void findAll_withPageable_filtersDeletedEntities() {
        saveEntity("active1", false);
        saveEntity("active2", false);
        saveEntity("deleted", true);

        Page<SoftDeleteRepoTestEntity> page = repository.findAll(PageRequest.of(0, 10));
        assertEquals(2, page.getTotalElements());
    }

    @Test
    void findById_returnsEmptyForDeletedEntity() {
        SoftDeleteRepoTestEntity entity = saveEntity("deleted", true);

        Optional<SoftDeleteRepoTestEntity> result = repository.findById(entity.getId());
        assertFalse(result.isPresent());
    }

    @Test
    void findById_returnsActiveEntity() {
        SoftDeleteRepoTestEntity entity = saveEntity("active", false);

        Optional<SoftDeleteRepoTestEntity> result = repository.findById(entity.getId());
        assertTrue(result.isPresent());
        assertEquals("active", result.get().getName());
    }

    @Test
    void findById_returnsEmptyForNullId() {
        Optional<SoftDeleteRepoTestEntity> result = repository.findById(null);
        assertFalse(result.isPresent());
    }

    @Test
    void count_filtersDeletedEntities() {
        saveEntity("active1", false);
        saveEntity("active2", false);
        saveEntity("deleted", true);

        assertEquals(2, repository.count());
    }

    @Test
    void exists_filtersDeletedEntities() {
        saveEntity("active", false);
        saveEntity("deleted", true);

        // exists with specification that matches deleted entity
        assertTrue(repository.exists((root, query, cb) -> cb.equal(root.get("name"), "active")));
        assertFalse(repository.exists((root, query, cb) -> cb.equal(root.get("name"), "deleted")));
    }

    @Test
    void autoFilterDisabled_returnsAllEntities() {
        saveEntity("active", false);
        saveEntity("deleted", true);

        SoftDeleteJpaRepository.setAutoFilterEnabled(false);
        List<SoftDeleteRepoTestEntity> result = repository.findAll();
        assertEquals(2, result.size());
    }

    @Test
    void ignoreSoftDelete_returnsAllEntities() {
        saveEntity("active", false);
        saveEntity("deleted", true);

        SoftDeleteContext.pushIgnore();
        try {
            List<SoftDeleteRepoTestEntity> result = repository.findAll();
            assertEquals(2, result.size());
        } finally {
            SoftDeleteContext.popIgnore();
        }
    }

    @Test
    void staticMethods_work() {
        assertTrue(SoftDeleteJpaRepository.isAutoFilterEnabled());

        SoftDeleteJpaRepository.setAutoFilterEnabled(false);
        assertFalse(SoftDeleteJpaRepository.isAutoFilterEnabled());

        SoftDeleteJpaRepository.setAutoFilterEnabled(true);
        assertTrue(SoftDeleteJpaRepository.isAutoFilterEnabled());
    }

    private SoftDeleteRepoTestEntity saveEntity(String name, boolean deleted) {
        SoftDeleteRepoTestEntity entity = new SoftDeleteRepoTestEntity();
        entity.setName(name);
        entity.setDeleted(deleted);
        return repository.save(entity);
    }
}
