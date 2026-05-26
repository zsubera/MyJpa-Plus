package com.zsubera.jpa.update;

import com.zsubera.jpa.spec.SoftDeleteTestEntity;
import com.zsubera.jpa.spec.SoftDeleteTestEntityRepository;
import com.zsubera.jpa.spec.TestApplication;
import com.zsubera.jpa.spec.TestEntity;
import com.zsubera.jpa.spec.TestEntityRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.context.ContextConfiguration;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ContextConfiguration(classes = TestApplication.class)
class SoftDeleteHelperTest {

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
    void testIsDeletedOnEntityWithoutSoftDeleteReturnsAll() {
        testEntityRepository.save(newEntity("a", 1));

        Specification<TestEntity> spec = SoftDeleteHelper.isDeleted(TestEntity.class);
        List<TestEntity> result = testEntityRepository.findAll(spec);

        assertEquals(1, result.size());
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
    void testIsSoftDeletedReturnsFalseForActiveEntity() {
        SoftDeleteTestEntity active = new SoftDeleteTestEntity();
        active.setName("active");
        active.setDeleted(false);
        repository.save(active);

        assertFalse(SoftDeleteHelper.isSoftDeleted(SoftDeleteTestEntity.class, active));
    }

    @Test
    void testIsSoftDeletedReturnsFalseForEntityWithoutAnnotation() {
        TestEntity entity = newEntity("test", 1);

        assertFalse(SoftDeleteHelper.isSoftDeleted(TestEntity.class, entity));
    }

    private TestEntity newEntity(String name, int status) {
        TestEntity entity = new TestEntity();
        entity.setName(name);
        entity.setStatus(status);
        return entity;
    }
}
