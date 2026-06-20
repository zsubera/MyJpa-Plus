package com.zsubera.jpa.template;

/**
 * 缓存适配器 SPI，为查询结果缓存提供可插拔的后端实现。
 *
 * <p>
 * 默认实现为 {@link QueryCacheManager}（基于 ConcurrentHashMap 的本地缓存）。 用户可通过提供自定义
 * {@code CacheAdapter} Bean 来替换为 Redis、Caffeine、Hazelcast 等分布式或近端缓存实现。
 *
 * <h3>使用方式</h3>
 *
 * <pre>{@code
 * // 方式 1：使用默认本地缓存（无需额外配置）
 * // Spring Boot 自动配置 QueryCacheManager 作为 CacheAdapter
 *
 * // 方式 2：自定义 Redis 缓存适配器
 * @Configuration
 * public class CacheConfig {
 *     @Bean
 *     public CacheAdapter cacheAdapter(RedisTemplate<String, Object> redis) {
 *         return new RedisCacheAdapter(redis);
 *     }
 * }
 *
 * // 方式 3：禁用缓存
 * @Configuration
 * public class CacheConfig {
 *     @Bean
 *     public CacheAdapter cacheAdapter() {
 *         return CacheAdapter.disabled();
 *     }
 * }
 * }</pre>
 *
 * <h3>实现要求</h3>
 * <ul>
 * <li>实现类必须是线程安全的</li>
 * <li>{@link #get(String)} 在键不存在或已过期时应返回 null</li>
 * <li>{@link #put(String, Object, long)} 的 {@code ttlSeconds} 参数表示生存时间（秒），0 表示不缓存</li>
 * <li>{@link #evictByPrefix(String)} 应移除所有以给定前缀开头的条目</li>
 * </ul>
 *
 * @see QueryCacheManager
 * @see MyJpaTemplate#setCacheManager(QueryCacheManager)
 * @since 1.3.0
 */
public interface CacheAdapter {

    /**
     * 根据键获取缓存值。
     *
     * @param key 缓存键
     * @param <T> 期望的值类型
     * @return 缓存值，如果不存在或已过期则返回 null
     */
    <T> T get(String key);

    /**
     * 将值存入缓存，指定 TTL（生存时间）。
     *
     * @param key 缓存键
     * @param value 要缓存的值
     * @param ttlSeconds 生存时间（秒），0 表示不缓存
     * @param <T> 值类型
     * @return 如果成功写入返回 true
     */
    <T> boolean put(String key, T value, long ttlSeconds);

    /**
     * 从缓存中移除指定条目。
     *
     * @param key 要驱逐的缓存键
     */
    void evict(String key);

    /**
     * 按键前缀批量驱逐缓存条目。
     *
     * @param keyPrefix 缓存键前缀
     * @return 被驱逐的条目数
     */
    int evictByPrefix(String keyPrefix);

    /**
     * 清除所有缓存条目。
     */
    void clear();

    /**
     * 返回当前缓存条目数。
     *
     * @return 条目数
     */
    int size();

    /**
     * 返回缓存命中率。
     *
     * @return 命中率（0.0-1.0）
     */
    double getHitRate();

    /**
     * 返回缓存命中次数。
     *
     * @return 命中次数
     */
    long getHitCount();

    /**
     * 返回缓存未命中次数。
     *
     * @return 未命中次数
     */
    long getMissCount();

    /**
     * 重置命中率统计计数器。
     */
    void resetStats();

    /**
     * 返回一个禁用的缓存适配器实例，所有操作均为无操作。
     *
     * <p>
     * 适用于不需要查询缓存的场景（如开发环境、测试环境）。
     *
     * @return 禁用的缓存适配器
     */
    static CacheAdapter disabled() {
        return DisabledCacheAdapter.INSTANCE;
    }
}
