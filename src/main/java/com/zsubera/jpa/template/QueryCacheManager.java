package com.zsubera.jpa.template;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
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

    /** 缓存命中计数 */
    private final java.util.concurrent.atomic.AtomicLong hitCount = new java.util.concurrent.atomic.AtomicLong(0);

    /** 缓存未命中计数 */
    private final java.util.concurrent.atomic.AtomicLong missCount = new java.util.concurrent.atomic.AtomicLong(0);

    /** LRU 缓存，使用 ConcurrentHashMap 实现线程安全的无锁读取。 */
    private final java.util.concurrent.ConcurrentMap<String, CachedQueryResult<?>> store;

    /** 插入顺序跟踪，用于实现近似 LRU 驱逐。 */
    private final java.util.concurrent.ConcurrentLinkedDeque<String> insertionOrder =
        new java.util.concurrent.ConcurrentLinkedDeque<>();

    /** deque 中的条目数量（包括可能不在 store 中的陈旧条目）。 */
    private final java.util.concurrent.atomic.AtomicInteger dequeSize =
        new java.util.concurrent.atomic.AtomicInteger(0);

    /** 驱逐操作锁，确保只有一个线程执行驱逐，避免重复扫描和内存泄漏 */
    private final ReentrantLock evictionLock = new ReentrantLock();

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
            missCount.incrementAndGet();
            return null;
        }
        if (result.isExpired()) {
            missCount.incrementAndGet();
            // 原子移除：仅当条目确实是当前过期条目时才移除，避免竞态条件误删新条目
            boolean removed = store.remove(key, result);
            if (removed) {
                if (insertionOrder.remove(key)) {
                    dequeSize.decrementAndGet();
                }
                log.debug("Cache expired for key: {}", key);
            }
            return null;
        }
        hitCount.incrementAndGet();
        try {
            return (T)result.getValue();
        } catch (ClassCastException e) {
            throw new ClassCastException(String.format(
                "Cache type mismatch for key '%s'. Cached type: %s. "
                    + "Ensure the same key is not used for different value types.",
                key, result.getValue().getClass().getName()));
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

        // 原代码在缓存满时每次 put 都调用 evictIfNeeded()，绕过了采样策略，
        // 导致高写入场景下每次 put 都执行缓存扫描。采样策略（每 10 次检查一次）
        // 结合 post-put 兜底循环已足够保证缓存有界。
        if (putCounter.incrementAndGet() % EVICTION_CHECK_INTERVAL == 0) {
            evictIfNeeded();
        }

        CachedQueryResult<?> oldValue = store.put(key, new CachedQueryResult<>(value, ttlSeconds));
        if (oldValue == null) {
            // 新 key：追加到 deque 并更新计数
            insertionOrder.addLast(key);
            dequeSize.incrementAndGet();
        }
        // 更新已有 key 时不需要修改 insertionOrder——旧条目会在 deque 漂移清理时被跳过

        // 清理 deque 中不在 store 中的陈旧条目
        int drift = dequeSize.get() - store.size();
        if (drift > Math.max(10, maxEntries / 10)) {
            cleanupDrift(drift > maxEntries / 2);
        }
        // ConcurrentLinkedDeque.pollFirst() 和 ConcurrentHashMap.remove() 都是线程安全的，
        // 限制最大尝试次数为 maxEntries/10，避免高负载下长时间循环
        int maxAttempts = Math.max(16, maxEntries / 10);
        int attempts = 0;
        while (store.size() > maxEntries && attempts < maxAttempts) {
            String oldest = insertionOrder.pollFirst();
            if (oldest == null) {
                break;
            }
            dequeSize.decrementAndGet();
            // 仅当 store 中确实存在该 key 时才算有效驱逐，否则跳过陈旧 deque 条目
            if (store.remove(oldest) != null) {
                log.debug("Post-put evicted oldest cache entry: {}", oldest);
            }
            attempts++;
        }
        log.debug("Cache put for key: {} (ttl={}s)", key, ttlSeconds);
        return true;
    }

    /**
     * 清除过期条目，再按容量限制驱逐最早条目。在 put() 之前调用以保证线程安全。
     *
     * <p>

     * 如果另一个线程正在执行驱逐，当前线程直接返回，因为驱逐操作是幂等的。
     */
    private void evictIfNeeded() {

        if (!evictionLock.tryLock()) {
            return;
        }
        try {
            evictExpiredEntries();
            // 限制驱逐循环次数，避免在高并发写入场景下长时间持锁
            int maxEvictions = Math.max(16, maxEntries / 10);
            for (int i = 0; i < maxEvictions && store.size() >= maxEntries; i++) {
                evictOldestEntry();
            }
        } finally {
            evictionLock.unlock();
        }
    }

    /**
     * 驱逐最早写入的缓存条目（使用 ConcurrentLinkedDeque 维护插入顺序）。 跳过 deque 中已不在 store 里的陈旧条目，防止 deque/store 漂移导致无效驱逐。
     */
    private void evictOldestEntry() {
        int maxAttempts = Math.max(8, maxEntries / 20);
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            String oldest = insertionOrder.pollFirst();
            if (oldest == null) {
                return;
            }
            dequeSize.decrementAndGet();
            // 仅当 store 中确实存在该 key 时才执行驱逐
            if (store.remove(oldest) != null) {
                log.debug("Evicted oldest cache entry: {}", oldest);
                return;
            }
            // deque 中的陈旧条目已跳过，继续尝试下一个
        }
    }

    /**
     * 清理 deque 与 store 之间的漂移条目。
     *
     * <p>
     * 当 drift 较小时，仅从 deque 头部清理（快速路径）。
     * 当 drift 超过 maxEntries/2 时，执行全量遍历清理（慢路径，但仅在严重漂移时触发）。
     *
     * @param fullScan 是否执行全量遍历
     */
    private void cleanupDrift(boolean fullScan) {
        if (fullScan) {
            // 全量遍历：移除 deque 中所有不在 store 中的陈旧条目
            java.util.Iterator<String> it = insertionOrder.iterator();
            int cleaned = 0;
            while (it.hasNext()) {
                String k = it.next();
                if (!store.containsKey(k)) {
                    it.remove();
                    dequeSize.decrementAndGet();
                    cleaned++;
                }
            }
            if (cleaned > 0) {
                log.debug("Full drift cleanup removed {} stale deque entries", cleaned);
            }
        } else {
            // 快速路径：仅从头部清理连续的陈旧条目
            int cleaned = 0;
            int maxAttempts = Math.max(16, maxEntries / 10);
            while (cleaned < maxAttempts) {
                String oldest = insertionOrder.peekFirst();
                if (oldest == null || store.containsKey(oldest)) {
                    break;
                }
                insertionOrder.pollFirst();
                dequeSize.decrementAndGet();
                cleaned++;
            }
        }
    }

    /**
     * 返回缓存命中率。
     *
     * @return 命中率（0.0-1.0），如果没有 get 操作则返回 0.0
     */
    public double getHitRate() {
        long hits = hitCount.get();
        long misses = missCount.get();
        long total = hits + misses;
        return total == 0 ? 0.0 : (double)hits / total;
    }

    /**
     * 返回缓存命中次数。
     *
     * @return 命中次数
     */
    public long getHitCount() {
        return hitCount.get();
    }

    /**
     * 返回缓存未命中次数。
     *
     * @return 未命中次数
     */
    public long getMissCount() {
        return missCount.get();
    }

    /**
     * 重置命中率统计计数器。
     */
    public void resetStats() {
        hitCount.set(0);
        missCount.set(0);
    }

    /**
     * 清除过期条目。采样部分条目进行检查，避免全量扫描带来的 CPU 热点。
     * 同时清理 insertionOrder 中对应的陈旧条目。
     */
    private void evictExpiredEntries() {
        int sampleSize = Math.min(store.size(), 64);
        if (sampleSize == 0) {
            return;
        }
        int count = 0;
        for (Map.Entry<String, CachedQueryResult<?>> entry : store.entrySet()) {
            if (count++ >= sampleSize) {
                break;
            }
            if (entry.getValue().isExpired()) {
                if (store.remove(entry.getKey(), entry.getValue())) {
                    if (insertionOrder.remove(entry.getKey())) {
                        dequeSize.decrementAndGet();
                    }
                    log.debug("Cache expired for key: {}", entry.getKey());
                }
            }
        }
    }

    /**
     * 从缓存中移除指定条目。
     *
     * @param key 要驱逐的缓存键
     */
    public void evict(String key) {
        Object removed = store.remove(key);
        if (removed != null && insertionOrder.remove(key)) {
            dequeSize.decrementAndGet();
        }
        log.debug("Cache evicted for key: {}", key);
    }

    /**
     * 清除所有缓存条目。
     *
     * <p>
     * <strong>并发说明：</strong>此方法非原子操作。与并发 {@link #put(String, Object, long)} 之间存在窗口期，
     * 可能导致 deque 与 store 之间的漂移。漂移是自愈的——后续 {@link #put} 调用中的 drift cleanup 会修复。
     */
    public void clear() {
        store.clear();
        insertionOrder.clear();
        dequeSize.set(0);
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
                if (insertionOrder.remove(entry.getKey())) {
                    dequeSize.decrementAndGet();
                }
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

    /**
     * 在事务提交后自动清除指定前缀的缓存条目。
     *
     * <p>
     * 此方法注册一个 {@link org.springframework.transaction.support.TransactionSynchronization}，
     * 在事务提交成功后执行缓存清除操作。如果当前没有活动事务，则立即清除。
     *
     * <p>
     * 使用示例：
     * <pre>{@code
     * @Transactional
     * public void updateUser(User user) {
     *     userRepository.save(user);
     *     // 事务提交后清除相关缓存
     *     cacheManager.evictByPrefixAfterTransactionCommit("User:");
     * }
     * }</pre>
     *
     * @param keyPrefix 要清除的缓存键前缀
     */
    public void evictByPrefixAfterTransactionCommit(String keyPrefix) {
        if (keyPrefix == null || keyPrefix.isEmpty()) {
            return;
        }
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()) {
            org.springframework.transaction.support.TransactionSynchronizationManager
                .registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        evictByPrefix(keyPrefix);
                        log.debug("Cache evicted after transaction commit for prefix: {}", keyPrefix);
                    }
                });
        } else {
            evictByPrefix(keyPrefix);
        }
    }

    /**
     * 注册事务同步器，在事务提交后清除所有缓存条目。
     *
     * <p>
     * 此方法适用于批量操作后需要清除所有查询缓存的场景。
     */
    public void clearAfterTransactionCommit() {
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()) {
            org.springframework.transaction.support.TransactionSynchronizationManager
                .registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        clear();
                        log.debug("Cache cleared after transaction commit");
                    }
                });
        } else {
            clear();
        }
    }
}
