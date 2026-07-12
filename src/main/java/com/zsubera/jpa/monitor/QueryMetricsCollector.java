package com.zsubera.jpa.monitor;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 查询性能指标收集器。
 *
 * <p>
 * 收集和统计查询性能指标，包括：
 * <ul>
 * <li>查询执行次数</li>
 * <li>总执行时间</li>
 * <li>平均执行时间</li>
 * <li>最大执行时间</li>
 * <li>慢查询次数</li>
 * </ul>
 *
 * <p>
 * 使用示例：
 *
 * <pre>{@code
 * QueryMetricsCollector metrics = QueryMetricsCollector.getInstance();
 *
 * // 记录查询执行
 * long startTime = System.nanoTime();
 * try {
 *     // 执行查询
 * } finally {
 *     long duration = System.nanoTime() - startTime;
 *     metrics.recordQuery("findAll", duration);
 * }
 *
 * // 获取统计信息
 * QueryMetricsCollector.QueryStats stats = metrics.getStats("findAll");
 * System.out.println("Average time: " + stats.getAverageTimeMs() + " ms");
 * }</pre>
 *
 * <p>
 * <strong>线程安全：</strong>此类是线程安全的，可以在多线程环境中使用。
 *

 */
public class QueryMetricsCollector {

    private static final Logger log = LoggerFactory.getLogger(QueryMetricsCollector.class);

    /** 单例实例 */
    private static final QueryMetricsCollector INSTANCE = new QueryMetricsCollector();

    /** 查询指标存储。Caffeine 内置 LRU 驱逐，max 4096 条目。 */
    private final Cache<String, QueryMetrics> metricsMap = Caffeine.newBuilder().maximumSize(4096).build();

    /** 慢查询阈值（纳秒），默认 1 秒 */
    private volatile long slowQueryThresholdNanos = 1_000_000_000L;

    /** 是否启用指标收集 */
    private volatile boolean enabled = true;

    private QueryMetricsCollector() {}

    /**
     * 获取单例实例。
     *
     * @return QueryMetricsCollector 实例
     */
    public static QueryMetricsCollector getInstance() {
        return INSTANCE;
    }

    /**
     * 设置慢查询阈值（毫秒）。
     *
     * @param thresholdMs 慢查询阈值（毫秒）
     * @throws IllegalArgumentException 如果阈值小于等于 0
     */
    public void setSlowQueryThresholdMs(long thresholdMs) {
        if (thresholdMs <= 0) {
            throw new IllegalArgumentException("thresholdMs must be positive");
        }
        this.slowQueryThresholdNanos = thresholdMs * 1_000_000L;
    }

    /**
     * 设置是否启用指标收集。
     *
     * @param enabled 是否启用
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 检查指标收集是否启用。
     *
     * @return 如果启用返回 true
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 记录查询执行。
     *
     * @param queryName 查询名称
     * @param durationNanos 执行时间（纳秒）
     */
    public void recordQuery(String queryName, long durationNanos) {
        if (!enabled || queryName == null) {
            return;
        }

        QueryMetrics metrics = metricsMap.get(queryName, k -> new QueryMetrics());
        metrics.record(durationNanos);

        // 检查是否为慢查询
        if (durationNanos >= slowQueryThresholdNanos) {
            double durationMs = durationNanos / 1_000_000.0;
            log.warn("SLOW QUERY DETECTED: '{}' took {} ms (threshold: {} ms)", queryName,
                String.format("%.2f", durationMs), String.format("%.2f", slowQueryThresholdNanos / 1_000_000.0));
        }
    }

    /**
     * 获取查询统计信息。
     *
     * @param queryName 查询名称
     * @return 查询统计信息，如果不存在返回 null
     */
    public QueryStats getStats(String queryName) {
        if (queryName == null) {
            return null;
        }
        QueryMetrics metrics = metricsMap.getIfPresent(queryName);
        return metrics != null ? metrics.toStats() : null;
    }

    /**
     * 获取所有查询的统计信息。
     *
     * @return 查询统计信息映射
     */
    public java.util.Map<String, QueryStats> getAllStats() {
        java.util.Map<String, QueryStats> result = new java.util.HashMap<>();
        metricsMap.asMap().forEach((name, metrics) -> result.put(name, metrics.toStats()));
        return result;
    }

    /**
     * 重置所有指标。
     */
    public void reset() {
        metricsMap.invalidateAll();
    }

    /**
     * 关闭指标收集器，清理缓存资源。
     *
     * <p>
     * 应在应用关闭时调用，防止内存泄漏。
     */
    public void shutdown() {
        metricsMap.invalidateAll();
        enabled = false;
    }

    /**
     * 重置指定查询的指标。
     *
     * @param queryName 查询名称
     */
    public void reset(String queryName) {
        if (queryName != null) {
            metricsMap.invalidate(queryName);
        }
    }

    /**
     * 获取查询执行次数。
     *
     * @param queryName 查询名称
     * @return 执行次数，如果不存在返回 0
     */
    public long getExecutionCount(String queryName) {
        QueryMetrics metrics = metricsMap.getIfPresent(queryName);
        return metrics != null ? metrics.count.sum() : 0;
    }

    /**
     * 查询指标内部类。
     */
    private static class QueryMetrics {
        final LongAdder count = new LongAdder();
        final LongAdder totalTimeNanos = new LongAdder();
        final AtomicLong maxTimeNanos = new AtomicLong();

        void record(long durationNanos) {
            count.increment();
            totalTimeNanos.add(durationNanos);

            // 更新最大值
            long currentMax;
            do {
                currentMax = maxTimeNanos.get();
            } while (durationNanos > currentMax && !maxTimeNanos.compareAndSet(currentMax, durationNanos));
        }

        QueryStats toStats() {
            long totalCount = count.sum();
            long totalTime = totalTimeNanos.sum();
            double averageTimeMs = totalCount > 0 ? (totalTime / (double)totalCount) / 1_000_000.0 : 0;
            double maxTimeMs = maxTimeNanos.get() / 1_000_000.0;
            double totalTimeMs = totalTime / 1_000_000.0;

            return new QueryStats(totalCount, totalTimeMs, averageTimeMs, maxTimeMs);
        }
    }

    /**
     * 查询统计信息。
     */
    public static class QueryStats {
        private final long executionCount;
        private final double totalTimeMs;
        private final double averageTimeMs;
        private final double maxTimeMs;

        QueryStats(long executionCount, double totalTimeMs, double averageTimeMs, double maxTimeMs) {
            this.executionCount = executionCount;
            this.totalTimeMs = totalTimeMs;
            this.averageTimeMs = averageTimeMs;
            this.maxTimeMs = maxTimeMs;
        }

        /**
         * 获取执行次数。
         *
         * @return 执行次数
         */
        public long getExecutionCount() {
            return executionCount;
        }

        /**
         * 获取总执行时间（毫秒）。
         *
         * @return 总执行时间
         */
        public double getTotalTimeMs() {
            return totalTimeMs;
        }

        /**
         * 获取平均执行时间（毫秒）。
         *
         * @return 平均执行时间
         */
        public double getAverageTimeMs() {
            return averageTimeMs;
        }

        /**
         * 获取最大执行时间（毫秒）。
         *
         * @return 最大执行时间
         */
        public double getMaxTimeMs() {
            return maxTimeMs;
        }

        @Override
        public String toString() {
            return String.format("QueryStats{count=%d, total=%.2fms, avg=%.2fms, max=%.2fms}", executionCount,
                totalTimeMs, averageTimeMs, maxTimeMs);
        }
    }
}
