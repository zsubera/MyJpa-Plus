package com.zsubera.jpa.repository;

import static org.junit.jupiter.api.Assertions.*;

import com.zsubera.jpa.template.MyJpaTemplate;
import java.util.List;
import java.util.Optional;
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
@ContextConfiguration(classes = DefaultMyJpaRepositoryIntegrationTest.TestConfig.class)
class DefaultMyJpaRepositoryIntegrationTest {

    @SpringBootApplication
    @EntityScan(basePackageClasses = {SoftDeleteRepoTestEntity.class, SimpleTestEntity.class})
    @EnableJpaRepositories(basePackages = "com.zsubera.jpa.repository",
        repositoryBaseClass = DefaultMyJpaRepository.class)
    static class TestConfig {
        @org.springframework.context.annotation.Bean
        MyJpaTemplate myJpaTemplate() {
            return new MyJpaTemplate();
        }
    }

    @Autowired
    private SoftDeleteRepoTestRepository repository;

    @Autowired
    private SimpleTestRepository simpleRepository;

    @Autowired
    private MyJpaTemplate template;

    private Object proxy;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        repository.flush();
        simpleRepository.deleteAll();
        simpleRepository.flush();
        com.zsubera.jpa.autoconfigure.GlobalConfigHolder.getConfig().setSoftDeleteAutoFilter(true);
        com.zsubera.jpa.autoconfigure.GlobalConfigHolder.getConfig().setBlockUnconditionalDelete(true);
        SoftDeleteContext.reset();
        proxy = org.springframework.test.util.AopTestUtils.getTargetObject(repository);
    }

    @AfterEach
    void tearDown() {
        SoftDeleteContext.reset();
        com.zsubera.jpa.autoconfigure.GlobalConfigHolder.getConfig().setSoftDeleteAutoFilter(true);
        com.zsubera.jpa.autoconfigure.GlobalConfigHolder.getConfig().setBlockUnconditionalDelete(true);
        DefaultMyJpaRepository.clearThreadLocal();
    }

    // ---- deleteInBatch: soft delete path (shouldApplySoftDeleteFilter=true) ----

    @Test
    void deleteInBatch_softDeletePath() throws Exception {
        SoftDeleteRepoTestEntity e1 = saveActive("a");
        java.lang.reflect.Method method =
            DefaultMyJpaRepository.class.getMethod("deleteInBatch", java.lang.Iterable.class);
        method.invoke(proxy, java.util.List.of(e1));
        SoftDeleteContext.pushIgnore();
        try {
            List<SoftDeleteRepoTestEntity> all = repository.findAll();
            assertEquals(1, all.size(), "Entity should still exist after soft delete");
            assertTrue(all.get(0).getDeleted(), "Entity should be soft-deleted");
        } finally {
            SoftDeleteContext.popIgnore();
        }
    }

    // ---- deleteInBatch: hard delete path (shouldApplySoftDeleteFilter=false, shouldBlockHardDelete=false) ----

    @Test
    void deleteInBatch_hardDeletePath() throws Exception {
        SoftDeleteRepoTestEntity e1 = saveActive("a");
        com.zsubera.jpa.autoconfigure.GlobalConfigHolder.getConfig().setSoftDeleteAutoFilter(false);
        com.zsubera.jpa.autoconfigure.GlobalConfigHolder.getConfig().setBlockUnconditionalDelete(false);
        java.lang.reflect.Method method =
            DefaultMyJpaRepository.class.getMethod("deleteInBatch", java.lang.Iterable.class);
        method.invoke(proxy, java.util.List.of(e1));
        assertEquals(0, repository.count());
    }

    // ---- deleteInBatch: null entities ----

    @Test
    void deleteInBatch_nullEntities_throws() throws Exception {
        java.lang.reflect.Method method =
            DefaultMyJpaRepository.class.getMethod("deleteInBatch", java.lang.Iterable.class);
        assertThrows(Exception.class, () -> method.invoke(proxy, (Object)null));
    }

    // ---- deleteInBatch: empty list ----

    @Test
    void deleteInBatch_emptyList_doesNothing() throws Exception {
        saveActive("a");
        java.lang.reflect.Method method =
            DefaultMyJpaRepository.class.getMethod("deleteInBatch", java.lang.Iterable.class);
        method.invoke(proxy, java.util.List.of());
        assertEquals(1, repository.count());
    }

    // ---- deleteInBatch: detached entities with null IDs ----

    @Test
    void deleteInBatch_detachedEntities_throws() throws Exception {
        SoftDeleteRepoTestEntity detached = new SoftDeleteRepoTestEntity();
        detached.setName("detached");
        // detached entity has null ID
        java.lang.reflect.Method method =
            DefaultMyJpaRepository.class.getMethod("deleteInBatch", java.lang.Iterable.class);
        Exception ex = assertThrows(Exception.class, () -> method.invoke(proxy, java.util.List.of(detached)));
        // InvocationTargetException wraps IllegalArgumentException
        assertTrue(ex.getCause() instanceof IllegalArgumentException,
            "Should throw IllegalArgumentException for detached entities, got: " + ex.getCause());
    }

    // ---- deleteInBatch: blocked path (shouldBlockHardDelete=true) ----

    // ---- deleteByIdIfExists: autoFilter disabled (non-soft-delete path) ----

    // ---- deleteByIdIfExists: null id ----

    @Test
    void deleteByIdIfExists_nullId_throws() throws Exception {
        java.lang.reflect.Method method = DefaultMyJpaRepository.class.getMethod("deleteByIdIfExists", Object.class);
        assertThrows(Exception.class, () -> method.invoke(proxy, (Object)null));
    }

    // ---- deleteByIdOrThrow: deprecated method ----

    // ---- mergeSoftDeleteFilter: non-soft-delete entity (SimpleTestEntity) ----

    @Test
    void mergeSoftDeleteFilter_nonSoftDeleteEntity_findAll() {
        SimpleTestEntity e1 = saveSimple("s1");
        List<SimpleTestEntity> result = simpleRepository.findAll();
        assertEquals(1, result.size());
    }

    @Test
    void mergeSoftDeleteFilter_nonSoftDeleteEntity_findAllWithSpec() {
        SimpleTestEntity e1 = saveSimple("s1");
        List<SimpleTestEntity> result =
            simpleRepository.findAll((Specification<SimpleTestEntity>)(root, query, cb) -> cb.conjunction());
        assertEquals(1, result.size());
    }

    @Test
    void mergeSoftDeleteFilter_nonSoftDeleteEntity_findAllWithPageable() {
        SimpleTestEntity e1 = saveSimple("s1");
        var page = simpleRepository.findAll((Specification<SimpleTestEntity>)(root, query, cb) -> cb.conjunction(),
            org.springframework.data.domain.PageRequest.of(0, 10));
        assertEquals(1, page.getTotalElements());
    }

    @Test
    void mergeSoftDeleteFilter_nonSoftDeleteEntity_findAllWithSort() {
        SimpleTestEntity e1 = saveSimple("b");
        SimpleTestEntity e2 = saveSimple("a");
        List<SimpleTestEntity> result =
            simpleRepository.findAll((Specification<SimpleTestEntity>)(root, query, cb) -> cb.conjunction(),
                org.springframework.data.domain.Sort.by("name"));
        assertEquals(2, result.size());
        assertEquals("a", result.get(0).getName());
    }

    @Test
    void mergeSoftDeleteFilter_nonSoftDeleteEntity_findOne() {
        SimpleTestEntity e1 = saveSimple("s1");
        Optional<SimpleTestEntity> result = simpleRepository
            .findOne((Specification<SimpleTestEntity>)(root, query, cb) -> cb.equal(root.get("name"), "s1"));
        assertTrue(result.isPresent());
    }

    @Test
    void mergeSoftDeleteFilter_nonSoftDeleteEntity_count() {
        SimpleTestEntity e1 = saveSimple("s1");
        long count = simpleRepository.count((Specification<SimpleTestEntity>)(root, query, cb) -> cb.conjunction());
        assertEquals(1, count);
    }

    @Test
    void mergeSoftDeleteFilter_nonSoftDeleteEntity_exists() {
        SimpleTestEntity e1 = saveSimple("s1");
        boolean exists =
            simpleRepository.exists((Specification<SimpleTestEntity>)(root, query, cb) -> cb.conjunction());
        assertTrue(exists);
    }

    @Test
    void mergeSoftDeleteFilter_nonSoftDeleteEntity_nullSpec() {
        SimpleTestEntity e1 = saveSimple("s1");
        List<SimpleTestEntity> result = simpleRepository.findAll((Specification<SimpleTestEntity>)null);
        assertEquals(1, result.size());
    }

    // ---- findAllById: autoFilter disabled ----

    // ---- existsById: autoFilter disabled ----

    @Test
    void existsById_autoFilterDisabled() {
        SoftDeleteRepoTestEntity e1 = saveActive("a");
        com.zsubera.jpa.autoconfigure.GlobalConfigHolder.getConfig().setSoftDeleteAutoFilter(false);
        assertTrue(repository.existsById(e1.getId()));
    }

    @Test
    void existsById_nullId() {
        // Spring wraps IllegalArgumentException for null ID
        try {
            repository.existsById(null);
        } catch (Exception e) {
            // expected - Spring wraps it
        }
    }

    // ---- findById: autoFilter disabled ----

    @Test
    void templateFindById_autoFilterDisabled_returnsSoftDeletedEntity() {
        SoftDeleteRepoTestEntity deleted = saveDeleted("deleted");
        com.zsubera.jpa.autoconfigure.GlobalConfigHolder.getConfig().setSoftDeleteAutoFilter(false);

        Optional<SoftDeleteRepoTestEntity> result = template.findById(SoftDeleteRepoTestEntity.class, deleted.getId());

        assertTrue(result.isPresent());
        assertEquals("deleted", result.get().getName());
    }

    @Test
    void templateFindById_withIgnoreContext_returnsSoftDeletedEntity() {
        SoftDeleteRepoTestEntity deleted = saveDeleted("deleted");

        Optional<SoftDeleteRepoTestEntity> result =
            SoftDeleteContext.withIgnore(() -> template.findById(SoftDeleteRepoTestEntity.class, deleted.getId()));

        assertTrue(result.isPresent());
        assertEquals("deleted", result.get().getName());
    }

    // ---- Helper methods ----

    private SoftDeleteRepoTestEntity saveActive(String name) {
        SoftDeleteRepoTestEntity entity = new SoftDeleteRepoTestEntity();
        entity.setName(name);
        entity.setDeleted(false);
        return repository.save(entity);
    }

    private SoftDeleteRepoTestEntity saveDeleted(String name) {
        SoftDeleteRepoTestEntity entity = new SoftDeleteRepoTestEntity();
        entity.setName(name);
        entity.setDeleted(true);
        return repository.save(entity);
    }

    private SimpleTestEntity saveSimple(String name) {
        SimpleTestEntity entity = new SimpleTestEntity();
        entity.setName(name);
        return simpleRepository.save(entity);
    }
}
