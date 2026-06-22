package com.zsubera.jpa.autoconfigure;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * 全局配置访问的集中入口，替代分散的静态 volatile 字段和反射调用。
 *
 * <p>
 * 此类提供线程安全的配置访问，支持两种模式：
 * <ul>
 * <li>Spring 环境：通过 {@link #setConfig(MyJpaPlusGlobalConfig)} 注入 Spring 管理的 Bean</li>
 * <li>非 Spring 环境：通过 {@link #setConfig(MyJpaPlusGlobalConfig)} 手动设置，或使用默认配置</li>
 * </ul>
 *
 * <p>
 * <strong>线程安全说明：</strong>所有字段均为 volatile，setter 在启动阶段调用，运行时为只读访问。
 *
 * @author myjpa-plus

 */
public final class GlobalConfigHolder {

    private GlobalConfigHolder() {}

    /**
     * 当前全局配置引用。volatile 保证多线程可见性。
     */
    private static volatile MyJpaPlusGlobalConfig config;

    private static final MyJpaPlusGlobalConfig DEFAULT_CONFIG = new MyJpaPlusGlobalConfig();

    /**
     * 设置全局配置。应在应用启动阶段调用一次。
     *
     * @param globalConfig 全局配置实例
     */
    @SuppressFBWarnings(value = "EI_EXPOSE_STATIC_REP2",
        justification = "Spring-managed singleton intentionally stored for global access")
    public static void setConfig(MyJpaPlusGlobalConfig globalConfig) {
        config = globalConfig;
    }

    /**
     * 获取全局配置。如果未配置则返回默认配置实例。
     *
     * @return 全局配置实例，永不为 null
     */
    @SuppressFBWarnings(value = "MS_EXPOSE_REP",
        justification = "Intentionally returns shared config singleton for global access")
    public static MyJpaPlusGlobalConfig getConfig() {
        MyJpaPlusGlobalConfig c = config;
        if (c == null) {
            return DEFAULT_CONFIG;
        }
        return c;
    }

    /**
     * 检查是否已配置全局配置（非默认值）。
     *
     * @return 如果已通过 {@link #setConfig(MyJpaPlusGlobalConfig)} 设置过配置返回 true
     */
    public static boolean isConfigured() {
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
        config = null;
    }
}
