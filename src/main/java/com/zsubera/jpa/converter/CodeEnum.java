package com.zsubera.jpa.converter;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.hibernate.annotations.Type;

/**
 * 标记实体枚举字段，自动应用 {@link CodeEnumType} 转换。
 *
 * <p>
 * 是 {@code @Type(CodeEnumType.class)} 的简写形式。
 *
 * <pre>
 * {
 *     &#64;code
 *     // 枚举定义
 *     public enum StatusEnum {
 *         ACTIVE(0, "正常"), DELETED(1, "已删除");
 *
 *         &#64;CodeEnumValue
 *         private final int code;
 *         private final String desc;
 *     }
 *
 *     // 实体使用
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
 * @see CodeEnumValue
 * @see CodeEnumType
 */
@Documented
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Type(CodeEnumType.class)
public @interface CodeEnum {}
