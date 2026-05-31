package com.zsubera.jpa.converter;

import static org.junit.jupiter.api.Assertions.*;

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

    // ===== 没有 @CodeEnumValue 的枚举 =====

    enum NoAnnotationEnum {
        A, B
    }

    @Test
    @DisplayName("没有 @CodeEnumValue 的枚举应返回 false")
    void shouldReturnFalseForEnumWithoutAnnotation() {
        assertFalse(CodeEnumType.hasCodeEnumValue(NoAnnotationEnum.class));
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
}
