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
 * 此注解用于指定枚举中哪个字段的值会被存储到数据库，替代默认的 {@code ordinal()} 方式。
 *
 * <p>
 * <strong>必须添加：</strong>当枚举的 code 值与 {@code ordinal()} 不同时。
 *
 * <pre>{@code
 * public enum StatusEnum {
 *     ACTIVE(10, "正常"), // ordinal=0, code=10
 *     DELETED(20, "已删除"); // ordinal=1, code=20
 *
 *     @CodeEnumValue // 必须！否则数据库会存 0/1 而不是 10/20
 *     private final int code;
 *     private final String desc;
 * }
 * }</pre>
 *
 * <p>
 * <strong>可选添加：</strong>当枚举的 code 值与 {@code ordinal()} 相同时。 建议加上以保持代码一致性，避免后续添加新枚举值导致 ordinal 变化。
 *
 * <pre>{@code
 * public enum Sex {
 *     MEN(0, "男"), // ordinal=0, code=0
 *     WOMEN(1, "女"), // ordinal=1, code=1
 *     UNKNOWN(2, "未知"); // ordinal=2, code=2
 *
 *     @CodeEnumValue // 可选，建议加上
 *     private final int code;
 *     private final String desc;
 * }
 * }</pre>
 *
 * <p>
 * <strong>支持的字段类型：</strong>{@code int}、{@code long}、{@code Integer}、{@code Long}、{@code String}
 *
 * @author myjpa-plus

 * @see CodeEnum
 * @see CodeEnumType
 */
@Documented
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface CodeEnumValue {}
