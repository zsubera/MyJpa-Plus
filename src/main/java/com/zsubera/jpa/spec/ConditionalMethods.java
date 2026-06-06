package com.zsubera.jpa.spec;

import java.util.Collection;
import org.springframework.lang.Nullable;

/**
 * 条件便捷方法的共享接口，提供 {@code boolean condition} 参数的条件方法。
 *
 * <p>
 * 所有条件方法都是 {@code default} 方法，当 {@code condition} 为 true 时委托给对应的基础条件方法， 否则返回 {@link #self()}。此接口统一了
 * {@link ConditionBuilder} 和 {@link com.zsubera.jpa.update.AbstractBulkOperationSpec} 中重复的条件便捷方法。
 *
 * @param <E> 条件操作的实体类型
 * @param <SELF> 用于流式链式调用的具体构建器类型
 * @see ConditionBuilder
 * @see com.zsubera.jpa.update.AbstractBulkOperationSpec
 */
public interface ConditionalMethods<E, SELF extends ConditionalMethods<E, SELF>> {

    /**
     * 返回当前构建器实例，用于链式调用。
     *
     * @return 当前构建器实例
     */
    SELF self();

    // ---- 基础条件方法声明（由实现类提供具体实现） ----

    /**
     * 添加等值条件。
     *
     * @param field 实体属性的方法引用
     * @param value 要比较的值
     * @return 当前构建器实例
     */
    SELF eq(SFunction<E, ?> field, @Nullable Object value);

    /**
     * 添加不等条件。
     *
     * @param field 实体属性的方法引用
     * @param value 要比较的值
     * @return 当前构建器实例
     */
    SELF ne(SFunction<E, ?> field, @Nullable Object value);

    /**
     * 添加大于条件。
     *
     * @param field 实体属性的方法引用
     * @param value 要比较的值
     * @return 当前构建器实例
     */
    SELF gt(SFunction<E, ?> field, Comparable<?> value);

    /**
     * 添加大于等于条件。
     *
     * @param field 实体属性的方法引用
     * @param value 要比较的值
     * @return 当前构建器实例
     */
    SELF ge(SFunction<E, ?> field, Comparable<?> value);

    /**
     * 添加小于条件。
     *
     * @param field 实体属性的方法引用
     * @param value 要比较的值
     * @return 当前构建器实例
     */
    SELF lt(SFunction<E, ?> field, Comparable<?> value);

    /**
     * 添加小于等于条件。
     *
     * @param field 实体属性的方法引用
     * @param value 要比较的值
     * @return 当前构建器实例
     */
    SELF le(SFunction<E, ?> field, Comparable<?> value);

    /**
     * 添加包含匹配条件：{@code field LIKE '%value%'}。
     *
     * <p>
     * 值中的 {@code %} 或 {@code _} 通配符会被自动转义，作为字面量处理。匹配模式自动包装为 {@code %value%}。
     *
     * @param field 实体属性的方法引用
     * @param value 要匹配的字符串值
     * @return 当前构建器实例
     */
    SELF like(SFunction<E, ?> field, String value);

    /**
     * 添加 NOT LIKE 包含匹配条件：{@code field NOT LIKE '%value%'}。
     *
     * <p>
     * 值中的 {@code %} 或 {@code _} 通配符会被自动转义，作为字面量处理。匹配模式自动包装为 {@code %value%}。
     *
     * @param field 实体属性的方法引用
     * @param value 要匹配的字符串值
     * @return 当前构建器实例
     */
    SELF notLike(SFunction<E, ?> field, String value);

    /**
     * 添加前缀匹配条件。
     *
     * @param field 实体属性的方法引用
     * @param value 前缀字符串值
     * @return 当前构建器实例
     */
    SELF startsWith(SFunction<E, ?> field, String value);

    /**
     * 添加后缀匹配条件。
     *
     * @param field 实体属性的方法引用
     * @param value 后缀字符串值
     * @return 当前构建器实例
     */
    SELF endsWith(SFunction<E, ?> field, String value);

    /**
     * 添加 IN 条件。
     *
     * @param field 实体属性的方法引用
     * @param values 值集合
     * @return 当前构建器实例
     */
    SELF in(SFunction<E, ?> field, Object... values);

    /**
     * 添加使用 Collection 的 IN 条件。
     *
     * @param field 实体属性的方法引用
     * @param values 值的集合
     * @return 当前构建器实例
     */
    SELF in(SFunction<E, ?> field, Collection<?> values);

    /**
     * 添加 NOT IN 条件。
     *
     * @param field 实体属性的方法引用
     * @param values 值集合
     * @return 当前构建器实例
     */
    SELF notIn(SFunction<E, ?> field, Object... values);

    /**
     * 添加使用 Collection 的 NOT IN 条件。
     *
     * @param field 实体属性的方法引用
     * @param values 值的集合
     * @return 当前构建器实例
     */
    SELF notIn(SFunction<E, ?> field, Collection<?> values);

    /**
     * 添加 BETWEEN 条件。
     *
     * @param field 实体属性的方法引用
     * @param start 下界（包含）
     * @param end 上界（包含）
     * @return 当前构建器实例
     */
    SELF between(SFunction<E, ?> field, Comparable<?> start, Comparable<?> end);

    /**
     * 添加 NOT BETWEEN 条件。
     *
     * @param field 实体属性的方法引用
     * @param start 下界（包含）
     * @param end 上界（包含）
     * @return 当前构建器实例
     */
    SELF notBetween(SFunction<E, ?> field, Comparable<?> start, Comparable<?> end);

    /**
     * 添加忽略大小写的等值条件。
     *
     * @param field 实体属性的方法引用
     * @param value 要比较的字符串值
     * @return 当前构建器实例
     */
    SELF eqIgnoreCase(SFunction<E, ?> field, @Nullable String value);

    /**
     * 添加忽略大小写的不等条件。
     *
     * @param field 实体属性的方法引用
     * @param value 要比较的字符串值
     * @return 当前构建器实例
     */
    SELF neIgnoreCase(SFunction<E, ?> field, @Nullable String value);

    /**
     * 添加忽略大小写的 LIKE 包含匹配条件。
     *
     * @param field 实体属性的方法引用
     * @param value 要匹配的字符串值
     * @return 当前构建器实例
     */
    SELF likeIgnoreCase(SFunction<E, ?> field, String value);

    /**
     * 添加 IS NULL 条件。
     *
     * @param field 实体属性的方法引用
     * @return 当前构建器实例
     */
    SELF isNull(SFunction<E, ?> field);

    /**
     * 添加 IS NOT NULL 条件。
     *
     * @param field 实体属性的方法引用
     * @return 当前构建器实例
     */
    SELF isNotNull(SFunction<E, ?> field);

    /**
     * 添加 IS EMPTY 条件。
     *
     * @param field 实体属性的方法引用
     * @return 当前构建器实例
     */
    SELF isEmpty(SFunction<E, ?> field);

    /**
     * 添加 IS NOT EMPTY 条件。
     *
     * @param field 实体属性的方法引用
     * @return 当前构建器实例
     */
    SELF isNotEmpty(SFunction<E, ?> field);

    // ---- 条件便捷方法（default 实现，统一委托给基础方法） ----

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
     * 仅在 {@code condition} 为 true 时添加包含匹配条件。
     *
     * @param condition 是否添加条件的标志
     * @param field 实体属性的方法引用
     * @param value 要匹配的字符串值
     * @return 当前构建器以支持链式调用
     */
    default SELF like(boolean condition, SFunction<E, ?> field, String value) {
        return condition ? like(field, value) : self();
    }

    /**
     * 仅在 {@code condition} 为 true 时添加 NOT LIKE 包含匹配条件。
     *
     * @param condition 是否添加条件的标志
     * @param field 实体属性的方法引用
     * @param value 要匹配的字符串值
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
     * 仅在 {@code condition} 为 true 时添加 NOT BETWEEN 条件。
     *
     * @param condition 是否添加条件的标志
     * @param field 实体属性的方法引用
     * @param start 下界（包含）
     * @param end 上界（包含）
     * @return 当前构建器以支持链式调用
     */
    default SELF notBetween(boolean condition, SFunction<E, ?> field, Comparable<?> start, Comparable<?> end) {
        return condition ? notBetween(field, start, end) : self();
    }

    /**
     * 仅在 {@code condition} 为 true 时添加忽略大小写的等值条件。
     *
     * @param condition 是否添加条件的标志
     * @param field 实体属性的方法引用
     * @param value 要比较的字符串值
     * @return 当前构建器以支持链式调用
     */
    default SELF eqIgnoreCase(boolean condition, SFunction<E, ?> field, @Nullable String value) {
        return condition ? eqIgnoreCase(field, value) : self();
    }

    /**
     * 仅在 {@code condition} 为 true 时添加忽略大小写的不等条件。
     *
     * @param condition 是否添加条件的标志
     * @param field 实体属性的方法引用
     * @param value 要比较的字符串值
     * @return 当前构建器以支持链式调用
     */
    default SELF neIgnoreCase(boolean condition, SFunction<E, ?> field, @Nullable String value) {
        return condition ? neIgnoreCase(field, value) : self();
    }

    /**
     * 仅在 {@code condition} 为 true 时添加忽略大小写的 LIKE 包含匹配条件。
     *
     * @param condition 是否添加条件的标志
     * @param field 实体属性的方法引用
     * @param value 要匹配的字符串值
     * @return 当前构建器以支持链式调用
     */
    default SELF likeIgnoreCase(boolean condition, SFunction<E, ?> field, String value) {
        return condition ? likeIgnoreCase(field, value) : self();
    }

    /**
     * 仅在 {@code condition} 为 true 时添加 IS NULL 条件。
     *
     * @param condition 是否添加条件的标志
     * @param field 实体属性的方法引用
     * @return 当前构建器以支持链式调用
     */
    default SELF isNull(boolean condition, SFunction<E, ?> field) {
        return condition ? isNull(field) : self();
    }

    /**
     * 仅在 {@code condition} 为 true 时添加 IS NOT NULL 条件。
     *
     * @param condition 是否添加条件的标志
     * @param field 实体属性的方法引用
     * @return 当前构建器以支持链式调用
     */
    default SELF isNotNull(boolean condition, SFunction<E, ?> field) {
        return condition ? isNotNull(field) : self();
    }

    /**
     * 仅在 {@code condition} 为 true 时添加 IS EMPTY 条件。
     *
     * @param condition 是否添加条件的标志
     * @param field 实体属性的方法引用
     * @return 当前构建器以支持链式调用
     */
    default SELF isEmpty(boolean condition, SFunction<E, ?> field) {
        return condition ? isEmpty(field) : self();
    }

    /**
     * 仅在 {@code condition} 为 true 时添加 IS NOT EMPTY 条件。
     *
     * @param condition 是否添加条件的标志
     * @param field 实体属性的方法引用
     * @return 当前构建器以支持链式调用
     */
    default SELF isNotEmpty(boolean condition, SFunction<E, ?> field) {
        return condition ? isNotEmpty(field) : self();
    }
}
