package com.zsubera.jpa.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 字段级数据库加密注解。
 *
 * <p>
 * 标记在 JPA 实体的 {@link String} 字段上，由 {@link com.zsubera.jpa.converter.EncryptConverter} 在
 * 写入数据库时透明加密、读取时透明解密。加密算法为 AES/GCM/NoPadding（认证加密），密钥通过 PBKDF2WithHmacSHA256
 * 派生。
 *
 * <p>
 * 存储格式为 {@code version:base64(iv + ciphertext)}，支持多版本密钥轮换。
 *
 * <p>
 * 使用示例：
 *
 * <pre>{@code
 * @Entity
 * public class User {
 *     @Id
 *     @GeneratedValue
 *     private Long id;
 *
 *     @Encrypt
 *     private String idCard;
 *
 *     @Encrypt
 *     private String phone;
 * }
 * }</pre>
 *
 * <p>
 * <strong>安全建议：</strong>与 {@link Mask @Mask} 注解组合使用，实现"存储加密 + 显示脱敏"的双重保护。
 *
 * @see com.zsubera.jpa.converter.EncryptConverter
 * @see com.zsubera.jpa.annotation.Mask
 */
@Documented
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Encrypt {}
