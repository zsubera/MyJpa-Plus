package com.zsubera.jpa.converter;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.zsubera.jpa.exception.MyJpaPlusException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 加密密钥管理器，负责密钥派生、缓存、轮换和配置验证。
 *
 * <p>
 * 从 {@link EncryptConverter} 提取，遵循单一职责原则。
 * 加密算法（AES/GCM）仍由 {@link EncryptConverter} 处理。
 *
 * <p>
 * 密钥缓存使用 {@link Caffeine} 实现，内置 LRU 驱逐和并发安全。
 *
 * @see EncryptConverter
 */
final class EncryptionKeyManager {

    private static final Logger log = LoggerFactory.getLogger(EncryptionKeyManager.class);

    private static final String KEY_ENV = "MYJPA_ENCRYPT_KEY";
    private static final String KEY_PROPERTY = "myjpa.encrypt.key";
    private static final String KEY_VERSION_ENV = "MYJPA_ENCRYPT_KEY_VERSION";
    private static final String KEY_VERSION_PROPERTY = "myjpa.encrypt.key.version";
    private static final String SALT_ENV = "MYJPA_ENCRYPT_SALT";
    private static final String SALT_PROPERTY = "myjpa.encrypt.salt";
    private static final int MIN_KEY_LENGTH = 16;
    private static final int MAX_KEY_CACHE_SIZE = 16;
    private static final long KEY_VERSION_REFRESH_INTERVAL_MS_DEFAULT = 300_000L;
    private static volatile long keyVersionRefreshIntervalMs = KEY_VERSION_REFRESH_INTERVAL_MS_DEFAULT;
    private static final int PBKDF2_KEY_LENGTH = 256;
    private static final int PBKDF2_ITERATIONS_MIN = 100_000;
    private static final int PBKDF2_ITERATIONS_MAX = 10_000_000;
    /** PBKDF2 默认迭代次数。 */
    static final int PBKDF2_ITERATIONS_DEFAULT = 600_000;

    /**
     * ponytail: 通过 volatile 支持 Spring 属性注入，优先级高于系统属性/环境变量。
     * 默认值 -1 表示未配置，回退到 getPbkdf2Iterations() 的系统属性/环境变量解析。
     */
    private static volatile int configuredPbkdf2Iterations = -1;

    /** ponytail: 由自动配置通过 setSkipSaltCheck() 注入，优先级高于系统属性/环境变量。 */
    private static volatile boolean skipSaltCheck = false;

    /** 跟踪密钥是否已被使用（加密/解密操作），用于防止运行时更改PBKDF2迭代次数 */
    private static final AtomicBoolean keysInUse = new AtomicBoolean(false);

    /** 跟踪迭代次数是否已配置。首次设置后锁定，防止跨重启时变更破坏已有密文。 */
    private static volatile boolean iterationsConfigured = false;

    /**
     * Spring Environment 注入的生产环境标志。
     * 由 MyJpaPlusAutoConfiguration 在自动配置阶段设置，用于检测 application.yml 中的 prod profile。
     */
    private static volatile Boolean springProductionEnvironment = null;

    /**
     * 设置 PBKDF2 迭代次数。由自动配置类在启动时调用。
     *
     * <p>
     * 此方法在首次调用后锁定迭代次数。后续调用（即使在密钥使用之前）将抛出
     * {@link IllegalStateException}，防止跨重启时变更迭代次数导致已有密文不可解密。
     *
     * @param iterations PBKDF2 迭代次数（100,000 - 10,000,000）
     * @throws IllegalStateException 如果迭代次数已配置，不允许更改
     * @throws IllegalArgumentException 如果迭代次数超出有效范围
     */
    static void setPbkdf2Iterations(int iterations) {
        if (iterations < PBKDF2_ITERATIONS_MIN || iterations > PBKDF2_ITERATIONS_MAX) {
            throw new IllegalArgumentException("PBKDF2 iterations must be between " + PBKDF2_ITERATIONS_MIN + " and "
                + PBKDF2_ITERATIONS_MAX + ", got: " + iterations);
        }
        KEY_VERSION_LOCK.lock();
        try {
            if (keysInUse.get()) {
                if (iterationsConfigured && configuredPbkdf2Iterations == iterations) {
                    return;
                }
                throw new IllegalStateException(
                    "Cannot change PBKDF2 iterations after keys have been used for encryption/decryption. "
                        + "This would make all existing ciphertext UNDECRYPTABLE. "
                        + "To change iterations, restart the application before any encryption operations.");
            }
            if (iterationsConfigured) {
                if (configuredPbkdf2Iterations == iterations) {
                    return;
                }
                throw new IllegalStateException("PBKDF2 iterations already configured. Cannot change from "
                    + configuredPbkdf2Iterations + " to " + iterations + ". "
                    + "Changing iterations between restarts makes existing ciphertext UNDECRYPTABLE. "
                    + "To change iterations, you must migrate all encrypted data first.");
            }
            configuredPbkdf2Iterations = iterations;
            iterationsConfigured = true;
            log.info("PBKDF2 iterations configured: {}", iterations);
        } finally {
            KEY_VERSION_LOCK.unlock();
        }
    }

    static void setSkipSaltCheck(boolean skip) {
        skipSaltCheck = skip;
        log.info("PBKDF2 salt check skip set to: {}", skip);
    }

    /**
     * 重置迭代次数配置状态，仅用于测试隔离。
     * 允许后续测试类重新配置 PBKDF2 迭代次数。
     */
    static void resetIterationsConfigured() {
        KEY_VERSION_LOCK.lock();
        try {
            iterationsConfigured = false;
            keysInUse.set(false);
        } finally {
            KEY_VERSION_LOCK.unlock();
        }
    }

    /**
     * 设置 Spring Environment 检测到的生产环境标志。
     * 由 MyJpaPlusAutoConfiguration 在自动配置阶段调用，用于检测 application.yml 中的 prod profile。
     *
     * @param isProduction true 如果 Spring Environment 检测到生产环境
     */
    public static void setSpringProductionEnvironment(Boolean isProduction) {
        springProductionEnvironment = isProduction;
        if (isProduction != null) {
            log.info("Spring Environment production detection: {}", isProduction);
        }
    }

    private static int getPbkdf2Iterations() {
        int configured = configuredPbkdf2Iterations;
        if (configured > 0) {
            return configured;
        }
        String prop = System.getProperty("myjpa-plus.encrypt.pbkdf2-iterations");
        if (prop != null) {
            try {
                int val = Integer.parseInt(prop);
                if (val >= PBKDF2_ITERATIONS_MIN && val <= PBKDF2_ITERATIONS_MAX) {
                    return val;
                }
            } catch (NumberFormatException ignored) {
            }
        }
        String env = System.getenv("MYJPA_ENCRYPT_PBKDF2_ITERATIONS");
        if (env != null) {
            try {
                int val = Integer.parseInt(env);
                if (val >= PBKDF2_ITERATIONS_MIN && val <= PBKDF2_ITERATIONS_MAX) {
                    return val;
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return PBKDF2_ITERATIONS_DEFAULT;
    }

    private static final java.util.regex.Pattern MULTI_KEY_PATTERN =
        java.util.regex.Pattern.compile("^v\\d+:.*(?:,\\s*v\\d+:.*)++$");
    private static final java.util.regex.Pattern SINGLE_ENTRY_PATTERN = java.util.regex.Pattern.compile("v\\d+:.+");
    private static final String SKIP_SALT_PROPERTY = "myjpa-plus.encrypt.skip-salt-check";
    private static final String SKIP_SALT_ENV = "MYJPA_ENCRYPT_SKIP_SALT_CHECK";

    /** 按版本缓存的密钥规范。Caffeine 内置 LRU 驱逐和并发安全。 */
    private static final Cache<String, SecretKeySpec> KEY_CACHE =
        Caffeine.newBuilder().maximumSize(MAX_KEY_CACHE_SIZE).build();

    private static final ReentrantLock KEY_VERSION_LOCK = new ReentrantLock();

    /** package-private for EncryptConverter access. */
    static final AtomicBoolean KEY_VALIDATED = new AtomicBoolean(false);

    /** 密钥版本快照。使用 nanoTime 而非 currentTimeMillis 以获得单调时钟。 */
    private static volatile KeyVersionSnapshot keyVersionSnapshot = new KeyVersionSnapshot("v1", 0);

    private record KeyVersionSnapshot(String version, long refreshTimestampNanos) {
    }

    private EncryptionKeyManager() {}

    static SecretKeySpec getKeySpec(String version) {
        String cacheKey = version != null ? version : "default";
        return KEY_CACHE.get(cacheKey, k -> {
            char[] rawKey = resolveRawKey(k);
            try {
                return deriveKey(rawKey);
            } finally {
                java.util.Arrays.fill(rawKey, '\0');
            }
        });
    }

    static String getKeyVersion() {
        KeyVersionSnapshot snap = keyVersionSnapshot;
        long now = System.nanoTime();
        long refreshIntervalNanos = keyVersionRefreshIntervalMs * 1_000_000L;
        if (snap.version != null && (now - snap.refreshTimestampNanos) < refreshIntervalNanos) {
            return snap.version;
        }
        KEY_VERSION_LOCK.lock();
        try {
            snap = keyVersionSnapshot;
            now = System.nanoTime();
            if (snap.version != null && (now - snap.refreshTimestampNanos) < refreshIntervalNanos) {
                return snap.version;
            }
            String version = System.getenv(KEY_VERSION_ENV);
            if (version == null || version.isEmpty()) {
                version = System.getProperty(KEY_VERSION_PROPERTY);
            }
            String resolved = (version != null && !version.isEmpty()) ? version : "v1";
            if (snap.version != null && !snap.version.equals(resolved)) {
                log.warn(
                    "Encryption key version changed from '{}' to '{}'. "
                        + "Data encrypted with version '{}' will use the new key after cache refresh.",
                    snap.version, resolved, snap.version);
            }
            keyVersionSnapshot = new KeyVersionSnapshot(resolved, now);
            return resolved;
        } finally {
            KEY_VERSION_LOCK.unlock();
        }
    }

    /**
     * 设置密钥版本缓存刷新间隔（毫秒）。默认 300,000ms（5分钟）。
     *
     * @param intervalMs 刷新间隔（毫秒），最小 1,000ms
     */
    static void setKeyVersionRefreshInterval(long intervalMs) {
        if (intervalMs < 1_000) {
            throw new IllegalArgumentException(
                "Key version refresh interval must be at least 1000ms, got: " + intervalMs);
        }
        keyVersionRefreshIntervalMs = intervalMs;
        log.info("Key version refresh interval set to {}ms", intervalMs);
    }

    private static char[] resolveRawKey(String version) {
        String keyEnv = System.getenv(KEY_ENV);
        String keyProp = System.getProperty(KEY_PROPERTY);
        String allKeys = keyEnv;
        if (allKeys == null || allKeys.isEmpty()) {
            allKeys = keyProp;
        }
        Objects.requireNonNull(allKeys,
            "Encryption key not set. Set environment variable " + KEY_ENV + " or system property " + KEY_PROPERTY);

        boolean looksLikeMultiKey = MULTI_KEY_PATTERN.matcher(allKeys).matches();
        if (looksLikeMultiKey) {
            String[] entries = allKeys.split(",");
            List<String> invalidEntries = new ArrayList<>();
            for (String entry : entries) {
                String trimmed = entry.trim();
                if (!SINGLE_ENTRY_PATTERN.matcher(trimmed).matches()) {
                    invalidEntries.add(trimmed);
                }
            }
            if (!invalidEntries.isEmpty()) {
                throw new MyJpaPlusException("Multi-key format detected but contains invalid entries: " + invalidEntries
                    + ". Expected format: 'vN:key1,vN:key2'. "
                    + "If this is a single key containing commas, ensure it does not start with 'vN:'.");
            }
            for (String entry : entries) {
                String trimmed = entry.trim();
                int colonIdx = trimmed.indexOf(':');
                if (colonIdx > 0) {
                    String entryVersion = trimmed.substring(0, colonIdx).trim();
                    String entryKey = trimmed.substring(colonIdx + 1).trim();
                    if (entryVersion.equals(version)) {
                        validateKeyLength(entryKey);
                        return entryKey.toCharArray();
                    }
                }
            }
            List<String> availableVersions = new ArrayList<>();
            for (String entry : entries) {
                int idx = entry.indexOf(':');
                if (idx > 0) {
                    availableVersions.add(entry.substring(0, idx).trim());
                }
            }
            // 当版本为 "default"（旧密文无 vN: 前缀）时，提供明确的迁移指引
            if ("default".equals(version)) {
                throw new MyJpaPlusException(
                    "Legacy encrypted data (without vN: version prefix) cannot be decrypted with multi-key configuration. "
                        + "Available key versions: " + availableVersions + ". "
                        + "To migrate: 1) Temporarily revert to single-key configuration; "
                        + "2) Re-encrypt all data; 3) Switch to multi-key configuration. "
                        + "Or set MYJPA_ENCRYPT_KEY_VERSION to an existing version to decrypt specific data.");
            }
            throw new MyJpaPlusException("Key version '" + version + "' not found in multi-key configuration. "
                + "Available versions: " + availableVersions + ". " + "Set the correct key version via "
                + KEY_VERSION_ENV + " or " + KEY_VERSION_PROPERTY + ".");
        }

        // ponytail: 处理单条目 vN:key 格式（如 "v1:32byteKey"），保持与多密钥模式一致的密钥材料提取。
        // 若此处将整个 "v1:32byteKey" 字符串（含版本前缀）送入 PBKDF2，则后续新增第二版本（变为
        // "v1:32byteKey,v2:otherKey"）后 v1 的派生密钥变化，已有数据永久不可解密。
        int singleColonIdx = allKeys.indexOf(':');
        if (singleColonIdx > 0) {
            String prefix = allKeys.substring(0, singleColonIdx).trim();
            if (prefix.length() > 1 && prefix.charAt(0) == 'v'
                && prefix.substring(1).chars().allMatch(Character::isDigit)) {
                String entryKey = allKeys.substring(singleColonIdx + 1).trim();
                if (version != null && !prefix.equals(version) && !"default".equals(version)) {
                    logVersionMismatch(version);
                }
                validateKeyLength(entryKey);
                return entryKey.toCharArray();
            }
        }

        if (version != null && !"v1".equals(version) && !"default".equals(version)) {
            logVersionMismatch(version);
        }
        validateKeyLength(allKeys);
        return allKeys.toCharArray();
    }

    private static void validateKeyLength(String key) {
        if (key != null) {
            int byteLength = key.getBytes(StandardCharsets.UTF_8).length;
            if (byteLength < MIN_KEY_LENGTH) {
                throw new MyJpaPlusException(
                    "Encryption key must be at least " + MIN_KEY_LENGTH + " bytes (UTF-8 encoded). "
                        + "Current byte length: " + byteLength + " (character length: " + key.length() + "). "
                        + "Short keys are vulnerable to dictionary attacks even with PBKDF2 derivation.");
            }
        }
    }

    private static void logVersionMismatch(String version) {
        log.warn("Key version '{}' requested but only a single key is configured. "
            + "If using single-key mode, this warning is expected. " + "Set MYJPA_ENCRYPT_KEY_VERSION=v1 to suppress.",
            version);
    }

    private static SecretKeySpec deriveKey(char[] keyChars) {
        byte[] derived = null;
        PBEKeySpec spec = null;
        try {
            byte[] salt = getSalt();
            spec = new PBEKeySpec(keyChars, salt, getPbkdf2Iterations(), PBKDF2_KEY_LENGTH);
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            derived = factory.generateSecret(spec).getEncoded();
            keysInUse.set(true);
            return new SecretKeySpec(derived, "AES");
        } catch (GeneralSecurityException e) {
            throw new MyJpaPlusException("Failed to derive encryption key via PBKDF2", e);
        } finally {
            if (spec != null) {
                spec.clearPassword();
            }
            java.util.Arrays.fill(keyChars, '\0');
            if (derived != null) {
                java.util.Arrays.fill(derived, (byte)0);
            }
        }
    }

    /** 开发环境专用回退盐值。仅在 skipSaltCheck=true 且非生产环境时使用。 */
    private static final byte[] DEV_SALT_FALLBACK = "myjpa-plus-dev-salt-fallback".getBytes(StandardCharsets.UTF_8);

    /**
     * 获取 PBKDF2 盐值。始终从环境变量/系统属性读取，不缓存盐值本身。
     *
     * <p>
     * ponytail: 移除 CACHED_SALT_REF 缓存，修复 clearCaches() 与并发 getSalt() 之间的竞态条件。
     * 之前的实现将盐值缓存在 AtomicReference 中，clearCaches() 重置为 null 后，
     * 已缓存旧盐的线程继续使用旧盐派生密钥，新线程使用新盐，导致不同密钥共存于数据库，
     * 旧盐加密的数据永久不可解密。
     *
     * <p>
     * System.getenv() 和 System.getProperty() 性能开销极小（纳秒级），无需缓存。
     */
    private static byte[] getSalt() {
        String salt = System.getenv(SALT_ENV);
        if (salt == null || salt.isEmpty()) {
            salt = System.getProperty(SALT_PROPERTY);
        }
        if (salt != null && !salt.isEmpty()) {
            return salt.getBytes(StandardCharsets.UTF_8);
        }
        if (isSaltCheckSkipped()) {
            // 检查生产环境：优先使用 Spring Environment 注入的标志，回退到系统属性/环境变量检查
            boolean isProduction = springProductionEnvironment != null ? springProductionEnvironment
                : com.zsubera.jpa.autoconfigure.EnvironmentHelper.isProductionEnvironment();
            if (isProduction) {
                throw new MyJpaPlusException("Cannot skip PBKDF2 salt check in production environment. "
                    + "Set environment variable " + SALT_ENV + " or system property " + SALT_PROPERTY + ".");
            }
            log.warn("SECURITY: Skipping PBKDF2 salt check. Using a dev-only deterministic salt. "
                + "Encrypted data will be UNRECOVERABLE after application restart. "
                + "This is ONLY acceptable for local development with disposable data.");
            return DEV_SALT_FALLBACK.clone();
        }
        throw new MyJpaPlusException("PBKDF2 salt must be configured. " + "Set environment variable " + SALT_ENV
            + " or system property " + SALT_PROPERTY + ". " + "Salt is required for PBKDF2 key derivation security. "
            + "To skip this check (development only), set " + SKIP_SALT_PROPERTY + "=true.");
    }

    static boolean isSaltCheckSkipped() {
        if (skipSaltCheck) {
            return true;
        }
        String skipCheck = System.getProperty(SKIP_SALT_PROPERTY);
        if (!"true".equalsIgnoreCase(skipCheck)) {
            skipCheck = System.getenv(SKIP_SALT_ENV);
        }
        return "true".equalsIgnoreCase(skipCheck);
    }

    static void validateKeyConfiguration() {
        if (KEY_VALIDATED.get()) {
            return;
        }
        // ponytail: 将耗时的密钥校验移到 synchronized 块外，减少锁持有时间。
        // 仅在 synchronized 块内设置 KEY_VALIDATED 标志。
        String keyEnv = System.getenv(KEY_ENV);
        String keyProp = System.getProperty(KEY_PROPERTY);
        if ((keyEnv == null || keyEnv.isEmpty()) && (keyProp == null || keyProp.isEmpty())) {
            throw new MyJpaPlusException("Encryption key not configured. Set environment variable " + KEY_ENV
                + " or system property " + KEY_PROPERTY + " before starting the application. "
                + "EncryptConverter requires a key to function.");
        }
        String key = (keyEnv != null && !keyEnv.isEmpty()) ? keyEnv : keyProp;
        // 在锁外执行耗时的密钥长度校验
        validateKeyLengthForKey(key);
        if (keyProp != null && !keyProp.isEmpty() && (keyEnv == null || keyEnv.isEmpty())) {
            log.warn("SECURITY: Encryption key configured via system property '{}'. "
                + "System properties are JVM-wide visible and may be exposed in process listings (e.g., /proc/PID/cmdline). "
                + "Use environment variable '{}' for production environments.", KEY_PROPERTY, KEY_ENV);
        }
        synchronized (EncryptionKeyManager.class) {
            if (KEY_VALIDATED.get()) {
                return;
            }
            KEY_VALIDATED.set(true);
        }
    }

    /**
     * 在 synchronized 块外执行密钥长度校验。支持单密钥和多版本密钥格式。
     */
    private static void validateKeyLengthForKey(String key) {
        if (MULTI_KEY_PATTERN.matcher(key).matches()) {
            String[] entries = key.split(",");
            for (String entry : entries) {
                int colonIdx = entry.indexOf(':');
                if (colonIdx >= 0 && colonIdx < entry.length() - 1) {
                    String rawValue = entry.substring(colonIdx + 1).trim();
                    int byteLen = rawValue.getBytes(StandardCharsets.UTF_8).length;
                    if (byteLen < MIN_KEY_LENGTH) {
                        String version = entry.substring(0, colonIdx).trim();
                        throw new MyJpaPlusException("Encryption key for version '" + version + "' must be at least "
                            + MIN_KEY_LENGTH + " bytes (UTF-8 encoded). Current: " + byteLen + " bytes.");
                    }
                }
            }
        } else {
            // ponytail: 单条目 vN:key 格式——仅验证冒号后的密钥材料（与 resolveRawKey 一致）
            int colonIdx = key.indexOf(':');
            if (colonIdx > 0) {
                String prefix = key.substring(0, colonIdx).trim();
                if (prefix.length() > 1 && prefix.charAt(0) == 'v'
                    && prefix.substring(1).chars().allMatch(Character::isDigit)) {
                    String rawValue = key.substring(colonIdx + 1).trim();
                    int byteLen = rawValue.getBytes(StandardCharsets.UTF_8).length;
                    if (byteLen < MIN_KEY_LENGTH) {
                        throw new MyJpaPlusException("Encryption key must be at least " + MIN_KEY_LENGTH
                            + " bytes (UTF-8 encoded). " + "Current byte length: " + byteLen + " (character length: "
                            + rawValue.length() + ").");
                    }
                    return;
                }
            }
            int byteLength = key.getBytes(StandardCharsets.UTF_8).length;
            if (byteLength < MIN_KEY_LENGTH) {
                throw new MyJpaPlusException(
                    "Encryption key must be at least " + MIN_KEY_LENGTH + " bytes (UTF-8 encoded). "
                        + "Current byte length: " + byteLength + " (character length: " + key.length() + ").");
            }
        }
    }

    /**
     * 清除所有缓存的密钥、版本信息和盐值。
     *
     * <p><strong>并发注意：</strong>此方法非原子操作 — 各字段在锁外重置，
     * 仅 keyVersionSnapshot 在锁内重置。并发的 getKeySpec()/getKeyVersion() 调用
     * 可能在短暂窗口内观察到混合状态（如旧版本标签 + 新迭代次数派生的密钥）。
     * 此方法应在应用启动阶段或已知无并发加密操作时调用（如 refreshKeyVersion()）。
     */
    static void clearCaches() {
        KEY_VERSION_LOCK.lock();
        try {
            KEY_CACHE.invalidateAll();
            KEY_VALIDATED.set(false);
            // 不重置 configuredPbkdf2Iterations 和 iterationsConfigured：
            // 这些值代表派生已有密钥时使用的迭代次数，必须在应用生命周期内保持一致。
            // 重置会导致 refreshKeyVersion() 后使用不同的迭代次数派生新密钥，
            // 使之前加密的数据永久无法解密。
            keyVersionSnapshot = new KeyVersionSnapshot(null, 0);
        } finally {
            KEY_VERSION_LOCK.unlock();
        }
    }

    static void refreshKeyVersion() {
        clearCaches();
        log.info("Encryption key version cache refreshed");
    }

}
