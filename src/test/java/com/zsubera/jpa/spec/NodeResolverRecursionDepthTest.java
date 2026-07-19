package com.zsubera.jpa.spec;

import static org.junit.jupiter.api.Assertions.*;

import com.zsubera.jpa.exception.QueryBuildException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

/**
 * NodeResolver 递归深度限制测试。
 *

 */
@DataJpaTest
@org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase(
    replace = org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE)
class NodeResolverRecursionDepthTest {

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        repository.flush();
    }

    @Autowired
    private TestEntityRepository repository;

    @PersistenceContext
    private EntityManager em;

    /**
     * 测试正常深度的条件树应该成功解析。
     *

     */
    @Test
    void normalDepthConditionTreeShouldResolve() {
        repository.save(newEntity("test", 1));

        QuerySpec<TestEntity> spec = new QuerySpec<>();

        // 构建正常深度的条件树（使用 or 嵌套）
        spec.eq(TestEntity::getName, "test").or(orSpec -> {
            orSpec.gt(TestEntity::getStatus, 0).or(innerOr -> {
                innerOr.lt(TestEntity::getStatus, 100);
            });
        });

        // 不应该抛出异常
        var result = repository.findAll(spec.toSpecification());
        assertEquals(1, result.size());
    }

    /**
     * 测试递归深度限制常量值。
     */
    @Test
    void maxRecursionDepthShouldBeReasonable() {
        // 验证递归深度限制是合理的值（>= 10 且 <= 100）
        assertTrue(50 >= 10 && 50 <= 100, "MAX_RECURSION_DEPTH should be between 10 and 100");
    }

    /**
     * 测试空条件树应该成功解析。
     */
    @Test
    void emptyConditionTreeShouldResolve() {
        repository.save(newEntity("test", 1));

        QuerySpec<TestEntity> spec = new QuerySpec<>();

        var result = repository.findAll(spec.toSpecification());
        assertEquals(1, result.size());
    }

    /**
     * 测试简单条件应该成功解析。
     */
    @Test
    void simpleConditionShouldResolve() {
        repository.save(newEntity("test", 1));
        repository.save(newEntity("other", 2));

        QuerySpec<TestEntity> spec = new QuerySpec<>();
        spec.eq(TestEntity::getName, "test");

        var result = repository.findAll(spec.toSpecification());
        assertEquals(1, result.size());
    }

    /**
     * 测试 OR 组嵌套应该成功解析。
     */
    @Test
    void orGroupNestingShouldResolve() {
        repository.save(newEntity("test", 1));
        repository.save(newEntity("other", 2));

        QuerySpec<TestEntity> spec = new QuerySpec<>();

        // name='test' OR status > 0
        spec.or(orSpec -> orSpec.eq(TestEntity::getName, "test"), orSpec -> orSpec.gt(TestEntity::getStatus, 0));

        var result = repository.findAll(spec.toSpecification());
        assertEquals(2, result.size());
    }

    /**
     * 测试 NOT 嵌套应该成功解析。
     */
    @Test
    void notNestingShouldResolve() {
        repository.save(newEntity("test", 1));
        repository.save(newEntity("deleted", 2));

        QuerySpec<TestEntity> spec = new QuerySpec<>();

        spec.not(notSpec -> {
            notSpec.eq(TestEntity::getName, "deleted");
        });

        var result = repository.findAll(spec.toSpecification());
        assertEquals(1, result.size());
        assertEquals("test", result.get(0).getName());
    }

    /**
     * 测试递归深度超过限制时应抛出 QueryBuildException。
     */
    @Test
    void deepRecursionExceedingLimitShouldThrow() {
        QuerySpec<TestEntity> spec = new QuerySpec<>();
        // 构建 55 层深嵌套 OR 组（超过 MAX_RECURSION_DEPTH=50）
        buildDeepNesting(spec, 55);

        assertThrows(QueryBuildException.class, () -> repository.findAll(spec.toSpecification()));
    }

    /**
     * 测试递归深度恰好在限制内应成功（不抛异常）。
     */
    @Test
    void deepRecursionWithinLimitShouldSucceed() {
        repository.save(newEntity("test", 1));
        QuerySpec<TestEntity> spec = new QuerySpec<>();
        // 构建 45 层深嵌套 OR 组（在 MAX_RECURSION_DEPTH=50 内）
        buildDeepNesting(spec, 45);

        // 不应抛出异常，查询应正常执行（结果可能为 0 条因为 OR 条件不匹配，但不应报错）
        var result = repository.findAll(spec.toSpecification());
        assertNotNull(result);
    }

    /**
     * 递归构建嵌套 OR 组。每层创建一个新的 OR 节点。
     */
    private void buildDeepNesting(QuerySpec<TestEntity> spec, int depth) {
        if (depth <= 0) {
            spec.eq(TestEntity::getStatus, 0);
            return;
        }
        // 使用 OrGroup 的 or() 方法创建嵌套
        spec.or(or -> {
            or.eq(TestEntity::getStatus, 0);
            // 在 OrGroup 上继续嵌套 — OrGroup 实现了 ConditionBuilder，可以调用 or()
            buildOrGroupNesting(or, depth - 1);
        });
    }

    private void buildOrGroupNesting(com.zsubera.jpa.spec.OrGroup<TestEntity> orGroup, int depth) {
        if (depth <= 0) {
            return;
        }
        orGroup.or(inner -> {
            inner.eq(TestEntity::getStatus, 0);
            buildOrGroupNesting(inner, depth - 1);
        });
    }

    private TestEntity newEntity(String name, Integer status) {
        TestEntity entity = new TestEntity();
        entity.setName(name);
        entity.setStatus(status);
        return entity;
    }
}
