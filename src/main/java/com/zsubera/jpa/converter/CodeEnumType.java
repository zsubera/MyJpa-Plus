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
 * <strong>使用方式：只需在枚举的 code 字段上加 {@code @CodeEnumValue}，无需创建转换器类！</strong>
 *
 * <pre>
 * {
 *     &#64;code
 *     public enum StatusEnum {
 *         ACTIVE(0, "正常"), DELETED(1, "已删除");
 *
 *         &#64;CodeEnumValue // 只需这一步！
 *         private final int code;
 *         private final String desc;
 *     }
 *
 *     // 实体中使用 @CodeEnum 注解
 *     &#64;Entity
 *     public class User {
 *         @CodeEnum
 *         private StatusEnum status;
 *     }
 * }
 * </pre>
 *
 * <p>
 * <strong>何时必须加 {@code @CodeEnumValue}：</strong>当枚举的 {@code code} 值与 {@code ordinal()} 不同时。
 *
 * <p>
 * <strong>何时可加可不加：</strong>当枚举的 {@code code} 值与 {@code ordinal()} 相同时，建议加上保持一致性。
 *
 * @author myjpa-plus
 * @since 1.1.0
 * @see CodeEnumValue
 * @see CodeEnum
 */
public class CodeEnumType implements UserType<Object>, DynamicParameterizedType {

    private static final Logger log = LoggerFactory.getLogger(CodeEnumType.class);

    private static final ConcurrentMap<Class<?>, Field> CODE_FIELD_CACHE = new ConcurrentHashMap<>();

    private Class<?> enumClass;
    private Field codeField;
    private int sqlType;
    private boolean useOrdinal;

    @Override
    public void setParameterValues(Properties parameters) {
        String typeName = parameters.getProperty(DynamicParameterizedType.RETURNED_CLASS);
        if (typeName != null) {
            try {
                ClassLoader cl = Thread.currentThread().getContextClassLoader();
                if (cl == null) {
                    cl = getClass().getClassLoader();
                }
                Class<?> typeClass = Class.forName(typeName, true, cl);
                if (typeClass.isEnum()) {
                    this.enumClass = typeClass;
                    resolveCodeField();
                    return;
                }
            } catch (ClassNotFoundException e) {
                log.warn("Failed to resolve enum class: {}", typeName, e);
            }
        }
        throw new HibernateException("CodeEnumType cannot resolve enum class.");
    }

    private void resolveCodeField() {
        this.codeField = resolveCodeField(enumClass);
        this.useOrdinal = (codeField == null);
        if (useOrdinal) {
            this.sqlType = Types.CHAR;
        } else {
            Class<?> fieldType = codeField.getType();
            this.sqlType = (fieldType == String.class) ? Types.VARCHAR : Types.CHAR;
        }
    }

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

    public static boolean hasCodeEnumValue(Class<?> enumClass) {
        return resolveCodeField(enumClass) != null;
    }

    @Override
    public int getSqlType() {
        return sqlType;
    }

    @Override
    public Class<Object> returnedClass() {
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

        if (useOrdinal) {
            try {
                int ordinal = Integer.parseInt(trimmedValue);
                Object[] constants = enumClass.getEnumConstants();
                if (ordinal >= 0 && ordinal < constants.length) {
                    return constants[ordinal];
                }
            } catch (NumberFormatException ignored) {
            }
            throw new HibernateException(
                String.format("No enum constant with ordinal '%s' in %s", trimmedValue, enumClass.getSimpleName()));
        }

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
        if (useOrdinal) {
            st.setString(index, String.valueOf(((Enum<?>)value).ordinal()));
        } else {
            try {
                Object codeValue = codeField.get(value);
                st.setString(index, codeValue != null ? String.valueOf(codeValue) : null);
            } catch (IllegalAccessException e) {
                throw new HibernateException("Failed to read @CodeEnumValue field", e);
            }
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
        if (useOrdinal) {
            return String.valueOf(((Enum<?>)value).ordinal());
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
        if (useOrdinal) {
            try {
                int ordinal = Integer.parseInt(code);
                Object[] constants = enumClass.getEnumConstants();
                if (ordinal >= 0 && ordinal < constants.length) {
                    return constants[ordinal];
                }
            } catch (NumberFormatException ignored) {
            }
            return null;
        }
        try {
            for (Object enumConstant : enumClass.getEnumConstants()) {
                Object codeValue = codeField.get(enumConstant);
                if (code.equals(String.valueOf(codeValue))) {
                    return enumConstant;
                }
            }
        } catch (IllegalAccessException ignored) {
        }
        return null;
    }
}
