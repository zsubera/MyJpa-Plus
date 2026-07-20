package com.zsubera.jpa.softdelete;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.zsubera.jpa.annotation.SoftDelete;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class SoftDeleteBulkExecutorCoverageTest {

    @AfterEach
    void cleanup() {
        SoftDeleteBulkExecutor.setEventPublisher(null);
    }

    // ---- publishEvent ----

    @Test
    void publishEvent_noPublisher_doesNothing() {
        SoftDeleteBulkExecutor.publishEvent(Object.class, 1);
    }

    @Test
    void publishEvent_withPublisher_publishes() {
        AtomicInteger count = new AtomicInteger();
        SoftDeleteBulkExecutor.setEventPublisher((cls, rows) -> count.addAndGet(rows));
        SoftDeleteBulkExecutor.publishEvent(Object.class, 5);
        assertEquals(5, count.get());
    }

    @Test
    void publishEvent_zeroRows_doesNotPublish() {
        AtomicInteger count = new AtomicInteger();
        SoftDeleteBulkExecutor.setEventPublisher((cls, rows) -> count.addAndGet(rows));
        SoftDeleteBulkExecutor.publishEvent(Object.class, 0);
        assertEquals(0, count.get());
    }

    @Test
    void publishEvent_publisherThrows_logsAndContinues() {
        SoftDeleteBulkExecutor.setEventPublisher((cls, rows) -> {
            throw new RuntimeException("boom");
        });
        assertDoesNotThrow(() -> SoftDeleteBulkExecutor.publishEvent(Object.class, 1));
    }

    @Test
    void publishEvent_getEventPublisher_returnsCurrent() {
        assertNull(SoftDeleteBulkExecutor.getEventPublisher());
        SoftDeleteBulkExecutor.setEventPublisher((cls, rows) -> {
        });
        assertNotNull(SoftDeleteBulkExecutor.getEventPublisher());
    }

    // ---- resolveTimestampColumn ----

    @Test
    void resolveTimestampColumn_nullAnnotation_returnsNull() {
        assertNull(SoftDeleteBulkExecutor.resolveTimestampColumn(TestEntity.class, null, "mysql"));
    }

    @Test
    void resolveTimestampColumn_nonExistentField_returnsNull() {
        SoftDelete annotation = mock(SoftDelete.class);
        when(annotation.deletedTimestampField()).thenReturn("nonExistentField");
        assertNull(SoftDeleteBulkExecutor.resolveTimestampColumn(TestEntity.class, annotation, "mysql"));
    }

    // ---- resolveTimestampField ----

    @Test
    void resolveTimestampField_nullAnnotation_returnsNull() {
        assertNull(SoftDeleteBulkExecutor.resolveTimestampField(TestEntity.class, null));
    }

    @Test
    void resolveTimestampField_nonExistentField_returnsNull() {
        SoftDelete annotation = mock(SoftDelete.class);
        when(annotation.deletedTimestampField()).thenReturn("nonExistentField");
        assertNull(SoftDeleteBulkExecutor.resolveTimestampField(TestEntity.class, annotation));
    }

    // ---- resolveVersionFieldInfo ----

    @Test
    void resolveVersionFieldInfo_noVersionField_returnsNull() {
        assertNull(SoftDeleteBulkExecutor.resolveVersionFieldInfo(TestEntity.class));
    }

    @Test
    void resolveVersionFieldInfo_withVersionField_returnsInfo() {
        SoftDeleteBulkExecutor.VersionFieldInfo info =
            SoftDeleteBulkExecutor.resolveVersionFieldInfo(TestEntityWithVersion.class);
        assertNotNull(info);
        assertEquals("version", info.field().getName());
    }

    @Test
    void resolveVersionFieldInfo_withVersionAndColumnAnnotation() {
        SoftDeleteBulkExecutor.VersionFieldInfo info =
            SoftDeleteBulkExecutor.resolveVersionFieldInfo(TestEntityWithVersionColumn.class);
        assertNotNull(info);
        assertEquals("custom_version", info.columnName());
    }

    // ---- resolveVersionColumn ----

    @Test
    void resolveVersionColumn_noVersionField_returnsNull() {
        assertNull(SoftDeleteBulkExecutor.resolveVersionColumn(TestEntity.class, "mysql"));
    }

    @Test
    void resolveVersionColumn_withVersionField_returnsQuotedColumn() {
        String col = SoftDeleteBulkExecutor.resolveVersionColumn(TestEntityWithVersion.class, "mysql");
        assertNotNull(col);
        assertTrue(col.contains("version"));
    }

    // ---- resolveVersionField ----

    @Test
    void resolveVersionField_noVersionField_returnsNull() {
        assertNull(SoftDeleteBulkExecutor.resolveVersionField(TestEntity.class));
    }

    @Test
    void resolveVersionField_withVersionField_returnsField() {
        java.lang.reflect.Field f = SoftDeleteBulkExecutor.resolveVersionField(TestEntityWithVersion.class);
        assertNotNull(f);
        assertEquals("version", f.getName());
    }

    // ---- validateGeneratedSql ----

    @Test
    void validateGeneratedSql_validSql_doesNotThrow() {
        assertDoesNotThrow(
            () -> SoftDeleteBulkExecutor.validateGeneratedSql("SELECT * FROM users WHERE id = 1", "test"));
    }

    @Test
    void validateGeneratedSql_invalidSql_logsWarning() {
        assertDoesNotThrow(() -> SoftDeleteBulkExecutor.validateGeneratedSql("INVALID SQL !!! ???", "test"));
    }

    // ---- softDeleteByIdsUsingEntityManager ----

    @Test
    void softDeleteByIdsUsingEntityManager_nullIds_returnsZero() {
        EntityManager em = mock(EntityManager.class);
        assertEquals(0, SoftDeleteBulkExecutor.softDeleteByIdsUsingEntityManager(em, TestEntity.class, null));
    }

    @Test
    void softDeleteByIdsUsingEntityManager_emptyIds_returnsZero() {
        EntityManager em = mock(EntityManager.class);
        assertEquals(0,
            SoftDeleteBulkExecutor.softDeleteByIdsUsingEntityManager(em, TestEntity.class, java.util.List.of()));
    }

    @Test
    void softDeleteByIdsUsingEntityManager_nullId_throws() {
        EntityManager em = mock(EntityManager.class);
        assertThrows(IllegalArgumentException.class, () -> SoftDeleteBulkExecutor.softDeleteByIdsUsingEntityManager(em,
            TestEntity.class, java.util.Arrays.asList(1L, null)));
    }

    // ---- softDeleteAll null checks ----

    @Test
    void softDeleteAll_nullEm_throws() {
        assertThrows(IllegalArgumentException.class,
            () -> SoftDeleteBulkExecutor.softDeleteAll(null, TestEntity.class, true));
    }

    @Test
    void softDeleteAll_nullEntityClass_throws() {
        assertThrows(IllegalArgumentException.class,
            () -> SoftDeleteBulkExecutor.softDeleteAll(mock(EntityManager.class), null, true));
    }

    @Test
    void softDeleteAll_notAllowUnconditional_throws() {
        assertThrows(IllegalStateException.class,
            () -> SoftDeleteBulkExecutor.softDeleteAll(mock(EntityManager.class), TestEntity.class, false));
    }

    // ---- softDeleteByIdsWithVersionCheck null checks ----

    @Test
    void softDeleteByIdsWithVersionCheck_nullEm_throws() {
        assertThrows(IllegalArgumentException.class, () -> SoftDeleteBulkExecutor.softDeleteByIdsWithVersionCheck(null,
            TestEntity.class, java.util.List.of(1L), 1L));
    }

    @Test
    void softDeleteByIdsWithVersionCheck_nullEntityClass_throws() {
        assertThrows(IllegalArgumentException.class, () -> SoftDeleteBulkExecutor
            .softDeleteByIdsWithVersionCheck(mock(EntityManager.class), null, java.util.List.of(1L), 1L));
    }

    @Test
    void softDeleteByIdsWithVersionCheck_emptyIds_returnsZero() {
        EntityManager em = mock(EntityManager.class);
        assertEquals(0,
            SoftDeleteBulkExecutor.softDeleteByIdsWithVersionCheck(em, TestEntity.class, java.util.List.of(), 1L));
    }

    @Test
    void softDeleteByIdsWithVersionCheck_nullIds_returnsZero() {
        EntityManager em = mock(EntityManager.class);
        assertEquals(0, SoftDeleteBulkExecutor.softDeleteByIdsWithVersionCheck(em, TestEntity.class, null, 1L));
    }

    // ---- softDeleteByIds null checks ----

    @Test
    void softDeleteByIds_nullEm_throws() {
        assertThrows(IllegalArgumentException.class,
            () -> SoftDeleteBulkExecutor.softDeleteByIds(null, TestEntity.class, java.util.List.of(1L)));
    }

    @Test
    void softDeleteByIds_nullEntityClass_throws() {
        assertThrows(IllegalArgumentException.class,
            () -> SoftDeleteBulkExecutor.softDeleteByIds(mock(EntityManager.class), null, java.util.List.of(1L)));
    }

    @Test
    void softDeleteByIds_emptyIds_returnsZero() {
        EntityManager em = mock(EntityManager.class);
        assertEquals(0, SoftDeleteBulkExecutor.softDeleteByIds(em, TestEntity.class, java.util.List.of()));
    }

    // ---- rollbackCurrentTransaction ----

    @Test
    void rollbackCurrentTransaction_noTransaction_returnsFalse() throws Exception {
        EntityManager em = mock(EntityManager.class);
        when(em.getTransaction()).thenThrow(new IllegalStateException("JTA"));
        java.lang.reflect.Method method =
            SoftDeleteBulkExecutor.class.getDeclaredMethod("rollbackCurrentTransaction", EntityManager.class);
        method.setAccessible(true);
        boolean result = (boolean)method.invoke(null, em);
        assertFalse(result);
    }

    @Test
    void rollbackCurrentTransaction_activeTransaction_rollsBack() throws Exception {
        EntityManager em = mock(EntityManager.class);
        EntityTransaction tx = mock(EntityTransaction.class);
        when(em.getTransaction()).thenReturn(tx);
        when(tx.isActive()).thenReturn(true);
        java.lang.reflect.Method method =
            SoftDeleteBulkExecutor.class.getDeclaredMethod("rollbackCurrentTransaction", EntityManager.class);
        method.setAccessible(true);
        boolean result = (boolean)method.invoke(null, em);
        assertTrue(result);
        verify(tx).rollback();
    }

    @Test
    void rollbackCurrentTransaction_rollbackFails_returnsFalse() throws Exception {
        EntityManager em = mock(EntityManager.class);
        EntityTransaction tx = mock(EntityTransaction.class);
        when(em.getTransaction()).thenReturn(tx);
        when(tx.isActive()).thenReturn(true);
        doThrow(new RuntimeException("rollback failed")).when(tx).rollback();
        java.lang.reflect.Method method =
            SoftDeleteBulkExecutor.class.getDeclaredMethod("rollbackCurrentTransaction", EntityManager.class);
        method.setAccessible(true);
        boolean result = (boolean)method.invoke(null, em);
        assertFalse(result);
    }

    @Test
    void rollbackCurrentTransaction_inactiveTransaction_triesSpring() throws Exception {
        EntityManager em = mock(EntityManager.class);
        EntityTransaction tx = mock(EntityTransaction.class);
        when(em.getTransaction()).thenReturn(tx);
        when(tx.isActive()).thenReturn(false);
        java.lang.reflect.Method method =
            SoftDeleteBulkExecutor.class.getDeclaredMethod("rollbackCurrentTransaction", EntityManager.class);
        method.setAccessible(true);
        boolean result = (boolean)method.invoke(null, em);
        assertFalse(result);
    }

    // ---- requireNonNull ----

    @Test
    void requireNonNull_null_throws() throws Exception {
        java.lang.reflect.Method method =
            SoftDeleteBulkExecutor.class.getDeclaredMethod("requireNonNull", Object.class, String.class);
        method.setAccessible(true);
        assertThrows(java.lang.reflect.InvocationTargetException.class,
            () -> method.invoke(null, (Object)null, "test"));
    }

    @Test
    void requireNonNull_nonNull_doesNotThrow() throws Exception {
        java.lang.reflect.Method method =
            SoftDeleteBulkExecutor.class.getDeclaredMethod("requireNonNull", Object.class, String.class);
        method.setAccessible(true);
        assertDoesNotThrow(() -> method.invoke(null, "value", "test"));
    }

    // ---- Test entities ----

    static class TestEntity {
        private Long id;
        @SoftDelete
        private boolean deleted;
    }

    static class TestEntityWithTimestamp {
        private Long id;
        @SoftDelete(deletedTimestampField = "deletedAt")
        private boolean deleted;
        private java.time.LocalDateTime deletedAt;
    }

    static class TestEntityWithVersion {
        @jakarta.persistence.Version
        private Long version;
    }

    static class TestEntityWithVersionColumn {
        @jakarta.persistence.Version
        @jakarta.persistence.Column(name = "custom_version")
        private Long version;
    }
}
