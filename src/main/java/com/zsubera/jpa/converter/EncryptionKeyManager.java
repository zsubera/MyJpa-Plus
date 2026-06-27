package com.zsubera.jpa.converter;

import com.zsubera.jpa.exception.MyJpaPlusException;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
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

    private static int getPbkdf2Iterations() {
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
        java.util.regex.Pattern.compile(".*v\\d+:.*,.+");
    private static final java.util.regex.Pattern SINGLE_ENTRY_PATTERN =
        java.util.regex.Pattern.compile("v\\d+:.+");
    private static final java.util.regex.Pattern VERSION_PATTERN =
        java.util.regex.Pattern.compile("v\\d+");

    private static final String SKIP_SALT_PROPERTY = "myjpa-plus.encrypt.skip-salt-check";
    private static final String SKIP_SALT_ENV = "MYJPA_ENCRYPT_SKIP_SALT_CHECK";

    /** 按版本缓存的密钥规范。 */
    private static final ConcurrentMap<String, SecretKeySpec> KEY_CACHE = new ConcurrentHashMap<>();

    private static final ReentrantReadWriteLock KEY_SPEC_LOCK = new ReentrantReadWriteLock();
    private static final Lock KEY_SPEC_WRITE_LOCK = KEY_SPEC_LOCK.writeLock();
    private static final ReentrantLock KEY_VERSION_LOCK = new ReentrantLock();

    /** package-private for EncryptConverter access. */
    static final AtomicBoolean KEY_VALIDATED = new AtomicBoolean(false);
    private static final AtomicBoolean DEV_SALT_WARNING_LOGGED = new AtomicBoolean(false);
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
            return existing;
        }
        KEY_SPEC_WRITE_LOCK.lock();
        try {
            existing = KEY_CACHE.get(cacheKey);
            if (existing != null) {
                return existing;
            }
            if (KEY_CACHE.size() >= MAX_KEY_CACHE_SIZE) {
                throw new MyJpaPlusException("Encryption key cache is full (" + MAX_KEY_CACHE_SIZE + " entries). "
                    + "Clear cache or increase MAX_KEY_CACHE_SIZE if this is legitimate key rotation.");
            }
            String rawKey = resolveRawKey(cacheKey);
            SecretKeySpec derived = deriveKey(rawKey);
            KEY_CACHE.put(cacheKey, derived);
            return derived;
        } finally {
            KEY_SPEC_WRITE_LOCK.unlock();
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

    private static String resolveRawKey(String version) {
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
                        return entryKey;
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
        return allKeys;
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

    private static SecretKeySpec deriveKey(String rawKeyMaterial) {
        char[] keyChars = rawKeyMaterial.toCharArray();
        byte[] derived = null;
        try {
            byte[] salt = getSalt();
            PBEKeySpec spec = new PBEKeySpec(keyChars, salt, getPbkdf2Iterations(), PBKDF2_KEY_LENGTH);
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            derived = factory.generateSecret(spec).getEncoded();
            return new SecretKeySpec(derived, "AES");
        } catch (GeneralSecurityException e) {
            throw new MyJpaPlusException("Failed to derive encryption key via PBKDF2", e);
        } finally {
            java.util.Arrays.fill(keyChars, '\0');
            if (derived != null) {
                java.util.Arrays.fill(derived, (byte) 0);
            }
        }
    }

    private static byte[] getSalt() {
        String salt = System.getenv(SALT_ENV);
        if (salt == null || salt.isEmpty()) {
            salt = System.getProperty(SALT_PROPERTY);
        }
        if (salt != null && !salt.isEmpty()) {
            return salt.getBytes(StandardCharsets.UTF_8);
        }
        if (isSaltCheckSkipped()) {
            if (com.zsubera.jpa.autoconfigure.EnvironmentHelper.isProductionEnvironment()) {
                throw new IllegalStateException("Cannot skip PBKDF2 salt check in production environment. "
                    + "Set environment variable " + SALT_ENV + " or system property " + SALT_PROPERTY + ".");
            }
            log.warn("SECURITY: PBKDF2 salt check is skipped via configuration. "
                + "A random salt will be generated per JVM startup — data encrypted in this session "
                + "WILL NOT BE RECOVERABLE after restart. "
                + "Set environment variable {} or system property {} for persistent salt.", SALT_ENV, SALT_PROPERTY);
            byte[] randomSalt = new byte[16];
            DEV_SECURE_RANDOM.nextBytes(randomSalt);
            return randomSalt;
        }
        throw new IllegalStateException("PBKDF2 salt must be configured. " + "Set environment variable " + SALT_ENV
            + " or system property " + SALT_PROPERTY + ". " + "Salt is required for PBKDF2 key derivation security. "
            + "To skip this check (development only), set " + SKIP_SALT_PROPERTY + "=true.");
    }

    static boolean isSaltCheckSkipped() {
        String skipCheck = System.getProperty(SKIP_SALT_PROPERTY);
        if (!"true".equalsIgnoreCase(skipCheck)) {
            skipCheck = System.getenv(SKIP_SALT_ENV);
        }
        return "true".equalsIgnoreCase(skipCheck);
    }

    @SuppressFBWarnings(value = "EI_EXPOSE_REP2",
        justification = "Internal class not exposed to external callers")
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
                throw new IllegalStateException("Encryption key not configured. Set environment variable " + KEY_ENV
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
            KEY_CACHE.clear();
            keyVersionSnapshot = new KeyVersionSnapshot("v1", 0);
        } finally {
            KEY_SPEC_WRITE_LOCK.unlock();
        }
        KEY_VALIDATED.set(false);
        DEV_SALT_WARNING_LOGGED.set(false);
    }

    static void refreshKeyVersion() {
        KEY_SPEC_WRITE_LOCK.lock();
        try {
            KEY_CACHE.clear();
            keyVersionSnapshot = new KeyVersionSnapshot(null, 0);
        } finally {
            KEY_SPEC_WRITE_LOCK.unlock();
        }
        KEY_VALIDATED.set(false);
        log.info("Encryption key version cache refreshed");
    }

    static AtomicBoolean getDevSaltWarningLogged() {
        return DEV_SALT_WARNING_LOGGED;
    }
}
