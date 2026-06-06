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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private static final String REQUIRE_SALT_PROPERTY = "myjpa-plus.encrypt.require-salt";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int PBKDF2_ITERATIONS = 600_000;
    private static final int PBKDF2_KEY_LENGTH = 256;
    private static final Logger log = LoggerFactory.getLogger(EncryptConverter.class);

    /** 防止弱密钥攻击的最小密钥长度（字符数）。 */
    private static final int MIN_KEY_LENGTH = 16;

    /** 严格模式开关，通过系统属性控制。默认为 false。 */
    private static final boolean STRICT_MODE = Boolean.parseBoolean(System.getProperty(STRICT_MODE_PROPERTY, "false"));

    /** 按版本缓存的密钥规范，避免重复读取环境变量和 KDF 派生。 */
    private static final ConcurrentMap<String, SecretKeySpec> KEY_CACHE = new ConcurrentHashMap<>();

    /** 缓存的密钥规范最大数量，防止恶意版本前缀导致内存耗尽。 */
    private static final int MAX_KEY_CACHE_SIZE = 16;

    /** 缓存的密钥版本，避免重复读取环境变量。 */
    private static volatile String cachedKeyVersion;

    /** 上次刷新密钥版本的时间戳。 */
    private static volatile long lastKeyVersionRefresh;

    /** 密钥版本缓存刷新间隔（毫秒），默认 5 分钟。 */
    private static final long KEY_VERSION_REFRESH_INTERVAL_MS = 300_000L;

    /** 线程安全的盐值缓存，替代 System.setProperty() 用法。 */
    private static final ConcurrentMap<String, byte[]> SALT_CACHE = new ConcurrentHashMap<>();

    /** 盐值文件创建的锁对象，防止 computeIfAbsent 中的竞态条件。 */
    private static final Object SALT_FILE_LOCK = new Object();

    /** 跟踪密钥验证是否已执行的标志。 */
    private static volatile boolean keyValidated = false;

    /**
     * 清除所有缓存的密钥和版本信息。仅用于测试环境。
     */
    static void clearCacheForTesting() {
        KEY_CACHE.clear();
        cachedKeyVersion = null;
        lastKeyVersionRefresh = 0;
        keyValidated = false;
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
        log.info("Encryption key version cache and salt cache refreshed");
    }

    /**
     * 在启动时验证加密密钥配置。应在应用程序初始化期间调用此方法， 以便及早检测缺失的密钥配置。
     *
     * <p>
     * 如果密钥未配置且启用了严格模式，抛出 IllegalStateException。否则记录警告。
     */
    public static void validateKeyConfiguration() {
        if (keyValidated) {
            return;
        }
        String keyEnv = System.getenv(KEY_ENV);
        String keyProp = System.getProperty(KEY_PROPERTY);
        if ((keyEnv == null || keyEnv.isEmpty()) && (keyProp == null || keyProp.isEmpty())) {
            if (STRICT_MODE) {
                throw new IllegalStateException("Encryption key not configured. Set environment variable " + KEY_ENV
                    + " or system property " + KEY_PROPERTY + " before starting the application. "
                    + "Strict mode is enabled, application cannot start without encryption key.");
            }
            log.warn("SECURITY: Encryption key not configured. Set environment variable {} or system property {}.",
                KEY_ENV, KEY_PROPERTY);
        } else {
            String key = (keyEnv != null && !keyEnv.isEmpty()) ? keyEnv : keyProp;
            if (key != null && key.length() < MIN_KEY_LENGTH) {
                throw new MyJpaPlusException("Encryption key must be at least " + MIN_KEY_LENGTH + " characters. "
                    + "Current length: " + key.length() + ".");
            }
            // 当使用系统属性配置密钥时发出警告
            // 系统属性在 JVM 全局可见，可能在进程列表中暴露
            if (keyProp != null && !keyProp.isEmpty() && (keyEnv == null || keyEnv.isEmpty())) {
                log.warn("SECURITY: Encryption key configured via system property '{}'. "
                    + "System properties are JVM-wide visible and may be exposed in process listings (e.g., /proc/PID/cmdline). "
                    + "Use environment variable '{}' for production environments.", KEY_PROPERTY, KEY_ENV);
            }
        }
        keyValidated = true;
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
            // 在同步块内重新读取时间戳以避免过期检查
            now = System.currentTimeMillis();
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
        // 加密前校验密钥配置以实现快速失败
        if (!keyValidated) {
            validateKeyConfiguration();
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
            log.error("Encryption failed", e);
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
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] decrypted = cipher.doFinal(encrypted);
            return new String(decrypted, StandardCharsets.UTF_8);
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
        SecretKeySpec existing = KEY_CACHE.get(cacheKey);
        if (existing != null) {
            return existing;
        }
        // 添加新条目前检查缓存大小限制
        if (KEY_CACHE.size() >= MAX_KEY_CACHE_SIZE && !KEY_CACHE.containsKey(cacheKey)) {
            throw new MyJpaPlusException(
                "Encryption key cache is full (" + MAX_KEY_CACHE_SIZE + " entries). " + "Cannot load key version '"
                    + cacheKey + "'. " + "This may indicate a malicious attempt to exhaust CPU via PBKDF2 derivation. "
                    + "Clear cache or increase MAX_KEY_CACHE_SIZE if this is legitimate key rotation.");
        }
        return KEY_CACHE.computeIfAbsent(cacheKey, v -> {
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

        // 使用显式正则表达式匹配多密钥格式 "vN:key,vN:key"
        // 之前的检测（contains(":") && contains(",")）在单个密钥同时包含这两个字符时存在歧义。
        // 新格式要求每个条目匹配 "vN:key" 模式。
        if (allKeys.matches(".*v\\d+:.*,.+")) {
            String[] entries = allKeys.split(",");
            boolean validMultiKey = true;
            for (String entry : entries) {
                entry = entry.trim();
                if (!entry.matches("v\\d+:.+")) {
                    log.warn("SECURITY: Invalid multi-key entry format '{}'. Expected 'vN:key'. "
                        + "Falling back to single-key mode.", entry);
                    validMultiKey = false;
                    break;
                }
            }
            if (validMultiKey) {
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
        }

        // 单密钥模式：如果指定了版本但没有找到对应密钥，使用主密钥
        if (version != null && !version.equals("v1") && !version.equals("default")) {
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
        if (key != null && key.length() < MIN_KEY_LENGTH) {
            throw new MyJpaPlusException("Encryption key must be at least " + MIN_KEY_LENGTH + " characters. "
                + "Current length: " + key.length() + ". "
                + "Short keys are vulnerable to dictionary attacks even with PBKDF2 derivation.");
        }
    }

    private static void logVersionMismatch(String version) {
        // 使用 java.util.logging 避免引入额外依赖
        log.warn("Key version '{}' not found in multi-key configuration. Using primary key.", version);
    }

    /**
     * 使用 PBKDF2WithHmacSHA256 从原始密钥材料派生 AES 密钥。
     *
     * @param rawKeyMaterial 原始密钥材料（密码或编码密钥）
     * @return 派生的 AES 密钥规范
     */
    private static SecretKeySpec deriveKey(String rawKeyMaterial) {
        try {
            // 始终通过 PBKDF2 派生密钥以保证安全性——已移除直接使用快捷方式
            // 直接使用原始密钥字节存在安全风险，因为低熵密码
            // 可以直接用作 AES 密钥。
            byte[] salt = getSalt();
            PBEKeySpec spec = new PBEKeySpec(rawKeyMaterial.toCharArray(), salt, PBKDF2_ITERATIONS, PBKDF2_KEY_LENGTH);
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            byte[] derived = factory.generateSecret(spec).getEncoded();
            return new SecretKeySpec(derived, "AES");
        } catch (GeneralSecurityException e) {
            // 使用 MyJpaPlusException 替代 IllegalStateException 保持一致性
            throw new MyJpaPlusException("Failed to derive encryption key via PBKDF2", e);
        }
    }

    /**
     * 检查当前是否为生产环境或需要强制要求盐值配置。
     *
     * <p>
     * 检测优先级：
     * <ol>
     * <li>显式配置 {@code myjpa-plus.encrypt.require-salt=true}（最高优先级）</li>
     * <li>系统属性 {@code spring.profiles.active} 包含 prod/production</li>
     * <li>环境变量 {@code SPRING_PROFILES_ACTIVE} 包含 prod/production</li>
     * <li>环境变量 {@code SPRING_PROFILES} 包含 prod/production</li>
     * </ol>
     *
     * @return 如果需要强制要求盐值配置返回 true
     */
    private static boolean isProductionEnvironment() {
        // 显式配置优先级最高
        String requireSalt = System.getProperty(REQUIRE_SALT_PROPERTY);
        if ("true".equalsIgnoreCase(requireSalt)) {
            return true;
        }
        requireSalt = System.getenv("MYJPA_ENCRYPT_REQUIRE_SALT");
        if ("true".equalsIgnoreCase(requireSalt)) {
            return true;
        }
        // 检查系统属性
        String profile = System.getProperty("spring.profiles.active", "");
        if (profile.contains("prod") || profile.contains("production")) {
            return true;
        }
        // 检查环境变量
        profile = System.getenv("SPRING_PROFILES_ACTIVE");
        if (profile != null && (profile.contains("prod") || profile.contains("production"))) {
            return true;
        }
        profile = System.getenv("SPRING_PROFILES");
        return profile != null && (profile.contains("prod") || profile.contains("production"));
    }

    /**
     * 获取 PBKDF2 盐值。优先从环境变量获取，回退到系统属性。 生产环境（spring.profiles.active 包含 prod/production）强制要求配置盐值。
     *
     * <p>
     * <strong>安全改进：</strong>非生产环境不再使用硬编码的默认盐值，而是生成随机盐值并记录警告。 随机盐值在 JVM 生命周期内保持一致（缓存在 SALT_CACHE 中），确保同一 JVM
     * 内的加密/解密操作使用相同盐值。
     *
     * <p>
     * <strong>改进：</strong>非生产环境的随机盐值会持久化到本地文件（{@code ~/.myjpa-plus/.salt}）， 确保跨 JVM 重启后盐值保持一致，避免之前加密的数据无法解密。
     *
     * <p>
     * <strong>建议：</strong>在 Windows 环境下，建议通过环境变量 {@code MYJPA_ENCRYPT_SALT} 配置盐值， 以避免多进程间的文件锁竞争问题。
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
            if (isProductionEnvironment()) {
                throw new IllegalStateException("PBKDF2 salt must be configured in production. "
                    + "Set environment variable " + SALT_ENV + " or system property " + SALT_PROPERTY);
            }
            // 使用 synchronized 块防止盐值文件创建中的竞态条件。
            // ConcurrentHashMap.computeIfAbsent 在竞争下可能多次执行 lambda，
            // 导致不一致的盐值写入文件。
            byte[] cached = SALT_CACHE.get("internal");
            if (cached != null) {
                return cached;
            }
            synchronized (SALT_FILE_LOCK) {
                cached = SALT_CACHE.get("internal");
                if (cached != null) {
                    return cached;
                }
                byte[] internalSalt = loadOrCreateSalt();
                SALT_CACHE.put("internal", internalSalt);
                return internalSalt;
            }
        }
        return salt.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * 从文件加载或创建盐值。必须在 {@link #SALT_FILE_LOCK} 同步块内调用。
     *
     * <p>
     * <strong>改进：</strong>使用文件锁（{@link java.nio.channels.FileChannel}）替代 synchronized 块，
     * 防止多进程同时写入盐值文件导致数据损坏。写入后再次验证文件内容，确保一致性。
     *
     * @return 盐值字节数组
     */
    private static byte[] loadOrCreateSalt() {
        // 使用 $HOME/.myjpa-plus/.salt 以获得更好的安全性（非全局可读的临时目录）
        java.io.File homeDir = new java.io.File(System.getProperty("user.home"), ".myjpa-plus");
        java.io.File saltFile = new java.io.File(homeDir, ".salt");
        if (saltFile.exists()) {
            try {
                byte[] fileSalt = java.nio.file.Files.readAllBytes(saltFile.toPath());
                if (fileSalt.length > 0) {
                    log.info("Loaded persisted PBKDF2 salt from {}", saltFile.getAbsolutePath());
                    return fileSalt;
                }
            } catch (java.io.IOException e) {
                log.warn("Failed to read salt file: {}", e.getMessage());
            }
        }
        byte[] randomSalt = generateRandomSalt().getBytes(StandardCharsets.UTF_8);
        // 尝试持久化盐值以保证跨重启的一致性
        try {
            if (!homeDir.exists()) {
                boolean created = homeDir.mkdirs();
                if (!created && !homeDir.exists()) {
                    // 目录创建失败则中止，避免无意义的文件写入
                    log.error(
                        "SECURITY: Failed to create directory: {}. "
                            + "Salt cannot be persisted. Set environment variable {} to ensure consistent encryption.",
                        homeDir.getAbsolutePath(), SALT_ENV);
                    return randomSalt;
                }
            }
            // 使用带重试的文件锁以兼容 Windows
            // Windows 文件锁在 tryLock() 时可能不可靠，因此我们重试
            java.nio.file.Path saltPath = saltFile.toPath();
            try (java.nio.channels.FileChannel channel =
                java.nio.channels.FileChannel.open(saltPath, java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.WRITE, java.nio.file.StandardOpenOption.READ)) {
                // Windows 文件锁可靠性重试循环（3 次尝试，200ms 延迟）
                java.nio.channels.FileLock lock = null;
                for (int attempt = 0; attempt < 3; attempt++) {
                    lock = channel.tryLock();
                    if (lock != null) {
                        break;
                    }
                    // 无法获取锁，等待并重试
                    try {
                        Thread.sleep(200);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
                if (lock != null) {
                    try {
                        // 重新检查是否另一个进程已写入盐值
                        if (channel.size() > 0) {
                            channel.position(0);
                            java.nio.ByteBuffer buffer = java.nio.ByteBuffer.allocate((int)channel.size());
                            channel.read(buffer);
                            buffer.flip();
                            byte[] existingSalt = new byte[buffer.remaining()];
                            buffer.get(existingSalt);
                            if (existingSalt.length > 0) {
                                log.info("Another process already wrote salt file, using existing salt");
                                return existingSalt;
                            }
                        }
                        // 在持有文件锁时写入盐值
                        channel.position(0);
                        channel.truncate(0);
                        channel.write(java.nio.ByteBuffer.wrap(randomSalt));
                        channel.force(true);
                        // 在释放锁之前验证盐值文件写入
                        channel.position(0);
                        java.nio.ByteBuffer verifyBuf = java.nio.ByteBuffer.allocate((int)channel.size());
                        channel.read(verifyBuf);
                        verifyBuf.flip();
                        byte[] writtenSalt = new byte[verifyBuf.remaining()];
                        verifyBuf.get(writtenSalt);
                        if (!java.util.Arrays.equals(randomSalt, writtenSalt)) {
                            throw new SecurityException("SECURITY: Salt file write verification failed. "
                                + "Written bytes do not match generated salt. " + "Set environment variable " + SALT_ENV
                                + " to ensure consistent encryption.");
                        }
                    } finally {
                        lock.release();
                    }
                } else {
                    // 无法获取锁，另一个进程正在写入。
                    // 使用更长的超时重试锁获取，而非不加锁写入。
                    for (int retryAttempt = 0; retryAttempt < 5; retryAttempt++) {
                        try {
                            Thread.sleep(200);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                        lock = channel.tryLock();
                        if (lock != null) {
                            break;
                        }
                    }
                    if (lock != null) {
                        try {
                            // 在等待期间另一个进程可能已写入盐值
                            if (channel.size() > 0) {
                                channel.position(0);
                                java.nio.ByteBuffer buffer = java.nio.ByteBuffer.allocate((int)channel.size());
                                channel.read(buffer);
                                buffer.flip();
                                byte[] existingSalt = new byte[buffer.remaining()];
                                buffer.get(existingSalt);
                                if (existingSalt.length > 0) {
                                    return existingSalt;
                                }
                            }
                            channel.position(0);
                            channel.truncate(0);
                            channel.write(java.nio.ByteBuffer.wrap(randomSalt));
                            channel.force(true);
                            // 在释放锁之前验证
                            channel.position(0);
                            java.nio.ByteBuffer verifyBuf = java.nio.ByteBuffer.allocate((int)channel.size());
                            channel.read(verifyBuf);
                            verifyBuf.flip();
                            byte[] writtenSalt = new byte[verifyBuf.remaining()];
                            verifyBuf.get(writtenSalt);
                            if (!java.util.Arrays.equals(randomSalt, writtenSalt)) {
                                throw new SecurityException("SECURITY: Salt file write verification failed. "
                                    + "Set environment variable " + SALT_ENV + " to ensure consistent encryption.");
                            }
                        } finally {
                            lock.release();
                        }
                    } else {
                        // 最后手段：读取文件——另一个进程可能已成功
                        byte[] fileSalt = java.nio.file.Files.readAllBytes(saltPath);
                        if (fileSalt.length > 0) {
                            return fileSalt;
                        }
                        log.warn("SECURITY: Could not acquire salt file lock and no salt found. "
                            + "Using non-persisted random salt. Set environment variable {}", SALT_ENV);
                    }
                }
            }
            // 设置文件权限——Unix 使用 POSIX，Windows 使用 ACL
            setSaltFilePermissions(homeDir, saltFile);
            log.warn(
                "SECURITY: Generated and persisted PBKDF2 salt to {}. "
                    + "Set environment variable {} for consistent encryption across environments.",
                saltFile.getAbsolutePath(), SALT_ENV);
        } catch (java.io.IOException e) {
            log.error(
                "SECURITY: Using non-persisted random salt. Previous encrypted data WILL BE UNRECOVERABLE after restart. "
                    + "Set environment variable {} or system property {}",
                SALT_ENV, SALT_PROPERTY);
        }
        return randomSalt;
    }

    /**
     * 设置盐值文件和目录的权限。在 Unix 系统上使用 POSIX 权限，在 Windows 上使用 Java NIO ACL。
     *
     * <p>
     * <strong>改进：</strong>Windows 上仅使用 Java NIO {@link java.nio.file.attribute.AclFileAttributeView} 设置 ACL 权限，移除了
     * icacls 命令行回退以防止命令注入风险。如果 NIO ACL 设置失败，仅记录 DEBUG 日志。
     *
     * @param homeDir 盐值目录
     * @param saltFile 盐值文件
     */
    private static void setSaltFilePermissions(java.io.File homeDir, java.io.File saltFile) {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) {
            // Windows - 仅使用 Java NIO ACL，移除 icacls 命令行回退以防止命令注入风险
            setWindowsPermissionsNio(saltFile);
        } else {
            // Unix/Linux/macOS - 使用 POSIX 权限
            try {
                java.util.Set<java.nio.file.attribute.PosixFilePermission> dirPerms =
                    java.util.EnumSet.of(java.nio.file.attribute.PosixFilePermission.OWNER_READ,
                        java.nio.file.attribute.PosixFilePermission.OWNER_WRITE,
                        java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE);
                java.nio.file.Files.setPosixFilePermissions(homeDir.toPath(), dirPerms);
                java.util.Set<java.nio.file.attribute.PosixFilePermission> filePerms =
                    java.util.EnumSet.of(java.nio.file.attribute.PosixFilePermission.OWNER_READ,
                        java.nio.file.attribute.PosixFilePermission.OWNER_WRITE);
                java.nio.file.Files.setPosixFilePermissions(saltFile.toPath(), filePerms);
            } catch (UnsupportedOperationException | java.io.IOException permEx) {
                log.debug("Cannot set POSIX permissions on salt file: {}", permEx.getMessage());
            }
        }
    }

    /**
     * 使用 Java NIO 设置 Windows 文件 ACL 权限。
     *
     * <p>
     * <strong>改进：</strong>移除了 icacls 命令行回退以防止命令注入风险。如果 NIO ACL 设置失败，仅记录 DEBUG 日志。
     *
     * @param saltFile 盐值文件
     */
    private static void setWindowsPermissionsNio(java.io.File saltFile) {
        try {
            java.nio.file.attribute.AclFileAttributeView view = java.nio.file.Files
                .getFileAttributeView(saltFile.toPath(), java.nio.file.attribute.AclFileAttributeView.class);
            if (view != null) {
                java.nio.file.attribute.UserPrincipal currentUser = java.nio.file.FileSystems.getDefault()
                    .getUserPrincipalLookupService().lookupPrincipalByName(System.getProperty("user.name"));
                java.nio.file.attribute.AclEntry entry = java.nio.file.attribute.AclEntry.newBuilder()
                    .setType(java.nio.file.attribute.AclEntryType.ALLOW).setPrincipal(currentUser)
                    .setPermissions(java.util.EnumSet.of(java.nio.file.attribute.AclEntryPermission.READ_DATA,
                        java.nio.file.attribute.AclEntryPermission.WRITE_DATA))
                    .build();
                view.setAcl(java.util.List.of(entry));
                log.debug("Set Windows ACL on salt file via Java NIO");
            }
        } catch (Exception e) {
            // 仅记录日志，不回退到 icacls 命令行（防止命令注入风险）
            log.debug("Cannot set Windows ACL via Java NIO: {}. "
                + "Set environment variable {} to ensure consistent encryption.", e.getMessage(), SALT_ENV);
        }
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
