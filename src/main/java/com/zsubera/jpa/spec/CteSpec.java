package com.zsubera.jpa.spec;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * CTE（Common Table Expression，公共表表达式）构建器。
 *
 * <p>
 * JPA Criteria API 不原生支持 CTE，因此此类使用原生 SQL 查询机制（{@link EntityManager#createNativeQuery(String)}）构建类型安全的 CTE 查询。支持
 * PostgreSQL 的 {@code WITH} 和 {@code WITH RECURSIVE} 语法。
 *
 * <p>
 * 使用示例：
 *
 * <pre>{@code
 * // 非递归 CTE
 * List<Object[]> results =
 *     CteSpec.with("active_users").columns("id", "name").as("SELECT id, name FROM users WHERE active = true")
 *         .select("SELECT * FROM active_users WHERE name LIKE :name").setParameter("name", "%John%").getResultList(em);
 *
 * // 递归 CTE
 * List<Object[]> results = CteSpec.withRecursive("category_tree").columns("id", "name", "parent_id", "depth")
 *     .as("SELECT id, name, parent_id, 0 FROM categories WHERE parent_id IS NULL" + " UNION ALL "
 *         + "SELECT c.id, c.name, c.parent_id, ct.depth + 1 FROM categories c "
 *         + "JOIN category_tree ct ON c.parent_id = ct.id")
 *     .select("SELECT * FROM category_tree ORDER BY depth").getResultList(em);
 * }</pre>
 *
 * <p>
 * <strong>此类是可变的且非线程安全。</strong>每次查询操作应创建新的 {@code CteSpec} 实例。
 */
@SuppressFBWarnings("SE_BAD_FIELD")
public class CteSpec {

    private static final Logger log = LoggerFactory.getLogger(CteSpec.class);

    /**
     * 安全标识符正则表达式：仅允许字母、数字和下划线，防止 SQL 注入。
     *
     * <p>
     * 用于校验 CTE 名称和列名，防止恶意 SQL 拼接。
     */
    private static final Pattern SAFE_IDENTIFIER_PATTERN = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]*$");

    private final List<CteEntry> cteEntries = new ArrayList<>();
    private String mainSql;
    private final Map<String, Object> parameters = new LinkedHashMap<>();
    private boolean recursive;

    private CteSpec(boolean recursive) {
        this.recursive = recursive;
    }

    /**
     * 创建非递归 CTE 构建器。
     *
     * @param cteName CTE 名称
     * @return 新的 CteSpec 实例
     * @throws IllegalArgumentException 如果 cteName 为 null 或空
     */
    public static CteSpec with(String cteName) {
        if (cteName == null || cteName.isEmpty()) {
            throw new IllegalArgumentException("cteName must not be null or empty");
        }
        if (!SAFE_IDENTIFIER_PATTERN.matcher(cteName).matches()) {
            throw new IllegalArgumentException(
                "cteName contains invalid characters: " + cteName + ". Only alphanumeric and underscore are allowed.");
        }
        return new CteSpec(false).addCte(cteName);
    }

    /**
     * 创建递归 CTE 构建器（{@code WITH RECURSIVE}）。
     *
     * @param cteName CTE 名称
     * @return 新的 CteSpec 实例
     * @throws IllegalArgumentException 如果 cteName 为 null 或空
     */
    public static CteSpec withRecursive(String cteName) {
        if (cteName == null || cteName.isEmpty()) {
            throw new IllegalArgumentException("cteName must not be null or empty");
        }
        if (!SAFE_IDENTIFIER_PATTERN.matcher(cteName).matches()) {
            throw new IllegalArgumentException(
                "cteName contains invalid characters: " + cteName + ". Only alphanumeric and underscore are allowed.");
        }
        return new CteSpec(true).addCte(cteName);
    }

    /**
     * 为当前 CTE 添加列别名。
     *
     * @param columns 列名列表
     * @return 当前 CteSpec 实例，支持链式调用
     * @throws IllegalArgumentException 如果 columns 为空或包含 null 元素
     */
    public CteSpec columns(String... columns) {
        if (columns == null || columns.length == 0) {
            throw new IllegalArgumentException("columns must not be empty");
        }
        for (String col : columns) {
            if (col == null || col.isEmpty()) {
                throw new IllegalArgumentException("columns must not contain null or empty elements");
            }
            if (!SAFE_IDENTIFIER_PATTERN.matcher(col).matches()) {
                throw new IllegalArgumentException("column name contains invalid characters: " + col
                    + ". Only alphanumeric and underscore are allowed.");
            }
        }
        CteEntry current = currentCte();
        current.columns = Arrays.asList(columns);
        return this;
    }

    /**
     * 设置当前 CTE 的查询 SQL。
     *
     * <p>
     * <strong>安全警告：</strong>SQL 字符串不应包含用户输入。CTE 名称和列名已通过正则校验防止注入， 但 SQL
     * 本身由开发者负责安全。请使用参数化查询（{@link #setParameter}）绑定用户输入值。
     *
     * <p>
     * <strong>SQL 注入防护：</strong>此方法会检测 SQL 中是否包含危险关键字（DROP、TRUNCATE、GRANT、REVOKE、EXEC），
     * 并在检测到时记录安全警告日志。此检测为启发式防护，不能替代参数化查询。
     *
     * @param sql CTE 查询 SQL（不含外层括号）
     * @return 当前 CteSpec 实例，支持链式调用
     * @throws IllegalArgumentException 如果 sql 为 null 或空
     */
    public CteSpec as(String sql) {
        if (sql == null || sql.isEmpty()) {
            throw new IllegalArgumentException("sql must not be null or empty");
        }
        checkSqlSafety(sql, "CTE");
        CteEntry current = currentCte();
        current.sql = sql;
        return this;
    }

    /**
     * 添加另一个 CTE（链式添加多个 CTE）。
     *
     * @param cteName CTE 名称
     * @return 当前 CteSpec 实例，支持链式调用
     * @throws IllegalArgumentException 如果 cteName 为 null 或空
     */
    public CteSpec and(String cteName) {
        if (cteName == null || cteName.isEmpty()) {
            throw new IllegalArgumentException("cteName must not be null or empty");
        }
        if (!SAFE_IDENTIFIER_PATTERN.matcher(cteName).matches()) {
            throw new IllegalArgumentException(
                "cteName contains invalid characters: " + cteName + ". Only alphanumeric and underscore are allowed.");
        }
        return addCte(cteName);
    }

    /**
     * 设置主查询 SQL。CTE 在此查询之前定义。
     *
     * <p>
     * <strong>安全警告：</strong>SQL 字符串不应包含用户输入。请使用参数化查询（{@link #setParameter}）绑定用户输入值。
     *
     * <p>
     * <strong>SQL 注入防护：</strong>此方法会检测 SQL 中是否包含危险关键字（DROP、TRUNCATE、GRANT、REVOKE、EXEC），
     * 并在检测到时记录安全警告日志。此检测为启发式防护，不能替代参数化查询。
     *
     * @param sql 主查询 SQL
     * @return 当前 CteSpec 实例，支持链式调用
     * @throws IllegalArgumentException 如果 sql 为 null 或空
     */
    public CteSpec select(String sql) {
        if (sql == null || sql.isEmpty()) {
            throw new IllegalArgumentException("sql must not be null or empty");
        }
        checkSqlSafety(sql, "main query");
        this.mainSql = sql;
        return this;
    }

    /**
     * 绑定命名参数。
     *
     * @param name 参数名
     * @param value 参数值
     * @return 当前 CteSpec 实例，支持链式调用
     * @throws IllegalArgumentException 如果 name 为 null
     */
    public CteSpec setParameter(String name, Object value) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("name must not be null or empty");
        }
        parameters.put(name, value);
        return this;
    }

    /**
     * 构建完整的 CTE SQL 并执行查询，返回结果列表。
     *
     * @param em EntityManager 实例
     * @return 查询结果列表（每行为 Object[]）
     * @throws IllegalStateException 如果 CTE 或主查询未完整配置
     * @throws IllegalArgumentException 如果 em 为 null
     */
    @SuppressWarnings("unchecked")
    public List<Object[]> getResultList(EntityManager em) {
        if (em == null) {
            throw new IllegalArgumentException("em must not be null");
        }
        String sql = buildSql();
        log.debug("CteSpec: executing native query (length={})", sql.length());
        Query query = em.createNativeQuery(sql);
        applyParameters(query);
        List<?> rawResults = query.getResultList();
        List<Object[]> results = new ArrayList<>();
        for (Object row : rawResults) {
            if (row instanceof Object[] arr) {
                results.add(arr);
            } else {
                results.add(new Object[] {row});
            }
        }
        return results;
    }

    /**
     * 构建完整的 CTE SQL 并执行查询，返回单个结果。
     *
     * @param em EntityManager 实例
     * @return 单个查询结果（Object[]），如果无结果则返回 null
     * @throws IllegalStateException 如果 CTE 或主查询未完整配置
     * @throws IllegalArgumentException 如果 em 为 null
     */
    @SuppressWarnings("unchecked")
    public Object[] getSingleResult(EntityManager em) {
        if (em == null) {
            throw new IllegalArgumentException("em must not be null");
        }
        String sql = buildSql();
        log.debug("CteSpec: executing native query for single result (length={})", sql.length());
        Query query = em.createNativeQuery(sql);
        applyParameters(query);
        List<?> results = query.getResultList();
        if (results.isEmpty()) {
            return null;
        }
        // P2: Warn when multiple results are returned but only first is used
        if (results.size() > 1) {
            log.warn("CteSpec.getSingleResult() returned {} results but only the first is used. "
                + "Consider adding LIMIT 1 to your query or using getResultList() instead.", results.size());
        }
        Object row = results.get(0);
        if (row instanceof Object[] arr) {
            return arr;
        }
        return new Object[] {row};
    }

    /**
     * 仅构建 SQL 字符串，不执行。用于调试或与其他组件集成。
     *
     * @return 完整的 CTE SQL 字符串
     * @throws IllegalStateException 如果 CTE 或主查询未完整配置
     */
    public String buildSql() {
        if (cteEntries.isEmpty()) {
            throw new IllegalStateException("No CTE defined. Use with() or withRecursive() first.");
        }
        if (mainSql == null || mainSql.isEmpty()) {
            throw new IllegalStateException("Main query not set. Use select() to define the main query.");
        }
        for (int i = 0; i < cteEntries.size(); i++) {
            CteEntry entry = cteEntries.get(i);
            if (entry.sql == null || entry.sql.isEmpty()) {
                throw new IllegalStateException("CTE '" + entry.name + "' has no SQL defined. Use as() to set it.");
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append(recursive ? "WITH RECURSIVE " : "WITH ");
        for (int i = 0; i < cteEntries.size(); i++) {
            CteEntry entry = cteEntries.get(i);
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(entry.name);
            if (entry.columns != null && !entry.columns.isEmpty()) {
                sb.append("(");
                sb.append(String.join(", ", entry.columns));
                sb.append(")");
            }
            sb.append(" AS (");
            sb.append(entry.sql);
            sb.append(")");
        }
        sb.append(" ");
        sb.append(mainSql);
        return sb.toString();
    }

    // ---- 内部方法 ----

    /** 危险 SQL 关键字黑名单，用于启发式 SQL 注入检测。 */
    private static final java.util.List<String> DANGEROUS_KEYWORDS =
        java.util.List.of("DROP", "TRUNCATE", "GRANT", "REVOKE", "EXEC", "EXECUTE", "xp_cmdshell", "sp_executesql");

    /** P2-3: SQL 注入模式检测正则表达式。 */
    private static final Pattern COMMENT_INJECTION_PATTERN = Pattern.compile("/\\*|\\*/|--\\s");
    private static final Pattern SEMICOLON_INJECTION_PATTERN = Pattern.compile(";\\s*\\w");

    /**
     * Detect SQL injection attempts and log security warnings.
     *
     * <p>
     * This is a heuristic defense, not a replacement for parameterized queries. Only detects obvious SQL injection
     * attempts. P2-3: Added comment injection and semicolon injection detection.
     *
     * @param sql the SQL string to check
     * @param context context description for logging
     */
    private static void checkSqlSafety(String sql, String context) {
        String upperSql = sql.toUpperCase();
        for (String keyword : DANGEROUS_KEYWORDS) {
            if (upperSql.contains(keyword)) {
                log.warn(
                    "SECURITY: {} SQL contains potentially dangerous keyword '{}'. "
                        + "Ensure this is intentional and not user input. SQL: {}",
                    context, keyword, sql.length() > 100 ? sql.substring(0, 100) + "..." : sql);
            }
        }
        // P2-3: Detect comment injection attempts
        if (COMMENT_INJECTION_PATTERN.matcher(sql).find()) {
            log.warn(
                "SECURITY: {} SQL contains potential comment injection patterns (/*, */, --). "
                    + "Ensure this is intentional and not user input. SQL: {}",
                context, sql.length() > 100 ? sql.substring(0, 100) + "..." : sql);
        }
        // P2-3: Detect semicolon injection attempts
        if (SEMICOLON_INJECTION_PATTERN.matcher(sql).find()) {
            log.warn(
                "SECURITY: {} SQL contains potential semicolon injection pattern. "
                    + "Ensure this is intentional and not user input. SQL: {}",
                context, sql.length() > 100 ? sql.substring(0, 100) + "..." : sql);
        }
    }

    private CteSpec addCte(String cteName) {
        cteEntries.add(new CteEntry(cteName));
        return this;
    }

    private CteEntry currentCte() {
        if (cteEntries.isEmpty()) {
            throw new IllegalStateException("No CTE defined. Use with() or withRecursive() first.");
        }
        return cteEntries.get(cteEntries.size() - 1);
    }

    private void applyParameters(Query query) {
        for (Map.Entry<String, Object> param : parameters.entrySet()) {
            query.setParameter(param.getKey(), param.getValue());
        }
    }

    /**
     * CTE 条目。
     */
    private static class CteEntry {
        final String name;
        List<String> columns;
        String sql;

        CteEntry(String name) {
            this.name = name;
        }
    }
}
