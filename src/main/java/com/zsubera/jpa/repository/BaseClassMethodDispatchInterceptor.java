package com.zsubera.jpa.repository;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.data.repository.core.support.RepositoryProxyPostProcessor;

/**
 * 修复 Spring Data 的 {@code DefaultMethodInvokingMethodInterceptor} 使用
 * {@code MethodHandles.findSpecial()}（非虚分派）调用接口默认方法时，
 * 绕过 CGLIB 代理的基类方法覆盖的问题。
 *
 * <p>
 * 此拦截器在代理链中优先于 {@code DefaultMethodInvokingMethodInterceptor} 执行。
 * 当检测到接口默认方法在基类中有覆盖时，直接通过反射调用基类的覆盖方法，
 * 避免非虚分派导致的 {@code UnsupportedOperationException}。
 *
 * @see DefaultMyJpaRepository#find(Class, org.springframework.data.jpa.domain.Specification)
 */
class BaseClassMethodDispatchInterceptor implements RepositoryProxyPostProcessor {

    private static final Logger log = LoggerFactory.getLogger(BaseClassMethodDispatchInterceptor.class);

    @Override
    public void postProcess(org.springframework.aop.framework.ProxyFactory factory,
        org.springframework.data.repository.core.RepositoryInformation metadata) {
        Class<?> baseClass = metadata.getRepositoryBaseClass();
        if (baseClass == null) {
            return;
        }
        // 预计算基类中可调用的方法
        Map<MethodSignature, Method> baseClassMethods = new ConcurrentHashMap<>();
        for (Method m : baseClass.getDeclaredMethods()) {
            m.setAccessible(true);
            baseClassMethods.put(new MethodSignature(m), m);
        }
        // 使用 addAdvisor(0, ...) 确保在链的最前面，优先于 DefaultMethodInvokingMethodInterceptor
        factory.addAdvisor(0, new DefaultPointcutAdvisor(new DispatchInterceptor(baseClass, baseClassMethods)));
    }

    /**
     * 方法拦截器：在 {@code DefaultMethodInvokingMethodInterceptor} 之前拦截接口默认方法调用。
     */
    private static class DispatchInterceptor implements MethodInterceptor {
        private final Class<?> baseClass;
        private final Map<MethodSignature, Method> baseClassMethods;

        DispatchInterceptor(Class<?> baseClass, Map<MethodSignature, Method> baseClassMethods) {
            this.baseClass = baseClass;
            this.baseClassMethods = baseClassMethods;
        }

        @Override
        public Object invoke(MethodInvocation invocation) throws Throwable {
            Method method = invocation.getMethod();
            if (!method.isDefault()) {
                return invocation.proceed();
            }
            // 检查基类是否有覆盖
            MethodSignature sig = new MethodSignature(method);
            Method baseMethod = baseClassMethods.get(sig);
            if (baseMethod != null && !java.lang.reflect.Modifier.isAbstract(baseMethod.getModifiers())) {
                // 直接通过反射调用基类的覆盖方法，绕过非虚分派
                return baseMethod.invoke(invocation.getThis(), invocation.getArguments());
            }
            return invocation.proceed();
        }
    }

    /**
     * 方法签名（用于 Map 键匹配），忽略返回类型和泛型。
     */
    private record MethodSignature(String name, Class<?>[] parameterTypes) {
        MethodSignature(Method method) {
            this(method.getName(), method.getParameterTypes());
        }
    }
}
