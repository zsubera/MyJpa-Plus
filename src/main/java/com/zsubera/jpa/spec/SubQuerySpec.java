package com.zsubera.jpa.spec;

import com.zsubera.jpa.util.LambdaUtils;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Type-safe builder for JPA EXISTS / NOT EXISTS subqueries.
 *
 * <p>Provides condition methods for the subquery entity and access to the correlated outer query
 * root for building correlation predicates.
 *
 * <p><strong>Design note:</strong> Unlike {@link ConditionBuilder}, which uses deferred evaluation
 * (building a tree of {@link ConditionNode} resolved at query execution time), {@code SubQuerySpec}
 * uses <em>eager</em> evaluation — each condition method immediately creates a JPA {@link
 * jakarta.persistence.criteria.Predicate} and adds it to an internal list. This is necessary
 * because subqueries must be fully constructed before the outer query is built. Predicate
 * construction is delegated to {@link PredicateHelper} to share logic with other components.
 *
 * <p>Usage via {@link QuerySpec#exists(Class, java.util.function.Consumer)} or {@link
 * QuerySpec#notExists(Class, java.util.function.Consumer)}.
 *
 * @param <S> the subquery entity type
 */
public class SubQuerySpec<S> {

  private final Subquery<S> subquery;
  private final Root<S> root;
  private final CriteriaBuilder cb;
  private final Root<?> correlatedRoot;
  private final List<Predicate> predicates = new ArrayList<>();
  private boolean selectSet;

  SubQuerySpec(Subquery<S> subquery, Root<S> root, Root<?> correlatedRoot, CriteriaBuilder cb) {
    this.subquery = subquery;
    this.root = root;
    this.correlatedRoot = correlatedRoot;
    this.cb = cb;
  }

  void applyWhere() {
    if (!predicates.isEmpty()) {
      subquery.where(cb.and(predicates.toArray(new Predicate[0])));
    }
  }

  boolean isSelectSet() {
    return selectSet;
  }

  /**
   * Returns the correlated outer query root for building correlation predicates. Use inside {@link
   * #where(java.util.function.Function)} to reference the outer entity.
   *
   * @param <T> the outer entity type
   * @return the outer query {@link Root}
   */
  @SuppressWarnings("unchecked")
  public <T> Root<T> correlated() {
    return (Root<T>) correlatedRoot;
  }

  /**
   * Adds an equality correlation condition between the outer query and the subquery.
   *
   * <p>This is the most common pattern for correlated subqueries, e.g.:
   *
   * <pre>{@code
   * qs.exists(Order.class, sub -> sub
   *     .correlatedEq(Customer::getId, Order::getCustomerId)
   *     .gt(Order::getAmount, 1000)
   * );
   * }</pre>
   *
   * generates: {@code EXISTS (SELECT 1 FROM orders WHERE customer.id = orders.customer_id AND
   * amount > 1000)}
   *
   * @param outerField the field on the outer entity (e.g., Customer::getId)
   * @param subField the corresponding field on the subquery entity (e.g., Order::getCustomerId)
   * @param <T> the outer entity type
   * @return this SubQuerySpec for chaining
   */
  public <T> SubQuerySpec<S> correlatedEq(SFunction<T, ?> outerField, SFunction<S, ?> subField) {
    predicates.add(
        cb.equal(
            correlatedRoot.get(LambdaUtils.getPropertyName(outerField)),
            root.get(LambdaUtils.getPropertyName(subField))));
    return this;
  }

  private String property(SFunction<S, ?> field) {
    if (field == null) {
      throw new IllegalArgumentException("field must not be null");
    }
    return LambdaUtils.getPropertyName(field);
  }

  // ---- Comparison operators ----

  /** Adds an equality condition on the subquery entity. */
  public SubQuerySpec<S> eq(SFunction<S, ?> field, Object value) {
    predicates.add(PredicateHelper.eq(root, property(field), value, cb));
    return this;
  }

  /** Adds a not-equal condition on the subquery entity. */
  public SubQuerySpec<S> ne(SFunction<S, ?> field, Object value) {
    predicates.add(PredicateHelper.ne(root, property(field), value, cb));
    return this;
  }

  public SubQuerySpec<S> gt(SFunction<S, ?> field, Comparable<?> value) {
    if (value == null) {
      throw new IllegalArgumentException("value must not be null");
    }
    predicates.add(PredicateHelper.gt(root, property(field), value, cb));
    return this;
  }

  public SubQuerySpec<S> ge(SFunction<S, ?> field, Comparable<?> value) {
    if (value == null) {
      throw new IllegalArgumentException("value must not be null");
    }
    predicates.add(PredicateHelper.ge(root, property(field), value, cb));
    return this;
  }

  public SubQuerySpec<S> lt(SFunction<S, ?> field, Comparable<?> value) {
    if (value == null) {
      throw new IllegalArgumentException("value must not be null");
    }
    predicates.add(PredicateHelper.lt(root, property(field), value, cb));
    return this;
  }

  public SubQuerySpec<S> le(SFunction<S, ?> field, Comparable<?> value) {
    if (value == null) {
      throw new IllegalArgumentException("value must not be null");
    }
    predicates.add(PredicateHelper.le(root, property(field), value, cb));
    return this;
  }

  // ---- String operators ----

  public SubQuerySpec<S> like(SFunction<S, ?> field, String value) {
    if (value == null) {
      throw new IllegalArgumentException("value must not be null");
    }
    predicates.add(PredicateHelper.like(root, property(field), value, cb));
    return this;
  }

  public SubQuerySpec<S> notLike(SFunction<S, ?> field, String value) {
    if (value == null) {
      throw new IllegalArgumentException("value must not be null");
    }
    predicates.add(PredicateHelper.notLike(root, property(field), value, cb));
    return this;
  }

  public SubQuerySpec<S> startsWith(SFunction<S, ?> field, String value) {
    if (value == null) {
      throw new IllegalArgumentException("value must not be null");
    }
    predicates.add(PredicateHelper.startsWith(root, property(field), value, cb));
    return this;
  }

  public SubQuerySpec<S> endsWith(SFunction<S, ?> field, String value) {
    if (value == null) {
      throw new IllegalArgumentException("value must not be null");
    }
    predicates.add(PredicateHelper.endsWith(root, property(field), value, cb));
    return this;
  }

  public SubQuerySpec<S> contains(SFunction<S, ?> field, String value) {
    if (value == null) {
      throw new IllegalArgumentException("value must not be null");
    }
    predicates.add(PredicateHelper.contains(root, property(field), value, cb));
    return this;
  }

  // ---- Collection operators ----

  public SubQuerySpec<S> between(SFunction<S, ?> field, Comparable<?> start, Comparable<?> end) {
    if (start == null) {
      throw new IllegalArgumentException("start must not be null");
    }
    if (end == null) {
      throw new IllegalArgumentException("end must not be null");
    }
    @SuppressWarnings({"unchecked", "rawtypes"})
    int cmp = ((java.lang.Comparable) start).compareTo(end);
    if (cmp > 0) {
      throw new IllegalArgumentException("start must not be greater than end");
    }
    predicates.add(PredicateHelper.between(root, property(field), start, end, cb));
    return this;
  }

  public SubQuerySpec<S> notBetween(SFunction<S, ?> field, Comparable<?> start, Comparable<?> end) {
    if (start == null) {
      throw new IllegalArgumentException("start must not be null");
    }
    if (end == null) {
      throw new IllegalArgumentException("end must not be null");
    }
    @SuppressWarnings({"unchecked", "rawtypes"})
    int cmp = ((java.lang.Comparable) start).compareTo(end);
    if (cmp > 0) {
      throw new IllegalArgumentException("start must not be greater than end");
    }
    predicates.add(PredicateHelper.notBetween(root, property(field), start, end, cb));
    return this;
  }

  public SubQuerySpec<S> in(SFunction<S, ?> field, Object... values) {
    if (values == null || values.length == 0) {
      throw new IllegalArgumentException("values must not be empty");
    }
    predicates.add(PredicateHelper.in(root, property(field), values, cb));
    return this;
  }

  public SubQuerySpec<S> notIn(SFunction<S, ?> field, Object... values) {
    if (values == null || values.length == 0) {
      throw new IllegalArgumentException("values must not be empty");
    }
    predicates.add(PredicateHelper.notIn(root, property(field), values, cb));
    return this;
  }

  public SubQuerySpec<S> in(SFunction<S, ?> field, Collection<?> values) {
    if (values == null || values.isEmpty()) {
      throw new IllegalArgumentException("values must not be empty");
    }
    predicates.add(PredicateHelper.in(root, property(field), values, cb));
    return this;
  }

  public SubQuerySpec<S> notIn(SFunction<S, ?> field, Collection<?> values) {
    if (values == null || values.isEmpty()) {
      throw new IllegalArgumentException("values must not be empty");
    }
    predicates.add(PredicateHelper.notIn(root, property(field), values, cb));
    return this;
  }

  // ---- Null operators ----

  public SubQuerySpec<S> isNull(SFunction<S, ?> field) {
    predicates.add(PredicateHelper.isNull(root, property(field), cb));
    return this;
  }

  public SubQuerySpec<S> isNotNull(SFunction<S, ?> field) {
    predicates.add(PredicateHelper.isNotNull(root, property(field), cb));
    return this;
  }

  public SubQuerySpec<S> eqIgnoreCase(SFunction<S, ?> field, String value) {
    predicates.add(PredicateHelper.eqIgnoreCase(root, property(field), value, cb));
    return this;
  }

  public SubQuerySpec<S> likeIgnoreCase(SFunction<S, ?> field, String value) {
    if (value == null) {
      throw new IllegalArgumentException("value must not be null");
    }
    predicates.add(PredicateHelper.likeIgnoreCase(root, property(field), value, cb));
    return this;
  }

  // ---- Collection empty checks ----

  public SubQuerySpec<S> isEmpty(SFunction<S, ?> field) {
    predicates.add(PredicateHelper.isEmpty(root, property(field), cb));
    return this;
  }

  public SubQuerySpec<S> isNotEmpty(SFunction<S, ?> field) {
    predicates.add(PredicateHelper.isNotEmpty(root, property(field), cb));
    return this;
  }

  // ---- Multi-field search ----

  @SuppressWarnings("unchecked")
  public SubQuerySpec<S> multiLike(String keyword, SFunction<S, ?>... fields) {
    if (keyword != null && !keyword.isEmpty() && fields != null && fields.length > 0) {
      String pattern = "%" + keyword + "%";
      List<Predicate> likes = new ArrayList<>();
      for (SFunction<S, ?> field : fields) {
        if (field == null) {
          throw new IllegalArgumentException("fields must not contain null elements");
        }
        likes.add(PredicateHelper.like(root, property(field), pattern, cb));
      }
      if (!likes.isEmpty()) {
        predicates.add(likes.size() == 1 ? likes.get(0) : cb.or(likes.toArray(new Predicate[0])));
      }
    }
    return this;
  }

  /**
   * Adds a raw predicate using the subquery root and criteria builder. Use this as an escape hatch
   * for complex conditions or correlation predicates. To reference the outer query root, use {@link
   * #correlated()}.
   *
   * <pre>{@code
   * qs.exists(Child.class, sub -> sub
   *     .where(r -> cb.and(
   *         cb.equal(r.get("parent"), sub.correlated()),
   *         cb.greaterThan(r.get("amount"), 0)
   *     ))
   * );
   * }</pre>
   */
  public SubQuerySpec<S> where(java.util.function.Function<Root<S>, Predicate> condition) {
    predicates.add(condition.apply(root));
    return this;
  }

  /**
   * Sets the SELECT clause for this subquery. If not called, the subquery selects the subquery root
   * by default.
   */
  public SubQuerySpec<S> select(SFunction<S, ?> field) {
    subquery.select(root.get(LambdaUtils.getPropertyName(field)));
    selectSet = true;
    return this;
  }
}
