package com.zsubera.jpa.spec;

import static org.junit.jupiter.api.Assertions.*;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

@DataJpaTest
@org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase(
    replace = org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE)
class ConditionBuilderValidationTest {

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        repository.flush();
    }

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
        assertThrows(IllegalArgumentException.class, () -> qs.like(TestEntity::getName, null));
    }

    @Test
    void testLikeNullValueThrowsException() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        // like() now delegates to likeSafe(), which throws IllegalArgumentException for null
        assertThrows(IllegalArgumentException.class, () -> qs.like(TestEntity::getName, null));
    }

    @Test
    void testNotLikeNullValueThrowsException() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        // notLike() now delegates to notLikeSafe(), which throws IllegalArgumentException for null
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
            () -> qs.multiLike("test", TestEntity::getName, (SFunction<TestEntity, String>)null));
    }

    @Test
    void testMultiLikeNullFieldsArrayThrowsException() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        assertThrows(IllegalArgumentException.class, () -> qs.multiLike("test", (SFunction<TestEntity, ?>[])null));
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
        qs.like(true, TestEntity::getName, "ell");
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

    @Test
    void testConditionalNeTrueAppliesCondition() {
        repository.save(newEntity("a", 1));
        repository.save(newEntity("b", 2));
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.ne(true, TestEntity::getStatus, 1);
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
    }

    @Test
    void testConditionalGtTrueAppliesCondition() {
        repository.save(newEntity("a", 1));
        repository.save(newEntity("b", 5));
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.gt(true, TestEntity::getStatus, 3);
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
    }

    @Test
    void testConditionalGeFalseSkipsCondition() {
        repository.save(newEntity("a", 1));
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.ge(false, TestEntity::getStatus, 100);
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
    }

    @Test
    void testConditionalLtTrueAppliesCondition() {
        repository.save(newEntity("a", 1));
        repository.save(newEntity("b", 10));
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.lt(true, TestEntity::getStatus, 5);
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
    }

    @Test
    void testConditionalLeFalseSkipsCondition() {
        repository.save(newEntity("a", 1));
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.le(false, TestEntity::getStatus, 0);
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
    }

    @Test
    void testConditionalNotLikeTrueAppliesCondition() {
        // notLike(true, ...) now delegates to notLikeSafe(), which escapes wildcards
        repository.save(newEntity("hello", 0));
        repository.save(newEntity("world", 0));
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        // notLikeSafe escapes wildcards, so "hello" is treated as literal
        qs.notLike(true, TestEntity::getName, "hello");
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        // NOT LIKE 'hello' matches "world" only
        assertEquals(1, result.size());
    }

    @Test
    void testConditionalStartsWithTrueAppliesCondition() {
        repository.save(newEntity("abc", 0));
        repository.save(newEntity("xyz", 0));
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.startsWith(true, TestEntity::getName, "ab");
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
    }

    @Test
    void testConditionalEndsWithFalseSkipsCondition() {
        repository.save(newEntity("abc", 0));
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.endsWith(false, TestEntity::getName, "zzz");
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
    }

    @Test
    void testConditionalContainsTrueAppliesCondition() {
        repository.save(newEntity("hello", 0));
        repository.save(newEntity("world", 0));
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.like(true, TestEntity::getName, "ell");
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
    }

    @Test
    void testConditionalInTrueAppliesCondition() {
        repository.save(newEntity("a", 1));
        repository.save(newEntity("b", 2));
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.in(true, TestEntity::getStatus, 1);
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
    }

    @Test
    void testConditionalInCollectionTrueAppliesCondition() {
        repository.save(newEntity("a", 1));
        repository.save(newEntity("b", 2));
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.in(true, TestEntity::getStatus, java.util.Arrays.asList(1));
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
    }

    @Test
    void testConditionalNotInTrueAppliesCondition() {
        repository.save(newEntity("a", 1));
        repository.save(newEntity("b", 2));
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.notIn(true, TestEntity::getStatus, 1);
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
    }

    @Test
    void testNotBetweenNullStartThrowsException() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        assertThrows(IllegalArgumentException.class, () -> qs.notBetween(TestEntity::getStatus, null, 5));
    }

    @Test
    void testNotBetweenNullEndThrowsException() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        assertThrows(IllegalArgumentException.class, () -> qs.notBetween(TestEntity::getStatus, 1, null));
    }

    @Test
    void testEqIgnoreCaseNullFieldThrowsException() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        assertThrows(IllegalArgumentException.class, () -> qs.eqIgnoreCase(null, "value"));
    }

    @Test
    void testIsEmptyNullFieldThrowsException() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        assertThrows(IllegalArgumentException.class, () -> qs.isEmpty(null));
    }

    @Test
    void testIsNotEmptyNullFieldThrowsException() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        assertThrows(IllegalArgumentException.class, () -> qs.isNotEmpty(null));
    }

    @Test
    void testBetweenTypeMismatchThrowsException() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        assertThrows(IllegalArgumentException.class, () -> qs.between(TestEntity::getName, 1, "abc"));
    }

    @Test
    void testNotBetweenTypeMismatchThrowsException() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        assertThrows(IllegalArgumentException.class, () -> qs.notBetween(TestEntity::getName, 1, "abc"));
    }

    @Test
    void testBetweenCrossNumericTypeAllowed() {
        // Integer vs Long should be allowed as both are Number types
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        assertDoesNotThrow(() -> qs.between(TestEntity::getStatus, 1, 2L));
    }

    @Test
    void testNotBetweenCrossNumericTypeAllowed() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        assertDoesNotThrow(() -> qs.notBetween(TestEntity::getStatus, 1, 2L));
    }

    @Test
    void testMultiLikeWithNestedFieldValidatesEachSegment() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        assertDoesNotThrow(() -> qs.multiLike("test", "name"));
    }

    @Test
    void testMultiLikeWithInvalidNestedFieldThrows() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        assertThrows(IllegalArgumentException.class, () -> qs.multiLike("test", "name;DROP"));
    }

    @Test
    void testMultiLikeWithEmptySegmentThrows() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        assertThrows(IllegalArgumentException.class, () -> qs.multiLike("test", "name."));
    }

    @Test
    void testMultiLikeWithNullFieldNameThrows() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        assertThrows(IllegalArgumentException.class, () -> qs.multiLike("test", (String[])null));
    }

    private TestEntity newEntity(String name, int status) {
        TestEntity entity = new TestEntity();
        entity.setName(name);
        entity.setStatus(status);
        return entity;
    }
}
