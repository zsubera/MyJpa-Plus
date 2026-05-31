package com.zsubera.jpa.template;

import java.time.Instant;

/**
 * Wrapper for cached query results with timestamp and TTL-based expiration.
 *
 * @param <T> the type of the cached result
 */
public class CachedQueryResult<T> {

    private final T value;
    private final Instant createdAt;
    private final long ttlSeconds;

    /**
     * Creates a new cached result.
     *
     * @param value the cached value
     * @param ttlSeconds time-to-live in seconds
     */
    public CachedQueryResult(T value, long ttlSeconds) {
        this.value = value;
        this.createdAt = Instant.now();
        this.ttlSeconds = ttlSeconds;
    }

    /**
     * Returns the cached value.
     *
     * @return cached value
     */
    public T getValue() {
        return value;
    }

    /**
     * Returns the creation timestamp.
     *
     * @return creation time
     */
    public Instant getCreatedAt() {
        return createdAt;
    }

    /**
     * Returns the TTL in seconds.
     *
     * @return TTL seconds
     */
    public long getTtlSeconds() {
        return ttlSeconds;
    }

    /**
     * Checks whether this cached result has expired.
     *
     * @return true if the result has exceeded its TTL
     */
    public boolean isExpired() {
        return !createdAt.plusSeconds(ttlSeconds).isAfter(Instant.now());
    }
}
