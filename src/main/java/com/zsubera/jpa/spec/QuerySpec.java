package com.zsubera.jpa.spec;

import com.zsubera.jpa.util.InClauseBuilder;
import com.zsubera.jpa.util.LambdaUtils;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.persistence.LockModeType;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.*;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.lang.Nullable;

/**
 * A type-safe, lambda-based builder for JPA {@link Specification} queries.
 *
 * <p>Uses method references (e.g. {@code Entity::getField}) instead of hardcoded property name
 * strings. Supports equality, comparison, string matching, collection operations, JOINs, EXISTS
 * subqueries, and arbitrarily nested AND/OR groups.
 *
 * <p><strong>This class is mutable and not thread-safe.</strong> Instances should not be shared
 * across threads. Create a new {@code QuerySpec} per query operation.
 *
 * <p>Example:
 *
 * <pre>{@code
 * new QuerySpec<User>()
 *     .eq(User::getStatus, "ACTIVE")
 *     .or()
 *         .like(User::getName, "%John%")
 *         .like(User::getEmail, "%john%")
 *     .endOr()
 *     .toSpecification();
 * }</pre>
 *
 * @param <T> the root entity type being queried
 */
@SuppressFBWarnings("SE_BAD_FIELD")
public class QuerySpec<T> implements Specification<T>, ConditionBuilder<T, QuerySpec<T>> {

  private static final Logger log = LoggerFactory.getLogger(QuerySpec.class);

  private final List<ConditionNode> conditions = new ArrayList<>();
  private final Deque<List<ConditionNode>> groupStack = new ArrayDeque<>();
  private boolean distinct = false;
  private final List<String> groupByFields = new ArrayList<>();
  private final List<BiFunction<Path<T>, CriteriaBuilder, Predicate>> havingConditions =
      new ArrayList<>();
  private final List<ConditionNode.OrderNode> orderNodes = new ArrayList<>();
  private Integer queryTimeout;
  private LockModeType lockMode;

  List<ConditionNode> currentGroup() {
    return groupStack.isEmpty() ? conditions : groupStack.peek();
  }

  @Override
  public List<ConditionNode> conditions() {
    return currentGroup();
  }

  /**
   * Exposes the ordering defined on this QuerySpec as a Spring Data {@link Sort}. Returns {@link
   * Sort#unsorted()} if no ordering has been set.
   *
   * <p>This allows {@link com.zsubera.jpa.util.PageableHelper} and other utilities to merge
   * QuerySpec ordering with external sorts.
   */
  public Sort getSort() {
    if (orderNodes.isEmpty()) {
      return Sort.unsorted();
    }
    List<Sort.Order> orders = new ArrayList<>();
    for (ConditionNode.OrderNode node : orderNodes) {
      orders.add(node.asc ? Sort.Order.asc(node.fieldName) : Sort.Order.desc(node.fieldName));
    }
    return Sort.by(orders);
  }

  /**
   * Sets a query timeout in seconds for the generated query. Applied by {@link
   * #applyQuerySettings(TypedQuery)} and {@link com.zsubera.jpa.template.MyJpaTemplate}.
   */
  public QuerySpec<T> timeout(int seconds) {
    this.queryTimeout = seconds;
    return this;
  }

  public Integer getQueryTimeout() {
    return queryTimeout;
  }

  /**
   * Sets a pessimistic lock mode for the generated query. Applied by {@link
   * #applyQuerySettings(TypedQuery)} and {@link com.zsubera.jpa.template.MyJpaTemplate}.
   */
  public QuerySpec<T> lockMode(LockModeType lockMode) {
    this.lockMode = lockMode;
    return this;
  }

  public LockModeType getLockMode() {
    return lockMode;
  }

  /**
   * Applies the configured query timeout and lock mode to the given {@link TypedQuery}. Called
   * automatically by {@link com.zsubera.jpa.template.MyJpaTemplate}.
   */
  public void applyQuerySettings(TypedQuery<?> query) {
    if (queryTimeout != null) {
      query.setHint("jakarta.persistence.query.timeout", queryTimeout * 1000);
    }
    if (lockMode != null) {
      query.setLockMode(lockMode);
    }
  }

  public QuerySpec<T> distinct() {
    this.distinct = true;
    if (log.isDebugEnabled()) {
      log.debug("QuerySpec: DISTINCT enabled");
    }
    return this;
  }

  /** Adds a GROUP BY clause on the given fields. */
  @SafeVarargs
  public final QuerySpec<T> groupBy(SFunction<T, ?>... fields) {
    for (SFunction<T, ?> f : fields) {
      groupByFields.add(LambdaUtils.getPropertyName(f));
    }
    if (log.isDebugEnabled()) {
      log.debug("QuerySpec: GROUP BY {}", groupByFields);
    }
    return this;
  }

  /**
   * Adds a HAVING condition for use with {@link #groupBy}. The function receives the query root and
   * criteria builder at execution time.
   */
  public QuerySpec<T> having(BiFunction<Path<T>, CriteriaBuilder, Predicate> condition) {
    havingConditions.add(condition);
    if (log.isDebugEnabled()) {
      log.debug("QuerySpec: HAVING condition added ({} total)", havingConditions.size());
    }
    return this;
  }

  /**
   * Adds ascending ORDER BY on the given fields.
   *
   * <p><strong>Note:</strong> When using {@code findAll(Specification, Pageable)}, Spring Data will
   * override this ordering with the {@link org.springframework.data.domain.Pageable Pageable}'s
   * sort. Use {@code findAll(spec, Sort.unsorted())} or {@link #orderByAsc(SFunction[])} without
   * {@code Pageable} to preserve the ordering set here.
   */
  @SafeVarargs
  public final QuerySpec<T> orderByAsc(SFunction<T, ?>... fields) {
    for (SFunction<T, ?> f : fields) {
      orderNodes.add(new ConditionNode.OrderNode(LambdaUtils.getPropertyName(f), true));
    }
    if (log.isDebugEnabled()) {
      log.debug("QuerySpec: ORDER BY ASC {}", Arrays.toString(fields));
    }
    return this;
  }

  /**
   * Adds descending ORDER BY on the given fields.
   *
   * <p><strong>Note:</strong> When using {@code findAll(Specification, Pageable)}, Spring Data will
   * override this ordering. See {@link #orderByAsc(SFunction[])} for details.
   */
  @SafeVarargs
  public final QuerySpec<T> orderByDesc(SFunction<T, ?>... fields) {
    for (SFunction<T, ?> f : fields) {
      orderNodes.add(new ConditionNode.OrderNode(LambdaUtils.getPropertyName(f), false));
    }
    if (log.isDebugEnabled()) {
      log.debug("QuerySpec: ORDER BY DESC {}", Arrays.toString(fields));
    }
    return this;
  }

  /** Adds an INNER JOIN on the given relationship field. */
  public <J> JoinGroup<T, J> join(SFunction<T, ?> field) {
    ConditionNode.JoinNode joinNode =
        new ConditionNode.JoinNode(
            LambdaUtils.getPropertyName(field), ConditionNode.JoinType.INNER);
    currentGroup().add(joinNode);
    return new JoinGroup<>(this, joinNode);
  }

  /** Adds a LEFT JOIN on the given relationship field. */
  public <J> JoinGroup<T, J> leftJoin(SFunction<T, ?> field) {
    ConditionNode.JoinNode joinNode =
        new ConditionNode.JoinNode(LambdaUtils.getPropertyName(field), ConditionNode.JoinType.LEFT);
    currentGroup().add(joinNode);
    return new JoinGroup<>(this, joinNode);
  }

  /** Adds a FETCH JOIN to eagerly load the given relationship. */
  public <J> JoinGroup<T, J> fetchJoin(SFunction<T, ?> field) {
    ConditionNode.JoinNode joinNode =
        new ConditionNode.JoinNode(
            LambdaUtils.getPropertyName(field), ConditionNode.JoinType.FETCH);
    currentGroup().add(joinNode);
    return new JoinGroup<>(this, joinNode);
  }

  /** Adds a LEFT FETCH JOIN. */
  public <J> JoinGroup<T, J> leftFetchJoin(SFunction<T, ?> field) {
    ConditionNode.JoinNode joinNode =
        new ConditionNode.JoinNode(
            LambdaUtils.getPropertyName(field), ConditionNode.JoinType.LEFT_FETCH);
    currentGroup().add(joinNode);
    return new JoinGroup<>(this, joinNode);
  }

  /** Adds an EXISTS subquery condition. */
  public <S> QuerySpec<T> exists(Class<S> subEntity, Consumer<SubQuerySpec<S>> config) {
    currentGroup().add(new ConditionNode.ExistsNode<>(subEntity, config, false));
    return this;
  }

  /** Adds a NOT EXISTS subquery condition. */
  public <S> QuerySpec<T> notExists(Class<S> subEntity, Consumer<SubQuerySpec<S>> config) {
    currentGroup().add(new ConditionNode.ExistsNode<>(subEntity, config, true));
    return this;
  }

  /** Opens an OR group. */
  public OrGroup<T> or() {
    ConditionNode.OrNode orNode = new ConditionNode.OrNode();
    currentGroup().add(orNode);
    groupStack.push(orNode.nodes);
    return new OrGroup<>(this);
  }

  void endOr() {
    if (groupStack.isEmpty()) {
      throw new IllegalStateException("endOr() called without a matching or()");
    }
    groupStack.pop();
  }

  void pushGroupStack(List<ConditionNode> nodes) {
    groupStack.push(nodes);
  }

  // ---- Consumer-based API (self-closing) ----

  /** Builds an OR group with a consumer, automatically closing it. */
  public QuerySpec<T> or(Consumer<OrGroup<T>> config) {
    ConditionNode.OrNode orNode = new ConditionNode.OrNode();
    currentGroup().add(orNode);
    groupStack.push(orNode.nodes);
    config.accept(new OrGroup<>(this));
    groupStack.pop();
    return this;
  }

  /** Builds a JOIN with a consumer, automatically closing it. */
  public <J> QuerySpec<T> join(SFunction<T, ?> field, Consumer<JoinGroup<T, J>> config) {
    ConditionNode.JoinNode joinNode =
        new ConditionNode.JoinNode(
            LambdaUtils.getPropertyName(field), ConditionNode.JoinType.INNER);
    currentGroup().add(joinNode);
    config.accept(new JoinGroup<>(this, joinNode));
    return this;
  }

  /** Builds a LEFT JOIN with a consumer, automatically closing it. */
  public <J> QuerySpec<T> leftJoin(SFunction<T, ?> field, Consumer<JoinGroup<T, J>> config) {
    ConditionNode.JoinNode joinNode =
        new ConditionNode.JoinNode(LambdaUtils.getPropertyName(field), ConditionNode.JoinType.LEFT);
    currentGroup().add(joinNode);
    config.accept(new JoinGroup<>(this, joinNode));
    return this;
  }

  /**
   * Builds a FETCH JOIN with a consumer, automatically closing it.
   *
   * @param field a method reference to the relationship
   * @param config consumer to configure the JoinGroup
   * @param <J> the joined entity type
   * @return this QuerySpec for chaining
   */
  public <J> QuerySpec<T> fetchJoin(SFunction<T, ?> field, Consumer<JoinGroup<T, J>> config) {
    ConditionNode.JoinNode joinNode =
        new ConditionNode.JoinNode(
            LambdaUtils.getPropertyName(field), ConditionNode.JoinType.FETCH);
    currentGroup().add(joinNode);
    config.accept(new JoinGroup<>(this, joinNode));
    return this;
  }

  /**
   * Builds a LEFT FETCH JOIN with a consumer, automatically closing it.
   *
   * @param field a method reference to the relationship
   * @param config consumer to configure the JoinGroup
   * @param <J> the joined entity type
   * @return this QuerySpec for chaining
   */
  public <J> QuerySpec<T> leftFetchJoin(SFunction<T, ?> field, Consumer<JoinGroup<T, J>> config) {
    ConditionNode.JoinNode joinNode =
        new ConditionNode.JoinNode(
            LambdaUtils.getPropertyName(field), ConditionNode.JoinType.LEFT_FETCH);
    currentGroup().add(joinNode);
    config.accept(new JoinGroup<>(this, joinNode));
    return this;
  }

  /** Adds a NOT group that negates the combined conditions. */
  public QuerySpec<T> not(Consumer<OrGroup<T>> config) {
    ConditionNode.AndNode andNode = new ConditionNode.AndNode();
    ConditionNode.NegateNode negate = new ConditionNode.NegateNode(andNode);
    currentGroup().add(negate);
    groupStack.push(andNode.nodes);
    config.accept(new OrGroup<>(this));
    groupStack.pop();
    return this;
  }

  private void validateCleanState() {
    if (!groupStack.isEmpty()) {
      throw new IllegalStateException(
          "Not all or() groups were closed with endOr() before building the query");
    }
  }

  /** Returns this QuerySpec as a Spring Data {@link Specification}. */
  public Specification<T> toSpecification() {
    return this;
  }

  /** Combines this QuerySpec with an external {@link Specification} using AND. */
  public Specification<T> toSpecification(@Nullable Specification<T> external) {
    if (external == null) {
      return this;
    }
    return this.and(external);
  }

  /**
   * Combines this QuerySpec with another using AND and returns a new combined {@link
   * Specification}. Use {@link #then(QuerySpec)} to combine conditions while retaining the
   * QuerySpec type for further chaining.
   */
  public Specification<T> and(QuerySpec<T> other) {
    if (other == null) {
      return this;
    }
    return this.and(other.toSpecification());
  }

  /**
   * Merges another QuerySpec's conditions into this one using AND semantics. The other spec's
   * conditions, grouping, ordering, and distinct flag are appended to this spec, preserving the
   * QuerySpec type for chaining.
   */
  @SuppressWarnings("unchecked")
  public QuerySpec<T> then(QuerySpec<T> other) {
    if (other == null) {
      return this;
    }
    this.conditions.addAll(other.conditions);
    if (other.distinct) {
      this.distinct = true;
    }
    this.groupByFields.addAll(other.groupByFields);
    this.havingConditions.addAll(other.havingConditions);
    this.orderNodes.addAll(other.orderNodes);
    return this;
  }

  /**
   * Combines this QuerySpec with another using OR and returns a new combined {@link Specification}.
   */
  public Specification<T> or(QuerySpec<T> other) {
    if (other == null) {
      return this;
    }
    return this.or(other.toSpecification());
  }

  @Override
  public Predicate toPredicate(Root<T> root, CriteriaQuery<?> query, CriteriaBuilder cb) {
    validateCleanState();
    if (log.isDebugEnabled()) {
      log.debug(
          "QuerySpec: building predicate for {} with {} conditions, {} order nodes, distinct={}",
          root.getModel().getName(),
          conditions.size(),
          orderNodes.size(),
          distinct);
    }
    if (query != null) {
      if (distinct) {
        query.distinct(true);
      }
      if (!groupByFields.isEmpty()) {
        List<Path<?>> paths = new ArrayList<>();
        for (String field : groupByFields) {
          paths.add(root.get(field));
        }
        query.groupBy(paths.toArray(new Expression[0]));
        for (BiFunction<Path<T>, CriteriaBuilder, Predicate> having : havingConditions) {
          query.having(having.apply(root, cb));
        }
      }
      if (!orderNodes.isEmpty()) {
        List<jakarta.persistence.criteria.Order> orders = new ArrayList<>();
        for (ConditionNode.OrderNode node : orderNodes) {
          if (node.asc) {
            orders.add(cb.asc(root.get(node.fieldName)));
          } else {
            orders.add(cb.desc(root.get(node.fieldName)));
          }
        }
        query.orderBy(orders);
      }
    }
    Map<String, Join<?, ?>> joinCache = new LinkedHashMap<>();
    List<Predicate> predicates = new ArrayList<>();
    for (ConditionNode node : conditions) {
      Predicate p = resolveNode(node, root, query, cb, joinCache, null);
      if (p != null) {
        predicates.add(p);
      }
    }
    Predicate result =
        predicates.isEmpty() ? cb.conjunction() : cb.and(predicates.toArray(new Predicate[0]));
    if (log.isDebugEnabled()) {
      log.debug("QuerySpec: predicate built with {} conditions", predicates.size());
    }
    return result;
  }

  private Predicate resolveNode(
      ConditionNode node,
      Path<?> path,
      CriteriaQuery<?> query,
      CriteriaBuilder cb,
      Map<String, Join<?, ?>> joinCache,
      String pathPrefix) {
    if (node instanceof ConditionNode.SimpleNode) {
      return resolveSimple((ConditionNode.SimpleNode) node, path, cb);
    }
    if (node instanceof ConditionNode.JoinNode) {
      return resolveJoin((ConditionNode.JoinNode) node, path, query, cb, joinCache, pathPrefix);
    }
    if (node instanceof ConditionNode.OrNode) {
      return resolveOr((ConditionNode.OrNode) node, path, query, cb, joinCache, pathPrefix);
    }
    if (node instanceof ConditionNode.AndNode) {
      return resolveAnd((ConditionNode.AndNode) node, path, query, cb, joinCache, pathPrefix);
    }
    if (node instanceof ConditionNode.MultiLikeNode) {
      return resolveMultiLike((ConditionNode.MultiLikeNode) node, path, cb);
    }
    if (node instanceof ConditionNode.CollectionNode) {
      return resolveCollection((ConditionNode.CollectionNode) node, path, cb);
    }
    if (node instanceof ConditionNode.ExistsNode) {
      return resolveExists((ConditionNode.ExistsNode<?>) node, path, query, cb);
    }
    if (node instanceof ConditionNode.RawNode) {
      return ((ConditionNode.RawNode) node).fn.apply(path, cb);
    }
    if (node instanceof ConditionNode.NegateNode) {
      Predicate inner =
          resolveNode(
              ((ConditionNode.NegateNode) node).inner, path, query, cb, joinCache, pathPrefix);
      return inner != null ? cb.not(inner) : null;
    }
    throw new IllegalArgumentException("Unknown ConditionNode type: " + node.getClass().getName());
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private Predicate resolveSimple(ConditionNode.SimpleNode node, Path<?> path, CriteriaBuilder cb) {
    Path<?> fieldPath = path.get(node.fieldName);
    switch (node.op) {
      case EQ:
        return cb.equal(fieldPath, node.value);
      case NE:
        return cb.notEqual(fieldPath, node.value);
      case GT:
        return cb.greaterThan((Expression<Comparable>) fieldPath, (Comparable) node.value);
      case GE:
        return cb.greaterThanOrEqualTo((Expression<Comparable>) fieldPath, (Comparable) node.value);
      case LT:
        return cb.lessThan((Expression<Comparable>) fieldPath, (Comparable) node.value);
      case LE:
        return cb.lessThanOrEqualTo((Expression<Comparable>) fieldPath, (Comparable) node.value);
      case LIKE:
        if (node.escapeChar != '\0') {
          return cb.like(fieldPath.as(String.class), (String) node.value, node.escapeChar);
        }
        return cb.like(fieldPath.as(String.class), (String) node.value);
      case NOT_LIKE:
        if (node.escapeChar != '\0') {
          return cb.notLike(fieldPath.as(String.class), (String) node.value, node.escapeChar);
        }
        return cb.notLike(fieldPath.as(String.class), (String) node.value);
      case EQ_IGNORE_CASE:
        return cb.equal(cb.upper(fieldPath.as(String.class)), ((String) node.value).toUpperCase());
      case LIKE_IGNORE_CASE:
        return cb.like(cb.upper(fieldPath.as(String.class)), ((String) node.value).toUpperCase());
      case IS_NULL:
        return cb.isNull(fieldPath);
      case IS_NOT_NULL:
        return cb.isNotNull(fieldPath);
      case IN:
        {
          if (node.value instanceof Collection) {
            return InClauseBuilder.in(cb, fieldPath, (Collection<?>) node.value);
          }
          return InClauseBuilder.in(cb, fieldPath, (Object[]) node.value);
        }
      case NOT_IN:
        {
          if (node.value instanceof Collection) {
            return InClauseBuilder.notIn(cb, fieldPath, (Collection<?>) node.value);
          }
          return InClauseBuilder.notIn(cb, fieldPath, (Object[]) node.value);
        }
      case BETWEEN:
        {
          Comparable<?>[] range = (Comparable<?>[]) node.value;
          return cb.between(
              (Expression<Comparable>) fieldPath, (Comparable) range[0], (Comparable) range[1]);
        }
      case NOT_BETWEEN:
        {
          Comparable<?>[] range = (Comparable<?>[]) node.value;
          return cb.not(
              cb.between(
                  (Expression<Comparable>) fieldPath,
                  (Comparable) range[0],
                  (Comparable) range[1]));
        }
      default:
        return cb.conjunction();
    }
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private Predicate resolveJoin(
      ConditionNode.JoinNode node,
      Path<?> path,
      CriteriaQuery<?> query,
      CriteriaBuilder cb,
      Map<String, Join<?, ?>> joinCache,
      String pathPrefix) {
    String fullPath =
        (pathPrefix != null && !pathPrefix.isEmpty() ? pathPrefix + "." : "") + node.fieldName;

    Join<?, ?> join =
        joinCache.computeIfAbsent(
            fullPath,
            k -> {
              boolean isFetch =
                  node.joinType == ConditionNode.JoinType.FETCH
                      || node.joinType == ConditionNode.JoinType.LEFT_FETCH;
              jakarta.persistence.criteria.JoinType jt =
                  (node.joinType == ConditionNode.JoinType.LEFT
                          || node.joinType == ConditionNode.JoinType.LEFT_FETCH)
                      ? jakarta.persistence.criteria.JoinType.LEFT
                      : jakarta.persistence.criteria.JoinType.INNER;
              if (isFetch) {
                return (Join<?, ?>) ((From<?, ?>) path).fetch(node.fieldName, jt);
              }
              return ((From<?, ?>) path).join(node.fieldName, jt);
            });

    List<Predicate> innerPredicates = new ArrayList<>();
    for (ConditionNode inner : node.innerConditions) {
      Predicate p = resolveNode(inner, join, query, cb, joinCache, fullPath);
      if (p != null) {
        innerPredicates.add(p);
      }
    }
    return innerPredicates.isEmpty()
        ? cb.conjunction()
        : cb.and(innerPredicates.toArray(new Predicate[0]));
  }

  private Predicate resolveOr(
      ConditionNode.OrNode node,
      Path<?> path,
      CriteriaQuery<?> query,
      CriteriaBuilder cb,
      Map<String, Join<?, ?>> joinCache,
      String pathPrefix) {
    List<Predicate> childPredicates = new ArrayList<>();
    for (ConditionNode child : node.nodes) {
      Predicate p = resolveNode(child, path, query, cb, joinCache, pathPrefix);
      if (p != null) {
        childPredicates.add(p);
      }
    }
    if (childPredicates.isEmpty()) {
      return cb.disjunction();
    }
    if (childPredicates.size() == 1) {
      return childPredicates.get(0);
    }
    return cb.or(childPredicates.toArray(new Predicate[0]));
  }

  private Predicate resolveAnd(
      ConditionNode.AndNode node,
      Path<?> path,
      CriteriaQuery<?> query,
      CriteriaBuilder cb,
      Map<String, Join<?, ?>> joinCache,
      String pathPrefix) {
    List<Predicate> childPredicates = new ArrayList<>();
    for (ConditionNode child : node.nodes) {
      Predicate p = resolveNode(child, path, query, cb, joinCache, pathPrefix);
      if (p != null) {
        childPredicates.add(p);
      }
    }
    if (childPredicates.isEmpty()) {
      return cb.conjunction();
    }
    if (childPredicates.size() == 1) {
      return childPredicates.get(0);
    }
    return cb.and(childPredicates.toArray(new Predicate[0]));
  }

  private Predicate resolveMultiLike(
      ConditionNode.MultiLikeNode node, Path<?> path, CriteriaBuilder cb) {
    List<Predicate> likes = new ArrayList<>();
    String pattern = "%" + node.keyword + "%";
    for (String fieldName : node.fieldNames) {
      likes.add(cb.like(path.get(fieldName).as(String.class), pattern));
    }
    return cb.or(likes.toArray(new Predicate[0]));
  }

  @SuppressWarnings("unchecked")
  private Predicate resolveCollection(
      ConditionNode.CollectionNode node, Path<?> path, CriteriaBuilder cb) {
    Path<?> fieldPath = path.get(node.fieldName);
    if (node.op == ConditionNode.CollectionOp.IS_EMPTY) {
      return cb.isEmpty((Expression<Collection<?>>) (Expression<?>) fieldPath);
    }
    return cb.isNotEmpty((Expression<Collection<?>>) (Expression<?>) fieldPath);
  }

  @SuppressWarnings("unchecked")
  private <S> Predicate resolveExists(
      ConditionNode.ExistsNode<S> node,
      Path<?> outerPath,
      CriteriaQuery<?> query,
      CriteriaBuilder cb) {
    if (query == null) {
      CriteriaQuery<?> tempQuery = cb.createQuery();
      return resolveExists(node, outerPath, tempQuery, cb);
    }
    jakarta.persistence.criteria.Subquery<S> subquery = query.subquery(node.subEntity);
    Root<S> subRoot = subquery.from(node.subEntity);
    Root<?> correlatedOuter = subquery.correlate((Root<?>) outerPath);
    SubQuerySpec<S> subSpec = new SubQuerySpec<>(subquery, subRoot, correlatedOuter, cb);
    node.config.accept(subSpec);
    subSpec.applyWhere();
    if (!subSpec.isSelectSet()) {
      subquery.select(subRoot);
    }
    return node.negate ? cb.not(cb.exists(subquery)) : cb.exists(subquery);
  }
}
