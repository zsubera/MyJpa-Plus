package com.zsubera.jpa.util;

import com.zsubera.jpa.exception.MyJpaPlusException;
import com.zsubera.jpa.exception.SecurityViolationException;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * SQL 标识符验证工具类。
 *
 * <p>
 * 用于验证和校验 SQL 标识符（表名、列名等），防止 SQL 注入攻击。 支持标准 ASCII 标识符和可选的 Unicode 标识符。
 *
 * <p>
 * 验证规则：
 * <ul>
 * <li>标识符必须以字母或下划线开头</li>
 * <li>后续字符只能是字母、数字或下划线</li>
 * <li>点号分隔的多段标识符（如 schema.table）每段独立验证</li>
 * <li>最大长度限制为 128 字符</li>
 * <li>可选的 Unicode 同形字符检测（Cyrillic/Greek/Armenian）</li>
 * </ul>
 *
 * <p>
 * 使用示例：
 *
 * <pre>{@code
 * // 验证标识符（如果无效则抛出异常）
 * IdentifierValidator.validate("users");
 * IdentifierValidator.validate("schema.users");
 *
 * // 验证注解中的表名
 * IdentifierValidator.validateTableName("my_table");
 *
 * // 验证注解中的列名
 * IdentifierValidator.validateColumnName("user_id");
 * }</pre>
 */
public final class IdentifierValidator {

    private IdentifierValidator() {}

    private static final Logger log = LoggerFactory.getLogger(IdentifierValidator.class);

    /** 安全标识符正则：仅允许字母、数字和下划线。用于单段和 schema.table 格式的每一段校验。 */
    public static final Pattern SAFE_IDENTIFIER_PATTERN = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]*$");

    /** Unicode 标识符正则：允许 Unicode 字母、数字和下划线，支持国际化标识符。 */
    private static final Pattern UNICODE_IDENTIFIER_PART_PATTERN = Pattern.compile("^[\\p{L}_][\\p{L}\\p{N}_]*$");

    /**
     * 常见 Unicode 同形字符检测：这些字符与 ASCII 字符视觉相似，可能用于绕过安全检查。
     *
     * <p>
     * <strong>已知限制：</strong>此模式覆盖西里尔字母、希腊字母、亚美尼亚字母、全角拉丁/数字、
     * 数学字母符号等常见混淆字符。不覆盖所有 Unicode 混淆字符（如装饰字母、封闭字母数字）。
     * 如需生产级检测，考虑使用 ICU4J 的 {@code SpoofChecker}。
     */
    private static final Pattern HOMOGLYPH_PATTERN =
        Pattern.compile("[\\u0400-\\u04FF\\u0370-\\u03FF\\u0530-\\u058F\\uFF00-\\uFFEF\\u2100-\\u214F\\u2460-\\u24FF]");

    /** 标识符最大长度，防止滥用。 */
    private static final int MAX_IDENTIFIER_LENGTH = 128;

    /** 是否启用 Unicode 标识符支持。可通过系统属性 myjpa-plus.merge.unicode-identifiers=true 启用。 */
    private static volatile boolean unicodeIdentifiers = false;

    private static volatile boolean propsLoaded = false;

    private static void ensurePropsLoaded() {
        if (!propsLoaded) {
            synchronized (IdentifierValidator.class) {
                if (!propsLoaded) {
                    String prop = System.getProperty("myjpa-plus.merge.unicode-identifiers");
                    if ("true".equalsIgnoreCase(prop)) {
                        unicodeIdentifiers = true;
                    }
                    propsLoaded = true;
                }
            }
        }
    }

    /**
     * 设置是否启用 Unicode 标识符支持。
     *
     * @param enabled 是否启用
     */
    public static void setUnicodeIdentifiers(boolean enabled) {
        ensurePropsLoaded();
        unicodeIdentifiers = enabled;
    }

    /**
     * 检查是否启用了 Unicode 标识符支持。
     *
     * @return 如果启用返回 true
     */
    public static boolean isUnicodeIdentifiersEnabled() {
        ensurePropsLoaded();
        return unicodeIdentifiers;
    }

    /**
     * 验证 SQL 标识符是否安全。标识符必须仅包含字母、数字、下划线和点号（用于多段标识符）。
     *
     * @param identifier 标识符
     * @throws MyJpaPlusException 如果标识符包含非法字符、超长或包含同形字符
     */
    public static void validate(String identifier) {
        if (identifier == null || identifier.isEmpty()) {
            throw new SecurityViolationException("Identifier must not be null or empty");
        }
        if (identifier.length() > MAX_IDENTIFIER_LENGTH) {
            throw new SecurityViolationException("Identifier length (" + identifier.length() + ") exceeds maximum ("
                + MAX_IDENTIFIER_LENGTH + "): '" + identifier.substring(0, 64) + "...'");
        }
        if (identifier.endsWith(".")) {
            throw new SecurityViolationException("Identifier must not end with '.': '" + identifier + "'");
        }
        String[] parts = identifier.split("\\.");
        for (String part : parts) {
            validatePart(part, identifier);
        }
    }

    /**
     * 验证表名是否安全。表名可以包含点号分隔的多段（schema.table）。
     *
     * @param tableName 表名
     * @throws MyJpaPlusException 如果表名包含非法字符
     */
    public static void validateTableName(String tableName) {
        validate(tableName);
    }

    /**
     * 验证列名是否安全。列名不能包含点号。
     *
     * @param columnName 列名
     * @throws MyJpaPlusException 如果列名包含非法字符
     */
    public static void validateColumnName(String columnName) {
        if (columnName == null || columnName.isEmpty()) {
            throw new SecurityViolationException("Column name must not be null or empty");
        }
        if (columnName.length() > MAX_IDENTIFIER_LENGTH) {
            throw new SecurityViolationException("Column name length (" + columnName.length() + ") exceeds maximum ("
                + MAX_IDENTIFIER_LENGTH + "): '" + columnName.substring(0, 64) + "...'");
        }
        Pattern validationPattern = unicodeIdentifiers ? UNICODE_IDENTIFIER_PART_PATTERN : SAFE_IDENTIFIER_PATTERN;
        if (!validationPattern.matcher(columnName).matches()) {
            throw new SecurityViolationException("Invalid column name: '" + columnName
                + "'. Must contain only alphanumeric characters and underscores."
                + (unicodeIdentifiers ? "" : " Use myjpa-plus.merge.unicode-identifiers=true for Unicode support."));
        }
        checkHomoglyphs(columnName);
    }

    /**
     * 从实体类解析数据库表名。优先使用 {@code @Table(name)} 注解，
     * 其次使用 {@code @Entity(name)} 注解，最后使用驼峰转下划线策略。
     *
     * <p>
     * 解析后的表名会通过 {@link #validateTableName(String)} 验证安全性。
     *
     * @param entityClass 实体类
     * @return 解析后的数据库表名
     * @throws IllegalArgumentException 如果表名注解包含非法字符
     */
    public static String resolveTableName(Class<?> entityClass) {
        jakarta.persistence.Table tableAnnotation = entityClass.getAnnotation(jakarta.persistence.Table.class);
        if (tableAnnotation != null && !tableAnnotation.name().isEmpty()) {
            StringBuilder tableName = new StringBuilder();
            String catalog = tableAnnotation.catalog();
            String schema = tableAnnotation.schema();
            String name = tableAnnotation.name();
            if (!catalog.isEmpty()) {
                tableName.append(catalog).append('.');
            }
            if (!schema.isEmpty()) {
                tableName.append(schema).append('.');
            }
            tableName.append(name);
            String fullName = tableName.toString();
            validateTableName(fullName);
            return fullName;
        }
        jakarta.persistence.Entity entityAnnotation = entityClass.getAnnotation(jakarta.persistence.Entity.class);
        if (entityAnnotation != null && !entityAnnotation.name().isEmpty()) {
            String name = entityAnnotation.name();
            validateTableName(name);
            return name;
        }
        String name = com.zsubera.jpa.util.StringHelper.camelToSnake(entityClass.getSimpleName());
        validateTableName(name);
        return name;
    }

    /**
     * 验证标识符段是否安全。
     *
     * @param part 标识符段
     * @param fullIdentifier 完整标识符（用于错误消息）
     * @throws MyJpaPlusException 如果标识符段包含非法字符或同形字符
     */
    private static void validatePart(String part, String fullIdentifier) {
        Pattern validationPattern = unicodeIdentifiers ? UNICODE_IDENTIFIER_PART_PATTERN : SAFE_IDENTIFIER_PATTERN;
        if (!validationPattern.matcher(part).matches()) {
            throw new SecurityViolationException("Invalid SQL identifier: '" + fullIdentifier
                + "'. Each part must contain only alphanumeric characters and underscores."
                + (unicodeIdentifiers ? "" : " Use myjpa-plus.merge.unicode-identifiers=true for Unicode support."));
        }
        checkHomoglyphs(part);
    }

    private static void checkHomoglyphs(String part) {
        if (unicodeIdentifiers && HOMOGLYPH_PATTERN.matcher(part).find()) {
            String homoglyphMsg = "SECURITY: Identifier '" + part + "' contains Unicode homoglyph characters "
                + "(Cyrillic/Greek/Armenian). This may indicate a homoglyph attack attempt.";
            throw new SecurityViolationException(homoglyphMsg);
        }
    }
}
