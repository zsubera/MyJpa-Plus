package com.zsubera.jpa.update;

import com.zsubera.jpa.spec.PredicateHelper;
import com.zsubera.jpa.spec.SFunction;
import com.zsubera.jpa.update.AbstractBulkOperationSpec.BulkConditionNode;
import java.util.List;

/**
 * 批量操作（{@link UpdateSpec} 和 {@link DeleteSpec}）中 OR 条件组的构建器。
 *
 * <p>
 * 通过此构建器添加的所有条件将以 OR 方式组合。由 {@link AbstractBulkOperationSpec#or(java.util.function.Consumer)} 隐式创建。
 *
 * @param <T> 实体类型
 * @param <SELF> 父构建器类型
 */
public class OrConditionBuilder<T, SELF extends AbstractBulkOperationSpec<T, SELF>> {

    private final SELF parent;
    private final List<BulkConditionNode> nodes;

    /**
     * 构造函数。
     *
     * @param parent 父构建器
     * @param nodes 条件节点列表
     */
    OrConditionBuilder(SELF parent, List<BulkConditionNode> nodes) {
        this.parent = parent;
        this.nodes = nodes;
    }

    /**
     * 添加等于条件：{@code field = value}。
     *
     * @param field 实体属性引用
     * @param value 比较值
     * @return 当前构建器实例
     */
    public OrConditionBuilder<T, SELF> eq(SFunction<T, ?> field, Object value) {
        String name = parent.property(field);
        nodes.add(new BulkConditionNode.LeafNode((root, cb) -> PredicateHelper.eq(root, name, value, cb)));
        return this;
    }

    /**
     * 添加不等于条件：{@code field != value}。
     *
     * @param field 实体属性引用
     * @param value 比较值
     * @return 当前构建器实例
     */
    public OrConditionBuilder<T, SELF> ne(SFunction<T, ?> field, Object value) {
        String name = parent.property(field);
        nodes.add(new BulkConditionNode.LeafNode((root, cb) -> PredicateHelper.ne(root, name, value, cb)));
        return this;
    }

    /**
     * 添加大于条件：{@code field > value}。
     *
     * @param field 实体属性引用
     * @param value 比较值
     * @return 当前构建器实例
     * @throws IllegalArgumentException 如果 value 为 null
     */
    public OrConditionBuilder<T, SELF> gt(SFunction<T, ?> field, Comparable<?> value) {
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
        String name = parent.property(field);
        nodes.add(new BulkConditionNode.LeafNode((root, cb) -> PredicateHelper.gt(root, name, value, cb)));
        return this;
    }

    /**
     * 添加大于等于条件：{@code field >= value}。
     *
     * @param field 实体属性引用
     * @param value 比较值
     * @return 当前构建器实例
     * @throws IllegalArgumentException 如果 value 为 null
     */
    public OrConditionBuilder<T, SELF> ge(SFunction<T, ?> field, Comparable<?> value) {
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
        String name = parent.property(field);
        nodes.add(new BulkConditionNode.LeafNode((root, cb) -> PredicateHelper.ge(root, name, value, cb)));
        return this;
    }

    /**
     * 添加小于条件：{@code field < value}。
     *
     * @param field 实体属性引用
     * @param value 比较值
     * @return 当前构建器实例
     * @throws IllegalArgumentException 如果 value 为 null
     */
    public OrConditionBuilder<T, SELF> lt(SFunction<T, ?> field, Comparable<?> value) {
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
        String name = parent.property(field);
        nodes.add(new BulkConditionNode.LeafNode((root, cb) -> PredicateHelper.lt(root, name, value, cb)));
        return this;
    }

    /**
     * 添加小于等于条件：{@code field <= value}。
     *
     * @param field 实体属性引用
     * @param value 比较值
     * @return 当前构建器实例
     * @throws IllegalArgumentException 如果 value 为 null
     */
    public OrConditionBuilder<T, SELF> le(SFunction<T, ?> field, Comparable<?> value) {
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
        String name = parent.property(field);
        nodes.add(new BulkConditionNode.LeafNode((root, cb) -> PredicateHelper.le(root, name, value, cb)));
        return this;
    }

    /**
     * 添加 LIKE 条件：{@code field LIKE value}。
     *
     * @param field 实体属性引用
     * @param value 匹配模式
     * @return 当前构建器实例
     * @throws IllegalArgumentException 如果 value 为 null
     */
    public OrConditionBuilder<T, SELF> like(SFunction<T, ?> field, String value) {
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
        String name = parent.property(field);
        nodes.add(new BulkConditionNode.LeafNode((root, cb) -> PredicateHelper.like(root, name, value, cb)));
        return this;
    }

    /**
     * 添加 NOT LIKE 条件：{@code field NOT LIKE value}。
     *
     * @param field 实体属性引用
     * @param value 匹配模式
     * @return 当前构建器实例
     * @throws IllegalArgumentException 如果 value 为 null
     */
    public OrConditionBuilder<T, SELF> notLike(SFunction<T, ?> field, String value) {
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
        String name = parent.property(field);
        nodes.add(new BulkConditionNode.LeafNode((root, cb) -> PredicateHelper.notLike(root, name, value, cb)));
        return this;
    }

    /**
     * 添加前缀匹配条件：{@code field LIKE 'value%'}。
     *
     * @param field 实体属性引用
     * @param value 前缀值
     * @return 当前构建器实例
     * @throws IllegalArgumentException 如果 value 为 null
     */
    public OrConditionBuilder<T, SELF> startsWith(SFunction<T, ?> field, String value) {
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
        String name = parent.property(field);
        nodes.add(new BulkConditionNode.LeafNode((root, cb) -> PredicateHelper.startsWith(root, name, value, cb)));
        return this;
    }

    /**
     * 添加后缀匹配条件：{@code field LIKE '%value'}。
     *
     * @param field 实体属性引用
     * @param value 后缀值
     * @return 当前构建器实例
     * @throws IllegalArgumentException 如果 value 为 null
     */
    public OrConditionBuilder<T, SELF> endsWith(SFunction<T, ?> field, String value) {
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
        String name = parent.property(field);
        nodes.add(new BulkConditionNode.LeafNode((root, cb) -> PredicateHelper.endsWith(root, name, value, cb)));
        return this;
    }

    /**
     * 添加包含条件：{@code field LIKE '%value%'}。
     *
     * @param field 实体属性引用
     * @param value 包含的值
     * @return 当前构建器实例
     * @throws IllegalArgumentException 如果 value 为 null
     */
    public OrConditionBuilder<T, SELF> contains(SFunction<T, ?> field, String value) {
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
        String name = parent.property(field);
        nodes.add(new BulkConditionNode.LeafNode((root, cb) -> PredicateHelper.contains(root, name, value, cb)));
        return this;
    }

    /**
     * 添加忽略大小写的等于条件：{@code UPPER(field) = UPPER(value)}。
     *
     * @param field 实体属性引用
     * @param value 比较值
     * @return 当前构建器实例
     */
    public OrConditionBuilder<T, SELF> eqIgnoreCase(SFunction<T, ?> field, String value) {
        String name = parent.property(field);
        nodes.add(new BulkConditionNode.LeafNode((root, cb) -> PredicateHelper.eqIgnoreCase(root, name, value, cb)));
        return this;
    }

    /**
     * 添加忽略大小写的 LIKE 条件：{@code UPPER(field) LIKE UPPER(value)}。
     *
     * @param field 实体属性引用
     * @param value 匹配模式
     * @return 当前构建器实例
     * @throws IllegalArgumentException 如果 value 为 null
     */
    public OrConditionBuilder<T, SELF> likeIgnoreCase(SFunction<T, ?> field, String value) {
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
        String name = parent.property(field);
        nodes.add(new BulkConditionNode.LeafNode((root, cb) -> PredicateHelper.likeIgnoreCase(root, name, value, cb)));
        return this;
    }

    /**
     * 添加 IN 条件：{@code field IN (values)}。
     *
     * @param field 实体属性引用
     * @param values 值数组
     * @return 当前构建器实例
     * @throws IllegalArgumentException 如果 values 为 null 或空
     */
    public OrConditionBuilder<T, SELF> in(SFunction<T, ?> field, Object... values) {
        String name = parent.property(field);
        if (values == null || values.length == 0) {
            throw new IllegalArgumentException("values must not be empty");
        }
        nodes.add(new BulkConditionNode.LeafNode((root, cb) -> PredicateHelper.in(root, name, values, cb)));
        return this;
    }

    /**
     * 添加 NOT IN 条件：{@code field NOT IN (values)}。
     *
     * @param field 实体属性引用
     * @param values 值数组
     * @return 当前构建器实例
     * @throws IllegalArgumentException 如果 values 为 null 或空
     */
    public OrConditionBuilder<T, SELF> notIn(SFunction<T, ?> field, Object... values) {
        String name = parent.property(field);
        if (values == null || values.length == 0) {
            throw new IllegalArgumentException("values must not be empty");
        }
        nodes.add(new BulkConditionNode.LeafNode((root, cb) -> PredicateHelper.notIn(root, name, values, cb)));
        return this;
    }

    /**
     * 添加 IS NULL 条件：{@code field IS NULL}。
     *
     * @param field 实体属性引用
     * @return 当前构建器实例
     */
    public OrConditionBuilder<T, SELF> isNull(SFunction<T, ?> field) {
        String name = parent.property(field);
        nodes.add(new BulkConditionNode.LeafNode((root, cb) -> PredicateHelper.isNull(root, name, cb)));
        return this;
    }

    /**
     * 添加 IS NOT NULL 条件：{@code field IS NOT NULL}。
     *
     * @param field 实体属性引用
     * @return 当前构建器实例
     */
    public OrConditionBuilder<T, SELF> isNotNull(SFunction<T, ?> field) {
        String name = parent.property(field);
        nodes.add(new BulkConditionNode.LeafNode((root, cb) -> PredicateHelper.isNotNull(root, name, cb)));
        return this;
    }

    /**
     * 添加 BETWEEN 条件：{@code field BETWEEN start AND end}。
     *
     * @param field 实体属性引用
     * @param start 范围起始值
     * @param end 范围结束值
     * @return 当前构建器实例
     * @throws IllegalArgumentException 如果 start 或 end 为 null，或类型不匹配，或 start 大于 end
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public OrConditionBuilder<T, SELF> between(SFunction<T, ?> field, Comparable<?> start, Comparable<?> end) {
        if (start == null) {
            throw new IllegalArgumentException("start must not be null");
        }
        if (end == null) {
            throw new IllegalArgumentException("end must not be null");
        }
        if (start.getClass() != end.getClass()) {
            throw new IllegalArgumentException("start and end must be of the same type, but got "
                + start.getClass().getName() + " and " + end.getClass().getName());
        }
        if (((Comparable)start).compareTo(end) > 0) {
            throw new IllegalArgumentException("start must not be greater than end");
        }
        String name = parent.property(field);
        nodes.add(new BulkConditionNode.LeafNode((root, cb) -> PredicateHelper.between(root, name, start, end, cb)));
        return this;
    }

    /**
     * 添加 NOT BETWEEN 条件：{@code field NOT BETWEEN start AND end}。
     *
     * @param field 实体属性引用
     * @param start 范围起始值
     * @param end 范围结束值
     * @return 当前构建器实例
     * @throws IllegalArgumentException 如果 start 或 end 为 null，或类型不匹配，或 start 大于 end
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public OrConditionBuilder<T, SELF> notBetween(SFunction<T, ?> field, Comparable<?> start, Comparable<?> end) {
        if (start == null) {
            throw new IllegalArgumentException("start must not be null");
        }
        if (end == null) {
            throw new IllegalArgumentException("end must not be null");
        }
        if (start.getClass() != end.getClass()) {
            throw new IllegalArgumentException("start and end must be of the same type, but got "
                + start.getClass().getName() + " and " + end.getClass().getName());
        }
        if (((Comparable)start).compareTo(end) > 0) {
            throw new IllegalArgumentException("start must not be greater than end");
        }
        String name = parent.property(field);
        nodes.add(new BulkConditionNode.LeafNode((root, cb) -> PredicateHelper.notBetween(root, name, start, end, cb)));
        return this;
    }

    /**
     * 添加集合为空条件：{@code field IS EMPTY}。
     *
     * @param field 实体属性引用
     * @return 当前构建器实例
     */
    public OrConditionBuilder<T, SELF> isEmpty(SFunction<T, ?> field) {
        String name = parent.property(field);
        nodes.add(new BulkConditionNode.LeafNode((root, cb) -> PredicateHelper.isEmpty(root, name, cb)));
        return this;
    }

    /**
     * 添加集合非空条件：{@code field IS NOT EMPTY}。
     *
     * @param field 实体属性引用
     * @return 当前构建器实例
     */
    public OrConditionBuilder<T, SELF> isNotEmpty(SFunction<T, ?> field) {
        String name = parent.property(field);
        nodes.add(new BulkConditionNode.LeafNode((root, cb) -> PredicateHelper.isNotEmpty(root, name, cb)));
        return this;
    }
}
