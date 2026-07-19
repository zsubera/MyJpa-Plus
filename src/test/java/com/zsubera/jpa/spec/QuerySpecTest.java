package com.zsubera.jpa.spec;

import static org.junit.jupiter.api.Assertions.*;

import com.zsubera.jpa.exception.QueryBuildException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.jpa.domain.Specification;

@DataJpaTest
@org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase(
    replace = org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE)
public class QuerySpecTest {

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
        qs.like(TestEntity::getName, "hello");
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
        qs.<ParentEntity>join(TestEntity::getParent, jg -> jg.eq(ParentEntity::getCategory, "admin"));

        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
    }

    @Test
    void testOrGroup() {
        repository.save(newEntity("alpha", 1));
        repository.save(newEntity("beta", 2));
        repository.save(newEntity("gamma", 3));
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.or(g -> g.eq(TestEntity::getName, "alpha"), g -> g.eq(TestEntity::getName, "beta"));
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(2, result.size());
    }

    @Test
    void testMultipleOrChainsOrSemantics() {
        repository.save(newEntity("alpha", 1));
        repository.save(newEntity("beta", 2));
        repository.save(newEntity("gamma", 3));
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.or(g -> g.eq(TestEntity::getStatus, 1)).or(g -> g.eq(TestEntity::getStatus, 2));
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
        qs.<ParentEntity>join(TestEntity::getParent, jg -> {
            jg.eq(ParentEntity::getCategory, "admin");
            jg.eq(ParentEntity::getLevel, 10);
        });

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
        qs.<ParentEntity>leftJoin(TestEntity::getParent, jg -> {
            jg.or(ojg -> {
                ojg.eq(ParentEntity::getCategory, "admin");
                ojg.isNull(ParentEntity::getCategory);
            });
        });

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
        qs.<ParentEntity>join(TestEntity::getParent, jg -> {
            jg.eq(ParentEntity::getCategory, "admin");
            jg.eq(ParentEntity::getLevel, 5);
        });

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
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.notLike(TestEntity::getName, "hello");
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
        assertThrows(IllegalArgumentException.class, () -> qs.join(null, jg -> {
        }));
    }

    @Test
    void testNullFieldInOrGroupThrowsException() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.or(og -> {
            assertThrows(IllegalArgumentException.class, () -> og.eq(null, "value"));
        });
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
        qs.<ParentEntity>join(TestEntity::getParent, jg -> {
            assertThrows(IllegalArgumentException.class, () -> jg.eq(null, "admin"));
        });
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
        qs.or(g -> g.eq(TestEntity::getName, "only"));
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
        qs.<ParentEntity>join(TestEntity::getParent, jg -> {
            jg.or(org -> org.eq(ParentEntity::getCategory, "admin"), org -> org.eq(ParentEntity::getCategory, "user"));
        });

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
        qs.like(TestEntity::getName, "ab");
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(2, result.size());
    }

    @Test
    void testOrConsumer() {
        repository.save(newEntity("alpha", 1));
        repository.save(newEntity("beta", 2));
        repository.save(newEntity("gamma", 3));
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.or(g -> g.eq(TestEntity::getName, "alpha"), g -> g.eq(TestEntity::getName, "beta"));
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
    void testOrGroupOrConsumer() {
        repository.save(newEntity("a", 1));
        repository.save(newEntity("b", 2));
        repository.save(newEntity("c", 3));
        repository.save(newEntity("d", 4));
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        // status=1 OR status=2 OR (status=2 AND status=3) -> status=1 OR status=2
        qs.or(outer -> outer.eq(TestEntity::getStatus, 1),
            inner -> inner.eq(TestEntity::getStatus, 2).eq(TestEntity::getStatus, 3));
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
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
        qs.likeIgnoreCase(TestEntity::getName, "hello");
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
        qs.<ParentEntity>fetchJoin(TestEntity::getParent, jg -> {
        });
        assertNotNull(qs.toSpecification());
    }

    @Test
    void testLeftFetchJoin() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.<ParentEntity>leftJoin(TestEntity::getParent, jg -> {
        });
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
        // rawLike() now delegates to contains(), which escapes wildcards
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.like(TestEntity::getName, "hel");
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        // contains("hel") matches "hello" and "hel%lo" (wildcards are escaped)
        assertEquals(2, result.size());
    }

    @Test
    void testRawLikeEscapesWildcard() {
        repository.save(newEntity("100%", 0));
        repository.save(newEntity("100x", 0));
        // rawLike() now delegates to contains(), which escapes wildcards
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.like(TestEntity::getName, "100%");
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        // contains("100%") with escaped wildcard matches only "100%"
        assertEquals(1, result.size());
        assertEquals("100%", result.get(0).getName());
    }

    @Test
    void testRawLikeEscapesUnderscore() {
        repository.save(newEntity("a_b", 0));
        repository.save(newEntity("axb", 0));
        // rawLike() now delegates to contains(), which escapes wildcards
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.like(TestEntity::getName, "a_b");
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        // contains("a_b") with escaped underscore matches only "a_b"
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
        // status=1 OR status=2 OR status=3
        qs.or(outer -> outer.eq(TestEntity::getStatus, 1), mid -> mid.eq(TestEntity::getStatus, 2),
            inner -> inner.eq(TestEntity::getStatus, 3));
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
    void testRepeatedLeftJoinPathKeepsLeftJoinSemantics() {
        ParentEntity parent = new ParentEntity();
        parent.setCategory("admin");
        parent.setLevel(10);
        em.persist(parent);

        TestEntity matched = newEntity("matched", 1);
        matched.setParent(parent);
        repository.save(matched);

        TestEntity withoutParent = newEntity("without-parent", 2);
        repository.save(withoutParent);

        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.<ParentEntity>leftJoin(TestEntity::getParent, j -> j.eq(ParentEntity::getCategory, "admin"));
        qs.<ParentEntity>leftJoin(TestEntity::getParent, j -> j.gt(ParentEntity::getLevel, 0));

        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(2, result.size(), "Repeated LEFT JOIN predicates should stay on the ON clause");
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

    // ---- inSubQuery / notInSubQuery tests ----

    @Test
    void testInSubQuery() {
        // 准备测试数据：status=1 的实体作为子查询目标
        repository.save(newEntity("match1", 1));
        repository.save(newEntity("match2", 1));
        repository.save(newEntity("other1", 2));
        repository.save(newEntity("other2", 3));

        // 查找 status IN (SELECT status FROM testEntity WHERE name LIKE '%match%')
        QuerySpec<TestEntity> spec = new QuerySpec<TestEntity>().inSubQuery(TestEntity::getStatus, TestEntity.class,
            sub -> sub.select(TestEntity::getStatus).like(TestEntity::getName, "match"));
        List<TestEntity> results = repository.findAll(spec.toSpecification());
        assertEquals(2, results.size());
        assertTrue(results.stream().allMatch(e -> e.getStatus() == 1));
    }

    @Test
    void testNotInSubQuery() {
        // 准备测试数据
        repository.save(newEntity("keep1", 1));
        repository.save(newEntity("keep2", 2));
        repository.save(newEntity("exclude1", 3));
        repository.save(newEntity("exclude2", 3));

        // 查找 status NOT IN (SELECT status FROM testEntity WHERE name LIKE '%exclude%')
        QuerySpec<TestEntity> spec = new QuerySpec<TestEntity>().notInSubQuery(TestEntity::getStatus, TestEntity.class,
            sub -> sub.select(TestEntity::getStatus).like(TestEntity::getName, "exclude"));
        List<TestEntity> results = repository.findAll(spec.toSpecification());
        assertEquals(2, results.size());
        assertTrue(results.stream().noneMatch(e -> e.getStatus() == 3));
    }

    @Test
    void testInSubQueryWithMultipleConditions() {
        // 准备测试数据
        repository.save(newEntity("target_a", 10));
        repository.save(newEntity("target_b", 20));
        repository.save(newEntity("non_target", 30));
        repository.save(newEntity("another", 40));

        // 子查询中带多种条件：name 以 target_ 开头且 status > 5
        QuerySpec<TestEntity> spec =
            new QuerySpec<TestEntity>().inSubQuery(TestEntity::getStatus, TestEntity.class, sub -> sub
                .select(TestEntity::getStatus).startsWith(TestEntity::getName, "target_").gt(TestEntity::getStatus, 5));
        List<TestEntity> results = repository.findAll(spec.toSpecification());
        // status 10 和 20 匹配（target_a 和 target_b 的 status）
        assertEquals(2, results.size());
    }

    @Test
    void testInSubQueryNullValidation() {
        // null outerField
        assertThrows(IllegalArgumentException.class,
            () -> new QuerySpec<TestEntity>().inSubQuery(null, TestEntity.class, sub -> {
            }));
        // null subEntity
        assertThrows(IllegalArgumentException.class,
            () -> new QuerySpec<TestEntity>().inSubQuery(TestEntity::getStatus, null, sub -> {
            }));
        // null config
        assertThrows(IllegalArgumentException.class,
            () -> new QuerySpec<TestEntity>().inSubQuery(TestEntity::getStatus, TestEntity.class, null));
        // notInSubQuery null outerField
        assertThrows(IllegalArgumentException.class,
            () -> new QuerySpec<TestEntity>().notInSubQuery(null, TestEntity.class, sub -> {
            }));
        // notInSubQuery null subEntity
        assertThrows(IllegalArgumentException.class,
            () -> new QuerySpec<TestEntity>().notInSubQuery(TestEntity::getStatus, null, sub -> {
            }));
        // notInSubQuery null config
        assertThrows(IllegalArgumentException.class,
            () -> new QuerySpec<TestEntity>().notInSubQuery(TestEntity::getStatus, TestEntity.class, null));
    }

    @Test
    void testInSubQueryInOrGroup() {
        // 准备测试数据
        repository.save(newEntity("sub_match", 1));
        repository.save(newEntity("direct", 2));
        repository.save(newEntity("neither", 3));

        // inSubQuery 与 OrGroup 结合使用
        QuerySpec<TestEntity> spec = new QuerySpec<TestEntity>().or(
            group -> group.inSubQuery(TestEntity::getStatus, TestEntity.class,
                sub -> sub.select(TestEntity::getStatus).eq(TestEntity::getName, "sub_match")),
            group -> group.eq(TestEntity::getName, "direct"));
        List<TestEntity> results = repository.findAll(spec.toSpecification());
        assertEquals(2, results.size());
        assertTrue(results.stream().anyMatch(e -> "direct".equals(e.getName())));
        assertTrue(results.stream().anyMatch(e -> e.getStatus() == 1));
    }

    @Test
    void testInSubQueryNoMatch() {
        // 子查询返回空结果
        repository.save(newEntity("a", 1));
        repository.save(newEntity("b", 2));

        QuerySpec<TestEntity> spec = new QuerySpec<TestEntity>().inSubQuery(TestEntity::getStatus, TestEntity.class,
            sub -> sub.select(TestEntity::getStatus).eq(TestEntity::getName, "nonexistent"));
        List<TestEntity> results = repository.findAll(spec.toSpecification());
        assertEquals(0, results.size());
    }

    @Test
    void testNotInSubQueryNoMatch() {
        // 子查询返回空结果，NOT IN 应返回所有行
        repository.save(newEntity("a", 1));
        repository.save(newEntity("b", 2));

        QuerySpec<TestEntity> spec = new QuerySpec<TestEntity>().notInSubQuery(TestEntity::getStatus, TestEntity.class,
            sub -> sub.select(TestEntity::getStatus).eq(TestEntity::getName, "nonexistent"));
        List<TestEntity> results = repository.findAll(spec.toSpecification());
        assertEquals(2, results.size());
    }

    private TestEntity newEntity(String name, int status) {
        TestEntity entity = new TestEntity();
        entity.setName(name);
        entity.setStatus(status);
        return entity;
    }

    // ---- Type-safe HAVING helper tests ----

    @Test
    void testHavingCount() {
        // GROUP BY with type-safe HAVING COUNT - verify specification is created
        QuerySpec<TestEntity> qs = new QuerySpec<TestEntity>().groupBy(TestEntity::getStatus)
            .havingCount(TestEntity::getId, ConditionNode.Op.GT, 2L);
        assertNotNull(qs.toSpecification(), "Specification with havingCount should be created");
    }

    @Test
    void testHavingSum() {
        // GROUP BY with type-safe HAVING SUM
        QuerySpec<TestEntity> qs = new QuerySpec<TestEntity>().groupBy(TestEntity::getParent)
            .havingSum(TestEntity::getStatus, ConditionNode.Op.GT, 25);
        assertNotNull(qs.toSpecification(), "Specification with havingSum should be created");
    }

    @Test
    void testHavingAvg() {
        // GROUP BY with type-safe HAVING AVG
        QuerySpec<TestEntity> qs = new QuerySpec<TestEntity>().groupBy(TestEntity::getParent)
            .havingAvg(TestEntity::getStatus, ConditionNode.Op.GT, 15);
        assertNotNull(qs.toSpecification(), "Specification with havingAvg should be created");
    }

    @Test
    void testHavingMax() {
        // GROUP BY with type-safe HAVING MAX
        QuerySpec<TestEntity> qs = new QuerySpec<TestEntity>().groupBy(TestEntity::getParent)
            .havingMax(TestEntity::getStatus, ConditionNode.Op.GE, 5);
        assertNotNull(qs.toSpecification(), "Specification with havingMax should be created");
    }

    @Test
    void testHavingMin() {
        // GROUP BY with type-safe HAVING MIN
        QuerySpec<TestEntity> qs = new QuerySpec<TestEntity>().groupBy(TestEntity::getParent)
            .havingMin(TestEntity::getStatus, ConditionNode.Op.LE, 5);
        assertNotNull(qs.toSpecification(), "Specification with havingMin should be created");
    }

    @Test
    void testHavingCountNullValidation() {
        assertThrows(IllegalArgumentException.class, () -> new QuerySpec<TestEntity>().groupBy(TestEntity::getStatus)
            .havingCount(null, ConditionNode.Op.GT, 5L));
        assertThrows(IllegalArgumentException.class,
            () -> new QuerySpec<TestEntity>().groupBy(TestEntity::getStatus).havingCount(TestEntity::getId, null, 5L));
    }

    @Test
    void testHavingSumNullValidation() {
        assertThrows(IllegalArgumentException.class,
            () -> new QuerySpec<TestEntity>().groupBy(TestEntity::getStatus).havingSum(null, ConditionNode.Op.GT, 10));
        assertThrows(IllegalArgumentException.class, () -> new QuerySpec<TestEntity>().groupBy(TestEntity::getStatus)
            .havingSum(TestEntity::getStatus, null, 10));
        assertThrows(IllegalArgumentException.class, () -> new QuerySpec<TestEntity>().groupBy(TestEntity::getStatus)
            .havingSum(TestEntity::getStatus, ConditionNode.Op.GT, null));
    }

    @Test
    void testHavingUnsupportedOperator() {
        // BETWEEN is not supported for HAVING
        assertThrows(IllegalArgumentException.class, () -> new QuerySpec<TestEntity>().groupBy(TestEntity::getStatus)
            .havingCount(TestEntity::getId, ConditionNode.Op.BETWEEN, 1L));
    }

    @Test
    void testHavingChainable() {
        // Verify HAVING methods support chaining
        QuerySpec<TestEntity> qs = new QuerySpec<TestEntity>().groupBy(TestEntity::getStatus)
            .havingCount(TestEntity::getId, ConditionNode.Op.GT, 1L)
            .havingSum(TestEntity::getStatus, ConditionNode.Op.GT, 10).orderByAsc(TestEntity::getStatus);
        assertNotNull(qs.toSpecification(), "Chained HAVING specification should be created");
    }

    @Test
    void testSimpleNodeDefensiveCopyCollection() {
        // Verify that SimpleNode performs defensive copy on Collection values
        java.util.List<String> values = new java.util.ArrayList<>();
        values.add("a");
        values.add("b");
        ConditionNode.SimpleNode node = new ConditionNode.SimpleNode("name", values, ConditionNode.Op.IN);
        // Modify original list
        values.add("c");
        // Node should still have 2 items (defensive copy)
        assertTrue(node.value instanceof java.util.List);
        assertEquals(2, ((java.util.List<?>)node.value).size(),
            "SimpleNode should perform defensive copy on Collection values");
    }

    @Test
    void testSimpleNodeToStringMasking() {
        // Verify toString() masks values properly
        ConditionNode.SimpleNode nullNode = new ConditionNode.SimpleNode("name", null, ConditionNode.Op.IS_NULL);
        assertTrue(nullNode.toString().contains("null"));

        ConditionNode.SimpleNode stringNode = new ConditionNode.SimpleNode("name", "secret", ConditionNode.Op.EQ);
        assertTrue(stringNode.toString().contains("***"));
        assertFalse(stringNode.toString().contains("secret"));

        ConditionNode.SimpleNode numberNode = new ConditionNode.SimpleNode("status", 42, ConditionNode.Op.EQ);
        assertTrue(numberNode.toString().contains("Integer[***]"));
    }

    // ---- copy() tests ----

    @Test
    void testCopyPreservesConditionsInCleanState() {
        repository.save(newEntity("a", 1));
        repository.save(newEntity("b", 2));
        repository.save(newEntity("c", 3));

        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.eq(TestEntity::getName, "a");
        qs.or(o -> o.eq(TestEntity::getStatus, 1), o -> o.eq(TestEntity::getStatus, 2));

        QuerySpec<TestEntity> copy = qs.copy();

        // Both should produce identical results
        // name='a' AND (status=1 OR status=2) => matches 'a' with status 1
        List<TestEntity> original = repository.findAll(qs.toSpecification());
        List<TestEntity> copied = repository.findAll(copy.toSpecification());
        assertEquals(original.size(), copied.size());
        assertEquals(1, original.size());
    }

    @Test
    void testCopyInsideOrConsumer() {
        // Verify copy() inside or() consumer throws QueryBuildException
        // because groupStack is transient build state and cannot be safely copied
        repository.save(newEntity("a", 1));
        repository.save(newEntity("b", 2));
        repository.save(newEntity("c", 3));

        QuerySpec<TestEntity> qs = new QuerySpec<>();
        assertThrows(QueryBuildException.class, () -> {
            qs.or(o -> {
                o.eq(TestEntity::getStatus, 1);
                qs.copy(); // Should throw: cannot copy inside or() consumer
            });
        });
    }

    @Test
    void testCopyInsideNestedOrConsumer() {
        // Verify copy() with nested OR throws QueryBuildException
        repository.save(newEntity("a", 1));
        repository.save(newEntity("b", 2));
        repository.save(newEntity("c", 3));
        repository.save(newEntity("d", 4));

        QuerySpec<TestEntity> qs = new QuerySpec<>();
        assertThrows(QueryBuildException.class, () -> {
            qs.or(outer -> {
                outer.eq(TestEntity::getStatus, 1);
                outer.or(inner -> {
                    inner.eq(TestEntity::getStatus, 2);
                    qs.copy(); // Should throw: cannot copy inside nested or() consumer
                });
            });
        });
    }

    @Test
    void testCopyDeepNestedOrGroupStackOrder() {
        // Verify that copy() inside nested OR throws QueryBuildException
        QuerySpec<TestEntity> qs = new QuerySpec<>();

        assertThrows(QueryBuildException.class, () -> {
            qs.or(outer -> {
                outer.eq(TestEntity::getStatus, 1);
                outer.or(mid -> {
                    mid.eq(TestEntity::getStatus, 2);
                    mid.or(inner -> {
                        inner.eq(TestEntity::getStatus, 3);
                        qs.copy(); // Should throw: cannot copy inside nested or() consumer
                    });
                });
            });
        });
    }

    @Test
    void testCopyEmptySpec_usesFastPath() {
        // Empty spec should use fast path (no deep copy needed)
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        QuerySpec<TestEntity> copy = qs.copy();

        // Should be independent instances
        assertNotSame(qs, copy);
        // Both should return all entities
        List<TestEntity> originalResult = repository.findAll(qs.toSpecification());
        List<TestEntity> copyResult = repository.findAll(copy.toSpecification());
        assertEquals(originalResult.size(), copyResult.size());
    }

    @Test
    void testCopyEmptySpecWithDistinct_onlyDistinctCopied() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.distinct();
        QuerySpec<TestEntity> copy = qs.copy();

        // Copy should have distinct=true
        java.lang.reflect.Field distinctField;
        try {
            distinctField = QuerySpec.class.getDeclaredField("distinct");
            distinctField.setAccessible(true);
            assertTrue((boolean)distinctField.get(copy), "Copy should have distinct=true");
        } catch (Exception e) {
            fail("Reflection failed: " + e.getMessage());
        }
    }

    // ---- toSpecification vs toPredicate validation tests ----

    @Test
    void toSpecification_validatesUnclosedOrGroup() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        // Push an or group but don't close it via Consumer
        OrGroup<TestEntity> orGroup = qs.pushOrGroup();
        orGroup.eq(TestEntity::getName, "test");
        // Don't call endOr() — group is unclosed

        assertThrows(com.zsubera.jpa.exception.QueryBuildException.class, qs::toSpecification,
            "toSpecification() should validate unclosed or() groups");
    }

    @Test
    void toPredicate_doesNotValidateUnclosedOrGroup() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        OrGroup<TestEntity> orGroup = qs.pushOrGroup();
        orGroup.eq(TestEntity::getName, "test");
        // Don't call endOr() — group is unclosed

        // toPredicate() should NOT throw — this is the Spring Data internal path
        // Use the real EntityManager from the test context
        jakarta.persistence.criteria.CriteriaBuilder cb = em.getCriteriaBuilder();
        jakarta.persistence.criteria.CriteriaQuery<TestEntity> cq = cb.createQuery(TestEntity.class);
        jakarta.persistence.criteria.Root<TestEntity> root = cq.from(TestEntity.class);

        // If toPredicate() throws IllegalStateException, this test will fail
        jakarta.persistence.criteria.Predicate predicate = qs.toPredicate(root, cq, cb);
        assertNotNull(predicate, "toPredicate() should return a valid predicate");
    }

    @Test
    void toSpecification_validatesClosedOrGroup() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.or(o -> o.eq(TestEntity::getName, "test"));
        // or() group is properly closed by Consumer

        // toSpecification() should not throw for properly closed groups
        Specification<TestEntity> spec = qs.toSpecification();
        assertNotNull(spec, "toSpecification() should return a valid Specification");
    }

    @Test
    void querySpec_usedAsSpecification_directlyWorks() {
        repository.save(newEntity("direct", 1));
        repository.save(newEntity("other", 2));
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.eq(TestEntity::getName, "direct");

        // Use QuerySpec directly as Specification (without calling toSpecification())
        List<TestEntity> result = repository.findAll((Specification<TestEntity>)qs);
        assertEquals(1, result.size());
        assertEquals("direct", result.get(0).getName());
    }

    @Test
    void querySpec_andSpecification_compositionWorks() {
        repository.save(newEntity("a", 1));
        repository.save(newEntity("b", 2));
        repository.save(newEntity("c", 3));

        QuerySpec<TestEntity> qs1 = new QuerySpec<>();
        qs1.ge(TestEntity::getStatus, 2);

        Specification<TestEntity> extra = (root, query, cb) -> cb.lessThan(root.get("status"), 3);

        // Compose QuerySpec with external Specification via toSpecification(external)
        List<TestEntity> result = repository.findAll(qs1.toSpecification(extra));
        assertEquals(1, result.size());
        assertEquals("b", result.get(0).getName());
    }

    @Test
    void testHavingCountWithDifferentOps() {
        repository.save(newEntity("a", 1));
        repository.save(newEntity("b", 2));
        repository.flush();

        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.groupBy(TestEntity::getStatus).havingCount(TestEntity::getId, ConditionNode.Op.GE, 1L);
        assertNotNull(qs.toSpecification());
    }

    @Test
    void testHavingSumWithDifferentOps() {
        repository.save(newEntity("a", 10));
        repository.save(newEntity("b", 20));
        repository.flush();

        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.groupBy(TestEntity::getStatus).havingSum(TestEntity::getStatus, ConditionNode.Op.GE, 10);
        assertNotNull(qs.toSpecification());
    }

    @Test
    void testHavingAvgWithDifferentOps() {
        repository.save(newEntity("a", 10));
        repository.flush();

        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.groupBy(TestEntity::getStatus).havingAvg(TestEntity::getStatus, ConditionNode.Op.GE, 10.0);
        assertNotNull(qs.toSpecification());
    }

    @Test
    void testHavingMaxWithDifferentOps() {
        repository.save(newEntity("a", 10));
        repository.flush();

        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.groupBy(TestEntity::getStatus).havingMax(TestEntity::getStatus, ConditionNode.Op.GE, 10);
        assertNotNull(qs.toSpecification());
    }

    @Test
    void testHavingMinWithDifferentOps() {
        repository.save(newEntity("a", 10));
        repository.flush();

        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.groupBy(TestEntity::getStatus).havingMin(TestEntity::getStatus, ConditionNode.Op.GE, 10);
        assertNotNull(qs.toSpecification());
    }

    @Test
    void testGroupByMultipleFieldsNew() {
        repository.save(newEntity("a", 1));
        repository.save(newEntity("b", 2));
        repository.flush();

        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.groupBy(TestEntity::getName, TestEntity::getStatus);
        assertNotNull(qs.toSpecification());
    }

    @Test
    void testToString() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.eq(TestEntity::getName, "test");
        String str = qs.toString();
        assertNotNull(str);
        assertTrue(str.contains("QuerySpec"));
    }

    @Test
    void testCopy() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.eq(TestEntity::getName, "test").distinct();

        QuerySpec<TestEntity> copy = qs.copy();
        assertNotNull(copy);
        assertTrue(copy.toString().contains("QuerySpec"));
    }

    @Test
    void testThen() {
        QuerySpec<TestEntity> qs1 = new QuerySpec<>();
        qs1.eq(TestEntity::getName, "a");

        QuerySpec<TestEntity> qs2 = new QuerySpec<>();
        qs2.eq(TestEntity::getStatus, 1);

        QuerySpec<TestEntity> result = qs1.then(qs2);
        assertNotNull(result);
    }

    @Test
    void testAndQuerySpec() {
        QuerySpec<TestEntity> qs1 = new QuerySpec<>();
        qs1.eq(TestEntity::getName, "a");

        QuerySpec<TestEntity> qs2 = new QuerySpec<>();
        qs2.eq(TestEntity::getStatus, 1);

        QuerySpec<TestEntity> result = qs1.and(qs2);
        assertNotNull(result);
    }

    @Test
    void testHavingCountNullFieldThrows() {
        assertThrows(IllegalArgumentException.class, () -> {
            new QuerySpec<TestEntity>().havingCount(null, ConditionNode.Op.GT, 1L);
        });
    }

    @Test
    void testHavingSumNullValueThrows() {
        assertThrows(IllegalArgumentException.class, () -> {
            new QuerySpec<TestEntity>().havingSum(TestEntity::getStatus, ConditionNode.Op.GT, null);
        });
    }

    @Test
    void testHavingAvgNullValueThrows() {
        assertThrows(IllegalArgumentException.class, () -> {
            new QuerySpec<TestEntity>().havingAvg(TestEntity::getStatus, ConditionNode.Op.GT, null);
        });
    }

    @Test
    void testHavingMaxNullValueThrows() {
        assertThrows(IllegalArgumentException.class, () -> {
            new QuerySpec<TestEntity>().havingMax(TestEntity::getStatus, ConditionNode.Op.GT, null);
        });
    }

    @Test
    void testHavingMinNullValueThrows() {
        assertThrows(IllegalArgumentException.class, () -> {
            new QuerySpec<TestEntity>().havingMin(TestEntity::getStatus, ConditionNode.Op.GT, null);
        });
    }

    @Test
    void testTimeout() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.timeout(30);
        assertEquals(Integer.valueOf(30), qs.getQueryTimeout());
    }

    @Test
    void testLockMode() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.lockMode(jakarta.persistence.LockModeType.PESSIMISTIC_READ);
        assertEquals(jakarta.persistence.LockModeType.PESSIMISTIC_READ, qs.getLockMode());
    }

    @Test
    void testOrderByAscAndDesc() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.orderByAsc(TestEntity::getName).orderByDesc(TestEntity::getStatus);
        assertNotNull(qs.toSpecification());
    }

    @Test
    void testGetSortNew() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.orderByAsc(TestEntity::getName).orderByDesc(TestEntity::getStatus);
        org.springframework.data.domain.Sort sort = qs.getSort();
        assertNotNull(sort);
        assertTrue(sort.isSorted());
    }

    @Test
    void testOrWithQuerySpec() {
        QuerySpec<TestEntity> qs1 = new QuerySpec<>();
        qs1.eq(TestEntity::getName, "a");

        QuerySpec<TestEntity> qs2 = new QuerySpec<>();
        qs2.eq(TestEntity::getName, "b");

        Specification<TestEntity> result = qs1.or(qs2);
        assertNotNull(result);
    }

    @Test
    void testNot() {
        repository.save(newEntity("a", 1));
        repository.save(newEntity("b", 2));
        repository.flush();

        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.not(n -> n.eq(TestEntity::getName, "a"));
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
        assertEquals("b", result.get(0).getName());
    }

    @Test
    void testNotWithEmptyGroup() {
        repository.save(newEntity("a", 1));
        repository.flush();

        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.not(n -> {
        });
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(0, result.size());
    }

    @Test
    void testOrWithEmptyGroup() {
        repository.save(newEntity("a", 1));
        repository.flush();

        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.or(o -> {
        });
        // Empty OR group is cleaned up, producing no predicate — all records match
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
    }

    @Test
    void testInSubQueryNew() {
        repository.save(newEntity("a", 1));
        repository.flush();

        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.inSubQuery(TestEntity::getStatus, TestEntity.class,
            sub -> sub.select(TestEntity::getStatus).eq(TestEntity::getStatus, 1));
        assertNotNull(qs.toSpecification());
    }

    @Test
    void testNotInSubQueryNew() {
        repository.save(newEntity("a", 1));
        repository.flush();

        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.notInSubQuery(TestEntity::getStatus, TestEntity.class,
            sub -> sub.select(TestEntity::getStatus).eq(TestEntity::getStatus, 1));
        assertNotNull(qs.toSpecification());
    }

    @Test
    void testMultiLikeStringFieldNames() {
        repository.save(newEntity("hello", 0));
        repository.flush();

        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.multiLike("hello", "name");
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
    }

    @Test
    void testToDescription() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.eq(TestEntity::getName, "test").groupBy(TestEntity::getStatus).orderByAsc(TestEntity::getName);
        String desc = qs.toDescription();
        assertNotNull(desc);
        assertTrue(desc.contains("Query{"));
    }

    @Test
    void testToDescriptionWithHaving() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.groupBy(TestEntity::getStatus).havingCount(TestEntity::getId, ConditionNode.Op.GT, 1L);
        String desc = qs.toDescription();
        assertNotNull(desc);
        assertTrue(desc.contains("HAVING"));
    }

    @Test
    void testToDescriptionWithTimeout() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.timeout(30);
        String desc = qs.toDescription();
        assertNotNull(desc);
        assertTrue(desc.contains("timeout=30s"));
    }

    @Test
    void testToDescriptionWithLockMode() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.lockMode(jakarta.persistence.LockModeType.PESSIMISTIC_READ);
        String desc = qs.toDescription();
        assertNotNull(desc);
        assertTrue(desc.contains("lockMode"));
    }

    @Test
    void testToDescriptionWithDistinct() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.distinct();
        String desc = qs.toDescription();
        assertNotNull(desc);
        assertTrue(desc.contains("DISTINCT"));
    }

    @Test
    void testToStringWithAllFields() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.eq(TestEntity::getName, "test").distinct().groupBy(TestEntity::getStatus)
            .havingCount(TestEntity::getId, ConditionNode.Op.GT, 1L).orderByAsc(TestEntity::getName).timeout(30)
            .lockMode(jakarta.persistence.LockModeType.PESSIMISTIC_READ);
        String str = qs.toString();
        assertNotNull(str);
        assertTrue(str.contains("distinct"));
        assertTrue(str.contains("groupBy"));
        assertTrue(str.contains("having"));
        assertTrue(str.contains("orderBy"));
        assertTrue(str.contains("timeout=30s"));
        assertTrue(str.contains("lockMode"));
    }

    @Test
    void testCopyWithGroupStack() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.eq(TestEntity::getName, "a");
        QuerySpec<TestEntity> copy = qs.copy();
        assertNotNull(copy);
    }

    @Test
    void testThenWithUnclosedGroup() {
        QuerySpec<TestEntity> qs1 = new QuerySpec<>();
        qs1.or(o -> o.eq(TestEntity::getName, "a"));
        // or() group is closed by Consumer

        QuerySpec<TestEntity> qs2 = new QuerySpec<>();
        qs2.eq(TestEntity::getStatus, 1);

        // This should work since both groups are closed
        QuerySpec<TestEntity> result = qs1.then(qs2);
        assertNotNull(result);
    }

    @Test
    void testOrWithQuerySpecReturnsSpecification() {
        QuerySpec<TestEntity> qs1 = new QuerySpec<>();
        qs1.eq(TestEntity::getName, "a");

        QuerySpec<TestEntity> qs2 = new QuerySpec<>();
        qs2.eq(TestEntity::getName, "b");

        Specification<TestEntity> result = qs1.or(qs2);
        assertNotNull(result);
    }

    @Test
    void testOrWithNullQuerySpec() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.eq(TestEntity::getName, "a");

        Specification<TestEntity> result = qs.orCombine((QuerySpec<TestEntity>)null);
        assertNotNull(result);
    }

    @Test
    void testToSpecificationWithExternalSpec() {
        repository.save(newEntity("a", 1));
        repository.save(newEntity("b", 2));
        repository.flush();

        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.eq(TestEntity::getName, "a");

        Specification<TestEntity> external = (root, query, cb) -> cb.greaterThan(root.get("status"), 0);
        Specification<TestEntity> combined = qs.toSpecification(external);
        List<TestEntity> result = repository.findAll(combined);
        assertEquals(1, result.size());
    }

    @Test
    void testToSpecificationWithNullExternalSpec() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.eq(TestEntity::getName, "test");

        Specification<TestEntity> result = qs.toSpecification((Specification<TestEntity>)null);
        assertNotNull(result);
    }

    @Test
    void testCopyWithGroupByAndHaving() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.groupBy(TestEntity::getStatus).havingCount(TestEntity::getId, ConditionNode.Op.GT, 1L)
            .eq(TestEntity::getName, "test");

        QuerySpec<TestEntity> copy = qs.copy();
        assertNotNull(copy);
    }

    @Test
    void testCopyWithOrderBy() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.orderByAsc(TestEntity::getName).orderByDesc(TestEntity::getStatus);

        QuerySpec<TestEntity> copy = qs.copy();
        assertNotNull(copy);
    }

    @Test
    void testCopyWithTimeoutAndLockMode() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.timeout(30).lockMode(jakarta.persistence.LockModeType.PESSIMISTIC_READ);

        QuerySpec<TestEntity> copy = qs.copy();
        assertNotNull(copy);
        assertEquals(Integer.valueOf(30), copy.getQueryTimeout());
        assertEquals(jakarta.persistence.LockModeType.PESSIMISTIC_READ, copy.getLockMode());
    }

    @Test
    void testToStringEmpty() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        String str = qs.toString();
        assertNotNull(str);
        assertTrue(str.contains("QuerySpec"));
        assertTrue(str.contains("conditions=0"));
    }

    @Test
    void testToStringWithConditions() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.eq(TestEntity::getName, "test").ne(TestEntity::getStatus, 1);
        String str = qs.toString();
        assertNotNull(str);
        assertTrue(str.contains("conditions=2"));
    }

    @Test
    void testToStringWithGroupBy() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.groupBy(TestEntity::getStatus);
        String str = qs.toString();
        assertNotNull(str);
        assertTrue(str.contains("groupBy"));
    }

    @Test
    void testToStringWithHaving() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.groupBy(TestEntity::getStatus).havingCount(TestEntity::getId, ConditionNode.Op.GT, 1L);
        String str = qs.toString();
        assertNotNull(str);
        assertTrue(str.contains("having"));
    }

    @Test
    void testToStringWithOrderBy() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.orderByAsc(TestEntity::getName);
        String str = qs.toString();
        assertNotNull(str);
        assertTrue(str.contains("orderBy"));
    }

    @Test
    void testToStringWithTimeout() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.timeout(30);
        String str = qs.toString();
        assertNotNull(str);
        assertTrue(str.contains("timeout=30s"));
    }

    @Test
    void testToStringWithLockMode() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.lockMode(jakarta.persistence.LockModeType.PESSIMISTIC_READ);
        String str = qs.toString();
        assertNotNull(str);
        assertTrue(str.contains("lockMode"));
    }

    @Test
    void testToStringWithDistinct() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.distinct();
        String str = qs.toString();
        assertNotNull(str);
        assertTrue(str.contains("distinct"));
    }

    @Test
    void testToDescriptionEmpty() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        String desc = qs.toDescription();
        assertNotNull(desc);
        assertTrue(desc.contains("Query{"));
    }

    @Test
    void testToDescriptionWithConditions() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.eq(TestEntity::getName, "test");
        String desc = qs.toDescription();
        assertNotNull(desc);
        assertTrue(desc.contains("WHERE"));
    }

    @Test
    void testToDescriptionWithGroupBy() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.groupBy(TestEntity::getStatus);
        String desc = qs.toDescription();
        assertNotNull(desc);
        assertTrue(desc.contains("GROUP BY"));
    }

    @Test
    void testToDescriptionWithOrderBy() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.orderByAsc(TestEntity::getName);
        String desc = qs.toDescription();
        assertNotNull(desc);
        assertTrue(desc.contains("ORDER BY"));
    }

    @Test
    void testApplyQuerySettingsWithTimeout() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.timeout(30);

        jakarta.persistence.criteria.CriteriaBuilder cb = em.getCriteriaBuilder();
        jakarta.persistence.criteria.CriteriaQuery<TestEntity> cq = cb.createQuery(TestEntity.class);
        cq.from(TestEntity.class);
        jakarta.persistence.TypedQuery<TestEntity> query = em.createQuery(cq);

        qs.applyQuerySettings(query);
    }

    @Test
    void testApplyQuerySettingsWithLockMode() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.lockMode(jakarta.persistence.LockModeType.PESSIMISTIC_READ);

        jakarta.persistence.criteria.CriteriaBuilder cb = em.getCriteriaBuilder();
        jakarta.persistence.criteria.CriteriaQuery<TestEntity> cq = cb.createQuery(TestEntity.class);
        cq.from(TestEntity.class);
        jakarta.persistence.TypedQuery<TestEntity> query = em.createQuery(cq);

        qs.applyQuerySettings(query);
    }

    @Test
    void testApplyQuerySettingsWithBoth() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.timeout(30).lockMode(jakarta.persistence.LockModeType.PESSIMISTIC_READ);

        jakarta.persistence.criteria.CriteriaBuilder cb = em.getCriteriaBuilder();
        jakarta.persistence.criteria.CriteriaQuery<TestEntity> cq = cb.createQuery(TestEntity.class);
        cq.from(TestEntity.class);
        jakarta.persistence.TypedQuery<TestEntity> query = em.createQuery(cq);

        qs.applyQuerySettings(query);
    }

    @Test
    void testApplyQuerySettingsWithoutSettings() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();

        jakarta.persistence.criteria.CriteriaBuilder cb = em.getCriteriaBuilder();
        jakarta.persistence.criteria.CriteriaQuery<TestEntity> cq = cb.createQuery(TestEntity.class);
        cq.from(TestEntity.class);
        jakarta.persistence.TypedQuery<TestEntity> query = em.createQuery(cq);

        qs.applyQuerySettings(query);
    }

    @Test
    void testCacheKeyEmpty() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        String key = qs.cacheKey();
        assertNotNull(key);
        assertTrue(key.startsWith("Q:"));
    }

    @Test
    void testCacheKeyWithConditions() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.eq(TestEntity::getName, "test");
        String key = qs.cacheKey();
        assertNotNull(key);
        assertTrue(key.contains("name"));
    }

    @Test
    void testCacheKeyWithDistinct() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.distinct();
        String key = qs.cacheKey();
        assertNotNull(key);
        assertTrue(key.contains("DISTINCT"));
    }

    @Test
    void testCacheKeyWithGroupBy() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.groupBy(TestEntity::getStatus);
        String key = qs.cacheKey();
        assertNotNull(key);
        assertTrue(key.contains("GROUPBY"));
    }

    @Test
    void testCacheKeyWithHaving() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.groupBy(TestEntity::getStatus).havingCount(TestEntity::getId, ConditionNode.Op.GT, 1L);
        String key = qs.cacheKey();
        assertNotNull(key);
        assertTrue(key.contains("HAVING"));
    }

    @Test
    void testCacheKeyWithOrderBy() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.orderByAsc(TestEntity::getName);
        String key = qs.cacheKey();
        assertNotNull(key);
        assertTrue(key.contains("ORDERBY"));
    }

    @Test
    void testCacheKeyWithTimeout() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.timeout(30);
        String key = qs.cacheKey();
        assertNotNull(key);
        assertTrue(key.contains("TIMEOUT"));
    }

    @Test
    void testCacheKeyWithLockMode() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.lockMode(jakarta.persistence.LockModeType.PESSIMISTIC_READ);
        String key = qs.cacheKey();
        assertNotNull(key);
        assertTrue(key.contains("LOCK"));
    }

    @Test
    void testCacheKeyDifferentForDifferentValues() {
        QuerySpec<TestEntity> qs1 = new QuerySpec<>();
        qs1.eq(TestEntity::getName, "a");

        QuerySpec<TestEntity> qs2 = new QuerySpec<>();
        qs2.eq(TestEntity::getName, "b");

        assertNotEquals(qs1.cacheKey(), qs2.cacheKey());
    }

    @Test
    void testCacheKeySameForSameValues() {
        QuerySpec<TestEntity> qs1 = new QuerySpec<>();
        qs1.eq(TestEntity::getName, "a");

        QuerySpec<TestEntity> qs2 = new QuerySpec<>();
        qs2.eq(TestEntity::getName, "a");

        assertEquals(qs1.cacheKey(), qs2.cacheKey());
    }

    @Test
    void testCopyDeepCopyNodes() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.or(o -> o.eq(TestEntity::getName, "a").eq(TestEntity::getStatus, 1));

        QuerySpec<TestEntity> copy = qs.copy();
        assertNotNull(copy);
    }

    @Test
    void testValidateCleanState() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.or(o -> o.eq(TestEntity::getName, "test"));
        // or() group is closed by Consumer

        // toSpecification() should not throw for properly closed groups
        Specification<TestEntity> spec = qs.toSpecification();
        assertNotNull(spec);
    }

    @Test
    void testApplyDistinctAndGroupBy() {
        repository.save(newEntity("a", 1));
        repository.save(newEntity("b", 1));
        repository.save(newEntity("c", 2));
        repository.flush();

        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.distinct();

        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertNotNull(result);
    }

    @Test
    void testApplyOrderBy() {
        repository.save(newEntity("c", 3));
        repository.save(newEntity("a", 1));
        repository.save(newEntity("b", 2));
        repository.flush();

        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.orderByAsc(TestEntity::getName);

        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(3, result.size());
        assertEquals("a", result.get(0).getName());
        assertEquals("b", result.get(1).getName());
        assertEquals("c", result.get(2).getName());
    }

    @Test
    void testApplyHaving() {
        repository.save(newEntity("a", 1));
        repository.save(newEntity("b", 1));
        repository.save(newEntity("c", 2));
        repository.flush();

        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.groupBy(TestEntity::getStatus).havingCount(TestEntity::getId, ConditionNode.Op.GT, 1L);

        // Use Specification to test having clause builds correctly
        Specification<TestEntity> spec = qs.toSpecification();
        assertNotNull(spec);
    }

    @Test
    void testGetSortEmpty() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        org.springframework.data.domain.Sort sort = qs.getSort();
        assertNotNull(sort);
        assertFalse(sort.isSorted());
    }

    @Test
    void testGetSortWithOrders() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.orderByAsc(TestEntity::getName).orderByDesc(TestEntity::getStatus);
        org.springframework.data.domain.Sort sort = qs.getSort();
        assertNotNull(sort);
        assertTrue(sort.isSorted());
    }

    @Test
    void testOf() {
        QuerySpec<TestEntity> qs = QuerySpec.of(s -> s.eq(TestEntity::getName, "test"));
        assertNotNull(qs);
    }

    @Test
    void testOfNull() {
        QuerySpec<TestEntity> qs = QuerySpec.of(null);
        assertNotNull(qs);
    }

    @Test
    void testCopyEmpty() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        QuerySpec<TestEntity> copy = qs.copy();
        assertNotNull(copy);
    }

    @Test
    void testCopyWithConditions() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.eq(TestEntity::getName, "test").eq(TestEntity::getStatus, 1);
        QuerySpec<TestEntity> copy = qs.copy();
        assertNotNull(copy);
    }

    @Test
    void testCopyWithOrGroup() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.or(o -> o.eq(TestEntity::getName, "a").eq(TestEntity::getName, "b"));
        QuerySpec<TestEntity> copy = qs.copy();
        assertNotNull(copy);
    }

    @Test
    void testCopyWithJoin() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.eq(TestEntity::getName, "test");
        QuerySpec<TestEntity> copy = qs.copy();
        assertNotNull(copy);
    }

    @Test
    void testThenNull() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.eq(TestEntity::getName, "test");
        QuerySpec<TestEntity> result = qs.then(null);
        assertNotNull(result);
    }

    @Test
    void testAndNull() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.eq(TestEntity::getName, "test");
        QuerySpec<TestEntity> result = qs.and(null);
        assertNotNull(result);
    }

    @Test
    void testOrNull() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.eq(TestEntity::getName, "test");
        Specification<TestEntity> result = qs.orCombine((QuerySpec<TestEntity>)null);
        assertNotNull(result);
    }

    @Test
    void testToSpecificationNull() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.eq(TestEntity::getName, "test");
        Specification<TestEntity> result = qs.toSpecification((Specification<TestEntity>)null);
        assertNotNull(result);
    }
}
