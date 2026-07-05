package com.zsubera.jpa.update;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.zsubera.jpa.spec.TestEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaDelete;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.Test;

class DeleteSpecMockTest {

    @Test
    void toDelete_noConditionsNoUnconditional_throws() {
        assertThrows(IllegalStateException.class, () -> new DeleteSpec<>(TestEntity.class).toDelete(em()));
    }

    @Test
    void deleteAll_withoutAllowUnconditional_throws() {
        assertThrows(IllegalStateException.class, () -> new DeleteSpec<>(TestEntity.class).deleteAll(em()));
    }

    @Test
    void toDelete_withAllowUnconditional_noConditions_succeeds() {
        DeleteSpec<TestEntity> spec = new DeleteSpec<>(TestEntity.class).allowUnconditional(true);
        assertNotNull(spec.toDelete(em()));
    }

    @Test
    void allowUnconditional_chaining() {
        DeleteSpec<TestEntity> spec =
            new DeleteSpec<>(TestEntity.class).allowUnconditional(true).allowUnconditional(false);
        assertNotNull(spec);
    }

    @Test
    void executeLimited_negativeLimit_throws() {
        assertThrows(IllegalArgumentException.class, () -> new DeleteSpec<>(TestEntity.class).executeLimited(em(), -1));
    }

    @Test
    void executeLimited_zeroLimit_throws() {
        assertThrows(IllegalArgumentException.class, () -> new DeleteSpec<>(TestEntity.class).executeLimited(em(), 0));
    }

    @Test
    void getEntityClass_returnsCorrectClass() {
        assertEquals(TestEntity.class, new DeleteSpec<>(TestEntity.class).getEntityClass());
    }

    @Test
    void multiLike_emptyKeyword_noConditionAdded() {
        DeleteSpec<TestEntity> spec = new DeleteSpec<>(TestEntity.class).multiLike("", TestEntity::getName);
        assertNotNull(spec);
    }

    @Test
    void multiLike_conditionFalse_noCondition() {
        DeleteSpec<TestEntity> spec = new DeleteSpec<>(TestEntity.class).multiLike(false, "test", TestEntity::getName);
        assertNotNull(spec);
    }

    @Test
    void eqStrict_nullValue_throws() {
        assertThrows(IllegalArgumentException.class,
            () -> new DeleteSpec<>(TestEntity.class).eqStrict(TestEntity::getName, null));
    }

    @Test
    void neStrict_nullValue_throws() {
        assertThrows(IllegalArgumentException.class,
            () -> new DeleteSpec<>(TestEntity.class).neStrict(TestEntity::getName, null));
    }

    @Test
    void eqStrict_withValue_addsCondition() {
        DeleteSpec<TestEntity> spec = new DeleteSpec<>(TestEntity.class).eqStrict(TestEntity::getName, "x");
        assertNotNull(spec);
    }

    @Test
    void neStrict_withValue_addsCondition() {
        DeleteSpec<TestEntity> spec = new DeleteSpec<>(TestEntity.class).neStrict(TestEntity::getName, "x");
        assertNotNull(spec);
    }

    private EntityManager em() {
        EntityManager em = mock(EntityManager.class);
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        when(em.getCriteriaBuilder()).thenReturn(cb);
        CriteriaDelete cd = mock(CriteriaDelete.class);
        when(cb.createCriteriaDelete(any(Class.class))).thenReturn(cd);
        Root root = mock(Root.class);
        when(cd.from(any(jakarta.persistence.metamodel.EntityType.class))).thenReturn(root);
        Path path = mock(Path.class);
        when(root.get(anyString())).thenReturn(path);
        Predicate pred = mock(Predicate.class);
        when(cb.and(any(Predicate[].class))).thenReturn(pred);
        when(cb.conjunction()).thenReturn(pred);
        jakarta.persistence.Query q = mock(jakarta.persistence.Query.class);
        when(em.createQuery(any(CriteriaDelete.class))).thenReturn(q);
        when(q.executeUpdate()).thenReturn(0);
        return em;
    }
}
