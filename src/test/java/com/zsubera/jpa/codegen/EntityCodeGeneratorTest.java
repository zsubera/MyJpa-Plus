package com.zsubera.jpa.codegen;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link EntityCodeGenerator}.
 */
class EntityCodeGeneratorTest {

    @Test
    void generateEntity_basicEntity() {
        List<EntityCodeGenerator.ColumnDef> columns =
            List.of(new EntityCodeGenerator.ColumnDef("name", "String", false),
                new EntityCodeGenerator.ColumnDef("price", "BigDecimal", true));

        String source = EntityCodeGenerator.generateEntity("products", columns, "com.example.domain");

        assertTrue(source.contains("package com.example.domain;"));
        assertTrue(source.contains("public class Products"));
        assertTrue(source.contains("@Table(name = \"products\")"));
        assertTrue(source.contains("private Long id;"));
        assertTrue(source.contains("private String name;"));
        assertTrue(source.contains("private BigDecimal price;"));
        assertTrue(source.contains("@Column(nullable = false)"));
        assertTrue(source.contains("public String getName()"));
        assertTrue(source.contains("public void setName(String name)"));
        assertTrue(source.contains("public BigDecimal getPrice()"));
        assertTrue(source.contains("public void setPrice(BigDecimal price)"));
        assertTrue(source.contains("import java.math.BigDecimal;"));
    }

    @Test
    void generateEntity_snakeCaseTableName() {
        List<EntityCodeGenerator.ColumnDef> columns =
            List.of(new EntityCodeGenerator.ColumnDef("userName", "String", false));

        String source = EntityCodeGenerator.generateEntity("user_accounts", columns, "com.example");

        assertTrue(source.contains("public class UserAccounts"));
        assertTrue(source.contains("@Table(name = \"user_accounts\")"));
    }

    @Test
    void generateEntity_withDateTypes() {
        List<EntityCodeGenerator.ColumnDef> columns =
            List.of(new EntityCodeGenerator.ColumnDef("birthDate", "LocalDate", true),
                new EntityCodeGenerator.ColumnDef("createdAt", "LocalDateTime", false),
                new EntityCodeGenerator.ColumnDef("updatedAt", "Instant", true));

        String source = EntityCodeGenerator.generateEntity("people", columns, "com.example");

        assertTrue(source.contains("import java.time.LocalDate;"));
        assertTrue(source.contains("import java.time.LocalDateTime;"));
        assertTrue(source.contains("import java.time.Instant;"));
    }

    @Test
    void generateEntity_noNullableAnnotationForNullableColumn() {
        List<EntityCodeGenerator.ColumnDef> columns =
            List.of(new EntityCodeGenerator.ColumnDef("description", "String", true));

        String source = EntityCodeGenerator.generateEntity("items", columns, "com.example");

        // nullable=true means no @Column annotation
        assertFalse(source.contains("@Column(nullable = false)"));
        assertTrue(source.contains("private String description;"));
    }

    @Test
    void generateRepository_basicRepo() {
        List<EntityCodeGenerator.ColumnDef> columns =
            List.of(new EntityCodeGenerator.ColumnDef("name", "String", false));

        String source =
            EntityCodeGenerator.generateRepository("products", columns, "com.example.domain", "com.example.repo");

        assertTrue(source.contains("package com.example.repo;"));
        assertTrue(source.contains("import com.example.domain.Products;"));
        assertTrue(source.contains("public interface ProductsRepository extends MyJpaRepository<Products, Long>"));
        assertTrue(source.contains("@Repository"));
    }

    @Test
    void generateEntity_nullTableName_throws() {
        assertThrows(IllegalArgumentException.class,
            () -> EntityCodeGenerator.generateEntity(null, List.of(), "com.example"));
    }

    @Test
    void generateEntity_blankTableName_throws() {
        assertThrows(IllegalArgumentException.class,
            () -> EntityCodeGenerator.generateEntity("  ", List.of(), "com.example"));
    }

    @Test
    void generateEntity_nullColumns_throws() {
        assertThrows(IllegalArgumentException.class,
            () -> EntityCodeGenerator.generateEntity("table", null, "com.example"));
    }

    @Test
    void generateEntity_nullPackage_throws() {
        assertThrows(IllegalArgumentException.class,
            () -> EntityCodeGenerator.generateEntity("table", List.of(), null));
    }

    @Test
    void generateRepository_nullRepoPackage_throws() {
        assertThrows(IllegalArgumentException.class,
            () -> EntityCodeGenerator.generateRepository("table", List.of(), "com.example", null));
    }

    @Test
    void toClassName_simpleConversion() {
        assertEquals("Users", EntityCodeGenerator.toClassName("users"));
        assertEquals("UserAccounts", EntityCodeGenerator.toClassName("user_accounts"));
        assertEquals("OrderItems", EntityCodeGenerator.toClassName("order_items"));
    }

    @Test
    void toClassName_numericPrefix() {
        assertEquals("T123Users", EntityCodeGenerator.toClassName("123_users"));
        assertEquals("T0Table", EntityCodeGenerator.toClassName("0_table"));
    }

    @Test
    void toClassName_singleWord() {
        assertEquals("Users", EntityCodeGenerator.toClassName("users"));
    }

    @Test
    void toClassName_emptyString() {
        assertThrows(IllegalArgumentException.class, () -> EntityCodeGenerator.toClassName(""));
    }

    @Test
    void columnDef_invalidName_throws() {
        assertThrows(IllegalArgumentException.class,
            () -> new EntityCodeGenerator.ColumnDef("name;DROP", "String", false));
        assertThrows(IllegalArgumentException.class,
            () -> new EntityCodeGenerator.ColumnDef("name spaces", "String", false));
        assertThrows(IllegalArgumentException.class,
            () -> new EntityCodeGenerator.ColumnDef("name-dash", "String", false));
    }

    @Test
    void columnDef_invalidJavaType_throws() {
        assertThrows(IllegalArgumentException.class,
            () -> new EntityCodeGenerator.ColumnDef("col", "String;exec", false));
    }

    @Test
    void columnDef_nullName_throws() {
        assertThrows(IllegalArgumentException.class, () -> new EntityCodeGenerator.ColumnDef(null, "String", false));
    }

    @Test
    void columnDef_emptyName_throws() {
        assertThrows(IllegalArgumentException.class, () -> new EntityCodeGenerator.ColumnDef("", "String", false));
    }

    @Test
    void columnDef_nullJavaType_throws() {
        assertThrows(IllegalArgumentException.class, () -> new EntityCodeGenerator.ColumnDef("col", null, false));
    }

    @Test
    void columnDef_emptyJavaType_throws() {
        assertThrows(IllegalArgumentException.class, () -> new EntityCodeGenerator.ColumnDef("col", "", false));
    }

    @Test
    void sanitizeFieldName_reservedWord() {
        assertEquals("class_", EntityCodeGenerator.sanitizeFieldName("class"));
        assertEquals("int_", EntityCodeGenerator.sanitizeFieldName("int"));
        assertEquals("new_", EntityCodeGenerator.sanitizeFieldName("new"));
        assertEquals("null_", EntityCodeGenerator.sanitizeFieldName("null"));
    }

    @Test
    void sanitizeFieldName_numericPrefix() {
        assertEquals("_1column", EntityCodeGenerator.sanitizeFieldName("1column"));
    }

    @Test
    void sanitizeFieldName_normal() {
        assertEquals("userName", EntityCodeGenerator.sanitizeFieldName("userName"));
    }

    @Test
    void sanitizeFieldName_null() {
        assertNull(EntityCodeGenerator.sanitizeFieldName(null));
    }

    @Test
    void sanitizeFieldName_empty() {
        assertEquals("", EntityCodeGenerator.sanitizeFieldName(""));
    }

    @Test
    void generateEntity_withCustomTemplate() {
        String template = "package ${package};\nclass ${className} {}\n";
        List<EntityCodeGenerator.ColumnDef> columns =
            List.of(new EntityCodeGenerator.ColumnDef("name", "String", false));
        String source = EntityCodeGenerator.generateEntity("users", columns, "com.example", template);
        assertTrue(source.contains("package com.example;"));
        assertTrue(source.contains("class Users {}"));
    }

    @Test
    void generateEntity_withCustomTemplate_blankFallsBack() {
        List<EntityCodeGenerator.ColumnDef> columns =
            List.of(new EntityCodeGenerator.ColumnDef("name", "String", false));
        String source = EntityCodeGenerator.generateEntity("users", columns, "com.example", "  ");
        assertTrue(source.contains("public class Users"));
    }

    @Test
    void generateRepository_withCustomTemplate() {
        String template = "package ${package};\ninterface ${repoName} {}\n";
        List<EntityCodeGenerator.ColumnDef> columns =
            List.of(new EntityCodeGenerator.ColumnDef("name", "String", false));
        String source = EntityCodeGenerator.generateRepository("users", columns, "com.example.domain",
            "com.example.repo", template);
        assertTrue(source.contains("package com.example.repo;"));
        assertTrue(source.contains("interface UsersRepository {}"));
    }

    @Test
    void generateRepository_withCustomTemplate_blankFallsBack() {
        List<EntityCodeGenerator.ColumnDef> columns =
            List.of(new EntityCodeGenerator.ColumnDef("name", "String", false));
        String source =
            EntityCodeGenerator.generateRepository("users", columns, "com.example.domain", "com.example.repo", "");
        assertTrue(source.contains("public interface UsersRepository"));
    }

    @Test
    void loadTemplateFromClasspath_notFound_throws() {
        assertThrows(IllegalArgumentException.class,
            () -> EntityCodeGenerator.loadTemplateFromClasspath("nonexistent/template.tmpl"));
    }

    @Test
    void loadTemplateFromClasspath_blank_throws() {
        assertThrows(IllegalArgumentException.class, () -> EntityCodeGenerator.loadTemplateFromClasspath(""));
        assertThrows(IllegalArgumentException.class, () -> EntityCodeGenerator.loadTemplateFromClasspath(null));
    }

    @Test
    void loadTemplateFromFile_notFound_throws() {
        assertThrows(IllegalArgumentException.class,
            () -> EntityCodeGenerator.loadTemplateFromFile(java.nio.file.Path.of("/nonexistent/template.tmpl")));
    }

    @Test
    void loadTemplateFromFile_null_throws() {
        assertThrows(IllegalArgumentException.class, () -> EntityCodeGenerator.loadTemplateFromFile(null));
    }

    @Test
    void generateEntity_invalidTableName_throws() {
        assertThrows(IllegalArgumentException.class,
            () -> EntityCodeGenerator.generateEntity("table;DROP", List.of(), "com.example"));
    }

    @Test
    void generateEntity_invalidPackage_throws() {
        assertThrows(IllegalArgumentException.class,
            () -> EntityCodeGenerator.generateEntity("table", List.of(), ".com.example"));
        assertThrows(IllegalArgumentException.class,
            () -> EntityCodeGenerator.generateEntity("table", List.of(), "com.example."));
        assertThrows(IllegalArgumentException.class,
            () -> EntityCodeGenerator.generateEntity("table", List.of(), "com..example"));
        assertThrows(IllegalArgumentException.class,
            () -> EntityCodeGenerator.generateEntity("table", List.of(), "com-example"));
    }

    @Test
    void generateEntity_emptyColumns() {
        String source = EntityCodeGenerator.generateEntity("users", List.of(), "com.example");
        assertTrue(source.contains("private Long id;"));
        assertTrue(source.contains("public Long getId()"));
    }

    @Test
    void generateRepository_nullTableName_throws() {
        assertThrows(IllegalArgumentException.class,
            () -> EntityCodeGenerator.generateRepository(null, List.of(), "com.example", "com.repo"));
    }

    @Test
    void generateRepository_blankTableName_throws() {
        assertThrows(IllegalArgumentException.class,
            () -> EntityCodeGenerator.generateRepository("  ", List.of(), "com.example", "com.repo"));
    }

    @Test
    void generateRepository_nullColumns_throws() {
        assertThrows(IllegalArgumentException.class,
            () -> EntityCodeGenerator.generateRepository("table", null, "com.example", "com.repo"));
    }

    @Test
    void generateRepository_nullEntityPackage_throws() {
        assertThrows(IllegalArgumentException.class,
            () -> EntityCodeGenerator.generateRepository("table", List.of(), null, "com.repo"));
    }

    @Test
    void generateRepository_blankEntityPackage_throws() {
        assertThrows(IllegalArgumentException.class,
            () -> EntityCodeGenerator.generateRepository("table", List.of(), "  ", "com.repo"));
    }

    @Test
    void generateRepository_blankRepoPackage_throws() {
        assertThrows(IllegalArgumentException.class,
            () -> EntityCodeGenerator.generateRepository("table", List.of(), "com.example", "  "));
    }

    @Test
    void generateRepository_invalidTableName_throws() {
        List<EntityCodeGenerator.ColumnDef> columns =
            List.of(new EntityCodeGenerator.ColumnDef("name", "String", false));
        assertThrows(IllegalArgumentException.class,
            () -> EntityCodeGenerator.generateRepository("table;DROP", columns, "com.example", "com.repo"));
        assertThrows(IllegalArgumentException.class,
            () -> EntityCodeGenerator.generateRepository("table\nRuntime.exec", columns, "com.example", "com.repo"));
    }

    @Test
    void generateRepository_invalidEntityPackage_throws() {
        List<EntityCodeGenerator.ColumnDef> columns =
            List.of(new EntityCodeGenerator.ColumnDef("name", "String", false));
        assertThrows(IllegalArgumentException.class,
            () -> EntityCodeGenerator.generateRepository("table", columns, ".com.example", "com.repo"));
        assertThrows(IllegalArgumentException.class,
            () -> EntityCodeGenerator.generateRepository("table", columns, "com.example.", "com.repo"));
        assertThrows(IllegalArgumentException.class,
            () -> EntityCodeGenerator.generateRepository("table", columns, "com-example", "com.repo"));
    }

    @Test
    void generateRepository_invalidRepoPackage_throws() {
        List<EntityCodeGenerator.ColumnDef> columns =
            List.of(new EntityCodeGenerator.ColumnDef("name", "String", false));
        assertThrows(IllegalArgumentException.class,
            () -> EntityCodeGenerator.generateRepository("table", columns, "com.example", ".com.repo"));
        assertThrows(IllegalArgumentException.class,
            () -> EntityCodeGenerator.generateRepository("table", columns, "com.example", "com-repo"));
    }

    @Test
    void generateEntity_templateWithInvalidTableName_throws() {
        String template = "package ${package};\nclass ${className} {}\n";
        List<EntityCodeGenerator.ColumnDef> columns =
            List.of(new EntityCodeGenerator.ColumnDef("name", "String", false));
        assertThrows(IllegalArgumentException.class,
            () -> EntityCodeGenerator.generateEntity("table;DROP", columns, "com.example", template));
    }

    @Test
    void generateEntity_templateWithInvalidPackage_throws() {
        String template = "package ${package};\nclass ${className} {}\n";
        List<EntityCodeGenerator.ColumnDef> columns =
            List.of(new EntityCodeGenerator.ColumnDef("name", "String", false));
        assertThrows(IllegalArgumentException.class,
            () -> EntityCodeGenerator.generateEntity("table", columns, ".com.example", template));
    }

    @Test
    void generateEntity_templateWithNullTableName_throws() {
        String template = "package ${package};\nclass ${className} {}\n";
        List<EntityCodeGenerator.ColumnDef> columns =
            List.of(new EntityCodeGenerator.ColumnDef("name", "String", false));
        assertThrows(IllegalArgumentException.class,
            () -> EntityCodeGenerator.generateEntity(null, columns, "com.example", template));
    }

    @Test
    void generateEntity_templateWithInvalidPackageChars_throws() {
        String template = "package ${package};\nclass ${className} {}\n";
        List<EntityCodeGenerator.ColumnDef> columns =
            List.of(new EntityCodeGenerator.ColumnDef("name", "String", false));
        assertThrows(IllegalArgumentException.class,
            () -> EntityCodeGenerator.generateEntity("table", columns, "com-example", template));
    }

    @Test
    void columnDef_getters() {
        EntityCodeGenerator.ColumnDef col = new EntityCodeGenerator.ColumnDef("name", "String", true);
        assertEquals("name", col.getName());
        assertEquals("String", col.getJavaType());
        assertTrue(col.isNullable());

        EntityCodeGenerator.ColumnDef col2 = new EntityCodeGenerator.ColumnDef("id", "Long", false);
        assertFalse(col2.isNullable());
    }
}
