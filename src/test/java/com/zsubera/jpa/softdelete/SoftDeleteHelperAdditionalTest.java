package com.zsubera.jpa.softdelete;

import static org.junit.jupiter.api.Assertions.*;

import com.zsubera.jpa.spec.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.context.ContextConfiguration;

@DataJpaTest
@org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase(
    replace = org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = TestApplication.class)
class SoftDeleteHelperAdditionalTest {

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        repository.flush();
        intRepo.deleteAll();
        intRepo.flush();
        stringRepo.deleteAll();
        stringRepo.flush();
        enumRepo.deleteAll();
        enumRepo.flush();
        testEntityRepo.deleteAll();
        testEntityRepo.flush();
    }

    @AfterEach
    void tearDown() {}

    @Autowired
    private SoftDeleteTestEntityRepository repository;

    @Autowired
    private SoftDeleteIntTestEntityRepository intRepo;

    @Autowired
    private SoftDeleteStringTestEntityRepository stringRepo;

    @Autowired
    private SoftDeleteEnumTestEntityRepository enumRepo;

    @Autowired
    private TestEntityRepository testEntityRepo;

    @PersistenceContext
    private EntityManager em;

    @Test
    void softDeleteAll_booleanType_executes() {
        SoftDeleteTestEntity e1 = new SoftDeleteTestEntity();
        e1.setName("test1");
        e1.setDeleted(false);
        repository.save(e1);
        repository.flush();

        int updated = SoftDeleteBulkExecutor.softDeleteAll(em, SoftDeleteTestEntity.class, true);
        assertTrue(updated >= 1);

        em.clear();
        List<SoftDeleteTestEntity> all = repository.findAll();
        assertTrue(all.stream().allMatch(e -> Boolean.TRUE.equals(e.getDeleted())));
    }

    @Test
    void softDeleteAll_integerType_executes() {
        SoftDeleteIntTestEntity e1 = new SoftDeleteIntTestEntity();
        e1.setName("int1");
        e1.setDeleted(0);
        intRepo.save(e1);
        intRepo.flush();

        int updated = SoftDeleteBulkExecutor.softDeleteAll(em, SoftDeleteIntTestEntity.class, true);
        assertTrue(updated >= 1);

        em.clear();
        List<SoftDeleteIntTestEntity> all = intRepo.findAll();
        assertTrue(all.stream().allMatch(e -> Integer.valueOf(1).equals(e.getDeleted())));
    }

    @Test
    void softDeleteAll_stringType_executes() {
        SoftDeleteStringTestEntity e1 = new SoftDeleteStringTestEntity();
        e1.setName("str1");
        e1.setDeleted("N");
        stringRepo.save(e1);
        stringRepo.flush();

        int updated = SoftDeleteBulkExecutor.softDeleteAll(em, SoftDeleteStringTestEntity.class, true);
        assertTrue(updated >= 1);

        em.clear();
        List<SoftDeleteStringTestEntity> all = stringRepo.findAll();
        assertTrue(all.stream().allMatch(e -> "Y".equals(e.getDeleted())));
    }

    @Test
    void softDeleteAll_enumType_executes() {
        SoftDeleteEnumTestEntity e1 = new SoftDeleteEnumTestEntity();
        e1.setName("enum1");
        e1.setStatus(SoftDeleteEnumTestEntity.Status.ACTIVE);
        enumRepo.save(e1);
        enumRepo.flush();

        int updated = SoftDeleteBulkExecutor.softDeleteAll(em, SoftDeleteEnumTestEntity.class, true);
        assertTrue(updated >= 1);

        em.clear();
        List<SoftDeleteEnumTestEntity> all = enumRepo.findAll();
        assertTrue(all.stream().allMatch(e -> SoftDeleteEnumTestEntity.Status.ARCHIVED.equals(e.getStatus())));
    }

    @Test
    void softDeleteAll_withMaxRowsLimit_doesNotThrowWhenUnder() {
        SoftDeleteTestEntity e1 = new SoftDeleteTestEntity();
        e1.setName("limit1");
        e1.setDeleted(false);
        repository.save(e1);
        repository.flush();

        int updated = SoftDeleteBulkExecutor.softDeleteAll(em, SoftDeleteTestEntity.class, true, 100);
        assertTrue(updated >= 1);
    }

    @Test
    void softDeleteAll_withUnlimitedRows_executes() {
        SoftDeleteTestEntity e1 = new SoftDeleteTestEntity();
        e1.setName("unlimited");
        e1.setDeleted(false);
        repository.save(e1);
        repository.flush();

        int updated = SoftDeleteBulkExecutor.softDeleteAll(em, SoftDeleteTestEntity.class, true, -1);
        assertTrue(updated >= 1);
    }

    @Test
    void softDeleteByIds_booleanType_executes() {
        SoftDeleteTestEntity e1 = new SoftDeleteTestEntity();
        e1.setName("byId1");
        e1.setDeleted(false);
        repository.save(e1);
        repository.flush();

        int updated = SoftDeleteBulkExecutor.softDeleteByIds(em, SoftDeleteTestEntity.class, List.of(e1.getId()));
        assertEquals(1, updated);

        em.clear();
        SoftDeleteTestEntity found = repository.findById(e1.getId()).orElse(null);
        assertNotNull(found);
        assertTrue(found.getDeleted());
    }

    @Test
    void softDeleteByIds_integerType_executes() {
        SoftDeleteIntTestEntity e1 = new SoftDeleteIntTestEntity();
        e1.setName("intById1");
        e1.setDeleted(0);
        intRepo.save(e1);
        intRepo.flush();

        int updated = SoftDeleteBulkExecutor.softDeleteByIds(em, SoftDeleteIntTestEntity.class, List.of(e1.getId()));
        assertEquals(1, updated);

        em.clear();
        SoftDeleteIntTestEntity found = intRepo.findById(e1.getId()).orElse(null);
        assertNotNull(found);
        assertEquals(Integer.valueOf(1), found.getDeleted());
    }

    @Test
    void softDeleteByIds_stringType_executes() {
        SoftDeleteStringTestEntity e1 = new SoftDeleteStringTestEntity();
        e1.setName("strById1");
        e1.setDeleted("N");
        stringRepo.save(e1);
        stringRepo.flush();

        int updated = SoftDeleteBulkExecutor.softDeleteByIds(em, SoftDeleteStringTestEntity.class, List.of(e1.getId()));
        assertEquals(1, updated);

        em.clear();
        SoftDeleteStringTestEntity found = stringRepo.findById(e1.getId()).orElse(null);
        assertNotNull(found);
        assertEquals("Y", found.getDeleted());
    }

    @Test
    void softDeleteByIds_enumType_executes() {
        SoftDeleteEnumTestEntity e1 = new SoftDeleteEnumTestEntity();
        e1.setName("enumById1");
        e1.setStatus(SoftDeleteEnumTestEntity.Status.ACTIVE);
        enumRepo.save(e1);
        enumRepo.flush();

        int updated = SoftDeleteBulkExecutor.softDeleteByIds(em, SoftDeleteEnumTestEntity.class, List.of(e1.getId()));
        assertEquals(1, updated);

        em.clear();
        SoftDeleteEnumTestEntity found = enumRepo.findById(e1.getId()).orElse(null);
        assertNotNull(found);
        assertEquals(SoftDeleteEnumTestEntity.Status.ARCHIVED, found.getStatus());
    }

    @Test
    void softDeleteByIds_emptyIds_returnsZero() {
        int updated = SoftDeleteBulkExecutor.softDeleteByIds(em, SoftDeleteTestEntity.class, List.of());
        assertEquals(0, updated);
    }

    @Test
    void softDeleteByIds_nullIds_returnsZero() {
        int updated = SoftDeleteBulkExecutor.softDeleteByIds(em, SoftDeleteTestEntity.class, null);
        assertEquals(0, updated);
    }

    @Test
    void softDeleteByIds_nullEm_throws() {
        assertThrows(IllegalArgumentException.class,
            () -> SoftDeleteBulkExecutor.softDeleteByIds(null, SoftDeleteTestEntity.class, List.of(1L)));
    }

    @Test
    void softDeleteByIds_nullClass_throws() {
        assertThrows(IllegalArgumentException.class,
            () -> SoftDeleteBulkExecutor.softDeleteByIds(em, null, List.of(1L)));
    }

    @Test
    void softDeleteByIds_exceedsHardLimit_throws() {
        int hardLimit = com.zsubera.jpa.util.InClauseBuilder.getHardLimit();
        java.util.List<Long> ids = new java.util.ArrayList<>();
        for (int i = 0; i <= hardLimit; i++) {
            ids.add((long)i);
        }
        assertThrows(IllegalArgumentException.class,
            () -> SoftDeleteBulkExecutor.softDeleteByIds(em, SoftDeleteTestEntity.class, ids));
    }

    @Test
    void softDeleteAll_noSoftDeleteField_throws() {
        assertThrows(IllegalArgumentException.class,
            () -> SoftDeleteBulkExecutor.softDeleteAll(em, TestEntity.class, true));
    }

    @Test
    void softDeleteAll_nullEm_throws() {
        assertThrows(IllegalArgumentException.class,
            () -> SoftDeleteBulkExecutor.softDeleteAll(null, SoftDeleteTestEntity.class, true));
    }

    @Test
    void softDeleteAll_nullClass_throws() {
        assertThrows(IllegalArgumentException.class, () -> SoftDeleteBulkExecutor.softDeleteAll(em, null, true));
    }

    @Test
    void softDeleteAll_notAllowed_throws() {
        assertThrows(IllegalStateException.class,
            () -> SoftDeleteBulkExecutor.softDeleteAll(em, SoftDeleteTestEntity.class, false));
    }

    @Test
    void softDeleteAll_withMaxRowsLimit_overLimit_throws() {
        SoftDeleteTestEntity e1 = new SoftDeleteTestEntity();
        e1.setName("over");
        e1.setDeleted(false);
        repository.save(e1);
        repository.flush();

        // maxRows=0 means unlimited (skip count check), so it won't throw
        // Use a very small maxRows to trigger the limit
        SoftDeleteTestEntity e2 = new SoftDeleteTestEntity();
        e2.setName("over2");
        e2.setDeleted(false);
        repository.save(e2);
        repository.flush();

        assertThrows(IllegalStateException.class,
            () -> SoftDeleteBulkExecutor.softDeleteAll(em, SoftDeleteTestEntity.class, true, 1));
    }

    @Test
    void softDeleteByIds_noSoftDeleteField_throws() {
        assertThrows(IllegalArgumentException.class,
            () -> SoftDeleteBulkExecutor.softDeleteByIds(em, TestEntity.class, List.of(1L)));
    }

    @Test
    void isNotDeleted_booleanField_filtersCorrectly() {
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
    void isNotDeleted_stringField_filtersCorrectly() {
        SoftDeleteStringTestEntity active = new SoftDeleteStringTestEntity();
        active.setName("active");
        active.setDeleted("N");
        stringRepo.save(active);

        SoftDeleteStringTestEntity deleted = new SoftDeleteStringTestEntity();
        deleted.setName("deleted");
        deleted.setDeleted("Y");
        stringRepo.save(deleted);

        Specification<SoftDeleteStringTestEntity> spec =
            SoftDeleteHelper.isNotDeleted(SoftDeleteStringTestEntity.class);
        List<SoftDeleteStringTestEntity> result = stringRepo.findAll(spec);
        assertEquals(1, result.size());
        assertEquals("active", result.get(0).getName());
    }

    @Test
    void isNotDeleted_enumField_filtersCorrectly() {
        SoftDeleteEnumTestEntity active = new SoftDeleteEnumTestEntity();
        active.setName("active");
        active.setStatus(SoftDeleteEnumTestEntity.Status.ACTIVE);
        enumRepo.save(active);

        SoftDeleteEnumTestEntity archived = new SoftDeleteEnumTestEntity();
        archived.setName("archived");
        archived.setStatus(SoftDeleteEnumTestEntity.Status.ARCHIVED);
        enumRepo.save(archived);

        Specification<SoftDeleteEnumTestEntity> spec = SoftDeleteHelper.isNotDeleted(SoftDeleteEnumTestEntity.class);
        List<SoftDeleteEnumTestEntity> result = enumRepo.findAll(spec);
        assertEquals(1, result.size());
        assertEquals("active", result.get(0).getName());
    }

    @Test
    void isDeleted_booleanField_filtersCorrectly() {
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
    void isDeleted_integerField_filtersCorrectly() {
        SoftDeleteIntTestEntity active = new SoftDeleteIntTestEntity();
        active.setName("active");
        active.setDeleted(0);
        intRepo.save(active);

        SoftDeleteIntTestEntity deleted = new SoftDeleteIntTestEntity();
        deleted.setName("deleted");
        deleted.setDeleted(1);
        intRepo.save(deleted);

        Specification<SoftDeleteIntTestEntity> spec = SoftDeleteHelper.isDeleted(SoftDeleteIntTestEntity.class);
        List<SoftDeleteIntTestEntity> result = intRepo.findAll(spec);
        assertEquals(1, result.size());
        assertEquals("deleted", result.get(0).getName());
    }

    @Test
    void isSoftDeleted_booleanField_true() {
        SoftDeleteTestEntity entity = new SoftDeleteTestEntity();
        entity.setName("deleted");
        entity.setDeleted(true);
        assertTrue(SoftDeleteHelper.isSoftDeleted(SoftDeleteTestEntity.class, entity));
    }

    @Test
    void isSoftDeleted_booleanField_false() {
        SoftDeleteTestEntity entity = new SoftDeleteTestEntity();
        entity.setName("active");
        entity.setDeleted(false);
        assertFalse(SoftDeleteHelper.isSoftDeleted(SoftDeleteTestEntity.class, entity));
    }

    @Test
    void isSoftDeleted_booleanField_null() {
        SoftDeleteTestEntity entity = new SoftDeleteTestEntity();
        entity.setName("nullDeleted");
        entity.setDeleted(null);
        assertFalse(SoftDeleteHelper.isSoftDeleted(SoftDeleteTestEntity.class, entity));
    }

    @Test
    void isSoftDeleted_integerField() {
        SoftDeleteIntTestEntity entity = new SoftDeleteIntTestEntity();
        entity.setName("deleted");
        entity.setDeleted(1);
        assertTrue(SoftDeleteHelper.isSoftDeleted(SoftDeleteIntTestEntity.class, entity));
    }

    @Test
    void isSoftDeleted_stringField() {
        SoftDeleteStringTestEntity entity = new SoftDeleteStringTestEntity();
        entity.setName("deleted");
        entity.setDeleted("Y");
        assertTrue(SoftDeleteHelper.isSoftDeleted(SoftDeleteStringTestEntity.class, entity));
    }

    @Test
    void isSoftDeleted_enumField() {
        SoftDeleteEnumTestEntity entity = new SoftDeleteEnumTestEntity();
        entity.setName("archived");
        entity.setStatus(SoftDeleteEnumTestEntity.Status.ARCHIVED);
        assertTrue(SoftDeleteHelper.isSoftDeleted(SoftDeleteEnumTestEntity.class, entity));
    }

    @Test
    void isSoftDeleted_nonSoftDeleteEntity_returnsFalse() {
        assertFalse(SoftDeleteHelper.isSoftDeleted(TestEntity.class, new TestEntity()));
    }

    @Test
    void isSoftDeleted_nullEntity_throws() {
        assertThrows(IllegalArgumentException.class,
            () -> SoftDeleteHelper.isSoftDeleted(SoftDeleteTestEntity.class, null));
    }

    @Test
    void isSoftDeleted_nullClass_throws() {
        assertThrows(IllegalArgumentException.class,
            () -> SoftDeleteHelper.isSoftDeleted(null, new SoftDeleteTestEntity()));
    }

    @Test
    void findSoftDeleteField_booleanType() {
        String field = SoftDeleteHelper.findSoftDeleteField(SoftDeleteTestEntity.class);
        assertEquals("deleted", field);
    }

    @Test
    void findSoftDeleteField_integerType() {
        String field = SoftDeleteHelper.findSoftDeleteField(SoftDeleteIntTestEntity.class);
        assertNotNull(field);
    }

    @Test
    void findSoftDeleteField_stringType() {
        String field = SoftDeleteHelper.findSoftDeleteField(SoftDeleteStringTestEntity.class);
        assertNotNull(field);
    }

    @Test
    void findSoftDeleteField_enumType() {
        String field = SoftDeleteHelper.findSoftDeleteField(SoftDeleteEnumTestEntity.class);
        assertNotNull(field);
    }

    @Test
    void findSoftDeleteField_noAnnotation_returnsNull() {
        assertNull(SoftDeleteHelper.findSoftDeleteField(TestEntity.class));
    }

    @Test
    void findSoftDeleteField_cached() {
        String first = SoftDeleteHelper.findSoftDeleteField(SoftDeleteTestEntity.class);
        String second = SoftDeleteHelper.findSoftDeleteField(SoftDeleteTestEntity.class);
        assertSame(first, second);
    }

    @Test
    void findSoftDeleteField_withIntegerField() {
        String field = SoftDeleteHelper.findSoftDeleteField(SoftDeleteIntTestEntity.class);
        assertNotNull(field);
    }

    @Test
    void notDeletedQuery_booleanType_hasConditions() {
        var qs = SoftDeleteHelper.notDeletedQuery(SoftDeleteTestEntity.class);
        assertFalse(qs.conditions().isEmpty());
    }

    @Test
    void notDeletedQuery_integerType_hasConditions() {
        var qs = SoftDeleteHelper.notDeletedQuery(SoftDeleteIntTestEntity.class);
        assertFalse(qs.conditions().isEmpty());
    }

    @Test
    void notDeletedQuery_stringType_hasConditions() {
        var qs = SoftDeleteHelper.notDeletedQuery(SoftDeleteStringTestEntity.class);
        assertFalse(qs.conditions().isEmpty());
    }

    @Test
    void notDeletedQuery_enumType_hasConditions() {
        var qs = SoftDeleteHelper.notDeletedQuery(SoftDeleteEnumTestEntity.class);
        assertFalse(qs.conditions().isEmpty());
    }

    @Test
    void notDeletedQuery_noAnnotation_returnsEmptyQuerySpec() {
        var qs = SoftDeleteHelper.notDeletedQuery(TestEntity.class);
        assertTrue(qs.conditions().isEmpty());
    }

    @Test
    void validateIdentifier_null_throws() {
        assertThrows(IllegalArgumentException.class, () -> SoftDeleteHelper.validateIdentifier(null));
    }

    @Test
    void validateIdentifier_empty_throws() {
        assertThrows(IllegalArgumentException.class, () -> SoftDeleteHelper.validateIdentifier(""));
    }

    @Test
    void validateIdentifier_invalidChars_throws() {
        assertThrows(IllegalArgumentException.class, () -> SoftDeleteHelper.validateIdentifier("bad-name"));
    }

    @Test
    void validateIdentifier_valid_returnsSame() {
        assertEquals("test_table", SoftDeleteHelper.validateIdentifier("test_table"));
    }

    @Test
    void validateIdentifier_schemaQualified_returnsSame() {
        assertEquals("schema.table", SoftDeleteHelper.validateIdentifier("schema.table"));
    }

    @Test
    void validateTableName_valid_returnsSame() {
        assertEquals("test_table", SoftDeleteHelper.validateTableName("test_table"));
    }

    @Test
    void specCache_returnsSameInstance() {
        Specification<SoftDeleteTestEntity> spec1 = SoftDeleteHelper.isNotDeleted(SoftDeleteTestEntity.class);
        Specification<SoftDeleteTestEntity> spec2 = SoftDeleteHelper.isNotDeleted(SoftDeleteTestEntity.class);
        assertSame(spec1, spec2);
    }

    @Test
    void isNotDeleted_noAnnotation_returnsAll() {
        testEntityRepo.save(newEntity("a", 1));
        testEntityRepo.save(newEntity("b", 2));

        Specification<TestEntity> spec = SoftDeleteHelper.isNotDeleted(TestEntity.class);
        List<TestEntity> result = testEntityRepo.findAll(spec);
        assertEquals(2, result.size());
    }

    @Test
    void isDeleted_noAnnotation_returnsNone() {
        testEntityRepo.save(newEntity("a", 1));

        Specification<TestEntity> spec = SoftDeleteHelper.isDeleted(TestEntity.class);
        List<TestEntity> result = testEntityRepo.findAll(spec);
        assertEquals(0, result.size());
    }

    @Test
    void softDeleteAll_booleanTypeWithMaxRows_executes() {
        SoftDeleteTestEntity e1 = new SoftDeleteTestEntity();
        e1.setName("limitBool");
        e1.setDeleted(false);
        repository.save(e1);
        repository.flush();

        int updated = SoftDeleteBulkExecutor.softDeleteAll(em, SoftDeleteTestEntity.class, true, 100);
        assertTrue(updated >= 1);
    }

    @Test
    void softDeleteAll_integerTypeWithMaxRows_executes() {
        SoftDeleteIntTestEntity e1 = new SoftDeleteIntTestEntity();
        e1.setName("limitInt");
        e1.setDeleted(0);
        intRepo.save(e1);
        intRepo.flush();

        int updated = SoftDeleteBulkExecutor.softDeleteAll(em, SoftDeleteIntTestEntity.class, true, 100);
        assertTrue(updated >= 1);
    }

    @Test
    void softDeleteAll_stringTypeWithMaxRows_executes() {
        SoftDeleteStringTestEntity e1 = new SoftDeleteStringTestEntity();
        e1.setName("limitStr");
        e1.setDeleted("N");
        stringRepo.save(e1);
        stringRepo.flush();

        int updated = SoftDeleteBulkExecutor.softDeleteAll(em, SoftDeleteStringTestEntity.class, true, 100);
        assertTrue(updated >= 1);
    }

    @Test
    void softDeleteAll_enumTypeWithMaxRows_executes() {
        SoftDeleteEnumTestEntity e1 = new SoftDeleteEnumTestEntity();
        e1.setName("limitEnum");
        e1.setStatus(SoftDeleteEnumTestEntity.Status.ACTIVE);
        enumRepo.save(e1);
        enumRepo.flush();

        int updated = SoftDeleteBulkExecutor.softDeleteAll(em, SoftDeleteEnumTestEntity.class, true, 100);
        assertTrue(updated >= 1);
    }

    @Test
    void softDeleteAll_booleanTypeWithUnlimited_executes() {
        SoftDeleteTestEntity e1 = new SoftDeleteTestEntity();
        e1.setName("unlimitedBool");
        e1.setDeleted(false);
        repository.save(e1);
        repository.flush();

        int updated = SoftDeleteBulkExecutor.softDeleteAll(em, SoftDeleteTestEntity.class, true, -1);
        assertTrue(updated >= 1);
    }

    @Test
    void softDeleteByIds_multipleIds_executes() {
        SoftDeleteTestEntity e1 = new SoftDeleteTestEntity();
        e1.setName("multi1");
        e1.setDeleted(false);
        repository.save(e1);

        SoftDeleteTestEntity e2 = new SoftDeleteTestEntity();
        e2.setName("multi2");
        e2.setDeleted(false);
        repository.save(e2);
        repository.flush();

        int updated =
            SoftDeleteBulkExecutor.softDeleteByIds(em, SoftDeleteTestEntity.class, List.of(e1.getId(), e2.getId()));
        assertEquals(2, updated);
    }

    @Test
    void softDeleteByIds_nonexistentIds_returnsZero() {
        int updated = SoftDeleteBulkExecutor.softDeleteByIds(em, SoftDeleteTestEntity.class, List.of(999999L));
        assertEquals(0, updated);
    }

    @Test
    void validateTableName_null_throws() {
        assertThrows(IllegalArgumentException.class, () -> SoftDeleteHelper.validateTableName(null));
    }

    @Test
    void validateTableName_empty_throws() {
        assertThrows(IllegalArgumentException.class, () -> SoftDeleteHelper.validateTableName(""));
    }

    @Test
    void notDeletedQuery_specReturnsCorrectFilter() {
        SoftDeleteTestEntity active = new SoftDeleteTestEntity();
        active.setName("active");
        active.setDeleted(false);
        repository.save(active);

        SoftDeleteTestEntity deleted = new SoftDeleteTestEntity();
        deleted.setName("deleted");
        deleted.setDeleted(true);
        repository.save(deleted);

        var qs = SoftDeleteHelper.notDeletedQuery(SoftDeleteTestEntity.class);
        qs.eq(SoftDeleteTestEntity::getName, "active");
        List<SoftDeleteTestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
        assertEquals("active", result.get(0).getName());
    }

    private TestEntity newEntity(String name, int status) {
        TestEntity entity = new TestEntity();
        entity.setName(name);
        entity.setStatus(status);
        return entity;
    }
}
