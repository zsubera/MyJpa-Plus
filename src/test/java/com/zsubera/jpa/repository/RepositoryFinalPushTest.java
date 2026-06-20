package com.zsubera.jpa.repository;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ContextConfiguration;

@DataJpaTest
@org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase(
    replace = org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = RepositoryFinalPushTest.TestConfig.class)
class RepositoryFinalPushTest {

    @SpringBootApplication
    @EntityScan(basePackageClasses = {SoftDeleteRepoTestEntity.class, SimpleTestEntity.class})
    @EnableJpaRepositories(basePackages = "com.zsubera.jpa.repository",
        repositoryBaseClass = DefaultMyJpaRepository.class)
    static class TestConfig {}

    @Autowired
    private SimpleTestRepository simpleRepository;

    @Autowired
    private SoftDeleteRepoTestRepository repository;

    @BeforeEach
    void setUp() {
        simpleRepository.deleteAll();
        simpleRepository.flush();
        repository.deleteAll();
        repository.flush();
        DefaultMyJpaRepository.setAutoFilterEnabled(true);
        DefaultMyJpaRepository.setBlockUnconditionalDelete(true);
        SoftDeleteContext.reset();
    }

    @AfterEach
    void tearDown() {
        SoftDeleteContext.reset();
        DefaultMyJpaRepository.setAutoFilterEnabled(true);
        DefaultMyJpaRepository.setBlockUnconditionalDelete(true);
    }

    // ---- SimpleTestEntity: all mergeSoftDeleteFilter paths (non-@SoftDelete entity) ----

    @Test
    void simpleEntity_findAll_noArg() {
        SimpleTestEntity e = saveSimple("a");
        assertEquals(1, simpleRepository.findAll().size());
    }

    @Test
    void simpleEntity_findAll_withSpec() {
        SimpleTestEntity e = saveSimple("a");
        assertEquals(1, simpleRepository.findAll(
            (Specification<SimpleTestEntity>)(root, query, cb) -> cb.conjunction()).size());
    }

    @Test
    void simpleEntity_findAll_withNullSpec() {
        SimpleTestEntity e = saveSimple("a");
        assertEquals(1, simpleRepository.findAll((Specification<SimpleTestEntity>)null).size());
    }

    @Test
    void simpleEntity_findAll_withSpecAndPageable() {
        SimpleTestEntity e = saveSimple("a");
        var page = simpleRepository.findAll(
            (Specification<SimpleTestEntity>)(root, query, cb) -> cb.conjunction(),
            org.springframework.data.domain.PageRequest.of(0, 10));
        assertEquals(1, page.getTotalElements());
    }

    @Test
    void simpleEntity_findAll_withSpecAndSort() {
        SimpleTestEntity e = saveSimple("a");
        assertEquals(1, simpleRepository.findAll(
            (Specification<SimpleTestEntity>)(root, query, cb) -> cb.conjunction(),
            org.springframework.data.domain.Sort.by("name")).size());
    }

    @Test
    void simpleEntity_findOne() {
        SimpleTestEntity e = saveSimple("a");
        assertTrue(simpleRepository.findOne(
            (Specification<SimpleTestEntity>)(root, query, cb) -> cb.conjunction()).isPresent());
    }

    @Test
    void simpleEntity_count() {
        SimpleTestEntity e = saveSimple("a");
        assertEquals(1, simpleRepository.count(
            (Specification<SimpleTestEntity>)(root, query, cb) -> cb.conjunction()));
    }

    @Test
    void simpleEntity_exists() {
        SimpleTestEntity e = saveSimple("a");
        assertTrue(simpleRepository.exists(
            (Specification<SimpleTestEntity>)(root, query, cb) -> cb.conjunction()));
    }

    @Test
    void simpleEntity_findAllById() {
        SimpleTestEntity e = saveSimple("a");
        assertEquals(1, simpleRepository.findAllById(java.util.List.of(e.getId())).size());
    }

    @Test
    void simpleEntity_existsById() {
        SimpleTestEntity e = saveSimple("a");
        assertTrue(simpleRepository.existsById(e.getId()));
    }

    @Test
    void simpleEntity_findById() {
        SimpleTestEntity e = saveSimple("a");
        assertTrue(simpleRepository.findById(e.getId()).isPresent());
    }

    // ---- MyJpaRepository: findNotDeletedAll with non-soft-delete entity ----

    @Test
    void simpleEntity_findNotDeletedAll_noArg() {
        SimpleTestEntity e = saveSimple("a");
        assertEquals(1, simpleRepository.findNotDeletedAll().size());
    }

    @Test
    void simpleEntity_findNotDeletedAll_withSpec() {
        SimpleTestEntity e = saveSimple("a");
        assertEquals(1, simpleRepository.findNotDeletedAll(
            (Specification<SimpleTestEntity>)(root, query, cb) -> cb.conjunction()).size());
    }

    @Test
    void simpleEntity_findNotDeletedAll_nullSpec() {
        SimpleTestEntity e = saveSimple("a");
        assertEquals(1, simpleRepository.findNotDeletedAll((Specification<SimpleTestEntity>)null).size());
    }

    @Test
    void simpleEntity_findNotDeletedOne_nullSpec_emptyTable() {
        assertFalse(simpleRepository.findNotDeletedOne((Specification<SimpleTestEntity>)null).isPresent());
    }

    @Test
    void simpleEntity_findNotDeletedOne_nullSpec_withData() {
        SimpleTestEntity e = saveSimple("a");
        assertTrue(simpleRepository.findNotDeletedOne((Specification<SimpleTestEntity>)null).isPresent());
    }

    @Test
    void simpleEntity_findNotDeletedOne_withSpec() {
        SimpleTestEntity e = saveSimple("a");
        assertTrue(simpleRepository.findNotDeletedOne(
            (Specification<SimpleTestEntity>)(root, query, cb) -> cb.conjunction()).isPresent());
    }

    @Test
    void simpleEntity_countNotDeleted_noArg() {
        SimpleTestEntity e = saveSimple("a");
        assertEquals(1, simpleRepository.countNotDeleted());
    }

    @Test
    void simpleEntity_countNotDeleted_withSpec() {
        SimpleTestEntity e = saveSimple("a");
        assertEquals(1, simpleRepository.countNotDeleted(
            (Specification<SimpleTestEntity>)(root, query, cb) -> cb.conjunction()));
    }

    @Test
    void simpleEntity_countNotDeleted_nullSpec() {
        SimpleTestEntity e = saveSimple("a");
        assertEquals(1, simpleRepository.countNotDeleted((Specification<SimpleTestEntity>)null));
    }

    // ---- SoftDeleteRepoTestEntity: deleteAllById with empty list ----

    @Test
    void deleteAllById_emptyList() {
        saveSoftDelete("a");
        repository.deleteAllById(java.util.List.of());
        assertEquals(1, repository.count());
    }

    @Test
    void deleteAllById_softDelete() {
        SoftDeleteRepoTestEntity e1 = saveSoftDelete("a");
        SoftDeleteRepoTestEntity e2 = saveSoftDelete("b");
        repository.deleteAllById(java.util.List.of(e1.getId(), e2.getId()));
        SoftDeleteContext.pushIgnore();
        try {
            assertEquals(2, repository.findAll().size());
        } finally {
            SoftDeleteContext.popIgnore();
        }
    }

    // ---- SoftDeleteRepoTestEntity: deleteAll soft delete ----

    @Test
    void deleteAll_softDelete() {
        saveSoftDelete("a");
        saveSoftDelete("b");
        repository.deleteAll();
        SoftDeleteContext.pushIgnore();
        try {
            assertEquals(2, repository.findAll().size());
        } finally {
            SoftDeleteContext.popIgnore();
        }
    }

    @Test
    void deleteAllInBatch_softDelete() {
        saveSoftDelete("a");
        saveSoftDelete("b");
        repository.deleteAllInBatch();
        SoftDeleteContext.pushIgnore();
        try {
            assertEquals(2, repository.findAll().size());
        } finally {
            SoftDeleteContext.popIgnore();
        }
    }

    // ---- SoftDeleteRepoTestEntity: hard delete when autoFilter disabled ----

    @Test
    void deleteAll_hardDelete() {
        DefaultMyJpaRepository.setAutoFilterEnabled(false);
        DefaultMyJpaRepository.setBlockUnconditionalDelete(false);
        saveSoftDelete("a");
        repository.deleteAll();
        assertEquals(0, repository.count());
    }

    @Test
    void deleteAllInBatch_hardDelete() {
        DefaultMyJpaRepository.setAutoFilterEnabled(false);
        DefaultMyJpaRepository.setBlockUnconditionalDelete(false);
        saveSoftDelete("a");
        repository.deleteAllInBatch();
        assertEquals(0, repository.count());
    }

    @Test
    void deleteAllById_hardDelete() {
        DefaultMyJpaRepository.setAutoFilterEnabled(false);
        DefaultMyJpaRepository.setBlockUnconditionalDelete(false);
        SoftDeleteRepoTestEntity e = saveSoftDelete("a");
        repository.deleteAllById(java.util.List.of(e.getId()));
        repository.flush();
        assertEquals(0, repository.count());
    }

    // ---- SoftDeleteRepoTestEntity: blocked paths ----

    @Test
    void deleteAll_blocked() {
        DefaultMyJpaRepository.setAutoFilterEnabled(false);
        DefaultMyJpaRepository.setBlockUnconditionalDelete(true);
        saveSoftDelete("a");
        assertThrows(Exception.class, () -> repository.deleteAll());
    }

    @Test
    void deleteAllInBatch_blocked() {
        DefaultMyJpaRepository.setAutoFilterEnabled(false);
        DefaultMyJpaRepository.setBlockUnconditionalDelete(true);
        saveSoftDelete("a");
        assertThrows(Exception.class, () -> repository.deleteAllInBatch());
    }

    @Test
    void deleteAllById_blocked() {
        DefaultMyJpaRepository.setAutoFilterEnabled(false);
        DefaultMyJpaRepository.setBlockUnconditionalDelete(true);
        SoftDeleteRepoTestEntity e = saveSoftDelete("a");
        assertThrows(Exception.class, () -> repository.deleteAllById(java.util.List.of(e.getId())));
    }

    // ---- SoftDeleteRepoTestEntity: findAll with ignore ----

    @Test
    void findAll_ignoreSoftDelete() {
        saveSoftDelete("active");
        saveSoftDeleteDeleted("deleted");
        SoftDeleteContext.pushIgnore();
        try {
            assertEquals(2, repository.findAll().size());
        } finally {
            SoftDeleteContext.popIgnore();
        }
    }

    @Test
    void findAll_withSpec_ignoreSoftDelete() {
        saveSoftDelete("active");
        saveSoftDeleteDeleted("deleted");
        SoftDeleteContext.pushIgnore();
        try {
            assertEquals(2, repository.findAll(
                (Specification<SoftDeleteRepoTestEntity>)(root, query, cb) -> cb.conjunction()).size());
        } finally {
            SoftDeleteContext.popIgnore();
        }
    }

    @Test
    void findAll_withSpecAndPageable_ignoreSoftDelete() {
        saveSoftDelete("active");
        saveSoftDeleteDeleted("deleted");
        SoftDeleteContext.pushIgnore();
        try {
            assertEquals(2, repository.findAll(
                (Specification<SoftDeleteRepoTestEntity>)(root, query, cb) -> cb.conjunction(),
                org.springframework.data.domain.PageRequest.of(0, 10)).getTotalElements());
        } finally {
            SoftDeleteContext.popIgnore();
        }
    }

    @Test
    void findAll_withSpecAndSort_ignoreSoftDelete() {
        saveSoftDelete("active");
        saveSoftDeleteDeleted("deleted");
        SoftDeleteContext.pushIgnore();
        try {
            assertEquals(2, repository.findAll(
                (Specification<SoftDeleteRepoTestEntity>)(root, query, cb) -> cb.conjunction(),
                org.springframework.data.domain.Sort.by("name")).size());
        } finally {
            SoftDeleteContext.popIgnore();
        }
    }

    // ---- Helpers ----

    private SimpleTestEntity saveSimple(String name) {
        SimpleTestEntity e = new SimpleTestEntity();
        e.setName(name);
        return simpleRepository.save(e);
    }

    private SoftDeleteRepoTestEntity saveSoftDelete(String name) {
        SoftDeleteRepoTestEntity e = new SoftDeleteRepoTestEntity();
        e.setName(name);
        e.setDeleted(false);
        return repository.save(e);
    }

    private SoftDeleteRepoTestEntity saveSoftDeleteDeleted(String name) {
        SoftDeleteRepoTestEntity e = new SoftDeleteRepoTestEntity();
        e.setName(name);
        e.setDeleted(true);
        return repository.save(e);
    }
}
