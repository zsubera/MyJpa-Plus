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
class UpdateSpecTest {

    @Autowired
    private TestEntityRepository repository;

    @PersistenceContext
    private EntityManager em;

    @Test
    void testUpdateSingleField() {
        repository.save(newEntity("old", 1));
        repository.save(newEntity("keep", 1));

        int count = new UpdateSpec<>(TestEntity.class).set(TestEntity::getName, "updated")
            .eq(TestEntity::getName, "old").execute(em);

        assertEquals(1, count);
        em.clear();
        List<TestEntity> all = repository.findAll();
        assertEquals(2, all.size());
        assertTrue(all.stream().anyMatch(e -> "updated".equals(e.getName())));
        assertTrue(all.stream().anyMatch(e -> "keep".equals(e.getName())));
    }

    @Test
    void testUpdateMultipleFields() {
        repository.save(newEntity("old", 1));

        int count = new UpdateSpec<>(TestEntity.class).set(TestEntity::getName, "new").set(TestEntity::getStatus, 99)
            .eq(TestEntity::getName, "old").execute(em);

        assertEquals(1, count);
        em.clear();
        List<TestEntity> all = repository.findAll();
        assertEquals(1, all.size());
        assertEquals("new", all.get(0).getName());
        assertEquals(Integer.valueOf(99), all.get(0).getStatus());
    }

    @Test
    void testUpdateWithNullValue() {
        repository.save(newEntity("name", 1));

        new UpdateSpec<>(TestEntity.class).set(TestEntity::getName, null).eq(TestEntity::getName, "name").execute(em);

        em.clear();
        TestEntity entity = repository.findAll().get(0);
        assertNull(entity.getName());
    }

    @Test
    void testUpdateWithGtCondition() {
        repository.save(newEntity("a", 1));
        repository.save(newEntity("b", 10));

        int count = new UpdateSpec<>(TestEntity.class).set(TestEntity::getName, "high").gt(TestEntity::getStatus, 5)
            .execute(em);

        assertEquals(1, count);
        em.clear();
        List<TestEntity> all = repository.findAll();
        assertEquals(2, all.size());
        assertTrue(all.stream().anyMatch(e -> "high".equals(e.getName())));
    }

    @Test
    void testUpdateWithGeCondition() {
        repository.save(newEntity("a", 5));
        repository.save(newEntity("b", 3));

        int count =
            new UpdateSpec<>(TestEntity.class).set(TestEntity::getName, "ge").ge(TestEntity::getStatus, 5).execute(em);

        assertEquals(1, count);
        em.clear();
        assertEquals("ge", repository.findAll().stream().filter(e -> e.getStatus() == 5).findFirst().get().getName());
    }

    @Test
    void testUpdateWithLtCondition() {
        repository.save(newEntity("a", 1));
        repository.save(newEntity("b", 10));

        int count =
            new UpdateSpec<>(TestEntity.class).set(TestEntity::getName, "low").lt(TestEntity::getStatus, 5).execute(em);

        assertEquals(1, count);
    }

    @Test
    void testUpdateWithLeCondition() {
        repository.save(newEntity("a", 3));
        repository.save(newEntity("b", 10));

        int count =
            new UpdateSpec<>(TestEntity.class).set(TestEntity::getName, "le").le(TestEntity::getStatus, 3).execute(em);

        assertEquals(1, count);
    }

    @Test
    void testUpdateWithLikeCondition() {
        repository.save(newEntity("hello", 1));
        repository.save(newEntity("world", 1));

        int count = new UpdateSpec<>(TestEntity.class).set(TestEntity::getStatus, 99)
            .startsWith(TestEntity::getName, "hel").execute(em);

        assertEquals(1, count);
        em.clear();
        TestEntity updated = repository.findAll().stream().filter(e -> "hello".equals(e.getName())).findFirst().get();
        assertEquals(Integer.valueOf(99), updated.getStatus());
    }

    @Test
    void testUpdateWithInCondition() {
        repository.save(newEntity("a", 1));
        repository.save(newEntity("b", 2));
        repository.save(newEntity("c", 3));

        int count = new UpdateSpec<>(TestEntity.class).set(TestEntity::getName, "in").in(TestEntity::getStatus, 1, 3)
            .execute(em);

        assertEquals(2, count);
        em.clear();
        List<TestEntity> all = repository.findAll();
        long updated = all.stream().filter(e -> "in".equals(e.getName())).count();
        assertEquals(2, updated);
    }

    @Test
    void testUpdateWithBetweenCondition() {
        repository.save(newEntity("a", 1));
        repository.save(newEntity("b", 3));
        repository.save(newEntity("c", 10));

        int count = new UpdateSpec<>(TestEntity.class).set(TestEntity::getName, "between")
            .between(TestEntity::getStatus, 1, 3).execute(em);

        assertEquals(2, count);
    }

    @Test
    void testUpdateWithIsNullCondition() {
        repository.save(newEntity("named", 1));
        TestEntity nullName = new TestEntity();
        nullName.setName(null);
        nullName.setStatus(99);
        em.persist(nullName);
        em.flush();

        int count =
            new UpdateSpec<>(TestEntity.class).set(TestEntity::getStatus, 0).isNull(TestEntity::getName).execute(em);

        assertEquals(1, count);
    }

    @Test
    void testUpdateWithIsNotNullCondition() {
        repository.save(newEntity("named", 1));
        TestEntity nullName = new TestEntity();
        nullName.setName(null);
        nullName.setStatus(99);
        em.persist(nullName);
        em.flush();

        int count = new UpdateSpec<>(TestEntity.class).set(TestEntity::getStatus, 100).isNotNull(TestEntity::getName)
            .execute(em);

        assertEquals(1, count);
    }

    @Test
    void testUpdateConditionalSet() {
        repository.save(newEntity("old", 1));

        int count = new UpdateSpec<>(TestEntity.class).set(false, TestEntity::getName, "shouldNotSet")
            .set(TestEntity::getStatus, 99).eq(TestEntity::getName, "old").execute(em);

        assertEquals(1, count);
        em.clear();
        TestEntity entity = repository.findAll().get(0);
        assertEquals("old", entity.getName());
        assertEquals(Integer.valueOf(99), entity.getStatus());
    }

    @Test
    void testUpdateNoSetClausesThrowsException() {
        UpdateSpec<TestEntity> spec = new UpdateSpec<>(TestEntity.class).eq(TestEntity::getName, "test");
        assertThrows(IllegalStateException.class, () -> spec.execute(em));
    }

    @Test
    void testUpdateNoConditionsUpdatesAll() {
        repository.save(newEntity("a", 1));
        repository.save(newEntity("b", 2));

        int count =
            new UpdateSpec<>(TestEntity.class).set(TestEntity::getStatus, 99).allowUnconditional(true).updateAll(em);

        assertEquals(2, count);
    }

    @Test
    void testUpdateWithNeCondition() {
        repository.save(newEntity("a", 1));
        repository.save(newEntity("b", 2));

        int count = new UpdateSpec<>(TestEntity.class).set(TestEntity::getName, "notA").ne(TestEntity::getName, "a")
            .execute(em);

        assertEquals(1, count);
    }

    @Test
    void testUpdateNeNullBecomesIsNotNull() {
        repository.save(newEntity("named", 1));
        TestEntity nullName = new TestEntity();
        nullName.setName(null);
        nullName.setStatus(99);
        em.persist(nullName);
        em.flush();

        int count = new UpdateSpec<>(TestEntity.class).set(TestEntity::getStatus, 100)
            .ne(TestEntity::getName, (String)null).execute(em);

        assertEquals(1, count);
    }

    @Test
    void testUpdateEqNullBecomesIsNull() {
        repository.save(newEntity("named", 1));
        TestEntity nullName = new TestEntity();
        nullName.setName(null);
        nullName.setStatus(99);
        em.persist(nullName);
        em.flush();

        int count = new UpdateSpec<>(TestEntity.class).set(TestEntity::getStatus, 0)
            .eq(TestEntity::getName, (String)null).execute(em);

        assertEquals(1, count);
    }

    @Test
    void testUpdateGtNullValueThrowsException() {
        assertThrows(IllegalArgumentException.class,
            () -> new UpdateSpec<>(TestEntity.class).gt(TestEntity::getStatus, null));
    }

    @Test
    void testUpdateBetweenInvalidRangeThrowsException() {
        assertThrows(IllegalArgumentException.class,
            () -> new UpdateSpec<>(TestEntity.class).between(TestEntity::getStatus, 10, 1));
    }

    @Test
    void testUpdateNotLike() {
        repository.save(newEntity("hello", 1));
        repository.save(newEntity("world", 1));
        int count = new UpdateSpec<>(TestEntity.class).set(TestEntity::getStatus, 99)
            .notLike(TestEntity::getName, "hello").execute(em);
        assertEquals(1, count);
    }

    @Test
    void testUpdateStartsWith() {
        repository.save(newEntity("hello", 1));
        repository.save(newEntity("world", 1));
        int count = new UpdateSpec<>(TestEntity.class).set(TestEntity::getStatus, 99)
            .startsWith(TestEntity::getName, "hel").execute(em);
        assertEquals(1, count);
    }

    @Test
    void testUpdateEndsWith() {
        repository.save(newEntity("ending", 1));
        repository.save(newEntity("start", 1));
        int count = new UpdateSpec<>(TestEntity.class).set(TestEntity::getStatus, 99)
            .endsWith(TestEntity::getName, "ing").execute(em);
        assertEquals(1, count);
    }

    @Test
    void testUpdateLike() {
        repository.save(newEntity("abc", 1));
        repository.save(newEntity("xyz", 1));
        int count = new UpdateSpec<>(TestEntity.class).set(TestEntity::getStatus, 99).like(TestEntity::getName, "ab")
            .execute(em);
        assertEquals(1, count);
    }

    @Test
    void testUpdateEqIgnoreCase() {
        repository.save(newEntity("Hello", 1));
        repository.save(newEntity("WORLD", 1));
        int count = new UpdateSpec<>(TestEntity.class).set(TestEntity::getStatus, 99)
            .eqIgnoreCase(TestEntity::getName, "HELLO").execute(em);
        assertEquals(1, count);
    }

    @Test
    void testUpdateLikeIgnoreCase() {
        repository.save(newEntity("HelloWorld", 1));
        repository.save(newEntity("xyz", 1));
        int count = new UpdateSpec<>(TestEntity.class).set(TestEntity::getStatus, 99)
            .likeIgnoreCase(TestEntity::getName, "hello").execute(em);
        assertEquals(1, count);
    }

    @Test
    void testUpdateNotIn() {
        repository.save(newEntity("a", 1));
        repository.save(newEntity("b", 2));
        repository.save(newEntity("c", 3));
        int count = new UpdateSpec<>(TestEntity.class).set(TestEntity::getName, "notIn")
            .notIn(TestEntity::getStatus, 1, 3).execute(em);
        assertEquals(1, count);
        em.clear();
        TestEntity updated = repository.findAll().stream().filter(e -> "notIn".equals(e.getName())).findFirst().get();
        assertEquals(Integer.valueOf(2), updated.getStatus());
    }

    @Test
    void testUpdateNotBetween() {
        repository.save(newEntity("a", 1));
        repository.save(newEntity("b", 5));
        repository.save(newEntity("c", 10));
        int count = new UpdateSpec<>(TestEntity.class).set(TestEntity::getName, "notBetween")
            .notBetween(TestEntity::getStatus, 3, 7).execute(em);
        assertEquals(2, count);
    }

    @Test
    void testUpdateWhere() {
        repository.save(newEntity("a", 1));
        repository.save(newEntity("b", 10));
        int count = new UpdateSpec<>(TestEntity.class).set(TestEntity::getName, "where").in(TestEntity::getStatus, 1)
            .execute(em);
        assertEquals(1, count);
    }

    @Test
    void testUpdateOrGroup() {
        repository.save(newEntity("alpha", 1));
        repository.save(newEntity("beta", 2));
        repository.save(newEntity("gamma", 3));
        int count = new UpdateSpec<>(TestEntity.class).set(TestEntity::getStatus, 99)
            .or(o -> o.eq(TestEntity::getName, "alpha").eq(TestEntity::getName, "beta")).execute(em);
        assertEquals(2, count);
    }

    @Test
    void testUpdateNotGroup() {
        repository.save(newEntity("a", 1));
        repository.save(newEntity("b", 2));
        int count = new UpdateSpec<>(TestEntity.class).set(TestEntity::getStatus, 99)
            .not(o -> o.eq(TestEntity::getName, "a")).execute(em);
        assertEquals(1, count);
    }

    @Test
    void testUpdateInWithCollection() {
        repository.save(newEntity("x", 1));
        repository.save(newEntity("y", 2));
        repository.save(newEntity("z", 3));
        int count = new UpdateSpec<>(TestEntity.class).set(TestEntity::getName, "updated")
            .in(TestEntity::getName, java.util.Arrays.asList("x", "z")).execute(em);
        assertEquals(2, count);
    }

    @Test
    void testUpdateNotInWithCollection() {
        repository.save(newEntity("x", 1));
        repository.save(newEntity("y", 2));
        repository.save(newEntity("z", 3));
        int count = new UpdateSpec<>(TestEntity.class).set(TestEntity::getName, "updated")
            .notIn(TestEntity::getName, java.util.Arrays.asList("x", "z")).execute(em);
        assertEquals(1, count);
    }

    @Test
    void testUpdateUpdateAllInTransaction() {
        repository.save(newEntity("old1", 1));
        repository.save(newEntity("old2", 2));
        int count = new UpdateSpec<>(TestEntity.class).set(TestEntity::getStatus, 99).allowUnconditional(true)
            .updateAllInTransaction(em);
        assertEquals(2, count);
    }

    @Test
    void testUpdateToUpdate() {
        repository.save(newEntity("target", 1));
        UpdateSpec<TestEntity> spec =
            new UpdateSpec<>(TestEntity.class).set(TestEntity::getStatus, 99).eq(TestEntity::getName, "target");
        jakarta.persistence.criteria.CriteriaUpdate<TestEntity> cu = spec.toUpdate(em);
        assertNotNull(cu);
    }

    @Test
    void testUpdateToUpdateNoSetClausesThrowsException() {
        UpdateSpec<TestEntity> spec = new UpdateSpec<>(TestEntity.class).eq(TestEntity::getName, "target");
        assertThrows(IllegalStateException.class, () -> spec.toUpdate(em));
    }

    @Test
    void testUpdateExecuteLimited() {
        for (int i = 0; i < 5; i++) {
            repository.save(newEntity("lim" + i, 0));
        }
        int count = new UpdateSpec<>(TestEntity.class).set(TestEntity::getStatus, 1).eq(TestEntity::getStatus, 0)
            .executeLimited(em, 3);
        assertEquals(3, count);
        em.clear();
        long updated = repository.findAll().stream().filter(e -> e.getStatus() == 1).count();
        assertEquals(3, updated);
    }

    @Test
    void testUpdateExecuteLimitedNoConditionsThrowsException() {
        repository.save(newEntity("a", 1));
        assertThrows(IllegalStateException.class,
            () -> new UpdateSpec<>(TestEntity.class).set(TestEntity::getStatus, 99).executeLimited(em, 10));
    }

    @Test
    void testUpdateExecuteLimitedInvalidLimitThrowsException() {
        repository.save(newEntity("a", 1));
        assertThrows(IllegalArgumentException.class, () -> new UpdateSpec<>(TestEntity.class)
            .set(TestEntity::getStatus, 1).eq(TestEntity::getStatus, 0).executeLimited(em, 0));
    }

    @Test
    void testUpdateExecuteInTransaction() {
        repository.save(newEntity("tx1", 1));
        int count = new UpdateSpec<>(TestEntity.class).set(TestEntity::getStatus, 99).eq(TestEntity::getStatus, 1)
            .executeInTransaction(em);
        assertEquals(1, count);
    }

    @Test
    void testUpdateOrGroupNe() {
        repository.save(newEntity("a", 1));
        repository.save(newEntity("b", 2));
        repository.save(newEntity("c", 3));
        int count = new UpdateSpec<>(TestEntity.class).set(TestEntity::getStatus, 99)
            .or(o -> o.ne(TestEntity::getName, "a")).execute(em);
        assertEquals(2, count);
    }

    @Test
    void testUpdateOrGroupGt() {
        repository.save(newEntity("a", 1));
        repository.save(newEntity("b", 10));
        int count = new UpdateSpec<>(TestEntity.class).set(TestEntity::getStatus, 99)
            .or(o -> o.gt(TestEntity::getStatus, 5)).execute(em);
        assertEquals(1, count);
    }

    @Test
    void testUpdateOrGroupGe() {
        repository.save(newEntity("a", 1));
        repository.save(newEntity("b", 5));
        int count = new UpdateSpec<>(TestEntity.class).set(TestEntity::getStatus, 99)
            .or(o -> o.ge(TestEntity::getStatus, 5)).execute(em);
        assertEquals(1, count);
    }

    @Test
    void testUpdateOrGroupLt() {
        repository.save(newEntity("a", 1));
        repository.save(newEntity("b", 10));
        int count = new UpdateSpec<>(TestEntity.class).set(TestEntity::getStatus, 99)
            .or(o -> o.lt(TestEntity::getStatus, 5)).execute(em);
        assertEquals(1, count);
    }

    @Test
    void testUpdateOrGroupLe() {
        repository.save(newEntity("a", 1));
        repository.save(newEntity("b", 10));
        int count = new UpdateSpec<>(TestEntity.class).set(TestEntity::getStatus, 99)
            .or(o -> o.le(TestEntity::getStatus, 1)).execute(em);
        assertEquals(1, count);
    }

    @Test
    void testUpdateOrGroupLike() {
        repository.save(newEntity("hello", 1));
        repository.save(newEntity("world", 2));
        int count = new UpdateSpec<>(TestEntity.class).set(TestEntity::getStatus, 99)
            .or(o -> o.like(TestEntity::getName, "hel")).execute(em);
        assertEquals(1, count);
    }

    @Test
    void testUpdateOrGroupNotLike() {
        repository.save(newEntity("hello", 1));
        repository.save(newEntity("world", 2));
        int count = new UpdateSpec<>(TestEntity.class).set(TestEntity::getStatus, 99)
            .or(o -> o.notLike(TestEntity::getName, "hello")).execute(em);
        assertEquals(1, count);
    }

    @Test
    void testUpdateOrGroupStartsWith() {
        repository.save(newEntity("hello", 1));
        repository.save(newEntity("world", 2));
        int count = new UpdateSpec<>(TestEntity.class).set(TestEntity::getStatus, 99)
            .or(o -> o.startsWith(TestEntity::getName, "hel")).execute(em);
        assertEquals(1, count);
    }

    @Test
    void testUpdateOrGroupEndsWith() {
        repository.save(newEntity("ending", 1));
        repository.save(newEntity("start", 2));
        int count = new UpdateSpec<>(TestEntity.class).set(TestEntity::getStatus, 99)
            .or(o -> o.endsWith(TestEntity::getName, "ing")).execute(em);
        assertEquals(1, count);
    }

    @Test
    void testUpdateOrGroupContains() {
        repository.save(newEntity("abc", 1));
        repository.save(newEntity("xyz", 2));
        int count = new UpdateSpec<>(TestEntity.class).set(TestEntity::getStatus, 99)
            .or(o -> o.like(TestEntity::getName, "ab")).execute(em);
        assertEquals(1, count);
    }

    @Test
    void testUpdateOrGroupEqIgnoreCase() {
        repository.save(newEntity("Hello", 1));
        repository.save(newEntity("world", 2));
        int count = new UpdateSpec<>(TestEntity.class).set(TestEntity::getStatus, 99)
            .or(o -> o.eqIgnoreCase(TestEntity::getName, "HELLO")).execute(em);
        assertEquals(1, count);
    }

    @Test
    void testUpdateOrGroupLikeIgnoreCase() {
        repository.save(newEntity("HelloWorld", 1));
        repository.save(newEntity("xyz", 2));
        int count = new UpdateSpec<>(TestEntity.class).set(TestEntity::getStatus, 99)
            .or(o -> o.likeIgnoreCase(TestEntity::getName, "hello")).execute(em);
        assertEquals(1, count);
    }

    @Test
    void testUpdateOrGroupIn() {
        repository.save(newEntity("a", 1));
        repository.save(newEntity("b", 2));
        repository.save(newEntity("c", 3));
        int count = new UpdateSpec<>(TestEntity.class).set(TestEntity::getStatus, 99)
            .or(o -> o.in(TestEntity::getStatus, 1, 3)).execute(em);
        assertEquals(2, count);
    }

    @Test
    void testUpdateOrGroupNotIn() {
        repository.save(newEntity("a", 1));
        repository.save(newEntity("b", 2));
        repository.save(newEntity("c", 3));
        int count = new UpdateSpec<>(TestEntity.class).set(TestEntity::getStatus, 99)
            .or(o -> o.notIn(TestEntity::getStatus, 1, 3)).execute(em);
        assertEquals(1, count);
    }

    @Test
    void testUpdateOrGroupIsNull() {
        repository.save(newEntity("named", 1));
        TestEntity nullName = new TestEntity();
        nullName.setName(null);
        nullName.setStatus(99);
        em.persist(nullName);
        em.flush();
        int count = new UpdateSpec<>(TestEntity.class).set(TestEntity::getStatus, 0)
            .or(o -> o.isNull(TestEntity::getName)).execute(em);
        assertEquals(1, count);
    }

    @Test
    void testUpdateOrGroupIsNotNull() {
        repository.save(newEntity("named", 1));
        TestEntity nullName = new TestEntity();
        nullName.setName(null);
        nullName.setStatus(99);
        em.persist(nullName);
        em.flush();
        int count = new UpdateSpec<>(TestEntity.class).set(TestEntity::getStatus, 0)
            .or(o -> o.isNotNull(TestEntity::getName)).execute(em);
        assertEquals(1, count);
    }

    @Test
    void testUpdateOrGroupBetween() {
        repository.save(newEntity("a", 1));
        repository.save(newEntity("b", 5));
        repository.save(newEntity("c", 10));
        int count = new UpdateSpec<>(TestEntity.class).set(TestEntity::getStatus, 99)
            .or(o -> o.between(TestEntity::getStatus, 1, 5)).execute(em);
        assertEquals(2, count);
    }

    @Test
    void testUpdateOrGroupNotBetween() {
        repository.save(newEntity("a", 1));
        repository.save(newEntity("b", 5));
        repository.save(newEntity("c", 10));
        int count = new UpdateSpec<>(TestEntity.class).set(TestEntity::getStatus, 99)
            .or(o -> o.notBetween(TestEntity::getStatus, 3, 7)).execute(em);
        assertEquals(2, count);
    }

    @Test
    void testUpdateNotGroupNe() {
        repository.save(newEntity("a", 1));
        repository.save(newEntity("b", 2));
        int count = new UpdateSpec<>(TestEntity.class).set(TestEntity::getStatus, 99)
            .not(o -> o.ne(TestEntity::getName, "a")).execute(em);
        assertEquals(1, count);
    }

    @Test
    void testUpdateNotBetweenInvalidRangeThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> new UpdateSpec<>(TestEntity.class)
            .set(TestEntity::getStatus, 1).notBetween(TestEntity::getStatus, 10, 1));
    }

    @Test
    void testUpdateBetweenTypeMismatchThrowsException() {
        assertThrows(IllegalArgumentException.class,
            () -> new UpdateSpec<>(TestEntity.class).between(TestEntity::getName, 1, "abc"));
    }

    @Test
    void testUpdateNotBetweenTypeMismatchThrowsException() {
        assertThrows(IllegalArgumentException.class,
            () -> new UpdateSpec<>(TestEntity.class).notBetween(TestEntity::getName, 1, "abc"));
    }

    @Test
    void testUpdateBetweenNullStartThrowsException() {
        assertThrows(IllegalArgumentException.class,
            () -> new UpdateSpec<>(TestEntity.class).between(TestEntity::getStatus, null, 5));
    }

    @Test
    void testUpdateBetweenNullEndThrowsException() {
        assertThrows(IllegalArgumentException.class,
            () -> new UpdateSpec<>(TestEntity.class).between(TestEntity::getStatus, 1, null));
    }

    @Test
    void testUpdateNotBetweenNullStartThrowsException() {
        assertThrows(IllegalArgumentException.class,
            () -> new UpdateSpec<>(TestEntity.class).notBetween(TestEntity::getStatus, null, 5));
    }

    @Test
    void testUpdateNotBetweenNullEndThrowsException() {
        assertThrows(IllegalArgumentException.class,
            () -> new UpdateSpec<>(TestEntity.class).notBetween(TestEntity::getStatus, 1, null));
    }

    @Test
    void testUpdateAllWithoutAllowUnconditionalThrowsException() {
        assertThrows(IllegalStateException.class,
            () -> new UpdateSpec<>(TestEntity.class).set(TestEntity::getName, "updated").updateAll(em));
    }

    @Test
    void testUpdateAllWithAllowUnconditional() {
        repository.save(newEntity("a", 1));
        repository.save(newEntity("b", 2));

        int count = new UpdateSpec<>(TestEntity.class).set(TestEntity::getName, "all-updated").allowUnconditional(true)
            .updateAll(em);

        assertEquals(2, count);
        em.clear();
        List<TestEntity> all = repository.findAll();
        assertTrue(all.stream().allMatch(e -> "all-updated".equals(e.getName())));
    }

    @Test
    void testUpdateAllNoSetClausesThrowsException() {
        assertThrows(IllegalStateException.class,
            () -> new UpdateSpec<>(TestEntity.class).allowUnconditional(true).updateAll(em));
    }

    private TestEntity newEntity(String name, int status) {
        TestEntity entity = new TestEntity();
        entity.setName(name);
        entity.setStatus(status);
        return entity;
    }

    @Test
    void testUpdateWithVersionIncrement() {
        repository.save(newEntity("version-test", 1));
        em.flush();
        em.clear();

        int count = new UpdateSpec<>(TestEntity.class).set(TestEntity::getStatus, 2).withVersionIncrement(true)
            .eq(TestEntity::getName, "version-test").execute(em);
        em.flush();
        em.clear();

        assertEquals(1, count);
        TestEntity found =
            repository.findAll().stream().filter(e -> "version-test".equals(e.getName())).findFirst().orElseThrow();
        assertEquals(Integer.valueOf(2), found.getStatus());
    }

    @Test
    void testUpdateSetAddAtomicIncrement() {
        TestEntity saved = repository.save(newEntity("increment-test", 10));
        em.flush();
        em.clear();

        int count = new UpdateSpec<>(TestEntity.class).setAdd(TestEntity::getStatus, 5)
            .eq(TestEntity::getId, saved.getId()).execute(em);
        em.flush();
        em.clear();

        assertEquals(1, count);
        TestEntity found = repository.findById(saved.getId()).orElseThrow();
        assertEquals(Integer.valueOf(15), found.getStatus());
    }

    @Test
    void testUpdateSetSubtractAtomicDecrement() {
        TestEntity saved = repository.save(newEntity("decrement-test", 20));
        em.flush();
        em.clear();

        int count = new UpdateSpec<>(TestEntity.class).setSubtract(TestEntity::getStatus, 7)
            .eq(TestEntity::getId, saved.getId()).execute(em);
        em.flush();
        em.clear();

        assertEquals(1, count);
        TestEntity found = repository.findById(saved.getId()).orElseThrow();
        assertEquals(Integer.valueOf(13), found.getStatus());
    }

    @Test
    void testUpdateAllowUnconditionalExplicitlyFalse() {
        assertThrows(IllegalStateException.class, () -> new UpdateSpec<>(TestEntity.class).set(TestEntity::getName, "x")
            .allowUnconditional(false).updateAll(em));
    }

    @Test
    void testUpdateSetConditionalWithFalseCondition() {
        repository.save(newEntity("cond-test", 1));
        em.flush();

        int count = new UpdateSpec<>(TestEntity.class).set(false, TestEntity::getStatus, 99)
            .set(true, TestEntity::getName, "updated").eq(TestEntity::getName, "cond-test").execute(em);
        em.flush();
        em.clear();

        assertEquals(1, count);
        TestEntity found =
            repository.findAll().stream().filter(e -> "updated".equals(e.getName())).findFirst().orElseThrow();
        assertEquals(Integer.valueOf(1), found.getStatus());
    }

    @Test
    void testUpdateSetAddWithNullFieldThrows() {
        assertThrows(IllegalArgumentException.class, () -> new UpdateSpec<>(TestEntity.class).setAdd(null, 1));
    }

    @Test
    void testUpdateSetSubtractWithNullAmountThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> new UpdateSpec<>(TestEntity.class).setSubtract(TestEntity::getStatus, null));
    }

    @Test
    void testUpdateOrGroupWithConsumer() {
        repository.save(newEntity("or-consumer-1", 1));
        repository.save(newEntity("or-consumer-2", 2));
        repository.save(newEntity("other", 3));
        em.flush();
        em.clear();

        int count = new UpdateSpec<>(TestEntity.class).set(TestEntity::getStatus, 99)
            .or(o -> o.eq(TestEntity::getName, "or-consumer-1").eq(TestEntity::getStatus, 2)).execute(em);
        em.flush();
        em.clear();

        assertEquals(2, count);
    }

    // [FIX] P0-2: 测试 UpdateSpec.eqIgnoreCase(null) 转为 IS NULL，与 ConditionBuilder 一致
    @Test
    void testUpdateWithEqIgnoreCaseNull() {
        repository.save(newEntity("alice", 1));
        repository.save(newEntity(null, 2));
        em.flush();
        em.clear();

        int count = new UpdateSpec<>(TestEntity.class).set(TestEntity::getStatus, 99)
            .eqIgnoreCase(TestEntity::getName, null).execute(em);
        em.flush();
        em.clear();

        assertEquals(1, count);
        List<TestEntity> all = repository.findAll();
        assertTrue(all.stream().anyMatch(e -> e.getStatus() == 99 && e.getName() == null));
        assertTrue(all.stream().anyMatch(e -> e.getStatus() == 1 && "alice".equals(e.getName())));
    }

    // [FIX] P0-2: 测试 UpdateSpec.neIgnoreCase(null) 转为 IS NOT NULL，与 ConditionBuilder 一致
    @Test
    void testUpdateWithNeIgnoreCaseNull() {
        repository.save(newEntity("alice", 1));
        repository.save(newEntity(null, 2));
        em.flush();
        em.clear();

        int count = new UpdateSpec<>(TestEntity.class).set(TestEntity::getStatus, 99)
            .neIgnoreCase(TestEntity::getName, null).execute(em);
        em.flush();
        em.clear();

        assertEquals(1, count);
        List<TestEntity> all = repository.findAll();
        assertTrue(all.stream().anyMatch(e -> e.getStatus() == 99 && "alice".equals(e.getName())));
        assertTrue(all.stream().anyMatch(e -> e.getStatus() == 2 && e.getName() == null));
    }
}
