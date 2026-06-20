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
        parentRepository.deleteAll();
        repository.flush();
    }

    @Autowired
    private TestEntityRepository repository;

    @Autowired
    private ParentEntityRepository parentRepository;

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

    // ===== deprecated 委托方法 =====

    @Test
    void testDeprecatedAddSafeFunctionNames() {
        ConditionBuilder.addSafeFunctionNames(List.of("custom_func"));
    }

    @Test
    void testDeprecatedAddBooleanFunctionNames() {
        ConditionBuilder.addBooleanFunctionNames(List.of("custom_bool"));
    }

    @Test
    void testDeprecatedFreezeExtraFunctionNames() {
        ConditionBuilder.freezeExtraFunctionNames();
    }

    // ===== eqStrict/neStrict 成功路径 =====

    @Test
    void testEqStrictSuccess() {
        repository.save(newEntity("a", 1));
        em.flush();
        em.clear();

        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.eqStrict(TestEntity::getName, "a");
        assertEquals(1, repository.findAll(qs.toSpecification()).size());
    }

    @Test
    void testNeStrictSuccess() {
        repository.save(newEntity("a", 1));
        repository.save(newEntity("b", 2));
        em.flush();
        em.clear();

        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.neStrict(TestEntity::getName, "a");
        assertEquals(1, repository.findAll(qs.toSpecification()).size());
    }

    // ===== notStartsWith/notEndsWith =====

    @Test
    void testNotStartsWith() {
        repository.save(newEntity("abc", 1));
        repository.save(newEntity("xyz", 2));
        em.flush();
        em.clear();

        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.notStartsWith(TestEntity::getName, "ab");
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
        assertEquals("xyz", result.get(0).getName());
    }

    @Test
    void testNotEndsWith() {
        repository.save(newEntity("abc", 1));
        repository.save(newEntity("xyz", 2));
        em.flush();
        em.clear();

        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.notEndsWith(TestEntity::getName, "bc");
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
        assertEquals("xyz", result.get(0).getName());
    }

    // ===== eqIgnoreCase/neIgnoreCase null 值 → IS NULL / IS NOT NULL =====

    @Test
    void testEqIgnoreCaseNullValueBecomesIsNull() {
        TestEntity e1 = newEntity("a", 1);
        e1.setName(null);
        repository.save(e1);
        repository.save(newEntity("b", 2));
        em.flush();
        em.clear();

        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.eqIgnoreCase(TestEntity::getName, null);
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
    }

    @Test
    void testNeIgnoreCaseNullValueBecomesIsNotNull() {
        TestEntity e1 = newEntity("a", 1);
        e1.setName(null);
        repository.save(e1);
        repository.save(newEntity("b", 2));
        em.flush();
        em.clear();

        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.neIgnoreCase(TestEntity::getName, null);
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
    }

    // ===== multiLike(String... null element) =====

    @Test
    void testMultiLikeStringNullElementThrows() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        assertThrows(IllegalArgumentException.class, () -> qs.multiLike("test", (String)null));
    }

    // ===== inSubQuery null 检查 =====

    @Test
    void testInSubQueryNullOuterFieldThrows() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        assertThrows(IllegalArgumentException.class, () -> qs.inSubQuery(null, TestEntity.class, sub -> {
        }));
    }

    @Test
    void testInSubQueryNullSubEntityThrows() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        assertThrows(IllegalArgumentException.class, () -> qs.inSubQuery(TestEntity::getStatus, null, sub -> {
        }));
    }

    @Test
    void testInSubQueryNullConfigThrows() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        assertThrows(IllegalArgumentException.class,
            () -> qs.inSubQuery(TestEntity::getStatus, TestEntity.class, null));
    }

    // ===== notInSubQuery null 检查 =====

    @Test
    void testNotInSubQueryNullOuterFieldThrows() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        assertThrows(IllegalArgumentException.class, () -> qs.notInSubQuery(null, TestEntity.class, sub -> {
        }));
    }

    @Test
    void testNotInSubQueryNullSubEntityThrows() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        assertThrows(IllegalArgumentException.class, () -> qs.notInSubQuery(TestEntity::getStatus, null, sub -> {
        }));
    }

    @Test
    void testNotInSubQueryNullConfigThrows() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        assertThrows(IllegalArgumentException.class,
            () -> qs.notInSubQuery(TestEntity::getStatus, TestEntity.class, null));
    }

    // ===== conditional boolean 方法 =====

    @Test
    void testEqStrictBooleanTrue() {
        repository.save(newEntity("target", 1));
        em.flush();
        em.clear();

        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.eqStrict(true, TestEntity::getName, "target");
        assertEquals(1, repository.findAll(qs.toSpecification()).size());
    }

    @Test
    void testEqStrictBooleanFalse() {
        repository.save(newEntity("target", 1));
        em.flush();
        em.clear();

        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.eqStrict(false, TestEntity::getName, "target");
        assertEquals(1, repository.findAll(qs.toSpecification()).size());
    }

    @Test
    void testNeStrictBooleanTrue() {
        repository.save(newEntity("target", 1));
        repository.save(newEntity("other", 2));
        em.flush();
        em.clear();

        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.neStrict(true, TestEntity::getName, "target");
        assertEquals(1, repository.findAll(qs.toSpecification()).size());
    }

    @Test
    void testNeStrictBooleanFalse() {
        repository.save(newEntity("target", 1));
        repository.save(newEntity("other", 2));
        em.flush();
        em.clear();

        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.neStrict(false, TestEntity::getName, "target");
        assertEquals(2, repository.findAll(qs.toSpecification()).size());
    }

    @Test
    void testMultiLikeStringBooleanTrue() {
        repository.save(newEntity("abc", 1));
        repository.save(newEntity("xyz", 2));
        em.flush();
        em.clear();

        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.multiLike(true, "ab", "name");
        assertEquals(1, repository.findAll(qs.toSpecification()).size());
    }

    @Test
    void testMultiLikeStringBooleanFalse() {
        repository.save(newEntity("abc", 1));
        repository.save(newEntity("xyz", 2));
        em.flush();
        em.clear();

        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.multiLike(false, "ab", "name");
        assertEquals(2, repository.findAll(qs.toSpecification()).size());
    }

    // ===== func() 方法 =====

    @Test
    void testFuncNullFieldThrows() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        assertThrows(IllegalArgumentException.class, () -> qs.func(null, "UPPER"));
    }

    @Test
    void testFuncNullFunctionNameThrows() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        assertThrows(IllegalArgumentException.class, () -> qs.func(TestEntity::getName, null));
    }

    @Test
    void testFuncEmptyFunctionNameThrows() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        assertThrows(IllegalArgumentException.class, () -> qs.func(TestEntity::getName, ""));
    }

    @Test
    void testFuncInvalidFunctionNameThrows() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        assertThrows(IllegalArgumentException.class, () -> qs.func(TestEntity::getName, "DROP TABLE"));
    }

    // ===== conditional boolean 方法（补充全覆盖） =====

    @Test
    void testConditionalGeTrueAddsCondition() {
        repository.save(newEntity("a", 1));
        repository.save(newEntity("b", 5));
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.ge(true, TestEntity::getStatus, 5);
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
    }

    @Test
    void testConditionalLtFalseSkipsCondition() {
        repository.save(newEntity("a", 1));
        repository.save(newEntity("b", 10));
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.lt(false, TestEntity::getStatus, 5);
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(2, result.size());
    }

    @Test
    void testConditionalLeTrueAddsCondition() {
        repository.save(newEntity("a", 1));
        repository.save(newEntity("b", 5));
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.le(true, TestEntity::getStatus, 1);
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
    }

    @Test
    void testConditionalNotLikeFalseSkipsCondition() {
        repository.save(newEntity("hello", 0));
        repository.save(newEntity("world", 0));
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.notLike(false, TestEntity::getName, "hello");
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(2, result.size());
    }

    @Test
    void testConditionalStartsWithFalseSkipsCondition() {
        repository.save(newEntity("abc", 0));
        repository.save(newEntity("xyz", 0));
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.startsWith(false, TestEntity::getName, "ab");
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(2, result.size());
    }

    @Test
    void testConditionalEndsWithTrueAddsCondition() {
        repository.save(newEntity("abc", 0));
        repository.save(newEntity("xyz", 0));
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.endsWith(true, TestEntity::getName, "bc");
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
    }

    @Test
    void testConditionalNotStartsWithTrueAddsCondition() {
        repository.save(newEntity("abc", 1));
        repository.save(newEntity("xyz", 2));
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.notStartsWith(true, TestEntity::getName, "ab");
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
        assertEquals("xyz", result.get(0).getName());
    }

    @Test
    void testConditionalNotStartsWithFalseSkipsCondition() {
        repository.save(newEntity("abc", 1));
        repository.save(newEntity("xyz", 2));
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.notStartsWith(false, TestEntity::getName, "ab");
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(2, result.size());
    }

    @Test
    void testConditionalNotEndsWithTrueAddsCondition() {
        repository.save(newEntity("abc", 1));
        repository.save(newEntity("xyz", 2));
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.notEndsWith(true, TestEntity::getName, "bc");
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
        assertEquals("xyz", result.get(0).getName());
    }

    @Test
    void testConditionalNotEndsWithFalseSkipsCondition() {
        repository.save(newEntity("abc", 1));
        repository.save(newEntity("xyz", 2));
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.notEndsWith(false, TestEntity::getName, "bc");
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(2, result.size());
    }

    @Test
    void testConditionalInVarargsFalseSkipsCondition() {
        repository.save(newEntity("a", 1));
        repository.save(newEntity("b", 2));
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.in(false, TestEntity::getStatus, 1, 2);
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(2, result.size());
    }

    @Test
    void testConditionalNotInVarargsFalseSkipsCondition() {
        repository.save(newEntity("a", 1));
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.notIn(false, TestEntity::getStatus, 1);
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
    }

    @Test
    void testConditionalBetweenTrueAddsCondition() {
        repository.save(newEntity("a", 1));
        repository.save(newEntity("b", 5));
        repository.save(newEntity("c", 10));
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.between(true, TestEntity::getStatus, 1, 5);
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(2, result.size());
    }

    @Test
    void testConditionalNotBetweenTrueAddsCondition() {
        repository.save(newEntity("a", 1));
        repository.save(newEntity("b", 5));
        repository.save(newEntity("c", 10));
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.notBetween(true, TestEntity::getStatus, 1, 5);
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
    }

    @Test
    void testConditionalNotBetweenFalseSkipsCondition() {
        repository.save(newEntity("a", 1));
        repository.save(newEntity("b", 5));
        repository.save(newEntity("c", 10));
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.notBetween(false, TestEntity::getStatus, 1, 5);
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(3, result.size());
    }

    @Test
    void testConditionalEqIgnoreCaseTrueAddsCondition() {
        repository.save(newEntity("hello", 0));
        repository.save(newEntity("world", 0));
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.eqIgnoreCase(true, TestEntity::getName, "HELLO");
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
    }

    @Test
    void testConditionalEqIgnoreCaseFalseSkipsCondition() {
        repository.save(newEntity("hello", 0));
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.eqIgnoreCase(false, TestEntity::getName, "HELLO");
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
    }

    @Test
    void testConditionalNeIgnoreCaseTrueAddsCondition() {
        repository.save(newEntity("hello", 0));
        repository.save(newEntity("world", 0));
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.neIgnoreCase(true, TestEntity::getName, "HELLO");
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
    }

    @Test
    void testConditionalNeIgnoreCaseFalseSkipsCondition() {
        repository.save(newEntity("hello", 0));
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.neIgnoreCase(false, TestEntity::getName, "HELLO");
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
    }

    @Test
    void testConditionalLikeIgnoreCaseTrueAddsCondition() {
        repository.save(newEntity("hello", 0));
        repository.save(newEntity("world", 0));
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.likeIgnoreCase(true, TestEntity::getName, "ELL");
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
    }

    @Test
    void testConditionalLikeIgnoreCaseFalseSkipsCondition() {
        repository.save(newEntity("hello", 0));
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.likeIgnoreCase(false, TestEntity::getName, "ELL");
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
    }

    @Test
    void testConditionalIsNullTrueAddsCondition() {
        TestEntity e = newEntity("test", 0);
        e.setName(null);
        repository.save(e);
        repository.save(newEntity("other", 0));
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.isNull(true, TestEntity::getName);
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
    }

    @Test
    void testConditionalIsNullFalseSkipsCondition() {
        TestEntity e = newEntity("test", 0);
        e.setName(null);
        repository.save(e);
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.isNull(false, TestEntity::getName);
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
    }

    @Test
    void testConditionalIsNotNullTrueAddsCondition() {
        repository.save(newEntity("a", 0));
        TestEntity e = newEntity("b", 0);
        e.setName(null);
        repository.save(e);
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.isNotNull(true, TestEntity::getName);
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
    }

    @Test
    void testConditionalIsNotNullFalseSkipsCondition() {
        repository.save(newEntity("a", 0));
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.isNotNull(false, TestEntity::getName);
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
    }

    @Test
    void testConditionalIsEmptyTrueAddsCondition() {
        ParentEntity p = new ParentEntity();
        p.setCategory("empty");
        parentRepository.save(p);
        ParentEntity p2 = new ParentEntity();
        p2.setCategory("withChild");
        parentRepository.save(p2);
        TestEntity child = newEntity("child", 0);
        child.setParent(p2);
        repository.save(child);
        em.flush();
        em.clear();

        QuerySpec<ParentEntity> qs = new QuerySpec<>();
        qs.isEmpty(true, ParentEntity::getChildren);
        List<ParentEntity> result = parentRepository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
    }

    @Test
    void testConditionalIsEmptyFalseSkipsCondition() {
        ParentEntity p = new ParentEntity();
        p.setCategory("test");
        parentRepository.save(p);

        QuerySpec<ParentEntity> qs = new QuerySpec<>();
        qs.isEmpty(false, ParentEntity::getChildren);
        List<ParentEntity> result = parentRepository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
    }

    @Test
    void testConditionalIsNotEmptyTrueAddsCondition() {
        ParentEntity p = new ParentEntity();
        p.setCategory("withChild");
        parentRepository.save(p);
        TestEntity child = newEntity("child", 0);
        child.setParent(p);
        repository.save(child);
        em.flush();
        em.clear();

        QuerySpec<ParentEntity> qs = new QuerySpec<>();
        qs.isNotEmpty(true, ParentEntity::getChildren);
        List<ParentEntity> result = parentRepository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
    }

    @Test
    void testConditionalIsNotEmptyFalseSkipsCondition() {
        ParentEntity p = new ParentEntity();
        p.setCategory("test");
        parentRepository.save(p);

        QuerySpec<ParentEntity> qs = new QuerySpec<>();
        qs.isNotEmpty(false, ParentEntity::getChildren);
        List<ParentEntity> result = parentRepository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
    }

    private TestEntity newEntity(String name, int status) {
        TestEntity entity = new TestEntity();
        entity.setName(name);
        entity.setStatus(status);
        return entity;
    }
}
