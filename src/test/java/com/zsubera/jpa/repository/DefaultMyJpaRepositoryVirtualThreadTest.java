package com.zsubera.jpa.repository;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledForJreRange;
import org.junit.jupiter.api.condition.JRE;

@EnabledForJreRange(min = JRE.JAVA_21)
class DefaultMyJpaRepositoryVirtualThreadTest {

    private static boolean virtualThreadsAvailable;
    private static Field autoFilterOverrideField;
    private static ThreadLocal<Boolean> autoFilterOverride;

    @BeforeAll
    @SuppressWarnings("unchecked")
    static void detectVirtualThreads() throws Exception {
        try {
            Class.forName("java.lang.Thread$Builder$OfVirtual");
            virtualThreadsAvailable = true;
        } catch (ClassNotFoundException e) {
            virtualThreadsAvailable = false;
        }
        autoFilterOverrideField = DefaultMyJpaRepository.class.getDeclaredField("AUTO_FILTER_OVERRIDE");
        autoFilterOverrideField.setAccessible(true);
        autoFilterOverride = (ThreadLocal<Boolean>)autoFilterOverrideField.get(null);
    }

    @AfterEach
    void cleanup() {
        DefaultMyJpaRepository.clearThreadLocal();
    }

    private ExecutorService newVirtualThreadExecutor() throws Exception {
        Assumptions.assumeTrue(virtualThreadsAvailable, "Virtual threads not available");
        Method newVirtualThreadPerTaskExecutor = Executors.class.getMethod("newVirtualThreadPerTaskExecutor");
        return (ExecutorService)newVirtualThreadPerTaskExecutor.invoke(null);
    }

    @Test
    void autoFilterOverride_doesNotAffectVirtualThread() throws Exception {
        ExecutorService vExec = newVirtualThreadExecutor();
        try {
            DefaultMyJpaRepository.withAutoFilterOverride(true, () -> assertTrue(autoFilterOverride.get()));

            Future<Boolean> childResult = vExec.submit(() -> autoFilterOverride.get());
            assertNull(childResult.get(5, TimeUnit.SECONDS));
        } finally {
            vExec.shutdown();
        }
    }

    @Test
    void autoFilterOverride_isIsolatedBetweenVirtualThreads() throws Exception {
        ExecutorService vExec = newVirtualThreadExecutor();
        try {
            Future<?> f1 = vExec.submit(() -> DefaultMyJpaRepository.withAutoFilterOverride(true, () -> {
            }));
            f1.get(5, TimeUnit.SECONDS);

            Future<Boolean> f2 = vExec.submit(() -> autoFilterOverride.get());
            assertNull(f2.get(5, TimeUnit.SECONDS));

            assertNull(autoFilterOverride.get());
        } finally {
            vExec.shutdown();
        }
    }

    @Test
    void withAutoFilterOverride_worksInVirtualThread() throws Exception {
        ExecutorService vExec = newVirtualThreadExecutor();
        try {
            Future<Boolean> result = vExec.submit(() -> {
                AtomicBoolean inside = new AtomicBoolean(false);
                DefaultMyJpaRepository.withAutoFilterOverride(true, () -> inside.set(autoFilterOverride.get()));
                return inside.get() && autoFilterOverride.get() == null;
            });
            assertTrue(result.get(5, TimeUnit.SECONDS));
        } finally {
            vExec.shutdown();
        }
    }

    @Test
    void withAutoFilterOverride_supplier_worksInVirtualThread() throws Exception {
        ExecutorService vExec = newVirtualThreadExecutor();
        try {
            Future<String> result = vExec.submit(() -> DefaultMyJpaRepository.withAutoFilterOverride(true, () -> {
                assertTrue(autoFilterOverride.get());
                return "ok";
            }));
            assertEquals("ok", result.get(5, TimeUnit.SECONDS));
            assertNull(autoFilterOverride.get());
        } finally {
            vExec.shutdown();
        }
    }

    @Test
    void clearThreadLocal_worksInVirtualThread() throws Exception {
        ExecutorService vExec = newVirtualThreadExecutor();
        try {
            Future<Boolean> result = vExec.submit(() -> {
                DefaultMyJpaRepository.withAutoFilterOverride(true, () -> {
                });
                DefaultMyJpaRepository.clearThreadLocal();
                return autoFilterOverride.get() == null;
            });
            assertTrue(result.get(5, TimeUnit.SECONDS));
        } finally {
            vExec.shutdown();
        }
    }

    @Test
    void withAutoFilterOverride_exceptionStillCleansUp() throws Exception {
        ExecutorService vExec = newVirtualThreadExecutor();
        try {
            Future<Boolean> result = vExec.submit(() -> {
                try {
                    DefaultMyJpaRepository.withAutoFilterOverride(true, () -> {
                        throw new RuntimeException("simulated error");
                    });
                    return false;
                } catch (RuntimeException e) {
                    return autoFilterOverride.get() == null;
                }
            });
            assertTrue(result.get(5, TimeUnit.SECONDS));
        } finally {
            vExec.shutdown();
        }
    }

    @Test
    void manyVirtualThreads_concurrentWithOverride() throws Exception {
        ExecutorService vExec = newVirtualThreadExecutor();
        try {
            int threadCount = 100;
            java.util.List<Future<Boolean>> futures = new java.util.ArrayList<>();
            for (int i = 0; i < threadCount; i++) {
                futures.add(vExec.submit(() -> {
                    DefaultMyJpaRepository.withAutoFilterOverride(true, () -> assertTrue(autoFilterOverride.get()));
                    return autoFilterOverride.get() == null;
                }));
            }
            for (Future<Boolean> f : futures) {
                assertTrue(f.get(10, TimeUnit.SECONDS));
            }
            assertNull(autoFilterOverride.get());
        } finally {
            vExec.shutdown();
        }
    }
}
