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

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class AggregateHelperTest {

    @Autowired
    private jakarta.persistence.EntityManager em;

    @Test
    void count_withRoot_returnsExpression() {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<TestEntity> cq = cb.createQuery(TestEntity.class);
        Root<TestEntity> root = cq.from(TestEntity.class);

        Expression<Long> expr = AggregateHelper.count(root, cb);
        assertNotNull(expr);
    }

    @Test
    void count_withField_returnsExpression() {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<TestEntity> cq = cb.createQuery(TestEntity.class);
        Root<TestEntity> root = cq.from(TestEntity.class);

        Expression<Long> expr = AggregateHelper.count(root, TestEntity::getName, cb);
        assertNotNull(expr);
    }

    @Test
    void countDistinct_withField_returnsExpression() {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<TestEntity> cq = cb.createQuery(TestEntity.class);
        Root<TestEntity> root = cq.from(TestEntity.class);

        Expression<Long> expr = AggregateHelper.countDistinct(root, TestEntity::getName, cb);
        assertNotNull(expr);
    }

    @Test
    void sum_withField_returnsExpression() {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<TestEntity> cq = cb.createQuery(TestEntity.class);
        Root<TestEntity> root = cq.from(TestEntity.class);

        Expression<? extends Number> expr = AggregateHelper.sum(root, TestEntity::getStatus, cb);
        assertNotNull(expr);
    }

    @Test
    void avg_withField_returnsExpression() {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<TestEntity> cq = cb.createQuery(TestEntity.class);
        Root<TestEntity> root = cq.from(TestEntity.class);

        Expression<Double> expr = AggregateHelper.avg(root, TestEntity::getStatus, cb);
        assertNotNull(expr);
    }

    @Test
    void max_withField_returnsExpression() {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<TestEntity> cq = cb.createQuery(TestEntity.class);
        Root<TestEntity> root = cq.from(TestEntity.class);

        Expression<?> expr = AggregateHelper.max(root, TestEntity::getStatus, cb);
        assertNotNull(expr);
    }

    @Test
    void min_withField_returnsExpression() {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<TestEntity> cq = cb.createQuery(TestEntity.class);
        Root<TestEntity> root = cq.from(TestEntity.class);

        Expression<?> expr = AggregateHelper.min(root, TestEntity::getStatus, cb);
        assertNotNull(expr);
    }

    @Test
    void count_nullRoot_throws() {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        assertThrows(IllegalArgumentException.class, () -> AggregateHelper.count(null, cb));
    }

    @Test
    void count_nullCb_throws() {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<TestEntity> cq = cb.createQuery(TestEntity.class);
        Root<TestEntity> root = cq.from(TestEntity.class);
        assertThrows(IllegalArgumentException.class, () -> AggregateHelper.count(root, null));
    }

    @Test
    void count_withField_nullRoot_throws() {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        assertThrows(IllegalArgumentException.class, () -> AggregateHelper.count(null, TestEntity::getName, cb));
    }

    @Test
    void count_withField_nullField_throws() {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<TestEntity> cq = cb.createQuery(TestEntity.class);
        Root<TestEntity> root = cq.from(TestEntity.class);
        assertThrows(IllegalArgumentException.class, () -> AggregateHelper.count(root, null, cb));
    }

    @Test
    void sum_nullField_throws() {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<TestEntity> cq = cb.createQuery(TestEntity.class);
        Root<TestEntity> root = cq.from(TestEntity.class);
        assertThrows(IllegalArgumentException.class, () -> AggregateHelper.sum(root, null, cb));
    }

    @Test
    void avg_nullField_throws() {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<TestEntity> cq = cb.createQuery(TestEntity.class);
        Root<TestEntity> root = cq.from(TestEntity.class);
        assertThrows(IllegalArgumentException.class, () -> AggregateHelper.avg(root, null, cb));
    }

    @Test
    void max_nullField_throws() {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<TestEntity> cq = cb.createQuery(TestEntity.class);
        Root<TestEntity> root = cq.from(TestEntity.class);
        assertThrows(IllegalArgumentException.class, () -> AggregateHelper.max(root, null, cb));
    }

    @Test
    void min_nullField_throws() {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<TestEntity> cq = cb.createQuery(TestEntity.class);
        Root<TestEntity> root = cq.from(TestEntity.class);
        assertThrows(IllegalArgumentException.class, () -> AggregateHelper.min(root, null, cb));
    }

    @Test
    void validateHavingOperator_supportedOps() {
        assertDoesNotThrow(() -> AggregateHelper.validateHavingOperator(ConditionNode.Op.GT));
        assertDoesNotThrow(() -> AggregateHelper.validateHavingOperator(ConditionNode.Op.GE));
        assertDoesNotThrow(() -> AggregateHelper.validateHavingOperator(ConditionNode.Op.LT));
        assertDoesNotThrow(() -> AggregateHelper.validateHavingOperator(ConditionNode.Op.LE));
        assertDoesNotThrow(() -> AggregateHelper.validateHavingOperator(ConditionNode.Op.EQ));
        assertDoesNotThrow(() -> AggregateHelper.validateHavingOperator(ConditionNode.Op.NE));
    }

    @Test
    void validateHavingOperator_unsupportedOp_throws() {
        assertThrows(IllegalArgumentException.class,
            () -> AggregateHelper.validateHavingOperator(ConditionNode.Op.LIKE));
        assertThrows(IllegalArgumentException.class, () -> AggregateHelper.validateHavingOperator(ConditionNode.Op.IN));
        assertThrows(IllegalArgumentException.class,
            () -> AggregateHelper.validateHavingOperator(ConditionNode.Op.BETWEEN));
    }

    @Test
    void validateHavingOperator_null_throws() {
        assertThrows(IllegalArgumentException.class, () -> AggregateHelper.validateHavingOperator(null));
    }

    @Test
    void compareExpression_allOperators() {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<TestEntity> cq = cb.createQuery(TestEntity.class);
        Root<TestEntity> root = cq.from(TestEntity.class);
        Expression<Number> expr = root.get("status");

        assertDoesNotThrow(() -> AggregateHelper.compareExpression(cb, expr, ConditionNode.Op.GT, 5));
        assertDoesNotThrow(() -> AggregateHelper.compareExpression(cb, expr, ConditionNode.Op.GE, 5));
        assertDoesNotThrow(() -> AggregateHelper.compareExpression(cb, expr, ConditionNode.Op.LT, 5));
        assertDoesNotThrow(() -> AggregateHelper.compareExpression(cb, expr, ConditionNode.Op.LE, 5));
        assertDoesNotThrow(() -> AggregateHelper.compareExpression(cb, expr, ConditionNode.Op.EQ, 5));
        assertDoesNotThrow(() -> AggregateHelper.compareExpression(cb, expr, ConditionNode.Op.NE, 5));
    }

    @Test
    void compareExpression_unsupportedOp_throws() {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<TestEntity> cq = cb.createQuery(TestEntity.class);
        Root<TestEntity> root = cq.from(TestEntity.class);
        Expression<Number> expr = root.get("status");

        assertThrows(IllegalArgumentException.class,
            () -> AggregateHelper.compareExpression(cb, expr, ConditionNode.Op.LIKE, 5));
    }

    @Test
    void compareComparable_allOperators() {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<TestEntity> cq = cb.createQuery(TestEntity.class);
        Root<TestEntity> root = cq.from(TestEntity.class);
        Expression<String> expr = root.get("name");

        assertDoesNotThrow(() -> AggregateHelper.compareComparable(cb, expr, ConditionNode.Op.GT, "test"));
        assertDoesNotThrow(() -> AggregateHelper.compareComparable(cb, expr, ConditionNode.Op.GE, "test"));
        assertDoesNotThrow(() -> AggregateHelper.compareComparable(cb, expr, ConditionNode.Op.LT, "test"));
        assertDoesNotThrow(() -> AggregateHelper.compareComparable(cb, expr, ConditionNode.Op.LE, "test"));
        assertDoesNotThrow(() -> AggregateHelper.compareComparable(cb, expr, ConditionNode.Op.EQ, "test"));
        assertDoesNotThrow(() -> AggregateHelper.compareComparable(cb, expr, ConditionNode.Op.NE, "test"));
    }

    @Test
    void compareComparable_unsupportedOp_throws() {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<TestEntity> cq = cb.createQuery(TestEntity.class);
        Root<TestEntity> root = cq.from(TestEntity.class);
        Expression<String> expr = root.get("name");

        assertThrows(IllegalArgumentException.class,
            () -> AggregateHelper.compareComparable(cb, expr, ConditionNode.Op.LIKE, "test"));
    }

    @jakarta.persistence.Entity
    @jakarta.persistence.Table(name = "agg_test_entity")
    static class TestEntity {
        @jakarta.persistence.Id
        @jakarta.persistence.GeneratedValue
        private Long id;
        private String name;
        private Integer status;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Integer getStatus() {
            return status;
        }

        public void setStatus(Integer status) {
            this.status = status;
        }
    }
}
