package com.zsubera.jpa.autoconfigure;

import java.util.Locale;

/**
 * 环境检测工具类，统一管理生产环境判断和系统属性/环境变量读取逻辑。
 *
 * <p>
 * 消除 {@link EncryptConverter} 和 {@link MyJpaPlusAutoConfiguration} 中重复的
 * {@code isProductionEnvironment()} 和 {@code isProdProfile()} 实现。
 *
 * <p>
 * 生产环境检测优先级：
 * <ol>
 *   <li>显式配置 {@code myjpa-plus.encrypt.require-salt=true} 或 {@code MYJPA_ENCRYPT_REQUIRE_SALT=true}</li>
 *   <li>系统属性 {@code spring.profiles.active} 包含 prod / production</li>
 *   <li>环境变量 {@code SPRING_PROFILES_ACTIVE} 或 {@code SPRING_PROFILES} 包含 prod / production</li>
 * </ol>
 */
public final class EnvironmentHelper {

    private static final String REQUIRE_SALT_PROPERTY = "myjpa-plus.encrypt.require-salt";

    private EnvironmentHelper() {}

    /**
     * 判断当前是否为生产环境。
     *
     * @return {@code true} 如果检测到生产环境配置
     */
    public static boolean isProductionEnvironment() {
        String requireSalt = System.getProperty(REQUIRE_SALT_PROPERTY);
        if ("true".equalsIgnoreCase(requireSalt)) {
            return true;
        }
        requireSalt = System.getenv("MYJPA_ENCRYPT_REQUIRE_SALT");
        if ("true".equalsIgnoreCase(requireSalt)) {
            return true;
        }
        String profile = System.getProperty("spring.profiles.active", "");
        if (isProdProfile(profile)) {
            return true;
        }
        profile = System.getenv("SPRING_PROFILES_ACTIVE");
        if (profile != null && isProdProfile(profile)) {
            return true;
        }
        profile = System.getenv("SPRING_PROFILES");
        return profile != null && isProdProfile(profile);
    }

    /**
     * 判断给定的 profile 字符串是否包含生产环境标识。
     * 使用 {@code startsWith} 匹配 production-us、prod-v2 等变体，
     * 但不匹配 reproduction。
     */
    public static boolean isProdProfile(String profile) {
        if (profile == null || profile.isEmpty()) {
            return false;
        }
        String lower = profile.toLowerCase(Locale.ROOT);
        for (String p : lower.split("[,\\s]+")) {
            String trimmed = p.trim();
            if ("prod".equals(trimmed) || "production".equals(trimmed)
                || trimmed.startsWith("prod-") || trimmed.startsWith("production-")) {
                return true;
            }
        }
        return false;
    }

    /**
     * 读取环境变量，如果未设置则回退到系统属性。
     *
     * @param envName  环境变量名
     * @param propName 系统属性名
     * @return 值，如果两者均未设置则返回 null
     */
    public static String getEnvOrProperty(String envName, String propName) {
        String value = System.getenv(envName);
        if (value != null && !value.isEmpty()) {
            return value;
        }
        return System.getProperty(propName);
    }

    /**
     * 读取环境变量或系统属性，如果两者均未设置则返回默认值。
     *
     * @param envName      环境变量名
     * @param propName     系统属性名
     * @param defaultValue 默认值
     * @return 值，如果两者均未设置则返回 defaultValue
     */
    public static String getEnvOrProperty(String envName, String propName, String defaultValue) {
        String value = getEnvOrProperty(envName, propName);
        return value != null ? value : defaultValue;
    }
}
