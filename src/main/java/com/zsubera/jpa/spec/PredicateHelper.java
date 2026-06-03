package com.zsubera.jpa.spec;

import com.zsubera.jpa.util.InClauseBuilder;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import java.util.Collection;

/**
 * JPA {@link Predicate} 构建的共享工具类。
 *
 * <p>
 * 提供即时构建 {@link Predicate} 的静态方法，供需要直接构建谓词的组件使用 （而非通过延迟执行的 {@link ConditionNode} 树）。
 *
 * <p>
 * 主要使用者包括 {@link SubQuerySpec}（即时构建）和 {@link com.zsubera.jpa.update.AbstractBulkOperationSpec
 * AbstractBulkOperationSpec}（延迟包装）， 消除了约 200 行重复的条件构建逻辑。
 *
 * <p>
 * 所有方法接收 {@link Path}、字段名、比较值和 {@link CriteriaBuilder}， 返回完整构建的 {@link Predicate}。参数的空值校验和范围校验由调用方负责。
 *
 * <p>
 * 内部工具类——不建议应用代码直接使用。
 *
 * @author zsubera
 * @since 1.0
 */
public final class PredicateHelper {

    /** LIKE 通配符转义字符：反斜杠。 */
    public static final char LIKE_ESCAPE_CHAR = '\\';

    /** 私有构造函数，防止实例化。 */
    private PredicateHelper() {}

    /**
     * 校验 BETWEEN/NOT BETWEEN 范围参数的合法性。
     *
     * <p>
     * 检查项：
     * <ul>
     * <li>start 和 end 均不能为 null</li>
     * <li>start 和 end 必须为兼容类型（双向 isAssignableFrom 检查，或均为 Number 类型）</li>
     * <li>start 不能大于 end</li>
     * </ul>
     *
     * @param start 范围起始值
     * @param end 范围结束值
     * @throws IllegalArgumentException 如果参数不合法
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void validateRange(Comparable<?> start, Comparable<?> end) {
        if (start == null) {
            throw new IllegalArgumentException("start must not be null");
        }
        if (end == null) {
            throw new IllegalArgumentException("end must not be null");
        }
        if (!start.getClass().isAssignableFrom(end.getClass()) && !end.getClass().isAssignableFrom(start.getClass())) {
            // Allow cross-numeric-type comparison (e.g., Integer vs Long)
            if (!(start instanceof Number) || !(end instanceof Number)) {
                throw new IllegalArgumentException("start and end must be compatible types, but got "
                    + start.getClass().getName() + " and " + end.getClass().getName());
            }
            // Use BigDecimal for precise cross-numeric-type comparison to avoid precision loss
            try {
                java.math.BigDecimal startDecimal = new java.math.BigDecimal(start.toString());
                java.math.BigDecimal endDecimal = new java.math.BigDecimal(end.toString());
                if (startDecimal.compareTo(endDecimal) > 0) {
                    throw new IllegalArgumentException("start must not be greater than end");
                }
            } catch (NumberFormatException e) {
                // Fallback to toString comparison if BigDecimal conversion fails
                try {
                    if (((Comparable)start).compareTo(end) > 0) {
                        throw new IllegalArgumentException("start must not be greater than end");
                    }
                } catch (ClassCastException cce) {
                    throw new IllegalArgumentException("Cannot compare incompatible numeric types: "
                        + start.getClass().getName() + " and " + end.getClass().getName(), cce);
                }
            }
            return;
        }
        if (((Comparable)start).compareTo(end) > 0) {
            throw new IllegalArgumentException("start must not be greater than end");
        }
    }

    /**
     * 转义 LIKE 模式中的 SQL 通配符（{@code %}、{@code _}）。
     *
     * <p>
     * 防止用户输入包含 {@code %} 或 {@code _} 的值时意外匹配到不相关的行。
     *
     * @param input 原始字符串值（可以为 null）
     * @return 转义后的字符串，如果输入为 null 则返回 null
     */
    public static String escapeLikeWildcards(String input) {
        if (input == null) {
            return null;
        }
        return input.replace("\\", "\\\\").replace("_", "\\_").replace("%", "\\%");
    }

    // ==================== 比较运算符 ====================

    /**
     * 构建等于（=）谓词。
     *
     * <p>
     * 当值为 null 时，自动转换为 IS NULL 判断。
     *
     * @param path 实体路径
     * @param fieldName 字段名
     * @param value 比较值（可以为 null）
     * @param cb CriteriaBuilder 实例
     * @return 等于谓词
     */
    public static Predicate eq(Path<?> path, String fieldName, Object value, CriteriaBuilder cb) {
        Path<?> fp = path.get(fieldName);
        return value == null ? cb.isNull(fp) : cb.equal(fp, value);
    }

    /**
     * 构建不等于（&lt;&gt;）谓词。
     *
     * <p>
     * 当值为 null 时，自动转换为 IS NOT NULL 判断。
     *
     * @param path 实体路径
     * @param fieldName 字段名
     * @param value 比较值（可以为 null）
     * @param cb CriteriaBuilder 实例
     * @return 不等于谓词
     */
    public static Predicate ne(Path<?> path, String fieldName, Object value, CriteriaBuilder cb) {
        Path<?> fp = path.get(fieldName);
        return value == null ? cb.isNotNull(fp) : cb.notEqual(fp, value);
    }

    /**
     * 构建大于（&gt;）谓词。
     *
     * @param path 实体路径
     * @param fieldName 字段名
     * @param value 比较值
     * @param cb CriteriaBuilder 实例
     * @return 大于谓词
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Predicate gt(Path<?> path, String fieldName, Comparable<?> value, CriteriaBuilder cb) {
        return cb.greaterThan(path.get(fieldName), (Comparable)value);
    }

    /**
     * 构建大于等于（&gt;=）谓词。
     *
     * @param path 实体路径
     * @param fieldName 字段名
     * @param value 比较值
     * @param cb CriteriaBuilder 实例
     * @return 大于等于谓词
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Predicate ge(Path<?> path, String fieldName, Comparable<?> value, CriteriaBuilder cb) {
        return cb.greaterThanOrEqualTo(path.get(fieldName), (Comparable)value);
    }

    /**
     * 构建小于（&lt;）谓词。
     *
     * @param path 实体路径
     * @param fieldName 字段名
     * @param value 比较值
     * @param cb CriteriaBuilder 实例
     * @return 小于谓词
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Predicate lt(Path<?> path, String fieldName, Comparable<?> value, CriteriaBuilder cb) {
        return cb.lessThan(path.get(fieldName), (Comparable)value);
    }

    /**
     * 构建小于等于（&lt;=）谓词。
     *
     * @param path 实体路径
     * @param fieldName 字段名
     * @param value 比较值
     * @param cb CriteriaBuilder 实例
     * @return 小于等于谓词
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Predicate le(Path<?> path, String fieldName, Comparable<?> value, CriteriaBuilder cb) {
        return cb.lessThanOrEqualTo(path.get(fieldName), (Comparable)value);
    }

    // ==================== 字符串运算符 ====================

    /**
     * 构建 LIKE 谓词。
     *
     * @param path 实体路径
     * @param fieldName 字段名
     * @param value LIKE 模式字符串
     * @param cb CriteriaBuilder 实例
     * @return LIKE 谓词
     */
    public static Predicate like(Path<?> path, String fieldName, String value, CriteriaBuilder cb) {
        return cb.like(path.get(fieldName).as(String.class), value);
    }

    /**
     * 构建带转义字符的 LIKE 谓词。
     *
     * @param path 实体路径
     * @param fieldName 字段名
     * @param value LIKE 模式字符串
     * @param cb CriteriaBuilder 实例
     * @param escapeChar 转义字符
     * @return LIKE 谓词
     */
    public static Predicate like(Path<?> path, String fieldName, String value, CriteriaBuilder cb, char escapeChar) {
        return cb.like(path.get(fieldName).as(String.class), value, escapeChar);
    }

    /**
     * 构建 NOT LIKE 谓词。
     *
     * @param path 实体路径
     * @param fieldName 字段名
     * @param value LIKE 模式字符串
     * @param cb CriteriaBuilder 实例
     * @return NOT LIKE 谓词
     */
    public static Predicate notLike(Path<?> path, String fieldName, String value, CriteriaBuilder cb) {
        return cb.notLike(path.get(fieldName).as(String.class), value);
    }

    /**
     * 构建带转义字符的 NOT LIKE 谓词。
     *
     * @param path 实体路径
     * @param fieldName 字段名
     * @param value LIKE 模式字符串
     * @param cb CriteriaBuilder 实例
     * @param escapeChar 转义字符
     * @return NOT LIKE 谓词
     */
    public static Predicate notLike(Path<?> path, String fieldName, String value, CriteriaBuilder cb, char escapeChar) {
        return cb.notLike(path.get(fieldName).as(String.class), value, escapeChar);
    }

    /**
     * 构建前缀匹配（LIKE 'value%'）谓词。
     *
     * <p>
     * 自动转义值中的通配符字符。
     *
     * @param path 实体路径
     * @param fieldName 字段名
     * @param value 前缀字符串
     * @param cb CriteriaBuilder 实例
     * @return 前缀匹配谓词
     */
    public static Predicate startsWith(Path<?> path, String fieldName, String value, CriteriaBuilder cb) {
        return cb.like(path.get(fieldName).as(String.class), escapeLikeWildcards(value) + "%", LIKE_ESCAPE_CHAR);
    }

    /**
     * 构建后缀匹配（LIKE '%value'）谓词。
     *
     * <p>
     * 自动转义值中的通配符字符。
     *
     * @param path 实体路径
     * @param fieldName 字段名
     * @param value 后缀字符串
     * @param cb CriteriaBuilder 实例
     * @return 后缀匹配谓词
     */
    public static Predicate endsWith(Path<?> path, String fieldName, String value, CriteriaBuilder cb) {
        return cb.like(path.get(fieldName).as(String.class), "%" + escapeLikeWildcards(value), LIKE_ESCAPE_CHAR);
    }

    /**
     * 构建包含匹配（LIKE '%value%'）谓词。
     *
     * <p>
     * 自动转义值中的通配符字符。
     *
     * @param path 实体路径
     * @param fieldName 字段名
     * @param value 要包含的字符串
     * @param cb CriteriaBuilder 实例
     * @return 包含匹配谓词
     */
    public static Predicate contains(Path<?> path, String fieldName, String value, CriteriaBuilder cb) {
        return cb.like(path.get(fieldName).as(String.class), "%" + escapeLikeWildcards(value) + "%", LIKE_ESCAPE_CHAR);
    }

    /**
     * 构建忽略大小写的等于谓词。
     *
     * <p>
     * 通过 {@code UPPER()} 函数将双方转换为大写后比较。 当值为 null 时，自动转换为 IS NULL 判断。
     *
     * @param path 实体路径
     * @param fieldName 字段名
     * @param value 比较值（可以为 null）
     * @param cb CriteriaBuilder 实例
     * @return 忽略大小写的等于谓词
     */
    public static Predicate eqIgnoreCase(Path<?> path, String fieldName, String value, CriteriaBuilder cb) {
        Path<?> fp = path.get(fieldName);
        if (value == null) {
            return cb.isNull(fp);
        }
        return cb.equal(cb.upper(fp.as(String.class)), value.toUpperCase(java.util.Locale.ROOT));
    }

    /**
     * 构建忽略大小写的不等于谓词。
     *
     * <p>
     * 通过 {@code UPPER()} 函数将双方转换为大写后进行比较。与 {@link #eqIgnoreCase} 对称。
     *
     * @param path 实体路径
     * @param fieldName 字段名
     * @param value 比较值（可以为 null）
     * @param cb CriteriaBuilder 实例
     * @return 忽略大小写的不等于谓词
     */
    public static Predicate neIgnoreCase(Path<?> path, String fieldName, String value, CriteriaBuilder cb) {
        Path<?> fp = path.get(fieldName);
        if (value == null) {
            return cb.isNotNull(fp);
        }
        return cb.notEqual(cb.upper(fp.as(String.class)), value.toUpperCase(java.util.Locale.ROOT));
    }

    /**
     * 构建忽略大小写的 LIKE 谓词。
     *
     * <p>
     * 通过 {@code UPPER()} 函数将双方转换为大写后进行 LIKE 匹配。 值中的 {@code %} 和 {@code _} 通配符应由调用方预先转义。
     *
     * @param path 实体路径
     * @param fieldName 字段名
     * @param value LIKE 模式字符串（应已包含大写形式）
     * @param cb CriteriaBuilder 实例
     * @return 忽略大小写的 LIKE 谓词
     */
    public static Predicate likeIgnoreCase(Path<?> path, String fieldName, String value, CriteriaBuilder cb) {
        return likeIgnoreCase(path, fieldName, value, cb, '\0');
    }

    /**
     * 构建带转义字符的忽略大小写的 LIKE 谓词。
     *
     * <p>
     * 通过 {@code UPPER()} 函数将双方转换为大写后进行 LIKE 匹配。
     *
     * @param path 实体路径
     * @param fieldName 字段名
     * @param value LIKE 模式字符串
     * @param cb CriteriaBuilder 实例
     * @param escapeChar 转义字符
     * @return 忽略大小写的 LIKE 谓词
     */
    public static Predicate likeIgnoreCase(Path<?> path, String fieldName, String value, CriteriaBuilder cb,
        char escapeChar) {
        if (escapeChar != '\0') {
            return cb.like(cb.upper(path.get(fieldName).as(String.class)), value.toUpperCase(java.util.Locale.ROOT),
                escapeChar);
        }
        return cb.like(cb.upper(path.get(fieldName).as(String.class)), value.toUpperCase(java.util.Locale.ROOT));
    }

    // ==================== 集合运算符 ====================

    /**
     * 构建 IN 谓词（数组形式）。
     *
     * <p>
     * 如果值的数量超过 {@link InClauseBuilder#getMaxInClauseSize()}，会自动分批处理。
     *
     * @param path 实体路径
     * @param fieldName 字段名
     * @param values 值数组
     * @param cb CriteriaBuilder 实例
     * @return IN 谓词
     */
    public static Predicate in(Path<?> path, String fieldName, Object[] values, CriteriaBuilder cb) {
        return InClauseBuilder.in(cb, path.get(fieldName), values);
    }

    /**
     * 构建 NOT IN 谓词（数组形式）。
     *
     * <p>
     * 如果值的数量超过 {@link InClauseBuilder#getMaxInClauseSize()}，会自动分批处理。
     *
     * @param path 实体路径
     * @param fieldName 字段名
     * @param values 值数组
     * @param cb CriteriaBuilder 实例
     * @return NOT IN 谓词
     */
    public static Predicate notIn(Path<?> path, String fieldName, Object[] values, CriteriaBuilder cb) {
        return InClauseBuilder.notIn(cb, path.get(fieldName), values);
    }

    /**
     * 构建 IN 谓词（集合形式）。
     *
     * <p>
     * 如果值的数量超过 {@link InClauseBuilder#getMaxInClauseSize()}，会自动分批处理。
     *
     * @param path 实体路径
     * @param fieldName 字段名
     * @param values 值集合
     * @param cb CriteriaBuilder 实例
     * @return IN 谓词
     */
    public static Predicate in(Path<?> path, String fieldName, Collection<?> values, CriteriaBuilder cb) {
        return InClauseBuilder.in(cb, path.get(fieldName), values);
    }

    /**
     * 构建 NOT IN 谓词（集合形式）。
     *
     * <p>
     * 如果值的数量超过 {@link InClauseBuilder#getMaxInClauseSize()}，会自动分批处理。
     *
     * @param path 实体路径
     * @param fieldName 字段名
     * @param values 值集合
     * @param cb CriteriaBuilder 实例
     * @return NOT IN 谓词
     */
    public static Predicate notIn(Path<?> path, String fieldName, Collection<?> values, CriteriaBuilder cb) {
        return InClauseBuilder.notIn(cb, path.get(fieldName), values);
    }

    // ==================== 范围运算符 ====================

    /**
     * 构建 BETWEEN 谓词。
     *
     * @param path 实体路径
     * @param fieldName 字段名
     * @param start 范围起始值（包含）
     * @param end 范围结束值（包含）
     * @param cb CriteriaBuilder 实例
     * @return BETWEEN 谓词
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Predicate between(Path<?> path, String fieldName, Comparable<?> start, Comparable<?> end,
        CriteriaBuilder cb) {
        return cb.between(path.get(fieldName), (Comparable)start, (Comparable)end);
    }

    /**
     * 构建 NOT BETWEEN 谓词。
     *
     * @param path 实体路径
     * @param fieldName 字段名
     * @param start 范围起始值（包含）
     * @param end 范围结束值（包含）
     * @param cb CriteriaBuilder 实例
     * @return NOT BETWEEN 谓词
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Predicate notBetween(Path<?> path, String fieldName, Comparable<?> start, Comparable<?> end,
        CriteriaBuilder cb) {
        return cb.not(cb.between(path.get(fieldName), (Comparable)start, (Comparable)end));
    }

    // ==================== NULL 运算符 ====================

    /**
     * 构建 IS NULL 谓词。
     *
     * @param path 实体路径
     * @param fieldName 字段名
     * @param cb CriteriaBuilder 实例
     * @return IS NULL 谓词
     */
    public static Predicate isNull(Path<?> path, String fieldName, CriteriaBuilder cb) {
        return cb.isNull(path.get(fieldName));
    }

    /**
     * 构建 IS NOT NULL 谓词。
     *
     * @param path 实体路径
     * @param fieldName 字段名
     * @param cb CriteriaBuilder 实例
     * @return IS NOT NULL 谓词
     */
    public static Predicate isNotNull(Path<?> path, String fieldName, CriteriaBuilder cb) {
        return cb.isNotNull(path.get(fieldName));
    }

    // ==================== 集合空值检查 ====================

    /**
     * 根据 {@link ConditionNode.SimpleNode} 的运算符解析为对应的 JPA {@link Predicate}。
     *
     * <p>
     * 此方法集中处理简单条件的解析逻辑，避免重复代码。新增 {@link ConditionNode.Op} 枚举值时，只需在此方法中添加对应的 case。
     *
     * @param path 实体路径（可以是 Root 或 Join）
     * @param node 简单条件节点
     * @param cb CriteriaBuilder 实例
     * @return 解析后的 Predicate
     * @throws IllegalArgumentException 如果遇到未处理的 Op 枚举值（编程错误）
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Predicate resolveSimplePredicate(Path<?> path, ConditionNode.SimpleNode node, CriteriaBuilder cb) {
        if (path == null) {
            throw new IllegalArgumentException("path must not be null");
        }
        if (node == null) {
            throw new IllegalArgumentException("node must not be null");
        }
        if (cb == null) {
            throw new IllegalArgumentException("cb must not be null");
        }
        Path<?> fieldPath = path.get(node.fieldName);
        switch (node.op) {
            case EQ:
                return node.value == null ? cb.isNull(fieldPath) : cb.equal(fieldPath, node.value);
            case NE:
                return node.value == null ? cb.isNotNull(fieldPath) : cb.notEqual(fieldPath, node.value);
            case GT:
                return cb.greaterThan((Expression<Comparable>)fieldPath, (Comparable)node.value);
            case GE:
                return cb.greaterThanOrEqualTo((Expression<Comparable>)fieldPath, (Comparable)node.value);
            case LT:
                return cb.lessThan((Expression<Comparable>)fieldPath, (Comparable)node.value);
            case LE:
                return cb.lessThanOrEqualTo((Expression<Comparable>)fieldPath, (Comparable)node.value);
            case LIKE:
                if (node.escapeChar != '\0') {
                    return cb.like(fieldPath.as(String.class), (String)node.value, node.escapeChar);
                }
                return cb.like(fieldPath.as(String.class), (String)node.value);
            case NOT_LIKE:
                if (node.escapeChar != '\0') {
                    return cb.notLike(fieldPath.as(String.class), (String)node.value, node.escapeChar);
                }
                return cb.notLike(fieldPath.as(String.class), (String)node.value);
            case EQ_IGNORE_CASE:
                if (node.value == null) {
                    return cb.isNull(fieldPath);
                }
                return cb.equal(cb.upper(fieldPath.as(String.class)),
                    ((String)node.value).toUpperCase(java.util.Locale.ROOT));
            case NE_IGNORE_CASE:
                if (node.value == null) {
                    return cb.isNotNull(fieldPath);
                }
                return cb.notEqual(cb.upper(fieldPath.as(String.class)),
                    ((String)node.value).toUpperCase(java.util.Locale.ROOT));
            case LIKE_IGNORE_CASE:
                if (node.value == null) {
                    return cb.isNull(fieldPath);
                }
                if (node.escapeChar != '\0') {
                    return cb.like(cb.upper(fieldPath.as(String.class)),
                        ((String)node.value).toUpperCase(java.util.Locale.ROOT), node.escapeChar);
                }
                return cb.like(cb.upper(fieldPath.as(String.class)),
                    ((String)node.value).toUpperCase(java.util.Locale.ROOT));
            case IS_NULL:
                return cb.isNull(fieldPath);
            case IS_NOT_NULL:
                return cb.isNotNull(fieldPath);
            case IN: {
                if (node.value == null) {
                    throw new IllegalArgumentException("IN operator requires non-null value, got null");
                }
                if (node.value instanceof Collection) {
                    return InClauseBuilder.in(cb, fieldPath, (Collection<?>)node.value);
                }
                if (node.value.getClass().isArray()) {
                    return InClauseBuilder.in(cb, fieldPath, (Object[])node.value);
                }
                throw new IllegalArgumentException(
                    "IN operator requires Collection or array, got: " + node.value.getClass().getName());
            }
            case NOT_IN: {
                if (node.value == null) {
                    throw new IllegalArgumentException("NOT_IN operator requires non-null value, got null");
                }
                if (node.value instanceof Collection) {
                    return InClauseBuilder.notIn(cb, fieldPath, (Collection<?>)node.value);
                }
                if (node.value.getClass().isArray()) {
                    return InClauseBuilder.notIn(cb, fieldPath, (Object[])node.value);
                }
                throw new IllegalArgumentException(
                    "NOT_IN operator requires Collection or array, got: " + node.value.getClass().getName());
            }
            case BETWEEN: {
                Comparable<?>[] range = (Comparable<?>[])node.value;
                if (range.length != 2) {
                    throw new IllegalArgumentException("BETWEEN requires exactly 2 values, got " + range.length);
                }
                return cb.between((Expression<Comparable>)fieldPath, (Comparable)range[0], (Comparable)range[1]);
            }
            case NOT_BETWEEN: {
                Comparable<?>[] range = (Comparable<?>[])node.value;
                if (range.length != 2) {
                    throw new IllegalArgumentException("NOT_BETWEEN requires exactly 2 values, got " + range.length);
                }
                return cb
                    .not(cb.between((Expression<Comparable>)fieldPath, (Comparable)range[0], (Comparable)range[1]));
            }
            default:
                throw new IllegalArgumentException("Unhandled Op: " + node.op);
        }
    }

    /**
     * 构建集合为空（IS EMPTY）谓词。
     *
     * @param path 实体路径
     * @param fieldName 字段名
     * @param cb CriteriaBuilder 实例
     * @return 集合为空谓词
     */
    @SuppressWarnings("unchecked")
    public static Predicate isEmpty(Path<?> path, String fieldName, CriteriaBuilder cb) {
        return cb.isEmpty((Expression<java.util.Collection<?>>)(Expression<?>)path.get(fieldName));
    }

    /**
     * 构建集合不为空（IS NOT EMPTY）谓词。
     *
     * @param path 实体路径
     * @param fieldName 字段名
     * @param cb CriteriaBuilder 实例
     * @return 集合不为空谓词
     */
    @SuppressWarnings("unchecked")
    public static Predicate isNotEmpty(Path<?> path, String fieldName, CriteriaBuilder cb) {
        return cb.isNotEmpty((Expression<java.util.Collection<?>>)(Expression<?>)path.get(fieldName));
    }
}
