package com.zsubera.jpa.exception;

import static org.junit.jupiter.api.Assertions.*;

import com.zsubera.jpa.exception.MyJpaPlusException.ErrorCode;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link MyJpaDataAccessException} — constructor variants and exception hierarchy.
 */
class MyJpaDataAccessExceptionTest {

    @Test
    void messageOnlyConstructor() {
        MyJpaDataAccessException ex = new MyJpaDataAccessException("DB error");
        assertEquals("DB error", ex.getMessage());
        assertEquals(ErrorCode.DATA_ACCESS, ex.getErrorCode());
        assertNull(ex.getContext());
        assertNull(ex.getCause());
    }

    @Test
    void messageAndCauseConstructor() {
        RuntimeException cause = new RuntimeException("connection refused");
        MyJpaDataAccessException ex = new MyJpaDataAccessException("DB error", cause);
        assertEquals("DB error", ex.getMessage());
        assertEquals(ErrorCode.DATA_ACCESS, ex.getErrorCode());
        assertSame(cause, ex.getCause());
    }

    @Test
    void messageContextCauseConstructor() {
        RuntimeException cause = new RuntimeException("timeout");
        MyJpaDataAccessException ex = new MyJpaDataAccessException("DB error", "OrderRepo.save", cause);
        assertEquals("DB error", ex.getMessage());
        assertEquals("OrderRepo.save", ex.getContext());
        assertEquals(ErrorCode.DATA_ACCESS, ex.getErrorCode());
        assertSame(cause, ex.getCause());
    }

    @Test
    void isInstanceOfMyJpaPlusException() {
        MyJpaDataAccessException ex = new MyJpaDataAccessException("err");
        assertInstanceOf(MyJpaPlusException.class, ex);
    }

    @Test
    void isInstanceOfRuntimeException() {
        MyJpaDataAccessException ex = new MyJpaDataAccessException("err");
        assertInstanceOf(RuntimeException.class, ex);
    }
}
