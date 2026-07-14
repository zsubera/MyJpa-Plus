package com.zsubera.jpa.converter;

import com.zsubera.jpa.exception.MyJpaPlusException;
import jakarta.persistence.AttributeConverter;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import jakarta.annotation.PreDestroy;
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

    /** 密钥预热线程池。volatile 保证 DCL 可见性。 */
    private static volatile java.util.concurrent.ExecutorService warmUpExecutor;

    private static final Object WARMUP_LOCK = new Object();

    private static java.util.concurrent.ExecutorService getOrCreateWarmUpExecutor() {
        java.util.concurrent.ExecutorService executor = warmUpExecutor;
        if (executor != null) {
            return executor;
        }
        synchronized (WARMUP_LOCK) {
            executor = warmUpExecutor;
            if (executor != null) {
                return executor;
            }
            executor = java.util.concurrent.Executors.newFixedThreadPool(1, r -> {
                Thread t = new Thread(r, "encrypt-key-warmup");
                t.setDaemon(true);
                return t;
            });
            warmUpExecutor = executor;
            return executor;
        }
    }

    /**
     * 获取 Cipher 实例。为避免 JDK GCM 状态重用缺陷（JDK-8201285），
     * 每次请求都创建新的 Cipher 实例，不池化。
     */
    private static Cipher borrowCipher() {
        try {
            return Cipher.getInstance(ALGORITHM);
        } catch (GeneralSecurityException e) {
            throw new MyJpaPlusException("Failed to initialize cipher", e);
        }
    }

    /**
     * 归还 Cipher 实例。由于每次操作都创建新实例，直接丢弃以避免
     * JDK GCM 内部状态在不兼容版本上产生的数据损坏（JDK-8201285）。
     */
    private static void returnCipher(Cipher cipher) {}

    /**
     * 安全清除 byte 数组中的敏感数据。
     *
     * <p>{@code Arrays.fill} 是 native 方法，JVM 无法将其优化掉。
     * 原实现使用 {@code ByteBuffer.allocate()} 复制一份secret再清零，
     * 实际上多分配了一份敏感数据在堆上，增加了暴露窗口。</p>
     */
    private static void wipe(byte[] secret) {
        if (secret == null)
            return;
        java.util.Arrays.fill(secret, (byte)0);
    }

    /**
     * 清除所有缓存的密钥、版本信息和 Cipher 对象池。用于应用关闭时清理和测试环境重置。
     */
    public static void clearCaches() {
        EncryptionKeyManager.clearCaches();
    }

    /**
     * 关闭预热线程池。由 Spring 容器在关闭时调用。
     *
     * <p>必须是非静态方法才能被 Spring 的 @PreDestroy 生命周期回调触发。
     */
    @PreDestroy
    public void shutdownWarmUpExecutor() {
        doShutdownWarmUpExecutor();
    }

    /**
     * 关闭预热线程池的静态实现。供外部直接调用（如测试清理）。
     */
    public static void doShutdownWarmUpExecutor() {
        java.util.concurrent.ExecutorService executor;
        synchronized (WARMUP_LOCK) {
            executor = warmUpExecutor;
            if (executor != null) {
                warmUpExecutor = null;
            }
        }
        if (executor != null && !executor.isShutdown()) {
            executor.shutdownNow();
        }
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
     * 设置 Spring Environment 检测到的生产环境标志。由自动配置类在启动时调用。
     *
     * @param isProduction true 如果 Spring Environment 检测到生产环境
     */
    public static void setSpringProductionEnvironment(Boolean isProduction) {
        EncryptionKeyManager.setSpringProductionEnvironment(isProduction);
    }

    /**
     * 设置密钥版本缓存刷新间隔（毫秒）。默认 300,000ms（5分钟）。
     * 缩短此间隔可使密钥版本变更更快生效，但会增加环境变量/系统属性的读取频率。
     *
     * @param intervalMs 刷新间隔（毫秒），最小 1,000ms
     */
    public static void setKeyVersionRefreshInterval(long intervalMs) {
        EncryptionKeyManager.setKeyVersionRefreshInterval(intervalMs);
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
                    log.warn("Failed to warm up encryption key cache: {}", e.getMessage(), e);
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
            log.warn("Failed to warm up encryption key cache synchronously: {}", e.getMessage(), e);
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
        String version = EncryptionKeyManager.getKeyVersion();
        SecretKeySpec keySpec = EncryptionKeyManager.getKeySpec(version);
        byte[] iv = new byte[GCM_IV_LENGTH];
        SECURE_RANDOM.nextBytes(iv);
        byte[] plaintextBytes = attribute.getBytes(StandardCharsets.UTF_8);
        byte[] encrypted = null;
        byte[] combined = null;
        try {
            Cipher cipher = borrowCipher();
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            encrypted = cipher.doFinal(plaintextBytes);
            combined = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);
            String result = version + ":" + Base64.getEncoder().encodeToString(combined);
            return result;
        } catch (GeneralSecurityException e) {
            log.error("Encryption failed", e);
            throw new MyJpaPlusException("Failed to encrypt value. Check encryption key configuration.", e);
        } finally {
            wipe(plaintextBytes);
            wipe(iv);
            wipe(combined);
            wipe(encrypted);
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
                log.error("Invalid Base64 data in encrypted field.", e);
                throw new MyJpaPlusException("Decryption failed: invalid Base64 data in encrypted field. "
                    + "The value may not be encrypted (stored as plain text), corrupted, "
                    + "or encrypted with a different encoding. "
                    + "If this field was recently added, ensure all existing rows are encrypted "
                    + "or the column default is compatible with the encrypted format.", e);
            }
            int minRequired = GCM_IV_LENGTH + (GCM_TAG_LENGTH / 8);
            if (combined.length < minRequired) {
                wipe(combined);
                log.error(
                    "Invalid encrypted data: decoded length ({}) is less than minimum required ({} bytes = IV + tag). "
                        + "Data may be truncated or corrupted.",
                    combined.length, minRequired);
                throw new MyJpaPlusException("Decryption failed: encrypted data too short (" + combined.length
                    + " bytes). Minimum required: " + minRequired + " bytes (IV + GCM tag). "
                    + "Data may be truncated or corrupted. Original value cannot be recovered.");
            }
            byte[] iv = new byte[GCM_IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH);
            byte[] encrypted = new byte[combined.length - GCM_IV_LENGTH];
            System.arraycopy(combined, GCM_IV_LENGTH, encrypted, 0, encrypted.length);
            byte[] decrypted = null;
            try {
                SecretKeySpec keySpec = EncryptionKeyManager.getKeySpec(version);
                Cipher cipher = borrowCipher();
                cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
                decrypted = cipher.doFinal(encrypted);
                return new String(decrypted, StandardCharsets.UTF_8);
            } finally {
                wipe(combined);
                wipe(iv);
                wipe(encrypted);
                wipe(decrypted);
            }
        } catch (GeneralSecurityException | RuntimeException e) {
            log.error("Decryption failed for encrypted field. "
                + "The encrypted value may be corrupted or encrypted with a different key.", e);
            throw new MyJpaPlusException("Decryption failed: the encrypted value may be corrupted "
                + "or encrypted with a different key. Original value cannot be recovered.", e);
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
        if (decrypted == null) {
            throw new MyJpaPlusException("reEncrypt failed: decryption returned null for the given value. "
                + "The data may be corrupted or encrypted with a different key. "
                + "Original encrypted value cannot be recovered.");
        }
        return SHARED_INSTANCE.convertToDatabaseColumn(decrypted);
    }
}
