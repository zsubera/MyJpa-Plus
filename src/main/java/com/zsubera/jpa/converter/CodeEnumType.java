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
 * <pre>{@code
 * public enum StatusEnum {
 *     ACTIVE(0, "正常"), DELETED(1, "已删除");
 *
 *     @CodeEnumValue // 只需这一步！
 *     private final int code;
 *     private final String desc;
 * }
 *
 * // 实体中使用 @CodeEnum 注解
 * @Entity
 * public class User {
 *     @CodeEnum
 *     private StatusEnum status;
 * }
 * }</pre>
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

    /** 哨兵字段，用于区分缓存中"未扫描"和"已扫描但未找到"的状态。使用专用哨兵而非反射获取字段名。 */
    private static final Field NO_CODE_FIELD_SENTINEL;

    static {
        try {
            NO_CODE_FIELD_SENTINEL = CodeEnumType.class.getDeclaredField("enumClass");
        } catch (NoSuchFieldException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

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
                // 安全检查 - 仅允许加载枚举类型
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
            // 未找到 @CodeEnumValue 时记录警告，回退到基于 ordinal 的映射
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
                log.warn(
                    "Unsupported @CodeEnumValue field type '{}' in enum {}. Using Types.CHAR as fallback. "
                        + "Supported types: String, int/Integer, long/Long.",
                    fieldType.getName(), enumClass.getSimpleName());
            }
        }
    }

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
                    } catch (java.lang.reflect.InaccessibleObjectException e) {
                        throw new IllegalStateException("Cannot access @CodeEnumValue field '" + field.getName()
                            + "' in " + cls.getName() + ". On Java 17+, add JVM argument: "
                            + "--add-opens java.base/java.lang.reflect=ALL-UNNAMED", e);
                    }
                    return field;
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
        // 健壮地处理不同的 SQL 类型
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
                // ordinal 越界
                throw new HibernateException(
                    String.format("No enum constant with ordinal '%s' in %s (valid range: 0-%d)", trimmedValue,
                        enumClass.getSimpleName(), constants.length - 1));
            } catch (NumberFormatException e) {
                throw new HibernateException(
                    String.format("No enum constant with ordinal '%s' in %s", trimmedValue, enumClass.getSimpleName()),
                    e);
            }
        }

        ConcurrentMap<String, Object> codeMap = getOrBuildCodeMap();
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
                    // 根据 code 字段类型使用类型化的 setter
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
            // 抛出异常而非静默返回 null，防止二级缓存场景下丢失枚举值
            throw new HibernateException(
                "Failed to access code field for enum " + value.getClass().getSimpleName() + ": " + e.getMessage(), e);
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
                // ordinal 越界
                throw new HibernateException(
                    String.format("No enum constant with ordinal '%s' in %s (valid range: 0-%d)", code,
                        enumClass.getSimpleName(), constants.length - 1));
            } catch (NumberFormatException e) {
                throw new HibernateException(
                    String.format("No enum constant with ordinal '%s' in %s", code, enumClass.getSimpleName()), e);
            }
        }
        // 使用共享的 getOrBuildCodeMap() 实现 O(1) 查找
        ConcurrentMap<String, Object> codeMap = getOrBuildCodeMap();
        Object result = codeMap.get(code);
        if (result != null) {
            return result;
        }
        throw new HibernateException(
            String.format("No enum constant with code '%s' in %s", code, enumClass.getSimpleName()));
    }

    /**
     * 获取或构建枚举 code -> 常量的缓存映射。
     */
    private ConcurrentMap<String, Object> getOrBuildCodeMap() {
        return ENUM_CODE_CACHE.computeIfAbsent(enumClass, cls -> {
            ConcurrentMap<String, Object> map = new ConcurrentHashMap<>();
            for (Object constant : cls.getEnumConstants()) {
                try {
                    Object codeValue = codeField.get(constant);
                    if (codeValue != null) {
                        // trim() 键以匹配 nullSafeGet 中的 trimmedValue 查找，防止含空格的 String 类型 CodeEnum 反序列化失败
                        map.put(String.valueOf(codeValue).trim(), constant);
                    }
                } catch (IllegalAccessException e) {
                    log.warn("Failed to read @CodeEnumValue field for enum {}", cls.getSimpleName(), e);
                }
            }
            return map;
        });
    }
}
