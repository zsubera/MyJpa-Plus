package com.zsubera.jpa.spec;

import static org.junit.jupiter.api.Assertions.*;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

/**
 * 测试多分支 OR Builder 语法。
 *
 * <p>
 * 每个 lambda 代表一个 OR 分支，lambda 内部的链式调用（如 {@code .eq().eq()}）表示 AND 语义，
 * 与外层保持一致，消除了"相同语法不同含义"的困惑。
 *
 * <pre>{@code
 * // 每个 lambda 是一个 OR 分支
 * s.or(
 *     o -> o.eq(User::getRole, "ADMIN"),           // 分支1: role='ADMIN'
 *     o -> o.eq(User::getStatus, "ACTIVE")         // 分支2: status='ACTIVE'
 * );
 * // → role='ADMIN' OR status='ACTIVE'
 *
 * // lambda 内部 .eq().eq() = AND（与外层一致）
 * s.or(
 *     o -> o.eq(User::getRole, "ADMIN").eq(User::getStatus, "ACTIVE"),   // 分支1: (ADMIN AND ACTIVE)
 *     o -> o.eq(User::getRole, "USER")                                    // 分支2: (USER)
 * );
 * // → (role='ADMIN' AND status='ACTIVE') OR (role='USER')
 * }</pre>
 */
@DataJpaTest
@org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase(
    replace = org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE)
class MultiBranchOrTest {

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        repository.flush();
    }

    @Autowired
    private TestEntityRepository repository;

    @PersistenceContext
    private EntityManager em;

    // ---- 基本多分支 OR 测试 ----

    @Test
    void testMultiBranchOrSimple() {
        // 每个 lambda 是一个 OR 分支
        repository.save(newEntity("admin", 1));
        repository.save(newEntity("user", 2));
        repository.save(newEntity("guest", 3));

        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.or(o -> o.eq(TestEntity::getName, "admin"), // 分支1: name='admin'
            o -> o.eq(TestEntity::getStatus, 2) // 分支2: status=2
        );

        // 期望：name='admin' OR status=2
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(2, result.size());
        assertTrue(result.stream().anyMatch(e -> "admin".equals(e.getName())));
        assertTrue(result.stream().anyMatch(e -> Integer.valueOf(2).equals(e.getStatus())));
    }

    @Test
    void testMultiBranchOrWithAndInside() {
        // lambda 内部 .eq().eq() = AND（与外层一致）
        repository.save(newEntity("admin", 1)); // name='admin', status=1
        repository.save(newEntity("admin", 2)); // name='admin', status=2
        repository.save(newEntity("user", 2)); // name='user', status=2
        repository.save(newEntity("guest", 3)); // name='guest', status=3

        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.or(o -> o.eq(TestEntity::getName, "admin").eq(TestEntity::getStatus, 1), // 分支1: (admin AND 1)
            o -> o.eq(TestEntity::getStatus, 2) // 分支2: status=2
        );

        // 期望：(name='admin' AND status=1) OR status=2
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(3, result.size());
        assertTrue(
            result.stream().anyMatch(e -> "admin".equals(e.getName()) && Integer.valueOf(1).equals(e.getStatus())));
        assertTrue(
            result.stream().anyMatch(e -> "admin".equals(e.getName()) && Integer.valueOf(2).equals(e.getStatus())));
        assertTrue(
            result.stream().anyMatch(e -> "user".equals(e.getName()) && Integer.valueOf(2).equals(e.getStatus())));
    }

    @Test
    void testMultiBranchOrThreeBranches() {
        // 三个 OR 分支
        repository.save(newEntity("admin", 1));
        repository.save(newEntity("user", 2));
        repository.save(newEntity("guest", 3));
        repository.save(newEntity("other", 4));

        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.or(o -> o.eq(TestEntity::getName, "admin"), o -> o.eq(TestEntity::getName, "user"),
            o -> o.eq(TestEntity::getName, "guest"));

        // 期望：name='admin' OR name='user' OR name='guest'
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(3, result.size());
        assertTrue(result.stream().noneMatch(e -> "other".equals(e.getName())));
    }

    @Test
    void testMultiBranchOrWithComplexConditions() {
        // 复杂条件：每个分支可以有多个 AND 条件
        repository.save(newEntity("admin", 1)); // name='admin', status=1
        repository.save(newEntity("admin", 2)); // name='admin', status=2
        repository.save(newEntity("user", 1)); // name='user', status=1
        repository.save(newEntity("user", 2)); // name='user', status=2

        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.or(o -> o.eq(TestEntity::getName, "admin").eq(TestEntity::getStatus, 1), // 分支1: (admin AND 1)
            o -> o.eq(TestEntity::getName, "user").eq(TestEntity::getStatus, 2) // 分支2: (user AND 2)
        );

        // 期望：(name='admin' AND status=1) OR (name='user' AND status=2)
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(2, result.size());
        assertTrue(
            result.stream().anyMatch(e -> "admin".equals(e.getName()) && Integer.valueOf(1).equals(e.getStatus())));
        assertTrue(
            result.stream().anyMatch(e -> "user".equals(e.getName()) && Integer.valueOf(2).equals(e.getStatus())));
    }

    // ---- 不同操作符测试 ----

    @Test
    void testMultiBranchOrWithMixedOperators() {
        // 混合操作符：每个分支可以有不同的操作符
        repository.save(newEntity("a", 1));
        repository.save(newEntity("b", 5));
        repository.save(newEntity("c", 10));

        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.or(o -> o.lt(TestEntity::getStatus, 3), // 分支1: status < 3
            o -> o.gt(TestEntity::getStatus, 7) // 分支2: status > 7
        );

        // 期望：status < 3 OR status > 7
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(2, result.size());
        assertTrue(result.stream().anyMatch(e -> "a".equals(e.getName())));
        assertTrue(result.stream().anyMatch(e -> "c".equals(e.getName())));
    }

    @Test
    void testMultiBranchOrWithLike() {
        // LIKE 操作符
        repository.save(newEntity("hello", 0));
        repository.save(newEntity("world", 0));
        repository.save(newEntity("help", 0));

        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.or(o -> o.like(TestEntity::getName, "hel"), // 分支1: name LIKE '%hel%'
            o -> o.like(TestEntity::getName, "wor") // 分支2: name LIKE '%wor%'
        );

        // 期望：name LIKE '%hel%' OR name LIKE '%wor%'
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(3, result.size());
    }

    @Test
    void testMultiBranchOrWithIn() {
        // IN 操作符
        repository.save(newEntity("a", 1));
        repository.save(newEntity("b", 2));
        repository.save(newEntity("c", 3));
        repository.save(newEntity("d", 4));

        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.or(o -> o.in(TestEntity::getStatus, 1, 2), // 分支1: status IN (1, 2)
            o -> o.in(TestEntity::getStatus, 3, 4) // 分支2: status IN (3, 4)
        );

        // 期望：status IN (1, 2) OR status IN (3, 4)
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(4, result.size());
    }

    @Test
    void testMultiBranchOrWithIsNull() {
        // IS NULL 操作符
        repository.save(newEntity("hasName", 0));
        TestEntity nullName = new TestEntity();
        nullName.setName(null);
        nullName.setStatus(99);
        repository.save(nullName);

        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.or(o -> o.isNull(TestEntity::getName), // 分支1: name IS NULL
            o -> o.eq(TestEntity::getStatus, 0) // 分支2: status = 0
        );

        // 期望：name IS NULL OR status = 0
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(2, result.size());
        assertTrue(result.stream().anyMatch(e -> e.getName() == null));
        assertTrue(result.stream().anyMatch(e -> "hasName".equals(e.getName())));
    }

    @Test
    void testMultiBranchOrWithBetween() {
        // BETWEEN 操作符
        repository.save(newEntity("a", 1));
        repository.save(newEntity("b", 5));
        repository.save(newEntity("c", 10));

        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.or(o -> o.between(TestEntity::getStatus, 1, 3), // 分支1: status BETWEEN 1 AND 3
            o -> o.between(TestEntity::getStatus, 8, 10) // 分支2: status BETWEEN 8 AND 10
        );

        // 期望：status BETWEEN 1 AND 3 OR status BETWEEN 8 AND 10
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(2, result.size());
        assertTrue(result.stream().anyMatch(e -> "a".equals(e.getName())));
        assertTrue(result.stream().anyMatch(e -> "c".equals(e.getName())));
    }

    // ---- 组合场景测试 ----

    @Test
    void testMultiBranchOrWithOuterAnd() {
        // OR 与外层 AND 结合
        repository.save(newEntity("admin", 1));
        repository.save(newEntity("admin", 2));
        repository.save(newEntity("user", 1));
        repository.save(newEntity("user", 2));

        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.eq(TestEntity::getName, "admin") // 外层 AND: name='admin'
            .or(o -> o.eq(TestEntity::getStatus, 1), // 分支1: status=1
                o -> o.eq(TestEntity::getStatus, 2) // 分支2: status=2
            );

        // 期望：name='admin' AND (status=1 OR status=2)
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(e -> "admin".equals(e.getName())));
    }

    @Test
    void testOrWithNotCombination() {
        // or() 与 not() 组合
        repository.save(newEntity("admin", 1));
        repository.save(newEntity("user", 2));
        repository.save(newEntity("guest", 3));

        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.not(n -> n.eq(TestEntity::getName, "admin")) // not(name='admin')
            .or(o -> o.eq(TestEntity::getName, "user"), // 分支1: name='user'
                o -> o.eq(TestEntity::getName, "guest") // 分支2: name='guest'
            );

        // 期望：name != 'admin' AND (name='user' OR name='guest')
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(2, result.size());
        assertTrue(result.stream().noneMatch(e -> "admin".equals(e.getName())));
    }

    @Test
    void testMultipleOrCallsMerge() {
        // 多次 or() 调用应该合并到同一个 OR 组
        repository.save(newEntity("admin", 1));
        repository.save(newEntity("user", 2));
        repository.save(newEntity("guest", 3));
        repository.save(newEntity("other", 4));

        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.or(o -> o.eq(TestEntity::getName, "admin")) // 第一次 or()
            .or(o -> o.eq(TestEntity::getName, "user")); // 第二次 or()，应该合并

        // 期望：name='admin' OR name='user'
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(2, result.size());
        assertTrue(result.stream().anyMatch(e -> "admin".equals(e.getName())));
        assertTrue(result.stream().anyMatch(e -> "user".equals(e.getName())));
    }

    @Test
    void testMultiBranchWithOuterCondition() {
        // OR 分支与外层条件组合
        repository.save(newEntity("admin", 1));
        repository.save(newEntity("admin", 2));
        repository.save(newEntity("user", 1));
        repository.save(newEntity("user", 2));
        repository.save(newEntity("guest", 3));

        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.ge(TestEntity::getStatus, 1) // 外层: status >= 1
            .or(o -> o.eq(TestEntity::getName, "admin"), // 分支1: name='admin'
                o -> o.eq(TestEntity::getName, "user") // 分支2: name='user'
            );

        // 期望：status >= 1 AND (name='admin' OR name='user')
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(4, result.size());
        assertTrue(result.stream().noneMatch(e -> "guest".equals(e.getName())));
    }

    // ---- 链式条件测试 ----

    @Test
    void testOrWithChainedConditions() {
        // OR 分支内部链式调用多个不同操作符
        repository.save(newEntity("a", 1));
        repository.save(newEntity("b", 5));
        repository.save(newEntity("c", 10));

        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.or(o -> o.eq(TestEntity::getName, "a").lt(TestEntity::getStatus, 3), // 分支1: (name='a' AND status < 3)
            o -> o.eq(TestEntity::getName, "c").gt(TestEntity::getStatus, 7) // 分支2: (name='c' AND status > 7)
        );

        // 期望：(name='a' AND status < 3) OR (name='c' AND status > 7)
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(2, result.size());
        assertTrue(result.stream().anyMatch(e -> "a".equals(e.getName())));
        assertTrue(result.stream().anyMatch(e -> "c".equals(e.getName())));
    }

    // ---- NULL 值测试 ----

    @Test
    void testOrWithNullValue() {
        // OR 分支包含 NULL 值
        repository.save(newEntity("admin", 1));
        TestEntity nullStatus = new TestEntity();
        nullStatus.setName("nullStatus");
        nullStatus.setStatus(null);
        repository.save(nullStatus);

        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.or(o -> o.eq(TestEntity::getName, "admin"), // 分支1: name='admin'
            o -> o.isNull(TestEntity::getStatus) // 分支2: status IS NULL
        );

        // 期望：name='admin' OR status IS NULL
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(2, result.size());
        assertTrue(result.stream().anyMatch(e -> "admin".equals(e.getName())));
        assertTrue(result.stream().anyMatch(e -> e.getStatus() == null));
    }

    // ---- 边界情况测试 ----

    @Test
    void testEmptyBranchesThrowsException() {
        // 空分支应抛出异常
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        assertThrows(IllegalArgumentException.class, () -> qs.or());
    }

    @Test
    void testNullBranchThrowsException() {
        // null 分支应抛出异常
        QuerySpec<TestEntity> qs = new QuerySpec<>();
        assertThrows(IllegalArgumentException.class, () -> qs.or((Consumer<OrGroup<TestEntity>>)null));
    }

    @Test
    void testBranchExceptionRevertsChanges() {
        // 分支抛出异常时应该回滚
        repository.save(newEntity("admin", 1));
        repository.save(newEntity("user", 2));
        repository.save(newEntity("guest", 3));

        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.eq(TestEntity::getStatus, 1); // 先添加一个正常条件

        // 第一个分支正常，第二个分支抛出异常
        try {
            qs.or(o -> o.eq(TestEntity::getName, "admin"), (Consumer<OrGroup<TestEntity>>)o -> {
                throw new RuntimeException("branch error");
            });
            fail("Should have thrown exception");
        } catch (RuntimeException e) {
            assertEquals("branch error", e.getMessage());
        }

        // 异常后查询应该只包含之前的条件
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(1, result.size()); // 只有 admin/1 符合 status=1
        assertEquals("admin", result.get(0).getName());
    }

    @Test
    void testOrWithEmptyResult() {
        // OR 分支都不匹配任何记录
        repository.save(newEntity("admin", 1));
        repository.save(newEntity("user", 2));

        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.or(o -> o.eq(TestEntity::getName, "guest"), // 分支1: name='guest'（不存在）
            o -> o.eq(TestEntity::getName, "other") // 分支2: name='other'（不存在）
        );

        // 期望：无结果
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(0, result.size());
    }

    @Test
    void testOrWithAllRecordsMatch() {
        // OR 分支匹配所有记录
        repository.save(newEntity("admin", 1));
        repository.save(newEntity("user", 2));
        repository.save(newEntity("guest", 3));

        QuerySpec<TestEntity> qs = new QuerySpec<>();
        qs.or(o -> o.eq(TestEntity::getName, "admin"), o -> o.eq(TestEntity::getName, "user"),
            o -> o.eq(TestEntity::getName, "guest"));

        // 期望：所有记录
        List<TestEntity> result = repository.findAll(qs.toSpecification());
        assertEquals(3, result.size());
    }

    private TestEntity newEntity(String name, int status) {
        TestEntity entity = new TestEntity();
        entity.setName(name);
        entity.setStatus(status);
        return entity;
    }
}
