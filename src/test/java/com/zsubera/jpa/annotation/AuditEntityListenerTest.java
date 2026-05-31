package com.zsubera.jpa.annotation;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Date;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link AuditEntityListener} 的单元测试。
 */
class AuditEntityListenerTest {

    private AuditEntityListener listener;

    @BeforeEach
    void setUp() {
        listener = new AuditEntityListener();
    }

    @jakarta.persistence.Entity
    @jakarta.persistence.EntityListeners(AuditEntityListener.class)
    @jakarta.persistence.Table(name = "audit_test_instant")
    static class InstantAuditEntity {
        @jakarta.persistence.Id
        @jakarta.persistence.GeneratedValue(strategy = jakarta.persistence.GenerationType.IDENTITY)
        private Long id;

        @CreatedAt
        private Instant createdAt;

        @UpdatedAt
        private Instant updatedAt;

        public Long getId() {
            return id;
        }

        public Instant getCreatedAt() {
            return createdAt;
        }

        public Instant getUpdatedAt() {
            return updatedAt;
        }
    }

    @jakarta.persistence.Entity
    @jakarta.persistence.EntityListeners(AuditEntityListener.class)
    @jakarta.persistence.Table(name = "audit_test_localdatetime")
    static class LocalDateTimeAuditEntity {
        @jakarta.persistence.Id
        @jakarta.persistence.GeneratedValue(strategy = jakarta.persistence.GenerationType.IDENTITY)
        private Long id;

        @CreatedAt
        private LocalDateTime createdAt;

        @UpdatedAt
        private LocalDateTime updatedAt;

        public Long getId() {
            return id;
        }

        public LocalDateTime getCreatedAt() {
            return createdAt;
        }

        public LocalDateTime getUpdatedAt() {
            return updatedAt;
        }
    }

    @jakarta.persistence.Entity
    @jakarta.persistence.EntityListeners(AuditEntityListener.class)
    @jakarta.persistence.Table(name = "audit_test_date")
    static class DateAuditEntity {
        @jakarta.persistence.Id
        @jakarta.persistence.GeneratedValue(strategy = jakarta.persistence.GenerationType.IDENTITY)
        private Long id;

        @CreatedAt
        private Date createdAt;

        @UpdatedAt
        private Date updatedAt;

        public Long getId() {
            return id;
        }

        public Date getCreatedAt() {
            return createdAt;
        }

        public Date getUpdatedAt() {
            return updatedAt;
        }
    }

    @jakarta.persistence.Entity
    @jakarta.persistence.EntityListeners(AuditEntityListener.class)
    @jakarta.persistence.Table(name = "audit_test_user")
    static class UserAuditEntity {
        @jakarta.persistence.Id
        @jakarta.persistence.GeneratedValue(strategy = jakarta.persistence.GenerationType.IDENTITY)
        private Long id;

        @CreatedAt
        private Instant createdAt;

        @UpdatedAt
        private Instant updatedAt;

        @CreatedBy
        private String createdBy;

        @UpdatedBy
        private String updatedBy;

        public Long getId() {
            return id;
        }

        public Instant getCreatedAt() {
            return createdAt;
        }

        public Instant getUpdatedAt() {
            return updatedAt;
        }

        public String getCreatedBy() {
            return createdBy;
        }

        public String getUpdatedBy() {
            return updatedBy;
        }
    }

    @jakarta.persistence.Entity
    @jakarta.persistence.EntityListeners(AuditEntityListener.class)
    @jakarta.persistence.Table(name = "audit_test_no_fields")
    static class NoAuditFieldsEntity {
        @jakarta.persistence.Id
        @jakarta.persistence.GeneratedValue(strategy = jakarta.persistence.GenerationType.IDENTITY)
        private Long id;

        private String name;

        public Long getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    @Test
    @DisplayName("prePersist - 应填充 Instant 类型的 createdAt 和 updatedAt")
    void shouldFillInstantAuditFields() {
        InstantAuditEntity entity = new InstantAuditEntity();
        assertNull(entity.getCreatedAt());
        assertNull(entity.getUpdatedAt());

        listener.prePersist(entity);

        assertNotNull(entity.getCreatedAt());
        assertNotNull(entity.getUpdatedAt());
        assertTrue(entity.getCreatedAt().isAfter(Instant.now().minusSeconds(5)));
        assertTrue(entity.getUpdatedAt().isAfter(Instant.now().minusSeconds(5)));
    }

    @Test
    @DisplayName("prePersist - 应填充 LocalDateTime 类型的 createdAt 和 updatedAt")
    void shouldFillLocalDateTimeAuditFields() {
        LocalDateTimeAuditEntity entity = new LocalDateTimeAuditEntity();
        assertNull(entity.getCreatedAt());
        assertNull(entity.getUpdatedAt());

        listener.prePersist(entity);

        assertNotNull(entity.getCreatedAt());
        assertNotNull(entity.getUpdatedAt());
        assertTrue(entity.getCreatedAt().isAfter(LocalDateTime.now().minusSeconds(5)));
        assertTrue(entity.getUpdatedAt().isAfter(LocalDateTime.now().minusSeconds(5)));
    }

    @Test
    @DisplayName("prePersist - 应填充 Date 类型的 createdAt 和 updatedAt")
    void shouldFillDateAuditFields() {
        DateAuditEntity entity = new DateAuditEntity();
        assertNull(entity.getCreatedAt());
        assertNull(entity.getUpdatedAt());

        listener.prePersist(entity);

        assertNotNull(entity.getCreatedAt());
        assertNotNull(entity.getUpdatedAt());
        Date now = new Date();
        assertTrue(entity.getCreatedAt().after(new Date(now.getTime() - 5000)));
        assertTrue(entity.getUpdatedAt().after(new Date(now.getTime() - 5000)));
    }

    @Test
    @DisplayName("preUpdate - 应仅填充 updatedAt，不修改 createdAt")
    void shouldOnlyFillUpdatedAtOnUpdate() {
        InstantAuditEntity entity = new InstantAuditEntity();
        listener.prePersist(entity);

        Instant originalCreatedAt = entity.getCreatedAt();
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        listener.preUpdate(entity);

        assertEquals(originalCreatedAt, entity.getCreatedAt());
        assertNotNull(entity.getUpdatedAt());
    }

    @Test
    @DisplayName("prePersist - 无审计字段的实体不应抛出异常")
    void shouldNotThrowForEntityWithoutAuditFields() {
        NoAuditFieldsEntity entity = new NoAuditFieldsEntity();
        entity.setName("test");

        assertDoesNotThrow(() -> listener.prePersist(entity));
        assertEquals("test", entity.getName());
    }

    @Test
    @DisplayName("preUpdate - 无审计字段的实体不应抛出异常")
    void shouldNotThrowForEntityWithoutAuditFieldsOnUpdate() {
        NoAuditFieldsEntity entity = new NoAuditFieldsEntity();
        entity.setName("test");

        assertDoesNotThrow(() -> listener.preUpdate(entity));
        assertEquals("test", entity.getName());
    }

    @Test
    @DisplayName("resolveAuditFields - 应缓存解析结果")
    void shouldCacheAuditFields() throws Exception {
        java.lang.reflect.Method resolveMethod =
            AuditEntityListener.class.getDeclaredMethod("resolveAuditFields", Class.class);
        resolveMethod.setAccessible(true);

        Object fields1 = resolveMethod.invoke(null, InstantAuditEntity.class);
        Object fields2 = resolveMethod.invoke(null, InstantAuditEntity.class);

        assertSame(fields1, fields2, "应返回缓存的同一 AuditFields 实例");
    }

    @Test
    @DisplayName("setFieldValue - 应正确设置 Instant 值")
    void shouldSetInstantFieldValue() throws Exception {
        java.lang.reflect.Method setFieldValueMethod =
            AuditEntityListener.class.getDeclaredMethod("setFieldValue", Object.class, Field.class, Object.class);
        setFieldValueMethod.setAccessible(true);

        InstantAuditEntity entity = new InstantAuditEntity();
        Field createdAtField = InstantAuditEntity.class.getDeclaredField("createdAt");
        createdAtField.setAccessible(true);

        Instant now = Instant.now();
        setFieldValueMethod.invoke(null, entity, createdAtField, now);

        assertEquals(now, entity.getCreatedAt());
    }

    @Test
    @DisplayName("setFieldValue - 应正确设置 String 值")
    void shouldSetStringFieldValue() throws Exception {
        java.lang.reflect.Method setFieldValueMethod =
            AuditEntityListener.class.getDeclaredMethod("setFieldValue", Object.class, Field.class, Object.class);
        setFieldValueMethod.setAccessible(true);

        UserAuditEntity entity = new UserAuditEntity();
        Field createdByField = UserAuditEntity.class.getDeclaredField("createdBy");
        createdByField.setAccessible(true);

        setFieldValueMethod.invoke(null, entity, createdByField, "testUser");

        assertEquals("testUser", entity.getCreatedBy());
    }

    @Test
    @DisplayName("getUserProvider - 无 ApplicationContext 时应返回 null")
    void shouldReturnNullWhenNoApplicationContext() throws Exception {
        java.lang.reflect.Field appCtxField = AuditEntityListener.class.getDeclaredField("applicationContext");
        appCtxField.setAccessible(true);
        Object originalCtx = appCtxField.get(null);

        try {
            appCtxField.set(null, null);

            java.lang.reflect.Method getUserProviderMethod =
                AuditEntityListener.class.getDeclaredMethod("getUserProvider");
            getUserProviderMethod.setAccessible(true);

            Object result = getUserProviderMethod.invoke(null);
            assertNull(result);
        } finally {
            appCtxField.set(null, originalCtx);
        }
    }

    @Test
    @DisplayName("setApplicationContext - 应能设置 ApplicationContext")
    void shouldSetApplicationContext() throws Exception {
        java.lang.reflect.Field appCtxField = AuditEntityListener.class.getDeclaredField("applicationContext");
        appCtxField.setAccessible(true);
        Object originalCtx = appCtxField.get(null);

        try {
            listener.setApplicationContext(null);
            assertNull(appCtxField.get(null));
        } finally {
            appCtxField.set(null, originalCtx);
        }
    }
}
