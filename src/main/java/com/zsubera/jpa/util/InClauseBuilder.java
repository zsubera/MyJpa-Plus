package com.zsubera.jpa.util;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import java.util.Collection;

/**
 * 用于构建JPA {@code IN} 和 {@code NOT IN} 子句的工具类，消除了重复的 {@code
 * CriteriaBuilder.In} 构建模式。
 *
 * <p>此类被 {@link com.zsubera.jpa.spec.ConditionBuilder}、{@link
 * com.zsubera.jpa.spec.SubQuerySpec} 和 {@link com.zsubera.jpa.update.AbstractBulkOperationSpec}
 * 内部使用，以避免在三个实现中重复相同的IN构建逻辑。
 */
public final class InClauseBuilder {

  private InClauseBuilder() {}

  /**
   * 构建 {@code IN} 谓词：{@code field IN (values)}。
   *
   * @param cb CriteriaBuilder
   * @param path 字段路径
   * @param values 要匹配的值，以数组形式
   * @return IN 谓词
   * @throws IllegalArgumentException 如果 values 为 null 或空
   */
  public static Predicate in(CriteriaBuilder cb, Path<?> path, Object... values) {
    CriteriaBuilder.In<Object> in = cb.in(path);
    for (Object v : values) {
      in.value(v);
    }
    return in;
  }

  /**
   * 构建 {@code IN} 谓词：{@code field IN (values)}。
   *
   * @param cb CriteriaBuilder
   * @param path 字段路径
   * @param values 要匹配的值，以集合形式
   * @return IN 谓词
   * @throws IllegalArgumentException 如果 values 为 null 或空
   */
  public static Predicate in(CriteriaBuilder cb, Path<?> path, Collection<?> values) {
    CriteriaBuilder.In<Object> in = cb.in(path);
    for (Object v : values) {
      in.value(v);
    }
    return in;
  }

  /**
   * 构建 {@code NOT IN} 谓词：{@code field NOT IN (values)}。
   *
   * @param cb CriteriaBuilder
   * @param path 字段路径
   * @param values 要匹配的值，以数组形式
   * @return NOT IN 谓词
   * @throws IllegalArgumentException 如果 values 为 null 或空
   */
  public static Predicate notIn(CriteriaBuilder cb, Path<?> path, Object... values) {
    return cb.not(in(cb, path, values));
  }

  /**
   * 构建 {@code NOT IN} 谓词：{@code field NOT IN (values)}。
   *
   * @param cb CriteriaBuilder
   * @param path 字段路径
   * @param values 要匹配的值，以集合形式
   * @return NOT IN 谓词
   * @throws IllegalArgumentException 如果 values 为 null 或空
   */
  public static Predicate notIn(CriteriaBuilder cb, Path<?> path, Collection<?> values) {
    return cb.not(in(cb, path, values));
  }
}
