package com.zsubera.jpa.repository;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ContextConfiguration;

@DataJpaTest
@org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase(
    replace = org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = MyJpaRepositoryExtendedTest.TestConfig.class)
class MyJpaRepositoryExtendedTest {

    @SpringBootApplication
    @EntityScan(basePackageClasses = {MyJpaTestEntity.class, SimpleTestEntity.class})
    @EnableJpaRepositories(basePackageClasses = {MyJpaTestRepository.class, SimpleTestRepository.class})
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
    void testFindAllWithSort() {
        MyJpaTestEntity e1 = new MyJpaTestEntity();
        e1.setName("b");
        e1.setDeleted(false);
        repository.save(e1);
        MyJpaTestEntity e2 = new MyJpaTestEntity();
        e2.setName("a");
        e2.setDeleted(false);
        repository.save(e2);
        List<MyJpaTestEntity> result = repository.findAll(Sort.by("name"));
        assertEquals(2, result.size());
        assertEquals("a", result.get(0).getName());
    }

    @Test
    void testFindAllWithPageable() {
        MyJpaTestEntity e1 = new MyJpaTestEntity();
        e1.setName("a");
        e1.setDeleted(false);
        repository.save(e1);
        MyJpaTestEntity e2 = new MyJpaTestEntity();
        e2.setName("b");
        e2.setDeleted(false);
        repository.save(e2);
        var page = repository.findAll(PageRequest.of(0, 10));
        assertTrue(page.getTotalElements() >= 2);
    }

    @Test
    void testFindAllWithSpecification() {
        MyJpaTestEntity e1 = new MyJpaTestEntity();
        e1.setName("target");
        e1.setDeleted(false);
        repository.save(e1);
        MyJpaTestEntity e2 = new MyJpaTestEntity();
        e2.setName("other");
        e2.setDeleted(false);
        repository.save(e2);
        List<MyJpaTestEntity> result = repository
            .findAll((Specification<MyJpaTestEntity>)(root, query, cb) -> cb.equal(root.get("name"), "target"));
        assertEquals(1, result.size());
    }

    @Test
    void testFindAllWithSpecificationAndSort() {
        MyJpaTestEntity e1 = new MyJpaTestEntity();
        e1.setName("b");
        e1.setDeleted(false);
        repository.save(e1);
        MyJpaTestEntity e2 = new MyJpaTestEntity();
        e2.setName("a");
        e2.setDeleted(false);
        repository.save(e2);
        List<MyJpaTestEntity> result =
            repository.findAll((Specification<MyJpaTestEntity>)(root, query, cb) -> cb.conjunction(), Sort.by("name"));
        assertEquals(2, result.size());
        assertEquals("a", result.get(0).getName());
    }

    @Test
    void testFindAllWithSpecificationAndPageable() {
        MyJpaTestEntity e1 = new MyJpaTestEntity();
        e1.setName("a");
        e1.setDeleted(false);
        repository.save(e1);
        var page = repository.findAll((Specification<MyJpaTestEntity>)(root, query, cb) -> cb.conjunction(),
            PageRequest.of(0, 10));
        assertTrue(page.getTotalElements() >= 1);
    }

    @Test
    void testFindOneWithSpecification() {
        MyJpaTestEntity e1 = new MyJpaTestEntity();
        e1.setName("findOneSpec");
        e1.setDeleted(false);
        repository.save(e1);
        var result = repository
            .findOne((Specification<MyJpaTestEntity>)(root, query, cb) -> cb.equal(root.get("name"), "findOneSpec"));
        assertTrue(result.isPresent());
    }

    @Test
    void testCountWithSpecification() {
        MyJpaTestEntity e1 = new MyJpaTestEntity();
        e1.setName("countSpec");
        e1.setDeleted(false);
        repository.save(e1);
        long count = repository
            .count((Specification<MyJpaTestEntity>)(root, query, cb) -> cb.equal(root.get("name"), "countSpec"));
        assertEquals(1, count);
    }

    @Test
    void testExistsWithSpecification() {
        MyJpaTestEntity e1 = new MyJpaTestEntity();
        e1.setName("existsSpec");
        e1.setDeleted(false);
        repository.save(e1);
        assertTrue(repository
            .exists((Specification<MyJpaTestEntity>)(root, query, cb) -> cb.equal(root.get("name"), "existsSpec")));
        assertFalse(repository
            .exists((Specification<MyJpaTestEntity>)(root, query, cb) -> cb.equal(root.get("name"), "nonexistent")));
    }

    @Test
    void testExecute_nullUpdateSpec_throws() {
        assertThrows(Exception.class,
            () -> repository.execute((com.zsubera.jpa.update.UpdateSpec<MyJpaTestEntity>)null));
    }

    @Test
    void testExecute_nullDeleteSpec_throws() {
        assertThrows(Exception.class,
            () -> repository.execute((com.zsubera.jpa.update.DeleteSpec<MyJpaTestEntity>)null));
    }

    @Test
    void testExecute_nullMergeSpec_throws() {
        assertThrows(Exception.class,
            () -> repository.execute((com.zsubera.jpa.update.MergeSpec<MyJpaTestEntity>)null));
    }

    @Test
    void testFindNotDeletedAll_withSort() {
        MyJpaTestEntity e1 = new MyJpaTestEntity();
        e1.setName("z");
        e1.setDeleted(false);
        repository.save(e1);
        MyJpaTestEntity e2 = new MyJpaTestEntity();
        e2.setName("a");
        e2.setDeleted(false);
        repository.save(e2);
        MyJpaTestEntity e3 = new MyJpaTestEntity();
        e3.setName("c");
        e3.setDeleted(true);
        repository.save(e3);
        List<MyJpaTestEntity> result = repository.findAll(Specification.where(null), Sort.by("name"));
        assertEquals(3, result.size());
    }

    @Test
    void testFindNotDeletedAll_withConsumerAndSort() {
        MyJpaTestEntity e1 = new MyJpaTestEntity();
        e1.setName("z");
        e1.setDeleted(false);
        repository.save(e1);
        MyJpaTestEntity e2 = new MyJpaTestEntity();
        e2.setName("a");
        e2.setDeleted(false);
        repository.save(e2);
        List<MyJpaTestEntity> result =
            repository.findAll(s -> s.eq(MyJpaTestEntity::getDeleted, false), Sort.by("name"));
        assertEquals(2, result.size());
        assertEquals("a", result.get(0).getName());
    }
}
