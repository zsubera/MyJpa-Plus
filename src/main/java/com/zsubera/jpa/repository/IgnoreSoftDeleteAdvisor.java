package com.zsubera.jpa.repository;

import com.zsubera.jpa.annotation.IgnoreSoftDelete;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicInteger;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * AOP 拦截器：自动检测 {@link IgnoreSoftDelete @IgnoreSoftDelete} 注解并设置 {@link SoftDeleteContext} 标志。
 *
 * <p>
 * 替代原先基于 {@code Thread.currentThread().getStackTrace()} 的栈遍历方案。 拦截所有 JPA Repository 方法调用（包括
 * {@link com.zsubera.jpa.repository.MyJpaRepository} 和 {@link DefaultMyJpaRepository}），检查目标方法或接口上是否存在
 * {@code @IgnoreSoftDelete} 注解。
 *
 * @see SoftDeleteContext
 * @see DefaultMyJpaRepository
 * @see com.zsubera.jpa.repository.MyJpaRepository
 */
@Aspect
@Component
@Order(Integer.MIN_VALUE + 200)
public class IgnoreSoftDeleteAdvisor {

    private static final Logger log = LoggerFactory.getLogger(IgnoreSoftDeleteAdvisor.class);

    /**
     * 缓存注解检查结果，避免重复反射。使用 Method 对象作为 key（identity-based），避免 hashCode 碰撞。
     * ponytail: 添加采样驱逐防止动态代理类名不断变化导致内存泄漏。
     */
    private static final java.util.concurrent.ConcurrentMap<Method, Boolean> ANNOTATION_CACHE =
        new java.util.concurrent.ConcurrentHashMap<>();

    /** ANNOTATION_CACHE 最大条目数，超过时触发采样驱逐。 */
    private static final int MAX_ANNOTATION_CACHE_SIZE = 4096;

    /** 采样计数器，每 256 次调用检查一次缓存大小。 */
    private static final java.util.concurrent.atomic.AtomicInteger EVICTION_COUNTER =
        new java.util.concurrent.atomic.AtomicInteger(0);

    /**
     * 拦截所有 Spring Data JPA Repository 方法调用。
     *
     * <p>
     * 切面范围覆盖所有 {@code JpaRepository} 子接口，确保 {@code @IgnoreSoftDelete} 注解在 {@link DefaultMyJpaRepository} 和
     * {@link com.zsubera.jpa.repository.MyJpaRepository} 上均生效。
     *
     * <p>
     * 优化：使用缓存避免对无注解方法的重复反射检查。
     *
     * @param pjp 连接点
     * @return 方法执行结果
     * @throws Throwable 方法执行异常
     */
    @Around("within(org.springframework.data.jpa.repository.JpaRepository+)")
    public Object aroundRepositoryMethod(ProceedingJoinPoint pjp) throws Throwable {
        MethodSignature signature = (MethodSignature)pjp.getSignature();
        Method method = signature.getMethod();

        // ponytail: 采样驱逐——每 256 次调用检查缓存大小，超过上限时清空防止动态代理类名导致内存泄漏
        if ((EVICTION_COUNTER.incrementAndGet() & 255) == 0 && ANNOTATION_CACHE.size() > MAX_ANNOTATION_CACHE_SIZE) {
            ANNOTATION_CACHE.clear();
        }

        Boolean hasAnnotation = ANNOTATION_CACHE.computeIfAbsent(method,
            m -> AnnotationUtils.findAnnotation(m, IgnoreSoftDelete.class) != null
                || AnnotationUtils.findAnnotation(m.getDeclaringClass(), IgnoreSoftDelete.class) != null);

        if (!hasAnnotation) {
            return pjp.proceed();
        }

        SoftDeleteContext.pushIgnore();
        try {
            if (log.isTraceEnabled()) {
                log.trace("Soft delete filter bypassed for method: {}.{}", method.getDeclaringClass().getSimpleName(),
                    method.getName());
            }
            return pjp.proceed();
        } finally {
            // 使用 popIgnore() 替代 clear()，支持嵌套调用场景
            SoftDeleteContext.popIgnore();
        }
    }
}
