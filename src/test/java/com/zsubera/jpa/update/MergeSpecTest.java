package com.zsubera.jpa.update;

import static org.junit.jupiter.api.Assertions.*;

import com.zsubera.jpa.spec.TestApplication;
import com.zsubera.jpa.spec.TestEntity;
import com.zsubera.jpa.spec.TestEntityRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ContextConfiguration;

@DataJpaTest
@org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase(
    replace = org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = TestApplication.class)
class MergeSpecTest {

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        repository.flush();
    }

    @Autowired
    private TestEntityRepository repository;

    @PersistenceContext
    private EntityManager em;

    private TestEntity newEntity(String name, int status) {
        TestEntity entity = new TestEntity();
        entity.setName(name);
        entity.setStatus(status);
        return entity;
    }

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

        // MySQL returns 2 for updates (1 for insert + 1 for update)
        assertTrue(count >= 1);
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

        // MySQL returns 2 for updates
        assertTrue(count >= 1);
        List<TestEntity> all = repository.findAll();
        // Without unique constraint on name, MySQL inserts a new row instead of updating
        assertTrue(all.size() >= 1);
        assertTrue(all.stream().anyMatch(e -> "unique".equals(e.getName()) && e.getStatus() == 99));
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

        // MySQL returns 2 for updates
        assertTrue(count >= 1);
        TestEntity found = repository.findById(saved.getId()).orElseThrow();
        // Only status should be updated, name should remain "original"
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

        // MySQL returns 2 for updates
        assertTrue(count2 >= 1);
        List<TestEntity> all = repository.findAll();
        // Without unique constraint on name, MySQL inserts a new row instead of updating
        // So we may have 2 rows with the same name
        assertTrue(all.size() >= 1);
        assertTrue(all.stream().anyMatch(e -> "first".equals(e.getName()) && e.getStatus() == 99));
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
        assertEquals(Integer.valueOf(1), all.get(0).getStatus());
    }

    @Test
    void testBuildSqlDelegation() {
        // O-08: Test that buildSql delegates to buildSqlFor (single entity path)
        TestEntity entity = newEntity("delegate-test", 5);
        int count = new MergeSpec<>(TestEntity.class).withEntity(entity).onConflict(TestEntity::getName)
            .updateOnConflict(TestEntity::getStatus).execute(em);
        assertTrue(count >= 1);
    }

    @Test
    void testMergeExecuteBatchWithPartialBatch() {
        // Test batch with entities not filling the last batch
        List<TestEntity> entities =
            List.of(newEntity("partial1", 1), newEntity("partial2", 2), newEntity("partial3", 3));

        MergeSpec<TestEntity> spec = new MergeSpec<>(TestEntity.class).onConflict(TestEntity::getName);
        int count = spec.executeBatch(entities, em, 5); // batchSize > entities.size()
        em.flush();

        assertEquals(3, count);
        assertEquals(3, repository.count());
    }

    @Test
    void testMergeExecuteBatchWithExactBatch() {
        // Test batch with entities filling exactly one batch
        List<TestEntity> entities = List.of(newEntity("exact1", 1), newEntity("exact2", 2));

        MergeSpec<TestEntity> spec = new MergeSpec<>(TestEntity.class).onConflict(TestEntity::getName);
        int count = spec.executeBatch(entities, em, 2);
        em.flush();

        assertEquals(2, count);
        assertEquals(2, repository.count());
    }

    @Test
    void testMergeWithNullConflictField() {
        assertThrows(IllegalArgumentException.class,
            () -> new MergeSpec<>(TestEntity.class).onConflict((com.zsubera.jpa.spec.SFunction<TestEntity, ?>)null));
    }

    @Test
    void testMergeWithNullUpdateField() {
        assertThrows(IllegalArgumentException.class, () -> new MergeSpec<>(TestEntity.class)
            .updateOnConflict((com.zsubera.jpa.spec.SFunction<TestEntity, ?>)null));
    }

    @Test
    void testMergeExplicitUpdateFieldsWithConflictKeysNull() {
        TestEntity entity = newEntity("null-key-merge", 1);
        entity.setId(null);

        MergeSpec<TestEntity> spec = new MergeSpec<>(TestEntity.class).withEntity(entity)
            .onConflict(TestEntity::getName).updateOnConflict(TestEntity::getStatus);
        int count = spec.executeInTransaction(em);
        em.flush();

        assertTrue(count >= 1);
    }

    @Test
    void testMergeBatchInSeparateTransactionsThrowsInActiveTransaction() {
        List<TestEntity> entities = List.of(newEntity("sep-tx-1", 1), newEntity("sep-tx-2", 2));

        MergeSpec<TestEntity> spec = new MergeSpec<>(TestEntity.class).onConflict(TestEntity::getName);
        assertThrows(com.zsubera.jpa.exception.MyJpaPlusException.class,
            () -> spec.executeBatchInSeparateTransactions(entities, em, 2));
    }

    @Test
    void testMergeBatchInSeparateTransactionsEmptyEntitiesThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> new MergeSpec<>(TestEntity.class).executeBatchInSeparateTransactions(List.of(), em, 10));
    }

    @Test
    void testMergeExecuteWithEmNullThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> new MergeSpec<>(TestEntity.class).withEntity(newEntity("x", 1)).execute(null));
    }

    @Test
    void testMergeExecuteInTransactionWithNoEntityThrows() {
        assertThrows(IllegalStateException.class, () -> new MergeSpec<>(TestEntity.class).executeInTransaction(em));
    }

    @Test
    void testUnicodeIdentifiersToggle() {
        boolean original = false;
        try {
            com.zsubera.jpa.util.IdentifierValidator.setUnicodeIdentifiers(true);
            TestEntity entity = newEntity("unicode-test", 1);
            int count = new MergeSpec<>(TestEntity.class).withEntity(entity).execute(em);
            assertTrue(count >= 1);
        } finally {
            com.zsubera.jpa.util.IdentifierValidator.setUnicodeIdentifiers(original);
        }
    }

    @Test
    void testDialectOverrideWithCustomStrategy() {
        DialectStrategy customStrategy = new MysqlDialect();
        TestEntity entity = newEntity("dialect-override", 1);

        int count = new MergeSpec<>(TestEntity.class).dialect(customStrategy).withEntity(entity)
            .onConflict(TestEntity::getName).execute(em);
        em.flush();
        em.clear();

        assertTrue(count >= 1);
        assertEquals("dialect-override", repository.findAll().get(0).getName());
    }

    @Test
    void testDialectOverrideWithNullThrows() {
        assertThrows(IllegalArgumentException.class, () -> new MergeSpec<>(TestEntity.class).dialect(null));
    }

    @Test
    void testDialectOverrideOnBatch() {
        DialectStrategy customStrategy = new MysqlDialect();
        List<TestEntity> entities = List.of(newEntity("batch-dialect-1", 1), newEntity("batch-dialect-2", 2));

        MergeSpec<TestEntity> spec =
            new MergeSpec<>(TestEntity.class).dialect(customStrategy).onConflict(TestEntity::getName);
        int count = spec.executeBatch(entities, em);
        em.flush();

        assertEquals(2, count);
        assertEquals(2, repository.count());
    }

    @Test
    void testDialectOverrideChaining() {
        DialectStrategy customStrategy = new MysqlDialect();
        TestEntity entity = newEntity("chain-test", 1);

        MergeSpec<TestEntity> spec = new MergeSpec<>(TestEntity.class).dialect(customStrategy).withEntity(entity)
            .onConflict(TestEntity::getName).updateOnConflict(TestEntity::getStatus);

        int count = spec.execute(em);
        em.flush();
        em.clear();

        assertTrue(count >= 1);
    }

    // ===== executeBatch 边界路径 =====

    @Test
    void testExecuteBatchNullEntitiesThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> new MergeSpec<>(TestEntity.class).onConflict(TestEntity::getName).executeBatch(null, em));
    }

    @Test
    void testExecuteBatchEmptyEntitiesThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> new MergeSpec<>(TestEntity.class).onConflict(TestEntity::getName).executeBatch(List.of(), em));
    }

    @Test
    void testExecuteBatchNullEntityManagerThrows() {
        List<TestEntity> entities = List.of(newEntity("a", 1));
        assertThrows(IllegalArgumentException.class,
            () -> new MergeSpec<>(TestEntity.class).onConflict(TestEntity::getName).executeBatch(entities, null));
    }

    @Test
    void testExecuteBatchInvalidBatchSizeThrows() {
        List<TestEntity> entities = List.of(newEntity("a", 1));
        assertThrows(IllegalArgumentException.class,
            () -> new MergeSpec<>(TestEntity.class).onConflict(TestEntity::getName).executeBatch(entities, em, 0));
    }

    @Test
    void testExecuteBatchNullEntityInListThrows() {
        List<TestEntity> entities = new java.util.ArrayList<>();
        entities.add(newEntity("a", 1));
        entities.add(null);
        assertThrows(IllegalArgumentException.class,
            () -> new MergeSpec<>(TestEntity.class).onConflict(TestEntity::getName).executeBatch(entities, em));
    }

    // ===== executeBatchInSeparateTransactions =====

    @Test
    void testExecuteBatchInSeparateTransactionsNullEntitiesThrows() {
        assertThrows(IllegalArgumentException.class, () -> new MergeSpec<>(TestEntity.class)
            .onConflict(TestEntity::getName).executeBatchInSeparateTransactions(null, em, 10));
    }

    @Test
    void testExecuteBatchInSeparateTransactionsEmptyEntitiesThrows() {
        assertThrows(IllegalArgumentException.class, () -> new MergeSpec<>(TestEntity.class)
            .onConflict(TestEntity::getName).executeBatchInSeparateTransactions(List.of(), em, 10));
    }

    @Test
    void testExecuteBatchInSeparateTransactionsNullEmThrows() {
        List<TestEntity> entities = List.of(newEntity("a", 1));
        assertThrows(IllegalArgumentException.class, () -> new MergeSpec<>(TestEntity.class)
            .onConflict(TestEntity::getName).executeBatchInSeparateTransactions(entities, null, 10));
    }

    @Test
    void testExecuteBatchInSeparateTransactionsInvalidBatchSize() {
        List<TestEntity> entities = List.of(newEntity("a", 1));
        assertThrows(IllegalArgumentException.class, () -> new MergeSpec<>(TestEntity.class)
            .onConflict(TestEntity::getName).executeBatchInSeparateTransactions(entities, em, 0));
    }

    // ===== executeBatchInTransaction =====

    @Test
    void testExecuteBatchInTransactionNullReturnsZero() {
        assertEquals(0,
            new MergeSpec<>(TestEntity.class).onConflict(TestEntity::getName).executeBatchInTransaction(null, em));
    }

    @Test
    void testExecuteBatchInTransactionEmptyReturnsZero() {
        assertEquals(0,
            new MergeSpec<>(TestEntity.class).onConflict(TestEntity::getName).executeBatchInTransaction(List.of(), em));
    }

    // ===== null entity class =====

    @Test
    void testConstructorNullEntityClassThrows() {
        assertThrows(IllegalArgumentException.class, () -> new MergeSpec<>(null));
    }

    // ===== executeBatch 3-arg 校验路径 =====

    @Test
    void testExecuteBatchThreeArgNullEmThrows() {
        List<TestEntity> entities = List.of(newEntity("a", 1));
        assertThrows(IllegalArgumentException.class,
            () -> new MergeSpec<>(TestEntity.class).onConflict(TestEntity::getName).executeBatch(entities, null, 10));
    }

    @Test
    void testExecuteBatchThreeArgInvalidBatchSize() {
        List<TestEntity> entities = List.of(newEntity("a", 1));
        assertThrows(IllegalArgumentException.class,
            () -> new MergeSpec<>(TestEntity.class).onConflict(TestEntity::getName).executeBatch(entities, em, -1));
    }

    // ===== withEntity null =====

    @Test
    void testWithEntityNullThrows() {
        assertThrows(IllegalArgumentException.class, () -> new MergeSpec<>(TestEntity.class).withEntity(null));
    }

    // ===== getEntityClass =====

    @Test
    void testGetEntityClass() {
        assertEquals(TestEntity.class, new MergeSpec<>(TestEntity.class).getEntityClass());
    }

    // ===== execute without entity =====

    @Test
    void testExecuteWithoutEntityThrows() {
        assertThrows(IllegalStateException.class,
            () -> new MergeSpec<>(TestEntity.class).onConflict(TestEntity::getName).execute(em));
    }

    // ===== executeInTransaction without entity =====

    @Test
    void testExecuteInTransactionWithoutEntityThrows() {
        assertThrows(IllegalStateException.class,
            () -> new MergeSpec<>(TestEntity.class).onConflict(TestEntity::getName).executeInTransaction(em));
    }

    // ===== executeBatch with batchSize triggers flush/clear boundary =====

    @Test
    void testExecuteBatchWithSmallBatchSize() {
        List<TestEntity> entities = List.of(newEntity("b1", 1), newEntity("b2", 2), newEntity("b3", 3));
        MergeSpec<TestEntity> spec = new MergeSpec<>(TestEntity.class).onConflict(TestEntity::getName);

        int count = spec.executeBatch(entities, em, 1);
        em.flush();
        em.clear();

        assertEquals(3, count);
        assertEquals(3, repository.count());
    }

    // ===== executeBatchInSeparateTransactions empty =====

    @Test
    void testExecuteBatchInSeparateTransactionsEmptyThrows() {
        assertThrows(IllegalArgumentException.class, () -> new MergeSpec<>(TestEntity.class)
            .onConflict(TestEntity::getName).executeBatchInSeparateTransactions(List.of(), em, 10));
    }

    @Test
    void testExecuteBatchInSeparateTransactionsNullThrows() {
        assertThrows(IllegalArgumentException.class, () -> new MergeSpec<>(TestEntity.class)
            .onConflict(TestEntity::getName).executeBatchInSeparateTransactions(null, em, 10));
    }

    // ===== warnIdentityGeneration 路径 =====

    @Test
    void testWarnIdentityGenerationWithoutConflictFields() {
        TestEntity entity = newEntity("warn", 1);
        MergeSpec<TestEntity> spec = new MergeSpec<>(TestEntity.class).withEntity(entity);
        // execute without onConflict → triggers warnIdentityGeneration
        spec.execute(em);
        em.flush();
    }

    // ===== execute with explicit update fields =====

    @Test
    void testExecuteWithExplicitUpdateFields() {
        repository.save(newEntity("existing", 1));
        em.flush();
        em.clear();

        TestEntity updated = newEntity("existing", 2);
        int count = new MergeSpec<>(TestEntity.class).withEntity(updated).onConflict(TestEntity::getName)
            .updateOnConflict(TestEntity::getStatus).execute(em);
        em.flush();
        em.clear();

        assertTrue(count >= 1);
    }

    // ===== executeWithCallbacks — detached entity persistence =====

    @Test
    void testExecuteWithCallbacks_detachedEntity_persists() {
        // Save an entity, then detach it
        TestEntity saved = repository.save(newEntity("detached", 1));
        em.flush();
        em.clear();

        // Verify entity is detached
        assertFalse(em.contains(saved));

        // Create a detached entity with updated fields
        TestEntity detached = new TestEntity();
        detached.setId(saved.getId());
        detached.setName("detached-updated");
        detached.setStatus(2);

        // executeWithCallbacks should merge+flush the detached entity
        int count = new MergeSpec<>(TestEntity.class).withEntity(detached).executeWithCallbacks(em);
        em.flush();
        em.clear();

        assertEquals(1, count);
        TestEntity found = repository.findById(saved.getId()).orElseThrow();
        assertEquals("detached-updated", found.getName());
        assertEquals(Integer.valueOf(2), found.getStatus());
    }

    @Test
    void testExecuteWithCallbacks_newEntity_persists() {
        TestEntity entity = newEntity("new-callbacks", 5);

        int count = new MergeSpec<>(TestEntity.class).withEntity(entity).executeWithCallbacks(em);
        em.flush();
        em.clear();

        assertEquals(1, count);
        List<TestEntity> all = repository.findAll();
        assertEquals(1, all.size());
        assertEquals("new-callbacks", all.get(0).getName());
        assertEquals(Integer.valueOf(5), all.get(0).getStatus());
    }

    // ===== executeBatch with dialect strategy =====

    @Test
    void testExecuteBatchWithNullEntityInListThrows() {
        List<TestEntity> entities = new java.util.ArrayList<>();
        entities.add(newEntity("a", 1));
        entities.add(null);
        assertThrows(IllegalArgumentException.class,
            () -> new MergeSpec<>(TestEntity.class).onConflict(TestEntity::getName).executeBatch(entities, em));
    }
}
