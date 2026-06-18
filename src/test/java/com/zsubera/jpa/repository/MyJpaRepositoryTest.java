package com.zsubera.jpa.repository;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ContextConfiguration;

@DataJpaTest
@org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase(
    replace = org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = MyJpaRepositoryTest.TestConfig.class)
class MyJpaRepositoryTest {

    @SpringBootApplication
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = {MyJpaTestEntity.class, SimpleTestEntity.class})
    @EnableJpaRepositories(basePackageClasses = {MyJpaTestRepository.class, SimpleTestRepository.class})
    static class TestConfig {}

    @Autowired
    private MyJpaTestRepository repository;

    @Autowired
    private SimpleTestRepository simpleRepository;

    @BeforeEach
    void setUp() {
        simpleRepository.deleteAll();
        simpleRepository.flush();
    }

    @Test
    void testFindNotDeletedByIdReturnsActiveEntity() {
        MyJpaTestEntity entity = new MyJpaTestEntity();
        entity.setName("active");
        entity.setDeleted(false);
        MyJpaTestEntity saved = repository.save(entity);
        Optional<MyJpaTestEntity> result = repository.findNotDeletedById(saved.getId());
        assertTrue(result.isPresent());
        assertEquals("active", result.get().getName());
    }

    @Test
    void testFindNotDeletedByIdFiltersOutDeletedEntity() {
        MyJpaTestEntity entity = new MyJpaTestEntity();
        entity.setName("deleted");
        entity.setDeleted(true);
        MyJpaTestEntity saved = repository.save(entity);

        Optional<MyJpaTestEntity> result = repository.findNotDeletedById(saved.getId());
        assertFalse(result.isPresent());
    }

    @Test
    void testCountNotDeletedReturnsOnlyActiveCount() {
        MyJpaTestEntity active = new MyJpaTestEntity();
        active.setName("a1");
        active.setDeleted(false);
        repository.save(active);

        MyJpaTestEntity active2 = new MyJpaTestEntity();
        active2.setName("a2");
        active2.setDeleted(false);
        repository.save(active2);

        MyJpaTestEntity deleted = new MyJpaTestEntity();
        deleted.setName("d1");
        deleted.setDeleted(true);
        repository.save(deleted);

        assertEquals(2L, repository.countNotDeleted());
    }

    @Test
    void testCountNotDeletedWithSpec() {
        MyJpaTestEntity a1 = new MyJpaTestEntity();
        a1.setName("match");
        a1.setDeleted(false);
        repository.save(a1);

        MyJpaTestEntity a2 = new MyJpaTestEntity();
        a2.setName("other");
        a2.setDeleted(false);
        repository.save(a2);

        MyJpaTestEntity d1 = new MyJpaTestEntity();
        d1.setName("match");
        d1.setDeleted(true);
        repository.save(d1);

        long count = repository
            .countNotDeleted((Specification<MyJpaTestEntity>)(root, query, cb) -> cb.equal(root.get("name"), "match"));
        assertEquals(1L, count);
    }

    @Test
    void testFindNotDeletedAllFiltersSoftDeleted() {
        MyJpaTestEntity active = new MyJpaTestEntity();
        active.setName("active");
        active.setDeleted(false);
        repository.save(active);
        MyJpaTestEntity deleted = new MyJpaTestEntity();
        deleted.setName("deleted");
        deleted.setDeleted(true);
        repository.save(deleted);

        List<MyJpaTestEntity> result = repository.findNotDeletedAll();
        assertEquals(1, result.size());
        assertEquals("active", result.get(0).getName());
    }

    @Test
    void testFindNotDeletedAllWithSpec() {
        MyJpaTestEntity a1 = new MyJpaTestEntity();
        a1.setName("target");
        a1.setDeleted(false);
        repository.save(a1);

        MyJpaTestEntity a2 = new MyJpaTestEntity();
        a2.setName("other");
        a2.setDeleted(false);
        repository.save(a2);

        List<MyJpaTestEntity> result = repository.findNotDeletedAll(
            (Specification<MyJpaTestEntity>)(root, query, cb) -> cb.equal(root.get("name"), "target"));
        assertEquals(1, result.size());
        assertEquals("target", result.get(0).getName());
    }

    @Test
    void testFindNotDeletedOne() {
        MyJpaTestEntity active = new MyJpaTestEntity();
        active.setName("target");
        active.setDeleted(false);
        repository.save(active);

        MyJpaTestEntity deleted = new MyJpaTestEntity();
        deleted.setName("target");
        deleted.setDeleted(true);
        repository.save(deleted);

        Optional<MyJpaTestEntity> result = repository.findNotDeletedOne(
            (Specification<MyJpaTestEntity>)(root, query, cb) -> cb.equal(root.get("name"), "target"));
        assertTrue(result.isPresent());
        assertFalse(result.get().getDeleted());
    }

    @Test
    void testFindNotDeletedAllWithPageable() {
        MyJpaTestEntity a1 = new MyJpaTestEntity();
        a1.setName("a");
        a1.setDeleted(false);
        repository.save(a1);

        MyJpaTestEntity a2 = new MyJpaTestEntity();
        a2.setName("b");
        a2.setDeleted(false);
        repository.save(a2);

        MyJpaTestEntity deleted = new MyJpaTestEntity();
        deleted.setName("c");
        deleted.setDeleted(true);
        repository.save(deleted);

        Page<MyJpaTestEntity> page = repository.findNotDeletedAll(
            (Specification<MyJpaTestEntity>)(root, query, cb) -> cb.conjunction(), PageRequest.of(0, 10));
        assertEquals(2, page.getTotalElements());
    }

    @Test
    void testFindNotDeletedOneNullSpecWithoutSoftDelete() {
        SimpleTestEntity e1 = new SimpleTestEntity();
        e1.setName("first");
        simpleRepository.save(e1);

        SimpleTestEntity e2 = new SimpleTestEntity();
        e2.setName("second");
        simpleRepository.save(e2);

        // findNotDeletedOne(null) 对无 @SoftDelete 的实体应返回第一个实体，而非加载全表
        Optional<SimpleTestEntity> result = simpleRepository.findNotDeletedOne((Specification<SimpleTestEntity>)null);
        assertTrue(result.isPresent(), "Should find at least one entity");
        assertEquals("first", result.get().getName());
    }

    @Test
    void testFindNotDeletedOneNullSpecEmptyTable() {
        Optional<SimpleTestEntity> result = simpleRepository.findNotDeletedOne((Specification<SimpleTestEntity>)null);
        assertFalse(result.isPresent(), "Should return empty for empty table");
    }

    @Test
    void testFindNotDeletedOneWithSpecWithoutSoftDelete() {
        SimpleTestEntity e1 = new SimpleTestEntity();
        e1.setName("target");
        simpleRepository.save(e1);

        SimpleTestEntity e2 = new SimpleTestEntity();
        e2.setName("other");
        simpleRepository.save(e2);

        Optional<SimpleTestEntity> result = simpleRepository
            .findOne((Specification<SimpleTestEntity>)(root, query, cb) -> cb.equal(root.get("name"), "target"));
        assertTrue(result.isPresent());
        assertEquals("target", result.get().getName());
    }

    @Test
    void testFindAllWithConsumerLambda() {
        MyJpaTestEntity e1 = new MyJpaTestEntity();
        e1.setName("lambda1");
        e1.setDeleted(false);
        repository.save(e1);

        MyJpaTestEntity e2 = new MyJpaTestEntity();
        e2.setName("lambda2");
        e2.setDeleted(false);
        repository.save(e2);

        List<MyJpaTestEntity> result = repository.findAll(s -> s.eq(MyJpaTestEntity::getName, "lambda1"));
        assertEquals(1, result.size());
        assertEquals("lambda1", result.get(0).getName());
    }

    @Test
    void testFindAllWithConsumerAndPageable() {
        MyJpaTestEntity e1 = new MyJpaTestEntity();
        e1.setName("page1");
        e1.setDeleted(false);
        repository.save(e1);

        MyJpaTestEntity e2 = new MyJpaTestEntity();
        e2.setName("page2");
        e2.setDeleted(false);
        repository.save(e2);

        Page<MyJpaTestEntity> page = repository.findAll(s -> s.eq(MyJpaTestEntity::getDeleted, false),
            org.springframework.data.domain.PageRequest.of(0, 10));
        assertTrue(page.getTotalElements() >= 2);
    }

    @Test
    void testFindAllWithConsumerAndSort() {
        MyJpaTestEntity e1 = new MyJpaTestEntity();
        e1.setName("sort2");
        e1.setDeleted(false);
        repository.save(e1);

        MyJpaTestEntity e2 = new MyJpaTestEntity();
        e2.setName("sort1");
        e2.setDeleted(false);
        repository.save(e2);

        List<MyJpaTestEntity> result = repository.findAll(s -> s.eq(MyJpaTestEntity::getDeleted, false),
            org.springframework.data.domain.Sort.by("name"));
        assertEquals(2, result.size());
    }

    @Test
    void testFindOneWithConsumerLambda() {
        MyJpaTestEntity e1 = new MyJpaTestEntity();
        e1.setName("findOneConsumer");
        e1.setDeleted(false);
        repository.save(e1);

        Optional<MyJpaTestEntity> result = repository.findOne(s -> s.eq(MyJpaTestEntity::getName, "findOneConsumer"));
        assertTrue(result.isPresent());
        assertEquals("findOneConsumer", result.get().getName());
    }

    @Test
    void testCountWithConsumerLambda() {
        MyJpaTestEntity e1 = new MyJpaTestEntity();
        e1.setName("countConsumer");
        e1.setDeleted(false);
        repository.save(e1);

        MyJpaTestEntity e2 = new MyJpaTestEntity();
        e2.setName("countConsumer2");
        e2.setDeleted(false);
        repository.save(e2);

        long count = repository.count(s -> s.eq(MyJpaTestEntity::getName, "countConsumer"));
        assertEquals(1, count);
    }

    @Test
    void testExistsWithConsumerLambda() {
        MyJpaTestEntity e1 = new MyJpaTestEntity();
        e1.setName("existsConsumer");
        e1.setDeleted(false);
        repository.save(e1);

        assertTrue(repository.exists(s -> s.eq(MyJpaTestEntity::getName, "existsConsumer")));
        assertFalse(repository.exists(s -> s.eq(MyJpaTestEntity::getName, "nonexistent")));
    }

    @Test
    void testFindNotDeletedAllWithConsumerLambda() {
        MyJpaTestEntity e1 = new MyJpaTestEntity();
        e1.setName("ndConsumer1");
        e1.setDeleted(false);
        repository.save(e1);

        MyJpaTestEntity e2 = new MyJpaTestEntity();
        e2.setName("ndConsumer2");
        e2.setDeleted(true);
        repository.save(e2);

        List<MyJpaTestEntity> result = repository.findNotDeletedAll(s -> s.eq(MyJpaTestEntity::getDeleted, false));
        assertEquals(1, result.size());
    }

    @Test
    void testFindNotDeletedAllWithConsumerAndPageable() {
        MyJpaTestEntity e1 = new MyJpaTestEntity();
        e1.setName("ndPage1");
        e1.setDeleted(false);
        repository.save(e1);

        Page<MyJpaTestEntity> page = repository.findNotDeletedAll(s -> s.eq(MyJpaTestEntity::getDeleted, false),
            org.springframework.data.domain.PageRequest.of(0, 10));
        assertTrue(page.getTotalElements() >= 1);
    }

    @Test
    void testFindNotDeletedOneWithConsumerLambda() {
        MyJpaTestEntity e1 = new MyJpaTestEntity();
        e1.setName("ndOneConsumer");
        e1.setDeleted(false);
        repository.save(e1);

        Optional<MyJpaTestEntity> result =
            repository.findNotDeletedOne(s -> s.eq(MyJpaTestEntity::getName, "ndOneConsumer"));
        assertTrue(result.isPresent());
    }

    @Test
    void testCountNotDeletedWithConsumerLambda() {
        MyJpaTestEntity e1 = new MyJpaTestEntity();
        e1.setName("ndCountConsumer");
        e1.setDeleted(false);
        repository.save(e1);

        long count = repository.countNotDeleted(s -> s.eq(MyJpaTestEntity::getDeleted, false));
        assertEquals(1, count);
    }
}
