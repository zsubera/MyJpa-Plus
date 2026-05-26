package com.zsubera.jpa.repository;

import com.zsubera.jpa.spec.SoftDeleteTestEntity;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

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
}
