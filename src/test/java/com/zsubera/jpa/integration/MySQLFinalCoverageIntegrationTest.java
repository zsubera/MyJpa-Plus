package com.zsubera.jpa.integration;

import static org.junit.jupiter.api.Assertions.*;

import com.zsubera.jpa.spec.ConditionNode;
import com.zsubera.jpa.spec.QuerySpec;
import com.zsubera.jpa.template.MyJpaTemplate;
import com.zsubera.jpa.template.QueryCacheManager;
import com.zsubera.jpa.update.UpdateSpec;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Tag("integration")
@SpringBootTest
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:mysql://localhost:3306/test?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC",
    "spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
    "spring.jpa.database-platform=org.hibernate.dialect.MySQLDialect", "spring.jpa.hibernate.ddl-auto=create"})
@Transactional
class MySQLFinalCoverageIntegrationTest {

    @Autowired
    private MySQLTestEntityRepository repository;
    @Autowired
    private MySQLParentEntityRepository parentRepository;
    @Autowired
    private MyJpaTemplate jpaTemplate;

    @PersistenceContext
    private EntityManager em;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        parentRepository.deleteAll();
    }

    // ==================== QuerySpec.havingCount/Sum/Avg/Max/Min (type-safe) ====================

    @Test
    void querySpec_havingCount_callable() {
        save("a", 10);
        save("b", 10);
        save("c", 20);

        QuerySpec<MySQLTestEntity> qs = new QuerySpec<>();
        qs.groupBy(MySQLTestEntity::getStatus);
        qs.havingCount(MySQLTestEntity::getStatus, ConditionNode.Op.GT, 1L);
        assertNotNull(qs.toDescription());
        assertTrue(qs.toDescription().contains("HAVING"));
    }

    @Test
    void querySpec_havingCount_nullField_throws() {
        QuerySpec<MySQLTestEntity> qs = new QuerySpec<>();
        assertThrows(IllegalArgumentException.class, () -> qs.havingCount(null, ConditionNode.Op.GT, 1L));
    }

    @Test
    void querySpec_havingCount_nullOp_throws() {
        QuerySpec<MySQLTestEntity> qs = new QuerySpec<>();
        assertThrows(IllegalArgumentException.class, () -> qs.havingCount(MySQLTestEntity::getStatus, null, 1L));
    }

    @Test
    void querySpec_havingSum_callable() {
        QuerySpec<MySQLTestEntity> qs = new QuerySpec<>();
        qs.groupBy(MySQLTestEntity::getStatus);
        qs.havingSum(MySQLTestEntity::getStatus, ConditionNode.Op.GT, 10);
        assertNotNull(qs.toDescription());
        assertTrue(qs.toDescription().contains("HAVING"));
    }

    @Test
    void querySpec_havingSum_nullValue_throws() {
        QuerySpec<MySQLTestEntity> qs = new QuerySpec<>();
        assertThrows(IllegalArgumentException.class,
            () -> qs.havingSum(MySQLTestEntity::getStatus, ConditionNode.Op.GT, null));
    }

    @Test
    void querySpec_havingAvg_callable() {
        QuerySpec<MySQLTestEntity> qs = new QuerySpec<>();
        qs.groupBy(MySQLTestEntity::getStatus);
        qs.havingAvg(MySQLTestEntity::getStatus, ConditionNode.Op.GT, 15.0);
        assertNotNull(qs.toDescription());
        assertTrue(qs.toDescription().contains("HAVING"));
    }

    @Test
    void querySpec_havingMax_callable() {
        QuerySpec<MySQLTestEntity> qs = new QuerySpec<>();
        qs.groupBy(MySQLTestEntity::getStatus);
        qs.havingMax(MySQLTestEntity::getStatus, ConditionNode.Op.GE, 20);
        assertNotNull(qs.toDescription());
        assertTrue(qs.toDescription().contains("HAVING"));
    }

    @Test
    void querySpec_havingMin_callable() {
        QuerySpec<MySQLTestEntity> qs = new QuerySpec<>();
        qs.groupBy(MySQLTestEntity::getStatus);
        qs.havingMin(MySQLTestEntity::getStatus, ConditionNode.Op.LE, 10);
        assertNotNull(qs.toDescription());
        assertTrue(qs.toDescription().contains("HAVING"));
    }

    // ==================== QuerySpec.groupBy null validation ====================

    @Test
    void querySpec_groupBy_nullFields_throws() {
        QuerySpec<MySQLTestEntity> qs = new QuerySpec<>();
        assertThrows(IllegalArgumentException.class,
            () -> qs.groupBy((com.zsubera.jpa.spec.SFunction<MySQLTestEntity, ?>[])null));
    }

    @Test
    void querySpec_having_null_throws() {
        QuerySpec<MySQLTestEntity> qs = new QuerySpec<>();
        assertThrows(IllegalArgumentException.class, () -> qs.having((Function<
            jakarta.persistence.criteria.Path<MySQLTestEntity>, jakarta.persistence.criteria.Predicate>)null));
    }

    // ==================== QuerySpec.timeout ====================

    @Test
    void querySpec_setTimeout() {
        QuerySpec<MySQLTestEntity> qs = new QuerySpec<>();
        qs.timeout(50);
        assertEquals(50, qs.getQueryTimeout().intValue());
    }

    // ==================== QuerySpec.copy deep verification ====================

    @Test
    void querySpec_copy_deepCopyVerification() {
        QuerySpec<MySQLTestEntity> original = new QuerySpec<>();
        original.eq(MySQLTestEntity::getName, "test");
        original.distinct();
        original.groupBy(MySQLTestEntity::getStatus);
        original.havingCount(MySQLTestEntity::getStatus, ConditionNode.Op.GT, 1L);
        original.orderByAsc(MySQLTestEntity::getName);
        original.timeout(5);
        original.lockMode(jakarta.persistence.LockModeType.PESSIMISTIC_READ);

        QuerySpec<MySQLTestEntity> copy = original.copy();

        assertEquals(original.cacheKey(), copy.cacheKey());
        assertEquals(original.getQueryTimeout(), copy.getQueryTimeout());
        assertEquals(original.getLockMode(), copy.getLockMode());

        copy.eq(MySQLTestEntity::getName, "modified");
        assertNotEquals(original.cacheKey(), copy.cacheKey());
    }

    // ==================== QuerySpec.cacheKey nested groups ====================

    @Test
    void querySpec_cacheKey_nestedGroups() {
        QuerySpec<MySQLTestEntity> qs = new QuerySpec<>();
        qs.eq(MySQLTestEntity::getName, "a");
        qs.or(g -> g.eq(MySQLTestEntity::getName, "b"));
        String key = qs.cacheKey();
        assertTrue(key.startsWith("Q:"));
        assertTrue(key.length() > 10);
    }

    // ==================== MyJpaTemplate.findAllCached ====================

    @Test
    void template_findAllCached() {
        QueryCacheManager cacheManager = new QueryCacheManager();
        jpaTemplate.setCacheManager(cacheManager);

        save("cached_a", 1);
        save("cached_b", 2);

        QuerySpec<MySQLTestEntity> spec = new QuerySpec<>();
        spec.eq(MySQLTestEntity::getStatus, 1);

        List<MySQLTestEntity> first = jpaTemplate.findAllCached(MySQLTestEntity.class, spec, 60);
        assertEquals(1, first.size());

        List<MySQLTestEntity> second = jpaTemplate.findAllCached(MySQLTestEntity.class, spec, 60);
        assertEquals(1, second.size());

        jpaTemplate.setCacheManager(null);
    }

    @Test
    void template_findAllCached_noCacheManager_throws() {
        jpaTemplate.setCacheManager(null);
        QuerySpec<MySQLTestEntity> spec = new QuerySpec<>();
        assertThrows(IllegalStateException.class, () -> jpaTemplate.findAllCached(MySQLTestEntity.class, spec, 60));
    }

    @Test
    void template_findAllCached_nullEntityClass_throws() {
        QueryCacheManager cacheManager = new QueryCacheManager();
        jpaTemplate.setCacheManager(cacheManager);
        try {
            QuerySpec<MySQLTestEntity> spec = new QuerySpec<>();
            assertThrows(IllegalArgumentException.class, () -> jpaTemplate.findAllCached(null, spec, 60));
        } finally {
            jpaTemplate.setCacheManager(null);
        }
    }

    @Test
    void template_findAllCached_negativeTtl_throws() {
        QueryCacheManager cacheManager = new QueryCacheManager();
        jpaTemplate.setCacheManager(cacheManager);
        try {
            QuerySpec<MySQLTestEntity> spec = new QuerySpec<>();
            assertThrows(IllegalArgumentException.class,
                () -> jpaTemplate.findAllCached(MySQLTestEntity.class, spec, -1));
        } finally {
            jpaTemplate.setCacheManager(null);
        }
    }

    // ==================== MyJpaTemplate.findKeysetPage ====================

    @Test
    void template_findKeysetPage_firstPage() {
        save("a", 3);
        save("b", 1);
        save("c", 2);

        Sort sort = Sort.by(Sort.Direction.ASC, "status");
        QuerySpec<MySQLTestEntity> spec = new QuerySpec<>();
        MyJpaTemplate.KeysetPage<MySQLTestEntity> page =
            jpaTemplate.findKeysetPage(MySQLTestEntity.class, spec.toSpecification(), sort, 2, null);

        assertEquals(2, page.content().size());
        assertTrue(page.hasNext());
        assertNotNull(page.lastSortValues());
    }

    @Test
    void template_findKeysetPage_secondPage() {
        save("a", 3);
        save("b", 1);
        save("c", 2);

        Sort sort = Sort.by(Sort.Direction.ASC, "status");
        QuerySpec<MySQLTestEntity> spec = new QuerySpec<>();
        MyJpaTemplate.KeysetPage<MySQLTestEntity> first =
            jpaTemplate.findKeysetPage(MySQLTestEntity.class, spec.toSpecification(), sort, 2, null);

        MyJpaTemplate.KeysetPage<MySQLTestEntity> second =
            jpaTemplate.findKeysetPage(MySQLTestEntity.class, spec.toSpecification(), sort, 2, first.lastSortValues());

        assertEquals(1, second.content().size());
        assertFalse(second.hasNext());
    }

    @Test
    void template_findKeysetPage_nullSort_throws() {
        QuerySpec<MySQLTestEntity> spec = new QuerySpec<>();
        assertThrows(IllegalArgumentException.class,
            () -> jpaTemplate.findKeysetPage(MySQLTestEntity.class, spec.toSpecification(), null, 10, null));
    }

    @Test
    void template_findKeysetPage_unsorted_throws() {
        QuerySpec<MySQLTestEntity> spec = new QuerySpec<>();
        assertThrows(IllegalArgumentException.class,
            () -> jpaTemplate.findKeysetPage(MySQLTestEntity.class, spec.toSpecification(), Sort.unsorted(), 10, null));
    }

    @Test
    void template_findKeysetPage_zeroPageSize_throws() {
        Sort sort = Sort.by(Sort.Direction.ASC, "status");
        QuerySpec<MySQLTestEntity> spec = new QuerySpec<>();
        assertThrows(IllegalArgumentException.class,
            () -> jpaTemplate.findKeysetPage(MySQLTestEntity.class, spec.toSpecification(), sort, 0, null));
    }

    @Test
    void template_findKeysetPage_mismatchedLastValues_throws() {
        Sort sort = Sort.by(Sort.Direction.ASC, "status");
        QuerySpec<MySQLTestEntity> spec = new QuerySpec<>();
        assertThrows(IllegalArgumentException.class, () -> jpaTemplate.findKeysetPage(MySQLTestEntity.class,
            spec.toSpecification(), sort, 10, new Object[] {1, "extra"}));
    }

    // ==================== MyJpaTemplate.executeBatch ====================

    @Test
    void template_executeBatch_update() {
        for (int i = 0; i < 5; i++) {
            save("batch_" + i, 1);
        }

        UpdateSpec<MySQLTestEntity> us = new UpdateSpec<>(MySQLTestEntity.class);
        us.set(MySQLTestEntity::getStatus, 99);
        us.eq(MySQLTestEntity::getStatus, 1);

        int count = jpaTemplate.executeBatch(us, 2);
        assertEquals(5, count);

        em.clear();
        assertEquals(5, repository.findAll().stream().filter(e -> e.getStatus() == 99).count());
    }

    @Test
    void template_executeBatch_delete() {
        for (int i = 0; i < 5; i++) {
            save("batch_" + i, i);
        }

        com.zsubera.jpa.update.DeleteSpec<MySQLTestEntity> ds =
            new com.zsubera.jpa.update.DeleteSpec<>(MySQLTestEntity.class);
        ds.lt(MySQLTestEntity::getStatus, 3);

        int count = jpaTemplate.executeBatch(ds, 2);
        assertEquals(3, count);
        assertEquals(2, repository.count());
    }

    // ==================== MyJpaTemplate.executeBatchInSeparateTransactions ====================

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void template_executeBatchInSeparateTransactions_update() {
        for (int i = 0; i < 5; i++) {
            save("sepbatch_" + i, 1);
        }

        UpdateSpec<MySQLTestEntity> us = new UpdateSpec<>(MySQLTestEntity.class);
        us.set(MySQLTestEntity::getStatus, 88);
        us.eq(MySQLTestEntity::getStatus, 1);

        int count = jpaTemplate.executeBatchInSeparateTransactions(us, 2);
        assertEquals(5, count);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void template_executeBatchInSeparateTransactions_delete() {
        for (int i = 0; i < 5; i++) {
            save("sepbatch_" + i, i);
        }

        com.zsubera.jpa.update.DeleteSpec<MySQLTestEntity> ds =
            new com.zsubera.jpa.update.DeleteSpec<>(MySQLTestEntity.class);
        ds.lt(MySQLTestEntity::getStatus, 3);

        int count = jpaTemplate.executeBatchInSeparateTransactions(ds, 2);
        assertEquals(3, count);
    }

    // ==================== MyJpaTemplate.executeBatchInSeparateTransactions with strategy ====================

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void template_executeBatchInSeparateTransactions_withStrategy() {
        for (int i = 0; i < 5; i++) {
            save("strat_" + i, 1);
        }

        UpdateSpec<MySQLTestEntity> us = new UpdateSpec<>(MySQLTestEntity.class);
        us.set(MySQLTestEntity::getStatus, 77);
        us.eq(MySQLTestEntity::getStatus, 1);

        MyJpaTemplate.BatchResult result =
            jpaTemplate.executeBatchInSeparateTransactions(us, 2, MyJpaTemplate.BatchFailureStrategy.CONTINUE);
        assertTrue(result.success());
        assertEquals(5, result.totalRows());
        assertTrue(result.batchCount() >= 1);
    }

    // ==================== MyJpaTemplate.setDeepPaginationOffsetLimit ====================

    @Test
    void template_setDeepPaginationOffsetLimit() {
        int original = -1;
        try {
            jpaTemplate.setDeepPaginationOffsetLimit(100);
            save("test", 1);
            QuerySpec<MySQLTestEntity> spec = new QuerySpec<>();
            List<MySQLTestEntity> result = jpaTemplate.findAll(MySQLTestEntity.class, spec, 10);
            assertEquals(1, result.size());
        } finally {
            jpaTemplate.setDeepPaginationOffsetLimit(original);
        }
    }

    // ==================== MyJpaTemplate.setMaxResults ====================

    @Test
    void template_setMaxResults_invalid_throws() {
        assertThrows(IllegalArgumentException.class, () -> jpaTemplate.setMaxResults(0));
    }

    @Test
    void template_setMaxResults_valid() {
        jpaTemplate.setMaxResults(100);
        save("test", 1);
        QuerySpec<MySQLTestEntity> spec = new QuerySpec<>();
        List<MySQLTestEntity> result = jpaTemplate.findAll(MySQLTestEntity.class, spec);
        assertEquals(1, result.size());
    }

    // ==================== QuerySpec.copy creates independent copy ====================

    @Test
    void querySpec_copy_createsIndependentSpec() {
        save("original", 1);
        save("modified", 2);

        QuerySpec<MySQLTestEntity> qs = new QuerySpec<>();
        qs.eq(MySQLTestEntity::getName, "original");

        QuerySpec<MySQLTestEntity> copy = qs.copy();
        copy.conditions().clear();
        copy.eq(MySQLTestEntity::getName, "modified");

        List<MySQLTestEntity> r1 = repository.findAll(qs);
        List<MySQLTestEntity> r2 = repository.findAll(copy);

        assertEquals(1, r1.size());
        assertEquals("original", r1.get(0).getName());
        assertEquals(1, r2.size());
        assertEquals("modified", r2.get(0).getName());
    }

    // ==================== UpdateSpec.withVersionIncrement ====================

    @Test
    void updateSpec_withVersionIncrement() {
        save("versioned", 1);
        MySQLTestEntity entity = repository.findByName("versioned").orElseThrow();

        UpdateSpec<MySQLTestEntity> us = new UpdateSpec<>(MySQLTestEntity.class);
        us.withVersionIncrement(true);
        us.set(MySQLTestEntity::getStatus, 2);
        us.eq(MySQLTestEntity::getId, entity.getId());
        us.execute(em);
        em.flush();
        em.clear();
    }

    // ==================== QueryCacheManager direct tests ====================

    @Test
    void cacheManager_putAndGet() {
        QueryCacheManager cm = new QueryCacheManager();
        cm.put("key1", "value1", 60);
        assertEquals("value1", cm.get("key1"));
    }

    @Test
    void cacheManager_eviction() {
        QueryCacheManager cm = new QueryCacheManager(2);
        cm.put("a", "val_a", 60);
        cm.put("b", "val_b", 60);
        cm.put("c", "val_c", 60);
        // Caffeine's maximumSize eviction is lazy; estimatedSize may be stale.
        // Verify the cache does not exceed the configured maximum.
        assertTrue(cm.size() <= 2, "Cache size should not exceed configured maximum of 2");
    }

    @Test
    void cacheManager_evictByPrefix() {
        QueryCacheManager cm = new QueryCacheManager();
        cm.put("prefix:1", "a", 60);
        cm.put("prefix:2", "b", 60);
        cm.put("other:1", "c", 60);

        int evicted = cm.evictByPrefix("prefix:");
        assertEquals(2, evicted);
        assertEquals(1, cm.size());
    }

    @Test
    void cacheManager_clear() {
        QueryCacheManager cm = new QueryCacheManager();
        cm.put("a", "val_a", 60);
        cm.put("b", "val_b", 60);
        cm.clear();
        assertEquals(0, cm.size());
    }

    @Test
    void cacheManager_expiry() {
        QueryCacheManager cm = new QueryCacheManager();
        cm.put("ttl_key", "ttl_val", 1);
        assertEquals("ttl_val", cm.get("ttl_key"));
        long deadline = System.nanoTime() + 5_000_000_000L;
        while (cm.get("ttl_key") != null && System.nanoTime() < deadline) {
            Thread.yield();
        }
        assertNull(cm.get("ttl_key"));
    }

    @Test
    void cacheManager_maxEntries_invalid() {
        QueryCacheManager cm = new QueryCacheManager();
        assertThrows(IllegalArgumentException.class, () -> cm.setMaxEntries(0));
    }

    // ==================== ConditionBuilder.func() ====================

    @Test
    void conditionBuilder_func_callable() {
        save("test", 1);

        QuerySpec<MySQLTestEntity> qs = new QuerySpec<>();
        qs.func(MySQLTestEntity::getStatus, "COALESCE", 0);
        qs.ge(MySQLTestEntity::getStatus, 0);

        List<MySQLTestEntity> result = repository.findAll(qs);
        assertEquals(1, result.size());
    }

    // ==================== MergeSpec.executeInTransaction MySQL ====================

    @Test
    void merge_executeInTransaction_mysql() {
        MySQLTestEntity entity = new MySQLTestEntity();
        entity.setName("merge_tx");
        entity.setStatus(1);

        int count = new com.zsubera.jpa.update.MergeSpec<>(MySQLTestEntity.class).withEntity(entity)
            .onConflict(MySQLTestEntity::getName).updateOnConflict(MySQLTestEntity::getStatus).executeInTransaction(em);
        em.flush();
        em.clear();

        assertTrue(count >= 1);
        assertEquals(Integer.valueOf(1), repository.findByName("merge_tx").orElseThrow().getStatus());
    }

    // ==================== EntityModifiedEvent ====================

    @Test
    void entityModifiedEvent_creation() {
        com.zsubera.jpa.template.EntityModifiedEvent event =
            new com.zsubera.jpa.template.EntityModifiedEvent(MySQLTestEntity.class, 5);
        assertEquals("com.zsubera.jpa.integration.MySQLTestEntity", event.getEntityName());
        assertEquals(5, event.getAffectedRows());
    }

    // ==================== saveAllBatchedInSeparateTransactions ====================

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void template_saveAllBatchedInSeparateTransactions_savesAll() {
        List<MySQLTestEntity> entities = new java.util.ArrayList<>();
        for (int i = 0; i < 5; i++) {
            MySQLTestEntity e = new MySQLTestEntity();
            e.setName("batch_" + i);
            e.setStatus(i);
            entities.add(e);
        }

        List<MySQLTestEntity> saved = jpaTemplate.saveAllBatchedInSeparateTransactions(entities, 2);
        assertEquals(5, saved.size());

        List<MySQLTestEntity> all = repository.findAll();
        assertEquals(5, all.size());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void template_saveAllBatchedInSeparateTransactions_updatesExisting() {
        MySQLTestEntity existing = save("original", 1);
        em.clear();

        existing.setName("updated");

        List<MySQLTestEntity> saved = jpaTemplate.saveAllBatchedInSeparateTransactions(List.of(existing), 10);
        assertEquals(1, saved.size());
        assertEquals("updated", saved.get(0).getName());

        em.clear();
        MySQLTestEntity found = repository.findById(existing.getId()).orElseThrow();
        assertEquals("updated", found.getName());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void template_saveAllBatchedInSeparateTransactions_emptyList() {
        List<MySQLTestEntity> saved = jpaTemplate.saveAllBatchedInSeparateTransactions(List.of(), 10);
        assertTrue(saved.isEmpty());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void template_saveAllBatchedInSeparateTransactions_throwsOnNull() {
        assertThrows(IllegalArgumentException.class, () -> jpaTemplate.saveAllBatchedInSeparateTransactions(null, 10));
    }

    // ==================== MyJpaTemplate.find with maxResults ====================

    @Test
    void template_find_positiveMaxResults() {
        save("a", 1);
        save("b", 2);
        save("c", 3);

        QuerySpec<MySQLTestEntity> spec = new QuerySpec<>();
        List<MySQLTestEntity> result = jpaTemplate.find(MySQLTestEntity.class, spec.toSpecification(), 2);
        assertEquals(2, result.size());
    }

    @Test
    void template_find_maxResults_exceedsTotal() {
        save("a", 1);
        save("b", 2);

        QuerySpec<MySQLTestEntity> spec = new QuerySpec<>();
        List<MySQLTestEntity> result = jpaTemplate.find(MySQLTestEntity.class, spec.toSpecification(), 10);
        assertEquals(2, result.size());
    }

    @Test
    void template_find_zeroMaxResults_throws() {
        QuerySpec<MySQLTestEntity> spec = new QuerySpec<>();
        assertThrows(IllegalArgumentException.class,
            () -> jpaTemplate.find(MySQLTestEntity.class, spec.toSpecification(), 0));
    }

    @Test
    void template_find_nullSpec_throws() {
        assertThrows(IllegalArgumentException.class, () -> jpaTemplate.find(MySQLTestEntity.class, null, 10));
    }

    // ==================== Helpers ====================

    private MySQLTestEntity save(String name, Integer status) {
        MySQLTestEntity e = new MySQLTestEntity();
        e.setName(name);
        e.setStatus(status);
        return repository.save(e);
    }

    private MySQLParentEntity createParent(String category, Integer level) {
        MySQLParentEntity p = new MySQLParentEntity();
        p.setCategory(category);
        p.setLevel(level);
        return parentRepository.save(p);
    }
}
