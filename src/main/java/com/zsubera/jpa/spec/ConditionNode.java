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
    ConditionNode.RawNode, ConditionNode.NegateNode {

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
        public final String fieldName;
        public final Object value;
        public final Op op;
        public final char escapeChar;

        public SimpleNode(String fieldName, Object value, Op op) {
            this(fieldName, value, op, '\0');
        }

        public SimpleNode(String fieldName, Object value, Op op, char escapeChar) {
            this.fieldName = fieldName;
            this.value = value;
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
            } else if (value instanceof String s && s.length() > 4) {
                maskedValue = s.substring(0, 2) + "***" + s.substring(s.length() - 2);
            } else if (value instanceof String) {
                maskedValue = "***";
            } else {
                maskedValue = value.toString();
            }
            return "SimpleNode[" + fieldName + " " + op + " " + maskedValue + "]";
        }
    }

    /** 带有内部条件的 JOIN 或 FETCH JOIN。 */
    final class JoinNode implements ConditionNode {
        public final String fieldName;
        public final JoinType joinType;
        public final List<ConditionNode> innerConditions = new ArrayList<>();

        public JoinNode(String fieldName, JoinType joinType) {
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
        public final List<ConditionNode> nodes = new ArrayList<>();

        @Override
        public String toString() {
            return "OrNode" + nodes;
        }
    }

    /** 条件的 AND 组。 */
    final class AndNode implements ConditionNode {
        public final List<ConditionNode> nodes = new ArrayList<>();

        @Override
        public String toString() {
            return "AndNode" + nodes;
        }
    }

    /** 多字段 LIKE 搜索（关键字通过 OR 与多个字段匹配）。 */
    final class MultiLikeNode implements ConditionNode {
        public final String keyword;
        public final String[] fieldNames;

        public MultiLikeNode(String keyword, String[] fieldNames) {
            this.keyword = keyword;
            this.fieldNames = fieldNames;
        }

        @Override
        public String toString() {
            return "MultiLikeNode[keyword='" + keyword + "', fields=" + java.util.Arrays.toString(fieldNames) + "]";
        }
    }

    /** 集合 IS_EMPTY 或 IS_NOT_EMPTY 检查。 */
    final class CollectionNode implements ConditionNode {
        public final String fieldName;
        public final CollectionOp op;

        public CollectionNode(String fieldName, CollectionOp op) {
            this.fieldName = fieldName;
            this.op = op;
        }

        @Override
        public String toString() {
            return "CollectionNode[" + fieldName + " " + op + "]";
        }
    }

    /** EXISTS 或 NOT EXISTS 关联子查询。 */
    final class ExistsNode<S> implements ConditionNode {
        public final Class<S> subEntity;
        public final Consumer<SubQuerySpec<S>> config;
        public final boolean negate;

        public ExistsNode(Class<S> subEntity, Consumer<SubQuerySpec<S>> config, boolean negate) {
            this.subEntity = subEntity;
            this.config = config;
            this.negate = negate;
        }

        @Override
        public String toString() {
            return "ExistsNode[" + (negate ? "NOT " : "") + subEntity.getSimpleName() + "]";
        }
    }

    /** 原始谓词函数（复杂条件的应急方案）。 */
    final class RawNode implements ConditionNode {
        public final BiFunction<jakarta.persistence.criteria.Path<?>, CriteriaBuilder, Predicate> fn;

        public RawNode(BiFunction<jakarta.persistence.criteria.Path<?>, CriteriaBuilder, Predicate> fn) {
            this.fn = fn;
        }

        @Override
        public String toString() {
            return "RawNode[fn=" + fn.getClass().getName() + "]";
        }
    }

    /** 取反组节点：NOT（内部条件）。 */
    final class NegateNode implements ConditionNode {
        public final ConditionNode inner;

        public NegateNode(ConditionNode inner) {
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
     *
     * @param fieldName 排序字段名
     * @param asc 是否升序
     */
    final class OrderNode {
        public final String fieldName;
        public final boolean asc;

        public OrderNode(String fieldName, boolean asc) {
            this.fieldName = fieldName;
            this.asc = asc;
        }

        @Override
        public String toString() {
            return "OrderNode[" + fieldName + (asc ? " ASC" : " DESC") + "]";
        }
    }
}
