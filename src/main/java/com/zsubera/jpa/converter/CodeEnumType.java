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

    private static final ConcurrentMap<Class<?>, Field> CODE_FIELD_CACHE =
        new org.springframework.util.ConcurrentReferenceHashMap<>(16,
            org.springframework.util.ConcurrentReferenceHashMap.ReferenceType.WEAK);

    /** 枚举类 -> (code -> enum constant) 的缓存，用于 nullSafeGet 的 O(1) 查找。 */
    private static final ConcurrentMap<Class<?>, ConcurrentMap<String, Object>> ENUM_CODE_CACHE =
        new org.springframework.util.ConcurrentReferenceHashMap<>(16,
            org.springframework.util.ConcurrentReferenceHashMap.ReferenceType.WEAK);

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
                // P2-2: 安全检查 - 仅允许加载枚举类型
                if (!typeClass.isEnum()) {
                    throw new HibernateException(
                        "CodeEnumType only supports enum types, but got: " + typeClass.getName());
                }
                this.enumClass = typeClass;
                resolveCodeField();
                return;
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
            // P1: Log WARNING when @CodeEnumValue is not found and ordinal is used as fallback
            log.warn(
                "@CodeEnumValue not found in enum {}. Falling back to ordinal-based mapping. "
                    + "Add @CodeEnumValue annotation to the code field for explicit mapping.",
                enumClass.getSimpleName());
            this.sqlType = Types.INTEGER;
        } else {
            Class<?> fieldType = codeField.getType();
            if (fieldType == String.class) {
                this.sqlType = Types.VARCHAR;
            } else if (fieldType == long.class || fieldType == Long.class) {
                this.sqlType = Types.BIGINT;
            } else if (fieldType == int.class || fieldType == Integer.class) {
                this.sqlType = Types.INTEGER;
            } else {
                this.sqlType = Types.CHAR;
            }
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
        // P1: Handle different SQL types robustly
        String value;
        if (sqlType == Types.BIGINT) {
            long longVal = rs.getLong(position);
            if (rs.wasNull()) {
                return null;
            }
            value = String.valueOf(longVal);
        } else if (sqlType == Types.INTEGER) {
            int intVal = rs.getInt(position);
            if (rs.wasNull()) {
                return null;
            }
            value = String.valueOf(intVal);
        } else {
            value = rs.getString(position);
        }
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
            } catch (NumberFormatException e) {
                log.warn("Failed to parse ordinal '{}' for enum {}", trimmedValue, enumClass.getSimpleName(), e);
            }
            throw new HibernateException(
                String.format("No enum constant with ordinal '%s' in %s", trimmedValue, enumClass.getSimpleName()));
        }

        // 使用缓存进行 O(1) 查找，避免每次线性扫描
        ConcurrentMap<String, Object> codeMap = ENUM_CODE_CACHE.computeIfAbsent(enumClass, cls -> {
            ConcurrentMap<String, Object> map = new ConcurrentHashMap<>();
            for (Object constant : cls.getEnumConstants()) {
                try {
                    Object codeValue = codeField.get(constant);
                    if (codeValue != null) {
                        map.put(String.valueOf(codeValue), constant);
                    }
                } catch (IllegalAccessException e) {
                    log.warn("Failed to read @CodeEnumValue field for enum {}", cls.getSimpleName(), e);
                }
            }
            return map;
        });
        Object result = codeMap.get(trimmedValue);
        if (result != null) {
            return result;
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
            st.setInt(index, ((Enum<?>)value).ordinal());
        } else {
            try {
                Object codeValue = codeField.get(value);
                if (codeValue == null) {
                    st.setNull(index, sqlType);
                } else if (codeValue instanceof Integer intVal) {
                    // P1-2: Use typed setter based on code field type
                    st.setInt(index, intVal);
                } else if (codeValue instanceof Long longVal) {
                    st.setLong(index, longVal);
                } else {
                    st.setString(index, String.valueOf(codeValue));
                }
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
            // P2: Log warning instead of silently swallowing exception
            log.warn("Failed to access code field for enum {}: {}", value.getClass().getSimpleName(), e.getMessage());
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
            } catch (NumberFormatException e) {
                log.warn("Failed to parse cached ordinal '{}' for enum {}", code, enumClass.getSimpleName(), e);
            }
            throw new HibernateException(
                String.format("No enum constant with ordinal '%s' in %s", code, enumClass.getSimpleName()));
        }
        // P2: Use ENUM_CODE_CACHE for O(1) lookup instead of linear scan
        ConcurrentMap<String, Object> codeMap = ENUM_CODE_CACHE.computeIfAbsent(enumClass, cls -> {
            ConcurrentMap<String, Object> map = new ConcurrentHashMap<>();
            for (Object constant : cls.getEnumConstants()) {
                try {
                    Object codeValue = codeField.get(constant);
                    if (codeValue != null) {
                        map.put(String.valueOf(codeValue), constant);
                    }
                } catch (IllegalAccessException e) {
                    log.warn("Failed to read @CodeEnumValue field for enum {}", cls.getSimpleName(), e);
                }
            }
            return map;
        });
        Object result = codeMap.get(code);
        if (result != null) {
            return result;
        }
        throw new HibernateException(
            String.format("No enum constant with code '%s' in %s", code, enumClass.getSimpleName()));
    }
}
