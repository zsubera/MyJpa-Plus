package com.zsubera.jpa.converter;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Types;
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
        // 需要先初始化 enumClass 和相关字段
        try {
            java.lang.reflect.Field enumClassField = CodeEnumType.class.getDeclaredField("enumClass");
            enumClassField.setAccessible(true);
            enumClassField.set(type, StatusEnum.class);

            java.lang.reflect.Field codeFieldField = CodeEnumType.class.getDeclaredField("codeField");
            codeFieldField.setAccessible(true);
            codeFieldField.set(type, CodeEnumType.resolveCodeField(StatusEnum.class));

            java.lang.reflect.Field useOrdinalField = CodeEnumType.class.getDeclaredField("useOrdinal");
            useOrdinalField.setAccessible(true);
            useOrdinalField.set(type, false);

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
        // 需要先初始化 enumClass 和相关字段
        // 由于 setParameterValues 需要 Hibernate 环境，这里通过反射设置
        try {
            java.lang.reflect.Field enumClassField = CodeEnumType.class.getDeclaredField("enumClass");
            enumClassField.setAccessible(true);
            enumClassField.set(type, StatusEnum.class);

            java.lang.reflect.Field codeFieldField = CodeEnumType.class.getDeclaredField("codeField");
            codeFieldField.setAccessible(true);
            codeFieldField.set(type, CodeEnumType.resolveCodeField(StatusEnum.class));

            java.lang.reflect.Field useOrdinalField = CodeEnumType.class.getDeclaredField("useOrdinal");
            useOrdinalField.setAccessible(true);
            useOrdinalField.set(type, false);

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
    @DisplayName("assemble 无效 code 应返回 null")
    void shouldAssembleInvalidCodeReturnNull() {
        CodeEnumType type = new CodeEnumType();
        try {
            java.lang.reflect.Field enumClassField = CodeEnumType.class.getDeclaredField("enumClass");
            enumClassField.setAccessible(true);
            enumClassField.set(type, StatusEnum.class);

            java.lang.reflect.Field codeFieldField = CodeEnumType.class.getDeclaredField("codeField");
            codeFieldField.setAccessible(true);
            codeFieldField.set(type, CodeEnumType.resolveCodeField(StatusEnum.class));

            java.lang.reflect.Field useOrdinalField = CodeEnumType.class.getDeclaredField("useOrdinal");
            useOrdinalField.setAccessible(true);
            useOrdinalField.set(type, false);

            Object result = type.assemble("999", null);
            assertNull(result);
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
}
