package com.zsubera.jpa.tenant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 租户过滤上下文，使用 ThreadLocal 计数器控制是否跳过自动过滤。
 *
 * <p>
 * 与 {@link com.zsubera.jpa.repository.SoftDeleteContext} 类似，使用计数器而非布尔标志以支持嵌套调用场景。
 *
 * <p>
 * 通常由 {@link IgnoreTenantAdvisor} 自动管理，不建议手动调用。
 *
 * <p>
 * <strong>虚拟线程说明：</strong>在 Java 21+ 虚拟线程环境中，{@code ThreadLocal} 仍然可用且功能正确。 未来版本可能考虑使用 {@code ScopedValue}（JEP
 * 462）替代，以获得更好的虚拟线程兼容性和自动清理语义。 当前实现已通过 {@link #pushIgnore()} 的安全上限和 {@link #checkHealth()} 健康检查机制防止计数泄漏。
 *
 * @see IgnoreTenantAdvisor
 * @see TenantProvider
 */
public final class TenantContext {

    private static final Logger log = LoggerFactory.getLogger(TenantContext.class);

    private static final ThreadLocal<Integer> ignoreCount = ThreadLocal.withInitial(() -> 0);

    /** P2-4: 安全上限，可通过系统属性 myjpa-plus.tenant.max-ignore-count 配置。 */
    private static volatile int maxIgnoreCount;

    static {
        int configured = 128;
        String prop = System.getProperty("myjpa-plus.tenant.max-ignore-count");
        if (prop != null) {
            try {
                int val = Integer.parseInt(prop);
                if (val > 0 && val <= 1024) {
                    configured = val;
                }
            } catch (NumberFormatException ignored) {
                // use default
            }
        }
        maxIgnoreCount = configured;
    }

    /**
     * P2-4: 获取最大忽略计数。
     *
     * @return 最大忽略计数
     */
    public static int getMaxIgnoreCount() {
        return maxIgnoreCount;
    }

    /**
     * P2-4: 设置最大忽略计数。由自动配置类调用。
     *
     * @param count 最大忽略计数（1-1024）
     */
    public static void setMaxIgnoreCount(int count) {
        if (count > 0 && count <= 1024) {
            maxIgnoreCount = count;
        }
    }

    private TenantContext() {}

    /**
     * 检查当前线程是否应跳过租户过滤。
     *
     * @return 如果应跳过过滤返回 true
     */
    public static boolean isIgnoreTenant() {
        return ignoreCount.get() > 0;
    }

    /**
     * 推入忽略标记（增加计数）。当方法进入 {@code @IgnoreTenant} 注解的方法时调用。
     *
     * @throws IllegalStateException 如果计数超过安全上限（可能存在泄漏）
     */
    public static void pushIgnore() {
        int current = ignoreCount.get();
        if (current >= maxIgnoreCount) {
            throw new IllegalStateException(
                "TenantContext ignore count exceeded maximum (" + maxIgnoreCount + "). Possible leak detected.");
        }
        ignoreCount.set(current + 1);
    }

    /**
     * 弹出忽略标记（减少计数）。当方法离开 {@code @IgnoreTenant} 注解的方法时调用。
     *
     * <p>
     * 当计数归零时自动清除 ThreadLocal，防止内存泄漏。包含防御性检查以处理异常场景下的计数漂移。
     */
    public static void popIgnore() {
        Integer count = ignoreCount.get();
        if (count == null || count <= 0) {
            // 计数器已归零或未初始化，说明存在异常场景下的计数漂移，强制清除
            ignoreCount.remove();
            return;
        }
        if (count - 1 <= 0) {
            ignoreCount.remove();
        } else {
            ignoreCount.set(count - 1);
        }
    }

    /**
     * 检查计数器健康状态。当计数异常偏高时记录警告日志。
     *
     * <p>
     * 建议在 AOP 切面的 finally 块中调用此方法，以便及时发现计数漂移问题。
     */
    public static void checkHealth() {
        int current = ignoreCount.get();
        if (current > maxIgnoreCount / 2) {
            log.warn("TenantContext ignore count ({}) is unusually high (max={}). "
                + "This may indicate a counter drift caused by exceptions in @IgnoreTenant methods. "
                + "Consider calling reset() to clear the counter.", current, maxIgnoreCount);
        }
    }

    /**
     * 重置当前线程的忽略状态，清除 ThreadLocal 值。用于异常恢复或测试场景。
     */
    public static void reset() {
        ignoreCount.remove();
    }
}
