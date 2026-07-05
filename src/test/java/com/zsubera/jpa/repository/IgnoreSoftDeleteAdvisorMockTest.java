package com.zsubera.jpa.repository;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.zsubera.jpa.annotation.IgnoreSoftDelete;
import java.lang.reflect.Method;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class IgnoreSoftDeleteAdvisorMockTest {

    private IgnoreSoftDeleteAdvisor advisor;

    @BeforeEach
    void setUp() {
        advisor = new IgnoreSoftDeleteAdvisor();
        SoftDeleteContext.reset();
    }

    @Test
    void aroundRepositoryMethod_noAnnotation_proceeds() throws Throwable {
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        Method method = NoAnnotationInterface.class.getMethod("findByName", String.class);
        when(pjp.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(method);
        when(pjp.proceed()).thenReturn("result");

        Object result = advisor.aroundRepositoryMethod(pjp);

        assertEquals("result", result);
        verify(pjp, times(1)).proceed();
        assertFalse(SoftDeleteContext.isIgnoreSoftDelete());
    }

    @Test
    void aroundRepositoryMethod_withAnnotation_pushesAndPops() throws Throwable {
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        Method method = AnnotatedInterface.class.getMethod("findAll");
        when(pjp.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(method);
        when(pjp.proceed()).thenReturn("result");

        Object result = advisor.aroundRepositoryMethod(pjp);

        assertEquals("result", result);
        verify(pjp, times(1)).proceed();
        assertFalse(SoftDeleteContext.isIgnoreSoftDelete());
    }

    @Test
    void aroundRepositoryMethod_withAnnotation_exceptionStillPops() throws Throwable {
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        Method method = AnnotatedInterface.class.getMethod("findAll");
        when(pjp.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(method);
        when(pjp.proceed()).thenThrow(new RuntimeException("test"));

        assertThrows(RuntimeException.class, () -> advisor.aroundRepositoryMethod(pjp));
        assertFalse(SoftDeleteContext.isIgnoreSoftDelete());
    }

    @Test
    void aroundRepositoryMethod_usesAnnotationCache() throws Throwable {
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        Method method = NoAnnotationInterface.class.getMethod("findById", Long.class);
        when(pjp.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(method);
        when(pjp.proceed()).thenReturn("result");

        advisor.aroundRepositoryMethod(pjp);
        advisor.aroundRepositoryMethod(pjp);

        verify(pjp, times(2)).proceed();
    }

    @Test
    void aroundRepositoryMethod_withAnnotation_repeatedCalls() throws Throwable {
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        Method method = AnnotatedInterface.class.getMethod("findAll");
        when(pjp.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(method);
        when(pjp.proceed()).thenReturn("result");

        advisor.aroundRepositoryMethod(pjp);
        assertFalse(SoftDeleteContext.isIgnoreSoftDelete());

        advisor.aroundRepositoryMethod(pjp);
        assertFalse(SoftDeleteContext.isIgnoreSoftDelete());
    }

    interface NoAnnotationInterface {
        Object findByName(String name);

        Object findById(Long id);
    }

    interface AnnotatedInterface {
        @IgnoreSoftDelete
        Object findAll();
    }
}
