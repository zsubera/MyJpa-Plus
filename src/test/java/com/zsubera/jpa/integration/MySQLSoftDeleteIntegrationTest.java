package com.zsubera.jpa.integration;

import static org.junit.jupiter.api.Assertions.*;

import com.zsubera.jpa.softdelete.SoftDeleteHelper;
import com.zsubera.jpa.spec.QuerySpec;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

@Tag("integration")
@SpringBootTest
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:mysql://localhost:3306/test?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC",
    "spring.datasource.username=root", "spring.datasource.password=1351.zhong",
    "spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
    "spring.jpa.database-platform=org.hibernate.dialect.MySQLDialect", "spring.jpa.hibernate.ddl-auto=create"})
class MySQLSoftDeleteIntegrationTest {

    @Autowired
    private SoftDeleteEntityRepository repository;

    @PersistenceContext
    private EntityManager em;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        repository.flush();
    }

    @Test
    void testIsNotDeletedFiltersOutSoftDeletedRecords() {
        SoftDeleteEntity activeEntity = new SoftDeleteEntity();
        activeEntity.setName("active");
        activeEntity.setActive(false);
        repository.save(activeEntity);

        SoftDeleteEntity deletedEntity = new SoftDeleteEntity();
        deletedEntity.setName("deleted");
        deletedEntity.setActive(true);
        repository.save(deletedEntity);
        repository.flush();

        Specification<SoftDeleteEntity> spec = SoftDeleteHelper.isNotDeleted(SoftDeleteEntity.class);
        List<SoftDeleteEntity> result = repository.findAll(spec);

        assertEquals(1, result.size());
        assertEquals("active", result.get(0).getName());
    }

    @Test
    void testIsDeletedReturnsOnlySoftDeletedRecords() {
        SoftDeleteEntity activeEntity = new SoftDeleteEntity();
        activeEntity.setName("active");
        activeEntity.setActive(false);
        repository.save(activeEntity);

        SoftDeleteEntity deletedEntity = new SoftDeleteEntity();
        deletedEntity.setName("deleted");
        deletedEntity.setActive(true);
        repository.save(deletedEntity);
        repository.flush();

        Specification<SoftDeleteEntity> spec = SoftDeleteHelper.isDeleted(SoftDeleteEntity.class);
        List<SoftDeleteEntity> result = repository.findAll(spec);

        assertEquals(1, result.size());
        assertEquals("deleted", result.get(0).getName());
    }

    @Test
    void testNotDeletedQueryReturnsQuerySpecWithFilter() {
        SoftDeleteEntity activeEntity = new SoftDeleteEntity();
        activeEntity.setName("target");
        activeEntity.setActive(false);
        repository.save(activeEntity);

        SoftDeleteEntity deletedEntity = new SoftDeleteEntity();
        deletedEntity.setName("target");
        deletedEntity.setActive(true);
        repository.save(deletedEntity);
        repository.flush();

        var qs = SoftDeleteHelper.notDeletedQuery(SoftDeleteEntity.class);
        qs.eq(SoftDeleteEntity::getName, "target");
        List<SoftDeleteEntity> result = repository.findAll(qs.toSpecification());

        assertEquals(1, result.size());
        assertFalse(result.get(0).getActive());
    }

    @Test
    @Transactional
    void testSoftDeleteByIdsMarksRecordsAsDeleted() {
        SoftDeleteEntity entity1 = new SoftDeleteEntity();
        entity1.setName("entity1");
        entity1.setActive(false);
        repository.save(entity1);

        SoftDeleteEntity entity2 = new SoftDeleteEntity();
        entity2.setName("entity2");
        entity2.setActive(false);
        repository.save(entity2);
        repository.flush();

        int count = SoftDeleteHelper.softDeleteByIds(em, SoftDeleteEntity.class, List.of(entity1.getId()));
        assertEquals(1, count);

        em.clear();

        SoftDeleteEntity found1 = repository.findById(entity1.getId()).orElse(null);
        assertNotNull(found1);
        assertTrue(found1.getActive(), "Entity should be soft deleted (active=true means deleted)");

        SoftDeleteEntity found2 = repository.findById(entity2.getId()).orElse(null);
        assertNotNull(found2);
        assertFalse(found2.getActive(), "Entity should remain not-deleted (active=false)");
    }

    @Test
    @Transactional
    void testSoftDeleteAllMarksAllRecordsAsDeleted() {
        SoftDeleteEntity entity1 = new SoftDeleteEntity();
        entity1.setName("entity1");
        entity1.setActive(false);
        repository.save(entity1);

        SoftDeleteEntity entity2 = new SoftDeleteEntity();
        entity2.setName("entity2");
        entity2.setActive(false);
        repository.save(entity2);
        repository.flush();

        int count = SoftDeleteHelper.softDeleteAll(em, SoftDeleteEntity.class, true);
        assertEquals(2, count);

        em.clear();

        List<SoftDeleteEntity> all = repository.findAll();
        for (SoftDeleteEntity entity : all) {
            assertTrue(entity.getActive(), "All entities should be soft deleted (active=true means deleted)");
        }
    }

    @Test
    void testIsSoftDeletedReturnsTrueForDeletedEntity() {
        SoftDeleteEntity deleted = new SoftDeleteEntity();
        deleted.setName("deleted");
        deleted.setActive(true);
        repository.save(deleted);
        repository.flush();

        assertTrue(SoftDeleteHelper.isSoftDeleted(SoftDeleteEntity.class, deleted));
    }

    @Test
    void testIsSoftDeletedReturnsFalseForActiveEntity() {
        SoftDeleteEntity active = new SoftDeleteEntity();
        active.setName("active");
        active.setActive(false);
        repository.save(active);
        repository.flush();

        assertFalse(SoftDeleteHelper.isSoftDeleted(SoftDeleteEntity.class, active));
    }

    @Test
    void testCombinedQueryWithSoftDeleteFilter() {
        SoftDeleteEntity active1 = new SoftDeleteEntity();
        active1.setName("target");
        active1.setStatus(1);
        active1.setActive(false);
        repository.save(active1);

        SoftDeleteEntity active2 = new SoftDeleteEntity();
        active2.setName("other");
        active2.setStatus(1);
        active2.setActive(false);
        repository.save(active2);

        SoftDeleteEntity deleted1 = new SoftDeleteEntity();
        deleted1.setName("target");
        deleted1.setStatus(1);
        deleted1.setActive(true);
        repository.save(deleted1);
        repository.flush();

        QuerySpec<SoftDeleteEntity> qs = SoftDeleteHelper.notDeletedQuery(SoftDeleteEntity.class);
        qs.eq(SoftDeleteEntity::getName, "target");
        qs.eq(SoftDeleteEntity::getStatus, 1);

        List<SoftDeleteEntity> result = repository.findAll(qs.toSpecification());

        assertEquals(1, result.size());
        assertEquals("target", result.get(0).getName());
        assertFalse(result.get(0).getActive());
    }

    @Test
    void testFindSoftDeleteFieldReturnsFieldNameForAnnotatedEntity() {
        String fieldName = SoftDeleteHelper.findSoftDeleteField(SoftDeleteEntity.class);
        assertEquals("active", fieldName);
    }
}
