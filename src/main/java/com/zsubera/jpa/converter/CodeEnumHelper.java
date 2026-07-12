package com.zsubera.jpa.converter;

import jakarta.persistence.AttributeConverter;
import java.lang.reflect.Field;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 枚举编码转换的工具类，为{@link AttributeConverter}和{@link com.zsubera.jpa.converter.CodeEnumType}提供共享的反射解析逻辑。
 *
 * <p>
 * <strong>与 {@link CodeEnumType} 的关系：</strong>{@code CodeEnumType} 是 Hibernate {@code UserType} 实现，
 * 仅适用于 Hibernate 作为 JPA Provider 的场景。对于 EclipseLink 等非 Hibernate JPA 实现，
 * 应使用本工具类配合 {@code AttributeConverter} 模式。
 *
 * <p>
 * <strong>非 Hibernate JPA Provider 使用方式：</strong>
 *
 * <pre>{@code
 * // 1. 直接使用 @CodeEnumValue 解析
 * Field codeField = CodeEnumHelper.resolveCodeField(StatusEnum.class);
 *
 * // 2. 使用 CodeEnumConverterFactory 创建每个枚举专用的 AttributeConverter
 * // @Convert(converter = StatusEnumConverter.class)
 * // private StatusEnum status;
 * }</pre>
 *
 * @see CodeEnumValue
 * @see CodeEnumType
 */
public final class CodeEnumHelper {

    private static final ConcurrentMap<Class<?>, Field> CODE_FIELD_CACHE = new ConcurrentHashMap<>();

    private static final Field NO_CODE_FIELD_SENTINEL;

    static {
        try {
            NO_CODE_FIELD_SENTINEL = CodeEnumHelper.class.getDeclaredField("NO_CODE_FIELD_SENTINEL");
        } catch (NoSuchFieldException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private CodeEnumHelper() {}

    /**
     * 解析枚举类中带有 {@link CodeEnumValue} 注解的字段。
     *
     * @param enumClass 枚举类
     * @return 标注了 {@code @CodeEnumValue} 的字段，如果未找到则返回 null
     */
    public static Field resolveCodeField(Class<?> enumClass) {
        Field cached = CODE_FIELD_CACHE.computeIfAbsent(enumClass, cls -> {
            for (Field field : cls.getDeclaredFields()) {
                if (field.isAnnotationPresent(CodeEnumValue.class)) {
                    try {
                        field.setAccessible(true);
                        return field;
                    } catch (Exception e) {
                        // ponytail: 记录完整异常栈，帮助排查模块访问限制问题
                        // 不静默返回 sentinel — 上游 CodeEnumType 会抛出 "@CodeEnumValue not found"
                        // 误导性错误，此处日志是定位根因的唯一线索
                        org.slf4j.LoggerFactory.getLogger(CodeEnumHelper.class).error(
                            "Cannot access @CodeEnumValue field '{}' in enum {}. "
                                + "Ensure the enum package is opened to the persistence provider.",
                            field.getName(), cls.getName(), e);
                        return NO_CODE_FIELD_SENTINEL;
                    }
                }
            }
            return NO_CODE_FIELD_SENTINEL;
        });
        return cached == NO_CODE_FIELD_SENTINEL ? null : cached;
    }

    /**
     * 检查枚举类是否包含 {@link CodeEnumValue} 注解的字段。
     *
     * @param enumClass 枚举类
     * @return 如果存在 {@code @CodeEnumValue} 字段则返回 true
     */
    public static boolean hasCodeEnumValue(Class<?> enumClass) {
        return resolveCodeField(enumClass) != null;
    }

}
