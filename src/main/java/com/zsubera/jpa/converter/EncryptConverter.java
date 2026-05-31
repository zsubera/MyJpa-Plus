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
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int PBKDF2_ITERATIONS = 100_000;
    private static final int PBKDF2_KEY_LENGTH = 256;
    private static final System.Logger LOG = System.getLogger("com.zsubera.jpa.converter.EncryptConverter");

    /** Cached key specs by version to avoid repeated environment variable reads and KDF derivation. */
    private static final ConcurrentMap<String, SecretKeySpec> KEY_CACHE = new ConcurrentHashMap<>();

    /** Cached key version to avoid repeated environment variable reads. */
    private static volatile String cachedKeyVersion;

    /**
     * 清除所有缓存的密钥和版本信息。仅用于测试环境。
     */
    static void clearCacheForTesting() {
        KEY_CACHE.clear();
        cachedKeyVersion = null;
    }

    /**
     * 获取当前密钥版本标识。结果缓存以避免每次操作读取环境变量。
     *
     * @return 密钥版本标识
     */
    private static String getKeyVersion() {
        String version = cachedKeyVersion;
        if (version != null) {
            return version;
        }
        version = System.getenv(KEY_VERSION_ENV);
        if (version == null || version.isEmpty()) {
            version = System.getProperty(KEY_VERSION_PROPERTY);
        }
        cachedKeyVersion = (version != null && !version.isEmpty()) ? version : "v1";
        return cachedKeyVersion;
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
                        return entryKey;
                    }
                }
            }
        }

        // 单密钥模式：如果指定了版本但没有找到对应密钥，使用主密钥
        if (version != null && !version.equals("v1") && !version.equals("default")) {
            logVersionMismatch(version);
        }
        return allKeys;
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
            byte[] keyBytes = rawKeyMaterial.getBytes(StandardCharsets.UTF_8);
            // 如果已经是有效的 AES 密钥长度，直接使用（向后兼容）
            if (keyBytes.length == 16 || keyBytes.length == 24 || keyBytes.length == 32) {
                // P0-4: 检测是否为高熵密钥（非可打印 ASCII）
                boolean looksLikeAscii = true;
                for (byte b : keyBytes) {
                    if (b < 0x20 || b > 0x7E) {
                        looksLikeAscii = false;
                        break;
                    }
                }
                if (looksLikeAscii) {
                    int uniqueChars = (int)new String(keyBytes, StandardCharsets.US_ASCII).chars().distinct().count();
                    String profile = System.getProperty("spring.profiles.active", "");
                    if (uniqueChars < 10 && (profile.contains("prod") || profile.contains("production"))) {
                        throw new IllegalStateException("Encryption key has low entropy (" + uniqueChars
                            + " unique characters). " + "Use Base64-encoded high-entropy key for production. "
                            + "Set MYJPA_ENCRYPT_KEY to a Base64-encoded random key.");
                    }
                    LOG.log(System.Logger.Level.WARNING,
                        "Raw key looks like printable ASCII ({0} bytes). "
                            + "Use Base64-encoded high-entropy key for production. "
                            + "Set MYJPA_ENCRYPT_KEY to a Base64-encoded random key.",
                        keyBytes.length);
                }
                return new SecretKeySpec(keyBytes, "AES");
            }
            // 否则使用 PBKDF2 派生
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
     * 获取 PBKDF2 盐值。优先从环境变量获取，回退到系统属性。 生产环境（spring.profiles.active 包含 prod/production）强制要求配置盐值。
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
            LOG.log(System.Logger.Level.WARNING,
                "Using default PBKDF2 salt. Set environment variable {0} or system property {1} for production use.",
                SALT_ENV, SALT_PROPERTY);
            return "myjpa-plus".getBytes(StandardCharsets.UTF_8);
        }
        return salt.getBytes(StandardCharsets.UTF_8);
    }
}
