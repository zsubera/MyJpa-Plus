package com.zsubera.jpa.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记实体字段为创建时间，实体持久化时自动填充为当前时间。
 *
 * <p>
 * 使用示例：
 *
 * <pre>{@code
 * @Entity
 * @EntityListeners(AuditEntityListener.class)
 * public class User {
 *     @CreatedAt
 *     private Instant createdAt;
 * }
 * }</pre>
 *
 * <p>
 * <strong>注意：</strong>字段类型必须为 {@link java.time.Instant}、{@link java.time.LocalDateTime} 或 {@link java.util.Date}。
 *
 * @author myjpa-plus

 * @see AuditEntityListener
 * @see UpdatedAt
 */
@Documented
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface CreatedAt {}
