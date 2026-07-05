package com.zsubera.jpa.monitor;

/**
 * 慢查询监听器接口，允许在检测到慢查询时执行自定义回调。
 *
 * <p>
 * 实现此接口并通过 {@link com.zsubera.jpa.autoconfigure.MyJpaPlusAutoConfiguration} 注册为 Spring Bean，
 * 即可在慢查询发生时收到通知。适用于发送指标到 Prometheus/Graphite、触发告警等场景。
 *
 * <h3>使用示例</h3>
 *
 * <pre>{@code
 * @Component
 * public class PrometheusSlowQueryListener implements SlowQueryListener {
 *
 *     private final MeterRegistry meterRegistry;
 *
 *     public PrometheusSlowQueryListener(MeterRegistry meterRegistry) {
 *         this.meterRegistry = meterRegistry;
 *     }
 *
 *     @Override
 *     public void onSlowQuery(String sql, long elapsedMs, long thresholdMs) {
 *         meterRegistry.counter("myjpa.slow_query.total",
 *             "threshold", String.valueOf(thresholdMs)).increment();
 *         meterRegistry.timer("myjpa.slow_query.duration").record(elapsedMs, TimeUnit.MILLISECONDS);
 *     }
 * }
 * }</pre>
 *
 * <p>
 * 线程安全：实现类应确保 {@link #onSlowQuery} 方法的线程安全性，因为可能从多个 JDBC 线程并发调用。
 *
 * @see SlowQueryDataSourceProxy
 * @see com.zsubera.jpa.autoconfigure.MyJpaPlusProperties.Monitoring
 */
@FunctionalInterface
public interface SlowQueryListener {

    /**
     * 当 SQL 执行时间超过慢查询阈值时调用。
     *
     * @param sql 已脱敏的 SQL 语句（通过 {@link SqlSanitizer#sanitize(String)} 处理）
     * @param elapsedMs 实际执行时间（毫秒）
     * @param thresholdMs 配置的慢查询阈值（毫秒）
     */
    void onSlowQuery(String sql, long elapsedMs, long thresholdMs);
}
