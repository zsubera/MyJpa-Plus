package com.zsubera.jpa.repository;

/**
 * 软删除过滤上下文，使用 ThreadLocal 计数器控制是否跳过自动过滤。
 *
 * <p>
 * 此类替代了原先基于 {@code Thread.currentThread().getStackTrace()} 的栈遍历方案，消除了每次查询 0.1-1ms 的性能开销和深层调用栈注入的安全风险。
 *
 * <p>
 * 使用计数器而非布尔标志，以支持嵌套调用场景。当外层方法和内层方法都标注了 {@code @IgnoreSoftDelete} 时， 外层方法结束不会错误地清除内层的忽略状态。
 *
 * <p>
 * 通常由 {@link IgnoreSoftDeleteAdvisor} 自动管理，不建议手动调用。
 *
 * @see IgnoreSoftDeleteAdvisor
 * @see SoftDeleteJpaRepository
 */
public final class SoftDeleteContext {

    private static final ThreadLocal<Integer> ignoreCount = ThreadLocal.withInitial(() -> 0);

    private SoftDeleteContext() {}

    /**
     * 检查当前线程是否应跳过软删除过滤。
     *
     * @return 如果应跳过过滤返回 true
     */
    public static boolean isIgnoreSoftDelete() {
        return ignoreCount.get() > 0;
    }

    /**
     * 推入忽略标记（增加计数）。当方法进入 {@code @IgnoreSoftDelete} 注解的方法时调用。
     */
    public static void pushIgnore() {
        ignoreCount.set(ignoreCount.get() + 1);
    }

    /**
     * 弹出忽略标记（减少计数）。当方法离开 {@code @IgnoreSoftDelete} 注解的方法时调用。
     *
     * <p>
     * 当计数归零时自动清除 ThreadLocal，防止内存泄漏。包含防御性检查以处理异常场景下的计数漂移。
     */
    public static void popIgnore() {
        Integer count = ignoreCount.get();
        if (count == null || count <= 0) {
            // 防御性清理，避免计数漂移
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
     * 清除当前线程的标志，防止 ThreadLocal 泄漏。应在方法执行完成后调用。
     *
     * @deprecated 自 1.1.0 起废弃，请使用 {@link #popIgnore()} 以支持嵌套调用场景。
     */
    @Deprecated(since = "1.1.0")
    public static void clear() {
        ignoreCount.remove();
    }

    /**
     * @deprecated 自 1.1.0 起废弃，请使用 {@link #pushIgnore()} 和 {@link #popIgnore()}。
     */
    @Deprecated(since = "1.1.0")
    public static void setIgnoreSoftDelete(boolean ignore) {
        if (ignore) {
            pushIgnore();
        } else {
            popIgnore();
        }
    }
}
