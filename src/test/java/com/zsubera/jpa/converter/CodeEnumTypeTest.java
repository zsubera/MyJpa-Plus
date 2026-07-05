package com.zsubera.jpa.converter;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Types;
import org.hibernate.usertype.DynamicParameterizedType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CodeEnumTypeTest {

    // ===== 测试枚举 =====

    enum StatusEnum {
        ACTIVE(0, "正常"), DELETED(1, "已删除");

        @CodeEnumValue
        private final int code;
        private final String desc;

        StatusEnum(int code, String desc) {
            this.code = code;
            this.desc = desc;
        }

        public int getCode() {
            return code;
        }

        public String getDesc() {
            return desc;
        }
    }

    enum GenderEnum {
        MALE("M", "男"), FEMALE("F", "女"), UNKNOWN("U", "未知");

        @CodeEnumValue
        private final String code;
        private final String desc;

        GenderEnum(String code, String desc) {
            this.code = code;
            this.desc = desc;
        }

        public String getCode() {
            return code;
        }

        public String getDesc() {
            return desc;
        }
    }

    // ===== 没有 @CodeEnumValue 的枚举 =====

    enum NoAnnotationEnum {
        A, B
    }

    // ===== 工具方法测试 =====

    @Test
    @DisplayName("应能解析 @CodeEnumValue 字段")
    void shouldResolveCodeEnumValueField() {
        java.lang.reflect.Field field = CodeEnumType.resolveCodeField(StatusEnum.class);
        assertNotNull(field);
        assertEquals("code", field.getName());
    }

    @Test
    @DisplayName("应能检查是否有 @CodeEnumValue 字段")
    void shouldCheckCodeEnumValuePresence() {
        assertTrue(CodeEnumType.hasCodeEnumValue(StatusEnum.class));
        assertTrue(CodeEnumType.hasCodeEnumValue(GenderEnum.class));
    }

    @Test
    @DisplayName("没有 @CodeEnumValue 的枚举应返回 false")
    void shouldReturnFalseForEnumWithoutAnnotation() {
        assertFalse(CodeEnumType.hasCodeEnumValue(NoAnnotationEnum.class));
    }

    @Test
    @DisplayName("没有 @CodeEnumValue 的枚举 resolveCodeField 应返回 null")
    void shouldReturnNullForEnumWithoutAnnotation() {
        java.lang.reflect.Field field = CodeEnumType.resolveCodeField(NoAnnotationEnum.class);
        assertNull(field);
    }

    // ===== code 值验证测试 =====

    @Test
    @DisplayName("int code 枚举应能获取正确的值")
    void shouldGetIntCodeValue() throws Exception {
        java.lang.reflect.Field codeField = CodeEnumType.resolveCodeField(StatusEnum.class);
        assertNotNull(codeField);
        assertEquals(0, codeField.getInt(StatusEnum.ACTIVE));
        assertEquals(1, codeField.getInt(StatusEnum.DELETED));
    }

    @Test
    @DisplayName("String code 枚举应能获取正确的值")
    void shouldGetStringCodeValue() throws Exception {
        java.lang.reflect.Field codeField = CodeEnumType.resolveCodeField(GenderEnum.class);
        assertNotNull(codeField);
        assertEquals("M", codeField.get(GenderEnum.MALE));
        assertEquals("F", codeField.get(GenderEnum.FEMALE));
        assertEquals("U", codeField.get(GenderEnum.UNKNOWN));
    }

    @Test
    @DisplayName("枚举应能遍历所有常量")
    void shouldIterateEnumConstants() {
        StatusEnum[] values = StatusEnum.values();
        assertEquals(2, values.length);
        assertEquals(StatusEnum.ACTIVE, values[0]);
        assertEquals(StatusEnum.DELETED, values[1]);
    }

    @Test
    @DisplayName("不同枚举类型应互不影响")
    void shouldNotInterfereWithDifferentEnumTypes() {
        assertEquals(0, StatusEnum.ACTIVE.getCode());
        assertEquals("M", GenderEnum.MALE.getCode());
    }

    @Test
    @DisplayName("code 与 ordinal 相同时应能正确工作")
    void shouldWorkWhenCodeEqualsOrdinal() {
        // StatusEnum: ACTIVE(0), DELETED(1) - code 与 ordinal 相同
        assertEquals(StatusEnum.ACTIVE.ordinal(), StatusEnum.ACTIVE.getCode());
        assertEquals(StatusEnum.DELETED.ordinal(), StatusEnum.DELETED.getCode());
    }

    // ===== CodeEnumType 实例方法测试 =====

    @Test
    @DisplayName("equals 方法应正确比较相同对象")
    void shouldCompareEqualObjects() {
        CodeEnumType type = new CodeEnumType();
        assertTrue(type.equals(StatusEnum.ACTIVE, StatusEnum.ACTIVE));
        assertFalse(type.equals(StatusEnum.ACTIVE, StatusEnum.DELETED));
        assertTrue(type.equals(null, null));
        assertFalse(type.equals(StatusEnum.ACTIVE, null));
        assertFalse(type.equals(null, StatusEnum.ACTIVE));
    }

    @Test
    @DisplayName("hashCode 方法应正确计算哈希值")
    void shouldComputeHashCode() {
        CodeEnumType type = new CodeEnumType();
        assertEquals(java.util.Objects.hashCode(StatusEnum.ACTIVE), type.hashCode(StatusEnum.ACTIVE));
        assertEquals(0, type.hashCode(null));
    }

    @Test
    @DisplayName("isMutable 应返回 false")
    void shouldReturnNotMutable() {
        CodeEnumType type = new CodeEnumType();
        assertFalse(type.isMutable());
    }

    @Test
    @DisplayName("deepCopy 应返回同一对象引用")
    void shouldDeepCopyReturnSameReference() {
        CodeEnumType type = new CodeEnumType();
        Object original = StatusEnum.ACTIVE;
        Object copied = type.deepCopy(original);
        assertSame(original, copied);
    }

    @Test
    @DisplayName("deepCopy null 应返回 null")
    void shouldDeepCopyNullReturnNull() {
        CodeEnumType type = new CodeEnumType();
        assertNull(type.deepCopy(null));
    }

    @Test
    @DisplayName("disassemble 应返回序列化表示")
    void shouldDisassembleEnumConstant() {
        CodeEnumType type = new CodeEnumType();
        try {
            java.lang.reflect.Field enumClassField = CodeEnumType.class.getDeclaredField("enumClass");
            enumClassField.setAccessible(true);
            enumClassField.set(type, StatusEnum.class);

            java.lang.reflect.Field codeFieldField = CodeEnumType.class.getDeclaredField("codeField");
            codeFieldField.setAccessible(true);
            codeFieldField.set(type, CodeEnumType.resolveCodeField(StatusEnum.class));

            java.io.Serializable result = type.disassemble(StatusEnum.ACTIVE);
            assertNotNull(result);
            assertEquals("0", result.toString());
        } catch (Exception e) {
            fail("反射设置字段失败: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("disassemble null 应返回 null")
    void shouldDisassembleNullReturnNull() {
        CodeEnumType type = new CodeEnumType();
        assertNull(type.disassemble(null));
    }

    @Test
    @DisplayName("assemble 应正确还原枚举常量")
    void shouldAssembleEnumConstant() {
        CodeEnumType type = new CodeEnumType();
        try {
            java.lang.reflect.Field enumClassField = CodeEnumType.class.getDeclaredField("enumClass");
            enumClassField.setAccessible(true);
            enumClassField.set(type, StatusEnum.class);

            java.lang.reflect.Field codeFieldField = CodeEnumType.class.getDeclaredField("codeField");
            codeFieldField.setAccessible(true);
            codeFieldField.set(type, CodeEnumType.resolveCodeField(StatusEnum.class));

            Object result = type.assemble("0", null);
            assertEquals(StatusEnum.ACTIVE, result);
        } catch (Exception e) {
            fail("反射设置字段失败: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("assemble null 应返回 null")
    void shouldAssembleNullReturnNull() {
        CodeEnumType type = new CodeEnumType();
        assertNull(type.assemble(null, null));
    }

    @Test
    @DisplayName("assemble 无效 code 应抛出 HibernateException")
    void shouldThrowForInvalidCodeOnAssemble() {
        CodeEnumType type = new CodeEnumType();
        try {
            java.lang.reflect.Field enumClassField = CodeEnumType.class.getDeclaredField("enumClass");
            enumClassField.setAccessible(true);
            enumClassField.set(type, StatusEnum.class);

            java.lang.reflect.Field codeFieldField = CodeEnumType.class.getDeclaredField("codeField");
            codeFieldField.setAccessible(true);
            codeFieldField.set(type, CodeEnumType.resolveCodeField(StatusEnum.class));

            assertThrows(org.hibernate.HibernateException.class, () -> type.assemble("999", null));
        } catch (Exception e) {
            fail("反射设置字段失败: " + e.getMessage());
        }
    }

    // ===== getSqlType 测试 =====

    @Test
    @DisplayName("getSqlType 对于 int code 应返回 CHAR")
    void shouldReturnCharSqlTypeForIntCode() {
        CodeEnumType type = new CodeEnumType();
        try {
            java.lang.reflect.Field enumClassField = CodeEnumType.class.getDeclaredField("enumClass");
            enumClassField.setAccessible(true);
            enumClassField.set(type, StatusEnum.class);

            java.lang.reflect.Field codeFieldField = CodeEnumType.class.getDeclaredField("codeField");
            codeFieldField.setAccessible(true);
            codeFieldField.set(type, CodeEnumType.resolveCodeField(StatusEnum.class));

            java.lang.reflect.Field sqlTypeField = CodeEnumType.class.getDeclaredField("sqlType");
            sqlTypeField.setAccessible(true);
            sqlTypeField.set(type, Types.CHAR);

            assertEquals(Types.CHAR, type.getSqlType());
        } catch (Exception e) {
            fail("反射设置字段失败: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("getSqlType 对于 String code 应返回 VARCHAR")
    void shouldReturnVarcharSqlTypeForStringCode() {
        CodeEnumType type = new CodeEnumType();
        try {
            java.lang.reflect.Field enumClassField = CodeEnumType.class.getDeclaredField("enumClass");
            enumClassField.setAccessible(true);
            enumClassField.set(type, GenderEnum.class);

            java.lang.reflect.Field codeFieldField = CodeEnumType.class.getDeclaredField("codeField");
            codeFieldField.setAccessible(true);
            codeFieldField.set(type, CodeEnumType.resolveCodeField(GenderEnum.class));

            java.lang.reflect.Field sqlTypeField = CodeEnumType.class.getDeclaredField("sqlType");
            sqlTypeField.setAccessible(true);
            sqlTypeField.set(type, Types.VARCHAR);

            assertEquals(Types.VARCHAR, type.getSqlType());
        } catch (Exception e) {
            fail("反射设置字段失败: " + e.getMessage());
        }
    }

    // ===== 缓存测试 =====

    @Test
    @DisplayName("resolveCodeField 应缓存结果")
    void shouldCacheCodeFieldResolution() {
        java.lang.reflect.Field field1 = CodeEnumType.resolveCodeField(StatusEnum.class);
        java.lang.reflect.Field field2 = CodeEnumType.resolveCodeField(StatusEnum.class);
        assertSame(field1, field2, "应返回缓存的同一 Field 实例");
    }

    @Test
    @DisplayName("resolveCodeField 对不同枚举应返回不同字段")
    void shouldReturnDifferentFieldsForDifferentEnums() {
        java.lang.reflect.Field statusField = CodeEnumType.resolveCodeField(StatusEnum.class);
        java.lang.reflect.Field genderField = CodeEnumType.resolveCodeField(GenderEnum.class);
        assertNotEquals(statusField, genderField);
    }

    // ===== setParameterValues 测试 =====

    @Test
    @DisplayName("setParameterValues 应正确初始化枚举类")
    void shouldInitializeEnumClass() {
        CodeEnumType type = new CodeEnumType();
        java.util.Properties props = new java.util.Properties();
        props.setProperty(DynamicParameterizedType.RETURNED_CLASS, StatusEnum.class.getName());
        type.setParameterValues(props);
        assertEquals(StatusEnum.class, type.returnedClass());
    }

    @Test
    @DisplayName("setParameterValues 无 RETURNED_CLASS 应抛出异常")
    void shouldThrowWhenNoReturnedClass() {
        CodeEnumType type = new CodeEnumType();
        java.util.Properties props = new java.util.Properties();
        assertThrows(org.hibernate.HibernateException.class, () -> type.setParameterValues(props));
    }

    @Test
    @DisplayName("setParameterValues 非枚举类型应抛出异常")
    void shouldThrowForNonEnumType() {
        CodeEnumType type = new CodeEnumType();
        java.util.Properties props = new java.util.Properties();
        props.setProperty(DynamicParameterizedType.RETURNED_CLASS, String.class.getName());
        assertThrows(org.hibernate.HibernateException.class, () -> type.setParameterValues(props));
    }

    @Test
    @DisplayName("setParameterValues 不存在的类应抛出异常")
    void shouldThrowForNonExistentClass() {
        CodeEnumType type = new CodeEnumType();
        java.util.Properties props = new java.util.Properties();
        props.setProperty(DynamicParameterizedType.RETURNED_CLASS, "com.nonexistent.FakeEnum");
        assertThrows(org.hibernate.HibernateException.class, () -> type.setParameterValues(props));
    }

    @Test
    @DisplayName("setParameterValues String code 应设置正确的 SQL 类型")
    void shouldSetVarcharForStringCode() {
        CodeEnumType type = new CodeEnumType();
        java.util.Properties props = new java.util.Properties();
        props.setProperty(DynamicParameterizedType.RETURNED_CLASS, GenderEnum.class.getName());
        type.setParameterValues(props);
        assertEquals(java.sql.Types.VARCHAR, type.getSqlType());
    }

    @Test
    @DisplayName("returnedClass 应返回正确的枚举类")
    void shouldReturnCorrectClass() {
        CodeEnumType type = new CodeEnumType();
        java.util.Properties props = new java.util.Properties();
        props.setProperty(DynamicParameterizedType.RETURNED_CLASS, StatusEnum.class.getName());
        type.setParameterValues(props);
        assertEquals(StatusEnum.class, type.returnedClass());
    }

    // ===== nullSafeSet 测试 =====

    @Test
    @DisplayName("nullSafeSet null 值应设置 NULL")
    void shouldSetNull() throws Exception {
        CodeEnumType type = initType(StatusEnum.class);
        java.sql.PreparedStatement ps = org.mockito.Mockito.mock(java.sql.PreparedStatement.class);
        type.nullSafeSet(ps, null, 1, null);
        org.mockito.Mockito.verify(ps).setNull(1, type.getSqlType());
    }

    @Test
    @DisplayName("nullSafeSet enum 值应设置 code")
    void shouldSetEnumValue() throws Exception {
        CodeEnumType type = initType(StatusEnum.class);
        java.sql.PreparedStatement ps = org.mockito.Mockito.mock(java.sql.PreparedStatement.class);
        type.nullSafeSet(ps, StatusEnum.ACTIVE, 1, null);
        org.mockito.Mockito.verify(ps).setInt(1, 0);
    }

    @Test
    @DisplayName("nullSafeSet String code enum 应设置 String 值")
    void shouldSetStringCodeValue() throws Exception {
        CodeEnumType type = initType(GenderEnum.class);
        java.sql.PreparedStatement ps = org.mockito.Mockito.mock(java.sql.PreparedStatement.class);
        type.nullSafeSet(ps, GenderEnum.MALE, 1, null);
        org.mockito.Mockito.verify(ps).setString(1, "M");
    }

    // ===== nullSafeGet 测试 =====

    @Test
    @DisplayName("nullSafeGet 返回 null 时应返回 null")
    void shouldReturnNullForNullValue() throws Exception {
        CodeEnumType type = initType(StatusEnum.class);
        java.sql.ResultSet rs = org.mockito.Mockito.mock(java.sql.ResultSet.class);
        org.mockito.Mockito.when(rs.getInt(1)).thenReturn(0);
        org.mockito.Mockito.when(rs.wasNull()).thenReturn(true);
        Object result = type.nullSafeGet(rs, 1, null, null);
        assertNull(result);
    }

    @Test
    @DisplayName("nullSafeGet 应根据 code 查找枚举")
    void shouldGetEnumByCode() throws Exception {
        CodeEnumType type = initType(StatusEnum.class);
        java.sql.ResultSet rs = org.mockito.Mockito.mock(java.sql.ResultSet.class);
        org.mockito.Mockito.when(rs.getInt(1)).thenReturn(0);
        org.mockito.Mockito.when(rs.wasNull()).thenReturn(false);
        Object result = type.nullSafeGet(rs, 1, null, null);
        assertEquals(StatusEnum.ACTIVE, result);
    }

    @Test
    @DisplayName("nullSafeGet 无效 code 应抛出异常")
    void shouldThrowForInvalidCode() throws Exception {
        CodeEnumType type = initType(StatusEnum.class);
        java.sql.ResultSet rs = org.mockito.Mockito.mock(java.sql.ResultSet.class);
        org.mockito.Mockito.when(rs.getInt(1)).thenReturn(999);
        org.mockito.Mockito.when(rs.wasNull()).thenReturn(false);
        assertThrows(org.hibernate.HibernateException.class, () -> type.nullSafeGet(rs, 1, null, null));
    }

    @Test
    @DisplayName("nullSafeGet String code enum 应正确查找")
    void shouldGetStringCodeEnum() throws Exception {
        CodeEnumType type = initType(GenderEnum.class);
        java.sql.ResultSet rs = org.mockito.Mockito.mock(java.sql.ResultSet.class);
        org.mockito.Mockito.when(rs.getString(1)).thenReturn("F");
        Object result = type.nullSafeGet(rs, 1, null, null);
        assertEquals(GenderEnum.FEMALE, result);
    }

    // ===== Long code 字段测试 =====

    enum LongCodeEnum {
        BIG(100L), SMALL(200L);

        @CodeEnumValue
        private final Long code;

        LongCodeEnum(Long code) {
            this.code = code;
        }
    }

    @Test
    @DisplayName("Long code 字段应返回 BIGINT SQL 类型")
    void shouldReturnBigintForLongCode() throws Exception {
        CodeEnumType type = initType(LongCodeEnum.class);
        assertEquals(java.sql.Types.BIGINT, type.getSqlType());
    }

    @Test
    @DisplayName("Long code nullSafeSet 应设置 long 值")
    void shouldSetLongValue() throws Exception {
        CodeEnumType type = initType(LongCodeEnum.class);
        java.sql.PreparedStatement ps = org.mockito.Mockito.mock(java.sql.PreparedStatement.class);
        type.nullSafeSet(ps, LongCodeEnum.BIG, 1, null);
        org.mockito.Mockito.verify(ps).setLong(1, 100L);
    }

    @Test
    @DisplayName("Long code nullSafeGet 应从 BIGINT 读取")
    void shouldGetLongCode() throws Exception {
        CodeEnumType type = initType(LongCodeEnum.class);
        java.sql.ResultSet rs = org.mockito.Mockito.mock(java.sql.ResultSet.class);
        org.mockito.Mockito.when(rs.getLong(1)).thenReturn(200L);
        Object result = type.nullSafeGet(rs, 1, null, null);
        assertEquals(LongCodeEnum.SMALL, result);
    }

    @Test
    @DisplayName("Long code nullSafeGet null 值应返回 null")
    void shouldReturnNullForNullLong() throws Exception {
        CodeEnumType type = initType(LongCodeEnum.class);
        java.sql.ResultSet rs = org.mockito.Mockito.mock(java.sql.ResultSet.class);
        org.mockito.Mockito.when(rs.getLong(1)).thenReturn(0L);
        org.mockito.Mockito.when(rs.wasNull()).thenReturn(true);
        Object result = type.nullSafeGet(rs, 1, null, null);
        assertNull(result);
    }

    // ===== 辅助方法 =====

    private CodeEnumType initType(Class<?> enumClass) throws Exception {
        CodeEnumType type = new CodeEnumType();
        java.util.Properties props = new java.util.Properties();
        props.setProperty(DynamicParameterizedType.RETURNED_CLASS, enumClass.getName());
        type.setParameterValues(props);
        return type;
    }
}
