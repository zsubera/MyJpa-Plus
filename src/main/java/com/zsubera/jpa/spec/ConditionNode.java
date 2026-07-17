package com.zsubera.jpa.spec;

import com.zsubera.jpa.util.InClauseBuilder;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 条件节点类型的密封层次结构，由 {@link QuerySpec}、{@link ConditionBuilder} 及相关类使用，
 * 用于构建延迟执行的 {@link jakarta.persistence.criteria.Predicate} 树。
 *
 * <p>
 * 每个节点表示查询条件树中的一个条件或结构元素（例如，简单比较、JOIN、OR 组、子查询等），
 * 在查询执行时进行解析。
 *
 * <h3>扩展指南</h3>
 * <p>
 * 本接口使用 Java sealed 机制限制实现类型。如需新增节点类型：
 * <ol>
 * <li>在本接口中添加新的 {@code final class} 实现（必须为 {@code final}）</li>
 * <li>在此接口的 {@code permits} 子句中添加新类</li>
 * <li>在 {@link NodeResolver} 的 {@code STRATEGIES} 映射中添加解析策略</li>
 * <li>在 {@link ConditionBuilder} 中添加对应的构建方法</li>
 * </ol>
 *
 * <p>
 * <strong>新增运算符（非节点类型）</strong>更简单——只需在 {@link Op} 枚举中添加新值，
 * 在 {@link ConditionBuilder} 中添加 default 方法，无需修改 {@link NodeResolver}。
 *
 * @see QuerySpec
 * @see ConditionBuilder
 * @see NodeResolver
 */
public sealed interface ConditionNode permits ConditionNode.SimpleNode, ConditionNode.JoinNode, ConditionNode.OrNode,
    ConditionNode.AndNode, ConditionNode.MultiLikeNode, ConditionNode.CollectionNode, ConditionNode.ExistsNode,
    ConditionNode.InSubQueryNode, ConditionNode.RawNode, ConditionNode.NegateNode, ConditionNode.FuncNode {

    /** 安全审计警告日志。 */
    Logger SECURITY_LOG = LoggerFactory.getLogger("com.zsubera.jpa.security");

    // ---- 操作枚举 ----

    /**
     * 字段-值条件的比较运算符。
     *
     * <p>
     * 每个枚举值通过 {@link #resolve(Path, String, Object, char, CriteriaBuilder)} 提供统一的谓词构建逻辑。
     * 新增运算符时，只需：
     * <ol>
     * <li>在此枚举中添加新值</li>
     * <li>在此枚举的 {@code resolve()} 方法中添加对应的 case</li>
     * <li>在 {@link ConditionBuilder} 中添加 default 方法</li>
     * <li>在 {@link ConditionalMethods} 中添加抽象方法声明</li>
     * </ol>
     * 无需修改 {@link PredicateHelper}、{@link NodeResolver} 或 {@link com.zsubera.jpa.spec.BulkConditionSupport}。
     */
    enum Op {
        EQ, NE, GT, GE, LT, LE, LIKE, NOT_LIKE, IN, NOT_IN, BETWEEN, NOT_BETWEEN, IS_NULL, IS_NOT_NULL, EQ_IGNORE_CASE,
        NE_IGNORE_CASE, LIKE_IGNORE_CASE;

        private static final char LIKE_ESCAPE = PredicateHelper.LIKE_ESCAPE_CHAR;

        /**
         * 将此运算符解析为 JPA {@link Predicate}。
         *
         * <p>
         * 此方法是所有条件构建路径（查询构建、批量操作、投影查询）的统一谓词构建入口。
         * 新增 {@link Op} 枚举值时，只需在此方法中添加对应的 case 即可。
         *
         * @param path 实体路径（Root 或 Join）
         * @param fieldName 字段名
         * @param value 比较值（可以为 null，由具体 Op 决定 null 处理策略）
         * @param escapeChar LIKE 转义字符（非 LIKE 类操作忽略此参数）
         * @param cb CriteriaBuilder 实例
         * @return 构建的 Predicate
         * @throws IllegalArgumentException 如果值类型不匹配或参数非法
         */
        @SuppressWarnings({"unchecked", "rawtypes"})
        public Predicate resolve(Path<?> path, String fieldName, Object value, char escapeChar, CriteriaBuilder cb) {
            // ponytail: 预计算 fieldPath，避免 PredicateHelper 中重复调用 path.get(fieldName)
            Path<?> fieldPath = path.get(fieldName);
            switch (this) {
                case EQ:
                    return value == null ? cb.isNull(fieldPath) : cb.equal(fieldPath, value);
                case NE:
                    return value == null ? cb.isNotNull(fieldPath) : cb.notEqual(fieldPath, value);
                case GT:
                    if (value == null) {
                        throw new IllegalArgumentException("GT operator requires non-null value");
                    }
                    if (!(value instanceof Comparable)) {
                        throw new IllegalArgumentException(
                            "GT operator requires Comparable value, got " + value.getClass().getName());
                    }
                    return cb.greaterThan((Path<Comparable>)fieldPath, (Comparable)value);
                case GE:
                    if (value == null) {
                        throw new IllegalArgumentException("GE operator requires non-null value");
                    }
                    if (!(value instanceof Comparable)) {
                        throw new IllegalArgumentException(
                            "GE operator requires Comparable value, got " + value.getClass().getName());
                    }
                    return cb.greaterThanOrEqualTo((Path<Comparable>)fieldPath, (Comparable)value);
                case LT:
                    if (value == null) {
                        throw new IllegalArgumentException("LT operator requires non-null value");
                    }
                    if (!(value instanceof Comparable)) {
                        throw new IllegalArgumentException(
                            "LT operator requires Comparable value, got " + value.getClass().getName());
                    }
                    return cb.lessThan((Path<Comparable>)fieldPath, (Comparable)value);
                case LE:
                    if (value == null) {
                        throw new IllegalArgumentException("LE operator requires non-null value");
                    }
                    if (!(value instanceof Comparable)) {
                        throw new IllegalArgumentException(
                            "LE operator requires Comparable value, got " + value.getClass().getName());
                    }
                    return cb.lessThanOrEqualTo((Path<Comparable>)fieldPath, (Comparable)value);
                case LIKE:
                    if (value == null) {
                        throw new IllegalArgumentException("LIKE operator requires non-null value");
                    }
                    if (escapeChar != '\0') {
                        return cb.like(fieldPath.as(String.class), (String)value, escapeChar);
                    }
                    return cb.like(fieldPath.as(String.class), (String)value);
                case NOT_LIKE:
                    if (value == null) {
                        throw new IllegalArgumentException("NOT_LIKE operator requires non-null value");
                    }
                    if (escapeChar != '\0') {
                        return cb.not(cb.like(fieldPath.as(String.class), (String)value, escapeChar));
                    }
                    return cb.not(cb.like(fieldPath.as(String.class), (String)value));
                case EQ_IGNORE_CASE:
                    return cb.equal(cb.lower(fieldPath.as(String.class)),
                        ((String)value).toLowerCase(java.util.Locale.ROOT));
                case NE_IGNORE_CASE:
                    return cb.notEqual(cb.lower(fieldPath.as(String.class)),
                        ((String)value).toLowerCase(java.util.Locale.ROOT));
                case LIKE_IGNORE_CASE:
                    if (value == null) {
                        throw new IllegalArgumentException("LIKE_IGNORE_CASE operator requires non-null value");
                    }
                    char esc = escapeChar != '\0' ? escapeChar : LIKE_ESCAPE;
                    return cb.like(cb.lower(fieldPath.as(String.class)),
                        ((String)value).toLowerCase(java.util.Locale.ROOT), esc);
                case IS_NULL:
                    return cb.isNull(fieldPath);
                case IS_NOT_NULL:
                    return cb.isNotNull(fieldPath);
                case IN:
                    return resolveInOrNotIn(fieldPath, value, cb, false);
                case NOT_IN:
                    return resolveInOrNotIn(fieldPath, value, cb, true);
                case BETWEEN:
                    return resolveBetweenOrNotBetween(path, fieldName, value, cb, false);
                case NOT_BETWEEN:
                    return resolveBetweenOrNotBetween(path, fieldName, value, cb, true);
                default:
                    throw new IllegalArgumentException("Unhandled Op: " + this);
            }
        }

        private static Predicate resolveInOrNotIn(Path<?> fieldPath, Object value, CriteriaBuilder cb, boolean negate) {
            if (value == null) {
                throw new IllegalArgumentException(
                    (negate ? "NOT_IN" : "IN") + " operator requires non-null value, got null");
            }
            if (value instanceof Collection<?> col) {
                if (col.isEmpty()) {
                    return negate ? cb.conjunction() : cb.disjunction();
                }
                return negate ? InClauseBuilder.notIn(cb, fieldPath, col) : InClauseBuilder.in(cb, fieldPath, col);
            }
            if (value.getClass().isArray()) {
                Object[] arr;
                if (value instanceof Object[] objArr) {
                    arr = objArr;
                } else {
                    int len = java.lang.reflect.Array.getLength(value);
                    arr = new Object[len];
                    for (int i = 0; i < len; i++) {
                        arr[i] = java.lang.reflect.Array.get(value, i);
                    }
                }
                if (arr.length == 0) {
                    return negate ? cb.conjunction() : cb.disjunction();
                }
                return negate ? InClauseBuilder.notIn(cb, fieldPath, arr) : InClauseBuilder.in(cb, fieldPath, arr);
            }
            throw new IllegalArgumentException((negate ? "NOT_IN" : "IN")
                + " operator requires Collection or array, got: " + value.getClass().getName());
        }

        private static Predicate resolveBetweenOrNotBetween(Path<?> path, String fieldName, Object value,
            CriteriaBuilder cb, boolean negate) {
            if (value == null) {
                throw new IllegalArgumentException(
                    (negate ? "NOT_BETWEEN" : "BETWEEN") + " operator requires non-null value");
            }
            if (!(value instanceof Comparable<?>[])) {
                throw new IllegalArgumentException((negate ? "NOT_BETWEEN" : "BETWEEN")
                    + " requires a Comparable[] value, got: " + value.getClass().getName());
            }
            Comparable<?>[] range = (Comparable<?>[])value;
            if (range.length != 2) {
                throw new IllegalArgumentException(
                    (negate ? "NOT_BETWEEN" : "BETWEEN") + " requires exactly 2 values, got " + range.length);
            }
            return negate ? PredicateHelper.notBetween(path, fieldName, range[0], range[1], cb)
                : PredicateHelper.between(path, fieldName, range[0], range[1], cb);
        }
    }

    /** JOIN 节点中使用的连接类型。 */
    enum JoinType {
        INNER, LEFT, FETCH, LEFT_FETCH
    }

    /** 用于多值关联检查的集合操作。 */
    enum CollectionOp {
        IS_EMPTY, IS_NOT_EMPTY
    }

    // ---- 节点类型 ----

    /**
     * 单个字段-值比较条件。
     *
     * <p>
     * <strong>实现说明：</strong>使用 {@code final class} 而非 {@code record} 是为了支持两阶段构造函数（无 escapeChar 的默认构造函数） 和自定义
     * {@code toString()} 以掩码敏感值。如需迁移至 record，需评估对所有直接字段访问的兼容性影响。
     */
    final class SimpleNode implements ConditionNode {
        final String fieldName;
        /** 条件值。对于数组类型（Object[]、Comparable[]），构造时会进行防御性拷贝。 */
        final Object value;
        final Op op;
        final char escapeChar;

        public SimpleNode(String fieldName, Object value, Op op) {
            this(fieldName, value, op, '\0');
        }

        public SimpleNode(String fieldName, Object value, Op op, char escapeChar) {
            if (fieldName == null) {
                throw new IllegalArgumentException("fieldName must not be null");
            }
            if (op == null) {
                throw new IllegalArgumentException("op must not be null");
            }
            this.fieldName = fieldName;
            // 防御性拷贝：数组和集合是可变的，拷贝防止外部修改影响内部状态
            if (value instanceof Comparable<?>[] arr) {
                this.value = arr.clone();
            } else if (value instanceof Object[] arr) {
                this.value = arr.clone();
            } else if (value instanceof Collection<?> col) {
                this.value = new ArrayList<>(col);
            } else {
                this.value = value;
            }
            this.op = op;
            this.escapeChar = escapeChar;
        }

        @Override
        public String toString() {
            String maskedValue;
            if (value == null) {
                maskedValue = "null";
            } else if (value instanceof Object[] arr) {
                maskedValue = "ARRAY[" + arr.length + " items]";
            } else if (value instanceof Collection<?> col) {
                maskedValue = "IN[" + col.size() + " items]";
            } else if (value instanceof String str) {
                // 字符串完全掩码：防止密码、token 等敏感数据泄露到日志系统
                maskedValue = "***(" + str.length() + " chars)";
            } else {
                maskedValue = value.getClass().getSimpleName() + "[***]";
            }
            return "SimpleNode[" + fieldName + " " + op + " " + maskedValue + "]";
        }
    }

    /** 带有内部条件的 JOIN 或 FETCH JOIN。 */
    final class JoinNode implements ConditionNode {
        final String fieldName;
        final JoinType joinType;
        /**
         * 内部条件列表。
         *
         * <p>
         * <strong>设计说明：</strong>此字段为包级私有可变列表，允许 {@link com.zsubera.jpa.spec.JoinGroup} 和
         * {@link com.zsubera.jpa.spec.QuerySpec} 直接操作。 这是有意的设计决策，因为：
         * <ul>
         * <li>包内所有使用方都在同一模块中，访问受控</li>
         * <li>使用不可变列表会导致每次添加条件时创建新列表，增加 GC 压力</li>
         * <li>外部用户无法访问此字段（包级私有）</li>
         * </ul>
         */
        // ponytail: JOIN 内部条件通常较少，使用较小的初始容量
        final List<ConditionNode> innerConditions = new ArrayList<>(2);

        public JoinNode(String fieldName, JoinType joinType) {
            if (fieldName == null) {
                throw new IllegalArgumentException("fieldName must not be null");
            }
            if (joinType == null) {
                throw new IllegalArgumentException("joinType must not be null");
            }
            this.fieldName = fieldName;
            this.joinType = joinType;
        }

        @Override
        public String toString() {
            return "JoinNode[" + joinType + " " + fieldName + " conditions=" + innerConditions + "]";
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (!(o instanceof JoinNode joinNode))
                return false;
            return fieldName.equals(joinNode.fieldName) && joinType == joinNode.joinType;
        }

        @Override
        public int hashCode() {
            return 31 * fieldName.hashCode() + joinType.hashCode();
        }
    }

    /** 条件的 OR 组。 */
    final class OrNode implements ConditionNode {
        // ponytail: 大多数 OR/AND 组只有 2-3 个条件，使用较小的初始容量减少内存浪费
        final List<ConditionNode> nodes = new ArrayList<>(4);

        /** 向 OR 组添加一个条件节点。 */
        void addNode(ConditionNode node) {
            if (node == null) {
                throw new IllegalArgumentException("node must not be null");
            }
            nodes.add(node);
        }

        /** 返回 OR 组中的子条件列表（不可变视图）。 */
        public List<ConditionNode> nodes() {
            return java.util.Collections.unmodifiableList(nodes);
        }

        @Override
        public String toString() {
            return "OrNode" + nodes;
        }
    }

    /** 条件的 AND 组。 */
    final class AndNode implements ConditionNode {
        // ponytail: 大多数 OR/AND 组只有 2-3 个条件，使用较小的初始容量减少内存浪费
        final List<ConditionNode> nodes = new ArrayList<>(4);

        /** 向 AND 组添加一个条件节点。 */
        void addNode(ConditionNode node) {
            if (node == null) {
                throw new IllegalArgumentException("node must not be null");
            }
            nodes.add(node);
        }

        /** 返回 AND 组中的子条件列表（不可变视图）。 */
        public List<ConditionNode> nodes() {
            return java.util.Collections.unmodifiableList(nodes);
        }

        @Override
        public String toString() {
            return "AndNode" + nodes;
        }
    }

    /** 多字段 LIKE 搜索（关键字通过 OR 与多个字段匹配）。 */
    final class MultiLikeNode implements ConditionNode {
        final String keyword;
        final String[] fieldNames;

        public MultiLikeNode(String keyword, String[] fieldNames) {
            if (keyword == null) {
                throw new IllegalArgumentException("keyword must not be null");
            }
            if (fieldNames == null) {
                throw new IllegalArgumentException("fieldNames must not be null");
            }
            if (fieldNames.length == 0) {
                throw new IllegalArgumentException("fieldNames must not be empty");
            }
            this.keyword = keyword;
            this.fieldNames = fieldNames.clone();
        }

        @Override
        public String toString() {
            // 掩码关键字，防止敏感数据泄露到日志
            return "MultiLikeNode[keyword='" + "***(" + keyword.length() + " chars)" + "', fields="
                + java.util.Arrays.toString(fieldNames) + "]";
        }
    }

    /** 集合 IS_EMPTY 或 IS_NOT_EMPTY 检查。 */
    final class CollectionNode implements ConditionNode {
        final String fieldName;
        final CollectionOp op;

        public CollectionNode(String fieldName, CollectionOp op) {
            if (fieldName == null) {
                throw new IllegalArgumentException("fieldName must not be null");
            }
            if (op == null) {
                throw new IllegalArgumentException("op must not be null");
            }
            this.fieldName = fieldName;
            this.op = op;
        }

        /** 返回字段名。 */
        public String fieldName() {
            return fieldName;
        }

        /** 返回集合操作类型。 */
        public CollectionOp op() {
            return op;
        }

        @Override
        public String toString() {
            return "CollectionNode[" + fieldName + " " + op + "]";
        }
    }

    /** EXISTS 或 NOT EXISTS 关联子查询。 */
    final class ExistsNode<S> implements ConditionNode {
        final Class<S> subEntity;
        final Consumer<SubQuerySpec<S>> config;
        final boolean negate;

        public ExistsNode(Class<S> subEntity, Consumer<SubQuerySpec<S>> config, boolean negate) {
            if (subEntity == null) {
                throw new IllegalArgumentException("subEntity must not be null");
            }
            if (config == null) {
                throw new IllegalArgumentException("config must not be null");
            }
            this.subEntity = subEntity;
            this.config = config;
            this.negate = negate;
        }

        @Override
        public String toString() {
            return "ExistsNode[" + (negate ? "NOT " : "") + subEntity.getSimpleName() + "]";
        }
    }

    /**
     * IN 子查询节点：{@code field IN (SELECT ...)}。
     *
     * <p>
     * 支持类型安全的 IN 子查询，例如：
     *
     * <pre>{@code
     * qs.inSubQuery(User::getDepartmentId, Department.class,
     *     sub -> sub.eq(Department::getActive, true).select(Department::getId));
     * }</pre>
     *
     * <p>
     * 生成：{@code user.department_id IN (SELECT d.id FROM department d WHERE d.active = true)}
     */
    final class InSubQueryNode<S> implements ConditionNode {
        final String outerFieldName;
        final Class<S> subEntity;
        final Consumer<SubQuerySpec<S>> config;
        final boolean negate;

        public InSubQueryNode(String outerFieldName, Class<S> subEntity, Consumer<SubQuerySpec<S>> config,
            boolean negate) {
            if (outerFieldName == null) {
                throw new IllegalArgumentException("outerFieldName must not be null");
            }
            if (subEntity == null) {
                throw new IllegalArgumentException("subEntity must not be null");
            }
            if (config == null) {
                throw new IllegalArgumentException("config must not be null");
            }
            this.outerFieldName = outerFieldName;
            this.subEntity = subEntity;
            this.config = config;
            this.negate = negate;
        }

        @Override
        public String toString() {
            return "InSubQueryNode[" + (negate ? "NOT " : "") + outerFieldName + " IN (SELECT FROM "
                + subEntity.getSimpleName() + ")]";
        }
    }

    /** 供框架内部使用的谓词函数节点（例如 SoftDeleteHelper）。 */
    final class RawNode implements ConditionNode {
        final BiFunction<jakarta.persistence.criteria.Path<?>, CriteriaBuilder, Predicate> fn;

        RawNode(BiFunction<jakarta.persistence.criteria.Path<?>, CriteriaBuilder, Predicate> fn) {
            if (fn == null) {
                throw new IllegalArgumentException("Predicate function must not be null");
            }
            this.fn = fn;
        }

        @Override
        public String toString() {
            return "RawNode[fn=" + fn.getClass().getName() + "]";
        }
    }

    static ConditionNode
        ofInternalPredicate(BiFunction<jakarta.persistence.criteria.Path<?>, CriteriaBuilder, Predicate> fn) {
        if (fn == null) {
            throw new IllegalArgumentException("Predicate function must not be null");
        }
        return new RawNode(fn);
    }

    /** 取反组节点：NOT（内部条件）。 */
    final class NegateNode implements ConditionNode {
        final ConditionNode inner;

        public NegateNode(ConditionNode inner) {
            if (inner == null) {
                throw new IllegalArgumentException("inner must not be null");
            }
            this.inner = inner;
        }

        /** 返回被取反的内部条件。 */
        public ConditionNode inner() {
            return inner;
        }

        @Override
        public String toString() {
            return "NegateNode[" + inner + "]";
        }
    }

    /**
     * 数据库函数调用节点：{@code functionName(field, params...)}。
     *
     * <p>
     * 用于调用数据库特定函数进行条件判断。第一个参数为字段名，后续参数为函数的额外参数。
     *
     * <p>
     * <strong>安全说明：</strong>构造函数为包级私有，外部代码必须通过 {@link #of(String, Object[])} 工厂方法创建实例， 该方法会验证函数名是否在白名单中。
     */
    final class FuncNode implements ConditionNode {
        final String functionName;
        final Object[] params;

        /**
         * 包级私有构造函数。由 {@link #of(String, Object[])} 工厂方法调用，
         * 白名单验证由 of() 完成，构造函数仅进行 null 检查和防御性拷贝。
         *
         * @param functionName 数据库函数名
         * @param params 函数参数
         */
        FuncNode(String functionName, Object[] params) {
            if (functionName == null) {
                throw new IllegalArgumentException("functionName must not be null");
            }
            if (params == null) {
                throw new IllegalArgumentException("params must not be null");
            }
            this.functionName = functionName;
            this.params = params.clone();
        }

        /**
         * 创建 FuncNode 实例的工厂方法，验证函数名是否在安全白名单中。
         *
         * @param functionName 数据库函数名
         * @param params 函数参数
         * @return 新的 FuncNode 实例
         * @throws IllegalArgumentException 如果 functionName 或 params 为 null
         * @throws com.zsubera.jpa.exception.MyJpaPlusException 如果函数名不在白名单中且白名单强制执行已启用
         */
        public static FuncNode of(String functionName, Object[] params) {
            if (functionName == null) {
                throw new IllegalArgumentException("functionName must not be null");
            }
            if (params == null) {
                throw new IllegalArgumentException("params must not be null");
            }
            // 验证函数名是否在白名单中，防止 SQL 注入，同时确保是布尔函数
            // 使用冻结快照（AtomicReference）进行无锁线程安全检查
            String upperName = functionName.toUpperCase(java.util.Locale.ROOT);
            if (!ConditionBuilder.SAFE_FUNCTION_NAMES.contains(upperName)
                && !FunctionWhitelist.containsSafeFunction(upperName)) {
                String msg =
                    "Function not in whitelist: '" + functionName + "'. " + "Only whitelisted functions are allowed. "
                        + "Use myjpa-plus.query.extra-safe-functions to add custom functions.";
                throw new com.zsubera.jpa.exception.SecurityViolationException(msg);
            }
            if (!ConditionBuilder.BOOLEAN_FUNCTION_NAMES.contains(upperName)
                && !FunctionWhitelist.containsBooleanFunction(upperName)) {
                String msg = "Function must be a boolean-returning function: '" + functionName + "'. "
                    + "Only boolean functions like IF, DECODE, COALESCE, NULLIF, NVL, JSONB_EXISTS, ST_CONTAINS are allowed in func(). "
                    + "Use myjpa-plus.query.extra-boolean-functions to add custom boolean functions.";
                throw new com.zsubera.jpa.exception.SecurityViolationException(msg);
            }
            return new FuncNode(functionName, params);
        }

        @Override
        public String toString() {
            // 掩码函数参数，防止敏感数据泄露到日志
            StringBuilder sb = new StringBuilder("FuncNode[");
            sb.append(functionName).append("(");
            for (int i = 0; i < params.length; i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                Object param = params[i];
                if (param == null) {
                    sb.append("null");
                } else if (param instanceof String) {
                    sb.append("String[***]");
                } else {
                    sb.append(param.getClass().getSimpleName()).append("[***]");
                }
            }
            sb.append(")]");
            return sb.toString();
        }
    }

    /**
     * ORDER BY 子句的排序节点。
     *
     * <p>
     * <strong>注意：</strong>此类定义在 {@code ConditionNode} 内部以便于组织，但不实现 {@code ConditionNode} 接口， 因为 ORDER BY 不是查询条件（WHERE
     * clause）的一部分。排序逻辑由 {@link QuerySpec} 中的 {@code orderNodes} 列表独立管理。
     */
    final class OrderNode {
        final String fieldName;
        final boolean asc;

        /**
         * 创建排序节点。
         *
         * @param fieldName 排序字段名
         * @param asc 是否升序
         */
        public OrderNode(String fieldName, boolean asc) {
            if (fieldName == null) {
                throw new IllegalArgumentException("fieldName must not be null");
            }
            this.fieldName = fieldName;
            this.asc = asc;
        }

        @Override
        public String toString() {
            return "OrderNode[" + fieldName + (asc ? " ASC" : " DESC") + "]";
        }
    }
}
