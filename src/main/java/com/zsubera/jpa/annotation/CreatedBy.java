package com.zsubera.jpa.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记实体字段为创建人，实体持久化时自动填充。
 *
 * <p>
 * 使用示例：
 *
 * <pre>
 * {
 *     &#64;code
 *     &#64;Entity
 *     &#64;EntityListeners(AuditEntityListener.class)
 *     public class User {
 *         @CreatedBy
 *         private String createdBy;
 *     }
 * }
 * </pre>
 *
 * <p>
 * <strong>注意：</strong>需要配置 {@link AuditUserProvider} 实现以提供当前用户信息。
 *
 * @author myjpa-plus
 * @since 1.3.0
 * @see AuditEntityListener
 * @see AuditUserProvider
 */
@Documented
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface CreatedBy {}
