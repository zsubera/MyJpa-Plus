package com.zsubera.jpa.projection;

import com.zsubera.jpa.spec.QuerySpec;
import com.zsubera.jpa.spec.SFunction;
import com.zsubera.jpa.util.LambdaUtils;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

/**
 * DTO 投影查询的类型安全构建器。
 *
 * <p>从实体中选择特定字段，并以 {@link Tuple} 或通过 {@code CriteriaBuilder.construct()} 返回自定义 DTO 的形式返回结果。支持 JOIN
 * 关联、ORDER BY 排序和分页查询。
 *
 * <p>使用示例：
 *
 * <pre>{@code
 * List<Tuple> results = new ProjectionSpec<>(User.class)
 *     .select(User::getName)
 *     .select(User::getEmail)
 *     .join(User::getDepartment, j -> j.eq(Department::getName, "Engineering"))
 *     .orderByAsc(User::getName)
 *     .where(q -> q.eq(User::getStatus, "ACTIVE"))
 *     .toTupleQuery(entityManager)
 *     .getResultList();
 * }</pre>
 *
 * @param <T> 根实体类型
 */
public class ProjectionSpec<T> {

  private final Class<T> entityClass;
  private final Map<String, SFunction<T, ?>> selections = new LinkedHashMap<>();
  private final QuerySpec<T> querySpec = new QuerySpec<>();
  private final List<JoinSpec> joins = new ArrayList<>();
  private final List<OrderSpec> orderSpecs = new ArrayList<>();
  private Class<?> dtoClass;

  /** 描述 JOIN 子句的内部记录类。 */
  private static final class JoinSpec {
    final String fieldName;
    final Consumer<?> config;
    final boolean left;

    <E> JoinSpec(String fieldName, Consumer<JoinGroup<E>> config, boolean left) {
      this.fieldName = fieldName;
      this.config = config;
      this.left = left;
    }
  }

  /**
   * JOIN 目标实体的嵌套条件构建器。
   *
   * <p>提供类似于 {@link com.zsubera.jpa.spec.ConditionBuilder} 的 API， 用于在 JOIN 子句中添加 ON 条件。
   *
   * @param <E> JOIN 目标实体类型
   */
  public static final class JoinGroup<E> {

    private final List<ConditionNode> conditions = new ArrayList<>();

    private JoinGroup() {}

    private static <E> JoinGroup<E> create() {
      return new JoinGroup<>();
    }

    /**
     * 添加等于条件。
     *
     * @param field 实体属性的方法引用
     * @param value 比较的值
     * @return 当前 JoinGroup 实例，支持链式调用
     * @see com.zsubera.jpa.spec.ConditionBuilder#eq(SFunction, Object)
     */
    public JoinGroup<E> eq(SFunction<E, ?> field, Object value) {
      conditions.add(new ConditionNode.Eq(LambdaUtils.getPropertyName(field), value));
      return this;
    }

    /**
     * 添加不等于条件。
     *
     * @param field 实体属性的方法引用
     * @param value 比较的值
     * @return 当前 JoinGroup 实例，支持链式调用
     * @see com.zsubera.jpa.spec.ConditionBuilder#ne(SFunction, Object)
     */
    public JoinGroup<E> ne(SFunction<E, ?> field, Object value) {
      conditions.add(new ConditionNode.Ne(LambdaUtils.getPropertyName(field), value));
      return this;
    }

    /**
     * 添加 LIKE 模糊匹配条件。
     *
     * @param field 实体属性的方法引用
     * @param value 匹配模式，不能为 null
     * @return 当前 JoinGroup 实例，支持链式调用
     * @throws IllegalArgumentException 如果 value 为 null
     * @see com.zsubera.jpa.spec.ConditionBuilder#like(SFunction, String)
     */
    public JoinGroup<E> like(SFunction<E, ?> field, String value) {
      if (value == null) {
        throw new IllegalArgumentException("value must not be null");
      }
      conditions.add(new ConditionNode.Like(LambdaUtils.getPropertyName(field), value));
      return this;
    }

    /**
     * 添加大于条件。
     *
     * @param field 实体属性的方法引用
     * @param value 比较的值，不能为 null
     * @return 当前 JoinGroup 实例，支持链式调用
     * @throws IllegalArgumentException 如果 value 为 null
     * @see com.zsubera.jpa.spec.ConditionBuilder#gt(SFunction, Comparable)
     */
    public JoinGroup<E> gt(SFunction<E, ?> field, Comparable<?> value) {
      if (value == null) {
        throw new IllegalArgumentException("value must not be null");
      }
      conditions.add(new ConditionNode.Gt(LambdaUtils.getPropertyName(field), value));
      return this;
    }

    /**
     * 添加小于条件。
     *
     * @param field 实体属性的方法引用
     * @param value 比较的值，不能为 null
     * @return 当前 JoinGroup 实例，支持链式调用
     * @throws IllegalArgumentException 如果 value 为 null
     * @see com.zsubera.jpa.spec.ConditionBuilder#lt(SFunction, Comparable)
     */
    public JoinGroup<E> lt(SFunction<E, ?> field, Comparable<?> value) {
      if (value == null) {
        throw new IllegalArgumentException("value must not be null");
      }
      conditions.add(new ConditionNode.Lt(LambdaUtils.getPropertyName(field), value));
      return this;
    }

    /**
     * 添加 IS NULL 条件。
     *
     * @param field 实体属性的方法引用
     * @return 当前 JoinGroup 实例，支持链式调用
     * @see com.zsubera.jpa.spec.ConditionBuilder#isNull(SFunction)
     */
    public JoinGroup<E> isNull(SFunction<E, ?> field) {
      conditions.add(new ConditionNode.IsNull(LambdaUtils.getPropertyName(field)));
      return this;
    }

    /**
     * 添加 IS NOT NULL 条件。
     *
     * @param field 实体属性的方法引用
     * @return 当前 JoinGroup 实例，支持链式调用
     * @see com.zsubera.jpa.spec.ConditionBuilder#isNotNull(SFunction)
     */
    public JoinGroup<E> isNotNull(SFunction<E, ?> field) {
      conditions.add(new ConditionNode.IsNotNull(LambdaUtils.getPropertyName(field)));
      return this;
    }

    /**
     * 投影 JOIN 条件的内部条件节点接口。
     *
     * <p>使用 sealed 接口和 record 实现，支持多种条件类型：
     *
     * <ul>
     *   <li>{@link Eq} - 等于条件
     *   <li>{@link Ne} - 不等于条件
     *   <li>{@link Like} - 模糊匹配条件
     *   <li>{@link Gt} - 大于条件
     *   <li>{@link Lt} - 小于条件
     *   <li>{@link IsNull} - IS NULL 条件
     *   <li>{@link IsNotNull} - IS NOT NULL 条件
     * </ul>
     */
    sealed interface ConditionNode {
      record Eq(String fieldName, Object value) implements ConditionNode {}

      record Ne(String fieldName, Object value) implements ConditionNode {}

      record Like(String fieldName, String value) implements ConditionNode {}

      record Gt(String fieldName, Comparable<?> value) implements ConditionNode {}

      record Lt(String fieldName, Comparable<?> value) implements ConditionNode {}

      record IsNull(String fieldName) implements ConditionNode {}

      record IsNotNull(String fieldName) implements ConditionNode {}
    }

    /**
     * 获取所有条件节点列表。
     *
     * @return 条件节点列表
     */
    List<ConditionNode> getConditions() {
      return conditions;
    }
  }

  /**
   * 描述 ORDER BY 子句的内部记录类。
   *
   * @param fieldName 字段名称
   * @param asc 是否升序排列
   */
  private record OrderSpec(String fieldName, boolean asc) {}

  /**
   * 创建投影查询构建器实例。
   *
   * @param entityClass 要查询的实体类
   */
  public ProjectionSpec(Class<T> entityClass) {
    this.entityClass = entityClass;
  }

  /**
   * 向 SELECT 子句添加要查询的字段。
   *
   * @param field 实体属性的方法引用
   * @return 当前 ProjectionSpec 实例，支持链式调用
   */
  public ProjectionSpec<T> select(SFunction<T, ?> field) {
    selections.put(LambdaUtils.getPropertyName(field), field);
    return this;
  }

  /**
   * 指定用于构造函数投影的 DTO 类。
   *
   * <p>DTO 必须有一个构造函数，其参数顺序和类型与选定的字段匹配。
   *
   * @param dtoClass DTO 类
   * @return 当前 ProjectionSpec 实例，支持链式调用
   */
  public ProjectionSpec<T> asDto(Class<?> dtoClass) {
    this.dtoClass = dtoClass;
    return this;
  }

  /**
   * 添加 INNER JOIN 关联查询，可对关联实体设置条件。
   *
   * @param field 一对多或一对一关系的方法引用
   * @param config 用于配置 JOIN 条件的消费者函数
   * @param <E> JOIN 目标实体类型
   * @return 当前 ProjectionSpec 实例，支持链式调用
   */
  public <E> ProjectionSpec<T> join(SFunction<T, ?> field, Consumer<JoinGroup<E>> config) {
    joins.add(new JoinSpec(LambdaUtils.getPropertyName(field), config, false));
    return this;
  }

  /**
   * 添加 LEFT JOIN 关联查询，可对关联实体设置条件。
   *
   * <p>LEFT JOIN 会返回左表的所有记录，即使右表中没有匹配的记录。
   *
   * @param field 一对多或一对一关系的方法引用
   * @param config 用于配置 JOIN 条件的消费者函数
   * @param <E> JOIN 目标实体类型
   * @return 当前 ProjectionSpec 实例，支持链式调用
   */
  public <E> ProjectionSpec<T> leftJoin(SFunction<T, ?> field, Consumer<JoinGroup<E>> config) {
    joins.add(new JoinSpec(LambdaUtils.getPropertyName(field), config, true));
    return this;
  }

  /**
   * 添加升序排序规则。
   *
   * @param field 实体属性的方法引用
   * @return 当前 ProjectionSpec 实例，支持链式调用
   */
  public ProjectionSpec<T> orderByAsc(SFunction<T, ?> field) {
    orderSpecs.add(new OrderSpec(LambdaUtils.getPropertyName(field), true));
    return this;
  }

  /**
   * 添加降序排序规则。
   *
   * @param field 实体属性的方法引用
   * @return 当前 ProjectionSpec 实例，支持链式调用
   */
  public ProjectionSpec<T> orderByDesc(SFunction<T, ?> field) {
    orderSpecs.add(new OrderSpec(LambdaUtils.getPropertyName(field), false));
    return this;
  }

  /**
   * 添加 WHERE 查询条件。
   *
   * @param config 用于配置查询条件的消费者函数
   * @return 当前 ProjectionSpec 实例，支持链式调用
   */
  public ProjectionSpec<T> where(Consumer<QuerySpec<T>> config) {
    config.accept(querySpec);
    return this;
  }

  /**
   * 直接访问底层的 {@link QuerySpec} 以进行链式调用。
   *
   * @return 底层的 QuerySpec 实例
   */
  @SuppressFBWarnings("EI_EXPOSE_REP")
  public QuerySpec<T> conditions() {
    return querySpec;
  }

  /**
   * 构建并返回以 {@link Tuple} 为结果类型的类型安全查询。
   *
   * @param em JPA 实体管理器
   * @return 返回 Tuple 结果的 TypedQuery 实例
   */
  public TypedQuery<Tuple> toTupleQuery(EntityManager em) {
    CriteriaBuilder cb = em.getCriteriaBuilder();
    CriteriaQuery<Tuple> query = cb.createTupleQuery();
    Root<T> root = query.from(entityClass);

    // Apply joins (side effects on root)
    resolveJoins(root, cb);

    // Apply selections
    List<jakarta.persistence.criteria.Selection<?>> selectionList = new ArrayList<>();
    for (String alias : selections.keySet()) {
      selectionList.add(root.get(alias).alias(alias));
    }
    query.multiselect(selectionList);

    // Apply WHERE
    applyPredicate(root, query, cb);

    // Apply ORDER BY
    applyOrderBy(root, cb, query);

    return em.createQuery(query);
  }

  /**
   * 构建并返回以 DTO 为结果类型的类型安全查询。
   *
   * <p>必须先调用 {@link #asDto(Class)} 方法指定 DTO 类。
   *
   * @param em JPA 实体管理器
   * @param <R> DTO 结果类型
   * @return 返回 DTO 结果的 TypedQuery 实例
   * @throws IllegalStateException 如果未调用 {@link #asDto(Class)} 方法
   */
  @SuppressWarnings("unchecked")
  public <R> TypedQuery<R> toDtoQuery(EntityManager em) {
    if (dtoClass == null) {
      throw new IllegalStateException("asDto() must be called before toDtoQuery()");
    }
    CriteriaBuilder cb = em.getCriteriaBuilder();
    CriteriaQuery<R> query = (CriteriaQuery<R>) cb.createQuery(dtoClass);
    Root<T> root = query.from(entityClass);

    // Apply joins
    resolveJoins(root, cb);

    // Apply selections as constructor arguments
    List<jakarta.persistence.criteria.Selection<?>> selectionList = new ArrayList<>();
    for (String fieldName : selections.keySet()) {
      selectionList.add(root.get(fieldName));
    }
    query.select(
        (CompoundSelection<R>)
            cb.construct(
                dtoClass, selectionList.toArray(new jakarta.persistence.criteria.Selection[0])));

    // Apply WHERE
    applyPredicate(root, query, cb);

    // Apply ORDER BY
    applyOrderBy(root, cb, query);

    return em.createQuery(query);
  }

  /**
   * 分页查询投影结果。
   *
   * @param em JPA 实体管理器
   * @param pageable 分页信息
   * @return 分页投影结果
   * @throws IllegalArgumentException 如果分页偏移量过大
   */
  public Page<Tuple> findPage(EntityManager em, Pageable pageable) {
    CriteriaBuilder cb = em.getCriteriaBuilder();

    // Handle unpaged
    if (pageable.isUnpaged()) {
      TypedQuery<Tuple> query = toTupleQuery(em);
      List<Tuple> allContent = query.getResultList();
      return new PageImpl<>(allContent);
    }

    // Count query
    CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
    Root<T> countRoot = countQuery.from(entityClass);
    countQuery.select(cb.count(countRoot));
    applyPredicate(countRoot, countQuery, cb);
    Long total = em.createQuery(countQuery).getSingleResult();

    // Data query
    TypedQuery<Tuple> query = toTupleQuery(em);
    if (pageable.getOffset() > Integer.MAX_VALUE) {
      throw new IllegalArgumentException("Offset too large: " + pageable.getOffset());
    }
    query.setFirstResult((int) pageable.getOffset());
    query.setMaxResults(pageable.getPageSize());
    List<Tuple> content = query.getResultList();

    return new PageImpl<>(content, pageable, total);
  }

  /**
   * 解析并应用所有 JOIN 子句。
   *
   * @param root 查询根实体
   * @param cb CriteriaBuilder 实例
   * @return JOIN 映射关系
   */
  @SuppressWarnings({"unchecked", "rawtypes"})
  private Map<String, Join<?, ?>> resolveJoins(Root<T> root, CriteriaBuilder cb) {
    Map<String, Join<?, ?>> joinMap = new LinkedHashMap<>();
    for (JoinSpec js : joins) {
      Join<?, ?> join =
          joinMap.computeIfAbsent(
              js.fieldName,
              k ->
                  js.left
                      ? root.join(js.fieldName, jakarta.persistence.criteria.JoinType.LEFT)
                      : root.join(js.fieldName));
      @SuppressWarnings("unchecked")
      Consumer<JoinGroup<Object>> cfg = (Consumer<JoinGroup<Object>>) js.config;
      JoinGroup<Object> group = JoinGroup.create();
      cfg.accept(group);
      List<Predicate> onPredicates = new ArrayList<>();
      for (JoinGroup.ConditionNode node : group.getConditions()) {
        if (node instanceof JoinGroup.ConditionNode.Eq eq) {
          onPredicates.add(cb.equal(join.get(eq.fieldName()), eq.value()));
        } else if (node instanceof JoinGroup.ConditionNode.Ne ne) {
          onPredicates.add(cb.notEqual(join.get(ne.fieldName()), ne.value()));
        } else if (node instanceof JoinGroup.ConditionNode.Like like) {
          onPredicates.add(cb.like(join.get(like.fieldName()).as(String.class), like.value()));
        } else if (node instanceof JoinGroup.ConditionNode.Gt gt) {
          @SuppressWarnings("unchecked")
          Expression<Comparable<Object>> gtExpr =
              (Expression<Comparable<Object>>) (Expression<?>) join.get(gt.fieldName());
          onPredicates.add(cb.greaterThan(gtExpr, (Comparable<Object>) gt.value()));
        } else if (node instanceof JoinGroup.ConditionNode.Lt lt) {
          @SuppressWarnings("unchecked")
          Expression<Comparable<Object>> ltExpr =
              (Expression<Comparable<Object>>) (Expression<?>) join.get(lt.fieldName());
          onPredicates.add(cb.lessThan(ltExpr, (Comparable<Object>) lt.value()));
        } else if (node instanceof JoinGroup.ConditionNode.IsNull isNull) {
          onPredicates.add(cb.isNull(join.get(isNull.fieldName())));
        } else if (node instanceof JoinGroup.ConditionNode.IsNotNull isNotNull) {
          onPredicates.add(cb.isNotNull(join.get(isNotNull.fieldName())));
        }
      }
      if (!onPredicates.isEmpty()) {
        join.on(cb.and(onPredicates.toArray(new Predicate[0])));
      }
    }
    return joinMap;
  }

  /**
   * 应用 WHERE 查询条件。
   *
   * @param root 查询根实体
   * @param query CriteriaQuery 实例
   * @param cb CriteriaBuilder 实例
   */
  @SuppressWarnings({"unchecked", "rawtypes"})
  private void applyPredicate(Root<T> root, CriteriaQuery<?> query, CriteriaBuilder cb) {
    jakarta.persistence.criteria.Predicate predicate =
        querySpec.toPredicate(root, (CriteriaQuery) query, cb);
    if (predicate != null) {
      query.where(predicate);
    }
  }

  /**
   * 应用 ORDER BY 排序规则。
   *
   * @param root 查询根实体
   * @param cb CriteriaBuilder 实例
   * @param query CriteriaQuery 实例
   */
  private void applyOrderBy(Root<T> root, CriteriaBuilder cb, CriteriaQuery<?> query) {
    if (!orderSpecs.isEmpty()) {
      List<jakarta.persistence.criteria.Order> orders = new ArrayList<>();
      for (OrderSpec os : orderSpecs) {
        Path<Object> path = root.get(os.fieldName());
        orders.add(os.asc() ? cb.asc(path) : cb.desc(path));
      }
      query.orderBy(orders);
    }
  }
}
