package com.zsubera.jpa.template;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.time.Instant;

/**
 * 带时间戳和基于TTL过期的缓存查询结果包装器。
 *
 * @param <T> 缓存结果的类型
 */
@SuppressFBWarnings(value = "CT_CONSTRUCTOR_THROW",
    justification = "Constructor validates parameters via IllegalArgumentException which is standard Java practice")
public class CachedQueryResult<T> {

    private final T value;
    private final Instant createdAt;
    private final long ttlSeconds;
    /** 预计算的过期时间，避免在每次调用isExpired()时创建新的Instant。 */
    private final Instant expiresAt;

    /**
     * 创建新的缓存结果。
     *
     * @param value 缓存的值
     * @param ttlSeconds 以秒为单位的生存时间
     */
    public CachedQueryResult(T value, long ttlSeconds) {
        // 验证值不为null，防止缓存null结果
        if (value == null) {
            throw new IllegalArgumentException("CachedQueryResult value must not be null");
        }
        this.value = value;
        this.createdAt = Instant.now();
        this.ttlSeconds = ttlSeconds;
        this.expiresAt = createdAt.plusSeconds(ttlSeconds);
    }

    /**
     * 返回缓存的值。
     *
     * @return 缓存值
     */
    public T getValue() {
        return value;
    }

    /**
     * 返回创建时间戳。
     *
     * @return 创建时间
     */
    public Instant getCreatedAt() {
        return createdAt;
    }

    /**
     * 返回以秒为单位的TTL。
     *
     * @return TTL秒数
     */
    public long getTtlSeconds() {
        return ttlSeconds;
    }

    /**
     * 检查此缓存结果是否已过期。
     *
     * @return 如果结果已超过其TTL则返回true
     */
    public boolean isExpired() {
        return !Instant.now().isBefore(expiresAt);
    }
}
