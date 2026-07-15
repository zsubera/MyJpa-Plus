package com.zsubera.jpa.monitor;

import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.*;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SQL 语句脱敏工具类（基于 JSqlParser AST 解析 + 后处理）。
 *
 * <p>
 * 用于在日志记录前移除 SQL 中的敏感数据（字符串字面量、数字字面量等），防止敏感信息泄露到日志文件。
 * 标识符（表名、列名等）和 LIMIT/OFFSET/FETCH 数字保留不变，便于调试。
 *
 * <p>
 * 实现策略：
 * <ol>
 * <li>使用 JSqlParser 解析 SQL 为 AST — 正确处理所有 SQL 方言（美元引用、Q 引用等）</li>
 * <li>AST toString() 生成规范化的 SQL — 字符串字面量变为单引号格式</li>
 * <li>后处理：用正则将单引号字符串和数字替换为 ?，保留 LIMIT/OFFSET 数字</li>
 * </ol>
 *
 * <p>
 * 相比纯正则实现的优势：
 * <ul>
 * <li>消除 ReDoS 风险 — JSqlParser 解析器不是正则引擎</li>
 * <li>正确处理嵌套子查询、复杂表达式、所有方言语法</li>
 * <li>解析失败时优雅降级（返回原始 SQL）</li>
 * </ul>
 *
 * <p>
 * 使用示例：
 *
 * <pre>{@code
 * String sql = "SELECT * FROM users WHERE name = 'John' AND age = 25";
 * String sanitized = SqlSanitizer.sanitize(sql);
 * // 结果: "SELECT * FROM users WHERE name = ? AND age = ?"
 * }</pre>
 */
public final class SqlSanitizer {

    private SqlSanitizer() {}

    /**
     * 后处理：匹配单引号字符串字面量（处理 '' 和 \' 转义）。
     * JSqlParser toString() 输出的字符串都是单引号格式。
     */
    private static final Pattern STRING_LITERAL_PATTERN =
        Pattern.compile("'[^'\\\\]*(?:\\\\.[^'\\\\]*|'')*[^'\\\\]*\\\\?'");

    /** 后处理：匹配 E-字符串（PostgreSQL）、N-字符串（Unicode）、X-字符串（十六进制）。 */
    private static final Pattern PREFIXED_STRING_PATTERN =
        Pattern.compile("(?:[ENX])'[^'\\\\]*(?:\\\\.[^'\\\\]*|'')*[^'\\\\]*\\\\?'");

    /** 后处理：匹配美元引用字符串（$$...$$），内容可包含单个 $。使用占有量词避免回溯。 */
    private static final Pattern DOLLAR_QUOTED_PATTERN = Pattern.compile("\\$\\$[^$]*+(?:\\$(?!\\$)[^$]*+)*\\$\\$");

    /** 后处理：匹配带标签的美元引用字符串（$tag$...$tag$），使用反向引用。 */
    private static final Pattern DOLLAR_TAGGED_PATTERN = Pattern.compile("\\$(\\w+)\\$.*?\\$\\1\\$", Pattern.DOTALL);

    /** 后处理：匹配 Oracle Q-引用字符串。支持多种定界符：括号 Q'[...]'、花括号 Q'{...}'、尖括号 Q'<...>'、单字符 Q'x...x'。 */
    private static final Pattern ORACLE_QQUOTE_PATTERN = Pattern.compile("[Qq]'");

    /** 后处理：匹配双引号字符串/标识符。 */
    private static final Pattern DOUBLE_QUOTE_PATTERN = Pattern.compile("\"[^\"]*\"");

    /** 后处理：匹配 PostgreSQL 美元参数（$1, $2 等）。 */
    private static final Pattern DOLLAR_PARAM_PATTERN = Pattern.compile("\\$\\d+");

    /** 后处理：匹配数字字面量（整数、小数、科学计数法）。 */
    private static final Pattern NUMBER_LITERAL_PATTERN = Pattern.compile("\\b\\d+\\.?\\d*(?:[eE][+-]?\\d+)?\\b");

    /** 保护 LIMIT/OFFSET/FETCH 后的数字不被替换。 */
    private static final Pattern LIMIT_OFFSET_PATTERN =
        Pattern.compile("(?i)(?:LIMIT|OFFSET|FETCH\\s+(?:FIRST|NEXT))\\s+\\d+(?:\\s+ROWS)?");

    /**
     * 对 SQL 语句进行脱敏处理，移除可能包含敏感数据的字符串字面量和数字字面量。
     *
     * <p>
     * 保留以下内容以便调试：
     * <ul>
     * <li>标识符（表名、列名，包括反引号和方括号包裹的标识符）</li>
     * <li>LIMIT/OFFSET/FETCH 后的数字</li>
     * <li>SQL 关键字和运算符</li>
     * </ul>
     *
     * @param sql 原始 SQL 语句
     * @return 脱敏后的 SQL 语句，如果输入为 null 则返回 "<null>"
     */
    public static String sanitize(String sql) {
        if (sql == null) {
            return "<null>";
        }
        if (sql.isEmpty()) {
            return "";
        }

        String normalized;
        try {
            Statement stmt = CCJSqlParserUtil.parse(sql);
            normalized = stmt.toString();
        } catch (JSQLParserException e) {
            // 解析失败时使用原始 SQL（降级为不脱敏，兼容 ORM 生成的非标准 SQL）
            normalized = sql;
        }

        // 替换各种类型的字符串字面量为 ?
        // 顺序重要：先处理前缀字符串，再处理普通单引号字符串
        normalized = PREFIXED_STRING_PATTERN.matcher(normalized).replaceAll("?");
        normalized = DOLLAR_QUOTED_PATTERN.matcher(normalized).replaceAll("?");
        normalized = DOLLAR_TAGGED_PATTERN.matcher(normalized).replaceAll("?");
        normalized = stripOracleQQuotes(normalized);
        normalized = DOUBLE_QUOTE_PATTERN.matcher(normalized).replaceAll("?");
        normalized = DOLLAR_PARAM_PATTERN.matcher(normalized).replaceAll("?");
        normalized = STRING_LITERAL_PATTERN.matcher(normalized).replaceAll("?");

        // 保护 LIMIT/OFFSET/FETCH 数字，替换其他数字
        normalized = protectAndReplaceNumbers(normalized);

        return normalized;
    }

    /**
     * 剥离 Oracle Q-引用字符串（Q'delimiter...delimiter'），替换为 ?。
     *
     * <p>
     * 支持所有 Oracle 定界符类型：
     * <ul>
     * <li>括号对：Q'[hello]', Q'(hello)', Q'{hello}', Q'&lt;hello&gt;'</li>
     * <li>单字符：Q'!hello!', Q'@hello@', Q'#hello#'</li>
     * <li>单引号（转义）：Q''hello O''world''（内容中的单引号用双写转义）</li>
     * </ul>
     *
     * @param sql 包含 Q-引用的 SQL
     * @return Q-引用被替换为 ? 的 SQL
     */
    public static String stripOracleQQuotes(String sql) {
        StringBuilder result = new StringBuilder(sql.length());
        int i = 0;
        while (i < sql.length()) {
            // 查找 Q' 或 q' 的起始位置
            if (i + 1 < sql.length() && (sql.charAt(i) == 'Q' || sql.charAt(i) == 'q') && sql.charAt(i + 1) == '\'') {
                char afterQuote = (i + 2 < sql.length()) ? sql.charAt(i + 2) : 0;
                if (afterQuote == '[' || afterQuote == '(' || afterQuote == '{' || afterQuote == '<') {
                    // 括号类定界符：支持嵌套（如 Q'[hello [world]]'）
                    char close =
                        (afterQuote == '[') ? ']' : (afterQuote == '(') ? ')' : (afterQuote == '{') ? '}' : '>';
                    int depth = 1;
                    int pos = i + 3;
                    while (pos < sql.length() && depth > 0) {
                        char c = sql.charAt(pos);
                        if (c == afterQuote) {
                            depth++;
                        } else if (c == close) {
                            depth--;
                        }
                        pos++;
                    }
                    if (depth == 0 && pos > 0 && sql.charAt(pos - 1) == close && pos < sql.length()
                        && sql.charAt(pos) == '\'') {
                        result.append('?');
                        i = pos + 1;
                        continue;
                    }
                } else if (afterQuote == '\'') {
                    // 单引号定界符：Q''...''，内容中的单引号用双写转义
                    int pos = i + 3;
                    while (pos < sql.length()) {
                        if (sql.charAt(pos) == '\'' && pos + 1 < sql.length() && sql.charAt(pos + 1) == '\'') {
                            pos += 2; // 跳过转义的 ''
                        } else if (sql.charAt(pos) == '\'') {
                            // 到达闭合的单引号定界符
                            result.append('?');
                            i = pos + 1;
                            break;
                        } else {
                            pos++;
                        }
                    }
                    if (i != pos) {
                        // 未找到闭合定界符，不替换
                        result.append(sql.charAt(i));
                        i++;
                    }
                    continue;
                } else if (afterQuote != 0 && !Character.isLetterOrDigit(afterQuote) && afterQuote != ' '
                    && afterQuote != '_') {
                    // 单字符定界符：Q'x...x'（x 是任意非字母数字、非空格字符）
                    char delimiter = afterQuote;
                    int end = sql.indexOf(delimiter, i + 3);
                    if (end >= 0 && end + 1 < sql.length() && sql.charAt(end + 1) == '\'') {
                        result.append('?');
                        i = end + 2;
                        continue;
                    }
                }
            }
            result.append(sql.charAt(i));
            i++;
        }
        return result.toString();
    }

    /**
     * 保护 LIMIT/OFFSET/FETCH 后的数字，替换其他数字字面量。
     */
    private static String protectAndReplaceNumbers(String sql) {
        List<String> protectedParts = new ArrayList<>();
        Matcher limitMatcher = LIMIT_OFFSET_PATTERN.matcher(sql);
        StringBuilder sb = new StringBuilder();
        int lastEnd = 0;

        while (limitMatcher.find()) {
            sb.append(sql, lastEnd, limitMatcher.start());
            String sentinel = "\uE000MYJPA_PROT_" + protectedParts.size() + "\uE000";
            sb.append(sentinel);
            protectedParts.add(limitMatcher.group());
            lastEnd = limitMatcher.end();
        }
        sb.append(sql.substring(lastEnd));

        String result = sb.toString();
        result = NUMBER_LITERAL_PATTERN.matcher(result).replaceAll("?");

        // 恢复被保护的部分
        for (int i = 0; i < protectedParts.size(); i++) {
            result = result.replace("\uE000MYJPA_PROT_" + i + "\uE000", protectedParts.get(i));
        }

        return result;
    }
}
