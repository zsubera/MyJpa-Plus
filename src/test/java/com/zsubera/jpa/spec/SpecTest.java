package com.zsubera.jpa.spec;

import static org.junit.jupiter.api.Assertions.*;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.jpa.domain.Specification;

@DataJpaTest
@org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase(
    replace = org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE)
@org.springframework.test.context.ContextConfiguration(classes = TestApplication.class)
class SpecTest {

    @Autowired
    private TestEntityRepository repository;

    @PersistenceContext
    private EntityManager em;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        repository.flush();
    }

    private TestEntity newEntity(String name, int status) {
        TestEntity e = new TestEntity();
        e.setName(name);
        e.setStatus(status);
        return e;
    }

    // ---- all() ----

    @Test
    void allCombinesWithAnd() {
        repository.save(newEntity("active", 1));
        repository.save(newEntity("inactive", 0));

        Specification<TestEntity> spec = Spec.all((root, query, cb) -> cb.equal(root.get("name"), "active"),
            (root, query, cb) -> cb.equal(root.get("status"), 1));
        List<TestEntity> result = repository.findAll(spec);
        assertEquals(1, result.size());
        assertEquals("active", result.get(0).getName());
    }

    @Test
    void allWithThreeSpecs() {
        repository.save(newEntity("active", 1));
        repository.save(newEntity("active", 0));
        repository.save(newEntity("inactive", 1));

        Specification<TestEntity> spec = Spec.all((root, query, cb) -> cb.equal(root.get("name"), "active"),
            (root, query, cb) -> cb.equal(root.get("status"), 1), (root, query, cb) -> cb.isNotNull(root.get("name")));
        List<TestEntity> result = repository.findAll(spec);
        assertEquals(1, result.size());
    }

    @Test
    void allSingleSpecReturnsSameSpec() {
        Specification<TestEntity> single = (root, query, cb) -> cb.equal(root.get("status"), 1);
        Specification<TestEntity> result = Spec.all(single);
        assertSame(single, result);
    }

    @Test
    void allWithNullThrows() {
        assertThrows(IllegalArgumentException.class, () -> Spec.all((Specification<TestEntity>[])null));
    }

    @Test
    void allWithEmptyThrows() {
        assertThrows(IllegalArgumentException.class, () -> Spec.<TestEntity>all());
    }

    // ---- any() ----

    @Test
    void anyCombinesWithOr() {
        repository.save(newEntity("alpha", 1));
        repository.save(newEntity("beta", 2));
        repository.save(newEntity("gamma", 3));

        Specification<TestEntity> spec = Spec.any((root, query, cb) -> cb.equal(root.get("name"), "alpha"),
            (root, query, cb) -> cb.equal(root.get("name"), "beta"));
        List<TestEntity> result = repository.findAll(spec);
        assertEquals(2, result.size());
    }

    @Test
    void anyWithThreeSpecs() {
        repository.save(newEntity("a", 1));
        repository.save(newEntity("b", 2));
        repository.save(newEntity("c", 3));
        repository.save(newEntity("d", 4));

        Specification<TestEntity> spec = Spec.any((root, query, cb) -> cb.equal(root.get("name"), "a"),
            (root, query, cb) -> cb.equal(root.get("name"), "b"), (root, query, cb) -> cb.equal(root.get("name"), "c"));
        List<TestEntity> result = repository.findAll(spec);
        assertEquals(3, result.size());
    }

    @Test
    void anySingleSpecReturnsSameSpec() {
        Specification<TestEntity> single = (root, query, cb) -> cb.equal(root.get("status"), 1);
        Specification<TestEntity> result = Spec.any(single);
        assertSame(single, result);
    }

    @Test
    void anyWithNullThrows() {
        assertThrows(IllegalArgumentException.class, () -> Spec.any((Specification<TestEntity>[])null));
    }

    @Test
    void anyWithEmptyThrows() {
        assertThrows(IllegalArgumentException.class, () -> Spec.<TestEntity>any());
    }

    // ---- not() ----

    @Test
    void notNegatesSpec() {
        repository.save(newEntity("active", 1));
        repository.save(newEntity("inactive", 0));

        Specification<TestEntity> active = (root, query, cb) -> cb.equal(root.get("status"), 1);
        Specification<TestEntity> notActive = Spec.not(active);

        List<TestEntity> result = repository.findAll(notActive);
        assertEquals(1, result.size());
        assertEquals("inactive", result.get(0).getName());
    }

    @Test
    void notWithNullThrows() {
        assertThrows(IllegalArgumentException.class, () -> Spec.not(null));
    }

    // ---- always() ----

    @Test
    void alwaysReturnsAll() {
        repository.save(newEntity("a", 1));
        repository.save(newEntity("b", 2));

        List<TestEntity> result = repository.findAll(Spec.always());
        assertEquals(2, result.size());
    }

    @Test
    void alwaysOnEmptyTable() {
        List<TestEntity> result = repository.findAll(Spec.always());
        assertEquals(0, result.size());
    }

    // ---- never() ----

    @Test
    void neverReturnsNone() {
        repository.save(newEntity("a", 1));
        repository.save(newEntity("b", 2));

        List<TestEntity> result = repository.findAll(Spec.never());
        assertEquals(0, result.size());
    }

    // ---- when() ----

    @Test
    void whenConditionTrueAppliesSpec() {
        repository.save(newEntity("active", 1));
        repository.save(newEntity("inactive", 0));

        Specification<TestEntity> active = (root, query, cb) -> cb.equal(root.get("status"), 1);
        Specification<TestEntity> result = Spec.when(true, active);

        List<TestEntity> found = repository.findAll(result);
        assertEquals(1, found.size());
    }

    @Test
    void whenConditionFalseReturnsAlways() {
        repository.save(newEntity("active", 1));
        repository.save(newEntity("inactive", 0));

        Specification<TestEntity> active = (root, query, cb) -> cb.equal(root.get("status"), 1);
        Specification<TestEntity> result = Spec.when(false, active);

        List<TestEntity> found = repository.findAll(result);
        assertEquals(2, found.size());
    }

    @Test
    void whenWithNullSpecThrows() {
        assertThrows(IllegalArgumentException.class, () -> Spec.when(true, null));
    }

    // ---- combination ----

    @Test
    void allWithAnyAndNot() {
        repository.save(newEntity("admin", 1));
        repository.save(newEntity("user", 1));
        repository.save(newEntity("deleted", 0));

        Specification<TestEntity> spec = Spec.all(
            Spec.any((root, query, cb) -> cb.equal(root.get("name"), "admin"),
                (root, query, cb) -> cb.equal(root.get("name"), "user")),
            Spec.not((root, query, cb) -> cb.equal(root.get("status"), 0)));

        List<TestEntity> result = repository.findAll(spec);
        assertEquals(2, result.size());
    }

    @Test
    void whenWithAllForDynamicQuery() {
        repository.save(newEntity("active", 1));
        repository.save(newEntity("inactive", 0));

        String nameFilter = null;
        Integer statusFilter = 1;

        Specification<TestEntity> spec = Spec.all(
            Spec.when(nameFilter != null, (root, query, cb) -> cb.like(root.get("name"), "%" + nameFilter + "%")),
            Spec.when(statusFilter != null, (root, query, cb) -> cb.equal(root.get("status"), statusFilter)));

        List<TestEntity> result = repository.findAll(spec);
        assertEquals(1, result.size());
        assertEquals("active", result.get(0).getName());
    }
}
