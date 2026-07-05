package com.zsubera.jpa.exception;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class MyJpaPlusExceptionTest {

    @Test
    void constructor_withMessage_setsMessage() {
        MyJpaPlusException ex = new MyJpaPlusException("test error");
        assertEquals("test error", ex.getMessage());
        assertNull(ex.getCause());
    }

    @Test
    void constructor_withMessageAndCause_setsBoth() {
        RuntimeException cause = new RuntimeException("root cause");
        MyJpaPlusException ex = new MyJpaPlusException("test error", cause);
        assertEquals("test error", ex.getMessage());
        assertSame(cause, ex.getCause());
    }

    @Test
    void isRuntimeException() {
        MyJpaPlusException ex = new MyJpaPlusException("error");
        assertInstanceOf(RuntimeException.class, ex);
    }
}
