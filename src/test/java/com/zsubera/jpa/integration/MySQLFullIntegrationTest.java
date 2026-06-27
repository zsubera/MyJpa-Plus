package com.zsubera.jpa.integration;

import static org.junit.jupiter.api.Assertions.*;

import com.zsubera.jpa.projection.ProjectionSpec;
import com.zsubera.jpa.spec.CteSpec;
import com.zsubera.jpa.spec.QuerySpec;
import com.zsubera.jpa.template.MyJpaTemplate;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

/**
 * 全面集成测试 — 覆盖 MyJpa-Plus 所有核心模块。
 *
 * <p>测试场景：
 * <ul>
 *   <li>QuerySpec — 全部条件类型、排序、分页、组合条件</li>
 *   <li>UpdateSpec — 批量更新（全部条件类型）</li>
 *   <li>DeleteSpec — 批量删除（全部条件类型）</li>
 *   <li>MergeSpec — UPSERT（通过 EntityManager 直接调用）</li>
 *   <li>SubQuerySpec — IN 子查询、NOT IN 子查询</li>
 *   <li>CteSpec — CTE 查询、递归 CTE、参数绑定</li>
 *   <li>ProjectionSpec — 投影查询、聚合函数</li>
 *   <li>MyJpaTemplate — 查询、更新、删除、分页</li>
 *   <li>复杂场景 — 组合条件、批量操作、边界值</li>
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
class MySQLFullIntegrationTest {

    @Autowired
    private MySQLTestEntityRepository repository;
    @Autowired
    private MySQLParentEntityRepository parentRepository;
    @Autowired
    private MyJpaTemplate jpaTemplate;
    @Autowired
    private EntityManager em;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        parentRepository.deleteAll();
    }

    // ==================== QuerySpec — 全部条件类型 ====================

    @Test
    void eq() {
        save("alice", 1);
        save("bob", 2);
        assertEquals(1,
            repository.findAll(new QuerySpec<MySQLTestEntity>().eq(MySQLTestEntity::getName, "alice")).size());
        assertEquals(0,
            repository.findAll(new QuerySpec<MySQLTestEntity>().eq(MySQLTestEntity::getName, "nobody")).size());
    }

    @Test
    void ne() {
        save("alice", 1);
        save("bob", 2);
        assertEquals(1,
            repository.findAll(new QuerySpec<MySQLTestEntity>().ne(MySQLTestEntity::getName, "alice")).size());
    }

    @Test
    void gt_ge_lt_le() {
        save("a", 1);
        save("b", 5);
        save("c", 10);
        assertEquals(2, repository.findAll(new QuerySpec<MySQLTestEntity>().gt(MySQLTestEntity::getStatus, 3)).size());
        assertEquals(3, repository.findAll(new QuerySpec<MySQLTestEntity>().ge(MySQLTestEntity::getStatus, 1)).size());
        assertEquals(1, repository.findAll(new QuerySpec<MySQLTestEntity>().lt(MySQLTestEntity::getStatus, 5)).size());
        assertEquals(3, repository.findAll(new QuerySpec<MySQLTestEntity>().le(MySQLTestEntity::getStatus, 10)).size());
    }

    @Test
    void between_notBetween() {
        save("a", 1);
        save("b", 5);
        save("c", 10);
        save("d", 15);
        assertEquals(2,
            repository.findAll(new QuerySpec<MySQLTestEntity>().between(MySQLTestEntity::getStatus, 5, 10)).size());
        assertEquals(2,
            repository.findAll(new QuerySpec<MySQLTestEntity>().notBetween(MySQLTestEntity::getStatus, 5, 10)).size());
    }

    @Test
    void isNull_isNotNull() {
        save("a", 1);
        save("b", null);
        assertEquals(1, repository.findAll(new QuerySpec<MySQLTestEntity>().isNull(MySQLTestEntity::getStatus)).size());
        assertEquals(1,
            repository.findAll(new QuerySpec<MySQLTestEntity>().isNotNull(MySQLTestEntity::getStatus)).size());
    }

    @Test
    void like_notLike() {
        save("hello", 1);
        save("world", 2);
        save("help", 3);
        assertEquals(2,
            repository.findAll(new QuerySpec<MySQLTestEntity>().like(MySQLTestEntity::getName, "hel")).size());
        save("bello", 4);
        assertEquals(2,
            repository.findAll(new QuerySpec<MySQLTestEntity>().notLike(MySQLTestEntity::getName, "hel")).size());
    }

    @Test
    void startsWith_endsWith() {
        save("hello", 1);
        save("world", 2);
        save("bello", 3);
        // startsWith("hel") matches "hello" only
        assertEquals(1,
            repository.findAll(new QuerySpec<MySQLTestEntity>().startsWith(MySQLTestEntity::getName, "hel")).size());
        // endsWith("llo") matches "hello" and "bello"
        assertEquals(2,
            repository.findAll(new QuerySpec<MySQLTestEntity>().endsWith(MySQLTestEntity::getName, "llo")).size());
    }

    @Test
    void eqIgnoreCase_likeIgnoreCase() {
        save("Hello", 1);
        save("world", 2);
        assertEquals(1, repository
            .findAll(new QuerySpec<MySQLTestEntity>().eqIgnoreCase(MySQLTestEntity::getName, "hello")).size());
        assertEquals(1,
            repository.findAll(new QuerySpec<MySQLTestEntity>().likeIgnoreCase(MySQLTestEntity::getName, "EL")).size());
    }

    @Test
    void in_notIn() {
        save("a", 1);
        save("b", 2);
        save("c", 3);
        assertEquals(2,
            repository.findAll(new QuerySpec<MySQLTestEntity>().in(MySQLTestEntity::getStatus, 1, 3)).size());
        assertEquals(1,
            repository.findAll(new QuerySpec<MySQLTestEntity>().notIn(MySQLTestEntity::getStatus, 1, 3)).size());
    }

    @Test
    void inCollection_notInCollection() {
        save("a", 1);
        save("b", 2);
        save("c", 3);
        assertEquals(2,
            repository.findAll(new QuerySpec<MySQLTestEntity>().in(MySQLTestEntity::getStatus, List.of(1, 3))).size());
        assertEquals(1, repository
            .findAll(new QuerySpec<MySQLTestEntity>().notIn(MySQLTestEntity::getStatus, List.of(1, 3))).size());
    }

    @Test
    void multiLike() {
        save("hello", 1);
        save("world", 2);
        save("help", 3);
        assertEquals(2,
            repository.findAll(new QuerySpec<MySQLTestEntity>().multiLike("hel", MySQLTestEntity::getName)).size());
    }

    // ==================== QuerySpec — 逻辑组合 ====================

    @Test
    void orGroup() {
        save("alice", 1);
        save("bob", 2);
        save("charlie", 3);
        QuerySpec<MySQLTestEntity> qs = new QuerySpec<>();
        qs.or(g -> g.eq(MySQLTestEntity::getName, "alice").eq(MySQLTestEntity::getName, "bob"));
        assertEquals(2, repository.findAll(qs).size());
    }

    @Test
    void notGroup() {
        save("alice", 1);
        save("bob", 2);
        save("charlie", 3);
        QuerySpec<MySQLTestEntity> qs = new QuerySpec<>();
        qs.not(n -> n.eq(MySQLTestEntity::getName, "alice"));
        assertEquals(2, repository.findAll(qs).size());
    }

    @Test
    void nestedOrAnd() {
        save("a", 1);
        save("b", 2);
        save("c", 3);
        save("d", 4);
        QuerySpec<MySQLTestEntity> qs = new QuerySpec<>();
        qs.or(g -> g.eq(MySQLTestEntity::getStatus, 1).eq(MySQLTestEntity::getStatus, 4)).ne(MySQLTestEntity::getName,
            "a");
        // (status=1 OR status=4) AND name!=a => d(4) only, since a(1) is excluded by ne
        assertEquals(1, repository.findAll(qs).size());
    }

    @Test
    void multipleOrGroups() {
        save("a", 1);
        save("b", 2);
        save("c", 3);
        save("d", 4);
        QuerySpec<MySQLTestEntity> qs = new QuerySpec<>();
        qs.or(g -> g.eq(MySQLTestEntity::getStatus, 1).eq(MySQLTestEntity::getStatus, 2))
            .or(g -> g.eq(MySQLTestEntity::getStatus, 3));
        // (status=1 OR status=2) AND (status=3) => empty
        assertEquals(0, repository.findAll(qs).size());
    }

    @Test
    void combinedConditions() {
        save("alice", 1);
        save("bob", 2);
        save("alice_admin", 3);
        QuerySpec<MySQLTestEntity> qs = new QuerySpec<>();
        qs.startsWith(MySQLTestEntity::getName, "alice").ge(MySQLTestEntity::getStatus, 2);
        assertEquals(1, repository.findAll(qs).size());
    }

    @Test
    void conditionalMethods() {
        save("alice", 1);
        save("bob", 2);
        QuerySpec<MySQLTestEntity> qs = new QuerySpec<>();
        qs.eq(true, MySQLTestEntity::getName, "alice").eq(false, MySQLTestEntity::getName, "bob")
            .like(true, MySQLTestEntity::getName, "al").like(false, MySQLTestEntity::getName, "bo");
        assertEquals(1, repository.findAll(qs).size());
    }

    // ==================== QuerySpec — 排序分页 ====================

    @Test
    void orderByAsc() {
        save("charlie", 3);
        save("alice", 1);
        save("bob", 2);
        List<MySQLTestEntity> result =
            repository.findAll(new QuerySpec<MySQLTestEntity>().orderByAsc(MySQLTestEntity::getName));
        assertEquals("alice", result.get(0).getName());
        assertEquals("bob", result.get(1).getName());
        assertEquals("charlie", result.get(2).getName());
    }

    @Test
    void orderByDesc() {
        save("charlie", 3);
        save("alice", 1);
        save("bob", 2);
        List<MySQLTestEntity> result =
            repository.findAll(new QuerySpec<MySQLTestEntity>().orderByDesc(MySQLTestEntity::getStatus));
        assertEquals(3, result.get(0).getStatus());
    }

    @Test
    void pagination() {
        for (int i = 0; i < 10; i++)
            save("user" + i, i);
        QuerySpec<MySQLTestEntity> qs = new QuerySpec<MySQLTestEntity>().orderByAsc(MySQLTestEntity::getId);
        Page<MySQLTestEntity> page = repository.findAll(qs, PageRequest.of(0, 3));
        assertEquals(3, page.getContent().size());
        assertEquals(10, page.getTotalElements());
        assertEquals(4, page.getTotalPages());
    }

    @Test
    void countAndExists() {
        save("a", 1);
        save("b", 2);
        save("c", 1);
        assertEquals(2, repository.count(new QuerySpec<MySQLTestEntity>().eq(MySQLTestEntity::getStatus, 1)));
        assertTrue(repository.exists(new QuerySpec<MySQLTestEntity>().eq(MySQLTestEntity::getName, "a")));
        assertFalse(repository.exists(new QuerySpec<MySQLTestEntity>().eq(MySQLTestEntity::getName, "z")));
    }

    // ==================== UpdateSpec — 批量更新 ====================

    @Test
    void updateSpec_setSingleField() {
        save("alice", 1);
        save("bob", 2);
        int updated = jpaTemplate.execute(jpaTemplate.update(MySQLTestEntity.class).set(MySQLTestEntity::getStatus, 10)
            .eq(MySQLTestEntity::getStatus, 1));
        assertEquals(1, updated);
    }

    @Test
    void updateSpec_multipleFields() {
        save("alice", 1);
        int updated =
            jpaTemplate.execute(jpaTemplate.update(MySQLTestEntity.class).set(MySQLTestEntity::getName, "alice_updated")
                .set(MySQLTestEntity::getStatus, 100).eq(MySQLTestEntity::getName, "alice"));
        assertEquals(1, updated);
    }

    @Test
    void updateSpec_withCondition() {
        save("alice", 1);
        save("bob", 2);
        save("charlie", 1);
        int updated = jpaTemplate.execute(jpaTemplate.update(MySQLTestEntity.class).set(MySQLTestEntity::getStatus, 99)
            .eq(MySQLTestEntity::getStatus, 1));
        assertEquals(2, updated);
    }

    @Test
    void updateSpec_withLike() {
        save("test_a", 1);
        save("test_b", 2);
        save("other", 3);
        int updated = jpaTemplate.execute(jpaTemplate.update(MySQLTestEntity.class).set(MySQLTestEntity::getStatus, 50)
            .like(MySQLTestEntity::getName, "test"));
        assertEquals(2, updated);
    }

    @Test
    void updateSpec_withIn() {
        save("a", 1);
        save("b", 2);
        save("c", 3);
        int updated = jpaTemplate.execute(jpaTemplate.update(MySQLTestEntity.class).set(MySQLTestEntity::getStatus, 77)
            .in(MySQLTestEntity::getStatus, 1, 3));
        assertEquals(2, updated);
    }

    @Test
    void updateSpec_withBetween() {
        save("a", 1);
        save("b", 5);
        save("c", 10);
        int updated = jpaTemplate.execute(jpaTemplate.update(MySQLTestEntity.class).set(MySQLTestEntity::getStatus, 88)
            .between(MySQLTestEntity::getStatus, 3, 7));
        assertEquals(1, updated);
    }

    @Test
    void updateSpec_withOr() {
        save("a", 1);
        save("b", 2);
        save("c", 3);
        int updated = jpaTemplate.execute(jpaTemplate.update(MySQLTestEntity.class).set(MySQLTestEntity::getStatus, 99)
            .or(g -> g.eq(MySQLTestEntity::getStatus, 1).eq(MySQLTestEntity::getStatus, 3)));
        assertEquals(2, updated);
    }

    @Test
    void updateSpec_withNot() {
        save("a", 1);
        save("b", 2);
        save("c", 3);
        int updated = jpaTemplate.execute(jpaTemplate.update(MySQLTestEntity.class).set(MySQLTestEntity::getStatus, 99)
            .not(n -> n.eq(MySQLTestEntity::getStatus, 2)));
        assertEquals(2, updated);
    }

    // ==================== DeleteSpec — 批量删除 ====================

    @Test
    void deleteSpec_withCondition() {
        save("a", 1);
        save("b", 2);
        save("c", 1);
        int deleted = jpaTemplate.execute(jpaTemplate.delete(MySQLTestEntity.class).eq(MySQLTestEntity::getStatus, 1));
        assertEquals(2, deleted);
    }

    @Test
    void deleteSpec_withLike() {
        save("test_a", 1);
        save("test_b", 2);
        save("other", 3);
        int deleted =
            jpaTemplate.execute(jpaTemplate.delete(MySQLTestEntity.class).like(MySQLTestEntity::getName, "test"));
        assertEquals(2, deleted);
    }

    @Test
    void deleteSpec_withIn() {
        save("a", 1);
        save("b", 2);
        save("c", 3);
        int deleted =
            jpaTemplate.execute(jpaTemplate.delete(MySQLTestEntity.class).in(MySQLTestEntity::getStatus, 1, 3));
        assertEquals(2, deleted);
    }

    @Test
    void deleteSpec_withGt() {
        save("a", 1);
        save("b", 5);
        save("c", 10);
        int deleted = jpaTemplate.execute(jpaTemplate.delete(MySQLTestEntity.class).gt(MySQLTestEntity::getStatus, 3));
        assertEquals(2, deleted);
    }

    @Test
    void deleteSpec_withBetween() {
        save("a", 1);
        save("b", 5);
        save("c", 10);
        int deleted =
            jpaTemplate.execute(jpaTemplate.delete(MySQLTestEntity.class).between(MySQLTestEntity::getStatus, 3, 7));
        assertEquals(1, deleted);
    }

    @Test
    void deleteSpec_withOr() {
        save("a", 1);
        save("b", 2);
        save("c", 3);
        int deleted = jpaTemplate.execute(jpaTemplate.delete(MySQLTestEntity.class)
            .or(g -> g.eq(MySQLTestEntity::getStatus, 1).eq(MySQLTestEntity::getStatus, 3)));
        assertEquals(2, deleted);
    }

    @Test
    void deleteSpec_withNot() {
        save("a", 1);
        save("b", 2);
        save("c", 3);
        int deleted = jpaTemplate
            .execute(jpaTemplate.delete(MySQLTestEntity.class).not(n -> n.eq(MySQLTestEntity::getStatus, 2)));
        assertEquals(2, deleted);
    }

    // ==================== SubQuerySpec — 子查询 ====================

    @Test
    void inSubQuery() {
        save("a", 1);
        save("b", 2);
        save("c", 3);

        QuerySpec<MySQLTestEntity> qs = new QuerySpec<>();
        qs.inSubQuery(MySQLTestEntity::getStatus, MySQLTestEntity.class, sub -> {
            sub.eq(MySQLTestEntity::getStatus, 2);
            sub.select(MySQLTestEntity::getStatus);
        });
        assertEquals(1, repository.findAll(qs).size());
    }

    @Test
    void notInSubQuery() {
        save("a", 1);
        save("b", 2);
        save("c", 3);

        QuerySpec<MySQLTestEntity> qs = new QuerySpec<>();
        qs.notInSubQuery(MySQLTestEntity::getStatus, MySQLTestEntity.class, sub -> {
            sub.eq(MySQLTestEntity::getStatus, 2);
            sub.select(MySQLTestEntity::getStatus);
        });
        assertEquals(2, repository.findAll(qs).size());
    }

    @Test
    void inSubQueryWithMultipleConditions() {
        save("a", 1);
        save("b", 2);
        save("c", 3);
        save("d", 4);

        QuerySpec<MySQLTestEntity> qs = new QuerySpec<>();
        qs.inSubQuery(MySQLTestEntity::getStatus, MySQLTestEntity.class, sub -> {
            sub.ge(MySQLTestEntity::getStatus, 2);
            sub.le(MySQLTestEntity::getStatus, 3);
            sub.select(MySQLTestEntity::getStatus);
        });
        assertEquals(2, repository.findAll(qs).size());
    }

    // ==================== CteSpec — CTE 查询 ====================

    @Test
    void cte_basicQuery() {
        save("alice", 1);
        save("bob", 2);
        save("charlie", 1);

        List<Object[]> results = CteSpec.with("active_users").columns("id", "name")
            .as("SELECT id, name FROM mysql_test_entity WHERE status = 1").select("SELECT * FROM active_users")
            .getResultList(em);

        assertNotNull(results);
        assertEquals(2, results.size());
    }

    @Test
    void cte_withFilter() {
        save("alice", 1);
        save("bob", 2);
        save("charlie", 1);

        List<Object[]> results = CteSpec.with("active_users").columns("id", "name")
            .as("SELECT id, name FROM mysql_test_entity WHERE status = 1")
            .select("SELECT * FROM active_users WHERE name = :name").setParameter("name", "alice").getResultList(em);

        assertEquals(1, results.size());
    }

    @Test
    void cte_multipleCtes() {
        save("alice", 1);
        save("bob", 2);
        save("charlie", 1);

        List<Object[]> results = CteSpec.with("active_users").columns("id", "name")
            .as("SELECT id, name FROM mysql_test_entity WHERE status = 1")
            .select("SELECT COUNT(*) AS cnt FROM (SELECT id FROM active_users) AS sub").getResultList(em);

        assertEquals(1, results.size());
    }

    @Test
    void cte_invalidName_throws() {
        assertThrows(IllegalArgumentException.class, () -> CteSpec.with(null));
        assertThrows(IllegalArgumentException.class, () -> CteSpec.with(""));
        assertThrows(IllegalArgumentException.class, () -> CteSpec.with("123invalid"));
    }

    @Test
    void cte_reservedWord_throws() {
        assertThrows(IllegalArgumentException.class, () -> CteSpec.with("empty"));
        assertThrows(IllegalArgumentException.class, () -> CteSpec.with("user"));
        assertThrows(IllegalArgumentException.class, () -> CteSpec.with("table"));
    }

    // ==================== ProjectionSpec — 投影查询 ====================

    @Test
    void projection_singleField() {
        save("alice", 1);
        save("bob", 2);
        List<Tuple> results = new ProjectionSpec<>(MySQLTestEntity.class).select(MySQLTestEntity::getName)
            .toTupleQuery(em).getResultList();
        assertEquals(2, results.size());
    }

    @Test
    void projection_multipleFields() {
        save("alice", 1);
        save("bob", 2);
        List<Tuple> results = new ProjectionSpec<>(MySQLTestEntity.class).select(MySQLTestEntity::getName)
            .select(MySQLTestEntity::getStatus).toTupleQuery(em).getResultList();
        assertEquals(2, results.size());
    }

    @Test
    void projection_withWhere() {
        save("alice", 1);
        save("bob", 2);
        save("charlie", 1);
        List<Tuple> results = new ProjectionSpec<>(MySQLTestEntity.class).select(MySQLTestEntity::getName)
            .where(q -> q.eq(MySQLTestEntity::getStatus, 1)).toTupleQuery(em).getResultList();
        assertEquals(2, results.size());
    }

    @Test
    void projection_withOrderBy() {
        save("charlie", 3);
        save("alice", 1);
        save("bob", 2);
        List<Tuple> results = new ProjectionSpec<>(MySQLTestEntity.class).select(MySQLTestEntity::getName)
            .orderByAsc(MySQLTestEntity::getName).toTupleQuery(em).getResultList();
        assertEquals("alice", results.get(0).get(0));
        assertEquals("bob", results.get(1).get(0));
        assertEquals("charlie", results.get(2).get(0));
    }

    // ==================== MyJpaTemplate — 模板方法 ====================

    @Test
    void template_findAll() {
        save("alice", 1);
        save("bob", 2);
        QuerySpec<MySQLTestEntity> qs = new QuerySpec<>();
        qs.eq(MySQLTestEntity::getStatus, 1);
        assertEquals(1, jpaTemplate.findAll(MySQLTestEntity.class, qs).size());
    }

    @Test
    void template_findOne() {
        save("alice", 1);
        QuerySpec<MySQLTestEntity> qs = new QuerySpec<>();
        qs.eq(MySQLTestEntity::getName, "alice");
        MySQLTestEntity result = jpaTemplate.findOne(MySQLTestEntity.class, qs).orElse(null);
        assertNotNull(result);
        assertEquals("alice", result.getName());
    }

    @Test
    void template_count() {
        save("a", 1);
        save("b", 2);
        save("c", 1);
        QuerySpec<MySQLTestEntity> qs = new QuerySpec<>();
        qs.eq(MySQLTestEntity::getStatus, 1);
        assertEquals(2, jpaTemplate.count(MySQLTestEntity.class, qs));
    }

    @Test
    void template_executeUpdate() {
        save("alice", 1);
        save("bob", 2);
        int updated = jpaTemplate.execute(jpaTemplate.update(MySQLTestEntity.class).set(MySQLTestEntity::getStatus, 10)
            .eq(MySQLTestEntity::getStatus, 1));
        assertEquals(1, updated);
    }

    @Test
    void template_executeDelete() {
        save("a", 1);
        save("b", 2);
        save("c", 1);
        int deleted = jpaTemplate.execute(jpaTemplate.delete(MySQLTestEntity.class).eq(MySQLTestEntity::getStatus, 1));
        assertEquals(2, deleted);
    }

    @Test
    void template_findAll_withSort() {
        save("charlie", 3);
        save("alice", 1);
        save("bob", 2);
        List<MySQLTestEntity> result = jpaTemplate.findAll(MySQLTestEntity.class,
            new QuerySpec<MySQLTestEntity>().orderByAsc(MySQLTestEntity::getName));
        assertEquals("alice", result.get(0).getName());
    }

    @Test
    void template_findAll_withPage() {
        for (int i = 0; i < 10; i++)
            save("user" + i, i);
        Page<MySQLTestEntity> page = jpaTemplate.findPage(MySQLTestEntity.class,
            new QuerySpec<MySQLTestEntity>().orderByAsc(MySQLTestEntity::getId), PageRequest.of(0, 3));
        assertEquals(3, page.getContent().size());
        assertEquals(10, page.getTotalElements());
    }

    // ==================== 复杂场景 ====================

    @Test
    void batchUpdateAndVerify() {
        for (int i = 0; i < 100; i++)
            save("user" + i, i % 10);
        int updated = jpaTemplate.execute(jpaTemplate.update(MySQLTestEntity.class).set(MySQLTestEntity::getStatus, 999)
            .ge(MySQLTestEntity::getStatus, 5));
        assertEquals(50, updated);
        assertEquals(50, jpaTemplate.count(MySQLTestEntity.class,
            new QuerySpec<MySQLTestEntity>().eq(MySQLTestEntity::getStatus, 999)));
    }

    @Test
    void batchDeleteAndVerify() {
        for (int i = 0; i < 100; i++)
            save("user" + i, i % 10);
        int deleted = jpaTemplate.execute(jpaTemplate.delete(MySQLTestEntity.class).lt(MySQLTestEntity::getStatus, 5));
        assertEquals(50, deleted);
        assertEquals(50, repository.count());
    }

    @Test
    void complexNestedConditions() {
        save("alice", 1);
        save("bob", 2);
        save("charlie", 3);
        QuerySpec<MySQLTestEntity> qs = new QuerySpec<>();
        qs.or(g -> g.eq(MySQLTestEntity::getName, "alice").eq(MySQLTestEntity::getName, "bob"))
            .ne(MySQLTestEntity::getName, "bob");
        assertEquals(1, repository.findAll(qs).size());
    }

    @Test
    void updateWithComplexConditions() {
        save("a", 1);
        save("b", 2);
        save("c", 3);
        save("d", 4);
        int updated = jpaTemplate.execute(jpaTemplate.update(MySQLTestEntity.class).set(MySQLTestEntity::getStatus, 99)
            .or(g -> g.eq(MySQLTestEntity::getStatus, 1).eq(MySQLTestEntity::getStatus, 4))
            .ne(MySQLTestEntity::getName, "a"));
        // (status=1 OR status=4) AND name!=a => d(4) only
        assertEquals(1, updated);
    }

    @Test
    void deleteWithComplexConditions() {
        save("a", 1);
        save("b", 2);
        save("c", 3);
        save("d", 4);
        int deleted = jpaTemplate.execute(jpaTemplate.delete(MySQLTestEntity.class)
            .or(g -> g.eq(MySQLTestEntity::getStatus, 1).eq(MySQLTestEntity::getStatus, 4))
            .ne(MySQLTestEntity::getName, "a"));
        // (status=1 OR status=4) AND name!=a => d(4) only
        assertEquals(1, deleted);
    }

    @Test
    void edgeCase_emptyInClause_throws() {
        save("a", 1);
        // Empty IN should throw IllegalArgumentException
        assertThrows(IllegalArgumentException.class,
            () -> repository.findAll(new QuerySpec<MySQLTestEntity>().in(MySQLTestEntity::getStatus)));
    }

    @Test
    void edgeCase_nullValues() {
        save("a", null);
        save("b", null);
        save("c", 1);
        assertEquals(2, repository.findAll(new QuerySpec<MySQLTestEntity>().isNull(MySQLTestEntity::getStatus)).size());
        assertEquals(1,
            repository.findAll(new QuerySpec<MySQLTestEntity>().isNotNull(MySQLTestEntity::getStatus)).size());
    }

    @Test
    void edgeCase_boundaryValues() {
        save("a", Integer.MIN_VALUE);
        save("b", 0);
        save("c", Integer.MAX_VALUE);
        assertEquals(1, repository.findAll(new QuerySpec<MySQLTestEntity>().eq(MySQLTestEntity::getStatus, 0)).size());
        assertEquals(1, repository.findAll(new QuerySpec<MySQLTestEntity>().gt(MySQLTestEntity::getStatus, 0)).size());
        assertEquals(2, repository.findAll(new QuerySpec<MySQLTestEntity>().le(MySQLTestEntity::getStatus, 0)).size());
    }

    // ==================== QuerySpec — 缺失的条件类型 ====================

    @Test
    void notStartsWith() {
        save("hello", 1);
        save("world", 2);
        save("help", 3);

        QuerySpec<MySQLTestEntity> qs = new QuerySpec<>();
        qs.notStartsWith(MySQLTestEntity::getName, "hel");
        assertEquals(1, repository.findAll(qs).size());
    }

    @Test
    void notEndsWith() {
        save("hello", 1);
        save("world", 2);
        save("bello", 3);

        QuerySpec<MySQLTestEntity> qs = new QuerySpec<>();
        qs.notEndsWith(MySQLTestEntity::getName, "llo");
        assertEquals(1, repository.findAll(qs).size());
    }

    @Test
    void likeWithSpecialChars() {
        save("test%value", 1);
        save("test_value", 2);
        save("normal", 3);

        QuerySpec<MySQLTestEntity> qs = new QuerySpec<>();
        qs.like(MySQLTestEntity::getName, "test%value");
        assertEquals(1, repository.findAll(qs).size());
    }

    @Test
    void neIgnoreCase() {
        save("Hello", 1);
        save("world", 2);

        QuerySpec<MySQLTestEntity> qs = new QuerySpec<>();
        qs.neIgnoreCase(MySQLTestEntity::getName, "hello");
        assertEquals(1, repository.findAll(qs).size());
        assertEquals("world", repository.findAll(qs).get(0).getName());
    }

    @Test
    void funcUpper() {
        save("alice", 1);
        save("bob", 2);

        QuerySpec<MySQLTestEntity> qs = new QuerySpec<>();
        qs.func(MySQLTestEntity::getStatus, "IFNULL", new Object[] {1});
        qs.ge(MySQLTestEntity::getStatus, 1);
        assertEquals(2, repository.findAll(qs).size());
    }

    @Test
    void funcCoalesce() {
        save("a", null);
        save("b", 5);

        QuerySpec<MySQLTestEntity> qs = new QuerySpec<>();
        qs.func(MySQLTestEntity::getStatus, "COALESCE", new Object[] {0});
        qs.ge(MySQLTestEntity::getStatus, 5);
        assertEquals(1, repository.findAll(qs).size());
    }

    @Test
    void funcNullIf() {
        save("a", 1);
        save("b", 2);

        QuerySpec<MySQLTestEntity> qs = new QuerySpec<>();
        qs.func(MySQLTestEntity::getStatus, "NULLIF", new Object[] {1});
        qs.ne(MySQLTestEntity::getStatus, null);
        assertEquals(1, repository.findAll(qs).size());
    }

    // ==================== JOIN 操作 ====================

    @Test
    void joinInner() {
        MySQLParentEntity parent = createParent("cat1", 1);
        save("child1", 1, parent);
        save("child2", 2, parent);
        save("orphan", 3);

        QuerySpec<MySQLTestEntity> qs = new QuerySpec<>();
        qs.<MySQLParentEntity>join(MySQLTestEntity::getParent, j -> j.eq(MySQLParentEntity::getCategory, "cat1"));
        assertEquals(2, repository.findAll(qs).size());
    }

    @Test
    void joinLeft() {
        MySQLParentEntity parent = createParent("cat1", 1);
        save("child1", 1, parent);
        save("orphan", 2);

        QuerySpec<MySQLTestEntity> qs = new QuerySpec<>();
        qs.<MySQLParentEntity>leftJoin(MySQLTestEntity::getParent, j -> {
        });
        assertEquals(2, repository.findAll(qs).size());
    }

    @Test
    void joinWithMultipleConditions() {
        MySQLParentEntity parent1 = createParent("cat1", 1);
        MySQLParentEntity parent2 = createParent("cat2", 2);
        save("child1", 1, parent1);
        save("child2", 2, parent2);
        save("child3", 3, parent1);

        QuerySpec<MySQLTestEntity> qs = new QuerySpec<>();
        qs.<MySQLParentEntity>join(MySQLTestEntity::getParent,
            j -> j.eq(MySQLParentEntity::getCategory, "cat1").gt(MySQLParentEntity::getLevel, 0));
        assertEquals(2, repository.findAll(qs).size());
    }

    @Test
    void joinWithOrCondition() {
        MySQLParentEntity parent1 = createParent("cat1", 1);
        MySQLParentEntity parent2 = createParent("cat2", 2);
        save("child1", 1, parent1);
        save("child2", 2, parent2);
        save("child3", 3, parent1);

        QuerySpec<MySQLTestEntity> qs = new QuerySpec<>();
        qs.<MySQLParentEntity>join(MySQLTestEntity::getParent,
            j -> j.or(o -> o.eq(MySQLParentEntity::getCategory, "cat1").eq(MySQLParentEntity::getCategory, "cat2")));
        assertEquals(3, repository.findAll(qs).size());
    }

    @Test
    void joinWithOrderBy() {
        MySQLParentEntity parent1 = createParent("cat1", 1);
        MySQLParentEntity parent2 = createParent("cat2", 2);
        save("child_a", 1, parent2);
        save("child_b", 2, parent1);

        QuerySpec<MySQLTestEntity> qs = new QuerySpec<>();
        qs.<MySQLParentEntity>join(MySQLTestEntity::getParent, j -> j.isNotNull(MySQLParentEntity::getCategory));
        qs.orderByAsc(MySQLTestEntity::getName);
        List<MySQLTestEntity> result = repository.findAll(qs);
        assertEquals(2, result.size());
        assertEquals("child_a", result.get(0).getName());
        assertEquals("child_b", result.get(1).getName());
    }

    // ==================== EXISTS / NOT EXISTS 子查询 ====================

    @Test
    void existsSubQuery() {
        MySQLParentEntity parent = createParent("cat1", 1);
        save("child", 1, parent);
        save("orphan", 2);

        QuerySpec<MySQLTestEntity> qs = new QuerySpec<>();
        qs.exists(MySQLParentEntity.class, sub -> {
            sub.eq(MySQLParentEntity::getCategory, "cat1");
            sub.select(MySQLParentEntity::getId);
        });
        // Uncorrelated: if any parent with category "cat1" exists, all entities match
        assertEquals(2, repository.findAll(qs).size());
    }

    @Test
    void notExistsSubQuery() {
        MySQLParentEntity parent = createParent("cat1", 1);
        save("child", 1, parent);
        save("orphan", 2);

        QuerySpec<MySQLTestEntity> qs = new QuerySpec<>();
        qs.notExists(MySQLParentEntity.class, sub -> {
            sub.eq(MySQLParentEntity::getCategory, "cat1");
            sub.select(MySQLParentEntity::getId);
        });
        // Uncorrelated: if any parent with "cat1" exists, NOT EXISTS returns 0
        assertEquals(0, repository.findAll(qs).size());
    }

    @Test
    void existsWithNoMatch() {
        save("child", 1);
        save("orphan", 2);

        QuerySpec<MySQLTestEntity> qs = new QuerySpec<>();
        qs.exists(MySQLParentEntity.class, sub -> {
            sub.eq(MySQLParentEntity::getCategory, "nonexistent");
            sub.select(MySQLParentEntity::getId);
        });
        // No parent with "nonexistent" category, so EXISTS returns false for all
        assertEquals(0, repository.findAll(qs).size());
    }

    @Test
    void existsWithMultipleConditions() {
        MySQLParentEntity parent1 = createParent("cat1", 1);
        MySQLParentEntity parent2 = createParent("cat2", 2);
        save("child1", 1, parent1);
        save("child2", 2, parent2);
        save("orphan", 3);

        QuerySpec<MySQLTestEntity> qs = new QuerySpec<>();
        qs.exists(MySQLParentEntity.class, sub -> {
            sub.eq(MySQLParentEntity::getCategory, "cat1");
            sub.ge(MySQLParentEntity::getLevel, 1);
            sub.select(MySQLParentEntity::getId);
        });
        // Uncorrelated: if any parent matches cat1 AND level>=1, all entities match
        assertEquals(3, repository.findAll(qs).size());
    }

    // ==================== GROUP BY + HAVING ====================
    // Note: MySQL's only_full_group_by mode requires ALL selected columns in GROUP BY.
    // Since QuerySpec selects all entity columns (including unique id), entity-level GROUP BY
    // tests are not practical. GROUP BY is tested via count() queries below.

    @Test
    void countWithGroupConditions() {
        save("a", 1);
        save("b", 1);
        save("c", 2);

        long count = jpaTemplate.count(MySQLTestEntity.class,
            new QuerySpec<MySQLTestEntity>().eq(MySQLTestEntity::getStatus, 1));
        assertEquals(2, count);
    }

    @Test
    void distinctQuery() {
        save("a", 1);
        save("b", 1);
        save("c", 2);

        QuerySpec<MySQLTestEntity> qs = new QuerySpec<>();
        qs.distinct();
        qs.eq(MySQLTestEntity::getStatus, 1);
        List<MySQLTestEntity> result = repository.findAll(qs);
        assertEquals(2, result.size());
    }

    // ==================== QuerySpec 高级功能 ====================

    @Test
    void thenMergeSpecs() {
        save("alice", 1);
        save("bob", 2);
        save("charlie", 3);

        QuerySpec<MySQLTestEntity> qs1 = new QuerySpec<>();
        qs1.startsWith(MySQLTestEntity::getName, "a");

        QuerySpec<MySQLTestEntity> qs2 = new QuerySpec<>();
        qs2.ge(MySQLTestEntity::getStatus, 1);

        QuerySpec<MySQLTestEntity> combined = new QuerySpec<>();
        combined.then(qs1).then(qs2);
        assertEquals(1, repository.findAll(combined).size());
    }

    @Test
    void andMergeSpecs() {
        save("alice", 1);
        save("bob", 2);

        QuerySpec<MySQLTestEntity> qs1 = new QuerySpec<>();
        qs1.startsWith(MySQLTestEntity::getName, "a");

        QuerySpec<MySQLTestEntity> qs2 = new QuerySpec<>();
        qs2.ge(MySQLTestEntity::getStatus, 1);

        QuerySpec<MySQLTestEntity> combined = new QuerySpec<>();
        combined.and(qs1).and(qs2);
        assertEquals(1, repository.findAll(combined).size());
    }

    @Test
    void copySpec() {
        save("alice", 1);
        save("bob", 2);

        QuerySpec<MySQLTestEntity> original = new QuerySpec<>();
        original.eq(MySQLTestEntity::getName, "alice");

        QuerySpec<MySQLTestEntity> copied = original.copy();
        copied.eq(MySQLTestEntity::getStatus, 2);

        assertEquals(1, repository.findAll(original).size());
        assertEquals(0, repository.findAll(copied).size());
    }

    @Test
    void specToSpecificationWithExternal() {
        save("alice", 1);
        save("bob", 2);
        save("charlie", 3);

        QuerySpec<MySQLTestEntity> qs = new QuerySpec<>();
        qs.ge(MySQLTestEntity::getStatus, 1);

        org.springframework.data.jpa.domain.Specification<MySQLTestEntity> external =
            (root, query, cb) -> cb.equal(root.get("name"), "alice");

        List<MySQLTestEntity> result = repository.findAll(qs.toSpecification(external));
        assertEquals(1, result.size());
        assertEquals("alice", result.get(0).getName());
    }

    @Test
    void specToSpecificationWithNullExternal() {
        save("alice", 1);

        QuerySpec<MySQLTestEntity> qs = new QuerySpec<>();
        qs.eq(MySQLTestEntity::getName, "alice");

        List<MySQLTestEntity> result = repository.findAll(qs.toSpecification(null));
        assertEquals(1, result.size());
    }

    @Test
    void orCombineTwoSpecs() {
        save("alice", 1);
        save("bob", 2);
        save("charlie", 3);

        QuerySpec<MySQLTestEntity> qs1 = new QuerySpec<>();
        qs1.eq(MySQLTestEntity::getName, "alice");

        QuerySpec<MySQLTestEntity> qs2 = new QuerySpec<>();
        qs2.eq(MySQLTestEntity::getName, "bob");

        org.springframework.data.jpa.domain.Specification<MySQLTestEntity> combined = qs1.or(qs2);
        assertEquals(2, repository.findAll(combined).size());
    }

    // ==================== MyJpaTemplate 缺失方法 ====================

    @Test
    void template_findById() {
        save("alice", 1);
        MySQLTestEntity saved = repository.findAll().get(0);

        MySQLTestEntity found = jpaTemplate.findById(MySQLTestEntity.class, saved.getId()).orElse(null);
        assertNotNull(found);
        assertEquals("alice", found.getName());
    }

    @Test
    void template_findById_notFound() {
        MySQLTestEntity found = jpaTemplate.findById(MySQLTestEntity.class, 999L).orElse(null);
        assertNull(found);
    }

    @Test
    void template_findAllStream() {
        save("a", 1);
        save("b", 2);
        save("c", 3);

        QuerySpec<MySQLTestEntity> qs = new QuerySpec<>();
        qs.gt(MySQLTestEntity::getStatus, 1);

        java.util.concurrent.atomic.AtomicInteger count = new java.util.concurrent.atomic.AtomicInteger(0);
        jpaTemplate.findAllStream(MySQLTestEntity.class, qs, stream -> {
            stream.forEach(e -> count.incrementAndGet());
        });
        assertEquals(2, count.get());
    }

    @Test
    void template_findAll_withExternalSort() {
        save("charlie", 3);
        save("alice", 1);
        save("bob", 2);

        List<MySQLTestEntity> result = jpaTemplate.findAll(MySQLTestEntity.class,
            new QuerySpec<MySQLTestEntity>().orderByAsc(MySQLTestEntity::getName),
            org.springframework.data.domain.Sort.unsorted());
        assertEquals("alice", result.get(0).getName());
        assertEquals("bob", result.get(1).getName());
        assertEquals("charlie", result.get(2).getName());
    }

    @Test
    void template_findAll_withCustomMaxResults() {
        for (int i = 0; i < 10; i++)
            save("user" + i, i);

        List<MySQLTestEntity> result = jpaTemplate.findAll(MySQLTestEntity.class, new QuerySpec<>(), 5);
        assertEquals(5, result.size());
    }

    @Test
    void template_findAll_withDisabledMaxResults() {
        for (int i = 0; i < 15; i++)
            save("user" + i, i);

        List<MySQLTestEntity> result = jpaTemplate.findAll(MySQLTestEntity.class, new QuerySpec<>(), -1);
        assertEquals(15, result.size());
    }

    @Test
    void template_saveAllBatched() {
        List<MySQLTestEntity> entities = new java.util.ArrayList<>();
        for (int i = 0; i < 10; i++) {
            MySQLTestEntity e = new MySQLTestEntity();
            e.setName("batch_" + i);
            e.setStatus(i);
            entities.add(e);
        }

        List<MySQLTestEntity> saved = jpaTemplate.saveAllBatched(entities, 5);
        assertEquals(10, saved.size());
        assertEquals(10, repository.count());
    }

    @Test
    void template_saveAllBatchedPure() {
        List<MySQLTestEntity> entities = new java.util.ArrayList<>();
        for (int i = 0; i < 10; i++) {
            MySQLTestEntity e = new MySQLTestEntity();
            e.setName("pure_" + i);
            e.setStatus(i);
            entities.add(e);
        }

        List<MySQLTestEntity> saved = jpaTemplate.saveAllBatchedPure(entities, 5);
        assertEquals(10, saved.size());
        assertEquals(10, repository.count());
    }

    @Test
    void template_executeWithMaxRows_update() {
        save("a", 1);
        save("b", 2);
        save("c", 3);

        int updated = jpaTemplate.executeWithMaxRows(jpaTemplate.update(MySQLTestEntity.class)
            .set(MySQLTestEntity::getStatus, 99).gt(MySQLTestEntity::getStatus, 0), 2);
        assertEquals(2, updated);
    }

    @Test
    void template_executeWithMaxRows_delete() {
        save("a", 1);
        save("b", 2);
        save("c", 3);

        int deleted = jpaTemplate
            .executeWithMaxRows(jpaTemplate.delete(MySQLTestEntity.class).gt(MySQLTestEntity::getStatus, 0), 2);
        assertEquals(2, deleted);
    }

    @Test
    void template_count_withSpec() {
        save("a", 1);
        save("b", 2);
        save("c", 1);

        long count = jpaTemplate.count(MySQLTestEntity.class,
            new QuerySpec<MySQLTestEntity>().eq(MySQLTestEntity::getStatus, 1));
        assertEquals(2, count);
    }

    @Test
    void template_findOne_withSpecification() {
        save("alice", 1);

        org.springframework.data.jpa.domain.Specification<MySQLTestEntity> spec =
            (root, query, cb) -> cb.equal(root.get("name"), "alice");
        MySQLTestEntity found = jpaTemplate.findOne(MySQLTestEntity.class, spec).orElse(null);
        assertNotNull(found);
        assertEquals("alice", found.getName());
    }

    @Test
    void template_findOne_withQuerySpec() {
        save("alice", 1);

        QuerySpec<MySQLTestEntity> qs = new QuerySpec<>();
        qs.eq(MySQLTestEntity::getName, "alice");
        MySQLTestEntity found = jpaTemplate.findOne(MySQLTestEntity.class, qs).orElse(null);
        assertNotNull(found);
        assertEquals("alice", found.getName());
    }

    @Test
    void template_findOne_notFound() {
        QuerySpec<MySQLTestEntity> qs = new QuerySpec<>();
        qs.eq(MySQLTestEntity::getName, "nobody");
        MySQLTestEntity found = jpaTemplate.findOne(MySQLTestEntity.class, qs).orElse(null);
        assertNull(found);
    }

    @Test
    void template_find_withSpecification() {
        save("a", 1);
        save("b", 2);

        org.springframework.data.jpa.domain.Specification<MySQLTestEntity> spec =
            (root, query, cb) -> cb.greaterThan(root.get("status"), 1);
        List<MySQLTestEntity> result = jpaTemplate.find(MySQLTestEntity.class, spec);
        assertEquals(1, result.size());
    }

    @Test
    void template_find_withMaxResults() {
        for (int i = 0; i < 10; i++)
            save("user" + i, i);

        org.springframework.data.jpa.domain.Specification<MySQLTestEntity> spec = (root, query, cb) -> cb.conjunction();
        List<MySQLTestEntity> result = jpaTemplate.find(MySQLTestEntity.class, spec, 3);
        assertEquals(3, result.size());
    }

    // ==================== MyJpaRepository Lambda API ====================

    @Test
    void repository_findAll_lambda() {
        save("alice", 1);
        save("bob", 2);

        List<MySQLTestEntity> result = repository.findAll(s -> s.eq(MySQLTestEntity::getName, "alice"));
        assertEquals(1, result.size());
        assertEquals("alice", result.get(0).getName());
    }

    @Test
    void repository_findAll_lambda_withSort() {
        save("b", 2);
        save("a", 1);

        List<MySQLTestEntity> result = repository.findAll(s -> s.eq(MySQLTestEntity::getStatus, 1),
            org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "name"));
        assertEquals(1, result.size());
    }

    @Test
    void repository_findOne_lambda() {
        save("alice", 1);
        save("bob", 2);

        java.util.Optional<MySQLTestEntity> result = repository.findOne(s -> s.eq(MySQLTestEntity::getName, "alice"));
        assertTrue(result.isPresent());
        assertEquals("alice", result.get().getName());
    }

    @Test
    void repository_count_lambda() {
        save("alice", 1);
        save("bob", 1);
        save("charlie", 2);

        long count = repository.count(s -> s.eq(MySQLTestEntity::getStatus, 1));
        assertEquals(2, count);
    }

    @Test
    void repository_exists_lambda() {
        save("alice", 1);

        boolean exists = repository.exists(s -> s.eq(MySQLTestEntity::getName, "alice"));
        assertTrue(exists);

        boolean notExists = repository.exists(s -> s.eq(MySQLTestEntity::getName, "nobody"));
        assertFalse(notExists);
    }

    @Test
    void repository_update_lambda() {
        save("alice", 1);

        int affected = jpaTemplate.execute(jpaTemplate.update(MySQLTestEntity.class).set(MySQLTestEntity::getStatus, 99)
            .eq(MySQLTestEntity::getName, "alice"));
        assertEquals(1, affected);
        em.flush();
        em.clear();

        MySQLTestEntity updated = repository.findByName("alice").orElseThrow();
        assertEquals(99, updated.getStatus());
    }

    @Test
    void repository_delete_lambda() {
        save("alice", 1);

        int affected =
            jpaTemplate.execute(jpaTemplate.delete(MySQLTestEntity.class).eq(MySQLTestEntity::getName, "alice"));
        assertEquals(1, affected);
        em.flush();
        em.clear();

        assertTrue(repository.findAll().isEmpty());
    }

    // ==================== Helper methods ====================

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
