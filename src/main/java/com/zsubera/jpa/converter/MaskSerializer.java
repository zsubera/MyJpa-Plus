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

public class MaskSerializer extends JsonSerializer<String> {

    private final MaskType maskType;

    public MaskSerializer() {
        this(MaskType.NAME);
    }

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

    public static String mask(String value, MaskType maskType) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        return switch (maskType) {
            case PHONE -> maskPhone(value);
            case EMAIL -> maskEmail(value);
            case ID_CARD -> maskIdCard(value);
            case NAME -> maskName(value);
        };
    }

    private static String maskPhone(String phone) {
        if (phone.length() < 7) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    private static String maskEmail(String email) {
        int atIndex = email.indexOf('@');
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
        if (idCard.length() < 8) {
            return idCard;
        }
        return idCard.substring(0, 3) + "*".repeat(idCard.length() - 7) + idCard.substring(idCard.length() - 4);
    }

    private static String maskName(String name) {
        if (name.length() <= 1) {
            return name;
        }
        if (name.length() == 2) {
            return name.charAt(0) + "*";
        }
        return name.charAt(0) + "*".repeat(name.length() - 2) + name.charAt(name.length() - 1);
    }

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
