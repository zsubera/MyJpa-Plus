package com.zsubera.jpa.autoconfigure;

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
 * @since 2.1.0
 */
public final class GlobalConfigHolder {

    private GlobalConfigHolder() {}

    /**
     * 当前全局配置引用。volatile 保证多线程可见性。
     */
    private static volatile MyJpaPlusGlobalConfig config;

    /**
     * 设置全局配置。应在应用启动阶段调用一次。
     *
     * @param globalConfig 全局配置实例
     */
    public static void setConfig(MyJpaPlusGlobalConfig globalConfig) {
        config = globalConfig;
    }

    /**
     * 获取全局配置。如果未配置则返回默认配置实例。
     *
     * @return 全局配置实例，永不为 null
     */
    public static MyJpaPlusGlobalConfig getConfig() {
        MyJpaPlusGlobalConfig c = config;
        if (c == null) {
            c = new MyJpaPlusGlobalConfig();
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
}
