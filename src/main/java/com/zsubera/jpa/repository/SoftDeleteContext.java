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
 * <h3>虚拟线程（Virtual Threads）兼容性</h3>
 * <p>
 * 此类的 ThreadLocal 与 Java 21+ 虚拟线程完全兼容。每个虚拟线程拥有独立的 ThreadLocal 映射，
 * 因此 {@link #pushIgnore()} / {@link #popIgnore()} 在虚拟线程中行为与平台线程一致。
 * <p>
 * 唯一需要注意的是跨虚拟线程的状态传递：如果需要在虚拟线程之间共享软删除忽略状态，
 * 请使用 {@link #captureAndResetForAsync()} 和 {@link #restoreForAsync(int)} 进行显式传递，
 * 或使用推荐的 {@link #withIgnore(Runnable)} 便捷方法自动管理生命周期。
 *
 * <h3>推荐用法</h3>
 * <p>
 * 对于手动管理软删除忽略状态的场景，推荐使用 {@link #withIgnore(Runnable)} 或
 * {@link #withIgnore(java.util.function.Supplier)} 以确保异常安全的自动清理：
 * <pre>{@code
 * SoftDeleteContext.withIgnore(() -> {
 *     // 在此范围内软删除过滤被跳过
 *     repository.findAll(); // 返回包含已删除记录的结果
 * }); // 自动恢复，即使发生异常
 * }</pre>
 *
 * <p>
 * 通常由 {@link IgnoreSoftDeleteAdvisor} 自动管理，不建议手动调用 push/pop。
 *
 * @see IgnoreSoftDeleteAdvisor
 * @see DefaultMyJpaRepository
 */
public final class SoftDeleteContext {

    private static final Logger log = LoggerFactory.getLogger(SoftDeleteContext.class);

    private static final ThreadLocal<int[]> IGNORE_COUNT = ThreadLocal.withInitial(() -> new int[] {0});

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

    private SoftDeleteContext() {}

    /**
     * 使用 try-with-resources 在范围内跳过软删除过滤。自动管理 push/pop 生命周期，异常安全。
     *
     * <pre>{@code
     * try (var ignored = SoftDeleteContext.ignoreScope()) {
     *     repository.findAll();
     * }
     * }</pre>
     *
     * @return 可关闭的 AutoCloseable，关闭时自动调用 popIgnore()
     */
    public static AutoCloseable ignoreScope() {
        pushIgnore();
        return new AutoCloseable() {
            @Override
            public void close() {
                popIgnore();
            }
        };
    }

    /**
     * 在软删除过滤被跳过的范围内执行操作。自动管理 push/pop 生命周期，异常安全。
     *
     * <p>
     * 推荐替代直接调用 {@link #pushIgnore()} / {@link #popIgnore()}，尤其在虚拟线程和异常场景下更安全：
     *
     * <pre>{@code
     * SoftDeleteContext.withIgnore(() -> {
     *     repository.findAll(); // 返回包含已删除记录的结果
     * }); // 自动恢复
     * }</pre>
     *
     * @param action 要执行的操作
     */
    public static void withIgnore(Runnable action) {
        pushIgnore();
        try {
            action.run();
        } finally {
            popIgnore();
        }
    }

    /**
     * 在软删除过滤被跳过的范围内执行 Supplier。自动管理 push/pop 生命周期，异常安全。
     *
     * <p>
     * 推荐替代直接调用 {@link #pushIgnore()} / {@link #popIgnore()}，尤其在虚拟线程和异常场景下更安全：
     *
     * <pre>{@code
     * List<T> result = SoftDeleteContext.withIgnore(() -> repository.findAll());
     * }</pre>
     *
     * @param supplier 要执行的 Supplier
     * @param <R> 返回类型
     * @return Supplier 的返回结果
     */
    public static <R> R withIgnore(java.util.function.Supplier<R> supplier) {
        pushIgnore();
        try {
            return supplier.get();
        } finally {
            popIgnore();
        }
    }

    /**
     * 检查当前线程是否应跳过软删除过滤。
     *
     * @return 如果应跳过过滤返回 true
     */
    public static boolean isIgnoreSoftDelete() {
        return IGNORE_COUNT.get()[0] > 0;
    }

    /**
     * 获取当前线程的忽略计数。
     *
     * @return 当前线程的忽略计数
     */
    public static int getIgnoreCount() {
        return IGNORE_COUNT.get()[0];
    }

    /**
     * 推入忽略标记（增加计数）。当方法进入 {@code @IgnoreSoftDelete} 注解的方法时调用。
     *
     * <p>
     * 当计数超过安全上限时，自动重置 ThreadLocal 并抛出异常。这防止了因 push/pop 不匹配导致的
     * 软删除过滤被永久跳过的安全风险。
     *
     * @throws IllegalStateException 如果计数超过安全上限（可能存在泄漏，ThreadLocal 已自动重置）
     */
    public static void pushIgnore() {
        int[] countHolder = IGNORE_COUNT.get();
        int count = countHolder[0];
        int limit = maxIgnoreCount;
        if (count >= limit) {
            log.error("SoftDeleteContext pushIgnore() exceeded safety ceiling ({}). "
                + "Auto-resetting to prevent soft-delete filter bypass. " + "Check for pushIgnore/popIgnore imbalance.",
                limit);
            countHolder[0] = 0;
            throw new IllegalStateException("SoftDeleteContext ignore count exceeded maximum (" + limit + "). "
                + "ThreadLocal has been auto-reset to prevent soft-delete filter bypass. "
                + "Ensure every pushIgnore() has a matching popIgnore() in a finally block.");
        }
        countHolder[0] = count + 1;
    }

    /**
     * 弹出忽略标记（减少计数）。当方法离开 {@code @IgnoreSoftDelete} 注解的方法时调用。
     *
     * <p>
     * 当计数归零时自动清除 ThreadLocal，防止内存泄漏。包含防御性检查以处理异常场景下的计数漂移。
     */
    public static void popIgnore() {
        int[] countHolder = IGNORE_COUNT.get();
        int count = countHolder[0];
        if (count <= 0) {
            log.warn("SoftDeleteContext.popIgnore() called with count={}, possible push/pop mismatch. "
                + "Cleaning up ThreadLocal to prevent memory leak.", count);
            IGNORE_COUNT.remove();
            return;
        }
        if (count == 1) {
            IGNORE_COUNT.remove();
        } else {
            countHolder[0] = count - 1;
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
        int[] countHolder = IGNORE_COUNT.get();
        int count = countHolder[0];
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
            IGNORE_COUNT.set(new int[] {capturedCount});
        }
    }

}
