package com.zsubera.jpa.spec;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import java.util.Collection;

/**
 * Shared predicate construction logic for components that build JPA {@link Predicate} instances
 * eagerly (rather than via the deferred {@link ConditionNode} tree).
 *
 * <p>Used by {@link SubQuerySpec} (immediate construction) and {@link
 * com.zsubera.jpa.update.AbstractBulkOperationSpec AbstractBulkOperationSpec} (deferred {@code
 * BiFunction} wrapping), eliminating ~200 lines of duplicated condition logic across the two
 * classes.
 *
 * <p>All methods take a {@link Path}, the resolved field name, the comparison value(s), and a
 * {@link CriteriaBuilder}, and return a fully constructed {@link Predicate}. Null validation and
 * range validation of parameters is the caller's responsibility.
 *
 * <p>Internal utility — not intended for direct use by application code.
 */
public final class PredicateHelper {

  /** Escape character used for LIKE wildcards: backslash. */
  public static final char LIKE_ESCAPE_CHAR = '\\';

  private PredicateHelper() {}

  /**
   * Escapes SQL wildcard characters ({@code %}, {@code _}) in a LIKE pattern value. This prevents
   * user-supplied values containing {@code %} or {@code _} from unintentionally matching unintended
   * rows.
   *
   * @param input the raw string value (may be null)
   * @return the escaped string, or null if input was null
   */
  public static String escapeLikeWildcards(String input) {
    if (input == null) {
      return null;
    }
    return input.replace("\\", "\\\\").replace("_", "\\_").replace("%", "\\%");
  }

  // ==================== Comparison operators ====================

  public static Predicate eq(Path<?> path, String fieldName, Object value, CriteriaBuilder cb) {
    Path<?> fp = path.get(fieldName);
    return value == null ? cb.isNull(fp) : cb.equal(fp, value);
  }

  public static Predicate ne(Path<?> path, String fieldName, Object value, CriteriaBuilder cb) {
    Path<?> fp = path.get(fieldName);
    return value == null ? cb.isNotNull(fp) : cb.notEqual(fp, value);
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  public static Predicate gt(
      Path<?> path, String fieldName, Comparable<?> value, CriteriaBuilder cb) {
    return cb.greaterThan((Expression) path.get(fieldName), (Comparable) value);
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  public static Predicate ge(
      Path<?> path, String fieldName, Comparable<?> value, CriteriaBuilder cb) {
    return cb.greaterThanOrEqualTo((Expression) path.get(fieldName), (Comparable) value);
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  public static Predicate lt(
      Path<?> path, String fieldName, Comparable<?> value, CriteriaBuilder cb) {
    return cb.lessThan((Expression) path.get(fieldName), (Comparable) value);
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  public static Predicate le(
      Path<?> path, String fieldName, Comparable<?> value, CriteriaBuilder cb) {
    return cb.lessThanOrEqualTo((Expression) path.get(fieldName), (Comparable) value);
  }

  // ==================== String operators ====================

  public static Predicate like(Path<?> path, String fieldName, String value, CriteriaBuilder cb) {
    return cb.like(path.get(fieldName).as(String.class), value);
  }

  public static Predicate notLike(
      Path<?> path, String fieldName, String value, CriteriaBuilder cb) {
    return cb.notLike(path.get(fieldName).as(String.class), value);
  }

  public static Predicate startsWith(
      Path<?> path, String fieldName, String value, CriteriaBuilder cb) {
    return cb.like(
        path.get(fieldName).as(String.class), escapeLikeWildcards(value) + "%", LIKE_ESCAPE_CHAR);
  }

  public static Predicate endsWith(
      Path<?> path, String fieldName, String value, CriteriaBuilder cb) {
    return cb.like(
        path.get(fieldName).as(String.class), "%" + escapeLikeWildcards(value), LIKE_ESCAPE_CHAR);
  }

  public static Predicate contains(
      Path<?> path, String fieldName, String value, CriteriaBuilder cb) {
    return cb.like(
        path.get(fieldName).as(String.class),
        "%" + escapeLikeWildcards(value) + "%",
        LIKE_ESCAPE_CHAR);
  }

  public static Predicate eqIgnoreCase(
      Path<?> path, String fieldName, String value, CriteriaBuilder cb) {
    Path<?> fp = path.get(fieldName);
    if (value == null) {
      return cb.isNull(fp);
    }
    return cb.equal(cb.upper(fp.as(String.class)), value.toUpperCase());
  }

  public static Predicate likeIgnoreCase(
      Path<?> path, String fieldName, String value, CriteriaBuilder cb) {
    return cb.like(cb.upper(path.get(fieldName).as(String.class)), value.toUpperCase());
  }

  // ==================== Collection operators ====================

  @SuppressWarnings({"unchecked", "rawtypes"})
  public static Predicate in(Path<?> path, String fieldName, Object[] values, CriteriaBuilder cb) {
    CriteriaBuilder.In<Object> in = cb.in(path.get(fieldName));
    for (Object v : values) {
      in.value(v);
    }
    return in;
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  public static Predicate notIn(
      Path<?> path, String fieldName, Object[] values, CriteriaBuilder cb) {
    CriteriaBuilder.In<Object> in = cb.in(path.get(fieldName));
    for (Object v : values) {
      in.value(v);
    }
    return cb.not(in);
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  public static Predicate in(
      Path<?> path, String fieldName, Collection<?> values, CriteriaBuilder cb) {
    CriteriaBuilder.In<Object> in = cb.in(path.get(fieldName));
    for (Object v : values) {
      in.value(v);
    }
    return in;
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  public static Predicate notIn(
      Path<?> path, String fieldName, Collection<?> values, CriteriaBuilder cb) {
    CriteriaBuilder.In<Object> in = cb.in(path.get(fieldName));
    for (Object v : values) {
      in.value(v);
    }
    return cb.not(in);
  }

  // ==================== Range operators ====================

  @SuppressWarnings({"unchecked", "rawtypes"})
  public static Predicate between(
      Path<?> path, String fieldName, Comparable<?> start, Comparable<?> end, CriteriaBuilder cb) {
    return cb.between((Expression) path.get(fieldName), (Comparable) start, (Comparable) end);
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  public static Predicate notBetween(
      Path<?> path, String fieldName, Comparable<?> start, Comparable<?> end, CriteriaBuilder cb) {
    return cb.not(
        cb.between((Expression) path.get(fieldName), (Comparable) start, (Comparable) end));
  }

  // ==================== Null operators ====================

  public static Predicate isNull(Path<?> path, String fieldName, CriteriaBuilder cb) {
    return cb.isNull(path.get(fieldName));
  }

  public static Predicate isNotNull(Path<?> path, String fieldName, CriteriaBuilder cb) {
    return cb.isNotNull(path.get(fieldName));
  }

  // ==================== Collection empty checks ====================

  @SuppressWarnings("unchecked")
  public static Predicate isEmpty(Path<?> path, String fieldName, CriteriaBuilder cb) {
    return cb.isEmpty((Expression<java.util.Collection<?>>) (Expression<?>) path.get(fieldName));
  }

  @SuppressWarnings("unchecked")
  public static Predicate isNotEmpty(Path<?> path, String fieldName, CriteriaBuilder cb) {
    return cb.isNotEmpty((Expression<java.util.Collection<?>>) (Expression<?>) path.get(fieldName));
  }
}
