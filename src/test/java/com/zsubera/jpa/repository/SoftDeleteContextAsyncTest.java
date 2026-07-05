package com.zsubera.jpa.repository;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * SoftDeleteContext 异步安全测试。
 *

 */
class SoftDeleteContextAsyncTest {

    @AfterEach
    void cleanup() {
        SoftDeleteContext.reset();
    }

    /**
     * 测试异步边界捕获和重置功能。
     *

     */
    @Test
    void captureAndResetForAsyncShouldPreserveState() {
        // 设置忽略状态
        SoftDeleteContext.pushIgnore();
        assertTrue(SoftDeleteContext.isIgnoreSoftDelete());

        // 捕获并重置
        int captured = SoftDeleteContext.captureAndResetForAsync();
        assertEquals(1, captured);
        assertFalse(SoftDeleteContext.isIgnoreSoftDelete());

        // 恢复状态
        SoftDeleteContext.restoreForAsync(captured);
        assertTrue(SoftDeleteContext.isIgnoreSoftDelete());

        // 清理
        SoftDeleteContext.reset();
    }

    /**
     * 测试线程池中状态不会泄漏。
     *

     */
    @Test
    void threadPoolShouldNotLeakState() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            // 主线程设置忽略状态
            SoftDeleteContext.pushIgnore();
            assertTrue(SoftDeleteContext.isIgnoreSoftDelete());

            // 捕获并重置
            int captured = SoftDeleteContext.captureAndResetForAsync();
            assertFalse(SoftDeleteContext.isIgnoreSoftDelete());

            // 在新线程中恢复
            Future<Boolean> result = executor.submit(() -> {
                SoftDeleteContext.restoreForAsync(captured);
                try {
                    return SoftDeleteContext.isIgnoreSoftDelete();
                } finally {
                    SoftDeleteContext.reset();
                }
            });

            assertTrue(result.get(5, TimeUnit.SECONDS));
            // 主线程状态已重置
            assertFalse(SoftDeleteContext.isIgnoreSoftDelete());
        } finally {
            SoftDeleteContext.reset();
            executor.shutdown();
        }
    }

    /**
     * 测试嵌套调用时的正确性。
     */
    @Test
    void nestedPushPopShouldWorkCorrectly() {
        SoftDeleteContext.pushIgnore();
        assertEquals(1, SoftDeleteContext.getIgnoreCount());

        SoftDeleteContext.pushIgnore();
        assertEquals(2, SoftDeleteContext.getIgnoreCount());

        SoftDeleteContext.popIgnore();
        assertEquals(1, SoftDeleteContext.getIgnoreCount());
        assertTrue(SoftDeleteContext.isIgnoreSoftDelete());

        SoftDeleteContext.popIgnore();
        assertFalse(SoftDeleteContext.isIgnoreSoftDelete());
    }

    /**
     * 测试异常场景下的防御性清理。
     */
    @Test
    void popIgnoreWhenCountZeroShouldNotThrow() {
        // 确保初始状态为 0
        SoftDeleteContext.reset();
        assertFalse(SoftDeleteContext.isIgnoreSoftDelete());

        // 多余的 pop 应该被安全处理
        assertDoesNotThrow(SoftDeleteContext::popIgnore);
    }

    /**
     * 测试异步任务中多次 push/pop 的正确性。
     */
    @Test
    void asyncTaskWithNestedPushPopShouldWork() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            // 主线程设置嵌套状态
            SoftDeleteContext.pushIgnore();
            SoftDeleteContext.pushIgnore();
            int captured = SoftDeleteContext.captureAndResetForAsync();
            assertEquals(2, captured);

            Future<Boolean> result = executor.submit(() -> {
                SoftDeleteContext.restoreForAsync(captured);
                try {
                    assertEquals(2, SoftDeleteContext.getIgnoreCount());
                    SoftDeleteContext.popIgnore();
                    assertEquals(1, SoftDeleteContext.getIgnoreCount());
                    SoftDeleteContext.popIgnore();
                    return !SoftDeleteContext.isIgnoreSoftDelete();
                } finally {
                    SoftDeleteContext.reset();
                }
            });

            assertTrue(result.get(5, TimeUnit.SECONDS));
        } finally {
            SoftDeleteContext.reset();
            executor.shutdown();
        }
    }

    /**
     * 测试获取忽略计数的方法。
     */
    @Test
    void getIgnoreCountShouldReturnCorrectValue() {
        SoftDeleteContext.reset();
        assertEquals(0, SoftDeleteContext.getIgnoreCount());

        SoftDeleteContext.pushIgnore();
        assertEquals(1, SoftDeleteContext.getIgnoreCount());

        SoftDeleteContext.pushIgnore();
        assertEquals(2, SoftDeleteContext.getIgnoreCount());

        SoftDeleteContext.reset();
        assertEquals(0, SoftDeleteContext.getIgnoreCount());
    }
}
