package com.zsubera.jpa.codegen;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Utility class that generates JPA entity class and repository interface source code strings from table name, column
 * definitions, and package name.
 *
 * <p>
 * This is a lightweight code generation helper -- not a Maven plugin. It produces Java source code as strings that can
 * be written to files or used for scaffolding.
 *
 * <p>
 * P2-7: Supports loading custom templates from external files or classpath. Templates support the following
 * placeholders:
 * <ul>
 * <li>{@code ${package}} -- target package name</li>
 * <li>{@code ${className}} -- class name</li>
 * <li>{@code ${tableName}} -- table name</li>
 * <li>{@code ${fields}} -- field declarations</li>
 * <li>{@code ${gettersSetters}} -- getter/setter methods</li>
 * <li>{@code ${imports}} -- extra import statements</li>
 * </ul>
 *
 * <p>
 * Example usage:
 *
 * <pre>{@code
 * List<EntityCodeGenerator.ColumnDef> columns = List.of(new EntityCodeGenerator.ColumnDef("name", "String", false),
 *     new EntityCodeGenerator.ColumnDef("price", "BigDecimal", true));
 * String entitySrc = EntityCodeGenerator.generateEntity("products", columns, "com.example.domain");
 * String repoSrc =
 *     EntityCodeGenerator.generateRepository("products", columns, "com.example.domain", "com.example.repo");
 *
 * // P2-7: Use custom template
 * String template = Files.readString(Path.of("templates/entity.java.tmpl"));
 * String entitySrc = EntityCodeGenerator.generateEntity("products", columns, "com.example.domain", template);
 * }</pre>
 */
public final class EntityCodeGenerator {

    private EntityCodeGenerator() {}

    /**
     * Column definition used for code generation.
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
         * Creates a column definition.
         *
         * @param name column/field name (snake_case for table, camelCase for Java)
         * @param javaType Java type simple name (e.g. "String", "Long", "BigDecimal")
         * @param nullable whether the column is nullable
         * @throws IllegalArgumentException if name or javaType is null or empty or contains invalid characters
         */
        public ColumnDef(String name, String javaType, boolean nullable) {
            if (name == null || name.isEmpty()) {
                throw new IllegalArgumentException("name must not be null or empty");
            }
            if (javaType == null || javaType.isEmpty()) {
                throw new IllegalArgumentException("javaType must not be null or empty");
            }
            // O-07: Validate column name to prevent code injection
            if (!SAFE_COLUMN_NAME.matcher(name).matches()) {
                throw new IllegalArgumentException(
                    "Column name contains invalid characters. Only alphanumeric and underscore are allowed: " + name);
            }
            // O-07: Validate Java type to prevent code injection
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
     * P2-7: Load template from classpath.
     *
     * @param classpathLocation classpath location (e.g. "templates/entity.java.tmpl")
     * @return template content string
     * @throws IllegalArgumentException if classpathLocation is null or file not found
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
     * P2-7: Load template from file system.
     *
     * @param templatePath template file path
     * @return template content string
     * @throws IllegalArgumentException if templatePath is null or file not found
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
     * Generates a JPA entity class source code string.
     *
     * @param tableName the database table name
     * @param columns list of column definitions (excluding the id column which is auto-generated)
     * @param entityPackage the target Java package for the entity
     * @return Java source code string for the entity class
     */
    public static String generateEntity(String tableName, List<ColumnDef> columns, String entityPackage) {
        if (tableName == null || tableName.isBlank()) {
            throw new IllegalArgumentException("tableName must not be blank");
        }
        // P1: Validate tableName to prevent code injection
        if (!tableName.matches("[a-zA-Z0-9_.]+")) {
            throw new IllegalArgumentException(
                "tableName contains invalid characters. Only alphanumeric, underscore, and dot are allowed: "
                    + tableName);
        }
        if (columns == null) {
            throw new IllegalArgumentException("columns must not be null");
        }
        if (entityPackage == null || entityPackage.isBlank()) {
            throw new IllegalArgumentException("entityPackage must not be blank");
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
            sb.append("    private ").append(col.getJavaType()).append(" ").append(col.getName()).append(";\n\n");
        }

        // getters and setters for id
        sb.append("    public Long getId() {\n");
        sb.append("        return id;\n");
        sb.append("    }\n\n");
        sb.append("    public void setId(Long id) {\n");
        sb.append("        this.id = id;\n");
        sb.append("    }\n\n");

        for (ColumnDef col : columns) {
            String capitalName = capitalize(col.getName());
            sb.append("    public ").append(col.getJavaType()).append(" get").append(capitalName).append("() {\n");
            sb.append("        return ").append(col.getName()).append(";\n");
            sb.append("    }\n\n");
            sb.append("    public void set").append(capitalName).append("(").append(col.getJavaType()).append(" ")
                .append(col.getName()).append(") {\n");
            sb.append("        this.").append(col.getName()).append(" = ").append(col.getName()).append(";\n");
            sb.append("    }\n\n");
        }

        sb.append("}\n");
        return sb.toString();
    }

    /**
     * P2-7: Generate entity class source code using a custom template.
     *
     * <p>
     * Template placeholders:
     * <ul>
     * <li>{@code ${package}} -- target package name</li>
     * <li>{@code ${className}} -- class name</li>
     * <li>{@code ${tableName}} -- table name</li>
     * <li>{@code ${fields}} -- field declarations (with annotations)</li>
     * <li>{@code ${gettersSetters}} -- getter/setter methods</li>
     * <li>{@code ${imports}} -- extra import statements</li>
     * </ul>
     *
     * @param tableName database table name
     * @param columns column definitions
     * @param entityPackage target Java package
     * @param template custom template string
     * @return generated Java source code string
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
     * Generates a Spring Data JPA repository interface source code string.
     *
     * @param tableName the database table name (used to derive the entity class name)
     * @param columns list of column definitions (currently unused but reserved for future query method generation)
     * @param entityPackage the Java package of the entity class
     * @param repoPackage the target Java package for the repository interface
     * @return Java source code string for the repository interface
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
     * P2-7: Generate repository interface source code using a custom template.
     *
     * @param tableName database table name
     * @param columns column definitions
     * @param entityPackage entity class Java package
     * @param repoPackage repository interface target Java package
     * @param template custom template string
     * @return generated Java source code string
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
     * Converts a snake_case table name to a PascalCase class name.
     *
     * @param tableName table name (e.g. "user_accounts")
     * @return class name (e.g. "UserAccounts")
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
        return sb.toString();
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
            sb.append("    private ").append(col.getJavaType()).append(" ").append(col.getName()).append(";\n\n");
        }
        return sb.toString();
    }

    private static String buildGettersSetters(List<ColumnDef> columns) {
        StringBuilder sb = new StringBuilder();
        // id getter/setter
        sb.append("    public Long getId() {\n");
        sb.append("        return id;\n");
        sb.append("    }\n\n");
        sb.append("    public void setId(Long id) {\n");
        sb.append("        this.id = id;\n");
        sb.append("    }\n\n");
        for (ColumnDef col : columns) {
            String capitalName = capitalize(col.getName());
            sb.append("    public ").append(col.getJavaType()).append(" get").append(capitalName).append("() {\n");
            sb.append("        return ").append(col.getName()).append(";\n");
            sb.append("    }\n\n");
            sb.append("    public void set").append(capitalName).append("(").append(col.getJavaType()).append(" ")
                .append(col.getName()).append(") {\n");
            sb.append("        this.").append(col.getName()).append(" = ").append(col.getName()).append(";\n");
            sb.append("    }\n\n");
        }
        return sb.toString();
    }
}
