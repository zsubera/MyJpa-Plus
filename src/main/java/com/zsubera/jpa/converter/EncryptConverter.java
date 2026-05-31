package com.zsubera.jpa.converter;

import com.zsubera.jpa.exception.MyJpaPlusException;
import jakarta.persistence.AttributeConverter;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

public class EncryptConverter implements AttributeConverter<String, String> {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;
    private static final String KEY_ENV = "MYJPA_ENCRYPT_KEY";
    private static final String KEY_PROPERTY = "myjpa.encrypt.key";
    private static final String KEY_VERSION_ENV = "MYJPA_ENCRYPT_KEY_VERSION";
    private static final String KEY_VERSION_PROPERTY = "myjpa.encrypt.key.version";
    private static final String SALT_ENV = "MYJPA_ENCRYPT_SALT";
    private static final String SALT_PROPERTY = "myjpa.encrypt.salt";
    private static final String STRICT_MODE_PROPERTY = "myjpa-plus.encrypt.strict-mode";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int PBKDF2_ITERATIONS = 600_000;
    private static final int PBKDF2_KEY_LENGTH = 256;
    private static final System.Logger LOG = System.getLogger("com.zsubera.jpa.converter.EncryptConverter");

    /** P0-3: Minimum key length in characters to prevent weak key attacks. */
    private static final int MIN_KEY_LENGTH = 8;

    /** 严格模式开关，通过系统属性控制。默认为 false。 */
    private static final boolean STRICT_MODE = Boolean.parseBoolean(System.getProperty(STRICT_MODE_PROPERTY, "false"));

    /** Cached key specs by version to avoid repeated environment variable reads and KDF derivation. */
    private static final ConcurrentMap<String, SecretKeySpec> KEY_CACHE = new ConcurrentHashMap<>();

    /** Cached key version to avoid repeated environment variable reads. */
    private static volatile String cachedKeyVersion;

    /** 上次刷新密钥版本的时间戳。 */
    private static volatile long lastKeyVersionRefresh;

    /** 密钥版本缓存刷新间隔（毫秒），默认 5 分钟。 */
    private static final long KEY_VERSION_REFRESH_INTERVAL_MS = 300_000L;

    /** P1-1: Thread-safe salt cache to replace System.setProperty() usage. */
    private static final ConcurrentMap<String, byte[]> SALT_CACHE = new ConcurrentHashMap<>();

    /**
     * 清除所有缓存的密钥和版本信息。仅用于测试环境。
     */
    static void clearCacheForTesting() {
        KEY_CACHE.clear();
        cachedKeyVersion = null;
        lastKeyVersionRefresh = 0;
    }

    /**
     * 强制刷新密钥版本缓存。在密钥轮换后调用此方法使新密钥立即生效。
     *
     * <p>
     * 此方法线程安全，可在运行时调用以支持在线密钥轮换。
     */
    public static void refreshKeyVersion() {
        cachedKeyVersion = null;
        KEY_CACHE.clear();
        SALT_CACHE.clear();
        lastKeyVersionRefresh = System.currentTimeMillis();
        LOG.log(System.Logger.Level.INFO, "Encryption key version cache and salt cache refreshed");
    }

    /**
     * 获取当前密钥版本标识。结果缓存以避免每次操作读取环境变量。
     *
     * <p>
     * 缓存每 {@link #KEY_VERSION_REFRESH_INTERVAL_MS} 自动刷新一次，确保密钥轮换后新版本能在合理时间内生效。
     *
     * @return 密钥版本标识
     */
    private static String getKeyVersion() {
        long now = System.currentTimeMillis();
        String version = cachedKeyVersion;
        if (version != null && (now - lastKeyVersionRefresh) < KEY_VERSION_REFRESH_INTERVAL_MS) {
            return version;
        }
        // 定期刷新或首次加载
        synchronized (EncryptConverter.class) {
            version = cachedKeyVersion;
            if (version != null && (now - lastKeyVersionRefresh) < KEY_VERSION_REFRESH_INTERVAL_MS) {
                return version;
            }
            version = System.getenv(KEY_VERSION_ENV);
            if (version == null || version.isEmpty()) {
                version = System.getProperty(KEY_VERSION_PROPERTY);
            }
            cachedKeyVersion = (version != null && !version.isEmpty()) ? version : "v1";
            lastKeyVersionRefresh = now;
            return cachedKeyVersion;
        }
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null) {
            return null;
        }
        try {
            SecretKeySpec keySpec = getKeySpec();
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            byte[] iv = new byte[GCM_IV_LENGTH];
            SECURE_RANDOM.nextBytes(iv);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] encrypted = cipher.doFinal(attribute.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);
            String version = getKeyVersion();
            return version + ":" + Base64.getEncoder().encodeToString(combined);
        } catch (GeneralSecurityException e) {
            LOG.log(System.Logger.Level.ERROR, "Encryption failed", e);
            throw new MyJpaPlusException("Failed to encrypt value. Check encryption key configuration.", e);
        }
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        try {
            // 支持带版本前缀和不带版本前缀两种格式
            String base64Data;
            String version = null;
            if (dbData.contains(":")) {
                // 带版本前缀格式: "v1:base64data"
                int colonIndex = dbData.indexOf(':');
                version = dbData.substring(0, colonIndex);
                base64Data = dbData.substring(colonIndex + 1);
            } else {
                // 兼容旧格式（无版本前缀）
                base64Data = dbData;
            }
            byte[] combined;
            try {
                combined = Base64.getDecoder().decode(base64Data);
            } catch (IllegalArgumentException e) {
                LOG.log(System.Logger.Level.ERROR, "Invalid Base64 data in encrypted field", e);
                throw new MyJpaPlusException("Failed to decrypt value: invalid Base64 encoding.", e);
            }
            if (combined.length < GCM_IV_LENGTH) {
                throw new MyJpaPlusException("Invalid encrypted data: decoded length (" + combined.length
                    + ") is less than minimum required (" + GCM_IV_LENGTH + " bytes for IV). "
                    + "Data may be corrupted or not encrypted with this converter.");
            }
            byte[] iv = new byte[GCM_IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH);
            byte[] encrypted = new byte[combined.length - GCM_IV_LENGTH];
            System.arraycopy(combined, GCM_IV_LENGTH, encrypted, 0, encrypted.length);
            SecretKeySpec keySpec = getKeySpec(version);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] decrypted = cipher.doFinal(encrypted);
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            LOG.log(System.Logger.Level.ERROR, "Decryption failed", e);
            throw new MyJpaPlusException("Failed to decrypt value. Check encryption key configuration.", e);
        }
    }

    /**
     * 获取当前版本的密钥规范。结果缓存以避免重复 KDF 派生。
     */
    private static SecretKeySpec getKeySpec() {
        return getKeySpec(getKeyVersion());
    }

    /**
     * 获取指定版本的密钥规范。支持多版本密钥配置（格式：v1:key1,v2:key2）。 使用 PBKDF2WithHmacSHA256 进行密钥派生。
     */
    private static SecretKeySpec getKeySpec(String version) {
        return KEY_CACHE.computeIfAbsent(version != null ? version : "default", v -> {
            String rawKey = resolveRawKey(v);
            return deriveKey(rawKey);
        });
    }

    /**
     * 从环境变量解析原始密钥。支持多版本格式（v1:key1,v2:key2）。
     */
    private static String resolveRawKey(String version) {
        String keyEnv = System.getenv(KEY_ENV);
        String keyProp = System.getProperty(KEY_PROPERTY);

        // 尝试多版本格式: "v1:key1,v2:key2"
        String allKeys = keyEnv;
        if (allKeys == null || allKeys.isEmpty()) {
            allKeys = keyProp;
        }
        Objects.requireNonNull(allKeys,
            "Encryption key not set. Set environment variable " + KEY_ENV + " or system property " + KEY_PROPERTY);

        // 解析多版本密钥
        if (allKeys.contains(":") && allKeys.contains(",")) {
            String[] entries = allKeys.split(",");
            for (String entry : entries) {
                entry = entry.trim();
                int colonIdx = entry.indexOf(':');
                if (colonIdx > 0) {
                    String entryVersion = entry.substring(0, colonIdx).trim();
                    String entryKey = entry.substring(colonIdx + 1).trim();
                    if (entryVersion.equals(version)) {
                        validateKeyLength(entryKey);
                        return entryKey;
                    }
                }
            }
        }

        // 单密钥模式：如果指定了版本但没有找到对应密钥，使用主密钥
        if (version != null && !version.equals("v1") && !version.equals("default")) {
            logVersionMismatch(version);
        }
        validateKeyLength(allKeys);
        return allKeys;
    }

    /**
     * P0-3: Validate minimum key length to prevent weak key dictionary attacks.
     *
     * @param key the raw key material to validate
     * @throws MyJpaPlusException if key is shorter than {@link #MIN_KEY_LENGTH}
     */
    private static void validateKeyLength(String key) {
        if (key != null && key.length() < MIN_KEY_LENGTH) {
            throw new MyJpaPlusException("Encryption key must be at least " + MIN_KEY_LENGTH + " characters. "
                + "Current length: " + key.length() + ". "
                + "Short keys are vulnerable to dictionary attacks even with PBKDF2 derivation.");
        }
    }

    private static void logVersionMismatch(String version) {
        // 使用 java.util.logging 避免引入额外依赖
        System.getLogger("com.zsubera.jpa.converter.EncryptConverter").log(System.Logger.Level.WARNING,
            "Key version '{0}' not found in multi-key configuration. Using primary key.", version);
    }

    /**
     * 使用 PBKDF2WithHmacSHA256 从原始密钥材料派生 AES 密钥。
     *
     * @param rawKeyMaterial 原始密钥材料（密码或编码密钥）
     * @return 派生的 AES 密钥规范
     */
    private static SecretKeySpec deriveKey(String rawKeyMaterial) {
        try {
            // P0: Always derive key via PBKDF2 for security - removed direct use shortcut
            // The direct use of raw key bytes was a security risk as low-entropy
            // passwords could be used directly as AES keys.
            byte[] salt = getSalt();
            PBEKeySpec spec = new PBEKeySpec(rawKeyMaterial.toCharArray(), salt, PBKDF2_ITERATIONS, PBKDF2_KEY_LENGTH);
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            byte[] derived = factory.generateSecret(spec).getEncoded();
            return new SecretKeySpec(derived, "AES");
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to derive encryption key via PBKDF2", e);
        }
    }

    /**
     * 检查当前是否为生产环境。
     *
     * @return 如果是生产环境返回 true
     */
    private static boolean isProductionEnvironment() {
        String profile = System.getProperty("spring.profiles.active", "");
        return profile.contains("prod") || profile.contains("production");
    }

    /**
     * 获取 PBKDF2 盐值。优先从环境变量获取，回退到系统属性。 生产环境（spring.profiles.active 包含 prod/production）强制要求配置盐值。
     *
     * <p>
     * <strong>安全改进：</strong>非生产环境不再使用硬编码的默认盐值，而是生成随机盐值并记录警告。 随机盐值在 JVM 生命周期内保持一致（缓存在系统属性中），确保同一 JVM 内的加密/解密操作使用相同盐值。
     *
     * @return 盐值字节数组
     * @throws IllegalStateException 生产环境未配置盐值时抛出
     */
    private static byte[] getSalt() {
        String salt = System.getenv(SALT_ENV);
        if (salt == null || salt.isEmpty()) {
            salt = System.getProperty(SALT_PROPERTY);
        }
        if (salt == null || salt.isEmpty()) {
            String profile = System.getProperty("spring.profiles.active", "");
            if (profile.contains("prod") || profile.contains("production")) {
                throw new IllegalStateException("PBKDF2 salt must be configured in production. "
                    + "Set environment variable " + SALT_ENV + " or system property " + SALT_PROPERTY);
            }
            // P1-1: Use ConcurrentHashMap for thread-safe salt caching instead of System.setProperty()
            return SALT_CACHE.computeIfAbsent("internal", k -> {
                byte[] randomSalt = generateRandomSalt().getBytes(StandardCharsets.UTF_8);
                LOG.log(System.Logger.Level.WARNING, "SECURITY: Using randomly generated PBKDF2 salt. "
                    + "Set environment variable {0} or system property {1} for consistent encryption across restarts.",
                    SALT_ENV, SALT_PROPERTY);
                return randomSalt;
            });
        }
        return salt.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * 生成随机盐值（Base64 编码的 16 字节随机数据）。
     *
     * @return Base64 编码的随机盐值字符串
     */
    private static String generateRandomSalt() {
        byte[] saltBytes = new byte[16];
        SECURE_RANDOM.nextBytes(saltBytes);
        return Base64.getEncoder().encodeToString(saltBytes);
    }

    /**
     * 使用当前密钥重新加密已加密的值。用于密钥轮换场景下的数据迁移。
     *
     * <p>
     * 此方法解密旧值后使用当前密钥版本重新加密。适用于批量迁移场景：
     *
     * <pre>{@code
     * // 1. 更新密钥环境变量
     * // 2. 调用 refreshKeyVersion() 使新密钥生效
     * EncryptConverter.refreshKeyVersion();
     * // 3. 逐条读取旧数据并重新加密
     * String oldEncrypted = ...; // 从数据库读取
     * String newEncrypted = EncryptConverter.reEncrypt(oldEncrypted);
     * // 4. 写回数据库
     * }</pre>
     *
     * @param encryptedValue 旧密钥加密的值（带版本前缀格式）
     * @return 使用当前密钥重新加密的值
     * @throws IllegalArgumentException 如果 encryptedValue 为 null
     * @throws com.zsubera.jpa.exception.MyJpaPlusException 如果解密或加密失败
     */
    public String reEncrypt(String encryptedValue) {
        if (encryptedValue == null) {
            throw new IllegalArgumentException("encryptedValue must not be null");
        }
        String decrypted = convertToEntityAttribute(encryptedValue);
        return convertToDatabaseColumn(decrypted);
    }
}
