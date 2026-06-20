package com.zsubera.jpa.util;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class StringHelperTest {

    @Test
    void camelToSnake_nullInput() {
        assertNull(StringHelper.camelToSnake(null));
    }

    @Test
    void camelToSnake_emptyInput() {
        assertEquals("", StringHelper.camelToSnake(""));
    }

    @Test
    void camelToSnake_simpleCamelCase() {
        assertEquals("camel_case", StringHelper.camelToSnake("camelCase"));
    }

    @Test
    void camelToSnake_acronymAtEnd() {
        assertEquals("parse_xml", StringHelper.camelToSnake("parseXML"));
    }

    @Test
    void camelToSnake_acronymInMiddle() {
        assertEquals("get_html_content", StringHelper.camelToSnake("getHTMLContent"));
    }

    @Test
    void camelToSnake_allUppercaseSingleChar() {
        assertEquals("a", StringHelper.camelToSnake("A"));
    }

    @Test
    void camelToSnake_leadingUppercase() {
        assertEquals("camel_case", StringHelper.camelToSnake("CamelCase"));
    }

    @Test
    void camelToSnake_alreadyLowercase() {
        assertEquals("lowercase", StringHelper.camelToSnake("lowercase"));
    }

    @Test
    void camelToSnake_singleCharacter() {
        assertEquals("a", StringHelper.camelToSnake("a"));
    }

    @Test
    void camelToSnake_multipleTransitions() {
        assertEquals("my_camel_case_string", StringHelper.camelToSnake("myCamelCaseString"));
    }

    @Test
    void camelToSnake_consecutiveUppercaseAtStart() {
        assertEquals("xml_parser", StringHelper.camelToSnake("XMLParser"));
    }

    @Test
    void camelToSnake_allLowercase() {
        assertEquals("hello", StringHelper.camelToSnake("hello"));
    }
}
