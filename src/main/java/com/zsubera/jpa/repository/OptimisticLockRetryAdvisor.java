package com.zsubera.jpa.repository;

import com.zsubera.jpa.annotation.RetryOnOptimisticLock;
import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.PersistenceException;
import java.lang.reflect.Method;
import java.util.concurrent.ThreadLocalRandom;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

/**
 * AOP 通知器，拦截带有 {@link RetryOnOptimisticLock} 注解的方法，在遇到 {@link OptimisticLockException} 时使用指数退避策略进行重试。
 *
 * <p>
 * 每次重试在独立事务（{@code REQUIRES_NEW}）中执行，确保上一次失败的事务已回滚，
 * L1 缓存中的过期实体被清除，重试时能读取最新数据。
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

    @Autowired(required = false)
    private PlatformTransactionManager transactionManager;

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
            // 回退：从目标类及其实现的接口查找（CGLIB 代理场景下接口注解可能丢失）
            try {
                Method targetMethod =
                    pjp.getTarget().getClass().getMethod(method.getName(), method.getParameterTypes());
                annotation = AnnotationUtils.findAnnotation(targetMethod, RetryOnOptimisticLock.class);
            } catch (NoSuchMethodException e) {
                // 目标方法未找到，尝试遍历接口
                for (Class<?> iface : pjp.getTarget().getClass().getInterfaces()) {
                    try {
                        Method ifaceMethod = iface.getMethod(method.getName(), method.getParameterTypes());
                        annotation = AnnotationUtils.findAnnotation(ifaceMethod, RetryOnOptimisticLock.class);
                        if (annotation != null) {
                            break;
                        }
                    } catch (NoSuchMethodException ignored) {
                    }
                }
            }
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
        long startTime = System.currentTimeMillis();
        while (true) {
            try {
                // ponytail: 所有尝试统一在 REQUIRES_NEW 中执行，避免首次失败污染调用方事务
                return executeInNewTransaction(pjp);
            } catch (OptimisticLockException | ObjectOptimisticLockingFailureException ex) {
                attempt = handleRetry(ex, attempt, maxRetries, backoffMs, startTime, method, "");
            } catch (PersistenceException ex) {
                if (isOptimisticLockCause(ex)) {
                    attempt = handleRetry(ex, attempt, maxRetries, backoffMs, startTime, method, " (wrapped)");
                } else {
                    throw ex;
                }
            }
        }
    }

    /**
     * 在新事务中执行重试，确保上一次失败事务已回滚且 L1 缓存已清除。
     * 当 transactionManager 可用时始终使用 REQUIRES_NEW，即使当前无活动事务——这样即使
     * 第一次尝试在无事务上下文中执行，重试也能获得干净的持久化上下文。
     * 当 transactionManager 不可用时直接执行（降级路径）。
     */
    private Object executeInNewTransaction(ProceedingJoinPoint pjp) throws Throwable {
        if (transactionManager != null) {
            DefaultTransactionDefinition def = new DefaultTransactionDefinition();
            def.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            TransactionStatus status = transactionManager.getTransaction(def);
            try {
                Object result = pjp.proceed();
                transactionManager.commit(status);
                return result;
            } catch (RuntimeException e) {
                transactionManager.rollback(status);
                throw e;
            } catch (Exception e) {
                transactionManager.rollback(status);
                throw e;
            }
        }
        return pjp.proceed();
    }

    private int handleRetry(Exception ex, int attempt, int maxRetries, long backoffMs, long startTime,
        java.lang.reflect.Method method, String label) throws Exception {
        attempt++;
        if (attempt > maxRetries) {
            log.warn("OptimisticLockException{} after {} retries for method {}.{}", label, maxRetries,
                method.getDeclaringClass().getSimpleName(), method.getName());
            throw ex;
        }
        long totalElapsed = System.currentTimeMillis() - startTime;
        if (totalElapsed >= MAX_TOTAL_TIMEOUT_MS) {
            log.warn("OptimisticLockException{} after {} retries ({}ms elapsed, timeout={}ms) for method {}.{}", label,
                attempt, totalElapsed, MAX_TOTAL_TIMEOUT_MS, method.getDeclaringClass().getSimpleName(),
                method.getName());
            throw ex;
        }
        long shift = Math.min(attempt - 1, 30);
        long baseDelay = Math.min(backoffMs * (1L << shift), MAX_BACKOFF_MS);
        baseDelay = Math.max(baseDelay, 1);
        long remainingTimeout = MAX_TOTAL_TIMEOUT_MS - totalElapsed;
        baseDelay = Math.min(baseDelay, remainingTimeout);
        long jitter = (long)(baseDelay * 0.2 * ThreadLocalRandom.current().nextDouble());
        long delay = baseDelay + jitter;
        log.debug("OptimisticLockException{} on attempt {}/{} for method {}.{}, retrying in {}ms", label, attempt,
            maxRetries, method.getDeclaringClass().getSimpleName(), method.getName(), delay);
        try {
            Thread.sleep(delay);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            ex.addSuppressed(ie);
            throw ex;
        }
        return attempt;
    }

    /**
     * 检查 PersistenceException 的原因链中是否包含 OptimisticLockException。
     */
    private static boolean isOptimisticLockCause(PersistenceException ex) {
        Throwable cause = ex.getCause();
        while (cause != null) {
            if (cause instanceof OptimisticLockException) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }
}
