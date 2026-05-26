package com.zsubera.jpa.spec;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
class ConditionBuilderValidationTest {

    @Autowired
    private TestEntityRepository repository;

    @PersistenceContext
    private EntityManager em;

    @Test
    void testStartsWithNullValueThrowsException() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        assertThrows(IllegalArgumentException.class, () -> qs.startsWith(TestEntity::getName, null));
    }

    @Test
    void testEndsWithNullValueThrowsException() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        assertThrows(IllegalArgumentException.class, () -> qs.endsWith(TestEntity::getName, null));
    }

    @Test
    void testContainsNullValueThrowsException() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        assertThrows(IllegalArgumentException.class, () -> qs.contains(TestEntity::getName, null));
    }

    @Test
    void testLikeNullValueThrowsException() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        assertThrows(IllegalArgumentException.class, () -> qs.like(TestEntity::getName, null));
    }

    @Test
    void testNotLikeNullValueThrowsException() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        assertThrows(IllegalArgumentException.class, () -> qs.notLike(TestEntity::getName, null));
    }

    @Test
    void testLikeIgnoreCaseNullValueThrowsException() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        assertThrows(IllegalArgumentException.class, () -> qs.likeIgnoreCase(TestEntity::getName, null));
    }

    @Test
    void testGtNullValueThrowsException() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        assertThrows(IllegalArgumentException.class, () -> qs.gt(TestEntity::getStatus, null));
    }

    @Test
    void testGeNullValueThrowsException() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        assertThrows(IllegalArgumentException.class, () -> qs.ge(TestEntity::getStatus, null));
    }

    @Test
    void testLtNullValueThrowsException() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        assertThrows(IllegalArgumentException.class, () -> qs.lt(TestEntity::getStatus, null));
    }

    @Test
    void testLeNullValueThrowsException() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        assertThrows(IllegalArgumentException.class, () -> qs.le(TestEntity::getStatus, null));
    }

    @Test
    void testBetweenNullStartThrowsException() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        assertThrows(IllegalArgumentException.class, () -> qs.between(TestEntity::getStatus, null, 5));
    }

    @Test
    void testBetweenNullEndThrowsException() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        assertThrows(IllegalArgumentException.class, () -> qs.between(TestEntity::getStatus, 1, null));
    }

    @Test
    void testBetweenStartGreaterThanEndThrowsException() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        assertThrows(IllegalArgumentException.class, () -> qs.between(TestEntity::getStatus, 10, 1));
    }

    @Test
    void testNotBetweenStartGreaterThanEndThrowsException() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        assertThrows(IllegalArgumentException.class, () -> qs.notBetween(TestEntity::getStatus, 10, 1));
    }

    @Test
    void testBetweenEqualBoundsAllowed() {
        repository.save(newEntity("item", 5));
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.between(TestEntity::getStatus, 5, 5);
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
    }

    @Test
    void testMultiLikeNullFieldInArrayThrowsException() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        assertThrows(IllegalArgumentException.class,
                () -> qs.multiLike("test", TestEntity::getName, (SFunction<TestEntity, String>) null));
    }

    @Test
    void testMultiLikeNullFieldsArrayThrowsException() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        assertThrows(IllegalArgumentException.class,
                () -> qs.multiLike("test", (SFunction<TestEntity, ?>[]) null));
    }

    @Test
    void testConditionalEqTrueAddsCondition() {
        repository.save(newEntity("match", 1));
        repository.save(newEntity("other", 2));
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.eq(true, TestEntity::getName, "match");
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
        assertEquals("match", result.get(0).getName());
    }

    @Test
    void testConditionalEqFalseSkipsCondition() {
        repository.save(newEntity("match", 1));
        repository.save(newEntity("other", 2));
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.eq(false, TestEntity::getName, "match");
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(2, result.size());
    }

    @Test
    void testConditionalLikeTrueAddsCondition() {
        repository.save(newEntity("hello", 0));
        repository.save(newEntity("world", 0));
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.like(true, TestEntity::getName, "%ell%");
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
    }

    @Test
    void testConditionalBetweenFalseSkipsCondition() {
        repository.save(newEntity("a", 1));
        repository.save(newEntity("b", 5));
        repository.save(newEntity("c", 10));
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.between(false, TestEntity::getStatus, 1, 5);
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(3, result.size());
    }

    @Test
    void testConditionalMultiLikeFalseSkipsCondition() {
        repository.save(newEntity("hello", 0));
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.multiLike(false, "hel", TestEntity::getName);
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
    }

    @Test
    void testConditionalNotInCollectionFalseSkipsCondition() {
        repository.save(newEntity("hello", 0));
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.notIn(false, TestEntity::getName, java.util.Arrays.asList("hello", "world"));
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
    }

    @Test
    void testConditionalNotInCollectionTrueAppliesCondition() {
        repository.save(newEntity("alpha", 0));
        repository.save(newEntity("beta", 0));
        repository.save(newEntity("gamma", 0));
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.notIn(true, TestEntity::getName, java.util.Arrays.asList("alpha", "beta"));
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
        assertEquals("gamma", result.get(0).getName());
    }

    private TestEntity newEntity(String name, int status) {
        TestEntity entity = new TestEntity();
        entity.setName(name);
        entity.setStatus(status);
        return entity;
    }
}
