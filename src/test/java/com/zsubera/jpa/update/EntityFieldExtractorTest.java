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
}
