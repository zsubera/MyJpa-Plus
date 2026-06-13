package com.zsubera.jpa.spec;

import static com.zsubera.jpa.spec.ConditionalMethods.requireField;
import static com.zsubera.jpa.spec.ConditionalMethods.requireNonEmpty;
import static com.zsubera.jpa.spec.ConditionalMethods.requireValue;
import static com.zsubera.jpa.spec.ConditionalMethods.resolveProperty;
import static com.zsubera.jpa.spec.ConditionalMethods.wrapLikePattern;
import static com.zsubera.jpa.spec.ConditionalMethods.prefixLikePattern;
import static com.zsubera.jpa.spec.ConditionalMethods.suffixLikePattern;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
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
public interface ConditionBuilder<E, SELF extends ConditionBuilder<E, SELF>> extends ConditionalMethods<E, SELF> {

    /**
     * 安全字段名正则表达式：仅允许字母、数字和下划线。
     *
     * <p>
     * 用于校验接受原始 {@code String} 字段名的方法（如 {@link #multiLike(String, String...)}），防止 SQL 注入。
     *
     * <p>
     * <strong>注意：</strong>此正则不允许点号（{@code .}），因为嵌套属性应通过 {@link SFunction} 方法引用处理。 如需支持嵌套路径（如 "address.city"），请使用
     * {@link #SAFE_NESTED_FIELD_NAME_PATTERN}。
     */
    Pattern SAFE_FIELD_NAME_PATTERN = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]*$");

    /**
     * 安全嵌套字段名正则表达式：允许字母、数字、下划线和点号。
     *
     * <p>
     * 用于校验需要支持嵌套属性路径的场景（如 JPA 嵌入对象的字段引用 {@code "address.city"}）。 点号分隔的每一段必须以字母或下划线开头，不允许连续点号。
     */
    Pattern SAFE_NESTED_FIELD_NAME_PATTERN = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]*(\\.[a-zA-Z_][a-zA-Z0-9_]*)*$");

    /**
     * 安全数据库函数名白名单。仅允许调用以下常见安全函数。
     *
     * <p>
     * 此集合为不可变集合，禁止调用危险函数如 {@code pg_sleep}、{@code SLEEP}、{code LOAD_FILE} 等。
     *
     * <p>
     * 白名单强制执行由 {@link #WHITELIST_ENFORCED} 控制（硬编码为 true）。 如需扩展白名单，请通过系统属性 {@code myjpa-plus.func.extra-safe-functions}
     * 在启动前配置。
     */
    Set<String> SAFE_FUNCTION_NAMES = Set.copyOf(initDefaultFunctionNames());

    /**
     * 白名单强制执行开关。硬编码为 true，不可通过系统属性禁用。
     *
     * <p>
     * 此开关防止通过系统属性 {@code myjpa-plus.func.whitelist-enforced=false} 禁用白名单保护。 攻击者若能控制系统属性，可禁用白名单保护，因此移除了系统属性读取逻辑。
     */
    boolean WHITELIST_ENFORCED = true;

    /**
     * 布尔函数名白名单。{@code func()} 方法只允许调用这些返回布尔值的函数。
     *
     * <p>
     * 严格是 {@link #SAFE_FUNCTION_NAMES} 的子集——函数必须先通过安全白名单，再通过布尔白名单。
     * 这防止了像 {@code LENGTH(name) = true} 这种无意义 SQL 的产生。
     */
    Set<String> BOOLEAN_FUNCTION_NAMES = Set.of("COALESCE", "NULLIF", "IF", "DECODE", "IFNULL", "NVL", "NVL2",
        "JSONB_EXISTS", "ST_CONTAINS", "ST_WITHIN", "ST_INTERSECTS");

    /**
     * 初始化默认安全函数名白名单。
     *
     * @return 包含默认安全函数名的可变线程安全集合
     */
    private static Set<String> initDefaultFunctionNames() {
        Set<String> names = new java.util.HashSet<>();
        names.addAll(Set.of("LOWER", "UPPER", "TRIM", "LTRIM", "RTRIM", "LENGTH", "CHAR_LENGTH", "COALESCE", "NULLIF",
            "ABS", "ROUND", "CEIL", "FLOOR", "MOD", "CONCAT", "SUBSTRING", "SUBSTR", "REPLACE", "LEFT", "RIGHT", "NOW",
            "CURRENT_TIMESTAMP", "CURRENT_DATE", "CURRENT_TIME", "EXTRACT", "DATE_FORMAT", "TO_CHAR", "TO_DATE",
            "TO_TIMESTAMP", "CAST", "TYPEOF", "JSONB_EXISTS", "JSONB_EXTRACT_PATH_TEXT", "JSON_VALUE", "ST_CONTAINS",
            "ST_DISTANCE", "ST_WITHIN", "ST_INTERSECTS", "ARRAY_LENGTH", "ARRAY_AGG", "STRING_AGG", "GREATEST", "LEAST",
            "SIGN", "POWER", "SQRT", "LOG", "LN", "EXP", "POSITION", "OVERLAY", "TRANSLATE", "REVERSE", "REPEAT",
            "SPACE", "YEAR", "MONTH", "DAY", "HOUR", "MINUTE", "SECOND", "ADD_MONTHS", "ADD_DAYS", "DATE_DIFF",
            "DATEDIFF", "IFNULL", "IF", "NVL", "NVL2", "DECODE", "JSON_OBJECT", "JSON_ARRAY", "JSON_EXTRACT",
            "JSON_UNQUOTE", "UUID", "UUID_GENERATE_V4", "HEX", "UNHEX"));
        // 日期/时间截断函数
        names.addAll(Set.of("TRUNCATE", "DATE_TRUNC", "DATE_TRUNCATE", "DATETRUNC"));
        // 窗口函数（用于 ORDER BY 或子查询中的表达式）
        names.addAll(Set.of("ROW_NUMBER", "RANK", "DENSE_RANK", "NTILE", "LAG", "LEAD", "FIRST_VALUE", "LAST_VALUE",
            "NTH_VALUE"));
        // 聚合函数
        names.addAll(Set.of("COUNT", "SUM", "AVG", "MIN", "MAX", "GROUP_CONCAT", "LISTAGG", "ARRAY_AGG"));
        // 数学函数
        names.addAll(Set.of("PI", "RADIANS", "DEGREES", "SIN", "COS", "TAN", "ASIN", "ACOS", "ATAN", "ATAN2", "CBRT",
            "FACTORIAL", "RANDOM", "RAND"));
        // 字符串函数
        names.addAll(Set.of("INITCAP", "LPAD", "RPAD", "ASCII", "CHR", "CONCAT_WS", "FORMAT", "INSERT", "LOCATE"));
        return names;
    }

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
    @Override
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
    @Override
    default SELF eq(SFunction<E, ?> field, @Nullable Object value) {
        requireField(field);
        conditions().add(new ConditionNode.SimpleNode(resolveProperty(field), value,
            value == null ? ConditionNode.Op.IS_NULL : ConditionNode.Op.EQ));
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
    @Override
    default SELF ne(SFunction<E, ?> field, @Nullable Object value) {
        requireField(field);
        conditions().add(new ConditionNode.SimpleNode(resolveProperty(field), value,
            value == null ? ConditionNode.Op.IS_NOT_NULL : ConditionNode.Op.NE));
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
        requireField(field);
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
        requireField(field);
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
    @Override
    default SELF gt(SFunction<E, ?> field, Comparable<?> value) {
        requireField(field);
        requireValue(value, "gt");
        conditions().add(new ConditionNode.SimpleNode(resolveProperty(field), value, ConditionNode.Op.GT));
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
    @Override
    default SELF ge(SFunction<E, ?> field, Comparable<?> value) {
        requireField(field);
        requireValue(value, "ge");
        conditions().add(new ConditionNode.SimpleNode(resolveProperty(field), value, ConditionNode.Op.GE));
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
    @Override
    default SELF lt(SFunction<E, ?> field, Comparable<?> value) {
        requireField(field);
        requireValue(value, "lt");
        conditions().add(new ConditionNode.SimpleNode(resolveProperty(field), value, ConditionNode.Op.LT));
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
    @Override
    default SELF le(SFunction<E, ?> field, Comparable<?> value) {
        requireField(field);
        requireValue(value, "le");
        conditions().add(new ConditionNode.SimpleNode(resolveProperty(field), value, ConditionNode.Op.LE));
        return self();
    }

    // ---- 字符串运算符 ----

    /**
     * 添加包含匹配条件 {@code field LIKE '%value%'}，值中的 {@code %} 或 {@code _} 通配符会被自动转义。
     *
     * @param field 实体属性的方法引用
     * @param value 要匹配的字符串值（通配符会被自动转义）
     * @return 当前构建器以支持链式调用
     * @throws IllegalArgumentException 如果 {@code field} 或 {@code value} 为 null
     */
    @Override
    default SELF like(SFunction<E, ?> field, String value) {
        requireField(field);
        requireValue(value, "like");
        conditions().add(new ConditionNode.SimpleNode(resolveProperty(field), wrapLikePattern(value),
            ConditionNode.Op.LIKE, PredicateHelper.LIKE_ESCAPE_CHAR));
        return self();
    }

    /**
     * 添加不包含匹配条件 {@code field NOT LIKE '%value%'}，值中的 {@code %} 或 {@code _} 通配符会被自动转义。
     *
     * @param field 实体属性的方法引用
     * @param value 要匹配的字符串值（通配符会被自动转义）
     * @return 当前构建器以支持链式调用
     * @throws IllegalArgumentException 如果 {@code field} 或 {@code value} 为 null
     */
    @Override
    default SELF notLike(SFunction<E, ?> field, String value) {
        requireField(field);
        requireValue(value, "notLike");
        conditions().add(new ConditionNode.SimpleNode(resolveProperty(field), wrapLikePattern(value),
            ConditionNode.Op.NOT_LIKE, PredicateHelper.LIKE_ESCAPE_CHAR));
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
    @Override
    default SELF startsWith(SFunction<E, ?> field, String value) {
        requireField(field);
        requireValue(value, "startsWith");
        conditions().add(new ConditionNode.SimpleNode(resolveProperty(field), prefixLikePattern(value),
            ConditionNode.Op.LIKE, PredicateHelper.LIKE_ESCAPE_CHAR));
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
    @Override
    default SELF endsWith(SFunction<E, ?> field, String value) {
        requireField(field);
        requireValue(value, "endsWith");
        conditions().add(new ConditionNode.SimpleNode(resolveProperty(field), suffixLikePattern(value),
            ConditionNode.Op.LIKE, PredicateHelper.LIKE_ESCAPE_CHAR));
        return self();
    }

    /**
     * 添加不匹配前缀条件：{@code field NOT LIKE 'value%'}。
     *
     * @param field 实体属性的方法引用
     * @param value 前缀字符串值
     * @return 当前构建器以支持链式调用
     * @throws IllegalArgumentException 如果 {@code field} 或 {@code value} 为 null
     */
    @Override
    default SELF notStartsWith(SFunction<E, ?> field, String value) {
        requireField(field);
        requireValue(value, "notStartsWith");
        conditions().add(new ConditionNode.SimpleNode(resolveProperty(field), prefixLikePattern(value),
            ConditionNode.Op.NOT_LIKE, PredicateHelper.LIKE_ESCAPE_CHAR));
        return self();
    }

    /**
     * 添加不匹配后缀条件：{@code field NOT LIKE '%value'}。
     *
     * @param field 实体属性的方法引用
     * @param value 后缀字符串值
     * @return 当前构建器以支持链式调用
     * @throws IllegalArgumentException 如果 {@code field} 或 {@code value} 为 null
     */
    @Override
    default SELF notEndsWith(SFunction<E, ?> field, String value) {
        requireField(field);
        requireValue(value, "notEndsWith");
        conditions().add(new ConditionNode.SimpleNode(resolveProperty(field), suffixLikePattern(value),
            ConditionNode.Op.NOT_LIKE, PredicateHelper.LIKE_ESCAPE_CHAR));
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
    @Override
    default SELF in(SFunction<E, ?> field, Object... values) {
        requireField(field);
        requireNonEmpty(values);
        conditions().add(new ConditionNode.SimpleNode(resolveProperty(field), values, ConditionNode.Op.IN));
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
    @Override
    default SELF notIn(SFunction<E, ?> field, Object... values) {
        requireField(field);
        requireNonEmpty(values);
        conditions().add(new ConditionNode.SimpleNode(resolveProperty(field), values, ConditionNode.Op.NOT_IN));
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
    @Override
    default SELF in(SFunction<E, ?> field, Collection<?> values) {
        requireField(field);
        requireNonEmpty(values);
        conditions().add(new ConditionNode.SimpleNode(resolveProperty(field), values, ConditionNode.Op.IN));
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
    @Override
    default SELF notIn(SFunction<E, ?> field, Collection<?> values) {
        requireField(field);
        requireNonEmpty(values);
        conditions().add(new ConditionNode.SimpleNode(resolveProperty(field), values, ConditionNode.Op.NOT_IN));
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
    @Override
    default SELF between(SFunction<E, ?> field, Comparable<?> start, Comparable<?> end) {
        requireField(field);
        PredicateHelper.validateRange(start, end);
        conditions().add(new ConditionNode.SimpleNode(resolveProperty(field), new Comparable<?>[] {start, end},
            ConditionNode.Op.BETWEEN));
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
    @Override
    default SELF notBetween(SFunction<E, ?> field, Comparable<?> start, Comparable<?> end) {
        requireField(field);
        PredicateHelper.validateRange(start, end);
        conditions().add(new ConditionNode.SimpleNode(resolveProperty(field), new Comparable<?>[] {start, end},
            ConditionNode.Op.NOT_BETWEEN));
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
    @Override
    default SELF isNull(SFunction<E, ?> field) {
        requireField(field);
        conditions().add(new ConditionNode.SimpleNode(resolveProperty(field), null, ConditionNode.Op.IS_NULL));
        return self();
    }

    /**
     * 添加 IS NOT NULL 条件：{@code field IS NOT NULL}。
     *
     * @param field 实体属性的方法引用
     * @return 当前构建器以支持链式调用
     * @throws IllegalArgumentException 如果 {@code field} 为 null
     */
    @Override
    default SELF isNotNull(SFunction<E, ?> field) {
        requireField(field);
        conditions().add(new ConditionNode.SimpleNode(resolveProperty(field), null, ConditionNode.Op.IS_NOT_NULL));
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
    @Override
    default SELF eqIgnoreCase(SFunction<E, ?> field, @Nullable String value) {
        requireField(field);
        if (value == null) {
            return isNull(field);
        }
        conditions().add(new ConditionNode.SimpleNode(resolveProperty(field), value, ConditionNode.Op.EQ_IGNORE_CASE));
        return self();
    }

    /**
     * 添加不区分大小写的不等条件：{@code UPPER(field) <> UPPER(value)}。 与 {@link #eqIgnoreCase} 对称，适用于排除特定不区分大小写值的场景。
     *
     * @param field 实体属性的方法引用
     * @param value 要比较的字符串值，如果为 null 则生成 IS NOT NULL 条件
     * @return 当前构建器以支持链式调用
     * @throws IllegalArgumentException 如果 {@code field} 为 null
     */
    @Override
    default SELF neIgnoreCase(SFunction<E, ?> field, @Nullable String value) {
        requireField(field);
        if (value == null) {
            return isNotNull(field);
        }
        conditions().add(new ConditionNode.SimpleNode(resolveProperty(field), value, ConditionNode.Op.NE_IGNORE_CASE));
        return self();
    }

    /**
     * 添加不区分大小写的 LIKE 条件：{@code UPPER(field) LIKE UPPER('%value%')}。
     *
     * <p>
     * 值中的 {@code %} 或 {@code _} 字符会被转义，作为字面量处理，防止 LIKE 注入。
     *
     * @param field 实体属性的方法引用
     * @param value 要匹配的原始字符串值（通配符会被转义）
     * @return 当前构建器以支持链式调用
     * @throws IllegalArgumentException 如果 {@code field} 或 {@code value} 为 null
     */
    @Override
    default SELF likeIgnoreCase(SFunction<E, ?> field, String value) {
        requireField(field);
        requireValue(value, "likeIgnoreCase");
        conditions().add(new ConditionNode.SimpleNode(resolveProperty(field), wrapLikePattern(value),
            ConditionNode.Op.LIKE_IGNORE_CASE, PredicateHelper.LIKE_ESCAPE_CHAR));
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
    @Override
    default SELF isEmpty(SFunction<E, ?> field) {
        requireField(field);
        conditions().add(new ConditionNode.CollectionNode(resolveProperty(field), ConditionNode.CollectionOp.IS_EMPTY));
        return self();
    }

    /**
     * 添加 IS NOT EMPTY 条件，用于一对多关联。
     *
     * @param field 实体属性的方法引用
     * @return 当前构建器以支持链式调用
     * @throws IllegalArgumentException 如果 {@code field} 为 null
     */
    @Override
    default SELF isNotEmpty(SFunction<E, ?> field) {
        requireField(field);
        conditions()
            .add(new ConditionNode.CollectionNode(resolveProperty(field), ConditionNode.CollectionOp.IS_NOT_EMPTY));
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
        if (keyword == null) {
            throw new IllegalArgumentException("keyword must not be null");
        }
        if (fields == null) {
            throw new IllegalArgumentException("fields must not be null");
        }
        if (!keyword.isEmpty() && fields.length > 0) {
            String[] fieldNames = new String[fields.length];
            for (int i = 0; i < fields.length; i++) {
                if (fields[i] == null) {
                    throw new IllegalArgumentException("fields[" + i + "] must not be null");
                }
                fieldNames[i] = resolveProperty(fields[i]);
            }
            conditions().add(new ConditionNode.MultiLikeNode(keyword, fieldNames));
        }
        return self();
    }

    /**
     * 添加多字段 LIKE 搜索（字符串字段名版本）。
     *
     * <p>
     * 适用于只有字符串形式字段名的场景（如通用解析器、动态字段名等）， 无法使用方法引用时的替代方案。
     *
     * <pre>{@code
     * // 使用字符串字段名
     * qs.multiLike("keyword", "name", "email", "phone");
     *
     * // 动态字段名
     * String[] searchFields = {"name", "email"};
     * qs.multiLike("keyword", searchFields);
     * }</pre>
     *
     * @param keyword 搜索关键字
     * @param fieldNames 一个或多个字段名字符串
     * @return 当前构建器以支持链式调用
     * @throws IllegalArgumentException 如果 {@code fieldNames} 为 null、空或包含 null 元素
     */
    default SELF multiLike(String keyword, String... fieldNames) {
        if (keyword == null) {
            throw new IllegalArgumentException("keyword must not be null");
        }
        if (fieldNames == null || fieldNames.length == 0) {
            throw new IllegalArgumentException("fieldNames must not be empty");
        }
        if (!keyword.isEmpty()) {
            for (int i = 0; i < fieldNames.length; i++) {
                if (fieldNames[i] == null) {
                    throw new IllegalArgumentException("fieldNames[" + i + "] must not be null");
                }
                // 使用 SAFE_NESTED_FIELD_NAME_PATTERN 支持点号分隔的字段名
                // （例如嵌入对象的 "address.city"）
                if (!SAFE_NESTED_FIELD_NAME_PATTERN.matcher(fieldNames[i]).matches()) {
                    throw new IllegalArgumentException(
                        "fieldNames[" + i + "] contains invalid characters: " + fieldNames[i]);
                }
            }
            conditions().add(new ConditionNode.MultiLikeNode(keyword, fieldNames));
        }
        return self();
    }

    // ---- IN 子查询 ----

    /**
     * 添加 IN 子查询条件：{@code field IN (SELECT ...)}。
     *
     * <p>
     * 使用示例：
     *
     * <pre>{@code
     * qs.inSubQuery(User::getDepartmentId, Department.class,
     *     sub -> sub.eq(Department::getActive, true).select(Department::getId));
     * }</pre>
     *
     * <p>
     * 生成：{@code user.department_id IN (SELECT d.id FROM department d WHERE d.active = true)}
     *
     * @param outerField 外部实体的字段方法引用
     * @param subEntity 子查询实体类型
     * @param config 子查询配置消费者
     * @param <S> 子查询实体类型
     * @return 当前构建器以支持链式调用
     * @throws IllegalArgumentException 如果任何参数为 null
     */
    default <S> SELF inSubQuery(SFunction<E, ?> outerField, Class<S> subEntity,
        java.util.function.Consumer<SubQuerySpec<S>> config) {
        if (outerField == null) {
            throw new IllegalArgumentException("outerField must not be null");
        }
        if (subEntity == null) {
            throw new IllegalArgumentException("subEntity must not be null");
        }
        if (config == null) {
            throw new IllegalArgumentException("config must not be null");
        }
        conditions().add(new ConditionNode.InSubQueryNode<>(resolveProperty(outerField), subEntity, config, false));
        return self();
    }

    /**
     * 添加 NOT IN 子查询条件：{@code field NOT IN (SELECT ...)}。
     *
     * <p>
     * 使用示例：
     *
     * <pre>{@code
     * qs.notInSubQuery(User::getDepartmentId, Department.class,
     *     sub -> sub.eq(Department::getArchived, true).select(Department::getId));
     * }</pre>
     *
     * <p>
     * 生成：{@code user.department_id NOT IN (SELECT d.id FROM department d WHERE d.archived = true)}
     *
     * @param outerField 外部实体的字段方法引用
     * @param subEntity 子查询实体类型
     * @param config 子查询配置消费者
     * @param <S> 子查询实体类型
     * @return 当前构建器以支持链式调用
     * @throws IllegalArgumentException 如果任何参数为 null
     */
    default <S> SELF notInSubQuery(SFunction<E, ?> outerField, Class<S> subEntity,
        java.util.function.Consumer<SubQuerySpec<S>> config) {
        if (outerField == null) {
            throw new IllegalArgumentException("outerField must not be null");
        }
        if (subEntity == null) {
            throw new IllegalArgumentException("subEntity must not be null");
        }
        if (config == null) {
            throw new IllegalArgumentException("config must not be null");
        }
        conditions().add(new ConditionNode.InSubQueryNode<>(resolveProperty(outerField), subEntity, config, true));
        return self();
    }

    // ---- ConditionBuilder 独有的条件便捷方法 ----

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

    /**
     * 仅在 {@code condition} 为 true 时添加严格等值条件。
     *
     * @param condition 是否添加条件的标志
     * @param field 实体属性的方法引用
     * @param value 要比较的值
     * @return 当前构建器以支持链式调用
     */
    default SELF eqStrict(boolean condition, SFunction<E, ?> field, Object value) {
        return condition ? eqStrict(field, value) : self();
    }

    /**
     * 仅在 {@code condition} 为 true 时添加严格不等条件。
     *
     * @param condition 是否添加条件的标志
     * @param field 实体属性的方法引用
     * @param value 要比较的值
     * @return 当前构建器以支持链式调用
     */
    default SELF neStrict(boolean condition, SFunction<E, ?> field, Object value) {
        return condition ? neStrict(field, value) : self();
    }

    /**
     * 仅在 {@code condition} 为 true 时添加多字段 LIKE 搜索（字符串字段名版本）。
     *
     * @param condition 是否添加条件的标志
     * @param keyword 搜索关键字
     * @param fieldNames 一个或多个字符串属性名
     * @return 当前构建器以支持链式调用
     */
    default SELF multiLike(boolean condition, String keyword, String... fieldNames) {
        return condition ? multiLike(keyword, fieldNames) : self();
    }

    /**
     * 仅在 {@code condition} 为 true 时调用数据库函数进行条件判断。
     *
     * @param condition 是否添加条件的标志
     * @param field 实体属性的方法引用，作为函数的第一个参数
     * @param functionName 数据库函数名（必须为硬编码常量，勿使用用户输入）
     * @param params 函数的额外参数
     * @return 当前构建器以支持链式调用
     */
    default SELF func(boolean condition, SFunction<E, ?> field, String functionName, Object... params) {
        return condition ? func(field, functionName, params) : self();
    }

    // ---- 数据库函数运算符 ----

    /**
     * 调用数据库函数进行条件判断。
     *
     * <p>
     * 此方法用于调用数据库特定的函数进行条件判断。例如，使用 PostgreSQL 的 {@code jsonb_exists} 函数：
     *
     * <pre>{@code
     * qs.func(User::getMetadata, "jsonb_exists", "key")
     * // 生成: jsonb_exists(user.metadata, 'key') = true
     * }</pre>
     *
     * <p>
     * <strong>安全警告：请勿使用用户输入作为 {@code functionName} 参数，以防止潜在的 SQL 注入风险！</strong>
     * <ul>
     * <li>函数名仅接受字母、数字和下划线（通过 {@link #SAFE_FIELD_NAME_PATTERN} 校验）</li>
     * <li>请务必使用硬编码的数据库函数名常量，例如 {@code "jsonb_exists"}、{@code "ST_Contains"} 等</li>
     * <li>绝不能将用户输入字符串直接传递给此参数</li>
     * <li>参数值通过 JPA 参数化绑定，不受 SQL 注入影响</li>
     * </ul>
     *
     * @param field 实体属性的方法引用，作为函数的第一个参数
     * @param functionName 数据库函数名（必须为硬编码常量，勿使用用户输入）
     * @param params 函数的额外参数
     * @return 当前构建器以支持链式调用
     * @throws IllegalArgumentException 如果 field、functionName 或 params 为 null，或 functionName 包含非法字符
     */
    @SuppressWarnings("unchecked")
    default SELF func(SFunction<E, ?> field, String functionName, Object... params) {
        if (field == null) {
            throw new IllegalArgumentException("field must not be null");
        }
        if (functionName == null || functionName.isEmpty()) {
            throw new IllegalArgumentException("functionName must not be null or empty");
        }
        if (!SAFE_FIELD_NAME_PATTERN.matcher(functionName).matches()) {
            throw new IllegalArgumentException("functionName contains invalid characters: " + functionName
                + ". Only alphanumeric characters and underscores are allowed.");
        }
        if (params == null) {
            throw new IllegalArgumentException("params must not be null");
        }
        String name = resolveProperty(field);
        Object[] allParams = new Object[params.length + 1];
        allParams[0] = name;
        System.arraycopy(params, 0, allParams, 1, params.length);
        // 使用 FuncNode.of() 工厂方法进行安全白名单校验，
        // 而非通过直接构造调用绕过校验。
        // FuncNode.of() 内部处理白名单检查和日志记录。
        conditions().add(ConditionNode.FuncNode.of(functionName, allParams));
        return self();
    }

    // ---- 条件便捷方法 ----
}
