package com.zsubera.jpa.softdelete;

import static org.junit.jupiter.api.Assertions.*;

import com.zsubera.jpa.spec.SoftDeleteEnumTestEntity;
import com.zsubera.jpa.spec.SoftDeleteEnumTestEntityRepository;
import com.zsubera.jpa.spec.SoftDeleteIntTestEntity;
import com.zsubera.jpa.spec.SoftDeleteIntTestEntityRepository;
import com.zsubera.jpa.spec.SoftDeleteStringTestEntity;
import com.zsubera.jpa.spec.SoftDeleteStringTestEntityRepository;
import com.zsubera.jpa.spec.SoftDeleteTestEntity;
import com.zsubera.jpa.spec.SoftDeleteTestEntityRepository;
import com.zsubera.jpa.spec.TestApplication;
import com.zsubera.jpa.spec.TestEntity;
import com.zsubera.jpa.spec.TestEntityRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
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
        intRepository.deleteAll();
        enumRepository.deleteAll();
        stringRepository.deleteAll();
        testEntityRepository.deleteAll();
        repository.flush();
    }

    @Autowired
    private SoftDeleteTestEntityRepository repository;

    @Autowired
    private TestEntityRepository testEntityRepository;

    @Autowired
    private SoftDeleteIntTestEntityRepository intRepository;

    @Autowired
    private SoftDeleteEnumTestEntityRepository enumRepository;

    @Autowired
    private SoftDeleteStringTestEntityRepository stringRepository;

    @PersistenceContext
    private EntityManager em;

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
        int count = SoftDeleteBulkExecutor.softDeleteByIds(
            org.mockito.Mockito.mock(jakarta.persistence.EntityManager.class), SoftDeleteTestEntity.class, List.of());
        assertEquals(0, count);
    }

    @Test
    void testSoftDeleteByIdsWithNullIds() {
        // Null IDs should return 0 without hitting the database
        int count = SoftDeleteBulkExecutor.softDeleteByIds(
            org.mockito.Mockito.mock(jakarta.persistence.EntityManager.class), SoftDeleteTestEntity.class, null);
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
            () -> SoftDeleteBulkExecutor.softDeleteAll(null, SoftDeleteTestEntity.class, true));
    }

    @Test
    void testSoftDeleteAllWithNullClassThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> SoftDeleteBulkExecutor.softDeleteAll(null, null, true));
    }

    @Test
    void testSoftDeleteAllWithoutAllowUnconditionalThrowsException() {

        // Use a mock EntityManager to pass the null check for em
        jakarta.persistence.EntityManager mockEm = org.mockito.Mockito.mock(jakarta.persistence.EntityManager.class);
        assertThrows(IllegalStateException.class,
            () -> SoftDeleteBulkExecutor.softDeleteAll(mockEm, SoftDeleteTestEntity.class, false));
    }

    @Test
    void testSoftDeleteByIdsWithNullEmThrowsException() {
        assertThrows(IllegalArgumentException.class,
            () -> SoftDeleteBulkExecutor.softDeleteByIds(null, SoftDeleteTestEntity.class, List.of(1L)));
    }

    @Test
    void testSoftDeleteByIdsWithNullClassThrowsException() {
        assertThrows(IllegalArgumentException.class,
            () -> SoftDeleteBulkExecutor.softDeleteByIds(null, null, List.of(1L)));
    }

    @Test
    void testIsNotDeletedWithNullClass() {
        assertThrows(IllegalArgumentException.class, () -> SoftDeleteHelper.isNotDeleted(null));
    }

    @Test
    void testIsDeletedWithNullClass() {
        assertThrows(IllegalArgumentException.class, () -> SoftDeleteHelper.isDeleted(null));
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

    @Test
    void testSoftDeleteAllWithMaxRowsLimit() {
        SoftDeleteTestEntity e1 = new SoftDeleteTestEntity();
        e1.setName("limit1");
        e1.setDeleted(false);
        repository.save(e1);
        SoftDeleteTestEntity e2 = new SoftDeleteTestEntity();
        e2.setName("limit2");
        e2.setDeleted(false);
        repository.save(e2);
        repository.flush();

        jakarta.persistence.EntityManager mockEm = org.mockito.Mockito.mock(jakarta.persistence.EntityManager.class);
        jakarta.persistence.Query mockCountQuery = org.mockito.Mockito.mock(jakarta.persistence.Query.class);
        jakarta.persistence.Query mockUpdateQuery = org.mockito.Mockito.mock(jakarta.persistence.Query.class);
        org.mockito.Mockito.when(mockEm.createNativeQuery(org.mockito.ArgumentMatchers.contains("SELECT COUNT")))
            .thenReturn(mockCountQuery);
        org.mockito.Mockito.when(mockCountQuery.getSingleResult()).thenReturn(5L);
        org.mockito.Mockito
            .when(
                mockCountQuery.setParameter(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any()))
            .thenReturn(mockCountQuery);
        org.mockito.Mockito.when(mockEm.createNativeQuery(org.mockito.ArgumentMatchers.contains("UPDATE")))
            .thenReturn(mockUpdateQuery);
        org.mockito.Mockito
            .when(
                mockUpdateQuery.setParameter(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any()))
            .thenReturn(mockUpdateQuery);
        // Return more updated rows than maxRows to trigger post-update limit check
        org.mockito.Mockito.when(mockUpdateQuery.executeUpdate()).thenReturn(5);
        jakarta.persistence.EntityTransaction mockTx =
            org.mockito.Mockito.mock(jakarta.persistence.EntityTransaction.class);
        org.mockito.Mockito.when(mockEm.getTransaction()).thenReturn(mockTx);
        org.mockito.Mockito.when(mockTx.isActive()).thenReturn(true);

        assertThrows(IllegalStateException.class,
            () -> SoftDeleteBulkExecutor.softDeleteAll(mockEm, SoftDeleteTestEntity.class, true, 1));
    }

    @Test
    void testSoftDeleteAllWithUnlimitedRows() {
        jakarta.persistence.EntityManager mockEm = org.mockito.Mockito.mock(jakarta.persistence.EntityManager.class);
        jakarta.persistence.Query mockUpdateQuery = org.mockito.Mockito.mock(jakarta.persistence.Query.class);
        org.mockito.Mockito.when(mockEm.createNativeQuery(org.mockito.ArgumentMatchers.contains("UPDATE")))
            .thenReturn(mockUpdateQuery);
        org.mockito.Mockito
            .when(
                mockUpdateQuery.setParameter(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any()))
            .thenReturn(mockUpdateQuery);
        org.mockito.Mockito.when(mockUpdateQuery.executeUpdate()).thenReturn(5);

        int result = SoftDeleteBulkExecutor.softDeleteAll(mockEm, SoftDeleteTestEntity.class, true, -1);
        assertEquals(5, result);
    }

    @Test
    void testSoftDeleteAllWithDefaultMaxRows() {
        jakarta.persistence.EntityManager mockEm = org.mockito.Mockito.mock(jakarta.persistence.EntityManager.class);
        jakarta.persistence.Query mockCountQuery = org.mockito.Mockito.mock(jakarta.persistence.Query.class);
        jakarta.persistence.Query mockUpdateQuery = org.mockito.Mockito.mock(jakarta.persistence.Query.class);
        // Mock probe query (SELECT 1 WHERE ... LIMIT) — returns 3 results to trigger COUNT path
        jakarta.persistence.Query mockProbeQuery = org.mockito.Mockito.mock(jakarta.persistence.Query.class);
        org.mockito.Mockito.when(mockEm.createNativeQuery(org.mockito.ArgumentMatchers.contains("SELECT 1")))
            .thenReturn(mockProbeQuery);
        org.mockito.Mockito.when(mockProbeQuery.setMaxResults(org.mockito.ArgumentMatchers.anyInt()))
            .thenReturn(mockProbeQuery);
        java.util.List<Object> probeResults = java.util.List.of(new Object(), new Object(), new Object());
        org.mockito.Mockito.when(mockProbeQuery.getResultList()).thenReturn(probeResults);
        org.mockito.Mockito.when(mockEm.createNativeQuery(org.mockito.ArgumentMatchers.contains("SELECT COUNT")))
            .thenReturn(mockCountQuery);
        org.mockito.Mockito.when(mockCountQuery.getSingleResult()).thenReturn(3L);
        org.mockito.Mockito
            .when(
                mockCountQuery.setParameter(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any()))
            .thenReturn(mockCountQuery);
        org.mockito.Mockito.when(mockCountQuery.setFirstResult(org.mockito.ArgumentMatchers.anyInt()))
            .thenReturn(mockCountQuery);
        org.mockito.Mockito.when(mockCountQuery.setMaxResults(org.mockito.ArgumentMatchers.anyInt()))
            .thenReturn(mockCountQuery);
        org.mockito.Mockito.when(mockEm.createNativeQuery(org.mockito.ArgumentMatchers.contains("UPDATE")))
            .thenReturn(mockUpdateQuery);
        org.mockito.Mockito
            .when(
                mockUpdateQuery.setParameter(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any()))
            .thenReturn(mockUpdateQuery);
        org.mockito.Mockito.when(mockUpdateQuery.executeUpdate()).thenReturn(3);

        int result = SoftDeleteBulkExecutor.softDeleteAll(mockEm, SoftDeleteTestEntity.class, true);
        assertEquals(3, result);
    }

    // ===== Integer soft delete type =====

    @Test
    void testIsSoftDeleted_integerType_deleted() {
        SoftDeleteIntTestEntity entity = new SoftDeleteIntTestEntity();
        entity.setName("deleted");
        entity.setDeleted(1);
        assertTrue(SoftDeleteHelper.isSoftDeleted(SoftDeleteIntTestEntity.class, entity));
    }

    @Test
    void testIsSoftDeleted_integerType_active() {
        SoftDeleteIntTestEntity entity = new SoftDeleteIntTestEntity();
        entity.setName("active");
        entity.setDeleted(0);
        assertFalse(SoftDeleteHelper.isSoftDeleted(SoftDeleteIntTestEntity.class, entity));
    }

    @Test
    void testIsNotDeleted_integerType_filtersCorrectly() {
        SoftDeleteIntTestEntity active = new SoftDeleteIntTestEntity();
        active.setName("active");
        active.setDeleted(0);
        intRepository.save(active);

        SoftDeleteIntTestEntity deleted = new SoftDeleteIntTestEntity();
        deleted.setName("deleted");
        deleted.setDeleted(1);
        intRepository.save(deleted);

        List<SoftDeleteIntTestEntity> result =
            intRepository.findAll(SoftDeleteHelper.isNotDeleted(SoftDeleteIntTestEntity.class));
        assertEquals(1, result.size());
        assertEquals("active", result.get(0).getName());
    }

    @Test
    void testIsDeleted_integerType_returnsOnlyDeleted() {
        SoftDeleteIntTestEntity active = new SoftDeleteIntTestEntity();
        active.setName("active");
        active.setDeleted(0);
        intRepository.save(active);

        SoftDeleteIntTestEntity deleted = new SoftDeleteIntTestEntity();
        deleted.setName("deleted");
        deleted.setDeleted(1);
        intRepository.save(deleted);

        List<SoftDeleteIntTestEntity> result =
            intRepository.findAll(SoftDeleteHelper.isDeleted(SoftDeleteIntTestEntity.class));
        assertEquals(1, result.size());
        assertEquals("deleted", result.get(0).getName());
    }

    @Test
    void testNotDeletedQuery_integerType() {
        SoftDeleteIntTestEntity active = new SoftDeleteIntTestEntity();
        active.setName("target");
        active.setDeleted(0);
        intRepository.save(active);

        SoftDeleteIntTestEntity deleted = new SoftDeleteIntTestEntity();
        deleted.setName("target");
        deleted.setDeleted(1);
        intRepository.save(deleted);

        var qs = SoftDeleteHelper.notDeletedQuery(SoftDeleteIntTestEntity.class);
        qs.eq(SoftDeleteIntTestEntity::getName, "target");
        List<SoftDeleteIntTestEntity> result = intRepository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
        assertEquals(0, result.get(0).getDeleted().intValue());
    }

    @Test
    void testFindSoftDeleteField_integerType() {
        String fieldName = SoftDeleteHelper.findSoftDeleteField(SoftDeleteIntTestEntity.class);
        assertEquals("deleted", fieldName);
    }

    // ===== Enum soft delete type =====

    @Test
    void testIsSoftDeleted_enumType_deleted() {
        SoftDeleteEnumTestEntity entity = new SoftDeleteEnumTestEntity();
        entity.setName("deleted");
        entity.setStatus(SoftDeleteEnumTestEntity.Status.ARCHIVED);
        assertTrue(SoftDeleteHelper.isSoftDeleted(SoftDeleteEnumTestEntity.class, entity));
    }

    @Test
    void testIsSoftDeleted_enumType_active() {
        SoftDeleteEnumTestEntity entity = new SoftDeleteEnumTestEntity();
        entity.setName("active");
        entity.setStatus(SoftDeleteEnumTestEntity.Status.ACTIVE);
        assertFalse(SoftDeleteHelper.isSoftDeleted(SoftDeleteEnumTestEntity.class, entity));
    }

    @Test
    void testIsNotDeleted_enumType_filtersCorrectly() {
        SoftDeleteEnumTestEntity active = new SoftDeleteEnumTestEntity();
        active.setName("active");
        active.setStatus(SoftDeleteEnumTestEntity.Status.ACTIVE);
        enumRepository.save(active);

        SoftDeleteEnumTestEntity deleted = new SoftDeleteEnumTestEntity();
        deleted.setName("deleted");
        deleted.setStatus(SoftDeleteEnumTestEntity.Status.ARCHIVED);
        enumRepository.save(deleted);

        List<SoftDeleteEnumTestEntity> result =
            enumRepository.findAll(SoftDeleteHelper.isNotDeleted(SoftDeleteEnumTestEntity.class));
        assertEquals(1, result.size());
        assertEquals("active", result.get(0).getName());
    }

    @Test
    void testIsDeleted_enumType_returnsOnlyDeleted() {
        SoftDeleteEnumTestEntity active = new SoftDeleteEnumTestEntity();
        active.setName("active");
        active.setStatus(SoftDeleteEnumTestEntity.Status.ACTIVE);
        enumRepository.save(active);

        SoftDeleteEnumTestEntity deleted = new SoftDeleteEnumTestEntity();
        deleted.setName("deleted");
        deleted.setStatus(SoftDeleteEnumTestEntity.Status.ARCHIVED);
        enumRepository.save(deleted);

        List<SoftDeleteEnumTestEntity> result =
            enumRepository.findAll(SoftDeleteHelper.isDeleted(SoftDeleteEnumTestEntity.class));
        assertEquals(1, result.size());
        assertEquals("deleted", result.get(0).getName());
    }

    @Test
    void testNotDeletedQuery_enumType() {
        SoftDeleteEnumTestEntity active = new SoftDeleteEnumTestEntity();
        active.setName("target");
        active.setStatus(SoftDeleteEnumTestEntity.Status.ACTIVE);
        enumRepository.save(active);

        SoftDeleteEnumTestEntity deleted = new SoftDeleteEnumTestEntity();
        deleted.setName("target");
        deleted.setStatus(SoftDeleteEnumTestEntity.Status.ARCHIVED);
        enumRepository.save(deleted);

        var qs = SoftDeleteHelper.notDeletedQuery(SoftDeleteEnumTestEntity.class);
        qs.eq(SoftDeleteEnumTestEntity::getName, "target");
        List<SoftDeleteEnumTestEntity> result = enumRepository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
        assertEquals(SoftDeleteEnumTestEntity.Status.ACTIVE, result.get(0).getStatus());
    }

    @Test
    void testFindSoftDeleteField_enumType() {
        String fieldName = SoftDeleteHelper.findSoftDeleteField(SoftDeleteEnumTestEntity.class);
        assertEquals("status", fieldName);
    }

    // ===== String soft delete type =====

    @Test
    void testIsSoftDeleted_stringType_deleted() {
        SoftDeleteStringTestEntity entity = new SoftDeleteStringTestEntity();
        entity.setName("deleted");
        entity.setDeleted("Y");
        assertTrue(SoftDeleteHelper.isSoftDeleted(SoftDeleteStringTestEntity.class, entity));
    }

    @Test
    void testIsSoftDeleted_stringType_active() {
        SoftDeleteStringTestEntity entity = new SoftDeleteStringTestEntity();
        entity.setName("active");
        entity.setDeleted("N");
        assertFalse(SoftDeleteHelper.isSoftDeleted(SoftDeleteStringTestEntity.class, entity));
    }

    @Test
    void testIsNotDeleted_stringType_filtersCorrectly() {
        SoftDeleteStringTestEntity active = new SoftDeleteStringTestEntity();
        active.setName("active");
        active.setDeleted("N");
        stringRepository.save(active);

        SoftDeleteStringTestEntity deleted = new SoftDeleteStringTestEntity();
        deleted.setName("deleted");
        deleted.setDeleted("Y");
        stringRepository.save(deleted);

        List<SoftDeleteStringTestEntity> result =
            stringRepository.findAll(SoftDeleteHelper.isNotDeleted(SoftDeleteStringTestEntity.class));
        assertEquals(1, result.size());
        assertEquals("active", result.get(0).getName());
    }

    @Test
    void testIsDeleted_stringType_returnsOnlyDeleted() {
        SoftDeleteStringTestEntity active = new SoftDeleteStringTestEntity();
        active.setName("active");
        active.setDeleted("N");
        stringRepository.save(active);

        SoftDeleteStringTestEntity deleted = new SoftDeleteStringTestEntity();
        deleted.setName("deleted");
        deleted.setDeleted("Y");
        stringRepository.save(deleted);

        List<SoftDeleteStringTestEntity> result =
            stringRepository.findAll(SoftDeleteHelper.isDeleted(SoftDeleteStringTestEntity.class));
        assertEquals(1, result.size());
        assertEquals("deleted", result.get(0).getName());
    }

    @Test
    void testNotDeletedQuery_stringType() {
        SoftDeleteStringTestEntity active = new SoftDeleteStringTestEntity();
        active.setName("target");
        active.setDeleted("N");
        stringRepository.save(active);

        SoftDeleteStringTestEntity deleted = new SoftDeleteStringTestEntity();
        deleted.setName("target");
        deleted.setDeleted("Y");
        stringRepository.save(deleted);

        var qs = SoftDeleteHelper.notDeletedQuery(SoftDeleteStringTestEntity.class);
        qs.eq(SoftDeleteStringTestEntity::getName, "target");
        List<SoftDeleteStringTestEntity> result = stringRepository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
        assertEquals("N", result.get(0).getDeleted());
    }

    @Test
    void testFindSoftDeleteField_stringType() {
        String fieldName = SoftDeleteHelper.findSoftDeleteField(SoftDeleteStringTestEntity.class);
        assertEquals("deleted", fieldName);
    }

    private TestEntity newEntity(String name, int status) {
        TestEntity entity = new TestEntity();
        entity.setName(name);
        entity.setStatus(status);
        return entity;
    }
}
