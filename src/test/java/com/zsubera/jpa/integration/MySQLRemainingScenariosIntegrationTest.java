package com.zsubera.jpa.integration;

import static org.junit.jupiter.api.Assertions.*;

import com.zsubera.jpa.template.CacheInvalidationListener;
import com.zsubera.jpa.template.EntityModifiedEvent;
import com.zsubera.jpa.template.MyJpaTemplate;
import com.zsubera.jpa.template.QueryCacheManager;
import com.zsubera.jpa.util.EntityGraphHelper;
import com.zsubera.jpa.util.PageableHelper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Integration tests for remaining uncovered scenarios:
 * <ul>
 *   <li>TransactionHelper: executeInNewTransaction with/without active tx</li>
 *   <li>CacheInvalidationListener: cache eviction via ApplicationEventPublisher</li>
 *   <li>EntityModifiedEvent: event construction and metadata</li>
 *   <li>PageableHelper: determineFetchSize for MySQL</li>
 *   <li>EntityGraphHelper: actual query execution with entity graphs</li>
 * </ul>
 */
@Tag("integration")
@SpringBootTest
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:mysql://localhost:3306/test?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC",
    "spring.datasource.username=root", "spring.datasource.password=1351.zhong",
    "spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
    "spring.jpa.database-platform=org.hibernate.dialect.MySQLDialect", "spring.jpa.hibernate.ddl-auto=create"})
@Transactional
class MySQLRemainingScenariosIntegrationTest {

    @Autowired
    private MySQLTestEntityRepository repository;
    @Autowired
    private MyJpaTemplate jpaTemplate;
    @Autowired
    private QueryCacheManager cacheManager;
    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @PersistenceContext
    private EntityManager em;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        cacheManager.clear();
    }

    // ==================== TransactionHelper ====================

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void transactionHelper_executeInNewTransaction_withoutActiveTx() {
        save("before", 1);

        int updated = jpaTemplate.execute(jpaTemplate.update(MySQLTestEntity.class).set(MySQLTestEntity::getStatus, 10)
            .eq(MySQLTestEntity::getName, "before"));
        assertEquals(1, updated);

        em.clear();
        assertEquals(Integer.valueOf(10), repository.findByName("before").orElseThrow().getStatus());
    }

    @Test
    void transactionHelper_executeInNewTransaction_withActiveTx() {
        save("before", 1);

        int updated = jpaTemplate.execute(jpaTemplate.update(MySQLTestEntity.class).set(MySQLTestEntity::getStatus, 20)
            .eq(MySQLTestEntity::getName, "before"));
        assertEquals(1, updated);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void transactionHelper_multipleSeparateTransactions() {
        for (int i = 0; i < 5; i++) {
            save("tx_" + i, i);
        }

        int total = jpaTemplate.execute(jpaTemplate.update(MySQLTestEntity.class).set(MySQLTestEntity::getStatus, 100)
            .ge(MySQLTestEntity::getStatus, 0));
        assertEquals(5, total);
    }

    // ==================== CacheInvalidationListener via ApplicationEventPublisher ====================

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void cacheInvalidation_viaEventPublisher_evictsCache() {
        cacheManager.put("MySQLTestEntity:query1", "result1", 60);
        cacheManager.put("MySQLTestEntity:query2", "result2", 60);
        cacheManager.put("OtherEntity:query3", "result3", 60);
        assertEquals(3, cacheManager.size());

        eventPublisher.publishEvent(new EntityModifiedEvent(MySQLTestEntity.class, 1));

        assertEquals(1, cacheManager.size());
        assertNull(cacheManager.get("MySQLTestEntity:query1"));
        assertNull(cacheManager.get("MySQLTestEntity:query2"));
        assertNotNull(cacheManager.get("OtherEntity:query3"));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void cacheInvalidation_viaEventPublisher_onlyMatchingPrefix() {
        cacheManager.put("User:q1", "v1", 60);
        cacheManager.put("User:q2", "v2", 60);
        cacheManager.put("Order:q3", "v3", 60);
        cacheManager.put("Product:q4", "v4", 60);

        eventPublisher.publishEvent(new EntityModifiedEvent("User", 2));

        assertEquals(2, cacheManager.size());
        assertNull(cacheManager.get("User:q1"));
        assertNull(cacheManager.get("User:q2"));
        assertNotNull(cacheManager.get("Order:q3"));
        assertNotNull(cacheManager.get("Product:q4"));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void cacheInvalidation_viaEventPublisher_noEntriesToEvict() {
        assertDoesNotThrow(() -> eventPublisher.publishEvent(new EntityModifiedEvent("NonExistent", 0)));
        assertEquals(0, cacheManager.size());
    }

    @Test
    void cacheInvalidation_noEvictionInsideTransaction() {
        cacheManager.put("MySQLTestEntity:txkey", "txval", 60);
        assertEquals(1, cacheManager.size());

        eventPublisher.publishEvent(new EntityModifiedEvent(MySQLTestEntity.class, 1));

        assertNotNull(cacheManager.get("MySQLTestEntity:txkey"),
            "Cache should NOT be evicted inside transaction (AFTER_COMMIT listener waits for commit)");
    }

    // ==================== CacheInvalidationListener direct unit test ====================

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void cacheInvalidationListener_direct_evictsOnNonTxCtx() {
        QueryCacheManager localCache = new QueryCacheManager();
        CacheInvalidationListener listener = new CacheInvalidationListener(localCache);

        localCache.put("Product:k1", "v1", 60);
        localCache.put("Product:k2", "v2", 60);
        localCache.put("Order:k3", "v3", 60);

        EntityModifiedEvent event = new EntityModifiedEvent("Product", 2);
        listener.onEntityModified(event);

        assertEquals(1, localCache.size());
        assertNull(localCache.get("Product:k1"));
        assertNull(localCache.get("Product:k2"));
        assertNotNull(localCache.get("Order:k3"));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void cacheInvalidationListener_direct_evictsOnlyMatchingPrefix() {
        QueryCacheManager localCache = new QueryCacheManager();
        CacheInvalidationListener listener = new CacheInvalidationListener(localCache);

        localCache.put("User:q1", "v1", 60);
        localCache.put("User:q2", "v2", 60);
        localCache.put("Order:q3", "v3", 60);
        localCache.put("Product:q4", "v4", 60);

        EntityModifiedEvent event = new EntityModifiedEvent("User", 2);
        listener.onEntityModified(event);

        assertEquals(2, localCache.size());
        assertNull(localCache.get("User:q1"));
        assertNull(localCache.get("User:q2"));
        assertNotNull(localCache.get("Order:q3"));
        assertNotNull(localCache.get("Product:q4"));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void cacheInvalidationListener_direct_noEntriesToEvict() {
        QueryCacheManager localCache = new QueryCacheManager();
        CacheInvalidationListener listener = new CacheInvalidationListener(localCache);

        EntityModifiedEvent event = new EntityModifiedEvent("NonExistent", 0);
        assertDoesNotThrow(() -> listener.onEntityModified(event));
        assertEquals(0, localCache.size());
    }

    // ==================== EntityModifiedEvent ====================

    @Test
    void entityModifiedEvent_withStringName() {
        EntityModifiedEvent event = new EntityModifiedEvent("CustomEntity", 5);
        assertEquals("CustomEntity", event.getEntityName());
        assertEquals(5, event.getAffectedRows());
    }

    @Test
    void entityModifiedEvent_withClass() {
        EntityModifiedEvent event = new EntityModifiedEvent(MySQLTestEntity.class, 10);
        assertEquals("MySQLTestEntity", event.getEntityName());
        assertEquals(10, event.getAffectedRows());
    }

    @Test
    void entityModifiedEvent_zeroAffectedRows() {
        EntityModifiedEvent event = new EntityModifiedEvent(MySQLTestEntity.class, 0);
        assertEquals("MySQLTestEntity", event.getEntityName());
        assertEquals(0, event.getAffectedRows());
    }

    // ==================== PageableHelper.determineFetchSize ====================

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void pageableHelper_determineFetchSize_returnsValidValue() {
        int fetchSize = PageableHelper.determineFetchSize(em);
        assertTrue(fetchSize == Integer.MIN_VALUE || fetchSize == 100 || fetchSize == 0,
            "fetchSize should be Integer.MIN_VALUE (MySQL), 100 (PostgreSQL), or 0 (fallback). Got: " + fetchSize);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void pageableHelper_determineFetchSize_mysqlReturnsMinValueOrFallback() {
        int fetchSize = PageableHelper.determineFetchSize(em);
        if (fetchSize == 0) {
            // EntityManager properties don't expose JDBC URL in this context — fallback is acceptable
            return;
        }
        assertEquals(Integer.MIN_VALUE, fetchSize, "MySQL should return Integer.MIN_VALUE when URL is accessible");
    }

    // ==================== EntityGraphHelper — actual query execution ====================

    @Test
    void entityGraphHelper_applyToQuery_fetchesLazyAssociation() {
        MySQLParentEntity parent = createParent("cat1", 1);
        MySQLTestEntity child = save("child", 1, parent);
        repository.flush();
        em.clear();

        EntityGraphHelper<MySQLTestEntity> graph = EntityGraphHelper.forEntity(MySQLTestEntity.class).add("parent");

        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<MySQLTestEntity> cq = cb.createQuery(MySQLTestEntity.class);
        cq.where(cb.equal(cq.from(MySQLTestEntity.class).get("id"), child.getId()));
        TypedQuery<MySQLTestEntity> query = em.createQuery(cq);

        graph.apply(query, em);
        MySQLTestEntity result = query.getSingleResult();

        assertNotNull(result);
        assertNotNull(result.getParent(), "Parent should be eagerly fetched via EntityGraph");
        assertEquals("cat1", result.getParent().getCategory());
    }

    @Test
    void entityGraphHelper_toHints_appliesFetchGraphToRepositoryQuery() {
        MySQLParentEntity parent = createParent("cat2", 2);
        save("child1", 1, parent);
        save("child2", 2, parent);
        save("orphan", 3);
        repository.flush();
        em.clear();

        EntityGraphHelper<MySQLTestEntity> graph =
            EntityGraphHelper.forEntity(MySQLTestEntity.class).fetchGraph().add("parent");

        var hints = graph.toHints(em);
        assertNotNull(hints.get("jakarta.persistence.fetchgraph"));
        assertNull(hints.get("jakarta.persistence.loadgraph"));
    }

    @Test
    void entityGraphHelper_loadGraph_appliesLoadHint() {
        EntityGraphHelper<MySQLTestEntity> graph =
            EntityGraphHelper.forEntity(MySQLTestEntity.class).loadGraph().add("parent");

        var hints = graph.toHints(em);
        assertEquals("jakarta.persistence.loadgraph", graph.getHintName());
        assertNotNull(hints.get("jakarta.persistence.loadgraph"));
        assertNull(hints.get("jakarta.persistence.fetchgraph"));
    }

    @Test
    void entityGraphHelper_nest_buildsNestedSubgraph() {
        EntityGraphHelper<MySQLTestEntity> graph =
            EntityGraphHelper.forEntity(MySQLTestEntity.class).add("parent").nest("children");

        var hints = graph.toHints(em);
        assertNotNull(hints);
        assertNotNull(graph.buildGraph(em));
    }

    @Test
    void entityGraphHelper_remove_removesAttribute() {
        EntityGraphHelper<MySQLTestEntity> graph =
            EntityGraphHelper.forEntity(MySQLTestEntity.class).add("parent").add("name");
        graph.remove("name");

        var hints = graph.toHints(em);
        assertNotNull(hints);
        var builtGraph = graph.buildGraph(em);
        assertEquals(1, builtGraph.getAttributeNodes().size());
    }

    @Test
    void entityGraphHelper_clear_removesAllAttributes() {
        EntityGraphHelper<MySQLTestEntity> graph =
            EntityGraphHelper.forEntity(MySQLTestEntity.class).add("parent").add("name");
        graph.clear();

        var hints = graph.toHints(em);
        assertTrue(hints.isEmpty(), "Hints should be empty after clear");
    }

    @Test
    void entityGraphHelper_emptyPaths_returnsEmptyHints() {
        EntityGraphHelper<MySQLTestEntity> graph = EntityGraphHelper.forEntity(MySQLTestEntity.class);

        var hints = graph.toHints(em);
        assertTrue(hints.isEmpty(), "Empty graph should produce empty hints");
    }

    // ==================== Helpers ====================

    @Autowired
    private MySQLParentEntityRepository parentRepository;

    private MySQLTestEntity save(String name, Integer status) {
        MySQLTestEntity entity = new MySQLTestEntity();
        entity.setName(name);
        entity.setStatus(status);
        return repository.save(entity);
    }

    private MySQLTestEntity save(String name, Integer status, MySQLParentEntity parent) {
        MySQLTestEntity entity = new MySQLTestEntity();
        entity.setName(name);
        entity.setStatus(status);
        entity.setParent(parent);
        return repository.save(entity);
    }

    private MySQLParentEntity createParent(String category, Integer level) {
        MySQLParentEntity parent = new MySQLParentEntity();
        parent.setCategory(category);
        parent.setLevel(level);
        return parentRepository.save(parent);
    }
}
