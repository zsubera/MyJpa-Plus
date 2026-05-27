package com.zsubera.jpa.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 将实体字段标记为软删除标志。
 *
 * <p>此注解标记一个字段用于指示实体是否已被软删除。该字段应为 {@code Boolean} 或 {@code boolean} 类型， 其中 {@code true}
 * 表示"已删除"，{@code false}（或 {@code null}）表示"未删除"。
 *
 * <p><strong>注意：</strong>此注解不会自动向所有查询注入 WHERE 条件。要过滤掉软删除的记录， 必须显式使用库提供的辅助方法：
 *
 * <ul>
 *   <li>{@code repository.findNotDeletedAll()} — 查找所有未删除的实体
 *   <li>{@code repository.findNotDeletedAll(spec)} — 带额外过滤条件
 *   <li>{@code SoftDeleteHelper.isNotDeleted(entityClass)} — 获取 {@code Specification} 过滤器
 *   <li>{@code SoftDeleteHelper.notDeletedQuery(entityClass)} — 构建带过滤条件的 {@code QuerySpec}
 * </ul>
 *
 * <p>使用示例：
 *
 * <pre>{@code
 * @Entity
 * public class Product {
 *     @SoftDelete
 *     private Boolean deleted = false;
 * }
 *
 * // 使用 MyJpaRepository 方法查询：
 * List<Product> active = repository.findNotDeletedAll();
 *
 * // 或直接使用 SoftDeleteHelper：
 * Specification<Product> spec = SoftDeleteHelper.isNotDeleted(Product.class);
 * List<Product> active = repository.findAll(spec.and(otherCondition));
 * }</pre>
 *
 * @see com.zsubera.jpa.repository.MyJpaRepository
 * @see com.zsubera.jpa.update.SoftDeleteHelper
 */
@Documented
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface SoftDelete {}
