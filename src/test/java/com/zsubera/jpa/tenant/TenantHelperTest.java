package com.zsubera.jpa.tenant;

import static org.junit.jupiter.api.Assertions.*;

import com.zsubera.jpa.annotation.TenantId;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link TenantHelper} field scanning and Specification building.
 */
class TenantHelperTest {

    @Entity
    static class TenantAwareEntity {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @TenantId
        private String tenantId;

        private String name;
    }

    @Entity
    static class NonTenantEntity {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        private String name;
    }

    @Test
    void findTenantIdField_returnsFieldNameForAnnotatedEntity() {
        String fieldName = TenantHelper.findTenantIdField(TenantAwareEntity.class);
        assertEquals("tenantId", fieldName);
    }

    @Test
    void findTenantIdField_returnsNullForNonAnnotatedEntity() {
        String fieldName = TenantHelper.findTenantIdField(NonTenantEntity.class);
        assertNull(fieldName);
    }

    @Test
    void belongsToTenant_returnsConjunctionForNonTenantEntity() {
        var spec = TenantHelper.belongsToTenant(NonTenantEntity.class, "tenant-1");
        assertNotNull(spec);
    }

    @Test
    void belongsToTenant_returnsFilterForTenantEntity() {
        var spec = TenantHelper.belongsToTenant(TenantAwareEntity.class, "tenant-1");
        assertNotNull(spec);
    }
}
