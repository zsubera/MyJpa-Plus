package com.zsubera.jpa.converter;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.hibernate.annotations.Type;

/**
 * 标记实体枚举字段，使用 {@link CodeEnumType} 进行数据库列值与枚举的自动转换。
 *
 * <p>
 * <strong>必须添加的场景：</strong>当数据库列类型为 {@code CHAR(1)} 或 {@code VARCHAR}，且存储的是枚举编码值（如 '0'、'1'、'M'、'F'）时。 不添加此注解会导致
 * Hibernate 6 抛出 {@code ArrayIndexOutOfBoundsException}。
 *
 * <p>
 * <strong>不需要添加的场景：</strong>当数据库列类型为 {@code INT} 或 {@code TINYINT}，且存储的是枚举序数（0、1、2）时， Hibernate 默认处理即可。
 *
 * <p>
 * <strong>使用示例：</strong>
 *
 * <pre>{@code
 * // 1. 枚举定义
 * public enum StatusEnum {
 *     ACTIVE(0, "正常"), DELETED(1, "已删除");
 *
 *     @CodeEnumValue // 可选，建议加上
 *     private final int code;
 *     private final String desc;
 * }
 *
 * // 2. 实体使用
 * @Entity
 * public class User {
 *     // 普通枚举字段
 *     @CodeEnum
 *     @Column(name = "status")
 *     private StatusEnum status;
 *
 *     // 与 @SoftDelete 配合使用
 *     @SoftDelete(deletedValue = "DELETED")
 *     @CodeEnum
 *     @Column(name = "del_flag")
 *     private DelFlag delFlag;
 * }
 * }</pre>
 *
 * <p>
 * <strong>与 {@link com.zsubera.jpa.annotation.SoftDelete @SoftDelete} 的关系：</strong> 当软删除字段使用枚举类型且数据库为 CHAR(1)
 * 时，需要同时使用两个注解。
 *
 * @author myjpa-plus

 * @see CodeEnumValue
 * @see CodeEnumType
 * @see com.zsubera.jpa.annotation.SoftDelete
 */
@Documented
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Type(CodeEnumType.class)
public @interface CodeEnum {}
