package com.zsubera.jpa.update;

import static org.junit.jupiter.api.Assertions.*;

import com.zsubera.jpa.exception.MyJpaPlusException;
import jakarta.persistence.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for EntityFieldExtractor focusing on:
 * - Circular @Embedded reference detection
 * - @Column(insertable=false) filtering
 * - @Column(updatable=false) handling
 * - Superclass field scanning
 */
class EntityFieldExtractorCircularTest {

    // ---- Test entities for circular reference detection ----

    @Embeddable
    static class NodeA {
        private String value;

        @Embedded
        private NodeB nestedB;
    }

    @Embeddable
    static class NodeB {
        private String value;

        @Embedded
        private NodeA nestedA; // Creates cycle: NodeA -> NodeB -> NodeA
    }

    @Entity
    static class EntityWithCircularEmbedding {
        @Id
        @GeneratedValue
        private Long id;

        @Embedded
        private NodeA root;
    }

    // ---- Test entities for insertable/updatable filtering ----

    @Entity
    static class EntityWithInsertableFalse {
        @Id
        @GeneratedValue
        private Long id;

        private String updatableField;

        @Column(insertable = false, updatable = true)
        private String insertableFalseField;
    }

    @Entity
    static class EntityWithUpdatableFalse {
        @Id
        @GeneratedValue
        private Long id;

        @Column(updatable = false)
        private String updatableFalseField;

        private String normalField;
    }

    // ---- Test entities for superclass scanning ----

    @MappedSuperclass
    static abstract class BaseEntity {
        @Id
        @GeneratedValue
        private Long id;

        private String baseField;
    }

    @Entity
    static class ChildEntity extends BaseEntity {
        private String childField;
    }

    // ---- Tests ----

    @Test
    @DisplayName("circular @Embedded reference is detected")
    void shouldDetectCircularEmbeddedReference() {
        EntityWithCircularEmbedding entity = new EntityWithCircularEmbedding();
        entity.root = new NodeA();
        entity.root.value = "a";
        entity.root.nestedB = new NodeB();
        entity.root.nestedB.value = "b";
        entity.root.nestedB.nestedA = entity.root; // circular!

        EntityFieldExtractor<EntityWithCircularEmbedding> extractor =
            new EntityFieldExtractor<>(EntityWithCircularEmbedding.class);

        assertThrows(MyJpaPlusException.class, () -> extractor.extractFieldValues(entity),
            "Should detect circular @Embedded reference and throw");
    }

    @Test
    @DisplayName("non-circular @Embedded reference works correctly")
    void shouldHandleNonCircularEmbedded() {
        @Embeddable
        static class SimpleEmbed {
            private String value;
        }

        @Entity
        static class EntityWithSimpleEmbed {
            @Id
            @GeneratedValue
            private Long id;

            @Embedded
            private SimpleEmbed embed;
        }

        EntityWithSimpleEmbed entity = new EntityWithSimpleEmbed();
        entity.embed = new SimpleEmbed();
        entity.embed.value = "test";

        EntityFieldExtractor<EntityWithSimpleEmbed> extractor = new EntityFieldExtractor<>(EntityWithSimpleEmbed.class);

        var fieldValues = extractor.extractFieldValues(entity);
        assertNotNull(fieldValues);
        assertFalse(fieldValues.isEmpty());
    }

    @Test
    @DisplayName("EntityFieldExtractor rejects null entity")
    void shouldRejectNullEntity() {
        EntityFieldExtractor<Object> extractor = new EntityFieldExtractor<>(Object.class);
        assertThrows(IllegalArgumentException.class, () -> extractor.extractFieldValues(null));
    }

    @Test
    @DisplayName("@Column(insertable=false) fields are excluded from extraction")
    void shouldExcludeInsertableFalseFields() {
        EntityWithInsertableFalse entity = new EntityWithInsertableFalse();
        entity.updatableField = "updatable";
        entity.insertableFalseField = "insertableFalse";

        EntityFieldExtractor<EntityWithInsertableFalse> extractor =
            new EntityFieldExtractor<>(EntityWithInsertableFalse.class);

        var fieldValues = extractor.extractFieldValues(entity);
        boolean hasInsertableFalse = fieldValues.stream().anyMatch(fv -> fv.fieldName().equals("insertableFalseField"));
        assertFalse(hasInsertableFalse, "@Column(insertable=false) fields should be excluded from extraction");
    }

    @Test
    @DisplayName("@Column(updatable=false) fields are marked as not updatable")
    void shouldMarkUpdatableFalseFields() {
        EntityWithUpdatableFalse entity = new EntityWithUpdatableFalse();
        entity.updatableFalseField = "not-updatable";
        entity.normalField = "normal";

        EntityFieldExtractor<EntityWithUpdatableFalse> extractor =
            new EntityFieldExtractor<>(EntityWithUpdatableFalse.class);

        var fieldValues = extractor.extractFieldValues(entity);
        var updatableFalseField =
            fieldValues.stream().filter(fv -> fv.fieldName().equals("updatableFalseField")).findFirst();
        assertTrue(updatableFalseField.isPresent(), "updatableFalseField should be present");
        assertFalse(updatableFalseField.get().updatable(),
            "@Column(updatable=false) field should be marked as not updatable");
    }

    @Test
    @DisplayName("superclass fields are included in extraction")
    void shouldIncludeSuperclassFields() throws Exception {
        ChildEntity entity = new ChildEntity();
        java.lang.reflect.Field baseField = BaseEntity.class.getDeclaredField("baseField");
        baseField.setAccessible(true);
        baseField.set(entity, "base");
        java.lang.reflect.Field childField = ChildEntity.class.getDeclaredField("childField");
        childField.setAccessible(true);
        childField.set(entity, "child");

        EntityFieldExtractor<ChildEntity> extractor = new EntityFieldExtractor<>(ChildEntity.class);

        var fieldValues = extractor.extractFieldValues(entity);
        boolean hasBaseField = fieldValues.stream().anyMatch(fv -> fv.fieldName().equals("baseField"));
        boolean hasChildField = fieldValues.stream().anyMatch(fv -> fv.fieldName().equals("childField"));
        assertTrue(hasBaseField, "Superclass field 'baseField' should be included");
        assertTrue(hasChildField, "Child field 'childField' should be included");
    }

    @Test
    @DisplayName("isAutoGeneratedId detects @GeneratedValue")
    void shouldDetectAutoGeneratedId() {
        EntityFieldExtractor<ChildEntity> extractor = new EntityFieldExtractor<>(ChildEntity.class);
        assertTrue(extractor.isAutoGeneratedId("id"), "Field 'id' with @GeneratedValue should be detected");
    }

    @Test
    @DisplayName("resolveIdColumnNames works for entity with @Id")
    void shouldResolveIdColumnNames() {
        EntityFieldExtractor<ChildEntity> extractor = new EntityFieldExtractor<>(ChildEntity.class);
        var idColumns = extractor.resolveIdColumnNames();
        assertNotNull(idColumns);
        assertFalse(idColumns.isEmpty());
    }
}
