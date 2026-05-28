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
 * 通过 {@link QuerySpec#exists(Class, java.util.function.Consumer)} 或
 * {@link QuerySpec#notExists(Class, java.util.function.Consumer)} 使用。
 *
 * @param <S> 子查询实体类型
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
     * 返回关联的外部查询根，用于构建关联谓词。在 {@link #where(java.util.function.Function)} 中使用 以引用外部实体。
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
     * 添加子查询实体的等值条件。
     *
     * @param field 实体字段
     * @param value 比较值
     * @return 当前 SubQuerySpec 实例，支持链式调用
     */
    public SubQuerySpec<S> eq(SFunction<S, ?> field, Object value) {
        predicates.add(PredicateHelper.eq(root, property(field), value, cb));
        return this;
    }

    /**
     * 添加子查询实体的不等值条件。
     *
     * @param field 实体字段
     * @param value 比较值
     * @return 当前 SubQuerySpec 实例，支持链式调用
     */
    public SubQuerySpec<S> ne(SFunction<S, ?> field, Object value) {
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
     * @param field 实体字段
     * @param value 匹配模式（可使用 % 通配符）
     * @return 当前 SubQuerySpec 实例，支持链式调用
     * @throws IllegalArgumentException 如果 value 为 null
     */
    public SubQuerySpec<S> like(SFunction<S, ?> field, String value) {
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
        predicates.add(PredicateHelper.like(root, property(field), value, cb));
        return this;
    }

    /**
     * 添加子查询实体的 NOT LIKE 条件。
     *
     * @param field 实体字段
     * @param value 匹配模式（可使用 % 通配符）
     * @return 当前 SubQuerySpec 实例，支持链式调用
     * @throws IllegalArgumentException 如果 value 为 null
     */
    public SubQuerySpec<S> notLike(SFunction<S, ?> field, String value) {
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
        predicates.add(PredicateHelper.notLike(root, property(field), value, cb));
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
        if (start == null) {
            throw new IllegalArgumentException("start must not be null");
        }
        if (end == null) {
            throw new IllegalArgumentException("end must not be null");
        }
        if (start.getClass() != end.getClass()) {
            throw new IllegalArgumentException("start and end must be of the same type, but got "
                + start.getClass().getName() + " and " + end.getClass().getName());
        }
        @SuppressWarnings({"unchecked", "rawtypes"})
        int cmp = ((java.lang.Comparable)start).compareTo(end);
        if (cmp > 0) {
            throw new IllegalArgumentException("start must not be greater than end");
        }
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
        if (start == null) {
            throw new IllegalArgumentException("start must not be null");
        }
        if (end == null) {
            throw new IllegalArgumentException("end must not be null");
        }
        if (start.getClass() != end.getClass()) {
            throw new IllegalArgumentException("start and end must be of the same type, but got "
                + start.getClass().getName() + " and " + end.getClass().getName());
        }
        @SuppressWarnings({"unchecked", "rawtypes"})
        int cmp = ((java.lang.Comparable)start).compareTo(end);
        if (cmp > 0) {
            throw new IllegalArgumentException("start must not be greater than end");
        }
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
     * 添加子查询实体的忽略大小写的 LIKE 条件。
     *
     * @param field 实体字段
     * @param value 匹配模式（可使用 % 通配符）
     * @return 当前 SubQuerySpec 实例，支持链式调用
     * @throws IllegalArgumentException 如果 value 为 null
     */
    public SubQuerySpec<S> likeIgnoreCase(SFunction<S, ?> field, String value) {
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
        predicates.add(PredicateHelper.likeIgnoreCase(root, property(field), value, cb));
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
     * 使用子查询根和 CriteriaBuilder 添加原始谓词。作为复杂条件或关联谓词的扩展机制。 要引用外部查询根，请使用 {@link #correlated()}。
     *
     * <pre>{@code
     * qs.exists(Child.class, sub -> sub
     *     .where(r -> cb.and(cb.equal(r.get("parent"), sub.correlated()), cb.greaterThan(r.get("amount"), 0))));
     * }</pre>
     *
     * <p>
     * <strong>安全警告：</strong>此方法允许直接操作 Root 对象，存在潜在的安全风险：
     * <ul>
     * <li>请勿使用用户输入的字符串拼接字段名，如 {@code root.get(userInput)}，这可能导致 SQL 注入</li>
     * <li>建议优先使用类型安全的方法引用 API（如 {@code eq(Entity::getField, value)}）</li>
     * <li>如果必须使用字符串字面量，请确保是硬编码的常量，而非运行时拼接</li>
     * </ul>
     *
     * @param condition 谓词函数，接收子查询根返回谓词
     * @return 当前 SubQuerySpec 实例，支持链式调用
     */
    public SubQuerySpec<S> where(java.util.function.Function<Root<S>, Predicate> condition) {
        if (condition == null) {
            throw new IllegalArgumentException("condition must not be null");
        }
        predicates.add(condition.apply(root));
        return this;
    }

    /**
     * 设置此子查询的 SELECT 子句。如果未调用，子查询默认选择子查询根。
     *
     * @param field 要选择的实体字段
     * @return 当前 SubQuerySpec 实例，支持链式调用
     */
    public SubQuerySpec<S> select(SFunction<S, ?> field) {
        if (field == null) {
            throw new IllegalArgumentException("field must not be null");
        }
        subquery.select(root.get(LambdaUtils.getPropertyName(field)));
        selectSet = true;
        return this;
    }
}
