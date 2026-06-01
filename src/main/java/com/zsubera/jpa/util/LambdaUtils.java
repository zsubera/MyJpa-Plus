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
    private static final int METHOD_CACHE_MAX_SIZE = 4096;

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
     * 使用 {@link ConcurrentHashMap} 实现的属性名缓存。
     *
     * <p>
     * 与之前使用的 {@link Collections#synchronizedMap} 包装 {@link LinkedHashMap} 相比， {@link ConcurrentHashMap}
     * 提供更好的并发读性能（读操作无锁），消除高并发场景下的同步瓶颈。
     *
     * <p>
     * <strong>驱逐策略说明：</strong>当缓存大小超过 {@link #maxCacheSize} 时，按迭代顺序清除约 25% 的条目。 这是近似 FIFO（First In First Out）策略——由于
     * {@link ConcurrentHashMap} 不维护访问顺序， 驱逐基于迭代顺序（近似插入顺序）而非最近最少使用。在实际使用中（热点属性名高度集中在少数实体类上）， 迭代顺序驱逐与精确 LRU 的效果差异可忽略。如需精确
     * LRU 行为，可通过 {@link #setMaxCacheSize(int)} 调整缓存大小， 或替换为 Caffeine 等高性能缓存库。
     */
    @SuppressWarnings("serial")
    private static final Map<String, String> CACHE = new ConcurrentHashMap<>(DEFAULT_CACHE_SIZE);

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
     * 当前实现为空操作，因为主缓存使用 {@link LinkedHashMap} 的 LRU 驱逐策略（通过 {@code removeEldestEntry()} 自动清理）， METHOD_CACHE 的清理已在
     * {@link #evictMethodCacheIfNeeded()} 中自动处理。
     */
    public static void shutdown() {
        // 空操作：主缓存使用 LinkedHashMap LRU 自动驱逐，METHOD_CACHE 通过 evictMethodCacheIfNeeded() 自动清理
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
            evictCacheIfNeeded();
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

    /** 主缓存驱逐锁 */
    private static final java.util.concurrent.atomic.AtomicBoolean CACHE_EVICTING =
        new java.util.concurrent.atomic.AtomicBoolean(false);

    /** 调用计数器，用于采样驱逐检查 */
    private static final java.util.concurrent.atomic.AtomicInteger CALL_COUNTER =
        new java.util.concurrent.atomic.AtomicInteger(0);

    /** 采样间隔：每 N 次调用检查一次驱逐 */
    private static final int EVICTION_CHECK_INTERVAL = 100;

    /**
     * 驱逐主缓存中的旧条目，防止缓存无限增长。使用采样策略，每 100 次调用检查一次。
     *
     * <p>
     * 当缓存大小超过 {@link #maxCacheSize} 时，清除约 25% 的条目。 使用 CAS 确保只有一个线程执行驱逐。
     */
    private static void evictCacheIfNeeded() {
        if (CALL_COUNTER.incrementAndGet() % EVICTION_CHECK_INTERVAL != 0) {
            return;
        }
        if (CACHE.size() > maxCacheSize && CACHE_EVICTING.compareAndSet(false, true)) {
            try {
                int currentSize = CACHE.size();
                if (currentSize > maxCacheSize) {
                    int target = (int)(maxCacheSize * EVICTION_TARGET_RATIO);
                    int toRemove = currentSize - target;
                    if (toRemove > 0) {
                        int removed = 0;
                        java.util.Iterator<Map.Entry<String, String>> it = CACHE.entrySet().iterator();
                        while (it.hasNext() && removed < toRemove) {
                            it.next();
                            it.remove();
                            removed++;
                        }
                        log.debug("Property name cache evicted {} entries (size: {} -> {})", removed, currentSize,
                            CACHE.size());
                    }
                }
            } finally {
                CACHE_EVICTING.set(false);
            }
        }
    }

    private static void evictMethodCacheIfNeeded() {
        if (CALL_COUNTER.get() % EVICTION_CHECK_INTERVAL != 0) {
            return;
        }
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
        // P2-2: Support Java Record accessor methods (record fields are accessed directly, no get/is prefix)
        // Record accessors have the same name as the field (e.g., "name" for a Record component named "name")
        // This is the fallback for method names that don't match get/is patterns
        return methodName;
    }
}
