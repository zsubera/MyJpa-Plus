package com.zsubera.jpa.repository;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.ReflectionUtils;

/**
 * 拦截仓库代理上的默认方法调用，路由到基类覆盖方法（通过普通 Java 反射实现虚分派）。
 *
 * <p>
 * Spring Data 的 {@code DefaultMethodInvokingMethodInterceptor} 使用
 * {@code MethodHandles.findSpecial()}（非虚分派）调用接口默认方法，绕过 CGLIB 代理的基类方法覆盖。
 * 本拦截器在其之前执行，通过 {@code Method.invoke(target, args)} 实现虚分派，
 * 确保 {@link DefaultMyJpaRepository} 中的覆盖方法被正确调用。
 *
 * @see DefaultMyJpaRepository#find(Class, java.util.function.Consumer)
 */
class DefaultMethodOverrideInterceptor implements MethodInterceptor {

    private static final Logger log = LoggerFactory.getLogger(DefaultMethodOverrideInterceptor.class);

    private final Object target;
    private final Map<Method, Method> overrideCache = new ConcurrentHashMap<>();

    DefaultMethodOverrideInterceptor(Object target) {
        this.target = target;
    }

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        Method method = invocation.getMethod();

        if (!method.isDefault()) {
            return invocation.proceed();
        }

        Method override = findOverride(method);
        if (override != null) {
            if (log.isTraceEnabled()) {
                log.trace("Routing default method {} to base class override {}", method.getName(),
                    override.getDeclaringClass().getSimpleName());
            }
            return override.invoke(target, invocation.getArguments());
        }

        return invocation.proceed();
    }

    private Method findOverride(Method interfaceMethod) {
        return overrideCache.computeIfAbsent(interfaceMethod, m -> {
            Method targetMethod = ReflectionUtils.findMethod(target.getClass(), m.getName(), m.getParameterTypes());
            if (targetMethod != null && targetMethod.getDeclaringClass() != m.getDeclaringClass()) {
                if (log.isTraceEnabled()) {
                    log.trace("Found override {} on {}", m.getName(), targetMethod.getDeclaringClass().getSimpleName());
                }
                ReflectionUtils.makeAccessible(targetMethod);
                return targetMethod;
            }
            if (log.isTraceEnabled()) {
                log.trace("No override found for {} (target: {}, declaringClass: {})",
                    m.getName(), target.getClass().getSimpleName(),
                    targetMethod != null ? targetMethod.getDeclaringClass().getSimpleName() : "null");
            }
            return null;
        });
    }
}
