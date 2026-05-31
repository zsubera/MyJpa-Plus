package com.zsubera.jpa.template;

import static org.junit.jupiter.api.Assertions.*;

import com.zsubera.jpa.spec.QuerySpec;
import com.zsubera.jpa.spec.TestApplication;
import com.zsubera.jpa.spec.TestEntity;
import com.zsubera.jpa.spec.TestEntityRepository;
import com.zsubera.jpa.update.DeleteSpec;
import com.zsubera.jpa.update.UpdateSpec;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.context.ContextConfiguration;

@DataJpaTest
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
        assertThrows(IllegalArgumentException.class, () -> custom.setMaxResults(-1));
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
        assertThrows(IllegalArgumentException.class, () -> template.findOne(TestEntity.class, null));
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
    void testExecuteBatchInSeparateTransactionsUpdate() {
        for (int i = 0; i < 5; i++) {
            TestEntity e = new TestEntity();
            e.setName("sepTx" + i);
            e.setStatus(0);
            repository.save(e);
        }
        repository.flush();

        UpdateSpec<TestEntity> spec =
            template.update(TestEntity.class).set(TestEntity::getStatus, 1).eq(TestEntity::getStatus, 0);
        int count = template.executeBatchInSeparateTransactions(spec, 2);
        assertTrue(count > 0, "Should update at least one row");
    }

    @Test
    void testExecuteBatchInSeparateTransactionsDelete() {
        for (int i = 0; i < 5; i++) {
            TestEntity e = new TestEntity();
            e.setName("sepTxDel" + i);
            e.setStatus(0);
            repository.save(e);
        }
        repository.flush();

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
}
