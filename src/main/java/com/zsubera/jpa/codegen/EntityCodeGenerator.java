package com.zsubera.jpa.codegen;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 从表名、列定义和包名生成 JPA 实体类和仓库接口源码字符串的工具类。
 *
 * <p>
 * 这是一个轻量级的代码生成辅助工具——不是 Maven 插件。它生成 Java 源码字符串，可以 写入文件或用于脚手架。
 *
 * <p>
 * 支持从外部文件或 classpath 加载自定义模板。模板支持以下占位符：
 * <ul>
 * <li>{@code ${package}} -- 目标包名</li>
 * <li>{@code ${className}} -- 类名</li>
 * <li>{@code ${tableName}} -- 表名</li>
 * <li>{@code ${fields}} -- 字段声明</li>
 * <li>{@code ${gettersSetters}} -- getter/setter 方法</li>
 * <li>{@code ${imports}} -- 额外的 import 语句</li>
 * </ul>
 *
 * <p>
 * 使用示例：
 *
 * <pre>{@code
 * List<EntityCodeGenerator.ColumnDef> columns = List.of(new EntityCodeGenerator.ColumnDef("name", "String", false),
 *     new EntityCodeGenerator.ColumnDef("price", "BigDecimal", true));
 * String entitySrc = EntityCodeGenerator.generateEntity("products", columns, "com.example.domain");
 * String repoSrc =
 *     EntityCodeGenerator.generateRepository("products", columns, "com.example.domain", "com.example.repo");
 *
 * // 使用自定义模板
 * String template = Files.readString(Path.of("templates/entity.java.tmpl"));
 * String entitySrc = EntityCodeGenerator.generateEntity("products", columns, "com.example.domain", template);
 * }</pre>
 */
public final class EntityCodeGenerator {

    private EntityCodeGenerator() {}

    /**
     * 用于代码生成的列定义。
     */
    public static final class ColumnDef {

        private static final java.util.regex.Pattern SAFE_COLUMN_NAME =
            java.util.regex.Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]*$");
        private static final java.util.regex.Pattern SAFE_JAVA_TYPE =
            java.util.regex.Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_.<>,?]*$");

        private final String name;
        private final String javaType;
        private final boolean nullable;

        /**
         * 创建列定义。
         *
         * @param name 列/字段名（表用 snake_case，Java 用 camelCase）
         * @param javaType Java 类型简单名称（如 "String"、"Long"、"BigDecimal"）
         * @param nullable 列是否可为空
         * @throws IllegalArgumentException 如果 name 或 javaType 为空或包含无效字符
         */
        public ColumnDef(String name, String javaType, boolean nullable) {
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
            this.name = name;
            this.javaType = javaType;
            this.nullable = nullable;
        }

        public String getName() {
            return name;
        }

        public String getJavaType() {
            return javaType;
        }

        public boolean isNullable() {
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
        if (tableName == null || tableName.isBlank()) {
            throw new IllegalArgumentException("tableName must not be blank");
        }
        // 校验 tableName 以防止代码注入
        if (!tableName.matches("[a-zA-Z0-9_]+")) {
            throw new IllegalArgumentException(
                "tableName contains invalid characters. Only alphanumeric and underscore are allowed: " + tableName);
        }
        if (columns == null) {
            throw new IllegalArgumentException("columns must not be null");
        }
        if (entityPackage == null || entityPackage.isBlank()) {
            throw new IllegalArgumentException("entityPackage must not be blank");
        }
        // 校验包名以防止代码注入
        if (!entityPackage.matches("[a-zA-Z_][a-zA-Z0-9_.]*")) {
            throw new IllegalArgumentException(
                "entityPackage contains invalid characters. Only alphanumeric, underscore, and dot are allowed: "
                    + entityPackage);
        }
        if (entityPackage.startsWith(".") || entityPackage.endsWith(".") || entityPackage.contains("..")) {
            throw new IllegalArgumentException(
                "entityPackage must not start/end with dot or contain consecutive dots: " + entityPackage);
        }

        String className = toClassName(tableName);
        StringBuilder sb = new StringBuilder();

        sb.append("package ").append(entityPackage).append(";\n\n");
        sb.append("import jakarta.persistence.Column;\n");
        sb.append("import jakarta.persistence.Entity;\n");
        sb.append("import jakarta.persistence.GeneratedValue;\n");
        sb.append("import jakarta.persistence.GenerationType;\n");
        sb.append("import jakarta.persistence.Id;\n");
        sb.append("import jakarta.persistence.Table;\n");
        appendExtraImports(sb, columns);
        sb.append("\n");

        sb.append("@Entity\n");
        sb.append("@Table(name = \"").append(tableName).append("\")\n");
        sb.append("public class ").append(className).append(" {\n\n");

        sb.append("    @Id\n");
        sb.append("    @GeneratedValue(strategy = GenerationType.IDENTITY)\n");
        sb.append("    private Long id;\n\n");

        for (ColumnDef col : columns) {
            if (!col.isNullable()) {
                sb.append("    @Column(nullable = false)\n");
            }
            String safeName = sanitizeFieldName(col.getName());
            sb.append("    private ").append(col.getJavaType()).append(" ").append(safeName).append(";\n\n");
        }

        // id 的 getter/setter
        sb.append("    public Long getId() {\n");
        sb.append("        return id;\n");
        sb.append("    }\n\n");
        sb.append("    public void setId(Long id) {\n");
        sb.append("        this.id = id;\n");
        sb.append("    }\n\n");

        for (ColumnDef col : columns) {
            String safeName = sanitizeFieldName(col.getName());
            String capitalName = capitalize(safeName);
            sb.append("    public ").append(col.getJavaType()).append(" get").append(capitalName).append("() {\n");
            sb.append("        return ").append(safeName).append(";\n");
            sb.append("    }\n\n");
            sb.append("    public void set").append(capitalName).append("(").append(col.getJavaType()).append(" ")
                .append(safeName).append(") {\n");
            sb.append("        this.").append(safeName).append(" = ").append(safeName).append(";\n");
            sb.append("    }\n\n");
        }

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
        String className = toClassName(tableName);
        String imports = buildExtraImports(columns);
        String fields = buildFields(columns);
        String gettersSetters = buildGettersSetters(columns);
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
        if (tableName == null || tableName.isBlank()) {
            throw new IllegalArgumentException("tableName must not be blank");
        }
        if (columns == null) {
            throw new IllegalArgumentException("columns must not be null");
        }
        if (entityPackage == null || entityPackage.isBlank()) {
            throw new IllegalArgumentException("entityPackage must not be blank");
        }
        if (repoPackage == null || repoPackage.isBlank()) {
            throw new IllegalArgumentException("repoPackage must not be blank");
        }

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
        String className = toClassName(tableName);
        String repoName = className + "Repository";
        return template.replace("${package}", repoPackage).replace("${entityPackage}", entityPackage)
            .replace("${className}", className).replace("${repoName}", repoName).replace("${tableName}", tableName);
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
        // 处理以数字开头的表名
        if (!result.isEmpty() && Character.isDigit(result.charAt(0))) {
            result = "T" + result;
        }
        return result;
    }

    private static final java.util.Set<String> JAVA_RESERVED_WORDS = java.util.Set.of("abstract", "assert", "boolean",
        "break", "byte", "case", "catch", "char", "class", "const", "continue", "default", "do", "double", "else",
        "enum", "extends", "final", "finally", "float", "for", "goto", "if", "implements", "import", "instanceof",
        "int", "interface", "long", "native", "new", "package", "private", "protected", "public", "return", "short",
        "static", "strictfp", "super", "switch", "synchronized", "this", "throw", "throws", "transient", "try", "void",
        "volatile", "while", "true", "false", "null");

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
        // 如果字段名是 Java 保留字则追加下划线
        if (JAVA_RESERVED_WORDS.contains(fieldName.toLowerCase())) {
            return fieldName + "_";
        }
        // 如果以数字开头则前置下划线
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
        boolean needsBigDecimal = columns.stream().anyMatch(c -> "BigDecimal".equals(c.getJavaType()));
        boolean needsLocalDate = columns.stream().anyMatch(c -> "LocalDate".equals(c.getJavaType()));
        boolean needsLocalDateTime = columns.stream().anyMatch(c -> "LocalDateTime".equals(c.getJavaType()));
        boolean needsInstant = columns.stream().anyMatch(c -> "Instant".equals(c.getJavaType()));

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
        if (columns.stream().anyMatch(c -> "BigDecimal".equals(c.getJavaType()))) {
            sb.append("import java.math.BigDecimal;\n");
        }
        if (columns.stream().anyMatch(c -> "LocalDate".equals(c.getJavaType()))) {
            sb.append("import java.time.LocalDate;\n");
        }
        if (columns.stream().anyMatch(c -> "LocalDateTime".equals(c.getJavaType()))) {
            sb.append("import java.time.LocalDateTime;\n");
        }
        if (columns.stream().anyMatch(c -> "Instant".equals(c.getJavaType()))) {
            sb.append("import java.time.Instant;\n");
        }
        return sb.toString();
    }

    private static String buildFields(List<ColumnDef> columns) {
        StringBuilder sb = new StringBuilder();
        sb.append("    @Id\n");
        sb.append("    @GeneratedValue(strategy = GenerationType.IDENTITY)\n");
        sb.append("    private Long id;\n\n");
        for (ColumnDef col : columns) {
            if (!col.isNullable()) {
                sb.append("    @Column(nullable = false)\n");
            }
            String safeName = sanitizeFieldName(col.getName());
            sb.append("    private ").append(col.getJavaType()).append(" ").append(safeName).append(";\n\n");
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
            String safeName = sanitizeFieldName(col.getName());
            String capitalName = capitalize(safeName);
            sb.append("    public ").append(col.getJavaType()).append(" get").append(capitalName).append("() {\n");
            sb.append("        return ").append(safeName).append(";\n");
            sb.append("    }\n\n");
            sb.append("    public void set").append(capitalName).append("(").append(col.getJavaType()).append(" ")
                .append(safeName).append(") {\n");
            sb.append("        this.").append(safeName).append(" = ").append(safeName).append(";\n");
            sb.append("    }\n\n");
        }
        return sb.toString();
    }
}
