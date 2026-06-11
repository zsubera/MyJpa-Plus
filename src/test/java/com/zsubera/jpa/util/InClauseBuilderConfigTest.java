package com.zsubera.jpa.util;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * InClauseBuilder 配置验证测试。
 *
 * <p>测试 P1-3 修复：配置验证不一致
 */
class InClauseBuilderConfigTest {

    @AfterEach
    void cleanup() {
        System.clearProperty("myjpa-plus.in-clause-max-size");
        System.clearProperty("myjpa-plus.in-clause-hard-limit");
        // 重置为默认配置
        InClauseBuilder.setConfig(new InClauseBuilder.Config(1000, 5000));
    }

    /**
     * 测试有效配置应该被接受。
     *
     * <p>P1-3 修复验证：确保配置验证正确
     */
    @Test
    void validConfigShouldBeAccepted() {
        InClauseBuilder.Config config = new InClauseBuilder.Config(1000, 5000);
        assertEquals(1000, config.maxInClauseSize());
        assertEquals(5000, config.hardLimit());
    }

    /**
     * 测试 hardLimit < maxInClauseSize 应该抛出异常。
     */
    @Test
    void hardLimitLessThanMaxSizeShouldThrow() {
        assertThrows(IllegalArgumentException.class, () -> {
            new InClauseBuilder.Config(2000, 1000);
        });
    }

    /**
     * 测试系统属性配置 hardLimit < maxSize 应该自动调整。
     *
     * <p>P1-3 修复验证：确保自动调整 hardLimit
     */
    @Test
    void systemPropertyHardLimitLessThanMaxSizeShouldBeAdjusted() {
        System.setProperty("myjpa-plus.in-clause-max-size", "2000");
        System.setProperty("myjpa-plus.in-clause-hard-limit", "1000");

        InClauseBuilder.Config config = InClauseBuilder.Config.loadFromSystemProperties();

        // hardLimit 应该被调整为 maxSize
        assertEquals(2000, config.maxInClauseSize());
        assertEquals(2000, config.hardLimit());
    }

    /**
     * 测试有效系统属性配置应该被接受。
     */
    @Test
    void validSystemPropertiesShouldBeAccepted() {
        System.setProperty("myjpa-plus.in-clause-max-size", "500");
        System.setProperty("myjpa-plus.in-clause-hard-limit", "2000");

        InClauseBuilder.Config config = InClauseBuilder.Config.loadFromSystemProperties();

        assertEquals(500, config.maxInClauseSize());
        assertEquals(2000, config.hardLimit());
    }

    /**
     * 测试无效系统属性应该使用默认值。
     */
    @Test
    void invalidSystemPropertiesShouldUseDefaults() {
        System.setProperty("myjpa-plus.in-clause-max-size", "invalid");
        System.setProperty("myjpa-plus.in-clause-hard-limit", "invalid");

        InClauseBuilder.Config config = InClauseBuilder.Config.loadFromSystemProperties();

        assertEquals(1000, config.maxInClauseSize());
        assertEquals(5000, config.hardLimit());
    }

    /**
     * 测试超出上限的系统属性应该被限制。
     */
    @Test
    void exceededUpperLimitSystemPropertiesShouldBeLimited() {
        System.setProperty("myjpa-plus.in-clause-max-size", "200000");
        System.setProperty("myjpa-plus.in-clause-hard-limit", "200000");

        InClauseBuilder.Config config = InClauseBuilder.Config.loadFromSystemProperties();

        assertEquals(100000, config.maxInClauseSize());
        assertEquals(100000, config.hardLimit());
    }

    /**
     * 测试 setConfig 应该原子切换配置。
     */
    @Test
    void setConfigShouldAtomicallySwitch() {
        InClauseBuilder.Config original = InClauseBuilder.getConfig();
        try {
            InClauseBuilder.Config newConfig = new InClauseBuilder.Config(500, 1000);
            InClauseBuilder.setConfig(newConfig);
            assertEquals(500, InClauseBuilder.getMaxInClauseSize());
            assertEquals(1000, InClauseBuilder.getHardLimit());
        } finally {
            InClauseBuilder.setConfig(original);
        }
    }
}
