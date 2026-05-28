package com.zsubera.jpa.spec;

import static com.zsubera.jpa.spec.PredicateHelper.escapeLikeWildcards;

import com.zsubera.jpa.util.LambdaUtils;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.Collection;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.springframework.lang.Nullable;

/**
 * 构建类型安全 JPA 查询条件的通用接口，使用 lambda 方法引用。
 *
 * <p>
 * 实现类通过 {@link #conditions()} 提供目标条件列表。所有条件方法都是 {@code default} 方法， 创建 {@link ConditionNode} 条目并追加到该列表中。
 *
 * <p>
 * 自类型参数 {@code SELF} 支持流式链式调用，使每个方法返回具体的构建器类型而非接口类型。
 *
 * <p>
 * 实现类：{@link QuerySpec}、{@link JoinGroup}、{@link OrGroup}、{@link OrJoinGroup}。
 *
 * @param <E> 条件操作的实体类型
 * @param <SELF> 用于流式链式调用的具体构建器类型
 */
public interface ConditionBuilder<E, SELF extends ConditionBuilder<E, SELF>> {

    /**
     * 获取当前条件列表。
     *
     * @return 条件节点列表
     */
    List<ConditionNode> conditions();

    /**
     * 将 {@code this} 转换为具体的构建器类型以支持方法链式调用。
     *
     * @return {@code this} 转换为 {@code SELF} 类型
     */
    @SuppressWarnings("unchecked")
    default SELF self() {
        return (SELF)this;
    }

    // ---- 比较运算符 ----

    /**
     * 添加等值条件：{@code field = value}。如果 {@code value} 为 null，则生成 {@code field IS NULL}。
     *
     * @param field 实体属性的方法引用
     * @param value 要比较的值
     * @return 当前构建器以支持链式调用
     * @throws IllegalArgumentException 如果 {@code field} 为 null
     */
    default SELF eq(SFunction<E, ?> field, @Nullable Object value) {
        if (field == null) {
            throw new IllegalArgumentException("field must not be null");
        }
        if (value == null) {
            conditions()
                .add(new ConditionNode.SimpleNode(LambdaUtils.getPropertyName(field), null, ConditionNode.Op.IS_NULL));
        } else {
            conditions()
                .add(new ConditionNode.SimpleNode(LambdaUtils.getPropertyName(field), value, ConditionNode.Op.EQ));
        }
        return self();
    }

    /**
     * 添加不等条件：{@code field != value}。如果 {@code value} 为 null，则生成 {@code field IS NOT NULL}。
     *
     * @param field 实体属性的方法引用
     * @param value 要比较的值
     * @return 当前构建器以支持链式调用
     * @throws IllegalArgumentException 如果 {@code field} 为 null
     */
    default SELF ne(SFunction<E, ?> field, @Nullable Object value) {
        if (field == null) {
            throw new IllegalArgumentException("field must not be null");
        }
        if (value == null) {
            conditions().add(
                new ConditionNode.SimpleNode(LambdaUtils.getPropertyName(field), null, ConditionNode.Op.IS_NOT_NULL));
        } else {
            conditions()
                .add(new ConditionNode.SimpleNode(LambdaUtils.getPropertyName(field), value, ConditionNode.Op.NE));
        }
        return self();
    }

    /**
     * 添加严格等值条件：{@code field = value}。如果 {@code value} 为 null，则抛出异常。
     *
     * <p>
     * 此方法提供明确的 null 处理选择，避免 {@link #eq(SFunction, Object)} 自动转换为 IS NULL 的行为。 如果您希望比较 null 值，请使用
     * {@link #isNull(SFunction)} 方法。
     *
     * @param field 实体属性的方法引用
     * @param value 要比较的值
     * @return 当前构建器以支持链式调用
     * @throws IllegalArgumentException 如果 {@code field} 或 {@code value} 为 null
     * @see #eq(SFunction, Object)
     * @see #isNull(SFunction)
     */
    default SELF eqStrict(SFunction<E, ?> field, Object value) {
        if (value == null) {
            throw new IllegalArgumentException("value must not be null. Use isNull() for null comparisons.");
        }
        return eq(field, value);
    }

    /**
     * 添加严格不等条件：{@code field != value}。如果 {@code value} 为 null，则抛出异常。
     *
     * <p>
     * 此方法提供明确的 null 处理选择，避免 {@link #ne(SFunction, Object)} 自动转换为 IS NOT NULL 的行为。 如果您希望比较 null 值，请使用
     * {@link #isNotNull(SFunction)} 方法。
     *
     * @param field 实体属性的方法引用
     * @param value 要比较的值
     * @return 当前构建器以支持链式调用
     * @throws IllegalArgumentException 如果 {@code field} 或 {@code value} 为 null
     * @see #ne(SFunction, Object)
     * @see #isNotNull(SFunction)
     */
    default SELF neStrict(SFunction<E, ?> field, Object value) {
        if (value == null) {
            throw new IllegalArgumentException("value must not be null. Use isNotNull() for null comparisons.");
        }
        return ne(field, value);
    }

    /**
     * 添加大于条件：{@code field > value}。
     *
     * @param field 实体属性的方法引用
     * @param value 要比较的值
     * @return 当前构建器以支持链式调用
     * @throws IllegalArgumentException 如果 {@code field} 或 {@code value} 为 null
     */
    default SELF gt(SFunction<E, ?> field, Comparable<?> value) {
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
        conditions().add(new ConditionNode.SimpleNode(LambdaUtils.getPropertyName(field), value, ConditionNode.Op.GT));
        return self();
    }

    /**
     * 添加大于等于条件：{@code field >= value}。
     *
     * @param field 实体属性的方法引用
     * @param value 要比较的值
     * @return 当前构建器以支持链式调用
     * @throws IllegalArgumentException 如果 {@code field} 或 {@code value} 为 null
     */
    default SELF ge(SFunction<E, ?> field, Comparable<?> value) {
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
        conditions().add(new ConditionNode.SimpleNode(LambdaUtils.getPropertyName(field), value, ConditionNode.Op.GE));
        return self();
    }

    /**
     * 添加小于条件：{@code field < value}。
     *
     * @param field 实体属性的方法引用
     * @param value 要比较的值
     * @return 当前构建器以支持链式调用
     * @throws IllegalArgumentException 如果 {@code field} 或 {@code value} 为 null
     */
    default SELF lt(SFunction<E, ?> field, Comparable<?> value) {
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
        conditions().add(new ConditionNode.SimpleNode(LambdaUtils.getPropertyName(field), value, ConditionNode.Op.LT));
        return self();
    }

    /**
     * 添加小于等于条件：{@code field <= value}。
     *
     * @param field 实体属性的方法引用
     * @param value 要比较的值
     * @return 当前构建器以支持链式调用
     * @throws IllegalArgumentException 如果 {@code field} 或 {@code value} 为 null
     */
    default SELF le(SFunction<E, ?> field, Comparable<?> value) {
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
        conditions().add(new ConditionNode.SimpleNode(LambdaUtils.getPropertyName(field), value, ConditionNode.Op.LE));
        return self();
    }

    // ---- 字符串运算符 ----

    /**
     * 添加 LIKE 条件：{@code field LIKE value}。调用者需要自行包含通配符（例如 {@code "%keyword%"}）。
     *
     * @param field 实体属性的方法引用
     * @param value 匹配模式的字符串值
     * @return 当前构建器以支持链式调用
     * @throws IllegalArgumentException 如果 {@code field} 或 {@code value} 为 null
     */
    default SELF like(SFunction<E, ?> field, String value) {
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
        conditions()
            .add(new ConditionNode.SimpleNode(LambdaUtils.getPropertyName(field), value, ConditionNode.Op.LIKE));
        return self();
    }

    /**
     * 添加带自动通配符转义的 LIKE 条件：{@code field LIKE '%value%'}。 值中的 {@code %} 或 {@code _} 字符会被转义，作为字面量处理。
     *
     * @param field 实体属性的方法引用
     * @param value 要匹配的原始字符串值
     * @return 当前构建器以支持链式调用
     * @throws IllegalArgumentException 如果 {@code field} 或 {@code value} 为 null
     */
    default SELF rawLike(SFunction<E, ?> field, String value) {
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
        conditions().add(new ConditionNode.SimpleNode(LambdaUtils.getPropertyName(field),
            "%" + escapeLikeWildcards(value) + "%", ConditionNode.Op.LIKE, PredicateHelper.LIKE_ESCAPE_CHAR));
        return self();
    }

    /**
     * 添加 NOT LIKE 条件：{@code field NOT LIKE value}。
     *
     * @param field 实体属性的方法引用
     * @param value 匹配模式的字符串值
     * @return 当前构建器以支持链式调用
     * @throws IllegalArgumentException 如果 {@code field} 或 {@code value} 为 null
     */
    default SELF notLike(SFunction<E, ?> field, String value) {
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
        conditions()
            .add(new ConditionNode.SimpleNode(LambdaUtils.getPropertyName(field), value, ConditionNode.Op.NOT_LIKE));
        return self();
    }

    /**
     * 添加前缀匹配 LIKE 条件：{@code field LIKE 'value%'}。
     *
     * @param field 实体属性的方法引用
     * @param value 前缀字符串值
     * @return 当前构建器以支持链式调用
     * @throws IllegalArgumentException 如果 {@code field} 或 {@code value} 为 null
     */
    default SELF startsWith(SFunction<E, ?> field, String value) {
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
        conditions().add(new ConditionNode.SimpleNode(LambdaUtils.getPropertyName(field),
            escapeLikeWildcards(value) + "%", ConditionNode.Op.LIKE, PredicateHelper.LIKE_ESCAPE_CHAR));
        return self();
    }

    /**
     * 添加后缀匹配 LIKE 条件：{@code field LIKE '%value'}。
     *
     * @param field 实体属性的方法引用
     * @param value 后缀字符串值
     * @return 当前构建器以支持链式调用
     * @throws IllegalArgumentException 如果 {@code field} 或 {@code value} 为 null
     */
    default SELF endsWith(SFunction<E, ?> field, String value) {
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
        conditions().add(new ConditionNode.SimpleNode(LambdaUtils.getPropertyName(field),
            "%" + escapeLikeWildcards(value), ConditionNode.Op.LIKE, PredicateHelper.LIKE_ESCAPE_CHAR));
        return self();
    }

    /**
     * 添加包含匹配 LIKE 条件：{@code field LIKE '%value%'}。
     *
     * @param field 实体属性的方法引用
     * @param value 要包含的子字符串值
     * @return 当前构建器以支持链式调用
     * @throws IllegalArgumentException 如果 {@code field} 或 {@code value} 为 null
     */
    default SELF contains(SFunction<E, ?> field, String value) {
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
        conditions().add(new ConditionNode.SimpleNode(LambdaUtils.getPropertyName(field),
            "%" + escapeLikeWildcards(value) + "%", ConditionNode.Op.LIKE, PredicateHelper.LIKE_ESCAPE_CHAR));
        return self();
    }

    // ---- 集合运算符 ----

    /**
     * 添加 IN 条件：{@code field IN (values)}。
     *
     * @param field 实体属性的方法引用
     * @param values 值集合
     * @return 当前构建器以支持链式调用
     * @throws IllegalArgumentException 如果 {@code field} 为 null 或 {@code values} 为空
     */
    default SELF in(SFunction<E, ?> field, Object... values) {
        if (field == null) {
            throw new IllegalArgumentException("field must not be null");
        }
        if (values == null || values.length == 0) {
            throw new IllegalArgumentException("values must not be empty");
        }
        conditions().add(new ConditionNode.SimpleNode(LambdaUtils.getPropertyName(field), values, ConditionNode.Op.IN));
        return self();
    }

    /**
     * 添加 NOT IN 条件：{@code field NOT IN (values)}。
     *
     * @param field 实体属性的方法引用
     * @param values 值集合
     * @return 当前构建器以支持链式调用
     * @throws IllegalArgumentException 如果 {@code field} 为 null 或 {@code values} 为空
     */
    default SELF notIn(SFunction<E, ?> field, Object... values) {
        if (field == null) {
            throw new IllegalArgumentException("field must not be null");
        }
        if (values == null || values.length == 0) {
            throw new IllegalArgumentException("values must not be empty");
        }
        conditions()
            .add(new ConditionNode.SimpleNode(LambdaUtils.getPropertyName(field), values, ConditionNode.Op.NOT_IN));
        return self();
    }

    /**
     * 添加使用 {@link Collection} 的 IN 条件：{@code field IN (values)}。
     *
     * @param field 实体属性的方法引用
     * @param values 值的集合
     * @return 当前构建器以支持链式调用
     * @throws IllegalArgumentException 如果 {@code field} 为 null 或 {@code values} 为空
     */
    default SELF in(SFunction<E, ?> field, Collection<?> values) {
        if (field == null) {
            throw new IllegalArgumentException("field must not be null");
        }
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException("values must not be empty");
        }
        conditions().add(new ConditionNode.SimpleNode(LambdaUtils.getPropertyName(field), values, ConditionNode.Op.IN));
        return self();
    }

    /**
     * 添加使用 {@link Collection} 的 NOT IN 条件：{@code field NOT IN (values)}。
     *
     * @param field 实体属性的方法引用
     * @param values 值的集合
     * @return 当前构建器以支持链式调用
     * @throws IllegalArgumentException 如果 {@code field} 为 null 或 {@code values} 为空
     */
    default SELF notIn(SFunction<E, ?> field, Collection<?> values) {
        if (field == null) {
            throw new IllegalArgumentException("field must not be null");
        }
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException("values must not be empty");
        }
        conditions()
            .add(new ConditionNode.SimpleNode(LambdaUtils.getPropertyName(field), values, ConditionNode.Op.NOT_IN));
        return self();
    }

    /**
     * 添加 BETWEEN 条件：{@code field BETWEEN start AND end}。
     *
     * @param field 实体属性的方法引用
     * @param start 下界（包含）
     * @param end 上界（包含）
     * @return 当前构建器以支持链式调用
     * @throws IllegalArgumentException 如果 {@code field}、{@code start} 或 {@code end} 为 null， 或者 start 大于 end
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    default SELF between(SFunction<E, ?> field, Comparable<?> start, Comparable<?> end) {
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
        if (((Comparable)start).compareTo(end) > 0) {
            throw new IllegalArgumentException("start must not be greater than end");
        }
        conditions().add(new ConditionNode.SimpleNode(LambdaUtils.getPropertyName(field),
            new Comparable<?>[] {start, end}, ConditionNode.Op.BETWEEN));
        return self();
    }

    /**
     * 添加 NOT BETWEEN 条件：{@code field NOT BETWEEN start AND end}。
     *
     * @param field 实体属性的方法引用
     * @param start 下界（包含）
     * @param end 上界（包含）
     * @return 当前构建器以支持链式调用
     * @throws IllegalArgumentException 如果 {@code field}、{@code start} 或 {@code end} 为 null， 或者 start 大于 end
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    default SELF notBetween(SFunction<E, ?> field, Comparable<?> start, Comparable<?> end) {
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
        if (((Comparable)start).compareTo(end) > 0) {
            throw new IllegalArgumentException("start must not be greater than end");
        }
        conditions().add(new ConditionNode.SimpleNode(LambdaUtils.getPropertyName(field),
            new Comparable<?>[] {start, end}, ConditionNode.Op.NOT_BETWEEN));
        return self();
    }

    // ---- 空值运算符 ----

    /**
     * 添加 IS NULL 条件：{@code field IS NULL}。
     *
     * @param field 实体属性的方法引用
     * @return 当前构建器以支持链式调用
     * @throws IllegalArgumentException 如果 {@code field} 为 null
     */
    default SELF isNull(SFunction<E, ?> field) {
        conditions()
            .add(new ConditionNode.SimpleNode(LambdaUtils.getPropertyName(field), null, ConditionNode.Op.IS_NULL));
        return self();
    }

    /**
     * 添加 IS NOT NULL 条件：{@code field IS NOT NULL}。
     *
     * @param field 实体属性的方法引用
     * @return 当前构建器以支持链式调用
     * @throws IllegalArgumentException 如果 {@code field} 为 null
     */
    default SELF isNotNull(SFunction<E, ?> field) {
        conditions()
            .add(new ConditionNode.SimpleNode(LambdaUtils.getPropertyName(field), null, ConditionNode.Op.IS_NOT_NULL));
        return self();
    }

    /**
     * 添加不区分大小写的等值条件：{@code UPPER(field) = UPPER(value)}。 适用于不区分大小写的用户名/邮箱查找。
     *
     * @param field 实体属性的方法引用
     * @param value 要比较的字符串值，如果为 null 则生成 IS NULL 条件
     * @return 当前构建器以支持链式调用
     * @throws IllegalArgumentException 如果 {@code field} 为 null
     */
    default SELF eqIgnoreCase(SFunction<E, ?> field, @Nullable String value) {
        if (field == null) {
            throw new IllegalArgumentException("field must not be null");
        }
        if (value == null) {
            return isNull(field);
        }
        conditions().add(
            new ConditionNode.SimpleNode(LambdaUtils.getPropertyName(field), value, ConditionNode.Op.EQ_IGNORE_CASE));
        return self();
    }

    /**
     * 添加不区分大小写的 LIKE 条件：{@code UPPER(field) LIKE UPPER('%value%')}。
     *
     * @param field 实体属性的方法引用
     * @param value 匹配模式的字符串值
     * @return 当前构建器以支持链式调用
     * @throws IllegalArgumentException 如果 {@code field} 或 {@code value} 为 null
     */
    default SELF likeIgnoreCase(SFunction<E, ?> field, String value) {
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
        conditions().add(
            new ConditionNode.SimpleNode(LambdaUtils.getPropertyName(field), value, ConditionNode.Op.LIKE_IGNORE_CASE));
        return self();
    }

    // ---- 集合空值检查 ----

    /**
     * 添加 IS EMPTY 条件，用于一对多关联。适用于 {@code @OneToMany} 或 {@code @ManyToMany} 字段。
     *
     * @param field 实体属性的方法引用
     * @return 当前构建器以支持链式调用
     * @throws IllegalArgumentException 如果 {@code field} 为 null
     */
    default SELF isEmpty(SFunction<E, ?> field) {
        if (field == null) {
            throw new IllegalArgumentException("field must not be null");
        }
        conditions().add(
            new ConditionNode.CollectionNode(LambdaUtils.getPropertyName(field), ConditionNode.CollectionOp.IS_EMPTY));
        return self();
    }

    /**
     * 添加 IS NOT EMPTY 条件，用于一对多关联。
     *
     * @param field 实体属性的方法引用
     * @return 当前构建器以支持链式调用
     * @throws IllegalArgumentException 如果 {@code field} 为 null
     */
    default SELF isNotEmpty(SFunction<E, ?> field) {
        if (field == null) {
            throw new IllegalArgumentException("field must not be null");
        }
        conditions().add(new ConditionNode.CollectionNode(LambdaUtils.getPropertyName(field),
            ConditionNode.CollectionOp.IS_NOT_EMPTY));
        return self();
    }

    /**
     * 添加原始 {@link Predicate} 条件，使用当前实体 {@link Path} 和 {@link CriteriaBuilder}。 这是处理构建器 API 未覆盖条件的扩展方法。
     *
     * <p>
     * <strong>注意：</strong>由于 Java 泛型类型推断限制，使用 lambda 时编译器可能无法正确推断 {@code Path<E>} 类型， 导致无法调用 {@code path.get()}
     * 等方法。此时需要显式声明参数类型：
     *
     * <pre>{@code
     * // 编译失败：path 被推断为 Object
     * qs.where((path, cb) -> cb.like(path.get("name"), "%test%"));
     *
     * // 正确：显式声明 Path<?> 类型
     * qs.where((Path<?> path, CriteriaBuilder cb) -> cb.like(path.get("name"), "%test%"));
     * }</pre>
     *
     * <p>
     * <strong>安全警告：</strong>此方法允许直接操作 Path 对象，存在潜在的安全风险：
     * <ul>
     * <li>请勿使用用户输入的字符串拼接字段名，如 {@code path.get(userInput)}，这可能导致 SQL 注入</li>
     * <li>建议优先使用类型安全的方法引用 API（如 {@code eq(Entity::getField, value)}）</li>
     * <li>如果必须使用字符串字面量，请确保是硬编码的常量，而非运行时拼接</li>
     * </ul>
     *
     * <pre>{@code
     * // 危险：用户输入直接拼接到字段名
     * String userInput = request.getParameter("field");
     * qs.where((path, cb) -> cb.equal(path.get(userInput), value)); // SQL 注入风险！
     *
     * // 安全：使用硬编码字段名
     * qs.where((path, cb) -> cb.equal(path.get("name"), value));
     *
     * // 更好：使用类型安全的方法引用
     * qs.eq(Entity::getName, value);
     * }</pre>
     *
     * @param fn 接收实体路径和条件构建器的函数，返回谓词
     * @return 当前构建器以支持链式调用
     * @throws IllegalArgumentException 如果 {@code fn} 为 null
     * @see #eq(SFunction, Object)
     * @see #ne(SFunction, Object)
     */
    @SuppressWarnings("unchecked")
    default SELF where(BiFunction<Path<E>, CriteriaBuilder, Predicate> fn) {
        if (fn == null) {
            throw new IllegalArgumentException("fn must not be null");
        }
        conditions().add(new ConditionNode.RawNode((BiFunction<Path<?>, CriteriaBuilder, Predicate>)(Object)fn));
        return self();
    }

    /**
     * 添加原始 {@link Predicate} 条件，使用 {@link Root} 参数。此重载避免了 {@link #where(BiFunction)} 的类型推断问题。
     *
     * <p>
     * 推荐使用此方法代替 {@link #where(BiFunction)}，因为 {@code Root<T>} 的类型推断更可靠：
     *
     * <pre>{@code
     * // 使用 where(Function) - 类型推断正常
     * qs.where(root -> cb.like(root.get("name"), "%test%"));
     *
     * // 注意：需要在外部获取 CriteriaBuilder
     * CriteriaBuilder cb = entityManager.getCriteriaBuilder();
     * qs.where(root -> cb.like(root.get("name"), "%test%"));
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
     * @param fn 接收 Root 的函数，返回谓词
     * @return 当前构建器以支持链式调用
     * @throws IllegalArgumentException 如果 {@code fn} 为 null
     * @see #eq(SFunction, Object)
     * @see #ne(SFunction, Object)
     */
    @SuppressWarnings("unchecked")
    default SELF where(Function<Root<E>, Predicate> fn) {
        if (fn == null) {
            throw new IllegalArgumentException("fn must not be null");
        }
        conditions().add(new ConditionNode.RawNode((root, cb) -> fn.apply((Root<E>)root)));
        return self();
    }

    // ---- 多字段搜索 ----

    /**
     * 添加多字段 LIKE 搜索。关键字被包装为 {@code %keyword%} 并与每个给定字段匹配，使用 OR 连接。
     *
     * @param keyword 搜索关键字
     * @param fields 一个或多个字符串属性的方法引用
     * @return 当前构建器以支持链式调用
     * @throws IllegalArgumentException 如果 {@code fields} 为 null 或包含 null 元素
     */
    @SuppressWarnings("unchecked")
    default SELF multiLike(String keyword, SFunction<E, ?>... fields) {
        if (fields == null) {
            throw new IllegalArgumentException("fields must not be null");
        }
        if (keyword != null && !keyword.isEmpty() && fields.length > 0) {
            String[] fieldNames = new String[fields.length];
            for (int i = 0; i < fields.length; i++) {
                if (fields[i] == null) {
                    throw new IllegalArgumentException("fields[" + i + "] must not be null");
                }
                fieldNames[i] = LambdaUtils.getPropertyName(fields[i]);
            }
            conditions().add(new ConditionNode.MultiLikeNode(keyword, fieldNames));
        }
        return self();
    }

    // ---- 条件便捷方法 ----

    /**
     * 仅在 {@code condition} 为 true 时添加等值条件。
     *
     * @param condition 是否添加条件的标志
     * @param field 实体属性的方法引用
     * @param value 要比较的值
     * @return 当前构建器以支持链式调用
     */
    default SELF eq(boolean condition, SFunction<E, ?> field, @Nullable Object value) {
        return condition ? eq(field, value) : self();
    }

    /**
     * 仅在 {@code condition} 为 true 时添加不等条件。
     *
     * @param condition 是否添加条件的标志
     * @param field 实体属性的方法引用
     * @param value 要比较的值
     * @return 当前构建器以支持链式调用
     */
    default SELF ne(boolean condition, SFunction<E, ?> field, @Nullable Object value) {
        return condition ? ne(field, value) : self();
    }

    /**
     * 仅在 {@code condition} 为 true 时添加大于条件。
     *
     * @param condition 是否添加条件的标志
     * @param field 实体属性的方法引用
     * @param value 要比较的值
     * @return 当前构建器以支持链式调用
     */
    default SELF gt(boolean condition, SFunction<E, ?> field, Comparable<?> value) {
        return condition ? gt(field, value) : self();
    }

    /**
     * 仅在 {@code condition} 为 true 时添加大于等于条件。
     *
     * @param condition 是否添加条件的标志
     * @param field 实体属性的方法引用
     * @param value 要比较的值
     * @return 当前构建器以支持链式调用
     */
    default SELF ge(boolean condition, SFunction<E, ?> field, Comparable<?> value) {
        return condition ? ge(field, value) : self();
    }

    /**
     * 仅在 {@code condition} 为 true 时添加小于条件。
     *
     * @param condition 是否添加条件的标志
     * @param field 实体属性的方法引用
     * @param value 要比较的值
     * @return 当前构建器以支持链式调用
     */
    default SELF lt(boolean condition, SFunction<E, ?> field, Comparable<?> value) {
        return condition ? lt(field, value) : self();
    }

    /**
     * 仅在 {@code condition} 为 true 时添加小于等于条件。
     *
     * @param condition 是否添加条件的标志
     * @param field 实体属性的方法引用
     * @param value 要比较的值
     * @return 当前构建器以支持链式调用
     */
    default SELF le(boolean condition, SFunction<E, ?> field, Comparable<?> value) {
        return condition ? le(field, value) : self();
    }

    /**
     * 仅在 {@code condition} 为 true 时添加 LIKE 条件。
     *
     * @param condition 是否添加条件的标志
     * @param field 实体属性的方法引用
     * @param value 匹配模式的字符串值
     * @return 当前构建器以支持链式调用
     */
    default SELF like(boolean condition, SFunction<E, ?> field, String value) {
        return condition ? like(field, value) : self();
    }

    /**
     * 仅在 {@code condition} 为 true 时添加 NOT LIKE 条件。
     *
     * @param condition 是否添加条件的标志
     * @param field 实体属性的方法引用
     * @param value 匹配模式的字符串值
     * @return 当前构建器以支持链式调用
     */
    default SELF notLike(boolean condition, SFunction<E, ?> field, String value) {
        return condition ? notLike(field, value) : self();
    }

    /**
     * 仅在 {@code condition} 为 true 时添加前缀匹配条件。
     *
     * @param condition 是否添加条件的标志
     * @param field 实体属性的方法引用
     * @param value 前缀字符串值
     * @return 当前构建器以支持链式调用
     */
    default SELF startsWith(boolean condition, SFunction<E, ?> field, String value) {
        return condition ? startsWith(field, value) : self();
    }

    /**
     * 仅在 {@code condition} 为 true 时添加后缀匹配条件。
     *
     * @param condition 是否添加条件的标志
     * @param field 实体属性的方法引用
     * @param value 后缀字符串值
     * @return 当前构建器以支持链式调用
     */
    default SELF endsWith(boolean condition, SFunction<E, ?> field, String value) {
        return condition ? endsWith(field, value) : self();
    }

    /**
     * 仅在 {@code condition} 为 true 时添加包含匹配条件。
     *
     * @param condition 是否添加条件的标志
     * @param field 实体属性的方法引用
     * @param value 要包含的子字符串值
     * @return 当前构建器以支持链式调用
     */
    default SELF contains(boolean condition, SFunction<E, ?> field, String value) {
        return condition ? contains(field, value) : self();
    }

    /**
     * 仅在 {@code condition} 为 true 时添加 IN 条件。
     *
     * @param condition 是否添加条件的标志
     * @param field 实体属性的方法引用
     * @param values 值集合
     * @return 当前构建器以支持链式调用
     */
    default SELF in(boolean condition, SFunction<E, ?> field, Object... values) {
        return condition ? in(field, values) : self();
    }

    /**
     * 仅在 {@code condition} 为 true 时添加使用 Collection 的 IN 条件。
     *
     * @param condition 是否添加条件的标志
     * @param field 实体属性的方法引用
     * @param values 值的集合
     * @return 当前构建器以支持链式调用
     */
    default SELF in(boolean condition, SFunction<E, ?> field, Collection<?> values) {
        return condition ? in(field, values) : self();
    }

    /**
     * 仅在 {@code condition} 为 true 时添加 NOT IN 条件。
     *
     * @param condition 是否添加条件的标志
     * @param field 实体属性的方法引用
     * @param values 值集合
     * @return 当前构建器以支持链式调用
     */
    default SELF notIn(boolean condition, SFunction<E, ?> field, Object... values) {
        return condition ? notIn(field, values) : self();
    }

    /**
     * 仅在 {@code condition} 为 true 时添加使用 Collection 的 NOT IN 条件。
     *
     * @param condition 是否添加条件的标志
     * @param field 实体属性的方法引用
     * @param values 值的集合
     * @return 当前构建器以支持链式调用
     */
    default SELF notIn(boolean condition, SFunction<E, ?> field, Collection<?> values) {
        return condition ? notIn(field, values) : self();
    }

    /**
     * 仅在 {@code condition} 为 true 时添加 BETWEEN 条件。
     *
     * @param condition 是否添加条件的标志
     * @param field 实体属性的方法引用
     * @param start 下界（包含）
     * @param end 上界（包含）
     * @return 当前构建器以支持链式调用
     */
    default SELF between(boolean condition, SFunction<E, ?> field, Comparable<?> start, Comparable<?> end) {
        return condition ? between(field, start, end) : self();
    }

    /**
     * 仅在 {@code condition} 为 true 时添加多字段 LIKE 搜索。
     *
     * @param condition 是否添加条件的标志
     * @param keyword 搜索关键字
     * @param fields 一个或多个字符串属性的方法引用
     * @return 当前构建器以支持链式调用
     */
    default SELF multiLike(boolean condition, String keyword, SFunction<E, ?>... fields) {
        return condition ? multiLike(keyword, fields) : self();
    }
}
