package com.zsubera.jpa.repository;

import static org.junit.jupiter.api.Assertions.*;

import com.zsubera.jpa.spec.SoftDeleteTestEntity;
import org.junit.jupiter.api.Test;

class EntityClassResolverTest {

    @Test
    void testResolveIdFieldNameReturnsIdForEntityWithIdAnnotation() {
        String fieldName = EntityClassResolver.resolveIdFieldName(MyJpaTestEntity.class);
        assertEquals("id", fieldName);
    }

    @Test
    void testResolveIdFieldNameReturnsIdForSoftDeleteEntity() {
        String fieldName = EntityClassResolver.resolveIdFieldName(SoftDeleteTestEntity.class);
        assertEquals("id", fieldName);
    }

    @Test
    void testResolveIdFieldNameReturnsDefaultIdForEntityWithoutId() {
        // String.class has no @Id field, should fall back to "id"
        String fieldName = EntityClassResolver.resolveIdFieldName(String.class);
        assertEquals("id", fieldName);
    }

    @Test
    void testResolveIdFieldNameIsCached() {
        String first = EntityClassResolver.resolveIdFieldName(MyJpaTestEntity.class);
        String second = EntityClassResolver.resolveIdFieldName(MyJpaTestEntity.class);
        assertSame(first, second);
    }

    @Test
    void testResolveReturnsEntityClass() {
        Class<MyJpaTestEntity> entityClass = EntityClassResolver.resolve(MyJpaTestRepository.class);
        assertEquals(MyJpaTestEntity.class, entityClass);
    }

    @Test
    void testResolveIsCached() {
        Class<MyJpaTestEntity> first = EntityClassResolver.resolve(MyJpaTestRepository.class);
        Class<MyJpaTestEntity> second = EntityClassResolver.resolve(MyJpaTestRepository.class);
        assertSame(first, second);
    }

    @Test
    void testResolveReturnsNullForNonMyJpaRepository() {
        Class<?> result = EntityClassResolver.resolve(NonMyJpaRepository.class);
        assertNull(result);
    }

    @Test
    void testResolveThroughIntermediateInterface() {
        Class<IndirectEntity> result = EntityClassResolver.resolve(IndirectRepo.class);
        assertEquals(IndirectEntity.class, result);
    }

    @Test
    void testResolveIsCachedForIndirectRepo() {
        Class<IndirectEntity> first = EntityClassResolver.resolve(IndirectRepo.class);
        Class<IndirectEntity> second = EntityClassResolver.resolve(IndirectRepo.class);
        assertSame(first, second);
    }

    private interface NonMyJpaRepository {
        void dummy();
    }

    @jakarta.persistence.Entity
    @jakarta.persistence.Table(name = "indirect_entity")
    static class IndirectEntity {
        @jakarta.persistence.Id
        @jakarta.persistence.GeneratedValue(strategy = jakarta.persistence.GenerationType.IDENTITY)
        private Long id;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }
    }

    interface BaseRepo<T, ID> extends MyJpaRepository<T, ID> {}

    interface IndirectRepo extends BaseRepo<IndirectEntity, Long> {}
}
