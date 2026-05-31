package com.zsubera.jpa.template;

import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ConcurrentHashMap-based query result cache with TTL expiration.
 *
 * <p>
 * Used by {@link MyJpaTemplate} (or directly) to cache query results. Entries are lazily evicted on access when their
 * TTL has expired.
 *
 * <p>
 * Example usage:
 *
 * <pre>{@code
 * QueryCacheManager cache = new QueryCacheManager();
 * cache.put("active-users", userList, 60);
 * List<User> cached = cache.get("active-users");
 * }</pre>
 */
public class QueryCacheManager {

    private static final Logger log = LoggerFactory.getLogger(QueryCacheManager.class);

    private final ConcurrentHashMap<String, CachedQueryResult<?>> store = new ConcurrentHashMap<>();

    /**
     * Retrieves a cached value by key. Returns null if the key is absent or the entry has expired.
     *
     * @param key cache key
     * @param <T> expected value type
     * @return cached value, or null if absent/expired
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        CachedQueryResult<?> result = store.get(key);
        if (result == null) {
            return null;
        }
        if (result.isExpired()) {
            store.remove(key);
            log.debug("Cache expired for key: {}", key);
            return null;
        }
        return (T)result.getValue();
    }

    /**
     * Stores a value in the cache with the given TTL.
     *
     * @param key cache key
     * @param value value to cache
     * @param ttlSeconds time-to-live in seconds
     * @param <T> value type
     */
    public <T> void put(String key, T value, long ttlSeconds) {
        store.put(key, new CachedQueryResult<>(value, ttlSeconds));
        log.debug("Cache put for key: {} (ttl={}s)", key, ttlSeconds);
    }

    /**
     * Removes a specific entry from the cache.
     *
     * @param key cache key to evict
     */
    public void evict(String key) {
        store.remove(key);
        log.debug("Cache evicted for key: {}", key);
    }

    /**
     * Clears all cached entries.
     */
    public void clear() {
        store.clear();
        log.debug("Cache cleared");
    }

    /**
     * Returns the number of entries currently in the store (including potentially expired ones not yet evicted).
     *
     * @return number of entries
     */
    public int size() {
        return store.size();
    }
}
