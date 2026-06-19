package com.zsubera.jpa.spec;

import static org.junit.jupiter.api.Assertions.*;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

@DataJpaTest
@org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase(
    replace = org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE)
class SubQuerySpecTest {

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        repository.flush();
    }

    @Autowired
    private TestEntityRepository repository;

    @Autowired
    private ParentEntityRepository parentRepository;

    @PersistenceContext
    private EntityManager em;

    @Test
    void testExistsWithComparisonOperator() {
        ParentEntity p = new ParentEntity();
        p.setCategory("cat");
        p.setLevel(10);
        em.persist(p);
        TestEntity child = newEntity("c1", 0);
        child.setParent(p);
        repository.save(child);

        QuerySpec<ParentEntity> qs = new QuerySpec<>();
        qs.exists(TestEntity.class, sub -> sub.gt(TestEntity::getStatus, -1));
        List<ParentEntity> result = parentRepository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
    }

    @Test
    void testExistsWithGeOperator() {
        ParentEntity p = new ParentEntity();
        p.setCategory("cat");
        p.setLevel(10);
        em.persist(p);
        TestEntity child = newEntity("c1", 5);
        child.setParent(p);
        repository.save(child);

        QuerySpec<ParentEntity> qs = new QuerySpec<>();
        qs.exists(TestEntity.class, sub -> sub.ge(TestEntity::getStatus, 5));
        List<ParentEntity> result = parentRepository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
    }

    @Test
    void testExistsWithLeOperator() {
        ParentEntity p = new ParentEntity();
        p.setCategory("cat");
        p.setLevel(10);
        em.persist(p);
        TestEntity child = newEntity("c1", 1);
        child.setParent(p);
        repository.save(child);

        QuerySpec<ParentEntity> qs = new QuerySpec<>();
        qs.exists(TestEntity.class, sub -> sub.le(TestEntity::getStatus, 1));
        List<ParentEntity> result = parentRepository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
    }

    @Test
    void testExistsWithInOperator() {
        ParentEntity p = new ParentEntity();
        p.setCategory("cat");
        p.setLevel(10);
        em.persist(p);
        TestEntity child = newEntity("c1", 2);
        child.setParent(p);
        repository.save(child);

        QuerySpec<ParentEntity> qs = new QuerySpec<>();
        qs.exists(TestEntity.class, sub -> sub.in(TestEntity::getStatus, Arrays.asList(1, 2, 3)));
        List<ParentEntity> result = parentRepository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
    }

    @Test
    void testExistsWithBetweenOperator() {
        ParentEntity p = new ParentEntity();
        p.setCategory("cat");
        p.setLevel(10);
        em.persist(p);
        TestEntity child = newEntity("c1", 5);
        child.setParent(p);
        repository.save(child);

        QuerySpec<ParentEntity> qs = new QuerySpec<>();
        qs.exists(TestEntity.class, sub -> sub.between(TestEntity::getStatus, 3, 7));
        List<ParentEntity> result = parentRepository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
    }

    @Test
    void testExistsWithLikeOperator() {
        ParentEntity p = new ParentEntity();
        p.setCategory("cat");
        p.setLevel(10);
        em.persist(p);
        TestEntity child = newEntity("hello", 0);
        child.setParent(p);
        repository.save(child);

        QuerySpec<ParentEntity> qs = new QuerySpec<>();
        qs.exists(TestEntity.class, sub -> sub.like(TestEntity::getName, "ell"));
        List<ParentEntity> result = parentRepository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
    }

    @Test
    void testExistsWithNotLikeOperator() {
        ParentEntity p = new ParentEntity();
        p.setCategory("cat");
        p.setLevel(10);
        em.persist(p);
        TestEntity child = newEntity("hello", 0);
        child.setParent(p);
        repository.save(child);

        QuerySpec<ParentEntity> qs = new QuerySpec<>();
        qs.exists(TestEntity.class, sub -> sub.notLike(TestEntity::getName, "xyz"));
        List<ParentEntity> result = parentRepository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
    }

    @Test
    void testExistsWithStartsWithOperator() {
        ParentEntity p = new ParentEntity();
        p.setCategory("cat");
        p.setLevel(10);
        em.persist(p);
        TestEntity child = newEntity("hello", 0);
        child.setParent(p);
        repository.save(child);

        QuerySpec<ParentEntity> qs = new QuerySpec<>();
        qs.exists(TestEntity.class, sub -> sub.startsWith(TestEntity::getName, "hel"));
        List<ParentEntity> result = parentRepository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
    }

    @Test
    void testExistsWithEndsWithOperator() {
        ParentEntity p = new ParentEntity();
        p.setCategory("cat");
        p.setLevel(10);
        em.persist(p);
        TestEntity child = newEntity("hello", 0);
        child.setParent(p);
        repository.save(child);

        QuerySpec<ParentEntity> qs = new QuerySpec<>();
        qs.exists(TestEntity.class, sub -> sub.endsWith(TestEntity::getName, "lo"));
        List<ParentEntity> result = parentRepository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
    }

    @Test
    void testExistsWithContainsOperator() {
        ParentEntity p = new ParentEntity();
        p.setCategory("cat");
        p.setLevel(10);
        em.persist(p);
        TestEntity child = newEntity("hello", 0);
        child.setParent(p);
        repository.save(child);

        QuerySpec<ParentEntity> qs = new QuerySpec<>();
        qs.exists(TestEntity.class, sub -> sub.like(TestEntity::getName, "ell"));
        List<ParentEntity> result = parentRepository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
    }

    @Test
    void testExistsWithIsNullOperator() {
        ParentEntity p = new ParentEntity();
        p.setCategory("cat");
        p.setLevel(10);
        em.persist(p);
        TestEntity child = new TestEntity();
        child.setName(null);
        child.setStatus(0);
        child.setParent(p);
        repository.save(child);

        QuerySpec<ParentEntity> qs = new QuerySpec<>();
        qs.exists(TestEntity.class, sub -> sub.isNull(TestEntity::getName));
        List<ParentEntity> result = parentRepository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
    }

    @Test
    void testExistsWithIsNotNullOperator() {
        ParentEntity p = new ParentEntity();
        p.setCategory("cat");
        p.setLevel(10);
        em.persist(p);
        TestEntity child = newEntity("named", 0);
        child.setParent(p);
        repository.save(child);

        QuerySpec<ParentEntity> qs = new QuerySpec<>();
        qs.exists(TestEntity.class, sub -> sub.isNotNull(TestEntity::getName));
        List<ParentEntity> result = parentRepository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
    }

    @Test
    void testExistsWithEqIgnoreCaseOperator() {
        ParentEntity p = new ParentEntity();
        p.setCategory("cat");
        p.setLevel(10);
        em.persist(p);
        TestEntity child = newEntity("Hello", 0);
        child.setParent(p);
        repository.save(child);

        QuerySpec<ParentEntity> qs = new QuerySpec<>();
        qs.exists(TestEntity.class, sub -> sub.eqIgnoreCase(TestEntity::getName, "HELLO"));
        List<ParentEntity> result = parentRepository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
    }

    @Test
    void testExistsWithLikeIgnoreCaseOperator() {
        ParentEntity p = new ParentEntity();
        p.setCategory("cat");
        p.setLevel(10);
        em.persist(p);
        TestEntity child = newEntity("HelloWorld", 0);
        child.setParent(p);
        repository.save(child);

        QuerySpec<ParentEntity> qs = new QuerySpec<>();
        qs.exists(TestEntity.class, sub -> sub.likeIgnoreCase(TestEntity::getName, "hello"));
        List<ParentEntity> result = parentRepository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
    }

    @Test
    void testExistsWithMultiLikeOperator() {
        ParentEntity p = new ParentEntity();
        p.setCategory("cat");
        p.setLevel(10);
        em.persist(p);
        TestEntity child = newEntity("searchable", 0);
        child.setParent(p);
        repository.save(child);

        QuerySpec<ParentEntity> qs = new QuerySpec<>();
        qs.exists(TestEntity.class, sub -> sub.multiLike("search", TestEntity::getName));
        List<ParentEntity> result = parentRepository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
    }

    @Test
    void testExistsWithSelectClause() {
        ParentEntity p = new ParentEntity();
        p.setCategory("cat");
        p.setLevel(10);
        em.persist(p);
        TestEntity child = newEntity("c1", 1);
        child.setParent(p);
        repository.save(child);

        QuerySpec<ParentEntity> qs = new QuerySpec<>();
        qs.exists(TestEntity.class, sub -> {
            sub.select(TestEntity::getStatus);
            sub.eq(TestEntity::getName, "c1");
        });
        List<ParentEntity> result = parentRepository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
    }

    @Test
    void testExistsWithRawPredicate() {
        ParentEntity p = new ParentEntity();
        p.setCategory("cat");
        p.setLevel(10);
        em.persist(p);
        TestEntity child = newEntity("c1", 1);
        child.setParent(p);
        repository.save(child);

        QuerySpec<ParentEntity> qs = new QuerySpec<>();
        qs.exists(TestEntity.class, sub -> sub.eq(TestEntity::getName, "c1"));
        List<ParentEntity> result = parentRepository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
    }

    @Test
    void testSubQueryNullValueThrowsException() {
        QuerySpec<ParentEntity> qs = new QuerySpec<>();
        qs.exists(TestEntity.class, sub -> sub.gt(TestEntity::getStatus, null));
        RuntimeException ex =
            assertThrows(RuntimeException.class, () -> parentRepository.findAll(qs.toSpecification()));
        assertTrue(ex.getCause() instanceof IllegalArgumentException || ex instanceof IllegalArgumentException);
    }

    @Test
    void testSubQueryLikeNullValueThrowsException() {
        QuerySpec<ParentEntity> qs = new QuerySpec<>();
        qs.exists(TestEntity.class, sub -> sub.like(TestEntity::getName, null));
        assertThrows(Exception.class, () -> parentRepository.findAll(qs.toSpecification()));
    }

    @Test
    void testSubQueryBetweenNullStartThrowsException() {
        QuerySpec<ParentEntity> qs = new QuerySpec<>();
        qs.exists(TestEntity.class, sub -> sub.between(TestEntity::getStatus, null, 5));
        RuntimeException ex =
            assertThrows(RuntimeException.class, () -> parentRepository.findAll(qs.toSpecification()));
        assertTrue(ex.getCause() instanceof IllegalArgumentException || ex instanceof IllegalArgumentException);
    }

    @Test
    void testSubQueryBetweenInvalidRangeThrowsException() {
        QuerySpec<ParentEntity> qs = new QuerySpec<>();
        qs.exists(TestEntity.class, sub -> sub.between(TestEntity::getStatus, 10, 1));
        RuntimeException ex =
            assertThrows(RuntimeException.class, () -> parentRepository.findAll(qs.toSpecification()));
        assertTrue(ex.getCause() instanceof IllegalArgumentException || ex instanceof IllegalArgumentException);
    }

    @Test
    void testSubQueryLikeIgnoreCaseNullValueThrowsException() {
        QuerySpec<ParentEntity> qs = new QuerySpec<>();
        qs.exists(TestEntity.class, sub -> sub.likeIgnoreCase(TestEntity::getName, null));
        RuntimeException ex =
            assertThrows(RuntimeException.class, () -> parentRepository.findAll(qs.toSpecification()));
        assertTrue(ex.getCause() instanceof IllegalArgumentException || ex instanceof IllegalArgumentException);
    }

    @Test
    void testExistsWithNotBetweenOperator() {
        ParentEntity p = new ParentEntity();
        p.setCategory("cat");
        p.setLevel(10);
        em.persist(p);
        TestEntity child = newEntity("c1", 1);
        child.setParent(p);
        repository.save(child);

        QuerySpec<ParentEntity> qs = new QuerySpec<>();
        qs.exists(TestEntity.class, sub -> sub.notBetween(TestEntity::getStatus, 5, 10));
        List<ParentEntity> result = parentRepository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
    }

    @Test
    void testSubQueryNotBetweenNullStartThrowsException() {
        QuerySpec<ParentEntity> qs = new QuerySpec<>();
        qs.exists(TestEntity.class, sub -> sub.notBetween(TestEntity::getStatus, null, 5));
        RuntimeException ex =
            assertThrows(RuntimeException.class, () -> parentRepository.findAll(qs.toSpecification()));
        assertTrue(ex.getCause() instanceof IllegalArgumentException || ex instanceof IllegalArgumentException);
    }

    @Test
    void testSubQueryNotBetweenInvalidRangeThrowsException() {
        QuerySpec<ParentEntity> qs = new QuerySpec<>();
        qs.exists(TestEntity.class, sub -> sub.notBetween(TestEntity::getStatus, 10, 1));
        RuntimeException ex =
            assertThrows(RuntimeException.class, () -> parentRepository.findAll(qs.toSpecification()));
        assertTrue(ex.getCause() instanceof IllegalArgumentException || ex instanceof IllegalArgumentException);
    }

    @Test
    void testExistsWithNeOperator() {
        ParentEntity p = new ParentEntity();
        p.setCategory("cat");
        p.setLevel(10);
        em.persist(p);
        TestEntity child = newEntity("c1", 1);
        child.setParent(p);
        repository.save(child);

        QuerySpec<ParentEntity> qs = new QuerySpec<>();
        qs.exists(TestEntity.class, sub -> sub.ne(TestEntity::getStatus, 99));
        List<ParentEntity> result = parentRepository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
    }

    @Test
    void testExistsWithNotInOperator() {
        ParentEntity p = new ParentEntity();
        p.setCategory("cat");
        p.setLevel(10);
        em.persist(p);
        TestEntity child = newEntity("c1", 1);
        child.setParent(p);
        repository.save(child);

        QuerySpec<ParentEntity> qs = new QuerySpec<>();
        qs.exists(TestEntity.class, sub -> sub.notIn(TestEntity::getStatus, 99, 100));
        List<ParentEntity> result = parentRepository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
    }

    @Test
    void testExistsWithNotInCollectionOperator() {
        ParentEntity p = new ParentEntity();
        p.setCategory("cat");
        p.setLevel(10);
        em.persist(p);
        TestEntity child = newEntity("c1", 1);
        child.setParent(p);
        repository.save(child);

        QuerySpec<ParentEntity> qs = new QuerySpec<>();
        qs.exists(TestEntity.class, sub -> sub.notIn(TestEntity::getStatus, java.util.Arrays.asList(99, 100)));
        List<ParentEntity> result = parentRepository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
    }

    @Test
    void testSubQueryMultiLikeWithNullKeywordNoOp() {
        QuerySpec<ParentEntity> qs = new QuerySpec<>();
        qs.exists(TestEntity.class, sub -> sub.multiLike(null, TestEntity::getName));
        assertNotNull(qs.toSpecification());
    }

    @Test
    void testSubQueryInNullValuesThrowsException() {
        QuerySpec<ParentEntity> qs = new QuerySpec<>();
        qs.exists(TestEntity.class, sub -> sub.in(TestEntity::getStatus, (Object[])null));
        RuntimeException ex =
            assertThrows(RuntimeException.class, () -> parentRepository.findAll(qs.toSpecification()));
        assertTrue(ex.getCause() instanceof IllegalArgumentException || ex instanceof IllegalArgumentException);
    }

    @Test
    void testSubQueryNotInNullValuesThrowsException() {
        QuerySpec<ParentEntity> qs = new QuerySpec<>();
        qs.exists(TestEntity.class, sub -> sub.notIn(TestEntity::getStatus, (Object[])null));
        RuntimeException ex =
            assertThrows(RuntimeException.class, () -> parentRepository.findAll(qs.toSpecification()));
        assertTrue(ex.getCause() instanceof IllegalArgumentException || ex instanceof IllegalArgumentException);
    }

    @Test
    void testSubQueryInEmptyArrayThrowsException() {
        QuerySpec<ParentEntity> qs = new QuerySpec<>();
        qs.exists(TestEntity.class, sub -> sub.in(TestEntity::getStatus, new Object[0]));
        RuntimeException ex =
            assertThrows(RuntimeException.class, () -> parentRepository.findAll(qs.toSpecification()));
        assertTrue(ex.getCause() instanceof IllegalArgumentException || ex instanceof IllegalArgumentException);
    }

    @Test
    void testSubQueryNotInEmptyArrayThrowsException() {
        QuerySpec<ParentEntity> qs = new QuerySpec<>();
        qs.exists(TestEntity.class, sub -> sub.notIn(TestEntity::getStatus, new Object[0]));
        RuntimeException ex =
            assertThrows(RuntimeException.class, () -> parentRepository.findAll(qs.toSpecification()));
        assertTrue(ex.getCause() instanceof IllegalArgumentException || ex instanceof IllegalArgumentException);
    }

    @Test
    void testSubQueryInEmptyCollectionThrowsException() {
        QuerySpec<ParentEntity> qs = new QuerySpec<>();
        qs.exists(TestEntity.class, sub -> sub.in(TestEntity::getStatus, java.util.Collections.emptyList()));
        RuntimeException ex =
            assertThrows(RuntimeException.class, () -> parentRepository.findAll(qs.toSpecification()));
        assertTrue(ex.getCause() instanceof IllegalArgumentException || ex instanceof IllegalArgumentException);
    }

    @Test
    void testSubQueryNotInEmptyCollectionThrowsException() {
        QuerySpec<ParentEntity> qs = new QuerySpec<>();
        qs.exists(TestEntity.class, sub -> sub.notIn(TestEntity::getStatus, java.util.Collections.emptyList()));
        RuntimeException ex =
            assertThrows(RuntimeException.class, () -> parentRepository.findAll(qs.toSpecification()));
        assertTrue(ex.getCause() instanceof IllegalArgumentException || ex instanceof IllegalArgumentException);
    }

    @Test
    void testSubQueryBetweenNullEndThrowsException() {
        QuerySpec<ParentEntity> qs = new QuerySpec<>();
        qs.exists(TestEntity.class, sub -> sub.between(TestEntity::getStatus, 1, null));
        RuntimeException ex =
            assertThrows(RuntimeException.class, () -> parentRepository.findAll(qs.toSpecification()));
        assertTrue(ex.getCause() instanceof IllegalArgumentException || ex instanceof IllegalArgumentException);
    }

    @Test
    void testSubQueryNotBetweenNullEndThrowsException() {
        QuerySpec<ParentEntity> qs = new QuerySpec<>();
        qs.exists(TestEntity.class, sub -> sub.notBetween(TestEntity::getStatus, 1, null));
        RuntimeException ex =
            assertThrows(RuntimeException.class, () -> parentRepository.findAll(qs.toSpecification()));
        assertTrue(ex.getCause() instanceof IllegalArgumentException || ex instanceof IllegalArgumentException);
    }

    @Test
    void testSubQueryNotLikeNullValueThrowsException() {
        QuerySpec<ParentEntity> qs = new QuerySpec<>();
        qs.exists(TestEntity.class, sub -> sub.notLike(TestEntity::getName, null));
        assertThrows(Exception.class, () -> parentRepository.findAll(qs.toSpecification()));
    }

    @Test
    void testSubQueryStartsWithNullValueThrowsException() {
        QuerySpec<ParentEntity> qs = new QuerySpec<>();
        qs.exists(TestEntity.class, sub -> sub.startsWith(TestEntity::getName, null));
        RuntimeException ex =
            assertThrows(RuntimeException.class, () -> parentRepository.findAll(qs.toSpecification()));
        assertTrue(ex.getCause() instanceof IllegalArgumentException || ex instanceof IllegalArgumentException);
    }

    @Test
    void testSubQueryEndsWithNullValueThrowsException() {
        QuerySpec<ParentEntity> qs = new QuerySpec<>();
        qs.exists(TestEntity.class, sub -> sub.endsWith(TestEntity::getName, null));
        RuntimeException ex =
            assertThrows(RuntimeException.class, () -> parentRepository.findAll(qs.toSpecification()));
        assertTrue(ex.getCause() instanceof IllegalArgumentException || ex instanceof IllegalArgumentException);
    }

    @Test
    void testSubQueryGtNullValueThrowsException() {
        QuerySpec<ParentEntity> qs = new QuerySpec<>();
        qs.exists(TestEntity.class, sub -> sub.gt(TestEntity::getStatus, null));
        RuntimeException ex =
            assertThrows(RuntimeException.class, () -> parentRepository.findAll(qs.toSpecification()));
        assertTrue(ex.getCause() instanceof IllegalArgumentException || ex instanceof IllegalArgumentException);
    }

    @Test
    void testSubQueryGeNullValueThrowsException() {
        QuerySpec<ParentEntity> qs = new QuerySpec<>();
        qs.exists(TestEntity.class, sub -> sub.ge(TestEntity::getStatus, null));
        RuntimeException ex =
            assertThrows(RuntimeException.class, () -> parentRepository.findAll(qs.toSpecification()));
        assertTrue(ex.getCause() instanceof IllegalArgumentException || ex instanceof IllegalArgumentException);
    }

    @Test
    void testSubQueryLtNullValueThrowsException() {
        QuerySpec<ParentEntity> qs = new QuerySpec<>();
        qs.exists(TestEntity.class, sub -> sub.lt(TestEntity::getStatus, null));
        RuntimeException ex =
            assertThrows(RuntimeException.class, () -> parentRepository.findAll(qs.toSpecification()));
        assertTrue(ex.getCause() instanceof IllegalArgumentException || ex instanceof IllegalArgumentException);
    }

    @Test
    void testSubQueryNullFieldThrowsException() {
        QuerySpec<ParentEntity> qs = new QuerySpec<>();
        qs.exists(TestEntity.class, sub -> sub.eq(null, "value"));
        RuntimeException ex =
            assertThrows(RuntimeException.class, () -> parentRepository.findAll(qs.toSpecification()));
        assertTrue(ex.getCause() instanceof IllegalArgumentException || ex instanceof IllegalArgumentException);
    }

    @Test
    void testSubQueryMultiLikeWithNullFieldThrowsException() {
        QuerySpec<ParentEntity> qs = new QuerySpec<>();
        qs.exists(TestEntity.class, sub -> sub.multiLike("test", (SFunction<TestEntity, ?>)null));
        RuntimeException ex =
            assertThrows(RuntimeException.class, () -> parentRepository.findAll(qs.toSpecification()));
        assertTrue(ex.getCause() instanceof IllegalArgumentException || ex instanceof IllegalArgumentException);
    }

    @Test
    void testExistsWithInCollectionVarargs() {
        ParentEntity p = new ParentEntity();
        p.setCategory("cat");
        p.setLevel(10);
        em.persist(p);
        TestEntity child = newEntity("c1", 2);
        child.setParent(p);
        repository.save(child);

        QuerySpec<ParentEntity> qs = new QuerySpec<>();
        qs.exists(TestEntity.class, sub -> sub.in(TestEntity::getStatus, 1, 2, 3));
        List<ParentEntity> result = parentRepository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
    }

    @Test
    void testExistsWithNotInCollectionVarargs() {
        ParentEntity p = new ParentEntity();
        p.setCategory("cat");
        p.setLevel(10);
        em.persist(p);
        TestEntity child = newEntity("c1", 2);
        child.setParent(p);
        repository.save(child);

        QuerySpec<ParentEntity> qs = new QuerySpec<>();
        qs.exists(TestEntity.class, sub -> sub.notIn(TestEntity::getStatus, 99, 100));
        List<ParentEntity> result = parentRepository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
    }

    @Test
    void testSubQueryBetweenTypeMismatchThrowsException() {
        // Integer and String are incompatible types
        assertThrows(IllegalArgumentException.class, () -> PredicateHelper.validateRange(1, "abc"));
    }

    @Test
    void testSubQueryNotBetweenTypeMismatchThrowsException() {
        // Integer and String are incompatible types
        assertThrows(IllegalArgumentException.class, () -> PredicateHelper.validateRange(1, "abc"));
    }

    @Test
    void testSubQueryEqIgnoreCaseNullValueBecomesIsNull() {
        ParentEntity p = new ParentEntity();
        p.setCategory("cat");
        p.setLevel(10);
        em.persist(p);
        TestEntity child = new TestEntity();
        child.setName(null);
        child.setStatus(0);
        child.setParent(p);
        repository.save(child);

        QuerySpec<ParentEntity> qs = new QuerySpec<>();
        qs.exists(TestEntity.class, sub -> sub.eqIgnoreCase(TestEntity::getName, null));
        List<ParentEntity> result = parentRepository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
    }

    private TestEntity newEntity(String name, int status) {
        TestEntity entity = new TestEntity();
        entity.setName(name);
        entity.setStatus(status);
        return entity;
    }

    // ===== or()/not() 业务逻辑路径测试 =====

    @Test
    void testExistsOrConditionGroupMultiItem() {
        ParentEntity p = new ParentEntity();
        p.setCategory("cat");
        p.setLevel(10);
        em.persist(p);
        TestEntity c1 = newEntity("c1", 1);
        c1.setParent(p);
        repository.save(c1);
        TestEntity c2 = newEntity("c2", 2);
        c2.setParent(p);
        repository.save(c2);
        repository.flush();

        QuerySpec<ParentEntity> qs = new QuerySpec<>();
        qs.exists(TestEntity.class, sub -> sub.or(o -> o.eq(TestEntity::getStatus, 1).eq(TestEntity::getStatus, 2)));
        List<ParentEntity> result = parentRepository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
    }

    @Test
    void testExistsNotConditionGroupMultiItem() {
        ParentEntity p = new ParentEntity();
        p.setCategory("cat");
        p.setLevel(10);
        em.persist(p);
        TestEntity c1 = newEntity("c1", 1);
        c1.setParent(p);
        repository.save(c1);
        TestEntity c2 = newEntity("c2", 2);
        c2.setParent(p);
        repository.save(c2);
        repository.flush();

        QuerySpec<ParentEntity> qs = new QuerySpec<>();
        qs.exists(TestEntity.class, sub -> sub.not(n -> n.eq(TestEntity::getStatus, 1).eq(TestEntity::getStatus, 2)));
        // NOT(status=1 AND status=2) matches records where NOT both are true
        List<ParentEntity> result = parentRepository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
    }

    @Test
    void testExistsOrConditionSingleItem() {
        ParentEntity p = new ParentEntity();
        p.setCategory("cat");
        p.setLevel(10);
        em.persist(p);
        TestEntity c1 = newEntity("c1", 1);
        c1.setParent(p);
        repository.save(c1);
        repository.flush();

        QuerySpec<ParentEntity> qs = new QuerySpec<>();
        qs.exists(TestEntity.class, sub -> sub.or(o -> o.eq(TestEntity::getStatus, 1)));
        List<ParentEntity> result = parentRepository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
    }

    @Test
    void testExistsNotConditionSingleItem() {
        ParentEntity p = new ParentEntity();
        p.setCategory("cat");
        p.setLevel(10);
        em.persist(p);
        TestEntity c1 = newEntity("c1", 1);
        c1.setParent(p);
        repository.save(c1);
        repository.flush();

        QuerySpec<ParentEntity> qs = new QuerySpec<>();
        qs.exists(TestEntity.class, sub -> sub.not(n -> n.eq(TestEntity::getStatus, 99)));
        List<ParentEntity> result = parentRepository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
    }

    // ===== multiLike SFunction 版本测试 =====

    @Test
    void testExistsMultiLikeSFunction() {
        ParentEntity p = new ParentEntity();
        p.setCategory("cat");
        p.setLevel(10);
        em.persist(p);
        TestEntity c1 = newEntity("hello", 0);
        c1.setParent(p);
        repository.save(c1);
        TestEntity c2 = newEntity("world", 0);
        c2.setParent(p);
        repository.save(c2);
        repository.flush();

        QuerySpec<ParentEntity> qs = new QuerySpec<>();
        qs.exists(TestEntity.class, sub -> sub.multiLike("hello", TestEntity::getName));
        List<ParentEntity> result = parentRepository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
    }

    @Test
    void testExistsMultiLikeSFunctionMultipleFields() {
        ParentEntity p = new ParentEntity();
        p.setCategory("cat");
        p.setLevel(10);
        em.persist(p);
        TestEntity c1 = newEntity("hello", 0);
        c1.setParent(p);
        repository.save(c1);
        TestEntity c2 = newEntity("world", 0);
        c2.setParent(p);
        repository.save(c2);
        repository.flush();

        QuerySpec<ParentEntity> qs = new QuerySpec<>();
        qs.exists(TestEntity.class, sub -> sub.multiLike("hello", TestEntity::getName, TestEntity::getName));
        List<ParentEntity> result = parentRepository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
    }

    @Test
    void testExistsMultiLikeStringFieldNames() {
        ParentEntity p = new ParentEntity();
        p.setCategory("cat");
        p.setLevel(10);
        em.persist(p);
        TestEntity c1 = newEntity("hello", 0);
        c1.setParent(p);
        repository.save(c1);
        repository.flush();

        QuerySpec<ParentEntity> qs = new QuerySpec<>();
        qs.exists(TestEntity.class, sub -> sub.multiLike("hello", "name"));
        List<ParentEntity> result = parentRepository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
    }

    // ===== correlatedEq 测试 =====

    @Test
    void testExistsCorrelatedEq() {
        ParentEntity p = new ParentEntity();
        p.setCategory("cat");
        p.setLevel(10);
        em.persist(p);
        TestEntity c1 = newEntity("c1", 10);
        c1.setParent(p);
        repository.save(c1);
        repository.flush();

        QuerySpec<ParentEntity> qs = new QuerySpec<>();
        qs.exists(TestEntity.class, sub -> sub.correlatedEq(ParentEntity::getLevel, TestEntity::getStatus));
        List<ParentEntity> result = parentRepository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
    }

    // ===== create() null 检查 =====

    @Test
    void testCreateNullSubquery() {
        assertThrows(IllegalArgumentException.class, () -> SubQuerySpec.create(null, null, null, null));
    }

    // ===== 条件方法 null 检查测试（延迟执行，异常在 toSpecification/findAll 时抛出） =====

    @Test
    void testCorrelatedEqNullOuterField() {
        QuerySpec<ParentEntity> qs = new QuerySpec<>();
        qs.exists(TestEntity.class, sub -> sub.correlatedEq(null, TestEntity::getStatus));
        RuntimeException ex =
            assertThrows(RuntimeException.class, () -> parentRepository.findAll(qs.toSpecification()));
        assertTrue(ex.getCause() instanceof IllegalArgumentException || ex instanceof IllegalArgumentException);
    }

    @Test
    void testCorrelatedEqNullSubField() {
        QuerySpec<ParentEntity> qs = new QuerySpec<>();
        qs.exists(TestEntity.class, sub -> sub.correlatedEq(ParentEntity::getLevel, null));
        RuntimeException ex =
            assertThrows(RuntimeException.class, () -> parentRepository.findAll(qs.toSpecification()));
        assertTrue(ex.getCause() instanceof IllegalArgumentException || ex instanceof IllegalArgumentException);
    }

    @Test
    void testEqStrictNullField() {
        QuerySpec<ParentEntity> qs = new QuerySpec<>();
        qs.exists(TestEntity.class, sub -> sub.eqStrict(null, "val"));
        RuntimeException ex =
            assertThrows(RuntimeException.class, () -> parentRepository.findAll(qs.toSpecification()));
        assertTrue(ex.getCause() instanceof IllegalArgumentException || ex instanceof IllegalArgumentException);
    }

    @Test
    void testEqStrictNullValue() {
        QuerySpec<ParentEntity> qs = new QuerySpec<>();
        qs.exists(TestEntity.class, sub -> sub.eqStrict(TestEntity::getName, null));
        RuntimeException ex =
            assertThrows(RuntimeException.class, () -> parentRepository.findAll(qs.toSpecification()));
        assertTrue(ex.getCause() instanceof IllegalArgumentException || ex instanceof IllegalArgumentException);
    }

    @Test
    void testNeStrictNullField() {
        QuerySpec<ParentEntity> qs = new QuerySpec<>();
        qs.exists(TestEntity.class, sub -> sub.neStrict(null, "val"));
        RuntimeException ex =
            assertThrows(RuntimeException.class, () -> parentRepository.findAll(qs.toSpecification()));
        assertTrue(ex.getCause() instanceof IllegalArgumentException || ex instanceof IllegalArgumentException);
    }

    @Test
    void testNeStrictNullValue() {
        QuerySpec<ParentEntity> qs = new QuerySpec<>();
        qs.exists(TestEntity.class, sub -> sub.neStrict(TestEntity::getName, null));
        RuntimeException ex =
            assertThrows(RuntimeException.class, () -> parentRepository.findAll(qs.toSpecification()));
        assertTrue(ex.getCause() instanceof IllegalArgumentException || ex instanceof IllegalArgumentException);
    }

    @Test
    void testNeNullField() {
        QuerySpec<ParentEntity> qs = new QuerySpec<>();
        qs.exists(TestEntity.class, sub -> sub.ne(null, "val"));
        RuntimeException ex =
            assertThrows(RuntimeException.class, () -> parentRepository.findAll(qs.toSpecification()));
        assertTrue(ex.getCause() instanceof IllegalArgumentException || ex instanceof IllegalArgumentException);
    }

    @Test
    void testGtNullField() {
        QuerySpec<ParentEntity> qs = new QuerySpec<>();
        qs.exists(TestEntity.class, sub -> sub.gt(null, 1));
        RuntimeException ex =
            assertThrows(RuntimeException.class, () -> parentRepository.findAll(qs.toSpecification()));
        assertTrue(ex.getCause() instanceof IllegalArgumentException || ex instanceof IllegalArgumentException);
    }

    @Test
    void testGtNullValue() {
        QuerySpec<ParentEntity> qs = new QuerySpec<>();
        qs.exists(TestEntity.class, sub -> sub.gt(TestEntity::getStatus, null));
        RuntimeException ex =
            assertThrows(RuntimeException.class, () -> parentRepository.findAll(qs.toSpecification()));
        assertTrue(ex.getCause() instanceof IllegalArgumentException || ex instanceof IllegalArgumentException);
    }

    @Test
    void testGeNullField() {
        QuerySpec<ParentEntity> qs = new QuerySpec<>();
        qs.exists(TestEntity.class, sub -> sub.ge(null, 1));
        RuntimeException ex =
            assertThrows(RuntimeException.class, () -> parentRepository.findAll(qs.toSpecification()));
        assertTrue(ex.getCause() instanceof IllegalArgumentException || ex instanceof IllegalArgumentException);
    }

    @Test
    void testGeNullValue() {
        QuerySpec<ParentEntity> qs = new QuerySpec<>();
        qs.exists(TestEntity.class, sub -> sub.ge(TestEntity::getStatus, null));
        RuntimeException ex =
            assertThrows(RuntimeException.class, () -> parentRepository.findAll(qs.toSpecification()));
        assertTrue(ex.getCause() instanceof IllegalArgumentException || ex instanceof IllegalArgumentException);
    }

    @Test
    void testLtNullField() {
        QuerySpec<ParentEntity> qs = new QuerySpec<>();
        qs.exists(TestEntity.class, sub -> sub.lt(null, 1));
        RuntimeException ex =
            assertThrows(RuntimeException.class, () -> parentRepository.findAll(qs.toSpecification()));
        assertTrue(ex.getCause() instanceof IllegalArgumentException || ex instanceof IllegalArgumentException);
    }

    @Test
    void testLtNullValue() {
        QuerySpec<ParentEntity> qs = new QuerySpec<>();
        qs.exists(TestEntity.class, sub -> sub.lt(TestEntity::getStatus, null));
        RuntimeException ex =
            assertThrows(RuntimeException.class, () -> parentRepository.findAll(qs.toSpecification()));
        assertTrue(ex.getCause() instanceof IllegalArgumentException || ex instanceof IllegalArgumentException);
    }

    @Test
    void testLeNullField() {
        QuerySpec<ParentEntity> qs = new QuerySpec<>();
        qs.exists(TestEntity.class, sub -> sub.le(null, 1));
        RuntimeException ex =
            assertThrows(RuntimeException.class, () -> parentRepository.findAll(qs.toSpecification()));
        assertTrue(ex.getCause() instanceof IllegalArgumentException || ex instanceof IllegalArgumentException);
    }

    @Test
    void testLeNullValue() {
        QuerySpec<ParentEntity> qs = new QuerySpec<>();
        qs.exists(TestEntity.class, sub -> sub.le(TestEntity::getStatus, null));
        RuntimeException ex =
            assertThrows(RuntimeException.class, () -> parentRepository.findAll(qs.toSpecification()));
        assertTrue(ex.getCause() instanceof IllegalArgumentException || ex instanceof IllegalArgumentException);
    }

    @Test
    void testLikeNullField() {
        QuerySpec<ParentEntity> qs = new QuerySpec<>();
        qs.exists(TestEntity.class, sub -> sub.like(null, "val"));
        RuntimeException ex =
            assertThrows(RuntimeException.class, () -> parentRepository.findAll(qs.toSpecification()));
        assertTrue(ex.getCause() instanceof IllegalArgumentException || ex instanceof IllegalArgumentException);
    }

    @Test
    void testLikeNullValue() {
        QuerySpec<ParentEntity> qs = new QuerySpec<>();
        qs.exists(TestEntity.class, sub -> sub.like(TestEntity::getName, null));
        RuntimeException ex =
            assertThrows(RuntimeException.class, () -> parentRepository.findAll(qs.toSpecification()));
        assertTrue(ex.getCause() instanceof IllegalArgumentException || ex instanceof IllegalArgumentException);
    }

    @Test
    void testNotLikeNullField() {
        QuerySpec<ParentEntity> qs = new QuerySpec<>();
        qs.exists(TestEntity.class, sub -> sub.notLike(null, "val"));
        RuntimeException ex =
            assertThrows(RuntimeException.class, () -> parentRepository.findAll(qs.toSpecification()));
        assertTrue(ex.getCause() instanceof IllegalArgumentException || ex instanceof IllegalArgumentException);
    }

    @Test
    void testNotLikeNullValue() {
        QuerySpec<ParentEntity> qs = new QuerySpec<>();
        qs.exists(TestEntity.class, sub -> sub.notLike(TestEntity::getName, null));
        RuntimeException ex =
            assertThrows(RuntimeException.class, () -> parentRepository.findAll(qs.toSpecification()));
        assertTrue(ex.getCause() instanceof IllegalArgumentException || ex instanceof IllegalArgumentException);
    }

    @Test
    void testBetweenNullField() {
        QuerySpec<ParentEntity> qs = new QuerySpec<>();
        qs.exists(TestEntity.class, sub -> sub.between(null, 1, 10));
        RuntimeException ex =
            assertThrows(RuntimeException.class, () -> parentRepository.findAll(qs.toSpecification()));
        assertTrue(ex.getCause() instanceof IllegalArgumentException || ex instanceof IllegalArgumentException);
    }

    @Test
    void testInNullField() {
        QuerySpec<ParentEntity> qs = new QuerySpec<>();
        qs.exists(TestEntity.class, sub -> sub.in(null, List.of(1, 2)));
        RuntimeException ex =
            assertThrows(RuntimeException.class, () -> parentRepository.findAll(qs.toSpecification()));
        assertTrue(ex.getCause() instanceof IllegalArgumentException || ex instanceof IllegalArgumentException);
    }

    @Test
    void testIsNullNullField() {
        QuerySpec<ParentEntity> qs = new QuerySpec<>();
        qs.exists(TestEntity.class, sub -> sub.isNull(null));
        RuntimeException ex =
            assertThrows(RuntimeException.class, () -> parentRepository.findAll(qs.toSpecification()));
        assertTrue(ex.getCause() instanceof IllegalArgumentException || ex instanceof IllegalArgumentException);
    }

    @Test
    void testIsNotNullNullField() {
        QuerySpec<ParentEntity> qs = new QuerySpec<>();
        qs.exists(TestEntity.class, sub -> sub.isNotNull(null));
        RuntimeException ex =
            assertThrows(RuntimeException.class, () -> parentRepository.findAll(qs.toSpecification()));
        assertTrue(ex.getCause() instanceof IllegalArgumentException || ex instanceof IllegalArgumentException);
    }

    @Test
    void testEqIgnoreCaseNullField() {
        QuerySpec<ParentEntity> qs = new QuerySpec<>();
        qs.exists(TestEntity.class, sub -> sub.eqIgnoreCase(null, "val"));
        RuntimeException ex =
            assertThrows(RuntimeException.class, () -> parentRepository.findAll(qs.toSpecification()));
        assertTrue(ex.getCause() instanceof IllegalArgumentException || ex instanceof IllegalArgumentException);
    }

    @Test
    void testNeIgnoreCaseNullField() {
        QuerySpec<ParentEntity> qs = new QuerySpec<>();
        qs.exists(TestEntity.class, sub -> sub.neIgnoreCase(null, "val"));
        RuntimeException ex =
            assertThrows(RuntimeException.class, () -> parentRepository.findAll(qs.toSpecification()));
        assertTrue(ex.getCause() instanceof IllegalArgumentException || ex instanceof IllegalArgumentException);
    }

    @Test
    void testLikeIgnoreCaseNullField() {
        QuerySpec<ParentEntity> qs = new QuerySpec<>();
        qs.exists(TestEntity.class, sub -> sub.likeIgnoreCase(null, "val"));
        RuntimeException ex =
            assertThrows(RuntimeException.class, () -> parentRepository.findAll(qs.toSpecification()));
        assertTrue(ex.getCause() instanceof IllegalArgumentException || ex instanceof IllegalArgumentException);
    }

    @Test
    void testLikeIgnoreCaseNullValue() {
        QuerySpec<ParentEntity> qs = new QuerySpec<>();
        qs.exists(TestEntity.class, sub -> sub.likeIgnoreCase(TestEntity::getName, null));
        RuntimeException ex =
            assertThrows(RuntimeException.class, () -> parentRepository.findAll(qs.toSpecification()));
        assertTrue(ex.getCause() instanceof IllegalArgumentException || ex instanceof IllegalArgumentException);
    }

    @Test
    void testIsEmptyNullField() {
        QuerySpec<ParentEntity> qs = new QuerySpec<>();
        qs.exists(TestEntity.class, sub -> sub.isEmpty(null));
        RuntimeException ex =
            assertThrows(RuntimeException.class, () -> parentRepository.findAll(qs.toSpecification()));
        assertTrue(ex.getCause() instanceof IllegalArgumentException || ex instanceof IllegalArgumentException);
    }

    @Test
    void testIsNotEmptyNullField() {
        QuerySpec<ParentEntity> qs = new QuerySpec<>();
        qs.exists(TestEntity.class, sub -> sub.isNotEmpty(null));
        RuntimeException ex =
            assertThrows(RuntimeException.class, () -> parentRepository.findAll(qs.toSpecification()));
        assertTrue(ex.getCause() instanceof IllegalArgumentException || ex instanceof IllegalArgumentException);
    }

    @Test
    void testMultiLikeNullKeyword() {
        QuerySpec<ParentEntity> qs = new QuerySpec<>();
        qs.exists(TestEntity.class, sub -> sub.multiLike(null, TestEntity::getName));
        RuntimeException ex =
            assertThrows(RuntimeException.class, () -> parentRepository.findAll(qs.toSpecification()));
        assertTrue(ex.getCause() instanceof IllegalArgumentException || ex instanceof IllegalArgumentException);
    }

    @Test
    void testMultiLikeNullFields() {
        QuerySpec<ParentEntity> qs = new QuerySpec<>();
        qs.exists(TestEntity.class, sub -> sub.multiLike("val", (SFunction<TestEntity, ?>[])null));
        RuntimeException ex =
            assertThrows(RuntimeException.class, () -> parentRepository.findAll(qs.toSpecification()));
        assertTrue(ex.getCause() instanceof IllegalArgumentException || ex instanceof IllegalArgumentException);
    }

    // ===== multiLike(String...) 重载 null 检查 =====

    @Test
    void testMultiLikeStringOverloadNullFieldNames() {
        QuerySpec<ParentEntity> qs = new QuerySpec<>();
        qs.exists(TestEntity.class, sub -> sub.multiLike("val", (String[])null));
        RuntimeException ex =
            assertThrows(RuntimeException.class, () -> parentRepository.findAll(qs.toSpecification()));
        assertTrue(ex.getCause() instanceof IllegalArgumentException || ex instanceof IllegalArgumentException);
    }

    @Test
    void testMultiLikeStringOverloadEmptyArray() {
        QuerySpec<ParentEntity> qs = new QuerySpec<>();
        qs.exists(TestEntity.class, sub -> sub.multiLike("val", new String[0]));
        RuntimeException ex =
            assertThrows(RuntimeException.class, () -> parentRepository.findAll(qs.toSpecification()));
        assertTrue(ex.getCause() instanceof IllegalArgumentException || ex instanceof IllegalArgumentException);
    }

    // ===== or(null) / not(null) null 检查 =====

    @Test
    void testOrNullConfigThrows() {
        QuerySpec<ParentEntity> qs = new QuerySpec<>();
        qs.exists(TestEntity.class, sub -> sub.or(null));
        RuntimeException ex =
            assertThrows(RuntimeException.class, () -> parentRepository.findAll(qs.toSpecification()));
        assertTrue(ex.getCause() instanceof IllegalArgumentException || ex instanceof IllegalArgumentException);
    }

    @Test
    void testNotNullConfigThrows() {
        QuerySpec<ParentEntity> qs = new QuerySpec<>();
        qs.exists(TestEntity.class, sub -> sub.not(null));
        RuntimeException ex =
            assertThrows(RuntimeException.class, () -> parentRepository.findAll(qs.toSpecification()));
        assertTrue(ex.getCause() instanceof IllegalArgumentException || ex instanceof IllegalArgumentException);
    }

    // ===== select() null 检查 =====

    @Test
    void testSelectNullFieldThrows() {
        QuerySpec<ParentEntity> qs = new QuerySpec<>();
        qs.exists(TestEntity.class, sub -> sub.select(null));
        RuntimeException ex =
            assertThrows(RuntimeException.class, () -> parentRepository.findAll(qs.toSpecification()));
        assertTrue(ex.getCause() instanceof IllegalArgumentException || ex instanceof IllegalArgumentException);
    }

    // ===== conditional boolean false 路径 =====

    @Test
    void testConditionalEqFalseSkips() {
        ParentEntity p = new ParentEntity();
        p.setCategory("cat");
        p.setLevel(10);
        em.persist(p);
        repository.save(newEntity("c1", 5));

        QuerySpec<ParentEntity> qs = new QuerySpec<>();
        qs.exists(TestEntity.class, sub -> sub.eq(false, TestEntity::getStatus, 5));
        List<ParentEntity> result = parentRepository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
    }

    @Test
    void testConditionalNeFalseSkips() {
        ParentEntity p = new ParentEntity();
        p.setCategory("cat");
        p.setLevel(10);
        em.persist(p);
        repository.save(newEntity("c1", 5));

        QuerySpec<ParentEntity> qs = new QuerySpec<>();
        qs.exists(TestEntity.class, sub -> sub.ne(false, TestEntity::getStatus, 99));
        List<ParentEntity> result = parentRepository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
    }

    @Test
    void testConditionalGtFalseSkips() {
        ParentEntity p = new ParentEntity();
        p.setCategory("cat");
        p.setLevel(10);
        em.persist(p);
        repository.save(newEntity("c1", 5));

        QuerySpec<ParentEntity> qs = new QuerySpec<>();
        qs.exists(TestEntity.class, sub -> sub.gt(false, TestEntity::getStatus, 1));
        List<ParentEntity> result = parentRepository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
    }

}
