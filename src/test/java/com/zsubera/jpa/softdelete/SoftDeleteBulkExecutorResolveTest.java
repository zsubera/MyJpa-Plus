package com.zsubera.jpa.softdelete;

import static org.junit.jupiter.api.Assertions.*;

import com.zsubera.jpa.annotation.SoftDelete;
import jakarta.persistence.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for SoftDeleteBulkExecutor focusing on:
 * - resolveExecContext validation
 * - ResolvedDeletedValue for different field types
 * - resolveTimestampColumn / resolveVersionColumn
 * - Edge cases in soft delete value resolution
 */
class SoftDeleteBulkExecutorResolveTest {

    // ---- Test entities ----

    @Entity
    static class BooleanSoftDeleteEntity {
        @Id
        @GeneratedValue
        private Long id;

        @SoftDelete
        private boolean deleted;
    }

    @Entity
    static class IntegerSoftDeleteEntity {
        @Id
        @GeneratedValue
        private Long id;

        @SoftDelete(deletedIntValue = 99)
        private int status;
    }

    @Entity
    static class StringSoftDeleteEntity {
        @Id
        @GeneratedValue
        private Long id;

        @SoftDelete(deletedStringValue = "D")
        private String flag;
    }

    @Entity
    static class TimestampSoftDeleteEntity {
        @Id
        @GeneratedValue
        private Long id;

        @SoftDelete(deletedTimestampField = "deletedAt")
        private boolean deleted;

        private java.time.LocalDateTime deletedAt;
    }

    @Entity
    static class VersionedEntity {
        @Id
        @GeneratedValue
        private Long id;

        @Version
        private Long version;

        @SoftDelete
        private boolean deleted;
    }

    @Entity
    static class NoSoftDeleteEntity {
        @Id
        @GeneratedValue
        private Long id;

        private String name;
    }

    // ---- Tests ----

    @Test
    @DisplayName("resolveDeletedValue for Boolean field returns booleanField=true")
    void shouldResolveBooleanDeletedValue() {
        var result = SoftDeleteHelper.resolveDeletedValue(BooleanSoftDeleteEntity.class,
            getField(BooleanSoftDeleteEntity.class, "deleted"),
            getField(BooleanSoftDeleteEntity.class, "deleted").getAnnotation(SoftDelete.class));

        assertTrue(result.booleanField(), "Boolean field should set booleanField=true");
        assertNull(result.dbValue(), "Boolean field should have null dbValue");
    }

    @Test
    @DisplayName("resolveDeletedValue for Integer field with custom deletedIntValue")
    void shouldResolveIntegerDeletedValue() {
        var result = SoftDeleteHelper.resolveDeletedValue(IntegerSoftDeleteEntity.class,
            getField(IntegerSoftDeleteEntity.class, "status"),
            getField(IntegerSoftDeleteEntity.class, "status").getAnnotation(SoftDelete.class));

        assertFalse(result.booleanField(), "Integer field should set booleanField=false");
        assertEquals(99, result.dbValue(), "Integer field should use deletedIntValue=99");
    }

    @Test
    @DisplayName("resolveDeletedValue for String field with custom deletedStringValue")
    void shouldResolveStringDeletedValue() {
        var result = SoftDeleteHelper.resolveDeletedValue(StringSoftDeleteEntity.class,
            getField(StringSoftDeleteEntity.class, "flag"),
            getField(StringSoftDeleteEntity.class, "flag").getAnnotation(SoftDelete.class));

        assertFalse(result.booleanField(), "String field should set booleanField=false");
        assertEquals("D", result.dbValue(), "String field should use deletedStringValue='D'");
    }

    @Test
    @DisplayName("resolveTimestampColumn returns column name when annotation specifies deletedTimestampField")
    void shouldResolveTimestampColumn() {
        var annotation = getField(TimestampSoftDeleteEntity.class, "deleted").getAnnotation(SoftDelete.class);
        String timestampCol =
            SoftDeleteBulkExecutor.resolveTimestampColumn(TimestampSoftDeleteEntity.class, annotation, "mysql");
        assertNotNull(timestampCol, "Should resolve timestamp column name");
        assertEquals("`deleted_at`", timestampCol, "Should convert camelCase to snake_case and quote for MySQL");
    }

    @Test
    @DisplayName("resolveTimestampColumn returns null when no deletedTimestampField")
    void shouldReturnNullTimestampColumn() {
        var annotation = getField(BooleanSoftDeleteEntity.class, "deleted").getAnnotation(SoftDelete.class);
        String timestampCol =
            SoftDeleteBulkExecutor.resolveTimestampColumn(BooleanSoftDeleteEntity.class, annotation, "mysql");
        assertNull(timestampCol, "Should return null when no deletedTimestampField specified");
    }

    @Test
    @DisplayName("resolveVersionColumn returns version column for entity with @Version")
    void shouldResolveVersionColumn() {
        String versionCol = SoftDeleteBulkExecutor.resolveVersionColumn(VersionedEntity.class, "mysql");
        assertNotNull(versionCol, "Should resolve version column");
        assertEquals("`version`", versionCol);
    }

    @Test
    @DisplayName("resolveVersionColumn returns null for entity without @Version")
    void shouldReturnNullVersionColumn() {
        String versionCol = SoftDeleteBulkExecutor.resolveVersionColumn(BooleanSoftDeleteEntity.class, "mysql");
        assertNull(versionCol, "Should return null for entity without @Version");
    }

    @Test
    @DisplayName("resolveTimestampField returns Field for entity with deletedTimestampField")
    void shouldResolveTimestampField() {
        var annotation = getField(TimestampSoftDeleteEntity.class, "deleted").getAnnotation(SoftDelete.class);
        var field = SoftDeleteBulkExecutor.resolveTimestampField(TimestampSoftDeleteEntity.class, annotation);
        assertNotNull(field, "Should resolve timestamp field");
        assertEquals("deletedAt", field.getName());
    }

    @Test
    @DisplayName("resolveTimestampField returns null for entity without deletedTimestampField")
    void shouldReturnNullTimestampField() {
        var annotation = getField(BooleanSoftDeleteEntity.class, "deleted").getAnnotation(SoftDelete.class);
        var field = SoftDeleteBulkExecutor.resolveTimestampField(BooleanSoftDeleteEntity.class, annotation);
        assertNull(field, "Should return null when no deletedTimestampField");
    }

    @Test
    @DisplayName("resolveVersionFieldInfo returns null for entity without @Version")
    void shouldReturnNullVersionFieldInfo() {
        var info = SoftDeleteBulkExecutor.resolveVersionFieldInfo(BooleanSoftDeleteEntity.class);
        assertNull(info, "Should return null for entity without @Version");
    }

    @Test
    @DisplayName("resolveVersionFieldInfo returns version info for entity with @Version")
    void shouldResolveVersionFieldInfo() {
        var info = SoftDeleteBulkExecutor.resolveVersionFieldInfo(VersionedEntity.class);
        assertNotNull(info, "Should resolve version field info");
        assertEquals("version", info.columnName());
        assertEquals("version", info.field().getName());
    }

    // ---- Helper ----

    private static java.lang.reflect.Field getField(Class<?> clazz, String name) {
        try {
            java.lang.reflect.Field f = clazz.getDeclaredField(name);
            f.setAccessible(true);
            return f;
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }
}
