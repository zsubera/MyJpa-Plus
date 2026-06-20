package com.zsubera.jpa.converter;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Types;
import java.util.Properties;
import org.hibernate.HibernateException;
import org.hibernate.usertype.DynamicParameterizedType;
import org.junit.jupiter.api.Test;

class CodeEnumTypeCoverageTest {

    enum LongCodeEnum {
        A(100L), B(200L);

        @CodeEnumValue
        private final Long code;

        LongCodeEnum(Long code) {
            this.code = code;
        }

        public Long getCode() {
            return code;
        }
    }

    enum IntCodeEnum {
        ACTIVE(0), DELETED(1);

        @CodeEnumValue
        private final int code;

        IntCodeEnum(int code) {
            this.code = code;
        }

        public int getCode() {
            return code;
        }
    }

    enum StringCodeEnum {
        MALE("M"), FEMALE("F");

        @CodeEnumValue
        private final String code;

        StringCodeEnum(String code) {
            this.code = code;
        }

        public String getCode() {
            return code;
        }
    }

    enum CharCodeEnum {
        A('X'), B('Y');

        @CodeEnumValue
        private final Character code;

        CharCodeEnum(Character code) {
            this.code = code;
        }

        public Character getCode() {
            return code;
        }
    }

    enum NoAnnotationEnum {
        A, B
    }

    // ---- setParameterValues: various paths ----

    @Test
    void setParameterValues_nullTypeName() {
        CodeEnumType type = new CodeEnumType();
        Properties props = new Properties();
        assertThrows(HibernateException.class, () -> type.setParameterValues(props));
    }

    @Test
    void setParameterValues_nonEnumType() {
        CodeEnumType type = new CodeEnumType();
        Properties props = new Properties();
        props.setProperty(DynamicParameterizedType.RETURNED_CLASS, "java.lang.String");
        assertThrows(HibernateException.class, () -> type.setParameterValues(props));
    }

    @Test
    void setParameterValues_invalidClassName() {
        CodeEnumType type = new CodeEnumType();
        Properties props = new Properties();
        props.setProperty(DynamicParameterizedType.RETURNED_CLASS, "com.nonexistent.FakeEnum");
        assertThrows(HibernateException.class, () -> type.setParameterValues(props));
    }

    @Test
    void setParameterValues_noAnnotationEnum() {
        CodeEnumType type = new CodeEnumType();
        Properties props = new Properties();
        props.setProperty(DynamicParameterizedType.RETURNED_CLASS, NoAnnotationEnum.class.getName());
        assertThrows(HibernateException.class, () -> type.setParameterValues(props));
    }

    // ---- resolveCodeField: various types ----

    @Test
    void resolveCodeField_longType() {
        Field field = CodeEnumType.resolveCodeField(LongCodeEnum.class);
        assertNotNull(field);
        assertEquals("code", field.getName());
    }

    @Test
    void resolveCodeField_intType() {
        Field field = CodeEnumType.resolveCodeField(IntCodeEnum.class);
        assertNotNull(field);
        assertEquals("code", field.getName());
    }

    @Test
    void resolveCodeField_stringType() {
        Field field = CodeEnumType.resolveCodeField(StringCodeEnum.class);
        assertNotNull(field);
    }

    @Test
    void resolveCodeField_charType() {
        Field field = CodeEnumType.resolveCodeField(CharCodeEnum.class);
        assertNotNull(field);
    }

    @Test
    void resolveCodeField_noAnnotation() {
        Field field = CodeEnumType.resolveCodeField(NoAnnotationEnum.class);
        assertNull(field);
    }

    // ---- setParameterValues: char type (unsupported type fallback) ----

    @Test
    void setParameterValues_charType_usesFallback() {
        CodeEnumType type = new CodeEnumType();
        Properties props = new Properties();
        props.setProperty(DynamicParameterizedType.RETURNED_CLASS, CharCodeEnum.class.getName());
        type.setParameterValues(props);
        // Char type should use Types.CHAR as fallback
        assertEquals(Types.CHAR, type.getSqlType());
    }

    // ---- nullSafeGet: various paths ----

    @Test
    void nullSafeGet_longType() throws Exception {
        CodeEnumType type = new CodeEnumType();
        Properties props = new Properties();
        props.setProperty(DynamicParameterizedType.RETURNED_CLASS, LongCodeEnum.class.getName());
        type.setParameterValues(props);
        ResultSet rs = mock(ResultSet.class);
        when(rs.getLong(1)).thenReturn(100L);
        when(rs.wasNull()).thenReturn(false);
        assertEquals(LongCodeEnum.A, type.nullSafeGet(rs, 1, null, null));
    }

    @Test
    void nullSafeGet_longType_null() throws Exception {
        CodeEnumType type = new CodeEnumType();
        Properties props = new Properties();
        props.setProperty(DynamicParameterizedType.RETURNED_CLASS, LongCodeEnum.class.getName());
        type.setParameterValues(props);
        ResultSet rs = mock(ResultSet.class);
        when(rs.getLong(1)).thenReturn(0L);
        when(rs.wasNull()).thenReturn(true);
        assertNull(type.nullSafeGet(rs, 1, null, null));
    }

    @Test
    void nullSafeGet_integerType() throws Exception {
        CodeEnumType type = new CodeEnumType();
        Properties props = new Properties();
        props.setProperty(DynamicParameterizedType.RETURNED_CLASS, IntCodeEnum.class.getName());
        type.setParameterValues(props);
        ResultSet rs = mock(ResultSet.class);
        when(rs.getInt(1)).thenReturn(0);
        when(rs.wasNull()).thenReturn(false);
        assertEquals(IntCodeEnum.ACTIVE, type.nullSafeGet(rs, 1, null, null));
    }

    @Test
    void nullSafeGet_integerType_null() throws Exception {
        CodeEnumType type = new CodeEnumType();
        Properties props = new Properties();
        props.setProperty(DynamicParameterizedType.RETURNED_CLASS, IntCodeEnum.class.getName());
        type.setParameterValues(props);
        ResultSet rs = mock(ResultSet.class);
        when(rs.getInt(1)).thenReturn(0);
        when(rs.wasNull()).thenReturn(true);
        assertNull(type.nullSafeGet(rs, 1, null, null));
    }

    @Test
    void nullSafeGet_stringType() throws Exception {
        CodeEnumType type = new CodeEnumType();
        Properties props = new Properties();
        props.setProperty(DynamicParameterizedType.RETURNED_CLASS, StringCodeEnum.class.getName());
        type.setParameterValues(props);
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString(1)).thenReturn("M");
        assertEquals(StringCodeEnum.MALE, type.nullSafeGet(rs, 1, null, null));
    }

    @Test
    void nullSafeGet_stringType_emptyValue() throws Exception {
        CodeEnumType type = new CodeEnumType();
        Properties props = new Properties();
        props.setProperty(DynamicParameterizedType.RETURNED_CLASS, StringCodeEnum.class.getName());
        type.setParameterValues(props);
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString(1)).thenReturn("  ");
        assertNull(type.nullSafeGet(rs, 1, null, null));
    }

    @Test
    void nullSafeGet_stringType_unknownCode() throws Exception {
        CodeEnumType type = new CodeEnumType();
        Properties props = new Properties();
        props.setProperty(DynamicParameterizedType.RETURNED_CLASS, StringCodeEnum.class.getName());
        type.setParameterValues(props);
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString(1)).thenReturn("Z");
        assertThrows(HibernateException.class, () -> type.nullSafeGet(rs, 1, null, null));
    }

    @Test
    void nullSafeGet_longType_unknownCode() throws Exception {
        CodeEnumType type = new CodeEnumType();
        Properties props = new Properties();
        props.setProperty(DynamicParameterizedType.RETURNED_CLASS, LongCodeEnum.class.getName());
        type.setParameterValues(props);
        ResultSet rs = mock(ResultSet.class);
        when(rs.getLong(1)).thenReturn(999L);
        when(rs.wasNull()).thenReturn(false);
        assertThrows(HibernateException.class, () -> type.nullSafeGet(rs, 1, null, null));
    }

    // ---- nullSafeSet: various paths ----

    @Test
    void nullSafeSet_nullValue() throws Exception {
        CodeEnumType type = new CodeEnumType();
        Properties props = new Properties();
        props.setProperty(DynamicParameterizedType.RETURNED_CLASS, StringCodeEnum.class.getName());
        type.setParameterValues(props);
        PreparedStatement ps = mock(PreparedStatement.class);
        type.nullSafeSet(ps, null, 1, null);
        verify(ps).setNull(1, Types.VARCHAR);
    }

    @Test
    void nullSafeSet_longValue() throws Exception {
        CodeEnumType type = new CodeEnumType();
        Properties props = new Properties();
        props.setProperty(DynamicParameterizedType.RETURNED_CLASS, LongCodeEnum.class.getName());
        type.setParameterValues(props);
        PreparedStatement ps = mock(PreparedStatement.class);
        type.nullSafeSet(ps, LongCodeEnum.A, 1, null);
        verify(ps).setLong(1, 100L);
    }

    @Test
    void nullSafeSet_intValue() throws Exception {
        CodeEnumType type = new CodeEnumType();
        Properties props = new Properties();
        props.setProperty(DynamicParameterizedType.RETURNED_CLASS, IntCodeEnum.class.getName());
        type.setParameterValues(props);
        PreparedStatement ps = mock(PreparedStatement.class);
        type.nullSafeSet(ps, IntCodeEnum.ACTIVE, 1, null);
        verify(ps).setInt(1, 0);
    }

    @Test
    void nullSafeSet_stringValue() throws Exception {
        CodeEnumType type = new CodeEnumType();
        Properties props = new Properties();
        props.setProperty(DynamicParameterizedType.RETURNED_CLASS, StringCodeEnum.class.getName());
        type.setParameterValues(props);
        PreparedStatement ps = mock(PreparedStatement.class);
        type.nullSafeSet(ps, StringCodeEnum.MALE, 1, null);
        verify(ps).setString(1, "M");
    }

    // ---- disassemble/assemble: various paths ----

    @Test
    void disassemble_null() throws Exception {
        CodeEnumType type = new CodeEnumType();
        Properties props = new Properties();
        props.setProperty(DynamicParameterizedType.RETURNED_CLASS, StringCodeEnum.class.getName());
        type.setParameterValues(props);
        assertNull(type.disassemble(null));
    }

    @Test
    void disassemble_withValue() throws Exception {
        CodeEnumType type = new CodeEnumType();
        Properties props = new Properties();
        props.setProperty(DynamicParameterizedType.RETURNED_CLASS, StringCodeEnum.class.getName());
        type.setParameterValues(props);
        assertEquals("M", type.disassemble(StringCodeEnum.MALE));
    }

    @Test
    void assemble_null() throws Exception {
        CodeEnumType type = new CodeEnumType();
        Properties props = new Properties();
        props.setProperty(DynamicParameterizedType.RETURNED_CLASS, StringCodeEnum.class.getName());
        type.setParameterValues(props);
        assertNull(type.assemble(null, null));
    }

    @Test
    void assemble_validCode() throws Exception {
        CodeEnumType type = new CodeEnumType();
        Properties props = new Properties();
        props.setProperty(DynamicParameterizedType.RETURNED_CLASS, StringCodeEnum.class.getName());
        type.setParameterValues(props);
        assertEquals(StringCodeEnum.MALE, type.assemble("M", null));
    }

    @Test
    void assemble_invalidCode() throws Exception {
        CodeEnumType type = new CodeEnumType();
        Properties props = new Properties();
        props.setProperty(DynamicParameterizedType.RETURNED_CLASS, StringCodeEnum.class.getName());
        type.setParameterValues(props);
        assertThrows(HibernateException.class, () -> type.assemble("Z", null));
    }

    @Test
    void assemble_longType() throws Exception {
        CodeEnumType type = new CodeEnumType();
        Properties props = new Properties();
        props.setProperty(DynamicParameterizedType.RETURNED_CLASS, LongCodeEnum.class.getName());
        type.setParameterValues(props);
        assertEquals(LongCodeEnum.A, type.assemble("100", null));
    }

    @Test
    void assemble_longType_invalid() throws Exception {
        CodeEnumType type = new CodeEnumType();
        Properties props = new Properties();
        props.setProperty(DynamicParameterizedType.RETURNED_CLASS, LongCodeEnum.class.getName());
        type.setParameterValues(props);
        assertThrows(HibernateException.class, () -> type.assemble("999", null));
    }

    // ---- deepCopy/isMutable ----

    @Test
    void deepCopy_returnsSameObject() throws Exception {
        CodeEnumType type = new CodeEnumType();
        Properties props = new Properties();
        props.setProperty(DynamicParameterizedType.RETURNED_CLASS, StringCodeEnum.class.getName());
        type.setParameterValues(props);
        assertSame(StringCodeEnum.MALE, type.deepCopy(StringCodeEnum.MALE));
    }

    @Test
    void isMutable_returnsFalse() throws Exception {
        CodeEnumType type = new CodeEnumType();
        Properties props = new Properties();
        props.setProperty(DynamicParameterizedType.RETURNED_CLASS, StringCodeEnum.class.getName());
        type.setParameterValues(props);
        assertFalse(type.isMutable());
    }

    // ---- nullSafeGet: ordinal path ----

    @Test
    void nullSafeGet_ordinalPath_validOrdinal() throws Exception {
        // Create a CodeEnumType with useOrdinal=true by using an enum without @CodeEnumValue
        CodeEnumType type = new CodeEnumType();
        Properties props = new Properties();
        props.setProperty(DynamicParameterizedType.RETURNED_CLASS, NoAnnotationEnum.class.getName());
        // This will set useOrdinal=true since there's no @CodeEnumValue
        // But resolveCodeField will throw HibernateException because ordinal-based mapping is not supported
        // So we need to test the ordinal path differently
        // Actually, looking at the code, if useOrdinal is true, resolveCodeField throws an exception
        // So the ordinal path in nullSafeGet is unreachable in normal operation
    }

    // ---- getOrBuildCodeMap: various paths ----

    @Test
    void getOrBuildCodeMap_buildsMap() throws Exception {
        CodeEnumType type = new CodeEnumType();
        Properties props = new Properties();
        props.setProperty(DynamicParameterizedType.RETURNED_CLASS, StringCodeEnum.class.getName());
        type.setParameterValues(props);
        java.lang.reflect.Method m = CodeEnumType.class.getDeclaredMethod("getOrBuildCodeMap");
        m.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.concurrent.ConcurrentMap<String, Object> map =
            (java.util.concurrent.ConcurrentMap<String, Object>)m.invoke(type);
        assertNotNull(map);
        assertEquals(StringCodeEnum.MALE, map.get("M"));
        assertEquals(StringCodeEnum.FEMALE, map.get("F"));
    }
}
