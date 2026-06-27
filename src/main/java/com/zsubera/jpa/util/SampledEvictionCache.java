package com.zsubera.jpa.util;

import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

/**
 * 基于 {@link ConcurrentHashMap} 的周期性驱逐缓存。
 *
 * <p>
 * 当缓存大小超过 {@code maxSize} 时，按 {@code samplingInterval} 采样检查并驱逐条目至
 * {@code maxSize × evictionTargetRatio}。使用 {@link ReentrantLock} 确保只有一个线程执行驱逐——
 * 驱逐是幂等的，其他线程跳过不等待。
 *
 * <p>
 * <strong>驱逐策略：</strong>使用迭代器顺序驱逐（hash-order 近似 FIFO 语义），按 ConcurrentHashMap 内部哈希桶顺序
 * 从迭代器头部开始移除。这不是真正的 LRU 或插入顺序 FIFO，但对于 LambdaUtils 和 KeysetPaginationHelper
 * 等预热型缓存场景已足够。
 *
 * <p>
 * 此类型消除了 {@code keySet().toArray()} 分配（参考 {@code KeysetPaginationHelper} 的原实现），
 * 驱逐时通过 {@link Iterator} 直接遍历。
 *
 * @param <K> 键类型
 * @param <V> 值类型
 */
public class SampledEvictionCache<K, V> {

    private final ConcurrentMap<K, V> store;
    private volatile int maxSize;
    private final double evictionTargetRatio;
    private final int samplingInterval;
    private final AtomicInteger counter = new AtomicInteger(0);
    private final ReentrantLock evictionLock = new ReentrantLock();

    /**
     * @param maxSize             触发驱逐的容量上限
     * @param evictionTargetRatio 驱逐后容量为目标比例（如 0.75 表示驱逐至 maxSize × 75%）
     * @param samplingInterval    每 N 次操作检查一次驱逐
     * @param initialCapacity     ConcurrentHashMap 初始容量
     */
    public SampledEvictionCache(int maxSize, double evictionTargetRatio, int samplingInterval, int initialCapacity) {
        if (maxSize <= 0)
            throw new IllegalArgumentException("maxSize must be positive, got: " + maxSize);
        if (evictionTargetRatio <= 0 || evictionTargetRatio >= 1)
            throw new IllegalArgumentException("evictionTargetRatio must be in (0, 1), got: " + evictionTargetRatio);
        if (samplingInterval <= 0)
            throw new IllegalArgumentException("samplingInterval must be positive, got: " + samplingInterval);
        if (initialCapacity <= 0)
            throw new IllegalArgumentException("initialCapacity must be positive, got: " + initialCapacity);
        this.maxSize = maxSize;
        this.evictionTargetRatio = evictionTargetRatio;
        this.samplingInterval = samplingInterval;
        this.store = new ConcurrentHashMap<>(initialCapacity);
    }

    /**
     * @param maxSize             触发驱逐的容量上限
     * @param evictionTargetRatio 驱逐后容量为目标比例
     * @param samplingInterval    每 N 次操作检查一次驱逐
     */
    public SampledEvictionCache(int maxSize, double evictionTargetRatio, int samplingInterval) {
        this(maxSize, evictionTargetRatio, samplingInterval, 64);
    }

    public V get(Object key) {
        return store.get(key);
    }

    /**
     * 若 key 不存在则计算并存入，返回已有值或计算值。每次调用执行采样驱逐检查。
     */
    public V computeIfAbsent(K key, Function<? super K, ? extends V> mappingFunction) {
        samplingEvict();
        return store.computeIfAbsent(key, mappingFunction);
    }

    /**
     * 存入键值对，返回旧值（可能为 null）。每次调用执行采样驱逐检查。
     */
    public V put(K key, V value) {
        samplingEvict();
        return store.put(key, value);
    }

    public int size() {
        return store.size();
    }

    /**
     * 动态调整最大容量。驱逐触发门槛将在下一次采样检查时生效。
     */
    public void setMaxSize(int maxSize) {
        if (maxSize <= 0)
            throw new IllegalArgumentException("maxSize must be positive, got: " + maxSize);
        this.maxSize = maxSize;
    }

    // ponytail: ConcurrentHashMap.clear() is thread-safe, no lock needed
    public void clear() {
        store.clear();
    }

    private void samplingEvict() {
        if (Math.floorMod(counter.getAndIncrement(), samplingInterval) != 0) {
            return;
        }
        if (store.size() <= maxSize) {
            return;
        }
        if (!evictionLock.tryLock()) {
            return;
        }
        try {
            int currentSize = store.size();
            if (currentSize > maxSize) {
                int target = Math.max(1, (int)(maxSize * evictionTargetRatio));
                int toRemove = currentSize - target;
                if (toRemove > 0) {
                    int removed = 0;
                    java.util.Iterator<K> it = store.keySet().iterator();
                    while (it.hasNext() && removed < toRemove) {
                        it.next();
                        it.remove();
                        removed++;
                    }
                }
            }
        } finally {
            evictionLock.unlock();
        }
    }
}
