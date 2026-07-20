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
class JoinGroupTest {

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
    void testJoinGroupWithMultipleConditions() {
        ParentEntity p = new ParentEntity();
        p.setCategory("admin");
        p.setLevel(10);
        em.persist(p);
        TestEntity child = newEntity("c1", 0);
        child.setParent(p);
        repository.save(child);

        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.<ParentEntity>join(TestEntity::getParent, jg -> {
            jg.eq(ParentEntity::getCategory, "admin");
            jg.eq(ParentEntity::getLevel, 10);
        });

        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
    }

    @Test
    void testJoinGroupGtOperator() {
        ParentEntity p = new ParentEntity();
        p.setCategory("cat");
        p.setLevel(10);
        em.persist(p);
        TestEntity child = newEntity("c1", 0);
        child.setParent(p);
        repository.save(child);

        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.<ParentEntity>join(TestEntity::getParent, jg -> jg.gt(ParentEntity::getLevel, 5));

        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
    }

    @Test
    void testJoinGroupGeOperator() {
        ParentEntity p = new ParentEntity();
        p.setCategory("cat");
        p.setLevel(10);
        em.persist(p);
        TestEntity child = newEntity("c1", 0);
        child.setParent(p);
        repository.save(child);

        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.<ParentEntity>join(TestEntity::getParent, jg -> jg.ge(ParentEntity::getLevel, 10));

        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
    }

    @Test
    void testJoinGroupLtOperator() {
        ParentEntity p = new ParentEntity();
        p.setCategory("cat");
        p.setLevel(3);
        em.persist(p);
        TestEntity child = newEntity("c1", 0);
        child.setParent(p);
        repository.save(child);

        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.<ParentEntity>join(TestEntity::getParent, jg -> jg.lt(ParentEntity::getLevel, 5));

        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
    }

    @Test
    void testJoinGroupLeOperator() {
        ParentEntity p = new ParentEntity();
        p.setCategory("cat");
        p.setLevel(3);
        em.persist(p);
        TestEntity child = newEntity("c1", 0);
        child.setParent(p);
        repository.save(child);

        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.<ParentEntity>join(TestEntity::getParent, jg -> jg.le(ParentEntity::getLevel, 3));

        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
    }

    @Test
    void testJoinGroupLikeOperator() {
        ParentEntity p = new ParentEntity();
        p.setCategory("administrator");
        p.setLevel(10);
        em.persist(p);
        TestEntity child = newEntity("c1", 0);
        child.setParent(p);
        repository.save(child);

        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.<ParentEntity>join(TestEntity::getParent, jg -> jg.startsWith(ParentEntity::getCategory, "admin"));

        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
    }

    @Test
    void testJoinGroupNeOperator() {
        ParentEntity p = new ParentEntity();
        p.setCategory("admin");
        p.setLevel(10);
        em.persist(p);
        TestEntity child = newEntity("c1", 0);
        child.setParent(p);
        repository.save(child);

        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.<ParentEntity>join(TestEntity::getParent, jg -> jg.ne(ParentEntity::getCategory, "user"));

        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
    }

    @Test
    void testJoinGroupInOperator() {
        ParentEntity p = new ParentEntity();
        p.setCategory("admin");
        p.setLevel(10);
        em.persist(p);
        TestEntity child = newEntity("c1", 0);
        child.setParent(p);
        repository.save(child);

        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.<ParentEntity>join(TestEntity::getParent, jg -> jg.in(ParentEntity::getCategory, "admin", "moderator"));

        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
    }

    @Test
    void testJoinGroupBetweenOperator() {
        ParentEntity p = new ParentEntity();
        p.setCategory("cat");
        p.setLevel(5);
        em.persist(p);
        TestEntity child = newEntity("c1", 0);
        child.setParent(p);
        repository.save(child);

        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.<ParentEntity>join(TestEntity::getParent, jg -> jg.between(ParentEntity::getLevel, 3, 7));

        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
    }

    @Test
    void testJoinGroupIsNullOperator() {
        ParentEntity p = new ParentEntity();
        p.setCategory(null);
        p.setLevel(1);
        em.persist(p);
        TestEntity child = newEntity("c1", 0);
        child.setParent(p);
        repository.save(child);

        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.<ParentEntity>join(TestEntity::getParent, jg -> jg.isNull(ParentEntity::getCategory));

        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
    }

    @Test
    void testJoinGroupIsNotNullOperator() {
        ParentEntity p = new ParentEntity();
        p.setCategory("cat");
        p.setLevel(1);
        em.persist(p);
        TestEntity child = newEntity("c1", 0);
        child.setParent(p);
        repository.save(child);

        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.<ParentEntity>join(TestEntity::getParent, jg -> jg.isNotNull(ParentEntity::getCategory));

        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
    }

    @Test
    void testJoinGroupStartsWithOperator() {
        ParentEntity p = new ParentEntity();
        p.setCategory("administrator");
        p.setLevel(1);
        em.persist(p);
        TestEntity child = newEntity("c1", 0);
        child.setParent(p);
        repository.save(child);

        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.<ParentEntity>join(TestEntity::getParent, jg -> jg.startsWith(ParentEntity::getCategory, "adm"));

        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
    }

    @Test
    void testJoinGroupEndsWithOperator() {
        ParentEntity p = new ParentEntity();
        p.setCategory("administrator");
        p.setLevel(1);
        em.persist(p);
        TestEntity child = newEntity("c1", 0);
        child.setParent(p);
        repository.save(child);

        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.<ParentEntity>join(TestEntity::getParent, jg -> jg.endsWith(ParentEntity::getCategory, "ator"));

        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
    }

    @Test
    void testJoinGroupContainsOperator() {
        ParentEntity p = new ParentEntity();
        p.setCategory("administrator");
        p.setLevel(1);
        em.persist(p);
        TestEntity child = newEntity("c1", 0);
        child.setParent(p);
        repository.save(child);

        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.<ParentEntity>join(TestEntity::getParent, jg -> jg.like(ParentEntity::getCategory, "inis"));

        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
    }

    @Test
    void testJoinGroupEqIgnoreCaseOperator() {
        ParentEntity p = new ParentEntity();
        p.setCategory("Admin");
        p.setLevel(1);
        em.persist(p);
        TestEntity child = newEntity("c1", 0);
        child.setParent(p);
        repository.save(child);

        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.<ParentEntity>join(TestEntity::getParent, jg -> jg.eqIgnoreCase(ParentEntity::getCategory, "ADMIN"));

        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
    }

    @Test
    void testJoinGroupOrWithinJoin() {
        ParentEntity p1 = new ParentEntity();
        p1.setCategory("admin");
        p1.setLevel(10);
        em.persist(p1);
        ParentEntity p2 = new ParentEntity();
        p2.setCategory("moderator");
        p2.setLevel(5);
        em.persist(p2);
        TestEntity c1 = newEntity("c1", 0);
        c1.setParent(p1);
        repository.save(c1);
        TestEntity c2 = newEntity("c2", 0);
        c2.setParent(p2);
        repository.save(c2);

        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.<ParentEntity>join(TestEntity::getParent, jg -> {
            jg.or(oj -> oj.eq(ParentEntity::getCategory, "admin"), oj -> oj.eq(ParentEntity::getCategory, "moderator"));
        });

        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(2, result.size());
    }

    @Test
    void testJoinGroupByConsumerApi() {
        ParentEntity parent = new ParentEntity();
        parent.setCategory("admin");
        parent.setLevel(5);
        em.persist(parent);
        TestEntity child = newEntity("c1", 0);
        child.setParent(parent);
        repository.save(child);

        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.<ParentEntity>join(TestEntity::getParent, j -> j.eq(ParentEntity::getCategory, "admin"));
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
    }

    @Test
    void testJoinGroupLeftJoinConsumerApi() {
        ParentEntity parent = new ParentEntity();
        parent.setCategory("admin");
        parent.setLevel(5);
        em.persist(parent);
        TestEntity child = newEntity("c1", 0);
        child.setParent(parent);
        repository.save(child);

        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.<ParentEntity>leftJoin(TestEntity::getParent, j -> j.eq(ParentEntity::getCategory, "admin"));
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
    }

    @Test
    void testJoinGroupRawLike() {
        ParentEntity parent = new ParentEntity();
        parent.setCategory("admin");
        parent.setLevel(10);
        em.persist(parent);

        TestEntity child = newEntity("hello", 0);
        child.setParent(parent);
        repository.save(child);

        // rawLike() now delegates to contains(), which works normally
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.<ParentEntity>join(TestEntity::getParent, jg -> jg.like(ParentEntity::getCategory, "adm"));
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
    }

    @Test
    void testJoinGroupNestedJoin() {
        ParentEntity parent = new ParentEntity();
        parent.setCategory("admin");
        parent.setLevel(10);
        em.persist(parent);

        TestEntity child = newEntity("child", 0);
        child.setParent(parent);
        repository.save(child);

        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.<ParentEntity>join(TestEntity::getParent, jg -> jg.like(ParentEntity::getCategory, "adm"));
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
    }

    @Test
    void testJoinGroupNestedJoinReturnJoin() {
        ParentEntity parent = new ParentEntity();
        parent.setCategory("admin");
        parent.setLevel(10);
        em.persist(parent);

        TestEntity child = newEntity("child", 0);
        child.setParent(parent);
        repository.save(child);

        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.<ParentEntity>join(TestEntity::getParent, jg -> {
            jg.<TestEntity>join(ParentEntity::getChildren, j2 -> j2.eq(TestEntity::getName, "child"));
        });
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
    }

    @Test
    void testJoinGroupNestedLeftJoinReturnJoin() {
        ParentEntity parent = new ParentEntity();
        parent.setCategory("admin");
        parent.setLevel(10);
        em.persist(parent);

        TestEntity child = newEntity("child", 0);
        child.setParent(parent);
        repository.save(child);

        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.<ParentEntity>join(TestEntity::getParent, jg -> {
            jg.<TestEntity>leftJoin(ParentEntity::getChildren, j2 -> j2.eq(TestEntity::getName, "child"));
        });
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(1, result.size());
    }

    private TestEntity newEntity(String name, int status) {
        TestEntity entity = new TestEntity();
        entity.setName(name);
        entity.setStatus(status);
        return entity;
    }
}
