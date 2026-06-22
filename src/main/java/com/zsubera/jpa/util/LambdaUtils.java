package com.zsubera.jpa.util;

import com.zsubera.jpa.exception.MyJpaPlusException;
import com.zsubera.jpa.spec.SFunction;
import java.beans.Introspector;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.invoke.SerializedLambda;
import java.lang.reflect.InaccessibleObjectException;
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
                // 使用默认值
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
     * 与之前使用的 {@code Collections.synchronizedMap} 包装 {@code LinkedHashMap} 相比， {@link ConcurrentHashMap}
     * 提供更好的并发读性能（读操作无锁），消除高并发场景下的同步瓶颈。
     *
     * <p>
     * <strong>驱逐策略说明：</strong>当缓存大小超过 {@link #maxCacheSize} 时，按迭代顺序清除约 25% 的条目。 这是近似 FIFO（First In First Out）策略——由于
     * {@link ConcurrentHashMap} 不维护访问顺序， 驱逐基于迭代顺序（近似插入顺序）而非最近最少使用。
     *
     * <p>
     * <strong>性能优化：</strong>在实际使用中（热点属性名高度集中在少数实体类上）， 迭代顺序驱逐与精确 LRU 的效果差异可忽略。如果您的应用场景中属性名分布非常均匀， 可以通过以下方式优化：
     * <ul>
     * <li>增大 {@link #maxCacheSize} 以减少驱逐频率</li>
     * <li>考虑使用 Caffeine 等高性能缓存库替换（需要额外依赖）</li>
     * </ul>
     */
    private static final Map<String, String> CACHE = new ConcurrentHashMap<>(DEFAULT_CACHE_SIZE);

    /**
     * 缓存每个 lambda 类对应的 Method 对象，避免重复反射操作。
     *
     * <p>
     * 大小限制为 2048，超过时清除旧条目以防止热部署场景下无限增长。
     */
    private static final Map<Class<?>, Method> METHOD_CACHE = new ConcurrentHashMap<>(METHOD_CACHE_INITIAL_CAPACITY);

    /**
     * 释放缓存资源。在应用关闭或热部署环境中应调用此方法以确保资源正确释放。
     *
     * <p>
     * 已在 {@code MyJpaPlusAutoConfiguration} 中通过 {@code ContextClosedEvent} 自动注册关闭钩子。
     */
    public static void shutdown() {
        CACHE.clear();
        METHOD_CACHE.clear();
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
        try {
            return resolveViaReflection(fn);
        } catch (InaccessibleObjectException | SecurityException e) {
            log.debug("Reflection path for LambdaUtils failed (JPMS restricted), falling back to serialization path", e);
        } catch (ReflectiveOperationException e) {
            log.debug("Reflection path for LambdaUtils failed, falling back to serialization path", e);
        }
        return resolveViaSerialization(fn);
    }

    /**
     * 通过反射 {@code SerializedLambda.writeReplace()} 解析属性名。
     * 需要 {@code --add-opens java.base/java.lang.invoke=ALL-UNNAMED}。
     */
    private static <T> String resolveViaReflection(SFunction<T, ?> fn) throws ReflectiveOperationException {
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
        SerializedLambda lambda = (SerializedLambda)writeReplace.invoke(fn);
        return resolvePropertyFromLambda(lambda);
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
            ObjectOutputStream oos = new ObjectOutputStream(baos);
            oos.writeObject(fn);
            oos.close();
            ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
            ObjectInputStream ois = new ObjectInputStream(bais);
            SerializedLambda lambda = (SerializedLambda)ois.readObject();
            ois.close();
            return resolvePropertyFromLambda(lambda);
        } catch (IOException | ClassNotFoundException e) {
            throw new MyJpaPlusException(
                "Failed to extract property name from method reference via serialization path. "
                    + "Ensure you are using a method reference directly (e.g., Entity::getField). "
                    + "Lambda expressions like e -> e.getField() are not supported. "
                    + "If using Java 17+ module system, add JVM argument: "
                    + "--add-opens java.base/java.lang.invoke=ALL-UNNAMED\n"
                    + "Maven: <jvmArguments>--add-opens java.base/java.lang.invoke=ALL-UNNAMED</jvmArguments>\n"
                    + "Gradle: bootRun { jvmArgs '--add-opens java.base/java.lang.invoke=ALL-UNNAMED' }", e);
        }
    }

    /**
     * 从 {@link SerializedLambda} 中提取方法名并转换为属性名。
     * 包含缓存查找和采样驱逐检查。
     */
    private static String resolvePropertyFromLambda(SerializedLambda lambda) {
        String key = lambda.getImplClass() + "#" + lambda.getImplMethodName();

        evictCacheIfNeeded();
        evictMethodCacheIfNeeded();
        String cached = CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        return CACHE.computeIfAbsent(key, k -> methodToProperty(lambda.getImplMethodName()));
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
    private static final java.util.concurrent.locks.ReentrantLock CACHE_EVICT_LOCK =
        new java.util.concurrent.locks.ReentrantLock();

    private static final java.util.concurrent.locks.ReentrantLock METHOD_EVICT_LOCK =
        new java.util.concurrent.locks.ReentrantLock();

    /** 调用计数器，用于采样驱逐检查（合并主缓存和方法缓存的检查） */
    private static final java.util.concurrent.atomic.AtomicInteger CALL_COUNTER =
        new java.util.concurrent.atomic.AtomicInteger(0);

    /** 采样间隔：每 N 次调用检查一次驱逐 */
    private static final int EVICTION_CHECK_INTERVAL = 100;

    /**
     * 驱逐主缓存中的旧条目，防止缓存无限增长。使用采样策略，每 100 次调用检查一次。
     *
     * <p>
     * 当缓存大小超过 {@link #maxCacheSize} 时，清除约 25% 的条目。 使用 CAS 确保只有一个线程执行驱逐。
     *
     * <p>
     * <strong>性能优化说明：</strong>
     * <ul>
     * <li>使用 AtomicInteger 采样，减少 CAS 竞争</li>
     * <li>驱逐时只迭代需要删除的条目数量，而非整个缓存</li>
     * <li>日志记录使用 debug 级别，生产环境默认不输出</li>
     * </ul>
     */
    private static void evictCacheIfNeeded() {
        if (CALL_COUNTER.incrementAndGet() % EVICTION_CHECK_INTERVAL != 0) {
            return;
        }
        // 快速检查：如果缓存大小未超过阈值，直接返回
        if (CACHE.size() <= maxCacheSize) {
            return;
        }
        // 使用 ReentrantLock 确保只有一个线程执行驱逐
        CACHE_EVICT_LOCK.lock();
        try {
            int currentSize = CACHE.size();
            // 再次检查（可能其他线程已经完成驱逐）
            if (currentSize > maxCacheSize) {
                int target = (int)(maxCacheSize * EVICTION_TARGET_RATIO);
                int toRemove = currentSize - target;
                if (toRemove > 0) {
                    int removed = 0;
                    long startTime = System.nanoTime();
                    java.util.Iterator<Map.Entry<String, String>> it = CACHE.entrySet().iterator();
                    while (it.hasNext() && removed < toRemove) {
                        it.next();
                        it.remove();
                        removed++;
                    }
                    long elapsed = System.nanoTime() - startTime;
                    if (log.isDebugEnabled()) {
                        log.debug("Property name cache evicted {} entries (size: {} -> {}, elapsed: {} us)", removed,
                            currentSize, CACHE.size(), elapsed / 1000);
                    }
                }
            }
        } finally {
            CACHE_EVICT_LOCK.unlock();
        }
    }

    private static void evictMethodCacheIfNeeded() {
        if (CALL_COUNTER.get() % EVICTION_CHECK_INTERVAL != 0) {
            return;
        }
        // 快速检查：如果缓存大小未超过阈值，直接返回
        if (METHOD_CACHE.size() <= METHOD_CACHE_MAX_SIZE) {
            return;
        }
        // 使用 ReentrantLock 确保只有一个线程执行驱逐
        METHOD_EVICT_LOCK.lock();
        try {
            int currentSize = METHOD_CACHE.size();
            // 再次检查（可能其他线程已经完成驱逐）
            if (currentSize > METHOD_CACHE_MAX_SIZE) {
                int target = (int)(METHOD_CACHE_MAX_SIZE * EVICTION_TARGET_RATIO);
                int toRemove = currentSize - target;
                if (toRemove > 0) {
                    int removed = 0;
                    long startTime = System.nanoTime();
                    java.util.Iterator<Map.Entry<Class<?>, Method>> it = METHOD_CACHE.entrySet().iterator();
                    while (it.hasNext() && removed < toRemove) {
                        it.next();
                        it.remove();
                        removed++;
                    }
                    long elapsed = System.nanoTime() - startTime;
                    if (log.isDebugEnabled()) {
                        log.debug("Method cache evicted {} entries (size: {} -> {}, elapsed: {} us)", removed,
                            currentSize, METHOD_CACHE.size(), elapsed / 1000);
                    }
                }
            }
        } finally {
            METHOD_EVICT_LOCK.unlock();
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
        // 支持 Java Record 访问器方法（record 字段直接访问，没有 get/is 前缀）
        // Record 访问器与字段同名（例如，对于名为 "name" 的 Record 组件，访问器就是 "name"）
        // 排除 java.lang.Object 的固有方法，避免将 hashCode/toString 等误识别为属性名
        if ("hashCode".equals(methodName) || "toString".equals(methodName) || "getClass".equals(methodName)
            || "notify".equals(methodName) || "notifyAll".equals(methodName) || "wait".equals(methodName)
            || "equals".equals(methodName) || "clone".equals(methodName) || "finalize".equals(methodName)) {
            throw new IllegalArgumentException("Method '" + methodName + "' is not a property accessor. "
                + "Use a getter method reference (Entity::getField) or record accessor.");
        }
        return methodName;
    }
}
