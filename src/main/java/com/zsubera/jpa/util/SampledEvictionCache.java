package com.zsubera.jpa.util;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 基于 {@link Caffeine} 的有界缓存，替代原先基于 ConcurrentHashMap 的手写采样驱逐实现。
 *
 * <p>
 * 使用 Caffeine 的 W-TinyLFU 驱逐算法，提供比手写采样驱逐更精确的命中率和更严格的大小约束。
 * Caffeine 内部使用 segment 锁实现高并发下的无锁读和低争用写。
 *
 * <p>
 * <strong>API 兼容性：</strong>保持与原 {@code ConcurrentHashMap + 采样驱逐} 实现完全相同的公开 API，
 * 确保 13+ 处调用方无需修改。
 *
 * <p>
 * <strong>setMaxSize 行为：</strong>Caffeine 的 {@code maximumSize} 不支持运行时动态调整。
 * 调用 {@link #setMaxSize(int)} 会重建缓存（丢失现有条目）。此方法仅在启动初始化阶段调用，
 * 运行时调用会导致缓存命中率骤降和并发操作数据丢失。
 *
 * @param <K> 键类型
 * @param <V> 值类型
 */
public class SampledEvictionCache<K, V> {

    private static final Logger log = LoggerFactory.getLogger(SampledEvictionCache.class);

    private volatile Cache<K, V> delegate;
    private volatile int maxSize;

    /** 标记缓存是否已完成初始化，初始化后禁止调用 setMaxSize() */
    private volatile boolean initialized = false;

    /**
     * @param maxSize             触发驱逐的容量上限
     * @param evictionTargetRatio 保留参数，Caffeine 使用 W-TinyLFU 自动管理驱逐比例
     * @param samplingInterval    保留参数，Caffeine 内部自动采样
     * @param initialCapacity     保留参数，Caffeine 自动管理初始容量
     */
    public SampledEvictionCache(int maxSize, double evictionTargetRatio, int samplingInterval, int initialCapacity) {
        if (maxSize <= 0)
            throw new IllegalArgumentException("maxSize must be positive, got: " + maxSize);
        this.maxSize = maxSize;
        this.delegate = build(maxSize);
    }

    /**
     * @param maxSize             触发驱逐的容量上限
     * @param evictionTargetRatio 保留参数
     * @param samplingInterval    保留参数
     */
    public SampledEvictionCache(int maxSize, double evictionTargetRatio, int samplingInterval) {
        this(maxSize, evictionTargetRatio, samplingInterval, 64);
    }

    private static <K, V> Cache<K, V> build(int maxSize) {
        return Caffeine.newBuilder().maximumSize(maxSize).build();
    }

    @SuppressWarnings("unchecked")
    public V get(Object key) {
        // ponytail: 使用 Caffeine 原生 API，避免创建 ConcurrentHashMap 视图
        return delegate.getIfPresent((K)key);
    }

    /**
     * 若 key 不存在则计算并存入，返回已有值或计算值。
     */
    public V computeIfAbsent(K key, Function<? super K, ? extends V> mappingFunction) {
        return delegate.get(key, mappingFunction::apply);
    }

    /**
     * 存入键值对。注意：不返回旧值（避免不必要的查找开销）。
     */
    public void put(K key, V value) {
        delegate.put(key, value);
    }

    public int size() {
        return (int)delegate.estimatedSize();
    }

    /**
     * 动态调整最大容量。会重建缓存（丢失现有条目）。
     *
     * <p><strong>重要：</strong>此方法仅应在启动初始化阶段调用（即缓存首次创建后、首次使用前）。
     * 运行时调用会导致：
     * <ul>
     *   <li>所有现有缓存条目丢失</li>
     *   <li>并发的 get/put 操作可能丢失数据（TOCTOU 竞态）</li>
     *   <li>缓存命中率骤降</li>
     * </ul>
     *
     * @param maxSize 新的最大容量
     * @throws IllegalArgumentException 如果 maxSize 不是正数
     * @apiNote 如果缓存已完成初始化（已有数据被缓存），会记录警告并重建缓存（丢弃现有条目）。
     */
    public synchronized void setMaxSize(int maxSize) {
        if (maxSize <= 0)
            throw new IllegalArgumentException("maxSize must be positive, got: " + maxSize);
        if (initialized) {
            log.warn(
                "setMaxSize() called after cache initialization. "
                    + "This will discard all existing entries and may cause data loss for concurrent operations. "
                    + "Consider creating a new SampledEvictionCache instance instead.",
                new RuntimeException("Stack trace for debugging"));
        }
        long previousSize = delegate.estimatedSize();
        // 先构建新缓存，再更新 maxSize，确保读者始终看到一致的缓存实例
        Cache<K, V> newDelegate = build(maxSize);
        this.delegate = newDelegate;
        this.maxSize = maxSize;
        initialized = true;
        if (previousSize > 0) {
            log.warn(
                "Cache resized from {} to {} entries — {} entries dropped. "
                    + "setMaxSize() should only be called during startup initialization.",
                previousSize, maxSize, previousSize);
        }
    }

    public void clear() {
        delegate.invalidateAll();
    }
}
