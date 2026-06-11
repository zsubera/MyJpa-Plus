package com.zsubera.jpa.template;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * 带时间戳和基于TTL过期的缓存查询结果包装器。
 *
 * <p>
 * 使用 {@link System#nanoTime()}（单调时钟）计算过期时间，避免系统时钟调整导致的缓存异常过期或永不过期。
 *
 * @param <T> 缓存结果的类型
 */
public class CachedQueryResult<T> {

    private final T value;
    private final long ttlSeconds;
    /** 使用单调时钟记录创建时刻（纳秒）。 */
    private final long createdAtNanos;
    /** 预计算的过期时刻（纳秒），避免在每次调用 isExpired() 时重复计算。 */
    private final long expiresAtNanos;

    /**
     * 创建新的缓存结果。
     *
     * @param value 缓存的值
     * @param ttlSeconds 以秒为单位的生存时间
     */
    @SuppressFBWarnings(value = "CT_CONSTRUCTOR_THROW",
        justification = "Constructor validates parameters via IllegalArgumentException which is standard Java practice")
    public CachedQueryResult(T value, long ttlSeconds) {
        if (value == null) {
            throw new IllegalArgumentException("CachedQueryResult value must not be null");
        }
        this.value = value;
        this.ttlSeconds = ttlSeconds;
        this.createdAtNanos = System.nanoTime();
        this.expiresAtNanos = createdAtNanos + ttlSeconds * 1_000_000_000L;
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
     * <p>
     * 使用 {@link System#nanoTime()} 单调时钟，不受系统时钟调整（NTP 同步、手动修改）影响。
     *
     * @return 如果结果已超过其TTL则返回 true
     */
    public boolean isExpired() {
        return System.nanoTime() >= expiresAtNanos;
    }
}
