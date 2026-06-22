package com.zsubera.jpa.exception;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ExceptionSubclassesTest {

    // ==================== BulkOperationException ====================

    @Test
    void bulkOperation_affectedRowsAndLimit_setsFields() {
        BulkOperationException ex = new BulkOperationException(5000, 1000);
        assertEquals(5000, ex.getAffectedRows());
        assertEquals(1000, ex.getLimit());
        assertEquals(MyJpaPlusException.ErrorCode.EXECUTION, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("5000"));
        assertTrue(ex.getMessage().contains("1000"));
    }

    @Test
    void bulkOperation_message_setsMessage() {
        BulkOperationException ex = new BulkOperationException("batch failed");
        assertEquals("batch failed", ex.getMessage());
        assertEquals(-1, ex.getAffectedRows());
        assertEquals(-1, ex.getLimit());
        assertNull(ex.getCause());
    }

    @Test
    void bulkOperation_messageAndCause_setsBoth() {
        RuntimeException cause = new RuntimeException("db error");
        BulkOperationException ex = new BulkOperationException("batch failed", cause);
        assertEquals("batch failed", ex.getMessage());
        assertSame(cause, ex.getCause());
        assertEquals(-1, ex.getAffectedRows());
        assertEquals(-1, ex.getLimit());
    }

    @Test
    void bulkOperation_isRuntimeException() {
        assertInstanceOf(RuntimeException.class, new BulkOperationException("err"));
        assertInstanceOf(MyJpaPlusException.class, new BulkOperationException("err"));
    }

    // ==================== DataAccessException ====================

    @Test
    void dataAccess_message_setsMessage() {
        DataAccessException ex = new DataAccessException("connection refused");
        assertEquals("connection refused", ex.getMessage());
        assertEquals(MyJpaPlusException.ErrorCode.DATA_ACCESS, ex.getErrorCode());
        assertNull(ex.getCause());
    }

    @Test
    void dataAccess_messageAndCause_setsBoth() {
        RuntimeException cause = new RuntimeException("timeout");
        DataAccessException ex = new DataAccessException("db error", cause);
        assertEquals("db error", ex.getMessage());
        assertSame(cause, ex.getCause());
    }

    @Test
    void dataAccess_messageContextCause_setsAll() {
        RuntimeException cause = new RuntimeException("rollback failed");
        DataAccessException ex = new DataAccessException("tx error", "UserRepository.save", cause);
        assertEquals("tx error", ex.getMessage());
        assertEquals("UserRepository.save", ex.getContext());
        assertSame(cause, ex.getCause());
    }

    @Test
    void dataAccess_isRuntimeException() {
        assertInstanceOf(RuntimeException.class, new DataAccessException("err"));
        assertInstanceOf(MyJpaPlusException.class, new DataAccessException("err"));
    }

    // ==================== SecurityViolationException ====================

    @Test
    void securityViolation_message_setsMessage() {
        SecurityViolationException ex = new SecurityViolationException("injection detected");
        assertEquals("injection detected", ex.getMessage());
        assertEquals(MyJpaPlusException.ErrorCode.SECURITY, ex.getErrorCode());
        assertNull(ex.getCause());
    }

    @Test
    void securityViolation_messageAndCause_setsBoth() {
        RuntimeException cause = new RuntimeException("validation failed");
        SecurityViolationException ex = new SecurityViolationException("bad input", cause);
        assertEquals("bad input", ex.getMessage());
        assertSame(cause, ex.getCause());
    }

    @Test
    void securityViolation_messageContext_setsContext() {
        SecurityViolationException ex = new SecurityViolationException("bad identifier", "DROP TABLE");
        assertEquals("bad identifier", ex.getMessage());
        assertEquals("DROP TABLE", ex.getContext());
        assertNull(ex.getCause());
    }

    @Test
    void securityViolation_isRuntimeException() {
        assertInstanceOf(RuntimeException.class, new SecurityViolationException("err"));
        assertInstanceOf(MyJpaPlusException.class, new SecurityViolationException("err"));
    }

    // ==================== QueryBuildException ====================

    @Test
    void queryBuild_message_setsMessage() {
        QueryBuildException ex = new QueryBuildException("invalid query");
        assertEquals("invalid query", ex.getMessage());
        assertEquals(MyJpaPlusException.ErrorCode.QUERY_BUILD, ex.getErrorCode());
        assertNull(ex.getCause());
    }

    @Test
    void queryBuild_messageAndCause_setsBoth() {
        RuntimeException cause = new RuntimeException("parse error");
        QueryBuildException ex = new QueryBuildException("build failed", cause);
        assertEquals("build failed", ex.getMessage());
        assertSame(cause, ex.getCause());
    }

    @Test
    void queryBuild_messageContext_setsContext() {
        QueryBuildException ex = new QueryBuildException("depth exceeded", "recursive query");
        assertEquals("depth exceeded", ex.getMessage());
        assertEquals("recursive query", ex.getContext());
        assertNull(ex.getCause());
    }

    @Test
    void queryBuild_isRuntimeException() {
        assertInstanceOf(RuntimeException.class, new QueryBuildException("err"));
        assertInstanceOf(MyJpaPlusException.class, new QueryBuildException("err"));
    }

    // ==================== MyJpaPlusException ====================

    @Test
    void myJpaPlusException_messageErrorCodeCause_setsAll() {
        RuntimeException cause = new RuntimeException("root");
        MyJpaPlusException ex = new MyJpaPlusException("msg", MyJpaPlusException.ErrorCode.CONFIGURATION, cause);
        assertEquals("msg", ex.getMessage());
        assertEquals(MyJpaPlusException.ErrorCode.CONFIGURATION, ex.getErrorCode());
        assertSame(cause, ex.getCause());
        assertNull(ex.getContext());
    }

    @Test
    void myJpaPlusException_fullConstructor_setsAll() {
        RuntimeException cause = new RuntimeException("root");
        MyJpaPlusException ex = new MyJpaPlusException("msg", MyJpaPlusException.ErrorCode.CONCURRENCY, "ctx", cause);
        assertEquals("msg", ex.getMessage());
        assertEquals(MyJpaPlusException.ErrorCode.CONCURRENCY, ex.getErrorCode());
        assertEquals("ctx", ex.getContext());
        assertSame(cause, ex.getCause());
    }

    @Test
    void myJpaPlusException_toString_masksSensitiveContext() {
        MyJpaPlusException ex =
            new MyJpaPlusException("err", MyJpaPlusException.ErrorCode.SECURITY, "password=secret123", null);
        String str = ex.toString();
        assertTrue(str.contains("***"), "Sensitive data should be masked");
        assertFalse(str.contains("secret123"), "Original value should not appear");
    }

    @Test
    void myJpaPlusException_toString_truncatesLongContext() {
        String longCtx = "x".repeat(300);
        MyJpaPlusException ex = new MyJpaPlusException("err", MyJpaPlusException.ErrorCode.GENERAL, longCtx, null);
        String str = ex.toString();
        assertTrue(str.contains("truncated"), "Long context should be truncated");
    }

    @Test
    void myJpaPlusException_errorCodeEnum_allValues() {
        MyJpaPlusException.ErrorCode[] values = MyJpaPlusException.ErrorCode.values();
        assertEquals(8, values.length);
        assertNotNull(MyJpaPlusException.ErrorCode.GENERAL);
        assertNotNull(MyJpaPlusException.ErrorCode.CONFIGURATION);
        assertNotNull(MyJpaPlusException.ErrorCode.QUERY_BUILD);
        assertNotNull(MyJpaPlusException.ErrorCode.EXECUTION);
        assertNotNull(MyJpaPlusException.ErrorCode.SECURITY);
        assertNotNull(MyJpaPlusException.ErrorCode.CONCURRENCY);
        assertNotNull(MyJpaPlusException.ErrorCode.DATA_ACCESS);
    }

    @Test
    void myJpaPlusException_nullErrorCode_defaultsToGeneral() {
        MyJpaPlusException ex = new MyJpaPlusException("msg", (MyJpaPlusException.ErrorCode)null, "ctx", null);
        assertEquals(MyJpaPlusException.ErrorCode.GENERAL, ex.getErrorCode());
    }

    @Test
    void myJpaPlusException_toString_withoutContext() {
        MyJpaPlusException ex = new MyJpaPlusException("simple error");
        String str = ex.toString();
        assertTrue(str.contains("simple error"));
        assertFalse(str.contains("context="));
    }

    @Test
    void myJpaPlusException_toString_withContextNoSensitiveData() {
        MyJpaPlusException ex =
            new MyJpaPlusException("err", MyJpaPlusException.ErrorCode.GENERAL, "normal context", null);
        String str = ex.toString();
        assertTrue(str.contains("context=normal context"));
    }

    @Test
    void myJpaPlusException_toString_tokenMasked() {
        MyJpaPlusException ex =
            new MyJpaPlusException("err", MyJpaPlusException.ErrorCode.GENERAL, "token=abc123xyz", null);
        String str = ex.toString();
        assertTrue(str.contains("token***"));
        assertFalse(str.contains("abc123xyz"));
    }

    @Test
    void myJpaPlusException_toString_secretMasked() {
        MyJpaPlusException ex =
            new MyJpaPlusException("err", MyJpaPlusException.ErrorCode.GENERAL, "secret:mysecretvalue", null);
        String str = ex.toString();
        assertTrue(str.contains("secret***"));
        assertFalse(str.contains("mysecretvalue"));
    }

    @Test
    void myJpaPlusException_toString_apiKeyMasked() {
        MyJpaPlusException ex =
            new MyJpaPlusException("err", MyJpaPlusException.ErrorCode.GENERAL, "api_key=sk_test_123", null);
        String str = ex.toString();
        assertTrue(str.contains("api_key***"));
        assertFalse(str.contains("sk_test_123"));
    }

    @Test
    void myJpaPlusException_toString_authMasked() {
        MyJpaPlusException ex =
            new MyJpaPlusException("err", MyJpaPlusException.ErrorCode.GENERAL, "authorization:bearer token123", null);
        String str = ex.toString();
        assertTrue(str.contains("authorization***"));
    }

    @Test
    void myJpaPlusException_toString_doesNotMaskPrimaryKey() {
        MyJpaPlusException ex =
            new MyJpaPlusException("err", MyJpaPlusException.ErrorCode.GENERAL, "primaryKey=12345", null);
        String str = ex.toString();
        assertFalse(str.contains("primaryKey***"), "primaryKey should not be masked");
    }

    @Test
    void myJpaPlusException_toString_exactBoundaryLength200() {
        String exactCtx = "a".repeat(200);
        MyJpaPlusException ex = new MyJpaPlusException("err", MyJpaPlusException.ErrorCode.GENERAL, exactCtx, null);
        String str = ex.toString();
        assertFalse(str.contains("truncated"), "Exactly 200 chars should not be truncated");
    }

    @Test
    void myJpaPlusException_toString_overBoundaryLength201() {
        String overCtx = "a".repeat(201);
        MyJpaPlusException ex = new MyJpaPlusException("err", MyJpaPlusException.ErrorCode.GENERAL, overCtx, null);
        String str = ex.toString();
        assertTrue(str.contains("truncated"), "201 chars should be truncated");
    }
}
