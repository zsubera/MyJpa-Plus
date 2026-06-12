package com.zsubera.jpa.util;

import static org.junit.jupiter.api.Assertions.*;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ContextConfiguration;
import com.zsubera.jpa.spec.TestApplication;
import com.zsubera.jpa.spec.TestEntity;

@DataJpaTest
@org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase(
    replace = org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = TestApplication.class)
class InClauseBuilderTest {

    @PersistenceContext
    private EntityManager em;

    @BeforeEach
    void setUp() {
        em.createQuery("DELETE FROM testEntity").executeUpdate();
        em.flush();
    }

    @Test
    void in_array_normalValues() {
        persistEntity("a", 1);
        persistEntity("b", 2);
        persistEntity("c", 3);
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<TestEntity> cq = cb.createQuery(TestEntity.class);
        Root<TestEntity> root = cq.from(TestEntity.class);
        Predicate predicate = InClauseBuilder.in(cb, root.get("status"), 1, 3);
        cq.where(predicate);
        List<TestEntity> result = em.createQuery(cq).getResultList();
        assertEquals(2, result.size());
    }

    @Test
    void in_array_null_throwsException() {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<TestEntity> cq = cb.createQuery(TestEntity.class);
        Root<TestEntity> root = cq.from(TestEntity.class);
        assertThrows(IllegalArgumentException.class, () -> InClauseBuilder.in(cb, root.get("status"), (Object[])null));
    }

    @Test
    void in_array_empty_throwsException() {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<TestEntity> cq = cb.createQuery(TestEntity.class);
        Root<TestEntity> root = cq.from(TestEntity.class);
        assertThrows(IllegalArgumentException.class, () -> InClauseBuilder.in(cb, root.get("status"), new Object[0]));
    }

    @Test
    void in_collection_normalValues() {
        persistEntity("a", 1);
        persistEntity("b", 2);
        persistEntity("c", 3);
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<TestEntity> cq = cb.createQuery(TestEntity.class);
        Root<TestEntity> root = cq.from(TestEntity.class);
        Collection<Integer> values = Arrays.asList(1, 3);
        Predicate predicate = InClauseBuilder.in(cb, root.get("status"), values);
        cq.where(predicate);
        List<TestEntity> result = em.createQuery(cq).getResultList();
        assertEquals(2, result.size());
    }

    @Test
    void in_collection_null_throwsException() {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<TestEntity> cq = cb.createQuery(TestEntity.class);
        Root<TestEntity> root = cq.from(TestEntity.class);
        assertThrows(IllegalArgumentException.class,
            () -> InClauseBuilder.in(cb, root.get("status"), (Collection<?>)null));
    }

    @Test
    void in_collection_empty_throwsException() {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<TestEntity> cq = cb.createQuery(TestEntity.class);
        Root<TestEntity> root = cq.from(TestEntity.class);
        assertThrows(IllegalArgumentException.class,
            () -> InClauseBuilder.in(cb, root.get("status"), new ArrayList<>()));
    }

    @Test
    void notIn_array_normalValues() {
        persistEntity("a", 1);
        persistEntity("b", 2);
        persistEntity("c", 3);
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<TestEntity> cq = cb.createQuery(TestEntity.class);
        Root<TestEntity> root = cq.from(TestEntity.class);
        Predicate predicate = InClauseBuilder.notIn(cb, root.get("status"), 1, 3);
        cq.where(predicate);
        List<TestEntity> result = em.createQuery(cq).getResultList();
        assertEquals(1, result.size());
        assertEquals(2, result.get(0).getStatus());
    }

    @Test
    void notIn_array_null_throwsException() {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<TestEntity> cq = cb.createQuery(TestEntity.class);
        Root<TestEntity> root = cq.from(TestEntity.class);
        assertThrows(IllegalArgumentException.class,
            () -> InClauseBuilder.notIn(cb, root.get("status"), (Object[])null));
    }

    @Test
    void notIn_array_empty_throwsException() {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<TestEntity> cq = cb.createQuery(TestEntity.class);
        Root<TestEntity> root = cq.from(TestEntity.class);
        assertThrows(IllegalArgumentException.class,
            () -> InClauseBuilder.notIn(cb, root.get("status"), new Object[0]));
    }

    @Test
    void notIn_collection_normalValues() {
        persistEntity("a", 1);
        persistEntity("b", 2);
        persistEntity("c", 3);
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<TestEntity> cq = cb.createQuery(TestEntity.class);
        Root<TestEntity> root = cq.from(TestEntity.class);
        Collection<Integer> values = Arrays.asList(1, 3);
        Predicate predicate = InClauseBuilder.notIn(cb, root.get("status"), values);
        cq.where(predicate);
        List<TestEntity> result = em.createQuery(cq).getResultList();
        assertEquals(1, result.size());
        assertEquals(2, result.get(0).getStatus());
    }

    @Test
    void notIn_collection_null_throwsException() {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<TestEntity> cq = cb.createQuery(TestEntity.class);
        Root<TestEntity> root = cq.from(TestEntity.class);
        assertThrows(IllegalArgumentException.class,
            () -> InClauseBuilder.notIn(cb, root.get("status"), (Collection<?>)null));
    }

    @Test
    void notIn_collection_empty_throwsException() {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<TestEntity> cq = cb.createQuery(TestEntity.class);
        Root<TestEntity> root = cq.from(TestEntity.class);
        assertThrows(IllegalArgumentException.class,
            () -> InClauseBuilder.notIn(cb, root.get("status"), new ArrayList<>()));
    }

    @Test
    void in_largeClause_batchedCorrectly() {
        persistEntity("target", 999);
        persistEntity("other", 0);

        int totalValues = InClauseBuilder.getMaxInClauseSize() + 100;
        List<Integer> values = new ArrayList<>();
        for (int i = 0; i < totalValues; i++) {
            values.add(i);
        }

        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<TestEntity> cq = cb.createQuery(TestEntity.class);
        Root<TestEntity> root = cq.from(TestEntity.class);
        Predicate predicate = InClauseBuilder.in(cb, root.get("status"), values);
        cq.where(predicate);
        List<TestEntity> result = em.createQuery(cq).getResultList();
        assertEquals(2, result.size());
    }

    @Test
    void notIn_largeClause_batchedCorrectly() {
        persistEntity("target", 999);
        persistEntity("excluded", 500);

        int totalValues = InClauseBuilder.getMaxInClauseSize() + 100;
        List<Integer> values = new ArrayList<>();
        for (int i = 0; i < totalValues; i++) {
            values.add(i);
        }

        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<TestEntity> cq = cb.createQuery(TestEntity.class);
        Root<TestEntity> root = cq.from(TestEntity.class);
        Predicate predicate = InClauseBuilder.notIn(cb, root.get("status"), values);
        cq.where(predicate);
        List<TestEntity> result = em.createQuery(cq).getResultList();
        assertEquals(0, result.size());
    }

    private void persistEntity(String name, int status) {
        TestEntity entity = new TestEntity();
        entity.setName(name);
        entity.setStatus(status);
        em.persist(entity);
    }
}
