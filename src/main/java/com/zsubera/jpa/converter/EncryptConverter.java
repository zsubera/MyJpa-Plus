package com.zsubera.jpa.converter;

import com.zsubera.jpa.exception.MyJpaPlusException;
import jakarta.persistence.AttributeConverter;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
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
 * 支持格式 {@code v1:key1,v2:key2}，配合 {@link #refreshKeyVersion()} 实现在线密钥轮换。
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
 */
@Converter
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
    private static final int PBKDF2_ITERATIONS = 600_000;
    private static final int PBKDF2_KEY_LENGTH = 256;
    private static final Logger log = LoggerFactory.getLogger(EncryptConverter.class);

    /**
     * GCM 模式下 Cipher 实例每次操作新建，不重用。
     *
     * <p>JDK 的 GCM Cipher 实现存在已知 bug（JDK-8201324）：doFinal() 失败后 Cipher
     * 内部状态未完全重置，复用会导致后续加解密输出错误结果。每次调用创建新实例是最安全的模式。</p>
     */
    private static Cipher createCipher() {
        try {
            return Cipher.getInstance(ALGORITHM);
        } catch (GeneralSecurityException e) {
            throw new MyJpaPlusException("Failed to initialize cipher", e);
        }
    }

    /** 防止弱密钥攻击的最小密钥长度（字符数）。 */
    private static final int MIN_KEY_LENGTH = 16;

    /** 按版本缓存的密钥规范，避免重复读取环境变量和 KDF 派生。 */
    private static final ConcurrentMap<String, SecretKeySpec> KEY_CACHE = new ConcurrentHashMap<>();

    // 实际大小检查使用 KEY_CACHE.size()

    /** 缓存的密钥规范最大数量，防止恶意版本前缀导致内存耗尽。 */
    private static final int MAX_KEY_CACHE_SIZE = 16;

    /** 用于 reEncrypt() 的共享实例（所有方法仅使用静态状态，线程安全）。 */
    private static final EncryptConverter SHARED_INSTANCE = new EncryptConverter();

    /** 多密钥格式正则表达式：vN:key,vN:key */
    private static final java.util.regex.Pattern MULTI_KEY_PATTERN = java.util.regex.Pattern.compile(".*v\\d+:.*,.+");

    /** 单条目格式正则表达式：vN:key */
    private static final java.util.regex.Pattern SINGLE_ENTRY_PATTERN = java.util.regex.Pattern.compile("v\\d+:.+");

    /** 密钥版本缓存刷新间隔（毫秒），默认 5 分钟。 */
    private static final long KEY_VERSION_REFRESH_INTERVAL_MS = 300_000L;

    /** 懒初始化的密钥预热线程池。使用 AtomicReference + lazy init 避免类加载时创建线程。 */
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

    /** 开发环境可选：显式设置为 true 时跳过盐值检查（仅限开发环境）。 */
    private static final String SKIP_SALT_PROPERTY = "myjpa-plus.encrypt.skip-salt-check";
    private static final String SKIP_SALT_ENV = "MYJPA_ENCRYPT_SKIP_SALT_CHECK";

    /** 保护 getKeySpec 缓存的读写锁：读路径（缓存命中）无需锁，写路径（缓存未命中/KDF 派生）互斥。 */
    private static final ReentrantReadWriteLock KEY_SPEC_LOCK = new ReentrantReadWriteLock();
    private static final Lock KEY_SPEC_READ_LOCK = KEY_SPEC_LOCK.readLock();
    private static final Lock KEY_SPEC_WRITE_LOCK = KEY_SPEC_LOCK.writeLock();

    /** 保护 getKeyVersion 缓存刷新的轻量锁（仅每 5 分钟触发一次写路径）。 */
    private static final ReentrantLock KEY_VERSION_LOCK = new ReentrantLock();

    /** 开发环境固定盐值。仅在 skip-salt-check=true 时使用，确保跨 JVM 重启的数据可恢复性。 */
    private static final String DEV_SALT = "myjpa-plus-dev-salt-2024";

    /** 加密数据版本前缀匹配模式（如 "v1"、"v2"）。 */
    private static final java.util.regex.Pattern VERSION_PATTERN = java.util.regex.Pattern.compile("v\\d+");

    /** 跟踪密钥验证是否已执行的标志。使用 AtomicBoolean 保证 check-then-act 原子性。 */
    private static final AtomicBoolean KEY_VALIDATED = new AtomicBoolean(false);

    /** 跟踪是否已记录过开发盐值警告（避免每次加密都记录）。使用 AtomicBoolean 保证只记录一次。 */
    private static final AtomicBoolean DEV_SALT_WARNING_LOGGED = new AtomicBoolean(false);

    /**
     * 清除当前线程的 Cipher 缓存（无操作：GCM 模式下每次操作新建 Cipher 实例，无需缓存清理）。
     *
     * @deprecated 此方法已无操作，保留仅为 API 兼容性。GCM 模式下每次操作新建 Cipher 实例，无需缓存清理。
     */
    @Deprecated(forRemoval = true)
    public static void removeCipher() {
        // GCM 模式下不缓存 Cipher，无需清理
    }

    /**
     * 清除所有缓存的密钥和版本信息。用于应用关闭时清理和测试环境重置。
     */
    public static void clearCaches() {
        KEY_CACHE.clear();
        keyVersionSnapshot = new KeyVersionSnapshot("v1", 0);
        KEY_VALIDATED.set(false);
        DEV_SALT_WARNING_LOGGED.set(false);
    }

    /**
     * 异步预热密钥缓存。在应用启动后调用此方法可避免首次请求的延迟。
     *
     * <p>
     * PBKDF2 密钥派生耗时约 100ms，首次加密/解密操作会因此产生较高延迟。
     * 调用此方法可在后台线程中预先执行密钥派生，将结果缓存以供后续使用。
     *
     * <p>
     * 使用示例：
     *
     * <pre>{@code
     * @Configuration
     * public class AppConfig {
     *     @Bean
     *     public ApplicationRunner keyWarmUpRunner() {
     *         return args -> EncryptConverter.warmUpKeyCache();
     *     }
     * }
     * }</pre>
     *
     * <p>
     * 此方法在后台线程中执行密钥派生，不阻塞主线程。如果密钥未配置，会记录警告日志。
     */
    public static void warmUpKeyCache() {
        java.util.concurrent.ExecutorService executor = getOrCreateWarmUpExecutor();
        try {
            java.util.concurrent.CompletableFuture.runAsync(() -> {
                try {
                    String version = getKeyVersion();
                    getKeySpec(version);
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
     *
     * <p>
     * 与 {@link #warmUpKeyCache()} 不同，此方法是同步执行的，会阻塞直到密钥派生完成。
     * 适用于需要确保密钥在首次使用前已就绪的场景。
     *
     * <p>
     * 使用示例：
     *
     * <pre>{@code
     * @PostConstruct
     * public void init() {
     *     EncryptConverter.warmUpKeyCacheSync();
     * }
     * }</pre>
     */
    public static void warmUpKeyCacheSync() {
        try {
            String version = getKeyVersion();
            getKeySpec(version);
            log.info("Encryption key cache warmed up synchronously for version: {}", version);
        } catch (Exception e) {
            log.warn("Failed to warm up encryption key cache synchronously: {}", e.getMessage());
        }
    }

    /**
     * 强制刷新密钥版本缓存。在密钥轮换后调用此方法使新密钥立即生效。
     *
     * <p>
     * 此方法线程安全，可在运行时调用以支持在线密钥轮换。
     */
    public static void refreshKeyVersion() {
        KEY_SPEC_WRITE_LOCK.lock();
        try {
            KEY_CACHE.clear();
            keyVersionSnapshot = new KeyVersionSnapshot(null, 0);
        } finally {
            KEY_SPEC_WRITE_LOCK.unlock();
        }
        log.info("Encryption key version cache refreshed");
    }

    /**
     * 在启动时验证加密密钥配置。应在应用程序初始化期间调用此方法， 以便及早检测缺失的密钥配置。
     *
     * <p>
     * 如果密钥未配置且启用了严格模式，抛出 IllegalStateException。否则记录警告。
     *
     * <p>
     * 此方法使用 synchronized 保证仅执行一次验证（避免多线程重复验证）。
     */
    public static void validateKeyConfiguration() {
        if (KEY_VALIDATED.get()) {
            return;
        }
        synchronized (EncryptConverter.class) {
            if (KEY_VALIDATED.get()) {
                return;
            }
            String keyEnv = System.getenv(KEY_ENV);
            String keyProp = System.getProperty(KEY_PROPERTY);
            if ((keyEnv == null || keyEnv.isEmpty()) && (keyProp == null || keyProp.isEmpty())) {
                // 密钥是 EncryptConverter 工作的必要条件，无论是否为严格模式都必须配置。
                // 之前非严格模式仅记录警告，导致实体保存时才抛出 NullPointerException，
                // 无法提供明确的错误信息。现在统一在启动时拒绝。
                throw new IllegalStateException("Encryption key not configured. Set environment variable " + KEY_ENV
                    + " or system property " + KEY_PROPERTY + " before starting the application. "
                    + "EncryptConverter requires a key to function.");
            } else {
                String key = (keyEnv != null && !keyEnv.isEmpty()) ? keyEnv : keyProp;
                if (key != null) {
                    // 基于 UTF-8 字节长度校验，而非字符长度，确保非 ASCII 字符（如中文）密钥的熵足够
                    int byteLength = key.getBytes(StandardCharsets.UTF_8).length;
                    if (byteLength < MIN_KEY_LENGTH) {
                        throw new MyJpaPlusException(
                            "Encryption key must be at least " + MIN_KEY_LENGTH + " bytes (UTF-8 encoded). "
                                + "Current byte length: " + byteLength + " (character length: " + key.length() + ").");
                    }
                }
                // 当使用系统属性配置密钥时发出警告
                // 系统属性在 JVM 全局可见，可能在进程列表中暴露
                if (keyProp != null && !keyProp.isEmpty() && (keyEnv == null || keyEnv.isEmpty())) {
                    log.warn("SECURITY: Encryption key configured via system property '{}'. "
                        + "System properties are JVM-wide visible and may be exposed in process listings (e.g., /proc/PID/cmdline). "
                        + "Use environment variable '{}' for production environments.", KEY_PROPERTY, KEY_ENV);
                }
            }
            KEY_VALIDATED.set(true);
        }
    }

    /** 密钥版本快照：将 version 和 refreshTimestamp 组合为单个 volatile 引用，保证原子读取。 */
    private static volatile KeyVersionSnapshot keyVersionSnapshot = new KeyVersionSnapshot("v1", 0);

    private static final class KeyVersionSnapshot {
        final String version;
        final long refreshTimestamp;
        KeyVersionSnapshot(String version, long refreshTimestamp) {
            this.version = version;
            this.refreshTimestamp = refreshTimestamp;
        }
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
        // ponytail: 单次 volatile 读取快照对象，避免两次 volatile 读之间的竞态导致版本与时间戳不一致
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

    /**
     * 将实体字段值加密后写入数据库列。
     *
     * <p>
     * 加密流程：生成随机 IV → AES/GCM 加密 → 拼接 IV 与密文 → Base64 编码 → 添加版本前缀。
     * 输出格式为 {@code version:base64(iv + ciphertext)}。
     *
     * @param attribute 实体字段的明文值
     * @return 加密后的密文字符串，如果 {@code attribute} 为 null 则返回 null
     * @throws MyJpaPlusException 如果加密过程失败
     */
    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null) {
            return null;
        }
        // 加密前校验密钥配置以实现快速失败
        if (!KEY_VALIDATED.get()) {
            validateKeyConfiguration();
        }
        // 首次加密时检查是否使用开发盐值，发出一次性 CRITICAL 警告
        if (DEV_SALT_WARNING_LOGGED.compareAndSet(false, true) && isUsingDevSalt()) {
            log.error("CRITICAL: Encryption is using a predictable development salt! "
                + "Encrypted data WILL NOT BE SECURE in production. "
                + "Set environment variable {} or system property {} before deploying.", SALT_ENV, SALT_PROPERTY);
        }
        try {
            SecretKeySpec keySpec = getKeySpec();
            Cipher cipher = createCipher();
            byte[] iv = new byte[GCM_IV_LENGTH];
            SECURE_RANDOM.nextBytes(iv);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] plaintextBytes = attribute.getBytes(StandardCharsets.UTF_8);
            try {
                byte[] encrypted = cipher.doFinal(plaintextBytes);
                byte[] combined = new byte[iv.length + encrypted.length];
                System.arraycopy(iv, 0, combined, 0, iv.length);
                System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);
                String version = getKeyVersion();
                return version + ":" + Base64.getEncoder().encodeToString(combined);
            } finally {
                java.util.Arrays.fill(plaintextBytes, (byte) 0);
            }
        } catch (GeneralSecurityException e) {
            log.error("Encryption failed", e);
            throw new MyJpaPlusException("Failed to encrypt value. Check encryption key configuration.", e);
        }
    }

    /**
     * 从数据库列读取密文并解密还原为实体字段明文值。
     *
     * <p>
     * 支持两种存储格式：
     * <ul>
     *   <li>带版本前缀格式：{@code version:base64(iv + ciphertext)}</li>
     *   <li>无版本前缀格式（兼容旧数据）：{@code base64(iv + ciphertext)}</li>
     * </ul>
     *
     * @param dbData 数据库中的密文字符串
     * @return 解密后的明文值，如果 {@code dbData} 为 null 则返回 null
     * @throws MyJpaPlusException 如果 Base64 解码失败、数据损坏或解密过程失败
     */
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
                if (!VERSION_PATTERN.matcher(version).matches()) {
                    throw new MyJpaPlusException("Invalid key version prefix: '" + version
                        + "'. Expected format: v1, v2, etc. "
                        + "Encrypted data may be corrupted or produced by a different system.");
                } else {
                    base64Data = dbData.substring(colonIndex + 1);
                }
            } else {
                // 兼容旧格式（无版本前缀）
                base64Data = dbData;
            }
            byte[] combined;
            try {
                combined = Base64.getDecoder().decode(base64Data);
            } catch (IllegalArgumentException e) {
                log.error("Invalid Base64 data in encrypted field", e);
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
            Cipher cipher = createCipher();
            cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] decrypted = cipher.doFinal(encrypted);
            try {
                return new String(decrypted, StandardCharsets.UTF_8);
            } finally {
                java.util.Arrays.fill(decrypted, (byte) 0);
            }
        } catch (GeneralSecurityException e) {
            log.error("Decryption failed", e);
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
     *
     * <p>
     * KEY_CACHE 大小限制为 {@value #MAX_KEY_CACHE_SIZE} 个条目。超出限制时拒绝解密未知版本， 防止恶意构造大量不同版本号前缀的加密数据导致 CPU 耗尽和 OOM。
     */
    private static SecretKeySpec getKeySpec(String version) {
        String cacheKey = version != null ? version : "default";
        // 快速路径：ConcurrentHashMap 无锁读，缓存命中时零锁开销
        SecretKeySpec existing = KEY_CACHE.get(cacheKey);
        if (existing != null) {
            return existing;
        }
        // 慢速路径：写锁保护 size 检查与 put 的原子性，避免并发超限和重复 KDF 派生
        KEY_SPEC_WRITE_LOCK.lock();
        try {
            existing = KEY_CACHE.get(cacheKey);
            if (existing != null) {
                return existing;
            }
            if (KEY_CACHE.size() >= MAX_KEY_CACHE_SIZE) {
                throw new MyJpaPlusException("Encryption key cache is full (" + MAX_KEY_CACHE_SIZE + " entries). "
                    + "Cannot load key version '" + cacheKey + "'. "
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

        // 检测是否为多密钥格式: "v1:key1,v2:key2"
        // 使用显式正则表达式匹配，避免 contains(":") && contains(",") 的歧义
        boolean looksLikeMultiKey = MULTI_KEY_PATTERN.matcher(allKeys).matches();
        if (looksLikeMultiKey) {
            String[] entries = allKeys.split(",");
            // 验证所有条目格式，收集无效条目用于诊断
            List<String> invalidEntries = new ArrayList<>();
            for (String entry : entries) {
                String trimmed = entry.trim();
                if (!SINGLE_ENTRY_PATTERN.matcher(trimmed).matches()) {
                    invalidEntries.add(trimmed);
                }
            }
            if (!invalidEntries.isEmpty()) {
                // 格式看起来像多密钥但有无效条目 — 抛出明确错误而非静默回退，
                // 因为静默回退可能导致使用整个多密钥字符串作为单一密钥，解密必然失败
                throw new MyJpaPlusException("Multi-key format detected but contains invalid entries: " + invalidEntries
                    + ". Expected format: 'vN:key1,vN:key2'. "
                    + "If this is a single key containing commas, ensure it does not start with 'vN:'.");
            }
            // 所有条目格式有效 — 在多密钥配置中查找目标版本
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
            // 多密钥配置中未找到目标版本 — 只暴露版本号，不暴露密钥材料
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

        // 单密钥模式
        if (version != null && !"v1".equals(version) && !"default".equals(version)) {
            logVersionMismatch(version);
        }
        validateKeyLength(allKeys);
        return allKeys;
    }

    /**
     * 校验最小密钥长度以防止弱密钥字典攻击。
     *
     * @param key 要校验的原始密钥材料
     * @throws MyJpaPlusException 如果密钥长度短于 {@link #MIN_KEY_LENGTH}
     */
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

    /**
     * 使用 PBKDF2WithHmacSHA256 从原始密钥材料派生 AES 密钥。
     *
     * @param rawKeyMaterial 原始密钥材料（密码或编码密钥）
     * @return 派生的 AES 密钥规范
     */
    private static SecretKeySpec deriveKey(String rawKeyMaterial) {
        char[] keyChars = rawKeyMaterial.toCharArray();
        byte[] derived = null;
        try {
            byte[] salt = getSalt();
            PBEKeySpec spec = new PBEKeySpec(keyChars, salt, PBKDF2_ITERATIONS, PBKDF2_KEY_LENGTH);
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

    /**
     * 检查当前是否使用开发盐值（未配置生产盐值且启用了 skip-salt-check）。
     */
    private static boolean isUsingDevSalt() {
        String salt = System.getenv(SALT_ENV);
        if (salt != null && !salt.isEmpty()) {
            return false;
        }
        salt = System.getProperty(SALT_PROPERTY);
        if (salt != null && !salt.isEmpty()) {
            return false;
        }
        String skipCheck = System.getProperty(SKIP_SALT_PROPERTY);
        if (!"true".equalsIgnoreCase(skipCheck)) {
            skipCheck = System.getenv(SKIP_SALT_ENV);
        }
        return "true".equalsIgnoreCase(skipCheck);
    }

    /**
     * 获取 PBKDF2 盐值。优先从环境变量/系统属性获取，未配置时使用开发盐值常量。
     *
     * <p>
     * 生产环境（显式配置了 require-salt=true 或检测到 prod profile）未配置盐值时直接抛异常。
     * 开发环境使用固定的开发盐值常量，确保 JVM 重启后加密数据可恢复。
     *
     * @return 盐值字节数组
     * @throws IllegalStateException 生产环境未配置盐值时抛出
     */
    private static byte[] getSalt() {
        String salt = System.getenv(SALT_ENV);
        if (salt == null || salt.isEmpty()) {
            salt = System.getProperty(SALT_PROPERTY);
        }
        if (salt != null && !salt.isEmpty()) {
            return salt.getBytes(StandardCharsets.UTF_8);
        }
        // 未配置盐值 — 默认拒绝，除非显式跳过
        String skipCheck = System.getProperty(SKIP_SALT_PROPERTY);
        if (!"true".equalsIgnoreCase(skipCheck)) {
            skipCheck = System.getenv(SKIP_SALT_ENV);
        }
        if ("true".equalsIgnoreCase(skipCheck)) {
            if (isProductionEnvironment()) {
                throw new IllegalStateException("Cannot skip PBKDF2 salt check in production environment. "
                    + "Set environment variable " + SALT_ENV + " or system property " + SALT_PROPERTY + ".");
            }
            log.warn("SECURITY: PBKDF2 salt check is skipped via configuration. "
                + "Encrypted data will use a fixed development salt. "
                + "Data encrypted with this salt WILL NOT BE RECOVERABLE if the key changes. "
                + "Set environment variable {} or system property {} for production.", SALT_ENV, SALT_PROPERTY);
            return DEV_SALT.getBytes(StandardCharsets.UTF_8);
        }
        throw new IllegalStateException("PBKDF2 salt must be configured. " + "Set environment variable " + SALT_ENV
            + " or system property " + SALT_PROPERTY + ". " + "Salt is required for PBKDF2 key derivation security. "
            + "To skip this check (development only), set " + SKIP_SALT_PROPERTY + "=true.");
    }

    /**
     * 检查当前是否为生产环境或需要强制要求盐值配置。
     * <p>
     * 委托给 {@link com.zsubera.jpa.autoconfigure.EnvironmentHelper#isProductionEnvironment()}，
     * 消除与 {@code MyJpaPlusAutoConfiguration} 之间的重复代码。
     *
     * @return 如果需要强制要求盐值配置返回 true
     */
    private static boolean isProductionEnvironment() {
        return com.zsubera.jpa.autoconfigure.EnvironmentHelper.isProductionEnvironment();
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
     * @throws MyJpaPlusException 如果解密或加密失败
     */
    public static String reEncrypt(String encryptedValue) {
        if (encryptedValue == null) {
            throw new IllegalArgumentException("encryptedValue must not be null");
        }
        if (!KEY_VALIDATED.get()) {
            validateKeyConfiguration();
        }
        String decrypted = SHARED_INSTANCE.convertToEntityAttribute(encryptedValue);
        return SHARED_INSTANCE.convertToDatabaseColumn(decrypted);
    }
}
