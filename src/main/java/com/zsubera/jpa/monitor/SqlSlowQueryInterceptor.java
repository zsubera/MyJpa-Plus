package com.zsubera.jpa.monitor;

import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Hibernate SQL 慢查询拦截器，实现了 Hibernate {@link StatementInspector} 接口。
 *
 * <p>
 * <strong>⚠️ 此类已废弃，将在下一个主版本中移除。</strong>
 * 慢查询监控已迁移至 {@link SlowQueryDataSourceProxy}（JDBC DataSource 代理层），提供更完整的计时覆盖且不依赖 Hibernate。
 *
 * <p>
 * <strong>迁移指南：</strong>
 * <ul>
 * <li>如果您使用 Spring Boot 自动配置，无需任何操作 — {@link SlowQueryDataSourceProxyPostProcessor} 会自动注册 {@link SlowQueryDataSourceProxy}</li>
 * <li>如果您手动注册了 {@code SqlSlowQueryInterceptor}，请移除该 Bean 并确保 {@code myjpa-plus.monitoring.enabled=true}</li>
 * </ul>
 *
 * @see SlowQueryDataSourceProxy
 * @see SlowQueryDataSourceProxyPostProcessor
 * @deprecated 已废弃。计时逻辑已迁移至 {@link SlowQueryDataSourceProxy}。此类将在未来版本中移除。
 */
@Deprecated(forRemoval = true)
public class SqlSlowQueryInterceptor implements StatementInspector {

    private static final long serialVersionUID = 1L;
    private static final Logger log = LoggerFactory.getLogger(SqlSlowQueryInterceptor.class);

    private static final String SLOW_QUERY_MARKER = "[SLOW QUERY]";

    private final long slowQueryThresholdMs;

    private final QueryMetricsCollector metricsCollector;

    /**
     * 创建慢查询拦截器。
     *
     * @param slowQueryThresholdMs 慢查询阈值（毫秒），超过此值的查询将记录警告日志
     * @throws IllegalArgumentException 如果 slowQueryThresholdMs 小于等于 0
     */
    public SqlSlowQueryInterceptor(long slowQueryThresholdMs) {
        this(slowQueryThresholdMs, QueryMetricsCollector.getInstance());
    }

    /**
     * 创建慢查询拦截器，指定指标收集器。
     *
     * @param slowQueryThresholdMs 慢查询阈值（毫秒）
     * @param metricsCollector     查询指标收集器
     */
    SqlSlowQueryInterceptor(long slowQueryThresholdMs, QueryMetricsCollector metricsCollector) {
        if (slowQueryThresholdMs <= 0) {
            throw new IllegalArgumentException("slowQueryThresholdMs must be positive");
        }
        this.slowQueryThresholdMs = slowQueryThresholdMs;
        this.metricsCollector = metricsCollector;
        log.warn("SqlSlowQueryInterceptor is disabled — timing is handled by SlowQueryDataSourceProxy at JDBC layer");
    }

    /**
     * 记录 SQL 开始的执行时间戳。
     * <p>
     * ponytail: 移除 ThreadLocal 计时 —— JDBC 层 {@link SlowQueryDataSourceProxy} 已提供完整计时，
     * 此处仅保留 StatementInspector 接口契约。Hibernate 6.x 的 StatementInspector 每次 SQL 执行前
     * 只调用一次 {@code inspect()}，无法在同层获取完成时间。
     */
    @Override
    public String inspect(String sql) {
        return sql;
    }

    /**
     * ponytail: 移除 ThreadLocal 计时 —— JDBC 层 {@link SlowQueryDataSourceProxy} 已提供完整计时。
     * 此方法保留为空操作以维持 API 兼容性。
     *
     * @param sql 已执行的 SQL 语句（未使用）
     */
    public void recordExecution(String sql) {
        // no-op: 计时由 SlowQueryDataSourceProxy 在 JDBC 层完成
    }

    /**
     * 获取慢查询阈值（毫秒）。
     *
     * @return 慢查询阈值
     */
    public long getSlowQueryThresholdMs() {
        return slowQueryThresholdMs;
    }
}
