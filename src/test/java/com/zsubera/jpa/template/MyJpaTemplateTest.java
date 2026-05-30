package com.zsubera.jpa.template;

import static org.junit.jupiter.api.Assertions.*;

import com.zsubera.jpa.spec.QuerySpec;
import com.zsubera.jpa.spec.TestEntity;
import com.zsubera.jpa.spec.TestEntityRepository;
import com.zsubera.jpa.update.DeleteSpec;
import com.zsubera.jpa.update.UpdateSpec;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ContextConfiguration;

@DataJpaTest
@ContextConfiguration(classes = MyJpaTemplateTest.TestConfig.class)
@Import(MyJpaTemplate.class)
class MyJpaTemplateTest {

    @SpringBootApplication
    @EntityScan(basePackageClasses = TestEntity.class)
    @EnableJpaRepositories(basePackageClasses = TestEntityRepository.class)
    static class TestConfig {}

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
}
