package com.zsubera.jpa.converter;

import com.zsubera.jpa.exception.MyJpaPlusException;
import jakarta.persistence.AttributeConverter;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;
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

    /** 密钥预热线程池。 */
    private static java.util.concurrent.ExecutorService warmUpExecutor;

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
     * Cipher 对象池最大容量，超过时丢弃归还的 cipher 防止无限增长。
     * ponytail: 64 cap 覆盖常见并发场景（虚拟线程 1000+ 时 pool 不会被撑爆）。
     * 如果出现 cipher 不足会重新创建，borrowCipher 允许多个线程同时新建。
     */
    private static final int MAX_POOL_SIZE = 64;

    /**
     * Cipher 对象池：使用 ConcurrentLinkedDeque 替代 ThreadLocal，
     * 避免虚拟线程场景下百万级 ThreadLocal 实例导致 OOM。
     */
    private static final java.util.Queue<Cipher> CIPHER_POOL = new ConcurrentLinkedDeque<>();

    private static Cipher borrowCipher() {
        Cipher cipher = CIPHER_POOL.poll();
        if (cipher == null) {
            try {
                cipher = Cipher.getInstance(ALGORITHM);
            } catch (GeneralSecurityException e) {
                throw new MyJpaPlusException("Failed to initialize cipher", e);
            }
        }
        return cipher;
    }

    private static void returnCipher(Cipher cipher) {
        if (CIPHER_POOL.size() >= MAX_POOL_SIZE) {
            // ponytail: 池满时丢弃归还的 cipher，下次 borrow 重新创建。
            // 避免池中混入状态异常的 cipher（doFinal 异常后虽然 init 会重置状态，
            // 但防御性丢弃防止任何潜在问题）。
            return;
        }
        CIPHER_POOL.offer(cipher);
    }

    /**
     * 清除 Cipher 对象池。用于应用关闭时清理。
     */
    static void clearCipherPool() {
        CIPHER_POOL.clear();
    }

    /**
     * 清除所有缓存的密钥、版本信息和 Cipher 对象池。用于应用关闭时清理和测试环境重置。
     */
    public static void clearCaches() {
        EncryptionKeyManager.clearCaches();
        clearCipherPool();
    }

    /**
     * 关闭预热线程池。由 Spring 容器在关闭时调用。
     */
    @PreDestroy
    public static void shutdownWarmUpExecutor() {
        java.util.concurrent.ExecutorService executor = warmUpExecutor;
        if (executor != null && !executor.isShutdown()) {
            executor.shutdownNow();
            warmUpExecutor = null;
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
        SecretKeySpec keySpec = EncryptionKeyManager.getKeySpec();
        byte[] iv = new byte[GCM_IV_LENGTH];
        SECURE_RANDOM.nextBytes(iv);
        byte[] plaintextBytes = attribute.getBytes(StandardCharsets.UTF_8);
        Cipher cipher = borrowCipher();
        try {
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] encrypted = cipher.doFinal(plaintextBytes);
            byte[] combined = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);
            String version = EncryptionKeyManager.getKeyVersion();
            // ponytail: 成功后归还 cipher 到池，失败时丢弃（doFinal 异常后 cipher 状态不可恢复）
            returnCipher(cipher);
            return version + ":" + Base64.getEncoder().encodeToString(combined);
        } catch (GeneralSecurityException e) {
            log.error("Encryption failed", e);
            throw new MyJpaPlusException("Failed to encrypt value. Check encryption key configuration.", e);
        } finally {
            java.util.Arrays.fill(plaintextBytes, (byte)0);
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
            Cipher cipher = borrowCipher();
            try {
                cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
                byte[] decrypted = cipher.doFinal(encrypted);
                // ponytail: 成功后归还 cipher 到池，失败时丢弃
                returnCipher(cipher);
                return new String(decrypted, StandardCharsets.UTF_8);
            } finally {
                // doFinal 异常时 cipher 内部状态机可能损坏，丢弃不归还
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
        try {
            return SHARED_INSTANCE.convertToDatabaseColumn(decrypted);
        } finally {
            // ponytail: String is immutable so full zeroing needs char[] migration.
            // Security: plaintext remains in heap until GC. In high-security environments,
            // consider using char[] throughout the application instead of String.
            decrypted = null;
        }
    }
}
