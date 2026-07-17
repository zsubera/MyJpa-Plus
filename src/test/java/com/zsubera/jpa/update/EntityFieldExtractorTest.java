package com.zsubera.jpa.update;

import static org.junit.jupiter.api.Assertions.*;

import jakarta.persistence.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class EntityFieldExtractorTest {

    @Entity
    @Table(name = "test_entity")
    static class TestEntity {
        @Id
        @GeneratedValue
        private Long id;

        @Column(name = "full_name")
        private String name;

        private String email;

        @Transient
        private String ignored;

        @Version
        private int version;

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

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getIgnored() {
            return ignored;
        }

        public void setIgnored(String ignored) {
            this.ignored = ignored;
        }

        public int getVersion() {
            return version;
        }

        public void setVersion(int version) {
            this.version = version;
        }
    }

    @Embeddable
    static class Address {
        @Column(name = "street_name")
        private String street;
        private String city;

        public String getStreet() {
            return street;
        }

        public void setStreet(String street) {
            this.street = street;
        }

        public String getCity() {
            return city;
        }

        public void setCity(String city) {
            this.city = city;
        }
    }

    @Entity
    @Table(name = "test_entity_embedded")
    static class EntityWithEmbedded {
        @Id
        @GeneratedValue
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
    @Table(name = "test_entity_with_column")
    static class EntityWithNoAnnotation {
        @Id
        @GeneratedValue
        private Long id;

        private String camelCaseField;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getCamelCaseField() {
            return camelCaseField;
        }

        public void setCamelCaseField(String camelCaseField) {
            this.camelCaseField = camelCaseField;
        }
    }

    @Test
    void extractFieldValues_null_throws() {
        EntityFieldExtractor<TestEntity> extractor = new EntityFieldExtractor<>(TestEntity.class);
        assertThrows(IllegalArgumentException.class, () -> extractor.extractFieldValues(null));
    }

    @Test
    void extractFieldValues_simpleEntity_extractsFields() {
        EntityFieldExtractor<TestEntity> extractor = new EntityFieldExtractor<>(TestEntity.class);
        TestEntity entity = new TestEntity();
        entity.setId(1L);
        entity.setName("John");
        entity.setEmail("john@example.com");

        List<EntityFieldExtractor.EntityFieldValue> fields = extractor.extractFieldValues(entity);

        assertFalse(fields.isEmpty());
        assertTrue(fields.stream().anyMatch(f -> f.fieldName().equals("name")));
        assertTrue(fields.stream().anyMatch(f -> f.fieldName().equals("email")));
        assertTrue(fields.stream().anyMatch(f -> f.fieldName().equals("id")));
    }

    @Test
    void extractFieldValues_excludesTransientAndVersion() {
        EntityFieldExtractor<TestEntity> extractor = new EntityFieldExtractor<>(TestEntity.class);
        TestEntity entity = new TestEntity();

        List<EntityFieldExtractor.EntityFieldValue> fields = extractor.extractFieldValues(entity);

        assertFalse(fields.stream().anyMatch(f -> f.fieldName().equals("ignored")));
        assertFalse(fields.stream().anyMatch(f -> f.fieldName().equals("version")));
    }

    @Test
    void extractFieldValues_withEmbedded() {
        EntityFieldExtractor<EntityWithEmbedded> extractor = new EntityFieldExtractor<>(EntityWithEmbedded.class);
        EntityWithEmbedded entity = new EntityWithEmbedded();
        entity.setName("Test");
        Address addr = new Address();
        addr.setStreet("Main St");
        addr.setCity("NYC");
        entity.setAddress(addr);

        List<EntityFieldExtractor.EntityFieldValue> fields = extractor.extractFieldValues(entity);

        assertTrue(fields.stream().anyMatch(f -> f.fieldName().equals("name")));
        assertTrue(fields.stream().anyMatch(f -> f.fieldName().equals("address.street")));
        assertTrue(fields.stream().anyMatch(f -> f.fieldName().equals("address.city")));
    }

    @Test
    void resolveColumnName_columnAnnotation() {
        EntityFieldExtractor<TestEntity> extractor = new EntityFieldExtractor<>(TestEntity.class);
        try {
            java.lang.reflect.Field nameField = TestEntity.class.getDeclaredField("name");
            assertEquals("full_name", extractor.resolveColumnName(nameField));
        } catch (NoSuchFieldException e) {
            fail(e);
        }
    }

    @Test
    void resolveColumnName_noAnnotation_snakeCase() {
        EntityFieldExtractor<EntityWithNoAnnotation> extractor =
            new EntityFieldExtractor<>(EntityWithNoAnnotation.class);
        try {
            java.lang.reflect.Field field = EntityWithNoAnnotation.class.getDeclaredField("camelCaseField");
            assertEquals("camel_case_field", extractor.resolveColumnName(field));
        } catch (NoSuchFieldException e) {
            fail(e);
        }
    }

    @Test
    void isAutoGeneratedId_generatedField_returnsTrue() {
        EntityFieldExtractor<TestEntity> extractor = new EntityFieldExtractor<>(TestEntity.class);
        assertTrue(extractor.isAutoGeneratedId("id"));
    }

    @Test
    void isAutoGeneratedId_nonGeneratedField_returnsFalse() {
        EntityFieldExtractor<TestEntity> extractor = new EntityFieldExtractor<>(TestEntity.class);
        assertFalse(extractor.isAutoGeneratedId("name"));
    }

    @Test
    void resolveIdColumnNames_returnsIdColumns() {
        EntityFieldExtractor<TestEntity> extractor = new EntityFieldExtractor<>(TestEntity.class);
        List<String> idColumns = extractor.resolveIdColumnNames();
        assertFalse(idColumns.isEmpty());
    }

    @Test
    void resolveJavaFieldToDbColumn_knownField() {
        EntityFieldExtractor<TestEntity> extractor = new EntityFieldExtractor<>(TestEntity.class);
        assertEquals("full_name", extractor.resolveJavaFieldToDbColumn("name"));
    }

    @Test
    void resolveJavaFieldToDbColumn_unknownField_returnsFieldName() {
        EntityFieldExtractor<TestEntity> extractor = new EntityFieldExtractor<>(TestEntity.class);
        assertEquals("unknownfield", extractor.resolveJavaFieldToDbColumn("unknownfield"));
    }

    @Entity
    @Table(name = "no_id_entity")
    static class NoIdEntity {
        private String name;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    @Test
    void resolveIdColumnNames_noId_throws() {
        EntityFieldExtractor<NoIdEntity> extractor = new EntityFieldExtractor<>(NoIdEntity.class);
        assertThrows(IllegalStateException.class, extractor::resolveIdColumnNames);
    }

    // ---- @Embedded null value ----

    @Test
    void extractFieldValues_embeddedNullValue_skipsEmbedded() {
        EntityWithEmbedded entity = new EntityWithEmbedded();
        entity.setName("test");
        entity.setAddress(null); // null embedded

        EntityFieldExtractor<EntityWithEmbedded> extractor = new EntityFieldExtractor<>(EntityWithEmbedded.class);
        List<EntityFieldExtractor.EntityFieldValue> fields = extractor.extractFieldValues(entity);

        assertTrue(fields.stream().anyMatch(f -> f.fieldName().equals("name")));
        assertFalse(fields.stream().anyMatch(f -> f.fieldName().startsWith("address.")));
    }

    // ---- @Column(updatable=false) is NOT excluded (by design) ----

    @Entity
    @Table(name = "entity_with_non_updatable")
    static class EntityWithNonUpdatable {
        @Id
        @GeneratedValue
        private Long id;

        private String name;

        @Column(updatable = false)
        private String createdAt;

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

        public String getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(String createdAt) {
            this.createdAt = createdAt;
        }
    }

    @Test
    void extractFieldValues_keepsNonUpdatableColumn() {
        // @Column(updatable=false) is intentionally kept — the field can be inserted
        EntityWithNonUpdatable entity = new EntityWithNonUpdatable();
        entity.setName("test");
        entity.setCreatedAt("2024-01-01");

        EntityFieldExtractor<EntityWithNonUpdatable> extractor =
            new EntityFieldExtractor<>(EntityWithNonUpdatable.class);
        List<EntityFieldExtractor.EntityFieldValue> fields = extractor.extractFieldValues(entity);

        assertTrue(fields.stream().anyMatch(f -> f.fieldName().equals("name")));
        assertTrue(fields.stream().anyMatch(f -> f.fieldName().equals("createdAt")));
    }

    // ---- @Column(insertable=false) IS excluded ----

    @Entity
    @Table(name = "entity_with_non_insertable")
    static class EntityWithNonInsertable {
        @Id
        @GeneratedValue
        private Long id;

        private String name;

        @Column(insertable = false)
        private String computedField;

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

        public String getComputedField() {
            return computedField;
        }

        public void setComputedField(String computedField) {
            this.computedField = computedField;
        }
    }

    @Test
    void extractFieldValues_excludesNonInsertableColumn() {
        EntityWithNonInsertable entity = new EntityWithNonInsertable();
        entity.setName("test");
        entity.setComputedField("computed");

        EntityFieldExtractor<EntityWithNonInsertable> extractor =
            new EntityFieldExtractor<>(EntityWithNonInsertable.class);
        List<EntityFieldExtractor.EntityFieldValue> fields = extractor.extractFieldValues(entity);

        assertTrue(fields.stream().anyMatch(f -> f.fieldName().equals("name")));
        assertFalse(fields.stream().anyMatch(f -> f.fieldName().equals("computedField")));
    }

    @Embeddable
    static class EmbeddableWithVersion {
        private String street;

        @Version
        private int version;

        public String getStreet() {
            return street;
        }

        public void setStreet(String street) {
            this.street = street;
        }

        public int getVersion() {
            return version;
        }

        public void setVersion(int version) {
            this.version = version;
        }
    }

    @Entity
    @Table(name = "test_entity_embedded_version")
    static class EntityWithEmbeddedVersion {
        @Id
        @GeneratedValue
        private Long id;

        private String name;

        @Embedded
        private EmbeddableWithVersion address;

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

        public EmbeddableWithVersion getAddress() {
            return address;
        }

        public void setAddress(EmbeddableWithVersion address) {
            this.address = address;
        }
    }

    @Test
    void extractFieldValues_excludesVersionFieldFromEmbedded() {
        EmbeddableWithVersion address = new EmbeddableWithVersion();
        address.setStreet("Main St");
        address.setVersion(5);

        EntityWithEmbeddedVersion entity = new EntityWithEmbeddedVersion();
        entity.setName("test");
        entity.setAddress(address);

        EntityFieldExtractor<EntityWithEmbeddedVersion> extractor =
            new EntityFieldExtractor<>(EntityWithEmbeddedVersion.class);
        List<EntityFieldExtractor.EntityFieldValue> fields = extractor.extractFieldValues(entity);

        assertTrue(fields.stream().anyMatch(f -> f.fieldName().equals("name")));
        assertTrue(fields.stream().anyMatch(f -> f.fieldName().equals("address.street")));
        assertFalse(fields.stream().anyMatch(f -> f.fieldName().equals("address.version")));
    }
}
