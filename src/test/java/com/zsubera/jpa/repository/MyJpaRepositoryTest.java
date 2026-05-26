package com.zsubera.jpa.repository;

import com.zsubera.jpa.update.SoftDeleteHelper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ContextConfiguration;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ContextConfiguration(classes = MyJpaRepositoryTest.TestConfig.class)
class MyJpaRepositoryTest {

    @SpringBootApplication
    @EntityScan(basePackageClasses = MyJpaTestEntity.class)
    @EnableJpaRepositories(basePackageClasses = MyJpaTestRepository.class)
    static class TestConfig {
    }

    @Autowired
    private MyJpaTestRepository repository;

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

        long count = repository.countNotDeleted(
                (Specification<MyJpaTestEntity>) (root, query, cb) -> cb.equal(root.get("name"), "match"));
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
                (Specification<MyJpaTestEntity>) (root, query, cb) -> cb.equal(root.get("name"), "target"));
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
                (Specification<MyJpaTestEntity>) (root, query, cb) -> cb.equal(root.get("name"), "target"));
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
                (Specification<MyJpaTestEntity>) (root, query, cb) -> cb.conjunction(),
                PageRequest.of(0, 10));
        assertEquals(2, page.getTotalElements());
    }
}
