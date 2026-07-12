package com.zsubera.jpa.spec;

import static org.junit.jupiter.api.Assertions.*;

import com.zsubera.jpa.exception.SecurityViolationException;
import org.junit.jupiter.api.Test;

/**
 * Security tests for {@link ConditionNode.FuncNode} whitelist enforcement.
 * Ensures dangerous SQL functions cannot bypass the whitelist.
 */
class ConditionNodeSecurityTest {

    @Test
    void funcNodeRejectsPgSleep() {
        SecurityViolationException ex = assertThrows(SecurityViolationException.class,
            () -> ConditionNode.FuncNode.of("pg_sleep", new Object[] {"fieldName", 10}));
        assertTrue(ex.getMessage().contains("not in whitelist"));
    }

    @Test
    void funcNodeRejectsSleep() {
        assertThrows(SecurityViolationException.class,
            () -> ConditionNode.FuncNode.of("SLEEP", new Object[] {"fieldName", 5}));
    }

    @Test
    void funcNodeRejectsLoadFile() {
        assertThrows(SecurityViolationException.class,
            () -> ConditionNode.FuncNode.of("LOAD_FILE", new Object[] {"fieldName", "/etc/passwd"}));
    }

    @Test
    void funcNodeRejectsExecMethod() {
        assertThrows(SecurityViolationException.class,
            () -> ConditionNode.FuncNode.of("EXEC", new Object[] {"fieldName", "rm -rf /"}));
    }

    @Test
    void funcNodeRejectsNonBooleanSafeFunction() {
        // UPPER is safe but NOT boolean-returning
        SecurityViolationException ex = assertThrows(SecurityViolationException.class,
            () -> ConditionNode.FuncNode.of("UPPER", new Object[] {"fieldName"}));
        assertTrue(ex.getMessage().contains("boolean-returning"));
    }

    @Test
    void funcNodeRejectsRandomString() {
        assertThrows(SecurityViolationException.class,
            () -> ConditionNode.FuncNode.of("XSS_PAYLOAD", new Object[] {"fieldName"}));
    }

    @Test
    void funcNodeRejectsSqlInjectionInFunctionName() {
        assertThrows(SecurityViolationException.class,
            () -> ConditionNode.FuncNode.of("COALESCE;DROP TABLE", new Object[] {"fieldName"}));
    }

    @Test
    void funcNodeAcceptsWhitelistedBooleanFunction() {
        assertDoesNotThrow(() -> ConditionNode.FuncNode.of("COALESCE", new Object[] {"fieldName", "default"}));
    }

    @Test
    void funcNodeAcceptsNvl() {
        assertDoesNotThrow(() -> ConditionNode.FuncNode.of("NVL", new Object[] {"fieldName", "fallback"}));
    }

    @Test
    void funcNodeAcceptsIfNull() {
        assertDoesNotThrow(() -> ConditionNode.FuncNode.of("IFNULL", new Object[] {"fieldName", 0}));
    }

    @Test
    void funcNodeAcceptsDecode() {
        assertDoesNotThrow(() -> ConditionNode.FuncNode.of("DECODE", new Object[] {"fieldName", "A", "B"}));
    }

    @Test
    void funcNodeNullFunctionNameThrows() {
        assertThrows(IllegalArgumentException.class, () -> ConditionNode.FuncNode.of(null, new Object[] {"fieldName"}));
    }

    @Test
    void funcNodeNullParamsThrows() {
        assertThrows(IllegalArgumentException.class, () -> ConditionNode.FuncNode.of("COALESCE", null));
    }

    @Test
    void funcNodeToStringMasksStringParams() {
        ConditionNode.FuncNode node = ConditionNode.FuncNode.of("COALESCE", new Object[] {"password", "default"});
        String str = node.toString();
        assertTrue(str.contains("String[***]"), "ToString should mask string params: " + str);
        assertFalse(str.contains("password"), "ToString must not expose sensitive value: " + str);
    }

    @Test
    void funcNodeToStringMasksNullParams() {
        ConditionNode.FuncNode node = ConditionNode.FuncNode.of("COALESCE", new Object[] {"field", null});
        String str = node.toString();
        assertTrue(str.contains("null"), "ToString should show null literal: " + str);
    }

    @Test
    void funcNodeToStringShowsFunctionName() {
        ConditionNode.FuncNode node = ConditionNode.FuncNode.of("COALESCE", new Object[] {"field"});
        assertTrue(node.toString().contains("COALESCE"));
    }

    @Test
    void funcNodeRejectsCaseInsensitiveDangerousFunction() {
        assertThrows(SecurityViolationException.class,
            () -> ConditionNode.FuncNode.of("pg_SlEeP", new Object[] {"field", 1}));
    }

    @Test
    void funcNodeRejectsStringConcatenationAttempt() {
        assertThrows(SecurityViolationException.class,
            () -> ConditionNode.FuncNode.of("CONCAT" + "; DROP TABLE users", new Object[] {"field"}));
    }

    @Test
    void funcNodeParamsDefensiveCopy() {
        Object[] original = new Object[] {"field", "value"};
        ConditionNode.FuncNode node = ConditionNode.FuncNode.of("COALESCE", original);
        original[1] = "MUTATED";
        // params should not be affected by external mutation
        assertArrayEquals(new Object[] {"field", "value"}, node.params);
    }
}
