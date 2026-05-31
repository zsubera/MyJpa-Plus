package com.zsubera.jpa.monitor;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import javax.sql.DataSource;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * SQL 慢查询拦截器。
 *
 * <p>
 * 实现 Hibernate {@link StatementInspector} 接口用于注册，实际计时通过 JDBC {@link DataSource} 代理完成。代理会拦截 {@code prepareStatement()}
 * 返回的 {@code PreparedStatement}，在 {@code executeQuery()}、{@code executeUpdate()} 和 {@code execute()}
 * 调用前后测量耗时，超过阈值时记录警告日志。
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
 */
public class SqlSlowQueryInterceptor implements StatementInspector {

    private static final long serialVersionUID = 1L;
    private static final Logger log = LoggerFactory.getLogger(SqlSlowQueryInterceptor.class);

    /** 代理类缓存，避免每次 prepareStatement 创建新的代理类。 */
    private static final ConcurrentMap<Class<?>, Class<?>> PROXY_CLASS_CACHE = new ConcurrentHashMap<>();

    private final long slowQueryThresholdMs;

    @SuppressFBWarnings("EI_EXPOSE_REP2")
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
     * 将给定的 {@link DataSource} 包装为带有慢查询计时功能的代理。
     *
     * @param dataSource 原始 DataSource
     * @return 代理 DataSource
     */
    public DataSource wrapDataSource(DataSource dataSource) {
        return (DataSource)Proxy.newProxyInstance(DataSource.class.getClassLoader(), new Class<?>[] {DataSource.class},
            new DataSourceProxyHandler(dataSource));
    }

    private class DataSourceProxyHandler implements InvocationHandler {

        private final DataSource target;

        @SuppressFBWarnings("EI_EXPOSE_REP2")
        DataSourceProxyHandler(DataSource target) {
            this.target = target;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if ("prepareStatement".equals(method.getName())) {
                String sql = args.length > 0 && args[0] instanceof String ? (String)args[0] : "unknown";
                Object stmt = method.invoke(target, args);
                return wrapPreparedStatement(stmt, sql);
            }
            return method.invoke(target, args);
        }

        private Object wrapPreparedStatement(Object stmt, String sql) {
            Class<?> stmtClass = stmt.getClass();
            Class<?> proxyClass = PROXY_CLASS_CACHE.computeIfAbsent(stmtClass,
                clz -> Proxy.getProxyClass(clz.getClassLoader(), clz.getInterfaces()));
            try {
                return proxyClass.getConstructor(InvocationHandler.class)
                    .newInstance(new PreparedStatementTimingHandler(stmt, sql));
            } catch (ReflectiveOperationException e) {
                // Fallback to direct proxy creation
                return Proxy.newProxyInstance(stmtClass.getClassLoader(), stmtClass.getInterfaces(),
                    new PreparedStatementTimingHandler(stmt, sql));
            }
        }
    }

    private class PreparedStatementTimingHandler implements InvocationHandler {

        private static final String SLOW_QUERY_MARKER = "[SLOW QUERY]";
        private final Object target;
        private final String sql;

        @SuppressFBWarnings("EI_EXPOSE_REP2")
        PreparedStatementTimingHandler(Object target, String sql) {
            this.target = target;
            this.sql = sql;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String name = method.getName();
            if ("executeQuery".equals(name) || "executeUpdate".equals(name) || "execute".equals(name)) {
                long start = System.nanoTime();
                try {
                    return method.invoke(target, args);
                } finally {
                    long elapsedNanos = System.nanoTime() - start;
                    long elapsedMs = java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(elapsedNanos);
                    if (elapsedMs >= slowQueryThresholdMs) {
                        String sanitizedSql = sanitizeSql(sql);
                        log.warn("{} SQL execution took {} ms (threshold: {} ms) - {}", SLOW_QUERY_MARKER, elapsedMs,
                            slowQueryThresholdMs, sanitizedSql);
                    }
                }
            }
            return method.invoke(target, args);
        }
    }

    /**
     * 消毒 SQL 语句，移除可能包含的参数值以防止敏感数据泄露到日志。
     *
     * <p>
     * 替换规则：
     * <ul>
     * <li>单引号字符串（支持转义单引号 ''）→ ?</li>
     * <li>PostgreSQL 美元引用（$1, $2 等）→ ?</li>
     * <li>十六进制字面量（X'...'）→ ?</li>
     * <li>Unicode 字符串（N'...'）→ ?</li>
     * <li>数字字面量 → ?</li>
     * </ul>
     *
     * @param sql 原始 SQL 语句
     * @return 消毒后的 SQL 语句
     */
    private static String sanitizeSql(String sql) {
        if (sql == null) {
            return "null";
        }
        String sanitized = sql.replaceAll("'(?:[^']|'')*'", "?") // 单引号字符串
            .replaceAll("\\$\\d+", "?") // PostgreSQL 美元引用参数
            .replaceAll("X'[0-9a-fA-F]+'", "?") // 十六进制字面量
            .replaceAll("N'[^']*'", "?") // Unicode 字符串
            .replaceAll("\\b\\d+\\.?\\d*\\b", "?"); // 数字字面量
        return sanitized;
    }
}
