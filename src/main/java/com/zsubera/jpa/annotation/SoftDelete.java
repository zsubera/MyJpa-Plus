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
 * <li>{@code Enum} — 通过 {@link #deletedValue()} 指定表示"已删除"的枚举值名称</li>
 * </ul>
 *
 * <p>
 * <strong>注意：</strong>此注解不会自动向所有查询注入 WHERE 条件。要过滤掉软删除的记录， 必须显式使用库提供的辅助方法：
 *
 * <ul>
 * <li>{@code repository.findNotDeletedAll()} — 查找所有未删除的实体
 * <li>{@code repository.findNotDeletedAll(spec)} — 带额外过滤条件
 * <li>{@code SoftDeleteHelper.isNotDeleted(entityClass)} — 获取 {@code Specification} 过滤器
 * <li>{@code SoftDeleteHelper.notDeletedQuery(entityClass)} — 构建带过滤条件的 {@code QuerySpec}
 * </ul>
 *
 * <p>
 * 使用示例（Boolean 类型）：
 *
 * <pre>
 * {
 *     &#64;code
 *     &#64;Entity
 *     public class Product {
 *         @SoftDelete
 *         private Boolean deleted = false;
 *     }
 * }
 * </pre>
 *
 * <p>
 * 使用示例（枚举类型）：
 *
 * <pre>
 * {
 *     &#64;code
 *     &#64;Entity
 *     public class Article {
 *         @SoftDelete(deletedValue = "DELETED")
 *         private DelFlag delFlag = DelFlag.EXIST;
 *     }
 *
 *     public enum DelFlag {
 *         EXIST, DELETED
 *     }
 * }
 * </pre>
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
     * 仅当字段类型为 {@code Enum} 时有效。对于 {@code Boolean} 类型字段，此属性被忽略。
     *
     * @return 表示"已删除"的枚举值名称，默认为空字符串（使用 Boolean 语义）
     */
    String deletedValue() default "";
}
