package com.zsubera.jpa.integration;

import static org.junit.jupiter.api.Assertions.*;

import com.zsubera.jpa.exception.MyJpaPlusException;
import com.zsubera.jpa.projection.ProjectionSpec;
import com.zsubera.jpa.spec.QuerySpec;
import com.zsubera.jpa.update.AuditUtils;
import com.zsubera.jpa.update.MergeSpec;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Tuple;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

@Tag("integration")
@SpringBootTest
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:mysql://localhost:3306/test?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC",
    "spring.datasource.username=root", "spring.datasource.password=1351.zhong",
    "spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
    "spring.jpa.database-platform=org.hibernate.dialect.MySQLDialect", "spring.jpa.hibernate.ddl-auto=create"})
@Transactional
class MySQLRemainingGapsIntegrationTest {

    @Autowired
    private MySQLTestEntityRepository repository;
    @Autowired
    private MySQLParentEntityRepository parentRepository;

    @PersistenceContext
    private EntityManager em;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        parentRepository.deleteAll();
    }

    // ==================== ProjectionSpec: asDto + toDtoQuery ====================

    @Test
    void projection_asDto_toDtoQuery() {
        save("alice", 1);
        save("bob", 2);

        List<MySQLNameStatusDto> results = new ProjectionSpec<>(MySQLTestEntity.class).select(MySQLTestEntity::getName)
            .select(MySQLTestEntity::getStatus).asDto(MySQLNameStatusDto.class).<MySQLNameStatusDto>toDtoQuery(em)
            .getResultList();

        assertEquals(2, results.size());
        MySQLNameStatusDto first = results.stream().filter(d -> "alice".equals(d.name)).findFirst().orElse(null);
        assertNotNull(first);
        assertEquals(1, first.status);
    }

    @Test
    void projection_asDto_withWhere() {
        save("alice", 1);
        save("bob", 2);

        List<MySQLNameStatusDto> results = new ProjectionSpec<>(MySQLTestEntity.class).select(MySQLTestEntity::getName)
            .select(MySQLTestEntity::getStatus).where(q -> q.eq(MySQLTestEntity::getStatus, 1))
            .asDto(MySQLNameStatusDto.class).<MySQLNameStatusDto>toDtoQuery(em).getResultList();

        assertEquals(1, results.size());
        assertEquals("alice", results.get(0).name);
        assertEquals(1, results.get(0).status);
    }

    @Test
    void projection_asDto_throwsWithoutSelection() {
        ProjectionSpec<MySQLTestEntity> spec =
            new ProjectionSpec<>(MySQLTestEntity.class).asDto(MySQLNameStatusDto.class);
        assertThrows(Exception.class, () -> spec.toDtoQuery(em).getResultList());
    }

    // ==================== ProjectionSpec: getResultStream ====================

    @Test
    void projection_getResultStream() {
        save("a", 1);
        save("b", 2);
        save("c", 3);

        try (Stream<Tuple> stream = new ProjectionSpec<>(MySQLTestEntity.class).select(MySQLTestEntity::getName)
            .select(MySQLTestEntity::getStatus).getResultStream(em)) {

            List<Tuple> results = stream.toList();
            assertEquals(3, results.size());
        }
    }

    // ==================== ProjectionSpec: leftJoin ====================

    @Test
    void projection_leftJoin() {
        MySQLParentEntity parent = createParent("cat1", 1);
        save("child", 1, parent);
        save("orphan", 2);

        List<Tuple> results = new ProjectionSpec<>(MySQLTestEntity.class).select(MySQLTestEntity::getName)
            .select(MySQLTestEntity::getStatus)
            .<MySQLParentEntity>leftJoin(MySQLTestEntity::getParent, j -> j.eq(MySQLParentEntity::getCategory, "cat1"))
            .toTupleQuery(em).getResultList();

        assertEquals(2, results.size());
    }

    // ==================== ProjectionSpec: orderByDesc ====================

    @Test
    void projection_orderByDesc() {
        save("c", 3);
        save("a", 1);
        save("b", 2);

        List<Tuple> results = new ProjectionSpec<>(MySQLTestEntity.class).select(MySQLTestEntity::getName)
            .select(MySQLTestEntity::getStatus).orderByDesc(MySQLTestEntity::getStatus).toTupleQuery(em)
            .getResultList();

        assertEquals(3, results.size());
        assertEquals(3, results.get(0).get(1));
        assertEquals(2, results.get(1).get(1));
        assertEquals(1, results.get(2).get(1));
    }

    // ==================== ProjectionSpec: deep pagination config ====================

    @Test
    void projection_deepPaginationThreshold() {
        ProjectionSpec<MySQLTestEntity> spec = new ProjectionSpec<>(MySQLTestEntity.class)
            .select(MySQLTestEntity::getName).withDeepPaginationThreshold(500);

        assertNotNull(spec);
    }

    @Test
    void projection_deepPaginationThreshold_invalid() {
        assertThrows(IllegalArgumentException.class, () -> new ProjectionSpec<>(MySQLTestEntity.class)
            .select(MySQLTestEntity::getName).withDeepPaginationThreshold(0));
    }

    @Test
    void projection_deepPaginationLimit_invalid() {
        assertThrows(IllegalArgumentException.class, () -> new ProjectionSpec<>(MySQLTestEntity.class)
            .select(MySQLTestEntity::getName).withDeepPaginationLimit(0));
    }

    // ==================== QuerySpec: cacheKey ====================

    @Test
    void querySpec_cacheKey_basic() {
        QuerySpec<MySQLTestEntity> qs = new QuerySpec<>();
        qs.eq(MySQLTestEntity::getName, "test");
        String key1 = qs.cacheKey();

        QuerySpec<MySQLTestEntity> qs2 = new QuerySpec<>();
        qs2.eq(MySQLTestEntity::getName, "test");
        String key2 = qs2.cacheKey();

        assertEquals(key1, key2);
        assertTrue(key1.startsWith("Q:"));
    }

    @Test
    void querySpec_cacheKey_differentParams() {
        QuerySpec<MySQLTestEntity> qs1 = new QuerySpec<>();
        qs1.eq(MySQLTestEntity::getName, "alice");
        String key1 = qs1.cacheKey();

        QuerySpec<MySQLTestEntity> qs2 = new QuerySpec<>();
        qs2.eq(MySQLTestEntity::getName, "bob");
        String key2 = qs2.cacheKey();

        assertNotEquals(key1, key2);
    }

    @Test
    void querySpec_cacheKey_distinct() {
        QuerySpec<MySQLTestEntity> qs = new QuerySpec<>();
        qs.distinct();
        String key = qs.cacheKey();
        assertTrue(key.contains("DISTINCT"));
    }

    @Test
    void querySpec_cacheKey_groupBy() {
        QuerySpec<MySQLTestEntity> qs = new QuerySpec<>();
        qs.groupBy(MySQLTestEntity::getStatus);
        String key = qs.cacheKey();
        assertTrue(key.contains("GROUPBY"));
    }

    @Test
    void querySpec_cacheKey_orderBy() {
        QuerySpec<MySQLTestEntity> qs = new QuerySpec<>();
        qs.orderByAsc(MySQLTestEntity::getStatus);
        String key = qs.cacheKey();
        assertTrue(key.contains("ORDERBY"));
    }

    @Test
    void querySpec_cacheKey_empty() {
        QuerySpec<MySQLTestEntity> qs = new QuerySpec<>();
        String key = qs.cacheKey();
        assertTrue(key.startsWith("Q:"));
    }

    // ==================== QuerySpec: lockMode + getLockMode ====================

    @Test
    void querySpec_lockMode() {
        QuerySpec<MySQLTestEntity> qs = new QuerySpec<>();
        assertNull(qs.getLockMode());

        qs.lockMode(LockModeType.PESSIMISTIC_READ);
        assertEquals(LockModeType.PESSIMISTIC_READ, qs.getLockMode());
    }

    @Test
    void querySpec_lockMode_null_throws() {
        QuerySpec<MySQLTestEntity> qs = new QuerySpec<>();
        assertThrows(IllegalArgumentException.class, () -> qs.lockMode(null));
    }

    @Test
    void querySpec_lockMode_appliesToQuery() {
        save("test", 1);

        QuerySpec<MySQLTestEntity> qs = new QuerySpec<>();
        qs.eq(MySQLTestEntity::getName, "test");
        qs.lockMode(LockModeType.PESSIMISTIC_READ);

        List<MySQLTestEntity> results = repository.findAll(qs.toSpecification());
        assertEquals(1, results.size());
    }

    // ==================== QuerySpec: getQueryTimeout ====================

    @Test
    void querySpec_getQueryTimeout() {
        QuerySpec<MySQLTestEntity> qs = new QuerySpec<>();
        assertNull(qs.getQueryTimeout());

        qs.timeout(5);
        assertEquals(5, qs.getQueryTimeout());
    }

    // ==================== QuerySpec: having(Function) ====================

    @Test
    void querySpec_havingFunction_callable() {
        save("a", 10);
        save("b", 10);
        save("c", 20);

        QuerySpec<MySQLTestEntity> qs = new QuerySpec<>();
        qs.groupBy(MySQLTestEntity::getStatus);
        qs.having(root -> {
            jakarta.persistence.criteria.CriteriaBuilder cb = em.getCriteriaBuilder();
            return cb.greaterThan(cb.count(root.get("status")), 1L);
        });
        assertNotNull(qs);
        assertNotNull(qs.toSql());
        assertTrue(qs.toSql().contains("HAVING"));
    }

    // ==================== QuerySpec: toString + toSql ====================

    @Test
    void querySpec_toString() {
        QuerySpec<MySQLTestEntity> qs = new QuerySpec<>();
        qs.eq(MySQLTestEntity::getName, "test");
        qs.distinct();
        qs.timeout(5);
        String str = qs.toString();
        assertTrue(str.contains("QuerySpec{"));
        assertTrue(str.contains("conditions=1"));
        assertTrue(str.contains("distinct"));
        assertTrue(str.contains("timeout=5s"));
    }

    @Test
    void querySpec_toSql() {
        QuerySpec<MySQLTestEntity> qs = new QuerySpec<>();
        qs.eq(MySQLTestEntity::getName, "test");
        qs.orderByAsc(MySQLTestEntity::getName);
        String sql = qs.toSql();
        assertTrue(sql.startsWith("Query{"));
        assertTrue(sql.contains("WHERE:"));
        assertTrue(sql.contains("ORDER BY:"));
    }

    @Test
    void querySpec_toSql_empty() {
        QuerySpec<MySQLTestEntity> qs = new QuerySpec<>();
        String sql = qs.toSql();
        assertEquals("Query{}", sql);
    }

    // ==================== MergeSpec: executeBatchInSeparateTransactions ====================

    @Test
    void merge_executeBatchInSeparateTransactions_inActiveTx_throws() {
        List<MySQLTestEntity> entities = new ArrayList<>();
        MySQLTestEntity e = new MySQLTestEntity();
        e.setName("test1");
        e.setStatus(1);
        entities.add(e);

        MergeSpec<MySQLTestEntity> spec = new MergeSpec<>(MySQLTestEntity.class).onConflict(MySQLTestEntity::getName)
            .updateOnConflict(MySQLTestEntity::getStatus);

        assertThrows(MyJpaPlusException.class, () -> spec.executeBatchInSeparateTransactions(entities, em, 10));
    }

    // ==================== AuditUtils ====================

    @Test
    void auditUtils_setAndGetMaxStackDepth() {
        int original = AuditUtils.getMaxStackDepth();
        try {
            AuditUtils.setMaxStackDepth(10);
            assertEquals(10, AuditUtils.getMaxStackDepth());
        } finally {
            AuditUtils.setMaxStackDepth(original);
        }
    }

    @Test
    void auditUtils_setMaxStackDepth_invalid() {
        int original = AuditUtils.getMaxStackDepth();
        try {
            AuditUtils.setMaxStackDepth(0);
            assertEquals(original, AuditUtils.getMaxStackDepth());

            AuditUtils.setMaxStackDepth(-1);
            assertEquals(original, AuditUtils.getMaxStackDepth());

            AuditUtils.setMaxStackDepth(21);
            assertEquals(original, AuditUtils.getMaxStackDepth());
        } finally {
            AuditUtils.setMaxStackDepth(original);
        }
    }

    @Test
    void auditUtils_getCallStack() {
        String stack = AuditUtils.getCallStack();
        assertNotNull(stack);
        assertFalse(stack.isEmpty());
        assertTrue(stack.contains("auditUtils_getCallStack"));
    }

    @Test
    void auditUtils_getCallStack_respectsMaxDepth() {
        int original = AuditUtils.getMaxStackDepth();
        try {
            AuditUtils.setMaxStackDepth(2);
            String stack = AuditUtils.getCallStack();
            assertNotNull(stack);
            String[] parts = stack.split(" <- ");
            assertTrue(parts.length <= 2);
        } finally {
            AuditUtils.setMaxStackDepth(original);
        }
    }

    // ==================== Edge Cases: update/delete 0 rows ====================

    @Test
    void updateSpec_zeroRows() {
        save("existing", 1);

        com.zsubera.jpa.update.UpdateSpec<MySQLTestEntity> us =
            new com.zsubera.jpa.update.UpdateSpec<>(MySQLTestEntity.class);
        us.set(MySQLTestEntity::getStatus, 99);
        us.eq(MySQLTestEntity::getName, "nonexistent");
        int count = us.execute(em);

        assertEquals(0, count);
    }

    @Test
    void deleteSpec_zeroRows() {
        save("existing", 1);

        com.zsubera.jpa.update.DeleteSpec<MySQLTestEntity> ds =
            new com.zsubera.jpa.update.DeleteSpec<>(MySQLTestEntity.class);
        ds.eq(MySQLTestEntity::getName, "nonexistent");
        int count = ds.execute(em);

        assertEquals(0, count);
    }

    // ==================== Edge Cases: notIn with null values ====================

    @Test
    void notIn_allNull_returnsConjunction() {
        save("a", 1);
        save("b", 2);

        QuerySpec<MySQLTestEntity> qs = new QuerySpec<>();
        java.util.List<Integer> nullList = new java.util.ArrayList<>();
        nullList.add(null);
        qs.notIn(MySQLTestEntity::getStatus, nullList);
        List<MySQLTestEntity> result = repository.findAll(qs);
        assertEquals(2, result.size());
    }

    // ==================== Edge Cases: UpdateSpec setNull ====================

    @Test
    void updateSpec_setNull() {
        save("test", 1);

        com.zsubera.jpa.update.UpdateSpec<MySQLTestEntity> us =
            new com.zsubera.jpa.update.UpdateSpec<>(MySQLTestEntity.class);
        us.set(MySQLTestEntity::getStatus, (Integer)null);
        us.eq(MySQLTestEntity::getName, "test");
        us.execute(em);
        em.flush();
        em.clear();

        MySQLTestEntity result = repository.findByName("test").orElse(null);
        assertNotNull(result);
        assertNull(result.getStatus());
    }

    // ==================== Edge Cases: UpdateSpec notLike, notIn ====================

    @Test
    void updateSpec_notLike() {
        save("alice", 1);
        save("bob", 2);
        save("charlie", 3);

        com.zsubera.jpa.update.UpdateSpec<MySQLTestEntity> us =
            new com.zsubera.jpa.update.UpdateSpec<>(MySQLTestEntity.class);
        us.set(MySQLTestEntity::getStatus, 0);
        us.notLike(MySQLTestEntity::getName, "al");
        us.execute(em);
        em.flush();
        em.clear();

        assertEquals(Integer.valueOf(1), repository.findByName("alice").orElseThrow().getStatus());
        assertEquals(Integer.valueOf(0), repository.findByName("bob").orElseThrow().getStatus());
        assertEquals(Integer.valueOf(0), repository.findByName("charlie").orElseThrow().getStatus());
    }

    @Test
    void updateSpec_notIn() {
        save("a", 1);
        save("b", 2);
        save("c", 3);

        com.zsubera.jpa.update.UpdateSpec<MySQLTestEntity> us =
            new com.zsubera.jpa.update.UpdateSpec<>(MySQLTestEntity.class);
        us.set(MySQLTestEntity::getStatus, 0);
        us.notIn(MySQLTestEntity::getName, List.of("a", "b"));
        us.execute(em);
        em.flush();
        em.clear();

        assertEquals(Integer.valueOf(1), repository.findByName("a").orElseThrow().getStatus());
        assertEquals(Integer.valueOf(0), repository.findByName("c").orElseThrow().getStatus());
    }

    // ==================== Edge Cases: DeleteSpec notLike, notIn ====================

    @Test
    void deleteSpec_notLike() {
        save("alice", 1);
        save("bob", 2);
        save("charlie", 3);

        com.zsubera.jpa.update.DeleteSpec<MySQLTestEntity> ds =
            new com.zsubera.jpa.update.DeleteSpec<>(MySQLTestEntity.class);
        ds.notLike(MySQLTestEntity::getName, "li");
        ds.execute(em);
        em.flush();
        em.clear();

        assertEquals(2, repository.count());
    }

    @Test
    void deleteSpec_notIn() {
        save("a", 1);
        save("b", 2);
        save("c", 3);

        com.zsubera.jpa.update.DeleteSpec<MySQLTestEntity> ds =
            new com.zsubera.jpa.update.DeleteSpec<>(MySQLTestEntity.class);
        ds.notIn(MySQLTestEntity::getName, List.of("a", "b"));
        ds.execute(em);
        em.flush();
        em.clear();

        assertEquals(2, repository.count());
    }

    // ==================== Edge Cases: QuerySpec IN with empty list ====================

    @Test
    void querySpec_in_emptyList_throws() {
        assertThrows(IllegalArgumentException.class, () -> {
            QuerySpec<MySQLTestEntity> qs = new QuerySpec<>();
            qs.in(MySQLTestEntity::getName, List.of());
            repository.findAll(qs);
        });
    }

    // ==================== Helpers ====================

    private MySQLTestEntity save(String name, Integer status) {
        MySQLTestEntity e = new MySQLTestEntity();
        e.setName(name);
        e.setStatus(status);
        return repository.save(e);
    }

    private MySQLTestEntity save(String name, Integer status, MySQLParentEntity parent) {
        MySQLTestEntity e = new MySQLTestEntity();
        e.setName(name);
        e.setStatus(status);
        e.setParent(parent);
        return repository.save(e);
    }

    private MySQLParentEntity createParent(String category, Integer level) {
        MySQLParentEntity p = new MySQLParentEntity();
        p.setCategory(category);
        p.setLevel(level);
        return parentRepository.save(p);
    }
}
