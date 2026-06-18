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

    @Test
    @DisplayName("destroy - 应清理静态字段")
    void shouldClearStaticFieldsOnDestroy() throws Exception {
        java.lang.reflect.Field appCtxField = AuditEntityListener.class.getDeclaredField("applicationContext");
        appCtxField.setAccessible(true);
        java.lang.reflect.Field providerField = AuditEntityListener.class.getDeclaredField("userProvider");
        providerField.setAccessible(true);
        java.lang.reflect.Field lookupField = AuditEntityListener.class.getDeclaredField("providerLookupAttempted");
        lookupField.setAccessible(true);

        Object origCtx = appCtxField.get(null);
        Object origProvider = providerField.get(null);
        boolean origLookup = lookupField.getBoolean(null);

        try {
            appCtxField.set(null, org.mockito.Mockito.mock(org.springframework.context.ApplicationContext.class));
            providerField.set(null, org.mockito.Mockito.mock(AuditUserProvider.class));
            lookupField.setBoolean(null, true);

            AuditEntityListener.destroy();

            assertNull(appCtxField.get(null));
            assertNull(providerField.get(null));
            assertFalse(lookupField.getBoolean(null));
        } finally {
            appCtxField.set(null, origCtx);
            providerField.set(null, origProvider);
            lookupField.setBoolean(null, origLookup);
        }
    }

    @Test
    @DisplayName("setAuditZoneId - 应设置时区")
    void shouldSetAuditZoneId() throws Exception {
        java.lang.reflect.Field zoneField = AuditEntityListener.class.getDeclaredField("auditZoneId");
        zoneField.setAccessible(true);
        java.time.ZoneId original = (java.time.ZoneId)zoneField.get(null);

        try {
            AuditEntityListener.setAuditZoneId(java.time.ZoneId.of("UTC"));
            assertEquals(java.time.ZoneId.of("UTC"), zoneField.get(null));

            AuditEntityListener.setAuditZoneId(null);
            assertEquals(java.time.ZoneId.of("UTC"), zoneField.get(null));
        } finally {
            zoneField.set(null, original);
        }
    }

    @jakarta.persistence.Entity
    @jakarta.persistence.EntityListeners(AuditEntityListener.class)
    @jakarta.persistence.Table(name = "audit_test_long")
    static class LongAuditEntity {
        @jakarta.persistence.Id
        @jakarta.persistence.GeneratedValue(strategy = jakarta.persistence.GenerationType.IDENTITY)
        private Long id;

        @CreatedAt
        private Long createdAt;

        @UpdatedAt
        private Long updatedAt;

        public Long getId() {
            return id;
        }

        public Long getCreatedAt() {
            return createdAt;
        }

        public Long getUpdatedAt() {
            return updatedAt;
        }
    }

    @Test
    @DisplayName("prePersist - 应填充 Long 类型的 createdAt 和 updatedAt")
    void shouldFillLongAuditFields() {
        LongAuditEntity entity = new LongAuditEntity();
        listener.prePersist(entity);

        assertNotNull(entity.getCreatedAt());
        assertNotNull(entity.getUpdatedAt());
        assertTrue(entity.getCreatedAt() > 0);
    }

    @jakarta.persistence.Entity
    @jakarta.persistence.EntityListeners(AuditEntityListener.class)
    @jakarta.persistence.Table(name = "audit_test_unsupported")
    static class UnsupportedTypeEntity {
        @jakarta.persistence.Id
        @jakarta.persistence.GeneratedValue(strategy = jakarta.persistence.GenerationType.IDENTITY)
        private Long id;

        @CreatedAt
        private Integer createdAt;

        public Long getId() {
            return id;
        }

        public Integer getCreatedAt() {
            return createdAt;
        }
    }

    @Test
    @DisplayName("resolveAuditFields - 不支持的字段类型应跳过")
    void shouldSkipUnsupportedFieldType() {
        UnsupportedTypeEntity entity = new UnsupportedTypeEntity();
        assertDoesNotThrow(() -> listener.prePersist(entity));
        assertNull(entity.getCreatedAt());
    }

    @Test
    @DisplayName("setFieldValue - Instant 到 Long 类型转换")
    void shouldSetLongFromInstant() throws Exception {
        java.lang.reflect.Method m =
            AuditEntityListener.class.getDeclaredMethod("setFieldValue", Object.class, Field.class, Object.class);
        m.setAccessible(true);

        LongAuditEntity entity = new LongAuditEntity();
        Field f = LongAuditEntity.class.getDeclaredField("createdAt");
        f.setAccessible(true);

        Instant now = Instant.now();
        m.invoke(null, entity, f, now);

        assertEquals(now.toEpochMilli(), entity.getCreatedAt());
    }

    @jakarta.persistence.Entity
    @jakarta.persistence.EntityListeners(AuditEntityListener.class)
    @jakarta.persistence.Table(name = "audit_test_str_long")
    static class StringToLongEntity {
        @jakarta.persistence.Id
        @jakarta.persistence.GeneratedValue(strategy = jakarta.persistence.GenerationType.IDENTITY)
        private Long id;

        @CreatedBy
        private Long userId;

        public Long getId() {
            return id;
        }

        public Long getUserId() {
            return userId;
        }

        public void setUserId(Long userId) {
            this.userId = userId;
        }
    }

    @Test
    @DisplayName("setFieldValue - String 到 Long 类型转换")
    void shouldSetLongFromString() throws Exception {
        java.lang.reflect.Method m =
            AuditEntityListener.class.getDeclaredMethod("setFieldValue", Object.class, Field.class, Object.class);
        m.setAccessible(true);

        StringToLongEntity entity = new StringToLongEntity();
        Field f = StringToLongEntity.class.getDeclaredField("userId");
        f.setAccessible(true);

        m.invoke(null, entity, f, "12345");
        assertEquals(12345L, entity.getUserId());
    }

    @Test
    @DisplayName("setFieldValue - String 到 Long 转换失败应忽略")
    void shouldIgnoreInvalidLongString() throws Exception {
        java.lang.reflect.Method m =
            AuditEntityListener.class.getDeclaredMethod("setFieldValue", Object.class, Field.class, Object.class);
        m.setAccessible(true);

        StringToLongEntity entity = new StringToLongEntity();
        Field f = StringToLongEntity.class.getDeclaredField("userId");
        f.setAccessible(true);

        m.invoke(null, entity, f, "not_a_number");
        assertNull(entity.getUserId());
    }

    @jakarta.persistence.Entity
    @jakarta.persistence.EntityListeners(AuditEntityListener.class)
    @jakarta.persistence.Table(name = "audit_test_str_int")
    static class StringToIntegerEntity {
        @jakarta.persistence.Id
        @jakarta.persistence.GeneratedValue(strategy = jakarta.persistence.GenerationType.IDENTITY)
        private Long id;

        @CreatedBy
        private Integer code;

        public Long getId() {
            return id;
        }

        public Integer getCode() {
            return code;
        }

        public void setCode(Integer code) {
            this.code = code;
        }
    }

    @Test
    @DisplayName("setFieldValue - String 到 Integer 类型转换")
    void shouldSetIntegerFromString() throws Exception {
        java.lang.reflect.Method m =
            AuditEntityListener.class.getDeclaredMethod("setFieldValue", Object.class, Field.class, Object.class);
        m.setAccessible(true);

        StringToIntegerEntity entity = new StringToIntegerEntity();
        Field f = StringToIntegerEntity.class.getDeclaredField("code");
        f.setAccessible(true);

        m.invoke(null, entity, f, "42");
        assertEquals(42, entity.getCode());
    }

    @Test
    @DisplayName("setFieldValue - String 到 Integer 转换失败应忽略")
    void shouldIgnoreInvalidIntegerString() throws Exception {
        java.lang.reflect.Method m =
            AuditEntityListener.class.getDeclaredMethod("setFieldValue", Object.class, Field.class, Object.class);
        m.setAccessible(true);

        StringToIntegerEntity entity = new StringToIntegerEntity();
        Field f = StringToIntegerEntity.class.getDeclaredField("code");
        f.setAccessible(true);

        m.invoke(null, entity, f, "not_a_number");
        assertNull(entity.getCode());
    }

    @Test
    @DisplayName("getUserProvider - 有 ApplicationContext 但无 bean 时应返回 null")
    void shouldReturnNullWhenNoBeanInContext() throws Exception {
        java.lang.reflect.Field appCtxField = AuditEntityListener.class.getDeclaredField("applicationContext");
        appCtxField.setAccessible(true);
        java.lang.reflect.Field lookupField = AuditEntityListener.class.getDeclaredField("providerLookupAttempted");
        lookupField.setAccessible(true);
        Object origCtx = appCtxField.get(null);
        boolean origLookup = lookupField.getBoolean(null);

        try {
            org.springframework.context.ApplicationContext mockCtx =
                org.mockito.Mockito.mock(org.springframework.context.ApplicationContext.class);
            org.mockito.Mockito.when(mockCtx.getBean(AuditUserProvider.class))
                .thenThrow(new org.springframework.beans.factory.NoSuchBeanDefinitionException("no bean"));

            appCtxField.set(null, mockCtx);
            lookupField.setBoolean(null, false);

            java.lang.reflect.Method m = AuditEntityListener.class.getDeclaredMethod("getUserProvider");
            m.setAccessible(true);
            Object result = m.invoke(null);
            assertNull(result);
        } finally {
            appCtxField.set(null, origCtx);
            lookupField.setBoolean(null, origLookup);
        }
    }

    @Test
    @DisplayName("getUserProvider - 有 ApplicationContext 且有 bean 时应返回 provider")
    void shouldReturnProviderWhenBeanExists() throws Exception {
        java.lang.reflect.Field appCtxField = AuditEntityListener.class.getDeclaredField("applicationContext");
        appCtxField.setAccessible(true);
        java.lang.reflect.Field providerField = AuditEntityListener.class.getDeclaredField("userProvider");
        providerField.setAccessible(true);
        java.lang.reflect.Field lookupField = AuditEntityListener.class.getDeclaredField("providerLookupAttempted");
        lookupField.setAccessible(true);
        Object origCtx = appCtxField.get(null);
        Object origProvider = providerField.get(null);
        boolean origLookup = lookupField.getBoolean(null);

        try {
            AuditUserProvider mockProvider = () -> "testUser";
            org.springframework.context.ApplicationContext mockCtx =
                org.mockito.Mockito.mock(org.springframework.context.ApplicationContext.class);
            org.mockito.Mockito.when(mockCtx.getBean(AuditUserProvider.class)).thenReturn(mockProvider);

            appCtxField.set(null, mockCtx);
            providerField.set(null, null);
            lookupField.setBoolean(null, false);

            java.lang.reflect.Method m = AuditEntityListener.class.getDeclaredMethod("getUserProvider");
            m.setAccessible(true);
            Object result = m.invoke(null);
            assertSame(mockProvider, result);
        } finally {
            appCtxField.set(null, origCtx);
            providerField.set(null, origProvider);
            lookupField.setBoolean(null, origLookup);
        }
    }

    @Test
    @DisplayName("prePersist - 有 provider 时应填充 createdBy/updatedBy")
    void shouldFillCreatedByWithProvider() throws Exception {
        java.lang.reflect.Field appCtxField = AuditEntityListener.class.getDeclaredField("applicationContext");
        appCtxField.setAccessible(true);
        java.lang.reflect.Field providerField = AuditEntityListener.class.getDeclaredField("userProvider");
        providerField.setAccessible(true);
        java.lang.reflect.Field lookupField = AuditEntityListener.class.getDeclaredField("providerLookupAttempted");
        lookupField.setAccessible(true);
        Object origCtx = appCtxField.get(null);
        Object origProvider = providerField.get(null);
        boolean origLookup = lookupField.getBoolean(null);

        try {
            AuditUserProvider mockProvider = () -> "auditUser";
            appCtxField.set(null, org.mockito.Mockito.mock(org.springframework.context.ApplicationContext.class));
            providerField.set(null, mockProvider);
            lookupField.setBoolean(null, true);

            UserAuditEntity entity = new UserAuditEntity();
            listener.prePersist(entity);

            assertEquals("auditUser", entity.getCreatedBy());
            assertEquals("auditUser", entity.getUpdatedBy());
        } finally {
            appCtxField.set(null, origCtx);
            providerField.set(null, origProvider);
            lookupField.setBoolean(null, origLookup);
        }
    }

    @Test
    @DisplayName("preUpdate - 有 provider 时应填充 updatedBy")
    void shouldFillUpdatedByWithProvider() throws Exception {
        java.lang.reflect.Field appCtxField = AuditEntityListener.class.getDeclaredField("applicationContext");
        appCtxField.setAccessible(true);
        java.lang.reflect.Field providerField = AuditEntityListener.class.getDeclaredField("userProvider");
        providerField.setAccessible(true);
        java.lang.reflect.Field lookupField = AuditEntityListener.class.getDeclaredField("providerLookupAttempted");
        lookupField.setAccessible(true);
        Object origCtx = appCtxField.get(null);
        Object origProvider = providerField.get(null);
        boolean origLookup = lookupField.getBoolean(null);

        try {
            AuditUserProvider mockProvider = () -> "auditUser";
            appCtxField.set(null, org.mockito.Mockito.mock(org.springframework.context.ApplicationContext.class));
            providerField.set(null, mockProvider);
            lookupField.setBoolean(null, true);

            UserAuditEntity entity = new UserAuditEntity();
            listener.preUpdate(entity);

            assertEquals("auditUser", entity.getUpdatedBy());
        } finally {
            appCtxField.set(null, origCtx);
            providerField.set(null, origProvider);
            lookupField.setBoolean(null, origLookup);
        }
    }
}
