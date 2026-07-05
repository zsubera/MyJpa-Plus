package com.zsubera.jpa.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记方法在遇到 {@link jakarta.persistence.OptimisticLockException} 时自动重试。
 *
 * <p>
 * 当抛出 {@code OptimisticLockException} 时，切面会以 {@link #backoffMs()} 毫秒为起始间隔， 指数退避方式重试最多 {@link #maxRetries()} 次。
 *
 * <p>
 * 使用示例：
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
     * 初始失败后的最大重试次数。
     *
     * @return 最大重试次数，默认 3
     */
    int maxRetries() default 3;

    /**
     * 首次重试前的初始退避时长（毫秒）。后续重试采用指数退避（backoffMs * 2^attempt）。
     *
     * @return 退避时长（毫秒），默认 100
     */
    long backoffMs() default 100;
}
