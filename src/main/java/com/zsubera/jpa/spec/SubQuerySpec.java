package com.zsubera.jpa.spec;

import com.zsubera.jpa.util.LambdaUtils;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import org.springframework.lang.Nullable;

/**
 * JPA EXISTS / NOT EXISTS 子查询的类型安全构建器。
 *
 * <p>
 * 提供子查询实体的条件方法，并可访问关联的外部查询根以构建关联谓词。
 *
 * <p>
 * <strong>设计说明：</strong>与 {@link ConditionBuilder} 使用延迟求值（构建在查询执行时解析的 {@link ConditionNode} 树）不同，{@code SubQuerySpec}
 * 使用<em>即时</em>求值——每个条件方法立即创建 JPA {@link jakarta.persistence.criteria.Predicate} 并添加到内部列表中。这是必要的，因为子查询必须在
 * 外部查询构建之前完全构造完成。谓词构造委托给 {@link PredicateHelper} 以与其他组件共享逻辑。
 *
 * <p>
 * 新增条件类型时，需同步更新以下位置：
 * <ol>
 * <li>{@link ConditionBuilder} — 查询构建器</li>
 * <li>{@link ConditionNode.Op} — 运算符枚举</li>
 * <li>{@link QuerySpec#resolveSimple} — 查询条件解析</li>
 * <li>{@link com.zsubera.jpa.projection.ProjectionSpec.ProjectionJoinGroup} — 投影 JOIN 条件</li>
 * <li>{@link com.zsubera.jpa.update.AbstractBulkOperationSpec} — 批量操作条件</li>
 * <li>此类（SubQuerySpec）— 子查询条件</li>
 * </ol>
 *
 * <p>
 * 通过 {@link QuerySpec#exists(Class, java.util.function.Consumer)} 或
 * {@link QuerySpec#notExists(Class, java.util.function.Consumer)} 使用。
 *
 * @param <S> 子查询实体类型
 */
@SuppressFBWarnings(value = "CT_CONSTRUCTOR_THROW",
    justification = "Static factory method validates parameters before constructor call")
public class SubQuerySpec<S> {

    private final Subquery<S> subquery;
    private final Root<S> root;
    private final CriteriaBuilder cb;
    private final Root<?> correlatedRoot;
    private final List<Predicate> predicates = new ArrayList<>();
    private boolean selectSet;
    private Class<?> selectType;
    /** 缓存的 SELECT 字段名，用于 inSubQuery 两阶段类型推断中的 select 重放。 */
    private String selectFieldName;

    private SubQuerySpec(Subquery<S> subquery, Root<S> root, Root<?> correlatedRoot, CriteriaBuilder cb) {
        this.subquery = subquery;
        this.root = root;
        this.correlatedRoot = correlatedRoot;
        this.cb = cb;
    }

    /**
     * 创建 SubQuerySpec 实例。
     *
     * @param subquery 子查询对象
     * @param root 子查询根
     * @param correlatedRoot 关联的外部查询根
     * @param cb CriteriaBuilder 实例
     * @return 新的 SubQuerySpec 实例
     * @throws IllegalArgumentException 如果任何参数为 null
     */
    static <S> SubQuerySpec<S> create(Subquery<S> subquery, Root<S> root, Root<?> correlatedRoot, CriteriaBuilder cb) {
        if (subquery == null) {
            throw new IllegalArgumentException("subquery must not be null");
        }
        if (root == null) {
            throw new IllegalArgumentException("root must not be null");
        }
        if (correlatedRoot == null) {
            throw new IllegalArgumentException("correlatedRoot must not be null");
        }
        if (cb == null) {
            throw new IllegalArgumentException("cb must not be null");
        }
        return new SubQuerySpec<>(subquery, root, correlatedRoot, cb);
    }

    void applyWhere() {
        if (!predicates.isEmpty()) {
            subquery.where(cb.and(predicates.toArray(new Predicate[0])));
        }
    }

    /**
     * 清除已添加的谓词。用于 inSubQuery 场景下的两阶段类型推断。
     */
    void clearPredicates() {
        predicates.clear();
    }

    boolean isSelectSet() {
        return selectSet;
    }

    /**
     * 返回关联的外部查询根，用于构建关联谓词。在 {@link #where(java.util.function.Function)} 中使用 以引用外部实体。
     *
     * <p>
     * <strong>类型安全警告：</strong>此方法执行未经检查的类型转换。调用方必须确保返回的 {@link Root} 与目标实体类型匹配， 否则可能导致运行时
     * {@link ClassCastException}。通常用于 {@link #correlatedEq(SFunction, SFunction)} 模式中。
     *
     * @param <T> 外部实体类型
     * @return 外部查询 {@link Root}
     */
    @SuppressWarnings("unchecked")
    public <T> Root<T> correlated() {
        return (Root<T>)correlatedRoot;
    }

    /**
     * 添加外部查询与子查询之间的等值关联条件。
     *
     * <p>
     * 这是关联子查询最常见的模式，例如：
     *
     * <pre>{@code
     * qs.exists(Order.class,
     *     sub -> sub.correlatedEq(Customer::getId, Order::getCustomerId).gt(Order::getAmount, 1000));
     * }</pre>
     *
     * 生成：{@code EXISTS (SELECT 1 FROM orders WHERE customer.id = orders.customer_id AND amount >
     * 1000)}
     *
     * @param outerField 外部实体的字段（例如 Customer::getId）
     * @param subField 子查询实体的对应字段（例如 Order::getCustomerId）
     * @param <T> 外部实体类型
     * @return 当前 SubQuerySpec 实例，支持链式调用
     */
    public <T> SubQuerySpec<S> correlatedEq(SFunction<T, ?> outerField, SFunction<S, ?> subField) {
        if (outerField == null) {
            throw new IllegalArgumentException("outerField must not be null");
        }
        if (subField == null) {
            throw new IllegalArgumentException("subField must not be null");
        }
        predicates.add(cb.equal(correlatedRoot.get(LambdaUtils.getPropertyName(outerField)),
            root.get(LambdaUtils.getPropertyName(subField))));
        return this;
    }

    /**
     * 获取字段属性名称。
     *
     * @param field 字段函数
     * @return 属性名称
     * @throws IllegalArgumentException 如果字段为 null
     */
    private String property(SFunction<S, ?> field) {
        if (field == null) {
            throw new IllegalArgumentException("field must not be null");
        }
        return LambdaUtils.getPropertyName(field);
    }

    // ---- 比较运算符 ----

    /**
     * 添加子查询实体的等值条件。value 为 null 时自动转为 IS NULL。
     *
     * @param field 实体字段
     * @param value 比较值，null 表示 IS NULL
     * @return 当前 SubQuerySpec 实例，支持链式调用
     * @throws IllegalArgumentException 如果 field 为 null
     */
    public SubQuerySpec<S> eq(SFunction<S, ?> field, @Nullable Object value) {
        predicates.add(PredicateHelper.eq(root, property(field), value, cb));
        return this;
    }

    /**
     * 添加子查询实体的不等值条件。value 为 null 时自动转为 IS NOT NULL。
     *
     * @param field 实体字段
     * @param value 比较值，null 表示 IS NOT NULL
     * @return 当前 SubQuerySpec 实例，支持链式调用
     * @throws IllegalArgumentException 如果 field 为 null
     */
    public SubQuerySpec<S> ne(SFunction<S, ?> field, @Nullable Object value) {
        predicates.add(PredicateHelper.ne(root, property(field), value, cb));
        return this;
    }

    /**
     * 添加子查询实体的大于条件。
     *
     * @param field 实体字段
     * @param value 比较值
     * @return 当前 SubQuerySpec 实例，支持链式调用
     * @throws IllegalArgumentException 如果 value 为 null
     */
    public SubQuerySpec<S> gt(SFunction<S, ?> field, Comparable<?> value) {
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
        predicates.add(PredicateHelper.gt(root, property(field), value, cb));
        return this;
    }

    /**
     * 添加子查询实体的大于等于条件。
     *
     * @param field 实体字段
     * @param value 比较值
     * @return 当前 SubQuerySpec 实例，支持链式调用
     * @throws IllegalArgumentException 如果 value 为 null
     */
    public SubQuerySpec<S> ge(SFunction<S, ?> field, Comparable<?> value) {
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
        predicates.add(PredicateHelper.ge(root, property(field), value, cb));
        return this;
    }

    /**
     * 添加子查询实体的小于条件。
     *
     * @param field 实体字段
     * @param value 比较值
     * @return 当前 SubQuerySpec 实例，支持链式调用
     * @throws IllegalArgumentException 如果 value 为 null
     */
    public SubQuerySpec<S> lt(SFunction<S, ?> field, Comparable<?> value) {
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
        predicates.add(PredicateHelper.lt(root, property(field), value, cb));
        return this;
    }

    /**
     * 添加子查询实体的小于等于条件。
     *
     * @param field 实体字段
     * @param value 比较值
     * @return 当前 SubQuerySpec 实例，支持链式调用
     * @throws IllegalArgumentException 如果 value 为 null
     */
    public SubQuerySpec<S> le(SFunction<S, ?> field, Comparable<?> value) {
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
        predicates.add(PredicateHelper.le(root, property(field), value, cb));
        return this;
    }

    // ---- 字符串运算符 ----

    /**
     * 添加子查询实体的 LIKE 条件。
     *
     * <p>
     * <b>安全警告</b>: 此方法不转义 {@code %} 和 {@code _} 通配符。如果 {@code value} 来自用户输入， 请使用 {@link #likeSafe(SFunction, String)}
     * 方法，该方法会自动转义通配符。
     * </p>
     *
     * @param field 实体字段
     * @param value 匹配模式（可使用 % 通配符）
     * @return 当前 SubQuerySpec 实例，支持链式调用
     * @throws IllegalArgumentException 如果 value 为 null
     * @see #likeSafe(SFunction, String)
     * @deprecated 使用 {@link #likeSafe(SFunction, String)} 替代，该方法会自动转义通配符防止 LIKE 注入
     */
    @Deprecated(since = "1.1.0", forRemoval = true)
    public SubQuerySpec<S> like(SFunction<S, ?> field, String value) {
        throw new UnsupportedOperationException("like() has been removed in 1.1.0. Use likeSafe() instead. "
            + "The original like() does not escape SQL wildcards, posing a security risk.");
    }

    /**
     * 添加子查询实体的 NOT LIKE 条件。
     *
     * <p>
     * <b>安全警告</b>: 此方法不转义 {@code %} 和 {@code _} 通配符。如果 {@code value} 来自用户输入， 请使用
     * {@link #notLikeSafe(SFunction, String)} 方法，该方法会自动转义通配符。
     * </p>
     *
     * @param field 实体字段
     * @param value 匹配模式（可使用 % 通配符）
     * @return 当前 SubQuerySpec 实例，支持链式调用
     * @throws IllegalArgumentException 如果 value 为 null
     * @see #notLikeSafe(SFunction, String)
     * @deprecated 使用 {@link #notLikeSafe(SFunction, String)} 替代，该方法会自动转义通配符防止 LIKE 注入
     */
    @Deprecated(since = "1.1.0", forRemoval = true)
    public SubQuerySpec<S> notLike(SFunction<S, ?> field, String value) {
        throw new UnsupportedOperationException("notLike() has been removed in 1.1.0. Use notLikeSafe() instead. "
            + "The original notLike() does not escape SQL wildcards, posing a security risk.");
    }

    /**
     * 添加带自动通配符转义的 LIKE 条件。值中的 {@code %} 或 {@code _} 字符会被转义，作为字面量处理。
     *
     * <p>
     * 此方法是 {@link #like(SFunction, String)} 的安全版本，适用于处理用户输入。
     *
     * @param field 实体字段
     * @param value 要匹配的原始字符串值（通配符会被转义）
     * @return 当前 SubQuerySpec 实例，支持链式调用
     * @throws IllegalArgumentException 如果 value 为 null
     * @see #like(SFunction, String)
     */
    public SubQuerySpec<S> likeSafe(SFunction<S, ?> field, String value) {
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
        predicates.add(PredicateHelper.like(root, property(field), PredicateHelper.escapeLikeWildcards(value), cb,
            PredicateHelper.LIKE_ESCAPE_CHAR));
        return this;
    }

    /**
     * 添加带自动通配符转义的 NOT LIKE 条件。值中的 {@code %} 或 {@code _} 字符会被转义，作为字面量处理。
     *
     * <p>
     * 此方法是 {@link #notLike(SFunction, String)} 的安全版本，适用于处理用户输入。
     *
     * @param field 实体字段
     * @param value 要匹配的原始字符串值（通配符会被转义）
     * @return 当前 SubQuerySpec 实例，支持链式调用
     * @throws IllegalArgumentException 如果 value 为 null
     * @see #notLike(SFunction, String)
     */
    public SubQuerySpec<S> notLikeSafe(SFunction<S, ?> field, String value) {
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
        predicates.add(PredicateHelper.notLike(root, property(field), PredicateHelper.escapeLikeWildcards(value), cb,
            PredicateHelper.LIKE_ESCAPE_CHAR));
        return this;
    }

    /**
     * 添加子查询实体的前缀匹配条件。
     *
     * @param field 实体字段
     * @param value 前缀值
     * @return 当前 SubQuerySpec 实例，支持链式调用
     * @throws IllegalArgumentException 如果 value 为 null
     */
    public SubQuerySpec<S> startsWith(SFunction<S, ?> field, String value) {
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
        predicates.add(PredicateHelper.startsWith(root, property(field), value, cb));
        return this;
    }

    /**
     * 添加子查询实体的后缀匹配条件。
     *
     * @param field 实体字段
     * @param value 后缀值
     * @return 当前 SubQuerySpec 实例，支持链式调用
     * @throws IllegalArgumentException 如果 value 为 null
     */
    public SubQuerySpec<S> endsWith(SFunction<S, ?> field, String value) {
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
        predicates.add(PredicateHelper.endsWith(root, property(field), value, cb));
        return this;
    }

    /**
     * 添加子查询实体的包含匹配条件。
     *
     * @param field 实体字段
     * @param value 包含的值
     * @return 当前 SubQuerySpec 实例，支持链式调用
     * @throws IllegalArgumentException 如果 value 为 null
     */
    public SubQuerySpec<S> contains(SFunction<S, ?> field, String value) {
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
        predicates.add(PredicateHelper.contains(root, property(field), value, cb));
        return this;
    }

    // ---- 集合运算符 ----

    /**
     * 添加子查询实体的 BETWEEN 条件。
     *
     * @param field 实体字段
     * @param start 范围起始值（包含）
     * @param end 范围结束值（包含）
     * @return 当前 SubQuerySpec 实例，支持链式调用
     * @throws IllegalArgumentException 如果 start 或 end 为 null，或 start 大于 end
     */
    public SubQuerySpec<S> between(SFunction<S, ?> field, Comparable<?> start, Comparable<?> end) {
        PredicateHelper.validateRange(start, end);
        predicates.add(PredicateHelper.between(root, property(field), start, end, cb));
        return this;
    }

    /**
     * 添加子查询实体的 NOT BETWEEN 条件。
     *
     * @param field 实体字段
     * @param start 范围起始值（包含）
     * @param end 范围结束值（包含）
     * @return 当前 SubQuerySpec 实例，支持链式调用
     * @throws IllegalArgumentException 如果 start 或 end 为 null，或 start 大于 end
     */
    public SubQuerySpec<S> notBetween(SFunction<S, ?> field, Comparable<?> start, Comparable<?> end) {
        PredicateHelper.validateRange(start, end);
        predicates.add(PredicateHelper.notBetween(root, property(field), start, end, cb));
        return this;
    }

    /**
     * 添加子查询实体的 IN 条件（可变参数形式）。
     *
     * @param field 实体字段
     * @param values 值列表
     * @return 当前 SubQuerySpec 实例，支持链式调用
     * @throws IllegalArgumentException 如果 values 为 null 或空
     */
    public SubQuerySpec<S> in(SFunction<S, ?> field, Object... values) {
        if (values == null || values.length == 0) {
            throw new IllegalArgumentException("values must not be empty");
        }
        predicates.add(PredicateHelper.in(root, property(field), values, cb));
        return this;
    }

    /**
     * 添加子查询实体的 NOT IN 条件（可变参数形式）。
     *
     * @param field 实体字段
     * @param values 值列表
     * @return 当前 SubQuerySpec 实例，支持链式调用
     * @throws IllegalArgumentException 如果 values 为 null 或空
     */
    public SubQuerySpec<S> notIn(SFunction<S, ?> field, Object... values) {
        if (values == null || values.length == 0) {
            throw new IllegalArgumentException("values must not be empty");
        }
        predicates.add(PredicateHelper.notIn(root, property(field), values, cb));
        return this;
    }

    /**
     * 添加子查询实体的 IN 条件（集合形式）。
     *
     * @param field 实体字段
     * @param values 值集合
     * @return 当前 SubQuerySpec 实例，支持链式调用
     * @throws IllegalArgumentException 如果 values 为 null 或空
     */
    public SubQuerySpec<S> in(SFunction<S, ?> field, Collection<?> values) {
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException("values must not be empty");
        }
        predicates.add(PredicateHelper.in(root, property(field), values, cb));
        return this;
    }

    /**
     * 添加子查询实体的 NOT IN 条件（集合形式）。
     *
     * @param field 实体字段
     * @param values 值集合
     * @return 当前 SubQuerySpec 实例，支持链式调用
     * @throws IllegalArgumentException 如果 values 为 null 或空
     */
    public SubQuerySpec<S> notIn(SFunction<S, ?> field, Collection<?> values) {
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException("values must not be empty");
        }
        predicates.add(PredicateHelper.notIn(root, property(field), values, cb));
        return this;
    }

    // ---- Null 运算符 ----

    /**
     * 添加子查询实体的 IS NULL 条件。
     *
     * @param field 实体字段
     * @return 当前 SubQuerySpec 实例，支持链式调用
     */
    public SubQuerySpec<S> isNull(SFunction<S, ?> field) {
        predicates.add(PredicateHelper.isNull(root, property(field), cb));
        return this;
    }

    /**
     * 添加子查询实体的 IS NOT NULL 条件。
     *
     * @param field 实体字段
     * @return 当前 SubQuerySpec 实例，支持链式调用
     */
    public SubQuerySpec<S> isNotNull(SFunction<S, ?> field) {
        predicates.add(PredicateHelper.isNotNull(root, property(field), cb));
        return this;
    }

    /**
     * 添加子查询实体的忽略大小写的等值条件。
     *
     * @param field 实体字段
     * @param value 比较值
     * @return 当前 SubQuerySpec 实例，支持链式调用
     */
    public SubQuerySpec<S> eqIgnoreCase(SFunction<S, ?> field, String value) {
        predicates.add(PredicateHelper.eqIgnoreCase(root, property(field), value, cb));
        return this;
    }

    /**
     * 添加子查询实体的忽略大小写的不等条件。
     *
     * @param field 实体字段
     * @param value 比较值
     * @return 当前 SubQuerySpec 实例，支持链式调用
     */
    public SubQuerySpec<S> neIgnoreCase(SFunction<S, ?> field, String value) {
        predicates.add(PredicateHelper.neIgnoreCase(root, property(field), value, cb));
        return this;
    }

    /**
     * 添加子查询实体的忽略大小写的 LIKE 条件。
     *
     * <p>
     * 值中的 {@code %} 或 {@code _} 字符会被转义，作为字面量处理，防止 LIKE 注入。
     *
     * @param field 实体字段
     * @param value 要匹配的原始字符串值（通配符会被转义）
     * @return 当前 SubQuerySpec 实例，支持链式调用
     * @throws IllegalArgumentException 如果 value 为 null
     */
    public SubQuerySpec<S> likeIgnoreCase(SFunction<S, ?> field, String value) {
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
        String escaped = PredicateHelper.escapeLikeWildcards(value);
        predicates.add(PredicateHelper.likeIgnoreCase(root, property(field), "%" + escaped + "%", cb,
            PredicateHelper.LIKE_ESCAPE_CHAR));
        return this;
    }

    // ---- 集合空检查 ----

    /**
     * 添加子查询实体的集合为空条件。
     *
     * @param field 实体集合字段
     * @return 当前 SubQuerySpec 实例，支持链式调用
     */
    public SubQuerySpec<S> isEmpty(SFunction<S, ?> field) {
        predicates.add(PredicateHelper.isEmpty(root, property(field), cb));
        return this;
    }

    /**
     * 添加子查询实体的集合不为空条件。
     *
     * @param field 实体集合字段
     * @return 当前 SubQuerySpec 实例，支持链式调用
     */
    public SubQuerySpec<S> isNotEmpty(SFunction<S, ?> field) {
        predicates.add(PredicateHelper.isNotEmpty(root, property(field), cb));
        return this;
    }

    // ---- 多字段搜索 ----

    /**
     * 添加多字段模糊搜索条件。对指定的多个字段进行 OR 组合的 LIKE 查询。
     *
     * @param keyword 搜索关键词
     * @param fields 要搜索的实体字段列表
     * @return 当前 SubQuerySpec 实例，支持链式调用
     * @throws IllegalArgumentException 如果 fields 中包含 null 元素
     */
    public SubQuerySpec<S> multiLike(String keyword, SFunction<S, ?>... fields) {
        if (fields == null) {
            throw new IllegalArgumentException("fields must not be null");
        }
        if (keyword != null && !keyword.isEmpty() && fields.length > 0) {
            String pattern = "%" + PredicateHelper.escapeLikeWildcards(keyword) + "%";
            List<Predicate> likes = new ArrayList<>();
            for (SFunction<S, ?> field : fields) {
                if (field == null) {
                    throw new IllegalArgumentException("fields must not contain null elements");
                }
                likes.add(
                    cb.like(root.get(property(field)).as(String.class), pattern, PredicateHelper.LIKE_ESCAPE_CHAR));
            }
            if (!likes.isEmpty()) {
                predicates.add(likes.size() == 1 ? likes.get(0) : cb.or(likes.toArray(new Predicate[0])));
            }
        }
        return this;
    }

    /**
     * 添加多字段模糊搜索条件（字符串字段名版本）。
     *
     * <p>
     * 适用于只有字符串形式字段名的场景（如通用解析器、动态字段名等）， 无法使用方法引用时的替代方案。
     *
     * <pre>{@code
     * // 使用字符串字段名
     * sub.multiLike("keyword", "name", "email", "phone");
     *
     * // 动态字段名
     * String[] searchFields = {"name", "email"};
     * sub.multiLike("keyword", searchFields);
     * }</pre>
     *
     * @param keyword 搜索关键词
     * @param fieldNames 要搜索的字段名字符串列表
     * @return 当前 SubQuerySpec 实例，支持链式调用
     * @throws IllegalArgumentException 如果 fieldNames 为 null、空或包含 null 元素
     */
    public SubQuerySpec<S> multiLike(String keyword, String... fieldNames) {
        if (fieldNames == null || fieldNames.length == 0) {
            throw new IllegalArgumentException("fieldNames must not be empty");
        }
        if (keyword != null && !keyword.isEmpty()) {
            String pattern = "%" + PredicateHelper.escapeLikeWildcards(keyword) + "%";
            List<Predicate> likes = new ArrayList<>();
            for (String fieldName : fieldNames) {
                if (fieldName == null) {
                    throw new IllegalArgumentException("fieldNames must not contain null elements");
                }
                if (!ConditionBuilder.SAFE_FIELD_NAME_PATTERN.matcher(fieldName).matches()) {
                    throw new IllegalArgumentException("fieldName contains invalid characters: " + fieldName);
                }
                likes.add(cb.like(root.get(fieldName).as(String.class), pattern, PredicateHelper.LIKE_ESCAPE_CHAR));
            }
            if (!likes.isEmpty()) {
                predicates.add(likes.size() == 1 ? likes.get(0) : cb.or(likes.toArray(new Predicate[0])));
            }
        }
        return this;
    }

    // ---- 条件便捷方法 ----

    /**
     * 仅在 {@code condition} 为 true 时添加等值条件。
     *
     * @param condition 是否添加条件的标志
     * @param field 实体属性的方法引用
     * @param value 要比较的值
     * @return 当前 SubQuerySpec 实例，支持链式调用
     */
    public SubQuerySpec<S> eq(boolean condition, SFunction<S, ?> field, @Nullable Object value) {
        return condition ? eq(field, value) : this;
    }

    /**
     * 仅在 {@code condition} 为 true 时添加不等条件。
     *
     * @param condition 是否添加条件的标志
     * @param field 实体属性的方法引用
     * @param value 要比较的值
     * @return 当前 SubQuerySpec 实例，支持链式调用
     */
    public SubQuerySpec<S> ne(boolean condition, SFunction<S, ?> field, @Nullable Object value) {
        return condition ? ne(field, value) : this;
    }

    /**
     * 仅在 {@code condition} 为 true 时添加大于条件。
     *
     * @param condition 是否添加条件的标志
     * @param field 实体属性的方法引用
     * @param value 要比较的值
     * @return 当前 SubQuerySpec 实例，支持链式调用
     */
    public SubQuerySpec<S> gt(boolean condition, SFunction<S, ?> field, Comparable<?> value) {
        return condition ? gt(field, value) : this;
    }

    /**
     * 仅在 {@code condition} 为 true 时添加大于等于条件。
     *
     * @param condition 是否添加条件的标志
     * @param field 实体属性的方法引用
     * @param value 要比较的值
     * @return 当前 SubQuerySpec 实例，支持链式调用
     */
    public SubQuerySpec<S> ge(boolean condition, SFunction<S, ?> field, Comparable<?> value) {
        return condition ? ge(field, value) : this;
    }

    /**
     * 仅在 {@code condition} 为 true 时添加小于条件。
     *
     * @param condition 是否添加条件的标志
     * @param field 实体属性的方法引用
     * @param value 要比较的值
     * @return 当前 SubQuerySpec 实例，支持链式调用
     */
    public SubQuerySpec<S> lt(boolean condition, SFunction<S, ?> field, Comparable<?> value) {
        return condition ? lt(field, value) : this;
    }

    /**
     * 仅在 {@code condition} 为 true 时添加小于等于条件。
     *
     * @param condition 是否添加条件的标志
     * @param field 实体属性的方法引用
     * @param value 要比较的值
     * @return 当前 SubQuerySpec 实例，支持链式调用
     */
    public SubQuerySpec<S> le(boolean condition, SFunction<S, ?> field, Comparable<?> value) {
        return condition ? le(field, value) : this;
    }

    /**
     * 仅在 {@code condition} 为 true 时添加前缀匹配条件。
     *
     * @param condition 是否添加条件的标志
     * @param field 实体属性的方法引用
     * @param value 前缀字符串值
     * @return 当前 SubQuerySpec 实例，支持链式调用
     */
    public SubQuerySpec<S> startsWith(boolean condition, SFunction<S, ?> field, String value) {
        return condition ? startsWith(field, value) : this;
    }

    /**
     * 仅在 {@code condition} 为 true 时添加后缀匹配条件。
     *
     * @param condition 是否添加条件的标志
     * @param field 实体属性的方法引用
     * @param value 后缀字符串值
     * @return 当前 SubQuerySpec 实例，支持链式调用
     */
    public SubQuerySpec<S> endsWith(boolean condition, SFunction<S, ?> field, String value) {
        return condition ? endsWith(field, value) : this;
    }

    /**
     * 仅在 {@code condition} 为 true 时添加包含匹配条件。
     *
     * @param condition 是否添加条件的标志
     * @param field 实体属性的方法引用
     * @param value 要包含的子字符串值
     * @return 当前 SubQuerySpec 实例，支持链式调用
     */
    public SubQuerySpec<S> contains(boolean condition, SFunction<S, ?> field, String value) {
        return condition ? contains(field, value) : this;
    }

    /**
     * 仅在 {@code condition} 为 true 时添加 IN 条件。
     *
     * @param condition 是否添加条件的标志
     * @param field 实体属性的方法引用
     * @param values 值集合
     * @return 当前 SubQuerySpec 实例，支持链式调用
     */
    public SubQuerySpec<S> in(boolean condition, SFunction<S, ?> field, Object... values) {
        return condition ? in(field, values) : this;
    }

    /**
     * 仅在 {@code condition} 为 true 时添加 NOT IN 条件。
     *
     * @param condition 是否添加条件的标志
     * @param field 实体属性的方法引用
     * @param values 值集合
     * @return 当前 SubQuerySpec 实例，支持链式调用
     */
    public SubQuerySpec<S> notIn(boolean condition, SFunction<S, ?> field, Object... values) {
        return condition ? notIn(field, values) : this;
    }

    /**
     * 仅在 {@code condition} 为 true 时添加 BETWEEN 条件。
     *
     * @param condition 是否添加条件的标志
     * @param field 实体属性的方法引用
     * @param start 范围起始值（包含）
     * @param end 范围结束值（包含）
     * @return 当前 SubQuerySpec 实例，支持链式调用
     */
    public SubQuerySpec<S> between(boolean condition, SFunction<S, ?> field, Comparable<?> start, Comparable<?> end) {
        return condition ? between(field, start, end) : this;
    }

    /**
     * 仅在 {@code condition} 为 true 时添加 NOT BETWEEN 条件。
     *
     * @param condition 是否添加条件的标志
     * @param field 实体属性的方法引用
     * @param start 范围起始值（包含）
     * @param end 范围结束值（包含）
     * @return 当前 SubQuerySpec 实例，支持链式调用
     */
    public SubQuerySpec<S> notBetween(boolean condition, SFunction<S, ?> field, Comparable<?> start,
        Comparable<?> end) {
        return condition ? notBetween(field, start, end) : this;
    }

    /**
     * 仅在 {@code condition} 为 true 时添加多字段模糊搜索条件。
     *
     * @param condition 是否添加条件的标志
     * @param keyword 搜索关键词
     * @param fields 要搜索的实体字段列表
     * @return 当前 SubQuerySpec 实例，支持链式调用
     */
    public SubQuerySpec<S> multiLike(boolean condition, String keyword, SFunction<S, ?>... fields) {
        return condition ? multiLike(keyword, fields) : this;
    }

    /**
     * 仅在 {@code condition} 为 true 时添加 IS EMPTY 条件。
     *
     * @param condition 是否添加条件的标志
     * @param field 实体属性的方法引用
     * @return 当前 SubQuerySpec 实例，支持链式调用
     */
    public SubQuerySpec<S> isEmpty(boolean condition, SFunction<S, ?> field) {
        return condition ? isEmpty(field) : this;
    }

    /**
     * 仅在 {@code condition} 为 true 时添加 IS NOT EMPTY 条件。
     *
     * @param condition 是否添加条件的标志
     * @param field 实体属性的方法引用
     * @return 当前 SubQuerySpec 实例，支持链式调用
     */
    public SubQuerySpec<S> isNotEmpty(boolean condition, SFunction<S, ?> field) {
        return condition ? isNotEmpty(field) : this;
    }

    /**
     * 使用子查询根和 CriteriaBuilder 添加原始谓词。作为复杂条件或关联谓词的扩展机制。 要引用外部查询根，请使用 {@link #correlated()}。
     *
     * <pre>{@code
     * qs.exists(Child.class, sub -> sub
     *     .where(r -> cb.and(cb.equal(r.get("parent"), sub.correlated()), cb.greaterThan(r.get("amount"), 0))));
     * }</pre>
     *
     * <p>
     * <strong>安全警告：此方法绕过类型安全机制，存在潜在的SQL注入风险！</strong>
     * <ul>
     * <li>请勿使用用户输入的字符串拼接字段名，如 {@code root.get(userInput)}，这可能导致 SQL 注入</li>
     * <li>建议优先使用类型安全的方法引用 API（如 {@code eq(Entity::getField, value)}）</li>
     * <li>如果必须使用字符串字面量，请确保是硬编码的常量，而非运行时拼接</li>
     * </ul>
     *
     * @param condition 谓词函数，接收子查询根返回谓词
     * @return 当前 SubQuerySpec 实例，支持链式调用
     * @deprecated 推荐使用类型安全的 {@link #eq(SFunction, Object)}、{@link #like(SFunction, String)} 等方法替代。 此方法绕过类型安全机制，存在潜在的
     *             SQL 注入风险。
     */
    @Deprecated(since = "1.1.0", forRemoval = true)
    public SubQuerySpec<S> where(java.util.function.Function<Root<S>, Predicate> condition) {
        if (condition == null) {
            throw new IllegalArgumentException("condition must not be null");
        }
        throw new UnsupportedOperationException("where(Function) has been removed for security reasons. "
            + "This method bypasses type safety and exposes SQL injection risk. "
            + "Use type-safe methods like eq(), like(), contains(), etc. instead.");
    }

    /**
     * 设置此子查询的 SELECT 子句。如果未调用，子查询默认选择子查询根。
     *
     * <p>
     * <strong>幂等性说明：</strong>如果通过 {@link #presetSelectType(Class, String)} 已预设了 selectType（例如在 inSubQuery 的两阶段类型推断中），
     * 则此方法在第二次调用时会跳过重复设置，避免不必要的操作。真实子查询的 SELECT 子句通过 {@link #applySelectToSubquery()} 方法在 lambda 执行后正确设置。
     *
     * @param field 要选择的实体字段
     * @return 当前 SubQuerySpec 实例，支持链式调用
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public SubQuerySpec<S> select(SFunction<S, ?> field) {
        if (field == null) {
            throw new IllegalArgumentException("field must not be null");
        }
        if (selectSet) {
            // 已设置过 select（通过 presetSelectType），跳过重复设置
            return this;
        }
        String propName = LambdaUtils.getPropertyName(field);
        Path selectPath = root.get(propName);
        subquery.select(selectPath);
        selectSet = true;
        selectType = selectPath.getJavaType();
        selectFieldName = propName;
        return this;
    }

    /**
     * 获取 SELECT 字段名。
     *
     * @return SELECT 字段名，如果未设置 SELECT 则返回 null
     */
    String getSelectFieldName() {
        return selectFieldName;
    }

    /**
     * 获取 SELECT 子句选择的字段类型。
     *
     * @return 选择的字段类型，如果未设置 SELECT 则返回 null
     */
    Class<?> getSelectType() {
        return selectType;
    }

    /**
     * 预设 SELECT 字段类型和字段名，避免 inSubQuery 场景下配置 lambda 的副作用问题。
     *
     * <p>
     * 此方法用于 {@link QuerySpec#resolveInSubQueryInternal} 中的第一阶段类型推断结果传递。通过预先设置类型和字段信息， 第二阶段中 {@link #select(SFunction)}
     * 调用将变为幂等操作（跳过重复设置），而真实子查询的 SELECT 子句通过 {@link #applySelectToSubquery()} 方法在 lambda 执行后正确设置。
     *
     * @param selectType SELECT 字段类型
     * @param selectFieldName SELECT 字段名
     */
    void presetSelectType(Class<?> selectType, String selectFieldName) {
        this.selectType = selectType;
        this.selectFieldName = selectFieldName;
        this.selectSet = true;
    }

    /**
     * 将缓存的 SELECT 字段应用到真实子查询。
     *
     * <p>
     * 用于 inSubQuery 的第二阶段：在 lambda 执行完成后，将 SELECT 子句正确设置到真实子查询上。 此方法在 {@link QuerySpec#resolveInSubQueryInternal} 中调用。
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    void applySelectToSubquery() {
        if (selectFieldName != null && selectSet) {
            Path selectPath = root.get(selectFieldName);
            subquery.select(selectPath);
        }
    }

    /**
     * 提取配置 lambda 中 SELECT 子句的目标类型和字段名，而不执行条件构建。
     *
     * <p>
     * 创建一个临时的 SubQuerySpec 实例来应用配置 lambda，仅提取 selectType 和 selectFieldName 信息。 临时实例的谓词不会被应用到任何子查询。此方法用于
     * {@link QuerySpec#resolveInSubQueryInternal} 中， 使第二阶段的 select() 调用变为幂等操作。
     *
     * @param <S> 子查询实体类型
     * @param subEntity 子查询实体类
     * @param config 配置 lambda
     * @param cb CriteriaBuilder 实例
     * @return SELECT 信息的数组 [selectType, selectFieldName]，如果未调用 select() 则两者均为 null
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    static <S> Object[] extractSelectInfo(Class<S> subEntity, Consumer<SubQuerySpec<S>> config, CriteriaBuilder cb) {
        // 创建临时子查询仅用于提取类型信息
        jakarta.persistence.criteria.CriteriaQuery<?> tempQuery = cb.createQuery(Object.class);
        jakarta.persistence.criteria.Subquery<S> tempSub = tempQuery.subquery(subEntity);
        Root<S> tempRoot = tempSub.from(subEntity);
        SubQuerySpec<S> tempSpec = SubQuerySpec.create(tempSub, tempRoot, tempRoot, cb);
        config.accept(tempSpec);
        return new Object[] {tempSpec.getSelectType(), tempSpec.getSelectFieldName()};
    }
}
