package com.zsubera.jpa.spec;

import java.io.Serializable;
import java.util.function.Function;

/**
 * A serializable function used to reference entity properties via method references.
 *
 * <p><strong>Must be used as a method reference:</strong>
 *
 * <pre>{@code Entity::getField}</pre>
 *
 * <p><strong>Lambda expressions are not supported:</strong>
 *
 * <pre>{@code e -> e.getField()}  // will throw IllegalArgumentException</pre>
 *
 * <p>The {@link Serializable} interface enables property-name extraction at runtime via Java's
 * {@link java.lang.invoke.SerializedLambda} mechanism, allowing type-safe query building without
 * hardcoded field name strings.
 *
 * @param <T> the entity type
 * @param <R> the property return type
 */
@FunctionalInterface
public interface SFunction<T, R> extends Function<T, R>, Serializable {}
