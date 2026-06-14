package com.zsubera.jpa.exception;

import java.io.Serial;

/**
 * 安全违规异常，用于 SQL 注入检测、标识符校验失败、无条件操作拦截等安全相关错误。
 *
 * <p>
 * 典型场景：
 * <ul>
 * <li>标识符校验失败（包含非法字符或 SQL 关键字）</li>
 * <li>函数名不在白名单中</li>
 * <li>CTE SQL 中检测到危险操作（DROP、TRUNCATE、GRANT 等）</li>
 * <li>SQL 注入模式检测（注释注入、分号注入、UNION SELECT 等）</li>
 * <li>无条件 UPDATE/DELETE 操作被拦截</li>
 * </ul>
 *
 * @author myjpa-plus
 * @since 2.1.0
 */
public class SecurityViolationException extends MyJpaPlusException {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 构造安全违规异常。
     *
     * @param message 错误消息
     */
    public SecurityViolationException(String message) {
        super(message, ErrorCode.SECURITY, (String)null, null);
    }

    /**
     * 构造带有原因的安全违规异常。
     *
     * @param message 错误消息
     * @param cause 原因
     */
    public SecurityViolationException(String message, Throwable cause) {
        super(message, ErrorCode.SECURITY, null, cause);
    }

    /**
     * 构造带有上下文的安全违规异常。
     *
     * @param message 错误消息
     * @param context 上下文信息（如违规的标识符名称）
     */
    public SecurityViolationException(String message, String context) {
        super(message, ErrorCode.SECURITY, context, null);
    }
}
