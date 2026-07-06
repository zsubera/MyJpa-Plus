package com.zsubera.jpa.template;

import com.zsubera.jpa.util.SampledEvictionCache;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

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
    private static final SampledEvictionCache<Class<?>, java.lang.reflect.Method> ID_METHOD_CACHE =
        new SampledEvictionCache<>(1024, 0.75, 100, 64);
    private static final java.lang.reflect.Method NO_ID_METHOD_SENTINEL;
    private static final java.lang.reflect.Method NO_VERSION_SENTINEL;

    static {
        try {
            NO_ID_METHOD_SENTINEL = Object.class.getDeclaredMethod("toString");
            NO_VERSION_SENTINEL = Object.class.getDeclaredMethod("hashCode");
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    private final EntityManager entityManager;
    private final jakarta.persistence.EntityManagerFactory entityManagerFactory;

    BatchSaveTemplate(EntityManager entityManager) {
        this.entityManager = entityManager;
        this.entityManagerFactory = entityManager.getEntityManagerFactory();
    }

    private <R> R executeInNewTransaction(java.util.function.Function<EntityManager, R> operation) {
        try (EntityManager em = entityManagerFactory.createEntityManager()) {
            jakarta.persistence.EntityTransaction tx = em.getTransaction();
            tx.begin();
            try {
                R r = operation.apply(em);
                tx.commit();
                return r;
            } catch (RuntimeException | Error e) {
                if (tx.isActive()) {
                    tx.rollback();
                }
                throw e;
            }
        }
    }

    /**
     * 批量保存实体，使用 EntityManager flush/clear 进行分批处理。
     *
     * <p>
     * 对新实体（ID 为 null）使用 {@code persist()}，对已存在的实体使用 {@code merge()}。
     *
     * <p>
     * <strong>关于手动分配 ID：</strong>使用 {@code @GeneratedValue} 但手动设置 ID 值时，
     * {@code PersistenceUnitUtil.getIdentifier()} 返回非 null，会误判为已有实体并使用 {@code merge()}。
     * merge() 会额外发送 SELECT 确认实体是否存在。如需完全避免此开销，请使用 {@link #saveAllBatchedPure}。
     *
     * @param entities 要保存的实体列表
     * @param batchSize 每批大小，建议值为 50-200
     * @param <T> 实体类型
     * @return 保存后的实体列表（detached 状态）
     * @throws IllegalArgumentException 如果 entities 为 null 或 batchSize 不是正数
     */
    <T> List<T> saveAllBatched(Iterable<T> entities, int batchSize) {
        return executeBatchedSave(entities, batchSize, entity -> {
            if (isNewEntity(entity)) {
                entityManager.persist(entity);
                return entity;
            } else {
                return entityManager.merge(entity);
            }
        });
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
        return executeBatchedSave(entities, batchSize, entity -> {
            entityManager.persist(entity);
            return entity;
        });
    }

    /**
     * 通用分批保存逻辑，消除 saveAllBatched 和 saveAllBatchedPure 的重复代码。
     *
     * @param entities 要保存的实体列表
     * @param batchSize 每批大小
     * @param saveFunction 保存函数，接收实体并返回保存后的实体
     * @param <T> 实体类型
     * @return 保存后的实体列表
     */
    private <T> List<T> executeBatchedSave(Iterable<T> entities, int batchSize,
        java.util.function.Function<T, T> saveFunction) {
        ArrayList<T> result = new ArrayList<>();
        int count = 0;
        for (T entity : entities) {
            try {
                result.add(saveFunction.apply(entity));
            } catch (RuntimeException e) {
                // 清理持久化上下文，防止脏状态干扰后续操作或异常处理
                entityManager.clear();
                throw e;
            }
            count++;
            if (count % batchSize == 0) {
                entityManager.flush();
                entityManager.clear();
            }
        }
        if (count > 0 && count % batchSize != 0) {
            entityManager.flush();
            entityManager.clear();
        }
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
        int batchNumber = 0;
        try {
            for (T entity : entities) {
                batch.add(entity);
                if (batch.size() >= batchSize) {
                    result.addAll(executeBatchSave(batch));
                    batch.clear();
                    batchNumber++;
                }
            }
            if (!batch.isEmpty()) {
                result.addAll(executeBatchSave(batch));
            }
        } catch (RuntimeException e) {
            throw new PartialBatchCommitException(batchNumber, result.size(), result, e);
        }
        return result;
    }

    /**
     * 独立事务批量保存部分提交异常。当某批在独立事务中提交失败时抛出，
     * 携带已成功提交的实体列表，便于调用方了解部分提交状态。
     */
    static class PartialBatchCommitException extends RuntimeException {
        private final int completedBatches;
        private final int committedEntities;
        private final List<?> committedResults;

        PartialBatchCommitException(int completedBatches, int committedEntities,
            List<?> committedResults, Throwable cause) {
            super("Batch save failed after " + completedBatches + " batches committed "
                + committedEntities + " entities. Remaining entities were NOT committed.", cause);
            this.completedBatches = completedBatches;
            this.committedEntities = committedEntities;
            this.committedResults = committedResults;
        }

        public int getCompletedBatches() {
            return completedBatches;
        }

        public int getCommittedEntities() {
            return committedEntities;
        }

        public List<?> getCommittedResults() {
            return committedResults;
        }
    }

    private <T> List<T> executeBatchSave(List<T> batch) {
        return executeInNewTransaction(em -> {
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

    private static final com.zsubera.jpa.util.SampledEvictionCache<Class<?>,
        java.lang.reflect.Method> VERSION_METHOD_CACHE =
            new com.zsubera.jpa.util.SampledEvictionCache<>(256, 0.75, 100, 64);

    /**
     * 尝试提取 @Version 字段的值。用于辅助判断手动赋 ID 的实体是否为新建。
     * null 返回值意味着无 @Version 字段或获取失败（统一视为"无版本信息"）。
     */
    private static Object extractVersionField(Object entity) {
        java.lang.reflect.Method getVersion = VERSION_METHOD_CACHE.computeIfAbsent(entity.getClass(), clazz -> {
            for (Class<?> c = clazz; c != null && c != Object.class; c = c.getSuperclass()) {
                for (java.lang.reflect.Field f : c.getDeclaredFields()) {
                    if (f.isAnnotationPresent(jakarta.persistence.Version.class)) {
                        String name = f.getName();
                        String getter = "get" + Character.toUpperCase(name.charAt(0)) + name.substring(1);
                        try {
                            return clazz.getMethod(getter);
                        } catch (NoSuchMethodException ignored) {
                        }
                    }
                }
            }
            return NO_VERSION_SENTINEL;
        });
        if (getVersion == null || getVersion == NO_VERSION_SENTINEL) {
            return null;
        }
        try {
            return getVersion.invoke(entity);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    private boolean isNewEntity(Object entity) {
        try {
            jakarta.persistence.PersistenceUnitUtil puu =
                entityManager.getEntityManagerFactory().getPersistenceUnitUtil();
            Object id = puu.getIdentifier(entity);
            // ponytail: null ID always means new entity — don't check @Version for null IDs,
            // as primitive @Version fields return non-null auto-boxed defaults (0L/0).
            if (id == null) {
                return true;
            }
            // ponytail: ID != null does not guarantee persistence for manually-assigned @GeneratedValue.
            // Check @Version field as secondary signal: non-null version + non-null ID → definite existing.
            // Primitive types (long/int) always return non-null auto-boxed value (0L/0), so check default value.
            if (isDefaultPrimitiveValue(id, entity.getClass())) {
                return true;
            }
            // ponytail: isDefaultPrimitiveValue returned false (non-default ID) — check @Version
            // as secondary signal. Non-null version + non-null non-default ID → definite existing.
            Object version = extractVersionField(entity);
            if (version != null) {
                return false;
            }
        } catch (RuntimeException e) {
            log.warn("PersistenceUnitUtil.getIdentifier() failed for {}, falling back to getId(): {}",
                entity.getClass().getSimpleName(), e.getMessage());
        }

        try {
            java.lang.reflect.Method getId = ID_METHOD_CACHE.computeIfAbsent(entity.getClass(), clazz -> {
                try {
                    return clazz.getMethod("getId");
                } catch (NoSuchMethodException e) {
                    return NO_ID_METHOD_SENTINEL;
                }
            });
            if (getId == NO_ID_METHOD_SENTINEL) {
                // No getId() method — check for @Id annotation as a last resort
                boolean hasIdAnnotation = hasIdAnnotation(entity.getClass());
                if (hasIdAnnotation) {
                    log.debug("No getId() method found for {} but @Id annotation present; "
                        + "assuming existing entity (merge will be used).", entity.getClass().getSimpleName());
                } else {
                    log.warn(
                        "No getId() method and no @Id annotation found for {}; assuming existing entity. "
                            + "This may cause unnecessary SELECT queries during merge. "
                            + "Consider implementing getId() method or using saveAllBatchedPure() for new entities.",
                        entity.getClass().getSimpleName());
                }
                return false;
            }
            Object id = getId.invoke(entity);
            return id == null;
        } catch (ReflectiveOperationException e) {
            throw new com.zsubera.jpa.exception.MyJpaPlusException(
                "Cannot determine if entity is new for " + entity.getClass().getSimpleName()
                    + ". Entity must have a getId() method or be managed by PersistenceUnitUtil. "
                    + "Use saveAllBatchedPure() for new entities, or implement getId().",
                e);
        }
    }

    /**
     * 检查 ID 值是否为对应原始类型的默认值（0L, 0），
     * 此类值来自 PersistenceUnitUtil.getIdentifier() 对原始类型 ID 字段的自动装箱返回。
     * 仅当 ID 字段有 @GeneratedValue 注解时，才将 0 视为"默认值"（新实体）。
     */
    private static boolean isDefaultPrimitiveValue(Object id, Class<?> entityClass) {
        if (!hasGeneratedValueAnnotation(entityClass)) {
            return false;
        }
        return id instanceof Long l && l == 0L || id instanceof Integer i && i == 0 || id instanceof Short s && s == 0
            || id instanceof Byte b && b == 0;
    }

    /**
     * 检查实体类的 ID 字段是否声明了 {@code @GeneratedValue} 注解。
     */
    private static boolean hasGeneratedValueAnnotation(Class<?> entityClass) {
        Class<?> current = entityClass;
        while (current != null && current != Object.class) {
            for (java.lang.reflect.Field field : current.getDeclaredFields()) {
                if (field.isAnnotationPresent(jakarta.persistence.Id.class)
                    || field.isAnnotationPresent(jakarta.persistence.EmbeddedId.class)) {
                    return field.isAnnotationPresent(jakarta.persistence.GeneratedValue.class);
                }
            }
            current = current.getSuperclass();
        }
        return false;
    }

    /**
     * 检查实体类或其父类是否声明了 {@code @Id} 或 {@code @EmbeddedId} 注解的字段。
     */
    private static boolean hasIdAnnotation(Class<?> entityClass) {
        Class<?> current = entityClass;
        while (current != null && current != Object.class) {
            for (java.lang.reflect.Field field : current.getDeclaredFields()) {
                if (field.isAnnotationPresent(jakarta.persistence.Id.class)
                    || field.isAnnotationPresent(jakarta.persistence.EmbeddedId.class)) {
                    return true;
                }
            }
            current = current.getSuperclass();
        }
        return false;
    }

}
