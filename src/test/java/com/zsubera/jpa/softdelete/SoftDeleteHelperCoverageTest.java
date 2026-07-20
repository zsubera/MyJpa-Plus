package com.zsubera.jpa.softdelete;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.zsubera.jpa.annotation.SoftDelete;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Id;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Root;
import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;

class SoftDeleteHelperCoverageTest {

    // ---- validateIdentifier ----

    @Test
    void validateIdentifier_validIdentifier() {
        assertEquals("users", SoftDeleteHelper.validateIdentifier("users"));
    }

    @Test
    void validateIdentifier_validWithUnderscore() {
        assertEquals("user_accounts", SoftDeleteHelper.validateIdentifier("user_accounts"));
    }

    // ---- quoteIdentifier ----

    @Test
    void quoteIdentifier_mysql_usesBacktick() {
        assertEquals("`users`", SoftDeleteHelper.quoteIdentifier("users", "mysql"));
    }

    @Test
    void quoteIdentifier_postgresql_usesDoubleQuote() {
        assertEquals("\"users\"", SoftDeleteHelper.quoteIdentifier("users", "postgresql"));
    }

    @Test
    void quoteIdentifier_oracle_usesDoubleQuote() {
        assertEquals("\"users\"", SoftDeleteHelper.quoteIdentifier("users", "oracle"));
    }

    @Test
    void quoteIdentifier_sqlserver_usesDoubleQuote() {
        assertEquals("\"users\"", SoftDeleteHelper.quoteIdentifier("users", "sqlserver"));
    }

    @Test
    void quoteIdentifier_null_returnsNull() {
        assertNull(SoftDeleteHelper.quoteIdentifier(null, "mysql"));
    }

    @Test
    void quoteIdentifier_empty_returnsEmpty() {
        assertEquals("", SoftDeleteHelper.quoteIdentifier("", "mysql"));
    }

    @Test
    void quoteIdentifier_schemaTable() {
        assertEquals("`mydb`.`users`", SoftDeleteHelper.quoteIdentifier("mydb.users", "mysql"));
    }

    @Test
    void quoteIdentifier_defaultDialect() {
        assertEquals("\"users\"", SoftDeleteHelper.quoteIdentifier("users"));
    }

    // ---- getEntityBaseName ----

    @Test
    void getEntityBaseName_normalClass() {
        String name = SoftDeleteHelper.getEntityBaseName(SimpleEntity.class);
        assertTrue(name.contains("SimpleEntity"));
    }

    // ---- findSoftDeleteField ----

    @Test
    void findSoftDeleteField_withSoftDeleteField() {
        String field = SoftDeleteHelper.findSoftDeleteField(EntityWithSoftDelete.class);
        assertNotNull(field);
        assertEquals("deleted", field);
    }

    @Test
    void findSoftDeleteField_noSoftDeleteField() {
        assertNull(SoftDeleteHelper.findSoftDeleteField(SimpleEntity.class));
    }

    // ---- getField ----

    @Test
    void getField_existingField() {
        Field f = SoftDeleteHelper.getField(EntityWithSoftDelete.class, "deleted");
        assertNotNull(f);
        assertEquals("deleted", f.getName());
    }

    @Test
    void getField_nonExistentField() {
        assertNull(SoftDeleteHelper.getField(EntityWithSoftDelete.class, "nonExistent"));
    }

    // ---- isSoftDeleted ----

    @Test
    void isSoftDeleted_notDeleted() {
        EntityWithSoftDelete entity = new EntityWithSoftDelete();
        entity.deleted = false;
        assertFalse(SoftDeleteHelper.isSoftDeleted(EntityWithSoftDelete.class, entity));
    }

    @Test
    void isSoftDeleted_deleted() {
        EntityWithSoftDelete entity = new EntityWithSoftDelete();
        entity.deleted = true;
        assertTrue(SoftDeleteHelper.isSoftDeleted(EntityWithSoftDelete.class, entity));
    }

    @Test
    void isSoftDeleted_nullEntity_throws() {
        assertThrows(IllegalArgumentException.class,
            () -> SoftDeleteHelper.isSoftDeleted(EntityWithSoftDelete.class, null));
    }

    // ---- isNotDeleted ----

    @Test
    void isNotDeleted_withSoftDeleteField() {
        var spec = SoftDeleteHelper.isNotDeleted(EntityWithSoftDelete.class);
        assertNotNull(spec);
    }

    @Test
    void isNotDeleted_noSoftDeleteField() {
        var spec = SoftDeleteHelper.isNotDeleted(SimpleEntity.class);
        assertNotNull(spec);
    }

    // ---- isDeleted ----

    @Test
    void isDeleted_withSoftDeleteField() {
        var spec = SoftDeleteHelper.isDeleted(EntityWithSoftDelete.class);
        assertNotNull(spec);
    }

    @Test
    void isDeleted_noSoftDeleteField() {
        var spec = SoftDeleteHelper.isDeleted(SimpleEntity.class);
        assertNotNull(spec);
    }

    // ---- resolveTableName ----

    @Test
    void resolveTableName() {
        String name = SoftDeleteHelper.resolveTableName(SimpleEntity.class);
        assertNotNull(name);
    }

    // ---- resolveColumnName ----

    @Test
    void resolveColumnName_existingField() {
        String name = SoftDeleteHelper.resolveColumnName(EntityWithSoftDelete.class, "deleted");
        assertNotNull(name);
    }

    // ---- resolveIdColumnName ----

    @Test
    void resolveIdColumnName() {
        String name = SoftDeleteHelper.resolveIdColumnName(SimpleEntity.class);
        assertNotNull(name);
    }

    // ---- resolveDeletedValue ----

    @Test
    void resolveDeletedValue_booleanField() throws Exception {
        Field f = EntityWithSoftDelete.class.getDeclaredField("deleted");
        SoftDelete ann = f.getAnnotation(SoftDelete.class);
        SoftDeleteHelper.ResolvedDeletedValue resolved =
            SoftDeleteHelper.resolveDeletedValue(EntityWithSoftDelete.class, f, ann);
        assertTrue(resolved.booleanField());
        assertNull(resolved.dbValue());
    }

    // ---- buildNotDeleted ----

    @SuppressWarnings("unchecked")
    @Test
    void buildNotDeleted() {
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        Root<?> root = mock(Root.class);
        Path path = mock(Path.class);
        when(root.get("deleted")).thenReturn(path);
        when(cb.equal(path, false)).thenReturn(mock(jakarta.persistence.criteria.Predicate.class));
        when(cb.isNull(path)).thenReturn(mock(jakarta.persistence.criteria.Predicate.class));
        when(cb.or(any(), any())).thenReturn(mock(jakarta.persistence.criteria.Predicate.class));
        var predicate = SoftDeleteHelper.buildNotDeleted(cb, root, "deleted", EntityWithSoftDelete.class);
        assertNotNull(predicate);
    }

    // ---- detectDialect ----

    @Test
    void detectDialect_withCachedDialect() throws Exception {
        // Set cachedDialect via reflection
        Field f = SoftDeleteHelper.class.getDeclaredField("cachedDialect");
        f.setAccessible(true);
        String old = (String)f.get(null);
        try {
            f.set(null, "mysql");
            EntityManager em = mock(EntityManager.class);
            assertEquals("mysql", SoftDeleteHelper.detectDialect(em));
        } finally {
            f.set(null, old);
        }
    }

    // ---- shutdown ----

    @Test
    void shutdown_doesNotThrow() {
        assertDoesNotThrow(SoftDeleteHelper::shutdown);
    }

    // ---- Test entities ----

    static class SimpleEntity {
        @Id
        private Long id;
    }

    static class EntityWithSoftDelete {
        @Id
        private Long id;
        @SoftDelete
        private boolean deleted;
    }
}
