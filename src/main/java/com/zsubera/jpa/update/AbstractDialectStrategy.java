package com.zsubera.jpa.update;

/**
 * 方言策略抽象基类，提供 {@link #escapeIdentifier(String)} 的通用实现。
 *
 * <p>
 * 子类只需实现 {@link #getQuoteChar()} 和 {@link #getEscapeSequence()} 方法，
 * 即可获得完整的标识符转义支持，避免重复的 split/append 逻辑。
 *
 * <p>
 * 支持点号分隔的复合标识符（如 {@code schema.table.column}），每个部分独立转义。
 *
 * @author myjpa-plus
 */
abstract class AbstractDialectStrategy implements DialectStrategy {

    /**
     * 返回该方言使用的引用字符（如双引号、反引号、方括号的左字符）。
     *
     * @return 引用字符
     */
    protected abstract char getQuoteChar();

    /**
     * 返回该方言使用的引用闭合字符（如双引号、反引号、方括号的右字符）。
     *
     * <p>
     * 默认返回与 {@link #getQuoteChar()} 相同的字符。方括号等非对称引用符的子类应覆盖此方法。
     *
     * @return 闭合引用字符
     */
    protected char getClosingQuoteChar() {
        return getQuoteChar();
    }

    /**
     * 返回转义引用字符的转义序列。
     *
     * <p>
     * 例如，双引号的转义序列是 {@code ""}，反引号是 {@code ``}，方括号是 {@code ]]}。
     *
     * @return 转义序列字符串
     */
    protected abstract String getEscapeSequence();

    /**
     * {@inheritDoc}
     *
     * <p>
     * 使用 {@link #getQuoteChar()} 和 {@link #getEscapeSequence()} 进行转义。
     * 支持点号分隔的复合标识符。
     */
    @Override
    public String escapeIdentifier(String identifier) {
        if (identifier == null) {
            throw new IllegalArgumentException("identifier must not be null");
        }
        char openQuote = getQuoteChar();
        char closeQuote = getClosingQuoteChar();
        String escapeSeq = getEscapeSequence();
        String closeQuoteStr = String.valueOf(closeQuote);

        if (identifier.indexOf('.') >= 0) {
            String[] parts = identifier.split("\\.");
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < parts.length; i++) {
                if (i > 0) {
                    sb.append('.');
                }
                sb.append(openQuote).append(parts[i].replace(closeQuoteStr, escapeSeq)).append(closeQuote);
            }
            return sb.toString();
        }
        return openQuote + identifier.replace(closeQuoteStr, escapeSeq) + closeQuote;
    }
}
