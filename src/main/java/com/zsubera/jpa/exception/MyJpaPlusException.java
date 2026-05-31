package com.zsubera.jpa.exception;

import java.io.Serial;

/**
 * MyJpa-Plus 库的基础异常类。所有库特定的异常都继承此类， 允许使用者通过捕获单一类型来处理所有库错误。
 *
 * <p>
 * 支持可选的错误码（{@link ErrorCode}）和异常上下文信息，便于日志分析和错误分类。
 */
public class MyJpaPlusException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 错误码枚举，用于分类和识别异常类型。 */
    public enum ErrorCode {
        /** 通用错误。 */
        GENERAL,
        /** 配置错误。 */
        CONFIGURATION,
        /** 查询构建错误。 */
        QUERY_BUILD,
        /** 执行错误。 */
        EXECUTION,
        /** 安全相关错误。 */
        SECURITY,
        /** 并发冲突错误。 */
        CONCURRENCY,
        /** 数据访问错误。 */
        DATA_ACCESS,
        /** 超时错误。 */
        TIMEOUT
    }

    private final ErrorCode errorCode;
    private final String context;

    /**
     * 构造带有错误消息的异常。
     *
     * @param message 错误消息
     */
    public MyJpaPlusException(String message) {
        this(message, ErrorCode.GENERAL, null, null);
    }

    /**
     * 构造带有错误消息和原因的异常。
     *
     * @param message 错误消息
     * @param cause 原因
     */
    public MyJpaPlusException(String message, Throwable cause) {
        this(message, ErrorCode.GENERAL, null, cause);
    }

    /**
     * 构造带有错误消息、错误码和原因的异常。
     *
     * @param message 错误消息
     * @param errorCode 错误码
     * @param cause 原因
     */
    public MyJpaPlusException(String message, ErrorCode errorCode, Throwable cause) {
        this(message, errorCode, null, cause);
    }

    /**
     * 构造带有完整信息的异常。
     *
     * @param message 错误消息
     * @param errorCode 错误码
     * @param context 异常上下文信息
     * @param cause 原因
     */
    public MyJpaPlusException(String message, ErrorCode errorCode, String context, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode != null ? errorCode : ErrorCode.GENERAL;
        this.context = context;
    }

    /**
     * 获取错误码。
     *
     * @return 错误码
     */
    public ErrorCode getErrorCode() {
        return errorCode;
    }

    /**
     * 获取异常上下文信息。
     *
     * @return 上下文信息，可能为 null
     */
    public String getContext() {
        return context;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(getClass().getName());
        sb.append(" [").append(errorCode).append("]");
        if (context != null) {
            sb.append(" context=").append(context);
        }
        String message = getMessage();
        if (message != null) {
            sb.append(": ").append(message);
        }
        return sb.toString();
    }
}
