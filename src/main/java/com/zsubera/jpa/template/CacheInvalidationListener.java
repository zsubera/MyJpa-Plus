package com.zsubera.jpa.template;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 缓存失效监听器，监听 {@link EntityModifiedEvent} 并在事务提交后自动清除相关查询缓存。
 *
 * <p>
 * 此组件解决了 QueryCacheManager 不自动失效的问题。当实体发生变更时，通过发布
 * {@link EntityModifiedEvent} 事件，此监听器会在事务提交后自动清除以该实体名称为前缀的缓存条目。
 *
 * <p>
 * <strong>使用方式：</strong>
 *
 * <pre>{@code
 * // 1. 在更新操作后发布事件
 * @Service
 * public class OrderService {
 *     @Autowired
 *     private ApplicationEventPublisher eventPublisher;
 *
 *     @Transactional
 *     public void updateOrderStatus(Long orderId, String status) {
 *         orderRepository.updateOrderStatus(orderId, status);
 *         eventPublisher.publishEvent(new EntityModifiedEvent(Order.class, 1));
 *     }
 * }
 *
 * // 2. 或在 MyJpaTemplate 的批量操作中自动发布
 * // UpdateSpec 和 DeleteSpec 执行后会自动发布事件（需启用 autoPublishEvents）
 * }</pre>
 *
 * <p>
 * <strong>自动发布配置：</strong>在 application.yml 中启用：
 *
 * <pre>{@code
 * myjpa-plus:
 *   cache:
 *     auto-invalidation-enabled: true
 * }</pre>
 *
 * @author myjpa-plus

 */
public class CacheInvalidationListener {

    private static final Logger log = LoggerFactory.getLogger(CacheInvalidationListener.class);

    private final CacheAdapter cacheAdapter;

    /**
     * 防止同一实体在同一事件中被双重驱逐的去重缓存。
     * key = 实体名, value = 事件发布的时间戳（纳秒）
     * 上限 1024 条目，超出后跳过去重（允许重复驱逐，幂等安全）。
     */
    private final ConcurrentMap<String, Long> recentEvictions = new ConcurrentHashMap<>();

    private static final long DEDUP_WINDOW_NS = 1_000_000L; // 1ms
    private static final int MAX_RECENT_EVICTIONS = 1024;

    /**
     * 创建缓存失效监听器（使用 CacheAdapter）。
     *
     * @param cacheAdapter 缓存适配器
     */
    @SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "Spring-managed singleton intentionally stored")
    public CacheInvalidationListener(CacheAdapter cacheAdapter) {
        this.cacheAdapter = cacheAdapter;
    }

    /**
     * 在事务提交后清除相关缓存条目。
     *
     * @param event 实体变更事件
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTransactionCommit(EntityModifiedEvent event) {
        evictByEntity(event.getEntityName(), "transaction commit");
    }

    /**
     * 无事务时立即清除相关缓存条目。
     *
     * @param event 实体变更事件
     */
    @EventListener
    public void onEntityModified(EntityModifiedEvent event) {
        if (!org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()) {
            evictByEntity(event.getEntityName(), "no active transaction");
        }
    }

    private void evictByEntity(String entityName, String reason) {
        long now = System.nanoTime();
        Long lastEviction = recentEvictions.get(entityName);
        if (lastEviction != null && (now - lastEviction) < DEDUP_WINDOW_NS) {
            log.debug("Skipping duplicate cache invalidation for entity '{}' ({})", entityName, reason);
            return;
        }
        if (recentEvictions.size() >= MAX_RECENT_EVICTIONS) {
            // ponytail: Map is full — purge stale entries (>10ms old) to make room.
            long cutoff = now - DEDUP_WINDOW_NS * 10;
            recentEvictions.values().removeIf(v -> v < cutoff);
        }
        recentEvictions.put(entityName, now);
        String prefix = entityName + ":";
        int evicted = cacheAdapter.evictByPrefix(prefix);
        if (evicted > 0) {
            log.debug("Cache invalidated ({}): {} entries evicted for entity '{}'", reason, evicted, entityName);
        }
    }
}
