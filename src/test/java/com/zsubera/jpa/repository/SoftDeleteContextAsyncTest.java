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

    // ---- TaskDecorator IGNORE_COUNT propagation tests (Fix #2) ----

    @Test
    void createTaskDecorator_propagatesIgnoreCount() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            // Simulate @IgnoreSoftDelete setting IGNORE_COUNT
            SoftDeleteContext.pushIgnore();
            assertTrue(SoftDeleteContext.isIgnoreSoftDelete());

            org.springframework.core.task.TaskDecorator decorator = DefaultMyJpaRepository.createTaskDecorator();
            Runnable decorated = decorator.decorate(() -> {
                // In async thread: IGNORE_COUNT should be restored
                assertTrue(SoftDeleteContext.isIgnoreSoftDelete(),
                    "TaskDecorator should propagate IGNORE_COUNT to async thread");
                assertEquals(1, SoftDeleteContext.getIgnoreCount());
            });

            // After decorate, parent state should be reset
            assertFalse(SoftDeleteContext.isIgnoreSoftDelete(),
                "Parent thread IGNORE_COUNT should be reset after decorate");

            Future<?> result = executor.submit(decorated);
            result.get(5, TimeUnit.SECONDS);

            // After async task, parent state should be restored
            // (Note: in real usage the parent thread continues independently)
        } finally {
            SoftDeleteContext.reset();
            executor.shutdown();
        }
    }

    @Test
    void createTaskDecorator_nestedIgnoreCount() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            SoftDeleteContext.pushIgnore();
            SoftDeleteContext.pushIgnore();
            assertEquals(2, SoftDeleteContext.getIgnoreCount());

            org.springframework.core.task.TaskDecorator decorator = DefaultMyJpaRepository.createTaskDecorator();
            Runnable decorated = decorator.decorate(() -> {
                assertEquals(2, SoftDeleteContext.getIgnoreCount(),
                    "TaskDecorator should propagate nested IGNORE_COUNT");
            });

            assertFalse(SoftDeleteContext.isIgnoreSoftDelete());

            Future<?> result = executor.submit(decorated);
            result.get(5, TimeUnit.SECONDS);
        } finally {
            SoftDeleteContext.reset();
            executor.shutdown();
        }
    }

    @Test
    void createTaskDecorator_zeroIgnoreCount_noop() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            // No pushIgnore called — IGNORE_COUNT should be 0
            org.springframework.core.task.TaskDecorator decorator = DefaultMyJpaRepository.createTaskDecorator();
            Runnable decorated = decorator.decorate(() -> {
                assertFalse(SoftDeleteContext.isIgnoreSoftDelete(),
                    "TaskDecorator with zero IGNORE_COUNT should not set ignore state");
            });

            Future<?> result = executor.submit(decorated);
            result.get(5, TimeUnit.SECONDS);
        } finally {
            SoftDeleteContext.reset();
            executor.shutdown();
        }
    }

    @Test
    void createTaskDecorator_exceptionInTask_capturesAndClearsIgnoreCount() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            SoftDeleteContext.pushIgnore();
            assertTrue(SoftDeleteContext.isIgnoreSoftDelete());

            org.springframework.core.task.TaskDecorator decorator = DefaultMyJpaRepository.createTaskDecorator();
            Runnable decorated = decorator.decorate(() -> {
                // Async thread should have IGNORE_COUNT restored
                assertTrue(SoftDeleteContext.isIgnoreSoftDelete());
                assertEquals(1, SoftDeleteContext.getIgnoreCount());
                throw new RuntimeException("task failure");
            });

            // After decorate, parent's IGNORE_COUNT should be captured and reset
            assertFalse(SoftDeleteContext.isIgnoreSoftDelete(),
                "Parent IGNORE_COUNT should be captured and reset by TaskDecorator");

            Future<?> result = executor.submit(decorated);
            try {
                result.get(5, TimeUnit.SECONDS);
                fail("Should have thrown");
            } catch (ExecutionException e) {
                assertEquals(RuntimeException.class, e.getCause().getClass());
            }

            // After task failure, async thread's IGNORE_COUNT should be cleaned up
            // (verified by the async thread's finally block calling SoftDeleteContext.reset())
        } finally {
            SoftDeleteContext.reset();
            executor.shutdown();
        }
    }
}
