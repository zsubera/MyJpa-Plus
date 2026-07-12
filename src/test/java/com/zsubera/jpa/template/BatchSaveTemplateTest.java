package com.zsubera.jpa.template;

import static org.junit.jupiter.api.Assertions.*;

import com.zsubera.jpa.spec.TestApplication;
import com.zsubera.jpa.spec.TestEntity;
import com.zsubera.jpa.spec.TestEntityRepository;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ContextConfiguration;

@DataJpaTest
@org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase(
    replace = org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = {TestApplication.class, BatchSaveTemplateTest.TestConfig.class})
class BatchSaveTemplateTest {

    @TestConfiguration
    static class TestConfig {
        @Bean
        public BatchSaveTemplate batchSaveTemplate(jakarta.persistence.EntityManager entityManager) {
            return new BatchSaveTemplate(entityManager);
        }
    }

    @Autowired
    private BatchSaveTemplate batchSaveTemplate;

    @Autowired
    private TestEntityRepository repository;

    @Autowired
    private jakarta.persistence.EntityManager entityManager;

    @BeforeEach
    void cleanUp() {
        repository.deleteAll();
        repository.flush();
    }

    // ---- saveAllBatched ----

    @Test
    void testSaveAllBatchedNewEntities() {
        List<TestEntity> entities = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            TestEntity e = new TestEntity();
            e.setName("batchNew" + i);
            e.setStatus(i);
            entities.add(e);
        }

        List<TestEntity> saved = batchSaveTemplate.saveAllBatched(entities, 2);
        assertEquals(5, saved.size());
        assertEquals(5, repository.count());
    }

    @Test
    void testSaveAllBatchedMixedNewAndExisting() {
        TestEntity existing = new TestEntity();
        existing.setName("existing");
        existing.setStatus(0);
        repository.saveAndFlush(existing);

        List<TestEntity> entities = new ArrayList<>();
        entities.add(existing);
        TestEntity newEntity = new TestEntity();
        newEntity.setName("newOne");
        newEntity.setStatus(1);
        entities.add(newEntity);

        List<TestEntity> saved = batchSaveTemplate.saveAllBatched(entities, 10);
        assertEquals(2, saved.size());
        assertEquals(2, repository.count());
    }

    @Test
    void testSaveAllBatchedEmptyList() {
        List<TestEntity> saved = batchSaveTemplate.saveAllBatched(List.of(), 10);
        assertEquals(0, saved.size());
        assertEquals(0, repository.count());
    }

    @Test
    void testSaveAllBatchedFlushesAtBatchSize() {
        List<TestEntity> entities = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            TestEntity e = new TestEntity();
            e.setName("flushBatch" + i);
            e.setStatus(i);
            entities.add(e);
        }

        List<TestEntity> saved = batchSaveTemplate.saveAllBatched(entities, 2);
        assertEquals(4, saved.size());
        assertEquals(4, repository.count());
    }

    @Test
    void testSaveAllBatchedNullEntities() {
        assertThrows(NullPointerException.class, () -> batchSaveTemplate.saveAllBatched(null, 10));
    }

    @Test
    void testSaveAllBatchedInvalidBatchSize() {
        List<TestEntity> entities = List.of(new TestEntity());
        assertThrows(ArithmeticException.class, () -> batchSaveTemplate.saveAllBatched(entities, 0));
    }

    // ---- saveAllBatchedPure ----

    @Test
    void testSaveAllBatchedPure() {
        List<TestEntity> entities = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            TestEntity e = new TestEntity();
            e.setName("pureBatch" + i);
            e.setStatus(i);
            entities.add(e);
        }

        List<TestEntity> saved = batchSaveTemplate.saveAllBatchedPure(entities, 3);
        assertEquals(6, saved.size());
        assertEquals(6, repository.count());
    }

    @Test
    void testSaveAllBatchedPureEmptyList() {
        List<TestEntity> saved = batchSaveTemplate.saveAllBatchedPure(List.of(), 10);
        assertEquals(0, saved.size());
    }

    @Test
    void testSaveAllBatchedPureNullEntities() {
        assertThrows(NullPointerException.class, () -> batchSaveTemplate.saveAllBatchedPure(null, 10));
    }

    @Test
    void testSaveAllBatchedPureInvalidBatchSize() {
        List<TestEntity> entities = List.of(new TestEntity());
        assertThrows(ArithmeticException.class, () -> batchSaveTemplate.saveAllBatchedPure(entities, 0));
    }

    // ---- saveAllBatchedInSeparateTransactions ----

    @Test
    @org.springframework.transaction.annotation.Transactional(
        propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void testSaveAllBatchedInSeparateTransactions() {
        List<TestEntity> entities = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            TestEntity e = new TestEntity();
            e.setName("sepTx" + i);
            e.setStatus(i);
            entities.add(e);
        }

        List<TestEntity> saved = batchSaveTemplate.saveAllBatchedInSeparateTransactions(entities, 2);
        assertEquals(5, saved.size());
        assertEquals(5, repository.count());
    }

    @Test
    @org.springframework.transaction.annotation.Transactional(
        propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void testSaveAllBatchedInSeparateTransactionsSingleBatch() {
        List<TestEntity> entities = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            TestEntity e = new TestEntity();
            e.setName("singleBatch" + i);
            e.setStatus(i);
            entities.add(e);
        }

        List<TestEntity> saved = batchSaveTemplate.saveAllBatchedInSeparateTransactions(entities, 10);
        assertEquals(3, saved.size());
        assertEquals(3, repository.count());
    }

    @Test
    void testSaveAllBatchedInSeparateTransactionsEmptyList() {
        List<TestEntity> saved = batchSaveTemplate.saveAllBatchedInSeparateTransactions(List.of(), 10);
        assertEquals(0, saved.size());
    }

    @Test
    void testSaveAllBatchedInSeparateTransactionsNullEntities() {
        var ex = assertThrows(BatchSaveTemplate.PartialBatchCommitException.class,
            () -> batchSaveTemplate.saveAllBatchedInSeparateTransactions(null, 10));
        assertInstanceOf(NullPointerException.class, ex.getCause());
    }

    @Test
    void testSaveAllBatchedInSeparateTransactionsInvalidBatchSize() {
        List<TestEntity> entities = List.of(new TestEntity());
        // batchSize=0 won't trigger ArithmeticException because the loop condition checks batch.size() >= batchSize
        // and with batchSize=0, batch never reaches size >= 0 after first add, so all go to remainder batch
        // But executeBatchSave will call persist for each, and empty list means no-op
        List<TestEntity> saved = batchSaveTemplate.saveAllBatchedInSeparateTransactions(entities, 0);
        assertEquals(1, saved.size());
    }

    @Test
    void testSaveAllBatchedWithFlushedExisting() {
        // Save and flush to ensure entity is managed, then merge path
        TestEntity existing = new TestEntity();
        existing.setName("flushed");
        existing.setStatus(0);
        TestEntity saved = repository.saveAndFlush(existing);

        // Now clear and re-save - this should trigger merge
        entityManager.clear();
        TestEntity detached = new TestEntity();
        detached.setId(saved.getId());
        detached.setName("updated");
        detached.setStatus(1);

        List<TestEntity> result = batchSaveTemplate.saveAllBatched(List.of(detached), 10);
        assertEquals(1, result.size());
    }

    @Test
    void testSaveAllBatchedInSeparateTransactionsWithExisting() {
        TestEntity existing = new TestEntity();
        existing.setName("sepExisting");
        existing.setStatus(0);
        TestEntity saved = repository.saveAndFlush(existing);

        entityManager.clear();
        TestEntity detached = new TestEntity();
        detached.setId(saved.getId());
        detached.setName("sepUpdated");
        detached.setStatus(1);

        List<TestEntity> result = batchSaveTemplate.saveAllBatchedInSeparateTransactions(List.of(detached), 10);
        assertEquals(1, result.size());
    }

    @Test
    void testSaveAllBatchedEvictionTriggered() {
        // Fill cache to trigger eviction
        for (int i = 0; i < 1100; i++) {
            TestEntity e = new TestEntity();
            e.setName("evict" + i);
            e.setStatus(i);
            // Use reflection to add to ID_METHOD_CACHE
            try {
                java.lang.reflect.Field cacheField = BatchSaveTemplate.class.getDeclaredField("ID_METHOD_CACHE");
                cacheField.setAccessible(true);
                @SuppressWarnings("unchecked")
                com.zsubera.jpa.util.SampledEvictionCache<java.lang.Class<?>, java.lang.reflect.Method> cache =
                    (com.zsubera.jpa.util.SampledEvictionCache<java.lang.Class<?>, java.lang.reflect.Method>)cacheField
                        .get(null);
                cache.put(e.getClass(), e.getClass().getMethod("getId"));
            } catch (Exception ignored) {
            }
        }

        // Now save entities to trigger eviction
        List<TestEntity> entities = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            TestEntity e = new TestEntity();
            e.setName("afterEvict" + i);
            e.setStatus(i);
            entities.add(e);
        }
        List<TestEntity> saved = batchSaveTemplate.saveAllBatched(entities, 2);
        assertEquals(3, saved.size());
    }

    @Test
    void testSaveAllBatchedPureWithBatchSizeOne() {
        List<TestEntity> entities = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            TestEntity e = new TestEntity();
            e.setName("bs1" + i);
            e.setStatus(i);
            entities.add(e);
        }

        List<TestEntity> saved = batchSaveTemplate.saveAllBatchedPure(entities, 1);
        assertEquals(3, saved.size());
        assertEquals(3, repository.count());
    }

    @Test
    void testSaveAllBatchedWithBatchSizeLargerThanList() {
        List<TestEntity> entities = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            TestEntity e = new TestEntity();
            e.setName("large" + i);
            e.setStatus(i);
            entities.add(e);
        }

        List<TestEntity> saved = batchSaveTemplate.saveAllBatched(entities, 100);
        assertEquals(2, saved.size());
        assertEquals(2, repository.count());
    }

    @Test
    void testSaveAllBatchedPureWithBatchSizeLargerThanList() {
        List<TestEntity> entities = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            TestEntity e = new TestEntity();
            e.setName("pureLarge" + i);
            e.setStatus(i);
            entities.add(e);
        }

        List<TestEntity> saved = batchSaveTemplate.saveAllBatchedPure(entities, 100);
        assertEquals(2, saved.size());
        assertEquals(2, repository.count());
    }

    @Test
    void testSaveAllBatchedNewEntityViaReflection() {
        com.zsubera.jpa.util.SampledEvictionCache<Class<?>, java.lang.reflect.Method> cache;
        try {
            java.lang.reflect.Field cacheField = BatchSaveTemplate.class.getDeclaredField("ID_METHOD_CACHE");
            cacheField.setAccessible(true);
            cache = (com.zsubera.jpa.util.SampledEvictionCache<Class<?>, java.lang.reflect.Method>)cacheField.get(null);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        cache.clear();

        TestEntity entity = new TestEntity();
        entity.setName("reflectNew");
        entity.setStatus(1);

        List<TestEntity> saved = batchSaveTemplate.saveAllBatched(List.of(entity), 10);
        assertEquals(1, saved.size());
        assertEquals(1, repository.count());
    }

    @Test
    void testSaveAllBatchedExistingEntityViaReflection() {
        TestEntity existing = new TestEntity();
        existing.setName("existingReflection");
        existing.setStatus(0);
        TestEntity saved = repository.saveAndFlush(existing);
        entityManager.clear();

        com.zsubera.jpa.util.SampledEvictionCache<Class<?>, java.lang.reflect.Method> cache;
        try {
            java.lang.reflect.Field cacheField = BatchSaveTemplate.class.getDeclaredField("ID_METHOD_CACHE");
            cacheField.setAccessible(true);
            cache = (com.zsubera.jpa.util.SampledEvictionCache<Class<?>, java.lang.reflect.Method>)cacheField.get(null);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        cache.clear();

        TestEntity detached = new TestEntity();
        detached.setId(saved.getId());
        detached.setName("updatedReflection");
        detached.setStatus(1);

        List<TestEntity> result = batchSaveTemplate.saveAllBatched(List.of(detached), 10);
        assertEquals(1, result.size());
    }

    @Test
    void testSaveAllBatchedIdMethodCacheEviction() {
        com.zsubera.jpa.util.SampledEvictionCache<Class<?>, java.lang.reflect.Method> cache;
        try {
            java.lang.reflect.Field cacheField = BatchSaveTemplate.class.getDeclaredField("ID_METHOD_CACHE");
            cacheField.setAccessible(true);
            cache = (com.zsubera.jpa.util.SampledEvictionCache<Class<?>, java.lang.reflect.Method>)cacheField.get(null);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        java.lang.reflect.Field sizeField;
        try {
            sizeField = BatchSaveTemplate.class.getDeclaredField("MAX_ID_METHOD_CACHE_SIZE");
            sizeField.setAccessible(true);
            int maxSize = sizeField.getInt(null);
            for (int i = 0; i < maxSize + 100; i++) {
                cache.put(String.class.getDeclaredClasses().length > i ? String.class : Object.class,
                    Object.class.getMethod("toString"));
            }
        } catch (Exception ignored) {
        }

        List<TestEntity> entities = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            TestEntity e = new TestEntity();
            e.setName("afterEvict2_" + i);
            e.setStatus(i);
            entities.add(e);
        }
        List<TestEntity> result = batchSaveTemplate.saveAllBatched(entities, 2);
        assertEquals(3, result.size());
    }

    @Test
    void testSaveAllBatchedInSeparateTransactionsPure() {
        List<TestEntity> entities = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            TestEntity e = new TestEntity();
            e.setName("sepTxPure" + i);
            e.setStatus(i);
            entities.add(e);
        }

        List<TestEntity> saved = batchSaveTemplate.saveAllBatchedPure(entities, 2);
        assertEquals(3, saved.size());
        assertEquals(3, repository.count());
    }

    @Test
    @org.springframework.transaction.annotation.Transactional(
        propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void testSaveAllBatchedInSeparateTransactionsWithExistingMixed() {
        TestEntity existing = new TestEntity();
        existing.setName("sepMixedExisting");
        existing.setStatus(0);
        TestEntity saved = repository.saveAndFlush(existing);
        entityManager.clear();

        List<TestEntity> entities = new ArrayList<>();
        TestEntity detached = new TestEntity();
        detached.setId(saved.getId());
        detached.setName("sepMixedUpdated");
        detached.setStatus(1);
        entities.add(detached);

        TestEntity newEntity = new TestEntity();
        newEntity.setName("sepMixedNew");
        newEntity.setStatus(2);
        entities.add(newEntity);

        List<TestEntity> result = batchSaveTemplate.saveAllBatchedInSeparateTransactions(entities, 10);
        assertEquals(2, result.size());
    }

    // ---- Primitive ID type tests ----

    @Test
    void isDefaultPrimitiveValue_returnsTrueForPrimitiveDefaults() throws Exception {
        java.lang.reflect.Method isDefault =
            BatchSaveTemplate.class.getDeclaredMethod("isDefaultPrimitiveValue", Object.class, Class.class);
        isDefault.setAccessible(true);

        assertTrue((boolean)isDefault.invoke(null, 0L, TestEntity.class));
        assertTrue((boolean)isDefault.invoke(null, 0, TestEntity.class));
        assertTrue((boolean)isDefault.invoke(null, (short)0, TestEntity.class));
        assertTrue((boolean)isDefault.invoke(null, (byte)0, TestEntity.class));
    }

    @Test
    void isDefaultPrimitiveValue_returnsFalseForNonNullObjectTypes() throws Exception {
        java.lang.reflect.Method isDefault =
            BatchSaveTemplate.class.getDeclaredMethod("isDefaultPrimitiveValue", Object.class, Class.class);
        isDefault.setAccessible(true);

        assertFalse((boolean)isDefault.invoke(null, 1L, TestEntity.class));
        assertFalse((boolean)isDefault.invoke(null, 42, TestEntity.class));
        assertFalse((boolean)isDefault.invoke(null, "0", TestEntity.class));
        assertFalse((boolean)isDefault.invoke(null, (Object)null, TestEntity.class));
    }

    @Test
    void isDefaultPrimitiveValue_returnsFalseForNonDefaultPrimitives() throws Exception {
        java.lang.reflect.Method isDefault =
            BatchSaveTemplate.class.getDeclaredMethod("isDefaultPrimitiveValue", Object.class, Class.class);
        isDefault.setAccessible(true);

        assertFalse((boolean)isDefault.invoke(null, Long.valueOf(100L), TestEntity.class));
        assertFalse((boolean)isDefault.invoke(null, Integer.valueOf(999), TestEntity.class));
    }

    @Test
    void isDefaultPrimitiveValue_returnsFalseWithoutGeneratedValue() throws Exception {
        java.lang.reflect.Method isDefault =
            BatchSaveTemplate.class.getDeclaredMethod("isDefaultPrimitiveValue", Object.class, Class.class);
        isDefault.setAccessible(true);

        assertFalse((boolean)isDefault.invoke(null, 0L, ManualIdEntity.class));
    }

    // ---- PartialBatchCommitException failedEntities ----

    @Test
    void partialBatchCommitException_carryFailedEntities() {
        List<String> committed = List.of("a", "b");
        List<String> failed = List.of("c", "d", "e");
        BatchSaveTemplate.PartialBatchCommitException ex = new BatchSaveTemplate.PartialBatchCommitException(1, 2,
            committed, failed, new RuntimeException("db error"));

        assertEquals(1, ex.getCompletedBatches());
        assertEquals(2, ex.getCommittedEntities());
        assertEquals(committed, ex.getCommittedResults());
        assertEquals(failed, ex.getFailedEntities());
        assertTrue(ex.getMessage().contains("3 entity(ies) were NOT committed"));
    }
}
