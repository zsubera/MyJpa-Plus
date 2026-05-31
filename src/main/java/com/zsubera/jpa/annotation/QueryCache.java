package com.zsubera.jpa.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method whose query results should be cached.
 *
 * <p>
 * Used with {@link com.zsubera.jpa.template.QueryCacheManager} to cache query results for a specified duration. The
 * cache key is derived from the method signature and arguments, or can be customized via {@link #key()}.
 *
 * <p>
 * Example usage:
 *
 * <pre>{@code
 * @QueryCache(expireSeconds = 120, key = "active-users")
 * public List<User> findActiveUsers() {
 *     return repository.findByStatus("ACTIVE");
 * }
 * }</pre>
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface QueryCache {

    /**
     * Time-to-live in seconds for cached results.
     *
     * @return expiration seconds, default 60
     */
    int expireSeconds() default 60;

    /**
     * Custom cache key. If empty, a key is generated from the method signature and arguments.
     *
     * @return cache key string, default empty (auto-generated)
     */
    String key() default "";
}
