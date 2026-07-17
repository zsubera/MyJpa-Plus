package com.zsubera.jpa.template;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;

/**
 * 基于 Redis 的查询缓存适配器实现。
 *
 * <p>
 * 使用 Spring Data Redis 的 {@link RedisTemplate} 作为底层存储，支持：
 * <ul>
 * <li>TTL 过期（通过 Redis 原生 EXPIRE 命令）</li>
 * <li>前缀驱逐（通过 SCAN + DEL）</li>
 * <li>序列化/反序列化（使用 JDK 序列化）</li>
 * </ul>
 *
 * <h3>使用方式</h3>
 *
 * <pre>{@code
 * // 自动配置方式（推荐）
 * myjpa-plus.cache.type=redis
 * myjpa-plus.cache.redis.host=localhost
 * myjpa-plus.cache.redis.port=6379
 *
 * // 手动配置方式
 * @Bean
 * public CacheAdapter cacheAdapter(RedisTemplate<String, Object> redis) {
 *     return new RedisCacheAdapter(redis);
 * }
 * }</pre>
 *
 * @see CacheAdapter
 * @see QueryCacheManager
 */
public class RedisCacheAdapter implements CacheAdapter {

    private static final Logger log = LoggerFactory.getLogger(RedisCacheAdapter.class);

    private final RedisTemplate<String, Object> redisTemplate;
    private final String keyPrefix;

    /** 驱逐代数，每次 evictByPrefix 或 clear 后递增 */
    private final AtomicLong evictionGeneration = new AtomicLong(0);

    /** 命中次数 */
    private final AtomicLong hitCount = new AtomicLong(0);

    /** 未命中次数 */
    private final AtomicLong missCount = new AtomicLong(0);

    /**
     * 创建 RedisCacheAdapter 实例。
     *
     * @param redisTemplate Redis 模板
     * @param keyPrefix 缓存键前缀
     */
    public RedisCacheAdapter(RedisTemplate<String, Object> redisTemplate, String keyPrefix) {
        if (redisTemplate == null) {
            throw new IllegalArgumentException("redisTemplate must not be null");
        }
        this.redisTemplate = redisTemplate;
        this.keyPrefix = keyPrefix != null ? keyPrefix : "myjpa:";
    }

    /**
     * 创建 RedisCacheAdapter 实例，使用默认键前缀 "myjpa:"。
     *
     * @param redisTemplate Redis 模板
     */
    public RedisCacheAdapter(RedisTemplate<String, Object> redisTemplate) {
        this(redisTemplate, "myjpa:");
    }

    private String fullKey(String key) {
        return keyPrefix + key;
    }

    @Override
    public <T> T get(String key) {
        try {
            Object value = redisTemplate.opsForValue().get(fullKey(key));
            if (value != null) {
                hitCount.incrementAndGet();
                @SuppressWarnings("unchecked")
                T result = (T)value;
                return result;
            }
            missCount.incrementAndGet();
            return null;
        } catch (Exception e) {
            log.warn("Redis get failed for key: {}, error: {}", key, e.getMessage());
            missCount.incrementAndGet();
            return null;
        }
    }

    @Override
    public <T> boolean put(String key, T value, long ttlSeconds) {
        try {
            if (ttlSeconds <= 0) {
                // ttl <= 0 表示不缓存
                return false;
            }
            redisTemplate.opsForValue().set(fullKey(key), value, ttlSeconds, java.util.concurrent.TimeUnit.SECONDS);
            return true;
        } catch (Exception e) {
            log.warn("Redis put failed for key: {}, error: {}", key, e.getMessage());
            return false;
        }
    }

    @Override
    public void evict(String key) {
        try {
            redisTemplate.delete(fullKey(key));
        } catch (Exception e) {
            log.warn("Redis evict failed for key: {}, error: {}", key, e.getMessage());
        }
    }

    @Override
    public int evictByPrefix(String keyPrefix) {
        try {
            String pattern = this.keyPrefix + keyPrefix + "*";
            Set<String> keys = new HashSet<>();

            // 使用 SCAN 遍历匹配的键
            redisTemplate.execute((RedisCallback<Void>)connection -> {
                Cursor<byte[]> cursor = connection.scan(ScanOptions.scanOptions().match(pattern).count(100).build());
                while (cursor.hasNext()) {
                    keys.add(new String(cursor.next()));
                }
                return null;
            });

            if (!keys.isEmpty()) {
                redisTemplate.delete(keys);
                evictionGeneration.incrementAndGet();
                log.debug("Redis evicted {} keys with prefix: {}", keys.size(), keyPrefix);
            }
            return keys.size();
        } catch (Exception e) {
            log.warn("Redis evictByPrefix failed for prefix: {}, error: {}", keyPrefix, e.getMessage());
            return 0;
        }
    }

    @Override
    public void clear() {
        try {
            String pattern = keyPrefix + "*";
            Set<String> keys = new HashSet<>();

            redisTemplate.execute((RedisCallback<Void>)connection -> {
                Cursor<byte[]> cursor = connection.scan(ScanOptions.scanOptions().match(pattern).count(100).build());
                while (cursor.hasNext()) {
                    keys.add(new String(cursor.next()));
                }
                return null;
            });

            if (!keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
            evictionGeneration.incrementAndGet();
            log.debug("Redis cleared {} keys with prefix: {}", keys.size(), keyPrefix);
        } catch (Exception e) {
            log.warn("Redis clear failed, error: {}", e.getMessage());
        }
    }

    @Override
    public int size() {
        try {
            String pattern = keyPrefix + "*";
            final int[] count = {0};

            redisTemplate.execute((RedisCallback<Void>)connection -> {
                Cursor<byte[]> cursor = connection.scan(ScanOptions.scanOptions().match(pattern).count(100).build());
                while (cursor.hasNext()) {
                    cursor.next();
                    count[0]++;
                }
                return null;
            });

            return count[0];
        } catch (Exception e) {
            log.warn("Redis size failed, error: {}", e.getMessage());
            return 0;
        }
    }

    @Override
    public double getHitRate() {
        long total = hitCount.get() + missCount.get();
        return total == 0 ? 0.0 : (double)hitCount.get() / total;
    }

    @Override
    public long getHitCount() {
        return hitCount.get();
    }

    @Override
    public long getMissCount() {
        return missCount.get();
    }

    @Override
    public void resetStats() {
        hitCount.set(0);
        missCount.set(0);
    }

    @Override
    public void putAll(Map<String, Object> entries, long defaultTtlSeconds) {
        if (entries == null || entries.isEmpty()) {
            return;
        }
        try {
            for (Map.Entry<String, Object> entry : entries.entrySet()) {
                if (defaultTtlSeconds > 0) {
                    redisTemplate.opsForValue().set(fullKey(entry.getKey()), entry.getValue(),
                        defaultTtlSeconds, java.util.concurrent.TimeUnit.SECONDS);
                } else {
                    redisTemplate.opsForValue().set(fullKey(entry.getKey()), entry.getValue());
                }
            }
        } catch (Exception e) {
            log.warn("Redis putAll failed, error: {}", e.getMessage());
        }
    }

    @Override
    public void evictAll(Collection<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return;
        }
        try {
            List<String> fullKeys = new ArrayList<>(keys.size());
            for (String key : keys) {
                fullKeys.add(fullKey(key));
            }
            redisTemplate.delete(fullKeys);
        } catch (Exception e) {
            log.warn("Redis evictAll failed, error: {}", e.getMessage());
        }
    }

    @Override
    public long getEvictionGeneration() {
        return evictionGeneration.get();
    }

    @Override
    public void close() {
        // RedisTemplate 的生命周期由 Spring 管理，此处无需额外关闭
        log.debug("RedisCacheAdapter closed");
    }
}
