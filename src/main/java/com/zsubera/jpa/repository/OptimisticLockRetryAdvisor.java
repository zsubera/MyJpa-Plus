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

    /** Maximum allowed retries hard limit to prevent infinite loops. */
    private static final int MAX_RETRIES_LIMIT = 20;

    /** Total timeout limit for all retries combined (60 seconds). */
    private static final long MAX_TOTAL_TIMEOUT_MS = 60_000;

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

        // Validate annotation parameters
        if (maxRetries < 0) {
            throw new IllegalStateException(
                "@RetryOnOptimisticLock.maxRetries must be non-negative, got: " + maxRetries);
        }
        // Hard limit on maxRetries to prevent infinite loops
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
                // Check total timeout to prevent infinite retry storms
                totalElapsed = System.currentTimeMillis() - startTime;
                if (totalElapsed >= MAX_TOTAL_TIMEOUT_MS) {
                    log.warn("OptimisticLockException after {} retries ({}ms elapsed, timeout={}ms) for method {}.{}",
                        attempt, totalElapsed, MAX_TOTAL_TIMEOUT_MS, method.getDeclaringClass().getSimpleName(),
                        method.getName());
                    throw ex;
                }
                // Cap the shift amount to prevent Long overflow.
                // backoffMs * (1L << shift) must not overflow. Since MAX_BACKOFF_MS is 30_000,
                // the effective delay is always capped, but we prevent the intermediate multiplication
                // from overflowing by capping shift at 44 (safe for any backoffMs up to ~500,000).
                int shift = Math.min(attempt - 1, 44);
                long shiftValue = (shift >= 63) ? Long.MAX_VALUE : (1L << shift);
                long safeShift = Math.min(shiftValue, MAX_BACKOFF_MS / Math.max(backoffMs, 1));
                long baseDelay = Math.min(backoffMs * safeShift, MAX_BACKOFF_MS);
                // Ensure minimum delay of 1ms to prevent tight retry loops
                baseDelay = Math.max(baseDelay, 1);
                // Ensure delay does not exceed remaining timeout
                long remainingTimeout = MAX_TOTAL_TIMEOUT_MS - totalElapsed;
                baseDelay = Math.min(baseDelay, remainingTimeout);
                // O-09: Jitter can be positive or negative (spread ±10% of base delay)
                // to prevent thundering herd when multiple threads retry simultaneously
                long jitter = (long)(baseDelay * (ThreadLocalRandom.current().nextDouble() - 0.5) * 0.2);
                long delay = Math.max(0, baseDelay + jitter);
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
