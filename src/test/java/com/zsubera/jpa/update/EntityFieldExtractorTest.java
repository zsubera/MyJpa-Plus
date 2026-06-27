package com.zsubera.jpa.update;

import static org.junit.jupiter.api.Assertions.*;

import jakarta.persistence.*;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EntityFieldExtractorTest {

    @BeforeEach
    void clearCaches() {
        com.zsubera.jpa.util.IdentifierValidator.setUnicodeIdentifiers(false);
    }

    @Test
    void extractFieldValues_nullEntity_throws() {
        EntityFieldExtractor<SimpleEntity> extractor = new EntityFieldExtractor<>(SimpleEntity.class);
        assertThrows(IllegalArgumentException.class, () -> extractor.extractFieldValues(null));
    }

    @Test
    void extractFieldValues_simpleEntity() {
        EntityFieldExtractor<SimpleEntity> extractor = new EntityFieldExtractor<>(SimpleEntity.class);
        SimpleEntity entity = new SimpleEntity();
        entity.setId(1L);
        entity.setName("test");
        entity.setStatus(42);

        List<EntityFieldExtractor.EntityFieldValue> values = extractor.extractFieldValues(entity);
        assertFalse(values.isEmpty());
        assertTrue(values.stream().anyMatch(v -> "name".equals(v.fieldName()) && "test".equals(v.value())));
        assertTrue(values.stream().anyMatch(v -> "status".equals(v.fieldName()) && 42 == (int)v.value()));
    }

    @Test
    void extractFieldValues_withColumnAnnotation() {
        EntityFieldExtractor<ColumnAnnotatedEntity> extractor = new EntityFieldExtractor<>(ColumnAnnotatedEntity.class);
        ColumnAnnotatedEntity entity = new ColumnAnnotatedEntity();
        entity.setFullName("alice");

        List<EntityFieldExtractor.EntityFieldValue> values = extractor.extractFieldValues(entity);
        assertTrue(
            values.stream().anyMatch(v -> "fullName".equals(v.fieldName()) && "full_name".equals(v.columnName())));
    }

    @Test
    void extractFieldValues_skipsTransientField() {
        EntityFieldExtractor<EntityWithTransient> extractor = new EntityFieldExtractor<>(EntityWithTransient.class);
        EntityWithTransient entity = new EntityWithTransient();
        entity.setName("test");
        entity.setTemp("temp");

        List<EntityFieldExtractor.EntityFieldValue> values = extractor.extractFieldValues(entity);
        assertTrue(values.stream().anyMatch(v -> "name".equals(v.fieldName())));
        assertFalse(values.stream().anyMatch(v -> "temp".equals(v.fieldName())));
    }

    @Test
    void extractFieldValues_skipsManyToOneField() {
        EntityFieldExtractor<EntityWithManyToOne> extractor = new EntityFieldExtractor<>(EntityWithManyToOne.class);
        EntityWithManyToOne entity = new EntityWithManyToOne();
        entity.setName("test");

        List<EntityFieldExtractor.EntityFieldValue> values = extractor.extractFieldValues(entity);
        assertTrue(values.stream().anyMatch(v -> "name".equals(v.fieldName())));
        assertFalse(values.stream().anyMatch(v -> "related".equals(v.fieldName())));
    }

    @Test
    void extractFieldValues_skipsOneToManyField() {
        EntityFieldExtractor<EntityWithOneToMany> extractor = new EntityFieldExtractor<>(EntityWithOneToMany.class);
        EntityWithOneToMany entity = new EntityWithOneToMany();
        entity.setName("test");

        List<EntityFieldExtractor.EntityFieldValue> values = extractor.extractFieldValues(entity);
        assertTrue(values.stream().anyMatch(v -> "name".equals(v.fieldName())));
        assertFalse(values.stream().anyMatch(v -> "items".equals(v.fieldName())));
    }

    @Test
    void extractFieldValues_skipsInsertableFalseField() {
        EntityFieldExtractor<InsertableFalseEntity> extractor = new EntityFieldExtractor<>(InsertableFalseEntity.class);
        InsertableFalseEntity entity = new InsertableFalseEntity();
        entity.setName("test");
        entity.setComputed("computed");

        List<EntityFieldExtractor.EntityFieldValue> values = extractor.extractFieldValues(entity);
        assertTrue(values.stream().anyMatch(v -> "name".equals(v.fieldName())));
        assertFalse(values.stream().anyMatch(v -> "computed".equals(v.fieldName())));
    }

    @Test
    void extractFieldValues_withEmbeddedField() {
        EntityFieldExtractor<EntityWithEmbedded> extractor = new EntityFieldExtractor<>(EntityWithEmbedded.class);
        EntityWithEmbedded entity = new EntityWithEmbedded();
        entity.setName("parent");
        Address address = new Address();
        address.setCity("Beijing");
        address.setZipCode("100000");
        entity.setAddress(address);

        List<EntityFieldExtractor.EntityFieldValue> values = extractor.extractFieldValues(entity);
        assertTrue(values.stream().anyMatch(v -> "name".equals(v.fieldName())));
        assertTrue(values.stream().anyMatch(v -> "address.city".equals(v.fieldName()) && "Beijing".equals(v.value())));
        assertTrue(
            values.stream().anyMatch(v -> "address.zipCode".equals(v.fieldName()) && "100000".equals(v.value())));
    }

    @Test
    void extractFieldValues_withEmbeddedAndAttributeOverride() {
        EntityFieldExtractor<EntityWithOverriddenEmbedded> extractor =
            new EntityFieldExtractor<>(EntityWithOverriddenEmbedded.class);
        EntityWithOverriddenEmbedded entity = new EntityWithOverriddenEmbedded();
        Address address = new Address();
        address.setCity("Shanghai");
        entity.setAddress(address);

        List<EntityFieldExtractor.EntityFieldValue> values = extractor.extractFieldValues(entity);
        assertTrue(values.stream()
            .anyMatch(v -> "address.city".equals(v.fieldName()) && "override_city".equals(v.columnName())));
    }

    @Test
    void extractFieldValues_withNestedEmbedded() {
        EntityFieldExtractor<EntityWithNestedEmbedded> extractor =
            new EntityFieldExtractor<>(EntityWithNestedEmbedded.class);
        EntityWithNestedEmbedded entity = new EntityWithNestedEmbedded();
        OuterEmbed outer = new OuterEmbed();
        InnerEmbed inner = new InnerEmbed();
        inner.setValue("deep");
        outer.setInner(inner);
        outer.setLabel("outer");
        entity.setOuter(outer);

        List<EntityFieldExtractor.EntityFieldValue> values = extractor.extractFieldValues(entity);
        assertTrue(values.stream().anyMatch(v -> "outer.label".equals(v.fieldName()) && "outer".equals(v.value())));
        assertTrue(
            values.stream().anyMatch(v -> "outer.inner.value".equals(v.fieldName()) && "deep".equals(v.value())));
    }

    @Test
    void extractFieldValues_circularEmbedded_detection() {
        EntityFieldExtractor<CircularEntityA> extractor = new EntityFieldExtractor<>(CircularEntityA.class);
        CircularEntityA entity = new CircularEntityA();
        entity.name = "a";
        CircularEmbedA embedA = new CircularEmbedA();
        CircularEmbedB embedB = new CircularEmbedB();
        embedB.setNameB("b");
        embedA.setB(embedB);
        embedB.setA(embedA);
        entity.embedded = embedA;

        assertThrows(com.zsubera.jpa.exception.MyJpaPlusException.class, () -> extractor.extractFieldValues(entity));
    }

    @Test
    void resolveColumnName_withColumnAnnotation() {
        EntityFieldExtractor<ColumnAnnotatedEntity> extractor = new EntityFieldExtractor<>(ColumnAnnotatedEntity.class);
        try {
            java.lang.reflect.Field f = ColumnAnnotatedEntity.class.getDeclaredField("fullName");
            String columnName = extractor.resolveColumnName(f);
            assertEquals("full_name", columnName);
        } catch (NoSuchFieldException e) {
            fail("Field not found");
        }
    }

    @Test
    void resolveColumnName_withoutColumnAnnotation() {
        EntityFieldExtractor<SimpleEntity> extractor = new EntityFieldExtractor<>(SimpleEntity.class);
        try {
            java.lang.reflect.Field f = SimpleEntity.class.getDeclaredField("name");
            String columnName = extractor.resolveColumnName(f);
            assertNotNull(columnName);
        } catch (NoSuchFieldException e) {
            fail("Field not found");
        }
    }

    @Test
    void resolveIdColumnNames_simpleId() {
        EntityFieldExtractor<SimpleEntity> extractor = new EntityFieldExtractor<>(SimpleEntity.class);
        List<String> idColumns = extractor.resolveIdColumnNames();
        assertNotNull(idColumns);
        assertFalse(idColumns.isEmpty());
    }

    @Test
    void resolveIdColumnNames_withEmbeddedId() {
        EntityFieldExtractor<EntityWithEmbeddedId> extractor = new EntityFieldExtractor<>(EntityWithEmbeddedId.class);
        assertThrows(IllegalStateException.class, extractor::resolveIdColumnNames);
    }

    @Test
    void resolveJavaFieldToDbColumn_existingField() {
        EntityFieldExtractor<SimpleEntity> extractor = new EntityFieldExtractor<>(SimpleEntity.class);
        String columnName = extractor.resolveJavaFieldToDbColumn("name");
        assertNotNull(columnName);
    }

    @Test
    void resolveJavaFieldToDbColumn_nonExistingField_fallback() {
        EntityFieldExtractor<SimpleEntity> extractor = new EntityFieldExtractor<>(SimpleEntity.class);
        String columnName = extractor.resolveJavaFieldToDbColumn("nonExistentField");
        assertEquals("nonExistentField", columnName);
    }

    @Test
    void isAutoGeneratedId_withGeneratedValue() {
        EntityFieldExtractor<SimpleEntity> extractor = new EntityFieldExtractor<>(SimpleEntity.class);
        assertTrue(extractor.isAutoGeneratedId("id"));
    }

    @Test
    void isAutoGeneratedId_withoutGeneratedValue() {
        EntityFieldExtractor<NonAutoIdEntity> extractor = new EntityFieldExtractor<>(NonAutoIdEntity.class);
        assertFalse(extractor.isAutoGeneratedId("id"));
    }

    @Test
    void isAutoGeneratedId_withEmbeddedId() {
        EntityFieldExtractor<EntityWithEmbeddedId> extractor = new EntityFieldExtractor<>(EntityWithEmbeddedId.class);
        assertTrue(extractor.isAutoGeneratedId("compositeId"));
    }

    @Test
    void isAutoGeneratedId_nonExistingField() {
        EntityFieldExtractor<SimpleEntity> extractor = new EntityFieldExtractor<>(SimpleEntity.class);
        assertFalse(extractor.isAutoGeneratedId("nonExistentField"));
    }

    @Test
    void extractFieldValues_skipsManyToManyField() {
        EntityFieldExtractor<EntityWithManyToMany> extractor = new EntityFieldExtractor<>(EntityWithManyToMany.class);
        EntityWithManyToMany entity = new EntityWithManyToMany();
        entity.setName("test");

        List<EntityFieldExtractor.EntityFieldValue> values = extractor.extractFieldValues(entity);
        assertTrue(values.stream().anyMatch(v -> "name".equals(v.fieldName())));
    }

    @Test
    void extractFieldValues_skipsOneToOneField() {
        EntityFieldExtractor<EntityWithOneToOne> extractor = new EntityFieldExtractor<>(EntityWithOneToOne.class);
        EntityWithOneToOne entity = new EntityWithOneToOne();
        entity.setName("test");

        List<EntityFieldExtractor.EntityFieldValue> values = extractor.extractFieldValues(entity);
        assertTrue(values.stream().anyMatch(v -> "name".equals(v.fieldName())));
    }

    @Test
    void extractFieldValues_skipsEmbeddedIdField() {
        EntityFieldExtractor<EntityWithEmbeddedId> extractor = new EntityFieldExtractor<>(EntityWithEmbeddedId.class);
        EntityWithEmbeddedId entity = new EntityWithEmbeddedId();
        entity.setValue("test");

        List<EntityFieldExtractor.EntityFieldValue> values = extractor.extractFieldValues(entity);
        assertTrue(values.stream().anyMatch(v -> "value".equals(v.fieldName())));
    }

    @Test
    void resolveColumnName_emptyColumnAnnotationName_fallsBack() {
        EntityFieldExtractor<EmptyColumnNameEntity> extractor = new EntityFieldExtractor<>(EmptyColumnNameEntity.class);
        try {
            java.lang.reflect.Field f = EmptyColumnNameEntity.class.getDeclaredField("name");
            String columnName = extractor.resolveColumnName(f);
            assertNotNull(columnName);
        } catch (NoSuchFieldException e) {
            fail("Field not found");
        }
    }

    @Test
    void extractFieldValues_booleanGetter() {
        EntityFieldExtractor<BooleanEntity> extractor = new EntityFieldExtractor<>(BooleanEntity.class);
        BooleanEntity entity = new BooleanEntity();
        entity.setActive(true);

        List<EntityFieldExtractor.EntityFieldValue> values = extractor.extractFieldValues(entity);
        assertTrue(values.stream().anyMatch(v -> "active".equals(v.fieldName()) && Boolean.TRUE.equals(v.value())));
    }

    @Test
    void extractFieldValues_booleanFieldWithIsGetter() {
        EntityFieldExtractor<BooleanEntityWithIs> extractor = new EntityFieldExtractor<>(BooleanEntityWithIs.class);
        BooleanEntityWithIs entity = new BooleanEntityWithIs();
        entity.setActive(true);

        List<EntityFieldExtractor.EntityFieldValue> values = extractor.extractFieldValues(entity);
        assertTrue(values.stream().anyMatch(v -> "active".equals(v.fieldName()) && Boolean.TRUE.equals(v.value())));
    }

    @Test
    void resolveJavaFieldToDbColumn_withColumnAnnotation() {
        EntityFieldExtractor<ColumnAnnotatedEntity> extractor = new EntityFieldExtractor<>(ColumnAnnotatedEntity.class);
        String columnName = extractor.resolveJavaFieldToDbColumn("fullName");
        assertEquals("full_name", columnName);
    }

    @Test
    void extractFieldValues_nullEmbeddedValue_ignored() {
        EntityFieldExtractor<EntityWithEmbedded> extractor = new EntityFieldExtractor<>(EntityWithEmbedded.class);
        EntityWithEmbedded entity = new EntityWithEmbedded();
        entity.setName("test");

        List<EntityFieldExtractor.EntityFieldValue> values = extractor.extractFieldValues(entity);
        assertTrue(values.stream().anyMatch(v -> "name".equals(v.fieldName())));
    }

    @Test
    void extractFieldValues_nestedEmbeddedCircularDetection() {
        EntityFieldExtractor<NestedCircularA> extractor = new EntityFieldExtractor<>(NestedCircularA.class);
        NestedCircularA entity = new NestedCircularA();
        entity.name = "a";
        NestedEmbedA embedA = new NestedEmbedA();
        NestedEmbedB embedB = new NestedEmbedB();
        embedA.value = "va";
        embedB.value = "vb";
        embedA.setRefB(embedB);
        embedB.setRefA(embedA);
        entity.embedded = embedA;

        assertThrows(com.zsubera.jpa.exception.MyJpaPlusException.class, () -> extractor.extractFieldValues(entity));
    }

    @Test
    void resolveColumnName_isInsertableFalse_viaClassHierarchy() {
        EntityFieldExtractor<SubClassEntity> extractor = new EntityFieldExtractor<>(SubClassEntity.class);
        SubClassEntity entity = new SubClassEntity();
        entity.setName("test");
        entity.setInheritedField("inherited");

        List<EntityFieldExtractor.EntityFieldValue> values = extractor.extractFieldValues(entity);
        assertTrue(values.stream().anyMatch(v -> "name".equals(v.fieldName())));
        assertTrue(values.stream().anyMatch(v -> "inheritedField".equals(v.fieldName())));
    }

    @Test
    void extractFieldValues_withEmbeddedNullAttributeOverride() {
        EntityFieldExtractor<EntityWithEmbedded> extractor = new EntityFieldExtractor<>(EntityWithEmbedded.class);
        EntityWithEmbedded entity = new EntityWithEmbedded();
        entity.setName("test");

        List<EntityFieldExtractor.EntityFieldValue> values = extractor.extractFieldValues(entity);
        assertNotNull(values);
    }

    @Test
    void extractFieldValues_manyToOneAndManyToManyBothSkipped() {
        EntityFieldExtractor<FullEntity> extractor = new EntityFieldExtractor<>(FullEntity.class);
        FullEntity entity = new FullEntity();
        entity.setName("test");

        List<EntityFieldExtractor.EntityFieldValue> values = extractor.extractFieldValues(entity);
        assertTrue(values.stream().anyMatch(v -> "name".equals(v.fieldName())));
        assertFalse(values.stream().anyMatch(v -> "ref".equals(v.fieldName())));
        assertFalse(values.stream().anyMatch(v -> "refs".equals(v.fieldName())));
    }

    @Test
    void extractFieldValues_withNullEmbeddedAndOverrides() {
        EntityFieldExtractor<EntityWithOverriddenEmbedded> extractor =
            new EntityFieldExtractor<>(EntityWithOverriddenEmbedded.class);
        EntityWithOverriddenEmbedded entity = new EntityWithOverriddenEmbedded();

        List<EntityFieldExtractor.EntityFieldValue> values = extractor.extractFieldValues(entity);
        assertNotNull(values);
    }

    @Test
    void resolveIdColumnNames_multipleIds() {
        EntityFieldExtractor<MultiIdEntity> extractor = new EntityFieldExtractor<>(MultiIdEntity.class);
        List<String> idColumns = extractor.resolveIdColumnNames();
        assertTrue(idColumns.size() >= 1);
    }

    @Test
    void extractFieldValues_withNoIdField() {
        EntityFieldExtractor<NoIdEntity> extractor = new EntityFieldExtractor<>(NoIdEntity.class);
        assertThrows(IllegalStateException.class, extractor::resolveIdColumnNames);
    }

    @Test
    void resolveJavaFieldToDbColumn_parentClassField() {
        EntityFieldExtractor<SubClassEntity> extractor = new EntityFieldExtractor<>(SubClassEntity.class);
        String col = extractor.resolveJavaFieldToDbColumn("inheritedField");
        assertEquals("inherited_field", col);
    }

    @Test
    void extractFieldValues_allFieldTypes() {
        EntityFieldExtractor<AllTypesEntity> extractor = new EntityFieldExtractor<>(AllTypesEntity.class);
        AllTypesEntity entity = new AllTypesEntity();
        entity.setName("test");
        entity.setStatus(1);
        entity.setAmount(9.99);
        entity.setFlag(true);

        List<EntityFieldExtractor.EntityFieldValue> values = extractor.extractFieldValues(entity);
        assertTrue(values.size() >= 3);
    }

    @Test
    void extractFieldValues_withNullEmbeddedAndAttributeOverride() {
        EntityFieldExtractor<EntityWithOverriddenEmbeddedNull> extractor =
            new EntityFieldExtractor<>(EntityWithOverriddenEmbeddedNull.class);
        EntityWithOverriddenEmbeddedNull entity = new EntityWithOverriddenEmbeddedNull();

        List<EntityFieldExtractor.EntityFieldValue> values = extractor.extractFieldValues(entity);
        assertNotNull(values);
    }

    @Test
    void resolveColumnName_columnWithExplicitName() {
        EntityFieldExtractor<ColumnAnnotatedEntity> extractor = new EntityFieldExtractor<>(ColumnAnnotatedEntity.class);
        try {
            java.lang.reflect.Field f = ColumnAnnotatedEntity.class.getDeclaredField("fullName");
            String col = extractor.resolveColumnName(f);
            assertEquals("full_name", col);
        } catch (NoSuchFieldException e) {
            fail("Field not found");
        }
    }

    @Test
    void isAutoGeneratedId_repeatedCalls_useCache() {
        EntityFieldExtractor<SimpleEntity> extractor = new EntityFieldExtractor<>(SimpleEntity.class);
        assertTrue(extractor.isAutoGeneratedId("id"));
        assertTrue(extractor.isAutoGeneratedId("id"));
        assertFalse(extractor.isAutoGeneratedId("nonExistent"));
    }

    @Test
    void resolveIdColumnNames_callTwice_usesCache() {
        EntityFieldExtractor<SimpleEntity> extractor = new EntityFieldExtractor<>(SimpleEntity.class);
        List<String> first = extractor.resolveIdColumnNames();
        List<String> second = extractor.resolveIdColumnNames();
        assertEquals(first, second);
    }

    @Test
    void extractFieldValues_withBooleanPrimitiveAndWrapper() {
        EntityFieldExtractor<BooleanEntity> extractor = new EntityFieldExtractor<>(BooleanEntity.class);
        BooleanEntity entity = new BooleanEntity();
        entity.setActive(true);

        List<EntityFieldExtractor.EntityFieldValue> values = extractor.extractFieldValues(entity);
        assertTrue(values.stream().anyMatch(v -> "active".equals(v.fieldName())));
    }

    @Test
    void extractFieldValues_booleanEntityWithIs_getter() {
        EntityFieldExtractor<BooleanEntityWithIs> extractor = new EntityFieldExtractor<>(BooleanEntityWithIs.class);
        BooleanEntityWithIs entity = new BooleanEntityWithIs();
        entity.setActive(true);

        List<EntityFieldExtractor.EntityFieldValue> values = extractor.extractFieldValues(entity);
        assertTrue(values.stream().anyMatch(v -> "active".equals(v.fieldName())));
    }

    @Test
    void extractFieldValues_withEmbeddedId_skipsIt() {
        EntityFieldExtractor<EntityWithEmbeddedId> extractor = new EntityFieldExtractor<>(EntityWithEmbeddedId.class);
        EntityWithEmbeddedId entity = new EntityWithEmbeddedId();
        entity.setValue("test");

        List<EntityFieldExtractor.EntityFieldValue> values = extractor.extractFieldValues(entity);
        assertFalse(values.stream().anyMatch(v -> "compositeId".equals(v.fieldName())));
    }

    @Test
    void extractFieldValues_withNullEmbedded_skipsIt() {
        EntityFieldExtractor<EntityWithEmbedded> extractor = new EntityFieldExtractor<>(EntityWithEmbedded.class);
        EntityWithEmbedded entity = new EntityWithEmbedded();
        entity.setName("test");

        List<EntityFieldExtractor.EntityFieldValue> values = extractor.extractFieldValues(entity);
        assertTrue(values.stream().anyMatch(v -> "name".equals(v.fieldName())));
    }

    @Test
    void extractFieldValues_withEmbeddedAndOverrides() {
        EntityFieldExtractor<EntityWithOverriddenEmbedded> extractor =
            new EntityFieldExtractor<>(EntityWithOverriddenEmbedded.class);
        EntityWithOverriddenEmbedded entity = new EntityWithOverriddenEmbedded();
        Address address = new Address();
        address.setCity("Beijing");
        address.setZipCode("100000");
        entity.setAddress(address);

        List<EntityFieldExtractor.EntityFieldValue> values = extractor.extractFieldValues(entity);
        assertTrue(values.stream()
            .anyMatch(v -> "address.city".equals(v.fieldName()) && "override_city".equals(v.columnName())));
        assertTrue(values.stream()
            .anyMatch(v -> "address.zipCode".equals(v.fieldName()) && "zip_code".equals(v.columnName())));
    }

    @Test
    void extractFieldValues_entityWithMultipleEmbeddedNull() {
        EntityFieldExtractor<EntityWithMultipleEmbedded> extractor =
            new EntityFieldExtractor<>(EntityWithMultipleEmbedded.class);
        EntityWithMultipleEmbedded entity = new EntityWithMultipleEmbedded();
        entity.setName("test");

        List<EntityFieldExtractor.EntityFieldValue> values = extractor.extractFieldValues(entity);
        assertTrue(values.stream().anyMatch(v -> "name".equals(v.fieldName())));
    }

    @Test
    void resolveIdColumnNames_embeddedIdEntity_throws() {
        EntityFieldExtractor<EntityWithEmbeddedId> extractor = new EntityFieldExtractor<>(EntityWithEmbeddedId.class);
        assertThrows(IllegalStateException.class, extractor::resolveIdColumnNames);
    }

    @Test
    void extractFieldValues_skipsSyntheticFields() {
        EntityFieldExtractor<SimpleEntity> extractor = new EntityFieldExtractor<>(SimpleEntity.class);
        SimpleEntity entity = new SimpleEntity();
        entity.setName("test");
        List<EntityFieldExtractor.EntityFieldValue> values = extractor.extractFieldValues(entity);
        assertFalse(values.isEmpty());
    }

    @Test
    void resolveJavaFieldToDbColumn_fallbackForUnknown() {
        EntityFieldExtractor<SimpleEntity> extractor = new EntityFieldExtractor<>(SimpleEntity.class);
        String col = extractor.resolveJavaFieldToDbColumn("unknownField");
        assertEquals("unknownField", col);
    }

    @Test
    void extractFieldValues_withEmbeddableAndNullNested() {
        EntityFieldExtractor<EntityWithNestedEmbedded> extractor =
            new EntityFieldExtractor<>(EntityWithNestedEmbedded.class);
        EntityWithNestedEmbedded entity = new EntityWithNestedEmbedded();
        OuterEmbed outer = new OuterEmbed();
        outer.setLabel("outer");
        entity.setOuter(outer);

        List<EntityFieldExtractor.EntityFieldValue> values = extractor.extractFieldValues(entity);
        assertTrue(values.stream().anyMatch(v -> "outer.label".equals(v.fieldName())));
    }

    @Test
    void extractFieldValues_inheritedEntity() {
        EntityFieldExtractor<SubClassEntity> extractor = new EntityFieldExtractor<>(SubClassEntity.class);
        SubClassEntity entity = new SubClassEntity();
        entity.setName("test");
        entity.setInheritedField("inherited");

        List<EntityFieldExtractor.EntityFieldValue> values = extractor.extractFieldValues(entity);
        assertTrue(values.stream().anyMatch(v -> "name".equals(v.fieldName())));
        assertTrue(values.stream().anyMatch(v -> "inheritedField".equals(v.fieldName())));
    }

    @Test
    void extractFieldValues_withEmbeddedEntityAndNullValue() {
        EntityFieldExtractor<EntityWithNullableEmbedded> extractor =
            new EntityFieldExtractor<>(EntityWithNullableEmbedded.class);
        EntityWithNullableEmbedded entity = new EntityWithNullableEmbedded();
        entity.setName("test");

        List<EntityFieldExtractor.EntityFieldValue> values = extractor.extractFieldValues(entity);
        assertTrue(values.stream().anyMatch(v -> "name".equals(v.fieldName())));
    }

    // ========== Test Entities ==========

    @Entity
    @Table(name = "ef_simple")
    static class SimpleEntity {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;
        private String name;
        private Integer status;

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

        public Integer getStatus() {
            return status;
        }

        public void setStatus(Integer status) {
            this.status = status;
        }
    }

    @Entity
    @Table(name = "ef_column_annotated")
    static class ColumnAnnotatedEntity {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;
        @Column(name = "full_name")
        private String fullName;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getFullName() {
            return fullName;
        }

        public void setFullName(String fullName) {
            this.fullName = fullName;
        }
    }

    @Entity
    @Table(name = "ef_transient")
    static class EntityWithTransient {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;
        private String name;
        @Transient
        private String temp;

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

        public String getTemp() {
            return temp;
        }

        public void setTemp(String temp) {
            this.temp = temp;
        }
    }

    @Entity
    @Table(name = "ef_manytoone")
    static class EntityWithManyToOne {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;
        private String name;
        @ManyToOne
        @JoinColumn(name = "related_id")
        private SimpleEntity related;

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

        public SimpleEntity getRelated() {
            return related;
        }

        public void setRelated(SimpleEntity related) {
            this.related = related;
        }
    }

    @Entity
    @Table(name = "ef_onetomany")
    static class EntityWithOneToMany {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;
        private String name;
        @OneToMany
        @JoinColumn(name = "owner_id")
        private java.util.List<SimpleEntity> items = new java.util.ArrayList<>();

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

        public java.util.List<SimpleEntity> getItems() {
            return items;
        }

        public void setItems(java.util.List<SimpleEntity> items) {
            this.items = items;
        }
    }

    @Entity
    @Table(name = "ef_insertable_false")
    static class InsertableFalseEntity {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;
        private String name;
        @Column(name = "computed", insertable = false)
        private String computed;

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

        public String getComputed() {
            return computed;
        }

        public void setComputed(String computed) {
            this.computed = computed;
        }
    }

    @Embeddable
    static class Address {
        private String city;
        @Column(name = "zip_code")
        private String zipCode;

        public String getCity() {
            return city;
        }

        public void setCity(String city) {
            this.city = city;
        }

        public String getZipCode() {
            return zipCode;
        }

        public void setZipCode(String zipCode) {
            this.zipCode = zipCode;
        }
    }

    @Entity
    @Table(name = "ef_embedded")
    static class EntityWithEmbedded {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;
        private String name;
        @Embedded
        private Address address;

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

        public Address getAddress() {
            return address;
        }

        public void setAddress(Address address) {
            this.address = address;
        }
    }

    @Entity
    @Table(name = "ef_overridden_embedded")
    static class EntityWithOverriddenEmbedded {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;
        @Embedded
        @AttributeOverride(name = "city", column = @Column(name = "override_city"))
        private Address address;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public Address getAddress() {
            return address;
        }

        public void setAddress(Address address) {
            this.address = address;
        }
    }

    @Embeddable
    static class InnerEmbed {
        private String value;

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }
    }

    @Embeddable
    static class OuterEmbed {
        private String label;
        @Embedded
        private InnerEmbed inner;

        public String getLabel() {
            return label;
        }

        public void setLabel(String label) {
            this.label = label;
        }

        public InnerEmbed getInner() {
            return inner;
        }

        public void setInner(InnerEmbed inner) {
            this.inner = inner;
        }
    }

    @Entity
    @Table(name = "ef_nested_embedded")
    static class EntityWithNestedEmbedded {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;
        @Embedded
        private OuterEmbed outer;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public OuterEmbed getOuter() {
            return outer;
        }

        public void setOuter(OuterEmbed outer) {
            this.outer = outer;
        }
    }

    @Embeddable
    static class CircularEmbedA {
        private String nameA;
        @Embedded
        private CircularEmbedB b;

        public String getNameA() {
            return nameA;
        }

        public void setNameA(String nameA) {
            this.nameA = nameA;
        }

        public CircularEmbedB getB() {
            return b;
        }

        public void setB(CircularEmbedB b) {
            this.b = b;
        }
    }

    @Embeddable
    static class CircularEmbedB {
        private String nameB;
        @Embedded
        private CircularEmbedA a;

        public String getNameB() {
            return nameB;
        }

        public void setNameB(String nameB) {
            this.nameB = nameB;
        }

        public CircularEmbedA getA() {
            return a;
        }

        public void setA(CircularEmbedA a) {
            this.a = a;
        }
    }

    @Entity
    @Table(name = "ef_circular_a")
    static class CircularEntityA {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        Long id;
        String name;
        @Embedded
        CircularEmbedA embedded;

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

        public CircularEmbedA getEmbedded() {
            return embedded;
        }

        public void setEmbedded(CircularEmbedA embedded) {
            this.embedded = embedded;
        }
    }

    @Embeddable
    static class NestedEmbedA {
        String value;
        @Embedded
        NestedEmbedB refB;

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }

        public NestedEmbedB getRefB() {
            return refB;
        }

        public void setRefB(NestedEmbedB refB) {
            this.refB = refB;
        }
    }

    @Embeddable
    static class NestedEmbedB {
        String value;
        @Embedded
        NestedEmbedA refA;

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }

        public NestedEmbedA getRefA() {
            return refA;
        }

        public void setRefA(NestedEmbedA refA) {
            this.refA = refA;
        }
    }

    @Entity
    @Table(name = "ef_nested_circular_a")
    static class NestedCircularA {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        Long id;
        String name;
        @Embedded
        NestedEmbedA embedded;

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

        public NestedEmbedA getEmbedded() {
            return embedded;
        }

        public void setEmbedded(NestedEmbedA embedded) {
            this.embedded = embedded;
        }
    }

    @MappedSuperclass
    static class BaseEntity {
        private String inheritedField;

        public String getInheritedField() {
            return inheritedField;
        }

        public void setInheritedField(String inheritedField) {
            this.inheritedField = inheritedField;
        }
    }

    @Entity
    @Table(name = "ef_subclass")
    static class SubClassEntity extends BaseEntity {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;
        private String name;

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
    }

    @Entity
    @Table(name = "ef_embedded_id_entity")
    static class EntityWithEmbeddedId {
        @EmbeddedId
        private CompositeId compositeId;
        private String value;

        public CompositeId getCompositeId() {
            return compositeId;
        }

        public void setCompositeId(CompositeId compositeId) {
            this.compositeId = compositeId;
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }
    }

    @Embeddable
    static class CompositeId implements java.io.Serializable {
        @Column(name = "id_part1")
        private Long idPart1;
        @Column(name = "id_part2")
        private Long idPart2;

        public Long getIdPart1() {
            return idPart1;
        }

        public void setIdPart1(Long idPart1) {
            this.idPart1 = idPart1;
        }

        public Long getIdPart2() {
            return idPart2;
        }

        public void setIdPart2(Long idPart2) {
            this.idPart2 = idPart2;
        }
    }

    @Entity
    @Table(name = "ef_empty_column_name")
    static class EmptyColumnNameEntity {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;
        @Column(name = "")
        private String name;

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
    }

    @Entity
    @Table(name = "ef_boolean")
    static class BooleanEntity {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;
        private Boolean active;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public Boolean getActive() {
            return active;
        }

        public void setActive(Boolean active) {
            this.active = active;
        }
    }

    @Entity
    @Table(name = "ef_boolean_is")
    static class BooleanEntityWithIs {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;
        private boolean active;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public boolean isActive() {
            return active;
        }

        public void setActive(boolean active) {
            this.active = active;
        }
    }

    @Entity
    @Table(name = "ef_manytomany")
    static class EntityWithManyToMany {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;
        private String name;
        @ManyToMany
        private java.util.List<SimpleEntity> tags = new java.util.ArrayList<>();

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
    }

    @Entity
    @Table(name = "ef_onetoone")
    static class EntityWithOneToOne {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;
        private String name;
        @OneToOne
        @JoinColumn(name = "detail_id")
        private SimpleEntity detail;

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

        public SimpleEntity getDetail() {
            return detail;
        }

        public void setDetail(SimpleEntity detail) {
            this.detail = detail;
        }
    }

    @Entity
    @Table(name = "ef_non_auto_id")
    static class NonAutoIdEntity {
        @Id
        private Long id;
        private String name;

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
    }

    @Entity
    @Table(name = "ef_full")
    static class FullEntity {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;
        private String name;
        @ManyToOne
        @JoinColumn(name = "ref_id")
        private SimpleEntity ref;
        @ManyToMany
        private java.util.List<SimpleEntity> refs = new java.util.ArrayList<>();

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
    }

    @Entity
    @Table(name = "ef_overridden_embedded_null")
    static class EntityWithOverriddenEmbeddedNull {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;
        @Embedded
        @AttributeOverride(name = "city", column = @Column(name = "override_city"))
        private Address address;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public Address getAddress() {
            return address;
        }

        public void setAddress(Address address) {
            this.address = address;
        }
    }

    @Entity
    @Table(name = "ef_multi_id")
    static class MultiIdEntity {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;
        private String name;

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
    }

    @Entity
    @Table(name = "ef_no_id")
    static class NoIdEntity {
        private String name;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    @Entity
    @Table(name = "ef_all_types")
    static class AllTypesEntity {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;
        private String name;
        private Integer status;
        private Double amount;
        private Boolean flag;

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

        public Integer getStatus() {
            return status;
        }

        public void setStatus(Integer status) {
            this.status = status;
        }

        public Double getAmount() {
            return amount;
        }

        public void setAmount(Double amount) {
            this.amount = amount;
        }

        public Boolean getFlag() {
            return flag;
        }

        public void setFlag(Boolean flag) {
            this.flag = flag;
        }
    }

    @Entity
    @Table(name = "ef_nullable_embedded")
    static class EntityWithNullableEmbedded {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;
        private String name;
        @Embedded
        private Address address;

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

        public Address getAddress() {
            return address;
        }

        public void setAddress(Address address) {
            this.address = address;
        }
    }

    @Entity
    @Table(name = "ef_multi_embedded")
    static class EntityWithMultipleEmbedded {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;
        private String name;
        @Embedded
        private Address address1;
        @Embedded
        @AttributeOverride(name = "city", column = @Column(name = "addr2_city"))
        private Address address2;

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

        public Address getAddress1() {
            return address1;
        }

        public void setAddress1(Address address1) {
            this.address1 = address1;
        }

        public Address getAddress2() {
            return address2;
        }

        public void setAddress2(Address address2) {
            this.address2 = address2;
        }
    }
}
