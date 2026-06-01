package com.zsubera.jpa.template;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.time.Instant;

/**
 * Wrapper for cached query results with timestamp and TTL-based expiration.
 *
 * @param <T> the type of the cached result
 */
@SuppressFBWarnings(value = "CT_CONSTRUCTOR_THROW",
    justification = "Constructor validates parameters via IllegalArgumentException which is standard Java practice")
public class CachedQueryResult<T> {

    private final T value;
    private final Instant createdAt;
    private final long ttlSeconds;
    /** P2: Pre-computed expiration time to avoid creating new Instant on every isExpired() call. */
    private final Instant expiresAt;

    /**
     * Creates a new cached result.
     *
     * @param value the cached value
     * @param ttlSeconds time-to-live in seconds
     */
    public CachedQueryResult(T value, long ttlSeconds) {
        // B-26: Validate value is non-null to prevent caching null results
        if (value == null) {
            throw new IllegalArgumentException("CachedQueryResult value must not be null");
        }
        this.value = value;
        this.createdAt = Instant.now();
        this.ttlSeconds = ttlSeconds;
        this.expiresAt = createdAt.plusSeconds(ttlSeconds);
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
        return !Instant.now().isBefore(expiresAt);
    }
}
