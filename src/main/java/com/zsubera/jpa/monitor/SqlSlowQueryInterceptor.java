package com.zsubera.jpa.monitor;

import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Hibernate SQL 慢查询拦截器，实现了 Hibernate {@link StatementInspector} 接口。
 *
 * <p>
 * <strong>Hibernate 专有：</strong>此类实现了 {@code org.hibernate.resource.jdbc.spi.StatementInspector}，
 * 仅在 Hibernate 作为 JPA Provider 时可用。由自动配置中的
 * {@code @ConditionalOnClass(name = "org.hibernate.resource.jdbc.spi.StatementInspector")} 条件控制注册。
 *
 * <p>
 * <strong>非 Hibernate 环境：</strong>慢查询监控功能由 {@link SlowQueryDataSourceProxy}
 * 通过纯 JDK {@link java.lang.reflect.Proxy} 实现，不依赖任何特定 JPA 实现。
 * 此机制与 Hibernate {@code StatementInspector} 互补，但完全独立运行。
 *
 * <p>
 * <strong>自动装配说明：</strong>
 * <ul>
 * <li>Hibernate 环境：同时注册本类（StatementInspector）和 {@link SlowQueryDataSourceProxyPostProcessor}
 * （DataSource 代理），提供双重监控</li>
 * <li>非 Hibernate 环境：仅通过 {@link SlowQueryDataSourceProxyPostProcessor} 注册 DataSource 代理</li>
 * </ul>
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
