package com.zsubera.jpa.update;

import com.zsubera.jpa.spec.TestApplication;
import com.zsubera.jpa.spec.TestEntity;
import com.zsubera.jpa.spec.TestEntityRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ContextConfiguration;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

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

        int count = new DeleteSpec<>(TestEntity.class)
                .eq(TestEntity::getName, "deleteMe")
                .execute(em);

        assertEquals(1, count);
        List<TestEntity> all = repository.findAll();
        assertEquals(1, all.size());
        assertEquals("keepMe", all.get(0).getName());
    }

    @Test
    void testDeleteByNeCondition() {
        repository.save(newEntity("a", 1));
        repository.save(newEntity("b", 2));

        int count = new DeleteSpec<>(TestEntity.class)
                .ne(TestEntity::getName, "a")
                .execute(em);

        assertEquals(1, count);
        assertEquals("a", repository.findAll().get(0).getName());
    }

    @Test
    void testDeleteByGtCondition() {
        repository.save(newEntity("low", 1));
        repository.save(newEntity("high", 10));

        int count = new DeleteSpec<>(TestEntity.class)
                .gt(TestEntity::getStatus, 5)
                .execute(em);

        assertEquals(1, count);
        assertEquals("low", repository.findAll().get(0).getName());
    }

    @Test
    void testDeleteByGeCondition() {
        repository.save(newEntity("a", 5));
        repository.save(newEntity("b", 3));

        int count = new DeleteSpec<>(TestEntity.class)
                .ge(TestEntity::getStatus, 5)
                .execute(em);

        assertEquals(1, count);
        assertEquals("b", repository.findAll().get(0).getName());
    }

    @Test
    void testDeleteByLtCondition() {
        repository.save(newEntity("a", 1));
        repository.save(newEntity("b", 10));

        int count = new DeleteSpec<>(TestEntity.class)
                .lt(TestEntity::getStatus, 5)
                .execute(em);

        assertEquals(1, count);
        assertEquals("b", repository.findAll().get(0).getName());
    }

    @Test
    void testDeleteByLeCondition() {
        repository.save(newEntity("a", 3));
        repository.save(newEntity("b", 10));

        int count = new DeleteSpec<>(TestEntity.class)
                .le(TestEntity::getStatus, 3)
                .execute(em);

        assertEquals(1, count);
    }

    @Test
    void testDeleteByLikeCondition() {
        repository.save(newEntity("hello", 1));
        repository.save(newEntity("world", 1));

        int count = new DeleteSpec<>(TestEntity.class)
                .like(TestEntity::getName, "hel%")
                .execute(em);

        assertEquals(1, count);
        assertEquals("world", repository.findAll().get(0).getName());
    }

    @Test
    void testDeleteByInCondition() {
        repository.save(newEntity("a", 1));
        repository.save(newEntity("b", 2));
        repository.save(newEntity("c", 3));

        int count = new DeleteSpec<>(TestEntity.class)
                .in(TestEntity::getStatus, 1, 2)
                .execute(em);

        assertEquals(2, count);
        assertEquals(1, repository.findAll().size());
    }

    @Test
    void testDeleteByBetweenCondition() {
        repository.save(newEntity("a", 1));
        repository.save(newEntity("b", 5));
        repository.save(newEntity("c", 10));

        int count = new DeleteSpec<>(TestEntity.class)
                .between(TestEntity::getStatus, 3, 7)
                .execute(em);

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

        int count = new DeleteSpec<>(TestEntity.class)
                .isNull(TestEntity::getName)
                .execute(em);

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

        int count = new DeleteSpec<>(TestEntity.class)
                .isNotNull(TestEntity::getName)
                .execute(em);

        assertEquals(1, count);
    }

    @Test
    void testDeleteNoConditionsDeletesAll() {
        repository.save(newEntity("a", 1));
        repository.save(newEntity("b", 2));

        int count = new DeleteSpec<>(TestEntity.class)
                .execute(em);

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

        int count = new DeleteSpec<>(TestEntity.class)
                .eq(TestEntity::getName, (String) null)
                .execute(em);

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

        int count = new DeleteSpec<>(TestEntity.class)
                .ne(TestEntity::getName, (String) null)
                .execute(em);

        assertEquals(1, count);
        assertNull(repository.findAll().get(0).getName());
    }

    @Test
    void testDeleteGtNullValueThrowsException() {
        assertThrows(IllegalArgumentException.class, () ->
                new DeleteSpec<>(TestEntity.class).gt(TestEntity::getStatus, null));
    }

    @Test
    void testDeleteBetweenInvalidRangeThrowsException() {
        assertThrows(IllegalArgumentException.class, () ->
                new DeleteSpec<>(TestEntity.class).between(TestEntity::getStatus, 10, 1));
    }

    private TestEntity newEntity(String name, int status) {
        TestEntity entity = new TestEntity();
        entity.setName(name);
        entity.setStatus(status);
        return entity;
    }
}
