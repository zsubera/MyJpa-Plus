package com.zsubera.jpa.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an entity field as the soft-delete flag.
 * <p>
 * When a field is annotated with {@code @SoftDelete}, the library
 * automatically injects a {@code WHERE field = false} (or {@code field IS NULL})
 * condition into queries for that entity type, unless the query explicitly
 * requests unfiltered results.
 * <p>
 * The annotated field should be of type {@code Boolean} or {@code boolean}.
 *
 * <pre>{@code
 * @Entity
 * public class Product {
 *     @SoftDelete
 *     private Boolean deleted = false;
 * }
 * }</pre>
 *
 * @see com.zsubera.jpa.repository.MyJpaRepository
 */
@Documented
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface SoftDelete {
}
