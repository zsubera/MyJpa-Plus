package com.zsubera.jpa.update;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.zsubera.jpa.spec.TestEntity;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;

/**
 * Mock-based tests for {@link MergeSpec#executeWithCallbacks}.
 */
class MergeSpecCallbacksMockTest {

    @Test
    void executeWithCallbacks_nullEm_throws() {
        assertThrows(IllegalArgumentException.class,
            () -> new MergeSpec<>(TestEntity.class).withEntity(new TestEntity()).executeWithCallbacks(null));
    }

    @Test
    void executeWithCallbacks_noEntity_throws() {
        assertThrows(IllegalStateException.class,
            () -> new MergeSpec<>(TestEntity.class).executeWithCallbacks(mock(EntityManager.class)));
    }

    @Test
    void executeWithCallbacks_withOnConflict_throws() {
        TestEntity entity = new TestEntity();
        entity.setName("test");
        assertThrows(UnsupportedOperationException.class, () -> new MergeSpec<>(TestEntity.class).withEntity(entity)
            .onConflict(TestEntity::getName).executeWithCallbacks(mock(EntityManager.class)));
    }

    @Test
    void executeWithCallbacks_withUpdateOnConflict_throws() {
        TestEntity entity = new TestEntity();
        entity.setName("test");
        assertThrows(UnsupportedOperationException.class, () -> new MergeSpec<>(TestEntity.class).withEntity(entity)
            .updateOnConflict(TestEntity::getName).executeWithCallbacks(mock(EntityManager.class)));
    }

    @Test
    void executeWithCallbacks_detachedEntity_mergesAndFlushes() {
        TestEntity entity = new TestEntity();
        entity.setName("test");
        EntityManager em = mock(EntityManager.class);
        when(em.contains(entity)).thenReturn(false);
        TestEntity managed = new TestEntity();
        managed.setName("test");
        when(em.merge(entity)).thenReturn(managed);

        int result = new MergeSpec<>(TestEntity.class).withEntity(entity).executeWithCallbacks(em);

        assertEquals(1, result);
        verify(em).merge(entity);
        verify(em).flush();
    }

    @Test
    void executeWithCallbacks_managedEntity_skipsMerge() {
        TestEntity entity = new TestEntity();
        entity.setName("test");
        EntityManager em = mock(EntityManager.class);
        when(em.contains(entity)).thenReturn(true);

        int result = new MergeSpec<>(TestEntity.class).withEntity(entity).executeWithCallbacks(em);

        assertEquals(1, result);
        verify(em, never()).merge(any());
        verify(em).flush();
    }
}
