package com.zsubera.jpa.util;

import com.zsubera.jpa.spec.QuerySpec;
import jakarta.persistence.EntityGraph;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Subgraph;
import jakarta.persistence.TypedQuery;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 用于与 {@link QuerySpec} 配合动态构建和应用 JPA {@link EntityGraph} 抓取策略的辅助类。
 *
 * <p>
 * JPA {@link EntityGraph} 允许声明式地指定查询时需要急切加载的关联关系， 提供了 {@code FETCH JOIN} 的替代方案，并支持在多个查询间复用。
 *
 * <p>
 * 使用示例：
 *
 * <pre>{@code
 * // 创建一个抓取 'roles' 和 'roles.permissions' 的实体图
 * EntityGraphHelper<User> graph = EntityGraphHelper.forEntity(User.class).add("roles").add("roles.permissions");
 *
 * // 使用 nest() 方法进行链式嵌套（等价于上面的写法）
 * EntityGraphHelper<User> graph = EntityGraphHelper.forEntity(User.class).add("roles").nest("permissions");
 *
 * // 为仓库调用构建查询提示：
 * Map<String, Object> hints = graph.toHints(entityManager);
 * List<User> users = repository.findAll(spec, hints);
 * }</pre>
 */
public final class EntityGraphHelper<T> {

    private static final Logger log = LoggerFactory.getLogger(EntityGraphHelper.class);

    /** JPA 查询提示键，用于 fetchgraph 模式。 */
    public static final String HINT_FETCHGRAPH = "jakarta.persistence.fetchgraph";

    /** JPA 查询提示键，用于 loadgraph 模式。 */
    public static final String HINT_LOADGRAPH = "jakarta.persistence.loadgraph";

    private final Class<T> entityClass;
    private final Map<String, String[]> attributePaths = new HashMap<>();
    private boolean loadGraphType = false;
    private String lastAddedPath = null;

    private EntityGraphHelper(Class<T> entityClass) {
        this.entityClass = entityClass;
    }

    /**
     * 为指定实体类创建新的 {@code EntityGraphHelper} 实例。
     *
     * @param entityClass 实体类
     * @param <T> 实体类型
     * @return 新的 EntityGraphHelper 实例
     */
    public static <T> EntityGraphHelper<T> forEntity(Class<T> entityClass) {
        if (entityClass == null) {
            throw new IllegalArgumentException("entityClass must not be null");
        }
        return new EntityGraphHelper<>(entityClass);
    }

    /**
     * 设置图类型为 LOAD（提示 JPA 在已有的急切加载属性之外，额外急切加载指定属性）。
     */
    public EntityGraphHelper<T> loadGraph() {
        this.loadGraphType = true;
        return this;
    }

    /**
     * 设置图类型为 FETCH（仅急切加载指定属性，其余属性延迟加载）。此为默认行为。
     */
    public EntityGraphHelper<T> fetchGraph() {
        this.loadGraphType = false;
        return this;
    }

    /**
     * 向实体图添加单个属性路径。支持点号表示法的嵌套路径：{@code "roles.permissions"}。
     *
     * @param attributePath 属性路径（例如 "roles"、"customer.address"）
     * @return 当前实例，支持链式调用
     */
    public EntityGraphHelper<T> add(String attributePath) {
        if (attributePath == null || attributePath.isEmpty()) {
            throw new IllegalArgumentException("attributePath must not be null or empty");
        }
        int dotIndex = attributePath.indexOf('.');
        if (dotIndex > 0) {
            String root = attributePath.substring(0, dotIndex);
            String subpath = attributePath.substring(dotIndex + 1);
            attributePaths.merge(root, new String[] {subpath}, (old, val) -> appendToArray(old, subpath));
        } else {
            // 使用 merge 而非 put 以保留已有的子路径
            // 例如：先 add("roles.permissions") 再 add("roles") 应保留 "permissions"
            attributePaths.merge(attributePath, new String[0], (old, val) -> old);
        }
        lastAddedPath = attributePath;
        return this;
    }

    /**
     * 向实体图添加多个属性路径。
     *
     * @param attributePaths 一个或多个属性路径
     * @return 当前实例，支持链式调用
     */
    public EntityGraphHelper<T> add(String... attributePaths) {
        for (String path : attributePaths) {
            add(path);
        }
        return this;
    }

    /**
     * 移除指定的属性路径。
     *
     * @param attributePath 要移除的属性路径
     * @return 当前实例，支持链式调用
     * @throws IllegalArgumentException 如果 attributePath 为 null 或空
     */
    public EntityGraphHelper<T> remove(String attributePath) {
        if (attributePath == null || attributePath.isEmpty()) {
            throw new IllegalArgumentException("attributePath must not be null or empty");
        }
        attributePaths.remove(attributePath);
        if (attributePath.equals(lastAddedPath)) {
            lastAddedPath = null;
        }
        return this;
    }

    /**
     * 清除所有属性路径。
     *
     * @return 当前实例，支持链式调用
     */
    public EntityGraphHelper<T> clear() {
        attributePaths.clear();
        lastAddedPath = null;
        return this;
    }

    /**
     * 在上一次添加的路径基础上进行嵌套。等价于 {@code add("parent.child")} 的链式写法。
     *
     * <p>
     * 示例：
     *
     * <pre>{@code
     * // 传统写法
     * graph.add("roles").add("roles.permissions").add("roles.permissions.menu");
     *
     * // 链式嵌套写法（等价）
     * graph.add("roles").nest("permissions").nest("menu");
     * }</pre>
     *
     * @param attributeName 嵌套的属性名称
     * @return 当前实例，支持链式调用
     * @throws IllegalStateException 如果没有先前的路径可嵌套
     * @throws IllegalArgumentException 如果 attributeName 为 null 或空
     */
    public EntityGraphHelper<T> nest(String attributeName) {
        if (lastAddedPath == null) {
            throw new IllegalStateException("No previous path to nest from. Call add() first.");
        }
        if (attributeName == null || attributeName.isEmpty()) {
            throw new IllegalArgumentException("attributeName must not be null or empty");
        }
        String nestedPath = lastAddedPath + "." + attributeName;
        add(nestedPath);
        return this;
    }

    /**
     * 将此实体图应用到指定的 {@link TypedQuery}。
     *
     * @param query 要应用实体图的类型化查询
     * @param em 用于创建实体图的 EntityManager
     * @param <R> 查询结果类型
     * @return 应用了实体图提示的同一查询对象
     */
    public <R> TypedQuery<R> apply(TypedQuery<R> query, EntityManager em) {
        if (attributePaths.isEmpty()) {
            return query;
        }
        EntityGraph<T> graph = buildGraph(em);
        query.setHint(getHintName(), graph);
        if (log.isDebugEnabled()) {
            log.debug("Applied {} entity graph with {} attributes to query", getHintName(), attributePaths.keySet());
        }
        return query;
    }

    /**
     * 将此实体图转换为 JPA 查询提示映射，用于支持 hints 参数的 Repository 查询方法（例如通过 {@code @QueryHints}）。
     *
     * @param em EntityManager
     * @return 包含实体图的 JPA 查询提示映射
     */
    public Map<String, Object> toHints(EntityManager em) {
        Map<String, Object> hints = new HashMap<>();
        if (!attributePaths.isEmpty()) {
            EntityGraph<T> graph = buildGraph(em);
            hints.put(getHintName(), graph);
        }
        return hints;
    }

    /**
     * 递归添加属性节点到子图，支持多级嵌套路径如 "b.c.d"。
     *
     * @param subgraph 子图
     * @param path 属性路径
     */
    private void addAttributeNodeRecursive(Subgraph<Object> subgraph, String path) {
        int dotIndex = path.indexOf('.');
        if (dotIndex > 0) {
            String root = path.substring(0, dotIndex);
            String remaining = path.substring(dotIndex + 1);
            Subgraph<Object> nested = subgraph.addSubgraph(root);
            addAttributeNodeRecursive(nested, remaining);
        } else {
            subgraph.addAttributeNodes(path);
        }
    }

    /**
     * 构建并返回 JPA {@link EntityGraph}。
     *
     * @param em 用于创建图的 EntityManager
     * @return 构建的实体图
     */
    public EntityGraph<T> buildGraph(EntityManager em) {
        EntityGraph<T> graph = em.createEntityGraph(entityClass);

        for (Map.Entry<String, String[]> entry : attributePaths.entrySet()) {
            String attributeName = entry.getKey();
            String[] subpaths = entry.getValue();

            if (subpaths.length == 0) {
                graph.addAttributeNodes(attributeName);
            } else {
                Subgraph<Object> subgraph = graph.addSubgraph(attributeName);
                for (String subpath : subpaths) {
                    // 支持多级嵌套路径，如 "b.c.d"
                    addAttributeNodeRecursive(subgraph, subpath);
                }
            }
        }

        if (log.isDebugEnabled()) {
            log.debug("Built {} entity graph for {}: {}", getHintName(), entityClass.getSimpleName(),
                attributePaths.keySet());
        }
        return graph;
    }

    /**
     * 返回当前图类型的 JPA 提示键。
     *
     * <ul>
     * <li>FETCH 图: {@code jakarta.persistence.fetchgraph}
     * <li>LOAD 图: {@code jakarta.persistence.loadgraph}
     * </ul>
     *
     * @return JPA 提示键
     */
    public String getHintName() {
        return loadGraphType ? HINT_LOADGRAPH : HINT_FETCHGRAPH;
    }

    /**
     * 将元素追加到数组末尾，返回新数组。
     *
     * @param old 原数组
     * @param element 要追加的元素
     * @return 包含新元素的新数组
     */
    private static String[] appendToArray(String[] old, String element) {
        String[] combined = new String[old.length + 1];
        System.arraycopy(old, 0, combined, 0, old.length);
        combined[old.length] = element;
        return combined;
    }
}
