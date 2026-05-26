package com.zsubera.jpa.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an entity field as the soft-delete flag.
 * <p>
 * This annotation marks a field as indicating whether an entity has been
 * soft-deleted. The field should be of type {@code Boolean} or {@code boolean},
 * where {@code true} means "deleted" and {@code false} (or {@code null}) means "not deleted".
 * <p>
 * <strong>Note:</strong> This annotation does NOT automatically inject WHERE conditions
 * into all queries. To filter out soft-deleted records, you must explicitly use
 * the helper methods provided by the library:
 * <ul>
 *   <li>{@code repository.findNotDeletedAll()} — find all non-deleted entities</li>
 *   <li>{@code repository.findNotDeletedAll(spec)} — with additional filtering</li>
 *   <li>{@code SoftDeleteHelper.isNotDeleted(entityClass)} — get a {@code Specification} filter</li>
 *   <li>{@code SoftDeleteHelper.notDeletedQuery(entityClass)} — build a {@code QuerySpec} with filter</li>
 * </ul>
 * <p>
 * Example usage:
 *
 * <pre>{@code
 * @Entity
 * public class Product {
 *     @SoftDelete
 *     private Boolean deleted = false;
 * }
 *
 * // Query using MyJpaRepository methods:
 * List<Product> active = repository.findNotDeletedAll();
 *
 * // Or using SoftDeleteHelper directly:
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
public @interface SoftDelete {
}
