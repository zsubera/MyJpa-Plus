package com.zsubera.jpa.template;

import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 批量保存操作模板，封装 {@code persist()/merge()} 的批量保存逻辑。
 *
 * <p>
 * 从 {@link MyJpaTemplate} 中提取，将批量保存相关的 isNewEntity 检测、分批 flush/clear、
 * 独立事务提交等逻辑集中在此类中。
 *
 * <p>
 * <strong>功能：</strong>
 * <ul>
 * <li>{@link #saveAllBatched} — 自动检测新/旧实体，新实体用 persist，旧实体用 merge</li>
 * <li>{@link #saveAllBatchedPure} — 纯 persist 批量保存，无 merge 开销</li>
 * <li>{@link #saveAllBatchedInSeparateTransactions} — 每批独立事务提交，避免长事务</li>
 * </ul>
 */
class BatchSaveTemplate {

    private static final Logger log = LoggerFactory.getLogger(BatchSaveTemplate.class);

    /** getId() 方法缓存，避免 isNewEntity() 每次反射查找。key = entity class，最大 1024 条 */
    private static final ConcurrentMap<Class<?>, java.lang.reflect.Method> ID_METHOD_CACHE =
        new ConcurrentHashMap<>(64);

    private static final int MAX_ID_METHOD_CACHE_SIZE = 1024;

    private final EntityManager entityManager;
    private final TransactionHelper transactionHelper;

    BatchSaveTemplate(EntityManager entityManager, TransactionHelper transactionHelper) {
        this.entityManager = entityManager;
        this.transactionHelper = transactionHelper;
    }

    /**
     * 批量保存实体，使用 EntityManager flush/clear 进行分批处理。
     *
     * <p>
     * 对新实体（ID 为 null）使用 {@code persist()}，对已存在的实体使用 {@code merge()}。
     *
     * @param entities 要保存的实体列表
     * @param batchSize 每批大小，建议值为 50-200
     * @param <T> 实体类型
     * @return 保存后的实体列表（detached 状态）
     * @throws IllegalArgumentException 如果 entities 为 null 或 batchSize 不是正数
     */
    <T> List<T> saveAllBatched(Iterable<T> entities, int batchSize) {
        ArrayList<T> result = new ArrayList<>();
        int count = 0;
        for (T entity : entities) {
            if (isNewEntity(entity)) {
                entityManager.persist(entity);
                result.add(entity);
            } else {
                result.add(entityManager.merge(entity));
            }
            count++;
            if (count % batchSize == 0) {
                entityManager.flush();
                entityManager.clear();
            }
        }
        entityManager.flush();
        entityManager.clear();
        return result;
    }

    /**
     * 纯 persist 批量保存实体，所有实体都使用 {@code persist()} 操作。
     *
     * @param entities 要保存的实体列表
     * @param batchSize 每批大小，建议值为 50-200
     * @param <T> 实体类型
     * @return 保存后的实体列表（detached 状态）
     * @throws IllegalArgumentException 如果 entities 为 null 或 batchSize 不是正数
     */
    <T> List<T> saveAllBatchedPure(Iterable<T> entities, int batchSize) {
        ArrayList<T> result = new ArrayList<>();
        int count = 0;
        for (T entity : entities) {
            entityManager.persist(entity);
            result.add(entity);
            count++;
            if (count % batchSize == 0) {
                entityManager.flush();
                entityManager.clear();
            }
        }
        entityManager.flush();
        entityManager.clear();
        return result;
    }

    /**
     * 批量保存实体，每批在独立事务中提交。
     *
     * @param entities 要保存的实体列表
     * @param batchSize 每批大小，建议值为 50-200
     * @param <T> 实体类型
     * @return 保存后的实体列表
     * @throws IllegalArgumentException 如果 entities 为 null 或 batchSize 不是正数
     */
    <T> List<T> saveAllBatchedInSeparateTransactions(Iterable<T> entities, int batchSize) {
        ArrayList<T> result = new ArrayList<>();
        ArrayList<T> batch = new ArrayList<>();
        for (T entity : entities) {
            batch.add(entity);
            if (batch.size() >= batchSize) {
                result.addAll(executeBatchSave(batch));
                batch.clear();
            }
        }
        if (!batch.isEmpty()) {
            result.addAll(executeBatchSave(batch));
        }
        return result;
    }

    private <T> List<T> executeBatchSave(List<T> batch) {
        return transactionHelper.executeInNewTransaction(em -> {
            ArrayList<T> batchRes = new ArrayList<>();
            for (T e : batch) {
                if (isNewEntity(e)) {
                    em.persist(e);
                    batchRes.add(e);
                } else {
                    batchRes.add(em.merge(e));
                }
            }
            em.flush();
            em.clear();
            return batchRes;
        });
    }

    private boolean isNewEntity(Object entity) {
        try {
            jakarta.persistence.PersistenceUnitUtil puu =
                entityManager.getEntityManagerFactory().getPersistenceUnitUtil();
            Object id = puu.getIdentifier(entity);
            return id == null;
        } catch (RuntimeException e) {
            if (log.isDebugEnabled()) {
                log.debug("PersistenceUnitUtil.getIdentifier() failed for {}: {}", entity.getClass().getSimpleName(),
                    e.getMessage());
            }
        }
        try {
            if (ID_METHOD_CACHE.size() >= MAX_ID_METHOD_CACHE_SIZE) {
                ID_METHOD_CACHE.clear();
            }
            java.lang.reflect.Method getId = ID_METHOD_CACHE.computeIfAbsent(entity.getClass(), clazz -> {
                try {
                    return clazz.getMethod("getId");
                } catch (NoSuchMethodException e) {
                    return null;
                }
            });
            if (getId == null) {
                log.debug("No getId() method found for {}; assuming existing", entity.getClass().getSimpleName());
                return false;
            }
            Object id = getId.invoke(entity);
            return id == null;
        } catch (ReflectiveOperationException ignored) {
        }
        log.debug("Cannot determine if entity is new for {}; assuming existing", entity.getClass().getSimpleName());
        return false;
    }
}
