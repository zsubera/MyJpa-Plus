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
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;

/**
 * AOP advisor that intercepts methods annotated with {@link RetryOnOptimisticLock} and retries on
 * {@link OptimisticLockException} with exponential backoff.
 *
 * <p>
 * Example: a method annotated with {@code @RetryOnOptimisticLock(maxRetries = 3, backoffMs = 100)} will retry up to 3
 * times with delays of 100ms, 200ms, and 400ms.
 */
@Aspect
@Component
public class OptimisticLockRetryAdvisor {

    private static final Logger log = LoggerFactory.getLogger(OptimisticLockRetryAdvisor.class);

    /** 最大退避延迟上限（毫秒），防止指数退避无限增长。 */
    private static final long MAX_BACKOFF_MS = 30_000;

    /**
     * Intercepts methods annotated with {@link RetryOnOptimisticLock} and retries on {@link OptimisticLockException}.
     *
     * @param pjp the proceeding join point
     * @return the method result
     * @throws Throwable if all retries are exhausted or a non-retryable exception occurs
     */
    @Around("@annotation(com.zsubera.jpa.annotation.RetryOnOptimisticLock)")
    public Object retryOnOptimisticLock(ProceedingJoinPoint pjp) throws Throwable {
        MethodSignature signature = (MethodSignature)pjp.getSignature();
        Method method = signature.getMethod();
        RetryOnOptimisticLock annotation = method.getAnnotation(RetryOnOptimisticLock.class);

        int maxRetries = annotation.maxRetries();
        long backoffMs = annotation.backoffMs();

        // P2: Validate annotation parameters
        if (maxRetries < 0) {
            throw new IllegalStateException(
                "@RetryOnOptimisticLock.maxRetries must be non-negative, got: " + maxRetries);
        }
        if (backoffMs <= 0) {
            throw new IllegalStateException("@RetryOnOptimisticLock.backoffMs must be positive, got: " + backoffMs);
        }

        int attempt = 0;
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
                // P0: Cap the shift amount to prevent Long overflow (1L << 46 is safe, 1L << 47 overflows)
                long baseDelay = Math.min(backoffMs * (1L << Math.min(attempt - 1, 46)), MAX_BACKOFF_MS);
                // P1: Ensure jitter is always positive (minimum 10% of base delay)
                long jitter = (long)(baseDelay * ThreadLocalRandom.current().nextDouble(0.1, 0.25));
                long delay = baseDelay + jitter;
                log.debug("OptimisticLockException on attempt {}/{} for method {}.{}, retrying in {}ms", attempt,
                    maxRetries, method.getDeclaringClass().getSimpleName(), method.getName(), delay);
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw ex;
                }
            }
        }
    }
}
