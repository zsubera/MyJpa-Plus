package com.zsubera.jpa.exception;

import java.io.Serial;

/**
 * 查询超时异常，用于查询执行时间超过配置的超时限制时抛出。
 *
 * <p>
 * 典型场景：
 * <ul>
 * <li>查询执行时间超过 {@code myjpa-plus.query.default-timeout-seconds} 配置</li>
 * <li>深度分页查询超过硬限制</li>
 * <li>批量操作中的查询超时</li>
 * </ul>
 *
 * @author myjpa-plus

 */
public class TimeoutException extends MyJpaPlusException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final long timeoutMs;

    /**
     * 构造查询超时异常。
     *
     * @param message 错误消息
     * @param timeoutMs 超时时间（毫秒）
     */
    public TimeoutException(String message, long timeoutMs) {
        super(message, ErrorCode.TIMEOUT, (String)null, null);
        this.timeoutMs = timeoutMs;
    }

    /**
     * 构造带有原因的查询超时异常。
     *
     * @param message 错误消息
     * @param timeoutMs 超时时间（毫秒）
     * @param cause 原因
     */
    public TimeoutException(String message, long timeoutMs, Throwable cause) {
        super(message, ErrorCode.TIMEOUT, null, cause);
        this.timeoutMs = timeoutMs;
    }

    /**
     * 获取配置的超时时间。
     *
     * @return 超时时间（毫秒）
     */
    public long getTimeoutMs() {
        return timeoutMs;
    }
}
