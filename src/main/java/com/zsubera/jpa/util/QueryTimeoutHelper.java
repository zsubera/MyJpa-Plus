package com.zsubera.jpa.util;

import jakarta.persistence.Query;

/**
 * 查询超时辅助工具类，为所有 JPA 查询统一设置超时时间。
 *
 * <p>
 * 通过 {@link #setDefaultTimeoutSeconds(int)} 配置全局默认超时（单位：秒）， 所有通过 {@link #applyTimeout(Query)} 设置超时的查询将自动应用此限制。
 *
 * <p>
 * 超时值通过 JPA hint {@code jakarta.persistence.query.timeout} 传递，单位为毫秒。 Hibernate 6+ 遵循 JPA 规范使用毫秒；Hibernate 5 及部分旧版本可能解释为秒。
 *
 * <p>
 * 设置为 {@code -1} 表示禁用超时限制。
 *
 * @author myjpa-plus
 * @since 1.2.0
 */
public final class QueryTimeoutHelper {

    private static volatile int defaultTimeoutMs = 30_000;

    private QueryTimeoutHelper() {}

    /**
     * 设置全局默认查询超时时间。
     *
     * @param seconds 超时时间（秒），必须为正数或 -1（禁用）
     * @throws IllegalArgumentException 如果 seconds 不合法（非正数且非 -1，或溢出）
     */
    public static void setDefaultTimeoutSeconds(int seconds) {
        if (seconds <= 0 && seconds != -1) {
            throw new IllegalArgumentException(
                "defaultTimeoutSeconds must be positive or -1 (disabled), but got: " + seconds);
        }
        if (seconds > Integer.MAX_VALUE / 1000) {
            throw new IllegalArgumentException("defaultTimeoutSeconds too large for millisecond conversion: " + seconds
                + " (max " + (Integer.MAX_VALUE / 1000) + ")");
        }
        defaultTimeoutMs = seconds > 0 ? seconds * 1000 : -1;
    }

    /**
     * 获取当前全局默认超时时间（毫秒）。
     *
     * @return 超时时间（毫秒），-1 表示禁用
     */
    public static int getDefaultTimeoutMs() {
        return defaultTimeoutMs;
    }

    /**
     * 检查是否已配置有效的超时时间。
     *
     * @return 如果超时已配置（&gt; 0）返回 true
     */
    public static boolean isTimeoutConfigured() {
        return defaultTimeoutMs > 0;
    }

    /**
     * 为 JPA 查询应用全局默认超时设置。
     *
     * <p>
     * 如果超时未配置（值为 -1 或 ≤ 0），此方法为 no-op。
     *
     * @param query 要设置超时的 JPA 查询
     */
    public static void applyTimeout(Query query) {
        if (defaultTimeoutMs > 0) {
            // JPA 规范定义 jakarta.persistence.query.timeout 为毫秒单位。
            // Hibernate 6+ 遵循规范使用毫秒；Hibernate 5 及部分旧版本可能解释为秒。
            // 此处按规范传毫秒值，如使用旧版 Hibernate 需自行适配。
            query.setHint("jakarta.persistence.query.timeout", defaultTimeoutMs);
        }
    }
}
