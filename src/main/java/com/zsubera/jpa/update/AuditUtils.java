package com.zsubera.jpa.update;

/**
 * 审计日志工具类。
 *
 * <p>
 * 提供调用栈获取等审计相关的公共方法，避免在多个类中重复实现。
 *
 * <p>
 * 用于记录危险操作（如无条件 UPDATE/DELETE）的调用栈信息，便于生产环境追踪。
 */
final class AuditUtils {

    /** 跳过的栈帧数（getStackTrace + getCallStack） */
    private static final int STACK_SKIP = 2;

    /** 最大调用栈深度（从调用者开始的层数），限制日志长度 */
    private static final int MAX_STACK_DEPTH = 5;

    private AuditUtils() {}

    /**
     * 获取调用栈信息，用于审计日志。
     *
     * <p>
     * 返回从调用者开始的最近 10 层调用栈，格式为 {@code className.methodName:lineNumber}， 各层之间用 {@code <- } 分隔。
     *
     * @return 格式化的调用栈字符串
     */
    static String getCallStack() {
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        // 跳过 getStackTrace() 和 getCallStack() 本身，从调用者开始
        StringBuilder sb = new StringBuilder();
        for (int i = STACK_SKIP; i < stack.length && i < STACK_SKIP + MAX_STACK_DEPTH; i++) {
            if (i > STACK_SKIP) {
                sb.append(" <- ");
            }
            sb.append(stack[i].getClassName()).append(".").append(stack[i].getMethodName());
            sb.append(":").append(stack[i].getLineNumber());
        }
        return sb.toString();
    }
}
