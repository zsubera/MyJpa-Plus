package com.zsubera.jpa.spec;

import static org.junit.jupiter.api.Assertions.*;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

@DataJpaTest
class OrGroupTest {

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
        qs.or().gt(TestEntity::getStatus, 5).eq(TestEntity::getName, "a").endOr();
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(2, result.size());
    }

    @Test
    void testOrGroupGeOperator() {
        repository.save(newEntity("a", 5));
        repository.save(newEntity("b", 10));
        repository.save(newEntity("c", 3));
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.or().ge(TestEntity::getStatus, 5).endOr();
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(2, result.size());
    }

    @Test
    void testOrGroupLtOperator() {
        repository.save(newEntity("a", 1));
        repository.save(newEntity("b", 10));
        repository.save(newEntity("c", 3));
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.or().lt(TestEntity::getStatus, 5).endOr();
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(2, result.size());
    }

    @Test
    void testOrGroupLeOperator() {
        repository.save(newEntity("a", 5));
        repository.save(newEntity("b", 10));
        repository.save(newEntity("c", 3));
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.or().le(TestEntity::getStatus, 5).endOr();
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(2, result.size());
    }

    @Test
    void testOrGroupLikeOperator() {
        repository.save(newEntity("hello", 0));
        repository.save(newEntity("world", 0));
        repository.save(newEntity("help", 0));
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.or().like(TestEntity::getName, "hel%").endOr();
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(2, result.size());
    }

    @Test
    void testOrGroupNotLikeOperator() {
        repository.save(newEntity("hello", 0));
        repository.save(newEntity("world", 0));
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.or().notLike(TestEntity::getName, "%hello%").endOr();
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
        assertEquals("world", result.get(0).getName());
    }

    @Test
    void testOrGroupStartsWithOperator() {
        repository.save(newEntity("hello", 0));
        repository.save(newEntity("help", 0));
        repository.save(newEntity("world", 0));
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.or().startsWith(TestEntity::getName, "hel").endOr();
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(2, result.size());
    }

    @Test
    void testOrGroupEndsWithOperator() {
        repository.save(newEntity("ending", 0));
        repository.save(newEntity("pending", 0));
        repository.save(newEntity("start", 0));
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.or().endsWith(TestEntity::getName, "ing").endOr();
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(2, result.size());
    }

    @Test
    void testOrGroupContainsOperator() {
        repository.save(newEntity("abc", 0));
        repository.save(newEntity("xabcx", 0));
        repository.save(newEntity("xyz", 0));
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.or().contains(TestEntity::getName, "ab").endOr();
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(2, result.size());
    }

    @Test
    void testOrGroupInOperator() {
        repository.save(newEntity("a", 1));
        repository.save(newEntity("b", 2));
        repository.save(newEntity("c", 3));
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.or().in(TestEntity::getStatus, 1, 3).endOr();
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(2, result.size());
    }

    @Test
    void testOrGroupNotInOperator() {
        repository.save(newEntity("a", 1));
        repository.save(newEntity("b", 2));
        repository.save(newEntity("c", 3));
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.or().notIn(TestEntity::getStatus, 1, 3).endOr();
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
        qs.or().between(TestEntity::getStatus, 3, 7).endOr();
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
        qs.or().isNull(TestEntity::getName).endOr();
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
        qs.or().isNotNull(TestEntity::getName).endOr();
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
        qs.or().eqIgnoreCase(TestEntity::getName, "HELLO").endOr();
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(2, result.size());
    }

    @Test
    void testOrGroupLikeIgnoreCaseOperator() {
        repository.save(newEntity("HelloWorld", 1));
        repository.save(newEntity("HELLO", 2));
        repository.save(newEntity("xyz", 3));
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.or().likeIgnoreCase(TestEntity::getName, "%hello%").endOr();
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
        qs.or().multiLike("hel", TestEntity::getName).endOr();
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
        qs.or(outer -> outer.eq(TestEntity::getStatus, 99)
                .<ParentEntity>join(TestEntity::getParent, j -> j.eq(ParentEntity::getCategory, "admin")));
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
        OrGroup<TestEntity> og = qs.or();
        JoinGroup<TestEntity, ParentEntity> jg = og.leftJoin(TestEntity::getParent);
        OrJoinGroup<TestEntity, ParentEntity> ojg = jg.or();
        ojg.eq(ParentEntity::getCategory, "admin");
        ojg.isNull(ParentEntity::getCategory);
        jg = ojg.endOr();
        jg.endJoin();
        og.endOr();

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
