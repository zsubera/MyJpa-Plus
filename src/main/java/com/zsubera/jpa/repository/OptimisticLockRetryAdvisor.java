package com.zsubera.jpa.repository;

import com.zsubera.jpa.annotation.RetryOnOptimisticLock;
import jakarta.persistence.OptimisticLockException;
import java.lang.reflect.Method;
import java.util.concurrent.ThreadLocalRandom;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;

/**
 * AOP 通知器，拦截带有 {@link RetryOnOptimisticLock} 注解的方法，在遇到 {@link OptimisticLockException} 时使用指数退避策略进行重试。
 *
 * <p>
 * 示例：标注 {@code @RetryOnOptimisticLock(maxRetries = 3, backoffMs = 100)} 的方法将最多重试 3 次， 延迟分别为 100ms、200ms 和 400ms。
 */
@Aspect
@Component
public class OptimisticLockRetryAdvisor {

    private static final Logger log = LoggerFactory.getLogger(OptimisticLockRetryAdvisor.class);

    /** 最大退避延迟上限（毫秒），防止指数退避无限增长。 */
    private static final long MAX_BACKOFF_MS = 30_000;

    /** 最大重试次数硬限制，防止无限循环。 */
    private static final int MAX_RETRIES_LIMIT = 20;

    /** 所有重试的总超时限制（60 秒）。 */
    private static final long MAX_TOTAL_TIMEOUT_MS = 60_000;

    /**
     * 拦截带有 {@link RetryOnOptimisticLock} 注解的方法，在 {@link OptimisticLockException} 时重试。
     *
     * @param pjp 连接点
     * @return 方法返回值
     * @throws Throwable 如果所有重试耗尽或发生不可重试的异常
     */
    @Around("@annotation(com.zsubera.jpa.annotation.RetryOnOptimisticLock)")
    public Object retryOnOptimisticLock(ProceedingJoinPoint pjp) throws Throwable {
        MethodSignature signature = (MethodSignature)pjp.getSignature();
        Method method = signature.getMethod();
        // 使用 AnnotationUtils.findAnnotation 处理 Spring 代理场景
        // method.getAnnotation() 在代理接口方法上可能返回 null
        RetryOnOptimisticLock annotation = AnnotationUtils.findAnnotation(method, RetryOnOptimisticLock.class);
        if (annotation == null) {
            // 回退：从目标方法查找
            Method targetMethod = pjp.getTarget().getClass().getMethod(method.getName(), method.getParameterTypes());
            annotation = AnnotationUtils.findAnnotation(targetMethod, RetryOnOptimisticLock.class);
        }
        if (annotation == null) {
            return pjp.proceed();
        }

        int maxRetries = annotation.maxRetries();
        long backoffMs = annotation.backoffMs();

        // 校验注解参数
        if (maxRetries < 0) {
            throw new IllegalStateException(
                "@RetryOnOptimisticLock.maxRetries must be non-negative, got: " + maxRetries);
        }
        // 对 maxRetries 设置硬限制，防止无限循环
        if (maxRetries > MAX_RETRIES_LIMIT) {
            throw new IllegalArgumentException(
                "@RetryOnOptimisticLock.maxRetries exceeds hard limit of " + MAX_RETRIES_LIMIT + ", got: " + maxRetries
                    + ". Consider using a lower value to prevent excessive retry attempts.");
        }
        if (backoffMs <= 0) {
            throw new IllegalStateException("@RetryOnOptimisticLock.backoffMs must be positive, got: " + backoffMs);
        }

        int attempt = 0;
        long totalElapsed = 0;
        long startTime = System.currentTimeMillis();
        while (true) {
            try {
                return pjp.proceed();
            } catch (OptimisticLockException | ObjectOptimisticLockingFailureException ex) {
                attempt++;
                if (attempt > maxRetries) {
                    log.warn("OptimisticLockException after {} retries for method {}.{}", maxRetries,
                        method.getDeclaringClass().getSimpleName(), method.getName());
                    throw ex;
                }
                // 检查总超时，防止无限重试风暴
                totalElapsed = System.currentTimeMillis() - startTime;
                if (totalElapsed >= MAX_TOTAL_TIMEOUT_MS) {
                    log.warn("OptimisticLockException after {} retries ({}ms elapsed, timeout={}ms) for method {}.{}",
                        attempt, totalElapsed, MAX_TOTAL_TIMEOUT_MS, method.getDeclaringClass().getSimpleName(),
                        method.getName());
                    throw ex;
                }
                // 指数退避：backoffMs * 2^(attempt-1)，上限 MAX_BACKOFF_MS
                long baseDelay = Math.min(backoffMs * (1L << Math.min(attempt - 1, 30)), MAX_BACKOFF_MS);
                // 确保最小延迟为 1ms，防止紧密重试循环
                baseDelay = Math.max(baseDelay, 1);
                // 确保延迟不超过剩余超时时间
                long remainingTimeout = MAX_TOTAL_TIMEOUT_MS - totalElapsed;
                baseDelay = Math.min(baseDelay, remainingTimeout);
                // 正向抖动（基础延迟的 0~20%），防止多线程同时重试时的惊群效应
                long jitter = (long)(baseDelay * 0.1 * ThreadLocalRandom.current().nextDouble());
                long delay = baseDelay + jitter;
                log.debug("OptimisticLockException on attempt {}/{} for method {}.{}, retrying in {}ms", attempt,
                    maxRetries, method.getDeclaringClass().getSimpleName(), method.getName(), delay);
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    ex.addSuppressed(ie);
                    throw ex;
                }
            }
        }
    }
}
