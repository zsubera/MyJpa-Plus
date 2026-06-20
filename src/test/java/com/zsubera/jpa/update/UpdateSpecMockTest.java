package com.zsubera.jpa.update;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.zsubera.jpa.spec.TestEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.CriteriaUpdate;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.Test;

class UpdateSpecMockTest {

    @Test
    void set_nullField_throws() {
        assertThrows(IllegalArgumentException.class, () -> new UpdateSpec<>(TestEntity.class).set(null, "x"));
    }

    @Test
    void setAdd_nullField_throws() {
        assertThrows(IllegalArgumentException.class, () -> new UpdateSpec<>(TestEntity.class).setAdd(null, 5));
    }

    @Test
    void setAdd_nullAmount_throws() {
        assertThrows(IllegalArgumentException.class,
            () -> new UpdateSpec<>(TestEntity.class).setAdd(TestEntity::getStatus, null));
    }

    @Test
    void setSubtract_nullField_throws() {
        assertThrows(IllegalArgumentException.class, () -> new UpdateSpec<>(TestEntity.class).setSubtract(null, 5));
    }

    @Test
    void setSubtract_nullAmount_throws() {
        assertThrows(IllegalArgumentException.class,
            () -> new UpdateSpec<>(TestEntity.class).setSubtract(TestEntity::getStatus, null));
    }

    @Test
    void setAdd_nonNumericField_throws() {
        assertThrows(IllegalArgumentException.class,
            () -> new UpdateSpec<>(TestEntity.class).setAdd(TestEntity::getName, 5));
    }

    @Test
    void setSubtract_nonNumericField_throws() {
        assertThrows(IllegalArgumentException.class,
            () -> new UpdateSpec<>(TestEntity.class).setSubtract(TestEntity::getName, 5));
    }

    @Test
    void set_conditionalTrue_addsClause() {
        UpdateSpec<TestEntity> spec = new UpdateSpec<>(TestEntity.class).set(true, TestEntity::getName, "x");
        assertNotNull(spec);
    }

    @Test
    void set_conditionalFalse_skipsClause() {
        UpdateSpec<TestEntity> spec = new UpdateSpec<>(TestEntity.class).set(false, TestEntity::getName, "x");
        assertNotNull(spec);
    }

    @Test
    void withVersionIncrement_chaining() {
        UpdateSpec<TestEntity> spec = new UpdateSpec<>(TestEntity.class).set(TestEntity::getName, "x")
            .withVersionIncrement(true).withVersionIncrement(false);
        assertNotNull(spec);
    }

    @Test
    void allowUnconditional_false_explicit() {
        assertThrows(IllegalStateException.class, () -> new UpdateSpec<>(TestEntity.class).set(TestEntity::getName, "x")
            .allowUnconditional(false).updateAll(em()));
    }

    @Test
    void updateAll_withoutAllowUnconditional_throws() {
        assertThrows(IllegalStateException.class,
            () -> new UpdateSpec<>(TestEntity.class).set(TestEntity::getName, "x").updateAll(em()));
    }

    @Test
    void updateAll_noSetClauses_throws() {
        assertThrows(IllegalStateException.class,
            () -> new UpdateSpec<>(TestEntity.class).allowUnconditional(true).updateAll(em()));
    }

    @Test
    void toUpdate_noSetClauses_throws() {
        assertThrows(IllegalStateException.class,
            () -> new UpdateSpec<>(TestEntity.class).eq(TestEntity::getName, "x").toUpdate(em()));
    }

    @Test
    void toUpdate_noConditionsNoUnconditional_throws() {
        assertThrows(IllegalStateException.class,
            () -> new UpdateSpec<>(TestEntity.class).set(TestEntity::getName, "x").toUpdate(em()));
    }

    @Test
    void executeLimited_noSetClauses_throws() {
        assertThrows(IllegalStateException.class, () -> new UpdateSpec<>(TestEntity.class).executeLimited(em(), 10));
    }

    @Test
    void executeLimited_negativeLimit_throws() {
        assertThrows(IllegalArgumentException.class,
            () -> new UpdateSpec<>(TestEntity.class).set(TestEntity::getName, "x").executeLimited(em(), -1));
    }

    @Test
    void executeLimited_zeroLimit_throws() {
        assertThrows(IllegalArgumentException.class,
            () -> new UpdateSpec<>(TestEntity.class).set(TestEntity::getName, "x").executeLimited(em(), 0));
    }

    @Test
    void getEntityClass_returnsCorrectClass() {
        assertEquals(TestEntity.class, new UpdateSpec<>(TestEntity.class).getEntityClass());
    }

    @Test
    void multiLike_emptyKeyword_noCondition() {
        UpdateSpec<TestEntity> spec =
            new UpdateSpec<>(TestEntity.class).set(TestEntity::getName, "x").multiLike("", TestEntity::getName);
        assertNotNull(spec);
    }

    @Test
    void multiLike_conditionFalse_noCondition() {
        UpdateSpec<TestEntity> spec = new UpdateSpec<>(TestEntity.class).set(TestEntity::getName, "x").multiLike(false,
            "test", TestEntity::getName);
        assertNotNull(spec);
    }

    @Test
    void eqStrict_nullValue_throws() {
        assertThrows(IllegalArgumentException.class,
            () -> new UpdateSpec<>(TestEntity.class).set(TestEntity::getName, "x").eqStrict(TestEntity::getName, null));
    }

    @Test
    void neStrict_nullValue_throws() {
        assertThrows(IllegalArgumentException.class,
            () -> new UpdateSpec<>(TestEntity.class).set(TestEntity::getName, "x").neStrict(TestEntity::getName, null));
    }

    @Test
    void eqStrict_conditionFalse() {
        UpdateSpec<TestEntity> spec = new UpdateSpec<>(TestEntity.class).set(TestEntity::getName, "x").eqStrict(false,
            TestEntity::getName, "target");
        assertNotNull(spec);
    }

    @Test
    void neStrict_conditionFalse() {
        UpdateSpec<TestEntity> spec = new UpdateSpec<>(TestEntity.class).set(TestEntity::getName, "x").neStrict(false,
            TestEntity::getName, "target");
        assertNotNull(spec);
    }

    private EntityManager em() {
        EntityManager em = mock(EntityManager.class);
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        when(em.getCriteriaBuilder()).thenReturn(cb);
        CriteriaUpdate cu = mock(CriteriaUpdate.class);
        when(cb.createCriteriaUpdate(any(Class.class))).thenReturn(cu);
        Root root = mock(Root.class);
        when(cu.from(any(Class.class))).thenReturn(root);
        Path path = mock(Path.class);
        when(root.get(anyString())).thenReturn(path);
        Predicate pred = mock(Predicate.class);
        when(cb.and(any(Predicate[].class))).thenReturn(pred);
        when(cb.conjunction()).thenReturn(pred);
        jakarta.persistence.TypedQuery tq = mock(jakarta.persistence.TypedQuery.class);
        when(em.createQuery(any(CriteriaUpdate.class))).thenReturn(tq);
        when(tq.executeUpdate()).thenReturn(0);
        jakarta.persistence.TypedQuery tq2 = mock(jakarta.persistence.TypedQuery.class);
        when(em.createQuery(any(CriteriaUpdate.class))).thenReturn(tq);
        when(cb.createQuery(Long.class)).thenReturn(mock(CriteriaQuery.class));
        return em;
    }
}
