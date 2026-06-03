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

    /** P2-9: 缓存 StackWalker 实例，避免每次调用都创建新实例 */
    private static final StackWalker STACK_WALKER = StackWalker.getInstance();

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
        // O-11: Use StackWalker (Java 9+) for efficient partial stack traversal.
        // StackWalker only materializes the requested number of frames, unlike
        // Thread.currentThread().getStackTrace() which captures the entire stack.
        int depth = maxStackDepth;
        StringBuilder sb = new StringBuilder();
        STACK_WALKER.walk(frames -> {
            frames.skip(1).limit(depth).forEach(frame -> {
                if (sb.length() > 0) {
                    sb.append(" <- ");
                }
                sb.append(frame.getClassName()).append(".").append(frame.getMethodName());
                sb.append(":").append(frame.getLineNumber());
            });
            return sb;
        });
        return sb.toString();
    }
}
