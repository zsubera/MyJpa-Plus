package com.zsubera.jpa.util;

import jakarta.persistence.Query;

public final class QueryTimeoutHelper {

    private static volatile int defaultTimeoutMs = 30_000;

    private QueryTimeoutHelper() {}

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

    public static int getDefaultTimeoutMs() {
        return defaultTimeoutMs;
    }

    public static boolean isTimeoutConfigured() {
        return defaultTimeoutMs > 0;
    }

    public static void applyTimeout(Query query) {
        if (defaultTimeoutMs > 0) {
            // JPA 规范定义 jakarta.persistence.query.timeout 为毫秒单位。
            // Hibernate 6+ 遵循规范使用毫秒；Hibernate 5 及部分旧版本可能解释为秒。
            // 此处按规范传毫秒值，如使用旧版 Hibernate 需自行适配。
            query.setHint("jakarta.persistence.query.timeout", defaultTimeoutMs);
        }
    }
}
