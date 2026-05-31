package com.zsubera.jpa.template;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 查询结果缓存管理器，基于 ConcurrentHashMap 实现，支持 TTL 过期和最大条目数限制。
 *
 * <p>
 * 由 {@link MyJpaTemplate}（或直接）使用以缓存查询结果。条目在访问时懒驱逐（TTL 过期）。 当缓存条目数超过最大限制时，按插入顺序驱逐最早的条目（近似 LRU）。
 *
 * <p>
 * <strong>事务集成说明：</strong>当前缓存实现独立于事务生命周期。写操作（INSERT/UPDATE/DELETE）后， 相关缓存条目不会自动失效。建议在事务提交后手动调用
 * {@link #evictByPrefix(String)} 清除相关缓存。
 *
 * <pre>{@code
 * @Transactional
 * public void updateUser(User user) {
 *     userRepository.save(user);
 *     // 事务提交后清除相关缓存
 *     cache.evictByPrefix("User:");
 * }
 * }</pre>
 *
 * <p>
 * 示例用法：
 *
 * <pre>{@code
 * QueryCacheManager cache = new QueryCacheManager();
 * cache.put("active-users", userList, 60);
 * List<User> cached = cache.get("active-users");
 * }</pre>
 *
 * <p>
 * 配置示例（application.yml）：
 *
 * <pre>{@code
 * myjpa-plus:
 *   cache:
 *     max-entries: 10000
 * }</pre>
 */
@SuppressFBWarnings(value = "CT_CONSTRUCTOR_THROW",
    justification = "Constructor validates parameters via IllegalArgumentException which is standard Java practice")
public class QueryCacheManager {

    private static final Logger log = LoggerFactory.getLogger(QueryCacheManager.class);

    /** 默认最大缓存条目数 */
    private static final int DEFAULT_MAX_ENTRIES = 10000;

    /** LRU 缓存，使用访问顺序实现 LRU 驱逐策略。 */
    private final LinkedHashMap<String, CachedQueryResult<?>> store;

    /** 读写锁，保证线程安全。 */
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    private volatile int maxEntries;

    /**
     * 创建使用默认最大条目数的 QueryCacheManager。
     */
    public QueryCacheManager() {
        this(DEFAULT_MAX_ENTRIES);
    }

    /**
     * 创建使用指定最大条目数的 QueryCacheManager。
     *
     * @param maxEntries 最大缓存条目数
     * @throws IllegalArgumentException 如果 maxEntries 不是正数
     */
    public QueryCacheManager(int maxEntries) {
        if (maxEntries <= 0) {
            throw new IllegalArgumentException("maxEntries must be positive");
        }
        this.maxEntries = maxEntries;
        this.store = new LinkedHashMap<>(16, 0.75f, true) {
            private static final long serialVersionUID = 1L;

            @Override
            protected boolean removeEldestEntry(Map.Entry<String, CachedQueryResult<?>> eldest) {
                return super.size() > QueryCacheManager.this.maxEntries;
            }
        };
    }

    /**
     * 设置最大缓存条目数。
     *
     * @param maxEntries 最大缓存条目数
     * @throws IllegalArgumentException 如果 maxEntries 不是正数
     */
    public void setMaxEntries(int maxEntries) {
        if (maxEntries <= 0) {
            throw new IllegalArgumentException("maxEntries must be positive");
        }
        this.maxEntries = maxEntries;
    }

    /**
     * 获取最大缓存条目数。
     *
     * @return 最大缓存条目数
     */
    public int getMaxEntries() {
        return maxEntries;
    }

    /**
     * Retrieves a cached value by key. Returns null if the key is absent or the entry has expired.
     *
     * @param key cache key
     * @param <T> expected value type
     * @return cached value, or null if absent/expired
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        lock.readLock().lock();
        try {
            CachedQueryResult<?> result = store.get(key);
            if (result == null) {
                return null;
            }
            if (result.isExpired()) {
                // Upgrade to write lock for removal
                lock.readLock().unlock();
                lock.writeLock().lock();
                try {
                    store.remove(key);
                } finally {
                    lock.writeLock().unlock();
                    lock.readLock().lock();
                }
                log.debug("Cache expired for key: {}", key);
                return null;
            }
            return (T)result.getValue();
        } finally {
            lock.readLock().unlock();
        }
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
        lock.writeLock().lock();
        try {
            store.put(key, new CachedQueryResult<>(value, ttlSeconds));
        } finally {
            lock.writeLock().unlock();
        }
        log.debug("Cache put for key: {} (ttl={}s)", key, ttlSeconds);
    }

    /**
     * Removes a specific entry from the cache.
     *
     * @param key cache key to evict
     */
    public void evict(String key) {
        lock.writeLock().lock();
        try {
            store.remove(key);
        } finally {
            lock.writeLock().unlock();
        }
        log.debug("Cache evicted for key: {}", key);
    }

    /**
     * Clears all cached entries.
     */
    public void clear() {
        lock.writeLock().lock();
        try {
            store.clear();
        } finally {
            lock.writeLock().unlock();
        }
        log.debug("Cache cleared");
    }

    /**
     * 按键前缀批量驱逐缓存条目。适用于实体变更后清除相关查询缓存。
     *
     * <p>
     * 示例：
     *
     * <pre>{@code
     * // User 实体更新后，清除所有以 "User:" 开头的缓存
     * cache.evictByPrefix("User:");
     * }</pre>
     *
     * @param keyPrefix 缓存键前缀
     * @return 被驱逐的条目数
     */
    public int evictByPrefix(String keyPrefix) {
        if (keyPrefix == null || keyPrefix.isEmpty()) {
            return 0;
        }
        int count = 0;
        lock.writeLock().lock();
        try {
            java.util.Iterator<Map.Entry<String, CachedQueryResult<?>>> it = store.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, CachedQueryResult<?>> entry = it.next();
                if (entry.getKey().startsWith(keyPrefix)) {
                    it.remove();
                    count++;
                }
            }
        } finally {
            lock.writeLock().unlock();
        }
        if (count > 0) {
            log.debug("Cache evicted {} entries with prefix '{}'", count, keyPrefix);
        }
        return count;
    }

    /**
     * Returns the number of entries currently in the store (including potentially expired ones not yet evicted).
     *
     * @return number of entries
     */
    public int size() {
        lock.readLock().lock();
        try {
            return store.size();
        } finally {
            lock.readLock().unlock();
        }
    }
}
