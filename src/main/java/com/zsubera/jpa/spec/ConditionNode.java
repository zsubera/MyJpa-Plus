package com.zsubera.jpa.spec;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;

/**
 * Sealed hierarchy of condition node types used by {@link QuerySpec},
 * {@link ConditionBuilder}, and related classes to build deferred
 * {@link jakarta.persistence.criteria.Predicate} trees.
 * <p>
 * Each node represents a single condition or structural element
 * (e.g., simple comparison, JOIN, OR group, subquery, etc.) in the
 * query condition tree, which is resolved at query execution time.
 */
public sealed interface ConditionNode
        permits ConditionNode.SimpleNode, ConditionNode.JoinNode, ConditionNode.OrNode,
        ConditionNode.AndNode, ConditionNode.MultiLikeNode, ConditionNode.CollectionNode,
        ConditionNode.ExistsNode, ConditionNode.RawNode, ConditionNode.NegateNode {

    // ---- Operation enums ----

    /** Comparison operators for field-value conditions. */
    enum Op {EQ, NE, GT, GE, LT, LE, LIKE, NOT_LIKE, IN, NOT_IN, BETWEEN, NOT_BETWEEN, IS_NULL, IS_NOT_NULL, EQ_IGNORE_CASE, LIKE_IGNORE_CASE}

    /** Join types used in JOIN nodes. */
    enum JoinType {INNER, LEFT, FETCH, LEFT_FETCH}

    /** Collection operations for to-many association checks. */
    enum CollectionOp {IS_EMPTY, IS_NOT_EMPTY}

    // ---- Node types ----

    /** A single field-value comparison condition. */
    final class SimpleNode implements ConditionNode {
        public final String fieldName;
        public final Object value;
        public final Op op;

        public SimpleNode(String fieldName, Object value, Op op) {
            this.fieldName = fieldName;
            this.value = value;
            this.op = op;
        }

        @Override
        public String toString() {
            return "SimpleNode[" + fieldName + " " + op + " " + value + "]";
        }
    }

    /** A JOIN or FETCH JOIN with inner conditions. */
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

    /** An OR group of conditions. */
    final class OrNode implements ConditionNode {
        public final List<ConditionNode> nodes = new ArrayList<>();

        @Override
        public String toString() {
            return "OrNode" + nodes;
        }
    }

    /** An AND group of conditions. */
    final class AndNode implements ConditionNode {
        public final List<ConditionNode> nodes = new ArrayList<>();

        @Override
        public String toString() {
            return "AndNode" + nodes;
        }
    }

    /** A multi-field LIKE search (keyword matched against multiple fields with OR). */
    final class MultiLikeNode implements ConditionNode {
        public final String keyword;
        public final String[] fieldNames;

        public MultiLikeNode(String keyword, String[] fieldNames) {
            this.keyword = keyword;
            this.fieldNames = fieldNames;
        }
    }

    /** A collection IS_EMPTY or IS_NOT_EMPTY check. */
    final class CollectionNode implements ConditionNode {
        public final String fieldName;
        public final CollectionOp op;

        public CollectionNode(String fieldName, CollectionOp op) {
            this.fieldName = fieldName;
            this.op = op;
        }
    }

    /** An EXISTS or NOT EXISTS correlated subquery. */
    final class ExistsNode<S> implements ConditionNode {
        public final Class<S> subEntity;
        public final Consumer<SubQuerySpec<S>> config;
        public final boolean negate;

        public ExistsNode(Class<S> subEntity, Consumer<SubQuerySpec<S>> config, boolean negate) {
            this.subEntity = subEntity;
            this.config = config;
            this.negate = negate;
        }
    }

    /** A raw predicate function (escape hatch for complex conditions). */
    final class RawNode implements ConditionNode {
        public final BiFunction<jakarta.persistence.criteria.Path<?>, CriteriaBuilder, Predicate> fn;

        public RawNode(BiFunction<jakarta.persistence.criteria.Path<?>, CriteriaBuilder, Predicate> fn) {
            this.fn = fn;
        }
    }

    /** A negated group node: NOT(inner conditions). */
    final class NegateNode implements ConditionNode {
        public final ConditionNode inner;

        public NegateNode(ConditionNode inner) {
            this.inner = inner;
        }
    }

    /** Order node for ORDER BY clauses. */
    final class OrderNode {
        public final String fieldName;
        public final boolean asc;

        public OrderNode(String fieldName, boolean asc) {
            this.fieldName = fieldName;
            this.asc = asc;
        }
    }
}
