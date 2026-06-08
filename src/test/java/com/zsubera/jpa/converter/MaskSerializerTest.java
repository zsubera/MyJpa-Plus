package com.zsubera.jpa.converter;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zsubera.jpa.annotation.Mask;
import com.zsubera.jpa.annotation.MaskType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MaskSerializerTest {

    @Test
    @DisplayName("PHONE mask: 11-digit phone")
    void shouldMaskPhone() {
        assertEquals("138****5678", MaskSerializer.mask("13812345678", MaskType.PHONE));
    }

    @Test
    @DisplayName("PHONE mask: short phone returns partial mask")
    void shouldNotMaskShortPhone() {
        assertEquals("12***", MaskSerializer.mask("12345", MaskType.PHONE));
    }

    @Test
    @DisplayName("EMAIL mask: standard email")
    void shouldMaskEmail() {
        assertEquals("t***@example.com", MaskSerializer.mask("test@example.com", MaskType.EMAIL));
    }

    @Test
    @DisplayName("EMAIL mask: single char local part")
    void shouldMaskEmailWithShortLocal() {
        assertEquals("***@x.com", MaskSerializer.mask("a@x.com", MaskType.EMAIL));
    }

    @Test
    @DisplayName("EMAIL mask: no @ returns as-is")
    void shouldNotMaskInvalidEmail() {
        assertEquals("invalid", MaskSerializer.mask("invalid", MaskType.EMAIL));
    }

    @Test
    @DisplayName("ID_CARD mask: 18-digit ID")
    void shouldMaskIdCard() {
        assertEquals("110***********1234", MaskSerializer.mask("110101199001011234", MaskType.ID_CARD));
    }

    @Test
    @DisplayName("ID_CARD mask: short value returns partial mask")
    void shouldNotMaskShortIdCard() {
        assertEquals("1*****7", MaskSerializer.mask("1234567", MaskType.ID_CARD));
    }

    @Test
    @DisplayName("NAME mask: three-char name")
    void shouldMaskThreeCharName() {
        String result = MaskSerializer.mask("\u5f20\u4e09\u4e30", MaskType.NAME);
        assertEquals(3, result.length());
        assertEquals('\u5f20', result.charAt(0));
        assertEquals('*', result.charAt(1));
        assertEquals('\u4e30', result.charAt(2));
    }

    @Test
    @DisplayName("NAME mask: two-char name")
    void shouldMaskTwoCharName() {
        String result = MaskSerializer.mask("\u5f20\u4e09", MaskType.NAME);
        assertEquals(2, result.length());
        assertEquals('\u5f20', result.charAt(0));
        assertEquals('*', result.charAt(1));
    }

    @Test
    @DisplayName("NAME mask: single-char name returns as-is")
    void shouldNotMaskSingleCharName() {
        assertEquals("\u5f20", MaskSerializer.mask("\u5f20", MaskType.NAME));
    }

    @Test
    @DisplayName("null returns null")
    void shouldReturnNullForNull() {
        assertNull(MaskSerializer.mask(null, MaskType.PHONE));
    }

    @Test
    @DisplayName("empty string returns empty")
    void shouldReturnEmptyForEmpty() {
        assertEquals("", MaskSerializer.mask("", MaskType.PHONE));
    }

    @Test
    @DisplayName("MaskModule auto-masks @Mask fields")
    void shouldAutoMaskWithModule() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new MaskSerializer.MaskModule());

        SampleDto dto = new SampleDto();
        dto.phone = "13812345678";
        dto.email = "test@example.com";
        dto.name = "\u5f20\u4e09\u4e30";
        dto.idCard = "110101199001011234";
        dto.normal = "visible";

        String json = mapper.writeValueAsString(dto);
        assertTrue(json.contains("138****5678"));
        assertTrue(json.contains("t***@example.com"));
        assertTrue(json.contains("\u5f20*\u4e30"));
        assertTrue(json.contains("110***********1234"));
        assertTrue(json.contains("visible"));
    }

    @Test
    @DisplayName("MaskModule writes null for null fields")
    void shouldWriteNullForNullFields() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new MaskSerializer.MaskModule());

        SampleDto dto = new SampleDto();
        dto.phone = null;

        String json = mapper.writeValueAsString(dto);
        assertTrue(json.contains("\"phone\":null"));
    }

    static class SampleDto {
        @Mask(type = MaskType.PHONE)
        public String phone;

        @Mask(type = MaskType.EMAIL)
        public String email;

        @Mask(type = MaskType.NAME)
        public String name;

        @Mask(type = MaskType.ID_CARD)
        public String idCard;

        public String normal;
    }
}
