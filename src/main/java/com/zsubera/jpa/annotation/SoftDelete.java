package com.zsubera.jpa.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 将实体字段标记为软删除标志。
 *
 * <p>
 * 此注解标记一个字段用于指示实体是否已被软删除。支持以下字段类型：
 *
 * <ul>
 * <li>{@code Boolean} / {@code boolean} — {@code true} 表示"已删除"，{@code false}（或 {@code null}）表示"未删除"</li>
 * <li>{@code Integer} / {@code int} — 通过 {@link #deletedIntValue()} 指定表示"已删除"的整数值（默认 1），其他值表示"未删除"</li>
 * <li>{@code Enum} — 通过 {@link #deletedValue()} 指定表示"已删除"的枚举值名称</li>
 * </ul>
 *
 * <p>
 * <strong>注意：</strong>
 * <ul>
 * <li>此注解不会自动向所有查询注入 WHERE 条件。要过滤掉软删除的记录， 必须显式使用库提供的辅助方法</li>
 * <li>当数据库列存储字符编码（如 CHAR(1) 存储 '0'/'1'）时，枚举字段需要配合 {@link com.zsubera.jpa.converter.CodeEnum @CodeEnum} 和
 * {@link com.zsubera.jpa.converter.CodeEnumValue @CodeEnumValue} 注解使用</li>
 * </ul>
 *
 * <p>
 * 使用示例（Boolean 类型）：
 *
 * <pre>{@code
 * @Entity
 * public class Product {
 *     @SoftDelete
 *     private Boolean deleted = false;
 * }
 * }</pre>
 *
 * <p>
 * 使用示例（Integer 类型）：
 *
 * <pre>{@code
 * @Entity
 * public class Order {
 *     @SoftDelete(deletedIntValue = 1)
 *     private Integer isDeleted = 0;
 * }
 * }</pre>
 *
 * <p>
 * 使用示例（枚举类型 + 字符编码列）：
 *
 * <pre>{@code
 * @Entity
 * public class User {
 *     @SoftDelete(deletedValue = "DELETED")
 *     private DelFlag delFlag;
 * }
 *
 * // 创建转换器
 * @Converter(autoApply = true)
 * public class DelFlagConverter extends CodeEnumAttributeConverter<DelFlag> {}
 *
 * public enum DelFlag {
 *     EXIST(0, "存在"), DELETED(1, "删除");
 *
 *     @CodeEnumValue
 *     private final int code;
 *     // ...
 * }
 * }</pre>
 *
 * @see com.zsubera.jpa.repository.MyJpaRepository
 * @see com.zsubera.jpa.update.SoftDeleteHelper
 */
@Documented
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface SoftDelete {

    /**
     * 枚举类型字段中表示"已删除"的枚举值名称。
     *
     * <p>
     * 仅当字段类型为 {@code Enum} 时有效。对于 {@code Boolean} 和 {@code Integer} 类型字段，此属性被忽略。
     *
     * @return 表示"已删除"的枚举值名称，默认为空字符串（使用 Boolean 语义）
     */
    String deletedValue() default "";

    /**
     * Integer 类型字段中表示"已删除"的整数值。
     *
     * <p>
     * 仅当字段类型为 {@code Integer} 或 {@code int} 时有效。对于 {@code Boolean} 和 {@code Enum} 类型字段，此属性被忽略。
     *
     * @return 表示"已删除"的整数值，默认为 1
     */
    int deletedIntValue() default 1;

    /**
     * String 类型字段中表示"已删除"的字符串值。
     *
     * <p>
     * 仅当字段类型为 {@code String} 时有效。对于 {@code Boolean}、{@code Integer} 和 {@code Enum} 类型字段，此属性被忽略。 常见用法：使用 {@code char(1)}
     * 存储 {@code '0'}（存在）和 {@code '2'}（删除）。
     *
     * <p>
     * 使用示例（String 类型）：
     *
     * <pre>{@code
     * @Entity
     * public class User {
     *     @SoftDelete(deletedStringValue = "2")
     *     @Column(name = "del_flag", length = 1)
     *     private String delFlag = "0";
     * }
     * }</pre>
     *
     * @return 表示"已删除"的字符串值，默认为 "2"
     */
    String deletedStringValue() default "2";
}
