package com.zsubera.jpa.spec;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;

/**
 * 条件节点类型的密封层次结构，由 {@link QuerySpec}、{@link ConditionBuilder} 及相关类使用， 用于构建延迟执行的
 * {@link jakarta.persistence.criteria.Predicate} 树。
 *
 * <p>
 * 每个节点表示查询条件树中的一个条件或结构元素（例如，简单比较、JOIN、OR 组、子查询等）， 在查询执行时进行解析。
 */
public sealed interface ConditionNode permits ConditionNode.SimpleNode, ConditionNode.JoinNode, ConditionNode.OrNode,
    ConditionNode.AndNode, ConditionNode.MultiLikeNode, ConditionNode.CollectionNode, ConditionNode.ExistsNode,
    ConditionNode.InSubQueryNode, ConditionNode.RawNode, ConditionNode.NegateNode {

    // ---- Operation enums ----

    /** 字段-值条件的比较运算符。 */
    enum Op {
        EQ, NE, GT, GE, LT, LE, LIKE, NOT_LIKE, IN, NOT_IN, BETWEEN, NOT_BETWEEN, IS_NULL, IS_NOT_NULL, EQ_IGNORE_CASE,
        LIKE_IGNORE_CASE
    }

    /** JOIN 节点中使用的连接类型。 */
    enum JoinType {
        INNER, LEFT, FETCH, LEFT_FETCH
    }

    /** 用于多值关联检查的集合操作。 */
    enum CollectionOp {
        IS_EMPTY, IS_NOT_EMPTY
    }

    // ---- Node types ----

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
            // 防御性拷贝：数组是可变的，拷贝防止外部修改影响内部状态
            if (value instanceof Object[] arr) {
                this.value = arr.clone();
            } else if (value instanceof Comparable<?>[] arr) {
                this.value = arr.clone();
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
            } else if (value instanceof String) {
                // 完全掩码：防止密码、token 等敏感数据泄露到日志系统
                maskedValue = "***";
            } else if (value instanceof Number) {
                // 数字类型掩码：防止业务数据泄露到日志
                maskedValue = "N***";
            } else {
                maskedValue = value.getClass().getSimpleName() + "***";
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

        @Override
        public String toString() {
            return "OrNode" + nodes;
        }
    }

    /** 条件的 AND 组。 */
    final class AndNode implements ConditionNode {
        final List<ConditionNode> nodes = new ArrayList<>();

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
     */
    final class RawNode implements ConditionNode {
        final BiFunction<jakarta.persistence.criteria.Path<?>, CriteriaBuilder, Predicate> fn;

        /**
         * 创建原始谓词节点。
         *
         * <p>
         * <strong>安全警告：</strong>此构造函数仅用于内部实现，外部用户应使用类型安全的条件方法。
         *
         * @param fn 谓词函数
         * @throws IllegalArgumentException 如果 fn 为 null
         */
        public RawNode(BiFunction<jakarta.persistence.criteria.Path<?>, CriteriaBuilder, Predicate> fn) {
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

    /** 取反组节点：NOT（内部条件）。 */
    final class NegateNode implements ConditionNode {
        final ConditionNode inner;

        public NegateNode(ConditionNode inner) {
            if (inner == null) {
                throw new IllegalArgumentException("inner must not be null");
            }
            this.inner = inner;
        }

        @Override
        public String toString() {
            return "NegateNode[" + inner + "]";
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
