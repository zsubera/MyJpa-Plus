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
        if (groupByFields != null && !groupByFields.isEmpty()) {
            sb.append("#GROUPBY(");
            for (int i = 0; i < groupByFields.size(); i++) {
                if (i > 0) {
                    sb.append(",");
                }
                sb.append(groupByFields.get(i));
            }
            sb.append(")");
        }
        if (havingConditions != null && !havingConditions.isEmpty()) {
            sb.append("#HAVING(");
            for (int i = 0; i < havingConditions.size(); i++) {
                if (i > 0) {
                    sb.append(",");
                }
                Object hc = havingConditions.get(i);
                if (hc instanceof ConditionNode cn) {
                    appendCacheKey(sb, cn);
                } else {
                    sb.append(hc.hashCode());
                }
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

    // ponytail: recursion depth limit to prevent StackOverflowError on maliciously deep trees
    private static final int MAX_CACHE_KEY_DEPTH = 128;

    static void appendCacheKey(StringBuilder sb, ConditionNode node) {
        appendCacheKey(sb, node, 0);
    }

    private static void appendCacheKey(StringBuilder sb, ConditionNode node, int depth) {
        if (depth > MAX_CACHE_KEY_DEPTH) {
            sb.append("DEPTH_EXCEEDED(").append(node.getClass().getSimpleName()).append(")");
            return;
        }
        int nextDepth = depth + 1;
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
                appendCacheKey(sb, inner, nextDepth);
            }
            sb.append(")");
        } else if (node instanceof ConditionNode.OrNode on) {
            sb.append("OR(");
            for (ConditionNode inner : on.nodes()) {
                appendCacheKey(sb, inner, nextDepth);
            }
            sb.append(")");
        } else if (node instanceof ConditionNode.AndNode an) {
            sb.append("AND(");
            for (ConditionNode inner : an.nodes()) {
                appendCacheKey(sb, inner, nextDepth);
            }
            sb.append(")");
        } else if (node instanceof ConditionNode.MultiLikeNode mln) {
            sb.append("MULTILIKE(");
            appendHashedValue(sb, mln.keyword);
            for (String f : mln.fieldNames) {
                sb.append(",").append(f);
            }
            sb.append(")");
        } else if (node instanceof ConditionNode.CollectionNode cn) {
            sb.append("COLLECTION(").append(cn.fieldName).append(",").append(cn.op).append(")");
        } else if (node instanceof ConditionNode.ExistsNode<?> en) {
            sb.append(en.negate ? "NOTEXISTS(" : "EXISTS(");
            sb.append(en.subEntity.getSimpleName());
            sb.append(",ref=").append(lambdaCapturedArgHash(en.config)).append(")");
        } else if (node instanceof ConditionNode.InSubQueryNode<?> isn) {
            sb.append(isn.negate ? "NOTINSUBQUERY(" : "INSUBQUERY(");
            sb.append(isn.outerFieldName).append(",").append(isn.subEntity.getSimpleName());
            sb.append(",ref=").append(lambdaCapturedArgHash(isn.config)).append(")");
        } else if (node instanceof ConditionNode.NegateNode nn) {
            sb.append("NOT(");
            appendCacheKey(sb, nn.inner(), nextDepth);
            sb.append(")");
        } else if (node instanceof ConditionNode.RawNode rn) {
            sb.append("RAW(").append(rn.fn.getClass().getName()).append(")");
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
     * <p>使用 FNV-1a 64-bit 非加密哈希。64-bit 哈希的生日碰撞上界为 ~2^32，
     * 对于百万级缓存条目，碰撞概率可忽略。</p>
     */
    private static void appendHashedValue(StringBuilder sb, Object value) {
        if (value instanceof String s) {
            sb.append("S[").append(s.length()).append(":").append(Long.toUnsignedString(hash64(s))).append("]");
        } else if (value instanceof Object[] arr) {
            sb.append("A[").append(arr.length).append(":").append(Long.toUnsignedString(hashObjectArray(arr))).append("]");
        } else if (value instanceof int[] arr) {
            sb.append("AI[").append(arr.length).append(":").append(Long.toUnsignedString(hashIntArray(arr))).append("]");
        } else if (value instanceof long[] arr) {
            sb.append("AL[").append(arr.length).append(":").append(Long.toUnsignedString(hashLongArray(arr))).append("]");
        } else if (value instanceof double[] arr) {
            sb.append("AD[").append(arr.length).append(":").append(Long.toUnsignedString(hashDoubleArray(arr))).append("]");
        } else if (value instanceof byte[] arr) {
            sb.append("AB[").append(arr.length).append(":").append(Long.toUnsignedString(hashByteArray(arr))).append("]");
        } else if (value instanceof float[] arr) {
            sb.append("AF[").append(arr.length).append(":").append(Long.toUnsignedString(hashFloatArray(arr))).append("]");
        } else if (value.getClass().isArray()) {
            // 剩余基本类型数组（short[], boolean[], char[]）使用 String.valueOf 哈希
            sb.append("AX[").append(value.getClass().getSimpleName()).append(":")
                .append(Long.toUnsignedString(hash64(String.valueOf(value)))).append("]");
        } else {
            String s = String.valueOf(value);
            sb.append("N[").append(value.getClass().getName()).append(":").append(Long.toUnsignedString(hash64(s)))
                .append("]");
        }
    }

    /**
     * FNV-1a 64-bit 非加密哈希，用于缓存键生成。
     */
    private static long hash64(String s) {
        long hash = 0xcbf29ce484222325L; // FNV offset basis
        for (int i = 0; i < s.length(); i++) {
            hash ^= s.charAt(i);
            hash *= 0x100000001b3L; // FNV prime
        }
        return hash;
    }

    private static long hashObjectArray(Object[] arr) {
        long hash = 0xcbf29ce484222325L;
        for (Object o : arr) {
            String s = String.valueOf(o);
            for (int i = 0; i < s.length(); i++) {
                hash ^= s.charAt(i);
                hash *= 0x100000001b3L;
            }
            hash ^= 0x9e3779b97f4a7c15L; // per-element separator
            hash *= 0x100000001b3L;
        }
        return hash;
    }

    private static long hashIntArray(int[] arr) {
        long hash = 0xcbf29ce484222325L;
        for (int v : arr) {
            hash ^= v;
            hash *= 0x100000001b3L;
        }
        return hash;
    }

    private static long hashLongArray(long[] arr) {
        long hash = 0xcbf29ce484222325L;
        for (long v : arr) {
            hash ^= v;
            hash *= 0x100000001b3L;
        }
        return hash;
    }

    private static long hashDoubleArray(double[] arr) {
        long hash = 0xcbf29ce484222325L;
        for (double v : arr) {
            hash ^= Double.doubleToLongBits(v);
            hash *= 0x100000001b3L;
        }
        return hash;
    }

    private static long hashByteArray(byte[] arr) {
        long hash = 0xcbf29ce484222325L;
        for (byte v : arr) {
            hash ^= v;
            hash *= 0x100000001b3L;
        }
        return hash;
    }

    private static long hashFloatArray(float[] arr) {
        long hash = 0xcbf29ce484222325L;
        for (float v : arr) {
            hash ^= Float.floatToIntBits(v);
            hash *= 0x100000001b3L;
        }
        return hash;
    }

    private static String lambdaCapturedArgHash(Object lambda) {
        try {
            java.lang.reflect.Method writeReplace = lambda.getClass().getDeclaredMethod("writeReplace");
            writeReplace.setAccessible(true);
            Object result = writeReplace.invoke(lambda);
            if (result instanceof java.lang.invoke.SerializedLambda sl) {
                int argCount = sl.getCapturedArgCount();
                if (argCount == 0) {
                    return "noargs:" + sl.getFunctionalInterfaceMethodName();
                }
                StringBuilder sb = new StringBuilder(sl.getFunctionalInterfaceMethodName());
                for (int i = 0; i < argCount; i++) {
                    Object arg = sl.getCapturedArg(i);
                    sb.append("|");
                    if (arg != null) {
                        sb.append(arg.getClass().getName()).append("=");
                        sb.append(Long.toUnsignedString(hash64(String.valueOf(arg))));
                    } else {
                        sb.append("null");
                    }
                }
                return Long.toUnsignedString(hash64(sb.toString()));
            }
        } catch (Exception e) {
            // Fall through to identity hash fallback
        }
        return String.valueOf(System.identityHashCode(lambda));
    }
}
