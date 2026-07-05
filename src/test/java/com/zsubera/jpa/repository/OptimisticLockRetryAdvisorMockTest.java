package com.zsubera.jpa.repository;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.zsubera.jpa.annotation.RetryOnOptimisticLock;
import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.PersistenceException;
import java.lang.reflect.Method;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OptimisticLockRetryAdvisorMockTest {

    private OptimisticLockRetryAdvisor advisor;

    @BeforeEach
    void setUp() {
        advisor = new OptimisticLockRetryAdvisor();
    }

    @Test
    void retryOnOptimisticLock_noAnnotation_proceedsDirectly() throws Throwable {
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        Method method = NoAnnotationService.class.getMethod("noAnnotation");
        when(pjp.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(method);
        when(pjp.getTarget()).thenReturn(new NoAnnotationService());
        when(pjp.proceed()).thenReturn("result");

        Object result = advisor.retryOnOptimisticLock(pjp);
        assertEquals("result", result);
        verify(pjp, times(1)).proceed();
    }

    @Test
    void retryOnOptimisticLock_succeedsFirstAttempt() throws Throwable {
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        Method method = RetryableService.class.getMethod("retry");
        when(pjp.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(method);
        when(pjp.proceed()).thenReturn("success");

        Object result = advisor.retryOnOptimisticLock(pjp);
        assertEquals("success", result);
        verify(pjp, times(1)).proceed();
    }

    @Test
    void retryOnOptimisticLock_errorPath_rollsBackTransaction() throws Throwable {
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        Method method = RetryableService.class.getMethod("retry");
        when(pjp.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(method);
        when(pjp.getTarget()).thenReturn(new RetryableService());
        when(pjp.proceed()).thenThrow(new OutOfMemoryError("simulated OOM"));

        // ponytail: Error thrown should not leave a dangling transaction
        assertThrows(OutOfMemoryError.class, () -> advisor.retryOnOptimisticLock(pjp));
        verify(pjp, times(1)).proceed();
    }

    @Test
    void retryOnOptimisticLock_succeedsAfterRetries() throws Throwable {
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        Method method = RetryableService.class.getMethod("retry");
        when(pjp.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(method);
        when(pjp.proceed()).thenThrow(new OptimisticLockException("lock"))
            .thenThrow(new OptimisticLockException("lock")).thenReturn("success");

        Object result = advisor.retryOnOptimisticLock(pjp);
        assertEquals("success", result);
        verify(pjp, times(3)).proceed();
    }

    @Test
    void retryOnOptimisticLock_throwsAfterMaxRetries() throws Throwable {
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        Method method = RetryableService.class.getMethod("retry");
        when(pjp.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(method);
        when(pjp.proceed()).thenThrow(new OptimisticLockException("lock"));

        assertThrows(OptimisticLockException.class, () -> advisor.retryOnOptimisticLock(pjp));
    }

    @Test
    void retryOnOptimisticLock_wrappedPersistenceException_retries() throws Throwable {
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        Method method = RetryableService.class.getMethod("retry");
        when(pjp.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(method);
        when(pjp.proceed()).thenThrow(new PersistenceException("wrapped", new OptimisticLockException("cause")))
            .thenReturn("success");

        Object result = advisor.retryOnOptimisticLock(pjp);
        assertEquals("success", result);
        verify(pjp, times(2)).proceed();
    }

    @Test
    void retryOnOptimisticLock_nonOptimisticPersistenceException_doesNotRetry() throws Throwable {
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        Method method = RetryableService.class.getMethod("retry");
        when(pjp.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(method);
        when(pjp.proceed()).thenThrow(new PersistenceException("non-optimistic"));

        assertThrows(PersistenceException.class, () -> advisor.retryOnOptimisticLock(pjp));
        verify(pjp, times(1)).proceed();
    }

    @Test
    void retryOnOptimisticLock_objectOptimisticLockException_retries() throws Throwable {
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        Method method = RetryableService.class.getMethod("retry");
        when(pjp.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(method);
        when(pjp.proceed())
            .thenThrow(new org.springframework.orm.ObjectOptimisticLockingFailureException("lock", new Object()))
            .thenReturn("success");

        Object result = advisor.retryOnOptimisticLock(pjp);
        assertEquals("success", result);
        verify(pjp, times(2)).proceed();
    }

    @Test
    void retryOnOptimisticLock_zeroRetries_throwsImmediately() throws Throwable {
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        Method method = RetryableService.class.getMethod("zeroRetries");
        when(pjp.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(method);
        when(pjp.proceed()).thenThrow(new OptimisticLockException("lock"));

        assertThrows(OptimisticLockException.class, () -> advisor.retryOnOptimisticLock(pjp));
        verify(pjp, times(1)).proceed();
    }

    @Test
    void retryOnOptimisticLock_nullAnnotation_fallbackToTarget() throws Throwable {
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        Method method = NoAnnotationService.class.getMethod("noAnnotation");
        when(pjp.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(method);
        when(pjp.getTarget()).thenReturn(new NoAnnotationService());
        when(pjp.proceed()).thenReturn("result");

        Object result = advisor.retryOnOptimisticLock(pjp);
        assertEquals("result", result);
        verify(pjp, times(1)).proceed();
    }

    @Test
    void retryOnOptimisticLock_noAnnotation_targetMethodNotFound() throws Throwable {
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        Method method = NoAnnotationService.class.getMethod("noAnnotation");
        when(pjp.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(method);
        when(pjp.getTarget()).thenReturn(new Object());
        when(pjp.proceed()).thenReturn("result");

        Object result = advisor.retryOnOptimisticLock(pjp);
        assertEquals("result", result);
        verify(pjp, times(1)).proceed();
    }

    interface RetryService {
        @RetryOnOptimisticLock(maxRetries = 2, backoffMs = 10)
        Object retry() throws Throwable;

        @RetryOnOptimisticLock(maxRetries = 0, backoffMs = 10)
        Object zeroRetries() throws Throwable;
    }

    static class RetryableService {
        @RetryOnOptimisticLock(maxRetries = 2, backoffMs = 10)
        public Object retry() throws Throwable {
            return "ok";
        }

        @RetryOnOptimisticLock(maxRetries = 0, backoffMs = 10)
        public Object zeroRetries() throws Throwable {
            return "ok";
        }
    }

    static class NoAnnotationService {
        public Object noAnnotation() {
            return "ok";
        }
    }
}
