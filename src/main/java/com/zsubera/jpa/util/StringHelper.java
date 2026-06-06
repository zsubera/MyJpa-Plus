package com.zsubera.jpa.util;

/**
 * 字符串工具类，提供常用的字符串转换方法。
 *
 * @author myjpa-plus
 * @since 1.2.0
 */
public final class StringHelper {

    private StringHelper() {
        // 工具类不可实例化
    }

    /**
     * 将驼峰命名（camelCase）转换为蛇形命名（snake_case）。
     *
     * <p>
     * 转换规则：
     * <ul>
     * <li>小写字母后跟大写字母：在大写字母前插入下划线（如 "camelCase" -> "camel_case"）</li>
     * <li>连续大写字母后跟小写字母：在最后一个大写字母前插入下划线（如 "XMLParser" -> "xml_parser"）</li>
     * <li>开头的大写字母不插入下划线（如 "CamelCase" -> "camel_case"）</li>
     * </ul>
     *
     * @param name 驼峰命名字符串
     * @return 蛇形命名字符串，如果输入为 null 或空字符串则原样返回
     * @since 1.2.0
     */
    public static String camelToSnake(String name) {
        if (name == null || name.isEmpty()) {
            return name;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (Character.isUpperCase(c)) {
                // 在大写字母前添加下划线，如果：
                // - 不在开头位置
                // - 前一个字符是小写字母（例如 "camelCase" -> "camel_case"）
                // - 下一个字符是小写字母且前一个字符是大写字母（例如 "XMLParser" -> "xml_parser"）
                if (i > 0) {
                    char prev = name.charAt(i - 1);
                    boolean nextIsLower = (i + 1 < name.length()) && Character.isLowerCase(name.charAt(i + 1));
                    if (Character.isLowerCase(prev) || (Character.isUpperCase(prev) && nextIsLower)) {
                        sb.append('_');
                    }
                }
                sb.append(Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
