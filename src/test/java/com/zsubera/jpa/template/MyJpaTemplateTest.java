package com.zsubera.jpa.template;

import static org.junit.jupiter.api.Assertions.*;

import com.zsubera.jpa.spec.QuerySpec;
import com.zsubera.jpa.spec.TestApplication;
import com.zsubera.jpa.spec.TestEntity;
import com.zsubera.jpa.spec.TestEntityRepository;
import com.zsubera.jpa.update.DeleteSpec;
import com.zsubera.jpa.update.UpdateSpec;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.context.ContextConfiguration;

@DataJpaTest
@org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase(
    replace = org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = {TestApplication.class, MyJpaTemplateTest.TestConfig.class})
class MyJpaTemplateTest {

    @TestConfiguration
    static class TestConfig {
        @Bean
        public MyJpaTemplate myJpaTemplate() {
            return new MyJpaTemplate();
        }
    }

    @Autowired
    private MyJpaTemplate template;

    @Autowired
    private TestEntityRepository repository;

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private jakarta.persistence.EntityManager entityManager;

    @BeforeEach
    void cleanUp() {
        repository.deleteAll();
        repository.flush();
    }

    // ---- 工厂方法测试 ----

    @Test
    void testUpdateFactory() {
        UpdateSpec<TestEntity> spec = template.update(TestEntity.class);
        assertNotNull(spec);
    }

    @Test
    void testDeleteFactory() {
        DeleteSpec<TestEntity> spec = template.delete(TestEntity.class);
        assertNotNull(spec);
    }

    // ---- findAll 测试 ----

    @Test
    void testFindAllWithQuerySpec() {
        TestEntity e = new TestEntity();
        e.setName("hello");
        e.setStatus(1);
        repository.save(e);
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.eq(TestEntity::getName, "hello");
        List<TestEntity> result = template.findAll(TestEntity.class, qs);
        assertEquals(1, result.size());
    }

    @Test
    void testFindAllWithMaxResults() {
        for (int i = 0; i < 10; i++) {
            TestEntity e = new TestEntity();
            e.setName("limit" + i);
            e.setStatus(i);
            repository.save(e);
        }

        QuerySpec<TestEntity> qs = new QuerySpec<>();
        List<TestEntity> result = template.findAll(TestEntity.class, qs, 3);
        assertEquals(3, result.size());
    }

    @Test
    void testFindAllWithEntityGraph() {
        TestEntity e = new TestEntity();
        e.setName("graph");
        e.setStatus(1);
        repository.save(e);

        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.eq(TestEntity::getName, "graph");
        List<TestEntity> result =
            template.findAll(TestEntity.class, qs, com.zsubera.jpa.util.EntityGraphHelper.forEntity(TestEntity.class));
        assertEquals(1, result.size());
    }

    // ---- find 测试 ----

    @Test
    void testFindWithSpecification() {
        TestEntity e = new TestEntity();
        e.setName("world");
        e.setStatus(2);
        repository.save(e);

        Specification<TestEntity> spec = (root, query, cb) -> cb.equal(root.get("name"), "world");
        List<TestEntity> result = template.find(TestEntity.class, spec);
        assertEquals(1, result.size());
    }

    @Test
    void testFindWithMaxResults() {
        for (int i = 0; i < 5; i++) {
            TestEntity e = new TestEntity();
            e.setName("find" + i);
            e.setStatus(i);
            repository.save(e);
        }

        Specification<TestEntity> spec = (root, query, cb) -> cb.conjunction();
        List<TestEntity> result = template.find(TestEntity.class, spec, 2);
        assertEquals(2, result.size());
    }

    // ---- findAllStream 测试 ----

    @Test
    void testFindAllStream() {
        for (int i = 0; i < 5; i++) {
            TestEntity e = new TestEntity();
            e.setName("stream" + i);
            e.setStatus(i);
            repository.save(e);
        }

        QuerySpec<TestEntity> qs = new QuerySpec<>();
        final long[] count = {0};
        template.findAllStream(TestEntity.class, qs, stream -> {
            count[0] = stream.count();
        });
        assertEquals(5, count[0]);
    }

    @Test
    void testFindAllStreamWithCondition() {
        for (int i = 0; i < 5; i++) {
            TestEntity e = new TestEntity();
            e.setName("streamCond" + i);
            e.setStatus(i);
            repository.save(e);
        }

        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.ge(TestEntity::getStatus, 3);
        final long[] count = {0};
        template.findAllStream(TestEntity.class, qs, stream -> {
            count[0] = stream.count();
        });
        assertEquals(2, count[0]);
    }

    @Test
    void testFindAllStreamWithEntityGraph() {
        TestEntity e = new TestEntity();
        e.setName("streamGraph");
        e.setStatus(1);
        repository.save(e);

        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.eq(TestEntity::getName, "streamGraph");
        final long[] count = {0};
        template.findAllStream(TestEntity.class, qs, stream -> {
            count[0] = stream.count();
        });
        assertEquals(1, count[0]);
    }

    // ---- 分页测试 ----

    @Test
    void testFindAllWithPagination() {
        for (int i = 0; i < 5; i++) {
            TestEntity e = new TestEntity();
            e.setName("item" + i);
            e.setStatus(i);
            repository.save(e);
        }

        QuerySpec<TestEntity> qs = new QuerySpec<>();
        Page<TestEntity> page = template.findAll(TestEntity.class, qs, PageRequest.of(0, 2, Sort.by("name")));
        assertEquals(5, page.getTotalElements());
        assertEquals(2, page.getContent().size());
    }

    @Test
    void testFindAllWithUnpaged() {
        TestEntity e = new TestEntity();
        e.setName("single");
        e.setStatus(1);
        repository.save(e);

        QuerySpec<TestEntity> qs = new QuerySpec<>();
        Page<TestEntity> page =
            template.findAll(TestEntity.class, qs, org.springframework.data.domain.Pageable.unpaged());
        assertEquals(1, page.getTotalElements());
    }

    @Test
    void testFindPageWithPagination() {
        for (int i = 0; i < 3; i++) {
            TestEntity e = new TestEntity();
            e.setName("x" + i);
            e.setStatus(1);
            repository.save(e);
        }

        Specification<TestEntity> spec = (root, query, cb) -> cb.equal(root.get("status"), 1);
        Page<TestEntity> page = template.findPage(TestEntity.class, spec, PageRequest.of(0, 10));
        assertEquals(3, page.getTotalElements());
    }

    @Test
    void testFindPageUnpaged() {
        TestEntity e = new TestEntity();
        e.setName("u");
        e.setStatus(1);
        repository.save(e);

        Specification<TestEntity> spec = (root, query, cb) -> cb.conjunction();
        Page<TestEntity> page =
            template.findPage(TestEntity.class, spec, org.springframework.data.domain.Pageable.unpaged());
        assertEquals(1, page.getTotalElements());
        assertEquals(1, page.getContent().size());
    }

    // ---- execute 测试 ----

    @Test
    void testExecuteUpdateSpec() {
        TestEntity e = new TestEntity();
        e.setName("old");
        e.setStatus(1);
        repository.save(e);

        UpdateSpec<TestEntity> spec =
            template.update(TestEntity.class).set(TestEntity::getName, "new").eq(TestEntity::getName, "old");
        int count = template.execute(spec);
        assertEquals(1, count);
    }

    @Test
    void testExecuteDeleteSpec() {
        TestEntity e = new TestEntity();
        e.setName("del");
        e.setStatus(1);
        repository.save(e);

        DeleteSpec<TestEntity> spec = template.delete(TestEntity.class).eq(TestEntity::getName, "del");
        int count = template.execute(spec);
        assertEquals(1, count);
    }

    // ---- executeBatch 测试 ----

    @Test
    void testExecuteBatchUpdate() {
        // 创建5条数据
        for (int i = 0; i < 5; i++) {
            TestEntity e = new TestEntity();
            e.setName("batch" + i);
            e.setStatus(0);
            repository.save(e);
        }
        repository.flush();

        // 更新 status 从 0 到 1，每批次2条
        UpdateSpec<TestEntity> spec =
            template.update(TestEntity.class).set(TestEntity::getStatus, 1).eq(TestEntity::getStatus, 0);
        int count = template.executeBatch(spec, 2);
        assertEquals(5, count);

        // 验证所有记录都已更新
        List<TestEntity> updated = repository.findAll();
        assertTrue(updated.stream().allMatch(e -> e.getStatus() == 1));
    }

    @Test
    void testExecuteBatchDelete() {
        // 创建5条数据
        for (int i = 0; i < 5; i++) {
            TestEntity e = new TestEntity();
            e.setName("delBatch" + i);
            e.setStatus(0);
            repository.save(e);
        }
        repository.flush();

        // 删除 status = 0 的记录，每批次2条
        DeleteSpec<TestEntity> spec = template.delete(TestEntity.class).eq(TestEntity::getStatus, 0);
        int count = template.executeBatch(spec, 2);
        assertEquals(5, count);

        // 验证所有记录都已删除
        List<TestEntity> remaining = repository.findAll();
        assertTrue(remaining.stream().noneMatch(e -> e.getName().startsWith("delBatch")));
    }

    // ---- executeLimited 测试（通过 executeBatch 间接测试） ----

    @Test
    void testExecuteLimitedUpdate() {
        for (int i = 0; i < 5; i++) {
            TestEntity e = new TestEntity();
            e.setName("limitUpd" + i);
            e.setStatus(0);
            repository.save(e);
        }
        repository.flush();

        UpdateSpec<TestEntity> spec =
            template.update(TestEntity.class).set(TestEntity::getStatus, 1).eq(TestEntity::getStatus, 0);

        // 使用 executeBatch 间接测试 executeLimited
        int count = template.executeBatch(spec, 3);
        assertEquals(5, count);
    }

    // ---- 配置参数测试 ----

    @Test
    void testConstructorWithCustomConfig() {
        MyJpaTemplate custom = new MyJpaTemplate(5000, 50000);
        assertNotNull(custom);
    }

    @Test
    void testSetMaxResultsWithInvalidValue() {
        MyJpaTemplate custom = new MyJpaTemplate();
        assertThrows(IllegalArgumentException.class, () -> custom.setMaxResults(0));
        assertThrows(IllegalArgumentException.class, () -> custom.setMaxResults(-2));
        // -1 is valid (disables limit)
        assertDoesNotThrow(() -> custom.setMaxResults(-1));
    }

    @Test
    void testSetDeepPaginationOffsetThresholdWithInvalidValue() {
        MyJpaTemplate custom = new MyJpaTemplate();
        assertThrows(IllegalArgumentException.class, () -> custom.setDeepPaginationOffsetThreshold(0));
        assertThrows(IllegalArgumentException.class, () -> custom.setDeepPaginationOffsetThreshold(-100));
    }

    @Test
    void testFindAllWithEntityGraphAndMaxResults() {
        for (int i = 0; i < 5; i++) {
            TestEntity e = new TestEntity();
            e.setName("graphLimit" + i);
            e.setStatus(i);
            repository.save(e);
        }

        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.ge(TestEntity::getStatus, 0);
        List<TestEntity> result = template.findAll(TestEntity.class, qs,
            com.zsubera.jpa.util.EntityGraphHelper.forEntity(TestEntity.class), 3);
        assertEquals(3, result.size());
    }

    @Test
    void testDefaultConstructorValues() {
        MyJpaTemplate defaultTemplate = new MyJpaTemplate();
        assertNotNull(defaultTemplate);
    }

    @Test
    void testSetMaxResultsValidValue() {
        MyJpaTemplate custom = new MyJpaTemplate();
        custom.setMaxResults(500);
        assertNotNull(custom);
    }

    @Test
    void testSetDeepPaginationOffsetThresholdValidValue() {
        MyJpaTemplate custom = new MyJpaTemplate();
        custom.setDeepPaginationOffsetThreshold(5000);
        assertNotNull(custom);
    }

    @Test
    void testFindPageWithSortedPageable() {
        for (int i = 0; i < 5; i++) {
            TestEntity e = new TestEntity();
            e.setName("sorted" + i);
            e.setStatus(i);
            repository.save(e);
        }

        Specification<TestEntity> spec = (root, query, cb) -> cb.conjunction();
        Page<TestEntity> page = template.findPage(TestEntity.class, spec, PageRequest.of(0, 10, Sort.by("name")));
        assertEquals(5, page.getTotalElements());
    }

    @Test
    void testFindPageWithDeepPaginationOffset() {
        for (int i = 0; i < 5; i++) {
            TestEntity e = new TestEntity();
            e.setName("deep" + i);
            e.setStatus(i);
            repository.save(e);
        }

        Specification<TestEntity> spec = (root, query, cb) -> cb.conjunction();
        template.setDeepPaginationOffsetThreshold(2);
        Page<TestEntity> page = template.findPage(TestEntity.class, spec, PageRequest.of(0, 10));
        assertEquals(5, page.getTotalElements());
    }

    // ---- findById 测试 ----

    @Test
    void testFindById() {
        TestEntity e = new TestEntity();
        e.setName("findById");
        e.setStatus(1);
        e = repository.save(e);

        var result = template.findById(TestEntity.class, e.getId());
        assertTrue(result.isPresent());
        assertEquals("findById", result.get().getName());
    }

    @Test
    void testFindByIdNotFound() {
        var result = template.findById(TestEntity.class, 99999L);
        assertTrue(result.isEmpty());
    }

    @Test
    void testFindByIdWithNullClass() {
        assertThrows(IllegalArgumentException.class, () -> template.findById(null, 1L));
    }

    @Test
    void testFindByIdWithNullId() {
        assertThrows(IllegalArgumentException.class, () -> template.findById(TestEntity.class, null));
    }

    // ---- findOne 测试 ----

    @Test
    void testFindOne() {
        TestEntity e = new TestEntity();
        e.setName("findOne");
        e.setStatus(1);
        repository.save(e);

        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.eq(TestEntity::getName, "findOne");
        var result = template.findOne(TestEntity.class, qs);
        assertTrue(result.isPresent());
        assertEquals(1, result.get().getStatus());
    }

    @Test
    void testFindOneNotFound() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.eq(TestEntity::getName, "nonexistent");
        var result = template.findOne(TestEntity.class, qs);
        assertTrue(result.isEmpty());
    }

    @Test
    void testFindOneWithNullClass() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        assertThrows(IllegalArgumentException.class, () -> template.findOne(null, qs));
    }

    @Test
    void testFindOneWithNullSpec() {
        assertThrows(IllegalArgumentException.class,
            () -> template.findOne(TestEntity.class, (QuerySpec<TestEntity>)null));
    }

    // ---- count 测试 ----

    @Test
    void testCountWithQuerySpec() {
        for (int i = 0; i < 3; i++) {
            TestEntity e = new TestEntity();
            e.setName("count" + i);
            e.setStatus(10);
            repository.save(e);
        }

        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.eq(TestEntity::getStatus, 10);
        long count = template.count(TestEntity.class, qs);
        assertEquals(3, count);
    }

    @Test
    void testCountWithSpecification() {
        for (int i = 0; i < 2; i++) {
            TestEntity e = new TestEntity();
            e.setName("countSpec" + i);
            e.setStatus(20);
            repository.save(e);
        }

        Specification<TestEntity> spec = (root, query, cb) -> cb.equal(root.get("status"), 20);
        long count = template.count(TestEntity.class, spec);
        assertEquals(2, count);
    }

    @Test
    void testCountWithNullClass() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        assertThrows(IllegalArgumentException.class, () -> template.count(null, qs));
    }

    @Test
    void testCountWithNullSpec() {
        assertThrows(IllegalArgumentException.class,
            () -> template.count(TestEntity.class, (QuerySpec<TestEntity>)null));
    }

    // ---- executeWithMaxRows 测试 ----

    @Test
    void testExecuteWithMaxRowsUpdate() {
        for (int i = 0; i < 5; i++) {
            TestEntity e = new TestEntity();
            e.setName("maxRows" + i);
            e.setStatus(0);
            repository.save(e);
        }
        repository.flush();

        UpdateSpec<TestEntity> spec =
            template.update(TestEntity.class).set(TestEntity::getStatus, 1).eq(TestEntity::getStatus, 0);
        // 使用 -1 表示使用全局配置
        int count = template.executeWithMaxRows(spec, -1);
        // 全局配置默认 maxBulkOperationRows=10000，所以应该全部更新
        assertTrue(count > 0);
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
        // 使用 -1 表示使用全局配置
        int count = template.executeWithMaxRows(spec, -1);
        assertTrue(count > 0);
    }

    @Test
    void testExecuteWithMaxRowsInvalidValue() {
        UpdateSpec<TestEntity> spec = template.update(TestEntity.class).set(TestEntity::getStatus, 1);
        assertThrows(IllegalArgumentException.class, () -> template.executeWithMaxRows(spec, 0));
        assertThrows(IllegalArgumentException.class, () -> template.executeWithMaxRows(spec, -2));
    }

    // ---- executeBatchInSeparateTransactions 测试 ----

    @Test
    @org.springframework.transaction.annotation.Transactional(
        propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void testExecuteBatchInSeparateTransactionsUpdate() {
        for (int i = 0; i < 5; i++) {
            TestEntity e = new TestEntity();
            e.setName("sepTx" + i);
            e.setStatus(0);
            repository.save(e);
        }
        repository.flush();
        entityManager.clear();

        UpdateSpec<TestEntity> spec =
            template.update(TestEntity.class).set(TestEntity::getStatus, 1).eq(TestEntity::getStatus, 0);
        int count = template.executeBatchInSeparateTransactions(spec, 2);
        assertTrue(count > 0, "Should update at least one row");
    }

    @Test
    @org.springframework.transaction.annotation.Transactional(
        propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void testExecuteBatchInSeparateTransactionsDelete() {
        for (int i = 0; i < 5; i++) {
            TestEntity e = new TestEntity();
            e.setName("sepTxDel" + i);
            e.setStatus(0);
            repository.save(e);
        }
        repository.flush();
        entityManager.clear();

        DeleteSpec<TestEntity> spec = template.delete(TestEntity.class).eq(TestEntity::getStatus, 0);
        int count = template.executeBatchInSeparateTransactions(spec, 2);
        assertEquals(5, count);

        // 验证所有记录都已删除
        List<TestEntity> remaining =
            repository.findAll().stream().filter(e -> e.getName().startsWith("sepTxDel")).toList();
        assertEquals(0, remaining.size());
    }

    @Test
    void testExecuteBatchInSeparateTransactionsInvalidArgs() {
        UpdateSpec<TestEntity> spec = template.update(TestEntity.class).set(TestEntity::getStatus, 1);
        assertThrows(IllegalArgumentException.class, () -> template.executeBatchInSeparateTransactions(spec, 0));
        assertThrows(IllegalArgumentException.class, () -> template.executeBatchInSeparateTransactions(spec, -1));
    }

    // ---- saveAllBatched 测试 ----

    @Test
    void testSaveAllBatched() {
        List<TestEntity> entities = new java.util.ArrayList<>();
        for (int i = 0; i < 5; i++) {
            TestEntity e = new TestEntity();
            e.setName("batchSave" + i);
            e.setStatus(i);
            entities.add(e);
        }

        List<TestEntity> saved = template.saveAllBatched(entities, 2);
        assertEquals(5, saved.size());
        assertEquals(5, repository.count());
    }

    @Test
    void testSaveAllBatchedWithNullEntities() {
        assertThrows(IllegalArgumentException.class, () -> template.saveAllBatched(null, 10));
    }

    @Test
    void testSaveAllBatchedWithInvalidBatchSize() {
        List<TestEntity> entities = List.of(new TestEntity());
        assertThrows(IllegalArgumentException.class, () -> template.saveAllBatched(entities, 0));
        assertThrows(IllegalArgumentException.class, () -> template.saveAllBatched(entities, -1));
    }

    // ---- saveAllBatchedInSeparateTransactions 测试 ----

    @Test
    @org.springframework.transaction.annotation.Transactional(
        propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void testSaveAllBatchedInSeparateTransactions() {
        List<TestEntity> entities = new java.util.ArrayList<>();
        for (int i = 0; i < 5; i++) {
            TestEntity e = new TestEntity();
            e.setName("sepTxSave" + i);
            e.setStatus(i);
            entities.add(e);
        }

        List<TestEntity> saved = template.saveAllBatchedInSeparateTransactions(entities, 2);
        assertEquals(5, saved.size());
        assertEquals(5, repository.count());
    }

    @Test
    void testSaveAllBatchedInSeparateTransactionsWithNullEntities() {
        assertThrows(IllegalArgumentException.class, () -> template.saveAllBatchedInSeparateTransactions(null, 10));
    }

    @Test
    void testSaveAllBatchedInSeparateTransactionsWithInvalidBatchSize() {
        List<TestEntity> entities = List.of(new TestEntity());
        assertThrows(IllegalArgumentException.class, () -> template.saveAllBatchedInSeparateTransactions(entities, 0));
        assertThrows(IllegalArgumentException.class, () -> template.saveAllBatchedInSeparateTransactions(entities, -1));
    }

    // ---- 深度分页硬限制测试 ----

    @Test
    void testDeepPaginationHardLimitExceeded() {
        template.setDeepPaginationOffsetLimit(100);
        template.setDeepPaginationOffsetThreshold(50);

        Specification<TestEntity> spec = (root, query, cb) -> cb.conjunction();
        // offset 超过硬限制应抛出异常
        assertThrows(IllegalArgumentException.class,
            () -> template.findPage(TestEntity.class, spec, PageRequest.of(100, 10)));
    }

    @Test
    void testDeepPaginationHardLimitDisabled() {
        template.setDeepPaginationOffsetLimit(-1);
        template.setDeepPaginationOffsetThreshold(2);

        for (int i = 0; i < 3; i++) {
            TestEntity e = new TestEntity();
            e.setName("hardLimit" + i);
            e.setStatus(i);
            repository.save(e);
        }

        Specification<TestEntity> spec = (root, query, cb) -> cb.conjunction();
        // 硬限制禁用时不应抛出异常
        Page<TestEntity> page = template.findPage(TestEntity.class, spec, PageRequest.of(0, 10));
        assertEquals(3, page.getTotalElements());
    }

    // ---- 配置参数高级测试 ----

    @Test
    void testSetDeepPaginationOffsetLimit() {
        MyJpaTemplate custom = new MyJpaTemplate();
        custom.setDeepPaginationOffsetLimit(5000);
        assertNotNull(custom);
    }

    @Test
    void testSetDeepPaginationOffsetLimitInvalid() {
        MyJpaTemplate custom = new MyJpaTemplate();
        assertThrows(IllegalArgumentException.class, () -> custom.setDeepPaginationOffsetLimit(0));
        assertThrows(IllegalArgumentException.class, () -> custom.setDeepPaginationOffsetLimit(-2));
    }

    @Test
    void testSetMaxBulkOperationRows() {
        MyJpaTemplate custom = new MyJpaTemplate();
        custom.setMaxBulkOperationRows(5000);
        assertNotNull(custom);
    }

    @Test
    void testSetMaxBulkOperationRowsInvalid() {
        MyJpaTemplate custom = new MyJpaTemplate();
        assertThrows(IllegalArgumentException.class, () -> custom.setMaxBulkOperationRows(0));
        assertThrows(IllegalArgumentException.class, () -> custom.setMaxBulkOperationRows(-2));
    }

    // ---- null 参数测试 ----

    @Test
    void testFindAllWithNullClass() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        assertThrows(IllegalArgumentException.class, () -> template.findAll(null, qs));
    }

    @Test
    void testFindAllWithNullSpec() {
        assertThrows(IllegalArgumentException.class,
            () -> template.findAll(TestEntity.class, (QuerySpec<TestEntity>)null));
    }

    @Test
    void testFindAllWithPaginationNullClass() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        assertThrows(IllegalArgumentException.class, () -> template.findAll(null, qs, PageRequest.of(0, 10)));
    }

    @Test
    void testFindAllWithPaginationNullPageable() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        assertThrows(IllegalArgumentException.class, () -> template.findAll(TestEntity.class, qs, (Pageable)null));
    }

    @Test
    void testExecuteUpdateSpecNull() {
        assertThrows(IllegalArgumentException.class, () -> template.execute((UpdateSpec<TestEntity>)null));
    }

    @Test
    void testExecuteDeleteSpecNull() {
        assertThrows(IllegalArgumentException.class, () -> template.execute((DeleteSpec<TestEntity>)null));
    }

    @Test
    void testExecuteBatchUpdateNull() {
        assertThrows(IllegalArgumentException.class, () -> template.executeBatch((UpdateSpec<TestEntity>)null, 10));
    }

    @Test
    void testExecuteBatchDeleteNull() {
        assertThrows(IllegalArgumentException.class, () -> template.executeBatch((DeleteSpec<TestEntity>)null, 10));
    }

    @Test
    void testExecuteBatchUpdateInvalidBatchSize() {
        UpdateSpec<TestEntity> spec = template.update(TestEntity.class).set(TestEntity::getStatus, 1);
        assertThrows(IllegalArgumentException.class, () -> template.executeBatch(spec, 0));
    }

    @Test
    void testExecuteBatchDeleteInvalidBatchSize() {
        DeleteSpec<TestEntity> spec = template.delete(TestEntity.class);
        assertThrows(IllegalArgumentException.class, () -> template.executeBatch(spec, -1));
    }

    @Test
    void testFindPageWithNullClass() {
        Specification<TestEntity> spec = (root, query, cb) -> cb.conjunction();
        assertThrows(IllegalArgumentException.class, () -> template.findPage(null, spec, PageRequest.of(0, 10)));
    }

    @Test
    void testFindPageWithNullSpec() {
        assertThrows(IllegalArgumentException.class,
            () -> template.findPage(TestEntity.class, (Specification<TestEntity>)null, PageRequest.of(0, 10)));
    }

    @Test
    void testFindPageWithNullPageable() {
        Specification<TestEntity> spec = (root, query, cb) -> cb.conjunction();
        assertThrows(IllegalArgumentException.class, () -> template.findPage(TestEntity.class, spec, null));
    }

    @Test
    void testFindWithSpecificationNullClass() {
        Specification<TestEntity> spec = (root, query, cb) -> cb.conjunction();
        assertThrows(IllegalArgumentException.class, () -> template.find(null, spec));
    }

    @Test
    void testFindWithSpecificationNullSpec() {
        assertThrows(IllegalArgumentException.class,
            () -> template.find(TestEntity.class, (Specification<TestEntity>)null));
    }

    @Test
    void testFindSlice() {
        for (int i = 0; i < 5; i++) {
            TestEntity e = new TestEntity();
            e.setName("slice" + i);
            e.setStatus(i);
            repository.save(e);
        }
        repository.flush();

        Specification<TestEntity> spec = (root, query, cb) -> cb.conjunction();
        org.springframework.data.domain.Slice<TestEntity> slice =
            template.findSlice(TestEntity.class, spec, PageRequest.of(0, 3));
        assertNotNull(slice);
        assertEquals(3, slice.getContent().size());
        assertTrue(slice.hasNext());
    }

    @Test
    void testFindSliceNullClass() {
        assertThrows(IllegalArgumentException.class,
            () -> template.findSlice(null, (root, query, cb) -> cb.conjunction(), PageRequest.of(0, 10)));
    }

    @Test
    void testFindSliceNullSpec() {
        assertThrows(IllegalArgumentException.class,
            () -> template.findSlice(TestEntity.class, null, PageRequest.of(0, 10)));
    }

    @Test
    void testFindSliceNullPageable() {
        assertThrows(IllegalArgumentException.class,
            () -> template.findSlice(TestEntity.class, (root, query, cb) -> cb.conjunction(), null));
    }

    @Test
    void testFindAllById() {
        TestEntity e1 = new TestEntity();
        e1.setName("byId1");
        e1.setStatus(1);
        repository.save(e1);

        TestEntity e2 = new TestEntity();
        e2.setName("byId2");
        e2.setStatus(2);
        repository.save(e2);
        repository.flush();

        List<TestEntity> result = template.findAllById(TestEntity.class, List.of(e1.getId(), e2.getId()));
        assertEquals(2, result.size());
    }

    @Test
    void testFindAllByIdNullClass() {
        assertThrows(IllegalArgumentException.class, () -> template.findAllById(null, List.of(1L)));
    }

    @Test
    void testFindAllByIdNullIds() {
        assertThrows(IllegalArgumentException.class, () -> template.findAllById(TestEntity.class, null));
    }

    @Test
    void testFindAllByIdEmptyIds() {
        assertThrows(IllegalArgumentException.class, () -> template.findAllById(TestEntity.class, List.of()));
    }

    @Test
    void testFindNotDeletedAllById() {
        TestEntity e1 = new TestEntity();
        e1.setName("ndById1");
        e1.setStatus(1);
        repository.save(e1);
        repository.flush();

        List<TestEntity> result = template.findNotDeletedAllById(TestEntity.class, List.of(e1.getId()));
        assertEquals(1, result.size());
    }

    @Test
    void testFindNotDeletedAllByIdNullClass() {
        assertThrows(IllegalArgumentException.class, () -> template.findNotDeletedAllById(null, List.of(1L)));
    }

    @Test
    void testFindNotDeletedAllByIdNullIds() {
        assertThrows(IllegalArgumentException.class, () -> template.findNotDeletedAllById(TestEntity.class, null));
    }

    @Test
    void testFindNotDeletedAllByIdEmptyIds() {
        assertThrows(IllegalArgumentException.class, () -> template.findNotDeletedAllById(TestEntity.class, List.of()));
    }

    @Test
    void testFindKeysetPage() {
        for (int i = 0; i < 5; i++) {
            TestEntity e = new TestEntity();
            e.setName("ks" + i);
            e.setStatus(i);
            repository.save(e);
        }
        repository.flush();

        Specification<TestEntity> spec = (root, query, cb) -> cb.conjunction();
        MyJpaTemplate.KeysetPage<TestEntity> page =
            template.findKeysetPage(TestEntity.class, spec, Sort.by("id"), 3, null);
        assertNotNull(page);
        assertEquals(3, page.content().size());
    }

    @Test
    void testFindKeysetPageNullClass() {
        assertThrows(IllegalArgumentException.class,
            () -> template.findKeysetPage(null, (root, query, cb) -> cb.conjunction(), Sort.by("id"), 10, null));
    }

    @Test
    void testFindKeysetPageNullSpec() {
        assertThrows(IllegalArgumentException.class,
            () -> template.findKeysetPage(TestEntity.class, null, Sort.by("id"), 10, null));
    }

    @Test
    void testFindKeysetPageNullSort() {
        assertThrows(IllegalArgumentException.class,
            () -> template.findKeysetPage(TestEntity.class, (root, query, cb) -> cb.conjunction(), null, 10, null));
    }

    @Test
    void testFindKeysetPageUnsortedSort() {
        assertThrows(IllegalArgumentException.class, () -> template.findKeysetPage(TestEntity.class,
            (root, query, cb) -> cb.conjunction(), Sort.unsorted(), 10, null));
    }

    @Test
    void testFindKeysetPageInvalidPageSize() {
        assertThrows(IllegalArgumentException.class, () -> template.findKeysetPage(TestEntity.class,
            (root, query, cb) -> cb.conjunction(), Sort.by("id"), 0, null));
    }

    @Test
    void testFindKeysetPageInvalidLastSortValues() {
        assertThrows(IllegalArgumentException.class, () -> template.findKeysetPage(TestEntity.class,
            (root, query, cb) -> cb.conjunction(), Sort.by("id"), 10, new Object[] {1, 2}));
    }

    @Test
    void testFindAllWithSort() {
        for (int i = 0; i < 3; i++) {
            TestEntity e = new TestEntity();
            e.setName("sort" + i);
            e.setStatus(i);
            repository.save(e);
        }
        repository.flush();

        QuerySpec<TestEntity> qs = new QuerySpec<>();
        List<TestEntity> result = template.findAll(TestEntity.class, qs, Sort.by("name"));
        assertEquals(3, result.size());
    }

    @Test
    void testFindAllWithSortNullClass() {
        assertThrows(IllegalArgumentException.class, () -> template.findAll(null, new QuerySpec<>(), Sort.by("name")));
    }

    @Test
    void testFindAllWithSortNullSpec() {
        assertThrows(IllegalArgumentException.class,
            () -> template.findAll(TestEntity.class, (QuerySpec<TestEntity>)null, Sort.by("name")));
    }

    @Test
    void testFindAllWithSortNullSort() {
        assertThrows(IllegalArgumentException.class,
            () -> template.findAll(TestEntity.class, new QuerySpec<>(), (Sort)null));
    }

    @Test
    void testFindOneWithQuerySpec() {
        TestEntity e = new TestEntity();
        e.setName("findOne");
        e.setStatus(1);
        repository.save(e);
        repository.flush();

        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.eq(TestEntity::getName, "findOne");
        java.util.Optional<TestEntity> result = template.findOne(TestEntity.class, qs);
        assertTrue(result.isPresent());
        assertEquals("findOne", result.get().getName());
    }

    @Test
    void testFindOneWithQuerySpecNotFound() {
        TestEntity e = new TestEntity();
        e.setName("exists");
        e.setStatus(1);
        repository.save(e);
        repository.flush();

        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.eq(TestEntity::getName, "nonexistent");
        java.util.Optional<TestEntity> result = template.findOne(TestEntity.class, qs);
        assertFalse(result.isPresent());
    }

    @Test
    void testFindOneWithQuerySpecNullClass() {
        assertThrows(IllegalArgumentException.class, () -> template.findOne(null, new QuerySpec<>()));
    }

    @Test
    void testFindOneWithQuerySpecNullSpec() {
        assertThrows(IllegalArgumentException.class,
            () -> template.findOne(TestEntity.class, (QuerySpec<TestEntity>)null));
    }

    @Test
    void testFindOneWithSpecification() {
        TestEntity e = new TestEntity();
        e.setName("findOneSpec");
        e.setStatus(1);
        repository.save(e);
        repository.flush();

        Specification<TestEntity> spec = (root, query, cb) -> cb.equal(root.get("name"), "findOneSpec");
        java.util.Optional<TestEntity> result = template.findOne(TestEntity.class, spec);
        assertTrue(result.isPresent());
    }

    @Test
    void testFindOneWithSpecificationNotFound() {
        TestEntity e = new TestEntity();
        e.setName("exists");
        e.setStatus(1);
        repository.save(e);
        repository.flush();

        Specification<TestEntity> spec = (root, query, cb) -> cb.equal(root.get("name"), "nonexistent");
        java.util.Optional<TestEntity> result = template.findOne(TestEntity.class, spec);
        assertFalse(result.isPresent());
    }

    @Test
    void testFindWithSpecificationAndMaxResults() {
        for (int i = 0; i < 5; i++) {
            TestEntity e = new TestEntity();
            e.setName("findSpecLimit" + i);
            e.setStatus(i);
            repository.save(e);
        }
        repository.flush();

        Specification<TestEntity> spec = (root, query, cb) -> cb.conjunction();
        List<TestEntity> result = template.find(TestEntity.class, spec, 3);
        assertEquals(3, result.size());
    }

    @Test
    void testFindWithSpecificationMaxResultsDisabled() {
        for (int i = 0; i < 5; i++) {
            TestEntity e = new TestEntity();
            e.setName("findSpecNoLimit" + i);
            e.setStatus(i);
            repository.save(e);
        }
        repository.flush();

        Specification<TestEntity> spec = (root, query, cb) -> cb.conjunction();
        // find() requires positive maxResults; use find with default maxResults
        List<TestEntity> result = template.find(TestEntity.class, spec);
        assertEquals(5, result.size());
    }

    @Test
    void testFindWithSpecificationAndMaxResultsInvalidMaxRows() {
        assertThrows(IllegalArgumentException.class,
            () -> template.find(TestEntity.class, (root, query, cb) -> cb.conjunction(), 0));
    }

    @Test
    void testFindAllWithQuerySpecNullClass() {
        assertThrows(IllegalArgumentException.class, () -> template.findAll(null, new QuerySpec<>()));
    }

    @Test
    void testFindAllWithQuerySpecNullSpec() {
        assertThrows(IllegalArgumentException.class,
            () -> template.findAll(TestEntity.class, (QuerySpec<TestEntity>)null));
    }

    @Test
    void testFindAllWithQuerySpecAndMaxResultsNullClass() {
        assertThrows(IllegalArgumentException.class, () -> template.findAll(null, new QuerySpec<>(), 10));
    }

    @Test
    void testFindAllWithQuerySpecAndMaxResultsNullSpec() {
        assertThrows(IllegalArgumentException.class,
            () -> template.findAll(TestEntity.class, (QuerySpec<TestEntity>)null, 10));
    }

    @Test
    void testFindAllWithQuerySpecAndMaxResultsInvalidMaxRows() {
        assertThrows(IllegalArgumentException.class, () -> template.findAll(TestEntity.class, new QuerySpec<>(), 0));
    }

    @Test
    void testFindAllWithQuerySpecAndSortNullClass() {
        assertThrows(IllegalArgumentException.class, () -> template.findAll(null, new QuerySpec<>(), Sort.by("name")));
    }

    @Test
    void testFindAllWithQuerySpecAndSortNullSpec() {
        assertThrows(IllegalArgumentException.class,
            () -> template.findAll(TestEntity.class, (QuerySpec<TestEntity>)null, Sort.by("name")));
    }

    @Test
    void testFindAllWithQuerySpecAndSortNullSort() {
        assertThrows(IllegalArgumentException.class,
            () -> template.findAll(TestEntity.class, new QuerySpec<>(), (Sort)null));
    }

    @Test
    void testFindAllWithQuerySpecAndEntityGraphNullClass() {
        assertThrows(IllegalArgumentException.class, () -> template.findAll(null, new QuerySpec<>(),
            com.zsubera.jpa.util.EntityGraphHelper.forEntity(TestEntity.class)));
    }

    @Test
    void testFindAllWithQuerySpecAndEntityGraphNullSpec() {
        assertThrows(IllegalArgumentException.class, () -> template.findAll(TestEntity.class,
            (QuerySpec<TestEntity>)null, com.zsubera.jpa.util.EntityGraphHelper.forEntity(TestEntity.class)));
    }

    @Test
    void testFindAllWithQuerySpecAndEntityGraphAndMaxResultsNullClass() {
        assertThrows(IllegalArgumentException.class, () -> template.findAll(null, new QuerySpec<>(),
            com.zsubera.jpa.util.EntityGraphHelper.forEntity(TestEntity.class), 10));
    }

    @Test
    void testFindAllWithQuerySpecAndEntityGraphAndMaxResultsNullSpec() {
        assertThrows(IllegalArgumentException.class, () -> template.findAll(TestEntity.class,
            (QuerySpec<TestEntity>)null, com.zsubera.jpa.util.EntityGraphHelper.forEntity(TestEntity.class), 10));
    }

    @Test
    void testFindAllWithQuerySpecAndEntityGraphAndMaxResultsInvalidMaxRows() {
        assertThrows(IllegalArgumentException.class, () -> template.findAll(TestEntity.class, new QuerySpec<>(),
            com.zsubera.jpa.util.EntityGraphHelper.forEntity(TestEntity.class), 0));
    }

    @Test
    void testFindPageNullClass() {
        assertThrows(IllegalArgumentException.class,
            () -> template.findPage(null, (root, query, cb) -> cb.conjunction(), PageRequest.of(0, 10)));
    }

    @Test
    void testFindPageNullSpec() {
        assertThrows(IllegalArgumentException.class,
            () -> template.findPage(TestEntity.class, (Specification<TestEntity>)null, PageRequest.of(0, 10)));
    }

    @Test
    void testFindPageNullPageable() {
        assertThrows(IllegalArgumentException.class,
            () -> template.findPage(TestEntity.class, (root, query, cb) -> cb.conjunction(), null));
    }

    @Test
    void testFindAllWithQuerySpecAndPageableNullClass() {
        assertThrows(IllegalArgumentException.class,
            () -> template.findAll(null, new QuerySpec<>(), PageRequest.of(0, 10)));
    }

    @Test
    void testFindAllWithQuerySpecAndPageableNullSpec() {
        assertThrows(IllegalArgumentException.class,
            () -> template.findAll(TestEntity.class, (QuerySpec<TestEntity>)null, PageRequest.of(0, 10)));
    }

    @Test
    void testFindAllWithQuerySpecAndPageableNullPageable() {
        assertThrows(IllegalArgumentException.class,
            () -> template.findAll(TestEntity.class, new QuerySpec<>(), (Pageable)null));
    }

    @Test
    void testFindAllStreamNullClass() {
        assertThrows(IllegalArgumentException.class, () -> template.findAllStream(null, new QuerySpec<>(), stream -> {
        }));
    }

    @Test
    void testFindAllStreamNullSpec() {
        assertThrows(IllegalArgumentException.class,
            () -> template.findAllStream(TestEntity.class, (QuerySpec<TestEntity>)null, stream -> {
            }));
    }

    @Test
    void testFindAllStreamNullConsumer() {
        assertThrows(IllegalArgumentException.class,
            () -> template.findAllStream(TestEntity.class, new QuerySpec<>(), null));
    }

    @Test
    void testFindAllStreamWithEntityGraphNullClass() {
        assertThrows(IllegalArgumentException.class, () -> template.findAllStream(null, new QuerySpec<>(),
            com.zsubera.jpa.util.EntityGraphHelper.forEntity(TestEntity.class), stream -> {
            }));
    }

    @Test
    void testFindAllStreamWithEntityGraphNullSpec() {
        assertThrows(IllegalArgumentException.class, () -> template.findAllStream(TestEntity.class,
            (QuerySpec<TestEntity>)null, com.zsubera.jpa.util.EntityGraphHelper.forEntity(TestEntity.class), stream -> {
            }));
    }

    @Test
    void testFindAllStreamWithEntityGraphNullConsumer() {
        assertThrows(IllegalArgumentException.class, () -> template.findAllStream(TestEntity.class, new QuerySpec<>(),
            com.zsubera.jpa.util.EntityGraphHelper.forEntity(TestEntity.class), null));
    }

    @Test
    void testFindAllStreamWithEntityGraphNullEntityGraph() {
        // findAllStream with null entityGraph should not throw (entityGraph is optional)
        TestEntity e = new TestEntity();
        e.setName("stream");
        e.setStatus(1);
        repository.save(e);
        repository.flush();
        assertDoesNotThrow(() -> template.findAllStream(TestEntity.class, new QuerySpec<>(), null, stream -> {
        }));
    }

    @Test
    void testFindAllCached() {
        QueryCacheManager cacheManager = new QueryCacheManager();
        template.setCacheManager(cacheManager);

        TestEntity e = new TestEntity();
        e.setName("cached");
        e.setStatus(1);
        repository.save(e);

        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.eq(TestEntity::getName, "cached");
        List<TestEntity> result = template.findAllCached(TestEntity.class, qs, 60);
        assertEquals(1, result.size());
        assertNull(cacheManager.get("nonexistent-key"));
    }

    @Test
    void testFindAllCachedCacheHit() {
        QueryCacheManager cacheManager = new QueryCacheManager();
        template.setCacheManager(cacheManager);

        TestEntity e = new TestEntity();
        e.setName("cacheHit");
        e.setStatus(1);
        repository.save(e);

        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.eq(TestEntity::getName, "cacheHit");
        template.findAllCached(TestEntity.class, qs, 60);
        List<TestEntity> result = template.findAllCached(TestEntity.class, qs, 60);
        assertEquals(1, result.size());
    }

    @Test
    void testFindAllCachedNullEntityClass() {
        QueryCacheManager cacheManager = new QueryCacheManager();
        template.setCacheManager(cacheManager);
        assertThrows(IllegalArgumentException.class, () -> template.findAllCached(null, new QuerySpec<>(), 60));
    }

    @Test
    void testFindAllCachedNullSpec() {
        QueryCacheManager cacheManager = new QueryCacheManager();
        template.setCacheManager(cacheManager);
        assertThrows(IllegalArgumentException.class,
            () -> template.findAllCached(TestEntity.class, (QuerySpec<TestEntity>)null, (long)60));
    }

    @Test
    void testFindAllCachedNegativeTtl() {
        QueryCacheManager cacheManager = new QueryCacheManager();
        template.setCacheManager(cacheManager);
        assertThrows(IllegalArgumentException.class,
            () -> template.findAllCached(TestEntity.class, new QuerySpec<>(), -1));
    }

    @Test
    void testFindAllCachedWithoutCacheManager() {
        template.setCacheManager(null);
        assertThrows(IllegalStateException.class,
            () -> template.findAllCached(TestEntity.class, new QuerySpec<>(), 60));
    }

    @Test
    void testFindAllCachedReturnIsMutableAndCacheIsIndependent() {
        QueryCacheManager cacheManager = new QueryCacheManager();
        template.setCacheManager(cacheManager);

        TestEntity e = new TestEntity();
        e.setName("mutable");
        e.setStatus(1);
        repository.save(e);

        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.eq(TestEntity::getName, "mutable");
        List<TestEntity> result = template.findAllCached(TestEntity.class, qs, 60);
        assertEquals(1, result.size());

        result.clear();
        assertEquals(0, result.size());

        List<TestEntity> cached = template.findAllCached(TestEntity.class, qs, 60);
        assertEquals(1, cached.size());
    }

    @Test
    void testGetSetCacheManager() {
        QueryCacheManager cm = new QueryCacheManager();
        template.setCacheManager(cm);
        assertSame(cm, template.getCacheManager());
    }

    @Test
    void testSetMaxBulkOperationRowsAfterInit() {
        assertDoesNotThrow(() -> template.setMaxBulkOperationRows(5000));
    }

    @Test
    void testExecuteMergeSpecNull() {
        assertThrows(IllegalArgumentException.class,
            () -> template.execute((com.zsubera.jpa.update.MergeSpec<TestEntity>)null));
    }

    @Test
    void testExecuteBatchMergeSpecNull() {
        assertThrows(IllegalArgumentException.class,
            () -> template.executeBatch((com.zsubera.jpa.update.MergeSpec<TestEntity>)null, List.of(), 10));
    }

    @Test
    void testExecuteBatchMergeSpecEmptyEntities() {
        com.zsubera.jpa.update.MergeSpec<TestEntity> spec = new com.zsubera.jpa.update.MergeSpec<>(TestEntity.class);
        assertThrows(IllegalArgumentException.class, () -> template.executeBatch(spec, List.of(), 10));
    }

    @Test
    void testExecuteBatchMergeSpecInvalidBatchSize() {
        com.zsubera.jpa.update.MergeSpec<TestEntity> spec = new com.zsubera.jpa.update.MergeSpec<>(TestEntity.class);
        assertThrows(IllegalArgumentException.class, () -> template.executeBatch(spec, List.of(new TestEntity()), 0));
    }

    @Test
    void testExecuteWithMaxRowsDeleteSpecNull() {
        assertThrows(IllegalArgumentException.class,
            () -> template.executeWithMaxRows((DeleteSpec<TestEntity>)null, 10));
    }

    @Test
    void testExecuteBatchInSeparateTransactionsUpdateWithStrategyNullSpec() {
        assertThrows(IllegalArgumentException.class,
            () -> template.executeBatchInSeparateTransactions((UpdateSpec<TestEntity>)null, 10,
                MyJpaTemplateOperations.BatchFailureStrategy.CONTINUE));
    }

    @Test
    void testExecuteBatchInSeparateTransactionsUpdateWithStrategyInvalidSize() {
        UpdateSpec<TestEntity> spec = template.update(TestEntity.class).set(TestEntity::getStatus, 1);
        assertThrows(IllegalArgumentException.class, () -> template.executeBatchInSeparateTransactions(spec, 0,
            MyJpaTemplateOperations.BatchFailureStrategy.CONTINUE));
    }

    @Test
    void testExecuteBatchInSeparateTransactionsUpdateWithStrategyNullStrategy() {
        UpdateSpec<TestEntity> spec = template.update(TestEntity.class).set(TestEntity::getStatus, 1);
        assertThrows(IllegalArgumentException.class, () -> template.executeBatchInSeparateTransactions(spec, 10, null));
    }

    @Test
    void testExecuteBatchInSeparateTransactionsDeleteWithStrategyNullSpec() {
        assertThrows(IllegalArgumentException.class,
            () -> template.executeBatchInSeparateTransactions((DeleteSpec<TestEntity>)null, 10,
                MyJpaTemplateOperations.BatchFailureStrategy.CONTINUE));
    }

    @Test
    void testExecuteBatchInSeparateTransactionsDeleteWithStrategyInvalidSize() {
        DeleteSpec<TestEntity> spec = template.delete(TestEntity.class);
        assertThrows(IllegalArgumentException.class, () -> template.executeBatchInSeparateTransactions(spec, 0,
            MyJpaTemplateOperations.BatchFailureStrategy.CONTINUE));
    }

    @Test
    void testExecuteBatchInSeparateTransactionsDeleteWithStrategyNullStrategy() {
        DeleteSpec<TestEntity> spec = template.delete(TestEntity.class);
        assertThrows(IllegalArgumentException.class, () -> template.executeBatchInSeparateTransactions(spec, 10, null));
    }

    @Test
    @org.springframework.transaction.annotation.Transactional(
        propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void testExecuteBatchInSeparateTransactionsUpdateWithAbortStrategy() {
        for (int i = 0; i < 5; i++) {
            TestEntity e = new TestEntity();
            e.setName("abortStrat" + i);
            e.setStatus(0);
            repository.save(e);
        }
        repository.flush();
        entityManager.clear();

        UpdateSpec<TestEntity> spec =
            template.update(TestEntity.class).set(TestEntity::getStatus, 1).eq(TestEntity::getStatus, 0);
        MyJpaTemplateOperations.BatchResult result =
            template.executeBatchInSeparateTransactions(spec, 2, MyJpaTemplateOperations.BatchFailureStrategy.ABORT);
        assertTrue(result.success());
        assertEquals(5, result.totalRows());
    }

    @Test
    @org.springframework.transaction.annotation.Transactional(
        propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void testExecuteBatchInSeparateTransactionsDeleteWithAbortStrategy() {
        for (int i = 0; i < 5; i++) {
            TestEntity e = new TestEntity();
            e.setName("abortDel" + i);
            e.setStatus(0);
            repository.save(e);
        }
        repository.flush();
        entityManager.clear();

        DeleteSpec<TestEntity> spec = template.delete(TestEntity.class).eq(TestEntity::getStatus, 0);
        MyJpaTemplateOperations.BatchResult result =
            template.executeBatchInSeparateTransactions(spec, 2, MyJpaTemplateOperations.BatchFailureStrategy.ABORT);
        assertTrue(result.success());
        assertEquals(5, result.totalRows());
    }

    @Test
    void testFindSliceWithSmallMaxResults() {
        for (int i = 0; i < 5; i++) {
            TestEntity e = new TestEntity();
            e.setName("sliceLim" + i);
            e.setStatus(i);
            repository.save(e);
        }
        repository.flush();

        template.setMaxResults(3);
        Specification<TestEntity> spec = (root, query, cb) -> cb.conjunction();
        org.springframework.data.domain.Slice<TestEntity> slice =
            template.findSlice(TestEntity.class, spec, PageRequest.of(0, 5));
        assertNotNull(slice);
        template.setMaxResults(MyJpaTemplate.DEFAULT_MAX_RESULTS);
    }

    @Test
    void testFindOneSpecificationNullClass() {
        assertThrows(IllegalArgumentException.class, () -> template.findOne(null, (Specification<TestEntity>)null));
    }

    @Test
    void testFindOneSpecificationNullSpec() {
        assertThrows(IllegalArgumentException.class,
            () -> template.findOne(TestEntity.class, (Specification<TestEntity>)null));
    }

    @Test
    void testCountSpecificationNullClass() {
        assertThrows(IllegalArgumentException.class, () -> template.count(null, (Specification<TestEntity>)null));
    }

    @Test
    void testCountSpecificationNullSpec() {
        assertThrows(IllegalArgumentException.class,
            () -> template.count(TestEntity.class, (Specification<TestEntity>)null));
    }

    @Test
    void testFindSpecificationNullClass() {
        assertThrows(IllegalArgumentException.class, () -> template.find(null, (root, query, cb) -> cb.conjunction()));
    }

    @Test
    void testFindSpecificationNullSpec() {
        assertThrows(IllegalArgumentException.class,
            () -> template.find(TestEntity.class, (Specification<TestEntity>)null));
    }

    @Test
    void testFindSpecificationInvalidMaxResults() {
        assertThrows(IllegalArgumentException.class,
            () -> template.find(TestEntity.class, (root, query, cb) -> cb.conjunction(), 0));
    }

    @Test
    void testSaveAllBatchedPureNull() {
        assertThrows(IllegalArgumentException.class, () -> template.saveAllBatchedPure(null, 10));
    }

    @Test
    void testSaveAllBatchedPureInvalidBatchSize() {
        List<TestEntity> entities = List.of(new TestEntity());
        assertThrows(IllegalArgumentException.class, () -> template.saveAllBatchedPure(entities, 0));
        assertThrows(IllegalArgumentException.class, () -> template.saveAllBatchedPure(entities, -1));
    }

    @Test
    void testFindAllStreamConsumerOverload() {
        TestEntity e = new TestEntity();
        e.setName("consumerStream");
        e.setStatus(1);
        repository.save(e);
        repository.flush();

        final long[] count = {0};
        template.findAllStream(TestEntity.class, qs -> qs.eq(TestEntity::getName, "consumerStream"), stream -> {
            count[0] = stream.count();
        });
        assertEquals(1, count[0]);
    }

    @Test
    void testFindAllCachedConsumerOverload() {
        QueryCacheManager cacheManager = new QueryCacheManager();
        template.setCacheManager(cacheManager);

        TestEntity e = new TestEntity();
        e.setName("cachedConsumer");
        e.setStatus(1);
        repository.save(e);

        List<TestEntity> result =
            template.findAllCached(TestEntity.class, qs -> qs.eq(TestEntity::getName, "cachedConsumer"), 60);
        assertEquals(1, result.size());
    }

    @Test
    void testFindOneConsumerOverload() {
        TestEntity e = new TestEntity();
        e.setName("findOneConsumer");
        e.setStatus(1);
        repository.save(e);

        var result = template.findOne(TestEntity.class, qs -> qs.eq(TestEntity::getName, "findOneConsumer"));
        assertTrue(result.isPresent());
    }

    @Test
    void testCountConsumerOverload() {
        for (int i = 0; i < 3; i++) {
            TestEntity e = new TestEntity();
            e.setName("countConsumer" + i);
            e.setStatus(10);
            repository.save(e);
        }
        long count = template.count(TestEntity.class, qs -> qs.eq(TestEntity::getStatus, 10));
        assertEquals(3, count);
    }

    @Test
    void testFindAllConsumerOverload() {
        for (int i = 0; i < 3; i++) {
            TestEntity e = new TestEntity();
            e.setName("findAllConsumer" + i);
            e.setStatus(1);
            repository.save(e);
        }
        List<TestEntity> result = template.findAll(TestEntity.class, qs -> qs.eq(TestEntity::getStatus, 1));
        assertEquals(3, result.size());
    }

    @Test
    void testFindAllConsumerWithMaxResultsOverload() {
        for (int i = 0; i < 5; i++) {
            TestEntity e = new TestEntity();
            e.setName("findAllConsumerMax" + i);
            e.setStatus(1);
            repository.save(e);
        }
        List<TestEntity> result = template.findAll(TestEntity.class, qs -> qs.eq(TestEntity::getStatus, 1), 3);
        assertEquals(3, result.size());
    }

    @Test
    void testFindAllConsumerWithSortOverload() {
        for (int i = 0; i < 3; i++) {
            TestEntity e = new TestEntity();
            e.setName("findAllConsumerSort" + i);
            e.setStatus(1);
            repository.save(e);
        }
        List<TestEntity> result =
            template.findAll(TestEntity.class, qs -> qs.eq(TestEntity::getStatus, 1), Sort.by("name"));
        assertEquals(3, result.size());
    }

    @Test
    void testFindAllConsumerWithPageableOverload() {
        for (int i = 0; i < 5; i++) {
            TestEntity e = new TestEntity();
            e.setName("findAllConsumerPage" + i);
            e.setStatus(1);
            repository.save(e);
        }
        Page<TestEntity> page =
            template.findAll(TestEntity.class, qs -> qs.eq(TestEntity::getStatus, 1), PageRequest.of(0, 2));
        assertEquals(5, page.getTotalElements());
    }

    @Test
    void testFindAllWithMaxResultsDisabled() {
        for (int i = 0; i < 5; i++) {
            TestEntity e = new TestEntity();
            e.setName("noLimit" + i);
            e.setStatus(0);
            repository.save(e);
        }

        QuerySpec<TestEntity> qs = new QuerySpec<>();
        List<TestEntity> result = template.findAll(TestEntity.class, qs, -1);
        assertEquals(5, result.size());
    }

    @Test
    void testFindAllWithEntityGraphAndMaxResultsDisabled() {
        for (int i = 0; i < 3; i++) {
            TestEntity e = new TestEntity();
            e.setName("egNoLimit" + i);
            e.setStatus(0);
            repository.save(e);
        }

        QuerySpec<TestEntity> qs = new QuerySpec<>();
        List<TestEntity> result = template.findAll(TestEntity.class, qs,
            com.zsubera.jpa.util.EntityGraphHelper.forEntity(TestEntity.class), -1);
        assertEquals(3, result.size());
    }

    @Test
    void testFindAllWithEntityGraphAndMaxResultsInvalid() {
        assertThrows(IllegalArgumentException.class, () -> template.findAll(TestEntity.class, new QuerySpec<>(),
            com.zsubera.jpa.util.EntityGraphHelper.forEntity(TestEntity.class), 0));
    }

    @Test
    void testFindKeysetPageWithMultiFieldSortNullValues() {
        TestEntity e1 = new TestEntity();
        e1.setName(null);
        e1.setStatus(1);
        repository.save(e1);

        TestEntity e2 = new TestEntity();
        e2.setName("aaa");
        e2.setStatus(2);
        repository.save(e2);
        repository.flush();

        Specification<TestEntity> spec = (root, query, cb) -> cb.conjunction();
        org.springframework.data.domain.Sort sort =
            org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Order.asc("name"),
                org.springframework.data.domain.Sort.Order.asc("status"));
        MyJpaTemplate.KeysetPage<TestEntity> page = template.findKeysetPage(TestEntity.class, spec, sort, 10, null);
        assertEquals(2, page.content().size());
    }

    @Test
    void testFindKeysetPageWithDescSortNullValues() {
        TestEntity e1 = new TestEntity();
        e1.setName(null);
        e1.setStatus(1);
        repository.save(e1);

        TestEntity e2 = new TestEntity();
        e2.setName("aaa");
        e2.setStatus(2);
        repository.save(e2);
        repository.flush();

        Specification<TestEntity> spec = (root, query, cb) -> cb.conjunction();
        org.springframework.data.domain.Sort sort =
            org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Order.desc("name"));
        MyJpaTemplate.KeysetPage<TestEntity> page = template.findKeysetPage(TestEntity.class, spec, sort, 10, null);
        assertEquals(2, page.content().size());
    }
}
