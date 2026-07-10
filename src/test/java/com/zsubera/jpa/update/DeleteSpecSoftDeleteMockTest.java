package com.zsubera.jpa.update;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.zsubera.jpa.spec.TestEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaDelete;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.CriteriaUpdate;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.metamodel.EntityType;
import org.junit.jupiter.api.Test;

/**
 * Mock-based tests for {@link DeleteSpec#executeAsSoftDelete}.
 */
class DeleteSpecSoftDeleteMockTest {

    @Test
    void executeAsSoftDelete_noConditionsNoUnconditional_throws() {
        DeleteSpec<TestEntity> spec = new DeleteSpec<>(TestEntity.class);
        assertThrows(IllegalStateException.class, () -> spec.executeAsSoftDelete(em(), "deleted", true));
    }

    @Test
    void executeAsSoftDelete_withAllowUnconditional_noConditions_succeeds() {
        DeleteSpec<TestEntity> spec = new DeleteSpec<>(TestEntity.class).allowUnconditional(true);
        EntityManager em = em();
        when(em.createQuery(any(CriteriaUpdate.class))).thenReturn(mock(jakarta.persistence.Query.class));
        int affected = spec.executeAsSoftDelete(em, "deleted", true);
        assertEquals(0, affected);
    }

    @Test
    void executeAsSoftDelete_withRawCondition_succeeds() {
        DeleteSpec<TestEntity> spec = new DeleteSpec<>(TestEntity.class);
        spec.addCondition((root, cb) -> cb.conjunction());
        jakarta.persistence.Query q = mock(jakarta.persistence.Query.class);
        when(q.executeUpdate()).thenReturn(3);
        EntityManager em = em();
        when(em.createQuery(any(CriteriaUpdate.class))).thenReturn(q);
        int affected = spec.executeAsSoftDelete(em, "deleted", true);
        assertEquals(3, affected);
    }

    @Test
    void executeAsSoftDelete_flushesWhenAffected() {
        DeleteSpec<TestEntity> spec = new DeleteSpec<>(TestEntity.class).allowUnconditional(true);
        jakarta.persistence.Query q = mock(jakarta.persistence.Query.class);
        when(q.executeUpdate()).thenReturn(3);
        EntityManager em = em();
        when(em.createQuery(any(CriteriaUpdate.class))).thenReturn(q);
        spec.executeAsSoftDelete(em, "deleted", true);
        // executeAsSoftDelete flushes and clears persistence context when affected > 0
        verify(em).flush();
        verify(em, atLeastOnce()).clear();
    }

    @Test
    void executeAsSoftDelete_noFlushWhenZeroAffected() {
        DeleteSpec<TestEntity> spec = new DeleteSpec<>(TestEntity.class).allowUnconditional(true);
        jakarta.persistence.Query q = mock(jakarta.persistence.Query.class);
        when(q.executeUpdate()).thenReturn(0);
        EntityManager em = em();
        when(em.createQuery(any(CriteriaUpdate.class))).thenReturn(q);
        spec.executeAsSoftDelete(em, "deleted", true);
        verify(em, never()).flush();
    }

    @Test
    void executeAsSoftDelete_withIntegerDeletedValue() {
        DeleteSpec<TestEntity> spec = new DeleteSpec<>(TestEntity.class);
        spec.addCondition((root, cb) -> cb.conjunction());
        jakarta.persistence.Query q = mock(jakarta.persistence.Query.class);
        when(q.executeUpdate()).thenReturn(1);
        EntityManager em = em();
        when(em.createQuery(any(CriteriaUpdate.class))).thenReturn(q);
        int affected = spec.executeAsSoftDelete(em, "deleted", 1);
        assertEquals(1, affected);
    }

    @Test
    void executeAsSoftDelete_withStringDeletedValue() {
        DeleteSpec<TestEntity> spec = new DeleteSpec<>(TestEntity.class);
        spec.addCondition((root, cb) -> cb.conjunction());
        jakarta.persistence.Query q = mock(jakarta.persistence.Query.class);
        when(q.executeUpdate()).thenReturn(1);
        EntityManager em = em();
        when(em.createQuery(any(CriteriaUpdate.class))).thenReturn(q);
        int affected = spec.executeAsSoftDelete(em, "deleted", "Y");
        assertEquals(1, affected);
    }

    @Test
    void executeAsSoftDelete_setFieldNameOnUpdate() {
        DeleteSpec<TestEntity> spec = new DeleteSpec<>(TestEntity.class).allowUnconditional(true);
        jakarta.persistence.Query q = mock(jakarta.persistence.Query.class);
        when(q.executeUpdate()).thenReturn(1);
        EntityManager em = em();
        when(em.createQuery(any(CriteriaUpdate.class))).thenReturn(q);
        spec.executeAsSoftDelete(em, "archived", true);
        verify(em).createQuery(any(CriteriaUpdate.class));
    }

    // ---- shared mock EM with full setup ----

    private EntityManager em() {
        EntityManager em = mock(EntityManager.class);
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        when(em.getCriteriaBuilder()).thenReturn(cb);

        // For buildPredicates (count query path)
        CriteriaQuery countQuery = mock(CriteriaQuery.class);
        when(cb.createQuery(Long.class)).thenReturn(countQuery);
        Root countRoot = mock(Root.class);
        when(countQuery.from(any(Class.class))).thenReturn(countRoot);
        when(countRoot.get(anyString())).thenReturn(mock(Path.class));
        when(cb.count(any(Root.class))).thenReturn(mock(jakarta.persistence.criteria.Expression.class));
        jakarta.persistence.TypedQuery countQ = mock(jakarta.persistence.TypedQuery.class);
        when(em.createQuery(any(CriteriaQuery.class))).thenReturn(countQ);
        when(countQ.getSingleResult()).thenReturn(0L);

        // For executeAsSoftDelete (CriteriaUpdate path)
        CriteriaUpdate update = mock(CriteriaUpdate.class);
        when(cb.createCriteriaUpdate(any(Class.class))).thenReturn(update);
        Root updateRoot = mock(Root.class);
        when(update.from(any(EntityType.class))).thenReturn(updateRoot);
        when(updateRoot.get(anyString())).thenReturn(mock(Path.class));

        Predicate pred = mock(Predicate.class);
        when(cb.conjunction()).thenReturn(pred);
        when(cb.and(any(Predicate[].class))).thenReturn(pred);

        return em;
    }
}
