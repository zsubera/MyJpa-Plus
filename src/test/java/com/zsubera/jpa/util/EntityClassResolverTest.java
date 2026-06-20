package com.zsubera.jpa.util;

import static org.junit.jupiter.api.Assertions.*;

import com.zsubera.jpa.repository.MyJpaRepository;
import com.zsubera.jpa.repository.MyJpaTestEntity;
import com.zsubera.jpa.repository.MyJpaTestRepository;
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
    void testResolveIdFieldNameThrowsExceptionForEntityWithoutId() {
        // String.class has no @Id field, should throw IllegalStateException
        assertThrows(IllegalStateException.class, () -> EntityClassResolver.resolveIdFieldName(String.class));
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

    @Test
    void testResolveIdFieldNameWithEmbeddedId() {
        String fieldName = EntityClassResolver.resolveIdFieldName(EmbeddedIdEntity.class);
        assertEquals("id", fieldName);
    }

    @Test
    void testResolveIdFieldNameWithIdClass() {
        String fieldName = EntityClassResolver.resolveIdFieldName(IdClassEntity.class);
        assertEquals("id1", fieldName);
    }

    @Test
    void testHasCompositeKeyWithEmbeddedId() {
        assertTrue(EntityClassResolver.hasCompositeKey(EmbeddedIdEntity.class));
    }

    @Test
    void testHasCompositeKeyWithIdClass() {
        assertTrue(EntityClassResolver.hasCompositeKey(IdClassEntity.class));
    }

    @Test
    void testHasCompositeKeyWithSingleId() {
        assertFalse(EntityClassResolver.hasCompositeKey(MyJpaTestEntity.class));
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

    @jakarta.persistence.Entity
    static class EmbeddedIdEntity {
        @jakarta.persistence.EmbeddedId
        private CompositeKey id;

        public CompositeKey getId() {
            return id;
        }

        public void setId(CompositeKey id) {
            this.id = id;
        }
    }

    static class CompositeKey implements java.io.Serializable {
        private Long id;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }
    }

    @jakarta.persistence.Entity
    @jakarta.persistence.IdClass(IdClassKey.class)
    static class IdClassEntity {
        @jakarta.persistence.Id
        private Long id1;

        @jakarta.persistence.Id
        private Long id2;

        public Long getId1() {
            return id1;
        }

        public void setId1(Long id1) {
            this.id1 = id1;
        }

        public Long getId2() {
            return id2;
        }

        public void setId2(Long id2) {
            this.id2 = id2;
        }
    }

    static class IdClassKey implements java.io.Serializable {
        private Long id1;
        private Long id2;

        public Long getId1() {
            return id1;
        }

        public void setId1(Long id1) {
            this.id1 = id1;
        }

        public Long getId2() {
            return id2;
        }

        public void setId2(Long id2) {
            this.id2 = id2;
        }
    }
}
