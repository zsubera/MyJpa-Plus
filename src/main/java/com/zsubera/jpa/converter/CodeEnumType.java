package com.zsubera.jpa.converter;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
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

 * @see CodeEnumValue
 * @see CodeEnum
 */
public class CodeEnumType implements UserType<Object>, DynamicParameterizedType {

    private static final Logger log = LoggerFactory.getLogger(CodeEnumType.class);

    /** 枚举类 -> (code -> enum constant) 的缓存，用于 nullSafeGet 的 O(1) 查找。 */
    private static final Cache<Class<?>, ConcurrentMap<String, Object>> ENUM_CODE_CACHE =
        Caffeine.newBuilder().weakKeys().build();

    private Class<?> enumClass;
    private Field codeField;
    private int sqlType;

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
        if (this.codeField == null) {
            throw new HibernateException("@CodeEnumValue not found in enum " + enumClass.getSimpleName()
                + ". Add @CodeEnumValue annotation to the code field.");
        }
        Class<?> fieldType = codeField.getType();
        if (fieldType == String.class) {
            this.sqlType = Types.VARCHAR;
        } else if (fieldType == long.class || fieldType == Long.class) {
            this.sqlType = Types.BIGINT;
        } else if (fieldType == int.class || fieldType == Integer.class) {
            this.sqlType = Types.INTEGER;
        } else if (fieldType == char.class || fieldType == Character.class) {
            this.sqlType = Types.CHAR;
        } else {
            throw new HibernateException("Unsupported @CodeEnumValue field type '" + fieldType.getName() + "' in enum "
                + enumClass.getSimpleName() + ". Supported types: String, int/Integer, long/Long.");
        }
    }

    /**
     * 解析枚举类中带有 {@link CodeEnumValue} 注解的字段。
     * <p>
     * 委托给 {@link CodeEnumHelper#resolveCodeField(Class)}，共享缓存。
     *
     * @param enumClass 枚举类
     * @return 标注了 {@code @CodeEnumValue} 的字段，如果未找到则返回 null
     */
    public static Field resolveCodeField(Class<?> enumClass) {
        return CodeEnumHelper.resolveCodeField(enumClass);
    }

    /**
     * 检查枚举类是否包含 {@link CodeEnumValue} 注解的字段。
     *
     * @param enumClass 枚举类
     * @return 如果存在 {@code @CodeEnumValue} 字段则返回 true
     */
    public static boolean hasCodeEnumValue(Class<?> enumClass) {
        return CodeEnumHelper.hasCodeEnumValue(enumClass);
    }

    @Override
    public int getSqlType() {
        return sqlType;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Class<Object> returnedClass() {
        return (Class<Object>)enumClass;
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
        try {
            Object codeValue = codeField.get(value);
            if (codeValue == null) {
                st.setNull(index, sqlType);
            } else if (codeValue instanceof Integer intVal) {
                st.setInt(index, intVal);
            } else if (codeValue instanceof Long longVal) {
                st.setLong(index, longVal);
            } else if (codeValue instanceof Character charVal) {
                st.setString(index, String.valueOf(charVal));
            } else {
                st.setString(index, String.valueOf(codeValue));
            }
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
            throw new HibernateException(
                "Failed to access code field for enum " + value.getClass().getSimpleName() + ": " + e.getMessage(), e);
        }
    }

    @Override
    public Object assemble(java.io.Serializable cached, Object owner) {
        if (cached == null) {
            return null;
        }
        String code = cached.toString().trim();
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
     * 缓存 loader 内部独立解析 codeField，避免依赖实例字段的竞态。
     */
    private ConcurrentMap<String, Object> getOrBuildCodeMap() {
        return ENUM_CODE_CACHE.get(enumClass, cls -> {
            Field field = resolveCodeField(cls);
            if (field == null) {
                throw new HibernateException("@CodeEnumValue not found in enum " + cls.getSimpleName()
                    + ". Add @CodeEnumValue annotation to the code field.");
            }
            ConcurrentMap<String, Object> map = new ConcurrentHashMap<>();
            for (Object constant : cls.getEnumConstants()) {
                try {
                    Object codeValue = field.get(constant);
                    if (codeValue != null) {
                        Object existing = map.put(String.valueOf(codeValue).trim(), constant);
                        if (existing != null) {
                            throw new HibernateException(
                                "Duplicate @CodeEnumValue '" + codeValue + "' in enum " + cls.getSimpleName()
                                    + " — both " + ((Enum<?>)existing).name() + " and " + ((Enum<?>)constant).name());
                        }
                    }
                } catch (IllegalAccessException e) {
                    // ponytail: 不能静默跳过 — 跳过会导致重复 code 检查被绕过，造成静默数据损坏。
                    // 抛出 HibernateException 让用户立即发现模块访问限制问题。
                    throw new HibernateException("Cannot access @CodeEnumValue field in enum " + cls.getSimpleName()
                        + "." + field.getName() + ". Ensure the enum package is opened to the persistence provider "
                        + "(add --add-opens or opens directive in module-info.java).", e);
                }
            }
            return map;
        });
    }
}
