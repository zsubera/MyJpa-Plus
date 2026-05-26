package com.zsubera.jpa.util;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import java.util.Collection;

/**
 * Utility for building JPA {@code IN} and {@code NOT IN} clauses, eliminating the repetitve {@code
 * CriteriaBuilder.In} construction pattern.
 *
 * <p>This class is used internally by {@link com.zsubera.jpa.spec.ConditionBuilder}, {@link
 * com.zsubera.jpa.spec.SubQuerySpec}, and {@link com.zsubera.jpa.update.AbstractBulkOperationSpec}
 * to avoid duplicating the same IN-building logic across three implementations.
 */
public final class InClauseBuilder {

  private InClauseBuilder() {}

  /**
   * Builds an {@code IN} predicate: {@code field IN (values)}.
   *
   * @param cb the CriteriaBuilder
   * @param path the field path
   * @param values the values to match against, as an array
   * @return the IN predicate
   * @throws IllegalArgumentException if values is null or empty
   */
  public static Predicate in(CriteriaBuilder cb, Path<?> path, Object... values) {
    CriteriaBuilder.In<Object> in = cb.in(path);
    for (Object v : values) {
      in.value(v);
    }
    return in;
  }

  /**
   * Builds an {@code IN} predicate: {@code field IN (values)}.
   *
   * @param cb the CriteriaBuilder
   * @param path the field path
   * @param values the values to match against, as a Collection
   * @return the IN predicate
   * @throws IllegalArgumentException if values is null or empty
   */
  public static Predicate in(CriteriaBuilder cb, Path<?> path, Collection<?> values) {
    CriteriaBuilder.In<Object> in = cb.in(path);
    for (Object v : values) {
      in.value(v);
    }
    return in;
  }

  /**
   * Builds a {@code NOT IN} predicate: {@code field NOT IN (values)}.
   *
   * @param cb the CriteriaBuilder
   * @param path the field path
   * @param values the values to match against, as an array
   * @return the NOT IN predicate
   * @throws IllegalArgumentException if values is null or empty
   */
  public static Predicate notIn(CriteriaBuilder cb, Path<?> path, Object... values) {
    return cb.not(in(cb, path, values));
  }

  /**
   * Builds a {@code NOT IN} predicate: {@code field NOT IN (values)}.
   *
   * @param cb the CriteriaBuilder
   * @param path the field path
   * @param values the values to match against, as a Collection
   * @return the NOT IN predicate
   * @throws IllegalArgumentException if values is null or empty
   */
  public static Predicate notIn(CriteriaBuilder cb, Path<?> path, Collection<?> values) {
    return cb.not(in(cb, path, values));
  }
}
