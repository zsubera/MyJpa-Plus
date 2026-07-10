package com.zsubera.jpa.util;

import com.zsubera.jpa.exception.MyJpaPlusException;
import com.zsubera.jpa.spec.SFunction;
import java.beans.Introspector;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectInputFilter;
import java.lang.invoke.SerializedLambda;
import java.lang.reflect.Method;
import java.util.Set;
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

    /** 默认缓存大小 */
    private static final int DEFAULT_CACHE_SIZE = 4096;

    /** 缓存最大允许大小上限 */
    private static final int MAX_CACHE_SIZE_UPPER_LIMIT = 65536;

    /**
     * 属性名缓存的最大大小。
     *
     * <p>
     * 配置优先级：Spring Boot 配置 > 系统属性 {@code myjpa-plus.lambda-cache-size} > 默认值 (4096)。
     */
    private static volatile int maxCacheSize;

    static {
        int configured = DEFAULT_CACHE_SIZE;
        String prop = System.getProperty("myjpa-plus.lambda-cache-size");
        if (prop != null) {
            try {
                int val = Integer.parseInt(prop);
                if (val > 0 && val <= MAX_CACHE_SIZE_UPPER_LIMIT) {
                    configured = val;
                } else if (val > MAX_CACHE_SIZE_UPPER_LIMIT) {
                    log.warn("myjpa-plus.lambda-cache-size value ({}) exceeds upper limit ({}). Using {}.", val,
                        MAX_CACHE_SIZE_UPPER_LIMIT, MAX_CACHE_SIZE_UPPER_LIMIT);
                    configured = MAX_CACHE_SIZE_UPPER_LIMIT;
                }
            } catch (NumberFormatException ignored) {
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
        if (size > 0 && size <= MAX_CACHE_SIZE_UPPER_LIMIT) {
            maxCacheSize = size;
            CACHE.setMaxSize(size);
            log.info("Lambda cache size configured to {}", size);
        } else if (size > MAX_CACHE_SIZE_UPPER_LIMIT) {
            log.warn("Lambda cache size ({}) exceeds upper limit ({}). Ignoring.", size, MAX_CACHE_SIZE_UPPER_LIMIT);
        }
        // ponytail: values <= 0 are silently ignored (existing behavior)
    }

    /**
     * 属性名缓存，使用 {@link SampledEvictionCache} 实现带采样驱逐的线程安全缓存。
     * 驱逐策略：超过 {@link #maxCacheSize} 时，每 100 次访问采样检查并驱逐至 75% 容量。
     */
    private static final SampledEvictionCache<String, String> CACHE =
        new SampledEvictionCache<>(maxCacheSize, 0.75, 100, DEFAULT_CACHE_SIZE);

    /**
     * 缓存每个 lambda 类对应的 Method 对象，避免重复反射操作。
     * 驱逐策略：超过 4096 时，每 100 次访问采样检查并驱逐至 75% 容量。
     */
    private static final SampledEvictionCache<Class<?>, Method> METHOD_CACHE =
        new SampledEvictionCache<>(4096, 0.75, 100, 4096);

    /**
     * SerializedLambda 缓存，避免每个缓存未命中时重复提取（resolveLambdaKey + resolveViaReflection 各提取一次）。
     * 提取 SerializedLambda 涉及反射 writeReplace() 调用，约 5-10μs/次。
     */
    private static final SampledEvictionCache<String, SerializedLambda> LAMBDA_CACHE =
        new SampledEvictionCache<>(4096, 0.75, 100, 4096);

    public static void shutdown() {
        CACHE.clear();
        METHOD_CACHE.clear();
        LAMBDA_CACHE.clear();
    }

    private LambdaUtils() {}

    /**
     * 从方法引用中提取属性名称（带 null 校验）。
     *
     * @param <T> 实体类型
     * @param fn 实体属性的方法引用
     * @return 属性名称
     * @throws IllegalArgumentException 如果 fn 为 null
     */
    public static <T> String property(SFunction<T, ?> fn) {
        if (fn == null) {
            throw new IllegalArgumentException("field must not be null");
        }
        return getPropertyName(fn);
    }

    /**
     * 从方法引用中提取属性名称。
     *
     * <p>
     * 使用 primary + fallback 双路径策略：
     * <ul>
     *   <li><strong>Primary（反射路径）：</strong>通过 {@code SerializedLambda.writeReplace()} 反射提取。
     *       需要 {@code --add-opens java.base/java.lang.invoke=ALL-UNNAMED}。</li>
     *   <li><strong>Fallback（序列化路径）：</strong>当 primary 因 JPMS 模块限制失败时，
     *       通过标准 Java 序列化 {@link ObjectOutputStream} 触发 lambda 的 {@code writeReplace()}，
     *       无需 {@code --add-opens}。</li>
     * </ul>
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
        // ponytail: resolveLambdaKeyAndLambda 同时提取 key 和 SerializedLambda，避免二次提取
        ResolveResult resolveResult = resolveLambdaKeyAndLambda(fn);
        String key = resolveResult.key();
        String cached = CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        SerializedLambda lambda = resolveResult.lambda();
        if (lambda == null) {
            // 尝试从 LAMBDA_CACHE 获取（其他线程可能已缓存）
            lambda = LAMBDA_CACHE.get(key);
        }
        if (lambda != null) {
            LAMBDA_CACHE.put(key, lambda);
            String propertyName = methodToProperty(lambda.getImplMethodName());
            IdentifierValidator.validateColumnName(propertyName);
            return CACHE.computeIfAbsent(key, k -> propertyName);
        }
        // ponytail: 降级到 resolveViaSerialization（仅当 lambda 提取完全失败时）
        String propertyName = resolveViaSerialization(fn);
        IdentifierValidator.validateColumnName(propertyName);
        // 缓存降级路径的结果，避免后续调用重复序列化（每次 5-10μs）
        return CACHE.computeIfAbsent(key, k -> propertyName);
    }

    /**
     * 解析 lambda 的唯一缓存键。优先使用 SerializedLambda 的 implClass+implMethodName（确定性、无碰撞），
     * 降级到 className+（仅在反射和序列化都失败时）。
     *
     * <p>
     * ponytail: 返回 ResolveResult 记录，同时包含缓存键和提取的 SerializedLambda，
     * 避免 getPropertyName() 中再次调用 extractSerializedLambda()（每次 5-10μs）。
     */
    private record ResolveResult(String key, SerializedLambda lambda) {
    }

    private static <T> ResolveResult resolveLambdaKeyAndLambda(SFunction<T, ?> fn) {
        SerializedLambda lambda = extractSerializedLambda(fn);
        if (lambda != null) {
            return new ResolveResult(lambda.getImplClass() + "#" + lambda.getImplMethodName(), lambda);
        }
        return new ResolveResult(fn.getClass().getName(), null);
    }

    private static <T> SerializedLambda extractSerializedLambda(SFunction<T, ?> fn) {
        try {
            return resolveViaReflectionLambda(fn);
        } catch (Exception e) {
            try {
                return resolveViaSerializationLambda(fn);
            } catch (Exception ex) {
                return null;
            }
        }
    }

    private static <T> SerializedLambda resolveViaReflectionLambda(SFunction<T, ?> fn) throws Exception {
        Class<?> fnClass = fn.getClass();
        Method writeReplace;
        try {
            writeReplace = METHOD_CACHE.computeIfAbsent(fnClass, clazz -> {
                try {
                    Method m = clazz.getDeclaredMethod("writeReplace");
                    m.setAccessible(true);
                    return m;
                } catch (ReflectiveOperationException e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (RuntimeException e) {
            if (e.getCause() instanceof ReflectiveOperationException roe) {
                throw roe;
            }
            throw e;
        }
        Object result = writeReplace.invoke(fn);
        if (result instanceof SerializedLambda lambda) {
            return lambda;
        }
        return null;
    }

    private static <T> SerializedLambda resolveViaSerializationLambda(SFunction<T, ?> fn) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(256);
        try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(fn);
        }
        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(baos.toByteArray()))) {
            // ponytail: 与 resolveViaSerialization() 保持一致，限制反序列化仅允许 SerializedLambda 类
            ois.setObjectInputFilter(info -> {
                if (info.serialClass() == null) {
                    return ObjectInputFilter.Status.ALLOWED;
                }
                return info.serialClass().getName().equals("java.lang.invoke.SerializedLambda")
                    ? ObjectInputFilter.Status.ALLOWED : ObjectInputFilter.Status.REJECTED;
            });
            return (SerializedLambda)ois.readObject();
        }
    }

    /**
     * 通过标准 Java 序列化解析属性名。利用 {@link ObjectOutputStream} 自动调用
     * lambda 的 {@code writeReplace()} 方法，无需 {@code --add-opens} 反射访问。
     *
     * <p>
     * ponytail: 序列化路径每次调用产生 IO 开销（约 5-10μs），但仅在反射路径不可用时的降级路径触发。
     * 结果仍会被 {@link #CACHE} 缓存，后续调用命中缓存后绕过此路径。
     */
    private static <T> String resolveViaSerialization(SFunction<T, ?> fn) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream(256);
            try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
                oos.writeObject(fn);
            }
            try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(baos.toByteArray()))) {
                // ponytail: 限制反序列化仅允许 SerializedLambda 类，防止反序列化攻击
                ois.setObjectInputFilter(info -> {
                    if (info.serialClass() == null) {
                        return ObjectInputFilter.Status.ALLOWED;
                    }
                    return info.serialClass().getName().equals("java.lang.invoke.SerializedLambda")
                        ? ObjectInputFilter.Status.ALLOWED : ObjectInputFilter.Status.REJECTED;
                });
                SerializedLambda lambda = (SerializedLambda)ois.readObject();
                String key = lambda.getImplClass() + "#" + lambda.getImplMethodName();
                return CACHE.computeIfAbsent(key, k -> methodToProperty(lambda.getImplMethodName()));
            }
        } catch (IOException | ClassNotFoundException e) {
            throw new MyJpaPlusException("Failed to extract property name from method reference. "
                + "Both reflection and serialization paths failed.\n" + "Common causes:\n"
                + "  1. You passed a lambda expression (e -> e.getField()) instead of a method reference (Entity::getField)\n"
                + "  2. Java 17+ module system restriction — add JVM argument:\n"
                + "     --add-opens java.base/java.lang.invoke=ALL-UNNAMED\n"
                + "     Maven: <jvmArguments>--add-opens java.base/java.lang.invoke=ALL-UNNAMED</jvmArguments>\n"
                + "     Gradle: bootRun { jvmArgs '--add-opens java.base/java.lang.invoke=ALL-UNNAMED' }\n"
                + "  3. Custom serialization proxy or bytecode enhancement interfering with writeReplace()\n"
                + "Original error: " + e.getMessage(), e);
        }
    }

    static int cacheSize() {
        return CACHE.size();
    }

    static void clearCache() {
        CACHE.clear();
    }

    /** 已知的 java.lang.Object 方法名集合，避免误识别为属性访问器。 */
    private static final Set<String> OBJECT_METHODS = Set.of("hashCode", "toString", "getClass", "notify", "notifyAll",
        "wait", "equals", "clone", "finalize", "isEmpty", "isBlank", "isNaN", "isNull", "isPresent");

    /**
     * 将方法名转换为属性名。
     *
     * @param methodName 方法名
     * @return 属性名
     */
    // ponytail: Lambda property extraction uses SerializedLambda (5-10μs uncached).
    // The SampledEvictionCache handles the caching; warm-up is not feasible without
    // actual method references from compiled code.

    private static String methodToProperty(String methodName) {
        if (methodName.startsWith("get") && methodName.length() > 3) {
            return Introspector.decapitalize(methodName.substring(3));
        }
        // ponytail: 验证 "is" 前缀的方法返回类型为 boolean/Boolean。
        // 仅通过方法名判断可能误判（如 isActive() 返回 int）。由于无法在编译期获取泛型类型信息，
        // 此处通过检查 methodName 前缀 + 大写字母模式进行推断，对于 is + 非布尔类型的边界情况
        // 由后续字段验证兜底。完整的类型校验需要反射加载 lambda 实现类，此处为性能权衡。
        if (methodName.startsWith("is") && methodName.length() > 2 && Character.isUpperCase(methodName.charAt(2))) {
            return Introspector.decapitalize(methodName.substring(2));
        }
        // 支持 Java Record 访问器方法（record 字段直接访问，没有 get/is 前缀）
        // Record 访问器与字段同名（例如，对于名为 "name" 的 Record 组件，访问器就是 "name"）
        // 排除 java.lang.Object 的固有方法，避免将 hashCode/toString 等误识别为属性名
        if (OBJECT_METHODS.contains(methodName)) {
            throw new IllegalArgumentException("Method '" + methodName + "' is not a property accessor. "
                + "Use a getter method reference (Entity::getField) or record accessor.");
        }
        return methodName;
    }
}
