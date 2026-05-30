package com.zsubera.jpa.converter;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记枚举中作为数据库存储值的字段。
 *
 * <p>
 * 配合 {@link CodeEnum} 注解使用，解决 Hibernate 6 将 CHAR(1) 枚举列映射为 TINYINT 导致的 {@code ArrayIndexOutOfBoundsException} 问题。
 *
 * <p>
 * 支持的字段类型：{@code int}、{@code long}、{@code Integer}、{@code Long}、{@code String}
 *
 * <pre>
 * {
 *     &#64;code
 *     public enum StatusEnum {
 *         ACTIVE(0, "正常"), DELETED(1, "已删除");
 *
 *         &#64;CodeEnumValue
 *         private final int code;
 *         private final String desc;
 *     }
 *
 *     &#64;Entity
 *     public class User {
 *         @CodeEnum
 *         private StatusEnum status;
 *     }
 * }
 * </pre>
 *
 * @author myjpa-plus
 * @since 1.1.0
 * @see CodeEnum
 * @see CodeEnumType
 */
@Documented
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface CodeEnumValue {}
