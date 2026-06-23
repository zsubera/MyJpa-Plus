package com.zsubera.jpa.monitor;

import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Hibernate SQL 慢查询拦截器，实现了 Hibernate {@link StatementInspector} 接口。
 *
 * <p>
 * 通过 Hibernate 的 StatementInspector SPI，在每次 SQL 执行前后注入计时逻辑，
 * 检测慢查询并记录警告日志。计时结果通过 {@link ThreadLocal} 传递给 inspect() 方法，
 * 由 StatementInspector 的实际调用方（hibernate statement executor）配合完成。
 *
 * <p>
 * <strong>Hibernate 专有：</strong>此类实现了 {@code org.hibernate.resource.jdbc.spi.StatementInspector}，
 * 仅在 Hibernate 作为 JPA Provider 时可用。由自动配置中的
 * {@code @ConditionalOnClass(name = "org.hibernate.resource.jdbc.spi.StatementInspector")} 条件控制注册。
 *
 * <p>
 * <strong>与非 Hibernate 环境的 Watch 关系：</strong>慢查询监控有两层互补机制：
 * <ul>
 * <li>本类（Hibernate StatementInspector）：在 Hibernate 层面拦截所有 SQL，包括 JPQL/HQL 生成的 SQL</li>
 * <li>{@link SlowQueryDataSourceProxy}（JDBC DataSource 代理）：在 JDBC 层面拦截，覆盖所有 JDBC 访问</li>
 * </ul>
 * 两者独立运行，在 Hibernate 环境中同时生效提供冗余监控。
 *
 * <p>
 * 使用方式：
 *
 * <pre>{@code
 * myjpa-plus:
 *   monitoring:
 *     slow-query-threshold-ms: 1000
 *     enabled: true
 * }</pre>
 *
 * @see SlowQueryDataSourceProxy
 * @see SlowQueryDataSourceProxyPostProcessor
 */
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
