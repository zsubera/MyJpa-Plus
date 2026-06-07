package com.zsubera.jpa.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记实体字段为更新时间，实体持久化或更新时自动填充为当前时间。
 *
 * <p>
 * 使用示例：
 *
 * <pre>{@code
 * @Entity
 * @EntityListeners(AuditEntityListener.class)
 * public class User {
 *     @UpdatedAt
 *     private Instant updatedAt;
 * }
 * }</pre>
 *
 * <p>
 * <strong>注意：</strong>字段类型必须为 {@link java.time.Instant}、{@link java.time.LocalDateTime} 或 {@link java.util.Date}。
 *
 * @author myjpa-plus
 * @since 1.2.0
 * @see AuditEntityListener
 * @see CreatedAt
 */
@Documented
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface UpdatedAt {}
