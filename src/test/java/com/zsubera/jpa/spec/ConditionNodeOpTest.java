package com.zsubera.jpa.spec;

import static org.junit.jupiter.api.Assertions.*;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.test.context.ContextConfiguration;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = TestApplication.class)
class ConditionNodeOpTest {

    @PersistenceContext
    private EntityManager em;

    private CriteriaBuilder cb;
    private Root<TestEntity> root;

    @BeforeEach
    void setUp() {
        cb = em.getCriteriaBuilder();
        CriteriaQuery<TestEntity> cq = cb.createQuery(TestEntity.class);
        root = cq.from(TestEntity.class);
    }

    @Test
    void resolveGt_nullValue_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> ConditionNode.Op.GT.resolve(root, "status", null, '\0', cb));
    }

    @Test
    void resolveGe_nullValue_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> ConditionNode.Op.GE.resolve(root, "status", null, '\0', cb));
    }

    @Test
    void resolveLt_nullValue_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> ConditionNode.Op.LT.resolve(root, "status", null, '\0', cb));
    }

    @Test
    void resolveLe_nullValue_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> ConditionNode.Op.LE.resolve(root, "status", null, '\0', cb));
    }

    @Test
    void resolveBetween_nullValue_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
            () -> ConditionNode.Op.BETWEEN.resolve(root, "status", null, '\0', cb));
    }

    @Test
    void resolveNotBetween_nullValue_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
            () -> ConditionNode.Op.NOT_BETWEEN.resolve(root, "status", null, '\0', cb));
    }

    @Test
    void resolveIn_intArray_succeeds() {
        assertDoesNotThrow(() -> ConditionNode.Op.IN.resolve(root, "status", new int[] {1, 2, 3}, '\0', cb));
    }

    @Test
    void resolveIn_longArray_succeeds() {
        assertDoesNotThrow(() -> ConditionNode.Op.IN.resolve(root, "status", new long[] {1L, 2L}, '\0', cb));
    }

    @Test
    void resolveNotIn_intArray_succeeds() {
        assertDoesNotThrow(() -> ConditionNode.Op.NOT_IN.resolve(root, "status", new int[] {1, 2}, '\0', cb));
    }

    @Test
    void resolveNotIn_longArray_succeeds() {
        assertDoesNotThrow(() -> ConditionNode.Op.NOT_IN.resolve(root, "status", new long[] {10L, 20L}, '\0', cb));
    }

    @Test
    void resolveIn_emptyIntArray_returnsDisjunction() {
        var predicate = ConditionNode.Op.IN.resolve(root, "status", new int[] {}, '\0', cb);
        assertNotNull(predicate);
    }

    @Test
    void resolveNotIn_emptyIntArray_returnsConjunction() {
        var predicate = ConditionNode.Op.NOT_IN.resolve(root, "status", new int[] {}, '\0', cb);
        assertNotNull(predicate);
    }

    @Test
    void resolveGt_validValue_succeeds() {
        assertDoesNotThrow(() -> ConditionNode.Op.GT.resolve(root, "status", 5, '\0', cb));
    }

    @Test
    void resolveGe_validValue_succeeds() {
        assertDoesNotThrow(() -> ConditionNode.Op.GE.resolve(root, "status", 5, '\0', cb));
    }

    @Test
    void resolveLt_validValue_succeeds() {
        assertDoesNotThrow(() -> ConditionNode.Op.LT.resolve(root, "status", 5, '\0', cb));
    }

    @Test
    void resolveLe_validValue_succeeds() {
        assertDoesNotThrow(() -> ConditionNode.Op.LE.resolve(root, "status", 5, '\0', cb));
    }

    @Test
    void resolveLike_nullValue_throws() {
        assertThrows(IllegalArgumentException.class, () -> ConditionNode.Op.LIKE.resolve(root, "name", null, '\0', cb));
    }

    @Test
    void resolveNotLike_nullValue_throws() {
        assertThrows(IllegalArgumentException.class,
            () -> ConditionNode.Op.NOT_LIKE.resolve(root, "name", null, '\0', cb));
    }

    @Test
    void resolveEqIgnoreCase_nullValue_producesIsNull() {
        Predicate p = ConditionNode.Op.EQ_IGNORE_CASE.resolve(root, "name", null, '\0', cb);
        assertNotNull(p);
    }

    @Test
    void resolveNeIgnoreCase_nullValue_producesIsNotNull() {
        Predicate p = ConditionNode.Op.NE_IGNORE_CASE.resolve(root, "name", null, '\0', cb);
        assertNotNull(p);
    }

    @Test
    void resolveLikeIgnoreCase_nullValue_throws() {
        assertThrows(IllegalArgumentException.class,
            () -> ConditionNode.Op.LIKE_IGNORE_CASE.resolve(root, "name", null, '\0', cb));
    }
}
