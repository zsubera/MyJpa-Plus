package com.zsubera.jpa.spec;

import com.zsubera.jpa.util.IdentifierValidator;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
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
public class CteSpec {

    private static final Logger log = LoggerFactory.getLogger(CteSpec.class);

    /**
     * SQL 保留字集合（MySQL + PostgreSQL 交集），CTE 名称不应使用这些关键字。
     *
     * <p>
     * MySQL 中使用保留字作为 CTE 名称会导致语法错误（如 {@code WITH empty AS ...} 被解析为 {@code WITH EMPTY}）。
     * PostgreSQL 对保留字更宽松，但为跨数据库兼容性仍应避免。
     */
    private static final java.util.Set<String> SQL_RESERVED_WORDS = java.util.Set.of(
        // DML
        "SELECT", "INSERT", "UPDATE", "DELETE", "FROM", "WHERE", "INTO", "VALUES", "SET", "JOIN", "LEFT", "RIGHT",
        "INNER", "OUTER", "CROSS", "ON", "USING", "AS", "DISTINCT", "ALL", "UNION", "EXCEPT", "INTERSECT", "ORDER",
        "BY", "GROUP", "HAVING", "LIMIT", "OFFSET", "FETCH", "FIRST", "NEXT", "ROWS",
        // DDL
        "CREATE", "ALTER", "DROP", "TRUNCATE", "RENAME", "INDEX", "TABLE", "VIEW", "SCHEMA", "DATABASE",
        // Constraint
        "PRIMARY", "KEY", "FOREIGN", "REFERENCES", "UNIQUE", "CHECK", "DEFAULT", "NULL", "NOT", "CONSTRAINT",
        // Transaction
        "BEGIN", "COMMIT", "ROLLBACK", "SAVEPOINT", "TRANSACTION", "ISOLATION", "LEVEL", "READ", "WRITE",
        // Boolean
        "TRUE", "FALSE",
        // Operators
        "AND", "OR", "IN", "EXISTS", "BETWEEN", "LIKE", "IS", "ANY", "SOME",
        // Functions reserved
        "COUNT", "SUM", "AVG", "MIN", "MAX", "COALESCE", "NULLIF", "CASE", "WHEN", "THEN", "ELSE", "END", "CAST",
        "CONVERT", "EXTRACT", "CURRENT_DATE", "CURRENT_TIME", "CURRENT_TIMESTAMP",
        // Common identifiers that conflict
        "USER", "COLUMN", "ROW", "VALUE", "TYPE", "STATUS", "NAME", "ID", "DATA", "TEXT", "DATE", "TIME",
        // MySQL specific
        "EMPTY", "DUAL", "FORCE", "IGNORE", "DELAYED", "LOW_PRIORITY", "HIGH_PRIORITY", "OUTFILE", "INFILE", "LOAD",
        "LOCAL",
        // PostgreSQL specific
        "RETURNING", "ILIKE", "SIMILAR", "OVERLAPS", "UNKNOWN", "ARRAY", "MULTISET", "PERFORM", "EXECUTE", "PL/pgSQL",
        // CTE specific
        "WITH", "RECURSIVE", "MATERIALIZED", "SEARCH", "CYCLE");

    /**
     * 用于检测 SQL 中未绑定命名参数的正则表达式。
     *
     * <p>
     * 使用负向后行断言排除 PostgreSQL :: 类型转换（如 ::text、::integer）。
     * 提取为静态字段避免每次调用 {@code checkUnboundParameters} 时重新编译。
     */
    private static final Pattern UNBOUND_PARAM_PATTERN = Pattern.compile("(?<!:):([a-zA-Z_][a-zA-Z0-9_]*)");

    private static final Class<?> HIBERNATE_SESSION_CLASS;
    private static final Class<?> HIBERNATE_WORK_CLASS;
    static {
        Class<?> sessionCls;
        Class<?> workCls;
        try {
            sessionCls = Class.forName("org.hibernate.Session");
            workCls = Class.forName("org.hibernate.jdbc.Work");
        } catch (ClassNotFoundException e) {
            sessionCls = null;
            workCls = null;
        }
        HIBERNATE_SESSION_CLASS = sessionCls;
        HIBERNATE_WORK_CLASS = workCls;
    }
    /** 流式查询默认 fetchSize，避免 PostgreSQL/MySQL 驱动将整个结果集加载到内存。 */
    private static final int DEFAULT_STREAM_FETCH_SIZE = 100;

    /**
     * 数据库产品名缓存。首次检测后直接返回，避免每次流式查询都执行反射创建代理。
     * 使用 AtomicReference 保证线程安全的单次检测。
     */
    private static final AtomicReference<String> cachedProductName = new AtomicReference<>();

    private final List<CteEntry> cteEntries = new ArrayList<>();
    private String mainSql;
    private final Map<String, Object> parameters = new LinkedHashMap<>();
    private final boolean recursive;

    /**
     * 严格模式开关。硬编码为 true（安全优先模式）。
     *
     * <p>
     * 设置为 true 时，检测到危险 SQL 关键字或未绑定参数会抛出异常。 但请注意：正则黑名单无法完备覆盖所有绕过方式，此检查仅作为辅助防护层。 安全责任在于使用者确保 CTE SQL 不包含用户输入。
     *
     * <p>
     * 此值不可配置，始终启用以防止 SQL 注入攻击。如需绕过安全检查，请使用参数化查询 {@link #asSafe(String)} 替代。
     */
    private static final boolean strictMode = true;

    /**
     * 获取严格模式状态。
     *
     * @return 始终返回 true（严格模式不可配置）
     */
    public static boolean isStrictMode() {
        return strictMode;
    }

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
        if (!IdentifierValidator.SAFE_IDENTIFIER_PATTERN.matcher(cteName).matches()) {
            throw new IllegalArgumentException(
                "cteName contains invalid characters: " + cteName + ". Only alphanumeric and underscore are allowed.");
        }
        validateNotReservedWord(cteName);
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
        if (!IdentifierValidator.SAFE_IDENTIFIER_PATTERN.matcher(cteName).matches()) {
            throw new IllegalArgumentException(
                "cteName contains invalid characters: " + cteName + ". Only alphanumeric and underscore are allowed.");
        }
        validateNotReservedWord(cteName);
        return new CteSpec(true).addCte(cteName);
    }

    /**
     * 校验 CTE 名称是否为 SQL 保留字。使用保留字作为 CTE 名称会导致语法错误或意外行为。
     *
     * <p>
     * MySQL 中 {@code WITH empty AS ...} 会被解析为 {@code WITH EMPTY AS ...}（保留字），
     * 导致语法错误。PostgreSQL 虽然允许，但为跨数据库兼容性仍应避免。
     *
     * @param name 要校验的名称
     * @throws IllegalArgumentException 如果名称是 SQL 保留字
     */
    private static void validateNotReservedWord(String name) {
        if (SQL_RESERVED_WORDS.contains(name.toUpperCase(java.util.Locale.ROOT))) {
            throw new IllegalArgumentException(
                "CTE name '" + name + "' is a SQL reserved word and may cause syntax errors. "
                    + "Please use a different name (e.g., '" + name + "_ct' or 'result_" + name + "').");
        }
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
            if (!IdentifierValidator.SAFE_IDENTIFIER_PATTERN.matcher(col).matches()) {
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
     * <strong>安全警告：</strong>SQL 字符串<b>绝不</b>应包含用户输入——此方法<b>不接受</b>用户输入。
     * CTE 名称和列名已通过正则校验防止注入，但 SQL 本身由开发者负责安全。
     * 如需用户输入的参数化查询，请使用 {@link #asSafe(String, Object...)}。
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
        if (strictMode) {
            validateSelectOnly(sql);
        }
        checkSqlSafety(sql, "CTE", recursive);
        CteEntry current = currentCte();
        current.sql = sql;
        return this;
    }

    /**
     * 安全的参数化 CTE SQL 设置方法。与 {@link #as(String)} 不同，此方法接受 SQL 模板和参数， 强制使用参数化查询防止 SQL 注入。
     *
     * <p>
     * 使用示例：
     *
     * <pre>{@code
     * CteSpec.with("filtered_users").asSafe("SELECT id, name FROM users WHERE status = ?1 AND age > ?2", "ACTIVE", 18)
     *     .select("SELECT * FROM filtered_users").getResultList(em);
     * }</pre>
     *
     * @param sqlTemplate CTE 查询 SQL 模板（使用 ?1, ?2 等位置参数）
     * @param params 参数值
     * @return 当前 CteSpec 实例，支持链式调用
     * @throws IllegalArgumentException 如果 sqlTemplate 为 null 或空
     */
    public CteSpec asSafe(String sqlTemplate, Object... params) {
        if (sqlTemplate == null || sqlTemplate.isEmpty()) {
            throw new IllegalArgumentException("sqlTemplate must not be null or empty");
        }
        if (strictMode) {
            validateSelectOnly(sqlTemplate);
        }
        checkSqlSafety(sqlTemplate, "CTE", recursive);
        CteEntry current = currentCte();
        if (params != null && params.length > 0) {
            int cteIndex = cteEntries.size() - 1;
            String rewrittenSql = sqlTemplate;
            for (int i = params.length - 1; i >= 0; i--) {
                String paramStr = "?" + (i + 1);
                String namedParam = "_cte_" + cteIndex + "_param_" + i;
                StringBuilder sb = new StringBuilder(rewrittenSql.length());
                boolean inString = false;
                boolean inDollarQuote = false;
                String dollarTag = null;
                for (int j = 0; j < rewrittenSql.length(); j++) {
                    char c = rewrittenSql.charAt(j);
                    // ponytail: 处理 PostgreSQL dollar-quoted strings ($$...$$ 或 $tag$...$tag$)
                    if (!inString && c == '$') {
                        int tagEnd = rewrittenSql.indexOf('$', j + 1);
                        if (tagEnd > j + 1) {
                            String candidateTag = rewrittenSql.substring(j + 1, tagEnd);
                            if (!candidateTag.contains("$") && !candidateTag.isEmpty()) {
                                // $tag$...$tag$ 形式
                                String closeTag = "$" + candidateTag + "$";
                                if (rewrittenSql.startsWith(closeTag, j)) {
                                    if (!inDollarQuote) {
                                        inDollarQuote = true;
                                        dollarTag = closeTag;
                                        j += closeTag.length() - 1;
                                        continue;
                                    } else if (closeTag.equals(dollarTag)) {
                                        inDollarQuote = false;
                                        dollarTag = null;
                                        j += closeTag.length() - 1;
                                        continue;
                                    }
                                }
                            } else if (candidateTag.isEmpty()) {
                                // $$...$$ 形式
                                if (!inDollarQuote) {
                                    inDollarQuote = true;
                                    dollarTag = "$$";
                                    j += 1; // skip second $
                                    continue;
                                } else if ("$$".equals(dollarTag)) {
                                    inDollarQuote = false;
                                    dollarTag = null;
                                    j += 1; // skip second $
                                    continue;
                                }
                            }
                        }
                        if (inDollarQuote) {
                            sb.append(c);
                            continue;
                        }
                    }
                    if (inDollarQuote) {
                        sb.append(c);
                        continue;
                    }
                    if (c == '\\' && inString && j + 1 < rewrittenSql.length()) {
                        sb.append(c);
                        sb.append(rewrittenSql.charAt(++j));
                        continue;
                    }
                    if (c == '\'') {
                        // ponytail: 处理 SQL 转义引号 '' — 两个连续单引号不切换 inString
                        if (inString && j + 1 < rewrittenSql.length() && rewrittenSql.charAt(j + 1) == '\'') {
                            sb.append("''");
                            j++; // skip the second quote
                            continue;
                        }
                        inString = !inString;
                    }
                    if (!inString && !inDollarQuote && c == '?' && rewrittenSql.regionMatches(j, paramStr, 0, paramStr.length())) {
                        sb.append(':').append(namedParam);
                        j += paramStr.length() - 1;
                    } else {
                        sb.append(c);
                    }
                }
                rewrittenSql = sb.toString();
                parameters.put(namedParam, params[i]);
            }
            current.sql = rewrittenSql;
        } else {
            current.sql = sqlTemplate;
        }
        return this;
    }

    /**
     * 验证 SQL 仅包含 SELECT 语句（strictMode=true 时阻止 DDL/DML）。
     *
     * @param sql SQL 字符串
     * @throws SecurityException 如果 strictMode=true 且检测到非 SELECT 语句
     */
    private static void validateSelectOnly(String sql) {
        String trimmed = sql.trim().toUpperCase();
        if (!trimmed.startsWith("SELECT") && !trimmed.startsWith("WITH")) {
            throw new SecurityException("SECURITY: CTE SQL must start with SELECT or WITH. " + "Got: "
                + (trimmed.length() > 50 ? trimmed.substring(0, 50) + "..." : trimmed));
        }
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
        if (!IdentifierValidator.SAFE_IDENTIFIER_PATTERN.matcher(cteName).matches()) {
            throw new IllegalArgumentException(
                "cteName contains invalid characters: " + cteName + ". Only alphanumeric and underscore are allowed.");
        }
        validateNotReservedWord(cteName);
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
        checkSqlSafety(sql, "main query", true);
        validateSelectOnly(sql);
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
     * @return 单个查询结果（Object[]），如果无结果则返回 {@link Optional#empty()}
     * @throws IllegalStateException 如果 CTE 或主查询未完整配置
     * @throws IllegalArgumentException 如果 em 为 null

     */
    public Optional<Object[]> getSingleResult(EntityManager em) {
        if (em == null) {
            throw new IllegalArgumentException("em must not be null");
        }
        String sql = buildSql();
        log.debug("CteSpec: executing native query for single result (length={})", sql.length());
        Query query = em.createNativeQuery(sql);
        applyParameters(query);
        if (!sql.toUpperCase(java.util.Locale.ROOT).contains("LIMIT")) {
            query.setMaxResults(2);
        }
        List<?> results = query.getResultList();
        if (results.isEmpty()) {
            return Optional.empty();
        }
        // 当返回多个结果但只使用第一个时发出警告
        if (results.size() > 1) {
            log.warn("CteSpec.getSingleResult() returned {} results but only the first is used. "
                + "Consider adding LIMIT 1 to your query or using getResultList() instead.", results.size());
        }
        Object row = results.get(0);
        if (row instanceof Object[] arr) {
            return Optional.of(arr);
        }
        return Optional.of(new Object[] {row});
    }

    /**
     * 安全的流式查询消费者重载，自动管理资源关闭。
     *
     * <p>
     * 此方法使用 try-with-resources 确保 Stream 正确关闭，避免资源泄漏。
     *
     * <pre>{@code
     * cteSpec.getResultStream(em, row -> {
     *     System.out.println("Row: " + Arrays.toString(row));
     * });
     * }</pre>
     *
     * @param em EntityManager 实例
     * @param consumer 行消费者
     * @throws IllegalStateException 如果 CTE 或主查询未完整配置
     * @throws IllegalArgumentException 如果 em 或 consumer 为 null
     */
    public void getResultStream(EntityManager em, java.util.function.Consumer<Object[]> consumer) {
        if (em == null) {
            throw new IllegalArgumentException("em must not be null");
        }
        if (consumer == null) {
            throw new IllegalArgumentException("consumer must not be null");
        }
        String sql = buildSql();
        log.debug("CteSpec: executing native stream query (length={})", sql.length());
        Query query = em.createNativeQuery(sql);
        applyParameters(query);
        applyFetchSize(em, query);
        try (java.util.stream.Stream<Object[]> stream = query.getResultStream().map(row -> {
            if (row instanceof Object[] arr) {
                return arr;
            }
            return new Object[] {row};
        })) {
            stream.forEach(consumer);
        }
    }

    /**
     * 根据数据库类型设置合适的 fetchSize，以支持流式查询。
     *
     * <p>
     * PostgreSQL 驱动在 fetchSize=0 时会将整个结果集加载到内存，失去流式查询的意义。 此方法检测数据库类型并设置适当的 fetchSize 值。
     *
     * @param em EntityManager 实例
     * @param query JPA Query 实例
     */
    private void applyFetchSize(EntityManager em, Query query) {
        String productName = cachedProductName.get();
        if (productName == null) {
            // 优先使用 Hibernate 路径
            String detected = null;
            if (HIBERNATE_SESSION_CLASS != null && HIBERNATE_WORK_CLASS != null) {
                detected = detectProductViaHibernate(em);
            }
            // Hibernate 不可用时，尝试通过直接 unwrap Connection（兼容 EclipseLink）
            if (detected == null) {
                detected = detectProductViaConnection(em);
            }
            if (detected != null) {
                // ponytail: compareAndSet 保证单次写入；最坏情况重复检测一次
                cachedProductName.compareAndSet(null, detected);
            }
            productName = cachedProductName.get();
        }
        if (productName != null) {
            String lower = productName.toLowerCase();
            if (lower.contains("postgresql") || lower.contains("mysql")) {
                setNativeQueryFetchSize(query, DEFAULT_STREAM_FETCH_SIZE);
            }
        }
    }

    /**
     * 通过 Hibernate Session.doWork 检测数据库产品名。
     *
     * @return 数据库产品名，失败时返回 null
     */
    private String detectProductViaHibernate(EntityManager em) {
        try {
            Object session = em.unwrap(HIBERNATE_SESSION_CLASS);
            String[] holder = new String[1];
            Object workProxy = java.lang.reflect.Proxy.newProxyInstance(HIBERNATE_WORK_CLASS.getClassLoader(),
                new Class<?>[] {HIBERNATE_WORK_CLASS}, (proxy, method, args) -> {
                    if ("execute".equals(method.getName()) && args.length == 1
                        && args[0] instanceof java.sql.Connection conn) {
                        holder[0] = conn.getMetaData().getDatabaseProductName();
                        return null;
                    }
                    if (method.getDeclaringClass() == Object.class) {
                        return switch (method.getName()) {
                            case "toString" -> "CteSpec.WorkProxy";
                            case "hashCode" -> System.identityHashCode(proxy);
                            case "equals" -> proxy == args[0];
                            default -> method.invoke(proxy, args);
                        };
                    }
                    return null;
                });
            java.lang.reflect.Method doWork = HIBERNATE_SESSION_CLASS.getMethod("doWork", HIBERNATE_WORK_CLASS);
            doWork.invoke(session, workProxy);
            return holder[0];
        } catch (Exception e) {
            log.debug("CteSpec: Hibernate product detection failed, trying fallback: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 通过直接 unwrap JDBC Connection 检测数据库产品名。
     * 适用于 EclipseLink 等非 Hibernate JPA 提供者。
     */
    private String detectProductViaConnection(EntityManager em) {
        try {
            java.sql.Connection conn = em.unwrap(java.sql.Connection.class);
            return conn.getMetaData().getDatabaseProductName();
        } catch (Exception e) {
            log.warn("CteSpec: Cannot determine database product name for fetchSize, streaming may be inefficient: {}",
                e.getMessage());
            return null;
        }
    }

    /**
     * 通过反射设置 NativeQuery.fetchSize。适用于 Hibernate 和 EclipseLink 的 NativeQuery。
     */
    private static void setNativeQueryFetchSize(Query query, int fetchSize) {
        // Hibernate NativeQuery
        try {
            Class<?> hibernateNq = Class.forName("org.hibernate.query.NativeQuery");
            if (hibernateNq.isInstance(query)) {
                hibernateNq.getMethod("setFetchSize", int.class).invoke(query, fetchSize);
                return;
            }
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException
            | java.lang.reflect.InvocationTargetException ignored) {
        }
        // EclipseLink: 使用 JPA 查询 hint
        query.setHint("eclipselink.jdbc.fetch-size", fetchSize);
    }

    /**
     * 仅构建 SQL 字符串，不执行。用于调试或与其他组件集成。
     *
     * <p>
     * <strong>安全校验：</strong>构建时会检查 SQL 中是否包含未绑定的命名参数（以 {@code :} 开头的标识符）， 如果存在未通过 {@link #setParameter} 绑定的参数，将记录安全警告。
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
        String sql = sb.toString();
        // 警告 SQL 中未绑定的命名参数，以鼓励使用参数化查询
        checkUnboundParameters(sql, parameters);
        return sql;
    }

    // ---- 内部方法 ----

    /**
     * 危险 SQL 关键字检测正则表达式，使用单词边界匹配避免子字符串误报。
     *
     * <p>
     * 使用 {@code \b} 单词边界确保仅匹配独立关键字（如 "DROP"），而非子字符串（如 "DROPDOWN" 中的 "DROP"）。 xp_cmdshell 和 sp_executesql
     * 不使用单词边界，因为它们是完整的存储过程名称。
     */
    private static final Pattern DANGEROUS_KEYWORD_PATTERN = Pattern.compile(
        "\\b(DROP|TRUNCATE|GRANT|REVOKE|ALTER|CREATE|EXEC|EXECUTE|DELETE|INSERT|UPDATE|MERGE|CALL|COPY)\\b",
        Pattern.CASE_INSENSITIVE);

    /** 危险存储过程名称，使用子字符串匹配。 */
    private static final Pattern DANGEROUS_PROCEDURE_PATTERN =
        Pattern.compile("(xp_cmdshell|sp_executesql)", Pattern.CASE_INSENSITIVE);

    /** SQL 注入模式检测正则表达式。 */
    private static final Pattern COMMENT_INJECTION_PATTERN = Pattern.compile("/\\*|\\*/|--(?!:)");
    private static final Pattern SEMICOLON_INJECTION_PATTERN = Pattern.compile(";\\s*\\w");

    /** UNION SELECT 注入检测模式。仅检测 UNION SELECT（不含 ALL），因为 UNION ALL 是递归 CTE 的合法语法。 */
    private static final Pattern UNION_SELECT_PATTERN =
        Pattern.compile("\\bUNION\\s+SELECT\\b", Pattern.CASE_INSENSITIVE);

    /** UNION ALL SELECT 注入检测模式。非递归 CTE 中 UNION ALL 不是合法语法，应视为注入尝试。 */
    private static final Pattern UNION_ALL_SELECT_PATTERN =
        Pattern.compile("\\bUNION\\s+ALL\\s+SELECT\\b", Pattern.CASE_INSENSITIVE);

    /** WAITFOR DELAY 时间盲注检测模式（SQL Server）。 */
    private static final Pattern WAITFOR_DELAY_PATTERN =
        Pattern.compile("\\bWAITFOR\\s+DELAY\\b", Pattern.CASE_INSENSITIVE);

    /** INTO OUTFILE / LOAD DATA 文件操作检测模式（MySQL）。 */
    private static final Pattern FILE_OPERATION_PATTERN =
        Pattern.compile("\\bINTO\\s+(OUTFILE|DUMPFILE)\\b|\\bLOAD\\s+DATA\\s+(INFILE|LOCAL)\\b",
            Pattern.CASE_INSENSITIVE);

    /**
     * 检测 SQL 中的潜在危险模式。默认仅记录日志，strictMode=true 时抛出异常。
     *
     * <p>
     * <strong>重要：</strong>此检查是启发式辅助防护层，不是安全边界。正则黑名单无法完备覆盖所有绕过方式 （编码、注释、嵌套等）。安全责任在于使用者确保 CTE SQL 不包含用户输入。
     *
     * @param sql 要检查的 SQL 字符串
     * @param context 日志中的上下文描述
     * @throws SecurityException 如果 strictMode=true 且检测到危险模式
     */
    private static void checkSqlSafety(String sql, String context, boolean recursive) {
        String truncated = sql.length() > 100 ? sql.substring(0, 100) + "..." : sql;
        // 使用单词边界正则匹配，避免子字符串误报（如 "DROPDOWN" 中的 "DROP"）
        if (DANGEROUS_KEYWORD_PATTERN.matcher(sql).find()) {
            String message = "SECURITY: " + context + " SQL contains potentially dangerous DDL/admin keyword. "
                + "Ensure this is intentional and not user input. SQL: " + truncated;
            if (strictMode) {
                throw new SecurityException(message);
            }
            log.warn(message);
        }
        if (DANGEROUS_PROCEDURE_PATTERN.matcher(sql).find()) {
            String message = "SECURITY: " + context
                + " SQL contains dangerous stored procedure name (xp_cmdshell/sp_executesql). " + "SQL: " + truncated;
            if (strictMode) {
                throw new SecurityException(message);
            }
            log.warn(message);
        }
        // 检测注释注入尝试
        if (COMMENT_INJECTION_PATTERN.matcher(sql).find()) {
            String message =
                "SECURITY: " + context + " SQL contains potential comment injection patterns (/*, */, --). "
                    + "Ensure this is intentional and not user input. SQL: " + truncated;
            if (strictMode) {
                throw new SecurityException(message);
            }
            log.warn(message);
        }
        // 检测分号注入尝试
        if (SEMICOLON_INJECTION_PATTERN.matcher(sql).find()) {
            String message = "SECURITY: " + context + " SQL contains potential semicolon injection pattern. "
                + "Ensure this is intentional and not user input. SQL: " + truncated;
            if (strictMode) {
                throw new SecurityException(message);
            }
            log.warn(message);
        }
        // 检测 UNION SELECT 注入尝试（仅非递归 CTE — 递归 CTE 中 UNION SELECT 是合法语法）
        if (!recursive && UNION_SELECT_PATTERN.matcher(sql).find()) {
            String message = "SECURITY: " + context + " SQL contains potential UNION SELECT injection pattern. "
                + "Ensure this is intentional and not user input. SQL: " + truncated;
            if (strictMode) {
                throw new SecurityException(message);
            }
            log.warn(message);
        }
        // 非递归 CTE 中 UNION ALL SELECT 不是合法语法，视为注入尝试
        if (!recursive && UNION_ALL_SELECT_PATTERN.matcher(sql).find()) {
            String message = "SECURITY: " + context
                + " SQL contains UNION ALL SELECT in non-recursive CTE (not valid syntax). "
                + "Ensure this is intentional and not user input. SQL: " + truncated;
            if (strictMode) {
                throw new SecurityException(message);
            }
            log.warn(message);
        }
        // 检测 WAITFOR DELAY 时间盲注（SQL Server）
        if (WAITFOR_DELAY_PATTERN.matcher(sql).find()) {
            String message = "SECURITY: " + context
                + " SQL contains WAITFOR DELAY pattern (time-based blind SQL injection). " + "SQL: " + truncated;
            if (strictMode) {
                throw new SecurityException(message);
            }
            log.warn(message);
        }
        // 检测 INTO OUTFILE / LOAD DATA 文件操作（MySQL）
        if (FILE_OPERATION_PATTERN.matcher(sql).find()) {
            String message = "SECURITY: " + context
                + " SQL contains file operation pattern (INTO OUTFILE/LOAD DATA). " + "SQL: " + truncated;
            if (strictMode) {
                throw new SecurityException(message);
            }
            log.warn(message);
        }
    }

    /**
     * 检查 SQL 中是否存在未绑定的命名参数。
     *
     * @param sql 完整 SQL 字符串
     * @param boundParams 已绑定的参数映射
     */
    private static void checkUnboundParameters(String sql, Map<String, Object> boundParams) {
        // ponytail: 原子组 (?>...) 防止非匹配输入上的灾难性回溯
        String stripped = sql.replaceAll("'(?>[^'\\\\]|\\\\.|'')*", "''");
        java.util.regex.Matcher matcher = UNBOUND_PARAM_PATTERN.matcher(stripped);
        List<String> unboundParams = new ArrayList<>();
        while (matcher.find()) {
            String paramName = matcher.group(1);
            // 跳过 asSafe() 内部管理的参数（如 :_cte_0_param_0）
            if (paramName.startsWith("_cte_") && paramName.contains("_param_")) {
                continue;
            }
            if (!boundParams.containsKey(paramName)) {
                unboundParams.add(paramName);
            }
        }
        if (!unboundParams.isEmpty()) {
            String message = "SECURITY: CTE SQL contains unbound named parameters: " + unboundParams
                + ". Ensure all parameters are bound via setParameter() to prevent SQL injection.";
            if (strictMode) {
                throw new IllegalStateException(message);
            }
            log.warn(message);
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
