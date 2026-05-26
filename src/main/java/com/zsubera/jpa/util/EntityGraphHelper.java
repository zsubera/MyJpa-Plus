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
 * Helper for building and applying JPA {@link EntityGraph} fetch strategies dynamically with {@link
 * QuerySpec}.
 *
 * <p>JPA {@link EntityGraph}s allow declarative specification of which associations to fetch
 * eagerly at query time, providing an alternative to {@code FETCH JOIN} with support for reuse
 * across queries.
 *
 * <p>Example:
 *
 * <pre>{@code
 * // Create an entity graph that fetches 'roles' and 'roles.permissions'
 * EntityGraphHelper<User> graph = EntityGraphHelper.forEntity(User.class)
 *     .add("roles")
 *     .add("roles", "permissions");
 *
 * // Build hints for repository invocation:
 * Map<String, Object> hints = graph.toHints(entityManager);
 * List<User> users = repository.findAll(spec, hints);
 * }</pre>
 */
public final class EntityGraphHelper<T> {

  private static final Logger log = LoggerFactory.getLogger(EntityGraphHelper.class);

  /** JPA hint key for javax.persistence.fetchgraph. */
  public static final String HINT_FETCHGRAPH = "jakarta.persistence.fetchgraph";

  /** JPA hint key for javax.persistence.loadgraph. */
  public static final String HINT_LOADGRAPH = "jakarta.persistence.loadgraph";

  private final Class<T> entityClass;
  private final Map<String, String[]> attributePaths = new HashMap<>();
  private boolean loadGraphType = false;

  private EntityGraphHelper(Class<T> entityClass) {
    this.entityClass = entityClass;
  }

  /**
   * Creates a new {@code EntityGraphHelper} for the given entity class.
   *
   * @param entityClass the entity class
   * @param <T> the entity type
   * @return a new EntityGraphHelper
   */
  public static <T> EntityGraphHelper<T> forEntity(Class<T> entityClass) {
    return new EntityGraphHelper<>(entityClass);
  }

  /**
   * Sets the graph type to LOAD (hints JPA to load the specified attributes eagerly in addition to
   * any attributes that are already eagerly loaded).
   */
  public EntityGraphHelper<T> loadGraph() {
    this.loadGraphType = true;
    return this;
  }

  /**
   * Sets the graph type to FETCH (only the specified attributes are fetched eagerly; all others are
   * loaded lazily). This is the default.
   */
  public EntityGraphHelper<T> fetchGraph() {
    this.loadGraphType = false;
    return this;
  }

  /**
   * Adds a single attribute path to the entity graph. Use dot notation for nested paths: {@code
   * "roles.permissions"}.
   *
   * @param attributePath the attribute path (e.g. "roles", "customer.address")
   * @return this helper for chaining
   */
  public EntityGraphHelper<T> add(String attributePath) {
    if (attributePath == null || attributePath.isEmpty()) {
      throw new IllegalArgumentException("attributePath must not be null or empty");
    }
    int dotIndex = attributePath.indexOf('.');
    if (dotIndex > 0) {
      String root = attributePath.substring(0, dotIndex);
      String subpath = attributePath.substring(dotIndex + 1);
      attributePaths.merge(
          root,
          new String[] {subpath},
          (old, val) -> {
            String[] combined = new String[old.length + 1];
            System.arraycopy(old, 0, combined, 0, old.length);
            combined[old.length] = subpath;
            return combined;
          });
    } else {
      // Use merge instead of put to preserve existing subpaths
      // e.g. add("roles.permissions") then add("roles") should keep "permissions"
      attributePaths.merge(attributePath, new String[0], (old, val) -> old);
    }
    return this;
  }

  /**
   * Adds multiple attribute paths to the entity graph.
   *
   * @param attributePaths one or more attribute paths
   * @return this helper for chaining
   */
  public EntityGraphHelper<T> add(String... attributePaths) {
    for (String path : attributePaths) {
      add(path);
    }
    return this;
  }

  /**
   * Applies this entity graph to the given {@link TypedQuery} using the provided EntityManager.
   *
   * @param query the typed query to apply the graph to
   * @param em the EntityManager for creating the entity graph
   * @param <R> the query result type
   * @return the same query with the entity graph hint applied
   */
  @SuppressWarnings({"unchecked", "rawtypes"})
  public <R> TypedQuery<R> apply(TypedQuery<R> query, EntityManager em) {
    if (attributePaths.isEmpty()) {
      return query;
    }
    EntityGraph<T> graph = buildGraph(em);
    query.setHint(getHintName(), graph);
    if (log.isDebugEnabled()) {
      log.debug(
          "Applied {} entity graph with {} attributes to query",
          getHintName(),
          attributePaths.keySet());
    }
    return query;
  }

  /**
   * Converts this entity graph into a map of JPA query hints for use with repository find methods
   * that accept hints (e.g., via {@code @QueryHints}).
   *
   * @param em the EntityManager
   * @return a Map of JPA query hints containing the entity graph
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
   * Builds and returns the JPA {@link EntityGraph}.
   *
   * @param em the EntityManager to create the graph from
   * @return the built entity graph
   */
  /**
   * Recursively adds attribute nodes to a subgraph, splitting multi-level paths like "b.c.d" into
   * nested subgraphs.
   */
  @SuppressWarnings({"unchecked", "rawtypes"})
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

  @SuppressWarnings({"unchecked", "rawtypes"})
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
          // Support multi-level nested paths like "b.c.d"
          addAttributeNodeRecursive(subgraph, subpath);
        }
      }
    }

    if (log.isDebugEnabled()) {
      log.debug(
          "Built {} entity graph for {}: {}",
          getHintName(),
          entityClass.getSimpleName(),
          attributePaths.keySet());
    }
    return graph;
  }

  /**
   * Returns the JPA hint key for the current graph type.
   *
   * <ul>
   *   <li>FETCH graph: {@code jakarta.persistence.fetchgraph}
   *   <li>LOAD graph: {@code jakarta.persistence.loadgraph}
   * </ul>
   */
  public String getHintName() {
    return loadGraphType ? HINT_LOADGRAPH : HINT_FETCHGRAPH;
  }
}
