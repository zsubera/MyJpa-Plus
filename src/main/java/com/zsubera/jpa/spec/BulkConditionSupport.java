package com.zsubera.jpa.spec;

import static com.zsubera.jpa.spec.ConditionalMethods.requireField;
import static com.zsubera.jpa.spec.ConditionalMethods.requireNonEmpty;
import static com.zsubera.jpa.spec.ConditionalMethods.requireValue;
import static com.zsubera.jpa.spec.ConditionalMethods.wrapLikePattern;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.Collection;
import java.util.function.BiFunction;
import org.springframework.lang.Nullable;

/**
 * 批量操作条件方法的共享委托接口，为 {@link com.zsubera.jpa.update.AbstractBulkOperationSpec} 和
 * {@link com.zsubera.jpa.update.OrConditionBuilder} 提供通用条件方法的 default 实现。
 *
 * <p>
 * 所有条件方法都是 {@code default} 方法，统一委托给 {@link #addCondition(BiFunction)}。
 * 实现类只需提供条件存储和字段名解析即可获得所有条件方法。
 *
 * <p>
 * 此接口消除了批量操作路径中两个类之间的条件方法重复。
 *
 * @param <T> 实体类型
 * @param <SELF> 用于流式链式调用的具体构建器类型
 */
public interface BulkConditionSupport<T, SELF extends BulkConditionSupport<T, SELF>>
    extends ConditionalMethods<T, SELF> {

    /**
     * 向底层存储中添加一个条件谓词。
     *
     * @param predicateFn 接受 Root 和 CriteriaBuilder 并返回 Predicate 的函数
     * @return 当前构建器实例
     */
    SELF addCondition(BiFunction<Root<?>, CriteriaBuilder, Predicate> predicateFn);

    /**
     * 将实体属性方法引用解析为字段名字符串。
     *
     * @param field 实体属性的方法引用
     * @return 字段名
     */
    String property(SFunction<T, ?> field);

    // ---- 比较运算符 ----

    default SELF eq(SFunction<T, ?> field, @Nullable Object value) {
        requireField(field);
        return addCondition((root, cb) -> PredicateHelper.eq(root, property(field), value, cb));
    }

    default SELF ne(SFunction<T, ?> field, @Nullable Object value) {
        requireField(field);
        return addCondition((root, cb) -> PredicateHelper.ne(root, property(field), value, cb));
    }

    default SELF gt(SFunction<T, ?> field, Comparable<?> value) {
        requireField(field);
        requireValue(value, "gt");
        return addCondition((root, cb) -> PredicateHelper.gt(root, property(field), value, cb));
    }

    default SELF ge(SFunction<T, ?> field, Comparable<?> value) {
        requireField(field);
        requireValue(value, "ge");
        return addCondition((root, cb) -> PredicateHelper.ge(root, property(field), value, cb));
    }

    default SELF lt(SFunction<T, ?> field, Comparable<?> value) {
        requireField(field);
        requireValue(value, "lt");
        return addCondition((root, cb) -> PredicateHelper.lt(root, property(field), value, cb));
    }

    default SELF le(SFunction<T, ?> field, Comparable<?> value) {
        requireField(field);
        requireValue(value, "le");
        return addCondition((root, cb) -> PredicateHelper.le(root, property(field), value, cb));
    }

    // ---- 字符串运算符 ----

    default SELF like(SFunction<T, ?> field, String value) {
        requireField(field);
        requireValue(value, "like");
        String pattern = wrapLikePattern(value);
        return addCondition(
            (root, cb) -> PredicateHelper.like(root, property(field), pattern, cb, PredicateHelper.LIKE_ESCAPE_CHAR));
    }

    default SELF notLike(SFunction<T, ?> field, String value) {
        requireField(field);
        requireValue(value, "notLike");
        String pattern = wrapLikePattern(value);
        return addCondition((root, cb) -> PredicateHelper.notLike(root, property(field), pattern, cb,
            PredicateHelper.LIKE_ESCAPE_CHAR));
    }

    default SELF startsWith(SFunction<T, ?> field, String value) {
        requireField(field);
        requireValue(value, "startsWith");
        return addCondition((root, cb) -> PredicateHelper.startsWith(root, property(field), value, cb));
    }

    default SELF endsWith(SFunction<T, ?> field, String value) {
        requireField(field);
        requireValue(value, "endsWith");
        return addCondition((root, cb) -> PredicateHelper.endsWith(root, property(field), value, cb));
    }

    default SELF notStartsWith(SFunction<T, ?> field, String value) {
        requireField(field);
        requireValue(value, "notStartsWith");
        return addCondition((root, cb) -> cb.not(PredicateHelper.startsWith(root, property(field), value, cb)));
    }

    default SELF notEndsWith(SFunction<T, ?> field, String value) {
        requireField(field);
        requireValue(value, "notEndsWith");
        return addCondition((root, cb) -> cb.not(PredicateHelper.endsWith(root, property(field), value, cb)));
    }

    // ---- 忽略大小写运算符 ----

    default SELF eqIgnoreCase(SFunction<T, ?> field, @Nullable String value) {
        requireField(field);
        if (value == null) {
            return isNull(field);
        }
        return addCondition((root, cb) -> PredicateHelper.eqIgnoreCase(root, property(field), value, cb));
    }

    default SELF neIgnoreCase(SFunction<T, ?> field, @Nullable String value) {
        requireField(field);
        if (value == null) {
            return isNotNull(field);
        }
        return addCondition((root, cb) -> PredicateHelper.neIgnoreCase(root, property(field), value, cb));
    }

    default SELF likeIgnoreCase(SFunction<T, ?> field, String value) {
        requireField(field);
        requireValue(value, "likeIgnoreCase");
        String pattern = wrapLikePattern(value);
        return addCondition((root, cb) -> PredicateHelper.likeIgnoreCase(root, property(field), pattern, cb,
            PredicateHelper.LIKE_ESCAPE_CHAR));
    }

    // ---- 集合运算符 ----

    default SELF in(SFunction<T, ?> field, Object... values) {
        requireField(field);
        requireNonEmpty(values);
        return addCondition((root, cb) -> PredicateHelper.in(root, property(field), values, cb));
    }

    default SELF notIn(SFunction<T, ?> field, Object... values) {
        requireField(field);
        requireNonEmpty(values);
        return addCondition((root, cb) -> PredicateHelper.notIn(root, property(field), values, cb));
    }

    default SELF in(SFunction<T, ?> field, Collection<?> values) {
        requireField(field);
        requireNonEmpty(values);
        return addCondition((root, cb) -> PredicateHelper.in(root, property(field), values, cb));
    }

    default SELF notIn(SFunction<T, ?> field, Collection<?> values) {
        requireField(field);
        requireNonEmpty(values);
        return addCondition((root, cb) -> PredicateHelper.notIn(root, property(field), values, cb));
    }

    // ---- 范围运算符 ----

    default SELF between(SFunction<T, ?> field, Comparable<?> start, Comparable<?> end) {
        requireField(field);
        PredicateHelper.validateRange(start, end);
        return addCondition((root, cb) -> PredicateHelper.between(root, property(field), start, end, cb));
    }

    default SELF notBetween(SFunction<T, ?> field, Comparable<?> start, Comparable<?> end) {
        requireField(field);
        PredicateHelper.validateRange(start, end);
        return addCondition((root, cb) -> PredicateHelper.notBetween(root, property(field), start, end, cb));
    }

    // ---- 空值运算符 ----

    default SELF isNull(SFunction<T, ?> field) {
        requireField(field);
        return addCondition((root, cb) -> PredicateHelper.isNull(root, property(field), cb));
    }

    default SELF isNotNull(SFunction<T, ?> field) {
        requireField(field);
        return addCondition((root, cb) -> PredicateHelper.isNotNull(root, property(field), cb));
    }

    // ---- 集合空值检查 ----

    default SELF isEmpty(SFunction<T, ?> field) {
        requireField(field);
        return addCondition((root, cb) -> PredicateHelper.isEmpty(root, property(field), cb));
    }

    default SELF isNotEmpty(SFunction<T, ?> field) {
        requireField(field);
        return addCondition((root, cb) -> PredicateHelper.isNotEmpty(root, property(field), cb));
    }

}
