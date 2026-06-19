package com.zsubera.jpa.softdelete;

import static org.junit.jupiter.api.Assertions.*;

import com.zsubera.jpa.spec.*;
import com.zsubera.jpa.spec.SoftDeleteEnumTestEntity.Status;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ContextConfiguration;

@DataJpaTest
@org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase(
    replace = org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = TestApplication.class)
class SoftDeleteHelperCoverageTest {

    @Autowired
    private SoftDeleteTestEntityRepository boolRepo;

    @Autowired
    private SoftDeleteIntTestEntityRepository intRepo;

    @Autowired
    private SoftDeleteStringTestEntityRepository strRepo;

    @Autowired
    private SoftDeleteEnumTestEntityRepository enumRepo;

    @Autowired
    private jakarta.persistence.EntityManager em;

    @BeforeEach
    void setUp() {
        boolRepo.deleteAll();
        intRepo.deleteAll();
        strRepo.deleteAll();
        enumRepo.deleteAll();
    }

    @Test
    void testSoftDeleteAllBooleanType() {
        boolRepo.save(buildBool("a", false));
        boolRepo.save(buildBool("b", false));
        boolRepo.save(buildBool("c", true));
        boolRepo.flush();

        int count = SoftDeleteHelper.softDeleteAll(em, SoftDeleteTestEntity.class, true);
        assertEquals(2, count);
    }

    @Test
    void testSoftDeleteAllIntegerType() {
        intRepo.save(buildInt("a", 0));
        intRepo.save(buildInt("b", 0));
        intRepo.save(buildInt("c", 1));
        intRepo.flush();

        int count = SoftDeleteHelper.softDeleteAll(em, SoftDeleteIntTestEntity.class, true);
        assertEquals(2, count);
    }

    @Test
    void testSoftDeleteAllStringType() {
        strRepo.save(buildStr("a", "N"));
        strRepo.save(buildStr("b", "N"));
        strRepo.save(buildStr("c", "Y"));
        strRepo.flush();

        int count = SoftDeleteHelper.softDeleteAll(em, SoftDeleteStringTestEntity.class, true);
        assertEquals(2, count);
    }

    @Test
    void testSoftDeleteAllEnumType() {
        enumRepo.save(buildEnum("a", Status.ACTIVE));
        enumRepo.save(buildEnum("b", Status.ACTIVE));
        enumRepo.save(buildEnum("c", Status.ARCHIVED));
        enumRepo.flush();

        int count = SoftDeleteHelper.softDeleteAll(em, SoftDeleteEnumTestEntity.class, true);
        assertEquals(2, count);
    }

    @Test
    void testSoftDeleteAllWithMaxRowsExceeded() {
        boolRepo.save(buildBool("a", false));
        boolRepo.save(buildBool("b", false));
        boolRepo.flush();

        assertThrows(IllegalStateException.class,
            () -> SoftDeleteHelper.softDeleteAll(em, SoftDeleteTestEntity.class, true, 1));
    }

    @Test
    void testSoftDeleteAllWithUnlimitedRows() {
        boolRepo.save(buildBool("a", false));
        boolRepo.save(buildBool("b", false));
        boolRepo.flush();

        int count = SoftDeleteHelper.softDeleteAll(em, SoftDeleteTestEntity.class, true, -1);
        assertEquals(2, count);
    }

    @Test
    void testSoftDeleteByIdsBooleanType() {
        var e1 = boolRepo.save(buildBool("a", false));
        var e2 = boolRepo.save(buildBool("b", false));
        boolRepo.save(buildBool("c", true));
        boolRepo.flush();

        int count = SoftDeleteHelper.softDeleteByIds(em, SoftDeleteTestEntity.class, List.of(e1.getId(), e2.getId()));
        assertEquals(2, count);
    }

    @Test
    void testSoftDeleteByIdsIntegerType() {
        var e1 = intRepo.save(buildInt("a", 0));
        var e2 = intRepo.save(buildInt("b", 0));
        intRepo.flush();

        int count =
            SoftDeleteHelper.softDeleteByIds(em, SoftDeleteIntTestEntity.class, List.of(e1.getId(), e2.getId()));
        assertEquals(2, count);
    }

    @Test
    void testSoftDeleteByIdsStringType() {
        var e1 = strRepo.save(buildStr("a", "N"));
        var e2 = strRepo.save(buildStr("b", "N"));
        strRepo.flush();

        int count =
            SoftDeleteHelper.softDeleteByIds(em, SoftDeleteStringTestEntity.class, List.of(e1.getId(), e2.getId()));
        assertEquals(2, count);
    }

    @Test
    void testSoftDeleteByIdsEnumType() {
        var e1 = enumRepo.save(buildEnum("a", Status.ACTIVE));
        var e2 = enumRepo.save(buildEnum("b", Status.ACTIVE));
        enumRepo.flush();

        int count =
            SoftDeleteHelper.softDeleteByIds(em, SoftDeleteEnumTestEntity.class, List.of(e1.getId(), e2.getId()));
        assertEquals(2, count);
    }

    @Test
    void testFindSoftDeleteFieldForIntType() {
        String field = SoftDeleteHelper.findSoftDeleteField(SoftDeleteIntTestEntity.class);
        assertEquals("deleted", field);
    }

    @Test
    void testFindSoftDeleteFieldForStringType() {
        String field = SoftDeleteHelper.findSoftDeleteField(SoftDeleteStringTestEntity.class);
        assertEquals("deleted", field);
    }

    @Test
    void testFindSoftDeleteFieldForEnumType() {
        String field = SoftDeleteHelper.findSoftDeleteField(SoftDeleteEnumTestEntity.class);
        assertEquals("status", field);
    }

    @Test
    void testIsSoftDeletedIntegerType() {
        SoftDeleteIntTestEntity notDeleted = buildInt("a", 0);
        assertFalse(SoftDeleteHelper.isSoftDeleted(SoftDeleteIntTestEntity.class, notDeleted));

        SoftDeleteIntTestEntity deleted = buildInt("b", 1);
        assertTrue(SoftDeleteHelper.isSoftDeleted(SoftDeleteIntTestEntity.class, deleted));
    }

    @Test
    void testIsSoftDeletedStringType() {
        SoftDeleteStringTestEntity notDeleted = buildStr("a", "N");
        assertFalse(SoftDeleteHelper.isSoftDeleted(SoftDeleteStringTestEntity.class, notDeleted));

        SoftDeleteStringTestEntity deleted = buildStr("b", "Y");
        assertTrue(SoftDeleteHelper.isSoftDeleted(SoftDeleteStringTestEntity.class, deleted));
    }

    @Test
    void testIsSoftDeletedEnumType() {
        SoftDeleteEnumTestEntity notDeleted = buildEnum("a", Status.ACTIVE);
        assertFalse(SoftDeleteHelper.isSoftDeleted(SoftDeleteEnumTestEntity.class, notDeleted));

        SoftDeleteEnumTestEntity deleted = buildEnum("b", Status.ARCHIVED);
        assertTrue(SoftDeleteHelper.isSoftDeleted(SoftDeleteEnumTestEntity.class, deleted));
    }

    @Test
    void testIsNotDeletedIntegerType() {
        intRepo.save(buildInt("active", 0));
        intRepo.save(buildInt("archived", 1));
        intRepo.flush();

        Specification<SoftDeleteIntTestEntity> spec = SoftDeleteHelper.isNotDeleted(SoftDeleteIntTestEntity.class);
        List<SoftDeleteIntTestEntity> result = intRepo.findAll(spec);
        assertEquals(1, result.size());
        assertEquals("active", result.get(0).getName());
    }

    @Test
    void testIsDeletedIntegerType() {
        intRepo.save(buildInt("active", 0));
        intRepo.save(buildInt("archived", 1));
        intRepo.flush();

        Specification<SoftDeleteIntTestEntity> spec = SoftDeleteHelper.isDeleted(SoftDeleteIntTestEntity.class);
        List<SoftDeleteIntTestEntity> result = intRepo.findAll(spec);
        assertEquals(1, result.size());
        assertEquals("archived", result.get(0).getName());
    }

    @Test
    void testIsNotDeletedStringType() {
        strRepo.save(buildStr("active", "N"));
        strRepo.save(buildStr("archived", "Y"));
        strRepo.flush();

        Specification<SoftDeleteStringTestEntity> spec =
            SoftDeleteHelper.isNotDeleted(SoftDeleteStringTestEntity.class);
        List<SoftDeleteStringTestEntity> result = strRepo.findAll(spec);
        assertEquals(1, result.size());
        assertEquals("active", result.get(0).getName());
    }

    @Test
    void testIsNotDeletedEnumType() {
        enumRepo.save(buildEnum("active", Status.ACTIVE));
        enumRepo.save(buildEnum("archived", Status.ARCHIVED));
        enumRepo.flush();

        Specification<SoftDeleteEnumTestEntity> spec = SoftDeleteHelper.isNotDeleted(SoftDeleteEnumTestEntity.class);
        List<SoftDeleteEnumTestEntity> result = enumRepo.findAll(spec);
        assertEquals(1, result.size());
        assertEquals("active", result.get(0).getName());
    }

    @Test
    void testShutdown() {
        assertDoesNotThrow(SoftDeleteHelper::shutdown);
    }

    @Test
    void testSoftDeleteAllWithAlreadyDeletedRecords() {
        boolRepo.save(buildBool("a", true));
        boolRepo.save(buildBool("b", true));
        boolRepo.flush();

        int count = SoftDeleteHelper.softDeleteAll(em, SoftDeleteTestEntity.class, true, -1);
        assertEquals(0, count);
    }

    @Test
    void testSoftDeleteByIdsAlreadyDeleted() {
        var e1 = boolRepo.save(buildBool("a", true));
        boolRepo.flush();

        int count = SoftDeleteHelper.softDeleteByIds(em, SoftDeleteTestEntity.class, List.of(e1.getId()));
        assertEquals(1, count);
    }

    private SoftDeleteTestEntity buildBool(String name, boolean deleted) {
        SoftDeleteTestEntity e = new SoftDeleteTestEntity();
        e.setName(name);
        e.setDeleted(deleted);
        return e;
    }

    private SoftDeleteIntTestEntity buildInt(String name, int deleted) {
        SoftDeleteIntTestEntity e = new SoftDeleteIntTestEntity();
        e.setName(name);
        e.setDeleted(deleted);
        return e;
    }

    private SoftDeleteStringTestEntity buildStr(String name, String deleted) {
        SoftDeleteStringTestEntity e = new SoftDeleteStringTestEntity();
        e.setName(name);
        e.setDeleted(deleted);
        return e;
    }

    private SoftDeleteEnumTestEntity buildEnum(String name, Status status) {
        SoftDeleteEnumTestEntity e = new SoftDeleteEnumTestEntity();
        e.setName(name);
        e.setStatus(status);
        return e;
    }

    @Test
    void testSoftDeleteAllBooleanWithMaxRowsExceeded() {
        boolRepo.save(buildBool("a", false));
        boolRepo.save(buildBool("b", false));
        boolRepo.flush();

        assertThrows(IllegalStateException.class,
            () -> SoftDeleteHelper.softDeleteAll(em, SoftDeleteTestEntity.class, true, 1));
    }

    @Test
    void testSoftDeleteAllBooleanWithMaxRowsWithinLimit() {
        boolRepo.save(buildBool("a", false));
        boolRepo.flush();

        int count = SoftDeleteHelper.softDeleteAll(em, SoftDeleteTestEntity.class, true, 1);
        assertEquals(1, count);
    }

    @Test
    void testSoftDeleteAllIntWithMaxRowsExceeded() {
        intRepo.save(buildInt("a", 0));
        intRepo.save(buildInt("b", 0));
        intRepo.flush();

        assertThrows(IllegalStateException.class,
            () -> SoftDeleteHelper.softDeleteAll(em, SoftDeleteIntTestEntity.class, true, 1));
    }

    @Test
    void testSoftDeleteAllStringWithMaxRowsExceeded() {
        strRepo.save(buildStr("a", "N"));
        strRepo.save(buildStr("b", "N"));
        strRepo.flush();

        assertThrows(IllegalStateException.class,
            () -> SoftDeleteHelper.softDeleteAll(em, SoftDeleteStringTestEntity.class, true, 1));
    }

    @Test
    void testSoftDeleteAllEnumWithMaxRowsExceeded() {
        enumRepo.save(buildEnum("a", Status.ACTIVE));
        enumRepo.save(buildEnum("b", Status.ACTIVE));
        enumRepo.flush();

        assertThrows(IllegalStateException.class,
            () -> SoftDeleteHelper.softDeleteAll(em, SoftDeleteEnumTestEntity.class, true, 1));
    }

    @Test
    void testSoftDeleteByIdsBooleanWithBatch() {
        var e1 = boolRepo.save(buildBool("a", false));
        var e2 = boolRepo.save(buildBool("b", false));
        boolRepo.flush();

        int count = SoftDeleteHelper.softDeleteByIds(em, SoftDeleteTestEntity.class, List.of(e1.getId(), e2.getId()));
        assertEquals(2, count);
    }

    @Test
    void testSoftDeleteByIdsIntWithBatch() {
        var e1 = intRepo.save(buildInt("a", 0));
        var e2 = intRepo.save(buildInt("b", 0));
        intRepo.flush();

        int count =
            SoftDeleteHelper.softDeleteByIds(em, SoftDeleteIntTestEntity.class, List.of(e1.getId(), e2.getId()));
        assertEquals(2, count);
    }

    @Test
    void testSoftDeleteByIdsStringWithBatch() {
        var e1 = strRepo.save(buildStr("a", "N"));
        var e2 = strRepo.save(buildStr("b", "N"));
        strRepo.flush();

        int count =
            SoftDeleteHelper.softDeleteByIds(em, SoftDeleteStringTestEntity.class, List.of(e1.getId(), e2.getId()));
        assertEquals(2, count);
    }

    @Test
    void testSoftDeleteByIdsEnumWithBatch() {
        var e1 = enumRepo.save(buildEnum("a", Status.ACTIVE));
        var e2 = enumRepo.save(buildEnum("b", Status.ACTIVE));
        enumRepo.flush();

        int count =
            SoftDeleteHelper.softDeleteByIds(em, SoftDeleteEnumTestEntity.class, List.of(e1.getId(), e2.getId()));
        assertEquals(2, count);
    }

    @Test
    void testSoftDeleteByIdsIntNullEm() {
        assertThrows(IllegalArgumentException.class,
            () -> SoftDeleteHelper.softDeleteByIds(null, SoftDeleteIntTestEntity.class, List.of(1L)));
    }

    @Test
    void testSoftDeleteByIdsIntNullClass() {
        assertThrows(IllegalArgumentException.class, () -> SoftDeleteHelper.softDeleteByIds(em, null, List.of(1L)));
    }

    @Test
    void testSoftDeleteByIdsStringNullEm() {
        assertThrows(IllegalArgumentException.class,
            () -> SoftDeleteHelper.softDeleteByIds(null, SoftDeleteStringTestEntity.class, List.of(1L)));
    }

    @Test
    void testSoftDeleteByIdsStringNullClass() {
        assertThrows(IllegalArgumentException.class, () -> SoftDeleteHelper.softDeleteByIds(em, null, List.of(1L)));
    }

    @Test
    void testSoftDeleteByIdsEnumNullEm() {
        assertThrows(IllegalArgumentException.class,
            () -> SoftDeleteHelper.softDeleteByIds(null, SoftDeleteEnumTestEntity.class, List.of(1L)));
    }

    @Test
    void testSoftDeleteByIdsEnumNullClass() {
        assertThrows(IllegalArgumentException.class, () -> SoftDeleteHelper.softDeleteByIds(em, null, List.of(1L)));
    }

    @Test
    void testSoftDeleteAllIntNullEm() {
        assertThrows(IllegalArgumentException.class,
            () -> SoftDeleteHelper.softDeleteAll(null, SoftDeleteIntTestEntity.class, true));
    }

    @Test
    void testSoftDeleteAllIntNullClass() {
        assertThrows(IllegalArgumentException.class, () -> SoftDeleteHelper.softDeleteAll(em, null, true));
    }

    @Test
    void testSoftDeleteAllStringNullEm() {
        assertThrows(IllegalArgumentException.class,
            () -> SoftDeleteHelper.softDeleteAll(null, SoftDeleteStringTestEntity.class, true));
    }

    @Test
    void testSoftDeleteAllStringNullClass() {
        assertThrows(IllegalArgumentException.class, () -> SoftDeleteHelper.softDeleteAll(em, null, true));
    }

    @Test
    void testSoftDeleteAllEnumNullEm() {
        assertThrows(IllegalArgumentException.class,
            () -> SoftDeleteHelper.softDeleteAll(null, SoftDeleteEnumTestEntity.class, true));
    }

    @Test
    void testSoftDeleteAllEnumNullClass() {
        assertThrows(IllegalArgumentException.class, () -> SoftDeleteHelper.softDeleteAll(em, null, true));
    }

    @Test
    void testSoftDeleteByIdsIntEmptyList() {
        int count = SoftDeleteHelper.softDeleteByIds(em, SoftDeleteIntTestEntity.class, List.of());
        assertEquals(0, count);
    }

    @Test
    void testSoftDeleteByIdsIntNullList() {
        int count = SoftDeleteHelper.softDeleteByIds(em, SoftDeleteIntTestEntity.class, null);
        assertEquals(0, count);
    }

    @Test
    void testSoftDeleteByIdsStringEmptyList() {
        int count = SoftDeleteHelper.softDeleteByIds(em, SoftDeleteStringTestEntity.class, List.of());
        assertEquals(0, count);
    }

    @Test
    void testSoftDeleteByIdsStringNullList() {
        int count = SoftDeleteHelper.softDeleteByIds(em, SoftDeleteStringTestEntity.class, null);
        assertEquals(0, count);
    }

    @Test
    void testSoftDeleteByIdsEnumEmptyList() {
        int count = SoftDeleteHelper.softDeleteByIds(em, SoftDeleteEnumTestEntity.class, List.of());
        assertEquals(0, count);
    }

    @Test
    void testSoftDeleteByIdsEnumNullList() {
        int count = SoftDeleteHelper.softDeleteByIds(em, SoftDeleteEnumTestEntity.class, null);
        assertEquals(0, count);
    }

    @Test
    void testSoftDeleteByIdsIntAlreadyDeleted() {
        var e1 = intRepo.save(buildInt("a", 1));
        intRepo.flush();

        int count = SoftDeleteHelper.softDeleteByIds(em, SoftDeleteIntTestEntity.class, List.of(e1.getId()));
        assertEquals(1, count);
    }

    @Test
    void testSoftDeleteByIdsStringAlreadyDeleted() {
        var e1 = strRepo.save(buildStr("a", "Y"));
        strRepo.flush();

        int count = SoftDeleteHelper.softDeleteByIds(em, SoftDeleteStringTestEntity.class, List.of(e1.getId()));
        assertEquals(1, count);
    }

    @Test
    void testSoftDeleteByIdsEnumAlreadyDeleted() {
        var e1 = enumRepo.save(buildEnum("a", Status.ARCHIVED));
        enumRepo.flush();

        int count = SoftDeleteHelper.softDeleteByIds(em, SoftDeleteEnumTestEntity.class, List.of(e1.getId()));
        assertEquals(1, count);
    }

    @Test
    void testSoftDeleteAllBooleanNoUnconditional() {
        assertThrows(IllegalStateException.class,
            () -> SoftDeleteHelper.softDeleteAll(em, SoftDeleteTestEntity.class, false));
    }

    @Test
    void testSoftDeleteAllIntNoUnconditional() {
        assertThrows(IllegalStateException.class,
            () -> SoftDeleteHelper.softDeleteAll(em, SoftDeleteIntTestEntity.class, false));
    }

    @Test
    void testSoftDeleteAllStringNoUnconditional() {
        assertThrows(IllegalStateException.class,
            () -> SoftDeleteHelper.softDeleteAll(em, SoftDeleteStringTestEntity.class, false));
    }

    @Test
    void testSoftDeleteAllEnumNoUnconditional() {
        assertThrows(IllegalStateException.class,
            () -> SoftDeleteHelper.softDeleteAll(em, SoftDeleteEnumTestEntity.class, false));
    }
}
