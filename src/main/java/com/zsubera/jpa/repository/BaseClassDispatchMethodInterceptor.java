package com.zsubera.jpa.repository;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.ProxyMethodInvocation;
import org.springframework.data.projection.DefaultMethodInvokingMethodInterceptor;
import org.springframework.data.repository.core.support.RepositoryProxyPostProcessor;

/**
 * 替换 Spring Data 的 {@link DefaultMethodInvokingMethodInterceptor}，使用
 * {@code MethodHandles.findVirtual()}（虚分派）替代 {@code findSpecial()}（非虚分派）。
 *
 * <p>
 * Spring Data 的 {@code DefaultMethodInvokingMethodInterceptor} 使用
 * {@code MethodHandles.findSpecial()} 创建非虚方法句柄。当在 CGLIB 代理上调用时，
 * 直接调用接口方法，绕过 CGLIB 的方法覆盖和拦截器链。
 *
 * <p>
 * 本拦截器使用 {@code findVirtual()} 创建虚方法句柄。当在 CGLIB 代理上调用时，
 * JVM 通过虚方法表找到代理类的覆盖方法（{@link DefaultMyJpaRepository} 中的实现），
 * 从而正确触发 CGLIB 拦截器链。
 *
 * @see DefaultMethodInvokingMethodInterceptor
 * @see DefaultMyJpaRepository#find(Class, java.util.function.Consumer)
 * @see DefaultMyJpaRepository#find(Class, org.springframework.data.jpa.domain.Specification)
 */
class BaseClassDispatchMethodInterceptor implements RepositoryProxyPostProcessor {

    private static final Logger log = LoggerFactory.getLogger(BaseClassDispatchMethodInterceptor.class);

    @Override
    public void postProcess(org.springframework.aop.framework.ProxyFactory factory,
        org.springframework.data.repository.core.RepositoryInformation metadata) {
        // 查找并移除 DefaultMethodInvokingMethodInterceptor
        Object[] advisors = factory.getAdvisors();
        for (int i = 0; i < advisors.length; i++) {
            if (advisors[i] instanceof org.springframework.aop.Advisor advisor) {
                if (advisor.getAdvice() instanceof DefaultMethodInvokingMethodInterceptor) {
                    factory.removeAdvisor(i);
                    log.debug(
                        "Removed DefaultMethodInvokingMethodInterceptor, replacing with virtual-dispatch version");
                    break;
                }
            }
        }
        // 添加使用虚分派的拦截器
        factory.addAdvice(new VirtualDispatchInterceptor());
    }

    /**
     * 使用 {@code MethodHandles.findVirtual()}（虚分派）调用接口默认方法。
     * 通过 ThreadLocal 检测递归调用，避免无限循环。
     */
    private static class VirtualDispatchInterceptor implements MethodInterceptor {

        private static final ThreadLocal<Boolean> INVOKE_DEPTH = new ThreadLocal<>();
        private final ConcurrentHashMap<Method, MethodHandle> handleCache = new ConcurrentHashMap<>();

        @Override
        public Object invoke(MethodInvocation invocation) throws Throwable {
            Method method = invocation.getMethod();
            if (!method.isDefault()) {
                return invocation.proceed();
            }
            // 检测递归：如果已在调用中，直接 proceed 避免无限循环
            Boolean depth = INVOKE_DEPTH.get();
            if (depth != null && depth) {
                return invocation.proceed();
            }
            INVOKE_DEPTH.set(true);
            try {
                MethodHandle handle = handleCache.computeIfAbsent(method, this::createVirtualHandle);
                Object proxy = getProxy(invocation);
                return handle.bindTo(proxy).invokeWithArguments(invocation.getArguments());
            } finally {
                INVOKE_DEPTH.remove();
            }
        }

        private MethodHandle createVirtualHandle(Method method) {
            try {
                Class<?> declaringClass = method.getDeclaringClass();
                MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(declaringClass, MethodHandles.lookup());
                MethodType type = MethodType.methodType(method.getReturnType(), method.getParameterTypes());
                // 使用 findVirtual 实现虚分派，而非 findSpecial 的非虚分派
                return lookup.findVirtual(declaringClass, method.getName(), type);
            } catch (Exception e) {
                throw new RuntimeException("Failed to create virtual method handle for " + method, e);
            }
        }

        private Object getProxy(MethodInvocation invocation) {
            if (invocation instanceof ProxyMethodInvocation pmi) {
                return pmi.getProxy();
            }
            return invocation.getThis();
        }
    }
}
