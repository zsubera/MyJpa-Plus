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
     * 将给定的 {@link DataSource} 包装为带有慢查询计时功能的代理。
     *
     * @param dataSource 原始 DataSource
     * @return 代理 DataSource
     */
    public DataSource wrapDataSource(DataSource dataSource) {
        return (DataSource)Proxy.newProxyInstance(DataSource.class.getClassLoader(), new Class<?>[] {DataSource.class},
            new DataSourceProxyHandler(dataSource, slowQueryThresholdMs));
    }

    public static class DataSourceProxyHandler implements InvocationHandler {

        private final DataSource target;
        private final long slowQueryThresholdMs;

        @SuppressFBWarnings("EI_EXPOSE_REP2")
        DataSourceProxyHandler(DataSource target, long slowQueryThresholdMs) {
            this.target = target;
            this.slowQueryThresholdMs = slowQueryThresholdMs;
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

            // 减少高并发场景下的锁竞争
            if (PROXY_CLASS_CACHE.size() > MAX_PROXY_CLASS_CACHE_SIZE) {
                synchronized (PROXY_CLASS_CACHE) {
                    // double-check：进入锁后再次检查，避免重复驱逐
                    if (PROXY_CLASS_CACHE.size() > MAX_PROXY_CLASS_CACHE_SIZE) {
                        int currentSize = PROXY_CLASS_CACHE.size();
                        int evictCount = Math.min(64, currentSize / 4);
                        int removed = 0;
                        java.util.Iterator<Class<?>> it = PROXY_CLASS_CACHE.keySet().iterator();
                        while (it.hasNext() && removed < evictCount) {
                            it.next();
                            it.remove();
                            removed++;
                        }
                        log.debug("Evicted {} proxy class cache entries (was size {})", removed, currentSize);
                    }
                }
            }
            Class<?> proxyClass = PROXY_CLASS_CACHE.computeIfAbsent(stmtClass,
                clz -> Proxy.getProxyClass(clz.getClassLoader(), clz.getInterfaces()));
            try {
                return proxyClass.getConstructor(InvocationHandler.class)
                    .newInstance(new PreparedStatementTimingHandler(stmt, sql, slowQueryThresholdMs));
            } catch (ReflectiveOperationException e) {
                // 回退到直接创建代理
                return Proxy.newProxyInstance(stmtClass.getClassLoader(), stmtClass.getInterfaces(),
                    new PreparedStatementTimingHandler(stmt, sql, slowQueryThresholdMs));
            }
        }
    }

    private static class PreparedStatementTimingHandler implements InvocationHandler {

        private static final String SLOW_QUERY_MARKER = "[SLOW QUERY]";
        private final Object target;
        private final String sql;
        private final long slowQueryThresholdMs;

        /** 可选的 Micrometer MeterRegistry，通过反射延迟加载以避免强依赖。 */
        private static volatile Object meterRegistry;
        private static volatile boolean micrometerAvailable;
        private static volatile boolean micrometerChecked;

        @SuppressFBWarnings("EI_EXPOSE_REP2")
        PreparedStatementTimingHandler(Object target, String sql, long slowQueryThresholdMs) {
            this.target = target;
            this.sql = sql;
            this.slowQueryThresholdMs = slowQueryThresholdMs;
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
                    recordMetrics(name, elapsedMs);
                    if (elapsedMs >= slowQueryThresholdMs) {
                        String sanitizedSql = SqlSanitizer.sanitize(sql);
                        log.warn("{} SQL execution took {} ms (threshold: {} ms) - {}", SLOW_QUERY_MARKER, elapsedMs,
                            slowQueryThresholdMs, sanitizedSql);
                    }
                }
            }
            return method.invoke(target, args);
        }

        /**
         * 记录查询执行指标到 Micrometer（如果可用）。
         */
        private void recordMetrics(String operationType, long elapsedMs) {
            if (!micrometerChecked) {
                checkMicrometerAvailable();
            }
            if (!micrometerAvailable || meterRegistry == null) {
                return;
            }
            try {
                Class<?> meterRegistryClass = Class.forName("io.micrometer.core.instrument.MeterRegistry");
                Class<?> distributionSummaryClass = Class.forName("io.micrometer.core.instrument.DistributionSummary");
                Class<?> tagsClass = Class.forName("io.micrometer.core.instrument.Tags");

                Object tags = tagsClass.getMethod("of", String.class, String.class).invoke(null, "type", operationType);

                Object summary =
                    distributionSummaryClass.getMethod("builder", String.class).invoke(null, "myjpa.query.duration");
                summary = distributionSummaryClass.getMethod("description", String.class).invoke(summary,
                    "JPA query execution duration");
                summary =
                    distributionSummaryClass.getMethod("register", meterRegistryClass).invoke(summary, meterRegistry);

                distributionSummaryClass.getMethod("record", tagsClass, double.class).invoke(summary, tags,
                    (double)elapsedMs);
            } catch (Exception ignored) {
                // Micrometer 调用失败时静默忽略，不影响查询执行
            }
        }

        private static synchronized void checkMicrometerAvailable() {
            if (micrometerChecked) {
                return;
            }
            micrometerChecked = true;
            try {
                Class<?> registryClass = Class.forName("io.micrometer.core.instrument.MeterRegistry");
                Class<?> globalRegistryClass = Class.forName("io.micrometer.core.instrument.Metrics");
                meterRegistry = globalRegistryClass.getMethod("globalRegistry").invoke(null);
                micrometerAvailable = meterRegistry != null;
                if (micrometerAvailable) {
                    log.info("Micrometer detected — SQL query metrics will be recorded to myjpa.query.duration");
                }
            } catch (Exception ignored) {
                // Micrometer 不在类路径上，静默降级
            }
        }
    }
}
