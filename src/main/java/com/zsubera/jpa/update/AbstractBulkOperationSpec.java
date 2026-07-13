package com.zsubera.jpa.update;

import static com.zsubera.jpa.spec.ConditionalMethods.wrapLikePattern;

import com.zsubera.jpa.exception.MyJpaPlusException;
import com.zsubera.jpa.spec.BulkConditionSupport;
import com.zsubera.jpa.spec.ConditionalMethods;
import com.zsubera.jpa.spec.PredicateHelper;
import com.zsubera.jpa.spec.SFunction;
import com.zsubera.jpa.util.LambdaUtils;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.jpa.domain.Specification;

/**
 * JPA 批量操作构建器（{@link UpdateSpec} 和 {@link DeleteSpec}）的抽象基类。
 *
 * <p>
 * 使用延迟 Lambda 求值提供通用条件方法。谓词构造委托给 {@link PredicateHelper} 以与其他组件共享逻辑。
 *
 * <p>
 * <strong>设计说明：</strong>条件方法实现通过 {@link com.zsubera.jpa.spec.BulkConditionSupport} 接口的 default 方法统一提供，
 * 避免了与 {@link com.zsubera.jpa.update.OrConditionBuilder} 之间的重复。两者使用不同的求值模型与
 * {@link com.zsubera.jpa.spec.ConditionBuilder} 区分：
 * <ul>
 * <li>{@code ConditionBuilder}：延迟执行——构建 {@link com.zsubera.jpa.spec.ConditionNode} 树，在查询时统一求值</li>
 * <li>批量操作路径（此类与 {@code OrConditionBuilder}）：延迟 Lambda——直接构建 {@code BiFunction<Root, CriteriaBuilder, Predicate>}</li>
 * </ul>
 *
 * <p>
 * 条件便捷方法（带 {@code boolean condition} 参数）通过实现 {@link ConditionalMethods} 接口统一提供，避免与
 * {@link com.zsubera.jpa.spec.ConditionBuilder} 重复。
 *
 * <p>
 * 新增条件类型时，需同步更新以下位置：
 * <ol>
 * <li>{@link com.zsubera.jpa.spec.ConditionBuilder} — 查询构建器</li>
 * <li>{@link com.zsubera.jpa.spec.ConditionNode.Op} — 运算符枚举</li>
 * <li>{@code NodeResolver} — 查询条件解析（resolveSimple 等方法）</li>
 * <li>{@link com.zsubera.jpa.spec.QueryProjectionSupport} — 投影查询支持</li>
 * <li>{@link com.zsubera.jpa.spec.BulkConditionSupport} — 批量操作条件共享委托（默认方法 + {@code addCondition()}）</li>
 * <li>{@link com.zsubera.jpa.spec.SubQuerySpec} — 子查询条件</li>
 * </ol>
 *
 * @param <T> 实体类型
 * @param <SELF> 具体构建器类型，用于流式链式调用
 */
public abstract class AbstractBulkOperationSpec<T, SELF extends AbstractBulkOperationSpec<T, SELF>>
    implements BulkConditionSupport<T, SELF> {

    private static final Logger log = LoggerFactory.getLogger(AbstractBulkOperationSpec.class);

    protected final Class<T> entityClass;
    protected final List<BulkConditionNode> conditionNodes = new ArrayList<>();
    private PersistenceContextStrategy persistenceContextStrategy = PersistenceContextStrategy.AUTO_CLEAR;

    /**
     * 构造函数，初始化实体类类型。
     *
     * @param entityClass 实体类类型
     */
    protected AbstractBulkOperationSpec(Class<T> entityClass) {
        this.entityClass = entityClass;
    }

    /**
     * 设置批量操作后的持久化上下文管理策略。
     *
     * <p>默认值为 {@link PersistenceContextStrategy#AUTO_CLEAR}，保持向后兼容。
     * 如果调用方希望自行管理持久化上下文，可设置为 {@link PersistenceContextStrategy#DEFER_TO_CALLER}。
     *
     * @param strategy 持久化上下文策略
     * @return 当前构建器实例，支持链式调用
     */
    public SELF persistenceStrategy(PersistenceContextStrategy strategy) {
        if (strategy == null) {
            throw new IllegalArgumentException("persistenceStrategy must not be null");
        }
        this.persistenceContextStrategy = strategy;
        return self();
    }

    /**
     * 批量操作后清理持久化上下文：根据策略选择性地 clear L1 缓存，然后驱逐 L2 缓存。
     * 子类在每个批量操作方法的 {@code affected > 0} 分支中调用此方法。
     */
    protected void afterBulkOperation(EntityManager em, Class<?> entityClass) {
        if (persistenceContextStrategy == PersistenceContextStrategy.AUTO_CLEAR) {
            // 不调用 em.flush()：批量操作（CriteriaUpdate/NativeQuery）已直接发送 SQL 到数据库，
            // em.flush() 会无意中将持久化上下文中其他无关实体类型的脏数据写入数据库，
            // 然后 em.clear() 又将这些实体分离，导致意外的数据持久化和 LazyInitializationException。
            em.clear();
        }
        com.zsubera.jpa.util.CacheEvictionHelper.evictEntityCache(em, entityClass);
    }

    /**
     * 获取实体类类型。
     *
     * @return 实体类类型
     */
    public Class<T> getEntityClass() {
        return entityClass;
    }

    /**
     * 返回当前构建器实例，用于链式调用。
     *
     * @return 当前构建器实例
     */
    @Override
    @SuppressWarnings("unchecked")
    public SELF self() {
        return (SELF)this;
    }

    @Override
    public String property(SFunction<T, ?> field) {
        return LambdaUtils.property(field);
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public SELF addCondition(BiFunction<Root<?>, CriteriaBuilder, Predicate> predicateFn) {
        conditionNodes.add(new BulkConditionNode.LeafNode((BiFunction)predicateFn));
        return self();
    }

    /**
     * 在事务中执行批量操作。
     *
     * <p>
     * <strong>事务管理策略：</strong>
     * <ul>
     * <li>如果当前存在活动事务（Spring 管理或 JTA），直接在该事务中执行</li>
     * <li>如果没有活动事务，创建新的 JPA EntityTransaction 并在完成后提交</li>
     * <li>执行过程中发生异常时，如果是新创建的事务则回滚</li>
     * </ul>
     *
     * @param em 实体管理器
     * @return 受影响的行数
     */
    public int executeInTransaction(EntityManager em) {
        return executeInTransaction(em, this::doExecute);
    }

    /**
     * 在事务中执行给定操作。
     *
     * <p>
     * 此重载方法允许子类执行自定义操作（如无条件 deleteAll）并进行正确的事务管理。
     * 事务管理委托给 {@link BulkTransactionHelper}，消除重复的事务管理代码。
     *
     * @param em 实体管理器
     * @param operation 要执行的操作
     * @return 受影响的行数
     */
    protected int executeInTransaction(EntityManager em, Function<EntityManager, Integer> operation) {
        return BulkTransactionHelper.executeInManagedTransaction(em, operation);
    }

    /**
     * 执行批量操作。要求底层 {@link EntityManager} 中存在活动事务。
     *
     * <p>
     * <strong>注意：</strong>此方法不会自动管理事务。调用方需确保：
     * <ul>
     * <li>在调用前开启事务</li>
     * <li>在调用后提交或回滚事务</li>
     * <li>捕获异常并处理事务回滚</li>
     * </ul>
     *
     * <p>
     * 当在已有活动事务的环境中调用时（嵌套调用），此方法直接执行操作但不提交也不回滚。 操作失败时异常会向上传播，由外层事务管理器处理。如需自动事务管理，请使用
     * {@link #executeInTransaction(EntityManager)} 方法。
     *
     * @param em 实体管理器
     * @return 受影响的行数
     * @throws jakarta.persistence.TransactionRequiredException 如果没有活动事务
     * @see #executeInTransaction(EntityManager)
     */
    public abstract int execute(EntityManager em);

    /**
     * 执行实际的批量操作逻辑，由子类实现。
     *
     * @param em 实体管理器
     * @return 受影响的行数
     */
    protected abstract int doExecute(EntityManager em);

    /**
     * 尝试回滚当前事务。支持 RESOURCE_LOCAL 和 JTA 环境。
     * <p>
     * RESOURCE_LOCAL 环境下使用 {@code em.getTransaction().rollback()}，
     * JTA 环境下回退到 {@code TransactionAspectSupport.currentTransactionStatus().setRollbackOnly()}。
     *
     * @param em 实体管理器
     * @param operationDesc 操作描述，用于日志
     * @return true 如果回滚成功或已标记为 rollback-only
     */
    static boolean rollbackOrMarkRollbackOnly(EntityManager em, String operationDesc) {
        try {
            jakarta.persistence.EntityTransaction tx = em.getTransaction();
            if (tx != null && tx.isActive()) {
                tx.rollback();
                log.warn("Transaction has been rolled back for {}.", operationDesc);
                return true;
            }
        } catch (IllegalStateException ignored) {
            // JTA 环境：getTransaction() 抛出 IllegalStateException
        } catch (Exception e) {
            log.warn("Failed to rollback via EntityTransaction for {}: {}", operationDesc, e.getMessage());
        }
        try {
            org.springframework.transaction.interceptor.TransactionAspectSupport.currentTransactionStatus()
                .setRollbackOnly();
            log.warn("Transaction marked as rollback-only for {}.", operationDesc);
            return true;
        } catch (Exception e) {
            log.error("Failed to mark rollback-only for {}: {}", operationDesc, e.getMessage());
        }
        return false;
    }

    /**
     * 获取全局配置中的最大批量操作行数限制。
     *
     * @return 限制值，如果未配置或禁用则返回 -1
     */
    protected int resolveMaxBulkOperationRows() {
        return com.zsubera.jpa.autoconfigure.GlobalConfigHolder.resolveMaxBulkOperationRows(-1);
    }

    /**
     * 执行带行数限制保护的批量操作。统一处理：限制检查 → 执行 → 后执行竞态检测。
     *
     * @param em 实体管理器
     * @param operationName 操作名称（UPDATE/DELETE），用于错误消息
     * @param buildAndExecute 构建 Criteria 并执行的操作，返回受影响行数
     * @return 受影响的行数
     */
    protected int executeWithLimitCheck(EntityManager em, String operationName,
        java.util.function.Function<EntityManager, Integer> buildAndExecute) {
        int limit = resolveMaxBulkOperationRows();
        if (limit > 0) {
            checkRowCountBeforeExecute(em, limit, operationName);
        }
        int affected = buildAndExecute.apply(em);
        if (limit > 0 && affected > limit) {
            if (log.isWarnEnabled()) {
                log.warn(
                    "{} affected {} rows, exceeding the pre-check limit of {}. " + "Concurrent modifications detected.",
                    operationName, affected, limit);
            }
            // ponytail: Explicit rollback consistent with updateAll()/deleteAll() behavior.
            // In Spring @Transactional context, the framework also rolls back on unchecked exceptions,
            // but this handles RESOURCE_LOCAL transactions where the caller may catch the exception.
            boolean rolledBack = rollbackOrMarkRollbackOnly(em, "Bulk " + operationName);
            if (!rolledBack) {
                log.error("CRITICAL: Rollback FAILED. The {} may be committed. Data corruption risk.", operationName);
            }
            String rollbackStatus = rolledBack ? "Transaction has been rolled back or marked rollback-only."
                : "CRITICAL: Rollback FAILED. The operation may be committed. Data corruption risk.";
            throw new com.zsubera.jpa.exception.MyJpaPlusException(
                operationName + " affected " + affected + " rows, exceeding the pre-check limit of " + limit
                    + ". Concurrent modifications detected. " + rollbackStatus);
        }
        return affected;
    }

    /** 批量操作条件树的密封节点类型。支持 AND、OR、NOT 和叶子谓词节点。 */
    sealed interface BulkConditionNode {
        /** 叶子谓词函数节点。 */
        record LeafNode(BiFunction<Root<?>, CriteriaBuilder, Predicate> fn) implements BulkConditionNode {
        }

        /** AND 子节点组。 */
        record AndNode(List<BulkConditionNode> children) implements BulkConditionNode {
            static AndNode of(List<BulkConditionNode> children) {
                return new AndNode(List.copyOf(children));
            }
        }

        /** OR 子节点组。 */
        record OrNode(List<BulkConditionNode> children) implements BulkConditionNode {
            static OrNode of(List<BulkConditionNode> children) {
                return new OrNode(List.copyOf(children));
            }
        }

        /** NOT 包装节点。 */
        record NotNode(BulkConditionNode child) implements BulkConditionNode {
        }
    }

    /**
     * 添加 OR 条件组。consumer 中添加的所有条件将以 OR 而非 AND 方式组合。
     *
     * <p>
     * 示例：
     *
     * <pre>{@code
     * new DeleteSpec<>(User.class).or(o -> o.eq(User::getStatus, "INACTIVE").eq(User::getStatus, "SUSPENDED"))
     *     .execute();
     * // WHERE (status = 'INACTIVE' OR status = 'SUSPENDED')
     * }</pre>
     *
     * @return 当前构建器实例，支持链式调用
     */
    public SELF or(Consumer<OrConditionBuilder<T, SELF>> config) {
        if (config == null) {
            throw new IllegalArgumentException("config must not be null");
        }
        List<BulkConditionNode> children = new ArrayList<>();
        config.accept(new OrConditionBuilder<>(self(), children));
        if (children.isEmpty()) {
            throw new IllegalArgumentException(
                "Empty or() consumer produces no conditions. Add at least one condition inside the consumer.");
        }
        conditionNodes.add(BulkConditionNode.OrNode.of(children));
        return self();
    }

    /**
     * 对内部条件组取反。通过 {@link OrConditionBuilder} 添加的所有条件以 AND 方式组合后整体取反。
     *
     * <p>
     * <strong>语义说明：</strong>{@code not(n -> n.eq(A).eq(B))} 生成 {@code NOT (A AND B)}，
     * 根据德摩根定律等价于 {@code NOT A OR NOT B}。
     * 与 {@link com.zsubera.jpa.spec.QuerySpec#not(java.util.function.Consumer)} 行为一致。
     *
     * <pre>{@code
     * // 示例: 删除状态不是 ACTIVE 或者不是 PENDING 的记录
     * // 生成: WHERE NOT (status = 'ACTIVE' AND status = 'PENDING')
     * // 等价: WHERE status != 'ACTIVE' OR status != 'PENDING'
     * deleteSpec.not(not -> not.eq(User::getStatus, Status.ACTIVE).eq(User::getStatus, Status.PENDING));
     * }</pre>
     *
     * @param config 配置函数，接收 {@link OrConditionBuilder} 以添加取反条件
     * @return 当前构建器实例，支持链式调用
     * @throws IllegalArgumentException 如果 {@code config} 为 null
     */
    public SELF not(Consumer<OrConditionBuilder<T, SELF>> config) {
        if (config == null) {
            throw new IllegalArgumentException("config must not be null");
        }
        List<BulkConditionNode> children = new ArrayList<>();
        config.accept(new OrConditionBuilder<>(self(), children));
        if (children.isEmpty()) {
            throw new IllegalArgumentException(
                "Empty not() consumer produces no conditions. Add at least one condition inside the consumer.");
        }

        // 之前使用 OrNode 导致 NOT(A OR B)，与 QuerySpec.not() 的 NOT(A AND B) 语义不同
        BulkConditionNode combined = children.size() == 1 ? children.get(0) : BulkConditionNode.AndNode.of(children);
        conditionNodes.add(new BulkConditionNode.NotNode(combined));
        return self();
    }

    /**
     * 复用 {@link Specification} 作为 WHERE 条件。
     *
     * <p>
     * 允许将 {@link com.zsubera.jpa.spec.QuerySpec} 或任何 {@link Specification} 实例直接用于批量操作的 WHERE 子句，
     * 避免查询和更新/删除之间的条件逻辑重复。
     *
     * <pre>{@code
     * QuerySpec<User> active = new QuerySpec<User>().eq(User::getStatus, "ACTIVE");
     *
     * // 复用同一条件进行查询和更新
     * List<User> users = repository.findAll(active);
     * repository.update(s -> s.set(User::getStatus, "INACTIVE").where(active));
     * }</pre>
     *
     * <p>
     * <strong>实现说明：</strong>{@link Specification#toPredicate} 需要 {@link CriteriaQuery} 参数，
     * 但大多数实现（包括 {@code QuerySpec}）仅在子查询中使用该参数。此处创建临时 {@code CriteriaQuery}
     * 以满足接口签名，不影响运行时行为。
     *
     * @param spec 查询规格说明，用作 WHERE 条件
     * @return 当前构建器实例，支持链式调用
     * @throws IllegalArgumentException 如果 spec 为 null
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public SELF where(Specification<T> spec) {
        if (spec == null) {
            throw new IllegalArgumentException("spec must not be null");
        }
        BiFunction<Root<?>, CriteriaBuilder, Predicate> specFn = (root, cb) -> {
            CriteriaQuery<?> tempQuery = cb.createQuery(entityClass);
            return spec.toPredicate((Root<T>)root, tempQuery, cb);
        };
        conditionNodes.add(new BulkConditionNode.LeafNode((BiFunction)specFn));
        return self();
    }

    // ---- 条件方法由 BulkConditionSupport 默认实现提供 ----

    /**
     * 添加 EXISTS 子查询条件。
     *
     * @param subEntity 子查询实体类型
     * @param config 子查询配置消费者
     * @param <S> 子查询实体类型
     * @return 当前构建器实例
     * @throws IllegalArgumentException 如果任何参数为 null
     */
    public <S> SELF exists(Class<S> subEntity, Consumer<com.zsubera.jpa.spec.SubQuerySpec<S>> config) {
        return addSubqueryCondition(subEntity, config, false);
    }

    /**
     * 添加 NOT EXISTS 子查询条件。
     *
     * @param subEntity 子查询实体类型
     * @param config 子查询配置消费者
     * @param <S> 子查询实体类型
     * @return 当前构建器实例
     * @throws IllegalArgumentException 如果任何参数为 null
     */
    public <S> SELF notExists(Class<S> subEntity, Consumer<com.zsubera.jpa.spec.SubQuerySpec<S>> config) {
        return addSubqueryCondition(subEntity, config, true);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private <S> SELF addSubqueryCondition(Class<S> subEntity, Consumer<com.zsubera.jpa.spec.SubQuerySpec<S>> config,
        boolean negate) {
        if (subEntity == null) {
            throw new IllegalArgumentException("subEntity must not be null");
        }
        if (config == null) {
            throw new IllegalArgumentException("config must not be null");
        }
        BiFunction<Root<?>, CriteriaBuilder, Predicate> subqueryFn = (root, cb) -> {
            jakarta.persistence.criteria.CriteriaQuery<?> tempQuery = cb.createQuery(entityClass);
            jakarta.persistence.criteria.Subquery<S> subquery = tempQuery.subquery(subEntity);
            Root<S> subRoot = subquery.from(subEntity);
            Root<?> correlatedOuter = subquery.correlate(root);
            com.zsubera.jpa.spec.SubQuerySpec<S> subSpec =
                com.zsubera.jpa.spec.SubQuerySpec.create(subquery, subRoot, correlatedOuter, cb);
            config.accept(subSpec);
            subSpec.applyWhere();
            if (!subSpec.isSelectSet()) {
                subquery.select(subRoot);
            }
            Predicate existsPredicate = cb.exists(subquery);
            return negate ? cb.not(existsPredicate) : existsPredicate;
        };
        conditionNodes.add(new BulkConditionNode.LeafNode((BiFunction)subqueryFn));
        return self();
    }

    /**
     * 添加多字段 LIKE 搜索条件。关键字被包装为 {@code %keyword%} 并与每个给定字段匹配，使用 OR 连接。
     *
     * <p>
     * 值中的 {@code %} 或 {@code _} 字符会被转义，作为字面量处理，防止 LIKE 注入。
     *
     * @param keyword 搜索关键字
     * @param fields 一个或多个字符串属性的方法引用
     * @return 当前构建器实例
     * @throws IllegalArgumentException 如果 keyword 为 null，或 fields 为 null，或 fields 包含 null 元素
     */
    @SafeVarargs
    public final SELF multiLike(String keyword, SFunction<T, ?>... fields) {
        BulkMultiLikeResult result = resolveMultiLike(keyword, fields, this::property);
        if (result != null) {
            conditionNodes.add(new BulkConditionNode.LeafNode(buildMultiLikeFn(result.fieldNames, result.pattern)));
        }
        return self();
    }

    /**
     * 仅在 {@code condition} 为 true 时添加多字段 LIKE 搜索条件。
     *
     * @param condition 是否添加条件的标志
     * @param keyword 搜索关键字
     * @param fields 一个或多个字符串属性的方法引用
     * @return 当前构建器实例
     */
    @SafeVarargs
    public final SELF multiLike(boolean condition, String keyword, SFunction<T, ?>... fields) {
        return condition ? multiLike(keyword, fields) : self();
    }

    /**
     * 共享的 multiLike 校验和创建逻辑，供 {@link com.zsubera.jpa.update.OrConditionBuilder} 复用。
     * ponytail: 消除 AbstractBulkOperationSpec.multiLike 与 OrConditionBuilder.multiLike 的代码重复。
     *
     * @param keyword 搜索关键字
     * @param fields 字段方法引用
     * @param fieldResolver 字段名解析器
     * @param <T> 实体类型
     * @return 包含字段名数组和 LIKE 模式的 pair，如果无需添加条件则返回 null
     * @throws IllegalArgumentException 如果 keyword 为 null，或 fields 为 null，或 fields 包含 null 元素
     */
    static <T> BulkMultiLikeResult resolveMultiLike(String keyword, SFunction<T, ?>[] fields,
        java.util.function.Function<SFunction<T, ?>, String> fieldResolver) {
        if (keyword == null) {
            throw new IllegalArgumentException("keyword must not be null");
        }
        if (fields == null) {
            throw new IllegalArgumentException("fields must not be null");
        }
        if (keyword.isEmpty() || fields.length == 0) {
            return null;
        }
        String[] fieldNames = new String[fields.length];
        for (int i = 0; i < fields.length; i++) {
            if (fields[i] == null) {
                throw new IllegalArgumentException("fields[" + i + "] must not be null");
            }
            fieldNames[i] = fieldResolver.apply(fields[i]);
        }
        String pattern = wrapLikePattern(keyword);
        return new BulkMultiLikeResult(fieldNames, pattern);
    }

    static final class BulkMultiLikeResult {
        final String[] fieldNames;
        final String pattern;

        BulkMultiLikeResult(String[] fieldNames, String pattern) {
            this.fieldNames = fieldNames;
            this.pattern = pattern;
        }
    }

    /**
     * 构建多字段 LIKE OR 谓词的共享逻辑。
     */
    static java.util.function.BiFunction<jakarta.persistence.criteria.Root<?>, CriteriaBuilder, Predicate>
        buildMultiLikeFn(String[] fieldNames, String pattern) {
        return (root, cb) -> {
            List<Predicate> likes = new java.util.ArrayList<>();
            for (String fieldName : fieldNames) {
                likes.add(PredicateHelper.like(root, fieldName, pattern, cb, PredicateHelper.LIKE_ESCAPE_CHAR));
            }
            return cb.or(likes.toArray(new Predicate[0]));
        };
    }

    /**
     * 获取全局配置实例。
     *
     * @return 全局配置，如果未配置则返回默认配置
     */
    protected static com.zsubera.jpa.autoconfigure.MyJpaPlusGlobalConfig getGlobalConfig() {
        return com.zsubera.jpa.autoconfigure.GlobalConfigHolder.getConfig();
    }

    /**
     * 在执行前精确计数受影响的行数。先通过轻量预检（SELECT 1 WHERE ... LIMIT {@code limit+1}）快速探测是否超限，
     * 仅在预检未触发时执行精确 {@code SELECT COUNT(*)}，避免在大量数据下的全表扫描开销。
     * 后执行检查提供并发防御——如果并发写入导致实际影响行数超过限制，
     * 抛出异常触发事务回滚。
     *
     * @param em 实体管理器
     * @param limit 最大允许行数
     * @param operationName 操作名称（UPDATE/DELETE），用于错误消息
     * @throws IllegalStateException 如果受影响行数超过限制
     */
    protected void checkRowCountBeforeExecute(EntityManager em, long limit, String operationName) {
        if (limit >= Integer.MAX_VALUE - 1) {
            return;
        }
        // 轻量预检：SELECT 1 WHERE ... LIMIT limit+1
        // 如果返回行数 ≤ limit，则无需精确 COUNT
        long probeLimit = Math.min(limit + 1, Integer.MAX_VALUE - 1);
        if (!probeExceedsLimit(em, (int)probeLimit)) {
            return;
        }
        // 预检命中上限：执行精确 COUNT 确认
        long exactCount = countBeforeExecute(em);
        if (exactCount > limit) {
            throw new IllegalStateException("Bulk " + operationName + " would affect " + exactCount
                + " rows, which exceeds the configured limit of " + limit + " rows. "
                + "Use executeLimited() with an explicit limit, or adjust myjpa-plus.query.max-bulk-operation-rows.");
        }
    }

    /**
     * 轻量预检：用 SELECT 1 + LIMIT probeLimit 快速判断是否可能超过限制。
     *
     * @return true 表示需要进一步精确 COUNT（预检结果达到 probeLimit）
     */
    private boolean probeExceedsLimit(EntityManager em, int probeLimit) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Integer> probeQuery = cb.createQuery(Integer.class);
        Root<T> root = probeQuery.from(entityClass);
        probeQuery.select(cb.literal(1));
        Predicate[] predicates = buildPredicates(root, cb);
        if (predicates.length > 0) {
            probeQuery.where(cb.and(predicates));
        }
        jakarta.persistence.TypedQuery<Integer> q = em.createQuery(probeQuery);
        q.setMaxResults(probeLimit);
        return q.getResultList().size() >= probeLimit;
    }

    /**
     * 在执行前精确计数受影响的行数。
     *
     * @param em 实体管理器
     * @return 精确受影响的行数
     */
    protected long countBeforeExecute(EntityManager em) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
        Root<T> root = countQuery.from(entityClass);
        countQuery.select(cb.count(root));
        Predicate[] predicates = buildPredicates(root, cb);
        if (predicates.length > 0) {
            countQuery.where(cb.and(predicates));
        }
        return em.createQuery(countQuery).getSingleResult();
    }

    /** 批量操作条件节点递归深度限制，防止 StackOverflowError。 */
    private static final int MAX_BULK_RECURSION_DEPTH = 50;

    /**
     * 解析条件节点为 JPA Predicate。
     *
     * @param node 条件节点
     * @param root 查询根对象
     * @param cb 条件构建器
     * @return 解析后的 Predicate
     */
    private Predicate resolveNode(BulkConditionNode node, Root<T> root, CriteriaBuilder cb) {
        return resolveNodeWithDepth(node, root, cb, 0);
    }

    /**
     * 解析条件节点为 JPA Predicate（带深度限制）。
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private Predicate resolveNodeWithDepth(BulkConditionNode node, Root<T> root, CriteriaBuilder cb, int depth) {
        if (depth > MAX_BULK_RECURSION_DEPTH) {
            throw new MyJpaPlusException(
                "Bulk condition node recursion depth exceeded maximum limit (" + MAX_BULK_RECURSION_DEPTH
                    + "). This may indicate a circular condition tree or " + "excessively nested condition structure.");
        }
        if (node instanceof BulkConditionNode.LeafNode l) {
            return ((BiFunction<Root<T>, CriteriaBuilder, Predicate>)(BiFunction)l.fn()).apply(root, cb);
        }
        if (node instanceof BulkConditionNode.AndNode a) {
            return resolveCompositeNode(a.children(), root, cb, depth, cb::and, cb.conjunction());
        }
        if (node instanceof BulkConditionNode.OrNode o) {
            return resolveCompositeNode(o.children(), root, cb, depth, cb::or, cb.disjunction());
        }
        if (node instanceof BulkConditionNode.NotNode n) {
            return cb.not(resolveNodeWithDepth(n.child(), root, cb, depth + 1));
        }
        throw new IllegalArgumentException("Unknown BulkConditionNode type: " + node.getClass().getName());
    }

    /**
     * 解析组合节点（AND/OR）的共享逻辑。
     */
    private Predicate resolveCompositeNode(List<BulkConditionNode> children, Root<T> root, CriteriaBuilder cb,
        int depth, java.util.function.BinaryOperator<Predicate> combiner, Predicate emptyDefault) {
        List<Predicate> childPredicates = new ArrayList<>();
        for (BulkConditionNode child : children) {
            childPredicates.add(resolveNodeWithDepth(child, root, cb, depth + 1));
        }
        if (childPredicates.isEmpty()) {
            return emptyDefault;
        }
        if (childPredicates.size() == 1) {
            return childPredicates.get(0);
        }
        return childPredicates.stream().reduce(combiner).orElse(emptyDefault);
    }

    /**
     * 构建所有条件节点的 Predicate 数组。
     *
     * @param root 查询根对象
     * @param cb 条件构建器
     * @return Predicate 数组
     */
    protected Predicate[] buildPredicates(Root<T> root, CriteriaBuilder cb) {
        if (conditionNodes.isEmpty()) {
            return new Predicate[0];
        }
        List<Predicate> predicates = new ArrayList<>();
        for (BulkConditionNode node : conditionNodes) {
            predicates.add(resolveNode(node, root, cb));
        }
        return predicates.toArray(new Predicate[0]);
    }
}
