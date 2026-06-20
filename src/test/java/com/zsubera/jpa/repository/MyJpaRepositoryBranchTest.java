package com.zsubera.jpa.repository;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ContextConfiguration;

@DataJpaTest
@org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase(
    replace = org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = MyJpaRepositoryBranchTest.TestConfig.class)
class MyJpaRepositoryBranchTest {

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
    void testFindNotDeletedAll_nullSpec() {
        MyJpaTestEntity e1 = new MyJpaTestEntity();
        e1.setName("a1");
        e1.setDeleted(false);
        repository.save(e1);
        MyJpaTestEntity e2 = new MyJpaTestEntity();
        e2.setName("a2");
        e2.setDeleted(true);
        repository.save(e2);
        List<MyJpaTestEntity> result = repository.findNotDeletedAll((Specification<MyJpaTestEntity>)null);
        assertEquals(1, result.size());
    }

    @Test
    void testFindNotDeletedAll_withSpec() {
        MyJpaTestEntity e1 = new MyJpaTestEntity();
        e1.setName("target");
        e1.setDeleted(false);
        repository.save(e1);
        MyJpaTestEntity e2 = new MyJpaTestEntity();
        e2.setName("other");
        e2.setDeleted(true);
        repository.save(e2);
        List<MyJpaTestEntity> result = repository.findNotDeletedAll(
            (Specification<MyJpaTestEntity>)(root, query, cb) -> cb.equal(root.get("name"), "target"));
        assertEquals(1, result.size());
    }

    @Test
    void testFindNotDeletedAll_noArg() {
        MyJpaTestEntity e1 = new MyJpaTestEntity();
        e1.setName("a1");
        e1.setDeleted(false);
        repository.save(e1);
        MyJpaTestEntity e2 = new MyJpaTestEntity();
        e2.setName("a2");
        e2.setDeleted(true);
        repository.save(e2);
        List<MyJpaTestEntity> result = repository.findNotDeletedAll();
        assertEquals(1, result.size());
    }

    @Test
    void testFindNotDeletedAll_withConsumerAndPageable() {
        MyJpaTestEntity e1 = new MyJpaTestEntity();
        e1.setName("page1");
        e1.setDeleted(false);
        repository.save(e1);
        MyJpaTestEntity e2 = new MyJpaTestEntity();
        e2.setName("page2");
        e2.setDeleted(true);
        repository.save(e2);
        Page<MyJpaTestEntity> page = repository.findNotDeletedAll(
            (Specification<MyJpaTestEntity>)(root, query, cb) -> cb.conjunction(), PageRequest.of(0, 10));
        assertEquals(1, page.getTotalElements());
    }

    @Test
    void testFindNotDeletedAll_withConsumerAndPageable_nullSpec() {
        MyJpaTestEntity e1 = new MyJpaTestEntity();
        e1.setName("pageNull");
        e1.setDeleted(false);
        repository.save(e1);
        Page<MyJpaTestEntity> page =
            repository.findNotDeletedAll((Specification<MyJpaTestEntity>)null, PageRequest.of(0, 10));
        assertEquals(1, page.getTotalElements());
    }

    @Test
    void testFindNotDeletedAll_withSort() {
        MyJpaTestEntity e1 = new MyJpaTestEntity();
        e1.setName("b");
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
    void testFindNotDeletedOne_nullSpec() {
        MyJpaTestEntity e1 = new MyJpaTestEntity();
        e1.setName("first");
        e1.setDeleted(false);
        repository.save(e1);
        var result = repository.findNotDeletedOne((Specification<MyJpaTestEntity>)null);
        assertTrue(result.isPresent());
    }

    @Test
    void testFindNotDeletedOne_withSpec() {
        MyJpaTestEntity e1 = new MyJpaTestEntity();
        e1.setName("target");
        e1.setDeleted(false);
        repository.save(e1);
        MyJpaTestEntity e2 = new MyJpaTestEntity();
        e2.setName("target");
        e2.setDeleted(true);
        repository.save(e2);
        var result = repository.findNotDeletedOne(
            (Specification<MyJpaTestEntity>)(root, query, cb) -> cb.equal(root.get("name"), "target"));
        assertTrue(result.isPresent());
        assertFalse(result.get().getDeleted());
    }

    @Test
    void testCountNotDeleted_nullSpec() {
        MyJpaTestEntity e1 = new MyJpaTestEntity();
        e1.setName("cnt");
        e1.setDeleted(false);
        repository.save(e1);
        MyJpaTestEntity e2 = new MyJpaTestEntity();
        e2.setName("cnt");
        e2.setDeleted(true);
        repository.save(e2);
        long count = repository.countNotDeleted((Specification<MyJpaTestEntity>)null);
        assertEquals(1, count);
    }

    @Test
    void testCountNotDeleted_withSpec() {
        MyJpaTestEntity e1 = new MyJpaTestEntity();
        e1.setName("cnt");
        e1.setDeleted(false);
        repository.save(e1);
        MyJpaTestEntity e2 = new MyJpaTestEntity();
        e2.setName("cnt");
        e2.setDeleted(true);
        repository.save(e2);
        long count = repository
            .countNotDeleted((Specification<MyJpaTestEntity>)(root, query, cb) -> cb.equal(root.get("name"), "cnt"));
        assertEquals(1, count);
    }

    @Test
    void testCountNotDeleted_noArg() {
        MyJpaTestEntity e1 = new MyJpaTestEntity();
        e1.setName("cntNoArg");
        e1.setDeleted(false);
        repository.save(e1);
        MyJpaTestEntity e2 = new MyJpaTestEntity();
        e2.setName("cntNoArg2");
        e2.setDeleted(true);
        repository.save(e2);
        long count = repository.countNotDeleted();
        assertEquals(1, count);
    }

    @Test
    void testFindNotDeletedById_nullId() {
        var result = repository.findNotDeletedById(null);
        assertFalse(result.isPresent());
    }

    @Test
    void testFindNotDeletedById_active() {
        MyJpaTestEntity e1 = new MyJpaTestEntity();
        e1.setName("ndById");
        e1.setDeleted(false);
        MyJpaTestEntity saved = repository.save(e1);
        var result = repository.findNotDeletedById(saved.getId());
        assertTrue(result.isPresent());
    }

    @Test
    void testFindNotDeletedById_deleted() {
        MyJpaTestEntity e1 = new MyJpaTestEntity();
        e1.setName("delById");
        e1.setDeleted(true);
        MyJpaTestEntity saved = repository.save(e1);
        var result = repository.findNotDeletedById(saved.getId());
        assertFalse(result.isPresent());
    }

    @Test
    void testSimpleEntity_findNotDeletedOne_noSoftDelete() {
        SimpleTestEntity e1 = new SimpleTestEntity();
        e1.setName("simple");
        simpleRepository.save(e1);
        var result = simpleRepository.findNotDeletedOne((Specification<SimpleTestEntity>)null);
        assertTrue(result.isPresent());
    }

    @Test
    void testSimpleEntity_findNotDeletedOne_emptyTable() {
        var result = simpleRepository.findNotDeletedOne((Specification<SimpleTestEntity>)null);
        assertFalse(result.isPresent());
    }

    @Test
    void testSimpleEntity_findNotDeletedOne_withSpec() {
        SimpleTestEntity e1 = new SimpleTestEntity();
        e1.setName("simple");
        simpleRepository.save(e1);
        var result = simpleRepository.findNotDeletedOne(
            (Specification<SimpleTestEntity>)(root, query, cb) -> cb.equal(root.get("name"), "simple"));
        assertTrue(result.isPresent());
    }

    @Test
    void testSimpleEntity_findNotDeletedAll_noSoftDelete() {
        SimpleTestEntity e1 = new SimpleTestEntity();
        e1.setName("simple");
        simpleRepository.save(e1);
        List<SimpleTestEntity> result = simpleRepository.findNotDeletedAll();
        assertTrue(result.size() >= 1);
    }

    @Test
    void testSimpleEntity_countNotDeleted_noSoftDelete() {
        SimpleTestEntity e1 = new SimpleTestEntity();
        e1.setName("simple");
        simpleRepository.save(e1);
        long count = simpleRepository.countNotDeleted();
        assertTrue(count >= 1);
    }

    @Test
    void testSimpleEntity_countNotDeleted_withSpec() {
        SimpleTestEntity e1 = new SimpleTestEntity();
        e1.setName("simple");
        simpleRepository.save(e1);
        long count = simpleRepository.countNotDeleted(
            (Specification<SimpleTestEntity>)(root, query, cb) -> cb.equal(root.get("name"), "simple"));
        assertEquals(1, count);
    }

    @Test
    void testSimpleEntity_findNotDeletedAll_withSpec() {
        SimpleTestEntity e1 = new SimpleTestEntity();
        e1.setName("simple");
        simpleRepository.save(e1);
        List<SimpleTestEntity> result =
            simpleRepository.findNotDeletedAll((Specification<SimpleTestEntity>)(root, query, cb) -> cb.conjunction());
        assertTrue(result.size() >= 1);
    }

    @Test
    void testSimpleEntity_findNotDeletedAll_nullSpec() {
        SimpleTestEntity e1 = new SimpleTestEntity();
        e1.setName("simple");
        simpleRepository.save(e1);
        List<SimpleTestEntity> result = simpleRepository.findNotDeletedAll((Specification<SimpleTestEntity>)null);
        assertTrue(result.size() >= 1);
    }

    @Test
    void testFindAll_withConsumerAndSort() {
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
}
