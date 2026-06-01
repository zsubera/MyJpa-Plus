package com.zsubera.jpa.tenant;

/**
 * 租户 ID 提供者接口。
 *
 * <p>
 * 实现此接口以提供当前请求的租户 ID。通常从 HTTP 请求头、JWT Token 或 SecurityContext 中获取。
 *
 * <p>
 * 使用示例：
 *
 * <pre>
 * {
 *     &#64;code
 *     &#64;Component
 *     public class HttpHeaderTenantProvider implements TenantProvider {
 *         private static final String TENANT_HEADER = "X-Tenant-ID";
 *
 *         @Override
 *         public Object getCurrentTenantId() {
 *             HttpServletRequest request =
 *                 ((ServletRequestAttributes)RequestContextHolder.getRequestAttributes()).getRequest();
 *             return request.getHeader(TENANT_HEADER);
 *         }
 *     }
 * }
 * </pre>
 *
 * <p>
 * <strong>注意：</strong>只有注册了 {@code TenantProvider} Bean，租户过滤才会激活。未注册时，所有查询不受租户过滤影响。
 *
 * @see TenantContext
 * @see com.zsubera.jpa.annotation.TenantId
 */
public interface TenantProvider {

    /**
     * 获取当前租户 ID。
     *
     * <p>
     * 返回 {@code null} 表示无活跃租户（未登录或系统级操作）。 调用方应检查返回值是否为 null，null 表示不应用租户过滤。
     *
     * @return 当前租户 ID，返回 {@code null} 表示无活跃租户
     */
    Object getCurrentTenantId();
}
