package com.zsubera.jpa.update;

/**
 * 审计日志工具类。
 *
 * <p>
 * 提供调用栈获取等审计相关的公共方法，避免在多个类中重复实现。
 *
 * <p>
 * 用于记录危险操作（如无条件 UPDATE/DELETE）的调用栈信息，便于生产环境追踪。
 *
 * <p>
 * 调用栈深度可通过以下方式配置（优先级从高到低）：
 * <ul>
 * <li>Spring Boot 配置：{@code myjpa-plus.audit.stack-trace-depth}</li>
 * <li>系统属性：{@code -Dmyjpa-plus.audit.stack-trace-depth}</li>
 * <li>默认值：5</li>
 * </ul>
 */
final class AuditUtils {

    /** 跳过的栈帧数（getStackTrace + getCallStack） */
    private static final int STACK_SKIP = 2;

    /** 默认最大调用栈深度 */
    private static final int DEFAULT_STACK_DEPTH = 5;

    /** 最大调用栈深度上限 */
    private static final int MAX_STACK_DEPTH_LIMIT = 20;

    /** 当前配置的最大调用栈深度 */
    private static volatile int maxStackDepth;

    static {
        int configured = DEFAULT_STACK_DEPTH;
        String prop = System.getProperty("myjpa-plus.audit.stack-trace-depth");
        if (prop != null) {
            try {
                int val = Integer.parseInt(prop);
                if (val > 0 && val <= MAX_STACK_DEPTH_LIMIT) {
                    configured = val;
                }
            } catch (NumberFormatException ignored) {
                // use default
            }
        }
        maxStackDepth = configured;
    }

    private AuditUtils() {}

    /**
     * 设置最大调用栈深度。由 Spring Boot 自动配置调用。
     *
     * @param depth 最大调用栈深度，有效范围 1-20
     */
    static void setMaxStackDepth(int depth) {
        if (depth > 0 && depth <= MAX_STACK_DEPTH_LIMIT) {
            maxStackDepth = depth;
        }
    }

    /**
     * 获取当前配置的最大调用栈深度。
     *
     * @return 最大调用栈深度
     */
    static int getMaxStackDepth() {
        return maxStackDepth;
    }

    /**
     * 获取调用栈信息，用于审计日志。
     *
     * <p>
     * 返回从调用者开始的最近 N 层调用栈（N 由 {@code maxStackDepth} 配置）， 格式为 {@code className.methodName:lineNumber}， 各层之间用 {@code <- }
     * 分隔。
     *
     * <p>
     * <strong>安全说明：</strong>返回的调用栈包含完整的类名和行号，可能泄露内部实现细节。 在生产环境中，建议通过 {@code maxStackDepth} 配置限制输出深度，
     * 或在日志采集层对调用栈信息进行脱敏处理。默认深度为 5 层。
     *
     * @return 格式化的调用栈字符串
     */
    static String getCallStack() {
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        int depth = maxStackDepth;
        // 跳过 getStackTrace() 和 getCallStack() 本身，从调用者开始
        StringBuilder sb = new StringBuilder();
        for (int i = STACK_SKIP; i < stack.length && i < STACK_SKIP + depth; i++) {
            if (i > STACK_SKIP) {
                sb.append(" <- ");
            }
            sb.append(stack[i].getClassName()).append(".").append(stack[i].getMethodName());
            sb.append(":").append(stack[i].getLineNumber());
        }
        return sb.toString();
    }
}
