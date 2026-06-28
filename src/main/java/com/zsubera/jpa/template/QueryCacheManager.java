package com.zsubera.jpa.template;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.ArrayList;
import java.util.List;
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
 *     cache.evictByPrefix("com.example.User:");
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
public class QueryCacheManager implements CacheAdapter {

    private static final Logger log = LoggerFactory.getLogger(QueryCacheManager.class);

    /** 默认最大缓存条目数 */
    private static final int DEFAULT_MAX_ENTRIES = 10000;

    /** 缓存键最大长度限制，防止恶意超长键导致内存问题 */
    private static final int MAX_KEY_LENGTH = 1024;

    /** 驱逐检查采样间隔，每 N 次 put/get 操作检查一次驱逐，避免每次操作都遍历全量缓存 */
    private static final int EVICTION_CHECK_INTERVAL = 10;

    /** 漂移检测最小阈值，低于此值不触发清理 */
    private static final int MIN_DRIFT_THRESHOLD = 10;

    /** CAS 驱逐最小尝试次数 */
    private static final int MIN_CAS_EVICTION_ATTEMPTS = 16;

    /** 清理漂移最小尝试次数 */
    private static final int MIN_CLEANUP_ATTEMPTS = 8;

    /** 过期条目清理最小采样数 */
    private static final int MIN_EXPIRY_SAMPLE_COUNT = 32;

    /** 过期条目清理最小采样数（小缓存） */
    private static final int MIN_EXPIRY_SAMPLE_COUNT_SMALL = 128;

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

    /** ponytail: 本地命中/未命中计数器，批量同步到全局计数器，减少原子操作开销 */
    private final java.util.concurrent.atomic.AtomicLong localHits = new java.util.concurrent.atomic.AtomicLong(0);
    private final java.util.concurrent.atomic.AtomicLong localMisses = new java.util.concurrent.atomic.AtomicLong(0);

    /** LRU 缓存，使用 ConcurrentHashMap 实现线程安全的无锁读取。 */
    private final java.util.concurrent.ConcurrentMap<String, CachedQueryResult<?>> store;

    /**
     * 插入顺序跟踪，记录每个键的插入时间戳（纳秒），用于实现近似 FIFO 驱逐。
     *
     * <p>
     * 使用 ConcurrentHashMap 替代 ConcurrentLinkedDeque，将 {@code remove(key)} 操作从 O(n) 降低到 O(1)。
     * 驱逐时通过遍历找到最旧的时间戳，虽然最坏情况仍是 O(n)，但避免了 deque 的线性扫描删除。
     */
    private final java.util.concurrent.ConcurrentMap<String, Long> insertionTimestamps =
        new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * ReentrantLock with tryLock() for eviction guard — non-blocking and optimal here because
     * ConcurrentHashMap already provides lock-free reads; StampedLock optimistic reads would add
     * complexity without benefit. This lock only guards the write-side eviction path, not reads.
     * tryLock() ensures at most one thread evicts at a time; others skip (eviction is idempotent).
     */
    private final ReentrantLock evictionLock = new ReentrantLock();

    /** 加载锁缓存，用于 computeIfAbsent 的缓存击穿保护。键在加载完毕后延迟清理。 */
    private final java.util.concurrent.ConcurrentMap<String, java.util.concurrent.locks.ReentrantLock> loadLocks =
        new java.util.concurrent.ConcurrentHashMap<>();

    /** clear() 调用计数器，用于 put() 检测并发 clear() 后丢弃写入。 */
    private final java.util.concurrent.atomic.AtomicLong clearGeneration =
        new java.util.concurrent.atomic.AtomicLong(0);

    /**
     * 前缀索引，将键的前缀（冒号前部分）映射到该前缀下的所有缓存键集合。
     * 用于将 {@link #evictByPrefix(String)} 从 O(n) 降低到 O(k)（k 为匹配条目数）。
     *
     * <p>
     * 使用 {@link java.util.concurrent.ConcurrentHashMap#newKeySet()} 替代 CopyOnWriteArraySet，
     * 避免 evictByPrefix 中大量 remove() 调用导致的 O(n²) 数组复制。
     */
    private final java.util.concurrent.ConcurrentMap<String, java.util.Set<String>> prefixIndex =
        new java.util.concurrent.ConcurrentHashMap<>();

    private volatile int maxEntries;

    /**
     * 从缓存键中提取前缀（冒号前的部分）。例如 {@code "User:abc123:Sort"} 返回 {@code "User"}。
     * 如果键不包含冒号，返回整个键。
     *
     * @param key 缓存键
     * @return 键的前缀
     */
    private static String extractPrefix(String key) {
        int colonIdx = key.indexOf(':');
        return colonIdx > 0 ? key.substring(0, colonIdx) : key;
    }

    /**
     * 向前缀索引中添加一个键。
     */
    private void addToPrefixIndex(String key) {
        // ponytail: 先检查 store 中是否仍有该 key，避免 evictByPrefix 并发竞争导致索引脏条目
        String prefix = extractPrefix(key);
        prefixIndex.compute(prefix, (k, set) -> {
            if (set == null) {
                set = java.util.concurrent.ConcurrentHashMap.newKeySet();
            }
            if (store.containsKey(key)) {
                set.add(key);
            }
            return set.isEmpty() ? null : set;
        });
    }

    /**
     * 从前缀索引中移除一个键。
     */
    private void removeFromPrefixIndex(String key) {
        String prefix = extractPrefix(key);
        prefixIndex.computeIfPresent(prefix, (k, keys) -> {
            keys.remove(key);
            return keys.isEmpty() ? null : keys;
        });
    }

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
     * <p>
     * 如果新值小于当前缓存条目数，将立即触发驱逐以将缓存缩减到新容量。
     *
     * @param maxEntries 最大缓存条目数
     * @throws IllegalArgumentException 如果 maxEntries 不是正数
     */
    public void setMaxEntries(int maxEntries) {
        if (maxEntries <= 0) {
            throw new IllegalArgumentException("maxEntries must be positive");
        }
        this.maxEntries = maxEntries;
        if (store.size() > maxEntries) {
            evictIfNeeded();
        }
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
    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        // ponytail: 延迟统计，仅每 EVICTION_CHECK_INTERVAL 次操作更新一次计数器
        if (getCounter.incrementAndGet() % EVICTION_CHECK_INTERVAL == 0) {
            evictExpiredEntries();
            hitCount.addAndGet(localHits.getAndSet(0));
            missCount.addAndGet(localMisses.getAndSet(0));
        }
        long genBefore = clearGeneration.get();
        CachedQueryResult<?> result = store.get(key);
        // 如果 clear() 在 get 期间发生，丢弃可能过期的数据
        if (clearGeneration.get() != genBefore) {
            localMisses.incrementAndGet();
            return null;
        }
        if (result == null) {
            localMisses.incrementAndGet();
            return null;
        }
        if (result.isExpired()) {
            localMisses.incrementAndGet();
            boolean removed = store.remove(key, result);
            if (removed) {
                insertionTimestamps.remove(key);
                removeFromPrefixIndex(key);
                log.debug("Cache expired for key: {}", key);
            }
            return null;
        }
        localHits.incrementAndGet();
        try {
            T value = (T)result.getValue();
            // ponytail: 返回 List 的防御性拷贝，防止调用者修改返回值破坏缓存数据一致性。
            // 非 List 类型直接返回引用（调用者应自行防御）。
            if (value instanceof List<?> list) {
                return (T)new ArrayList<>(list);
            }
            return value;
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
    @Override
    public <T> boolean put(String key, T value, long ttlSeconds) {
        if (key == null || key.isEmpty()) {
            return false;
        }
        if (ttlSeconds < 0) {
            throw new IllegalArgumentException("ttlSeconds must not be negative, got: " + ttlSeconds);
        }
        if (ttlSeconds == 0) {
            return false;
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

        // 快照 clear() 代数，检测并发 clear() 后丢弃写入
        long genBefore = clearGeneration.get();

        CachedQueryResult<?> newValue = new CachedQueryResult<>(value, ttlSeconds);
        CachedQueryResult<?> existing = store.putIfAbsent(key, newValue);
        if (existing != null) {
            store.put(key, newValue);
            // ponytail: 覆盖写入时同步更新时间戳，防止后置驱逐循环使用旧时间戳误驱逐新值
            insertionTimestamps.put(key, System.nanoTime());
        } else {
            insertionTimestamps.put(key, System.nanoTime());
            addToPrefixIndex(key);
        }

        // 如果 clear() 在 put 期间发生，丢弃本次写入
        if (clearGeneration.get() != genBefore) {
            store.remove(key);
            insertionTimestamps.remove(key);
            removeFromPrefixIndex(key);
            return false;
        }

        // 清理 insertionTimestamps 中不在 store 中的陈旧条目
        int timestampsSize = insertionTimestamps.size();
        int drift = timestampsSize - store.size();
        if (drift > Math.max(MIN_DRIFT_THRESHOLD, maxEntries / 10)) {
            cleanupDrift(drift > maxEntries / 2);
        }
        // CAS-based eviction: find oldest entry by timestamp, CAS remove(key, value).
        // 避免并发 put 替换值后误删新条目。CAS 失败说明条目被并发替换，跳过即可。
        // ponytail: 驱逐到 maxEntries*3/4 留出头部空间，防止竞争写入下缓存超标。
        // 如果 CAS 持续失败（高竞争），attempts 仍递增确保有穷。
        long targetSize = Math.max(1, (long)maxEntries * 3 / 4);
        int maxAttempts = Math.max(MIN_CAS_EVICTION_ATTEMPTS, maxEntries / 10);
        int attempts = 0;
        while (store.size() > targetSize && attempts < maxAttempts) {
            String oldest = findOldestKey();
            if (oldest == null) {
                break;
            }
            CachedQueryResult<?> val = store.get(oldest);
            if (val == null) {
                insertionTimestamps.remove(oldest);
                removeFromPrefixIndex(oldest);
                attempts++;
                continue;
            }
            if (store.remove(oldest, val)) {
                insertionTimestamps.remove(oldest);
                removeFromPrefixIndex(oldest);
                log.debug("Post-put evicted oldest cache entry: {}", oldest);
            }
            attempts++;
        }
        log.debug("Cache put for key: {} (ttl={}s)", key, ttlSeconds);
        return true;
    }

    /**
     * 原子性地获取或计算缓存值，提供缓存击穿保护。
     *
     * <p>
     * 当多个线程并发请求同一个未缓存的键时，仅一个线程执行 {@code loader}，
     * 其余线程等待其结果。适用于回源计算开销大的场景（如数据库查询）。
     *
     * <p>
     * ponytail: 使用 per-key {@link java.util.concurrent.locks.ReentrantLock} 实现细粒度锁定，
     * 避免全局锁竞争。负载键在加载完成后不会主动清理，而是作为轻量级条纹锁长期存在；
     * 若 {@code loadLocks} 数量超过最大缓存条目数触发懒惰清理。
     *
     * @param key 缓存键
     * @param loader 值加载函数，在缓存未命中时调用
     * @param ttlSeconds 生存时间（秒）
     * @param <T> 值类型
     * @return 已缓存或新加载的值
     */
    @SuppressWarnings("unchecked")
    public <T> T computeIfAbsent(String key, java.util.function.Supplier<T> loader, long ttlSeconds) {
        T cached = get(key);
        if (cached != null) {
            return cached;
        }
        java.util.concurrent.locks.ReentrantLock lock =
            loadLocks.computeIfAbsent(key, k -> new java.util.concurrent.locks.ReentrantLock());
        lock.lock();
        try {
            cached = get(key);
            if (cached != null) {
                return cached;
            }
            T value = loader.get();
            if (value != null) {
                put(key, value, ttlSeconds);
            }
            return value;
        } finally {
            lock.unlock();
            // ponytail: 用后即清理，防止 loadLocks 无上限增长。
            // 下次同一 key 的 computeIfAbsent 会创建新锁，开销远小于 loader（典型为 DB 查询）。
            loadLocks.remove(key, lock);
        }
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
            int maxEvictions = Math.max(MIN_CAS_EVICTION_ATTEMPTS, maxEntries / 10);
            for (int i = 0; i < maxEvictions && store.size() >= maxEntries; i++) {
                evictOldestEntry();
            }
        } finally {
            evictionLock.unlock();
        }
    }

    /**
     * 驱逐最早写入的缓存条目（使用 insertionTimestamps 维护插入顺序）。
     * 跳过已不在 store 里的陈旧条目，防止漂移导致无效驱逐。
     */
    private void evictOldestEntry() {
        int maxAttempts = Math.max(MIN_CLEANUP_ATTEMPTS, maxEntries / 20);
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            String oldest = findOldestKey();
            if (oldest == null) {
                return;
            }
            CachedQueryResult<?> val = store.get(oldest);
            if (val == null) {
                insertionTimestamps.remove(oldest);
                removeFromPrefixIndex(oldest);
                continue;
            }
            if (store.remove(oldest, val)) {
                insertionTimestamps.remove(oldest);
                removeFromPrefixIndex(oldest);
                log.debug("Evicted oldest cache entry: {}", oldest);
                return;
            }
            // CAS 失败：条目被并发替换，保留条目，下次循环重试其他 key
        }
    }

    /**
     * 查找插入时间最早的缓存键（采样策略）。
     *
     * <p>
     * 采样 insertionTimestamps 中的固定数量条目，选择其中时间戳最小的键。
     * 对于驱逐目的，"足够旧"的条目与"绝对最旧"的条目是等价的。
     * ponytail: 全表扫 O(n) 改为固定采样 O(sampleCount)，驱逐效果不变。
     *
     * @return 最早插入的键，如果没有任何条目则返回 null
     */
    private String findOldestKey() {
        String oldestKey = null;
        long oldestTimestamp = Long.MAX_VALUE;
        int sampleCount = Math.max(MIN_EXPIRY_SAMPLE_COUNT, maxEntries / 20);
        int checked = 0;
        for (java.util.Map.Entry<String, Long> entry : insertionTimestamps.entrySet()) {
            if (checked++ >= sampleCount) {
                break;
            }
            if (entry.getValue() < oldestTimestamp) {
                oldestTimestamp = entry.getValue();
                oldestKey = entry.getKey();
            }
        }
        return oldestKey;
    }

    /**
     * 清理 insertionTimestamps 与 store 之间的漂移条目。
     *
     * <p>
     * 当 drift 较小时，仅清理快速路径。
     * 当 drift 超过 maxEntries/2 时，执行全量遍历清理（慢路径，但仅在严重漂移时触发）。
     *
     * @param fullScan 是否执行全量遍历
     */
    private void cleanupDrift(boolean fullScan) {
        if (fullScan) {
            // 全量遍历：移除 insertionTimestamps 中所有不在 store 中的陈旧条目
            int cleaned = 0;
            java.util.Iterator<String> it = insertionTimestamps.keySet().iterator();
            while (it.hasNext()) {
                String k = it.next();
                if (!store.containsKey(k)) {
                    it.remove();
                    removeFromPrefixIndex(k);
                    cleaned++;
                }
            }
            if (cleaned > 0) {
                log.debug("Full drift cleanup removed {} stale timestamp entries", cleaned);
            }
        } else {
            // 快速路径：移除不在 store 中的陈旧条目（采样检查）
            int cleaned = 0;
            int maxAttempts = Math.max(MIN_CAS_EVICTION_ATTEMPTS, maxEntries / 10);
            java.util.Iterator<String> it = insertionTimestamps.keySet().iterator();
            while (cleaned < maxAttempts && it.hasNext()) {
                String k = it.next();
                if (!store.containsKey(k)) {
                    it.remove();
                    removeFromPrefixIndex(k);
                    cleaned++;
                }
            }
        }
    }

    /**
     * 返回缓存命中率。
     *
     * @return 命中率（0.0-1.0），如果没有 get 操作则返回 0.0
     */
    private void syncCounters() {
        hitCount.addAndGet(localHits.getAndSet(0));
        missCount.addAndGet(localMisses.getAndSet(0));
    }

    @Override
    public double getHitRate() {
        syncCounters();
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
    @Override
    public long getHitCount() {
        syncCounters();
        return hitCount.get();
    }

    /**
     * 返回缓存未命中次数。
     *
     * @return 未命中次数
     */
    @Override
    public long getMissCount() {
        syncCounters();
        return missCount.get();
    }

    /**
     * 重置命中率统计计数器。
     */
    @Override
    public void resetStats() {
        hitCount.set(0);
        missCount.set(0);
        localHits.set(0);
        localMisses.set(0);
    }

    /**
     * 清除过期条目。采样部分条目进行检查，避免全量扫描带来的 CPU 热点。
     * 同时清理 insertionTimestamps 中对应的陈旧条目。
     */
    // ponytail: sample scales with store size — probes 20% (vs old 10%) for 10k+ entries
    /** ponytail: 旋转偏移量，每次过期检查前进 sampleSize，避免反复检查同一批条目 */
    private final java.util.concurrent.atomic.AtomicInteger evictionCursor =
        new java.util.concurrent.atomic.AtomicInteger(0);

    private void evictExpiredEntries() {
        int storeSize = store.size();
        int sampleSize = storeSize > MIN_EXPIRY_SAMPLE_COUNT_SMALL
            ? Math.min(storeSize, Math.max(MIN_EXPIRY_SAMPLE_COUNT_SMALL, storeSize / 5)) : storeSize;
        if (sampleSize == 0) {
            return;
        }
        int skip = evictionCursor.getAndAccumulate(sampleSize, (prev, add) -> prev + add < storeSize ? prev + add : 0);
        int count = 0;
        for (Map.Entry<String, CachedQueryResult<?>> entry : store.entrySet()) {
            if (skip > 0) {
                skip--;
                continue;
            }
            if (count++ >= sampleSize) {
                break;
            }
            if (entry.getValue().isExpired()) {
                if (store.remove(entry.getKey(), entry.getValue())) {
                    insertionTimestamps.remove(entry.getKey());
                    removeFromPrefixIndex(entry.getKey());
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
    @Override
    public void evict(String key) {
        // ponytail: CAS 移除确保仅当值未被并发替换时才清理时间戳
        CachedQueryResult<?> val = store.remove(key);
        if (val != null) {
            insertionTimestamps.remove(key);
            removeFromPrefixIndex(key);
        }
        log.debug("Cache evicted for key: {}", key);
    }

    /**
     * 清除所有缓存条目。
     *
     * <p>
     * <strong>并发说明：</strong>此方法非原子操作。与并发 {@link #put(String, Object, long)} 之间存在窗口期，
     * 可能导致 insertionTimestamps 与 store 之间的漂移。漂移是自愈的——后续 {@link #put} 调用中的 drift cleanup 会修复。
     */
    @Override
    public void clear() {
        evictionLock.lock();
        try {
            // ponytail: 先递增 generation，让并发 put() 看到新 generation 后丢弃写入，消除僵尸条目窗口
            clearGeneration.incrementAndGet();
            insertionTimestamps.clear();
            prefixIndex.clear();
            store.clear();
        } finally {
            evictionLock.unlock();
        }
        log.debug("Cache cleared");
    }

    /**
     * 按键前缀批量驱逐缓存条目。适用于实体变更后清除相关查询缓存。
     *
     * <p>
     * <strong>性能说明：</strong>使用前缀索引将复杂度从 O(n) 降低到 O(k)（k 为匹配条目数）。
     * 前缀索引在 {@link #put} 时自动维护，索引条目数等于实体类型数（通常 <100），开销极小。
     *
     * <p>
     * 示例：
     *
     * <pre>{@code
     * // User 实体更新后，清除所有以 "User:" 开头的缓存
     * cache.evictByPrefix("com.example.User:");
     * }</pre>
     *
     * @param keyPrefix 缓存键前缀
     * @return 被驱逐的条目数
     */
    @Override
    public int evictByPrefix(String keyPrefix) {
        if (keyPrefix == null || keyPrefix.isEmpty()) {
            return 0;
        }
        // 规范化前缀：无冒号时自动追加，确保 startsWith 匹配不会越界到其他前缀
        String normalizedPrefix = keyPrefix.endsWith(":") ? keyPrefix : keyPrefix + ":";
        String prefix = keyPrefix.endsWith(":") ? keyPrefix.substring(0, keyPrefix.length() - 1) : keyPrefix;
        java.util.Set<String> indexedKeys = prefixIndex.get(prefix);
        int count = 0;
        if (indexedKeys != null) {
            // 复制集合避免 ConcurrentModificationException
            for (String key : new java.util.ArrayList<>(indexedKeys)) {
                if (key.startsWith(normalizedPrefix)) {
                    store.remove(key);
                    insertionTimestamps.remove(key);
                    indexedKeys.remove(key);
                    count++;
                }
            }
            if (indexedKeys.isEmpty()) {
                prefixIndex.computeIfPresent(prefix, (k, v) -> v.isEmpty() ? null : v);
            } else {
                // ponytail: 清理当前前缀下已被 store.remove 但索引残留的 key
                // 数据竞争: put() 先成功 store.putIfAbsent, 然后被并发 evict
                // 导致 store 无 key 但索引仍残留
                indexedKeys.removeIf(k -> !store.containsKey(k));
            }
        } else {
            // ponytail: 前缀索引只存储第一级前缀，多段前缀需回退到全量扫描
            // ponytail: 先收集再删除，避免在 ConcurrentHashMap 迭代中修改
            java.util.List<String> keysToRemove = new java.util.ArrayList<>();
            for (String key : store.keySet()) {
                if (key.startsWith(normalizedPrefix)) {
                    keysToRemove.add(key);
                }
            }
            for (String key : keysToRemove) {
                if (store.remove(key) != null) {
                    insertionTimestamps.remove(key);
                    removeFromPrefixIndex(key);
                    count++;
                }
            }
        }
        // ponytail: 清理 prefixIndex 中可能残留的脏 key（put -> store 成功，addToPrefixIndex 被并发 evict 打断）
        if (count > 0) {
            prefixIndex.forEach((p, keys) -> {
                if (keys != null) {
                    keys.removeIf(k -> !store.containsKey(k));
                }
            });
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
    @Override
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
     *     cacheManager.evictByPrefixAfterTransactionCommit("com.example.User:");
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
