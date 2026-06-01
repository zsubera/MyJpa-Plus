package com.zsubera.jpa.template;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.Map;
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
 * <p>
 * <strong>P2-7 改进：</strong>在 Spring 环境中，可以使用 {@code @TransactionalEventListener} 监听事务提交/回滚事件， 自动管理缓存失效。示例：
 *
 * <pre>
 * {
 *     &#64;code
 *     &#64;Component
 *     public class CacheInvalidationListener {
 *         &#64;Autowired
 *         private QueryCacheManager cache;
 *
 *         @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
 *         public void onTransactionCommit(EntityModifiedEvent event) {
 *             cache.evictByPrefix(event.getEntityName() + ":");
 *         }
 *     }
 * }
 * </pre>
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

    /** P1: 缓存键最大长度限制，防止恶意超长键导致内存问题 */
    private static final int MAX_KEY_LENGTH = 1024;

    /** P1: 驱逐检查采样间隔，每 N 次 put 检查一次驱逐，避免每次 put 都遍历全量缓存 */
    private static final int EVICTION_CHECK_INTERVAL = 10;

    /** P1: put 操作计数器，用于采样驱逐检查 */
    private static final java.util.concurrent.atomic.AtomicInteger PUT_COUNTER =
        new java.util.concurrent.atomic.AtomicInteger();

    /** LRU 缓存，使用 ConcurrentHashMap 实现线程安全的无锁读取。 */
    private final java.util.concurrent.ConcurrentMap<String, CachedQueryResult<?>> store;

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
        this.store = new java.util.concurrent.ConcurrentHashMap<>();
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
     * <p>
     * <strong>线程安全说明：</strong>使用 ConcurrentHashMap 实现无锁读取，过期条目在访问时懒驱逐。 使用 {@code remove(key, value)}
     * 原子操作确保仅移除未被其他线程替换的过期条目。
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
            // 原子移除：仅当条目未被其他线程替换时才移除
            store.remove(key, result);
            log.debug("Cache expired for key: {}", key);
            return null;
        }
        return (T)result.getValue();
    }

    /**
     * Stores a value in the cache with the given TTL.
     *
     * <p>
     * 当缓存条目数超过最大限制时，清除过期条目。如果仍超过限制，驱逐最早的条目后写入。
     *
     * @param key cache key
     * @param value value to cache
     * @param ttlSeconds time-to-live in seconds
     * @param <T> value type
     * @return 如果成功写入返回 true，如果 key 为 null 或空返回 false
     */
    public <T> boolean put(String key, T value, long ttlSeconds) {
        if (key == null || key.isEmpty()) {
            return false;
        }
        // P1: 缓存键长度验证
        if (key.length() > MAX_KEY_LENGTH) {
            log.warn("Cache key length ({}) exceeds maximum ({}). Key rejected: {}...", key.length(), MAX_KEY_LENGTH,
                key.substring(0, 64));
            return false;
        }
        // P1: 采样驱逐策略 - 每 EVICTION_CHECK_INTERVAL 次 put 检查一次，避免每次 put 遍历全量缓存
        if (PUT_COUNTER.incrementAndGet() % EVICTION_CHECK_INTERVAL == 0) {
            evictIfNeeded();
        } else if (store.size() >= maxEntries * 2) {
            // 紧急驱逐：缓存严重超限时立即驱逐，防止内存溢出
            evictIfNeeded();
        }
        store.put(key, new CachedQueryResult<>(value, ttlSeconds));
        log.debug("Cache put for key: {} (ttl={}s)", key, ttlSeconds);
        return true;
    }

    /**
     * 先清除过期条目，再按容量限制驱逐最早条目。在 put() 之前调用以保证线程安全。
     */
    private void evictIfNeeded() {
        evictExpiredEntries();
        while (store.size() >= maxEntries) {
            evictOldestEntry();
        }
    }

    /**
     * 驱逐最早写入的缓存条目（ConcurrentHashMap 迭代顺序近似插入顺序）。
     */
    private void evictOldestEntry() {
        java.util.Iterator<Map.Entry<String, CachedQueryResult<?>>> it = store.entrySet().iterator();
        if (it.hasNext()) {
            Map.Entry<String, CachedQueryResult<?>> eldest = it.next();
            it.remove();
            log.debug("Evicted oldest cache entry: {}", eldest.getKey());
        }
    }

    /**
     * 清除所有过期条目。
     */
    private void evictExpiredEntries() {
        java.util.Iterator<Map.Entry<String, CachedQueryResult<?>>> it = store.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, CachedQueryResult<?>> entry = it.next();
            if (entry.getValue().isExpired()) {
                it.remove();
            }
        }
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
        java.util.Iterator<Map.Entry<String, CachedQueryResult<?>>> it = store.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, CachedQueryResult<?>> entry = it.next();
            if (entry.getKey().startsWith(keyPrefix)) {
                it.remove();
                count++;
            }
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
        return store.size();
    }
}
