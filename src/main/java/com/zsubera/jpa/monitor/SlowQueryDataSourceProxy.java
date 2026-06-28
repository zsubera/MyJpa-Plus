package com.zsubera.jpa.monitor;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
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

    private static final int MAX_PROXY_CLASS_CACHE_SIZE = 4096;
    private static final com.zsubera.jpa.util.SampledEvictionCache<Class<?>, Class<?>> PROXY_CLASS_CACHE =
        new com.zsubera.jpa.util.SampledEvictionCache<>(MAX_PROXY_CLASS_CACHE_SIZE, 0.75, 100, 64);

    /**
     * ponytail: 慢查询监听器列表，支持多个监听器注册。通过 {@link #addListener} 注册。
     */
    private static final java.util.concurrent.CopyOnWriteArrayList<SlowQueryListener> LISTENERS =
        new java.util.concurrent.CopyOnWriteArrayList<>();

    /**
     * 注册慢查询监听器。多个监听器可同时注册，按注册顺序调用。
     *
     * @param listener 要注册的监听器
     * @throws IllegalArgumentException 如果 listener 为 null
     */
    public static void addListener(SlowQueryListener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("listener must not be null");
        }
        LISTENERS.add(listener);
    }

    /**
     * 移除已注册的慢查询监听器。
     *
     * @param listener 要移除的监听器
     */
    public static void removeListener(SlowQueryListener listener) {
        LISTENERS.remove(listener);
    }

    /**
     * 通知所有已注册的监听器。内部方法，由计时委托调用。
     */
    static void notifyListeners(String sql, long elapsedMs, long thresholdMs) {
        for (SlowQueryListener listener : LISTENERS) {
            try {
                listener.onSlowQuery(sql, elapsedMs, thresholdMs);
            } catch (Exception e) {
                log.warn("SlowQueryListener threw exception", e);
            }
        }
    }

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

    @SuppressFBWarnings(value = "EI_EXPOSE_REP",
        justification = "Proxy class cache is intentionally shared across all proxy instances for performance")
    static com.zsubera.jpa.util.SampledEvictionCache<Class<?>, Class<?>> getProxyClassCache() {
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
            String methodName = method.getName();
            if ("prepareStatement".equals(methodName) || "prepareCall".equals(methodName)
                || "createStatement".equals(methodName)) {
                String sql = extractSql(args);
                Object stmt = method.invoke(target, args);
                if ("prepareCall".equals(methodName) || "createStatement".equals(methodName)) {
                    return wrapStatement(stmt, sql);
                }
                return wrapPreparedStatement(stmt, sql);
            }
            return method.invoke(target, args);
        }

        private String extractSql(Object[] args) {
            if (args != null && args.length > 0 && args[0] instanceof String s) {
                return s;
            }
            return "unknown";
        }

        private Object wrapPreparedStatement(Object stmt, String sql) {
            Class<?> stmtClass = stmt.getClass();
            if (!hasInterfaces(stmtClass)) {
                log.debug("PreparedStatement class {} implements no interfaces, skipping proxy wrapping",
                    stmtClass.getName());
                return stmt;
            }
            return createProxy(stmtClass, stmt, new PreparedStatementTimingHandler(stmt, sql, slowQueryThresholdMs));
        }

        private Object wrapStatement(Object stmt, String sql) {
            Class<?> stmtClass = stmt.getClass();
            if (!hasInterfaces(stmtClass)) {
                log.debug("Statement class {} implements no interfaces, skipping proxy wrapping", stmtClass.getName());
                return stmt;
            }
            return createProxy(stmtClass, stmt, new StatementTimingHandler(stmt, sql, slowQueryThresholdMs));
        }

        private static boolean hasInterfaces(Class<?> clazz) {
            while (clazz != null && clazz != Object.class) {
                if (clazz.getInterfaces().length > 0)
                    return true;
                clazz = clazz.getSuperclass();
            }
            return false;
        }

        private Object createProxy(Class<?> stmtClass, Object stmt, InvocationHandler handler) {
            Class<?> proxyClass = PROXY_CLASS_CACHE.computeIfAbsent(stmtClass,
                clz -> Proxy.getProxyClass(clz.getClassLoader(), clz.getInterfaces()));
            try {
                return proxyClass.getConstructor(InvocationHandler.class).newInstance(handler);
            } catch (ReflectiveOperationException e) {
                return Proxy.newProxyInstance(stmtClass.getClassLoader(), stmtClass.getInterfaces(), handler);
            }
        }
    }

    private static class PreparedStatementTimingHandler implements InvocationHandler {

        private final Object target;
        private final String sql;
        private final long slowQueryThresholdMs;
        private int batchCount;

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
            if ("addBatch".equals(name)) {
                Object result = method.invoke(target, args);
                batchCount++;
                return result;
            }
            if ("clearBatch".equals(name)) {
                Object result = method.invoke(target, args);
                batchCount = 0;
                return result;
            }
            if ("executeBatch".equals(name)) {
                String batchSql =
                    batchCount > 0 ? sql + " [batch of " + batchCount + " statements]" : sql + " [batch (0)]";
                return StatementTimingDelegate.invokeTimed(target, batchSql, slowQueryThresholdMs, method, args);
            }
            return StatementTimingDelegate.invokeTimed(target, sql, slowQueryThresholdMs, method, args);
        }
    }

    /**
     * Micrometer 指标记录工具类。从 PreparedStatementTimingHandler 提取，
     * 供 PreparedStatementTimingHandler 和 StatementTimingHandler 共用。
     */
    private static final class MicrometerMetrics {

        private record ReflectCache(Object meterRegistry, Class<?> meterRegistryClass,
            Class<?> distributionSummaryClass, Class<?> tagsClass, Method tagsOfMethod, Method summaryBuilderMethod,
            Method summaryDescMethod, Method summaryRegisterMethod, Method summaryRecordMethod) {
        }

        private static volatile ReflectCache micrometerCache;
        private static volatile Object cachedSummary;

        static void recordQueryDuration(String operationType, long elapsedMs) {
            ReflectCache cache = micrometerCache;
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
                    synchronized (MicrometerMetrics.class) {
                        summary = cachedSummary;
                        if (summary == null) {
                            summary = cache.summaryBuilderMethod().invoke(null, "myjpa.query.duration");
                            summary = cache.summaryDescMethod().invoke(summary, "JPA query execution duration");
                            summary = cache.summaryRegisterMethod().invoke(summary, cache.meterRegistry());
                            cachedSummary = summary;
                        }
                    }
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
                micrometerCache = new ReflectCache(registry, registryClass, distSummaryClass, tagsClass,
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

    /**
     * Statement 计时处理器（用于 createStatement() 和 prepareCall() 返回的 Statement 对象）。
     * 与 PreparedStatementTimingHandler 共享相同计时逻辑和 Micrometer 指标记录。
     */
    private static class StatementTimingHandler implements InvocationHandler {

        private String sql;
        private final Object target;
        private final long slowQueryThresholdMs;
        private int batchCount;

        StatementTimingHandler(Object target, String sql, long slowQueryThresholdMs) {
            this.target = target;
            this.sql = sql;
            this.slowQueryThresholdMs = slowQueryThresholdMs;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String name = method.getName();
            // capture SQL from execute(String), executeQuery(String), executeUpdate(String)
            // ponytail: createStatement() doesn't take SQL, so we capture it here
            if (("execute".equals(name) || "executeQuery".equals(name) || "executeUpdate".equals(name)) && args != null
                && args.length > 0 && args[0] instanceof String s) {
                this.sql = s;
            }
            if ("addBatch".equals(name) && args != null && args.length > 0 && args[0] instanceof String s) {
                this.sql = s;
                Object result = method.invoke(target, args);
                batchCount++;
                return result;
            }
            if ("addBatch".equals(name)) {
                Object result = method.invoke(target, args);
                batchCount++;
                return result;
            }
            if ("clearBatch".equals(name)) {
                Object result = method.invoke(target, args);
                batchCount = 0;
                return result;
            }
            if ("executeBatch".equals(name)) {
                String batchSql =
                    batchCount > 0 ? sql + " [batch of " + batchCount + " statements]" : sql + " [batch (0)]";
                return StatementTimingDelegate.invokeTimed(target, batchSql, slowQueryThresholdMs, method, args);
            }
            return StatementTimingDelegate.invokeTimed(target, sql, slowQueryThresholdMs, method, args);
        }
    }

    /**
     * 共享的计时委托逻辑，供 PreparedStatementTimingHandler 和 StatementTimingHandler 复用。
     */
    private static class StatementTimingDelegate {

        private static final String SLOW_QUERY_MARKER = "[SLOW QUERY]";

        static Object invokeTimed(Object target, String sql, long slowQueryThresholdMs, Method method, Object[] args)
            throws Throwable {
            String name = method.getName();
            if ("executeQuery".equals(name) || "executeUpdate".equals(name) || "execute".equals(name)
                || "executeBatch".equals(name)) {
                long start = System.nanoTime();
                try {
                    return method.invoke(target, args);
                } finally {
                    long elapsedMs = java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
                    MicrometerMetrics.recordQueryDuration(name, elapsedMs);
                    if (elapsedMs >= slowQueryThresholdMs) {
                        String sanitizedSql = SqlSanitizer.sanitize(sql);
                        log.warn("{} {} execution took {} ms (threshold: {} ms) - {}", SLOW_QUERY_MARKER, name,
                            elapsedMs, slowQueryThresholdMs, sanitizedSql);
                        notifyListeners(sanitizedSql, elapsedMs, slowQueryThresholdMs);
                    }
                }
            }
            return method.invoke(target, args);
        }
    }
}
