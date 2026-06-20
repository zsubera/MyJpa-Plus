package com.zsubera.jpa.monitor;

import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Hibernate SQL 慢查询拦截器，实现了 Hibernate {@link StatementInspector} 接口。
 *
 * <p>
 * 此类仅在 Hibernate 环境中可用。实际的 JDBC DataSource 代理计时功能由
 * {@link SlowQueryDataSourceProxy} 提供，不依赖任何特定 JPA 实现。
 *
 * <p>
 * <strong>自动装配说明：</strong>
 * <ul>
 * <li>Hibernate 环境：自动注册本类作为 {@code StatementInspector}（通过
 * {@code hibernate.session_factory.statement_inspector}），同时通过 {@link SlowQueryDataSourceProxy}
 * 提供 DataSource 代理计时</li>
 * <li>非 Hibernate 环境：仅通过 {@link SlowQueryDataSourceProxy} 提供 DataSource 代理计时</li>
 * </ul>
 *
 * <p>
 * <strong>DataSource 包装：</strong>如需手动包装 DataSource，使用
 * {@link SlowQueryDataSourceProxy#wrap(javax.sql.DataSource, long)} 而非本类。
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

    private final long slowQueryThresholdMs;

    /**
     * 创建慢查询拦截器。
     *
     * @param slowQueryThresholdMs 慢查询阈值（毫秒），超过此值的查询将记录警告日志
     * @throws IllegalArgumentException 如果 slowQueryThresholdMs 小于等于 0
     */
    public SqlSlowQueryInterceptor(long slowQueryThresholdMs) {
        if (slowQueryThresholdMs <= 0) {
            throw new IllegalArgumentException("slowQueryThresholdMs must be positive");
        }
        this.slowQueryThresholdMs = slowQueryThresholdMs;
    }

    @Override
    public String inspect(String sql) {
        return sql;
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
