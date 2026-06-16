package com.zsubera.jpa.repository;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Method;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledForJreRange;
import org.junit.jupiter.api.condition.JRE;

/**
 * 验证 {@link SoftDeleteContext} 在 Java 21+ 虚拟线程下的正确行为。
 *
 * <p>
 * 虚拟线程拥有独立的 ThreadLocal 映射，因此 push/pop 操作在虚拟线程中
 * 与平台线程行为一致。此测试通过反射调用虚拟线程 API，确保在 Java 17 下可编译。
 */
@EnabledForJreRange(min = JRE.JAVA_21)
class SoftDeleteContextVirtualThreadTest {

    private static boolean virtualThreadsAvailable;

    @BeforeAll
    static void detectVirtualThreads() {
        try {
            Class.forName("java.lang.Thread$Builder$OfVirtual");
            virtualThreadsAvailable = true;
        } catch (ClassNotFoundException e) {
            virtualThreadsAvailable = false;
        }
    }

    @AfterEach
    void cleanup() {
        SoftDeleteContext.reset();
    }

    private ExecutorService newVirtualThreadExecutor() throws Exception {
        Assumptions.assumeTrue(virtualThreadsAvailable, "Virtual threads not available");
        Method newVirtualThreadPerTaskExecutor = Executors.class.getMethod("newVirtualThreadPerTaskExecutor");
        return (ExecutorService)newVirtualThreadPerTaskExecutor.invoke(null);
    }

    @Test
    void virtualThread_pushPop_doesNotAffectParentThread() throws Exception {
        ExecutorService vExec = newVirtualThreadExecutor();
        try {
            SoftDeleteContext.pushIgnore();
            assertTrue(SoftDeleteContext.isIgnoreSoftDelete());

            Future<Boolean> childResult = vExec.submit(() -> SoftDeleteContext.isIgnoreSoftDelete());
            // 虚拟线程不继承父线程的 ThreadLocal 值
            assertFalse(childResult.get(5, TimeUnit.SECONDS));

            // 父线程状态不受影响
            assertTrue(SoftDeleteContext.isIgnoreSoftDelete());
            SoftDeleteContext.popIgnore();
        } finally {
            vExec.shutdown();
        }
    }

    @Test
    void virtualThread_pushPop_isIsolatedBetweenVirtualThreads() throws Exception {
        ExecutorService vExec = newVirtualThreadExecutor();
        try {
            // 虚拟线程 1：push 后不 pop（模拟异常场景）
            Future<?> f1 = vExec.submit(() -> SoftDeleteContext.pushIgnore());
            f1.get(5, TimeUnit.SECONDS);

            // 虚拟线程 2：应该看不到虚拟线程 1 的状态
            Future<Boolean> f2 = vExec.submit(() -> SoftDeleteContext.isIgnoreSoftDelete());
            assertFalse(f2.get(5, TimeUnit.SECONDS));

            // 父线程也不受影响
            assertFalse(SoftDeleteContext.isIgnoreSoftDelete());
        } finally {
            vExec.shutdown();
        }
    }

    @Test
    void withIgnore_worksInVirtualThread() throws Exception {
        ExecutorService vExec = newVirtualThreadExecutor();
        try {
            Future<Boolean> result = vExec.submit(() -> {
                AtomicBoolean wasIgnored = new AtomicBoolean(false);
                SoftDeleteContext.withIgnore(() -> wasIgnored.set(SoftDeleteContext.isIgnoreSoftDelete()));
                return wasIgnored.get() && !SoftDeleteContext.isIgnoreSoftDelete();
            });
            assertTrue(result.get(5, TimeUnit.SECONDS));
        } finally {
            vExec.shutdown();
        }
    }

    @Test
    void withIgnore_supplier_worksInVirtualThread() throws Exception {
        ExecutorService vExec = newVirtualThreadExecutor();
        try {
            Future<String> result = vExec.submit(() -> SoftDeleteContext.withIgnore(() -> {
                assertTrue(SoftDeleteContext.isIgnoreSoftDelete());
                return "completed";
            }));
            assertEquals("completed", result.get(5, TimeUnit.SECONDS));
            assertFalse(SoftDeleteContext.isIgnoreSoftDelete());
        } finally {
            vExec.shutdown();
        }
    }

    @Test
    void captureAndRestore_worksAcrossVirtualThreads() throws Exception {
        ExecutorService vExec = newVirtualThreadExecutor();
        try {
            SoftDeleteContext.pushIgnore();
            SoftDeleteContext.pushIgnore();
            int captured = SoftDeleteContext.captureAndResetForAsync();
            assertEquals(2, captured);

            Future<Boolean> result = vExec.submit(() -> {
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
            vExec.shutdown();
        }
    }

    @Test
    void withIgnore_exceptionStillCleansUp() throws Exception {
        ExecutorService vExec = newVirtualThreadExecutor();
        try {
            Future<Boolean> result = vExec.submit(() -> {
                try {
                    SoftDeleteContext.withIgnore(() -> {
                        throw new RuntimeException("simulated error");
                    });
                    return false;
                } catch (RuntimeException e) {
                    return !SoftDeleteContext.isIgnoreSoftDelete();
                }
            });
            assertTrue(result.get(5, TimeUnit.SECONDS));
        } finally {
            vExec.shutdown();
        }
    }

    @Test
    void manyVirtualThreads_concurrentPushPop() throws Exception {
        ExecutorService vExec = newVirtualThreadExecutor();
        try {
            int threadCount = 100;
            java.util.List<Future<Boolean>> futures = new java.util.ArrayList<>();
            for (int i = 0; i < threadCount; i++) {
                futures.add(vExec.submit(() -> {
                    SoftDeleteContext.withIgnore(() -> assertTrue(SoftDeleteContext.isIgnoreSoftDelete()));
                    return !SoftDeleteContext.isIgnoreSoftDelete();
                }));
            }
            for (Future<Boolean> f : futures) {
                assertTrue(f.get(10, TimeUnit.SECONDS));
            }
            assertFalse(SoftDeleteContext.isIgnoreSoftDelete());
        } finally {
            vExec.shutdown();
        }
    }
}
