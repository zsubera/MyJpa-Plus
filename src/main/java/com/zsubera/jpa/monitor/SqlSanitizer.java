package com.zsubera.jpa.monitor;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SQL 语句脱敏工具类。
 *
 * <p>
 * 用于在日志记录前移除 SQL 中的敏感数据（字符串字面量、数字字面量等），防止敏感信息泄露到日志文件。 标识符（表名、列名等）和 LIMIT/OFFSET 数字保留不变，便于调试。
 *
 * <p>
 * 支持的 SQL 方言特性：
 * <ul>
 * <li>标准 SQL 单引号字符串（支持转义 '' 和反斜杠转义 \'）</li>
 * <li>PostgreSQL 美元引用字符串（$$...$$ 和 $tag$...$tag$）</li>
 * <li>PostgreSQL 美元参数（$1, $2 等）</li>
 * <li>Oracle Q 引用字符串（q'[]', q'()', q'{}', q'<>'）</li>
 * <li>十六进制字面量（X'...'）</li>
 * <li>Unicode 字符串（N'...'）</li>
 * <li>数字字面量（排除 LIMIT/OFFSET/FETCH 后的数字）</li>
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

    /** 十六进制字面量模式（X'...'）— 必须在单引号模式之前匹配 */
    private static final Pattern HEX_LITERAL_PATTERN = Pattern.compile("X'(?:[^'\\\\]|\\\\.|'')*'");

    /** Unicode 字符串模式（N'...'）— 必须在单引号模式之前匹配 */
    private static final Pattern UNICODE_STRING_PATTERN = Pattern.compile("N'(?:[^'\\\\]|\\\\.|'')*'");

    /** PostgreSQL E-字符串模式（E'...'）— 必须在单引号模式之前匹配 */
    private static final Pattern PG_ESTRING_PATTERN = Pattern.compile("E'(?:[^'\\\\]|\\\\.|'')*'");

    /** 双引号字符串模式（MySQL ANSI_QUOTES / SQLite）— 必须在单引号模式之前匹配 */
    private static final Pattern DOUBLE_QUOTE_PATTERN = Pattern.compile("\"(?:[^\"\\\\]|\\\\.|\"\")*\"");

    /** 单引号字符串模式，支持转义 '' 和反斜杠转义 \' */
    private static final Pattern SINGLE_QUOTE_PATTERN = Pattern.compile("'(?:[^'\\\\]|\\\\.|'')*'");

    /** PostgreSQL 美元参数模式（$1, $2 等） */
    private static final Pattern DOLLAR_PARAM_PATTERN = Pattern.compile("\\$\\d+");

    /** 数字字面量模式 */
    private static final Pattern NUMBER_LITERAL_PATTERN = Pattern.compile("\\b\\d+\\.?\\d*(?:[eE][+-]?\\d+)?\\b");

    /** SQL 注释模式（单行注释 -- 和多行注释 /* ... *​/） */
    private static final Pattern COMMENT_PATTERN = Pattern.compile("(?:--[^\n]*|/\\*[\\s\\S]*?\\*/)");

    /** LIMIT/OFFSET/FETCH 数字保护模式 */
    private static final Pattern LIMIT_OFFSET_PATTERN =
        Pattern.compile("(?i)(?:LIMIT|OFFSET|FETCH\\s+(?:FIRST|NEXT))\\s+\\d+(?:\\s+ROWS)?");

    /** PostgreSQL 美元引用字符串模式（$$...$$ 和 $tag$...$tag$，不支持嵌套）。 ponytail: {0,10000} 限制匹配内容长度防 ReDoS。 */
    private static final Pattern DOLLAR_QUOTE_PATTERN = Pattern.compile(
        "\\$\\$(?:[^$]+|\\$(?!\\$)){0,10000}\\$\\$|\\$(\\w+)\\$(?:[^$]+|\\$(?!\\1\\$)){0,10000}\\$\\1\\$",
        Pattern.CASE_INSENSITIVE);

    /** Oracle Q 引用模式：q'[...]', q'(...)', q'{...}', q'<...>', q'!...!' 等。 ponytail: 使用 {0,4000} 限制匹配长度防止 ReDoS。 */
    private static final Pattern Q_QUOTE_BRACKET_PATTERN = Pattern.compile(
        "q'\\[[\\s\\S]{0,4000}?\\]'|q'\\([\\s\\S]{0,4000}?\\)'|q'\\{[\\s\\S]{0,4000}?\\}'|q'<[\\s\\S]{0,4000}?>'",
        Pattern.CASE_INSENSITIVE);

    /** Oracle Q 引用单字符分隔符模式：q'x...x' 等（排除 bracket、空白和引号），长度限制 4000 */
    private static final Pattern Q_QUOTE_CHAR_PATTERN =
        Pattern.compile("q'([^\\[({<\\s'])[\\s\\S]{0,4000}?\\1'", Pattern.CASE_INSENSITIVE);

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
     * @return 脱敏后的 SQL 语句，如果输入为 null 则返回 "null"
     */
    private static final int MAX_SANITIZE_LENGTH = 100_000;

    public static String sanitize(String sql) {
        if (sql == null) {
            return "null";
        }

        if (sql.length() > MAX_SANITIZE_LENGTH) {
            sql = sql.substring(0, MAX_SANITIZE_LENGTH) + "...[truncated]";
        }
        String result = sql;

        // 替换各种字符串字面量（顺序重要：必须在注释移除之前，防止 -- 跨引号匹配）
        result = DOLLAR_QUOTE_PATTERN.matcher(result).replaceAll("?"); // PostgreSQL 美元引用字符串
        result = Q_QUOTE_BRACKET_PATTERN.matcher(result).replaceAll("?"); // Oracle q'[...]' 引用字符串
        result = Q_QUOTE_CHAR_PATTERN.matcher(result).replaceAll("?"); // Oracle q'x...x' 引用字符串
        result = HEX_LITERAL_PATTERN.matcher(result).replaceAll("?"); // 十六进制字面量（X'...'）
        result = UNICODE_STRING_PATTERN.matcher(result).replaceAll("?"); // Unicode 字符串（N'...'）
        result = PG_ESTRING_PATTERN.matcher(result).replaceAll("?"); // PostgreSQL E-字符串（E'...'）
        result = DOUBLE_QUOTE_PATTERN.matcher(result).replaceAll("?"); // 双引号字符串（MySQL ANSI_QUOTES）
        result = SINGLE_QUOTE_PATTERN.matcher(result).replaceAll("?"); // 单引号字符串
        result = DOLLAR_PARAM_PATTERN.matcher(result).replaceAll("?"); // PostgreSQL 美元参数

        // 移除注释（字符串已被替换后，-- 不会跨引号匹配）
        result = COMMENT_PATTERN.matcher(result).replaceAll("");

        // 保护 LIMIT/OFFSET/FETCH 数字，然后替换其他数字
        result = protectAndReplaceNumbers(result);

        return result;
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
