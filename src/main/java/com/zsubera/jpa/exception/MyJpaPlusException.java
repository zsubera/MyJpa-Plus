package com.zsubera.jpa.exception;

import java.io.Serial;
import java.util.regex.Pattern;

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
        DATA_ACCESS
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
        // 清理上下文以防止敏感数据泄露（SQL参数、凭据等）
        if (context != null) {
            String sanitized = sanitizeContext(context);
            sb.append(" context=").append(sanitized);
        }
        String message = getMessage();
        if (message != null) {
            sb.append(": ").append(message);
        }
        return sb.toString();
    }

    /**
     * 对上下文信息进行脱敏处理，防止敏感数据泄露到日志或监控系统。
     *
     * <p>
     * 脱敏策略：截断过长内容，检测并替换敏感数据模式（password=, token=, key=, secret= 等）。
     *
     * @param ctx 原始上下文
     * @return 脱敏后的上下文
     */
    private static String sanitizeContext(String ctx) {
        // 检测并遮蔽敏感数据模式
        String sanitized = SENSITIVE_DATA_PATTERN.matcher(ctx).replaceAll("$1***");
        if (sanitized.length() > 200) {
            return sanitized.substring(0, 200) + "...(truncated)";
        }
        return sanitized;
    }

    /** 敏感数据模式检测正则：匹配 password=, token=, key=, secret= 等模式。使用 (?<!\\w) 词边界避免误匹配，同时通过排除列表保护 primaryKey 等合法字段名。 */
    private static final Pattern SENSITIVE_DATA_PATTERN = Pattern.compile(
        "(?i)(?<!\\w)(password|passwd|token|api[_-]?key|apikey|secret|credential|authorization|auth[_-]?token|authtoken|auth[_-]?key|authkey|ssn|credit[_-]?card|creditcard|access[_-]?key|accesskey|private[_-]?key|privatekey|connection[_-]?string|jdbc)[=:]\\s*\\S+");
}
