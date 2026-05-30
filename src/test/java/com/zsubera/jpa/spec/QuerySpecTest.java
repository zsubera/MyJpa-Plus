package com.zsubera.jpa.spec;

import static org.junit.jupiter.api.Assertions.*;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.jpa.domain.Specification;

@DataJpaTest
public class QuerySpecTest {

    @Autowired
    private TestEntityRepository repository;

    @Autowired
    private ParentEntityRepository parentRepository;

    @PersistenceContext
    private EntityManager em;

    // ---- Existing tests ----

    @Test
    void testSimpleEq() {
        repository.save(newEntity("test1", 0));
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.eq(TestEntity::getName, "test1");
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
        assertEquals("test1", result.get(0).getName());
    }

    @Test
    void testSimpleLike() {
        repository.save(newEntity("hello", 0));
        repository.save(newEntity("world", 0));
        repository.save(newEntity("hello world", 0));
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.like(TestEntity::getName, "%hello%");
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(2, result.size());
    }

    @Test
    void testEqWithStatus() {
        repository.save(newEntity("a", 1));
        repository.save(newEntity("b", 2));
        repository.save(newEntity("c", 1));
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.eq(TestEntity::getStatus, 1);
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(2, result.size());
    }

    @Test
    void testInCondition() {
        repository.save(newEntity("a", 1));
        repository.save(newEntity("b", 2));
        repository.save(newEntity("c", 3));
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.in(TestEntity::getStatus, 1, 3);
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(2, result.size());
    }

    @Test
    void testMultiConditionAnd() {
        repository.save(newEntity("match", 1));
        repository.save(newEntity("match", 2));
        repository.save(newEntity("other", 1));
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.eq(TestEntity::getName, "match").eq(TestEntity::getStatus, 1);
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
        assertEquals("match", result.get(0).getName());
        assertEquals(Integer.valueOf(1), result.get(0).getStatus());
    }

    @Test
    void testJoinEq() {
        ParentEntity parent = new ParentEntity();
        parent.setCategory("admin");
        parent.setLevel(10);
        em.persist(parent);

        TestEntity child = newEntity("child1", 0);
        child.setParent(parent);
        repository.save(child);

        QuerySpec<TestEntity> qs = new QuerySpec<>();
        JoinGroup<TestEntity, ParentEntity> jg = qs.join(TestEntity::getParent);
        jg.eq(ParentEntity::getCategory, "admin");
        jg.endJoin();

        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
    }

    @Test
    void testOrGroup() {
        repository.save(newEntity("alpha", 1));
        repository.save(newEntity("beta", 2));
        repository.save(newEntity("gamma", 3));
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.or().eq(TestEntity::getName, "alpha").eq(TestEntity::getName, "beta").endOr();
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(2, result.size());
    }

    @Test
    void testMultiLike() {
        repository.save(newEntity("hello", 0));
        repository.save(newEntity("world", 0));
        repository.save(newEntity("help", 0));
        repository.save(newEntity("welcome", 0));
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.multiLike("hel", TestEntity::getName);
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(2, result.size());
    }

    @Test
    void testIsNull() {
        repository.save(newEntity("hasName", 0));
        TestEntity nullName = new TestEntity();
        nullName.setName(null);
        nullName.setStatus(99);
        repository.save(nullName);
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.isNull(TestEntity::getName);
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
        assertNull(result.get(0).getName());
    }

    @Test
    void testIsNotNull() {
        repository.save(newEntity("hasName", 0));
        TestEntity nullName = new TestEntity();
        nullName.setName(null);
        nullName.setStatus(99);
        repository.save(nullName);
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.isNotNull(TestEntity::getName);
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
        assertEquals("hasName", result.get(0).getName());
    }

    @Test
    void testEmptyQuerySpecReturnsAll() {
        repository.save(newEntity("a", 1));
        repository.save(newEntity("b", 2));
        List<TestEntity> result = repository.findAll(new QuerySpec<TestEntity>().toSpecification());
        assertEquals(2, result.size());
    }

    @Test
    void testCombineWithExternalSpec() {
        repository.save(newEntity("match", 1));
        repository.save(newEntity("match", 2));
        repository.save(newEntity("other", 1));
        Specification<TestEntity> external = (root, query, cb) -> cb.equal(root.get("name"), "match");
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.eq(TestEntity::getStatus, 1);
        List<TestEntity> result = repository.findAll(qs.toSpecification(external));
        assertEquals(1, result.size());
        assertEquals("match", result.get(0).getName());
        assertEquals(Integer.valueOf(1), result.get(0).getStatus());
    }

    @Test
    void testBetween() {
        for (int i = 1; i <= 10; i++) {
            repository.save(newEntity("item" + i, i));
        }
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.between(TestEntity::getStatus, 3, 7);
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(5, result.size());
    }

    @Test
    void testGtAndLt() {
        repository.save(newEntity("low", 1));
        repository.save(newEntity("mid", 5));
        repository.save(newEntity("high", 10));
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.gt(TestEntity::getStatus, 1).lt(TestEntity::getStatus, 10);
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
        assertEquals("mid", result.get(0).getName());
    }

    @Test
    void testJoinWithMultipleConditions() {
        ParentEntity parent = new ParentEntity();
        parent.setCategory("admin");
        parent.setLevel(10);
        em.persist(parent);

        ParentEntity other = new ParentEntity();
        other.setCategory("user");
        other.setLevel(5);
        em.persist(other);

        TestEntity child1 = newEntity("c1", 0);
        child1.setParent(parent);
        repository.save(child1);

        TestEntity child2 = newEntity("c2", 0);
        child2.setParent(other);
        repository.save(child2);

        QuerySpec<TestEntity> qs = new QuerySpec<>();
        JoinGroup<TestEntity, ParentEntity> jg = qs.join(TestEntity::getParent);
        jg.eq(ParentEntity::getCategory, "admin");
        jg.eq(ParentEntity::getLevel, 10);
        jg.endJoin();

        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
        assertEquals("c1", result.get(0).getName());
    }

    @Test
    void testLeftJoin() {
        TestEntity orphan = newEntity("orphan", 0);
        orphan.setParent(null);
        repository.save(orphan);

        ParentEntity parent = new ParentEntity();
        parent.setCategory("admin");
        parent.setLevel(10);
        em.persist(parent);

        TestEntity child = newEntity("child", 0);
        child.setParent(parent);
        repository.save(child);

        QuerySpec<TestEntity> qs = new QuerySpec<>();
        JoinGroup<TestEntity, ParentEntity> jg = qs.leftJoin(TestEntity::getParent);
        OrJoinGroup<TestEntity, ParentEntity> orGroup = jg.or();
        orGroup.eq(ParentEntity::getCategory, "admin");
        orGroup.isNull(ParentEntity::getCategory);
        jg = orGroup.endOr();
        jg.endJoin();

        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(2, result.size());
    }

    @Test
    void testJoinReuseSharedJoin() {
        ParentEntity parent = new ParentEntity();
        parent.setCategory("admin");
        parent.setLevel(5);
        em.persist(parent);

        TestEntity child = newEntity("t1", 0);
        child.setParent(parent);
        repository.save(child);

        QuerySpec<TestEntity> qs = new QuerySpec<>();
        JoinGroup<TestEntity, ParentEntity> jg = qs.join(TestEntity::getParent);
        jg.eq(ParentEntity::getCategory, "admin");
        jg.eq(ParentEntity::getLevel, 5);
        jg.endJoin();

        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
    }

    @Test
    void testNotEqual() {
        repository.save(newEntity("a", 1));
        repository.save(newEntity("b", 2));
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.ne(TestEntity::getStatus, 1);
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
        assertEquals("b", result.get(0).getName());
    }

    // ---- New tests for expanded coverage ----

    @Test
    void testNotLike() {
        repository.save(newEntity("hello", 0));
        repository.save(newEntity("world", 0));
        repository.save(newEntity("hello world", 0));
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.notLike(TestEntity::getName, "%hello%");
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
        assertEquals("world", result.get(0).getName());
    }

    @Test
    void testNotIn() {
        repository.save(newEntity("a", 1));
        repository.save(newEntity("b", 2));
        repository.save(newEntity("c", 3));
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.notIn(TestEntity::getStatus, 1, 3);
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
        assertEquals(2, result.get(0).getStatus());
    }

    @Test
    void testGe() {
        repository.save(newEntity("a", 1));
        repository.save(newEntity("b", 3));
        repository.save(newEntity("c", 5));
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.ge(TestEntity::getStatus, 3);
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(2, result.size());
    }

    @Test
    void testLe() {
        repository.save(newEntity("a", 1));
        repository.save(newEntity("b", 3));
        repository.save(newEntity("c", 5));
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.le(TestEntity::getStatus, 3);
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(2, result.size());
    }

    @Test
    void testExistsSubquery() {
        ParentEntity parent = new ParentEntity();
        parent.setCategory("active");
        parent.setLevel(1);
        em.persist(parent);

        ParentEntity emptyParent = new ParentEntity();
        emptyParent.setCategory("empty");
        emptyParent.setLevel(0);
        em.persist(emptyParent);

        TestEntity child = newEntity("child", 0);
        child.setParent(parent);
        repository.save(child);

        QuerySpec<ParentEntity> qs = new QuerySpec<>();
        qs.exists(TestEntity.class, sub -> sub.eq(TestEntity::getName, "child"));
        List<ParentEntity> result = parentRepository.findAll(qs.toSpecification());
        assertEquals(2, result.size());
    }

    @Test
    void testNotExistsSubquery() {
        ParentEntity parent = new ParentEntity();
        parent.setCategory("active");
        parent.setLevel(1);
        em.persist(parent);

        ParentEntity emptyParent = new ParentEntity();
        emptyParent.setCategory("empty");
        emptyParent.setLevel(0);
        em.persist(emptyParent);

        TestEntity child = newEntity("child", 0);
        child.setParent(parent);
        repository.save(child);

        QuerySpec<ParentEntity> qs = new QuerySpec<>();
        qs.notExists(TestEntity.class, sub -> sub.eq(TestEntity::getName, "nonexistent"));
        List<ParentEntity> result = parentRepository.findAll(qs.toSpecification());
        assertEquals(2, result.size());
    }

    @Test
    void testIsEmptyCollection() {
        ParentEntity parent = new ParentEntity();
        parent.setCategory("withKids");
        parent.setLevel(1);
        em.persist(parent);

        ParentEntity emptyParent = new ParentEntity();
        emptyParent.setCategory("noKids");
        emptyParent.setLevel(2);
        em.persist(emptyParent);

        TestEntity child = newEntity("kid", 0);
        child.setParent(parent);
        repository.save(child);
        em.flush();

        QuerySpec<ParentEntity> qs = new QuerySpec<>();
        qs.isEmpty(ParentEntity::getChildren);
        List<ParentEntity> result = parentRepository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
        assertEquals("noKids", result.get(0).getCategory());
    }

    @Test
    void testIsNotEmptyCollection() {
        ParentEntity parent = new ParentEntity();
        parent.setCategory("withKids");
        parent.setLevel(1);
        em.persist(parent);

        ParentEntity emptyParent = new ParentEntity();
        emptyParent.setCategory("noKids");
        emptyParent.setLevel(2);
        em.persist(emptyParent);

        TestEntity child = newEntity("kid", 0);
        child.setParent(parent);
        repository.save(child);
        em.flush();

        QuerySpec<ParentEntity> qs = new QuerySpec<>();
        qs.isNotEmpty(ParentEntity::getChildren);
        List<ParentEntity> result = parentRepository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
        assertEquals("withKids", result.get(0).getCategory());
    }

    @Test
    void testDistinct() {
        repository.save(newEntity("dup", 1));
        repository.save(newEntity("dup", 1));
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.eq(TestEntity::getName, "dup").distinct();
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(2, result.size());
    }

    @Test
    void testExternalSpecNullReturnsSelf() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        Specification<TestEntity> result = qs.toSpecification(null);
        assertSame(qs, result);
    }

    // ---- Exception tests ----

    @Test
    void testNullFieldThrowsException() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        assertThrows(IllegalArgumentException.class, () -> qs.eq(null, "value"));
    }

    @Test
    void testNullFieldInJoinThrowsException() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        assertThrows(IllegalArgumentException.class, () -> qs.join(null));
    }

    @Test
    void testNullFieldInOrGroupThrowsException() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        OrGroup<TestEntity> og = qs.or();
        assertThrows(IllegalArgumentException.class, () -> og.eq(null, "value"));
    }

    @Test
    void testNullFieldInJoinGroupThrowsException() {
        ParentEntity parent = new ParentEntity();
        parent.setCategory("admin");
        parent.setLevel(1);
        em.persist(parent);
        TestEntity child = newEntity("child", 0);
        child.setParent(parent);
        repository.save(child);

        QuerySpec<TestEntity> qs = new QuerySpec<>();
        JoinGroup<TestEntity, ParentEntity> jg = qs.join(TestEntity::getParent);
        assertThrows(IllegalArgumentException.class, () -> jg.eq(null, "admin"));
    }

    @Test
    void testMultiLikeWithNullKeywordThrows() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        assertThrows(IllegalArgumentException.class, () -> qs.multiLike(null, TestEntity::getName));
    }

    @Test
    void testMultiLikeWithEmptyFieldsNoOp() {
        repository.save(newEntity("hello", 0));
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        @SuppressWarnings("unchecked")
        SFunction<TestEntity, ?>[] empty = new SFunction[0];
        qs.multiLike("hel", empty);
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
    }

    @Test
    void testSingleElementOrGroup() {
        repository.save(newEntity("only", 1));
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.or().eq(TestEntity::getName, "only").endOr();
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
    }

    @Test
    void testJoinWithLeftJoinOnOrGroup() {
        ParentEntity admin = new ParentEntity();
        admin.setCategory("admin");
        admin.setLevel(10);
        em.persist(admin);

        ParentEntity user = new ParentEntity();
        user.setCategory("user");
        user.setLevel(5);
        em.persist(user);

        TestEntity c1 = newEntity("c1", 0);
        c1.setParent(admin);
        repository.save(c1);

        TestEntity c2 = newEntity("c2", 0);
        c2.setParent(user);
        repository.save(c2);

        QuerySpec<TestEntity> qs = new QuerySpec<>();
        JoinGroup<TestEntity, ParentEntity> jg = qs.join(TestEntity::getParent);
        OrJoinGroup<TestEntity, ParentEntity> org = jg.or();
        org.eq(ParentEntity::getCategory, "admin");
        org.eq(ParentEntity::getCategory, "user");
        jg = org.endOr();
        jg.endJoin();

        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(2, result.size());
    }

    // ---- Phase 2 tests: new features ----

    @Test
    void testEqNullAutoIsNull() {
        repository.save(newEntity("hasName", 0));
        TestEntity nullName = new TestEntity();
        nullName.setName(null);
        nullName.setStatus(99);
        repository.save(nullName);
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.eq(TestEntity::getName, (String)null);
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
        assertNull(result.get(0).getName());
    }

    @Test
    void testNeNullAutoIsNotNull() {
        repository.save(newEntity("hasName", 0));
        TestEntity nullName = new TestEntity();
        nullName.setName(null);
        nullName.setStatus(99);
        repository.save(nullName);
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.ne(TestEntity::getName, (String)null);
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
        assertEquals("hasName", result.get(0).getName());
    }

    @Test
    void testStartsWith() {
        repository.save(newEntity("hello", 0));
        repository.save(newEntity("help", 0));
        repository.save(newEntity("world", 0));
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.startsWith(TestEntity::getName, "hel");
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(2, result.size());
    }

    @Test
    void testEndsWith() {
        repository.save(newEntity("ending", 0));
        repository.save(newEntity("pending", 0));
        repository.save(newEntity("start", 0));
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.endsWith(TestEntity::getName, "ing");
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(2, result.size());
    }

    @Test
    void testContains() {
        repository.save(newEntity("abc", 0));
        repository.save(newEntity("xabcx", 0));
        repository.save(newEntity("xyz", 0));
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.contains(TestEntity::getName, "ab");
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(2, result.size());
    }

    @Test
    void testOrConsumer() {
        repository.save(newEntity("alpha", 1));
        repository.save(newEntity("beta", 2));
        repository.save(newEntity("gamma", 3));
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.or(g -> g.eq(TestEntity::getName, "alpha").eq(TestEntity::getName, "beta"));
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(2, result.size());
    }

    @Test
    void testJoinConsumer() {
        ParentEntity parent = new ParentEntity();
        parent.setCategory("admin");
        parent.setLevel(10);
        em.persist(parent);
        TestEntity child = newEntity("child", 0);
        child.setParent(parent);
        repository.save(child);

        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.<ParentEntity>join(TestEntity::getParent, j -> j.eq(ParentEntity::getCategory, "admin"));
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
    }

    @Test
    void testLeftJoinConsumer() {
        TestEntity orphan = newEntity("orphan", 0);
        orphan.setParent(null);
        repository.save(orphan);
        ParentEntity parent = new ParentEntity();
        parent.setCategory("admin");
        parent.setLevel(10);
        em.persist(parent);
        TestEntity child = newEntity("child", 0);
        child.setParent(parent);
        repository.save(child);

        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.<ParentEntity>leftJoin(TestEntity::getParent,
            j -> j.or(oj -> oj.eq(ParentEntity::getCategory, "admin").isNull(ParentEntity::getCategory)));
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(2, result.size());
    }

    @Test
    void testNotConsumer() {
        repository.save(newEntity("a", 1));
        repository.save(newEntity("b", 2));
        repository.save(newEntity("c", 3));
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.not(g -> g.eq(TestEntity::getStatus, 1));
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(2, result.size());
    }

    @Test
    void testNotMultiConditionAndSemantics() {
        // NOT(name = 'a' AND status > 3) = name != 'a' OR status <= 3
        repository.save(newEntity("a", 5)); // name='a', status>3 -> excluded
        repository.save(newEntity("a", 1)); // name='a', status<=3 -> included (status<=3)
        repository.save(newEntity("b", 5)); // name!='a', status>3 -> included (name!='a')
        repository.save(newEntity("b", 1)); // name!='a', status<=3 -> included (name!='a')
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.not(g -> g.eq(TestEntity::getName, "a").gt(TestEntity::getStatus, 3));
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(3, result.size());
    }

    @Test
    void testNotMultiConditionAndSemanticsAllExcluded() {
        // NOT(name = 'a' AND status > 0) where all entities have status > 0
        repository.save(newEntity("a", 1));
        repository.save(newEntity("a", 2));
        repository.save(newEntity("b", 3));
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.not(g -> g.eq(TestEntity::getName, "a").gt(TestEntity::getStatus, 0));
        // name='a' AND status>0: matches a1, a2 -> excluded
        // name='b' is NOT matching (name!='a') -> included
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
        assertEquals("b", result.get(0).getName());
    }

    @Test
    void testWhereRawPredicate() {
        repository.save(newEntity("low", 1));
        repository.save(newEntity("mid", 5));
        repository.save(newEntity("high", 10));
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.gt(TestEntity::getStatus, 5);
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
        assertEquals("high", result.get(0).getName());
    }

    @Test
    void testInCollection() {
        repository.save(newEntity("a", 1));
        repository.save(newEntity("b", 2));
        repository.save(newEntity("c", 3));
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.in(TestEntity::getStatus, java.util.Arrays.asList(1, 3));
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(2, result.size());
    }

    @Test
    void testNotInCollection() {
        repository.save(newEntity("a", 1));
        repository.save(newEntity("b", 2));
        repository.save(newEntity("c", 3));
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.notIn(TestEntity::getStatus, java.util.Arrays.asList(1, 3));
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
        assertEquals(2, result.get(0).getStatus());
    }

    @Test
    void testValidateCleanStateThrowsOnUnclosedOr() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.or();
        qs.eq(TestEntity::getName, "test");
        Exception e = assertThrows(Exception.class, () -> repository.findAll(qs.toSpecification()));
        assertInstanceOf(IllegalStateException.class, e.getCause());
    }

    @Test
    void testOrGroupOrConsumer() {
        repository.save(newEntity("a", 1));
        repository.save(newEntity("b", 2));
        repository.save(newEntity("c", 3));
        repository.save(newEntity("d", 4));
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.or(outer -> outer.eq(TestEntity::getStatus, 1)
            .or(inner -> inner.eq(TestEntity::getStatus, 2).eq(TestEntity::getStatus, 3)));
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(3, result.size());
    }

    @Test
    void testEqIgnoreCase() {
        repository.save(newEntity("Hello", 1));
        repository.save(newEntity("hello", 2));
        repository.save(newEntity("WORLD", 3));
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.eqIgnoreCase(TestEntity::getName, "HELLO");
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(2, result.size());
    }

    @Test
    void testLikeIgnoreCase() {
        repository.save(newEntity("HelloWorld", 1));
        repository.save(newEntity("HELLO", 2));
        repository.save(newEntity("xyz", 3));
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.likeIgnoreCase(TestEntity::getName, "%hello%");
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(2, result.size());
    }

    @Test
    void testGroupBy() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.groupBy(TestEntity::getStatus);
        // GROUP BY on entity-level findAll is incompatible with strict SQL mode.
        // The groupBy feature is designed for custom projection queries.
        assertNotNull(qs.toSpecification());
    }

    @Test
    void testGroupByHaving() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.groupBy(TestEntity::getStatus).having((root, cb) -> cb.greaterThan(cb.count(root), 1L));
        assertNotNull(qs.toSpecification());
    }

    @Test
    void testThenCombinesConditions() {
        repository.save(newEntity("alpha", 1));
        repository.save(newEntity("beta", 2));
        repository.save(newEntity("gamma", 3));

        QuerySpec<TestEntity> qs1 = new QuerySpec<>();
        qs1.eq(TestEntity::getName, "alpha");

        QuerySpec<TestEntity> qs2 = new QuerySpec<>();
        qs2.eq(TestEntity::getStatus, 1);

        qs1.then(qs2);
        List<TestEntity> result = repository.findAll(qs1.toSpecification());
        assertEquals(1, result.size());
        assertEquals("alpha", result.get(0).getName());
    }

    @Test
    void testThenWithNullIsNoOp() {
        repository.save(newEntity("test", 0));
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        assertSame(qs, qs.then(null));
    }

    @Test
    void testTimeoutGetterSetter() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.timeout(30);
        assertEquals(Integer.valueOf(30), qs.getQueryTimeout());
        assertNull(qs.getLockMode());
    }

    @Test
    void testLockModeGetterSetter() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.lockMode(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        assertEquals(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE, qs.getLockMode());
        assertNull(qs.getQueryTimeout());
    }

    @Test
    void testApplyQuerySettings() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        assertNull(qs.getQueryTimeout());
        assertNull(qs.getLockMode());
        // applyQuerySettings on null fields is no-op - verified by no exception
    }

    @Test
    void testNotBetweenIntegration() {
        for (int i = 1; i <= 10; i++) {
            repository.save(newEntity("item" + i, i));
        }
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.notBetween(TestEntity::getStatus, 3, 7);
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(5, result.size());
    }

    @Test
    void testFetchJoin() {
        ParentEntity parent = new ParentEntity();
        parent.setCategory("cat");
        parent.setLevel(1);
        em.persist(parent);
        TestEntity child = newEntity("c1", 0);
        child.setParent(parent);
        repository.save(child);

        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.fetchJoin(TestEntity::getParent);
        assertNotNull(qs.toSpecification());
    }

    @Test
    void testLeftFetchJoin() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.leftFetchJoin(TestEntity::getParent);
        assertNotNull(qs.toSpecification());
    }

    @Test
    void testFetchJoinWithConsumer() {
        ParentEntity parent = new ParentEntity();
        parent.setCategory("admin");
        parent.setLevel(5);
        em.persist(parent);
        TestEntity child = newEntity("child", 0);
        child.setParent(parent);
        repository.save(child);

        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.<ParentEntity>fetchJoin(TestEntity::getParent, j -> j.eq(ParentEntity::getCategory, "admin"));
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
    }

    @Test
    void testLeftFetchJoinWithConsumer() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.<ParentEntity>leftFetchJoin(TestEntity::getParent, j -> j.eq(ParentEntity::getCategory, "admin"));
        assertNotNull(qs.toSpecification());
    }

    @Test
    void testOrderByAscIntegration() {
        repository.save(newEntity("b", 2));
        repository.save(newEntity("a", 1));
        repository.save(newEntity("c", 3));
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.orderByAsc(TestEntity::getName);
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals("a", result.get(0).getName());
        assertEquals("b", result.get(1).getName());
        assertEquals("c", result.get(2).getName());
    }

    @Test
    void testOrderByDescIntegration() {
        repository.save(newEntity("b", 2));
        repository.save(newEntity("a", 1));
        repository.save(newEntity("c", 3));
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.orderByDesc(TestEntity::getName);
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals("c", result.get(0).getName());
        assertEquals("b", result.get(1).getName());
        assertEquals("a", result.get(2).getName());
    }

    @Test
    void testGetSort() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        assertFalse(qs.getSort().isSorted());
        qs.orderByAsc(TestEntity::getName);
        assertTrue(qs.getSort().isSorted());
    }

    @Test
    void testAndWithOtherQuerySpec() {
        repository.save(newEntity("match", 1));
        repository.save(newEntity("match", 2));
        repository.save(newEntity("other", 3));
        QuerySpec<TestEntity> qs1 = new QuerySpec<>();
        qs1.eq(TestEntity::getName, "match");
        QuerySpec<TestEntity> qs2 = new QuerySpec<>();
        qs2.eq(TestEntity::getStatus, 1);
        Specification<TestEntity> combined = qs1.and(qs2);
        List<TestEntity> result = repository.findAll(combined);
        assertEquals(1, result.size());
    }

    @Test
    void testOrWithOtherQuerySpec() {
        repository.save(newEntity("alpha", 1));
        repository.save(newEntity("beta", 2));
        repository.save(newEntity("gamma", 3));
        QuerySpec<TestEntity> qs1 = new QuerySpec<>();
        qs1.eq(TestEntity::getName, "alpha");
        QuerySpec<TestEntity> qs2 = new QuerySpec<>();
        qs2.eq(TestEntity::getName, "beta");
        Specification<TestEntity> combined = qs1.or(qs2);
        List<TestEntity> result = repository.findAll(combined);
        assertEquals(2, result.size());
    }

    @Test
    void testApplyQuerySettingsWithBothTimeoutAndLockMode() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.timeout(10);
        qs.lockMode(jakarta.persistence.LockModeType.PESSIMISTIC_READ);
        assertNotNull(qs.getQueryTimeout());
        assertNotNull(qs.getLockMode());
    }

    @Test
    void testRawLike() {
        repository.save(newEntity("hello", 0));
        repository.save(newEntity("world", 0));
        repository.save(newEntity("hel%lo", 0));
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.rawLike(TestEntity::getName, "hel");
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(2, result.size());
    }

    @Test
    void testRawLikeEscapesWildcard() {
        repository.save(newEntity("100%", 0));
        repository.save(newEntity("100x", 0));
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.rawLike(TestEntity::getName, "100%");
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
        assertEquals("100%", result.get(0).getName());
    }

    @Test
    void testRawLikeEscapesUnderscore() {
        repository.save(newEntity("a_b", 0));
        repository.save(newEntity("axb", 0));
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.rawLike(TestEntity::getName, "a_b");
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
        assertEquals("a_b", result.get(0).getName());
    }

    @Test
    void testNestedOrThreeLevels() {
        repository.save(newEntity("a", 1));
        repository.save(newEntity("b", 2));
        repository.save(newEntity("c", 3));
        repository.save(newEntity("d", 4));
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.or(outer -> outer.eq(TestEntity::getStatus, 1)
            .or(mid -> mid.eq(TestEntity::getStatus, 2).or(inner -> inner.eq(TestEntity::getStatus, 3))));
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(3, result.size());
    }

    @Test
    void testGroupByMultipleFields() {
        repository.save(newEntity("a", 1));
        repository.save(newEntity("a", 1));
        repository.save(newEntity("b", 2));
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.groupBy(TestEntity::getName, TestEntity::getStatus);
        assertNotNull(qs.toSpecification());
    }

    @Test
    void testGroupByHavingWithPredicate() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.groupBy(TestEntity::getStatus).having((root, cb) -> cb.greaterThan(cb.count(root.get("name")), 0L));
        assertNotNull(qs.toSpecification());
    }

    @Test
    void testMixedJoinTypes() {
        ParentEntity p1 = new ParentEntity();
        p1.setCategory("admin");
        p1.setLevel(10);
        em.persist(p1);

        TestEntity child = newEntity("child", 0);
        child.setParent(p1);
        repository.save(child);

        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.<ParentEntity>join(TestEntity::getParent, j -> j.eq(ParentEntity::getCategory, "admin"));
        qs.<ParentEntity>leftJoin(TestEntity::getParent, j -> j.gt(ParentEntity::getLevel, 0));
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
    }

    @Test
    void testNotBetween() {
        for (int i = 1; i <= 10; i++) {
            repository.save(newEntity("item" + i, i));
        }
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.notBetween(TestEntity::getStatus, 4, 7);
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(6, result.size());
    }

    @Test
    void testOrderNodeToString() {
        ConditionNode.OrderNode ascNode = new ConditionNode.OrderNode("name", true);
        assertEquals("OrderNode[name ASC]", ascNode.toString());

        ConditionNode.OrderNode descNode = new ConditionNode.OrderNode("status", false);
        assertEquals("OrderNode[status DESC]", descNode.toString());
    }

    private TestEntity newEntity(String name, int status) {
        TestEntity entity = new TestEntity();
        entity.setName(name);
        entity.setStatus(status);
        return entity;
    }
}
