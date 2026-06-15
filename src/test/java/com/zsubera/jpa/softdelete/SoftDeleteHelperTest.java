package com.zsubera.jpa.softdelete;

import static org.junit.jupiter.api.Assertions.*;

import com.zsubera.jpa.spec.SoftDeleteTestEntity;
import com.zsubera.jpa.spec.SoftDeleteTestEntityRepository;
import com.zsubera.jpa.spec.TestApplication;
import com.zsubera.jpa.spec.TestEntity;
import com.zsubera.jpa.spec.TestEntityRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.context.ContextConfiguration;

@DataJpaTest
@org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase(
    replace = org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = TestApplication.class)
class SoftDeleteHelperTest {

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        repository.flush();
    }

    @Autowired
    private SoftDeleteTestEntityRepository repository;

    @Autowired
    private TestEntityRepository testEntityRepository;

    @Test
    void testIsNotDeletedFiltersOutSoftDeletedRecords() {
        SoftDeleteTestEntity active = new SoftDeleteTestEntity();
        active.setName("active");
        active.setDeleted(false);
        repository.save(active);

        SoftDeleteTestEntity deleted = new SoftDeleteTestEntity();
        deleted.setName("deleted");
        deleted.setDeleted(true);
        repository.save(deleted);

        Specification<SoftDeleteTestEntity> spec = SoftDeleteHelper.isNotDeleted(SoftDeleteTestEntity.class);
        List<SoftDeleteTestEntity> result = repository.findAll(spec);

        assertEquals(1, result.size());
        assertEquals("active", result.get(0).getName());
    }

    @Test
    void testIsDeletedReturnsOnlySoftDeletedRecords() {
        SoftDeleteTestEntity active = new SoftDeleteTestEntity();
        active.setName("active");
        active.setDeleted(false);
        repository.save(active);

        SoftDeleteTestEntity deleted = new SoftDeleteTestEntity();
        deleted.setName("deleted");
        deleted.setDeleted(true);
        repository.save(deleted);

        Specification<SoftDeleteTestEntity> spec = SoftDeleteHelper.isDeleted(SoftDeleteTestEntity.class);
        List<SoftDeleteTestEntity> result = repository.findAll(spec);

        assertEquals(1, result.size());
        assertEquals("deleted", result.get(0).getName());
    }

    @Test
    void testNotDeletedQueryReturnsQuerySpecWithFilter() {
        SoftDeleteTestEntity active = new SoftDeleteTestEntity();
        active.setName("target");
        active.setDeleted(false);
        repository.save(active);

        SoftDeleteTestEntity deleted = new SoftDeleteTestEntity();
        deleted.setName("target");
        deleted.setDeleted(true);
        repository.save(deleted);

        var qs = SoftDeleteHelper.notDeletedQuery(SoftDeleteTestEntity.class);
        qs.eq(SoftDeleteTestEntity::getName, "target");
        List<SoftDeleteTestEntity> result = repository.findAll(qs.toSpecification());

        assertEquals(1, result.size());
        assertFalse(result.get(0).getDeleted());
    }

    @Test
    void testIsNotDeletedOnEntityWithoutSoftDeleteReturnsAll() {
        testEntityRepository.save(newEntity("a", 1));
        testEntityRepository.save(newEntity("b", 2));

        Specification<TestEntity> spec = SoftDeleteHelper.isNotDeleted(TestEntity.class);
        List<TestEntity> result = testEntityRepository.findAll(spec);

        assertEquals(2, result.size());
    }

    @Test
    void testIsDeletedOnEntityWithoutSoftDeleteReturnsNone() {
        testEntityRepository.save(newEntity("a", 1));

        Specification<TestEntity> spec = SoftDeleteHelper.isDeleted(TestEntity.class);
        List<TestEntity> result = testEntityRepository.findAll(spec);

        assertEquals(0, result.size());
    }

    @Test
    void testSpecCacheReturnsSameInstance() {
        Specification<SoftDeleteTestEntity> spec1 = SoftDeleteHelper.isNotDeleted(SoftDeleteTestEntity.class);
        Specification<SoftDeleteTestEntity> spec2 = SoftDeleteHelper.isNotDeleted(SoftDeleteTestEntity.class);

        assertSame(spec1, spec2);
    }

    @Test
    void testIsSoftDeletedReturnsTrueForDeletedEntity() {
        SoftDeleteTestEntity deleted = new SoftDeleteTestEntity();
        deleted.setName("deleted");
        deleted.setDeleted(true);
        repository.save(deleted);

        assertTrue(SoftDeleteHelper.isSoftDeleted(SoftDeleteTestEntity.class, deleted));
    }

    @Test
    void testNotDeletedQuery() {
        SoftDeleteTestEntity active = new SoftDeleteTestEntity();
        active.setName("active");
        active.setDeleted(false);
        repository.save(active);

        SoftDeleteTestEntity deleted = new SoftDeleteTestEntity();
        deleted.setName("deleted");
        deleted.setDeleted(true);
        repository.save(deleted);

        // notDeletedQuery returns a QuerySpec with soft delete filter
        var qs = SoftDeleteHelper.notDeletedQuery(SoftDeleteTestEntity.class);
        assertFalse(qs.conditions().isEmpty(), "notDeletedQuery should have at least one condition");
    }

    @Test
    void testIsSoftDeletedReturnsFalseForEntityWithoutAnnotation() {
        TestEntity entity = newEntity("test", 1);

        assertFalse(SoftDeleteHelper.isSoftDeleted(TestEntity.class, entity));
    }

    @Test
    void testFindSoftDeleteFieldReturnsFieldNameForAnnotatedEntity() {
        String fieldName = SoftDeleteHelper.findSoftDeleteField(SoftDeleteTestEntity.class);
        assertEquals("deleted", fieldName);
    }

    @Test
    void testFindSoftDeleteFieldReturnsNullForEntityWithoutAnnotation() {
        String fieldName = SoftDeleteHelper.findSoftDeleteField(TestEntity.class);
        assertNull(fieldName);
    }

    @Test
    void testFindSoftDeleteFieldIsCached() {
        String first = SoftDeleteHelper.findSoftDeleteField(SoftDeleteTestEntity.class);
        String second = SoftDeleteHelper.findSoftDeleteField(SoftDeleteTestEntity.class);
        assertSame(first, second);
    }

    @Test
    void testIsSoftDeletedOnNullDeletedValue() {
        SoftDeleteTestEntity entity = new SoftDeleteTestEntity();
        entity.setName("nullDeleted");
        entity.setDeleted(null);
        repository.save(entity);

        assertFalse(SoftDeleteHelper.isSoftDeleted(SoftDeleteTestEntity.class, entity));
    }

    @Test
    void testFindSoftDeleteFieldOnInvalidEntityDoesNotThrow() {
        // Should return null gracefully, not throw
        assertNull(SoftDeleteHelper.findSoftDeleteField(Object.class));
    }

    @Test
    void testSoftDeleteByIdsWithEmptyIds() {
        // Empty IDs should return 0 without hitting the database
        int count = SoftDeleteHelper.softDeleteByIds(org.mockito.Mockito.mock(jakarta.persistence.EntityManager.class),
            SoftDeleteTestEntity.class, List.of());
        assertEquals(0, count);
    }

    @Test
    void testSoftDeleteByIdsWithNullIds() {
        // Null IDs should return 0 without hitting the database
        int count = SoftDeleteHelper.softDeleteByIds(org.mockito.Mockito.mock(jakarta.persistence.EntityManager.class),
            SoftDeleteTestEntity.class, null);
        assertEquals(0, count);
    }

    @Test
    void testEscapeIdentifierWithValidInput() {
        String result = SoftDeleteHelper.validateIdentifier("test_table");
        assertEquals("test_table", result);
    }

    @Test
    void testEscapeIdentifierWithSchemaTable() {
        // validateIdentifier validates each segment individually without quoting
        String result = SoftDeleteHelper.validateIdentifier("schema.table");
        assertEquals("schema.table", result);
    }

    @Test
    void testEscapeIdentifierWithNullThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> SoftDeleteHelper.validateIdentifier(null));
    }

    @Test
    void testEscapeIdentifierWithEmptyThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> SoftDeleteHelper.validateIdentifier(""));
    }

    @Test
    void testEscapeIdentifierWithInvalidCharsThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> SoftDeleteHelper.validateIdentifier("table;DROP"));
    }

    @Test
    void testSoftDeleteAllWithNullEmThrowsException() {
        assertThrows(IllegalArgumentException.class,
            () -> SoftDeleteHelper.softDeleteAll(null, SoftDeleteTestEntity.class, true));
    }

    @Test
    void testSoftDeleteAllWithNullClassThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> SoftDeleteHelper.softDeleteAll(null, null, true));
    }

    @Test
    void testSoftDeleteAllWithoutAllowUnconditionalThrowsException() {

        // Use a mock EntityManager to pass the null check for em
        jakarta.persistence.EntityManager mockEm = org.mockito.Mockito.mock(jakarta.persistence.EntityManager.class);
        assertThrows(IllegalStateException.class,
            () -> SoftDeleteHelper.softDeleteAll(mockEm, SoftDeleteTestEntity.class, false));
    }

    @Test
    void testSoftDeleteByIdsWithNullEmThrowsException() {
        assertThrows(IllegalArgumentException.class,
            () -> SoftDeleteHelper.softDeleteByIds(null, SoftDeleteTestEntity.class, List.of(1L)));
    }

    @Test
    void testSoftDeleteByIdsWithNullClassThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> SoftDeleteHelper.softDeleteByIds(null, null, List.of(1L)));
    }

    @Test
    void testIsNotDeletedWithNullClass() {
        // findSoftDeleteField throws NPE when entityClass is null
        assertThrows(NullPointerException.class, () -> SoftDeleteHelper.isNotDeleted(null));
    }

    @Test
    void testIsDeletedWithNullClass() {
        // findSoftDeleteField throws NPE when entityClass is null
        assertThrows(NullPointerException.class, () -> SoftDeleteHelper.isDeleted(null));
    }

    @Test
    void testIsSoftDeletedWithDeletedEntity() {
        SoftDeleteTestEntity entity = new SoftDeleteTestEntity();
        entity.setName("deleted");
        entity.setDeleted(true);
        assertTrue(SoftDeleteHelper.isSoftDeleted(SoftDeleteTestEntity.class, entity));
    }

    @Test
    void testIsSoftDeletedWithActiveEntity() {
        SoftDeleteTestEntity entity = new SoftDeleteTestEntity();
        entity.setName("active");
        entity.setDeleted(false);
        assertFalse(SoftDeleteHelper.isSoftDeleted(SoftDeleteTestEntity.class, entity));
    }

    @Test
    void testIsSoftDeletedWithNullEntityThrowsException() {
        assertThrows(IllegalArgumentException.class,
            () -> SoftDeleteHelper.isSoftDeleted(SoftDeleteTestEntity.class, null));
    }

    @Test
    void testIsSoftDeletedWithNullClassThrowsException() {
        assertThrows(IllegalArgumentException.class,
            () -> SoftDeleteHelper.isSoftDeleted(null, new SoftDeleteTestEntity()));
    }

    @Test
    void testIsSoftDeletedWithNonSoftDeleteEntity() {
        // TestEntity has no @SoftDelete field, should return false
        TestEntity entity = new TestEntity();
        entity.setName("test");
        assertFalse(SoftDeleteHelper.isSoftDeleted(TestEntity.class, entity));
    }

    @Test
    void testFindSoftDeleteFieldWithNonSoftDeleteEntity() {
        // TestEntity has no @SoftDelete field
        assertNull(SoftDeleteHelper.findSoftDeleteField(TestEntity.class));
    }

    @Test
    void testNotDeletedQueryHasConditions() {
        // notDeletedQuery returns a QuerySpec with soft delete filter
        var qs = SoftDeleteHelper.notDeletedQuery(SoftDeleteTestEntity.class);
        assertFalse(qs.conditions().isEmpty(), "notDeletedQuery should have at least one condition");
    }

    @Test
    void testEscapeIdentifier_simpleIdentifier() {
        assertEquals("my_column", SoftDeleteHelper.validateIdentifier("my_column"));
    }

    @Test
    void testEscapeIdentifier_schemaQualified() {
        assertEquals("myschema.mytable", SoftDeleteHelper.validateIdentifier("myschema.mytable"));
    }

    @Test
    void testEscapeIdentifier_catalogSchemaQualified() {
        assertEquals("mycatalog.myschema.mytable", SoftDeleteHelper.validateIdentifier("mycatalog.myschema.mytable"));
    }

    @Test
    void testEscapeIdentifier_invalidCharactersThrows() {
        assertThrows(IllegalArgumentException.class, () -> SoftDeleteHelper.validateIdentifier("my column"));
        assertThrows(IllegalArgumentException.class, () -> SoftDeleteHelper.validateIdentifier("my-table"));
        assertThrows(IllegalArgumentException.class, () -> SoftDeleteHelper.validateIdentifier("'; DROP TABLE --"));
    }

    @Test
    void testEscapeIdentifier_nullOrEmptyThrows() {
        assertThrows(IllegalArgumentException.class, () -> SoftDeleteHelper.validateIdentifier(null));
        assertThrows(IllegalArgumentException.class, () -> SoftDeleteHelper.validateIdentifier(""));
    }

    private TestEntity newEntity(String name, int status) {
        TestEntity entity = new TestEntity();
        entity.setName(name);
        entity.setStatus(status);
        return entity;
    }
}
