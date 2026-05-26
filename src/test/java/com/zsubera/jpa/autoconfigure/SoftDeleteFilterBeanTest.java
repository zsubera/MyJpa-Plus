package com.zsubera.jpa.autoconfigure;

import com.zsubera.jpa.spec.SoftDeleteTestEntity;
import com.zsubera.jpa.spec.TestEntity;
import com.zsubera.jpa.update.SoftDeleteHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.domain.Specification;

import static org.junit.jupiter.api.Assertions.*;

class SoftDeleteFilterBeanTest {

    private SoftDeleteFilterBean filterBean;

    @BeforeEach
    void setUp() {
        MyJpaPlusProperties.SoftDelete sd = new MyJpaPlusProperties.SoftDelete();
        sd.setAutoFilter(true);
        MyJpaPlusProperties props = new MyJpaPlusProperties();
        props.setSoftDelete(sd);
        filterBean = new SoftDeleteFilterBean(props);
    }

    @Test
    void testApplyWithNullSpecReturnsNotDeletedFilter() {
        Specification<SoftDeleteTestEntity> result = filterBean.apply(null, SoftDeleteTestEntity.class);
        assertNotNull(result);
    }

    @Test
    void testApplyWithSpecReturnsCombinedFilter() {
        Specification<SoftDeleteTestEntity> spec = (root, query, cb) -> cb.equal(root.get("name"), "test");
        Specification<SoftDeleteTestEntity> result = filterBean.apply(spec, SoftDeleteTestEntity.class);
        assertNotNull(result);
    }

    @Test
    void testApplyReturnsOriginalSpecIfNoSoftDeleteField() {
        Specification<TestEntity> spec = (root, query, cb) -> cb.equal(root.get("name"), "test");
        Specification<TestEntity> result = filterBean.apply(spec, TestEntity.class);
        assertSame(spec, result, "Should return the original spec when entity has no @SoftDelete");
    }

    @Test
    void testApplyWithNullSpecAndNoSoftDeleteReturnsNull() {
        Specification<TestEntity> result = filterBean.apply(null, TestEntity.class);
        assertNull(result);
    }

    @Test
    void testHasSoftDeleteFieldReturnsTrueForAnnotatedEntity() {
        assertTrue(filterBean.hasSoftDeleteField(SoftDeleteTestEntity.class));
    }

    @Test
    void testHasSoftDeleteFieldReturnsFalseForEntityWithoutAnnotation() {
        assertFalse(filterBean.hasSoftDeleteField(TestEntity.class));
    }

    @Test
    void testRegisterEntityWarmsCache() {
        filterBean.registerEntity(SoftDeleteTestEntity.class);
        assertTrue(filterBean.hasSoftDeleteField(SoftDeleteTestEntity.class));
    }
}
