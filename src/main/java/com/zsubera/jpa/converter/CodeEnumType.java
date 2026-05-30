package com.zsubera.jpa.converter;

import java.lang.reflect.Field;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.hibernate.HibernateException;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.usertype.DynamicParameterizedType;
import org.hibernate.usertype.UserType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 通用枚举 Hibernate {@link UserType} 实现，配合 {@link CodeEnumValue} 注解使用。
 *
 * <p>
 * 解决 Hibernate 6 将数据库中 CHAR(1) 类型的枚举列（存储 '0'、'1' 等编码）映射为 TINYINT， 导致 {@code ArrayIndexOutOfBoundsException} 的问题。
 *
 * <p>
 * <strong>使用方式：</strong>
 *
 * <pre>
 * {
 *     &#64;code
 *     // 1. 枚举的 code 字段上加 @CodeEnumValue
 *     public enum StatusEnum {
 *         ACTIVE(0, "正常"), DELETED(1, "已删除");
 *
 *         &#64;CodeEnumValue
 *         private final int code;
 *         private final String desc;
 *         // ...
 *     }
 *
 *     // 2. 实体字段上加 @Type(CodeEnumType.class)
 *     &#64;Entity
 *     public class User {
 *         &#64;Type(CodeEnumType.class)
 *         &#64;Column(name = "status")
 *         private StatusEnum status;
 *     }
 * }
 * </pre>
 *
 * <p>
 * <strong>支持的 code 字段类型：</strong>
 * <ul>
 * <li>{@code int} / {@code Integer} — 数据库列为 CHAR/VARCHAR</li>
 * <li>{@code long} / {@code Long} — 数据库列为 CHAR/VARCHAR</li>
 * <li>{@code String} — 数据库列为 VARCHAR</li>
 * </ul>
 *
 * @author myjpa-plus
 * @since 1.1.0
 * @see CodeEnumValue
 */
public class CodeEnumType implements UserType<Object>, DynamicParameterizedType {

    private static final Logger log = LoggerFactory.getLogger(CodeEnumType.class);

    private static final ConcurrentMap<Class<?>, Field> CODE_FIELD_CACHE = new ConcurrentHashMap<>();

    private Class<?> enumClass;
    private Field codeField;
    private int sqlType;

    @Override
    public void setParameterValues(Properties parameters) {
        String typeName = parameters.getProperty(DynamicParameterizedType.RETURNED_CLASS);
        if (typeName != null) {
            try {
                Class<?> typeClass = Class.forName(typeName);
                if (typeClass.isEnum()) {
                    this.enumClass = typeClass;
                    resolveCodeField();
                    return;
                }
            } catch (ClassNotFoundException e) {
                log.warn("Failed to resolve enum class: {}", typeName, e);
            }
        }
        for (String key : parameters.stringPropertyNames()) {
            String value = parameters.getProperty(key);
            if (value != null && !value.isEmpty()) {
                try {
                    Class<?> typeClass = Class.forName(value);
                    if (typeClass.isEnum()) {
                        this.enumClass = typeClass;
                        resolveCodeField();
                        return;
                    }
                } catch (ClassNotFoundException ignored) {
                    // 继续尝试下一个参数
                }
            }
        }
        throw new HibernateException("CodeEnumType cannot resolve enum class.");
    }

    private void resolveCodeField() {
        this.codeField = resolveCodeField(enumClass);
        if (this.codeField == null) {
            throw new HibernateException(
                String.format("Enum %s must have a field annotated with @CodeEnumValue", enumClass.getSimpleName()));
        }
        Class<?> fieldType = codeField.getType();
        this.sqlType = (fieldType == String.class) ? Types.VARCHAR : Types.CHAR;
    }

    /**
     * 从枚举类解析 {@link CodeEnumValue} 注解标记的字段。
     *
     * @param enumClass 枚举类
     * @return {@code @CodeEnumValue} 标记的字段，如果没有则返回 {@code null}
     */
    public static Field resolveCodeField(Class<?> enumClass) {
        return CODE_FIELD_CACHE.computeIfAbsent(enumClass, cls -> {
            for (Field field : cls.getDeclaredFields()) {
                if (field.isAnnotationPresent(CodeEnumValue.class)) {
                    field.setAccessible(true);
                    return field;
                }
            }
            return null;
        });
    }

    /**
     * 检查枚举类是否有 {@link CodeEnumValue} 注解标记的字段。
     *
     * @param enumClass 枚举类
     * @return 如果有 {@code @CodeEnumValue} 字段返回 {@code true}
     */
    public static boolean hasCodeEnumValue(Class<?> enumClass) {
        return resolveCodeField(enumClass) != null;
    }

    @Override
    public int getSqlType() {
        return sqlType;
    }

    @Override
    public Class<Object> returnedClass() {
        // 返回具体的枚举类型，而不是 Object.class
        // 这样 Hibernate 才能正确设置字段值
        return (Class<Object>)(Class<?>)enumClass;
    }

    @Override
    public boolean equals(Object x, Object y) {
        return Objects.equals(x, y);
    }

    @Override
    public int hashCode(Object x) {
        return Objects.hashCode(x);
    }

    @Override
    public Object nullSafeGet(ResultSet rs, int position, SharedSessionContractImplementor session, Object owner)
        throws SQLException {
        String value = rs.getString(position);
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        String trimmedValue = value.trim();
        try {
            for (Object enumConstant : enumClass.getEnumConstants()) {
                Object codeValue = codeField.get(enumConstant);
                if (codeValue != null && trimmedValue.equals(String.valueOf(codeValue))) {
                    return enumConstant;
                }
            }
        } catch (IllegalAccessException e) {
            throw new HibernateException("Failed to read @CodeEnumValue field", e);
        }
        throw new HibernateException(
            String.format("No enum constant with code '%s' in %s", trimmedValue, enumClass.getSimpleName()));
    }

    @Override
    public void nullSafeSet(PreparedStatement st, Object value, int index, SharedSessionContractImplementor session)
        throws SQLException {
        if (value == null) {
            st.setNull(index, sqlType);
            return;
        }
        try {
            Object codeValue = codeField.get(value);
            st.setString(index, codeValue != null ? String.valueOf(codeValue) : null);
        } catch (IllegalAccessException e) {
            throw new HibernateException("Failed to read @CodeEnumValue field", e);
        }
    }

    @Override
    public Object deepCopy(Object value) {
        return value;
    }

    @Override
    public boolean isMutable() {
        return false;
    }

    @Override
    public java.io.Serializable disassemble(Object value) {
        if (value == null) {
            return null;
        }
        try {
            Object codeValue = codeField.get(value);
            return codeValue != null ? String.valueOf(codeValue) : null;
        } catch (IllegalAccessException e) {
            return null;
        }
    }

    @Override
    public Object assemble(java.io.Serializable cached, Object owner) {
        if (cached == null) {
            return null;
        }
        String code = cached.toString();
        try {
            for (Object enumConstant : enumClass.getEnumConstants()) {
                Object codeValue = codeField.get(enumConstant);
                if (code.equals(String.valueOf(codeValue))) {
                    return enumConstant;
                }
            }
        } catch (IllegalAccessException ignored) {
            // 返回 null
        }
        return null;
    }
}
