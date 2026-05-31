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
class MergeSpecTest {

    @Autowired
    private TestEntityRepository repository;

    @PersistenceContext
    private EntityManager em;

    @Test
    void testMergeInsertNew() {
        TestEntity entity = newEntity("new", 1);

        int count = new MergeSpec<>(TestEntity.class).withEntity(entity).execute(em);
        em.flush();
        em.clear();

        assertEquals(1, count);
        List<TestEntity> all = repository.findAll();
        assertEquals(1, all.size());
        assertEquals("new", all.get(0).getName());
        assertEquals(Integer.valueOf(1), all.get(0).getStatus());
    }

    @Test
    void testMergeUpdateExisting() {
        TestEntity saved = repository.save(newEntity("original", 1));
        em.flush();
        em.clear();

        TestEntity entity = new TestEntity();
        entity.setId(saved.getId());
        entity.setName("updated");
        entity.setStatus(99);

        int count = new MergeSpec<>(TestEntity.class).withEntity(entity).execute(em);
        em.flush();
        em.clear();

        assertEquals(1, count);
        TestEntity found = repository.findById(saved.getId()).orElseThrow();
        assertEquals("updated", found.getName());
        assertEquals(Integer.valueOf(99), found.getStatus());
    }

    @Test
    void testMergeWithExplicitConflictColumns() {
        repository.save(newEntity("unique", 1));
        em.flush();
        em.clear();

        TestEntity entity = new TestEntity();
        entity.setName("unique");
        entity.setStatus(99);

        int count = new MergeSpec<>(TestEntity.class).withEntity(entity).onConflict(TestEntity::getName).execute(em);
        em.flush();
        em.clear();

        assertEquals(1, count);
        List<TestEntity> all = repository.findAll();
        assertEquals(1, all.size());
        assertEquals("unique", all.get(0).getName());
        assertEquals(Integer.valueOf(99), all.get(0).getStatus());
    }

    @Test
    void testMergeWithPartialUpdateColumns() {
        TestEntity saved = repository.save(newEntity("original", 1));
        em.flush();
        em.clear();

        TestEntity entity = new TestEntity();
        entity.setId(saved.getId());
        entity.setName("updated");
        entity.setStatus(99);

        int count =
            new MergeSpec<>(TestEntity.class).withEntity(entity).updateOnConflict(TestEntity::getStatus).execute(em);
        em.flush();
        em.clear();

        assertEquals(1, count);
        TestEntity found = repository.findById(saved.getId()).orElseThrow();
        assertEquals("original", found.getName());
        assertEquals(Integer.valueOf(99), found.getStatus());
    }

    @Test
    void testMergeInsertThenUpdate() {
        TestEntity entity1 = newEntity("first", 1);
        int count1 = new MergeSpec<>(TestEntity.class).withEntity(entity1).onConflict(TestEntity::getName).execute(em);
        em.flush();
        em.clear();

        assertEquals(1, count1);
        TestEntity found1 =
            repository.findAll().stream().filter(e -> "first".equals(e.getName())).findFirst().orElseThrow();
        assertEquals(Integer.valueOf(1), found1.getStatus());

        TestEntity entity2 = new TestEntity();
        entity2.setName("first");
        entity2.setStatus(99);

        int count2 = new MergeSpec<>(TestEntity.class).withEntity(entity2).onConflict(TestEntity::getName).execute(em);
        em.flush();
        em.clear();

        assertEquals(1, count2);
        List<TestEntity> all = repository.findAll();
        assertEquals(1, all.size());
        assertEquals("first", all.get(0).getName());
        assertEquals(Integer.valueOf(99), all.get(0).getStatus());
    }

    @Test
    void testMergeExecuteInTransaction() {
        TestEntity entity = newEntity("tx", 1);

        int count = new MergeSpec<>(TestEntity.class).withEntity(entity).executeInTransaction(em);
        em.flush();
        em.clear();

        assertEquals(1, count);
        List<TestEntity> all = repository.findAll();
        assertEquals(1, all.size());
        assertEquals("tx", all.get(0).getName());
    }

    @Test
    void testMergeWithMultipleConflictColumns() {
        repository.save(newEntity("multi", 1));
        em.flush();
        em.clear();

        TestEntity entity = new TestEntity();
        entity.setName("multi");
        entity.setStatus(1);

        int count = new MergeSpec<>(TestEntity.class).withEntity(entity)
            .onConflict(TestEntity::getName, TestEntity::getStatus).execute(em);
        em.flush();
        em.clear();

        assertEquals(1, count);
    }

    @Test
    void testMergeNullEntityThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> new MergeSpec<>(TestEntity.class).withEntity(null));
    }

    @Test
    void testMergeNullEntityClassThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> new MergeSpec<>(null));
    }

    @Test
    void testMergeExecuteWithoutEntityThrowsException() {
        assertThrows(IllegalStateException.class, () -> new MergeSpec<>(TestEntity.class).execute(em));
    }

    @Test
    void testMergeNullEmThrowsException() {
        assertThrows(IllegalArgumentException.class,
            () -> new MergeSpec<>(TestEntity.class).withEntity(newEntity("a", 1)).execute(null));
    }

    @Test
    void testMergeOnConflictEmptyFieldsThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> new MergeSpec<>(TestEntity.class).onConflict());
    }

    @Test
    void testMergeUpdateOnConflictEmptyFieldsThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> new MergeSpec<>(TestEntity.class).updateOnConflict());
    }

    @Test
    void testMergeOnConflictNullFieldThrowsException() {
        assertThrows(IllegalArgumentException.class,
            () -> new MergeSpec<>(TestEntity.class).onConflict((com.zsubera.jpa.spec.SFunction<TestEntity, ?>)null));
    }

    @Test
    void testMergeUpdateOnConflictNullFieldThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> new MergeSpec<>(TestEntity.class)
            .updateOnConflict((com.zsubera.jpa.spec.SFunction<TestEntity, ?>)null));
    }

    private TestEntity newEntity(String name, int status) {
        TestEntity entity = new TestEntity();
        entity.setName(name);
        entity.setStatus(status);
        return entity;
    }
}
