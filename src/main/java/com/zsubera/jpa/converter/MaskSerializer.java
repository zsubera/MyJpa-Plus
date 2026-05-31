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
        if (idCard.length() < 4) {
            return "*".repeat(idCard.length());
        }
        if (idCard.length() < 8) {
            return idCard.substring(0, 1) + "*".repeat(idCard.length() - 2) + idCard.substring(idCard.length() - 1);
        }
        return idCard.substring(0, 3) + "*".repeat(idCard.length() - 7) + idCard.substring(idCard.length() - 4);
    }

    private static String maskName(String name) {
        int codePointCount = name.codePointCount(0, name.length());
        if (codePointCount <= 1) {
            return name;
        }
        if (codePointCount == 2) {
            return new String(Character.toChars(name.codePointAt(0))) + "*";
        }
        int lastCodePointIndex = name.offsetByCodePoints(0, codePointCount - 1);
        return new String(Character.toChars(name.codePointAt(0))) + "*".repeat(codePointCount - 2)
            + new String(Character.toChars(name.codePointAt(lastCodePointIndex)));
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
        if (address.length() <= 6) {
            return address;
        }
        return address.substring(0, 6) + "*".repeat(address.length() - 6);
    }

    private static String maskLicensePlate(String plate) {
        if (plate.length() < 3) {
            return plate;
        }
        return plate.substring(0, 2) + "*".repeat(plate.length() - 3) + plate.charAt(plate.length() - 1);
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
