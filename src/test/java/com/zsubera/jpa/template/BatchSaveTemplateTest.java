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
        public BatchSaveTemplate batchSaveTemplate(jakarta.persistence.EntityManager entityManager,
            org.springframework.context.ApplicationContext applicationContext) {
            TransactionHelper txHelper = new TransactionHelper(entityManager, null, applicationContext);
            return new BatchSaveTemplate(entityManager, txHelper);
        }
    }

    @Autowired
    private BatchSaveTemplate batchSaveTemplate;

    @Autowired
    private TestEntityRepository repository;

    // [FIX] 清理测试数据，避免测试间数据泄漏导致断言失败
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
        assertThrows(NullPointerException.class,
            () -> batchSaveTemplate.saveAllBatchedInSeparateTransactions(null, 10));
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
}
