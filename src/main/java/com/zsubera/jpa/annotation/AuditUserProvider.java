package com.zsubera.jpa.annotation;

/**
 * 审计用户信息提供者接口。
 *
 * <p>
 * 实现此接口以提供当前用户信息，用于 {@link CreatedBy} 和 {@link UpdatedBy} 注解的自动填充。
 *
 * <pre>
 * {
 *     &#64;code
 *     &#64;Component
 *     public class SecurityAuditUserProvider implements AuditUserProvider {
 *         @Override
 *         public String getCurrentUser() {
 *             Authentication auth = SecurityContextHolder.getContext().getAuthentication();
 *             return auth != null ? auth.getName() : "SYSTEM";
 *         }
 *     }
 * }
 * </pre>
 *
 * @author myjpa-plus
 * @since 1.3.0
 * @see CreatedBy
 * @see UpdatedBy
 * @see AuditEntityListener
 */
public interface AuditUserProvider {

    /**
     * 获取当前用户名。
     *
     * @return 当前用户名，如果无法获取则返回默认值（如 "SYSTEM"）
     */
    String getCurrentUser();
}
