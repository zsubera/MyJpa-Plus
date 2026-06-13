package com.zsubera.jpa.template;

import static org.junit.jupiter.api.Assertions.*;

import com.zsubera.jpa.spec.TestApplication;
import com.zsubera.jpa.spec.TestEntity;
import com.zsubera.jpa.spec.TestEntityRepository;
import com.zsubera.jpa.update.DeleteSpec;
import com.zsubera.jpa.update.MergeSpec;
import com.zsubera.jpa.update.UpdateSpec;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ContextConfiguration;

@DataJpaTest
@org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase(
    replace = org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = {TestApplication.class, BulkOperationTemplateTest.TestConfig.class})
class BulkOperationTemplateTest {

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        repository.flush();
    }

    @TestConfiguration
    static class TestConfig {

        @Bean
        public MyJpaTemplate myJpaTemplate() {
            return new MyJpaTemplate();
        }

        @Bean
        public BulkOperationTemplate bulkOperationTemplate(jakarta.persistence.EntityManager entityManager,
            jakarta.persistence.EntityManagerFactory entityManagerFactory, ApplicationContext applicationContext) {
            return new BulkOperationTemplate(entityManager, 10000, entityManagerFactory, applicationContext);
        }
    }

    @Autowired
    private BulkOperationTemplate bulkOperationTemplate;

    @Autowired
    private MyJpaTemplate template;

    @Autowired
    private TestEntityRepository repository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private ApplicationContext applicationContext;

    @AfterEach
    void clearEntityManager() {
        entityManager.clear();
    }

    // ---- execute(UpdateSpec) ----

    @Test
    void testExecuteUpdate() {
        for (int i = 0; i < 3; i++) {
            TestEntity e = new TestEntity();
            e.setName("bulkUpd" + i);
            e.setStatus(0);
            repository.save(e);
        }
        repository.flush();

        UpdateSpec<TestEntity> spec =
            template.update(TestEntity.class).set(TestEntity::getStatus, 1).eq(TestEntity::getStatus, 0);
        int count = bulkOperationTemplate.execute(spec);
        assertEquals(3, count);
    }

    @Test
    void testExecuteUpdateNull() {
        assertThrows(IllegalArgumentException.class, () -> bulkOperationTemplate.execute((UpdateSpec<TestEntity>)null));
    }

    // ---- execute(DeleteSpec) ----

    @Test
    void testExecuteDelete() {
        for (int i = 0; i < 3; i++) {
            TestEntity e = new TestEntity();
            e.setName("bulkDel" + i);
            e.setStatus(0);
            repository.save(e);
        }
        repository.flush();

        DeleteSpec<TestEntity> spec = template.delete(TestEntity.class).eq(TestEntity::getStatus, 0);
        int count = bulkOperationTemplate.execute(spec);
        assertEquals(3, count);
    }

    @Test
    void testExecuteDeleteNull() {
        assertThrows(IllegalArgumentException.class, () -> bulkOperationTemplate.execute((DeleteSpec<TestEntity>)null));
    }

    // ---- execute(MergeSpec) ----

    @Test
    void testExecuteMerge() {
        TestEntity existing = new TestEntity();
        existing.setName("mergeTarget");
        existing.setStatus(0);
        repository.saveAndFlush(existing);

        TestEntity toMerge = new TestEntity();
        toMerge.setName("mergeTarget");
        toMerge.setStatus(1);

        MergeSpec<TestEntity> spec =
            new MergeSpec<>(TestEntity.class).onConflict(TestEntity::getName).withEntity(toMerge);
        int count = bulkOperationTemplate.execute(spec);
        assertTrue(count >= 0);
    }

    @Test
    void testExecuteMergeNull() {
        assertThrows(IllegalArgumentException.class, () -> bulkOperationTemplate.execute((MergeSpec<TestEntity>)null));
    }

    // ---- executeWithMaxRows ----

    @Test
    void testExecuteWithMaxRowsUpdate() {
        for (int i = 0; i < 5; i++) {
            TestEntity e = new TestEntity();
            e.setName("maxRowsUpd" + i);
            e.setStatus(0);
            repository.save(e);
        }
        repository.flush();

        UpdateSpec<TestEntity> spec =
            template.update(TestEntity.class).set(TestEntity::getStatus, 1).eq(TestEntity::getStatus, 0);
        int count = bulkOperationTemplate.executeWithMaxRows(spec, 3);
        assertEquals(3, count);
    }

    @Test
    void testExecuteWithMaxRowsDelete() {
        for (int i = 0; i < 5; i++) {
            TestEntity e = new TestEntity();
            e.setName("maxRowsDel" + i);
            e.setStatus(0);
            repository.save(e);
        }
        repository.flush();

        DeleteSpec<TestEntity> spec = template.delete(TestEntity.class).eq(TestEntity::getStatus, 0);
        int count = bulkOperationTemplate.executeWithMaxRows(spec, 2);
        assertEquals(2, count);
    }

    @Test
    void testExecuteWithMaxRowsUpdateUsesGlobalConfig() {
        for (int i = 0; i < 3; i++) {
            TestEntity e = new TestEntity();
            e.setName("globalMax" + i);
            e.setStatus(0);
            repository.save(e);
        }
        repository.flush();

        UpdateSpec<TestEntity> spec =
            template.update(TestEntity.class).set(TestEntity::getStatus, 1).eq(TestEntity::getStatus, 0);
        // maxRows = -1 means use global config (10000)
        int count = bulkOperationTemplate.executeWithMaxRows(spec, -1);
        assertEquals(3, count);
    }

    @Test
    void testExecuteWithMaxRowsUpdateNull() {
        assertThrows(IllegalArgumentException.class,
            () -> bulkOperationTemplate.executeWithMaxRows((UpdateSpec<TestEntity>)null, 10));
    }

    @Test
    void testExecuteWithMaxRowsDeleteNull() {
        assertThrows(IllegalArgumentException.class,
            () -> bulkOperationTemplate.executeWithMaxRows((DeleteSpec<TestEntity>)null, 10));
    }

    @Test
    void testExecuteWithMaxRowsInvalidValue() {
        UpdateSpec<TestEntity> spec = template.update(TestEntity.class).set(TestEntity::getStatus, 1);
        assertThrows(IllegalArgumentException.class, () -> bulkOperationTemplate.executeWithMaxRows(spec, 0));
        assertThrows(IllegalArgumentException.class, () -> bulkOperationTemplate.executeWithMaxRows(spec, -2));
    }

    // ---- setMaxBulkOperationRows ----

    @Test
    void testSetMaxBulkOperationRows() {
        bulkOperationTemplate.setMaxBulkOperationRows(500);
        // After setting, -1 should use new global config (500)
        for (int i = 0; i < 3; i++) {
            TestEntity e = new TestEntity();
            e.setName("newLimit" + i);
            e.setStatus(0);
            repository.save(e);
        }
        repository.flush();

        UpdateSpec<TestEntity> spec =
            template.update(TestEntity.class).set(TestEntity::getStatus, 1).eq(TestEntity::getStatus, 0);
        int count = bulkOperationTemplate.executeWithMaxRows(spec, -1);
        assertEquals(3, count);
    }

    // ---- executeBatch(UpdateSpec) ----

    @Test
    void testExecuteBatchUpdate() {
        for (int i = 0; i < 5; i++) {
            TestEntity e = new TestEntity();
            e.setName("batchUpd" + i);
            e.setStatus(0);
            repository.save(e);
        }
        repository.flush();

        UpdateSpec<TestEntity> spec =
            template.update(TestEntity.class).set(TestEntity::getStatus, 1).eq(TestEntity::getStatus, 0);
        int count = bulkOperationTemplate.executeBatch(spec, 2);
        assertEquals(5, count);
    }

    @Test
    void testExecuteBatchDelete() {
        for (int i = 0; i < 5; i++) {
            TestEntity e = new TestEntity();
            e.setName("batchDel" + i);
            e.setStatus(0);
            repository.save(e);
        }
        repository.flush();

        DeleteSpec<TestEntity> spec = template.delete(TestEntity.class).eq(TestEntity::getStatus, 0);
        int count = bulkOperationTemplate.executeBatch(spec, 2);
        assertEquals(5, count);
    }

    @Test
    void testExecuteBatchUpdateNull() {
        assertThrows(IllegalArgumentException.class,
            () -> bulkOperationTemplate.executeBatch((UpdateSpec<TestEntity>)null, 10));
    }

    @Test
    void testExecuteBatchDeleteNull() {
        assertThrows(IllegalArgumentException.class,
            () -> bulkOperationTemplate.executeBatch((DeleteSpec<TestEntity>)null, 10));
    }

    @Test
    void testExecuteBatchInvalidBatchSize() {
        UpdateSpec<TestEntity> spec = template.update(TestEntity.class).set(TestEntity::getStatus, 1);
        assertThrows(IllegalArgumentException.class, () -> bulkOperationTemplate.executeBatch(spec, 0));
        assertThrows(IllegalArgumentException.class, () -> bulkOperationTemplate.executeBatch(spec, -1));
    }

    // ---- executeBatchInSeparateTransactions ----

    // 使用 TransactionTemplate 在独立事务中插入数据，确保数据对后续 REQUIRES_NEW 事务可见。
    @Test
    @org.springframework.transaction.annotation.Transactional(
        propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void testExecuteBatchInSeparateTransactionsUpdate() {
        insertTestData("sepTxBatchUpd", 5);

        UpdateSpec<TestEntity> spec =
            template.update(TestEntity.class).set(TestEntity::getStatus, 1).eq(TestEntity::getStatus, 0);
        int count = bulkOperationTemplate.executeBatchInSeparateTransactions(spec, 2);
        assertEquals(5, count);
    }

    @Test
    @org.springframework.transaction.annotation.Transactional(
        propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void testExecuteBatchInSeparateTransactionsDelete() {
        insertTestData("sepTxBatchDel", 5);

        DeleteSpec<TestEntity> spec = template.delete(TestEntity.class).eq(TestEntity::getStatus, 0);
        int count = bulkOperationTemplate.executeBatchInSeparateTransactions(spec, 2);
        assertEquals(5, count);
    }

    @Test
    void testExecuteBatchInSeparateTransactionsUpdateNull() {
        assertThrows(IllegalArgumentException.class,
            () -> bulkOperationTemplate.executeBatchInSeparateTransactions((UpdateSpec<TestEntity>)null, 10));
    }

    @Test
    void testExecuteBatchInSeparateTransactionsDeleteNull() {
        assertThrows(IllegalArgumentException.class,
            () -> bulkOperationTemplate.executeBatchInSeparateTransactions((DeleteSpec<TestEntity>)null, 10));
    }

    @Test
    void testExecuteBatchInSeparateTransactionsInvalidBatchSize() {
        UpdateSpec<TestEntity> spec = template.update(TestEntity.class).set(TestEntity::getStatus, 1);
        assertThrows(IllegalArgumentException.class,
            () -> bulkOperationTemplate.executeBatchInSeparateTransactions(spec, 0));
        assertThrows(IllegalArgumentException.class,
            () -> bulkOperationTemplate.executeBatchInSeparateTransactions(spec, -1));
    }

    // ---- executeBatchInSeparateTransactions with BatchResult ----

    @Test
    @org.springframework.transaction.annotation.Transactional(
        propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void testExecuteBatchInSeparateTransactionsWithResultUpdate() {
        insertTestData("resultSepTx", 5);

        UpdateSpec<TestEntity> spec =
            template.update(TestEntity.class).set(TestEntity::getStatus, 1).eq(TestEntity::getStatus, 0);
        BulkOperationTemplate.BatchResult result = bulkOperationTemplate.executeBatchInSeparateTransactions(spec, 2,
            BulkOperationTemplate.BatchFailureStrategy.CONTINUE);
        assertTrue(result.success());
        assertEquals(5, result.totalRows());
        assertEquals(-1, result.failedBatchIndex());
        assertNull(result.failureCause());
    }

    @Test
    @org.springframework.transaction.annotation.Transactional(
        propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void testExecuteBatchInSeparateTransactionsWithResultDelete() {
        insertTestData("resultSepTxDel", 5);

        DeleteSpec<TestEntity> spec = template.delete(TestEntity.class).eq(TestEntity::getStatus, 0);
        BulkOperationTemplate.BatchResult result = bulkOperationTemplate.executeBatchInSeparateTransactions(spec, 2,
            BulkOperationTemplate.BatchFailureStrategy.CONTINUE);
        assertTrue(result.success());
        assertEquals(5, result.totalRows());
    }

    @Test
    void testExecuteBatchInSeparateTransactionsWithResultNullSpec() {
        assertThrows(IllegalArgumentException.class,
            () -> bulkOperationTemplate.executeBatchInSeparateTransactions((UpdateSpec<TestEntity>)null, 10,
                BulkOperationTemplate.BatchFailureStrategy.CONTINUE));
    }

    @Test
    void testExecuteBatchInSeparateTransactionsWithResultNullStrategy() {
        UpdateSpec<TestEntity> spec = template.update(TestEntity.class).set(TestEntity::getStatus, 1);
        assertThrows(IllegalArgumentException.class,
            () -> bulkOperationTemplate.executeBatchInSeparateTransactions(spec, 10, null));
    }

    @Test
    void testExecuteBatchInSeparateTransactionsWithResultInvalidBatchSize() {
        UpdateSpec<TestEntity> spec = template.update(TestEntity.class).set(TestEntity::getStatus, 1);
        assertThrows(IllegalArgumentException.class, () -> bulkOperationTemplate
            .executeBatchInSeparateTransactions(spec, 0, BulkOperationTemplate.BatchFailureStrategy.CONTINUE));
        assertThrows(IllegalArgumentException.class, () -> bulkOperationTemplate
            .executeBatchInSeparateTransactions(spec, -1, BulkOperationTemplate.BatchFailureStrategy.ABORT));
    }

    // ---- BatchFailureStrategy enum ----

    @Test
    void testBatchFailureStrategyValues() {
        BulkOperationTemplate.BatchFailureStrategy[] strategies = BulkOperationTemplate.BatchFailureStrategy.values();
        assertEquals(2, strategies.length);
        assertNotNull(BulkOperationTemplate.BatchFailureStrategy.CONTINUE);
        assertNotNull(BulkOperationTemplate.BatchFailureStrategy.ABORT);
    }

    // ---- BatchResult record ----

    @Test
    void testBatchResultRecord() {
        BulkOperationTemplate.BatchResult result = new BulkOperationTemplate.BatchResult(10, 5, true, -1, null);
        assertEquals(10, result.totalRows());
        assertEquals(5, result.batchCount());
        assertTrue(result.success());
        assertEquals(-1, result.failedBatchIndex());
        assertNull(result.failureCause());
    }

    /**

     * @DataJpaTest
    @org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase(replace = org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE) 的 @Transactional 在测试方法结束前不会提交，
     * 而 REQUIRES_NEW 会挂起外层事务创建新 EM，新 EM 无法看到外层未提交的数据。
     */
    private void insertTestData(String namePrefix, int count) {
        org.springframework.transaction.PlatformTransactionManager txManager =
            applicationContext.getBean(org.springframework.transaction.PlatformTransactionManager.class);
        org.springframework.transaction.support.TransactionTemplate txTemplate =
            new org.springframework.transaction.support.TransactionTemplate(txManager);

        txTemplate
            .setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        txTemplate.executeWithoutResult(status -> {
            for (int i = 0; i < count; i++) {
                TestEntity e = new TestEntity();
                e.setName(namePrefix + i);
                e.setStatus(0);
                entityManager.persist(e);
            }
            entityManager.flush();
        });
    }
}
