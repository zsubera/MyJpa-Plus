package com.zsubera.jpa.util;

import com.zsubera.jpa.exception.MyJpaPlusException;
import com.zsubera.jpa.spec.SFunction;
import java.beans.Introspector;
import java.lang.invoke.SerializedLambda;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Lambda 工具类，用于从方法引用中提取实体属性名称。
 *
 * <p>
 * 支持将 {@code Entity::getField} 形式的方法引用转换为 {@code "field"} 形式的属性名称， 用于 JPA Criteria API 的动态查询构建。
 *
 * <p>
 * 使用示例：
 *
 * <pre>{@code
 * String name = LambdaUtils.getPropertyName(User::getName);
 * // 返回 "name"
 * }</pre>
 *
 * <p>
 * 缓存配置优先级：Spring Boot 配置 > 系统属性 > 默认值
 *
 * <ul>
 * <li>Spring Boot: {@code myjpa-plus.lambda-cache-size}
 * <li>系统属性: {@code -Dmyjpa-plus.lambda-cache-size}
 * <li>默认值: 4096
 * </ul>
 */
public final class LambdaUtils {

    private static final Logger log = LoggerFactory.getLogger(LambdaUtils.class);

    /**
     * 属性名缓存的最大大小。
     *
     * <p>
     * 配置优先级：Spring Boot 配置 > 系统属性 {@code myjpa-plus.lambda-cache-size} > 默认值 (4096)。
     */
    private static int maxCacheSize;

    static {
        int configured = 4096;
        String prop = System.getProperty("myjpa-plus.lambda-cache-size");
        if (prop != null) {
            try {
                int val = Integer.parseInt(prop);
                if (val > 0 && val <= 65536) {
                    configured = val;
                } else if (val > 65536) {
                    log.warn("myjpa-plus.lambda-cache-size value ({}) exceeds upper limit (65536). Using 65536.", val);
                    configured = 65536;
                }
            } catch (NumberFormatException ignored) {
                // use default
            }
        }
        maxCacheSize = configured;
    }

    /**
     * 获取缓存最大大小。
     *
     * @return 缓存最大大小
     */
    public static int getMaxCacheSize() {
        return maxCacheSize;
    }

    /**
     * 设置缓存最大大小。由 Spring Boot 自动配置调用。
     *
     * <p>
     * 有效范围：1-65536。超出范围的值将被忽略并记录警告。
     *
     * @param size 缓存最大大小
     */
    public static void setMaxCacheSize(int size) {
        if (size > 0 && size <= 65536) {
            maxCacheSize = size;
            log.info("Lambda cache size configured to {}", size);
        } else if (size > 65536) {
            log.warn("Lambda cache size ({}) exceeds upper limit (65536). Ignoring.", size);
        }
    }

    /**
     * 使用 LinkedHashMap 实现 LRU 缓存，按访问顺序维护条目，确保热点数据不被误驱逐。
     *
     * <p>
     * 当缓存大小超过 {@link #maxCacheSize} 时会自动驱逐最旧的条目（通过 {@code removeEldestEntry}），防止热部署场景下无限增长。驱逐后已有的 lambda
     * 元数据会在下次访问时重新解析（无副作用）。
     */
    private static final Map<String, String> CACHE =
        Collections.synchronizedMap(new LinkedHashMap<>(4096, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
                return size() > maxCacheSize;
            }
        });

    /** 缓存每个 lambda 类对应的 Method 对象，避免重复反射操作。 */
    private static final Map<Class<?>, Method> METHOD_CACHE = new ConcurrentHashMap<>();

    /**
     * 关闭后台清理线程。在应用关闭或热部署环境中应调用此方法以确保资源正确释放。
     *
     * <p>
     * 已在 {@code MyJpaPlusAutoConfiguration} 中通过 {@code DisposableBean} 自动注册关闭钩子。
     *
     * <p>
     * 当前实现为空操作，因为缓存清理已通过 {@link LinkedHashMap} 的 {@code removeEldestEntry} 自动处理。
     */
    public static void shutdown() {
        // 空操作：缓存清理已通过 LinkedHashMap 的 removeEldestEntry 自动处理
    }

    private LambdaUtils() {}

    /**
     * 从方法引用中提取属性名称。
     *
     * @param <T> 实体类型
     * @param fn 实体属性的方法引用
     * @return 属性名称
     * @throws IllegalArgumentException 如果 fn 为 null
     * @throws MyJpaPlusException 如果无法从方法引用中提取属性名称
     */
    public static <T> String getPropertyName(SFunction<T, ?> fn) {
        if (fn == null) {
            throw new IllegalArgumentException("SFunction must not be null");
        }
        try {
            Class<?> fnClass = fn.getClass();
            Method writeReplace = METHOD_CACHE.computeIfAbsent(fnClass, clazz -> {
                try {
                    Method m = clazz.getDeclaredMethod("writeReplace");
                    m.setAccessible(true);
                    return m;
                } catch (ReflectiveOperationException e) {
                    throw new MyJpaPlusException("Failed to extract property name from method reference. "
                        + "Ensure you are using a method reference directly (e.g., Entity::getField). "
                        + "Lambda expressions like e -> e.getField() are not supported. "
                        + "If using Java 17+ module system, add JVM argument: "
                        + "--add-opens java.base/java.lang.invoke=ALL-UNNAMED", e);
                }
            });
            SerializedLambda lambda = (SerializedLambda)writeReplace.invoke(fn);
            String key = lambda.getImplClass() + "#" + lambda.getImplMethodName();
            return CACHE.computeIfAbsent(key, k -> methodToProperty(lambda.getImplMethodName()));
        } catch (ReflectiveOperationException e) {
            throw new MyJpaPlusException("Failed to extract property name from method reference. "
                + "Ensure you are using a method reference directly (e.g., Entity::getField). "
                + "Lambda expressions like e -> e.getField() are not supported. "
                + "If using Java 17+ module system, add JVM argument: "
                + "--add-opens java.base/java.lang.invoke=ALL-UNNAMED", e);
        } catch (SecurityException e) {
            throw new MyJpaPlusException("Failed to extract property name due to security restriction. "
                + "If using Java 17+ module system, add JVM argument: "
                + "--add-opens java.base/java.lang.invoke=ALL-UNNAMED", e);
        }
    }

    /**
     * 获取缓存大小。
     *
     * @return 缓存中的条目数量
     */
    static int cacheSize() {
        return CACHE.size();
    }

    /** 清空缓存。 */
    static void clearCache() {
        CACHE.clear();
    }

    /**
     * 将方法名转换为属性名。
     *
     * @param methodName 方法名
     * @return 属性名
     */
    private static String methodToProperty(String methodName) {
        if (methodName.startsWith("get") && methodName.length() > 3) {
            return Introspector.decapitalize(methodName.substring(3));
        }
        if (methodName.startsWith("is") && methodName.length() > 2 && Character.isUpperCase(methodName.charAt(2))) {
            return Introspector.decapitalize(methodName.substring(2));
        }
        return methodName;
    }
}
