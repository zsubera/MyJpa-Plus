package com.zsubera.jpa.spec;

import static org.junit.jupiter.api.Assertions.*;

import com.zsubera.jpa.exception.SecurityViolationException;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class FuncNodeTest {

    @AfterEach
    void cleanup() {
        FunctionWhitelist.reset();
    }

    @Test
    void of_validBooleanFunction_createsNode() {
        ConditionNode.FuncNode node = ConditionNode.FuncNode.of("COALESCE", new Object[] {"field", "default"});
        assertNotNull(node);
        assertEquals("COALESCE", node.functionName);
        assertArrayEquals(new Object[] {"field", "default"}, node.params);
    }

    @Test
    void of_caseInsensitive_createsNode() {
        ConditionNode.FuncNode node = ConditionNode.FuncNode.of("coalesce", new Object[] {"field"});
        assertNotNull(node);
        assertEquals("coalesce", node.functionName);
    }

    @Test
    void of_nullFunctionName_throws() {
        assertThrows(IllegalArgumentException.class, () -> ConditionNode.FuncNode.of(null, new Object[] {}));
    }

    @Test
    void of_nullParams_throws() {
        assertThrows(IllegalArgumentException.class, () -> ConditionNode.FuncNode.of("COALESCE", null));
    }

    @Test
    void of_nonWhitelistedFunction_throwsSecurityException() {
        assertThrows(SecurityViolationException.class,
            () -> ConditionNode.FuncNode.of("PG_SLEEP", new Object[] {"field", 1}));
    }

    @Test
    void of_nonBooleanFunction_throwsSecurityException() {
        assertThrows(SecurityViolationException.class,
            () -> ConditionNode.FuncNode.of("LENGTH", new Object[] {"field"}));
    }

    @Test
    void of_allBooleanFunctions_work() {
        for (String fn : ConditionBuilder.BOOLEAN_FUNCTION_NAMES) {
            ConditionNode.FuncNode node = ConditionNode.FuncNode.of(fn, new Object[] {"field"});
            assertNotNull(node, "Function " + fn + " should be accepted");
        }
    }

    @Test
    void constructor_nullFunctionName_throws() {
        assertThrows(IllegalArgumentException.class, () -> new ConditionNode.FuncNode(null, new Object[] {}));
    }

    @Test
    void constructor_nullParams_throws() {
        assertThrows(IllegalArgumentException.class, () -> new ConditionNode.FuncNode("COALESCE", null));
    }

    @Test
    void constructor_clonesParams() {
        Object[] original = new Object[] {"field", "value"};
        ConditionNode.FuncNode node = new ConditionNode.FuncNode("COALESCE", original);
        original[0] = "modified";
        assertEquals("field", node.params[0], "Params should be defensively copied");
    }

    @Test
    void toString_masksStringParams() {
        ConditionNode.FuncNode node = ConditionNode.FuncNode.of("COALESCE", new Object[] {"password", "secret"});
        String str = node.toString();
        assertTrue(str.contains("FuncNode[COALESCE("));
        assertTrue(str.contains("String[***]"));
        assertFalse(str.contains("password"));
        assertFalse(str.contains("secret"));
    }

    @Test
    void toString_masksNullParams() {
        ConditionNode.FuncNode node = ConditionNode.FuncNode.of("COALESCE", new Object[] {null});
        String str = node.toString();
        assertTrue(str.contains("null"));
    }

    @Test
    void toString_masksNonStringParams() {
        ConditionNode.FuncNode node = ConditionNode.FuncNode.of("COALESCE", new Object[] {42, 3.14});
        String str = node.toString();
        assertTrue(str.contains("Integer[***]"));
        assertTrue(str.contains("Double[***]"));
    }

    @Test
    void toString_emptyParams() {
        ConditionNode.FuncNode node = ConditionNode.FuncNode.of("COALESCE", new Object[] {});
        String str = node.toString();
        assertEquals("FuncNode[COALESCE()]", str);
    }

    // ---- Snapshot pattern tests ----

    @Test
    void addSafeFunctionNames_makesFunctionAvailableViaFuncNode() {
        // func() requires function in BOTH safe list AND boolean list
        FunctionWhitelist.addSafeFunctionNames(Set.of("MY_CUSTOM_FUNC"));
        FunctionWhitelist.addBooleanFunctionNames(Set.of("MY_CUSTOM_FUNC"));
        ConditionNode.FuncNode node = ConditionNode.FuncNode.of("MY_CUSTOM_FUNC", new Object[] {"field"});
        assertNotNull(node, "Function added via addSafeFunctionNames should be accepted");
        assertEquals("MY_CUSTOM_FUNC", node.functionName);
    }

    @Test
    void addSafeFunctionNames_caseInsensitive() {
        FunctionWhitelist.addSafeFunctionNames(Set.of("my_func"));
        FunctionWhitelist.addBooleanFunctionNames(Set.of("my_func"));
        ConditionNode.FuncNode node = ConditionNode.FuncNode.of("MY_FUNC", new Object[] {"field"});
        assertNotNull(node, "Function should be matched case-insensitively");
    }

    @Test
    void addBooleanFunctionNames_makesFunctionAvailableViaFuncNode() {
        // func() requires function in BOTH safe list AND boolean list
        FunctionWhitelist.addSafeFunctionNames(Set.of("MY_BOOL_FUNC"));
        FunctionWhitelist.addBooleanFunctionNames(Set.of("MY_BOOL_FUNC"));
        ConditionNode.FuncNode node = ConditionNode.FuncNode.of("MY_BOOL_FUNC", new Object[] {"field"});
        assertNotNull(node, "Boolean function added via addBooleanFunctionNames should be accepted");
    }

    @Test
    void addBooleanFunctionNames_notInSafeList_throwsSecurityException() {
        // Only add to boolean list, not safe list — should fail safe list check
        FunctionWhitelist.addBooleanFunctionNames(Set.of("UNSAFE_FUNC"));
        assertThrows(SecurityViolationException.class,
            () -> ConditionNode.FuncNode.of("UNSAFE_FUNC", new Object[] {"field"}),
            "Function in boolean list but not in safe list should be rejected");
    }

    @Test
    void addSafeFunctionNames_notInBooleanList_throwsSecurityException() {
        // Only add to safe list, not boolean list — should fail boolean list check
        FunctionWhitelist.addSafeFunctionNames(Set.of("SAFE_ONLY_FUNC"));
        assertThrows(SecurityViolationException.class,
            () -> ConditionNode.FuncNode.of("SAFE_ONLY_FUNC", new Object[] {"field"}),
            "Function in safe list but not in boolean list should be rejected");
    }

    @Test
    void addSafeFunctionNames_emptyCollection_noEffect() {
        FunctionWhitelist.addSafeFunctionNames(Set.of());
        assertThrows(SecurityViolationException.class,
            () -> ConditionNode.FuncNode.of("STILL_NOT_LISTED", new Object[] {"field"}));
    }

    @Test
    void addSafeFunctionNames_null_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> FunctionWhitelist.addSafeFunctionNames(null));
    }

    @Test
    void addBooleanFunctionNames_null_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> FunctionWhitelist.addBooleanFunctionNames(null));
    }

    @Test
    void freezeExtraFunctionNames_snapshotIsImmutable() {
        FunctionWhitelist.addSafeFunctionNames(Set.of("FROZEN_FUNC"));
        FunctionWhitelist.addBooleanFunctionNames(Set.of("FROZEN_FUNC"));
        Set<String> snapshot = FunctionWhitelist.FROZEN_EXTRA_SAFE_FUNCTION_NAMES.get();
        assertNotNull(snapshot);
        assertTrue(snapshot.contains("FROZEN_FUNC"));
        assertThrows(UnsupportedOperationException.class, () -> snapshot.add("NEW_FUNC"));
    }

    @Test
    void addSafeFunctionNames_afterFreeze_updatesSnapshot() {
        FunctionWhitelist.addSafeFunctionNames(Set.of("FUNC_A"));
        FunctionWhitelist.addBooleanFunctionNames(Set.of("FUNC_A"));
        assertTrue(FunctionWhitelist.FROZEN_EXTRA_SAFE_FUNCTION_NAMES.get().contains("FUNC_A"));

        FunctionWhitelist.addSafeFunctionNames(Set.of("FUNC_B"));
        FunctionWhitelist.addBooleanFunctionNames(Set.of("FUNC_B"));
        assertTrue(FunctionWhitelist.FROZEN_EXTRA_SAFE_FUNCTION_NAMES.get().contains("FUNC_B"),
            "Snapshot should be updated after second add");
    }

    @Test
    void concurrentReadsDuringAdd_noException() throws Exception {
        int threadCount = 8;
        int iterations = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicBoolean hasError = new AtomicBoolean(false);
        CopyOnWriteArrayList<Future<?>> futures = new CopyOnWriteArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            final int threadIdx = i;
            futures.add(executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int j = 0; j < iterations; j++) {
                        String funcName = "FUNC_" + threadIdx + "_" + j;
                        FunctionWhitelist.addSafeFunctionNames(Set.of(funcName));
                        FunctionWhitelist.FROZEN_EXTRA_SAFE_FUNCTION_NAMES.get().contains(funcName);
                    }
                } catch (Exception e) {
                    hasError.set(true);
                }
            }));
        }

        startLatch.countDown();
        for (Future<?> f : futures) {
            f.get();
        }
        executor.shutdown();

        assertFalse(hasError.get(), "Concurrent reads and writes should not throw exceptions");
    }
}
