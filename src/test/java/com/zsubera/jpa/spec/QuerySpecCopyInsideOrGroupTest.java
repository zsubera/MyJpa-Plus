package com.zsubera.jpa.spec;

import static org.junit.jupiter.api.Assertions.*;

import com.zsubera.jpa.exception.QueryBuildException;
import org.junit.jupiter.api.Test;

/**
 * 测试 QuerySpec.copy() 在 or()/not() 组内调用时的行为。
 *
 * <p>copy() 在 or()/not() 组内调用应抛出 QueryBuildException，
 * 因为 groupStack 是可变状态，深拷贝会导致后续条件添加到根节点而非当前条件组。
 */
class QuerySpecCopyInsideOrGroupTest {

    @Test
    void copy_insideOrGroup_throwsQueryBuildException() {
        QuerySpec<TestEntity> spec = new QuerySpec<>();
        spec.eq(TestEntity::getName, "test");

        assertThrows(QueryBuildException.class, () -> spec.or(o -> {
            o.eq(TestEntity::getStatus, 1);
            // 在 or() 组内调用 copy() 应抛异常
            spec.copy();
        }));
    }

    @Test
    void copy_insideNotGroup_throwsQueryBuildException() {
        QuerySpec<TestEntity> spec = new QuerySpec<>();
        spec.eq(TestEntity::getName, "test");

        assertThrows(QueryBuildException.class, () -> spec.not(n -> {
            n.eq(TestEntity::getStatus, 1);
            // 在 not() 组内调用 copy() 应抛异常
            spec.copy();
        }));
    }

    @Test
    void copy_outsideOrGroup_succeeds() {
        QuerySpec<TestEntity> spec = new QuerySpec<>();
        spec.eq(TestEntity::getName, "test").or(o -> o.eq(TestEntity::getStatus, 1));

        // or() 组已关闭，copy() 应成功
        assertDoesNotThrow(() -> {
            QuerySpec<TestEntity> copy = spec.copy();
            assertNotNull(copy);
        });
    }

    @Test
    void copy_empty_spec_succeeds() {
        QuerySpec<TestEntity> spec = new QuerySpec<>();
        assertDoesNotThrow(() -> {
            QuerySpec<TestEntity> copy = spec.copy();
            assertNotNull(copy);
        });
    }
}
