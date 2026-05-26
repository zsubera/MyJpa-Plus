package com.zsubera.jpa.spec;

import static org.junit.jupiter.api.Assertions.*;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

@DataJpaTest
class JoinGroupTest {

  @Autowired private TestEntityRepository repository;

  @PersistenceContext private EntityManager em;

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
    JoinGroup<TestEntity, ParentEntity> jg = qs.join(TestEntity::getParent);
    jg.eq(ParentEntity::getCategory, "admin");
    jg.eq(ParentEntity::getLevel, 10);
    jg.endJoin();

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
    JoinGroup<TestEntity, ParentEntity> jg = qs.join(TestEntity::getParent);
    jg.gt(ParentEntity::getLevel, 5);
    jg.endJoin();

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
    JoinGroup<TestEntity, ParentEntity> jg = qs.join(TestEntity::getParent);
    jg.ge(ParentEntity::getLevel, 10);
    jg.endJoin();

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
    JoinGroup<TestEntity, ParentEntity> jg = qs.join(TestEntity::getParent);
    jg.lt(ParentEntity::getLevel, 5);
    jg.endJoin();

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
    JoinGroup<TestEntity, ParentEntity> jg = qs.join(TestEntity::getParent);
    jg.le(ParentEntity::getLevel, 3);
    jg.endJoin();

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
    JoinGroup<TestEntity, ParentEntity> jg = qs.join(TestEntity::getParent);
    jg.like(ParentEntity::getCategory, "admin%");
    jg.endJoin();

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
    JoinGroup<TestEntity, ParentEntity> jg = qs.join(TestEntity::getParent);
    jg.ne(ParentEntity::getCategory, "user");
    jg.endJoin();

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
    JoinGroup<TestEntity, ParentEntity> jg = qs.join(TestEntity::getParent);
    jg.in(ParentEntity::getCategory, "admin", "moderator");
    jg.endJoin();

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
    JoinGroup<TestEntity, ParentEntity> jg = qs.join(TestEntity::getParent);
    jg.between(ParentEntity::getLevel, 3, 7);
    jg.endJoin();

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
    JoinGroup<TestEntity, ParentEntity> jg = qs.join(TestEntity::getParent);
    jg.isNull(ParentEntity::getCategory);
    jg.endJoin();

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
    JoinGroup<TestEntity, ParentEntity> jg = qs.join(TestEntity::getParent);
    jg.isNotNull(ParentEntity::getCategory);
    jg.endJoin();

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
    JoinGroup<TestEntity, ParentEntity> jg = qs.join(TestEntity::getParent);
    jg.startsWith(ParentEntity::getCategory, "adm");
    jg.endJoin();

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
    JoinGroup<TestEntity, ParentEntity> jg = qs.join(TestEntity::getParent);
    jg.endsWith(ParentEntity::getCategory, "ator");
    jg.endJoin();

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
    JoinGroup<TestEntity, ParentEntity> jg = qs.join(TestEntity::getParent);
    jg.contains(ParentEntity::getCategory, "inis");
    jg.endJoin();

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
    JoinGroup<TestEntity, ParentEntity> jg = qs.join(TestEntity::getParent);
    jg.eqIgnoreCase(ParentEntity::getCategory, "ADMIN");
    jg.endJoin();

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
    JoinGroup<TestEntity, ParentEntity> jg = qs.join(TestEntity::getParent);
    jg.or(
        oj -> oj.eq(ParentEntity::getCategory, "admin").eq(ParentEntity::getCategory, "moderator"));
    jg.endJoin();

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
