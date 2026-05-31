package com.zsubera.jpa.tenant;

/**
 * 租户过滤上下文，使用 ThreadLocal 计数器控制是否跳过自动过滤。
 *
 * <p>
 * 与 {@link com.zsubera.jpa.repository.SoftDeleteContext} 类似，使用计数器而非布尔标志以支持嵌套调用场景。
 *
 * <p>
 * 通常由 {@link IgnoreTenantAdvisor} 自动管理，不建议手动调用。
 *
 * @see IgnoreTenantAdvisor
 * @see TenantProvider
 */
public final class TenantContext {

    private static final ThreadLocal<Integer> ignoreCount = ThreadLocal.withInitial(() -> 0);

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
     */
    public static void pushIgnore() {
        ignoreCount.set(ignoreCount.get() + 1);
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
     * 重置当前线程的忽略状态，清除 ThreadLocal 值。用于异常恢复或测试场景。
     */
    public static void reset() {
        ignoreCount.remove();
    }
}
