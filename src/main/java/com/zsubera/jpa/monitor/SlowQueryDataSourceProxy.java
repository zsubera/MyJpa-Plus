package com.zsubera.jpa.monitor;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 慢查询 DataSource 代理，不依赖任何特定 JPA 实现（Hibernate、EclipseLink 等）。
 *
 * <p>
 * 使用标准 JDBC {@link Proxy} 拦截 {@code prepareStatement()} 返回的 {@code PreparedStatement}，
 * 在 {@code executeQuery()}、{@code executeUpdate()} 和 {@code execute()} 调用前后测量耗时，
 * 超过阈值时记录警告日志。同时支持 Micrometer 指标导出（如果类路径上存在）。
 *
 * <p>
 * <strong>与 {@link SqlSlowQueryInterceptor} 的关系：</strong>
 * <ul>
 * <li>{@code SqlSlowQueryInterceptor} 实现 Hibernate 的 {@code StatementInspector} 接口，用于 SQL 日志拦截
 * <li>本类提供独立的 JDBC DataSource 代理，不依赖 Hibernate
 * <li>在 Hibernate 环境中，两者可同时使用（通过 {@link SlowQueryDataSourceProxyPostProcessor} 自动装配）
 * <li>在非 Hibernate 环境中，仅本类提供慢查询监控功能
 * </ul>
 *
 * <p>
 * 使用方式：
 *
 * <pre>{@code
 * // 手动包装 DataSource
 * DataSource original = ...;
 * DataSource proxied = SlowQueryDataSourceProxy.wrap(original, 1000);
 *
 * // 或通过 Spring Boot 自动配置
 * myjpa-plus:
 *   monitoring:
 *     enabled: true
 *     slow-query-threshold-ms: 1000
 * }</pre>
 *

 */
public final class SlowQueryDataSourceProxy {

    private static final Logger log = LoggerFactory.getLogger(SlowQueryDataSourceProxy.class);

    private static final int MAX_PROXY_CLASS_CACHE_SIZE = 512;
    private static final ConcurrentMap<Class<?>, Class<?>> PROXY_CLASS_CACHE = new ConcurrentHashMap<>();

    private SlowQueryDataSourceProxy() {}

    /**
     * 将给定的 {@link DataSource} 包装为带有慢查询计时功能的代理。
     *
     * @param dataSource 原始 DataSource
     * @param slowQueryThresholdMs 慢查询阈值（毫秒）
     * @return 代理 DataSource
     * @throws IllegalArgumentException 如果 dataSource 为 null 或 slowQueryThresholdMs 小于等于 0
     */
    public static DataSource wrap(DataSource dataSource, long slowQueryThresholdMs) {
        if (dataSource == null) {
            throw new IllegalArgumentException("dataSource must not be null");
        }
        if (slowQueryThresholdMs <= 0) {
            throw new IllegalArgumentException("slowQueryThresholdMs must be positive, got: " + slowQueryThresholdMs);
        }
        return (DataSource)Proxy.newProxyInstance(DataSource.class.getClassLoader(), new Class<?>[] {DataSource.class},
            new DataSourceProxyHandler(dataSource, slowQueryThresholdMs));
    }

    /**
     * 检查给定的 {@link DataSource} 是否已被本类包装。
     *
     * @param ds 要检查的 DataSource
     * @return 如果已被包装返回 true
     */
    public static boolean isWrapped(DataSource ds) {
        if (ds != null && Proxy.isProxyClass(ds.getClass())) {
            InvocationHandler handler = Proxy.getInvocationHandler(ds);
            return handler instanceof DataSourceProxyHandler;
        }
        return false;
    }

    private static void evictCacheIfNeeded() {
        if (PROXY_CLASS_CACHE.size() <= MAX_PROXY_CLASS_CACHE_SIZE) {
            return;
        }
        int toRemove = Math.max(1, PROXY_CLASS_CACHE.size() / 4);
        int removed = 0;
        java.util.Iterator<Class<?>> it = PROXY_CLASS_CACHE.keySet().iterator();
        while (it.hasNext() && removed < toRemove) {
            it.next();
            it.remove();
            removed++;
        }
    }

    @SuppressFBWarnings(value = "EI_EXPOSE_REP",
        justification = "Proxy class cache is intentionally shared across all proxy instances for performance")
    static ConcurrentMap<Class<?>, Class<?>> getProxyClassCache() {
        return PROXY_CLASS_CACHE;
    }

    static class DataSourceProxyHandler implements InvocationHandler {

        private final DataSource target;
        private final long slowQueryThresholdMs;

        @SuppressFBWarnings(value = "EI_EXPOSE_REP2",
            justification = "Internal proxy handler stores target DataSource reference for delegation")
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
            if (stmtClass.getInterfaces().length == 0) {
                log.debug("PreparedStatement class {} implements no interfaces, skipping proxy wrapping",
                    stmtClass.getName());
                return stmt;
            }

            Class<?> proxyClass = PROXY_CLASS_CACHE.get(stmtClass);
            if (proxyClass == null) {
                if (PROXY_CLASS_CACHE.size() >= MAX_PROXY_CLASS_CACHE_SIZE) {
                    evictCacheIfNeeded();
                }
                proxyClass = PROXY_CLASS_CACHE.computeIfAbsent(stmtClass,
                    clz -> Proxy.getProxyClass(clz.getClassLoader(), clz.getInterfaces()));
            }
            try {
                return proxyClass.getConstructor(InvocationHandler.class)
                    .newInstance(new PreparedStatementTimingHandler(stmt, sql, slowQueryThresholdMs));
            } catch (ReflectiveOperationException e) {
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

        private record MicrometerReflectCache(Object meterRegistry, Class<?> meterRegistryClass,
            Class<?> distributionSummaryClass, Class<?> tagsClass, Method tagsOfMethod, Method summaryBuilderMethod,
            Method summaryDescMethod, Method summaryRegisterMethod, Method summaryRecordMethod) {
        }

        private static volatile MicrometerReflectCache micrometerCache;
        private static volatile Object cachedSummary;

        @SuppressFBWarnings(value = "EI_EXPOSE_REP2",
            justification = "Internal timing handler stores JDBC proxy target for delegation")
        PreparedStatementTimingHandler(Object target, String sql, long slowQueryThresholdMs) {
            this.target = target;
            this.sql = sql;
            this.slowQueryThresholdMs = slowQueryThresholdMs;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String name = method.getName();
            if ("executeQuery".equals(name) || "executeUpdate".equals(name) || "execute".equals(name)
                || "executeBatch".equals(name)) {
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

        private void recordMetrics(String operationType, long elapsedMs) {
            MicrometerReflectCache cache = micrometerCache;
            if (cache == null) {
                checkMicrometerAvailable();
                cache = micrometerCache;
            }
            if (cache == null) {
                return;
            }
            try {
                Object tags = cache.tagsOfMethod().invoke(null, "type", operationType);

                Object summary = cachedSummary;
                if (summary == null) {
                    summary = cache.summaryBuilderMethod().invoke(null, "myjpa.query.duration");
                    summary = cache.summaryDescMethod().invoke(summary, "JPA query execution duration");
                    summary = cache.summaryRegisterMethod().invoke(summary, cache.meterRegistry());
                    cachedSummary = summary;
                }

                cache.summaryRecordMethod().invoke(summary, tags, (double)elapsedMs);
            } catch (ReflectiveOperationException e) {
                log.debug("Failed to record Micrometer metrics", e);
            }
        }

        private static synchronized void checkMicrometerAvailable() {
            if (micrometerCache != null) {
                return;
            }
            try {
                Class<?> registryClass = Class.forName("io.micrometer.core.instrument.MeterRegistry");
                Class<?> globalRegistryClass = Class.forName("io.micrometer.core.instrument.Metrics");
                Object registry = globalRegistryClass.getMethod("globalRegistry").invoke(null);
                if (registry == null) {
                    return;
                }
                Class<?> tagsClass = Class.forName("io.micrometer.core.instrument.Tags");
                Class<?> distSummaryClass = Class.forName("io.micrometer.core.instrument.DistributionSummary");
                micrometerCache = new MicrometerReflectCache(registry, registryClass, distSummaryClass, tagsClass,
                    tagsClass.getMethod("of", String.class, String.class),
                    distSummaryClass.getMethod("builder", String.class),
                    distSummaryClass.getMethod("description", String.class),
                    distSummaryClass.getMethod("register", registryClass),
                    distSummaryClass.getMethod("record", tagsClass, double.class));
                log.info("Micrometer detected — SQL query metrics will be recorded to myjpa.query.duration");
            } catch (ReflectiveOperationException e) {
                log.trace("Micrometer not available on classpath", e);
            }
        }
    }
}
