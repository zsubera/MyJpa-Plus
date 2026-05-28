package com.zsubera.jpa.repository;

/**
 * 软删除过滤上下文，使用 ThreadLocal 标志控制是否跳过自动过滤。
 *
 * <p>
 * 此类替代了原先基于 {@code Thread.currentThread().getStackTrace()} 的栈遍历方案，消除了每次查询 0.1-1ms 的性能开销和深层调用栈注入的安全风险。
 *
 * <p>
 * 通常由 {@link IgnoreSoftDeleteAdvisor} 自动管理，不建议手动调用。
 *
 * @see IgnoreSoftDeleteAdvisor
 * @see SoftDeleteJpaRepository
 */
public final class SoftDeleteContext {

    private static final ThreadLocal<Boolean> ignoreSoftDeleteFlag = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private SoftDeleteContext() {}

    /**
     * 检查当前线程是否应跳过软删除过滤。
     *
     * @return 如果应跳过过滤返回 true
     */
    public static boolean isIgnoreSoftDelete() {
        return ignoreSoftDeleteFlag.get();
    }

    /**
     * 设置当前线程的软删除过滤跳过标志。
     *
     * @param ignore 是否跳过过滤
     */
    public static void setIgnoreSoftDelete(boolean ignore) {
        ignoreSoftDeleteFlag.set(ignore);
    }

    /**
     * 清除当前线程的标志，防止 ThreadLocal 泄漏。应在方法执行完成后调用。
     */
    public static void clear() {
        ignoreSoftDeleteFlag.remove();
    }
}
