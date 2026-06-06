package com.zsubera.jpa.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记方法的查询结果需要缓存。
 *
 * <p>
 * 与 {@link com.zsubera.jpa.template.QueryCacheManager} 配合使用，将查询结果缓存指定时长。 缓存键根据方法签名和参数生成，也可通过 {@link #key()} 自定义。
 *
 * <p>
 * 使用示例：
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
     * 缓存结果的存活时间（秒）。
     *
     * @return 过期时间（秒），默认 60
     */
    int expireSeconds() default 60;

    /**
     * 自定义缓存键。为空时，根据方法签名和参数自动生成。
     *
     * @return 缓存键字符串，默认为空（自动生成）
     */
    String key() default "";
}
