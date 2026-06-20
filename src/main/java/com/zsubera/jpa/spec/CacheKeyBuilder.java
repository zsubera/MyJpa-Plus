package com.zsubera.jpa.spec;

import java.util.Collection;
import java.util.List;

/**
 * 从 {@link QuerySpec} 提取的缓存键生成逻辑。
 *
 * <p>
 * 将条件树序列化为唯一的缓存键字符串，用于 {@link com.zsubera.jpa.template.QueryCacheManager} 的查询缓存。
 * 缓存键包含参数值的哈希（而非明文），防止密码、token 等敏感数据泄露。
 *
 * <p>
 * 内部工具类——不建议应用代码直接使用。
 */
final class CacheKeyBuilder {

    private CacheKeyBuilder() {}

    /**
     * 生成包含实际参数值的缓存键。返回的字符串包含条件值的哈希，
     * 确保不同参数值的查询产生不同的缓存键。
     *
     * @param conditions 根条件节点列表
     * @param currentGroup 当前活动组（可能是根条件或嵌套组）
     * @param groupStack 组栈（用于检测嵌套 OR 组）
     * @param distinct 是否启用 DISTINCT
     * @param groupByFields GROUP BY 字段列表
     * @param havingConditions HAVING 条件列表
     * @param orderNodes 排序节点列表
     * @param queryTimeout 查询超时
     * @param lockMode 锁模式
     * @return 缓存键字符串
     */
    static String buildCacheKey(List<ConditionNode> conditions, List<ConditionNode> currentGroup,
        java.util.Deque<List<ConditionNode>> groupStack, boolean distinct, List<String> groupByFields,
        List<?> havingConditions, List<ConditionNode.OrderNode> orderNodes, Integer queryTimeout,
        jakarta.persistence.LockModeType lockMode) {

        StringBuilder sb = new StringBuilder("Q:");
        if (!groupStack.isEmpty()) {
            sb.append("ROOT(");
            for (ConditionNode node : conditions) {
                appendCacheKey(sb, node);
            }
            sb.append(")#NESTED(");
            for (ConditionNode node : currentGroup) {
                appendCacheKey(sb, node);
            }
            sb.append(")");
        } else {
            for (ConditionNode node : currentGroup) {
                appendCacheKey(sb, node);
            }
        }
        if (distinct) {
            sb.append("#DISTINCT");
        }
        if (!groupByFields.isEmpty()) {
            sb.append("#GROUPBY(");
            for (int i = 0; i < groupByFields.size(); i++) {
                if (i > 0) {
                    sb.append(",");
                }
                sb.append(groupByFields.get(i));
            }
            sb.append(")");
        }
        if (!havingConditions.isEmpty()) {
            sb.append("#HAVING(");
            for (int i = 0; i < havingConditions.size(); i++) {
                if (i > 0) {
                    sb.append(",");
                }
                sb.append(havingConditions.get(i).hashCode());
            }
            sb.append(")");
        }
        if (!orderNodes.isEmpty()) {
            sb.append("#ORDERBY(");
            for (int i = 0; i < orderNodes.size(); i++) {
                if (i > 0) {
                    sb.append(",");
                }
                ConditionNode.OrderNode node = orderNodes.get(i);
                sb.append(node.fieldName).append(node.asc ? "ASC" : "DESC");
            }
            sb.append(")");
        }
        if (queryTimeout != null) {
            sb.append("#TIMEOUT(").append(queryTimeout).append(")");
        }
        if (lockMode != null) {
            sb.append("#LOCK(").append(lockMode).append(")");
        }
        return sb.toString();
    }

    static void appendCacheKey(StringBuilder sb, ConditionNode node) {
        if (node instanceof ConditionNode.SimpleNode sn) {
            sb.append(sn.fieldName).append(sn.op);
            appendValue(sb, sn.value);
            sb.append(";");
        } else if (node instanceof ConditionNode.FuncNode fn) {
            sb.append("FUNC(").append(fn.functionName);
            for (Object p : fn.params) {
                sb.append(",");
                appendValue(sb, p);
            }
            sb.append(")");
        } else if (node instanceof ConditionNode.JoinNode jn) {
            sb.append("JOIN(").append(jn.fieldName).append(",").append(jn.joinType).append(",");
            for (ConditionNode inner : jn.innerConditions) {
                appendCacheKey(sb, inner);
            }
            sb.append(")");
        } else if (node instanceof ConditionNode.OrNode on) {
            sb.append("OR(");
            for (ConditionNode inner : on.nodes()) {
                appendCacheKey(sb, inner);
            }
            sb.append(")");
        } else if (node instanceof ConditionNode.AndNode an) {
            sb.append("AND(");
            for (ConditionNode inner : an.nodes()) {
                appendCacheKey(sb, inner);
            }
            sb.append(")");
        } else if (node instanceof ConditionNode.MultiLikeNode mln) {
            sb.append("MULTILIKE(").append(mln.keyword);
            for (String f : mln.fieldNames) {
                sb.append(",").append(f);
            }
            sb.append(")");
        } else if (node instanceof ConditionNode.CollectionNode cn) {
            sb.append("COLLECTION(").append(cn.fieldName).append(",").append(cn.op).append(")");
        } else if (node instanceof ConditionNode.ExistsNode<?> en) {
            sb.append(en.negate ? "NOTEXISTS(" : "EXISTS(");
            sb.append(en.subEntity.getSimpleName());
            sb.append(",condHash=").append(en.config.hashCode()).append(")");
        } else if (node instanceof ConditionNode.InSubQueryNode<?> isn) {
            sb.append(isn.negate ? "NOTINSUBQUERY(" : "INSUBQUERY(");
            sb.append(isn.outerFieldName).append(",").append(isn.subEntity.getSimpleName());
            sb.append(",condHash=").append(isn.config.hashCode()).append(")");
        } else if (node instanceof ConditionNode.NegateNode nn) {
            sb.append("NOT(");
            appendCacheKey(sb, nn.inner());
            sb.append(")");
        } else if (node instanceof ConditionNode.RawNode rn) {
            sb.append("RAW(").append(rn.fn.getClass().getName()).append("@")
                .append(Integer.toHexString(rn.fn.hashCode())).append(")");
        } else {
            sb.append(node.getClass().getSimpleName()).append("@").append(node.hashCode());
        }
    }

    private static void appendValue(StringBuilder sb, Object value) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof Collection<?> col) {
            sb.append("COLLECTION[").append(col.size()).append("]");
            for (Object item : col) {
                appendHashedValue(sb, item);
            }
        } else if (value instanceof Object[] arr) {
            sb.append("ARRAY[").append(arr.length).append("]");
            for (Object item : arr) {
                appendHashedValue(sb, item);
            }
        } else {
            appendHashedValue(sb, value);
        }
    }

    /**
     * 将值的哈希码写入缓存键，而非原始值。防止密码、token 等敏感数据泄露到缓存键中。
     * 包含类型信息和内容哈希以减少碰撞概率。
     *
     * <p>对数组使用 {@link java.util.Arrays#deepHashCode} 基于内容哈希，
     * 而非 Object.hashCode()（基于对象地址），确保不同内容的数组产生不同的缓存键。</p>
     */
    private static void appendHashedValue(StringBuilder sb, Object value) {
        if (value instanceof String s) {
            sb.append("S[").append(s.length()).append(":").append(s.hashCode()).append("]");
        } else if (value instanceof Object[] arr) {
            sb.append("A[").append(arr.length).append(":").append(java.util.Arrays.deepHashCode(arr)).append("]");
        } else if (value instanceof int[] arr) {
            sb.append("AI[").append(arr.length).append(":").append(java.util.Arrays.hashCode(arr)).append("]");
        } else if (value instanceof long[] arr) {
            sb.append("AL[").append(arr.length).append(":").append(java.util.Arrays.hashCode(arr)).append("]");
        } else if (value instanceof double[] arr) {
            sb.append("AD[").append(arr.length).append(":").append(java.util.Arrays.hashCode(arr)).append("]");
        } else if (value.getClass().isArray()) {
            sb.append("AX[").append(value.getClass().getSimpleName()).append(":")
                .append(java.util.Arrays.deepHashCode(new Object[] {value})).append("]");
        } else {
            String s = String.valueOf(value);
            sb.append("N[").append(value.getClass().getName()).append(":").append(s.length()).append(":")
                .append(s.hashCode()).append("]");
        }
    }
}
