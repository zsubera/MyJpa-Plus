package com.zsubera.jpa.autoconfigure;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
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

    private GlobalConfigHolder() {}

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
    @SuppressFBWarnings(value = "EI_EXPOSE_STATIC_REP2",
        justification = "Spring-managed singleton intentionally stored for global access")
    public static void setApplicationContext(ApplicationContext ctx) {
        applicationContext = ctx;
        cachedBean = null;
    }

    /**
     * 设置全局配置。应在应用启动阶段调用一次。
     *
     * @param globalConfig 全局配置实例
     */
    @SuppressFBWarnings(value = "EI_EXPOSE_STATIC_REP2",
        justification = "Spring-managed singleton intentionally stored for global access")
    public static void setConfig(MyJpaPlusGlobalConfig globalConfig) {
        config = globalConfig;
        cachedBean = null;
    }

    /**
     * 缓存的 Spring Bean 引用，避免每次 getConfig() 调用 ctx.getBean()。
     */
    private static volatile MyJpaPlusGlobalConfig cachedBean;

    /**
     * 获取全局配置。优先级：Spring Bean 容器 > 静态持有实例 > 默认配置。
     *
     * @return 全局配置实例，永不为 null
     */
    @SuppressFBWarnings(value = "MS_EXPOSE_REP",
        justification = "Intentionally returns shared config singleton for global access")
    public static MyJpaPlusGlobalConfig getConfig() {
        // 优先从 ApplicationContext 查找（首次命中后缓存 Bean 引用）
        MyJpaPlusGlobalConfig bean = cachedBean;
        if (bean != null) {
            return bean;
        }
        ApplicationContext ctx = applicationContext;
        if (ctx != null) {
            try {
                bean = ctx.getBean(MyJpaPlusGlobalConfig.class);
                cachedBean = bean;
                return bean;
            } catch (Exception ignored) {
                cachedBean = null;
            }
        }
        // 回退到静态持有实例
        MyJpaPlusGlobalConfig c = config;
        return c != null ? c : new MyJpaPlusGlobalConfig();
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
        config = null;
        applicationContext = null;
        cachedBean = null;
    }
}
