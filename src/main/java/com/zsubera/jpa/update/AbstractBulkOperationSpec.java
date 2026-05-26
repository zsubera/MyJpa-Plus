package com.zsubera.jpa.update;

import com.zsubera.jpa.spec.PredicateHelper;
import com.zsubera.jpa.spec.SFunction;
import com.zsubera.jpa.util.LambdaUtils;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import org.springframework.lang.Nullable;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Abstract base for type-safe JPA bulk operation builders ({@link UpdateSpec} and {@link
 * DeleteSpec}).
 *
 * <p>Provides common condition methods using deferred lambda evaluation. Predicate construction is
 * delegated to {@link PredicateHelper} to share logic with other components.
 *
 * @param <T> the entity type
 * @param <SELF> the concrete builder type for fluent chaining
 */
public abstract class AbstractBulkOperationSpec<
    T, SELF extends AbstractBulkOperationSpec<T, SELF>> {

  protected final Class<T> entityClass;
  protected final List<BulkConditionNode> conditionNodes = new ArrayList<>();

  protected AbstractBulkOperationSpec(Class<T> entityClass) {
    this.entityClass = entityClass;
  }

  @SuppressWarnings("unchecked")
  protected SELF self() {
    return (SELF) this;
  }

  protected String property(SFunction<T, ?> field) {
    if (field == null) {
      throw new IllegalArgumentException("field must not be null");
    }
    return LambdaUtils.getPropertyName(field);
  }

  /**
   * Executes the bulk operation within a new transaction if none is active, otherwise executes
   * within the current transaction.
   *
   * @param em the EntityManager
   * @return the number of affected rows
   */
  public int executeInTransaction(EntityManager em) {
    return executeInTransaction(em, this::doExecute);
  }

  /**
   * Executes the given operation within a new transaction if none is active, otherwise executes
   * within the current transaction.
   *
   * <p>This overload allows subclasses to execute custom operations (e.g., unconditional deleteAll)
   * with proper transaction management.
   *
   * @param em the EntityManager
   * @param operation the operation to execute
   * @return the number of affected rows
   */
  protected int executeInTransaction(EntityManager em, Function<EntityManager, Integer> operation) {
    // Check if Spring manages the transaction first (container-managed JTA or Spring tx)
    boolean springTxActive = TransactionSynchronizationManager.isActualTransactionActive();
    if (springTxActive) {
      // Spring transaction is active — delegate to it, don't touch EntityTransaction directly
      return operation.apply(em);
    }
    // No Spring transaction — use JPA's EntityTransaction for standalone scenarios
    EntityTransaction tx = em.getTransaction();
    boolean isNewTransaction = !tx.isActive();
    if (isNewTransaction) {
      tx.begin();
    }
    try {
      int result = operation.apply(em);
      if (isNewTransaction) {
        tx.commit();
      }
      return result;
    } catch (RuntimeException e) {
      if (isNewTransaction && tx.isActive()) {
        try {
          tx.rollback();
        } catch (Exception rollbackEx) {
          e.addSuppressed(rollbackEx);
        }
      }
      throw e;
    } catch (Exception e) {
      if (isNewTransaction && tx.isActive()) {
        try {
          tx.rollback();
        } catch (Exception rollbackEx) {
          e.addSuppressed(rollbackEx);
        }
      }
      throw new RuntimeException("Bulk operation failed", e);
    }
  }

  /**
   * Executes the bulk operation. Requires an active transaction in the underlying {@link
   * EntityManager}.
   *
   * @param em the EntityManager
   * @return the number of affected rows
   * @throws jakarta.persistence.TransactionRequiredException if no transaction is active
   */
  public abstract int execute(EntityManager em);

  protected abstract int doExecute(EntityManager em);

  /**
   * Sealed node type for bulk operation condition trees. Supports AND (default), OR, NOT, and leaf
   * predicate nodes.
   */
  @SuppressWarnings("unchecked")
  sealed interface BulkConditionNode {
    /** A leaf predicate function. */
    record LeafNode(BiFunction<Root<?>, CriteriaBuilder, Predicate> fn)
        implements BulkConditionNode {}

    /** An OR group of child nodes. */
    record OrNode(List<BulkConditionNode> children) implements BulkConditionNode {}

    /** A NOT wrapper around a child node. */
    record NotNode(BulkConditionNode child) implements BulkConditionNode {}
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private BulkConditionNode leaf(BiFunction<Root<T>, CriteriaBuilder, Predicate> fn) {
    return new BulkConditionNode.LeafNode((BiFunction) fn);
  }

  /**
   * Adds an OR group of conditions. All conditions added inside the consumer will be combined with
   * OR instead of AND.
   *
   * <p>Example:
   *
   * <pre>{@code
   * new DeleteSpec<>(User.class)
   *     .or(o -> o.eq(User::getStatus, "INACTIVE").eq(User::getStatus, "SUSPENDED"))
   *     .execute();
   * // WHERE (status = 'INACTIVE' OR status = 'SUSPENDED')
   * }</pre>
   */
  public SELF or(Consumer<OrConditionBuilder<T, SELF>> config) {
    List<BulkConditionNode> children = new ArrayList<>();
    config.accept(new OrConditionBuilder<>(self(), children));
    conditionNodes.add(new BulkConditionNode.OrNode(children));
    return self();
  }

  /**
   * Adds a NOT group of conditions. The combined conditions inside the consumer
   * will be negated.
   * <p>
   * Example:
   * <pre>{@code
   * new DeleteSpec<>(User.class)
   *     .not(o -> o.eq(User::getStatus, "ACTIVE"))
   *     .execute();
   * // WHERE NOT (status = 'ACTIVE')
   * }</>
   */
  public SELF not(Consumer<OrConditionBuilder<T, SELF>> config) {
    List<BulkConditionNode> children = new ArrayList<>();
    config.accept(new OrConditionBuilder<>(self(), children));
    BulkConditionNode combined =
        children.size() == 1 ? children.get(0) : new BulkConditionNode.OrNode(children);
    conditionNodes.add(new BulkConditionNode.NotNode(combined));
    return self();
  }

  public SELF eq(SFunction<T, ?> field, @Nullable Object value) {
    String name = property(field);
    conditionNodes.add(leaf((root, cb) -> PredicateHelper.eq(root, name, value, cb)));
    return self();
  }

  public SELF ne(SFunction<T, ?> field, @Nullable Object value) {
    String name = property(field);
    conditionNodes.add(leaf((root, cb) -> PredicateHelper.ne(root, name, value, cb)));
    return self();
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  public SELF gt(SFunction<T, ?> field, Comparable<?> value) {
    if (value == null) {
      throw new IllegalArgumentException("value must not be null");
    }
    String name = property(field);
    conditionNodes.add(leaf((root, cb) -> PredicateHelper.gt(root, name, value, cb)));
    return self();
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  public SELF ge(SFunction<T, ?> field, Comparable<?> value) {
    if (value == null) {
      throw new IllegalArgumentException("value must not be null");
    }
    String name = property(field);
    conditionNodes.add(leaf((root, cb) -> PredicateHelper.ge(root, name, value, cb)));
    return self();
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  public SELF lt(SFunction<T, ?> field, Comparable<?> value) {
    if (value == null) {
      throw new IllegalArgumentException("value must not be null");
    }
    String name = property(field);
    conditionNodes.add(leaf((root, cb) -> PredicateHelper.lt(root, name, value, cb)));
    return self();
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  public SELF le(SFunction<T, ?> field, Comparable<?> value) {
    if (value == null) {
      throw new IllegalArgumentException("value must not be null");
    }
    String name = property(field);
    conditionNodes.add(leaf((root, cb) -> PredicateHelper.le(root, name, value, cb)));
    return self();
  }

  public SELF like(SFunction<T, ?> field, String value) {
    if (value == null) {
      throw new IllegalArgumentException("value must not be null");
    }
    String name = property(field);
    conditionNodes.add(leaf((root, cb) -> PredicateHelper.like(root, name, value, cb)));
    return self();
  }

  public SELF notLike(SFunction<T, ?> field, String value) {
    if (value == null) {
      throw new IllegalArgumentException("value must not be null");
    }
    String name = property(field);
    conditionNodes.add(leaf((root, cb) -> PredicateHelper.notLike(root, name, value, cb)));
    return self();
  }

  public SELF startsWith(SFunction<T, ?> field, String value) {
    if (value == null) {
      throw new IllegalArgumentException("value must not be null");
    }
    String name = property(field);
    conditionNodes.add(leaf((root, cb) -> PredicateHelper.startsWith(root, name, value, cb)));
    return self();
  }

  public SELF endsWith(SFunction<T, ?> field, String value) {
    if (value == null) {
      throw new IllegalArgumentException("value must not be null");
    }
    String name = property(field);
    conditionNodes.add(leaf((root, cb) -> PredicateHelper.endsWith(root, name, value, cb)));
    return self();
  }

  public SELF contains(SFunction<T, ?> field, String value) {
    if (value == null) {
      throw new IllegalArgumentException("value must not be null");
    }
    String name = property(field);
    conditionNodes.add(leaf((root, cb) -> PredicateHelper.contains(root, name, value, cb)));
    return self();
  }

  public SELF eqIgnoreCase(SFunction<T, ?> field, String value) {
    String name = property(field);
    conditionNodes.add(leaf((root, cb) -> PredicateHelper.eqIgnoreCase(root, name, value, cb)));
    return self();
  }

  public SELF likeIgnoreCase(SFunction<T, ?> field, String value) {
    if (value == null) {
      throw new IllegalArgumentException("value must not be null");
    }
    String name = property(field);
    conditionNodes.add(leaf((root, cb) -> PredicateHelper.likeIgnoreCase(root, name, value, cb)));
    return self();
  }

  public SELF in(SFunction<T, ?> field, Object... values) {
    String name = property(field);
    if (values == null || values.length == 0) {
      throw new IllegalArgumentException("values must not be empty");
    }
    conditionNodes.add(leaf((root, cb) -> PredicateHelper.in(root, name, values, cb)));
    return self();
  }

  public SELF notIn(SFunction<T, ?> field, Object... values) {
    String name = property(field);
    if (values == null || values.length == 0) {
      throw new IllegalArgumentException("values must not be empty");
    }
    conditionNodes.add(leaf((root, cb) -> PredicateHelper.notIn(root, name, values, cb)));
    return self();
  }

  public SELF in(SFunction<T, ?> field, Collection<?> values) {
    String name = property(field);
    if (values == null || values.isEmpty()) {
      throw new IllegalArgumentException("values must not be empty");
    }
    conditionNodes.add(leaf((root, cb) -> PredicateHelper.in(root, name, values, cb)));
    return self();
  }

  public SELF notIn(SFunction<T, ?> field, Collection<?> values) {
    String name = property(field);
    if (values == null || values.isEmpty()) {
      throw new IllegalArgumentException("values must not be empty");
    }
    conditionNodes.add(leaf((root, cb) -> PredicateHelper.notIn(root, name, values, cb)));
    return self();
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  public SELF between(SFunction<T, ?> field, Comparable<?> start, Comparable<?> end) {
    if (start == null) {
      throw new IllegalArgumentException("start must not be null");
    }
    if (end == null) {
      throw new IllegalArgumentException("end must not be null");
    }
    if (((Comparable) start).compareTo(end) > 0) {
      throw new IllegalArgumentException("start must not be greater than end");
    }
    String name = property(field);
    conditionNodes.add(leaf((root, cb) -> PredicateHelper.between(root, name, start, end, cb)));
    return self();
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  public SELF notBetween(SFunction<T, ?> field, Comparable<?> start, Comparable<?> end) {
    if (start == null) {
      throw new IllegalArgumentException("start must not be null");
    }
    if (end == null) {
      throw new IllegalArgumentException("end must not be null");
    }
    if (((Comparable) start).compareTo(end) > 0) {
      throw new IllegalArgumentException("start must not be greater than end");
    }
    String name = property(field);
    conditionNodes.add(leaf((root, cb) -> PredicateHelper.notBetween(root, name, start, end, cb)));
    return self();
  }

  public SELF isNull(SFunction<T, ?> field) {
    String name = property(field);
    conditionNodes.add(leaf((root, cb) -> PredicateHelper.isNull(root, name, cb)));
    return self();
  }

  public SELF isNotNull(SFunction<T, ?> field) {
    String name = property(field);
    conditionNodes.add(leaf((root, cb) -> PredicateHelper.isNotNull(root, name, cb)));
    return self();
  }

  @SuppressWarnings("unchecked")
  public SELF where(Function<Root<T>, Predicate> condition) {
    if (condition == null) {
      throw new IllegalArgumentException("condition must not be null");
    }
    conditionNodes.add(leaf((root, cb) -> condition.apply(root)));
    return self();
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private Predicate resolveNode(BulkConditionNode node, Root<T> root, CriteriaBuilder cb) {
    if (node instanceof BulkConditionNode.LeafNode l) {
      return ((BiFunction<Root<T>, CriteriaBuilder, Predicate>) (BiFunction) l.fn())
          .apply(root, cb);
    }
    if (node instanceof BulkConditionNode.OrNode o) {
      List<Predicate> childPredicates = new ArrayList<>();
      for (BulkConditionNode child : o.children()) {
        childPredicates.add(resolveNode(child, root, cb));
      }
      if (childPredicates.isEmpty()) {
        return cb.disjunction();
      }
      if (childPredicates.size() == 1) {
        return childPredicates.get(0);
      }
      return cb.or(childPredicates.toArray(new Predicate[0]));
    }
    if (node instanceof BulkConditionNode.NotNode n) {
      return cb.not(resolveNode(n.child(), root, cb));
    }
    throw new IllegalArgumentException(
        "Unknown BulkConditionNode type: " + node.getClass().getName());
  }

  protected Predicate[] buildPredicates(Root<T> root, CriteriaBuilder cb) {
    if (conditionNodes.isEmpty()) {
      return null;
    }
    List<Predicate> predicates = new ArrayList<>();
    for (BulkConditionNode node : conditionNodes) {
      predicates.add(resolveNode(node, root, cb));
    }
    return predicates.toArray(new Predicate[0]);
  }
}
