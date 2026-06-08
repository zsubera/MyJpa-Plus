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
 * <strong>此功能仅限 Hibernate 环境。</strong>实现了 Hibernate {@link StatementInspector} 接口用于注册， 实际计时通过 JDBC {@link DataSource}
 * 代理完成。在非 Hibernate JPA 实现（如 EclipseLink、OpenJPA）上， {@link StatementInspector} 接口不可用，此类应通过
 * {@code @ConditionalOnClass(StatementInspector.class)} 条件装配来跳过自动配置。代理会拦截 {@code prepareStatement()} 返回的
 * {@code PreparedStatement}， 在 {@code executeQuery()}、{@code executeUpdate()} 和 {@code execute()} 调用前后测量耗时，超过阈值时记录警告日志。
 *
 * <p>
 * <strong>非 Hibernate 环境替代方案：</strong>如果需要在非 Hibernate 环境中监控慢查询， 可以使用 {@link #wrapDataSource(DataSource)} 方法手动包装
 * {@link DataSource}， 该方法使用标准 JDBC 代理实现，不依赖 Hibernate。
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

    /**
     * 代理类缓存，避免每次 prepareStatement 创建新的代理类。 使用大小限制的 ConcurrentHashMap，超过限制时随机驱逐以防止内存泄漏。
     */
    private static final int MAX_PROXY_CLASS_CACHE_SIZE = 512;
    private static final ConcurrentMap<Class<?>, Class<?>> PROXY_CLASS_CACHE = new ConcurrentHashMap<>();

    /** 代理类缓存驱逐锁，确保只有一个线程执行驱逐 */
    private static final java.util.concurrent.atomic.AtomicBoolean EVICTING =
        new java.util.concurrent.atomic.AtomicBoolean(false);

    private final long slowQueryThresholdMs;

    /**
     * 创建慢查询拦截器。
     *
     * @param slowQueryThresholdMs 慢查询阈值（毫秒），超过此值的查询将记录警告日志
     * @throws IllegalArgumentException 如果 slowQueryThresholdMs 小于等于 0
     */
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

    public class DataSourceProxyHandler implements InvocationHandler {

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
            // 如果 Statement 实现类没有实现任何接口，无法创建 JDK 动态代理，
            // 直接返回原始对象。正常 JDBC 驱动的 PreparedStatement 总会实现
            // java.sql.PreparedStatement 等接口，此为防御性检查。
            if (stmtClass.getInterfaces().length == 0) {
                log.debug("PreparedStatement class {} implements no interfaces, skipping proxy wrapping",
                    stmtClass.getName());
                return stmt;
            }
            // 采样驱逐：每 64 次调用检查一次缓存大小，避免每次调用都检查
            if (PROXY_CLASS_CACHE.size() > MAX_PROXY_CLASS_CACHE_SIZE
                && java.util.concurrent.ThreadLocalRandom.current().nextInt(64) == 0) {
                // 使用 CAS 确保只有一个线程执行驱逐
                if (EVICTING.compareAndSet(false, true)) {
                    try {
                        int currentSize = PROXY_CLASS_CACHE.size();
                        if (currentSize > MAX_PROXY_CLASS_CACHE_SIZE) {
                            int toRemove = currentSize / 4;
                            java.util.Iterator<?> it = PROXY_CLASS_CACHE.keySet().iterator();
                            int removed = 0;
                            while (it.hasNext() && removed < toRemove) {
                                it.next();
                                it.remove();
                                removed++;
                            }
                        }
                    } finally {
                        EVICTING.set(false);
                    }
                }
            }
            Class<?> proxyClass = PROXY_CLASS_CACHE.computeIfAbsent(stmtClass,
                clz -> Proxy.getProxyClass(clz.getClassLoader(), clz.getInterfaces()));
            try {
                return proxyClass.getConstructor(InvocationHandler.class)
                    .newInstance(new PreparedStatementTimingHandler(stmt, sql));
            } catch (ReflectiveOperationException e) {
                // 回退到直接创建代理
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
                        String sanitizedSql = SqlSanitizer.sanitize(sql);
                        log.warn("{} SQL execution took {} ms (threshold: {} ms) - {}", SLOW_QUERY_MARKER, elapsedMs,
                            slowQueryThresholdMs, sanitizedSql);
                    }
                }
            }
            return method.invoke(target, args);
        }
    }
}
