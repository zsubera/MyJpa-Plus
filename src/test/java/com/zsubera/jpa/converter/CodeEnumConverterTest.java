package com.zsubera.jpa.converter;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CodeEnumConverterTest {

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

    enum SexEnum {
        UNKNOWN(0, "未知"), MALE(1, "男"), FEMALE(2, "女");

        @CodeEnumValue
        private final int code;
        private final String desc;

        SexEnum(int code, String desc) {
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

    enum UserTypeEnum {
        ADMIN("A", "管理员"), USER("U", "普通用户"), GUEST("G", "访客");

        @CodeEnumValue
        private final String code;
        private final String desc;

        UserTypeEnum(String code, String desc) {
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

    // ===== @CodeEnumValue 注解测试 =====

    @Test
    @DisplayName("int 类型 code 应正确标记")
    void shouldMarkIntCodeField() throws Exception {
        java.lang.reflect.Field codeField = StatusEnum.class.getDeclaredField("code");
        assertNotNull(codeField.getAnnotation(CodeEnumValue.class));
    }

    @Test
    @DisplayName("String 类型 code 应正确标记")
    void shouldMarkStringCodeField() throws Exception {
        java.lang.reflect.Field codeField = GenderEnum.class.getDeclaredField("code");
        assertNotNull(codeField.getAnnotation(CodeEnumValue.class));
    }

    @Test
    @DisplayName("int code 枚举应能获取正确的值")
    void shouldGetIntCodeValue() throws Exception {
        java.lang.reflect.Field codeField = StatusEnum.class.getDeclaredField("code");
        codeField.setAccessible(true);
        assertEquals(0, codeField.getInt(StatusEnum.ACTIVE));
        assertEquals(1, codeField.getInt(StatusEnum.DELETED));
    }

    @Test
    @DisplayName("String code 枚举应能获取正确的值")
    void shouldGetStringCodeValue() throws Exception {
        java.lang.reflect.Field codeField = GenderEnum.class.getDeclaredField("code");
        codeField.setAccessible(true);
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
        assertEquals(1, SexEnum.MALE.getCode());
        assertEquals("M", GenderEnum.MALE.getCode());
        assertEquals("A", UserTypeEnum.ADMIN.getCode());
    }

    @Test
    @DisplayName("枚举 toString 应正常工作")
    void shouldHaveWorkingToString() {
        assertEquals("ACTIVE", StatusEnum.ACTIVE.toString());
        assertEquals("DELETED", StatusEnum.DELETED.toString());
    }

    @Test
    @DisplayName("枚举 valueOf 应正常工作")
    void shouldHaveWorkingValueOf() {
        assertEquals(StatusEnum.ACTIVE, StatusEnum.valueOf("ACTIVE"));
        assertEquals(StatusEnum.DELETED, StatusEnum.valueOf("DELETED"));
    }

    @Test
    @DisplayName("枚举 ordinal 应正常工作")
    void shouldHaveWorkingOrdinal() {
        assertEquals(0, StatusEnum.ACTIVE.ordinal());
        assertEquals(1, StatusEnum.DELETED.ordinal());
    }

    @Test
    @DisplayName("枚举 name 应正常工作")
    void shouldHaveWorkingName() {
        assertEquals("ACTIVE", StatusEnum.ACTIVE.name());
        assertEquals("DELETED", StatusEnum.DELETED.name());
    }

    // ===== 没有 @CodeEnumValue 的枚举 =====

    enum NoAnnotationEnum {
        A, B
    }

    // ===== CodeEnumType 工具方法测试 =====

    @Test
    @DisplayName("CodeEnumType 应能解析 @CodeEnumValue 字段")
    void shouldResolveCodeEnumValueField() {
        java.lang.reflect.Field field = CodeEnumType.resolveCodeField(StatusEnum.class);
        assertNotNull(field);
        assertEquals("code", field.getName());
    }

    @Test
    @DisplayName("CodeEnumType 应能检查是否有 @CodeEnumValue 字段")
    void shouldCheckCodeEnumValuePresence() {
        assertTrue(CodeEnumType.hasCodeEnumValue(StatusEnum.class));
        assertTrue(CodeEnumType.hasCodeEnumValue(GenderEnum.class));
    }

    @Test
    @DisplayName("没有 @CodeEnumValue 的枚举应返回 false")
    void shouldReturnFalseForEnumWithoutAnnotation() {
        assertFalse(CodeEnumType.hasCodeEnumValue(NoAnnotationEnum.class));
    }
}
