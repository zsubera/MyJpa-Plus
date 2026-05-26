package com.zsubera.jpa.util;

import com.zsubera.jpa.exception.MyJpaPlusException;
import com.zsubera.jpa.spec.SFunction;

import java.beans.Introspector;
import java.lang.invoke.SerializedLambda;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class LambdaUtils {

    private static final int MAX_CACHE_SIZE = 1024;
    private static final Map<String, String> CACHE =
            Collections.synchronizedMap(new LinkedHashMap<>(MAX_CACHE_SIZE, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
                    return size() > MAX_CACHE_SIZE;
                }
            });

    private LambdaUtils() {
    }

    public static <T> String getPropertyName(SFunction<T, ?> fn) {
        if (fn == null) {
            throw new IllegalArgumentException("SFunction must not be null");
        }
        try {
            Method writeReplace = fn.getClass().getDeclaredMethod("writeReplace");
            writeReplace.setAccessible(true);
            SerializedLambda lambda = (SerializedLambda) writeReplace.invoke(fn);
            String key = lambda.getImplClass() + "#" + lambda.getImplMethodName();
            String cached = CACHE.get(key);
            if (cached != null) {
                return cached;
            }
            return CACHE.computeIfAbsent(key, k -> methodToProperty(lambda.getImplMethodName()));
        } catch (ReflectiveOperationException e) {
            throw new MyJpaPlusException(
                    "Failed to extract property name from method reference. "
                            + "Ensure you are using a method reference directly (e.g., Entity::getField). "
                            + "Lambda expressions like e -> e.getField() are not supported.", e);
        } catch (SecurityException e) {
            throw new MyJpaPlusException(
                    "Failed to extract property name due to security restriction.", e);
        }
    }

    static int cacheSize() {
        return CACHE.size();
    }

    static void clearCache() {
        CACHE.clear();
    }

    private static String methodToProperty(String methodName) {
        if (methodName.startsWith("get") && methodName.length() > 3) {
            return Introspector.decapitalize(methodName.substring(3));
        }
        if (methodName.startsWith("is") && methodName.length() > 2) {
            return Introspector.decapitalize(methodName.substring(2));
        }
        return methodName;
    }
}
