package com.zsubera.jpa.integration;

import static org.junit.jupiter.api.Assertions.*;

import com.zsubera.jpa.spec.QuerySpec;
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

@Tag("integration")
@SpringBootTest
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:mysql://localhost:3306/test?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC",
    "spring.datasource.username=root", "spring.datasource.password=1351.zhong",
    "spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
    "spring.jpa.database-platform=org.hibernate.dialect.MySQLDialect", "spring.jpa.hibernate.ddl-auto=create"})
@Transactional
class MySQLIntegrationTest {

    @Autowired
    private MySQLTestEntityRepository repository;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
    }

    // ==================== QuerySpec 基础条件 ====================

    @Test
    void testEq() {
        save("alice", 1);
        save("bob", 2);

        QuerySpec<MySQLTestEntity> qs = new QuerySpec<>();
        qs.eq(MySQLTestEntity::getName, "alice");
        assertEquals(1, repository.findAll(qs).size());
        assertEquals("alice", repository.findAll(qs).get(0).getName());
    }

    @Test
    void testNe() {
        save("alice", 1);
        save("bob", 2);

        QuerySpec<MySQLTestEntity> qs = new QuerySpec<>();
        qs.ne(MySQLTestEntity::getName, "alice");
        assertEquals(1, repository.findAll(qs).size());
        assertEquals("bob", repository.findAll(qs).get(0).getName());
    }

    @Test
    void testGt() {
        save("a", 1);
        save("b", 5);
        save("c", 10);

        QuerySpec<MySQLTestEntity> qs = new QuerySpec<>();
        qs.gt(MySQLTestEntity::getStatus, 3);
        assertEquals(2, repository.findAll(qs).size());
    }

    @Test
    void testGe() {
        save("a", 5);
        save("b", 10);

        QuerySpec<MySQLTestEntity> qs = new QuerySpec<>();
        qs.ge(MySQLTestEntity::getStatus, 5);
        assertEquals(2, repository.findAll(qs).size());
    }

    @Test
    void testLt() {
        save("a", 1);
        save("b", 5);
        save("c", 10);

        QuerySpec<MySQLTestEntity> qs = new QuerySpec<>();
        qs.lt(MySQLTestEntity::getStatus, 5);
        assertEquals(1, repository.findAll(qs).size());
    }

    @Test
    void testLe() {
        save("a", 5);
        save("b", 10);

        QuerySpec<MySQLTestEntity> qs = new QuerySpec<>();
        qs.le(MySQLTestEntity::getStatus, 10);
        assertEquals(2, repository.findAll(qs).size());
    }

    @Test
    void testBetween() {
        save("a", 1);
        save("b", 5);
        save("c", 10);
        save("d", 15);

        QuerySpec<MySQLTestEntity> qs = new QuerySpec<>();
        qs.between(MySQLTestEntity::getStatus, 5, 10);
        assertEquals(2, repository.findAll(qs).size());
    }

    @Test
    void testNotBetween() {
        save("a", 1);
        save("b", 5);
        save("c", 10);
        save("d", 15);

        QuerySpec<MySQLTestEntity> qs = new QuerySpec<>();
        qs.notBetween(MySQLTestEntity::getStatus, 5, 10);
        assertEquals(2, repository.findAll(qs).size());
    }

    @Test
    void testIsNull() {
        save("a", 1);
        save("b", null);

        QuerySpec<MySQLTestEntity> qs = new QuerySpec<>();
        qs.isNull(MySQLTestEntity::getStatus);
        assertEquals(1, repository.findAll(qs).size());
    }

    @Test
    void testIsNotNull() {
        save("a", 1);
        save("b", null);

        QuerySpec<MySQLTestEntity> qs = new QuerySpec<>();
        qs.isNotNull(MySQLTestEntity::getStatus);
        assertEquals(1, repository.findAll(qs).size());
    }

    // ==================== 字符串条件 ====================

    @Test
    void testLike() {
        save("hello", 1);
        save("world", 2);
        save("help", 3);

        QuerySpec<MySQLTestEntity> qs = new QuerySpec<>();
        qs.like(MySQLTestEntity::getName, "hel");
        assertEquals(2, repository.findAll(qs).size());
    }

    @Test
    void testStartsWith() {
        save("hello", 1);
        save("help", 2);
        save("world", 3);

        QuerySpec<MySQLTestEntity> qs = new QuerySpec<>();
        qs.startsWith(MySQLTestEntity::getName, "hel");
        assertEquals(2, repository.findAll(qs).size());
    }

    @Test
    void testEndsWith() {
        save("hello", 1);
        save("world", 2);
        save("bello", 3);

        QuerySpec<MySQLTestEntity> qs = new QuerySpec<>();
        qs.endsWith(MySQLTestEntity::getName, "llo");
        assertEquals(2, repository.findAll(qs).size());
    }

    @Test
    void testNotLike() {
        save("hello", 1);
        save("world", 2);

        QuerySpec<MySQLTestEntity> qs = new QuerySpec<>();
        qs.notLike(MySQLTestEntity::getName, "hel");
        assertEquals(1, repository.findAll(qs).size());
    }

    @Test
    void testEqIgnoreCase() {
        save("Hello", 1);
        save("world", 2);

        QuerySpec<MySQLTestEntity> qs = new QuerySpec<>();
        qs.eqIgnoreCase(MySQLTestEntity::getName, "hello");
        assertEquals(1, repository.findAll(qs).size());
    }

    @Test
    void testLikeIgnoreCase() {
        save("Hello", 1);
        save("world", 2);

        QuerySpec<MySQLTestEntity> qs = new QuerySpec<>();
        qs.likeIgnoreCase(MySQLTestEntity::getName, "EL");
        assertEquals(1, repository.findAll(qs).size());
    }

    // ==================== IN 条件 ====================

    @Test
    void testIn() {
        save("a", 1);
        save("b", 2);
        save("c", 3);

        QuerySpec<MySQLTestEntity> qs = new QuerySpec<>();
        qs.in(MySQLTestEntity::getStatus, 1, 3);
        assertEquals(2, repository.findAll(qs).size());
    }

    @Test
    void testNotIn() {
        save("a", 1);
        save("b", 2);
        save("c", 3);

        QuerySpec<MySQLTestEntity> qs = new QuerySpec<>();
        qs.notIn(MySQLTestEntity::getStatus, 1, 3);
        assertEquals(1, repository.findAll(qs).size());
    }

    // ==================== 多字段搜索 ====================

    @Test
    void testMultiLike() {
        save("hello", 1);
        save("world", 2);
        save("help", 3);

        QuerySpec<MySQLTestEntity> qs = new QuerySpec<>();
        qs.multiLike("hel", MySQLTestEntity::getName);
        assertEquals(2, repository.findAll(qs).size());
    }

    // ==================== OR 条件 ====================

    @Test
    void testOr() {
        save("alice", 1);
        save("bob", 2);
        save("charlie", 3);

        QuerySpec<MySQLTestEntity> qs = new QuerySpec<>();
        qs.or(g -> g.eq(MySQLTestEntity::getName, "alice").eq(MySQLTestEntity::getName, "bob"));
        assertEquals(2, repository.findAll(qs).size());
    }

    // ==================== NOT 条件 ====================

    @Test
    void testNot() {
        save("alice", 1);
        save("bob", 2);
        save("charlie", 3);

        QuerySpec<MySQLTestEntity> qs = new QuerySpec<>();
        qs.not(n -> n.eq(MySQLTestEntity::getName, "alice"));
        assertEquals(2, repository.findAll(qs).size());
    }

    // ==================== 排序和分页 ====================

    @Test
    void testSort() {
        save("charlie", 3);
        save("alice", 1);
        save("bob", 2);

        QuerySpec<MySQLTestEntity> qs = new QuerySpec<>();
        qs.orderByAsc(MySQLTestEntity::getName);
        List<MySQLTestEntity> result = repository.findAll(qs);
        assertEquals(3, result.size());
        assertEquals("alice", result.get(0).getName());
        assertEquals("bob", result.get(1).getName());
        assertEquals("charlie", result.get(2).getName());
    }

    @Test
    void testSortDesc() {
        save("charlie", 3);
        save("alice", 1);
        save("bob", 2);

        QuerySpec<MySQLTestEntity> qs = new QuerySpec<>();
        qs.orderByDesc(MySQLTestEntity::getStatus);
        List<MySQLTestEntity> result = repository.findAll(qs);
        assertEquals(3, result.size());
        assertEquals(3, result.get(0).getStatus());
    }

    @Test
    void testPagination() {
        for (int i = 0; i < 10; i++) {
            save("user" + i, i);
        }

        QuerySpec<MySQLTestEntity> qs = new QuerySpec<>();
        qs.orderByAsc(MySQLTestEntity::getId);
        Page<MySQLTestEntity> page = repository.findAll(qs, PageRequest.of(0, 3));
        assertEquals(3, page.getContent().size());
        assertEquals(10, page.getTotalElements());
        assertEquals(4, page.getTotalPages());
    }

    // ==================== 组合条件 ====================

    @Test
    void testCombinedConditions() {
        save("alice", 1);
        save("bob", 2);
        save("alice_admin", 3);

        QuerySpec<MySQLTestEntity> qs = new QuerySpec<>();
        qs.startsWith(MySQLTestEntity::getName, "alice").ge(MySQLTestEntity::getStatus, 2);
        List<MySQLTestEntity> result = repository.findAll(qs);
        assertEquals(1, result.size());
        assertEquals("alice_admin", result.get(0).getName());
    }

    @Test
    void testGroupOr() {
        save("a", 1);
        save("b", 2);
        save("c", 3);

        QuerySpec<MySQLTestEntity> qs = new QuerySpec<>();
        qs.or(g -> g.eq(MySQLTestEntity::getStatus, 1).eq(MySQLTestEntity::getStatus, 3));
        assertEquals(2, repository.findAll(qs).size());
    }

    // ==================== count 和 exists ====================

    @Test
    void testCountWithSpec() {
        save("a", 1);
        save("b", 2);
        save("c", 1);

        QuerySpec<MySQLTestEntity> qs = new QuerySpec<>();
        qs.eq(MySQLTestEntity::getStatus, 1);
        assertEquals(2, repository.count(qs));
    }

    @Test
    void testExistsWithSpec() {
        save("alice", 1);

        QuerySpec<MySQLTestEntity> qs = new QuerySpec<>();
        qs.eq(MySQLTestEntity::getName, "alice");
        assertTrue(repository.exists(qs));

        qs = new QuerySpec<>();
        qs.eq(MySQLTestEntity::getName, "bob");
        assertFalse(repository.exists(qs));
    }

    // ==================== 条件便捷方法 ====================

    @Test
    void testConditionalEq() {
        save("alice", 1);
        save("bob", 2);

        QuerySpec<MySQLTestEntity> qs = new QuerySpec<>();
        qs.eq(true, MySQLTestEntity::getName, "alice");
        qs.eq(false, MySQLTestEntity::getName, "bob"); // Should be ignored
        assertEquals(1, repository.findAll(qs).size());
    }

    @Test
    void testConditionalLike() {
        save("hello", 1);
        save("world", 2);

        QuerySpec<MySQLTestEntity> qs = new QuerySpec<>();
        qs.like(true, MySQLTestEntity::getName, "hel");
        qs.like(false, MySQLTestEntity::getName, "wor"); // Should be ignored
        assertEquals(1, repository.findAll(qs).size());
    }

    // ==================== Helper methods ====================

    private MySQLTestEntity save(String name, Integer status) {
        MySQLTestEntity entity = new MySQLTestEntity();
        entity.setName(name);
        entity.setStatus(status);
        return repository.save(entity);
    }
}
