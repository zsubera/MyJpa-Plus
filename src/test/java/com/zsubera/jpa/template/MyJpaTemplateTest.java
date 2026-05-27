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
        assertEquals(1, page.getTotalElements()); // unpaged sets total = content size
        assertEquals(1, page.getContent().size());
    }

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
}
