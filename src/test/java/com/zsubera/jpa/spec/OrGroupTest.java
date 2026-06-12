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
class OrGroupTest {

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
    void testOrGroupGtOperator() {
        repository.save(newEntity("a", 1));
        repository.save(newEntity("b", 10));
        repository.save(newEntity("c", 3));
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.or(g -> g.gt(TestEntity::getStatus, 5).eq(TestEntity::getName, "a"));
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(2, result.size());
    }

    @Test
    void testOrGroupGeOperator() {
        repository.save(newEntity("a", 5));
        repository.save(newEntity("b", 10));
        repository.save(newEntity("c", 3));
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.or(g -> g.ge(TestEntity::getStatus, 5));
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(2, result.size());
    }

    @Test
    void testOrGroupLtOperator() {
        repository.save(newEntity("a", 1));
        repository.save(newEntity("b", 10));
        repository.save(newEntity("c", 3));
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.or(g -> g.lt(TestEntity::getStatus, 5));
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(2, result.size());
    }

    @Test
    void testOrGroupLeOperator() {
        repository.save(newEntity("a", 5));
        repository.save(newEntity("b", 10));
        repository.save(newEntity("c", 3));
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.or(g -> g.le(TestEntity::getStatus, 5));
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(2, result.size());
    }

    @Test
    void testOrGroupStartsWithWithSetup() {
        repository.save(newEntity("hello", 0));
        repository.save(newEntity("world", 0));
        repository.save(newEntity("help", 0));
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.or(g -> g.startsWith(TestEntity::getName, "hel"));
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(2, result.size());
    }

    @Test
    void testOrGroupNotLikeOperator() {
        repository.save(newEntity("hello", 0));
        repository.save(newEntity("world", 0));
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.or(g -> g.notLike(TestEntity::getName, "hello"));
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
        assertEquals("world", result.get(0).getName());
    }

    @Test
    void testOrGroupEndsWithOperator() {
        repository.save(newEntity("ending", 0));
        repository.save(newEntity("pending", 0));
        repository.save(newEntity("start", 0));
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.or(g -> g.endsWith(TestEntity::getName, "ing"));
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(2, result.size());
    }

    @Test
    void testOrGroupLikeOperator() {
        repository.save(newEntity("abc", 0));
        repository.save(newEntity("xabcx", 0));
        repository.save(newEntity("xyz", 0));
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.or(g -> g.like(TestEntity::getName, "ab"));
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(2, result.size());
    }

    @Test
    void testOrGroupInOperator() {
        repository.save(newEntity("a", 1));
        repository.save(newEntity("b", 2));
        repository.save(newEntity("c", 3));
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.or(g -> g.in(TestEntity::getStatus, 1, 3));
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(2, result.size());
    }

    @Test
    void testOrGroupNotInOperator() {
        repository.save(newEntity("a", 1));
        repository.save(newEntity("b", 2));
        repository.save(newEntity("c", 3));
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.or(g -> g.notIn(TestEntity::getStatus, 1, 3));
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
        assertEquals(2, result.get(0).getStatus());
    }

    @Test
    void testOrGroupBetweenOperator() {
        repository.save(newEntity("a", 1));
        repository.save(newEntity("b", 5));
        repository.save(newEntity("c", 10));
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.or(g -> g.between(TestEntity::getStatus, 3, 7));
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
        assertEquals(5, result.get(0).getStatus());
    }

    @Test
    void testOrGroupIsNullOperator() {
        repository.save(newEntity("hasName", 0));
        TestEntity nullName = new TestEntity();
        nullName.setName(null);
        nullName.setStatus(99);
        repository.save(nullName);
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.or(g -> g.isNull(TestEntity::getName));
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
        assertNull(result.get(0).getName());
    }

    @Test
    void testOrGroupIsNotNullOperator() {
        repository.save(newEntity("hasName", 0));
        TestEntity nullName = new TestEntity();
        nullName.setName(null);
        nullName.setStatus(99);
        repository.save(nullName);
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.or(g -> g.isNotNull(TestEntity::getName));
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
        assertEquals("hasName", result.get(0).getName());
    }

    @Test
    void testOrGroupEqIgnoreCaseOperator() {
        repository.save(newEntity("Hello", 1));
        repository.save(newEntity("hello", 2));
        repository.save(newEntity("WORLD", 3));
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.or(g -> g.eqIgnoreCase(TestEntity::getName, "HELLO"));
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(2, result.size());
    }

    @Test
    void testOrGroupLikeIgnoreCaseOperator() {
        repository.save(newEntity("HelloWorld", 1));
        repository.save(newEntity("HELLO", 2));
        repository.save(newEntity("xyz", 3));
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.or(g -> g.likeIgnoreCase(TestEntity::getName, "hello"));
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(2, result.size());
    }

    @Test
    void testOrGroupMultiLikeOperator() {
        repository.save(newEntity("hello", 0));
        repository.save(newEntity("world", 0));
        repository.save(newEntity("help", 0));
        repository.save(newEntity("welcome", 0));
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.or(g -> g.multiLike("hel", TestEntity::getName));
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(2, result.size());
    }

    @Test
    void testOrGroupNestedOr() {
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
    void testOrGroupWithJoin() {
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
        qs.or(outer -> outer.eq(TestEntity::getStatus, 99).<ParentEntity>join(TestEntity::getParent,
            j -> j.eq(ParentEntity::getCategory, "admin")));
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
    }

    @Test
    void testOrGroupWithLeftJoin() {
        ParentEntity admin = new ParentEntity();
        admin.setCategory("admin");
        admin.setLevel(10);
        em.persist(admin);
        TestEntity orphan = newEntity("orphan", 0);
        orphan.setParent(null);
        repository.save(orphan);
        TestEntity child = newEntity("child", 0);
        child.setParent(admin);
        repository.save(child);

        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.or(og -> {
            og.<ParentEntity>leftJoin(TestEntity::getParent, jg -> {
                jg.or(ojg -> {
                    ojg.eq(ParentEntity::getCategory, "admin");
                    ojg.isNull(ParentEntity::getCategory);
                });
            });
        });

        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(2, result.size());
    }

    @Test
    void testOrGroupLike() {
        repository.save(newEntity("hello", 0));
        repository.save(newEntity("world", 0));
        // like() wraps with % on both sides
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.or(g -> g.like(TestEntity::getName, "hel"));
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        // like("hel") matches "hello" (contains "hel" substring)
        assertEquals(1, result.size());
    }

    @Test
    void testOrGroupLikeEscapesWildcard() {
        repository.save(newEntity("100%", 0));
        repository.save(newEntity("100x", 0));
        // like() escapes wildcards
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.or(g -> g.like(TestEntity::getName, "100%"));
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
        assertEquals("100%", result.get(0).getName());
    }

    @Test
    void testOrGroupJoinConsumer() {
        ParentEntity admin = new ParentEntity();
        admin.setCategory("admin");
        admin.setLevel(10);
        em.persist(admin);
        TestEntity child = newEntity("child", 0);
        child.setParent(admin);
        repository.save(child);

        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.or(og -> og.<ParentEntity>join(TestEntity::getParent, j -> j.eq(ParentEntity::getCategory, "admin")));
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
    }

    @Test
    void testOrGroupLeftJoinConsumer() {
        ParentEntity admin = new ParentEntity();
        admin.setCategory("admin");
        admin.setLevel(10);
        em.persist(admin);
        TestEntity orphan = newEntity("orphan", 0);
        orphan.setParent(null);
        repository.save(orphan);
        TestEntity child = newEntity("child", 0);
        child.setParent(admin);
        repository.save(child);

        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.or(og -> og.<ParentEntity>leftJoin(TestEntity::getParent,
            j -> j.or(oj -> oj.eq(ParentEntity::getCategory, "admin").isNull(ParentEntity::getCategory))));
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(2, result.size());
    }

    @Test
    void testOrGroupNotBetween() {
        repository.save(newEntity("a", 1));
        repository.save(newEntity("b", 5));
        repository.save(newEntity("c", 10));
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.or(g -> g.notBetween(TestEntity::getStatus, 3, 7));
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(2, result.size());
    }

    private TestEntity newEntity(String name, int status) {
        TestEntity entity = new TestEntity();
        entity.setName(name);
        entity.setStatus(status);
        return entity;
    }
}
