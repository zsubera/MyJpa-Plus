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
 * <strong>重要：</strong>此注解基于 Hibernate 的 {@code @Type} 机制，仅适用于 Hibernate 作为 JPA Provider 的场景。
 *
 * <p>
 * <strong>必须添加的场景：</strong>当数据库列类型为 {@code CHAR(1)} 或 {@code VARCHAR}，且存储的是枚举编码值（如 '0'、'1'、'M'、'F'）时。 不添加此注解会导致
 * Hibernate 6 抛出 {@code ArrayIndexOutOfBoundsException}。
 *
 * <p>
 * <strong>不需要添加的场景：</strong>当数据库列类型为 {@code INT} 或 {@code TINYINT}，且存储的是枚举序数（0、1、2）时， Hibernate 默认处理即可。
 *
 * <p>
 * <strong>非 Hibernate JPA Provider 使用方式（如 EclipseLink）：</strong>
 *
 * <pre>{@code
 * // 对于每个枚举类型，创建专用的 AttributeConverter：
 * @Converter(autoApply = true)
 * public class StatusEnumConverter implements AttributeConverter<StatusEnum, Integer> {
 *     // 实现略，将枚举的 code 值写入数据库
 * }
 *
 * // 然后在实体中使用：
 * @Convert(converter = StatusEnumConverter.class)
 * private StatusEnum status;
 * }</pre>
 *
 * <p>
 * <strong>与 {@link com.zsubera.jpa.annotation.SoftDelete @SoftDelete} 的关系：</strong> 当软删除字段使用枚举类型且数据库为 CHAR(1)
 * 时，需要同时使用两个注解。
 *
 * @author myjpa-plus

 * @see CodeEnumValue
 * @see CodeEnumType
 * @see CodeEnumHelper
 * @see com.zsubera.jpa.annotation.SoftDelete
 */
@Documented
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Type(CodeEnumType.class)
public @interface CodeEnum {}
