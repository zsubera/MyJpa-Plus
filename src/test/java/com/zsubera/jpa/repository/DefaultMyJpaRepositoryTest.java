package com.zsubera.jpa.repository;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
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
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ContextConfiguration;

@DataJpaTest
@org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase(
    replace = org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = DefaultMyJpaRepositoryTest.TestConfig.class)
class DefaultMyJpaRepositoryTest {

    @SpringBootApplication
    @EntityScan(basePackageClasses = SoftDeleteRepoTestEntity.class)
    @EnableJpaRepositories(basePackages = "com.zsubera.jpa.repository",
        repositoryBaseClass = DefaultMyJpaRepository.class)
    static class TestConfig {}

    @Autowired
    private SoftDeleteRepoTestRepository repository;

    @BeforeEach
    void setup() {
        DefaultMyJpaRepository.setAutoFilterEnabled(true);
        DefaultMyJpaRepository.setBlockUnconditionalDelete(true);
        SoftDeleteContext.reset();
    }

    @AfterEach
    void cleanup() {
        SoftDeleteContext.reset();
        DefaultMyJpaRepository.setAutoFilterEnabled(true);
        DefaultMyJpaRepository.setBlockUnconditionalDelete(true);
        DefaultMyJpaRepository.clearThreadLocal();
    }

    // ---- findAll ----

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
    void findAll_withSpecification_filtersDeletedEntities() {
        saveEntity("target", false);
        saveEntity("other", false);
        saveEntity("target-deleted", true);

        Specification<SoftDeleteRepoTestEntity> spec = (root, query, cb) -> cb.like(root.get("name"), "target%");
        List<SoftDeleteRepoTestEntity> result = repository.findAll(spec);
        assertEquals(1, result.size());
        assertEquals("target", result.get(0).getName());
    }

    @Test
    void findAll_withSpecificationAndPageable_filtersDeletedEntities() {
        saveEntity("a", false);
        saveEntity("b", false);
        saveEntity("c", true);

        Specification<SoftDeleteRepoTestEntity> spec = (root, query, cb) -> cb.conjunction();
        Page<SoftDeleteRepoTestEntity> page = repository.findAll(spec, PageRequest.of(0, 10));
        assertEquals(2, page.getTotalElements());
    }

    @Test
    void findAll_withSpecificationAndSort_filtersDeletedEntities() {
        saveEntity("a", false);
        saveEntity("b", false);
        saveEntity("c", true);

        Specification<SoftDeleteRepoTestEntity> spec = (root, query, cb) -> cb.conjunction();
        List<SoftDeleteRepoTestEntity> result = repository.findAll(spec, Sort.by("name"));
        assertEquals(2, result.size());
    }

    // ---- findById ----

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

    // ---- findOne ----

    @Test
    void findOne_filtersDeletedEntities() {
        saveEntity("target", false);
        saveEntity("target-deleted", true);

        Specification<SoftDeleteRepoTestEntity> spec = (root, query, cb) -> cb.like(root.get("name"), "target%");
        Optional<SoftDeleteRepoTestEntity> result = repository.findOne(spec);
        assertTrue(result.isPresent());
        assertFalse(result.get().getDeleted());
    }

    // ---- count ----

    @Test
    void count_filtersDeletedEntities() {
        saveEntity("active1", false);
        saveEntity("active2", false);
        saveEntity("deleted", true);

        assertEquals(2, repository.count());
    }

    @Test
    void count_withSpecification_filtersDeletedEntities() {
        saveEntity("match", false);
        saveEntity("match-deleted", true);
        saveEntity("other", false);

        Specification<SoftDeleteRepoTestEntity> spec = (root, query, cb) -> cb.equal(root.get("name"), "match");
        assertEquals(1, repository.count(spec));
    }

    // ---- exists ----

    @Test
    void exists_filtersDeletedEntities() {
        saveEntity("active", false);
        saveEntity("deleted", true);

        assertTrue(repository.exists((root, query, cb) -> cb.equal(root.get("name"), "active")));
        assertFalse(repository.exists((root, query, cb) -> cb.equal(root.get("name"), "deleted")));
    }

    // ---- findAllById ----

    @Test
    void findAllById_filtersDeletedEntities() {
        SoftDeleteRepoTestEntity active = saveEntity("active", false);
        SoftDeleteRepoTestEntity deleted = saveEntity("deleted", true);

        List<SoftDeleteRepoTestEntity> result = repository.findAllById(Arrays.asList(active.getId(), deleted.getId()));
        assertEquals(1, result.size());
        assertEquals("active", result.get(0).getName());
    }

    @Test
    void findAllById_returnsEmptyForEmptyIds() {
        saveEntity("active", false);

        List<SoftDeleteRepoTestEntity> result = repository.findAllById(List.of());
        assertTrue(result.isEmpty());
    }

    // ---- existsById ----

    @Test
    void existsById_returnsFalseForDeletedEntity() {
        SoftDeleteRepoTestEntity deleted = saveEntity("deleted", true);
        assertFalse(repository.existsById(deleted.getId()));
    }

    @Test
    void existsById_returnsTrueForActiveEntity() {
        SoftDeleteRepoTestEntity active = saveEntity("active", false);
        assertTrue(repository.existsById(active.getId()));
    }

    @Test
    void existsById_returnsFalseForNullId() {
        assertFalse(repository.existsById(null));
    }

    // ---- deleteById ----

    @Test
    void deleteById_softDeletesEntity() {
        SoftDeleteRepoTestEntity entity = saveEntity("toDelete", false);
        Long id = entity.getId();

        repository.deleteById(id);
        repository.flush();

        Optional<SoftDeleteRepoTestEntity> result = repository.findById(id);
        assertFalse(result.isPresent());
    }

    @Test
    void deleteById_throwsForNullId() {
        assertThrows(Exception.class, () -> repository.deleteById(null));
    }

    // ---- deleteAll (soft delete) ----

    @Test
    void deleteAll_softDeletesAllNonDeletedRecords() {
        saveEntity("a1", false);
        saveEntity("a2", false);
        saveEntity("a3", false);

        repository.deleteAll();

        List<SoftDeleteRepoTestEntity> all = findAllIncludingDeleted();
        assertEquals(3, all.size(), "All records should still exist");
        assertTrue(all.stream().allMatch(e -> Boolean.TRUE.equals(e.getDeleted())),
            "All records should be soft-deleted");
    }

    @Test
    void deleteAll_alreadyDeletedRecordsAreNotDoubleProcessed() {
        SoftDeleteRepoTestEntity active = saveEntity("active", false);
        SoftDeleteRepoTestEntity alreadyDeleted = saveEntity("already-deleted", true);

        repository.deleteAll();

        // Re-fetch from DB since softDeleteAll uses native SQL + em.clear(), invalidating the persistence context
        List<SoftDeleteRepoTestEntity> all = findAllIncludingDeleted();
        assertEquals(2, all.size());
        SoftDeleteRepoTestEntity refetchedActive = all.stream().filter(e -> "active".equals(e.getName())).findFirst()
            .orElseThrow(() -> new AssertionError("Active entity should exist after soft delete"));
        assertTrue(Boolean.TRUE.equals(refetchedActive.getDeleted()), "Active record should now be soft-deleted");
        SoftDeleteRepoTestEntity refetchedDeleted = all.stream().filter(e -> "already-deleted".equals(e.getName()))
            .findFirst().orElseThrow(() -> new AssertionError("Already-deleted entity should exist"));
        assertTrue(Boolean.TRUE.equals(refetchedDeleted.getDeleted()),
            "Already-deleted record should remain soft-deleted");
    }

    @Test
    void deleteAllInBatch_softDeletesAllNonDeletedRecords() {
        saveEntity("b1", false);
        saveEntity("b2", false);
        saveEntity("b3", false);

        repository.deleteAllInBatch();

        List<SoftDeleteRepoTestEntity> all = findAllIncludingDeleted();
        assertEquals(3, all.size(), "All records should still exist");
        assertTrue(all.stream().allMatch(e -> Boolean.TRUE.equals(e.getDeleted())),
            "All records should be soft-deleted");
    }

    private List<SoftDeleteRepoTestEntity> findAllIncludingDeleted() {
        SoftDeleteContext.pushIgnore();
        try {
            return repository.findAll();
        } finally {
            SoftDeleteContext.popIgnore();
        }
    }

    // ---- deleteAllById ----

    @Test
    void deleteAllById_throwsForNullIds() {
        assertThrows(Exception.class, () -> repository.deleteAllById(null));
    }

    // ---- deleteInBatch ----

    @Test
    void deleteInBatch_throwsForNullEntities() {
        assertThrows(Exception.class, () -> repository.deleteInBatch(null));
    }
    // ---- blockUnconditionalDelete ----

    @Test
    void deleteAll_blockedWhenAutoFilterDisabled() {
        saveEntity("a", false);
        DefaultMyJpaRepository.setAutoFilterEnabled(false);
        DefaultMyJpaRepository.setBlockUnconditionalDelete(true);

        assertThrows(Exception.class, () -> repository.deleteAll());
    }

    @Test
    void deleteAllAllById_blockedWhenAutoFilterDisabled() {
        SoftDeleteRepoTestEntity entity = saveEntity("a", false);
        DefaultMyJpaRepository.setAutoFilterEnabled(false);
        DefaultMyJpaRepository.setBlockUnconditionalDelete(true);

        assertThrows(Exception.class, () -> repository.deleteAllById(Arrays.asList(entity.getId())));
    }

    @Test
    void deleteAllInBatch_blockedWhenAutoFilterDisabled() {
        saveEntity("a", false);
        DefaultMyJpaRepository.setAutoFilterEnabled(false);
        DefaultMyJpaRepository.setBlockUnconditionalDelete(true);

        assertThrows(Exception.class, () -> repository.deleteAllInBatch());
    }

    // ---- static methods ----

    @Test
    void autoFilterEnabled_staticMethods() {
        assertTrue(DefaultMyJpaRepository.isAutoFilterEnabled());

        DefaultMyJpaRepository.setAutoFilterEnabled(false);
        assertFalse(DefaultMyJpaRepository.isAutoFilterEnabled());

        DefaultMyJpaRepository.setAutoFilterEnabled(true);
        assertTrue(DefaultMyJpaRepository.isAutoFilterEnabled());
    }

    @Test
    void blockUnconditionalDelete_staticMethods() {
        assertTrue(DefaultMyJpaRepository.isBlockUnconditionalDelete());

        DefaultMyJpaRepository.setBlockUnconditionalDelete(false);
        assertFalse(DefaultMyJpaRepository.isBlockUnconditionalDelete());

        DefaultMyJpaRepository.setBlockUnconditionalDelete(true);
        assertTrue(DefaultMyJpaRepository.isBlockUnconditionalDelete());
    }

    @Test
    void withAutoFilterOverride_runsActionAndRestoresValue() {
        saveEntity("active", false);
        saveEntity("deleted", true);

        // Override to disable filtering within the block
        DefaultMyJpaRepository.withAutoFilterOverride(false, () -> {
            List<SoftDeleteRepoTestEntity> result = repository.findAll();
            assertEquals(2, result.size(), "Auto-filter should be disabled inside override block");
        });

        // After block, filtering should be restored
        List<SoftDeleteRepoTestEntity> result = repository.findAll();
        assertEquals(1, result.size(), "Auto-filter should be restored after override block");
    }

    @Test
    void withAutoFilterOverride_nestedRestoresPreviousValue() {
        saveEntity("active", false);
        saveEntity("deleted", true);

        DefaultMyJpaRepository.withAutoFilterOverride(false, () -> {
            // Outer: filtering disabled
            List<SoftDeleteRepoTestEntity> outer = repository.findAll();
            assertEquals(2, outer.size());

            DefaultMyJpaRepository.withAutoFilterOverride(true, () -> {
                // Inner: filtering re-enabled
                List<SoftDeleteRepoTestEntity> inner = repository.findAll();
                assertEquals(1, inner.size());
            });

            // After inner: should restore to outer (disabled)
            List<SoftDeleteRepoTestEntity> afterInner = repository.findAll();
            assertEquals(2, afterInner.size());
        });

        // After outer: should restore to global (enabled)
        List<SoftDeleteRepoTestEntity> afterOuter = repository.findAll();
        assertEquals(1, afterOuter.size());
    }

    @Test
    void withAutoFilterOverride_supplierReturnsValue() {
        String result = DefaultMyJpaRepository.withAutoFilterOverride(null, () -> "hello");
        assertEquals("hello", result);
    }

    // ---- ignore soft delete ----

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

    private SoftDeleteRepoTestEntity saveEntity(String name, boolean deleted) {
        SoftDeleteRepoTestEntity entity = new SoftDeleteRepoTestEntity();
        entity.setName(name);
        entity.setDeleted(deleted);
        return repository.save(entity);
    }

    // ---- deleteAll hard delete audit path ----

    @Test
    void deleteAll_hardDeleteWhenAutoFilterDisabled() {
        DefaultMyJpaRepository.setAutoFilterEnabled(false);
        DefaultMyJpaRepository.setBlockUnconditionalDelete(false);
        try {
            saveEntity("a", false);
            saveEntity("b", false);
            repository.deleteAll();
            assertEquals(0, repository.count());
        } finally {
            DefaultMyJpaRepository.setAutoFilterEnabled(true);
            DefaultMyJpaRepository.setBlockUnconditionalDelete(true);
        }
    }

    // ---- deleteAllById blocked + hard delete paths ----

    @Test
    void deleteAllById_blockedWhenAutoFilterDisabled() {
        DefaultMyJpaRepository.setAutoFilterEnabled(false);
        DefaultMyJpaRepository.setBlockUnconditionalDelete(true);
        try {
            SoftDeleteRepoTestEntity e1 = saveEntity("blocked", false);
            assertThrows(Exception.class, () -> repository.deleteAllById(List.of(e1.getId())));
        } finally {
            DefaultMyJpaRepository.setAutoFilterEnabled(true);
        }
    }

    @Test
    void deleteInBatch_hardDeleteWhenAutoFilterDisabled() {
        DefaultMyJpaRepository.setAutoFilterEnabled(false);
        DefaultMyJpaRepository.setBlockUnconditionalDelete(false);
        try {
            SoftDeleteRepoTestEntity e1 = saveEntity("batchHard", false);
            repository.deleteInBatch(List.of(e1));
            assertEquals(0, repository.count());
        } finally {
            DefaultMyJpaRepository.setAutoFilterEnabled(true);
            DefaultMyJpaRepository.setBlockUnconditionalDelete(true);
        }
    }

    // ---- deleteAllInBatch blocked + hard delete ----

    @Test
    void deleteAllInBatch_hardDeleteWhenAutoFilterDisabled() {
        DefaultMyJpaRepository.setAutoFilterEnabled(false);
        DefaultMyJpaRepository.setBlockUnconditionalDelete(false);
        try {
            saveEntity("aibHard", false);
            repository.deleteAllInBatch();
            assertEquals(0, repository.count());
        } finally {
            DefaultMyJpaRepository.setAutoFilterEnabled(true);
            DefaultMyJpaRepository.setBlockUnconditionalDelete(true);
        }
    }

    // ---- ConfigProvider fallback paths ----

    @Test
    void setAutoFilterEnabled_globalConfigProviderNull() throws Exception {
        java.lang.reflect.Field f = DefaultMyJpaRepository.class.getDeclaredField("globalConfigProvider");
        f.setAccessible(true);
        Object old = f.get(null);
        try {
            f.set(null, null);
            DefaultMyJpaRepository.setAutoFilterEnabled(false);
            assertFalse(DefaultMyJpaRepository.isAutoFilterEnabled());
        } finally {
            f.set(null, old);
        }
    }

    @Test
    void setBlockUnconditionalDelete_globalConfigProviderNull() throws Exception {
        java.lang.reflect.Field f = DefaultMyJpaRepository.class.getDeclaredField("globalConfigProvider");
        f.setAccessible(true);
        Object old = f.get(null);
        try {
            f.set(null, null);
            DefaultMyJpaRepository.setBlockUnconditionalDelete(false);
            assertFalse(DefaultMyJpaRepository.isBlockUnconditionalDelete());
        } finally {
            f.set(null, old);
        }
    }

    @Test
    void isBlockUnconditionalDelete_configProviderNull() throws Exception {
        java.lang.reflect.Field f = DefaultMyJpaRepository.class.getDeclaredField("globalConfigProvider");
        f.setAccessible(true);
        Object old = f.get(null);
        try {
            f.set(null, null);
            assertTrue(DefaultMyJpaRepository.isBlockUnconditionalDelete());
        } finally {
            f.set(null, old);
        }
    }

    @Test
    void setAutoFilterEnabled_withNonMutableConfigProvider_createsNewProvider() throws Exception {
        java.lang.reflect.Field f = DefaultMyJpaRepository.class.getDeclaredField("globalConfigProvider");
        f.setAccessible(true);
        Object old = f.get(null);
        try {
            // Set a non-MutableConfigProvider (lambda implementing ConfigProvider)
            DefaultMyJpaRepository.ConfigProvider nonMutable = new DefaultMyJpaRepository.ConfigProvider() {
                @Override
                public boolean isAutoFilterEnabled() {
                    return true;
                }

                @Override
                public boolean isBlockUnconditionalDelete() {
                    return true;
                }
            };
            f.set(null, nonMutable);
            DefaultMyJpaRepository.setAutoFilterEnabled(false);
            assertFalse(DefaultMyJpaRepository.isAutoFilterEnabled());
        } finally {
            f.set(null, old);
        }
    }

    @Test
    void setBlockUnconditionalDelete_withNonMutableConfigProvider_createsNewProvider() throws Exception {
        java.lang.reflect.Field f = DefaultMyJpaRepository.class.getDeclaredField("globalConfigProvider");
        f.setAccessible(true);
        Object old = f.get(null);
        try {
            DefaultMyJpaRepository.ConfigProvider nonMutable = new DefaultMyJpaRepository.ConfigProvider() {
                @Override
                public boolean isAutoFilterEnabled() {
                    return true;
                }

                @Override
                public boolean isBlockUnconditionalDelete() {
                    return true;
                }
            };
            f.set(null, nonMutable);
            DefaultMyJpaRepository.setBlockUnconditionalDelete(false);
            assertFalse(DefaultMyJpaRepository.isBlockUnconditionalDelete());
        } finally {
            f.set(null, old);
        }
    }

    @Test
    void deleteAllById_hardDeleteWhenAutoFilterDisabled() {
        DefaultMyJpaRepository.setAutoFilterEnabled(false);
        DefaultMyJpaRepository.setBlockUnconditionalDelete(false);
        try {
            SoftDeleteRepoTestEntity e1 = saveEntity("hardDeleteById", false);
            repository.deleteAllById(List.of(e1.getId()));
            repository.flush();
            assertEquals(0, repository.count());
        } finally {
            DefaultMyJpaRepository.setAutoFilterEnabled(true);
            DefaultMyJpaRepository.setBlockUnconditionalDelete(true);
        }
    }

    @Test
    void deleteAllById_emptyList_doesNothing() {
        saveEntity("keep", false);
        repository.deleteAllById(List.of());
        assertEquals(1, repository.count());
    }

    @Test
    void deleteInBatch_blockedWhenAutoFilterDisabled() {
        saveEntity("a", false);
        DefaultMyJpaRepository.setAutoFilterEnabled(false);
        DefaultMyJpaRepository.setBlockUnconditionalDelete(true);

        assertThrows(Exception.class, () -> repository.deleteInBatch(null));
    }

    @Test
    void deleteAllInBatch_hardDeleteWhenAutoFilterDisabled2() {
        DefaultMyJpaRepository.setAutoFilterEnabled(false);
        DefaultMyJpaRepository.setBlockUnconditionalDelete(false);
        try {
            saveEntity("aibHard2", false);
            repository.deleteAllInBatch();
            assertEquals(0, repository.count());
        } finally {
            DefaultMyJpaRepository.setAutoFilterEnabled(true);
            DefaultMyJpaRepository.setBlockUnconditionalDelete(true);
        }
    }

    @Test
    void withAutoFilterOverride_supplierNestedRestoresPrevious() {
        saveEntity("active", false);
        saveEntity("deleted", true);

        DefaultMyJpaRepository.withAutoFilterOverride(false, () -> {
            List<SoftDeleteRepoTestEntity> outer = repository.findAll();
            assertEquals(2, outer.size());

            String result = DefaultMyJpaRepository.withAutoFilterOverride(true, () -> {
                List<SoftDeleteRepoTestEntity> inner = repository.findAll();
                assertEquals(1, inner.size());
                return "inner-done";
            });
            assertEquals("inner-done", result);

            List<SoftDeleteRepoTestEntity> afterInner = repository.findAll();
            assertEquals(2, afterInner.size());
        });

        List<SoftDeleteRepoTestEntity> afterOuter = repository.findAll();
        assertEquals(1, afterOuter.size());
    }

    @Test
    void withAutoFilterOverride_nullValue_removesOverride() {
        saveEntity("active", false);

        DefaultMyJpaRepository.withAutoFilterOverride(false, () -> {
            List<SoftDeleteRepoTestEntity> result = repository.findAll();
            assertEquals(1, result.size());
        });

        List<SoftDeleteRepoTestEntity> after = repository.findAll();
        assertEquals(1, after.size());
    }

    @Test
    void isAutoFilterEnabled_configProviderReturnsValue() throws Exception {
        java.lang.reflect.Field f = DefaultMyJpaRepository.class.getDeclaredField("globalConfigProvider");
        f.setAccessible(true);
        Object old = f.get(null);
        try {
            DefaultMyJpaRepository.ConfigProvider provider =
                DefaultMyJpaRepository.createMutableConfigProvider(true, true);
            f.set(null, provider);
            assertTrue(DefaultMyJpaRepository.isAutoFilterEnabled());
            java.lang.reflect.Method setMethod =
                provider.getClass().getDeclaredMethod("setAutoFilterEnabled", boolean.class);
            setMethod.setAccessible(true);
            setMethod.invoke(provider, false);
            assertFalse(DefaultMyJpaRepository.isAutoFilterEnabled());
        } finally {
            f.set(null, old);
        }
    }

    @Test
    void createMutableConfigProvider_returnsWorkingProvider() {
        DefaultMyJpaRepository.ConfigProvider provider =
            DefaultMyJpaRepository.createMutableConfigProvider(true, false);
        assertTrue(provider.isAutoFilterEnabled());
        assertFalse(provider.isBlockUnconditionalDelete());
    }
}
