package com.zsubera.jpa.util;

import com.zsubera.jpa.exception.MyJpaPlusException;
import com.zsubera.jpa.spec.SFunction;
import java.beans.Introspector;
import java.lang.invoke.SerializedLambda;
import java.lang.reflect.Method;
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

    /** 缓存驱逐目标比例，驱逐后缓存大小降至 maxCacheSize 的此比例 */
    private static final double EVICTION_TARGET_RATIO = 0.75;

    /** 默认缓存大小 */
    private static final int DEFAULT_CACHE_SIZE = 4096;

    /** 缓存最大允许大小上限 */
    private static final int MAX_CACHE_SIZE_UPPER_LIMIT = 65536;

    /** METHOD_CACHE 默认初始容量 */
    private static final int METHOD_CACHE_INITIAL_CAPACITY = 256;

    /** METHOD_CACHE 最大容量，超过时触发驱逐 */
    private static final int METHOD_CACHE_MAX_SIZE = 2048;

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
        if (size > 0 && size <= MAX_CACHE_SIZE_UPPER_LIMIT) {
            maxCacheSize = size;
            log.info("Lambda cache size configured to {}", size);
        } else if (size > MAX_CACHE_SIZE_UPPER_LIMIT) {
            log.warn("Lambda cache size ({}) exceeds upper limit ({}). Ignoring.", size, MAX_CACHE_SIZE_UPPER_LIMIT);
        }
    }

    /**
     * 使用 ConcurrentHashMap 实现线程安全的属性名缓存。
     *
     * <p>
     * 与之前使用的 Collections.synchronizedMap 不同，ConcurrentHashMap 使用分段锁（Java 8+ 为 CAS + synchronized）， 在高并发场景下性能更优。当缓存大小超过
     * {@link #maxCacheSize} 时，通过 {@link #evictIfNeeded()} 驱逐旧条目。
     */
    private static final ConcurrentHashMap<String, String> CACHE = new ConcurrentHashMap<>(DEFAULT_CACHE_SIZE);

    /**
     * 缓存每个 lambda 类对应的 Method 对象，避免重复反射操作。
     *
     * <p>
     * 大小限制为 2048，超过时清除旧条目以防止热部署场景下无限增长。
     */
    private static final Map<Class<?>, Method> METHOD_CACHE = new ConcurrentHashMap<>(METHOD_CACHE_INITIAL_CAPACITY);

    /**
     * 关闭后台清理线程。在应用关闭或热部署环境中应调用此方法以确保资源正确释放。
     *
     * <p>
     * 已在 {@code MyJpaPlusAutoConfiguration} 中通过 {@code DisposableBean} 自动注册关闭钩子。
     *
     * <p>
     * 当前实现为空操作，因为缓存清理已通过 {@link ConcurrentHashMap} 的 {@code clear()} 方法在 {@link #evictIfNeeded()} 中自动处理。
     */
    public static void shutdown() {
        // 空操作：缓存清理已通过 ConcurrentHashMap 的 clear() 方法在 evictIfNeeded() 中自动处理
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
            String result = CACHE.computeIfAbsent(key, k -> methodToProperty(lambda.getImplMethodName()));
            evictIfNeeded();
            evictMethodCacheIfNeeded();
            return result;
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
     * 驱逐旧缓存条目，确保缓存大小不超过 {@link #maxCacheSize}。
     *
     * <p>
     * 当缓存大小超过限制时，使用 CAS 操作确保只有一个线程执行驱逐， 驱逐后缓存大小降至 {@link #EVICTION_TARGET_RATIO} 水平。 使用
     * {@link java.util.concurrent.atomic.AtomicBoolean} 防止多线程同时驱逐导致的缓存雪崩。
     *
     * <p>
     * <strong>已知限制：</strong>当前驱逐策略使用迭代器顺序删除，不保证 LRU（最近最少使用）语义。 在极端高并发场景下，热点条目可能被意外驱逐。 对于大多数应用场景，此策略已足够，
     * 因为热点条目会被快速重新加载到缓存中。 如需真正的 LRU 语义，可考虑使用 Caffeine 或其他专业缓存库。
     */
    private static final java.util.concurrent.atomic.AtomicBoolean EVICTING =
        new java.util.concurrent.atomic.AtomicBoolean(false);

    private static void evictIfNeeded() {
        long currentSize = CACHE.mappingCount();
        if (currentSize > maxCacheSize && EVICTING.compareAndSet(false, true)) {
            try {
                // 双重检查：获取锁后再次检查大小
                currentSize = CACHE.mappingCount();
                if (currentSize > maxCacheSize) {
                    long target = (long)(maxCacheSize * EVICTION_TARGET_RATIO);
                    long toRemove = currentSize - target;
                    if (toRemove > 0) {
                        long removed = 0;
                        java.util.Iterator<Map.Entry<String, String>> it = CACHE.entrySet().iterator();
                        while (it.hasNext() && removed < toRemove) {
                            it.next();
                            it.remove();
                            removed++;
                        }
                        log.debug("Lambda cache evicted {} entries (size: {} -> {})", removed, currentSize,
                            CACHE.mappingCount());
                    }
                }
            } finally {
                EVICTING.set(false);
            }
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
     * 驱逐 METHOD_CACHE 中的旧条目，防止热部署场景下无限增长。
     *
     * <p>
     * 当 METHOD_CACHE 大小超过 2048 时，驱逐约 25% 的条目，而非清除全部。 该缓存仅存储 Class -> Method 映射，数量通常有界，但在热部署场景下可能积累旧类加载器的条目。 使用 CAS
     * 操作确保只有一个线程执行驱逐。
     */
    private static final java.util.concurrent.atomic.AtomicBoolean METHOD_EVICTING =
        new java.util.concurrent.atomic.AtomicBoolean(false);

    private static void evictMethodCacheIfNeeded() {
        if (METHOD_CACHE.size() > METHOD_CACHE_MAX_SIZE && METHOD_EVICTING.compareAndSet(false, true)) {
            try {
                int currentSize = METHOD_CACHE.size();
                if (currentSize > METHOD_CACHE_MAX_SIZE) {
                    int target = (int)(METHOD_CACHE_MAX_SIZE * EVICTION_TARGET_RATIO);
                    int toRemove = currentSize - target;
                    if (toRemove > 0) {
                        int removed = 0;
                        java.util.Iterator<Map.Entry<Class<?>, Method>> it = METHOD_CACHE.entrySet().iterator();
                        while (it.hasNext() && removed < toRemove) {
                            it.next();
                            it.remove();
                            removed++;
                        }
                        log.debug("Method cache evicted {} entries (size: {} -> {})", removed, currentSize,
                            METHOD_CACHE.size());
                    }
                }
            } finally {
                METHOD_EVICTING.set(false);
            }
        }
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
