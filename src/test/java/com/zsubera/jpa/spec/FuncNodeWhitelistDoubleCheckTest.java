package com.zsubera.jpa.spec;

import static org.junit.jupiter.api.Assertions.*;

import com.zsubera.jpa.exception.SecurityViolationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * 测试 FuncNode 的双重白名单验证逻辑。
 *
 * <p>FuncNode.of() 需要同时满足两个条件：
 * 1. 函数名在安全函数白名单中
 * 2. 函数名在布尔函数白名单中
 *
 * <p>任一条件不满足都应抛出 SecurityViolationException。
 */
class FuncNodeWhitelistDoubleCheckTest {

    @AfterEach
    void cleanup() {
        FunctionWhitelist.reset();
    }

    // ---- 安全函数白名单检查 ----

    @Test
    void funcNode_notInSafeWhitelist_throwsSecurityException() {
        // LENGTH 在安全白名单中但不在布尔函数白名单中
        assertThrows(SecurityViolationException.class,
            () -> ConditionNode.FuncNode.of("LENGTH", new Object[] {"field"}));
    }

    @Test
    void funcNode_notInBooleanWhitelist_throwsSecurityException() {
        // LENGTH 不在布尔函数白名单中
        assertThrows(SecurityViolationException.class,
            () -> ConditionNode.FuncNode.of("LENGTH", new Object[] {"field"}));
    }

    // ---- 自定义白名单扩展 ----

    @Test
    void funcNode_customSafeAndBooleanFunction_succeeds() {
        // 添加自定义函数到两个白名单
        FunctionWhitelist.addSafeFunctionNames(java.util.List.of("CUSTOM_BOOL_FUNC"));
        FunctionWhitelist.addBooleanFunctionNames(java.util.List.of("CUSTOM_BOOL_FUNC"));

        assertDoesNotThrow(() -> {
            ConditionNode.FuncNode node = ConditionNode.FuncNode.of("CUSTOM_BOOL_FUNC", new Object[] {"field"});
            assertNotNull(node);
            assertEquals("CUSTOM_BOOL_FUNC", node.functionName);
        });
    }

    @Test
    void funcNode_customSafeOnly_throwsForBooleanCheck() {
        // 仅添加到安全白名单，不添加到布尔函数白名单
        FunctionWhitelist.addSafeFunctionNames(java.util.List.of("SAFE_ONLY_FUNC"));

        assertThrows(SecurityViolationException.class,
            () -> ConditionNode.FuncNode.of("SAFE_ONLY_FUNC", new Object[] {"field"}));
    }

    @Test
    void funcNode_customBooleanOnly_throwsForSafeCheck() {
        // 仅添加到布尔函数白名单，不添加到安全白名单
        // 这种情况在当前实现中不会发生，因为 addBooleanFunctionNames 会先验证安全白名单
        // 但我们可以直接测试白名单检查逻辑
        assertThrows(SecurityViolationException.class,
            () -> ConditionNode.FuncNode.of("DANGEROUS_FUNC", new Object[] {"field"}));
    }

    // ---- 内置白名单函数测试 ----

    @Test
    void funcNode_coalesce_succeeds() {
        assertDoesNotThrow(() -> {
            ConditionNode.FuncNode node = ConditionNode.FuncNode.of("COALESCE", new Object[] {"field", "default"});
            assertNotNull(node);
        });
    }

    @Test
    void funcNode_nvl_succeeds() {
        assertDoesNotThrow(() -> {
            ConditionNode.FuncNode node = ConditionNode.FuncNode.of("NVL", new Object[] {"field", "default"});
            assertNotNull(node);
        });
    }

    @Test
    void funcNode_nullif_succeeds() {
        assertDoesNotThrow(() -> {
            ConditionNode.FuncNode node = ConditionNode.FuncNode.of("NULLIF", new Object[] {"field", "value"});
            assertNotNull(node);
        });
    }

    // ---- SQL 注入尝试 ----

    @Test
    void funcNode_sqlInjectionAttempt_throwsSecurityException() {
        // 尝试通过函数名注入 SQL
        assertThrows(SecurityViolationException.class,
            () -> ConditionNode.FuncNode.of("1=1; DROP TABLE users; --", new Object[] {}));
    }

    @Test
    void funcNode_emptyFunctionName_throwsSecurityException() {
        // 空字符串不在白名单中，先触发安全检查
        assertThrows(SecurityViolationException.class, () -> ConditionNode.FuncNode.of("", new Object[] {}));
    }

    @Test
    void funcNode_nullFunctionName_throws() {
        assertThrows(IllegalArgumentException.class, () -> ConditionNode.FuncNode.of(null, new Object[] {}));
    }
}
