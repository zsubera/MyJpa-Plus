package com.zsubera.jpa.spec;

import static org.junit.jupiter.api.Assertions.*;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

@DataJpaTest
class SubQuerySpecTest {

  @Autowired private TestEntityRepository repository;

  @Autowired private ParentEntityRepository parentRepository;

  @PersistenceContext private EntityManager em;

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
    qs.exists(TestEntity.class, sub -> sub.like(TestEntity::getName, "%ell%"));
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
    qs.exists(TestEntity.class, sub -> sub.notLike(TestEntity::getName, "%xyz%"));
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
    qs.exists(TestEntity.class, sub -> sub.contains(TestEntity::getName, "ell"));
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
    qs.exists(TestEntity.class, sub -> sub.likeIgnoreCase(TestEntity::getName, "%hello%"));
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
    qs.exists(
        TestEntity.class,
        sub -> {
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
    qs.exists(
        TestEntity.class,
        sub -> sub.where(r -> em.getCriteriaBuilder().equal(r.get("name"), "c1")));
    List<ParentEntity> result = parentRepository.findAll(qs.toSpecification());
    assertEquals(1, result.size());
  }

  @Test
  void testExistsWithCorrelatedRoot() {
    ParentEntity p = new ParentEntity();
    p.setCategory("admin");
    p.setLevel(10);
    em.persist(p);
    TestEntity child = newEntity("c1", 0);
    child.setParent(p);
    repository.save(child);

    QuerySpec<ParentEntity> qs = new QuerySpec<>();
    qs.exists(
        TestEntity.class,
        sub ->
            sub.where(
                r -> em.getCriteriaBuilder().equal(r.get("parent").get("category"), "admin")));
    List<ParentEntity> result = parentRepository.findAll(qs.toSpecification());
    assertEquals(1, result.size());
  }

  @Test
  void testSubQueryNullValueThrowsException() {
    QuerySpec<ParentEntity> qs = new QuerySpec<>();
    qs.exists(TestEntity.class, sub -> sub.gt(TestEntity::getStatus, null));
    RuntimeException ex =
        assertThrows(RuntimeException.class, () -> parentRepository.findAll(qs.toSpecification()));
    assertTrue(
        ex.getCause() instanceof IllegalArgumentException
            || ex instanceof IllegalArgumentException);
  }

  @Test
  void testSubQueryLikeNullValueThrowsException() {
    QuerySpec<ParentEntity> qs = new QuerySpec<>();
    qs.exists(TestEntity.class, sub -> sub.like(TestEntity::getName, null));
    RuntimeException ex =
        assertThrows(RuntimeException.class, () -> parentRepository.findAll(qs.toSpecification()));
    assertTrue(
        ex.getCause() instanceof IllegalArgumentException
            || ex instanceof IllegalArgumentException);
  }

  @Test
  void testSubQueryBetweenNullStartThrowsException() {
    QuerySpec<ParentEntity> qs = new QuerySpec<>();
    qs.exists(TestEntity.class, sub -> sub.between(TestEntity::getStatus, null, 5));
    RuntimeException ex =
        assertThrows(RuntimeException.class, () -> parentRepository.findAll(qs.toSpecification()));
    assertTrue(
        ex.getCause() instanceof IllegalArgumentException
            || ex instanceof IllegalArgumentException);
  }

  @Test
  void testSubQueryBetweenInvalidRangeThrowsException() {
    QuerySpec<ParentEntity> qs = new QuerySpec<>();
    qs.exists(TestEntity.class, sub -> sub.between(TestEntity::getStatus, 10, 1));
    RuntimeException ex =
        assertThrows(RuntimeException.class, () -> parentRepository.findAll(qs.toSpecification()));
    assertTrue(
        ex.getCause() instanceof IllegalArgumentException
            || ex instanceof IllegalArgumentException);
  }

  @Test
  void testSubQueryLikeIgnoreCaseNullValueThrowsException() {
    QuerySpec<ParentEntity> qs = new QuerySpec<>();
    qs.exists(TestEntity.class, sub -> sub.likeIgnoreCase(TestEntity::getName, null));
    RuntimeException ex =
        assertThrows(RuntimeException.class, () -> parentRepository.findAll(qs.toSpecification()));
    assertTrue(
        ex.getCause() instanceof IllegalArgumentException
            || ex instanceof IllegalArgumentException);
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
    assertTrue(
        ex.getCause() instanceof IllegalArgumentException
            || ex instanceof IllegalArgumentException);
  }

  @Test
  void testSubQueryNotBetweenInvalidRangeThrowsException() {
    QuerySpec<ParentEntity> qs = new QuerySpec<>();
    qs.exists(TestEntity.class, sub -> sub.notBetween(TestEntity::getStatus, 10, 1));
    RuntimeException ex =
        assertThrows(RuntimeException.class, () -> parentRepository.findAll(qs.toSpecification()));
    assertTrue(
        ex.getCause() instanceof IllegalArgumentException
            || ex instanceof IllegalArgumentException);
  }

  @Test
  void testExistsWithCorrelatedEq() {
    ParentEntity p = new ParentEntity();
    p.setCategory("admin");
    p.setLevel(10);
    em.persist(p);
    TestEntity child = newEntity("child", 5);
    child.setParent(p);
    repository.save(child);

    QuerySpec<ParentEntity> qs = new QuerySpec<>();
    qs.exists(
        TestEntity.class,
        sub ->
            sub.where(
                    r ->
                        em.getCriteriaBuilder()
                            .equal(
                                r.get("parent").get("id"),
                                sub.<ParentEntity>correlated().get("id")))
                .gt(TestEntity::getStatus, 3));
    List<ParentEntity> result = parentRepository.findAll(qs.toSpecification());
    assertEquals(1, result.size());
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
    qs.exists(
        TestEntity.class,
        sub -> sub.notIn(TestEntity::getStatus, java.util.Arrays.asList(99, 100)));
    List<ParentEntity> result = parentRepository.findAll(qs.toSpecification());
    assertEquals(1, result.size());
  }

  @Test
  void testExistsWithIsEmptyOperator() {
    ParentEntity p = new ParentEntity();
    p.setCategory("empty");
    p.setLevel(1);
    em.persist(p);

    ParentEntity q = new ParentEntity();
    q.setCategory("hasChild");
    q.setLevel(2);
    em.persist(q);
    TestEntity child = newEntity("kid", 0);
    child.setParent(q);
    repository.save(child);
    em.flush();

    QuerySpec<ParentEntity> qs = new QuerySpec<>();
    qs.exists(
        ParentEntity.class,
        sub ->
            sub.where(
                    r ->
                        em.getCriteriaBuilder()
                            .equal(r.get("id"), sub.<ParentEntity>correlated().get("id")))
                .isEmpty(ParentEntity::getChildren));
    List<ParentEntity> result = parentRepository.findAll(qs.toSpecification());
    assertEquals(1, result.size());
    assertEquals("empty", result.get(0).getCategory());
  }

  @Test
  void testExistsWithIsNotEmptyOperator() {
    ParentEntity p = new ParentEntity();
    p.setCategory("empty");
    p.setLevel(1);
    em.persist(p);

    ParentEntity q = new ParentEntity();
    q.setCategory("hasChild");
    q.setLevel(2);
    em.persist(q);
    TestEntity child = newEntity("kid", 0);
    child.setParent(q);
    repository.save(child);
    em.flush();

    QuerySpec<ParentEntity> qs = new QuerySpec<>();
    qs.exists(
        ParentEntity.class,
        sub ->
            sub.where(
                    r ->
                        em.getCriteriaBuilder()
                            .equal(r.get("id"), sub.<ParentEntity>correlated().get("id")))
                .isNotEmpty(ParentEntity::getChildren));
    List<ParentEntity> result = parentRepository.findAll(qs.toSpecification());
    assertEquals(1, result.size());
    assertEquals("hasChild", result.get(0).getCategory());
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
    qs.exists(TestEntity.class, sub -> sub.in(TestEntity::getStatus, (Object[]) null));
    RuntimeException ex =
        assertThrows(RuntimeException.class, () -> parentRepository.findAll(qs.toSpecification()));
    assertTrue(
        ex.getCause() instanceof IllegalArgumentException
            || ex instanceof IllegalArgumentException);
  }

  @Test
  void testSubQueryNotInNullValuesThrowsException() {
    QuerySpec<ParentEntity> qs = new QuerySpec<>();
    qs.exists(TestEntity.class, sub -> sub.notIn(TestEntity::getStatus, (Object[]) null));
    RuntimeException ex =
        assertThrows(RuntimeException.class, () -> parentRepository.findAll(qs.toSpecification()));
    assertTrue(
        ex.getCause() instanceof IllegalArgumentException
            || ex instanceof IllegalArgumentException);
  }

  private TestEntity newEntity(String name, int status) {
    TestEntity entity = new TestEntity();
    entity.setName(name);
    entity.setStatus(status);
    return entity;
  }
}
