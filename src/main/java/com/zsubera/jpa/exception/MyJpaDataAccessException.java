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
 * @author myjpa-plus
 */
public class MyJpaDataAccessException extends MyJpaPlusException {

    @Serial
    private static final long serialVersionUID = 1L;

    public MyJpaDataAccessException(String message) {
        super(message, ErrorCode.DATA_ACCESS, (String)null, null);
    }

    public MyJpaDataAccessException(String message, Throwable cause) {
        super(message, ErrorCode.DATA_ACCESS, null, cause);
    }

    public MyJpaDataAccessException(String message, String context, Throwable cause) {
        super(message, ErrorCode.DATA_ACCESS, context, cause);
    }
}
