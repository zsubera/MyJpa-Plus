package com.zsubera.jpa.converter;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Method;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EncryptionKeyManagerTest {

    @BeforeEach
    void setUp() throws Exception {
        System.setProperty("myjpa.encrypt.key", "1234567890123456");
        System.setProperty("myjpa.encrypt.salt", "test-salt-value");
        EncryptConverter.resetIterationsConfigured();
        EncryptConverter.clearCaches();
    }

    @AfterEach
    void tearDown() throws Exception {
        System.clearProperty("myjpa.encrypt.key");
        System.clearProperty("myjpa.encrypt.salt");
        System.clearProperty("myjpa-plus.encrypt.skip-salt-check");
        EncryptConverter.resetIterationsConfigured();
        EncryptConverter.clearCaches();
        EncryptConverter.setSkipSaltCheck(false);
    }

    @Test
    void setPbkdf2IterationsValidRange() {
        // First set succeeds
        EncryptionKeyManager.setPbkdf2Iterations(100000);
        // Reset iterations configured state, then subsequent sets should succeed
        EncryptionKeyManager.resetIterationsConfigured();
        EncryptionKeyManager.setPbkdf2Iterations(1000000);
        EncryptionKeyManager.resetIterationsConfigured();
        EncryptionKeyManager.setPbkdf2Iterations(10000000);
        EncryptionKeyManager.resetIterationsConfigured();
    }

    @Test
    void setPbkdf2IterationsBelowMinThrows() {
        assertThrows(IllegalArgumentException.class, () -> EncryptionKeyManager.setPbkdf2Iterations(99999));
    }

    @Test
    void setPbkdf2IterationsAboveMaxThrows() {
        assertThrows(IllegalArgumentException.class, () -> EncryptionKeyManager.setPbkdf2Iterations(10000001));
    }

    @Test
    void setSkipSaltCheck() {
        EncryptionKeyManager.setSkipSaltCheck(true);
        assertTrue(EncryptionKeyManager.isSaltCheckSkipped());
        EncryptionKeyManager.setSkipSaltCheck(false);
        assertFalse(EncryptionKeyManager.isSaltCheckSkipped());
    }

    /**
     * P0-2 修复验证：getSalt() 不再缓存盐值，每次从环境变量读取。
     * 修改系统属性后，getSalt() 应返回新值而非旧的缓存值。
     */
    @Test
    void getSalt_readsFromEnvironment每次都() throws Exception {
        Method getSalt = EncryptionKeyManager.class.getDeclaredMethod("getSalt");
        getSalt.setAccessible(true);

        // 设置初始盐值
        System.setProperty("myjpa.encrypt.salt", "salt-v1");
        byte[] salt1 = (byte[])getSalt.invoke(null);
        assertArrayEquals("salt-v1".getBytes(java.nio.charset.StandardCharsets.UTF_8), salt1);

        // 修改盐值（模拟运行时变更）
        System.setProperty("myjpa.encrypt.salt", "salt-v2");
        byte[] salt2 = (byte[])getSalt.invoke(null);

        // 修复前：salt2 == salt1（使用缓存值）
        // 修复后：salt2 != salt1（每次从环境变量读取）
        assertArrayEquals("salt-v2".getBytes(java.nio.charset.StandardCharsets.UTF_8), salt2);
        assertNotEquals(new String(salt1), new String(salt2));
    }

    /**
     * P0-2 修复验证：clearCaches() 不再操作盐值缓存（已移除）。
     */
    @Test
    void clearCaches_doesNotAffectSaltResolution() throws Exception {
        Method getSalt = EncryptionKeyManager.class.getDeclaredMethod("getSalt");
        getSalt.setAccessible(true);

        System.setProperty("myjpa.encrypt.salt", "persistent-salt");
        byte[] before = (byte[])getSalt.invoke(null);

        EncryptConverter.clearCaches();

        byte[] after = (byte[])getSalt.invoke(null);
        assertArrayEquals(before, after);
    }

    /**
     * 回归测试：单条目 v1:key 与多密钥模式下 v1 的密钥派生结果一致。
     * 使用 resolveRawKey 反射验证：当密钥为 "v1:32byteKeyMaterialHere!!" 时，
     * 派生输入应为 "32byteKeyMaterialHere!!"（不含版本前缀），
     * 与多密钥 "v1:32byteKeyMaterialHere!!,v2:other32byteKeyHere!!" 中 v1 的提取结果一致。
     * 修复前：单条目使用 full string（含 v1:），多条目使用 key only，导致后续新增 v2 时数据丢失。
     */
    @Test
    void resolveRawKey_singleEntryVnFormatConsistentWithMultiKey() throws Exception {
        Method resolveRawKey = EncryptionKeyManager.class.getDeclaredMethod("resolveRawKey", String.class);
        resolveRawKey.setAccessible(true);

        // 单条目 v1:key（修复后应仅提取 key after colon）
        System.setProperty("myjpa.encrypt.key", "v1:32byteKeyMaterialHere!!");
        EncryptConverter.clearCaches();
        char[] singleResult = (char[])resolveRawKey.invoke(null, "v1");
        assertArrayEquals("32byteKeyMaterialHere!!".toCharArray(), singleResult,
            "single-entry v1:key should extract key after colon");

        // 多密钥格式中 v1 的提取
        System.setProperty("myjpa.encrypt.key", "v1:32byteKeyMaterialHere!!,v2:other32byteKeyHere!!");
        EncryptConverter.clearCaches();
        char[] multiResult = (char[])resolveRawKey.invoke(null, "v1");
        assertArrayEquals("32byteKeyMaterialHere!!".toCharArray(), multiResult,
            "multi-key v1 should extract key after colon");

        // 两种格式的 v1 派生密钥必须一致
        assertArrayEquals(singleResult, multiResult,
            "single-entry v1 and multi-key v1 must produce identical key material");
    }

    /**
     * 回归测试：纯密钥格式（无 vN: 前缀）不受影响，仍以完整字符串作为派生输入。
     */
    @Test
    void resolveRawKey_plainKeyWithoutVPrefix_unchanged() throws Exception {
        Method resolveRawKey = EncryptionKeyManager.class.getDeclaredMethod("resolveRawKey", String.class);
        resolveRawKey.setAccessible(true);

        String plainKey = "thisIsAPlain32ByteKeyForDerivation";
        System.setProperty("myjpa.encrypt.key", plainKey);
        EncryptConverter.clearCaches();
        char[] result = (char[])resolveRawKey.invoke(null, "v1");
        assertArrayEquals(plainKey.toCharArray(), result, "plain key without vN: prefix should be used as-is");
    }

    /**
     * 回归测试：密钥派生实际一致性验证——使用单条目 v1:key 加密后，
     * 切换到多密钥格式（含相同 v1 条目）应能解密同一数据。
     */
    @Test
    void encryptAndDecrypt_consistentBetweenSingleAndMultiKey() {
        System.setProperty("myjpa.encrypt.key", "v1:32byteKeyMaterialHere!!");
        System.setProperty("myjpa.encrypt.salt", "test-consistent-salt");
        EncryptConverter.clearCaches();

        EncryptConverter converter = new EncryptConverter();
        String encrypted = converter.convertToDatabaseColumn("secret-data");
        assertNotNull(encrypted);
        assertTrue(encrypted.startsWith("v1:"));

        // 切换到多密钥格式（v1 密钥材料相同）
        System.setProperty("myjpa.encrypt.key", "v1:32byteKeyMaterialHere!!,v2:other32byteKeyHere!!");
        EncryptConverter.clearCaches();

        String decrypted = converter.convertToEntityAttribute(encrypted);
        assertEquals("secret-data", decrypted, "Data encrypted with single-entry v1:key must be decryptable "
            + "after switching to multi-key format with same v1 entry");
    }
}
