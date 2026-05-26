package com.zsubera.jpa.repository;

import org.springframework.core.ResolvableType;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

final class EntityClassResolver {

    private static final ConcurrentMap<Class<?>, Class<?>> CACHE = new ConcurrentHashMap<>();

    private EntityClassResolver() {
    }

    @SuppressWarnings("unchecked")
    static <T> Class<T> resolve(Class<?> repositoryClass) {
        return (Class<T>) CACHE.computeIfAbsent(repositoryClass, clz ->
                ResolvableType.forClass(clz)
                        .as(MyJpaRepository.class)
                        .resolveGeneric(0));
    }
}
