
/*
 * Copyright (C) 2022 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.jibx;

import java.net.Inet4Address;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.TimeZone;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.dellroad.stuff.java.IdGenerator;
import org.dellroad.stuff.net.IPv4Util;
import org.dellroad.stuff.string.ByteArrayEncoder;
import org.dellroad.stuff.string.DateEncoder;
import org.dellroad.stuff.string.StringEncoder;
import org.jibx.runtime.JiBXParseException;

/**
 * JiBX parsing utility methods. These methods can be used as JiBX value serializer/deserializer methods.
 */
public final class ParseUtil {

    private static final String[] BOOLEAN_TRUES = { "1", "true", "yes" };
    private static final String[] BOOLEAN_FALSES = { "0", "false", "no" };
    private static final String XSD_DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ssZ";

    private static final String TIME_INTERVAL_PATTERN
      = "(([0-9]+)d)?(([0-9]+)h)?(([0-9]+)m)?((([0-9]+(\\.[0-9]+)?)|(\\.[0-9]+))s)?";

    private ParseUtil() {
    }

    /**
     * Deserialize a {@link String}, allowing arbitrary characters via backslash escapes.
     * The string will be decoded by {@link StringEncoder#decode}.
     *
     * @param string XML encoding created by {@link #serializeString}
     * @return decoded value
     * @throws JiBXParseException if the parse fails
     * @see StringEncoder#decode
     */
    public static String deserializeString(String string) throws JiBXParseException {
        try {
            return StringEncoder.decode(string);
        } catch (IllegalArgumentException e) {
            throw new JiBXParseException("invalid encoded string", string, e);
        }
    }

    /**
     * Serialize a {@link String}.
     * The string will be encoded by {@link StringEncoder#encode}.
     *
     * @param string value to encode for XML
     * @return encoded value
     * @see StringEncoder#encode
     */
    public static String serializeString(String string) {
        return StringEncoder.encode(string, false);
    }

    /**
     * Deserialize a {@link TimeZone}.
     *
     * @param string XML encoding created by {@link #serializeTimeZone}
     * @return decoded value
     * @throws JiBXParseException if the parse fails
     * @see #serializeTimeZone
     */
    public static TimeZone deserializeTimeZone(String string) throws JiBXParseException {
        if (!new HashSet<>(Arrays.asList(TimeZone.getAvailableIDs())).contains(string))
            throw new JiBXParseException("unrecognized time zone", string);
        return TimeZone.getTimeZone(string);
    }

    /**
     * Serialize a {@link TimeZone}.
     *
     * @param timeZone value to encode for XML
     * @return encoded value
     * @see #deserializeTimeZone
     */
    public static String serializeTimeZone(TimeZone timeZone) {
        return timeZone.getID();
    }

    /**
     * Deserialize an {@link URI}.
     *
     * <p>
     * Note: there is no need for a custom serializer, as {@link URI#toString} already does the right thing.
     *
     * @param string XML encoding created by {@link URI#toString}
     * @return decoded value
     * @throws JiBXParseException if the parse fails
     */
    public static URI deserializeURI(String string) throws JiBXParseException {
        try {
            return new URI(string);
        } catch (URISyntaxException e) {
            throw new JiBXParseException("invalid URI", string, e);
        }
    }

    /**
     * Deserialize an object by reference.
     *
     * <p>
     * Invoke this method from your own custom deserializer to produce an result of the correct type.
     *
     * <p>
     * The object must have been unmarshalled already and had its ID registered via {@link IdMapper#setId}.
     *
     * @param <T> expected type
     * @param string XML encoding created by {@link #serializeReference}
     * @param type expected type
     * @return decoded value
     * @throws IllegalArgumentException if {@code string} cannot be parsed
     * @throws JiBXParseException if the reference has not been registered to any object yet (e.g., forward reference)
     * @throws JiBXParseException if the referenced object is not an instance of {@code type}
     * @see #serializeReference
     * @see IdMapper
     */
    public static <T> T deserializeReference(String string, Class<T> type) throws JiBXParseException {
        if (string == null)
            return null;
        long id;
        try {
            id = IdMapper.parseId(string);
        } catch (IllegalArgumentException e) {
            throw new JiBXParseException("invalid object reference", string, e);
        }
        Object obj = IdGenerator.get().getObject(id);
        if (obj == null)
            throw new JiBXParseException("unregistered object reference `" + string + "'; possible forward reference", string);
        try {
            return type.cast(obj);
        } catch (ClassCastException e) {
            throw new JiBXParseException("object reference `" + string + "' is assigned to an instance of "
              + obj.getClass() + " which is not assignable to " + type, string);
        }
    }

    /**
     * Serialize an object by reference.
     *
     * <p>
     * The object must have been marshalled already and had its ID assigned via {@link IdMapper#getId}.
     *
     * @param obj object to encode by reference for XML
     * @return encoded reference
     * @throws IllegalArgumentException if the object has not been registered yet (e.g., forward reference)
     * @see #deserializeReference
     * @see IdMapper
     */
    public static String serializeReference(Object obj) {
        if (obj == null)
            return null;
        long id = IdGenerator.get().checkId(obj);
        if (id == 0)
            throw new IllegalArgumentException("unregistered object; possible forward reference to " + obj);
        return IdMapper.formatId(id);
    }

    /**
     * Deserialize a {@link UUID}.
     *
     * <p>
     * Note: there is no need for a custom serializer, as {@link UUID#toString} already does the right thing.
     *
     * @param string XML encoding created by {@link UUID#toString}
     * @return decoded value
     * @throws JiBXParseException if the parse fails
     */
    public static UUID deserializeUUID(String string) throws JiBXParseException {
        try {
            return UUID.fromString(string);
        } catch (IllegalArgumentException e) {
            throw new JiBXParseException("invalid UUID", string, e);
        }
    }

    /**
     * Derialize a millisecond-granularity time interval, e.g., "30s", "1d12h", "0.250s", etc.
     *
     * @param string XML encoding created by {@link #serializeTimeInterval}
     * @return decoded value
     * @throws JiBXParseException if the parse fails
     * @see #serializeTimeInterval
     */
    public static long deserializeTimeInterval(String string) throws JiBXParseException {

        // Apply regular expression
        Matcher m = Pattern.compile(TIME_INTERVAL_PATTERN).matcher(string);
        if (string.length() == 0 || !m.matches())
            throw new JiBXParseException("invalid time interval", string);

        // Parse regex groups
        Object[][] groups = new Object[][] {
            { 2, 24 * 60 * 60 * 1000 },
            { 4,      60 * 60 * 1000 },
            { 6,           60 * 1000 },
            { 8,                1000 },
        };
        long value = 0;
        for (int i = 0; i < groups.length; i++) {
            String group = m.group((Integer)groups[i][0]);
            if (group == null || group.length() == 0)
                continue;
            double num;
            try {
                num = Double.parseDouble(group);
            } catch (NumberFormatException e) {
                throw new JiBXParseException("invalid time interval", string);
            }
            value += Math.round(num * (Integer)groups[i][1]);
            if (value < 0)
                throw new JiBXParseException("time interval is too large", string);
        }

        // Done
        return value;
    }

    /**
     * Serialize a millisecond-granularity time interval, e.g., "30s", "1d12h", "0.250s", etc.
     *
     * @param value millisecond value to encode
     * @return encoded value
     * @see #deserializeTimeInterval
     */
    public static String serializeTimeInterval(long value) {
        if (value < 0)
            throw new IllegalArgumentException("negative value");
        StringBuilder b = new StringBuilder(32);
        long days = value / (24 * 60 * 60 * 1000);
        value = value % (24 * 60 * 60 * 1000);
        if (days > 0)
            b.append(days).append('d');
        long hours = value / (60 * 60 * 1000);
        value = value % (60 * 60 * 1000);
        if (hours > 0)
            b.append(hours).append('h');
        long minutes = value / (60 * 1000);
        value = value % (60 * 1000);
        if (minutes > 0)
            b.append(minutes).append('m');
        long millis = value;
        if (millis != 0 || b.length() == 0) {
            if (millis % 1000 == 0)
                b.append(String.format("%ds", millis / 1000));
            else
                b.append(String.format("%.3fs", millis / 1000.0));
        }
        return b.toString();
    }

    /**
     * Deserialize a byte array using {@link ByteArrayEncoder}.
     *
     * @param string XML encoding created by {@link #serializeByteArray}
     * @return decoded value
     * @throws JiBXParseException if the parse fails
     * @see ByteArrayEncoder
     */
    public static byte[] deserializeByteArray(String string) throws JiBXParseException {
        try {
            return ByteArrayEncoder.decode(string);
        } catch (IllegalArgumentException e) {
            throw new JiBXParseException("invalid byte array", string, e);
        }
    }

    /**
     * Serialize a byte array using {@link ByteArrayEncoder}.
     *
     * @param array array to encode
     * @return encoded array
     * @see ByteArrayEncoder
     */
    public static String serializeByteArray(byte[] array) {
        return ByteArrayEncoder.encode(array);
    }

    /**
     * Deserialize a byte array using MAC address notation (colon-separated). Each byte must have two digits.
     *
     * @param string XML encoding created by {@link #serializeByteArrayWithColons}
     * @return decoded value
     * @throws JiBXParseException if the parse fails
     * @see #serializeByteArrayWithColons
     */
    public static byte[] deserializeByteArrayWithColons(String string) throws JiBXParseException {
        if (string.length() == 0)
            return new byte[0];
        if (string.length() % 3 != 2)
            throw new JiBXParseException("invalid byte array", string);
        char[] nocolons = new char[((string.length() + 1) / 3) * 2];
        int j = 0;
        for (int i = 0; i < string.length(); i++) {
            if (i % 3 == 2) {
                if (string.charAt(i) != ':')
                    throw new JiBXParseException("invalid byte array", string);
                continue;
            }
            nocolons[j++] = string.charAt(i);
        }
        try {
            return ByteArrayEncoder.decode(new String(nocolons));
        } catch (IllegalArgumentException e) {
            throw new JiBXParseException("invalid byte array", string, e);
        }
    }

    /**
     * Serialize a byte array using MAC address notation (colon-separated). Each byte will have two digits.
     *
     * @param array array to encode
     * @return encoded array
     * @see #deserializeByteArrayWithColons
     */
    public static String serializeByteArrayWithColons(byte[] array) {
        char[] colons = new char[array.length * 3 - 1];
        String nocolons = ByteArrayEncoder.encode(array);
        int j = 0;
        for (int i = 0; i < nocolons.length(); i += 2) {
            colons[j++] = nocolons.charAt(i);
            colons[j++] = nocolons.charAt(i + 1);
            if (j < colons.length)
                colons[j++] = ':';
        }
        return new String(colons);
    }

    /**
     * Deserialize an {@link Inet4Address}. No DNS name resolution of any kind is performed.
     *
     * @param string XML encoding created by {@link #serializeInet4Address}
     * @return decoded value
     * @throws JiBXParseException if the parse fails
     * @see #serializeInet4Address
     */
    public static Inet4Address deserializeInet4Address(String string) throws JiBXParseException {
        try {
            return IPv4Util.fromString(string);
        } catch (IllegalArgumentException e) {
            throw new JiBXParseException("invalid IPv4 address", string);
        }
    }

    /**
     * Serialize an {@link Inet4Address}.
     *
     * @param addr address to encode
     * @return encoded address
     * @see #deserializeInet4Address
     */
    public static String serializeInet4Address(Inet4Address addr) {
        return IPv4Util.toString(addr);
    }

    /**
     * Deserialize a {@link SimpleDateFormat}.
     *
     * @param string XML encoding created by {@link #serializeSimpleDateFormat}
     * @return decoded value
     * @throws JiBXParseException if the parse fails
     * @see #serializeSimpleDateFormat
     */
    public static SimpleDateFormat deserializeSimpleDateFormat(String string) throws JiBXParseException {
        try {
            return new SimpleDateFormat(string);
        } catch (IllegalArgumentException e) {
            throw new JiBXParseException("invalid date format", string, e);
        }
    }

    /**
     * Serialize a {@link SimpleDateFormat}.
     *
     * @param format date format to encode
     * @return encoded format
     * @see #deserializeSimpleDateFormat
     */
    public static String serializeSimpleDateFormat(SimpleDateFormat format) {
        return format.toPattern();
    }

    /**
     * Deserialize a {@link Date}. This method can be used as a deserialize support method.
     *
     * @param string XML encoding created by {@link #serializeDate(Date, String)}
     * @param format format for {@link SimpleDateFormat}
     * @return decoded value
     * @throws JiBXParseException if the parse fails
     * @see #serializeDate
     */
    public static Date deserializeDate(String string, String format) throws JiBXParseException {
        try {
            return deserializeSimpleDateFormat(format).parse(string);
        } catch (java.text.ParseException e) {
            throw new JiBXParseException("invalid date", string, e);
        }
    }

    /**
     * Serialize a {@link Date}. This method can be used as a serialize support method.
     *
     * @param date date to serialize
     * @param format format for {@link SimpleDateFormat}.
     * @return encoded date
     * @see #deserializeDate
     * @throws JiBXParseException if {@code format} is invalid
     */
    public static String serializeDate(Date date, String format) throws JiBXParseException {
        return deserializeSimpleDateFormat(format).format(date);
    }

    /**
     * Deserialize a {@link Date} in the format supported by {@link DateEncoder}.
     *
     * @param date XML encoding created by {@link #serializeDate(Date)}
     * @return decoded value
     * @throws JiBXParseException if the parse fails
     * @see DateEncoder
     */
    public static Date deserializeDate(String date) throws JiBXParseException {
        try {
            return DateEncoder.decode(date);
        } catch (IllegalArgumentException e) {
            throw new JiBXParseException("invalid date", date, e);
        }
    }

    /**
     * Serialize a {@link Date} in the format supported by {@link DateEncoder}.
     *
     * @param date date to encode
     * @return encoded date
     * @see DateEncoder
     */
    public static String serializeDate(Date date) {
        return DateEncoder.encode(date);
    }

    /**
     * Deserialize a {@link Date} in XSD dateTime format.
     *
     * @param date XML encoding created by {@link #serializeXSDDateTime}
     * @return decoded value
     * @throws JiBXParseException if the parse fails
     * @see #serializeXSDDateTime
     * @see <a href="http://www.w3.org/TR/xmlschema-2/#dateTime">XSD dateTime datatype</a>
     */
    public static Date deserializeXSDDateTime(String date) throws JiBXParseException {
        try {
            return deserializeSimpleDateFormat(XSD_DATE_FORMAT).parse(date);
        } catch (java.text.ParseException e) {
            throw new JiBXParseException("invalid date", date, e);
        }
    }

    /**
     * Serialize a {@link Date} to XSD dateTime format.
     *
     * @param date date to encode
     * @return encoded date
     * @see #deserializeXSDDateTime
     * @see <a href="http://www.w3.org/TR/xmlschema-2/#dateTime">XSD dateTime datatype</a>
     */
    public static String serializeXSDDateTime(Date date) {
        return new SimpleDateFormat(XSD_DATE_FORMAT).format(date);
    }

    /**
     * Deserialize a {@link Pattern}.
     *
     * <p>
     * Note: there is no need for a custom serializer, as {@link Pattern#toString} already does the right thing.
     *
     * @param string XML encoding created by {@link Pattern#toString}
     * @return decoded value
     * @throws JiBXParseException if the parse fails
     */
    public static Pattern deserializePattern(String string) throws JiBXParseException {
        try {
            return Pattern.compile(string);
        } catch (PatternSyntaxException e) {
            throw new JiBXParseException("invalid regular expression", string, e);
        }
    }

    /**
     * JiBX {@link String} deserializer that normalizes a string as is required by the {@code xsd:token} XSD type.
     * This removes leading and trailing whitespace, and collapses all interior whitespace
     * down to a single space character.
     *
     * @param string string to normalize
     * @return normalized string
     * @throws NullPointerException if {@code string} is null
     */
    public static String normalize(String string) {
        return string.trim().replaceAll("\\s+", " ");
    }

    /**
     * JiBX {@link String} deserializer support method that verifies that the input string matches the
     * given regular expression. This method can be invoked by custom deserializers that supply the
     * regular expression to it.
     *
     * @param regex regular expression pattern which input must match
     * @param string input string
     * @return decoded value
     * @throws NullPointerException if {@code string} of {@code regex} is null
     * @throws JiBXParseException   if {@code string} does not match {@code regex}
     * @throws java.util.regex.PatternSyntaxException
     *                              if {@code regex} is not a valid regular expression
     */
    public static String deserializeMatching(String regex, String string) throws JiBXParseException {
        if (!string.matches(regex))
            throw new JiBXParseException("input does not match pattern \"" + regex + "\"", string);
        return string;
    }

    /**
     * Boolean parser that allows "yes" and "no" as well as the usual "true", "false", "0", "1".
     * Comparisons are case-insensitive.
     *
     * @param string XML encoding of boolean value
     * @return decoded value
     * @throws JiBXParseException if the value is not recognizable as a boolean
     * @see #deserializeBooleanStrictly
     */
    public static boolean deserializeBoolean(String string) throws JiBXParseException {
        for (String s : BOOLEAN_TRUES) {
            if (string.equalsIgnoreCase(s))
                return true;
        }
        for (String s : BOOLEAN_FALSES) {
            if (string.equalsIgnoreCase(s))
                return false;
        }
        throw new JiBXParseException("invalid Boolean value", string);
    }

    /**
     * Deserialize a boolean strictly, only allowing the values {@code true} or {@code false}.
     *
     * @param string XML encoding created by {@link Boolean#toString}
     * @return decoded value
     * @throws JiBXParseException if the parse fails
     * @see #deserializeBoolean
     */
    public static boolean deserializeBooleanStrictly(String string) throws JiBXParseException {
        if ("true".equals(string))
            return true;
        if ("false".equals(string))
            return false;
        throw new JiBXParseException("invalid boolean value; must be `true' or `false'", string);
    }

    /**
     * Deserialize an array of integers separated by commas and/or whitespace.
     *
     * @param string XML encoding created by {@link #serializeIntArray}
     * @return decoded integer array
     * @throws JiBXParseException if the parse fails
     * @see #serializeIntArray
     */
    public static int[] deserializeIntArray(String string) throws JiBXParseException {
        String[] ints = string.trim().split("(\\s*,\\s*|\\s+)");
        if (ints.length == 0 || ints[0].length() == 0)
            return new int[0];
        int[] array = new int[ints.length];
        try {
            for (int i = 0; i < array.length; i++)
                array[i] = Integer.parseInt(ints[i]);
        } catch (NumberFormatException e) {
            throw new JiBXParseException("invalid integer list", string, e);
        }
        return array;
    }

    /**
     * Serialize an array of integers. Example: "1, 2, 3".
     *
     * @param array integer array
     * @return string encoding of {@code array}
     * @see #deserializeIntArray
     */
    public static String serializeIntArray(int[] array) {
        StringBuilder buf = new StringBuilder(array.length * 2);
        for (int i = 0; i < array.length; i++) {
            if (i > 0)
                buf.append(", ");
            buf.append(array[i]);
        }
        return buf.toString();
    }
}

