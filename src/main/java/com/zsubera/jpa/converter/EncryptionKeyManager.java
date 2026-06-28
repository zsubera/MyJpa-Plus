package com.zsubera.jpa.converter;

import com.zsubera.jpa.exception.MyJpaPlusException;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 加密密钥管理器，负责密钥派生、缓存、轮换和配置验证。
 *
 * <p>
 * 从 {@link EncryptConverter} 提取，遵循单一职责原则。 加密算法（AES/GCM）仍由 {@link EncryptConverter} 处理。
 *
 * <p>
 * <strong>锁顺序约束：</strong>当需要同时获取 {@link #KEY_SPEC_WRITE_LOCK} 和 {@link #KEY_VERSION_LOCK}
 * 时，必须按照以下固定顺序获取以避免死锁：
 * <pre>
 * KEY_SPEC_WRITE_LOCK → KEY_VERSION_LOCK
 * </pre>
 * 当前仅在 {@link #clearCaches()} 中同时获取两把锁。如果未来新增方法需要同时获取，请遵守此顺序。
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
    private static final long KEY_VERSION_REFRESH_INTERVAL_MS = 300_000L;
    private static final int PBKDF2_KEY_LENGTH = 256;

    /**
     * ponytail: 通过 volatile 支持 Spring 属性注入，优先级高于系统属性/环境变量。
     * 默认值 -1 表示未配置，回退到 getPbkdf2Iterations() 的系统属性/环境变量解析。
     */
    private static volatile int configuredPbkdf2Iterations = -1;

    /** ponytail: 由自动配置通过 setSkipSaltCheck() 注入，优先级高于系统属性/环境变量。 */
    private static volatile boolean skipSaltCheck = false;

    /**
     * 设置 PBKDF2 迭代次数。由自动配置类在启动时调用。
     *
     * @param iterations PBKDF2 迭代次数（100,000 - 10,000,000）
     */
    static void setPbkdf2Iterations(int iterations) {
        if (iterations >= 100_000 && iterations <= 10_000_000) {
            configuredPbkdf2Iterations = iterations;
        } else {
            throw new IllegalArgumentException(
                "PBKDF2 iterations must be between 100000 and 10000000, got: " + iterations);
        }
    }

    static void setSkipSaltCheck(boolean skip) {
        skipSaltCheck = skip;
        log.info("PBKDF2 salt check skip set to: {}", skip);
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
                if (val >= 100_000 && val <= 10_000_000) {
                    return val;
                }
            } catch (NumberFormatException ignored) {
            }
        }
        String env = System.getenv("MYJPA_ENCRYPT_PBKDF2_ITERATIONS");
        if (env != null) {
            try {
                int val = Integer.parseInt(env);
                if (val >= 100_000 && val <= 10_000_000) {
                    return val;
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return 600_000;
    }

    private static final java.util.regex.Pattern MULTI_KEY_PATTERN =
        java.util.regex.Pattern.compile("^v\\d+:.*(?:,\\s*v\\d+:.*)++$");
    private static final java.util.regex.Pattern SINGLE_ENTRY_PATTERN = java.util.regex.Pattern.compile("v\\d+:.+");
    private static final java.util.regex.Pattern VERSION_PATTERN = java.util.regex.Pattern.compile("v\\d+");

    private static final String SKIP_SALT_PROPERTY = "myjpa-plus.encrypt.skip-salt-check";
    private static final String SKIP_SALT_ENV = "MYJPA_ENCRYPT_SKIP_SALT_CHECK";

    /** 按版本缓存的密钥规范。 */
    private static final ConcurrentMap<String, SecretKeySpec> KEY_CACHE = new ConcurrentHashMap<>();

    /** LRU 访问时间戳，用于淘汰最久未使用的密钥。 */
    private static final ConcurrentMap<String, Long> KEY_ACCESS_TIMES = new ConcurrentHashMap<>();

    private static final ReentrantReadWriteLock KEY_SPEC_LOCK = new ReentrantReadWriteLock();
    private static final Lock KEY_SPEC_WRITE_LOCK = KEY_SPEC_LOCK.writeLock();
    private static final ReentrantLock KEY_VERSION_LOCK = new ReentrantLock();

    /** package-private for EncryptConverter access. */
    static final AtomicBoolean KEY_VALIDATED = new AtomicBoolean(false);
    private static final java.security.SecureRandom DEV_SECURE_RANDOM = new java.security.SecureRandom();

    /** 密钥版本快照。 */
    private static volatile KeyVersionSnapshot keyVersionSnapshot = new KeyVersionSnapshot("v1", 0);

    private static final class KeyVersionSnapshot {
        final String version;
        final long refreshTimestamp;

        KeyVersionSnapshot(String version, long refreshTimestamp) {
            this.version = version;
            this.refreshTimestamp = refreshTimestamp;
        }
    }

    private EncryptionKeyManager() {}

    static SecretKeySpec getKeySpec() {
        return getKeySpec(getKeyVersion());
    }

    static SecretKeySpec getKeySpec(String version) {
        String cacheKey = version != null ? version : "default";
        SecretKeySpec existing = KEY_CACHE.get(cacheKey);
        if (existing != null) {
            KEY_ACCESS_TIMES.put(cacheKey, System.nanoTime());
            return existing;
        }
        KEY_SPEC_WRITE_LOCK.lock();
        try {
            existing = KEY_CACHE.get(cacheKey);
            if (existing != null) {
                KEY_ACCESS_TIMES.put(cacheKey, System.nanoTime());
                return existing;
            }
            if (KEY_CACHE.size() >= MAX_KEY_CACHE_SIZE) {
                evictLruKey();
            }
            char[] rawKey = resolveRawKey(cacheKey);
            try {
                SecretKeySpec derived = deriveKey(rawKey);
                KEY_CACHE.put(cacheKey, derived);
                KEY_ACCESS_TIMES.put(cacheKey, System.nanoTime());
                return derived;
            } finally {
                java.util.Arrays.fill(rawKey, '\0');
            }
        } finally {
            KEY_SPEC_WRITE_LOCK.unlock();
        }
    }

    /**
     * LRU 淘汰：移除最久未使用的密钥条目，保护当前版本和 "default" 条目不被淘汰。
     */
    private static void evictLruKey() {
        String currentVersion = getKeyVersion();
        String victim = null;
        long oldestTime = Long.MAX_VALUE;
        for (var entry : KEY_ACCESS_TIMES.entrySet()) {
            String k = entry.getKey();
            if (k.equals(currentVersion) || k.equals("default")) {
                continue;
            }
            Long time = entry.getValue();
            if (time != null && time < oldestTime) {
                oldestTime = time;
                victim = k;
            }
        }
        if (victim != null) {
            KEY_CACHE.remove(victim);
            KEY_ACCESS_TIMES.remove(victim);
        }
    }

    static String getKeyVersion() {
        KeyVersionSnapshot snap = keyVersionSnapshot;
        long now = System.currentTimeMillis();
        if (snap.version != null && (now - snap.refreshTimestamp) < KEY_VERSION_REFRESH_INTERVAL_MS) {
            return snap.version;
        }
        KEY_VERSION_LOCK.lock();
        try {
            snap = keyVersionSnapshot;
            now = System.currentTimeMillis();
            if (snap.version != null && (now - snap.refreshTimestamp) < KEY_VERSION_REFRESH_INTERVAL_MS) {
                return snap.version;
            }
            String version = System.getenv(KEY_VERSION_ENV);
            if (version == null || version.isEmpty()) {
                version = System.getProperty(KEY_VERSION_PROPERTY);
            }
            String resolved = (version != null && !version.isEmpty()) ? version : "v1";
            keyVersionSnapshot = new KeyVersionSnapshot(resolved, now);
            return resolved;
        } finally {
            KEY_VERSION_LOCK.unlock();
        }
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
            throw new MyJpaPlusException("Key version '" + version + "' not found in multi-key configuration. "
                + "Available versions: " + availableVersions + ". " + "Set the correct key version via "
                + KEY_VERSION_ENV + " or " + KEY_VERSION_PROPERTY + ".");
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

    private static final AtomicReference<byte[]> cachedSaltRef = new AtomicReference<>();

    private static byte[] getSalt() {
        byte[] cached = cachedSaltRef.get();
        if (cached != null) {
            return cached.clone();
        }
        String salt = System.getenv(SALT_ENV);
        if (salt == null || salt.isEmpty()) {
            salt = System.getProperty(SALT_PROPERTY);
        }
        if (salt != null && !salt.isEmpty()) {
            byte[] bytes = salt.getBytes(StandardCharsets.UTF_8);
            // ponytail: AtomicReference.compareAndSet 保证单次计算，避免 TOCTOU 双重复计算
            cachedSaltRef.compareAndSet(null, bytes);
            return cachedSaltRef.get().clone();
        }
        if (isSaltCheckSkipped()) {
            if (com.zsubera.jpa.autoconfigure.EnvironmentHelper.isProductionEnvironment()) {
                throw new MyJpaPlusException("Cannot skip PBKDF2 salt check in production environment. "
                    + "Set environment variable " + SALT_ENV + " or system property " + SALT_PROPERTY + ".");
            }
            log.warn("SECURITY: Skipping PBKDF2 salt check. Using a dev-only deterministic salt derived from "
                + "application name. Encrypted data will be UNRECOVERABLE after application restart. "
                + "This is ONLY acceptable for local development with disposable data.");
            byte[] devSalt = "myjpa-plus-dev-salt-fallback-2024".getBytes(StandardCharsets.UTF_8);
            cachedSaltRef.compareAndSet(null, devSalt);
            return cachedSaltRef.get().clone();
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

    @SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "Internal class not exposed to external callers")
    static void validateKeyConfiguration() {
        if (KEY_VALIDATED.get()) {
            return;
        }
        synchronized (EncryptionKeyManager.class) {
            if (KEY_VALIDATED.get()) {
                return;
            }
            String keyEnv = System.getenv(KEY_ENV);
            String keyProp = System.getProperty(KEY_PROPERTY);
            if ((keyEnv == null || keyEnv.isEmpty()) && (keyProp == null || keyProp.isEmpty())) {
                throw new MyJpaPlusException("Encryption key not configured. Set environment variable " + KEY_ENV
                    + " or system property " + KEY_PROPERTY + " before starting the application. "
                    + "EncryptConverter requires a key to function.");
            } else {
                String key = (keyEnv != null && !keyEnv.isEmpty()) ? keyEnv : keyProp;
                if (key != null) {
                    int byteLength = key.getBytes(StandardCharsets.UTF_8).length;
                    if (byteLength < MIN_KEY_LENGTH) {
                        throw new MyJpaPlusException(
                            "Encryption key must be at least " + MIN_KEY_LENGTH + " bytes (UTF-8 encoded). "
                                + "Current byte length: " + byteLength + " (character length: " + key.length() + ").");
                    }
                }
                if (keyProp != null && !keyProp.isEmpty() && (keyEnv == null || keyEnv.isEmpty())) {
                    log.warn("SECURITY: Encryption key configured via system property '{}'. "
                        + "System properties are JVM-wide visible and may be exposed in process listings (e.g., /proc/PID/cmdline). "
                        + "Use environment variable '{}' for production environments.", KEY_PROPERTY, KEY_ENV);
                }
            }
            KEY_VALIDATED.set(true);
        }
    }

    static void clearCaches() {
        KEY_SPEC_WRITE_LOCK.lock();
        try {
            KEY_VERSION_LOCK.lock();
            try {
                keyVersionSnapshot = new KeyVersionSnapshot(null, 0);
            } finally {
                KEY_VERSION_LOCK.unlock();
            }
            KEY_CACHE.clear();
            KEY_ACCESS_TIMES.clear();
            KEY_VALIDATED.set(false);
            // ponytail: 不对 salt 做清零，因为：
            // 1. salt 来自环境变量/系统属性，不属于敏感秘密
            // 2. 并发 getSalt() 可能已持有数组引用，清零导致返回全零 salt 破坏 PBKDF2 派生
            cachedSaltRef.set(null);
        } finally {
            KEY_SPEC_WRITE_LOCK.unlock();
        }
    }

    static void refreshKeyVersion() {
        clearCaches();
        log.info("Encryption key version cache refreshed");
    }

}
