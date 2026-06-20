package com.zsubera.jpa.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记实体字段为更新人，实体持久化或更新时自动填充。
 *
 * <p>
 * 使用示例：
 *
 * <pre>{@code
 * @Entity
 * @EntityListeners(AuditEntityListener.class)
 * public class User {
 *     @UpdatedBy
 *     private String updatedBy;
 * }
 * }</pre>
 *
 * <p>
 * <strong>注意：</strong>需要配置 {@link AuditUserProvider} 实现以提供当前用户信息。
 *
 * @author myjpa-plus

 * @see AuditEntityListener
 * @see AuditUserProvider
 */
@Documented
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface UpdatedBy {}
