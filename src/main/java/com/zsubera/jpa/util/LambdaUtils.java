package com.zsubera.jpa.util;

import com.zsubera.jpa.exception.MyJpaPlusException;
import com.zsubera.jpa.spec.SFunction;
import java.beans.Introspector;
import java.lang.invoke.SerializedLambda;
import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
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
 * 缓存配置：属性名缓存默认大小为 4096。可通过系统属性 {@code myjpa-plus.lambda-cache-size} 自定义：
 *
 * <pre>{@code
 * // 启动时设置
 * -Dmyjpa-plus.lambda-cache-size=8192
 * }</pre>
 */
public final class LambdaUtils {

    private static final Logger log = LoggerFactory.getLogger(LambdaUtils.class);

    private static final int MAX_CACHE_SIZE;

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
        MAX_CACHE_SIZE = configured;
    }

    /**
     * Thread-safe cache using ConcurrentHashMap. A background daemon thread periodically checks cache size and clears
     * if it exceeds MAX_CACHE_SIZE. This prevents memory leaks in hot-redeploy and OSGi environments.
     */
    private static final Map<String, String> CACHE = new ConcurrentHashMap<>();

    static {
        ScheduledExecutorService cleaner = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "myjpa-cache-cleaner");
            t.setDaemon(true);
            return t;
        });
        cleaner.scheduleAtFixedRate(() -> {
            if (CACHE.size() > MAX_CACHE_SIZE) {
                // 使用更小的驱逐比例（10%而非50%）以减少性能毛刺
                int toRemove = CACHE.size() / 10;
                if (toRemove < 1) {
                    toRemove = 1;
                }
                if (log.isDebugEnabled()) {
                    log.debug("LambdaUtils cache size ({}) exceeds limit ({}). Evicting ~{} entries.", CACHE.size(),
                        MAX_CACHE_SIZE, toRemove);
                }
                Iterator<String> it = CACHE.keySet().iterator();
                for (int i = 0; i < toRemove && it.hasNext(); i++) {
                    it.next();
                    it.remove();
                }
            }
        }, 5, 5, TimeUnit.MINUTES);
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
            Method writeReplace = fn.getClass().getDeclaredMethod("writeReplace");
            writeReplace.setAccessible(true);
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
