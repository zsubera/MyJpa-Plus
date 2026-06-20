package com.zsubera.jpa.template;

/**
 * 禁用的缓存适配器，所有操作均为无操作。
 *
 * <p>
 * 通过 {@link CacheAdapterdisabled()} 获取单例实例。适用于不需要查询缓存的场景。
 *

 */
final class DisabledCacheAdapter implements CacheAdapter {

    static final DisabledCacheAdapter INSTANCE = new DisabledCacheAdapter();

    private DisabledCacheAdapter() {}

    @Override
    public <T> T get(String key) {
        return null;
    }

    @Override
    public <T> boolean put(String key, T value, long ttlSeconds) {
        return false;
    }

    @Override
    public void evict(String key) {}

    @Override
    public int evictByPrefix(String keyPrefix) {
        return 0;
    }

    @Override
    public void clear() {}

    @Override
    public int size() {
        return 0;
    }

    @Override
    public double getHitRate() {
        return 0.0;
    }

    @Override
    public long getHitCount() {
        return 0;
    }

    @Override
    public long getMissCount() {
        return 0;
    }

    @Override
    public void resetStats() {}
}
