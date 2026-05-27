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

        int count = new UpdateSpec<>(TestEntity.class).set(TestEntity::getStatus, 99).like(TestEntity::getName, "hel%")
            .execute(em);

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

        int count = new UpdateSpec<>(TestEntity.class).set(TestEntity::getStatus, 99).updateAll(em);

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
            .notLike(TestEntity::getName, "%hello%").execute(em);
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
    void testUpdateContains() {
        repository.save(newEntity("abc", 1));
        repository.save(newEntity("xyz", 1));
        int count = new UpdateSpec<>(TestEntity.class).set(TestEntity::getStatus, 99)
            .contains(TestEntity::getName, "ab").execute(em);
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
            .likeIgnoreCase(TestEntity::getName, "%hello%").execute(em);
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
        int count = new UpdateSpec<>(TestEntity.class).set(TestEntity::getName, "where")
            .where(root -> root.get("status").in(1)).execute(em);
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
        int count = new UpdateSpec<>(TestEntity.class).set(TestEntity::getStatus, 99).updateAllInTransaction(em);
        assertEquals(2, count);
    }

    private TestEntity newEntity(String name, int status) {
        TestEntity entity = new TestEntity();
        entity.setName(name);
        entity.setStatus(status);
        return entity;
    }
}
