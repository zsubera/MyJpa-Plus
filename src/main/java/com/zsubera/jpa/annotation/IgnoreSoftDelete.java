package com.zsubera.jpa.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记查询方法或仓库接口以跳过软删除自动过滤。
 *
 * <p>
 * 当 {@code myjpa-plus.soft-delete.auto-filter=true} 时，所有查询会自动追加软删除过滤条件。
 * 使用此注解可跳过自动过滤，查询包含已删除的记录。
 *
 * <p>
 * 使用示例：
 *
 * <pre>{@code
 * // 标记单个方法
 * @IgnoreSoftDelete
 * @Query("SELECT p FROM Product p WHERE p.id = :id")
 * Product findByIdIncludingDeleted(@Param("id") Long id);
 *
 * // 标记整个接口（所有方法都不过滤）
 * @IgnoreSoftDelete
 * public interface TrashRepository extends MyJpaRepository<Product, Long> {
 * }
 * }</pre>
 *
 * @see com.zsubera.jpa.annotation.SoftDelete
 * @see com.zsubera.jpa.update.SoftDeleteHelper
 */
@Documented
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface IgnoreSoftDelete {}
