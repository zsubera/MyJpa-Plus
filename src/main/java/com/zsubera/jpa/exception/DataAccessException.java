package com.zsubera.jpa.exception;

import java.io.Serial;

/**
 * 数据访问异常，用于 EntityManager 操作、事务管理、连接问题等数据层错误。
 *
 * <p>
 * 典型场景：
 * <ul>
 * <li>JPA EntityTransaction 回滚失败</li>
 * <li>JTA 环境中缺少活动事务</li>
 * <li>实体管理器操作失败</li>
 * <li>数据库连接异常</li>
 * </ul>
 *
 * @author Zsubera
 */
public class DataAccessException extends MyJpaPlusException {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 构造数据访问异常。
     *
     * @param message 错误消息
     */
    public DataAccessException(String message) {
        super(message, ErrorCode.DATA_ACCESS, (String)null, null);
    }

    /**
     * 构造带有原因的数据访问异常。
     *
     * @param message 错误消息
     * @param cause 原因
     */
    public DataAccessException(String message, Throwable cause) {
        super(message, ErrorCode.DATA_ACCESS, null, cause);
    }

    /**
     * 构造带有上下文的数据访问异常。
     *
     * @param message 错误消息
     * @param context 上下文信息
     * @param cause 原因
     */
    public DataAccessException(String message, String context, Throwable cause) {
        super(message, ErrorCode.DATA_ACCESS, context, cause);
    }
}
