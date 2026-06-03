package com.zsubera.jpa.tenant;

import com.zsubera.jpa.annotation.IgnoreTenant;
import java.lang.reflect.Method;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * AOP 拦截器：自动检测 {@link IgnoreTenant @IgnoreTenant} 注解并设置 {@link TenantContext} 标志。
 *
 * <p>
 * 拦截所有 JPA Repository 方法调用，检查目标方法或接口上是否存在 {@code @IgnoreTenant} 注解。
 *
 * @see TenantContext
 * @see com.zsubera.jpa.annotation.IgnoreTenant
 */
@Aspect
@Component
@Order(Integer.MIN_VALUE + 101)
@ConditionalOnBean(TenantProvider.class)
public class IgnoreTenantAdvisor {

    private static final Logger log = LoggerFactory.getLogger(IgnoreTenantAdvisor.class);

    /** O-10: Cache annotation check results to avoid repeated reflection. P2-11: Use weak keys to prevent leak. */
    private static final java.util.concurrent.ConcurrentMap<Method, Boolean> ANNOTATION_CACHE =
        new org.springframework.util.ConcurrentReferenceHashMap<>(16,
            org.springframework.util.ConcurrentReferenceHashMap.ReferenceType.WEAK);

    /**
     * 拦截所有 Spring Data JPA Repository 方法调用。
     *
     * @param pjp 连接点
     * @return 方法执行结果
     * @throws Throwable 方法执行异常
     */
    @Around("within(org.springframework.data.jpa.repository.JpaRepository+)")
    public Object aroundRepositoryMethod(ProceedingJoinPoint pjp) throws Throwable {
        MethodSignature signature = (MethodSignature)pjp.getSignature();
        Method method = signature.getMethod();

        // O-10: Use cached annotation check to avoid repeated reflection
        Boolean hasAnnotation =
            ANNOTATION_CACHE.computeIfAbsent(method, m -> AnnotationUtils.findAnnotation(m, IgnoreTenant.class) != null
                || AnnotationUtils.findAnnotation(m.getDeclaringClass(), IgnoreTenant.class) != null);

        if (!hasAnnotation) {
            return pjp.proceed();
        }

        TenantContext.pushIgnore();
        try {
            if (log.isTraceEnabled()) {
                log.trace("Tenant filter bypassed for method: {}.{}", method.getDeclaringClass().getSimpleName(),
                    method.getName());
            }
            return pjp.proceed();
        } finally {
            TenantContext.popIgnore();
        }
    }
}
