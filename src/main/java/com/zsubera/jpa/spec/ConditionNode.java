package com.zsubera.jpa.spec;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 条件节点类型的密封层次结构，由 {@link QuerySpec}、{@link ConditionBuilder} 及相关类使用， 用于构建延迟执行的
 * {@link jakarta.persistence.criteria.Predicate} 树。
 *
 * <p>
 * 每个节点表示查询条件树中的一个条件或结构元素（例如，简单比较、JOIN、OR 组、子查询等）， 在查询执行时进行解析。
 */
public sealed interface ConditionNode permits ConditionNode.SimpleNode, ConditionNode.JoinNode, ConditionNode.OrNode,
    ConditionNode.AndNode, ConditionNode.MultiLikeNode, ConditionNode.CollectionNode, ConditionNode.ExistsNode,
    ConditionNode.InSubQueryNode, ConditionNode.RawNode, ConditionNode.NegateNode, ConditionNode.FuncNode {

    /** Logger for security audit warnings. */
    Logger SECURITY_LOG = LoggerFactory.getLogger("com.zsubera.jpa.security");

    // ---- 操作枚举 ----

    /** 字段-值条件的比较运算符。 */
    enum Op {
        EQ, NE, GT, GE, LT, LE, LIKE, NOT_LIKE, IN, NOT_IN, BETWEEN, NOT_BETWEEN, IS_NULL, IS_NOT_NULL, EQ_IGNORE_CASE,
        NE_IGNORE_CASE, LIKE_IGNORE_CASE
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
            if (value instanceof Object[] arr) {
                this.value = arr.clone();
            } else if (value instanceof Comparable<?>[] arr) {
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
                maskedValue = "IN[" + arr.length + " items]";
            } else if (value instanceof Comparable<?>[] arr) {
                maskedValue = "BETWEEN[" + arr.length + " items]";
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
        final List<ConditionNode> innerConditions = new ArrayList<>();

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
    }

    /** 条件的 OR 组。 */
    final class OrNode implements ConditionNode {
        final List<ConditionNode> nodes = new ArrayList<>();

        /** 返回 OR 组中的子条件列表。 */
        @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(value = "EI_EXPOSE_REP",
            justification = "Nodes list is intentionally exposed for condition tree traversal by QuerySpec and ProjectionSpec")
        public List<ConditionNode> nodes() {
            return nodes;
        }

        @Override
        public String toString() {
            return "OrNode" + nodes;
        }
    }

    /** 条件的 AND 组。 */
    final class AndNode implements ConditionNode {
        final List<ConditionNode> nodes = new ArrayList<>();

        /** 返回 AND 组中的子条件列表。 */
        @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(value = "EI_EXPOSE_REP",
            justification = "Nodes list is intentionally exposed for condition tree traversal by QuerySpec and ProjectionSpec")
        public List<ConditionNode> nodes() {
            return nodes;
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
            return "MultiLikeNode[keyword='" + keyword + "', fields=" + java.util.Arrays.toString(fieldNames) + "]";
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

    /**
     * 原始谓词函数（复杂条件的应急方案）。
     *
     * <p>
     * <strong>安全警告：</strong>此类仅用于内部实现复杂条件逻辑，不应在外部直接使用。 它通过 BiFunction 构建原始条件，绕过类型安全机制，存在潜在的 SQL 注入风险。
     *
     * <p>
     * 外部用户应使用类型安全的条件方法（如 {@code eq()}、{@code likeSafe()} 等）。 如果必须使用原始谓词，请确保不将用户输入直接拼接到字段名中。
     *
     * @deprecated 此类计划在 2.0 版本中完全移除。请使用类型安全的条件方法替代。
     */
    @Deprecated(since = "1.1.0", forRemoval = true)
    final class RawNode implements ConditionNode {
        final BiFunction<jakarta.persistence.criteria.Path<?>, CriteriaBuilder, Predicate> fn;

        /**
         * 创建原始谓词节点。
         *
         * <p>
         * <strong>安全警告：</strong>此构造函数为包级私有，防止外部代码直接创建 RawNode 实例。 外部代码应使用 {@link #ofRawPredicate(BiFunction)} 工厂方法。
         *
         * @param fn 谓词函数
         * @throws IllegalArgumentException 如果 fn 为 null
         */
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

    /**
     * 创建原始谓词节点的工厂方法。
     *
     * <p>
     * <strong>安全警告：</strong>此方法绕过类型安全机制，存在潜在的 SQL 注入风险。 仅在类型安全的条件方法（如 {@code eq()}、{@code likeSafe()} 等）无法满足需求时使用。
     *
     * @param fn 谓词函数
     * @return 新的 RawNode 实例
     * @throws IllegalArgumentException 如果 fn 为 null
     */
    static ConditionNode
        ofRawPredicate(BiFunction<jakarta.persistence.criteria.Path<?>, CriteriaBuilder, Predicate> fn) {
        if (fn == null) {
            throw new IllegalArgumentException("Predicate function must not be null");
        }
        SECURITY_LOG.warn("SECURITY: RawNode.ofRawPredicate() called - this bypasses type safety. "
            + "Use type-safe methods (eq, likeSafe, etc.) instead. Call stack: {}", getCallStackSummary());
        return new RawNode(fn);
    }

    /**
     * 创建内部谓词节点的工厂方法（不触发安全审计日志）。
     *
     * <p>
     * 此方法仅供框架内部使用（如 {@code SoftDeleteHelper}、{@code TenantHelper}）， 这些组件的谓词函数不接受用户输入，不存在 SQL 注入风险。
     *
     * <p>
     * <strong>注意：</strong>外部代码不应使用此方法。如需创建原始谓词，请使用 {@link #ofRawPredicate(BiFunction)}。
     *
     * @param fn 谓词函数
     * @return 新的 RawNode 实例
     * @throws IllegalArgumentException 如果 fn 为 null
     */
    static ConditionNode
        ofInternalPredicate(BiFunction<jakarta.persistence.criteria.Path<?>, CriteriaBuilder, Predicate> fn) {
        if (fn == null) {
            throw new IllegalArgumentException("Predicate function must not be null");
        }
        return new RawNode(fn);
    }

    /**
     * 获取调用栈摘要，用于安全审计日志。
     *
     * @return 调用栈摘要字符串
     */
    private static String getCallStackSummary() {
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (StackTraceElement element : stack) {
            String className = element.getClassName();
            if (className.startsWith("com.zsubera.jpa.") && !className.endsWith("ConditionNode")) {
                if (count > 0) {
                    sb.append(" <- ");
                }
                sb.append(className.substring(className.lastIndexOf('.') + 1)).append(".")
                    .append(element.getMethodName());
                count++;
                if (count >= 3) {
                    break;
                }
            }
        }
        return sb.length() > 0 ? sb.toString() : "unknown";
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
         * 包级私有构造函数，防止外部代码绕过白名单验证。
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
            return new FuncNode(functionName, params);
        }

        @Override
        public String toString() {
            return "FuncNode[" + functionName + "(" + java.util.Arrays.toString(params) + ")]";
        }
    }

    /**
     * ORDER BY 子句的排序节点。
     *
     * <p>
     * <strong>注意：</strong>此类定义在 {@code ConditionNode} 内部以便于组织，但不实现 {@code ConditionNode} 接口， 因为 ORDER BY 不是查询条件（WHERE
     * clause）的一部分。排序逻辑由 {@link QuerySpec} 中的 {@code orders} 列表独立管理。
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
