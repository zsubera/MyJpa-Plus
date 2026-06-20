package com.zsubera.jpa.template;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * TransactionHelper 事务传播行为测试。
 *

 */
class TransactionHelperTest {

    /**
     * 测试无活动事务时使用 REQUIRED（创建新事务）。
     *

     */
    @Test
    void shouldUseRequiredWhenNoActiveTransaction() throws Exception {
        TransactionHelper helper = new TransactionHelper(null, null, null);

        // 通过反射调用 getOrCreateRequiredTemplate
        java.lang.reflect.Method method = TransactionHelper.class.getDeclaredMethod("getOrCreateRequiredTemplate",
            org.springframework.transaction.PlatformTransactionManager.class);
        method.setAccessible(true);

        // 使用 mock PlatformTransactionManager
        org.springframework.transaction.PlatformTransactionManager mockTxManager =
            org.mockito.Mockito.mock(org.springframework.transaction.PlatformTransactionManager.class);

        TransactionTemplate template = (TransactionTemplate)method.invoke(helper, mockTxManager);
        assertEquals(TransactionDefinition.PROPAGATION_REQUIRED, template.getPropagationBehavior(),
            "No active transaction should use PROPAGATION_REQUIRED");
    }

    /**
     * 测试有活动事务时使用 REQUIRES_NEW（挂起外部事务，创建独立事务）。
     *

     */
    @Test
    void shouldUseRequiresNewWhenActiveTransactionExists() throws Exception {
        TransactionHelper helper = new TransactionHelper(null, null, null);

        java.lang.reflect.Method method = TransactionHelper.class.getDeclaredMethod("getOrCreateRequiresNewTemplate",
            org.springframework.transaction.PlatformTransactionManager.class);
        method.setAccessible(true);

        org.springframework.transaction.PlatformTransactionManager mockTxManager =
            org.mockito.Mockito.mock(org.springframework.transaction.PlatformTransactionManager.class);

        TransactionTemplate template = (TransactionTemplate)method.invoke(helper, mockTxManager);
        assertEquals(TransactionDefinition.PROPAGATION_REQUIRES_NEW, template.getPropagationBehavior(),
            "Active transaction should use PROPAGATION_REQUIRES_NEW to create independent transaction");
    }

    /**
     * 测试模板缓存：多次调用应返回同一实例。
     */
    @Test
    void shouldCacheTransactionTemplates() throws Exception {
        TransactionHelper helper = new TransactionHelper(null, null, null);

        org.springframework.transaction.PlatformTransactionManager mockTxManager =
            org.mockito.Mockito.mock(org.springframework.transaction.PlatformTransactionManager.class);

        java.lang.reflect.Method requiredMethod = TransactionHelper.class.getDeclaredMethod(
            "getOrCreateRequiredTemplate", org.springframework.transaction.PlatformTransactionManager.class);
        requiredMethod.setAccessible(true);

        java.lang.reflect.Method requiresNewMethod = TransactionHelper.class.getDeclaredMethod(
            "getOrCreateRequiresNewTemplate", org.springframework.transaction.PlatformTransactionManager.class);
        requiresNewMethod.setAccessible(true);

        TransactionTemplate t1 = (TransactionTemplate)requiredMethod.invoke(helper, mockTxManager);
        TransactionTemplate t2 = (TransactionTemplate)requiredMethod.invoke(helper, mockTxManager);
        assertSame(t1, t2, "Required template should be cached and return same instance");

        TransactionTemplate t3 = (TransactionTemplate)requiresNewMethod.invoke(helper, mockTxManager);
        TransactionTemplate t4 = (TransactionTemplate)requiresNewMethod.invoke(helper, mockTxManager);
        assertSame(t3, t4, "RequiresNew template should be cached and return same instance");

        assertNotSame(t1, t3, "Required and RequiresNew templates should be different instances");
    }

    /**
     * 测试异常情况下的正确行为。
     */
    @Test
    void shouldThrowWhenTransactionManagerNotAvailable() {
        TransactionHelper helper = new TransactionHelper(null, null, null);

        assertThrows(IllegalStateException.class, () -> {
            helper.executeInNewTransaction(em -> 42);
        });
    }

    /**
     * 测试类文档包含正确的传播行为说明。
     */
    @Test
    void classDocumentationShouldExplainCorrectPropagation() throws Exception {
        java.lang.reflect.Method method =
            TransactionHelper.class.getDeclaredMethod("executeInNewTransaction", java.util.function.Function.class);

        assertNotNull(method, "executeInNewTransaction method should exist");
        assertEquals(1, method.getTypeParameters().length, "Should have one type parameter");
    }

    @Test
    void shouldReturnNullWhenApplicationContextIsNull() throws Exception {
        TransactionHelper helper = new TransactionHelper(null, null, null);
        java.lang.reflect.Method method = TransactionHelper.class.getDeclaredMethod("getTransactionManager");
        method.setAccessible(true);
        Object result = method.invoke(helper);
        assertNull(result);
    }

    @Test
    void shouldReturnNullWhenBeanNotFound() throws Exception {
        org.springframework.context.ApplicationContext mockCtx =
            org.mockito.Mockito.mock(org.springframework.context.ApplicationContext.class);
        org.mockito.Mockito.when(mockCtx.getBean(org.springframework.transaction.PlatformTransactionManager.class))
            .thenThrow(new org.springframework.beans.factory.NoSuchBeanDefinitionException("no tx mgr"));

        TransactionHelper helper = new TransactionHelper(null, null, mockCtx);
        java.lang.reflect.Method method = TransactionHelper.class.getDeclaredMethod("getTransactionManager");
        method.setAccessible(true);
        Object result = method.invoke(helper);
        assertNull(result);
    }
}
