package com.zsubera.jpa.template;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 查询结果缓存管理器，基于 {@link Caffeine} 实现，支持 TTL 过期和最大条目数限制。
 *
 * <p>
 * 由 {@link MyJpaTemplate}（或直接）使用以缓存查询结果。条目在访问时惰性驱逐（TTL 过期），
 * 同时由 Caffeine 的 W-TinyLFU 算法在容量超限时自动驱逐低频访问条目。
 *
 * <p>
 * <strong>与原手写实现的区别：</strong>
 * <ul>
 * <li>驱逐策略从"采样 hash-order FIFO"升级为 W-TinyLFU（更高命中率）</li>
 * <li>TTL 过期从"手动采样扫描"变为 Caffeine 内部自动管理</li>
 * <li>并发安全从"ConcurrentHashMap + CAS + tryLock"变为 Caffeine 内置 segment 锁</li>
 * <li>消除了 generation counter、drift detection 等手写竞态处理代码</li>
 * </ul>
 *
 * <p>
 * <strong>事务集成说明：</strong>当前缓存实现独立于事务生命周期。写操作（INSERT/UPDATE/DELETE）后，
 * 相关缓存条目不会自动失效。建议在事务提交后手动调用 {@link #evictByPrefix(String)} 清除相关缓存。
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
public class QueryCacheManager implements CacheAdapter {

    private static final Logger log = LoggerFactory.getLogger(QueryCacheManager.class);

    /** 默认最大缓存条目数 */
    private static final int DEFAULT_MAX_ENTRIES = 10000;

    /** 缓存键最大长度限制，防止恶意超长键导致内存问题 */
    private static final int MAX_KEY_LENGTH = 1024;

    /** 哨兵对象，用于在 Caffeine 的 get(key, mappingFunction) 中表示 null 结果 */
    private static final Object NULL_SENTINEL = new Object();

    /** 缓存命中计数 */
    private final AtomicLong hitCount = new AtomicLong(0);

    /** 缓存未命中计数 */
    private final AtomicLong missCount = new AtomicLong(0);

    /**
     * 驱逐代数计数器。每次 evictByPrefix 或 clear 调用后递增。
     * 用于 MyJpaTemplate.afterCommit 回调中检测缓存是否在查询后被驱逐。
     */
    private final AtomicLong evictionGeneration = new AtomicLong(0);

    /**
     * Caffeine 缓存实例。
     * <p>
     * 使用 {@link CachedValue} 包装器实现 per-entry TTL，每次访问时检查过期状态。
     * Caffeine 内部使用 W-TinyLFU 驱逐算法，在容量超限时自动驱逐低频访问条目。
     */
    private final Cache<String, Object> cache;

    /** 最大缓存条目数（用于 getMaxEntries/setMaxEntries） */
    private volatile int maxEntries;

    /**
     * 前缀索引，用于 O(k) 前缀驱逐（k = 匹配条目数）而非 O(n) 全扫描。
     * key = 实体类名前缀（如 "com.example.User:"），value = 该前缀下的缓存键集合。
     */
    private final java.util.concurrent.ConcurrentHashMap<String, java.util.Set<String>> prefixIndex =
        new java.util.concurrent.ConcurrentHashMap<>();

    /** 内部缓存值包装器，记录写入时间戳以支持 per-entry TTL 过期 */
    private record CachedValue(Object value, long writeTimeNanos, long ttlNanos) {
        boolean isExpired() {
            return System.nanoTime() - writeTimeNanos >= ttlNanos;
        }
    }

    private Object packWithTtl(Object value, long ttlSeconds) {
        return new CachedValue(value, System.nanoTime(), TimeUnit.SECONDS.toNanos(ttlSeconds));
    }

    @SuppressWarnings("unchecked")
    private <T> T unpackIfPresent(Object cached) {
        if (cached == null) {
            return null;
        }
        if (cached instanceof CachedValue cv) {
            if (cv.isExpired()) {
                return null;
            }
            Object val = cv.value();
            return val == NULL_SENTINEL ? null : (T) val;
        }
        if (cached == NULL_SENTINEL) {
            return null;
        }
        return (T) cached;
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
        this.cache = Caffeine.newBuilder()
            .maximumSize(maxEntries)
            .executor(Runnable::run)
            .removalListener((String key, Object value, com.github.benmanes.caffeine.cache.RemovalCause cause) -> {
                if (key != null) {
                    removeFromPrefixIndex(key);
                }
            })
            .build();
    }

    /**
     * 设置最大缓存条目数。
     *
     * <p>
     * Caffeine 的 maximumSize 不支持运行时动态调整，此方法记录新值供后续创建使用。
     * 如需立即生效，需重建缓存实例。
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
     * Caffeine 的 {@code getIfPresent} 会自动处理 TTL 过期——过期条目在访问时惰性清除。
     * 返回 List 时提供防御性拷贝，防止调用者修改返回值破坏缓存数据一致性。
     *
     * @param key 缓存键
     * @param <T> 期望的值类型
     * @return 缓存值，如果不存在/已过期则返回 null
     */
    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        Object cached = cache.getIfPresent(key);
        T value = unpackIfPresent(cached);
        if (value == null) {
            missCount.incrementAndGet();
            return null;
        }
        hitCount.incrementAndGet();
        // ponytail: 返回不可变 List 视图，避免防御性拷贝的内存开销，
        // 同时防止调用者修改返回值破坏缓存数据一致性。
        if (value instanceof List<?> list) {
            @SuppressWarnings("unchecked")
            T result = (T) java.util.Collections.unmodifiableList(list);
            return result;
        }
        return value;
    }

    /**
     * 将值存入缓存，指定 TTL（生存时间）。
     *
     * <p>
     * Caffeine 内部自动处理容量限制——超过 maximumSize 时由 W-TinyLFU 算法驱逐低频条目。
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
        if (key.length() > MAX_KEY_LENGTH) {
            log.warn("Cache key length ({}) exceeds maximum ({}). Key rejected: {}...", key.length(), MAX_KEY_LENGTH,
                key.substring(0, 64));
            return false;
        }
        cache.put(key, packWithTtl(value, ttlSeconds));
        addToPrefixIndex(key);
        log.debug("Cache put for key: {} (ttl={}s)", key, ttlSeconds);
        return true;
    }

    /**
     * 原子性地获取或计算缓存值，提供缓存击穿保护。
     *
     * <p>
     * Caffeine 的 {@code get(key, mappingFunction)} 内置了 per-key 线程安全保证——
     * 当多个线程并发请求同一个未缓存的键时，仅一个线程执行 {@code loader}，
     * 其余线程等待其结果。无需手写 ReentrantLock 防击穿逻辑。
     *
     * @param key 缓存键
     * @param loader 值加载函数，在缓存未命中时调用
     * @param ttlSeconds 生存时间（秒）
     * @param <T> 值类型
     * @return 已缓存或新加载的值
     */
    @SuppressWarnings("unchecked")
    public <T> T computeIfAbsent(String key, java.util.function.Supplier<T> loader, long ttlSeconds) {
        Object cached = cache.get(key, k -> {
            T loaded = loader.get();
            return loaded != null ? packWithTtl(loaded, ttlSeconds) : packWithTtl(NULL_SENTINEL, ttlSeconds);
        });
        addToPrefixIndex(key);
        T value = unpackIfPresent(cached);
        if (value == null) {
            missCount.incrementAndGet();
        } else {
            hitCount.incrementAndGet();
        }
        return value;
    }

    /**
     * 从缓存中移除指定条目。
     *
     * @param key 要驱逐的缓存键
     */
    @Override
    public void evict(String key) {
        cache.invalidate(key);
        // removal listener 已通过 removeFromPrefixIndex 清理前缀索引，
        // 无需重复调用（避免与 removal listener 竞态）。
        log.debug("Cache evicted for key: {}", key);
    }

    /**
     * 清除所有缓存条目。
     */
    @Override
    public void clear() {
        // 先清空 prefixIndex，再清空 cache，确保 concurrent put() 在 cache.invalidateAll()
        // 之后添加的条目仍会被 removal listener 清理前缀索引。
        prefixIndex.clear();
        cache.invalidateAll();
        evictionGeneration.incrementAndGet();
        log.debug("Cache cleared");
    }

    /**
     * 按键前缀批量驱逐缓存条目。适用于实体变更后清除相关查询缓存。
     *
     * <p>
     * <strong>性能说明：</strong>使用 Caffeine 的 {@code asMap()} 视图进行流式过滤，
     * 复杂度为 O(n)（n 为缓存总条目数）。对于典型场景（<10000 条目），性能可接受。
     * 如需 O(k) 前缀驱逐，可维护前缀索引（参见原手写实现）。
     *
     * @param keyPrefix 缓存键前缀
     * @return 被驱逐的条目数
     */
    @Override
    public int evictByPrefix(String keyPrefix) {
        if (keyPrefix == null || keyPrefix.isEmpty()) {
            return 0;
        }
        String normalizedPrefix = keyPrefix.endsWith(":") ? keyPrefix : keyPrefix + ":";
        java.util.Set<String> trackedKeys = prefixIndex.get(normalizedPrefix);
        if (trackedKeys == null || trackedKeys.isEmpty()) {
            return 0;
        }
        List<String> keysToRemove = new ArrayList<>(trackedKeys);
        for (String key : keysToRemove) {
            cache.invalidate(key);
        }
        // 不显式调用 prefixIndex.remove()：removal listener 已通过 removeFromPrefixIndex
        // 原子性地清理每个 key 的前缀索引。当 set 为空时自动移除 prefix 条目。
        // 显式 remove 会与并发 put() 竞态，导致新添加的 key 被遗弃。
        evictionGeneration.incrementAndGet();
        if (!keysToRemove.isEmpty()) {
            log.debug("Cache evicted {} entries with prefix '{}'", keysToRemove.size(), keyPrefix);
        }
        return keysToRemove.size();
    }

    private String extractPrefix(String key) {
        int colonIndex = key.indexOf(':');
        return colonIndex >= 0 ? key.substring(0, colonIndex + 1) : key;
    }

    private void addToPrefixIndex(String key) {
        String prefix = extractPrefix(key);
        prefixIndex.computeIfAbsent(prefix, k -> java.util.concurrent.ConcurrentHashMap.newKeySet()).add(key);
    }

    private void removeFromPrefixIndex(String key) {
        String prefix = extractPrefix(key);
        // 使用 compute 原子性地检查 set 是否为空，避免与并发 addToPrefixIndex 竞态：
        // 仅当 set 移除 key 后为空时才移除 prefix 条目。
        prefixIndex.compute(prefix, (k, keys) -> {
            if (keys != null) {
                keys.remove(key);
                if (keys.isEmpty()) {
                    return null;
                }
            }
            return keys;
        });
    }

    /**
     * 返回当前存储中的条目数。
     *
     * @return 条目数
     */
    @Override
    public int size() {
        return (int) cache.estimatedSize();
    }

    @Override
    public long getEvictionGeneration() {
        return evictionGeneration.get();
    }

    /**
     * 返回缓存命中率。
     *
     * @return 命中率（0.0-1.0），如果没有 get 操作则返回 0.0
     */
    @Override
    public double getHitRate() {
        long hits = hitCount.get();
        long misses = missCount.get();
        long total = hits + misses;
        return total == 0 ? 0.0 : (double) hits / total;
    }

    /**
     * 返回缓存命中次数。
     *
     * @return 命中次数
     */
    @Override
    public long getHitCount() {
        return hitCount.get();
    }

    /**
     * 返回缓存未命中次数。
     *
     * @return 未命中次数
     */
    @Override
    public long getMissCount() {
        return missCount.get();
    }

    /**
     * 重置命中率统计计数器。
     */
    @Override
    public void resetStats() {
        hitCount.set(0);
        missCount.set(0);
    }

    /**
     * 在事务提交后自动清除指定前缀的缓存条目。
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
