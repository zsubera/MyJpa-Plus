package com.zsubera.jpa.template;

import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 深度分页保护工具类，提供阈值警告和硬限制检查。
 *
 * <p>
 * 被 {@link MyJpaTemplate} 和 {@link com.zsubera.jpa.projection.ProjectionSpec} 共享，
 * 避免分页保护逻辑的重复实现。
 */
public final class DeepPaginationGuard {

    private static final Logger log = LoggerFactory.getLogger(DeepPaginationGuard.class);

    /** 深度分页警告日志的最小间隔（毫秒），防止日志泛滥。 */
    static final long WARN_INTERVAL_MS = MyJpaTemplate.DEEP_PAGINATION_WARN_INTERVAL_MS;

    private DeepPaginationGuard() {}

    /**
     * 检查深度分页，超过阈值时记录警告（限流），超过硬限制时抛出异常。
     *
     * @param offset 当前分页偏移量
     * @param threshold 警告阈值
     * @param hardLimit 硬限制（-1 表示禁用）
     * @param lastWarnTime 上次警告时间戳（用于限流）
     */
    public static void check(long offset, int threshold, int hardLimit, AtomicLong lastWarnTime) {
        if (offset >= threshold && threshold > 0) {
            long now = System.currentTimeMillis();
            long lastWarn = lastWarnTime.get();
            if (now - lastWarn > WARN_INTERVAL_MS && lastWarnTime.compareAndSet(lastWarn, now)) {
                log.warn("Deep pagination detected (offset={}). This may cause slow queries. "
                    + "Consider using keyset pagination for better performance.", offset);
            }
        }
        if (hardLimit > 0 && offset > hardLimit) {
            throw new IllegalArgumentException("Pagination offset (" + offset + ") exceeds the configured hard limit ("
                + hardLimit
                + "). Use keyset pagination for better performance, or adjust myjpa-plus.query.deep-pagination-offset-limit.");
        }
    }
}
