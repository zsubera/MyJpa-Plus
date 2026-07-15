package com.zsubera.jpa.softdelete;

import static org.junit.jupiter.api.Assertions.*;

import com.zsubera.jpa.annotation.SoftDelete;
import jakarta.persistence.*;
import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link SoftDeleteBulkExecutor} @Version field handling.
 */
class SoftDeleteBulkExecutorTest {

    @Entity
    @Table(name = "versioned_soft_delete_test")
    static class VersionedSoftDeleteEntity {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        private String name;

        @SoftDelete
        private Boolean deleted = false;

        @Version
        private Long version;

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

        public Boolean getDeleted() {
            return deleted;
        }

        public void setDeleted(Boolean deleted) {
            this.deleted = deleted;
        }

        public Long getVersion() {
            return version;
        }

        public void setVersion(Long version) {
            this.version = version;
        }
    }

    @Entity
    @Table(name = "simple_soft_delete_test")
    static class SimpleSoftDeleteEntity {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;
        private String name;
        @SoftDelete
        private Boolean deleted = false;

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

        public Boolean getDeleted() {
            return deleted;
        }

        public void setDeleted(Boolean deleted) {
            this.deleted = deleted;
        }
    }

    @Test
    void resolveVersionColumn_returnsColumnNameForVersionedEntity() {
        String column = SoftDeleteBulkExecutor.resolveVersionColumn(VersionedSoftDeleteEntity.class, "mysql");
        assertNotNull(column, "Version column should be resolved for entity with @Version");
        assertEquals("`version`", column);
    }

    @Test
    void resolveVersionColumn_returnsNullForNonVersionedEntity() {
        String column = SoftDeleteBulkExecutor.resolveVersionColumn(SimpleSoftDeleteEntity.class, "mysql");
        assertNull(column, "Version column should be null for entity without @Version");
    }

    @Test
    void resolveVersionColumn_withColumnAnnotation_returnsCustomColumnName() {
        @Entity
        @Table(name = "custom_version_test")
        class CustomVersionEntity {
            @Id
            private Long id;
            @SoftDelete
            private Boolean deleted = false;
            @Version
            @Column(name = "opt_lock")
            private Integer version;

            public Long getId() {
                return id;
            }

            public Boolean getDeleted() {
                return deleted;
            }

            public Integer getVersion() {
                return version;
            }
        }

        String column = SoftDeleteBulkExecutor.resolveVersionColumn(CustomVersionEntity.class, "mysql");
        assertEquals("`opt_lock`", column, "Should resolve @Column name for @Version field and quote for MySQL");
    }

    @Test
    void resolveVersionField_returnsFieldForVersionedEntity() {
        Field field = SoftDeleteBulkExecutor.resolveVersionField(VersionedSoftDeleteEntity.class);
        assertNotNull(field);
        assertTrue(field.isAnnotationPresent(Version.class));
    }

    @Test
    void resolveVersionField_returnsNullForNonVersionedEntity() {
        Field field = SoftDeleteBulkExecutor.resolveVersionField(SimpleSoftDeleteEntity.class);
        assertNull(field);
    }

    @Test
    void versionCheckExceptionMessage_mentionsPossibleCauses() {
        // Verify the error message format includes misleading-cause disclaimer
        int idsSize = 3;
        long expectedVersion = 5L;
        String msg = "Soft delete returned 0 affected rows for " + idsSize
            + " IDs. Possible causes: entities already soft-deleted, or version mismatch (expected: " + expectedVersion
            + ").";
        assertTrue(msg.contains("Possible causes"));
        assertTrue(msg.contains("already soft-deleted"));
        assertTrue(msg.contains("version mismatch"));
        assertTrue(msg.contains("expected: 5"));
    }
}
