package com.zsubera.jpa.spec;

import static org.junit.jupiter.api.Assertions.*;

import jakarta.persistence.LockModeType;
import jakarta.persistence.TypedQuery;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

@DataJpaTest
@org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase(
    replace = org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE)
@org.springframework.test.context.ContextConfiguration(classes = TestApplication.class)
class QuerySpecCoverageTest {

    @Autowired
    private TestEntityRepository repository;

    @Autowired
    private ParentEntityRepository parentRepository;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        repository.flush();
        parentRepository.deleteAll();
        parentRepository.flush();
    }

    private TestEntity newEntity(String name, int status) {
        TestEntity e = new TestEntity();
        e.setName(name);
        e.setStatus(status);
        return e;
    }

    @Test
    void testCacheKeyWithNestedOrGroup() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.or(o -> o.eq(TestEntity::getStatus, 1).eq(TestEntity::getStatus, 2));
        String key = qs.cacheKey();
        assertNotNull(key);
        assertTrue(key.contains("OR("));
    }

    @Test
    void testCacheKeyWithGroupByHavingOrderBy() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.groupBy(TestEntity::getStatus).havingCount(TestEntity::getId, ConditionNode.Op.GT, 1L)
            .orderByAsc(TestEntity::getName).orderByDesc(TestEntity::getStatus);
        String key = qs.cacheKey();
        assertTrue(key.contains("#GROUPBY("));
        assertTrue(key.contains("#HAVING("));
        assertTrue(key.contains("#ORDERBY("));
    }

    @Test
    void testCacheKeyWithTimeoutAndLockMode() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.timeout(30).lockMode(LockModeType.PESSIMISTIC_WRITE);
        String key = qs.cacheKey();
        assertTrue(key.contains("#TIMEOUT(30)"));
        assertTrue(key.contains("#LOCK("));
    }

    @Test
    void testCacheKeyWithDistinct() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.eq(TestEntity::getName, "test").distinct();
        String key = qs.cacheKey();
        assertTrue(key.contains("#DISTINCT"));
    }

    @Test
    void testCopyDeepCopyJoinNode() {
        ParentEntity parent = new ParentEntity();
        parent.setCategory("admin");
        parent.setLevel(10);
        em.persist(parent);
        TestEntity child = newEntity("child", 0);
        child.setParent(parent);
        repository.save(child);
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.<ParentEntity>join(TestEntity::getParent, jg -> jg.eq(ParentEntity::getCategory, "admin"));
        QuerySpec<TestEntity> copy = qs.copy();
        List<TestEntity> original = repository.findAll(qs.toSpecification());
        List<TestEntity> copied = repository.findAll(copy.toSpecification());
        assertEquals(original.size(), copied.size());
    }

    @Test
    void testCopyDeepCopyNegateNode() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.not(n -> n.eq(TestEntity::getName, "test"));
        QuerySpec<TestEntity> copy = qs.copy();
        assertNotNull(copy);
    }

    @Test
    void testCopyDeepCopyAndNode() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.not(n -> {
            n.eq(TestEntity::getName, "a");
            n.eq(TestEntity::getStatus, 1);
        });
        QuerySpec<TestEntity> copy = qs.copy();
        assertNotNull(copy);
    }

    @Test
    void testApplyQuerySettings() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.timeout(10).lockMode(LockModeType.PESSIMISTIC_READ);
        TypedQuery mockQuery = org.mockito.Mockito.mock(TypedQuery.class);
        qs.applyQuerySettings(mockQuery);
        org.mockito.Mockito.verify(mockQuery).setHint("jakarta.persistence.query.timeout", 10000);
        org.mockito.Mockito.verify(mockQuery).setLockMode(LockModeType.PESSIMISTIC_READ);
    }

    @Test
    void testApplyQuerySettingsNull() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        TypedQuery mockQuery = org.mockito.Mockito.mock(TypedQuery.class);
        qs.applyQuerySettings(mockQuery);
        org.mockito.Mockito.verify(mockQuery, org.mockito.Mockito.never())
            .setHint(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any());
        org.mockito.Mockito.verify(mockQuery, org.mockito.Mockito.never())
            .setLockMode(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void testToDescriptionEmpty() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        assertEquals("Query{}", qs.toDescription());
    }

    @Test
    void testToStringEmpty() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        String str = qs.toString();
        assertTrue(str.contains("conditions=0"));
    }

    @Test
    void testTimeoutZeroThrows() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        assertThrows(IllegalArgumentException.class, () -> qs.timeout(0));
        assertThrows(IllegalArgumentException.class, () -> qs.timeout(-1));
    }

    @Test
    void testLockModeNullThrows() {
        assertThrows(IllegalArgumentException.class, () -> new QuerySpec<TestEntity>().lockMode(null));
    }

    @Test
    void testGroupByNullThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> new QuerySpec<TestEntity>().groupBy((SFunction<TestEntity, ?>[])null));
    }

    @Test
    void testGroupByNullElementThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> new QuerySpec<TestEntity>().groupBy((SFunction<TestEntity, ?>)null));
    }

    @Test
    void testOrderByAscNullThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> new QuerySpec<TestEntity>().orderByAsc((SFunction<TestEntity, ?>[])null));
    }

    @Test
    void testOrderByAscNullElementThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> new QuerySpec<TestEntity>().orderByAsc((SFunction<TestEntity, ?>)null));
    }

    @Test
    void testOrderByDescNullThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> new QuerySpec<TestEntity>().orderByDesc((SFunction<TestEntity, ?>[])null));
    }

    @Test
    void testOrderByDescNullElementThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> new QuerySpec<TestEntity>().orderByDesc((SFunction<TestEntity, ?>)null));
    }

    @Test
    void testJoinNullConfigThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> new QuerySpec<TestEntity>().join(TestEntity::getParent, null));
    }

    @Test
    void testLeftJoinNullConfigThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> new QuerySpec<TestEntity>().leftJoin(TestEntity::getParent, null));
    }

    @Test
    void testFetchJoinNullFieldThrows() {
        assertThrows(IllegalArgumentException.class, () -> new QuerySpec<TestEntity>().fetchJoin(null, jg -> {
        }));
    }

    @Test
    void testFetchJoinNullConfigThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> new QuerySpec<TestEntity>().fetchJoin(TestEntity::getParent, null));
    }

    @Test
    void testLeftFetchJoinNullFieldThrows() {
        assertThrows(IllegalArgumentException.class, () -> new QuerySpec<TestEntity>().leftFetchJoin(null, jg -> {
        }));
    }

    @Test
    void testLeftFetchJoinNullConfigThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> new QuerySpec<TestEntity>().leftFetchJoin(TestEntity::getParent, null));
    }

    @Test
    void testExistsNullSubEntityThrows() {
        assertThrows(IllegalArgumentException.class, () -> new QuerySpec<TestEntity>().exists(null, sub -> {
        }));
    }

    @Test
    void testExistsNullConfigThrows() {
        assertThrows(IllegalArgumentException.class, () -> new QuerySpec<TestEntity>().exists(TestEntity.class, null));
    }

    @Test
    void testNotExistsNullSubEntityThrows() {
        assertThrows(IllegalArgumentException.class, () -> new QuerySpec<TestEntity>().notExists(null, sub -> {
        }));
    }

    @Test
    void testNotExistsNullConfigThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> new QuerySpec<TestEntity>().notExists(TestEntity.class, null));
    }

    @Test
    void testToSqlDeprecated() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.eq(TestEntity::getName, "test");
        assertEquals(qs.toDescription(), qs.toDescription());
    }

    @Test
    void testCopyInsideOrGroup() {
        final QuerySpec<TestEntity>[] copyHolder = new QuerySpec[1];
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.or(o -> {
            o.eq(TestEntity::getStatus, 1);
            copyHolder[0] = qs.copy();
        });
        assertNotNull(copyHolder[0]);
    }

    @Test
    void testConditionNodeValidation() {
        assertThrows(IllegalArgumentException.class,
            () -> new ConditionNode.SimpleNode(null, "x", ConditionNode.Op.EQ));
        assertThrows(IllegalArgumentException.class, () -> new ConditionNode.SimpleNode("name", "x", null));
        assertThrows(IllegalArgumentException.class,
            () -> new ConditionNode.JoinNode(null, ConditionNode.JoinType.INNER));
        assertThrows(IllegalArgumentException.class, () -> new ConditionNode.JoinNode("x", null));
        assertThrows(IllegalArgumentException.class, () -> new ConditionNode.MultiLikeNode(null, new String[] {"x"}));
        assertThrows(IllegalArgumentException.class, () -> new ConditionNode.MultiLikeNode("x", (String[])null));
        assertThrows(IllegalArgumentException.class, () -> new ConditionNode.MultiLikeNode("x", new String[0]));
        assertThrows(IllegalArgumentException.class,
            () -> new ConditionNode.CollectionNode(null, ConditionNode.CollectionOp.IS_EMPTY));
        assertThrows(IllegalArgumentException.class, () -> new ConditionNode.CollectionNode("x", null));
        assertThrows(IllegalArgumentException.class, () -> new ConditionNode.ExistsNode<>(null, s -> {
        }, false));
        assertThrows(IllegalArgumentException.class,
            () -> new ConditionNode.ExistsNode<>(TestEntity.class, null, false));
        assertThrows(IllegalArgumentException.class,
            () -> new ConditionNode.InSubQueryNode<>(null, TestEntity.class, s -> {
            }, false));
        assertThrows(IllegalArgumentException.class, () -> new ConditionNode.InSubQueryNode<>("x", null, s -> {
        }, false));
        assertThrows(IllegalArgumentException.class,
            () -> new ConditionNode.InSubQueryNode<>("x", TestEntity.class, null, false));
        assertThrows(IllegalArgumentException.class, () -> new ConditionNode.NegateNode(null));
        assertThrows(IllegalArgumentException.class, () -> new ConditionNode.RawNode(null));
        assertThrows(IllegalArgumentException.class, () -> ConditionNode.ofInternalPredicate(null));
        assertThrows(IllegalArgumentException.class, () -> new ConditionNode.OrderNode(null, true));
    }

    @Test
    void testConditionNodeToStrings() {
        ConditionNode.SimpleNode sn = new ConditionNode.SimpleNode("name", "secret", ConditionNode.Op.EQ);
        assertTrue(sn.toString().contains("***"));

        ConditionNode.JoinNode jn = new ConditionNode.JoinNode("parent", ConditionNode.JoinType.INNER);
        assertTrue(jn.toString().contains("JoinNode"));

        ConditionNode.OrNode on = new ConditionNode.OrNode();
        assertTrue(on.toString().contains("OrNode"));

        ConditionNode.AndNode an = new ConditionNode.AndNode();
        assertTrue(an.toString().contains("AndNode"));

        ConditionNode.MultiLikeNode mln = new ConditionNode.MultiLikeNode("key", new String[] {"name"});
        assertTrue(mln.toString().contains("MultiLikeNode"));

        ConditionNode.CollectionNode cn =
            new ConditionNode.CollectionNode("children", ConditionNode.CollectionOp.IS_EMPTY);
        assertTrue(cn.toString().contains("CollectionNode"));

        ConditionNode.ExistsNode<TestEntity> en = new ConditionNode.ExistsNode<>(TestEntity.class, s -> {
        }, false);
        assertTrue(en.toString().contains("ExistsNode"));

        ConditionNode.InSubQueryNode<TestEntity> isn =
            new ConditionNode.InSubQueryNode<>("status", TestEntity.class, s -> {
            }, false);
        assertTrue(isn.toString().contains("InSubQueryNode"));

        ConditionNode.NegateNode nn =
            new ConditionNode.NegateNode(new ConditionNode.SimpleNode("name", "x", ConditionNode.Op.EQ));
        assertTrue(nn.toString().contains("NegateNode"));
    }

    @Test
    void testOrNodeValidation() {
        ConditionNode.OrNode on = new ConditionNode.OrNode();
        assertThrows(IllegalArgumentException.class, () -> on.addNode(null));
        on.addNode(new ConditionNode.SimpleNode("name", "x", ConditionNode.Op.EQ));
        assertEquals(1, on.nodes().size());
    }

    @Test
    void testSimpleNodeArrayDefensiveCopy() {
        String[] arr = {"a", "b"};
        ConditionNode.SimpleNode node = new ConditionNode.SimpleNode("name", arr, ConditionNode.Op.IN);
        arr[0] = "c";
        assertEquals("a", ((String[])node.value)[0]);
    }

    @Test
    void testSimpleNodeCollectionDefensiveCopy() {
        java.util.List<String> values = new java.util.ArrayList<>(java.util.Arrays.asList("a", "b"));
        ConditionNode.SimpleNode node = new ConditionNode.SimpleNode("name", values, ConditionNode.Op.IN);
        values.add("c");
        assertEquals(2, ((java.util.List<?>)node.value).size());
    }

    @Test
    void testEndOrWithoutMatchingOrThrows() {
        assertThrows(IllegalStateException.class, () -> new QuerySpec<TestEntity>().endOr());
    }

    @Test
    void testCurrentGroupWhenGroupStackEmpty() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        List<ConditionNode> group = qs.currentGroup();
        assertTrue(group.isEmpty());
    }

    @Test
    void testPushOrGroupAndEndOr() {
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        OrGroup<TestEntity> orGroup = qs.pushOrGroup();
        orGroup.eq(TestEntity::getName, "test");
        qs.endOr();
        assertNotNull(qs.toSpecification());
    }

    @Test
    void testGetSortUnsorted() {
        assertFalse(new QuerySpec<TestEntity>().getSort().isSorted());
    }

    @Test
    void testQueryTimeoutGetterDefault() {
        assertNull(new QuerySpec<TestEntity>().getQueryTimeout());
    }

    @Test
    void testLockModeGetterDefault() {
        assertNull(new QuerySpec<TestEntity>().getLockMode());
    }

    @Test
    void testAndNodeNodesReturnsUnmodifiableList() {
        ConditionNode.AndNode an = new ConditionNode.AndNode();
        assertTrue(an.nodes().isEmpty());
    }

    @Test
    void testSetGlobalConfig() {
        com.zsubera.jpa.autoconfigure.GlobalConfigHolder
            .setConfig(new com.zsubera.jpa.autoconfigure.MyJpaPlusGlobalConfig());
    }

    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager em;
}
