package com.zsubera.jpa.codegen;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 从表名、列定义和包名生成 JPA 实体类和仓库接口源码字符串的工具类。
 *
 * <p>
 * 这是一个轻量级的代码生成辅助工具——不是 Maven 插件。它生成 Java 源码字符串，可以 写入文件或用于脚手架。
 *
 * @apiNote 此类为实验性 API，可能在未来版本中发生不兼容变更。
 *          它是独立的脚手架工具，不属于 MyJpa-Plus 核心查询/批量操作功能。
 */
public final class EntityCodeGenerator {

    private EntityCodeGenerator() {}

    /**
         * 用于代码生成的列定义。
         */
    public record ColumnDef(String name, String javaType, boolean nullable) {

        private static final Pattern SAFE_COLUMN_NAME = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]*$");
        private static final Pattern SAFE_JAVA_TYPE = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_.<>,?]*$");

        /**
         * 创建列定义。
         *
         * @param name 列/字段名（表用 snake_case，Java 用 camelCase）
         * @param javaType Java 类型简单名称（如 "String"、"Long"、"BigDecimal"）
         * @param nullable 列是否可为空
         * @throws IllegalArgumentException 如果 name 或 javaType 为空或包含无效字符
         */
        public ColumnDef {
            if (name == null || name.isEmpty()) {
                throw new IllegalArgumentException("name must not be null or empty");
            }
            if (javaType == null || javaType.isEmpty()) {
                throw new IllegalArgumentException("javaType must not be null or empty");
            }
            // O-07：校验列名以防止代码注入
            if (!SAFE_COLUMN_NAME.matcher(name).matches()) {
                throw new IllegalArgumentException(
                    "Column name contains invalid characters. Only alphanumeric and underscore are allowed: " + name);
            }
            // O-07：校验 Java 类型以防止代码注入
            if (!SAFE_JAVA_TYPE.matcher(javaType).matches()) {
                throw new IllegalArgumentException("Java type contains invalid characters: " + javaType);
            }
        }

        /**
         * 获取列名。
         *
         * @return 列名
         */
        @Override
        public String name() {
            return name;
        }

        /**
         * 获取 Java 类型名称。
         *
         * @return Java 类型简单名称（如 "String"、"Long"）
         */
        @Override
        public String javaType() {
            return javaType;
        }

        /**
         * 检查列是否可为空。
         *
         * @return 如果列可为空返回 true
         */
        @Override
        public boolean nullable() {
            return nullable;
        }
    }

    /**
     * 从 classpath 加载模板。
     *
     * @param classpathLocation classpath 位置（如 "templates/entity.java.tmpl"）
     * @return 模板内容字符串
     * @throws IllegalArgumentException 如果 classpathLocation 为空或文件未找到
     */
    public static String loadTemplateFromClasspath(String classpathLocation) {
        if (classpathLocation == null || classpathLocation.isBlank()) {
            throw new IllegalArgumentException("classpathLocation must not be blank");
        }
        try (InputStream is = EntityCodeGenerator.class.getClassLoader().getResourceAsStream(classpathLocation)) {
            if (is == null) {
                throw new IllegalArgumentException("Template not found on classpath: " + classpathLocation);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to read template from classpath: " + classpathLocation, e);
        }
    }

    /**
     * 从文件系统加载模板。
     *
     * @param templatePath 模板文件路径
     * @return 模板内容字符串
     * @throws IllegalArgumentException 如果 templatePath 为空或文件未找到
     */
    public static String loadTemplateFromFile(Path templatePath) {
        if (templatePath == null) {
            throw new IllegalArgumentException("templatePath must not be null");
        }
        try {
            return Files.readString(templatePath, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to read template from file: " + templatePath, e);
        }
    }

    /**
     * 生成 JPA 实体类源码字符串。
     *
     * @param tableName 数据库表名
     * @param columns 列定义列表（不包括自动生成的 id 列）
     * @param entityPackage 实体的目标 Java 包
     * @return 实体类的 Java 源码字符串
     */
    public static String generateEntity(String tableName, List<ColumnDef> columns, String entityPackage) {
        validateTableName(tableName);
        validateColumns(columns);
        validatePackage(entityPackage);

        String className = toClassName(tableName);
        String imports = buildExtraImports(columns);
        String fields = buildFields(columns);
        String gettersSetters = buildGettersSetters(columns);

        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(entityPackage).append(";\n\n");
        sb.append("import jakarta.persistence.Column;\n");
        sb.append("import jakarta.persistence.Entity;\n");
        sb.append("import jakarta.persistence.GeneratedValue;\n");
        sb.append("import jakarta.persistence.GenerationType;\n");
        sb.append("import jakarta.persistence.Id;\n");
        sb.append("import jakarta.persistence.Table;\n");
        sb.append(imports);
        sb.append("\n");

        sb.append("@Entity\n");
        sb.append("@Table(name = \"").append(tableName).append("\")\n");
        sb.append("public class ").append(className).append(" {\n\n");
        sb.append(fields);
        sb.append(gettersSetters);
        sb.append("}\n");
        return sb.toString();
    }

    /**
     * 使用自定义模板生成实体类源码。
     *
     * <p>
     * 模板占位符：
     * <ul>
     * <li>{@code ${package}} -- 目标包名</li>
     * <li>{@code ${className}} -- 类名</li>
     * <li>{@code ${tableName}} -- 表名</li>
     * <li>{@code ${fields}} -- 字段声明（带注解）</li>
     * <li>{@code ${gettersSetters}} -- getter/setter 方法</li>
     * <li>{@code ${imports}} -- 额外的 import 语句</li>
     * </ul>
     *
     * @param tableName 数据库表名
     * @param columns 列定义
     * @param entityPackage 目标 Java 包
     * @param template 自定义模板字符串
     * @return 生成的 Java 源码字符串
     */
    public static String generateEntity(String tableName, List<ColumnDef> columns, String entityPackage,
        String template) {
        if (template == null || template.isBlank()) {
            return generateEntity(tableName, columns, entityPackage);
        }
        validateTableName(tableName);
        validateColumns(columns);
        validatePackage(entityPackage);
        String className = toClassName(tableName);
        String imports = buildExtraImports(columns);
        String fields = buildFields(columns);
        String gettersSetters = buildGettersSetters(columns);
        // ponytail: template 参数必须是受信任的模板字符串，不要接受用户输入
        return template.replace("${package}", entityPackage).replace("${className}", className)
            .replace("${tableName}", tableName).replace("${imports}", imports).replace("${fields}", fields)
            .replace("${gettersSetters}", gettersSetters);
    }

    /**
     * 生成 Spring Data JPA 仓库接口源码字符串。
     *
     * @param tableName 数据库表名（用于推导实体类名）
     * @param columns 列定义列表（当前未使用但为将来查询方法生成保留）
     * @param entityPackage 实体类的 Java 包
     * @param repoPackage 仓库接口的目标 Java 包
     * @return 仓库接口的 Java 源码字符串
     */
    public static String generateRepository(String tableName, List<ColumnDef> columns, String entityPackage,
        String repoPackage) {
        validateTableName(tableName);
        validateColumns(columns);
        validatePackage(entityPackage);
        validatePackage(repoPackage);

        String className = toClassName(tableName);
        String repoName = className + "Repository";

        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(repoPackage).append(";\n\n");
        sb.append("import ").append(entityPackage).append(".").append(className).append(";\n");
        sb.append("import com.zsubera.jpa.repository.MyJpaRepository;\n");
        sb.append("import org.springframework.stereotype.Repository;\n\n");
        sb.append("@Repository\n");
        sb.append("public interface ").append(repoName).append(" extends MyJpaRepository<").append(className)
            .append(", Long> {\n");
        sb.append("}\n");
        return sb.toString();
    }

    /**
     * 使用自定义模板生成仓库接口源码。
     *
     * @param tableName 数据库表名
     * @param columns 列定义
     * @param entityPackage 实体类 Java 包
     * @param repoPackage 仓库接口目标 Java 包
     * @param template 自定义模板字符串
     * @return 生成的 Java 源码字符串
     */
    public static String generateRepository(String tableName, List<ColumnDef> columns, String entityPackage,
        String repoPackage, String template) {
        if (template == null || template.isBlank()) {
            return generateRepository(tableName, columns, entityPackage, repoPackage);
        }
        validateTableName(tableName);
        validateColumns(columns);
        validatePackage(entityPackage);
        validatePackage(repoPackage);
        String className = toClassName(tableName);
        String repoName = className + "Repository";
        return template.replace("${package}", repoPackage).replace("${entityPackage}", entityPackage)
            .replace("${className}", className).replace("${repoName}", repoName).replace("${tableName}", tableName);
    }

    // ---- 验证辅助方法 ----

    private static void validateTableName(String tableName) {
        if (tableName == null || tableName.isBlank()) {
            throw new IllegalArgumentException("tableName must not be blank");
        }
        if (!tableName.matches("[a-zA-Z0-9_]+")) {
            throw new IllegalArgumentException(
                "tableName contains invalid characters. Only alphanumeric and underscore are allowed: " + tableName);
        }
    }

    private static void validateColumns(List<ColumnDef> columns) {
        if (columns == null) {
            throw new IllegalArgumentException("columns must not be null");
        }
    }

    private static void validatePackage(String packageName) {
        if (packageName == null || packageName.isBlank()) {
            throw new IllegalArgumentException("package must not be blank");
        }
        if (!packageName.matches("[a-zA-Z_][a-zA-Z0-9_.]*")) {
            throw new IllegalArgumentException(
                "package contains invalid characters. Only alphanumeric, underscore, and dot are allowed: "
                    + packageName);
        }
        if (packageName.startsWith(".") || packageName.endsWith(".") || packageName.contains("..")) {
            throw new IllegalArgumentException(
                "package must not start/end with dot or contain consecutive dots: " + packageName);
        }
    }

    /**
     * 将 snake_case 表名转换为 PascalCase 类名。
     *
     * @param tableName 表名（如 "user_accounts"）
     * @return 类名（如 "UserAccounts"）
     */
    static String toClassName(String tableName) {
        StringBuilder sb = new StringBuilder();
        boolean capitalizeNext = true;
        for (char c : tableName.toCharArray()) {
            if (c == '_') {
                capitalizeNext = true;
            } else if (capitalizeNext) {
                sb.append(Character.toUpperCase(c));
                capitalizeNext = false;
            } else {
                sb.append(c);
            }
        }
        String result = sb.toString();
        if (result.isEmpty()) {
            throw new IllegalArgumentException("Cannot derive a class name from table name: " + tableName);
        }
        // 处理以数字开头的表名
        if (Character.isDigit(result.charAt(0))) {
            result = "T" + result;
        }
        return result;
    }

    private static final java.util.Set<String> JAVA_RESERVED_WORDS = java.util.Set.of("abstract", "assert", "boolean",
        "break", "byte", "case", "catch", "char", "class", "const", "continue", "default", "do", "double", "else",
        "enum", "extends", "final", "finally", "float", "for", "goto", "if", "implements", "import", "instanceof",
        "int", "interface", "long", "native", "new", "package", "private", "protected", "public", "return", "short",
        "static", "strictfp", "super", "switch", "synchronized", "this", "throw", "throws", "transient", "try", "void",
        "volatile", "while", "true", "false", "null", "var", "yield", "record", "sealed", "permits", "exports",
        "module", "opens", "provides", "requires", "to", "transitive", "uses", "with", "_");

    /**
     * 校验字段名是否为 Java 保留字，如果是则追加下划线后缀。
     *
     * @param fieldName 字段名
     * @return 安全的字段名
     */
    static String sanitizeFieldName(String fieldName) {
        if (fieldName == null || fieldName.isEmpty()) {
            return fieldName;
        }
        if (JAVA_RESERVED_WORDS.contains(fieldName)) {
            return fieldName + "_";
        }
        if (Character.isDigit(fieldName.charAt(0))) {
            return "_" + fieldName;
        }
        return fieldName;
    }

    private static String capitalize(String name) {
        if (name == null || name.isEmpty()) {
            return name;
        }
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    private static void appendExtraImports(StringBuilder sb, List<ColumnDef> columns) {
        boolean needsBigDecimal = columns.stream().anyMatch(c -> "BigDecimal".equals(c.javaType()));
        boolean needsLocalDate = columns.stream().anyMatch(c -> "LocalDate".equals(c.javaType()));
        boolean needsLocalDateTime = columns.stream().anyMatch(c -> "LocalDateTime".equals(c.javaType()));
        boolean needsInstant = columns.stream().anyMatch(c -> "Instant".equals(c.javaType()));

        if (needsBigDecimal) {
            sb.append("import java.math.BigDecimal;\n");
        }
        if (needsLocalDate) {
            sb.append("import java.time.LocalDate;\n");
        }
        if (needsLocalDateTime) {
            sb.append("import java.time.LocalDateTime;\n");
        }
        if (needsInstant) {
            sb.append("import java.time.Instant;\n");
        }
    }

    private static String buildExtraImports(List<ColumnDef> columns) {
        StringBuilder sb = new StringBuilder();
        appendExtraImports(sb, columns);
        return sb.toString();
    }

    private static String buildFields(List<ColumnDef> columns) {
        StringBuilder sb = new StringBuilder();
        sb.append("    @Id\n");
        sb.append("    @GeneratedValue(strategy = GenerationType.IDENTITY)\n");
        sb.append("    private Long id;\n\n");
        for (ColumnDef col : columns) {
            String safeName = sanitizeFieldName(col.name());
            boolean nameSanitized = !safeName.equals(col.name());
            if (!col.nullable() || nameSanitized) {
                sb.append("    @Column(");
                java.util.List<String> attrs = new java.util.ArrayList<>();
                if (nameSanitized) {
                    attrs.add("name = \"" + col.name() + "\"");
                }
                if (!col.nullable()) {
                    attrs.add("nullable = false");
                }
                sb.append(String.join(", ", attrs)).append(")\n");
            }
            sb.append("    private ").append(col.javaType()).append(" ").append(safeName).append(";\n\n");
        }
        return sb.toString();
    }

    private static String buildGettersSetters(List<ColumnDef> columns) {
        StringBuilder sb = new StringBuilder();
        // id 的 getter/setter
        sb.append("    public Long getId() {\n");
        sb.append("        return id;\n");
        sb.append("    }\n\n");
        sb.append("    public void setId(Long id) {\n");
        sb.append("        this.id = id;\n");
        sb.append("    }\n\n");
        for (ColumnDef col : columns) {
            String safeName = sanitizeFieldName(col.name());
            String capitalName = capitalize(safeName);
            sb.append("    public ").append(col.javaType()).append(" get").append(capitalName).append("() {\n");
            sb.append("        return ").append(safeName).append(";\n");
            sb.append("    }\n\n");
            sb.append("    public void set").append(capitalName).append("(").append(col.javaType()).append(" ")
                .append(safeName).append(") {\n");
            sb.append("        this.").append(safeName).append(" = ").append(safeName).append(";\n");
            sb.append("    }\n\n");
        }
        return sb.toString();
    }
}
