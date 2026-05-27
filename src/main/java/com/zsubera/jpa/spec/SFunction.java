package com.zsubera.jpa.spec;

import java.io.Serializable;
import java.util.function.Function;

/**
 * 用于通过方法引用引用实体属性的可序列化函数。
 *
 * <p>
 * <strong>必须用作方法引用：</strong>
 *
 * <pre>{@code
 * Entity::getField
 * }</pre>
 *
 * <p>
 * <strong>不支持 Lambda 表达式：</strong>
 *
 * <pre>{@code
 * e -> e.getField()
 * }  // 将抛出 IllegalArgumentException</pre>
 *
 * <p>
 * {@link Serializable} 接口通过 Java 的 {@link java.lang.invoke.SerializedLambda} 机制
 * 在运行时启用属性名称提取，允许在不硬编码字段名字符串的情况下进行类型安全的查询构建。
 *
 * @param <T> 实体类型
 * @param <R> 属性返回类型
 */
@FunctionalInterface
public interface SFunction<T, R> extends Function<T, R>, Serializable {}
