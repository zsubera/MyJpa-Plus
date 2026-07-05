package com.zsubera.jpa.spec;

import static org.junit.jupiter.api.Assertions.*;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;

/**
 * Tests for {@link QueryAggregates} — static aggregate helper methods.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@org.springframework.test.context.ContextConfiguration(classes = TestApplication.class)
class QueryAggregatesTest {

    @Autowired
    private jakarta.persistence.EntityManager em;

    @Test
    void count_withRoot_returnsExpression() {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<TestEntity> cq = cb.createQuery(TestEntity.class);
        Root<TestEntity> root = cq.from(TestEntity.class);
        assertNotNull(QueryAggregates.count(root, cb));
    }

    @Test
    void count_withField_returnsExpression() {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<TestEntity> cq = cb.createQuery(TestEntity.class);
        Root<TestEntity> root = cq.from(TestEntity.class);
        assertNotNull(QueryAggregates.count(root, TestEntity::getName, cb));
    }

    @Test
    void countDistinct_returnsExpression() {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<TestEntity> cq = cb.createQuery(TestEntity.class);
        Root<TestEntity> root = cq.from(TestEntity.class);
        assertNotNull(QueryAggregates.countDistinct(root, TestEntity::getName, cb));
    }

    @Test
    void sum_returnsExpression() {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<TestEntity> cq = cb.createQuery(TestEntity.class);
        Root<TestEntity> root = cq.from(TestEntity.class);
        assertNotNull(QueryAggregates.sum(root, TestEntity::getStatus, cb));
    }

    @Test
    void avg_returnsExpression() {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<TestEntity> cq = cb.createQuery(TestEntity.class);
        Root<TestEntity> root = cq.from(TestEntity.class);
        assertNotNull(QueryAggregates.avg(root, TestEntity::getStatus, cb));
    }

    @Test
    void max_returnsExpression() {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<TestEntity> cq = cb.createQuery(TestEntity.class);
        Root<TestEntity> root = cq.from(TestEntity.class);
        assertNotNull(QueryAggregates.max(root, TestEntity::getStatus, cb));
    }

    @Test
    void min_returnsExpression() {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<TestEntity> cq = cb.createQuery(TestEntity.class);
        Root<TestEntity> root = cq.from(TestEntity.class);
        assertNotNull(QueryAggregates.min(root, TestEntity::getStatus, cb));
    }

    @Test
    void delegates_toQuerySpecStaticMethods() {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<TestEntity> cq = cb.createQuery(TestEntity.class);
        Root<TestEntity> root = cq.from(TestEntity.class);

        Expression<Long> qaExpr = QueryAggregates.count(root, cb);
        assertNotNull(qaExpr);
    }
}
