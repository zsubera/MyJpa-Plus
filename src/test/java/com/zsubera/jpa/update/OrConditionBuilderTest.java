package com.zsubera.jpa.update;

import static org.junit.jupiter.api.Assertions.*;

import com.zsubera.jpa.spec.TestApplication;
import com.zsubera.jpa.spec.TestEntity;
import com.zsubera.jpa.spec.TestEntityRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ContextConfiguration;

/**
 * Tests for {@link OrConditionBuilder}.
 */
@DataJpaTest
@ContextConfiguration(classes = TestApplication.class)
class OrConditionBuilderTest {

    @Autowired
    private TestEntityRepository repository;

    @PersistenceContext
    private EntityManager em;

    @Test
    void orCondition_eq_matchesCorrectly() {
        repository.save(newEntity("alice", 1));
        repository.save(newEntity("bob", 2));
        repository.save(newEntity("charlie", 3));

        List<TestEntity> result = repository.findAll(new com.zsubera.jpa.spec.QuerySpec<TestEntity>()
            .or(or -> or.eq(TestEntity::getName, "alice").eq(TestEntity::getName, "bob")));

        assertEquals(2, result.size());
    }

    @Test
    void orCondition_ne_matchesCorrectly() {
        repository.save(newEntity("alice", 1));
        repository.save(newEntity("bob", 2));
        repository.save(newEntity("charlie", 3));

        List<TestEntity> result = repository.findAll(new com.zsubera.jpa.spec.QuerySpec<TestEntity>()
            .or(or -> or.ne(TestEntity::getName, "alice").ne(TestEntity::getName, "bob")));

        assertEquals(3, result.size()); // OR: name != 'alice' OR name != 'bob' matches all
    }

    @Test
    void orCondition_gtLt_matchesCorrectly() {
        repository.save(newEntity("a", 1));
        repository.save(newEntity("b", 5));
        repository.save(newEntity("c", 10));
        repository.save(newEntity("d", 15));

        List<TestEntity> result = repository.findAll(new com.zsubera.jpa.spec.QuerySpec<TestEntity>()
            .or(or -> or.gt(TestEntity::getStatus, 12).lt(TestEntity::getStatus, 3)));

        assertEquals(2, result.size()); // status > 12 OR status < 3
    }

    @Test
    void orCondition_geLe_matchesCorrectly() {
        repository.save(newEntity("a", 1));
        repository.save(newEntity("b", 5));
        repository.save(newEntity("c", 10));

        List<TestEntity> result = repository.findAll(new com.zsubera.jpa.spec.QuerySpec<TestEntity>()
            .or(or -> or.ge(TestEntity::getStatus, 10).le(TestEntity::getStatus, 1)));

        assertEquals(2, result.size()); // status >= 10 OR status <= 1
    }

    @Test
    void orCondition_startsWith_endsWith() {
        repository.save(newEntity("alice", 1));
        repository.save(newEntity("bob", 2));
        repository.save(newEntity("charlie", 3));

        List<TestEntity> result = repository.findAll(new com.zsubera.jpa.spec.QuerySpec<TestEntity>()
            .or(or -> or.startsWith(TestEntity::getName, "ali").endsWith(TestEntity::getName, "lie")));

        assertEquals(2, result.size());
    }

    @Test
    void orCondition_in_matchesCorrectly() {
        repository.save(newEntity("alice", 1));
        repository.save(newEntity("bob", 2));
        repository.save(newEntity("charlie", 3));

        List<TestEntity> result = repository.findAll(
            new com.zsubera.jpa.spec.QuerySpec<TestEntity>().or(or -> or.in(TestEntity::getName, "alice", "charlie")));

        assertEquals(2, result.size());
    }

    @Test
    void orCondition_notIn_matchesCorrectly() {
        repository.save(newEntity("alice", 1));
        repository.save(newEntity("bob", 2));
        repository.save(newEntity("charlie", 3));

        List<TestEntity> result = repository
            .findAll(new com.zsubera.jpa.spec.QuerySpec<TestEntity>().or(or -> or.notIn(TestEntity::getName, "alice")));

        assertEquals(2, result.size());
    }

    @Test
    void orCondition_isNullIsNotNull() {
        repository.save(newEntity("alice", 1));
        TestEntity nullName = new TestEntity();
        nullName.setName(null);
        nullName.setStatus(99);
        em.persist(nullName);
        em.flush();

        List<TestEntity> result = repository.findAll(new com.zsubera.jpa.spec.QuerySpec<TestEntity>()
            .or(or -> or.isNull(TestEntity::getName).isNotNull(TestEntity::getName)));

        assertEquals(2, result.size()); // all match: null OR not-null
    }

    @Test
    void orCondition_between_matchesCorrectly() {
        repository.save(newEntity("a", 1));
        repository.save(newEntity("b", 5));
        repository.save(newEntity("c", 10));
        repository.save(newEntity("d", 15));

        List<TestEntity> result = repository.findAll(
            new com.zsubera.jpa.spec.QuerySpec<TestEntity>().or(or -> or.between(TestEntity::getStatus, 4, 6)));

        assertEquals(1, result.size());
        assertEquals("b", result.get(0).getName());
    }

    @Test
    void orCondition_eqIgnoreCase_matchesCorrectly() {
        repository.save(newEntity("Alice", 1));
        repository.save(newEntity("bob", 2));

        List<TestEntity> result = repository.findAll(
            new com.zsubera.jpa.spec.QuerySpec<TestEntity>().or(or -> or.eqIgnoreCase(TestEntity::getName, "alice")));

        assertEquals(1, result.size());
    }

    @Test
    void orCondition_likeIgnoreCase_matchesCorrectly() {
        repository.save(newEntity("Alice", 1));
        repository.save(newEntity("bob", 2));

        List<TestEntity> result = repository.findAll(
            new com.zsubera.jpa.spec.QuerySpec<TestEntity>().or(or -> or.likeIgnoreCase(TestEntity::getName, "ali")));

        assertEquals(1, result.size());
    }

    @Test
    void orCondition_multiLike_matchesCorrectly() {
        repository.save(newEntity("alice", 1));
        repository.save(newEntity("bob", 2));
        repository.save(newEntity("charlie", 3));

        List<TestEntity> result = repository.findAll(
            new com.zsubera.jpa.spec.QuerySpec<TestEntity>().or(or -> or.multiLike("li", TestEntity::getName)));

        assertEquals(2, result.size()); // alice and charlie contain "li"
    }

    @Test
    void orCondition_startsWith_null_throwsException() {
        UpdateSpec<TestEntity> spec = new UpdateSpec<>(TestEntity.class);
        OrConditionBuilder<TestEntity, UpdateSpec<TestEntity>> builder =
            new OrConditionBuilder<>(spec, new ArrayList<>());
        assertThrows(IllegalArgumentException.class, () -> builder.startsWith(TestEntity::getName, null));
    }

    @Test
    void orCondition_endsWith_null_throwsException() {
        UpdateSpec<TestEntity> spec = new UpdateSpec<>(TestEntity.class);
        OrConditionBuilder<TestEntity, UpdateSpec<TestEntity>> builder =
            new OrConditionBuilder<>(spec, new ArrayList<>());
        assertThrows(IllegalArgumentException.class, () -> builder.endsWith(TestEntity::getName, null));
    }

    private TestEntity newEntity(String name, int status) {
        TestEntity e = new TestEntity();
        e.setName(name);
        e.setStatus(status);
        return e;
    }
}
