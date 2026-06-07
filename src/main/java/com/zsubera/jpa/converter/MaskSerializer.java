package com.zsubera.jpa.converter;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier;
import com.zsubera.jpa.annotation.Mask;
import com.zsubera.jpa.annotation.MaskType;
import java.io.IOException;
import java.util.List;

/**
 * Jackson 序列化器，用于对敏感字段进行显示层脱敏。
 *
 * <p>
 * <strong>重要安全说明：</strong>此序列化器仅在 JSON 输出时进行脱敏处理。实体对象在 JVM 内存中仍持有完整的原始数据。 如需在存储层也进行数据保护，请与
 * {@link com.zsubera.jpa.annotation.Encrypt @Encrypt} 注解组合使用， 实现"存储加密 + 显示脱敏"的双重保护。
 *
 * <p>
 * 支持的脱敏类型：{@link MaskType#PHONE}、{@link MaskType#EMAIL}、{@link MaskType#ID_CARD}、
 * {@link MaskType#NAME}、{@link MaskType#BANK_CARD}、{@link MaskType#ADDRESS}、{@link MaskType#LICENSE_PLATE}。
 *
 * @see com.zsubera.jpa.annotation.Mask
 * @see MaskType
 */
public class MaskSerializer extends JsonSerializer<String> {

    private final MaskType maskType;

    /**
     * 创建默认脱敏类型为 {@link MaskType#NAME} 的序列化器。
     */
    public MaskSerializer() {
        this(MaskType.NAME);
    }

    /**
     * 创建指定脱敏类型的序列化器。
     *
     * @param maskType 脱敏类型
     */
    public MaskSerializer(MaskType maskType) {
        this.maskType = maskType;
    }

    @Override
    public void serialize(String value, JsonGenerator gen, SerializerProvider provider) throws IOException {
        if (value == null) {
            gen.writeNull();
            return;
        }
        gen.writeString(mask(value, maskType));
    }

    /**
     * 对敏感字符串进行脱敏处理。
     *
     * @param value 原始值
     * @param maskType 脱敏类型
     * @return 脱敏后的字符串，如果 value 为 null 或空则原样返回
     */
    public static String mask(String value, MaskType maskType) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        return switch (maskType) {
            case PHONE -> maskPhone(value);
            case EMAIL -> maskEmail(value);
            case ID_CARD -> maskIdCard(value);
            case NAME -> maskName(value);
            case BANK_CARD -> maskBankCard(value);
            case ADDRESS -> maskAddress(value);
            case LICENSE_PLATE -> maskLicensePlate(value);
        };
    }

    private static String maskPhone(String phone) {
        if (phone.length() < 3) {
            return "*".repeat(phone.length());
        }
        if (phone.length() < 7) {
            return phone.substring(0, 2) + "*".repeat(phone.length() - 2);
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    private static String maskEmail(String email) {
        // 使用lastIndexOf正确处理多个@符号
        int atIndex = email.lastIndexOf('@');
        if (atIndex <= 0) {
            return email;
        }
        String localPart = email.substring(0, atIndex);
        String domain = email.substring(atIndex);
        if (localPart.length() <= 1) {
            return localPart + "***" + domain;
        }
        return localPart.charAt(0) + "***" + domain;
    }

    private static String maskIdCard(String idCard) {
        if (idCard.length() < 4) {
            return "*".repeat(idCard.length());
        }
        if (idCard.length() < 8) {
            return idCard.substring(0, 1) + "*".repeat(idCard.length() - 2) + idCard.substring(idCard.length() - 1);
        }
        return idCard.substring(0, 3) + "*".repeat(idCard.length() - 7) + idCard.substring(idCard.length() - 4);
    }

    private static String maskName(String name) {
        // 使用codePointCount正确处理Unicode
        int codePointCount = name.codePointCount(0, name.length());
        if (codePointCount <= 1) {
            return name;
        }
        if (codePointCount == 2) {
            return new String(Character.toChars(name.codePointAt(0))) + "*";
        }
        // 对于3个以上字符：保留首尾，遮蔽中间部分
        int lastCodePointIndex = name.offsetByCodePoints(0, codePointCount - 1);
        StringBuilder sb = new StringBuilder();
        sb.append(Character.toChars(name.codePointAt(0)));
        // 确保正确的Unicode字符重复
        for (int i = 0; i < codePointCount - 2; i++) {
            sb.append('*');
        }
        sb.append(Character.toChars(name.codePointAt(lastCodePointIndex)));
        return sb.toString();
    }

    private static String maskBankCard(String bankCard) {
        if (bankCard.length() < 4) {
            return "*".repeat(bankCard.length());
        }
        if (bankCard.length() < 8) {
            return "*".repeat(bankCard.length() - 2) + bankCard.substring(bankCard.length() - 2);
        }
        return "*".repeat(bankCard.length() - 4) + bankCard.substring(bankCard.length() - 4);
    }

    private static String maskAddress(String address) {
        // 对于短地址（长度<=6），保留前2个字符，遮蔽其余部分
        if (address.length() <= 2) {
            return address;
        }
        if (address.length() <= 6) {
            return address.substring(0, 2) + "*".repeat(address.length() - 2);
        }
        return address.substring(0, 6) + "*".repeat(address.length() - 6);
    }

    private static String maskLicensePlate(String plate) {
        if (plate.length() < 2) {
            return plate;
        }
        if (plate.length() == 2) {
            return plate.charAt(0) + "*";
        }
        return plate.substring(0, 2) + "*".repeat(plate.length() - 3) + plate.charAt(plate.length() - 1);
    }

    /**
     * Jackson 模块，自动发现 {@link Mask @Mask} 注解的 {@code String} 字段并注册对应的脱敏序列化器。
     *
     * <p>
     * 使用方式：
     *
     * <pre>{@code
     * ObjectMapper mapper = new ObjectMapper();
     * mapper.registerModule(new MaskSerializer.MaskModule());
     * }</pre>
     */
    public static class MaskModule extends SimpleModule {

        public MaskModule() {
            setSerializerModifier(new BeanSerializerModifier() {
                @Override
                @SuppressWarnings("unchecked")
                public List<BeanPropertyWriter> changeProperties(
                    com.fasterxml.jackson.databind.SerializationConfig config,
                    com.fasterxml.jackson.databind.BeanDescription beanDesc, List<BeanPropertyWriter> beanProperties) {
                    for (BeanPropertyWriter writer : beanProperties) {
                        Mask mask = writer.getAnnotation(Mask.class);
                        if (mask != null && writer.getType().getRawClass() == String.class) {
                            JsonSerializer<?> ser = new MaskSerializer(mask.type());
                            writer.assignSerializer((JsonSerializer<Object>)ser);
                        }
                    }
                    return beanProperties;
                }
            });
        }
    }
}
