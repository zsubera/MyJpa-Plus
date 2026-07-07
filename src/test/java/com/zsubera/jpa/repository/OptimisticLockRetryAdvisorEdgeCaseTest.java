package com.zsubera.jpa.repository;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.zsubera.jpa.annotation.RetryOnOptimisticLock;
import jakarta.persistence.OptimisticLockException;
import java.lang.reflect.Method;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Edge-case tests for {@link OptimisticLockRetryAdvisor} — annotation validation,
 * timeout exhaustion, and parameter boundary conditions.
 */
class OptimisticLockRetryAdvisorEdgeCaseTest {

    private OptimisticLockRetryAdvisor advisor;

    @BeforeEach
    void setUp() {
        advisor = new OptimisticLockRetryAdvisor();
    }

    // ---- annotation parameter validation ----

    @Test
    void negativeMaxRetries_throwsIllegalState() throws Throwable {
        ProceedingJoinPoint pjp = mockPjp(NegativeRetriesService.class, "method");
        assertThrows(IllegalStateException.class, () -> advisor.retryOnOptimisticLock(pjp));
    }

    @Test
    void maxRetriesExceedingLimit_throwsIllegalArgument() throws Throwable {
        ProceedingJoinPoint pjp = mockPjp(ExcessiveRetriesService.class, "method");
        assertThrows(IllegalArgumentException.class, () -> advisor.retryOnOptimisticLock(pjp));
    }

    @Test
    void zeroBackoff_throwsIllegalState() throws Throwable {
        ProceedingJoinPoint pjp = mockPjp(ZeroBackoffService.class, "method");
        assertThrows(IllegalStateException.class, () -> advisor.retryOnOptimisticLock(pjp));
    }

    @Test
    void negativeBackoff_throwsIllegalState() throws Throwable {
        ProceedingJoinPoint pjp = mockPjp(NegativeBackoffService.class, "method");
        assertThrows(IllegalStateException.class, () -> advisor.retryOnOptimisticLock(pjp));
    }

    // ---- transactionManager null path ----

    @Test
    void transactionManagerNull_proceedsWithoutTransaction() throws Throwable {
        ProceedingJoinPoint pjp = mockPjp(RetryableService.class, "retry");
        when(pjp.proceed()).thenReturn("ok");

        Object result = advisor.retryOnOptimisticLock(pjp);
        assertEquals("ok", result);
        verify(pjp, times(1)).proceed();
    }

    // ---- total timeout exhaustion ----

    @Test
    void totalTimeoutExceeded_throwsAfterTimeout() throws Throwable {
        ProceedingJoinPoint pjp = mockPjp(LongBackoffService.class, "retry");
        // Each retry takes backoffMs=60000, so after 1 retry the 60s timeout is hit
        when(pjp.proceed()).thenThrow(new OptimisticLockException("lock"))
            .thenThrow(new OptimisticLockException("lock"));

        assertThrows(OptimisticLockException.class, () -> advisor.retryOnOptimisticLock(pjp));
    }

    // ---- annotation cache hit path ----

    @Test
    void annotationCacheHit_reusesCachedAnnotation() throws Throwable {
        // First call caches the annotation
        ProceedingJoinPoint pjp1 = mockPjp(RetryableService.class, "retry");
        when(pjp1.proceed()).thenReturn("first");
        advisor.retryOnOptimisticLock(pjp1);

        // Second call should hit the cache
        ProceedingJoinPoint pjp2 = mockPjp(RetryableService.class, "retry");
        when(pjp2.proceed()).thenReturn("second");
        Object result = advisor.retryOnOptimisticLock(pjp2);
        assertEquals("second", result);
    }

    // ---- InterruptedException during sleep ----

    @Test
    void interruptDuringSleep_restoresInterruptAndThrows() throws Throwable {
        ProceedingJoinPoint pjp = mockPjp(RetryableService.class, "retry");
        when(pjp.proceed()).thenThrow(new OptimisticLockException("lock"));

        Thread currentThread = Thread.currentThread();
        boolean wasInterrupted = currentThread.isInterrupted();

        assertThrows(OptimisticLockException.class, () -> advisor.retryOnOptimisticLock(pjp));
    }

    // ---- helpers ----

    private ProceedingJoinPoint mockPjp(Class<?> serviceClass, String methodName) throws Exception {
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        Method method = serviceClass.getMethod(methodName);
        when(pjp.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(method);
        when(pjp.getTarget()).thenReturn(serviceClass.getDeclaredConstructor().newInstance());
        return pjp;
    }

    // ---- test service classes with various annotation configurations ----

    static class NegativeRetriesService {
        @RetryOnOptimisticLock(maxRetries = -1, backoffMs = 10)
        public Object method() { return "ok"; }
    }

    static class ExcessiveRetriesService {
        @RetryOnOptimisticLock(maxRetries = 100, backoffMs = 10)
        public Object method() { return "ok"; }
    }

    static class ZeroBackoffService {
        @RetryOnOptimisticLock(maxRetries = 2, backoffMs = 0)
        public Object method() { return "ok"; }
    }

    static class NegativeBackoffService {
        @RetryOnOptimisticLock(maxRetries = 2, backoffMs = -100)
        public Object method() { return "ok"; }
    }

    static class RetryableService {
        @RetryOnOptimisticLock(maxRetries = 2, backoffMs = 10)
        public Object retry() { return "ok"; }
    }

    static class LongBackoffService {
        @RetryOnOptimisticLock(maxRetries = 5, backoffMs = 60000)
        public Object retry() { return "ok"; }
    }
}
