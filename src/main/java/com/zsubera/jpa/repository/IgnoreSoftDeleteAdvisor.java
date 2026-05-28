package com.zsubera.jpa.repository;

import com.zsubera.jpa.annotation.IgnoreSoftDelete;
import java.lang.reflect.Method;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * AOP 拦截器：自动检测 {@link IgnoreSoftDelete @IgnoreSoftDelete} 注解并设置 {@link SoftDeleteContext} 标志。
 *
 * <p>
 * 替代原先基于 {@code Thread.currentThread().getStackTrace()} 的栈遍历方案。 拦截所有 Repository 方法调用，检查目标方法或接口上是否存在
 * {@code @IgnoreSoftDelete} 注解。
 *
 * @see SoftDeleteContext
 * @see SoftDeleteJpaRepository
 */
@Aspect
@Component
@Order(Integer.MIN_VALUE + 100)
public class IgnoreSoftDeleteAdvisor {

    private static final Logger log = LoggerFactory.getLogger(IgnoreSoftDeleteAdvisor.class);

    /**
     * 拦截所有 Spring Data Repository 方法调用。
     *
     * @param pjp 连接点
     * @return 方法执行结果
     * @throws Throwable 方法执行异常
     */
    @Around("within(com.zsubera.jpa.repository..*) || within(org.springframework.data.jpa.repository.support..*)")
    public Object aroundRepositoryMethod(ProceedingJoinPoint pjp) throws Throwable {
        MethodSignature signature = (MethodSignature)pjp.getSignature();
        Method method = signature.getMethod();

        // 检查方法级别注解
        boolean hasAnnotation = method.isAnnotationPresent(IgnoreSoftDelete.class);
        // 检查接口级别注解
        if (!hasAnnotation) {
            Class<?> declaringClass = method.getDeclaringClass();
            hasAnnotation = declaringClass.isAnnotationPresent(IgnoreSoftDelete.class);
        }

        if (hasAnnotation) {
            SoftDeleteContext.setIgnoreSoftDelete(true);
            if (log.isTraceEnabled()) {
                log.trace("Soft delete filter bypassed for method: {}.{}", method.getDeclaringClass().getSimpleName(),
                    method.getName());
            }
        }

        try {
            return pjp.proceed();
        } finally {
            if (hasAnnotation) {
                SoftDeleteContext.clear();
            }
        }
    }
}
