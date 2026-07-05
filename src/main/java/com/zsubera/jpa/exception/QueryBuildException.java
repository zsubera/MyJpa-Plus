package com.zsubera.jpa.exception;

import java.io.Serial;

/**
 * 查询构建阶段的异常，用于 QuerySpec、ConditionBuilder 等查询构建器中的错误。
 *
 * <p>
 * 典型场景：
 * <ul>
 * <li>条件树递归深度超过限制</li>
 * <li>OR/NOT 组未正确闭合</li>
 * <li>无效的字段名或函数名</li>
 * <li>子查询构建错误</li>
 * </ul>
 *
 * @author myjpa-plus

 */
public class QueryBuildException extends MyJpaPlusException {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 构造查询构建异常。
     *
     * @param message 错误消息
     */
    public QueryBuildException(String message) {
        super(message, ErrorCode.QUERY_BUILD, null, null);
    }

    /**
     * 构造带有原因的查询构建异常。
     *
     * @param message 错误消息
     * @param cause 原因
     */
    public QueryBuildException(String message, Throwable cause) {
        super(message, ErrorCode.QUERY_BUILD, null, cause);
    }

    /**
     * 构造带有上下文的查询构建异常。
     *
     * @param message 错误消息
     * @param context 上下文信息
     */
    public QueryBuildException(String message, String context) {
        super(message, ErrorCode.QUERY_BUILD, context, null);
    }
}
