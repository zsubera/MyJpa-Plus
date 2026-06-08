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
 * <strong>改进：</strong>在 Spring 环境中，可以使用 {@code @TransactionalEventListener} 监听事务提交/回滚事件， 自动管理缓存失效。示例：
 *
 * <pre>{@code
 * @Component
 * public class CacheInvalidationListener {
 *     @Autowired
 *     private QueryCacheManager cache;
 *
 *     @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
 *     public void onTransactionCommit(EntityModifiedEvent event) {
 *         cache.evictByPrefix(event.getEntityName() + ":");
 *     }
 * }
 * }</pre>
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

    /** 缓存键最大长度限制，防止恶意超长键导致内存问题 */
    private static final int MAX_KEY_LENGTH = 1024;

    /** 驱逐检查采样间隔，每 N 次 put/get 操作检查一次驱逐，避免每次操作都遍历全量缓存 */
    private static final int EVICTION_CHECK_INTERVAL = 10;

    /** put 操作计数器，用于采样驱逐检查（实例级别，避免多实例干扰） */
    private final java.util.concurrent.atomic.AtomicInteger putCounter =
        new java.util.concurrent.atomic.AtomicInteger();

    /** get 操作计数器，用于读取时触发过期清理 */
    private final java.util.concurrent.atomic.AtomicInteger getCounter =
        new java.util.concurrent.atomic.AtomicInteger();

    /** LRU 缓存，使用 ConcurrentHashMap 实现线程安全的无锁读取。 */
    private final java.util.concurrent.ConcurrentMap<String, CachedQueryResult<?>> store;

    /** 插入顺序跟踪，用于实现近似 LRU 驱逐。 */
    private final java.util.concurrent.ConcurrentLinkedDeque<String> insertionOrder =
        new java.util.concurrent.ConcurrentLinkedDeque<>();

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
     * 根据键获取缓存值。如果键不存在或条目已过期则返回 null。
     *
     * <p>
     * <strong>线程安全说明：</strong>使用 ConcurrentHashMap 实现无锁读取，过期条目在访问时懒驱逐。 使用 {@code remove(key, value)}
     * 原子操作确保仅移除未被其他线程替换的过期条目。
     *
     * @param key 缓存键
     * @param <T> 期望的值类型
     * @return 缓存值，如果不存在/已过期则返回 null
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        // 读取时采样触发过期清理，防止高读低写场景下过期条目长期占用内存
        if (getCounter.incrementAndGet() % EVICTION_CHECK_INTERVAL == 0) {
            evictExpiredEntries();
        }
        CachedQueryResult<?> result = store.get(key);
        if (result == null) {
            return null;
        }
        if (result.isExpired()) {
            // 原子移除：仅当条目确实是当前过期条目时才移除，避免竞态条件误删新条目
            boolean removed = store.remove(key, result);
            if (removed) {
                // 仅当确实移除了过期条目时才清理 deque，避免误删并发 put 的新条目
                insertionOrder.remove(key);
                log.debug("Cache expired for key: {}", key);
            }
            return null;
        }
        try {
            return (T)result.getValue();
        } catch (ClassCastException e) {
            throw new ClassCastException("Cache type mismatch for key '" + key + "'. "
                + "Expected type mismatch. Cached value type: " + result.getValue().getClass().getName());
        }
    }

    /**
     * 将值存入缓存，指定 TTL（生存时间）。
     *
     * <p>
     * 当缓存条目数超过最大限制时，清除过期条目。如果仍超过限制，驱逐最早的条目后写入。
     *
     * @param key 缓存键
     * @param value 要缓存的值
     * @param ttlSeconds 生存时间（秒）
     * @param <T> 值类型
     * @return 如果成功写入返回 true，如果 key 为 null 或空返回 false
     */
    public <T> boolean put(String key, T value, long ttlSeconds) {
        if (key == null || key.isEmpty()) {
            return false;
        }
        if (ttlSeconds < 0) {
            throw new IllegalArgumentException("ttlSeconds must not be negative, got: " + ttlSeconds);
        }
        // 缓存键长度验证
        if (key.length() > MAX_KEY_LENGTH) {
            log.warn("Cache key length ({}) exceeds maximum ({}). Key rejected: {}...", key.length(), MAX_KEY_LENGTH,
                key.substring(0, 64));
            return false;
        }
        // 采样驱逐策略 - 每 EVICTION_CHECK_INTERVAL 次 put 检查一次，避免每次 put 遍历全量缓存
        if (putCounter.incrementAndGet() % EVICTION_CHECK_INTERVAL == 0) {
            evictIfNeeded();
        } else if (store.size() >= maxEntries * 2) {
            // 紧急驱逐：缓存严重超限时立即驱逐，防止内存溢出
            evictIfNeeded();
        }
        store.put(key, new CachedQueryResult<>(value, ttlSeconds));
        // 先移除旧的 deque 条目（如果有），避免重复条目导致 deque 无限增长
        insertionOrder.remove(key);
        insertionOrder.addLast(key);
        // 清理 deque 中已不存在的条目（防止 deque 无限增长）
        while (insertionOrder.size() > store.size() + 100) {
            String oldest = insertionOrder.peekFirst();
            if (oldest != null && !store.containsKey(oldest)) {
                insertionOrder.pollFirst();
            } else {
                break;
            }
        }
        log.debug("Cache put for key: {} (ttl={}s)", key, ttlSeconds);
        return true;
    }

    /**
     * 清除过期条目，再按容量限制驱逐最早条目。在 put() 之前调用以保证线程安全。
     */
    private void evictIfNeeded() {
        evictExpiredEntries();
        // 使用有界重试次数避免无限循环
        for (int i = 0; i < maxEntries && store.size() >= maxEntries; i++) {
            evictOldestEntry();
        }
    }

    /**
     * 驱逐最早写入的缓存条目（使用 ConcurrentLinkedDeque 维护插入顺序）。 跳过 deque 中已不在 store 里的陈旧条目，防止 deque/store 漂移导致无效驱逐。
     */
    private void evictOldestEntry() {
        for (int attempt = 0; attempt < 10; attempt++) {
            String oldest = insertionOrder.pollFirst();
            if (oldest == null) {
                return;
            }
            // 仅当 store 中确实存在该 key 时才执行驱逐
            if (store.remove(oldest) != null) {
                log.debug("Evicted oldest cache entry: {}", oldest);
                return;
            }
            // deque 中的陈旧条目已跳过，继续尝试下一个
        }
    }

    /**
     * 清除所有过期条目（原子操作，避免竞态条件误删新条目）。 同时清理 insertionOrder 中对应的陈旧条目。
     */
    private void evictExpiredEntries() {
        store.entrySet().removeIf(e -> {
            if (e.getValue().isExpired()) {
                insertionOrder.remove(e.getKey());
                return true;
            }
            return false;
        });
    }

    /**
     * 从缓存中移除指定条目。
     *
     * @param key 要驱逐的缓存键
     */
    public void evict(String key) {
        store.remove(key);
        insertionOrder.remove(key);
        log.debug("Cache evicted for key: {}", key);
    }

    /**
     * 清除所有缓存条目。
     */
    public void clear() {
        store.clear();
        insertionOrder.clear();
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
                insertionOrder.remove(entry.getKey());
                count++;
            }
        }
        if (count > 0) {
            log.debug("Cache evicted {} entries with prefix '{}'", count, keyPrefix);
        }
        return count;
    }

    /**
     * 返回当前存储中的条目数（包括尚未驱逐的可能已过期条目）。
     *
     * @return 条目数
     */
    public int size() {
        return store.size();
    }
}
