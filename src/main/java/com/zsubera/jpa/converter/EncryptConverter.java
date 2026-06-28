package com.zsubera.jpa.converter;

import com.zsubera.jpa.exception.MyJpaPlusException;
import jakarta.persistence.AttributeConverter;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.*;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import jakarta.persistence.Converter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JPA 属性转换器，提供透明的 AES/GCM 字段级加密存储。
 *
 * <p>
 * 在实体字段写入数据库时自动加密，读取时自动解密。加密算法为
 * {@code AES/GCM/NoPadding}（认证加密），每次加密使用随机 12 字节 IV，提供机密性与完整性保护。
 *
 * <h3>密钥配置</h3>
 * <ul>
 *   <li>环境变量 {@code MYJPA_ENCRYPT_KEY}（推荐）</li>
 *   <li>系统属性 {@code myjpa.encrypt.key}</li>
 * </ul>
 *
 * <p>
 * 密钥通过 PBKDF2WithHmacSHA256（600,000 次迭代、256 位输出）派生 AES 密钥。最小密钥长度为 16 字节（UTF-8）。
 *
 * <h3>多版本密钥轮换</h3>
 * 支持格式 {@code v1:key1,v2:key2}，配合 {@link EncryptionKeyManager#refreshKeyVersion()} 实现在线密钥轮换。
 *
 * <h3>盐值管理</h3>
 * <ul>
 *   <li>生产环境：必须通过环境变量 {@code MYJPA_ENCRYPT_SALT} 或系统属性 {@code myjpa.encrypt.salt} 配置盐值</li>
 *   <li>开发环境：未配置时使用固定的开发盐值常量（仅限开发，生产不安全）</li>
 * </ul>
 *
 * <h3>典型用法</h3>
 *
 * <pre>{@code
 * @Entity
 * public class User {
 *     @Id @GeneratedValue
 *     private Long id;
 *
 *     @Encrypt
 *     private String phone;
 * }
 * }</pre>
 *
 * @see com.zsubera.jpa.annotation.Encrypt
 * @see EncryptionKeyManager
 */
@Converter
public class EncryptConverter implements AttributeConverter<String, String> {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final Logger log = LoggerFactory.getLogger(EncryptConverter.class);

    /** 用于 reEncrypt() 的共享实例（所有方法仅使用静态状态，线程安全）。 */
    private static final EncryptConverter SHARED_INSTANCE = new EncryptConverter();

    /** 加密数据版本前缀匹配模式。 */
    private static final java.util.regex.Pattern VERSION_PATTERN = java.util.regex.Pattern.compile("v\\d+");

    /** 密钥预热线程池。 */
    private static final java.util.concurrent.atomic.AtomicReference<
        java.util.concurrent.ExecutorService> WARM_UP_EXECUTOR = new java.util.concurrent.atomic.AtomicReference<>();

    private static java.util.concurrent.ExecutorService getOrCreateWarmUpExecutor() {
        java.util.concurrent.ExecutorService executor = WARM_UP_EXECUTOR.get();
        if (executor != null) {
            return executor;
        }
        synchronized (WARM_UP_EXECUTOR) {
            executor = WARM_UP_EXECUTOR.get();
            if (executor != null) {
                return executor;
            }
            executor = java.util.concurrent.Executors.newFixedThreadPool(1, r -> {
                Thread t = new Thread(r, "encrypt-key-warmup");
                t.setDaemon(true);
                return t;
            });
            WARM_UP_EXECUTOR.set(executor);
            return executor;
        }
    }

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            java.util.concurrent.ExecutorService executor = WARM_UP_EXECUTOR.getAndSet(null);
            if (executor != null) {
                executor.shutdownNow();
            }
        }, "encrypt-key-warmup-shutdown"));
    }

    /**
     * ponytail: 使用 ThreadLocal 缓存 Cipher 实例，避免每次加解密都执行 Cipher.getInstance() 的 SPI 查找开销。
     * 每次操作通过 cipher.init() 重置状态，等价于新建实例但省去了 SPI 查找和对象分配。
     * ThreadLocal 与虚拟线程兼容（每个虚拟线程拥有独立映射）。
     */
    private static final ThreadLocal<Cipher> CIPHER_CACHE = ThreadLocal.withInitial(() -> {
        try {
            return Cipher.getInstance(ALGORITHM);
        } catch (GeneralSecurityException e) {
            throw new MyJpaPlusException("Failed to initialize cipher", e);
        }
    });

    private static Cipher getCipher() {
        return CIPHER_CACHE.get();
    }

    /**
     * @deprecated 此方法已无操作，保留仅为 API 兼容性。Cipher 实例通过 ThreadLocal 缓存，无需手动清理。
     */
    @Deprecated(forRemoval = true)
    public static void removeCipher() {}

    /**
     * 清除 ThreadLocal 缓存的 Cipher 实例。用于应用关闭时清理。
     */
    static void clearCipherCache() {
        CIPHER_CACHE.remove();
    }

    /**
     * 清除所有缓存的密钥、版本信息和 ThreadLocal Cipher 实例。用于应用关闭时清理和测试环境重置。
     */
    public static void clearCaches() {
        EncryptionKeyManager.clearCaches();
        clearCipherCache();
    }

    /**
     * 设置 PBKDF2 密钥派生迭代次数。由自动配置类在启动时调用。
     *
     * @param iterations 迭代次数（100,000 - 10,000,000）
     */
    public static void setPbkdf2Iterations(int iterations) {
        EncryptionKeyManager.setPbkdf2Iterations(iterations);
    }

    /**
     * 设置是否跳过 PBKDF2 盐值检查（开发环境使用）。由自动配置类在启动时调用。
     *
     * @param skip true 跳过盐值检查
     */
    public static void setSkipSaltCheck(boolean skip) {
        EncryptionKeyManager.setSkipSaltCheck(skip);
    }

    /**
     * 异步预热密钥缓存。在应用启动后调用此方法可避免首次请求的延迟。
     */
    public static void warmUpKeyCache() {
        java.util.concurrent.ExecutorService executor = getOrCreateWarmUpExecutor();
        try {
            java.util.concurrent.CompletableFuture.runAsync(() -> {
                try {
                    String version = EncryptionKeyManager.getKeyVersion();
                    EncryptionKeyManager.getKeySpec(version);
                    log.info("Encryption key cache warmed up for version: {}", version);
                } catch (Exception e) {
                    log.warn("Failed to warm up encryption key cache: {}", e.getMessage());
                }
            }, executor);
        } catch (java.util.concurrent.RejectedExecutionException e) {
            log.warn("Encryption key warm-up executor rejected task (may be shutting down): {}", e.getMessage());
        }
    }

    /**
     * 同步预热密钥缓存。在应用启动时调用此方法确保密钥已就绪。
     */
    public static void warmUpKeyCacheSync() {
        try {
            String version = EncryptionKeyManager.getKeyVersion();
            EncryptionKeyManager.getKeySpec(version);
            log.info("Encryption key cache warmed up synchronously for version: {}", version);
        } catch (Exception e) {
            log.warn("Failed to warm up encryption key cache synchronously: {}", e.getMessage());
        }
    }

    /**
     * 强制刷新密钥版本缓存。在密钥轮换后调用此方法使新密钥立即生效。
     */
    public static void refreshKeyVersion() {
        EncryptionKeyManager.refreshKeyVersion();
    }

    /**
     * 在启动时验证加密密钥配置。
     */
    public static void validateKeyConfiguration() {
        EncryptionKeyManager.validateKeyConfiguration();
    }

    /**
     * 将实体字段值加密后写入数据库列。
     */
    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null) {
            return null;
        }
        if (!EncryptionKeyManager.KEY_VALIDATED.get()) {
            validateKeyConfiguration();
        }
        // ponytail: getSalt() now throws when no persistent salt is configured,
        // preventing silent data-loss. The warning previously logged here is
        // unreachable — deriveKey() -> getSalt() throws first.
        try {
            SecretKeySpec keySpec = EncryptionKeyManager.getKeySpec();
            Cipher cipher = getCipher();
            byte[] iv = new byte[GCM_IV_LENGTH];
            SECURE_RANDOM.nextBytes(iv);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] plaintextBytes = attribute.getBytes(StandardCharsets.UTF_8);
            try {
                byte[] encrypted = cipher.doFinal(plaintextBytes);
                byte[] combined = new byte[iv.length + encrypted.length];
                System.arraycopy(iv, 0, combined, 0, iv.length);
                System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);
                String version = EncryptionKeyManager.getKeyVersion();
                return version + ":" + Base64.getEncoder().encodeToString(combined);
            } finally {
                java.util.Arrays.fill(plaintextBytes, (byte)0);
            }
        } catch (GeneralSecurityException e) {
            log.error("Encryption failed", e);
            throw new MyJpaPlusException("Failed to encrypt value. Check encryption key configuration.", e);
        }
    }

    /**
     * 从数据库列读取密文并解密还原为实体字段明文值。
     */
    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        if (!EncryptionKeyManager.KEY_VALIDATED.get()) {
            validateKeyConfiguration();
        }
        try {
            String base64Data;
            String version = null;
            int colonIndex = dbData.indexOf(':');
            if (colonIndex > 0) {
                String candidateVersion = dbData.substring(0, colonIndex);
                if (VERSION_PATTERN.matcher(candidateVersion).matches()) {
                    version = candidateVersion;
                    base64Data = dbData.substring(colonIndex + 1);
                } else {
                    base64Data = dbData;
                }
            } else {
                base64Data = dbData;
            }
            byte[] combined;
            try {
                combined = Base64.getDecoder().decode(base64Data);
            } catch (IllegalArgumentException e) {
                log.error("Invalid Base64 data in encrypted field", e);
                throw new MyJpaPlusException("Failed to decrypt value: invalid Base64 encoding.", e);
            }
            int minRequired = GCM_IV_LENGTH + (GCM_TAG_LENGTH / 8);
            if (combined.length < minRequired) {
                throw new MyJpaPlusException("Invalid encrypted data: decoded length (" + combined.length
                    + ") is less than minimum required (" + minRequired + " bytes = IV + tag). "
                    + "Data may be truncated or corrupted.");
            }
            byte[] iv = new byte[GCM_IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH);
            byte[] encrypted = new byte[combined.length - GCM_IV_LENGTH];
            System.arraycopy(combined, GCM_IV_LENGTH, encrypted, 0, encrypted.length);
            SecretKeySpec keySpec = EncryptionKeyManager.getKeySpec(version);
            Cipher cipher = getCipher();
            cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] decrypted = cipher.doFinal(encrypted);
            try {
                return new String(decrypted, StandardCharsets.UTF_8);
            } finally {
                java.util.Arrays.fill(decrypted, (byte)0);
            }
        } catch (GeneralSecurityException e) {
            log.error("Decryption failed", e);
            throw new MyJpaPlusException("Failed to decrypt value. Check encryption key configuration.", e);
        }
    }

    /**
     * 使用当前密钥重新加密已加密的值。用于密钥轮换场景下的数据迁移。
     */
    public static String reEncrypt(String encryptedValue) {
        if (encryptedValue == null) {
            throw new IllegalArgumentException("encryptedValue must not be null");
        }
        if (!EncryptionKeyManager.KEY_VALIDATED.get()) {
            validateKeyConfiguration();
        }
        String decrypted = SHARED_INSTANCE.convertToEntityAttribute(encryptedValue);
        // ponytail: plaintext String stays on heap until GC. Upgrade to char[] + wipe if audit demands it.
        return SHARED_INSTANCE.convertToDatabaseColumn(decrypted);
    }
}
