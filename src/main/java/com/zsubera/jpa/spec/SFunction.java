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
 * <strong>为什么继承 {@link Serializable}：</strong>
 *
 * <p>
 * {@link Serializable} 接口是 Lambda 属性名提取机制的核心要求。Java 编译器对实现 {@code Serializable} 的函数式接口 特殊处理——生成的 Lambda 类会包含
 * {@link java.lang.invoke.SerializedLambda} 元数据， 其中包含实现方法的类名和方法名（如 {@code User.getName}）。 运行时通过 {@code writeReplace()}
 * 反射机制提取此元数据， 再将 getter 方法名转换为属性名（{@code getName} → {@code name}）。
 *
 * <p>
 * 如果不继承 {@code Serializable}，编译器不会生成 {@code SerializedLambda} 元数据， 属性名提取将失败。
 *
 * @param <T> 实体类型
 * @param <R> 属性返回类型
 * @see LambdaUtils#getPropertyName(SFunction)
 */
@FunctionalInterface
public interface SFunction<T, R> extends Function<T, R>, Serializable {}
