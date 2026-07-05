package com.zsubera.jpa.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 字段级显示层脱敏注解。
 *
 * <p>
 * 标记在 JPA 实体或 DTO 的 {@link String} 字段上，由 {@link com.zsubera.jpa.converter.MaskSerializer.MaskModule}
 * 在 JSON 序列化时自动对字段值进行脱敏处理。
 *
 * <p>
 * <strong>重要安全说明：</strong>此注解仅在 JSON 输出时进行脱敏。实体对象在 JVM 内存中仍持有完整原始数据。
 * 如需在存储层也进行数据保护，请与 {@link Encrypt @Encrypt} 注解组合使用。
 *
 * <p>
 * 使用示例：
 *
 * <pre>{@code
 * public class UserDto {
 *     private String name;
 *
 *     @Mask(type = MaskType.PHONE)
 *     private String phone;
 *
 *     @Mask(type = MaskType.EMAIL)
 *     private String email;
 * }
 * }</pre>
 *
 * <p>
 * 需要将 {@link com.zsubera.jpa.converter.MaskSerializer.MaskModule} 注册到 {@code ObjectMapper}：
 *
 * <pre>{@code
 * @Configuration
 * public class JacksonConfig {
 *     @Bean
 *     public ObjectMapper objectMapper() {
 *         ObjectMapper mapper = new ObjectMapper();
 *         mapper.registerModule(new MaskSerializer.MaskModule());
 *         return mapper;
 *     }
 * }
 * }</pre>
 *
 * @see MaskType
 * @see com.zsubera.jpa.converter.MaskSerializer
 * @see Encrypt
 */
@Documented
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Mask {
    /**
     * 脱敏类型。
     *
     * @return 脱敏类型枚举值
     */
    MaskType type();
}
