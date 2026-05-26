package com.zsubera.jpa.util;

import com.zsubera.jpa.spec.SFunction;

import java.beans.Introspector;
import java.lang.invoke.SerializedLambda;
import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;

public final class LambdaUtils {

    private static final ConcurrentHashMap<SFunction<?, ?>, String> CACHE = new ConcurrentHashMap<>();

    private LambdaUtils() {
    }

    public static <T> String getPropertyName(SFunction<T, ?> fn) {
        if (fn == null) {
            throw new IllegalArgumentException("SFunction must not be null");
        }
        return CACHE.computeIfAbsent(fn, LambdaUtils::resolvePropertyName);
    }

    private static String resolvePropertyName(SFunction<?, ?> fn) {
        try {
            Method writeReplace = fn.getClass().getDeclaredMethod("writeReplace");
            writeReplace.setAccessible(true);
            SerializedLambda lambda = (SerializedLambda) writeReplace.invoke(fn);
            String methodName = lambda.getImplMethodName();
            return methodToProperty(methodName);
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "Failed to extract property name from method reference. "
                            + "Ensure you are using a method reference directly (e.g., Entity::getField). "
                            + "Lambda expressions like e -> e.getField() are not supported.", e);
        }
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
