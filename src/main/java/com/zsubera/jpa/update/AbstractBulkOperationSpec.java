package com.zsubera.jpa.update;

import static com.zsubera.jpa.spec.ConditionalMethods.wrapLikePattern;

import com.zsubera.jpa.exception.MyJpaPlusException;
import com.zsubera.jpa.spec.BulkConditionSupport;
import com.zsubera.jpa.spec.ConditionalMethods;
import com.zsubera.jpa.spec.PredicateHelper;
import com.zsubera.jpa.spec.SFunction;
import com.zsubera.jpa.util.LambdaUtils;
import com.zsubera.jpa.util.QueryTimeoutHelper;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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
 * <li>{@link com.zsubera.jpa.projection.ProjectionSpec.ProjectionJoinGroup} — 投影 JOIN 条件</li>
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

    /**
     * 构造函数，初始化实体类类型。
     *
     * @param entityClass 实体类类型
     */
    protected AbstractBulkOperationSpec(Class<T> entityClass) {
        this.entityClass = entityClass;
    }

    /**
     * 返回当前构建器实例，用于链式调用。
     *
     * @return 当前构建器实例
     */
    @SuppressWarnings("unchecked")
    public SELF self() {
        return (SELF)this;
    }

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
     * 在新事务中执行批量操作，如果当前没有活动事务则创建新事务，否则在当前事务中执行。
     *
     * @param em 实体管理器
     * @return 受影响的行数
     */
    public int executeInTransaction(EntityManager em) {
        return executeInTransaction(em, this::doExecute);
    }

    /**
     * 在新事务中执行给定操作，如果当前没有活动事务则创建新事务，否则在当前事务中执行。
     *
     * <p>
     * 此重载方法允许子类执行自定义操作（如无条件 deleteAll）并进行正确的事务管理。
     *
     * @param em 实体管理器
     * @param operation 要执行的操作
     * @return 受影响的行数
     */
    protected int executeInTransaction(EntityManager em, Function<EntityManager, Integer> operation) {
        // 首先检查 Spring 是否管理事务（容器管理的 JTA 或 Spring 事务）
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            return operation.apply(em);
        }
        // 没有 Spring 事务 — 使用 JPA 的 EntityTransaction 用于独立场景。
        EntityTransaction tx = em.getTransaction();
        if (tx == null) {
            return executeInJtaEnvironment(em, operation);
        }
        return executeWithEntityTransaction(em, tx, operation);
    }

    /**
     * 在 JTA 环境中执行操作。
     *
     * @param em 实体管理器
     * @param operation 要执行的操作
     * @return 受影响的行数
     */
    private int executeInJtaEnvironment(EntityManager em, Function<EntityManager, Integer> operation) {
        if (log.isDebugEnabled()) {
            log.debug(
                "JTA environment detected (getTransaction() returned null), executing with container-managed transaction");
        }
        try {
            return operation.apply(em);
        } catch (jakarta.persistence.TransactionRequiredException e) {
            throw new MyJpaPlusException("No active transaction in JTA environment. "
                + "Ensure a container-managed transaction is active, or use @Transactional annotation.", e);
        }
    }

    /**
     * 使用 JPA EntityTransaction 执行操作。
     *
     * @param em 实体管理器
     * @param tx 实体事务
     * @param operation 要执行的操作
     * @return 受影响的行数
     */
    private int executeWithEntityTransaction(EntityManager em, EntityTransaction tx,
        Function<EntityManager, Integer> operation) {
        boolean isNewTransaction = !tx.isActive();
        if (isNewTransaction) {
            tx.begin();
        }
        try {
            int result = operation.apply(em);
            if (isNewTransaction) {
                tx.commit();
            }
            return result;
        } catch (RuntimeException e) {
            if (isNewTransaction) {
                rollbackIfActive(tx, e);
            }
            throw e;
        } catch (Exception e) {
            if (isNewTransaction) {
                rollbackIfActive(tx, e);
            }
            log.error("Unexpected checked exception in bulk operation (type: {}): {}", e.getClass().getName(),
                e.getMessage(), e);
            throw new MyJpaPlusException(
                "Bulk operation failed: " + e.getClass().getSimpleName() + " - " + e.getMessage(), e);
        }
    }

    /**
     * 如果事务处于活动状态则回滚，并将回滚异常添加为原始异常的抑制异常。
     *
     * @param tx 实体事务
     * @param original 原始异常
     */
    private void rollbackIfActive(EntityTransaction tx, Exception original) {
        if (tx.isActive()) {
            try {
                tx.rollback();
            } catch (Exception rollbackEx) {
                original.addSuppressed(rollbackEx);
            }
        }
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

    /** 批量操作条件树的密封节点类型。支持 AND、OR、NOT 和叶子谓词节点。 */
    sealed interface BulkConditionNode {
        /** 叶子谓词函数节点。 */
        record LeafNode(BiFunction<Root<?>, CriteriaBuilder, Predicate> fn) implements BulkConditionNode {
        }

        /** AND 子节点组。 */
        @SuppressFBWarnings({"EI_EXPOSE_REP", "EI_EXPOSE_REP2"})
        record AndNode(List<BulkConditionNode> children) implements BulkConditionNode {
        }

        /** OR 子节点组。 */
        @SuppressFBWarnings({"EI_EXPOSE_REP", "EI_EXPOSE_REP2"})
        record OrNode(List<BulkConditionNode> children) implements BulkConditionNode {
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
        conditionNodes.add(new BulkConditionNode.OrNode(List.copyOf(children)));
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
        // [FIX] P1-1: 使用 AndNode 组合子条件，与 QuerySpec.not() 语义一致
        // 之前使用 OrNode 导致 NOT(A OR B)，与 QuerySpec.not() 的 NOT(A AND B) 语义不同
        BulkConditionNode combined =
            children.size() == 1 ? children.get(0) : new BulkConditionNode.AndNode(List.copyOf(children));
        conditionNodes.add(new BulkConditionNode.NotNode(combined));
        return self();
    }
    // ---- 条件方法由 BulkConditionSupport 默认实现提供 ----

    @Override
    public SELF eqStrict(SFunction<T, ?> field, Object value) {
        if (value == null) {
            throw new IllegalArgumentException("value must not be null. Use isNull() for null comparisons.");
        }
        return eq(field, value);
    }

    @Override
    public SELF neStrict(SFunction<T, ?> field, Object value) {
        if (value == null) {
            throw new IllegalArgumentException("value must not be null. Use isNotNull() for null comparisons.");
        }
        return ne(field, value);
    }

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
        if (subEntity == null) {
            throw new IllegalArgumentException("subEntity must not be null");
        }
        if (config == null) {
            throw new IllegalArgumentException("config must not be null");
        }
        BiFunction<Root<?>, CriteriaBuilder, Predicate> existsFn = (root, cb) -> {
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
            return cb.exists(subquery);
        };
        conditionNodes.add(new BulkConditionNode.LeafNode((BiFunction)existsFn));
        return self();
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
        if (subEntity == null) {
            throw new IllegalArgumentException("subEntity must not be null");
        }
        if (config == null) {
            throw new IllegalArgumentException("config must not be null");
        }
        BiFunction<Root<?>, CriteriaBuilder, Predicate> notExistsFn = (root, cb) -> {
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
            return cb.not(cb.exists(subquery));
        };
        conditionNodes.add(new BulkConditionNode.LeafNode((BiFunction)notExistsFn));
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
        if (keyword == null) {
            throw new IllegalArgumentException("keyword must not be null");
        }
        if (fields == null) {
            throw new IllegalArgumentException("fields must not be null");
        }
        if (!keyword.isEmpty() && fields.length > 0) {
            String[] fieldNames = new String[fields.length];
            for (int i = 0; i < fields.length; i++) {
                if (fields[i] == null) {
                    throw new IllegalArgumentException("fields[" + i + "] must not be null");
                }
                fieldNames[i] = property(fields[i]);
            }
            String pattern = wrapLikePattern(keyword);
            BiFunction<Root<?>, CriteriaBuilder, Predicate> multiLikeFn = (root, cb) -> {
                List<Predicate> likes = new java.util.ArrayList<>();
                for (String fieldName : fieldNames) {
                    likes.add(PredicateHelper.like(root, fieldName, pattern, cb, PredicateHelper.LIKE_ESCAPE_CHAR));
                }
                return cb.or(likes.toArray(new Predicate[0]));
            };
            conditionNodes.add(new BulkConditionNode.LeafNode((BiFunction)multiLikeFn));
        }
        return self();
    }

    /**
     * 仅在 {@code condition} 为 true 时添加严格等值条件。
     *
     * @param condition 是否添加条件的标志
     * @param field 实体属性引用
     * @param value 比较值
     * @return 当前构建器实例
     */
    public SELF eqStrict(boolean condition, SFunction<T, ?> field, Object value) {
        return condition ? eqStrict(field, value) : self();
    }

    /**
     * 仅在 {@code condition} 为 true 时添加严格不等于条件。
     *
     * @param condition 是否添加条件的标志
     * @param field 实体属性引用
     * @param value 比较值
     * @return 当前构建器实例
     */
    public SELF neStrict(boolean condition, SFunction<T, ?> field, Object value) {
        return condition ? neStrict(field, value) : self();
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
     * 为批量操作查询应用全局默认超时。
     *
     * @param query JPA Query 实例
     */
    protected void applyTimeout(jakarta.persistence.Query query) {
        QueryTimeoutHelper.applyTimeout(query);
    }

    /**
     * 解析条件节点为 JPA Predicate。
     *
     * @param node 条件节点
     * @param root 查询根对象
     * @param cb 条件构建器
     * @return 解析后的 Predicate
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private Predicate resolveNode(BulkConditionNode node, Root<T> root, CriteriaBuilder cb) {
        if (node instanceof BulkConditionNode.LeafNode l) {
            return ((BiFunction<Root<T>, CriteriaBuilder, Predicate>)(BiFunction)l.fn()).apply(root, cb);
        }
        if (node instanceof BulkConditionNode.AndNode a) {
            List<Predicate> childPredicates = new ArrayList<>();
            for (BulkConditionNode child : a.children()) {
                childPredicates.add(resolveNode(child, root, cb));
            }
            if (childPredicates.isEmpty()) {
                return cb.conjunction();
            }
            if (childPredicates.size() == 1) {
                return childPredicates.get(0);
            }
            return cb.and(childPredicates.toArray(new Predicate[0]));
        }
        if (node instanceof BulkConditionNode.OrNode o) {
            List<Predicate> childPredicates = new ArrayList<>();
            for (BulkConditionNode child : o.children()) {
                childPredicates.add(resolveNode(child, root, cb));
            }
            if (childPredicates.isEmpty()) {
                return cb.disjunction();
            }
            if (childPredicates.size() == 1) {
                return childPredicates.get(0);
            }
            return cb.or(childPredicates.toArray(new Predicate[0]));
        }
        if (node instanceof BulkConditionNode.NotNode n) {
            return cb.not(resolveNode(n.child(), root, cb));
        }
        throw new IllegalArgumentException("Unknown BulkConditionNode type: " + node.getClass().getName());
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
