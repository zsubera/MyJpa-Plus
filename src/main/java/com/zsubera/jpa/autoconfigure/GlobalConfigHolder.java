package com.zsubera.jpa.autoconfigure;

import org.springframework.context.ApplicationContext;

/**
 * 全局配置访问的集中入口，替代分散的静态 volatile 字段和反射调用。
 *
 * <p>
 * 此类提供线程安全的配置访问，支持三种模式：
 * <ul>
 * <li>Spring 环境（推荐）：通过 {@link #setApplicationContext(ApplicationContext)} 注册 Spring 上下文，
 *     后续可通过 Spring 容器直接获取 {@link MyJpaPlusGlobalConfig} Bean</li>
 * <li>Spring 环境（兼容）：通过 {@link #setConfig(MyJpaPlusGlobalConfig)} 直接注入 Spring 管理的 Bean</li>
 * <li>非 Spring 环境：使用默认配置</li>
 * </ul>
 *
 * <p>
 * <strong>推荐使用路径：</strong>优先从 Spring {@link ApplicationContext} 获取 Bean，
 * 回退到静态持有的配置实例，最后使用默认值。
 *
 * <p>
 * <strong>线程安全说明：</strong>所有字段均为 volatile，setter 在启动阶段调用，运行时为只读访问。
 *
 * @author myjpa-plus

 */
public final class GlobalConfigHolder {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(GlobalConfigHolder.class);

    private GlobalConfigHolder() {}

    private static final MyJpaPlusGlobalConfig DEFAULT_CONFIG = new MyJpaPlusGlobalConfig();

    /**
     * 当前全局配置引用。volatile 保证多线程可见性。
     */
    private static volatile MyJpaPlusGlobalConfig config;

    /**
     * Spring ApplicationContext 引用，用于按需查找 MyJpaPlusGlobalConfig Bean。
     */
    private static volatile ApplicationContext applicationContext;

    /**
     * 设置 Spring ApplicationContext。由 {@link MyJpaPlusAutoConfiguration} 在启动时调用。
     *
     * @param ctx Spring 应用上下文
     */
    public static void setApplicationContext(ApplicationContext ctx) {
        applicationContext = ctx;
        cachedBean = null;
        cachedBeanVerifyTime = 0;
    }

    /**
     * 设置全局配置。应在应用启动阶段调用一次。
     *
     * @param globalConfig 全局配置实例
     */
    public static void setConfig(MyJpaPlusGlobalConfig globalConfig) {
        config = globalConfig;
        cachedBean = null;
        cachedBeanVerifyTime = 0;
    }

    /**
     * 缓存的 Spring Bean 引用，避免每次 getConfig() 调用 ctx.getBean()。
     */
    private static volatile MyJpaPlusGlobalConfig cachedBean;

    /**
     * 缓存的 bean 最后验证时间戳（纳秒），用于定期重新验证缓存的有效性。
     * 每 5 分钟重新从 ApplicationContext 查找一次，防止 Context 刷新后引用过期。
     */
    private static volatile long cachedBeanVerifyTime;

    private static final long CACHE_VERIFY_INTERVAL_NANOS = java.util.concurrent.TimeUnit.MINUTES.toNanos(5);

    /** 上次查找失败的单调时间戳（纳秒），用于退避重试。使用 nanoTime 确保单调递增。 */
    private static volatile long lastLookupFailureTime;

    /**
     * 查找失败后的退避间隔（毫秒），5 秒内不重试。
     */
    private static final long LOOKUP_BACKOFF_MS = 5000;
    private static final long LOOKUP_BACKOFF_NANOS =
        java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(LOOKUP_BACKOFF_MS);

    /**
     * 获取全局配置。优先级：Spring Bean 容器 > 静态持有实例 > 默认配置。
     *
     * @return 全局配置实例，永不为 null
     */
    public static MyJpaPlusGlobalConfig getConfig() {
        // 优先从 ApplicationContext 查找（首次命中后缓存 Bean 引用，每 5 分钟重新验证）
        MyJpaPlusGlobalConfig bean = cachedBean;
        if (bean != null) {
            long now = System.nanoTime();
            if (now - cachedBeanVerifyTime < CACHE_VERIFY_INTERVAL_NANOS) {
                return bean;
            }
            // 缓存过期，尝试重新获取以验证 bean 是否仍然有效
            ApplicationContext ctx = applicationContext;
            if (ctx != null) {
                try {
                    MyJpaPlusGlobalConfig refreshed = ctx.getBean(MyJpaPlusGlobalConfig.class);
                    // 原子性地更新缓存：先写 bean，再写时间戳
                    cachedBean = refreshed;
                    cachedBeanVerifyTime = now;
                    return refreshed;
                } catch (Exception e) {
                    // bean 已不存在（Context 刷新），原子性地清除缓存
                    // 设置当前时间而非0，避免立即重试导致警告日志风暴
                    cachedBeanVerifyTime = now;
                    cachedBean = null;
                }
            }
            // 回退到过期的缓存（比返回 null 更安全），记录警告以便排查配置异常
            LOG.warn("MyJpaPlusGlobalConfig bean lookup failed (context may have been refreshed), "
                + "falling back to stale cached instance. Config changes since last refresh will not take effect "
                + "until the next successful lookup.");
            return bean;
        }
        ApplicationContext ctx = applicationContext;
        if (ctx != null) {
            // 退避重试：上次查找失败后在退避窗口内跳过重试
            long now = System.nanoTime();
            if (now - lastLookupFailureTime < LOOKUP_BACKOFF_NANOS) {
                MyJpaPlusGlobalConfig c = config;
                return c != null ? c : DEFAULT_CONFIG;
            }
            try {
                bean = ctx.getBean(MyJpaPlusGlobalConfig.class);
                cachedBean = bean;
                return bean;
            } catch (Exception e) {
                LOG.warn(
                    "Failed to lookup MyJpaPlusGlobalConfig from ApplicationContext, falling back to static config: {}",
                    e.getMessage());
                // ponytail: 仅在 bean 仍为 null 时设置退避时间，避免其他线程已成功缓存时干扰
                if (cachedBean == null) {
                    lastLookupFailureTime = now;
                }
            }
        }
        // 回退到静态持有实例
        MyJpaPlusGlobalConfig c = config;
        return c != null ? c : DEFAULT_CONFIG;
    }

    /**
     * 检查是否已配置全局配置（非默认值）。
     *
     * @return 如果已通过 {@link #setConfig(MyJpaPlusGlobalConfig)} 设置过配置返回 true
     */
    public static boolean isConfigured() {
        ApplicationContext ctx = applicationContext;
        if (ctx != null) {
            return ctx.getBeanNamesForType(MyJpaPlusGlobalConfig.class).length > 0;
        }
        return config != null;
    }

    /**
     * 重置全局配置为未配置状态（恢复为默认配置）。
     *
     * <p>
     * 此方法主要用于测试环境，在 {@code @AfterEach} 中调用以防止测试间状态泄漏。
     * 生产环境不应调用此方法。
     */
    public static void reset() {
        // 原子性地清除缓存：先清除缓存的 bean，再清除 applicationContext
        cachedBean = null;
        cachedBeanVerifyTime = 0;
        lastLookupFailureTime = 0;
        config = null;
        applicationContext = null;
    }

    // ---- 共享配置解析工具方法 ----

    /**
     * 从 GlobalConfig 解析整数配置值。优先使用 GlobalConfig 中的值，回退到默认值。
     *
     * @param getter 从 GlobalConfig 获取值的函数
     * @param defaultValue 默认值（GlobalConfig 未配置或值无效时使用）
     * @return 解析后的配置值
     */
    public static int resolveConfigValue(java.util.function.Function<MyJpaPlusGlobalConfig, Integer> getter,
        int defaultValue) {
        MyJpaPlusGlobalConfig cfg = getConfig();
        if (cfg != null) {
            Integer value = getter.apply(cfg);
            if (value != null) {
                return value;
            }
        }
        return defaultValue;
    }

    /**
     * 从 GlobalConfig 解析最大批量操作行数。
     *
     * @param localFallback 本地回退值
     * @return 解析后的配置值
     */
    public static int resolveMaxBulkOperationRows(int localFallback) {
        return resolveConfigValue(MyJpaPlusGlobalConfig::getMaxBulkOperationRows, localFallback);
    }

    public static int resolveMaxUpsertBatchIterations(int localFallback) {
        return resolveConfigValue(MyJpaPlusGlobalConfig::getMaxUpsertBatchIterations, localFallback);
    }
}
