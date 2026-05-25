package com.zsubera.jpa.spec;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.jpa.domain.Specification;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

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
        qs.eq(TestEntity::getName, "match")
          .eq(TestEntity::getStatus, 1);
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
        qs.or()
            .eq(TestEntity::getName, "alpha")
            .eq(TestEntity::getName, "beta")
          .endOr();
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
        Specification<TestEntity> external = (root, query, cb) ->
                cb.equal(root.get("name"), "match");
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
        qs.gt(TestEntity::getStatus, 1)
          .lt(TestEntity::getStatus, 10);
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
        qs.exists(TestEntity.class, sub -> sub
                .eq(TestEntity::getName, "child"));
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
        qs.notExists(TestEntity.class, sub -> sub
                .eq(TestEntity::getName, "nonexistent"));
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
    void testMultiLikeWithNullKeywordNoOp() {
        repository.save(newEntity("hello", 0));
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.multiLike(null, TestEntity::getName);
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
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
        qs.or()
            .eq(TestEntity::getName, "only")
          .endOr();
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
        qs.eq(TestEntity::getName, (String) null);
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
        qs.ne(TestEntity::getName, (String) null);
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
        qs.<ParentEntity>leftJoin(TestEntity::getParent, j -> j
            .or(oj -> oj.eq(ParentEntity::getCategory, "admin").isNull(ParentEntity::getCategory))
        );
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
    void testWhereRawPredicate() {
        repository.save(newEntity("low", 1));
        repository.save(newEntity("mid", 5));
        repository.save(newEntity("high", 10));
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.where((path, cb) -> cb.greaterThan(path.get("status"), 5));
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
        Exception e = assertThrows(Exception.class,
                () -> repository.findAll(qs.toSpecification()));
        assertInstanceOf(IllegalStateException.class, e.getCause());
    }

    @Test
    void testOrGroupOrConsumer() {
        repository.save(newEntity("a", 1));
        repository.save(newEntity("b", 2));
        repository.save(newEntity("c", 3));
        repository.save(newEntity("d", 4));
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.or(outer -> outer
            .eq(TestEntity::getStatus, 1)
            .or(inner -> inner.eq(TestEntity::getStatus, 2).eq(TestEntity::getStatus, 3)));
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(3, result.size());
    }

    private TestEntity newEntity(String name, int status) {
        TestEntity entity = new TestEntity();
        entity.setName(name);
        entity.setStatus(status);
        return entity;
    }
}
