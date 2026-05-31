package com.zsubera.jpa.tenant;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link TenantContext} ThreadLocal counter behavior.
 */
class TenantContextTest {

    @AfterEach
    void cleanup() {
        TenantContext.reset();
    }

    @Test
    void isIgnoreTenant_default_returnsFalse() {
        assertFalse(TenantContext.isIgnoreTenant());
    }

    @Test
    void pushIgnore_setsIgnoreFlag() {
        TenantContext.pushIgnore();
        assertTrue(TenantContext.isIgnoreTenant());
    }

    @Test
    void popIgnore_clearsIgnoreFlag() {
        TenantContext.pushIgnore();
        TenantContext.popIgnore();
        assertFalse(TenantContext.isIgnoreTenant());
    }

    @Test
    void pushIgnore_supportsNesting() {
        TenantContext.pushIgnore();
        TenantContext.pushIgnore();

        assertTrue(TenantContext.isIgnoreTenant());

        TenantContext.popIgnore();
        assertTrue(TenantContext.isIgnoreTenant());

        TenantContext.popIgnore();
        assertFalse(TenantContext.isIgnoreTenant());
    }

    @Test
    void popIgnore_onZeroCount_doesNothing() {
        TenantContext.popIgnore();
        assertFalse(TenantContext.isIgnoreTenant());
    }

    @Test
    void reset_removesAllIgnores() {
        TenantContext.pushIgnore();
        TenantContext.pushIgnore();
        TenantContext.reset();

        assertFalse(TenantContext.isIgnoreTenant());
    }

    @Test
    void threadLocal_isThreadIsolated() throws InterruptedException {
        TenantContext.pushIgnore();
        assertTrue(TenantContext.isIgnoreTenant());

        Thread otherThread = new Thread(() -> {
            assertFalse(TenantContext.isIgnoreTenant());
            TenantContext.pushIgnore();
            assertTrue(TenantContext.isIgnoreTenant());
            TenantContext.popIgnore();
        });
        otherThread.start();
        otherThread.join();

        assertTrue(TenantContext.isIgnoreTenant());
        TenantContext.popIgnore();
    }
}
