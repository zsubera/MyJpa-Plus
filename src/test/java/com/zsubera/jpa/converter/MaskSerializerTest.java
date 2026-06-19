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

    @Test
    @DisplayName("PHONE mask: very short phone (<3 chars)")
    void shouldMaskVeryShortPhone() {
        assertEquals("*", MaskSerializer.mask("1", MaskType.PHONE));
        assertEquals("**", MaskSerializer.mask("12", MaskType.PHONE));
    }

    @Test
    @DisplayName("PHONE mask: medium phone (3-6 chars)")
    void shouldMaskMediumPhone() {
        assertEquals("13****", MaskSerializer.mask("138123", MaskType.PHONE));
        assertEquals("12****", MaskSerializer.mask("123456", MaskType.PHONE));
    }

    @Test
    @DisplayName("ID_CARD mask: very short ID (<4 chars)")
    void shouldMaskVeryShortIdCard() {
        assertEquals("*", MaskSerializer.mask("1", MaskType.ID_CARD));
        assertEquals("**", MaskSerializer.mask("12", MaskType.ID_CARD));
        assertEquals("***", MaskSerializer.mask("123", MaskType.ID_CARD));
    }

    @Test
    @DisplayName("ID_CARD mask: medium ID (4-7 chars)")
    void shouldMaskMediumIdCard() {
        assertEquals("1**4", MaskSerializer.mask("1234", MaskType.ID_CARD));
        assertEquals("1*****7", MaskSerializer.mask("1234567", MaskType.ID_CARD));
    }

    @Test
    @DisplayName("BANK_CARD mask: short card (<4 chars)")
    void shouldMaskShortBankCard() {
        assertEquals("*", MaskSerializer.mask("1", MaskType.BANK_CARD));
        assertEquals("**", MaskSerializer.mask("12", MaskType.BANK_CARD));
        assertEquals("***", MaskSerializer.mask("123", MaskType.BANK_CARD));
    }

    @Test
    @DisplayName("BANK_CARD mask: medium card (4-7 chars)")
    void shouldMaskMediumBankCard() {
        assertEquals("****56", MaskSerializer.mask("123456", MaskType.BANK_CARD));
        assertEquals("****5678", MaskSerializer.mask("12345678", MaskType.BANK_CARD));
    }

    @Test
    @DisplayName("BANK_CARD mask: long card (>=8 chars)")
    void shouldMaskLongBankCard() {
        assertEquals("****5678", MaskSerializer.mask("12345678", MaskType.BANK_CARD));
    }

    @Test
    @DisplayName("ADDRESS mask: short address (<=2 chars)")
    void shouldNotMaskShortAddress() {
        assertEquals("AB", MaskSerializer.mask("AB", MaskType.ADDRESS));
    }

    @Test
    @DisplayName("ADDRESS mask: medium address (3-6 chars)")
    void shouldMaskMediumAddress() {
        assertEquals("AB***", MaskSerializer.mask("ABCDE", MaskType.ADDRESS));
    }

    @Test
    @DisplayName("ADDRESS mask: long address (>6 chars)")
    void shouldMaskLongAddress() {
        assertEquals("ABCDEF*****", MaskSerializer.mask("ABCDEFGHIJK", MaskType.ADDRESS));
    }

    @Test
    @DisplayName("LICENSE_PLATE mask: single char")
    void shouldNotMaskSingleCharPlate() {
        assertEquals("A", MaskSerializer.mask("A", MaskType.LICENSE_PLATE));
    }

    @Test
    @DisplayName("LICENSE_PLATE mask: two chars")
    void shouldMaskTwoCharPlate() {
        assertEquals("A*", MaskSerializer.mask("AB", MaskType.LICENSE_PLATE));
    }

    @Test
    @DisplayName("LICENSE_PLATE mask: three chars")
    void shouldMaskThreeCharPlate() {
        assertEquals("A*C", MaskSerializer.mask("ABC", MaskType.LICENSE_PLATE));
    }

    @Test
    @DisplayName("LICENSE_PLATE mask: long plate (>=4 chars)")
    void shouldMaskLongPlate() {
        assertEquals("AB**E", MaskSerializer.mask("ABCDE", MaskType.LICENSE_PLATE));
    }

    @Test
    @DisplayName("Constructor with default mask type")
    void shouldCreateDefaultMaskSerializer() {
        MaskSerializer serializer = new MaskSerializer();
        assertNotNull(serializer);
    }

    @Test
    @DisplayName("Constructor with specific mask type")
    void shouldCreateSpecificMaskSerializer() {
        MaskSerializer serializer = new MaskSerializer(MaskType.PHONE);
        assertNotNull(serializer);
    }

    @Test
    @DisplayName("serialize method writes masked value")
    void shouldSerializeMaskedValue() throws Exception {
        MaskSerializer serializer = new MaskSerializer(MaskType.PHONE);
        java.io.StringWriter sw = new java.io.StringWriter();
        com.fasterxml.jackson.core.JsonGenerator gen = new com.fasterxml.jackson.core.JsonFactory().createGenerator(sw);
        serializer.serialize("13812345678", gen, null);
        gen.flush();
        assertTrue(sw.toString().contains("138****5678"));
    }

    @Test
    @DisplayName("serialize method writes null for null value")
    void shouldSerializeNullValue() throws Exception {
        MaskSerializer serializer = new MaskSerializer(MaskType.PHONE);
        java.io.StringWriter sw = new java.io.StringWriter();
        com.fasterxml.jackson.core.JsonGenerator gen = new com.fasterxml.jackson.core.JsonFactory().createGenerator(sw);
        serializer.serialize(null, gen, null);
        gen.flush();
        assertTrue(sw.toString().contains("null"));
    }
}
