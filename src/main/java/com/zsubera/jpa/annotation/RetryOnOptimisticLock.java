package com.zsubera.jpa.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method for automatic retry on {@link jakarta.persistence.OptimisticLockException}.
 *
 * <p>
 * When an {@code OptimisticLockException} is thrown, the advisor will retry the method up to {@link #maxRetries()}
 * times with exponential backoff starting at {@link #backoffMs()} milliseconds.
 *
 * <p>
 * Example usage:
 *
 * <pre>{@code
 * @RetryOnOptimisticLock(maxRetries = 5, backoffMs = 200)
 * public void updateProduct(Long id, String newName) {
 *     Product p = repository.findById(id).orElseThrow();
 *     p.setName(newName);
 *     repository.save(p);
 * }
 * }</pre>
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RetryOnOptimisticLock {

    /**
     * Maximum number of retry attempts after the initial failure.
     *
     * @return max retries, default 3
     */
    int maxRetries() default 3;

    /**
     * Initial backoff duration in milliseconds before the first retry. Subsequent retries use exponential backoff
     * (backoffMs * 2^attempt).
     *
     * @return backoff in milliseconds, default 100
     */
    long backoffMs() default 100;
}
