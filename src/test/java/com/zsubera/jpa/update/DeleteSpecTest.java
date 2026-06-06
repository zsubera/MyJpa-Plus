package com.zsubera.jpa.update;

import static org.junit.jupiter.api.Assertions.*;

import com.zsubera.jpa.spec.TestApplication;
import com.zsubera.jpa.spec.TestEntity;
import com.zsubera.jpa.spec.TestEntityRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ContextConfiguration;

@DataJpaTest
@ContextConfiguration(classes = TestApplication.class)
class DeleteSpecTest {

    @Autowired
    private TestEntityRepository repository;

    @PersistenceContext
    private EntityManager em;

    @Test
    void testDeleteByEqCondition() {
        repository.save(newEntity("deleteMe", 1));
        repository.save(newEntity("keepMe", 1));

        int count = new DeleteSpec<>(TestEntity.class).eq(TestEntity::getName, "deleteMe").execute(em);

        assertEquals(1, count);
        List<TestEntity> all = repository.findAll();
        assertEquals(1, all.size());
        assertEquals("keepMe", all.get(0).getName());
    }

    @Test
    void testDeleteByNeCondition() {
        repository.save(newEntity("a", 1));
        repository.save(newEntity("b", 2));

        int count = new DeleteSpec<>(TestEntity.class).ne(TestEntity::getName, "a").execute(em);

        assertEquals(1, count);
        assertEquals("a", repository.findAll().get(0).getName());
    }

    @Test
    void testDeleteByGtCondition() {
        repository.save(newEntity("low", 1));
        repository.save(newEntity("high", 10));

        int count = new DeleteSpec<>(TestEntity.class).gt(TestEntity::getStatus, 5).execute(em);

        assertEquals(1, count);
        assertEquals("low", repository.findAll().get(0).getName());
    }

    @Test
    void testDeleteByGeCondition() {
        repository.save(newEntity("a", 5));
        repository.save(newEntity("b", 3));

        int count = new DeleteSpec<>(TestEntity.class).ge(TestEntity::getStatus, 5).execute(em);

        assertEquals(1, count);
        assertEquals("b", repository.findAll().get(0).getName());
    }

    @Test
    void testDeleteByLtCondition() {
        repository.save(newEntity("a", 1));
        repository.save(newEntity("b", 10));

        int count = new DeleteSpec<>(TestEntity.class).lt(TestEntity::getStatus, 5).execute(em);

        assertEquals(1, count);
        assertEquals("b", repository.findAll().get(0).getName());
    }

    @Test
    void testDeleteByLeCondition() {
        repository.save(newEntity("a", 3));
        repository.save(newEntity("b", 10));

        int count = new DeleteSpec<>(TestEntity.class).le(TestEntity::getStatus, 3).execute(em);

        assertEquals(1, count);
    }

    @Test
    void testDeleteByLikeCondition() {
        repository.save(newEntity("hello", 1));
        repository.save(newEntity("world", 1));

        int count = new DeleteSpec<>(TestEntity.class).startsWith(TestEntity::getName, "hel").execute(em);

        assertEquals(1, count);
        assertEquals("world", repository.findAll().get(0).getName());
    }

    @Test
    void testDeleteByInCondition() {
        repository.save(newEntity("a", 1));
        repository.save(newEntity("b", 2));
        repository.save(newEntity("c", 3));

        int count = new DeleteSpec<>(TestEntity.class).in(TestEntity::getStatus, 1, 2).execute(em);

        assertEquals(2, count);
        assertEquals(1, repository.findAll().size());
    }

    @Test
    void testDeleteByBetweenCondition() {
        repository.save(newEntity("a", 1));
        repository.save(newEntity("b", 5));
        repository.save(newEntity("c", 10));

        int count = new DeleteSpec<>(TestEntity.class).between(TestEntity::getStatus, 3, 7).execute(em);

        assertEquals(1, count);
        assertEquals(2, repository.findAll().size());
    }

    @Test
    void testDeleteByIsNullCondition() {
        repository.save(newEntity("named", 1));
        TestEntity nullName = new TestEntity();
        nullName.setName(null);
        nullName.setStatus(99);
        em.persist(nullName);
        em.flush();

        int count = new DeleteSpec<>(TestEntity.class).isNull(TestEntity::getName).execute(em);

        assertEquals(1, count);
        assertEquals(1, repository.findAll().size());
    }

    @Test
    void testDeleteByIsNotNullCondition() {
        repository.save(newEntity("named", 1));
        TestEntity nullName = new TestEntity();
        nullName.setName(null);
        nullName.setStatus(99);
        em.persist(nullName);
        em.flush();

        int count = new DeleteSpec<>(TestEntity.class).isNotNull(TestEntity::getName).execute(em);

        assertEquals(1, count);
    }

    @Test
    void testDeleteNoConditionsThrowsException() {
        repository.save(newEntity("a", 1));
        repository.save(newEntity("b", 2));

        assertThrows(IllegalStateException.class, () -> new DeleteSpec<>(TestEntity.class).execute(em));
    }

    @Test
    void testDeleteAll() {
        repository.save(newEntity("a", 1));
        repository.save(newEntity("b", 2));

        int count = new DeleteSpec<>(TestEntity.class).allowUnconditional(true).deleteAll(em);

        assertEquals(2, count);
        assertTrue(repository.findAll().isEmpty());
    }

    @Test
    void testDeleteEqNullBecomesIsNull() {
        repository.save(newEntity("named", 1));
        TestEntity nullName = new TestEntity();
        nullName.setName(null);
        nullName.setStatus(99);
        em.persist(nullName);
        em.flush();

        int count = new DeleteSpec<>(TestEntity.class).eq(TestEntity::getName, (String)null).execute(em);

        assertEquals(1, count);
    }

    @Test
    void testDeleteNeNullBecomesIsNotNull() {
        repository.save(newEntity("named", 1));
        TestEntity nullName = new TestEntity();
        nullName.setName(null);
        nullName.setStatus(99);
        em.persist(nullName);
        em.flush();

        int count = new DeleteSpec<>(TestEntity.class).ne(TestEntity::getName, (String)null).execute(em);

        assertEquals(1, count);
        assertNull(repository.findAll().get(0).getName());
    }

    @Test
    void testDeleteGtNullValueThrowsException() {
        assertThrows(IllegalArgumentException.class,
            () -> new DeleteSpec<>(TestEntity.class).gt(TestEntity::getStatus, null));
    }

    @Test
    void testDeleteBetweenInvalidRangeThrowsException() {
        assertThrows(IllegalArgumentException.class,
            () -> new DeleteSpec<>(TestEntity.class).between(TestEntity::getStatus, 10, 1));
    }

    @Test
    void testDeleteNotLike() {
        repository.save(newEntity("hello", 1));
        repository.save(newEntity("world", 1));
        int count = new DeleteSpec<>(TestEntity.class).notLike(TestEntity::getName, "hello").execute(em);
        assertEquals(1, count);
        assertEquals("hello", repository.findAll().get(0).getName());
    }

    @Test
    void testDeleteStartsWith() {
        repository.save(newEntity("hello", 1));
        repository.save(newEntity("world", 1));
        int count = new DeleteSpec<>(TestEntity.class).startsWith(TestEntity::getName, "hel").execute(em);
        assertEquals(1, count);
        assertEquals("world", repository.findAll().get(0).getName());
    }

    @Test
    void testDeleteEndsWith() {
        repository.save(newEntity("ending", 1));
        repository.save(newEntity("start", 1));
        int count = new DeleteSpec<>(TestEntity.class).endsWith(TestEntity::getName, "ing").execute(em);
        assertEquals(1, count);
        assertEquals("start", repository.findAll().get(0).getName());
    }

    @Test
    void testDeleteLike() {
        repository.save(newEntity("abc", 1));
        repository.save(newEntity("xyz", 1));
        int count = new DeleteSpec<>(TestEntity.class).like(TestEntity::getName, "ab").execute(em);
        assertEquals(1, count);
        assertEquals("xyz", repository.findAll().get(0).getName());
    }

    @Test
    void testDeleteEqIgnoreCase() {
        repository.save(newEntity("Hello", 1));
        repository.save(newEntity("WORLD", 1));
        int count = new DeleteSpec<>(TestEntity.class).eqIgnoreCase(TestEntity::getName, "HELLO").execute(em);
        assertEquals(1, count);
        assertEquals("WORLD", repository.findAll().get(0).getName());
    }

    @Test
    void testDeleteLikeIgnoreCase() {
        repository.save(newEntity("HelloWorld", 1));
        repository.save(newEntity("xyz", 1));
        int count = new DeleteSpec<>(TestEntity.class).likeIgnoreCase(TestEntity::getName, "hello").execute(em);
        assertEquals(1, count);
        assertEquals("xyz", repository.findAll().get(0).getName());
    }

    @Test
    void testDeleteNotIn() {
        repository.save(newEntity("a", 1));
        repository.save(newEntity("b", 2));
        repository.save(newEntity("c", 3));
        int count = new DeleteSpec<>(TestEntity.class).notIn(TestEntity::getStatus, 1, 3).execute(em);
        assertEquals(1, count);
        assertEquals(2, repository.findAll().size());
    }

    @Test
    void testDeleteNotBetween() {
        repository.save(newEntity("a", 1));
        repository.save(newEntity("b", 5));
        repository.save(newEntity("c", 10));
        int count = new DeleteSpec<>(TestEntity.class).notBetween(TestEntity::getStatus, 3, 7).execute(em);
        assertEquals(2, count);
        assertEquals(1, repository.findAll().size());
    }

    @Test
    void testDeleteWhere() {
        repository.save(newEntity("a", 1));
        repository.save(newEntity("b", 10));
        int count = new DeleteSpec<>(TestEntity.class).in(TestEntity::getStatus, 1).execute(em);
        assertEquals(1, count);
        assertEquals("b", repository.findAll().get(0).getName());
    }

    @Test
    void testDeleteOrGroup() {
        repository.save(newEntity("alpha", 1));
        repository.save(newEntity("beta", 2));
        repository.save(newEntity("gamma", 3));
        int count = new DeleteSpec<>(TestEntity.class)
            .or(o -> o.eq(TestEntity::getName, "alpha").eq(TestEntity::getName, "beta")).execute(em);
        assertEquals(2, count);
        assertEquals(1, repository.count());
    }

    @Test
    void testDeleteNotGroup() {
        repository.save(newEntity("a", 1));
        repository.save(newEntity("b", 2));
        int count = new DeleteSpec<>(TestEntity.class).not(o -> o.eq(TestEntity::getName, "a")).execute(em);
        assertEquals(1, count);
        assertEquals("a", repository.findAll().get(0).getName());
    }

    @Test
    void testDeleteInWithCollection() {
        repository.save(newEntity("x", 1));
        repository.save(newEntity("y", 2));
        repository.save(newEntity("z", 3));
        int count =
            new DeleteSpec<>(TestEntity.class).in(TestEntity::getName, java.util.Arrays.asList("x", "z")).execute(em);
        assertEquals(2, count);
    }

    @Test
    void testDeleteNotInWithCollection() {
        repository.save(newEntity("x", 1));
        repository.save(newEntity("y", 2));
        repository.save(newEntity("z", 3));
        int count = new DeleteSpec<>(TestEntity.class).notIn(TestEntity::getName, java.util.Arrays.asList("x", "z"))
            .execute(em);
        assertEquals(1, count);
        assertEquals("x", repository.findAll().get(0).getName());
    }

    @Test
    void testDeleteDeleteAllInTransaction() {
        repository.save(newEntity("a", 1));
        repository.save(newEntity("b", 2));
        int count = new DeleteSpec<>(TestEntity.class).allowUnconditional(true).deleteAllInTransaction(em);
        assertEquals(2, count);
        assertEquals(0, repository.count());
    }

    @Test
    void testDeleteToDelete() {
        repository.save(newEntity("target", 1));
        DeleteSpec<TestEntity> spec = new DeleteSpec<>(TestEntity.class).eq(TestEntity::getName, "target");
        jakarta.persistence.criteria.CriteriaDelete<TestEntity> cd = spec.toDelete(em);
        assertNotNull(cd);
    }

    @Test
    void testDeleteToDeleteNoConditionsThrowsException() {
        DeleteSpec<TestEntity> spec = new DeleteSpec<>(TestEntity.class);
        assertThrows(IllegalStateException.class, () -> spec.toDelete(em));
    }

    @Test
    void testDeleteExecuteLimited() {
        for (int i = 0; i < 5; i++) {
            repository.save(newEntity("lim" + i, 0));
        }
        int count = new DeleteSpec<>(TestEntity.class).eq(TestEntity::getStatus, 0).executeLimited(em, 3);
        assertEquals(3, count);
        em.clear();
        assertEquals(2, repository.count());
    }

    @Test
    void testDeleteExecuteLimitedNoConditionsThrowsException() {
        repository.save(newEntity("a", 1));
        assertThrows(IllegalStateException.class, () -> new DeleteSpec<>(TestEntity.class).executeLimited(em, 10));
    }

    @Test
    void testDeleteExecuteLimitedInvalidLimitThrowsException() {
        repository.save(newEntity("a", 1));
        assertThrows(IllegalArgumentException.class,
            () -> new DeleteSpec<>(TestEntity.class).eq(TestEntity::getStatus, 0).executeLimited(em, 0));
    }

    @Test
    void testDeleteExecuteInTransaction() {
        repository.save(newEntity("tx1", 1));
        int count = new DeleteSpec<>(TestEntity.class).eq(TestEntity::getStatus, 1).executeInTransaction(em);
        assertEquals(1, count);
    }

    @Test
    void testDeleteOrGroupNe() {
        repository.save(newEntity("a", 1));
        repository.save(newEntity("b", 2));
        int count = new DeleteSpec<>(TestEntity.class).or(o -> o.ne(TestEntity::getName, "a")).execute(em);
        assertEquals(1, count);
    }

    @Test
    void testDeleteOrGroupGt() {
        repository.save(newEntity("a", 1));
        repository.save(newEntity("b", 10));
        int count = new DeleteSpec<>(TestEntity.class).or(o -> o.gt(TestEntity::getStatus, 5)).execute(em);
        assertEquals(1, count);
    }

    @Test
    void testDeleteOrGroupGe() {
        repository.save(newEntity("a", 1));
        repository.save(newEntity("b", 5));
        int count = new DeleteSpec<>(TestEntity.class).or(o -> o.ge(TestEntity::getStatus, 5)).execute(em);
        assertEquals(1, count);
    }

    @Test
    void testDeleteOrGroupLt() {
        repository.save(newEntity("a", 1));
        repository.save(newEntity("b", 10));
        int count = new DeleteSpec<>(TestEntity.class).or(o -> o.lt(TestEntity::getStatus, 5)).execute(em);
        assertEquals(1, count);
    }

    @Test
    void testDeleteOrGroupLe() {
        repository.save(newEntity("a", 1));
        repository.save(newEntity("b", 10));
        int count = new DeleteSpec<>(TestEntity.class).or(o -> o.le(TestEntity::getStatus, 1)).execute(em);
        assertEquals(1, count);
    }

    @Test
    void testDeleteOrGroupLike() {
        repository.save(newEntity("hello", 1));
        repository.save(newEntity("world", 2));
        int count = new DeleteSpec<>(TestEntity.class).or(o -> o.like(TestEntity::getName, "hel")).execute(em);
        assertEquals(1, count);
    }

    @Test
    void testDeleteOrGroupNotLike() {
        repository.save(newEntity("hello", 1));
        repository.save(newEntity("world", 2));
        int count = new DeleteSpec<>(TestEntity.class).or(o -> o.notLike(TestEntity::getName, "hello")).execute(em);
        assertEquals(1, count);
    }

    @Test
    void testDeleteOrGroupStartsWith() {
        repository.save(newEntity("hello", 1));
        repository.save(newEntity("world", 2));
        int count = new DeleteSpec<>(TestEntity.class).or(o -> o.startsWith(TestEntity::getName, "hel")).execute(em);
        assertEquals(1, count);
    }

    @Test
    void testDeleteOrGroupEndsWith() {
        repository.save(newEntity("ending", 1));
        repository.save(newEntity("start", 2));
        int count = new DeleteSpec<>(TestEntity.class).or(o -> o.endsWith(TestEntity::getName, "ing")).execute(em);
        assertEquals(1, count);
    }

    @Test
    void testDeleteOrGroupContains() {
        repository.save(newEntity("abc", 1));
        repository.save(newEntity("xyz", 2));
        int count = new DeleteSpec<>(TestEntity.class).or(o -> o.like(TestEntity::getName, "ab")).execute(em);
        assertEquals(1, count);
    }

    @Test
    void testDeleteOrGroupEqIgnoreCase() {
        repository.save(newEntity("Hello", 1));
        repository.save(newEntity("world", 2));
        int count =
            new DeleteSpec<>(TestEntity.class).or(o -> o.eqIgnoreCase(TestEntity::getName, "HELLO")).execute(em);
        assertEquals(1, count);
    }

    @Test
    void testDeleteOrGroupLikeIgnoreCase() {
        repository.save(newEntity("HelloWorld", 1));
        repository.save(newEntity("xyz", 2));
        int count =
            new DeleteSpec<>(TestEntity.class).or(o -> o.likeIgnoreCase(TestEntity::getName, "hello")).execute(em);
        assertEquals(1, count);
    }

    @Test
    void testDeleteOrGroupIn() {
        repository.save(newEntity("a", 1));
        repository.save(newEntity("b", 2));
        repository.save(newEntity("c", 3));
        int count = new DeleteSpec<>(TestEntity.class).or(o -> o.in(TestEntity::getStatus, 1, 3)).execute(em);
        assertEquals(2, count);
    }

    @Test
    void testDeleteOrGroupNotIn() {
        repository.save(newEntity("a", 1));
        repository.save(newEntity("b", 2));
        repository.save(newEntity("c", 3));
        int count = new DeleteSpec<>(TestEntity.class).or(o -> o.notIn(TestEntity::getStatus, 1, 3)).execute(em);
        assertEquals(1, count);
    }

    @Test
    void testDeleteOrGroupIsNull() {
        repository.save(newEntity("named", 1));
        TestEntity nullName = new TestEntity();
        nullName.setName(null);
        nullName.setStatus(99);
        em.persist(nullName);
        em.flush();
        int count = new DeleteSpec<>(TestEntity.class).or(o -> o.isNull(TestEntity::getName)).execute(em);
        assertEquals(1, count);
    }

    @Test
    void testDeleteOrGroupIsNotNull() {
        repository.save(newEntity("named", 1));
        TestEntity nullName = new TestEntity();
        nullName.setName(null);
        nullName.setStatus(99);
        em.persist(nullName);
        em.flush();
        int count = new DeleteSpec<>(TestEntity.class).or(o -> o.isNotNull(TestEntity::getName)).execute(em);
        assertEquals(1, count);
    }

    @Test
    void testDeleteOrGroupBetween() {
        repository.save(newEntity("a", 1));
        repository.save(newEntity("b", 5));
        repository.save(newEntity("c", 10));
        int count = new DeleteSpec<>(TestEntity.class).or(o -> o.between(TestEntity::getStatus, 1, 5)).execute(em);
        assertEquals(2, count);
    }

    @Test
    void testDeleteOrGroupNotBetween() {
        repository.save(newEntity("a", 1));
        repository.save(newEntity("b", 5));
        repository.save(newEntity("c", 10));
        int count = new DeleteSpec<>(TestEntity.class).or(o -> o.notBetween(TestEntity::getStatus, 3, 7)).execute(em);
        assertEquals(2, count);
    }

    @Test
    void testDeleteNotGroupNe() {
        repository.save(newEntity("a", 1));
        repository.save(newEntity("b", 2));
        int count = new DeleteSpec<>(TestEntity.class).not(o -> o.ne(TestEntity::getName, "a")).execute(em);
        assertEquals(1, count);
    }

    @Test
    void testDeleteNotBetweenInvalidRangeThrowsException() {
        assertThrows(IllegalArgumentException.class,
            () -> new DeleteSpec<>(TestEntity.class).notBetween(TestEntity::getStatus, 10, 1));
    }

    @Test
    void testDeleteOrGroupGtNullThrowsException() {
        assertThrows(IllegalArgumentException.class,
            () -> new DeleteSpec<>(TestEntity.class).or(o -> o.gt(TestEntity::getStatus, null)));
    }

    @Test
    void testDeleteOrGroupGeNullThrowsException() {
        assertThrows(IllegalArgumentException.class,
            () -> new DeleteSpec<>(TestEntity.class).or(o -> o.ge(TestEntity::getStatus, null)));
    }

    @Test
    void testDeleteOrGroupLtNullThrowsException() {
        assertThrows(IllegalArgumentException.class,
            () -> new DeleteSpec<>(TestEntity.class).or(o -> o.lt(TestEntity::getStatus, null)));
    }

    @Test
    void testDeleteOrGroupLeNullThrowsException() {
        assertThrows(IllegalArgumentException.class,
            () -> new DeleteSpec<>(TestEntity.class).or(o -> o.le(TestEntity::getStatus, null)));
    }

    @Test
    void testDeleteOrGroupLikeNullThrowsException() {
        assertThrows(IllegalArgumentException.class,
            () -> new DeleteSpec<>(TestEntity.class).or(o -> o.like(TestEntity::getName, null)));
    }

    @Test
    void testDeleteOrGroupNotLikeNullThrowsException() {
        assertThrows(IllegalArgumentException.class,
            () -> new DeleteSpec<>(TestEntity.class).or(o -> o.notLike(TestEntity::getName, null)));
    }

    @Test
    void testDeleteOrGroupStartsWithNullThrowsException() {
        assertThrows(IllegalArgumentException.class,
            () -> new DeleteSpec<>(TestEntity.class).or(o -> o.startsWith(TestEntity::getName, null)));
    }

    @Test
    void testDeleteOrGroupEndsWithNullThrowsException() {
        assertThrows(IllegalArgumentException.class,
            () -> new DeleteSpec<>(TestEntity.class).or(o -> o.endsWith(TestEntity::getName, null)));
    }

    @Test
    void testDeleteOrGroupContainsNullThrowsException() {
        assertThrows(IllegalArgumentException.class,
            () -> new DeleteSpec<>(TestEntity.class).or(o -> o.like(TestEntity::getName, null)));
    }

    @Test
    void testDeleteOrGroupLikeIgnoreCaseNullThrowsException() {
        assertThrows(IllegalArgumentException.class,
            () -> new DeleteSpec<>(TestEntity.class).or(o -> o.likeIgnoreCase(TestEntity::getName, null)));
    }

    @Test
    void testDeleteOrGroupInEmptyThrowsException() {
        assertThrows(IllegalArgumentException.class,
            () -> new DeleteSpec<>(TestEntity.class).or(o -> o.in(TestEntity::getStatus)));
    }

    @Test
    void testDeleteOrGroupNotInEmptyThrowsException() {
        assertThrows(IllegalArgumentException.class,
            () -> new DeleteSpec<>(TestEntity.class).or(o -> o.notIn(TestEntity::getStatus)));
    }

    @Test
    void testDeleteOrGroupBetweenNullStartThrowsException() {
        assertThrows(IllegalArgumentException.class,
            () -> new DeleteSpec<>(TestEntity.class).or(o -> o.between(TestEntity::getStatus, null, 5)));
    }

    @Test
    void testDeleteOrGroupBetweenNullEndThrowsException() {
        assertThrows(IllegalArgumentException.class,
            () -> new DeleteSpec<>(TestEntity.class).or(o -> o.between(TestEntity::getStatus, 1, null)));
    }

    @Test
    void testDeleteOrGroupNotBetweenNullStartThrowsException() {
        assertThrows(IllegalArgumentException.class,
            () -> new DeleteSpec<>(TestEntity.class).or(o -> o.notBetween(TestEntity::getStatus, null, 5)));
    }

    @Test
    void testDeleteOrGroupNotBetweenNullEndThrowsException() {
        assertThrows(IllegalArgumentException.class,
            () -> new DeleteSpec<>(TestEntity.class).or(o -> o.notBetween(TestEntity::getStatus, 1, null)));
    }

    @Test
    void testDeleteOrGroupWithMultipleConditions() {
        repository.save(newEntity("a", 1));
        repository.save(newEntity("b", 10));
        repository.save(newEntity("c", 5));
        int count = new DeleteSpec<>(TestEntity.class)
            .or(o -> o.gt(TestEntity::getStatus, 8).lt(TestEntity::getStatus, 3)).execute(em);
        assertEquals(2, count);
    }

    @Test
    void testDeleteNotGroupWithMultipleConditions() {
        repository.save(newEntity("a", 1));
        repository.save(newEntity("b", 10));
        repository.save(newEntity("c", 5));
        // NOT(status > 3 OR status < 8) = status <= 3 AND status >= 8 -> no match
        // Actually: NOT(cond1 OR cond2) = NOT(cond1) AND NOT(cond2)
        // NOT(>3) = <=3, NOT(<8) = >=8 -> <=3 AND >=8 -> no entity matches
        int count = new DeleteSpec<>(TestEntity.class)
            .not(o -> o.gt(TestEntity::getStatus, 3).lt(TestEntity::getStatus, 8)).execute(em);
        assertEquals(0, count);
    }

    @Test
    void testDeleteNullFieldThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> new DeleteSpec<>(TestEntity.class).eq(null, "value"));
    }

    @Test
    void testDeleteBetweenTypeMismatchThrowsException() {
        assertThrows(IllegalArgumentException.class,
            () -> new DeleteSpec<>(TestEntity.class).between(TestEntity::getName, 1, "abc"));
    }

    @Test
    void testDeleteNotBetweenTypeMismatchThrowsException() {
        assertThrows(IllegalArgumentException.class,
            () -> new DeleteSpec<>(TestEntity.class).notBetween(TestEntity::getName, 1, "abc"));
    }

    @Test
    void testDeleteBetweenNullStartThrowsException() {
        assertThrows(IllegalArgumentException.class,
            () -> new DeleteSpec<>(TestEntity.class).between(TestEntity::getStatus, null, 5));
    }

    @Test
    void testDeleteBetweenNullEndThrowsException() {
        assertThrows(IllegalArgumentException.class,
            () -> new DeleteSpec<>(TestEntity.class).between(TestEntity::getStatus, 1, null));
    }

    @Test
    void testDeleteNotBetweenNullStartThrowsException() {
        assertThrows(IllegalArgumentException.class,
            () -> new DeleteSpec<>(TestEntity.class).notBetween(TestEntity::getStatus, null, 5));
    }

    @Test
    void testDeleteNotBetweenNullEndThrowsException() {
        assertThrows(IllegalArgumentException.class,
            () -> new DeleteSpec<>(TestEntity.class).notBetween(TestEntity::getStatus, 1, null));
    }

    @Test
    void testDeleteOrGroupBetweenTypeMismatchThrowsException() {
        assertThrows(IllegalArgumentException.class,
            () -> new DeleteSpec<>(TestEntity.class).or(o -> o.between(TestEntity::getName, 1, "abc")));
    }

    @Test
    void testDeleteOrGroupNotBetweenTypeMismatchThrowsException() {
        assertThrows(IllegalArgumentException.class,
            () -> new DeleteSpec<>(TestEntity.class).or(o -> o.notBetween(TestEntity::getName, 1, "abc")));
    }

    @Test
    void testDeleteOrGroupBetweenStartGreaterThanEndThrowsException() {
        assertThrows(IllegalArgumentException.class,
            () -> new DeleteSpec<>(TestEntity.class).or(o -> o.between(TestEntity::getStatus, 10, 1)));
    }

    @Test
    void testDeleteOrGroupNotBetweenStartGreaterThanEndThrowsException() {
        assertThrows(IllegalArgumentException.class,
            () -> new DeleteSpec<>(TestEntity.class).or(o -> o.notBetween(TestEntity::getStatus, 10, 1)));
    }

    private TestEntity newEntity(String name, int status) {
        TestEntity entity = new TestEntity();
        entity.setName(name);
        entity.setStatus(status);
        return entity;
    }
}
