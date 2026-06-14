package com.zsubera.jpa.template;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
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
 * @since 2.1.0
 */
public class CacheInvalidationListener {

    private static final Logger log = LoggerFactory.getLogger(CacheInvalidationListener.class);

    private final QueryCacheManager cacheManager;

    /**
     * 创建缓存失效监听器。
     *
     * @param cacheManager 查询缓存管理器
     */
    @SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "Spring-managed singleton intentionally stored")
    public CacheInvalidationListener(QueryCacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    /**
     * 在事务提交后清除相关缓存条目。
     *
     * @param event 实体变更事件
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTransactionCommit(EntityModifiedEvent event) {
        String prefix = event.getEntityName() + ":";
        int evicted = cacheManager.evictByPrefix(prefix);
        if (evicted > 0) {
            log.debug("Cache invalidated after transaction commit: {} entries evicted for entity '{}'", evicted,
                event.getEntityName());
        }
    }

    /**
     * 无事务时立即清除相关缓存条目。
     *
     * @param event 实体变更事件
     */
    @EventListener
    public void onEntityModified(EntityModifiedEvent event) {
        if (!org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()) {
            String prefix = event.getEntityName() + ":";
            int evicted = cacheManager.evictByPrefix(prefix);
            if (evicted > 0) {
                log.debug("Cache invalidated immediately (no active transaction): {} entries evicted for entity '{}'",
                    evicted, event.getEntityName());
            }
        }
    }
}
