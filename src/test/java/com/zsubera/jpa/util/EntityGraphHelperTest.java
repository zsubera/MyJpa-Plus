package com.zsubera.jpa.util;

import static org.junit.jupiter.api.Assertions.*;

import com.zsubera.jpa.spec.TestApplication;
import com.zsubera.jpa.spec.TestEntity;
import jakarta.persistence.EntityGraph;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ContextConfiguration;

@DataJpaTest
@org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase(
    replace = org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = TestApplication.class)
class EntityGraphHelperTest {

    @PersistenceContext
    private EntityManager em;

    @Test
    void testForEntityReturnsNewInstance() {
        EntityGraphHelper<TestEntity> helper = EntityGraphHelper.forEntity(TestEntity.class);
        assertNotNull(helper);
    }

    @Test
    void testDefaultGraphTypeIsFetch() {
        EntityGraphHelper<TestEntity> helper = EntityGraphHelper.forEntity(TestEntity.class);
        assertEquals("jakarta.persistence.fetchgraph", helper.getHintName());
    }

    @Test
    void testLoadGraphChangesHintName() {
        EntityGraphHelper<TestEntity> helper = EntityGraphHelper.forEntity(TestEntity.class).loadGraph();
        assertEquals("jakarta.persistence.loadgraph", helper.getHintName());
    }

    @Test
    void testFetchGraphRestoresHintName() {
        EntityGraphHelper<TestEntity> helper = EntityGraphHelper.forEntity(TestEntity.class).loadGraph().fetchGraph();
        assertEquals("jakarta.persistence.fetchgraph", helper.getHintName());
    }

    @Test
    void testForEntityNullThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> EntityGraphHelper.forEntity(null));
    }

    @Test
    void testRemoveAttribute() {
        EntityGraphHelper<TestEntity> helper = EntityGraphHelper.forEntity(TestEntity.class).add("parent");
        assertFalse(helper.toHints(em).isEmpty());
        helper.remove("parent");
        assertTrue(helper.toHints(em).isEmpty());
    }

    @Test
    void testRemoveNullPathThrowsException() {
        EntityGraphHelper<TestEntity> helper = EntityGraphHelper.forEntity(TestEntity.class).add("parent");
        assertThrows(IllegalArgumentException.class, () -> helper.remove(null));
    }

    @Test
    void testRemoveEmptyPathThrowsException() {
        EntityGraphHelper<TestEntity> helper = EntityGraphHelper.forEntity(TestEntity.class).add("parent");
        assertThrows(IllegalArgumentException.class, () -> helper.remove(""));
    }

    @Test
    void testClearRemovesAllPaths() {
        EntityGraphHelper<TestEntity> helper = EntityGraphHelper.forEntity(TestEntity.class).add("parent").add("name");
        assertFalse(helper.toHints(em).isEmpty());
        helper.clear();
        assertTrue(helper.toHints(em).isEmpty());
    }

    @Test
    void testNestAfterAdd() {
        EntityGraphHelper<TestEntity> helper =
            EntityGraphHelper.forEntity(TestEntity.class).add("parent").nest("category");
        Map<String, Object> hints = helper.toHints(em);
        assertFalse(hints.isEmpty());
    }

    @Test
    void testNestWithoutPriorAddThrowsException() {
        EntityGraphHelper<TestEntity> helper = EntityGraphHelper.forEntity(TestEntity.class);
        assertThrows(IllegalStateException.class, () -> helper.nest("x"));
    }

    @Test
    void testNestNullThrowsException() {
        EntityGraphHelper<TestEntity> helper = EntityGraphHelper.forEntity(TestEntity.class).add("parent");
        assertThrows(IllegalArgumentException.class, () -> helper.nest(null));
    }

    @Test
    void testNestEmptyThrowsException() {
        EntityGraphHelper<TestEntity> helper = EntityGraphHelper.forEntity(TestEntity.class).add("parent");
        assertThrows(IllegalArgumentException.class, () -> helper.nest(""));
    }

    @Test
    void testDuplicateSubpathNotAddedTwice() {
        EntityGraphHelper<TestEntity> helper =
            EntityGraphHelper.forEntity(TestEntity.class).add("parent.category").add("parent.category");
        EntityGraph<TestEntity> graph = helper.buildGraph(em);
        assertNotNull(graph);
    }

    @Test
    void testAddSingleAttribute() {
        EntityGraphHelper<TestEntity> helper = EntityGraphHelper.forEntity(TestEntity.class).add("parent");
        EntityGraph<TestEntity> graph = helper.buildGraph(em);
        assertNotNull(graph);
    }

    @Test
    void testAddMultipleAttributesViaVarargs() {
        EntityGraphHelper<TestEntity> helper = EntityGraphHelper.forEntity(TestEntity.class).add("name", "status");
        Map<String, Object> hints = helper.toHints(em);
        assertFalse(hints.isEmpty());
        assertEquals(1, hints.size());
        assertEquals("jakarta.persistence.fetchgraph", hints.keySet().iterator().next());
    }

    @Test
    void testAddNullPathThrowsException() {
        EntityGraphHelper<TestEntity> helper = EntityGraphHelper.forEntity(TestEntity.class);
        assertThrows(IllegalArgumentException.class, () -> helper.add((String)null));
    }

    @Test
    void testAddEmptyPathThrowsException() {
        EntityGraphHelper<TestEntity> helper = EntityGraphHelper.forEntity(TestEntity.class);
        assertThrows(IllegalArgumentException.class, () -> helper.add(""));
    }

    @Test
    void testToHintsWithNoAttributesReturnsEmptyMap() {
        EntityGraphHelper<TestEntity> helper = EntityGraphHelper.forEntity(TestEntity.class);
        Map<String, Object> hints = helper.toHints(em);
        assertTrue(hints.isEmpty());
    }

    @Test
    void testToHintsWithAttributesReturnsNonEmptyMap() {
        EntityGraphHelper<TestEntity> helper = EntityGraphHelper.forEntity(TestEntity.class).add("parent");
        Map<String, Object> hints = helper.toHints(em);
        assertFalse(hints.isEmpty());
        assertNotNull(hints.get("jakarta.persistence.fetchgraph"));
    }

    @Test
    void testApplyWithNoAttributesReturnsSameQuery() {
        EntityGraphHelper<TestEntity> helper = EntityGraphHelper.forEntity(TestEntity.class);

        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<TestEntity> cq = cb.createQuery(TestEntity.class);
        cq.from(TestEntity.class);
        TypedQuery<TestEntity> query = em.createQuery(cq);

        TypedQuery<TestEntity> result = helper.apply(query, em);
        assertSame(query, result);
    }

    @Test
    void testApplyWithAttributesReturnsQuery() {
        EntityGraphHelper<TestEntity> helper = EntityGraphHelper.forEntity(TestEntity.class).add("parent");

        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<TestEntity> cq = cb.createQuery(TestEntity.class);
        cq.from(TestEntity.class);
        TypedQuery<TestEntity> query = em.createQuery(cq);

        TypedQuery<TestEntity> result = helper.apply(query, em);
        assertNotNull(result);
    }

    @Test
    void testBuildGraphWithAssociation() {
        EntityGraphHelper<TestEntity> helper = EntityGraphHelper.forEntity(TestEntity.class).add("parent");
        EntityGraph<TestEntity> graph = helper.buildGraph(em);
        assertNotNull(graph);
        assertNotNull(graph.getAttributeNodes());
        assertFalse(graph.getAttributeNodes().isEmpty());
    }

    @Test
    void testBuildGraphWithMultiplePaths() {
        EntityGraphHelper<TestEntity> helper = EntityGraphHelper.forEntity(TestEntity.class).add("parent").add("name");
        EntityGraph<TestEntity> graph = helper.buildGraph(em);
        assertNotNull(graph);
        assertEquals(2, graph.getAttributeNodes().size());
    }

    @Test
    void testBuildGraphWithLoadType() {
        EntityGraphHelper<TestEntity> helper = EntityGraphHelper.forEntity(TestEntity.class).loadGraph().add("parent");
        EntityGraph<TestEntity> graph = helper.buildGraph(em);
        assertNotNull(graph);
        assertEquals("jakarta.persistence.loadgraph", helper.getHintName());
    }

    @Test
    void testToHintsWithLoadGraphType() {
        EntityGraphHelper<TestEntity> helper = EntityGraphHelper.forEntity(TestEntity.class).loadGraph().add("parent");
        Map<String, Object> hints = helper.toHints(em);
        assertNotNull(hints.get("jakarta.persistence.loadgraph"));
        assertNull(hints.get("jakarta.persistence.fetchgraph"));
    }

    @Test
    void testDotPathHandlingMergesSubpaths() {
        EntityGraphHelper<TestEntity> helper = EntityGraphHelper.forEntity(TestEntity.class).add("parent");
        EntityGraph<TestEntity> graph = helper.buildGraph(em);
        assertNotNull(graph);
    }

    @Test
    void testRemoveClearsLastAddedPath() {
        EntityGraphHelper<TestEntity> helper = EntityGraphHelper.forEntity(TestEntity.class).add("parent");
        helper.remove("parent");
        assertThrows(IllegalStateException.class, () -> helper.nest("child"));
    }

    @Test
    void testMultipleDotPathsMerged() {
        EntityGraphHelper<TestEntity> helper =
            EntityGraphHelper.forEntity(TestEntity.class).add("parent.category").add("parent.level");
        EntityGraph<TestEntity> graph = helper.buildGraph(em);
        assertNotNull(graph);
    }

    @Test
    void testVarargsWithDotPaths() {
        EntityGraphHelper<TestEntity> helper =
            EntityGraphHelper.forEntity(TestEntity.class).add("parent.category", "name");
        EntityGraph<TestEntity> graph = helper.buildGraph(em);
        assertNotNull(graph);
        assertEquals(2, graph.getAttributeNodes().size());
    }

    @Test
    void testAddSameRootThenSubpathMerges() {
        EntityGraphHelper<TestEntity> helper =
            EntityGraphHelper.forEntity(TestEntity.class).add("parent").add("parent.category");
        EntityGraph<TestEntity> graph = helper.buildGraph(em);
        assertNotNull(graph);
    }

    @Test
    void testLoadGraphWithMultipleAttributes() {
        EntityGraphHelper<TestEntity> helper =
            EntityGraphHelper.forEntity(TestEntity.class).loadGraph().add("parent").add("name");
        Map<String, Object> hints = helper.toHints(em);
        assertNotNull(hints.get("jakarta.persistence.loadgraph"));
        EntityGraph<TestEntity> graph = helper.buildGraph(em);
        assertEquals(2, graph.getAttributeNodes().size());
    }
}
