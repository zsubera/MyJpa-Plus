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
}
