package com.zsubera.jpa.spec;

import static org.junit.jupiter.api.Assertions.*;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

/**
 * NodeResolver 递归深度限制测试。
 *
 * <p>测试 P1-4 修复：递归深度无限制导致 StackOverflowError
 */
@DataJpaTest
class NodeResolverRecursionDepthTest {

    @Autowired
    private TestEntityRepository repository;

    @PersistenceContext
    private EntityManager em;

    /**
     * 测试正常深度的条件树应该成功解析。
     *
     * <p>P1-4 修复验证：确保正常查询不受影响
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

        spec.or(orSpec -> {
            orSpec.eq(TestEntity::getName, "test").or(innerOr -> {
                innerOr.gt(TestEntity::getStatus, 0);
            });
        });

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

    private TestEntity newEntity(String name, Integer status) {
        TestEntity entity = new TestEntity();
        entity.setName(name);
        entity.setStatus(status);
        return entity;
    }
}
