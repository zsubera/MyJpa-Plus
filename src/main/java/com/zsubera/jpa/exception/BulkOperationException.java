package com.zsubera.jpa.exception;

import java.io.Serial;

/**
 * 批量操作异常，用于 UpdateSpec、DeleteSpec、MergeSpec 等批量操作中的错误。
 *
 * <p>
 * 典型场景：
 * <ul>
 * <li>批量操作影响行数超过配置的最大限制</li>
 * <li>批量迭代次数超过安全上限</li>
 * <li>批量操作中部分批次执行失败</li>
 * <li>独立事务批量操作的回滚失败</li>
 * </ul>
 *
 * @author myjpa-plus

 */
public class BulkOperationException extends MyJpaPlusException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final long affectedRows;
    private final long limit;

    /**
     * 构造批量操作超限异常。
     *
     * @param affectedRows 实际受影响的行数
     * @param limit 配置的最大行数限制
     */
    public BulkOperationException(long affectedRows, long limit) {
        super("Bulk operation would affect " + affectedRows + " rows, which exceeds the configured limit of " + limit
            + " rows. Use executeLimited() with an explicit limit, or adjust myjpa-plus.query.max-bulk-operation-rows.",
            ErrorCode.EXECUTION, null, null);
        this.affectedRows = affectedRows;
        this.limit = limit;
    }

    /**
     * 构造带有消息的批量操作异常。
     *
     * @param message 错误消息
     */
    public BulkOperationException(String message) {
        super(message, ErrorCode.EXECUTION, null, null);
        this.affectedRows = -1;
        this.limit = -1;
    }

    /**
     * 构造带有消息和原因的批量操作异常。
     *
     * @param message 错误消息
     * @param cause 原因
     */
    public BulkOperationException(String message, Throwable cause) {
        super(message, ErrorCode.EXECUTION, null, cause);
        this.affectedRows = -1;
        this.limit = -1;
    }

    /**
     * 获取实际受影响的行数。
     *
     * @return 受影响的行数，如果未知则返回 -1
     */
    public long getAffectedRows() {
        return affectedRows;
    }

    /**
     * 获取配置的最大行数限制。
     *
     * @return 最大行数限制，如果未知则返回 -1
     */
    public long getLimit() {
        return limit;
    }
}
