
/*
 * Copyright (C) 2011 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.string;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Encodes/decodes Java strings, escaping control and XML-invalid characters.
 */
public final class StringEncoder {

    /**
     * A regular expression that matches exactly the set of valid {@link #encode encode()}'d strings.
     */
    public static final Pattern ENCODE_PATTERN
      = Pattern.compile("([\\t\\r\\n\\x20-\\x5b\\x5d-\\ud7ff]|[\\ue000-\\ufffd]"
        + "|\\\\([\\\\bftrn]|u[\\p{XDigit}]{4}))*");

    /**
     * A regular expression that matches exactly the set of valid {@link #enquote enquote()}'d strings.
     */
    public static final Pattern ENQUOTE_PATTERN
      = Pattern.compile("\"([\\t\\r\\n\\x20\\x21\\x23-\\x5b\\x5d-\\ud7ff]|[\\ue000-\\ufffd]"
        + "|\\\\([\\\\bftrn\"]|u[\\p{XDigit}]{4}))*\"");

    private static final char[] HEXDIGITS = "0123456789abcdef".toCharArray();

    private StringEncoder() {
    }

    /**
     * Encode a string, escaping control and XML-invalid characters.
     * Whether tab, newline, and carriage return are escaped is optional;
     * these are the three control characters that are valid inside XML documents.
     *
     * <p>
     * Characters are escaped using <code>&#92;uNNNN</code> notation like Java unicode characters,
     * e.g., <code>0x001f</code> would appear in the encoded string as <code>&#92;u001f</code>.
     * Normal Java backslash escapes are used for tab, newline, carriage return, backspace, and formfeed.
     * Backslash characters are themselves encoded with a double backslash.
     *
     * @param value         string to encode (possibly null)
     * @param escapeTABNLCR escape tab, newline, and carriage return characters as well
     * @return the encoded version of {@code value}, or {@code null} if {@code value} was {@code null}
     * @see #decode
     * @see #isValidXMLChar
     */
    public static String encode(String value, boolean escapeTABNLCR) {
        if (value == null)
            return value;
        StringBuilder buf = new StringBuilder(value.length() + 4);
        final int limit = value.length();
        for (int i = 0; i < limit; i++) {
            final char ch = value.charAt(i);

            // Handle special escapes
            switch (ch) {
            case '\\':
                buf.append('\\').append('\\');
                continue;
            case '\b':
                buf.append('\\').append('b');
                continue;
            case '\f':
                buf.append('\\').append('f');
                continue;
            case '\t':
                if (escapeTABNLCR) {
                    buf.append('\\').append('t');
                    continue;
                }
                break;
            case '\n':
                if (escapeTABNLCR) {
                    buf.append('\\').append('n');
                    continue;
                }
                break;
            case '\r':
                if (escapeTABNLCR) {
                    buf.append('\\').append('r');
                    continue;
                }
                break;
            default:
                break;
            }

            // If character is an otherwise valid XML character, pass it through unchanged
            if (isValidXMLChar(ch)) {
                buf.append(ch);
                continue;
            }

            // Escape it using 4 digit hex
            buf.append('\\');
            buf.append('u');
            for (int shift = 12; shift >= 0; shift -= 4)
                buf.append(HEXDIGITS[(ch >> shift) & 0x0f]);
        }
        return buf.toString();
    }

    /**
     * Decode a string encoded by {@link #encode}.
     *
     * <p>
     * The parsing is strict; any ill-formed backslash escape sequence (i.e., not of the form
     * <code>&#92;uNNNN</code>, <code>\b</code>, <code>\t</code>, <code>\n</code>, <code>\f</code>, <code>\r</code>
     * or <code>\\</code>) will cause an exception to be thrown.
     *
     * @param text string to decode (possibly null)
     * @return the decoded version of {@code text}, or {@code null} if {@code text} was {@code null}
     * @throws IllegalArgumentException if {@code text} contains an invalid escape sequence
     * @see #encode
     */
    public static String decode(String text) {
        if (text == null)
            return null;
        StringBuilder buf = new StringBuilder(text.length());
        final int limit = text.length();
        for (int i = 0; i < limit; i++) {
            char ch = text.charAt(i);

            // Handle unescaped characters
            if (ch != '\\') {
                if (!isValidXMLChar(ch))
                    throw new IllegalArgumentException(String.format("illegal character 0x%04x in encoded string", ch));
                buf.append(ch);
                continue;
            }

            // Get next char
            if (++i >= limit)
                throw new IllegalArgumentException("illegal trailing '\\' in encoded string");
            ch = text.charAt(i);

            // Check for special escapes
            switch (ch) {
            case '\\':
                buf.append('\\');
                continue;
            case 'b':
                buf.append('\b');
                continue;
            case 't':
                buf.append('\t');
                continue;
            case 'n':
                buf.append('\n');
                continue;
            case 'f':
                buf.append('\f');
                continue;
            case 'r':
                buf.append('\r');
                continue;
            default:
                break;
            }

            // Must be unicode escape
            if (ch != 'u')
                throw new IllegalArgumentException("illegal escape sequence '\\" + ch + "' in encoded string");

            // Decode hex value
            if (i + 4 >= limit)
                throw new IllegalArgumentException("illegal truncated '\\u' escape sequence in encoded string");
            int value = 0;
            for (int j = 0; j < 4; j++) {
                int nibble = Character.digit(text.charAt(++i), 16);
                if (nibble == -1) {
                    throw new IllegalArgumentException(
                      "illegal escape sequence '" + text.substring(i - j - 2, i - j + 4) + "' in encoded string");
                }
                assert nibble >= 0 && nibble <= 0xf;
                value = (value << 4) | nibble;
            }

            // Append decodec character
            buf.append((char)value);
        }
        return buf.toString();
    }

    /**
     * Enquote a string. Functions like {@link #encode encode(string, true)} but in addition the resulting
     * string is surrounded by double quotes and double quotes in the string are backslash-escaped.
     *
     * <p>
     * Note: the strings returned by this method are not suitable for decoding by {@link #decode}.
     * Use {@link #dequote} instead.
     *
     * @param string string to enquote
     * @return enquoted string, or the string {@code null} (not quoted) if {@code string} is null
     * @throws IllegalArgumentException if {@code string} is null
     */
    public static String enquote(String string) {
        if (string == null)
            return "null";
        return '"' + StringEncoder.encode(string, true).replaceAll(Pattern.quote("\""), Matcher.quoteReplacement("\\\"")) + '"';
    }

    /**
     * Enquote bytes, treating them as an ASCII string.
     *
     * @param data ascii character buffer
     * @param off starting offset in {@code data}
     * @param len number of characters in {@code data}
     * @return enquoted string
     * @throws IllegalArgumentException if {@code data} is null
     * @throws IndexOutOfBoundsException if {@code off} and/or {@code len} is invalid
     * @see #enquote(String)
     */
    public static String enquote(byte[] data, int off, int len) {
        if (data == null)
            throw new IllegalArgumentException("null data");
        if (off < 0 || len < 0 || off + len < 0 || off + len > data.length)
            throw new IndexOutOfBoundsException("invalid off/len");
        char[] chars = new char[len];
        for (int i = 0; i < len; i++)
            chars[i] = (char)(data[off + i] & 0xff);
        return enquote(new String(chars));
    }

    /**
     * Dequote a string previously enquoted by {@link #enquote}.
     *
     * @param quotedString a string returned by {@link #enquote}
     * @return original unquoted string
     * @throws IllegalArgumentException if {@code quotedString} has an invalid format (i.e., it could not have
     *  ever been returned by {@link #enquote})
     */
    public static String dequote(String quotedString) {
        int len = quotedString.length();
        if (len < 2 || quotedString.charAt(0) != '"' || quotedString.charAt(len - 1) != '"')
            throw new IllegalArgumentException("invalid quoted string: not surrounded by quote characters");
        quotedString = quotedString.substring(1, len - 1);
        if (quotedString.matches("(?s)^(\"|.*[^\\\\]\").*$"))
            throw new IllegalArgumentException("invalid quoted string: unescaped nested quote character");
        quotedString = quotedString.replaceAll(Pattern.quote("\\\""), Matcher.quoteReplacement("\""));
        return decode(quotedString);
    }

    /**
     * Determine the length of a string previously enquoted by {@link #enquote} when it appears
     * as the prefix of a longer string. This method assumes that the prefix is a valid quoted
     * string; use {@link #dequote} to verify.
     *
     * @param string a string containing a prefix returned by {@link #enquote}
     * @return length of {@code string} when enquoted
     * @throws IllegalArgumentException if a starting or terminating quote character is not found
     */
    public static int enquotedLength(String string) {
        int len = string.length();
        if (len == 0 || string.charAt(0) != '"')
            throw new IllegalArgumentException("invalid quoted string prefix: string does not begin with a quote character");
        for (int i = 1; i < len; i++) {
            if (string.charAt(i) == '"' && string.charAt(i - 1) != '\\')
                return i + 1;
        }
        throw new IllegalArgumentException("invalid quoted string prefix: no terminating quote character found");
    }

    /**
     * Determine if the given character is a valid XML character according to the XML 1.0 specification.
     *
     * <p>
     * Valid characters are tab, newline, carriage return, and characters in the ranges
     * <code>&#92;u0020 - &#92;ud7ff</code> and <code>&#92;ue000 - &#92;fffdf</code> (inclusive).
     *
     * @param ch character to test
     * @return true if {@code ch} is valid within XML
     * @see <a href="http://www.w3.org/TR/REC-xml/#charsets">The XML 1.0 Specification</a>
     */
    public static boolean isValidXMLChar(char ch) {
        return (ch >= '\u0020' && ch <= '\ud7ff') || ch == '\n' || ch == '\r' || ch == '\t' || (ch >= '\ue000' && ch <= '\ufffd');
    }
}

