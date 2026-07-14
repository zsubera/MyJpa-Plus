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
    @EnableJpaRepositories(basePackageClasses = {MyJpaTestRepository.class, SimpleTestRepository.class},
        repositoryBaseClass = DefaultMyJpaRepository.class)
    static class TestConfig {}

    @Autowired
    private MyJpaTestRepository repository;

    @Autowired
    private SimpleTestRepository simpleRepository;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        repository.flush();
        simpleRepository.deleteAll();
        simpleRepository.flush();
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
    void testFindAll_withSortAndConsumer() {
        MyJpaTestEntity e1 = new MyJpaTestEntity();
        e1.setName("z");
        e1.setDeleted(false);
        repository.save(e1);

        MyJpaTestEntity e2 = new MyJpaTestEntity();
        e2.setName("a");
        e2.setDeleted(false);
        repository.save(e2);

        List<MyJpaTestEntity> result =
            repository.findAll((Specification<MyJpaTestEntity>)(root, query, cb) -> cb.conjunction(),
                org.springframework.data.domain.Sort.by("name"));
        assertEquals(2, result.size());
        assertEquals("a", result.get(0).getName());
    }

    @Test
    void testFindAll_withSortAndConsumerLambda() {
        MyJpaTestEntity e1 = new MyJpaTestEntity();
        e1.setName("z");
        e1.setDeleted(false);
        repository.save(e1);

        MyJpaTestEntity e2 = new MyJpaTestEntity();
        e2.setName("a");
        e2.setDeleted(false);
        repository.save(e2);

        List<MyJpaTestEntity> result = repository.findAll(s -> s.eq(MyJpaTestEntity::getDeleted, false),
            org.springframework.data.domain.Sort.by("name"));
        assertEquals(2, result.size());
        assertEquals("a", result.get(0).getName());
    }

    // ---- Projection mode throws UnsupportedOperationException ----

    @Test
    void findAll_specWithProjection_throwsUnsupportedOperationException() {
        var spec = new com.zsubera.jpa.spec.QuerySpec<MyJpaTestEntity>().select(MyJpaTestEntity::getName);
        assertThrows(UnsupportedOperationException.class, () -> repository.findAll(spec));
    }

    @Test
    void findAll_specWithProjectionAndSort_throwsUnsupportedOperationException() {
        var spec = new com.zsubera.jpa.spec.QuerySpec<MyJpaTestEntity>().select(MyJpaTestEntity::getName);
        assertThrows(UnsupportedOperationException.class,
            () -> repository.findAll(spec, org.springframework.data.domain.Sort.by("name")));
    }

    @Test
    void findAll_specWithProjectionAndPageable_throwsUnsupportedOperationException() {
        var spec = new com.zsubera.jpa.spec.QuerySpec<MyJpaTestEntity>().select(MyJpaTestEntity::getName);
        assertThrows(UnsupportedOperationException.class,
            () -> repository.findAll(spec, org.springframework.data.domain.PageRequest.of(0, 10)));
    }

    @Test
    void findOne_specWithProjection_throwsUnsupportedOperationException() {
        var spec = new com.zsubera.jpa.spec.QuerySpec<MyJpaTestEntity>().select(MyJpaTestEntity::getName);
        assertThrows(UnsupportedOperationException.class, () -> repository.findOne(spec));
    }
}
