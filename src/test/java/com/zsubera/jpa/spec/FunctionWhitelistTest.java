package com.zsubera.jpa.spec;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class FunctionWhitelistTest {

    @AfterEach
    void cleanup() {
        FunctionWhitelist.reset();
    }

    @Test
    void addSafeFunctionNames() {
        FunctionWhitelist.addSafeFunctionNames(List.of("CUSTOM_FUNC", "another_func"));
        assertTrue(FunctionWhitelist.containsSafeFunction("CUSTOM_FUNC"));
        assertTrue(FunctionWhitelist.containsSafeFunction("ANOTHER_FUNC"));
        assertFalse(FunctionWhitelist.containsSafeFunction("UNKNOWN"));
    }

    @Test
    void addBooleanFunctionNames() {
        FunctionWhitelist.addBooleanFunctionNames(List.of("IS_ACTIVE", "has_role"));
        assertTrue(FunctionWhitelist.containsBooleanFunction("IS_ACTIVE"));
        assertTrue(FunctionWhitelist.containsBooleanFunction("HAS_ROLE"));
        assertFalse(FunctionWhitelist.containsBooleanFunction("UNKNOWN"));
    }

    @Test
    void invalidFunctionNameThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> FunctionWhitelist.addSafeFunctionNames(List.of("DROP TABLE")));
    }

    @Test
    void nullFunctionNamesThrows() {
        assertThrows(IllegalArgumentException.class, () -> FunctionWhitelist.addSafeFunctionNames(null));
    }

    @Test
    void emptyNameIsSkipped() {
        FunctionWhitelist.addSafeFunctionNames(List.of(""));
        assertFalse(FunctionWhitelist.containsSafeFunction(""));
    }

    @Test
    void nullNameIsSkipped() {
        java.util.List<String> names = new java.util.ArrayList<>();
        names.add(null);
        FunctionWhitelist.addSafeFunctionNames(names);
    }

    @Test
    void resetClearsAll() {
        FunctionWhitelist.addSafeFunctionNames(List.of("MY_FUNC"));
        FunctionWhitelist.addBooleanFunctionNames(List.of("MY_BOOL"));
        FunctionWhitelist.reset();
        assertFalse(FunctionWhitelist.containsSafeFunction("MY_FUNC"));
        assertFalse(FunctionWhitelist.containsBooleanFunction("MY_BOOL"));
    }

    @Test
    void nameIsUpperCaseNormalized() {
        FunctionWhitelist.addSafeFunctionNames(List.of("mixedCase"));
        assertTrue(FunctionWhitelist.containsSafeFunction("MIXEDCASE"));
    }

    @Test
    void nameWithNumbersIsValid() {
        FunctionWhitelist.addSafeFunctionNames(List.of("FUNC123"));
        assertTrue(FunctionWhitelist.containsSafeFunction("FUNC123"));
    }

    @Test
    void nameStartingWithDigitIsInvalid() {
        assertThrows(IllegalArgumentException.class, () -> FunctionWhitelist.addSafeFunctionNames(List.of("1FUNC")));
    }

    @Test
    void freezeUpdatesSnapshot() {
        FunctionWhitelist.addSafeFunctionNames(List.of("BEFORE_FREEZE"));
        FunctionWhitelist.freezeExtraFunctionNames();
        assertTrue(FunctionWhitelist.containsSafeFunction("BEFORE_FREEZE"));
    }
}
