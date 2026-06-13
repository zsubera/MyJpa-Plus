package com.zsubera.jpa.repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
 * @see DefaultMyJpaRepository
 */
public final class SoftDeleteContext {

    private static final Logger log = LoggerFactory.getLogger(SoftDeleteContext.class);

    private static final ThreadLocal<Integer> IGNORE_COUNT = ThreadLocal.withInitial(() -> 0);

    /** 安全上限：超过此值认为存在泄漏，抛出异常。可通过系统属性 myjpa-plus.soft-delete.max-ignore-count 配置。 */
    private static volatile int maxIgnoreCount;

    static {
        int configured = 64;
        String prop = System.getProperty("myjpa-plus.soft-delete.max-ignore-count");
        if (prop != null) {
            try {
                int val = Integer.parseInt(prop);
                if (val > 0 && val <= 1024) {
                    configured = val;
                }
            } catch (NumberFormatException ignored) {
                // 使用默认值
            }
        }
        maxIgnoreCount = configured;
    }

    /**
     * 获取最大忽略计数。
     *
     * @return 最大忽略计数
     */
    public static int getMaxIgnoreCount() {
        return maxIgnoreCount;
    }

    /**
     * 设置最大忽略计数。
     *
     * @param count 最大忽略计数（1-1024）
     */
    public static void setMaxIgnoreCount(int count) {
        if (count > 0 && count <= 1024) {
            maxIgnoreCount = count;
        }
    }

    private SoftDeleteContext() {}

    /**
     * 检查当前线程是否应跳过软删除过滤。
     *
     * @return 如果应跳过过滤返回 true
     */
    public static boolean isIgnoreSoftDelete() {
        return IGNORE_COUNT.get() > 0;
    }

    /**
     * 获取当前线程的忽略计数。
     *
     * @return 当前线程的忽略计数
     */
    public static int getIgnoreCount() {
        return IGNORE_COUNT.get();
    }

    /**
     * 推入忽略标记（增加计数）。当方法进入 {@code @IgnoreSoftDelete} 注解的方法时调用。
     *
     * @throws IllegalStateException 如果计数超过安全上限（可能存在泄漏）
     */
    public static void pushIgnore() {
        // 简化为单次读取 - ThreadLocal.withInitial保证非null
        int count = IGNORE_COUNT.get();
        if (count >= maxIgnoreCount) {
            throw new IllegalStateException(
                "SoftDeleteContext ignore count exceeded maximum (" + maxIgnoreCount + "). Possible leak detected.");
        }
        IGNORE_COUNT.set(count + 1);
    }

    /**
     * 弹出忽略标记（减少计数）。当方法离开 {@code @IgnoreSoftDelete} 注解的方法时调用。
     *
     * <p>
     * 当计数归零时自动清除 ThreadLocal，防止内存泄漏。包含防御性检查以处理异常场景下的计数漂移。
     */
    public static void popIgnore() {
        // ThreadLocal.withInitial保证非null，所以count始终>=0。
        // null检查不可达但保留作为防御性编程。
        int count = IGNORE_COUNT.get();
        if (count <= 0) {
            // 异常场景下计数漂移的防御性清理
            log.warn("SoftDeleteContext.popIgnore() called with count={}, possible push/pop mismatch. "
                + "Cleaning up ThreadLocal to prevent memory leak.", count);
            IGNORE_COUNT.remove();
            return;
        }
        if (count - 1 <= 0) {
            IGNORE_COUNT.remove();
        } else {
            IGNORE_COUNT.set(count - 1);
        }
    }

    /**
     * 重置当前线程的忽略状态，清除 ThreadLocal 值。用于异常恢复或测试场景。
     *
     * <p>
     * 当 pushIgnore() 和 popIgnore() 不匹配时（如异常导致 Advisor 的 after 未执行）， 可调用此方法强制清理，防止线程池环境下的内存泄漏。
     */
    public static void reset() {
        IGNORE_COUNT.remove();
    }

    /**
     * 在异步边界前捕获并重置状态，返回原始忽略计数用于后续恢复。
     *
     * <p>
     * 此方法用于 {@code @Async}、{@code CompletableFuture}、响应式流等异步机制。 在异步任务提交前调用此方法捕获状态，然后在异步任务中调用
     * {@link #restoreForAsync(int)} 恢复状态。
     *
     * <p>
     * 使用示例：
     *
     * <pre>{@code
     * // 在异步边界前捕获状态
     * int captured = SoftDeleteContext.captureAndResetForAsync();
     *
     * CompletableFuture.supplyAsync(() -> {
     *     try {
     *         // 在异步任务中恢复状态
     *         SoftDeleteContext.restoreForAsync(captured);
     *         return repository.findAll();
     *     } finally {
     *         SoftDeleteContext.reset();
     *     }
     * });
     * }</pre>
     *
     * @return 当前线程的忽略计数（0 表示未忽略）
     */
    public static int captureAndResetForAsync() {
        int count = IGNORE_COUNT.get();
        if (count > 0) {
            IGNORE_COUNT.remove();
        }
        return count;
    }

    /**
     * 在异步任务中恢复之前捕获的忽略状态。
     *
     * <p>
     * 配合 {@link #captureAndResetForAsync()} 使用，确保异步任务继承父线程的软删除忽略状态。
     *
     * <p>
     * 使用示例：
     *
     * <pre>{@code
     * int captured = SoftDeleteContext.captureAndResetForAsync();
     *
     * CompletableFuture.supplyAsync(() -> {
     *     try {
     *         SoftDeleteContext.restoreForAsync(captured);
     *         return repository.findAll();
     *     } finally {
     *         SoftDeleteContext.reset();
     *     }
     * });
     * }</pre>
     *
     * @param capturedCount 之前通过 {@link #captureAndResetForAsync()} 捕获的忽略计数
     */
    public static void restoreForAsync(int capturedCount) {
        if (capturedCount > 0) {
            IGNORE_COUNT.set(capturedCount);
        }
    }

    /**
     * 在异步边界处捕获并重置当前线程的忽略状态。
     *
     * @return 之前通过 {@link #captureAndResetForAsync()} 捕获的忽略计数
     * @deprecated 此方法丢失嵌套 ignore count 信息。请使用 {@link #captureAndResetForAsync()} 保留计数。
     */
    @Deprecated
    public static boolean captureAndReset() {
        boolean ignoring = isIgnoreSoftDelete();
        if (ignoring) {
            reset();
        }
        return ignoring;
    }

}
