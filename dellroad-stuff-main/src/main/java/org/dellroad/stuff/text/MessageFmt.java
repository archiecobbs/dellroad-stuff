
/*
 * Copyright (C) 2011 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.text;

import java.lang.reflect.Field;
import java.text.ChoiceFormat;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.Format;
import java.text.MessageFormat;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * POJO representing a {@link MessageFormat} that exposes its structure. Supports arbitrary recursive nesting
 * of {@link MessageFormat} with {@link ChoiceFormat}.
 *
 * <p>
 * This makes it easier to introspect, bypass nested quoting confusion, (de)serialize instances to/from XML, etc.
 */
public class MessageFmt {

    private static final Field FORMATS_FIELD;
    private static final Field OFFSETS_FIELD;
    private static final Field PATTERN_FIELD;
    private static final Field MAX_OFFSET_FIELD;
    private static final Field ARGUMENT_NUMBERS_FIELD;

    static {
        try {
            FORMATS_FIELD = MessageFormat.class.getDeclaredField("formats");
            OFFSETS_FIELD = MessageFormat.class.getDeclaredField("offsets");
            PATTERN_FIELD = MessageFormat.class.getDeclaredField("pattern");
            MAX_OFFSET_FIELD = MessageFormat.class.getDeclaredField("maxOffset");
            ARGUMENT_NUMBERS_FIELD = MessageFormat.class.getDeclaredField("argumentNumbers");
        } catch (NoSuchFieldException e) {
            throw new RuntimeException("internal error", e);
        }
        OFFSETS_FIELD.setAccessible(true);
        FORMATS_FIELD.setAccessible(true);
        PATTERN_FIELD.setAccessible(true);
        MAX_OFFSET_FIELD.setAccessible(true);
        ARGUMENT_NUMBERS_FIELD.setAccessible(true);
    }

    private Locale locale = Locale.getDefault(Locale.Category.FORMAT);
    private List<Segment> segments = new ArrayList<>();

// Constructors

    public MessageFmt() {
    }

    public MessageFmt(MessageFormat format) {

        // Sanity check
        if (format == null)
            throw new IllegalArgumentException("null format");

        // Introspect
        final Format[] formats;
        final int[] offsets;
        final String pattern;
        final int maxOffset;
        final int[] argumentNumbers;
        try {
            formats = (Format[])FORMATS_FIELD.get(format);
            offsets = (int[])OFFSETS_FIELD.get(format);
            pattern = (String)PATTERN_FIELD.get(format);
            maxOffset = (Integer)MAX_OFFSET_FIELD.get(format);
            argumentNumbers = (int[])ARGUMENT_NUMBERS_FIELD.get(format);
        } catch (Exception e) {
            throw new RuntimeException("internal error", e);
        }

        // Get locale
        this.locale = format.getLocale();

        // Extract segments
        int prevOffset = 0;
        for (int i = 0; i <= maxOffset; i++) {

            // Add intervening TextSegment if needed
            final int nextOffset = offsets[i];
            if (nextOffset > prevOffset) {
                this.segments.add(new TextSegment(pattern.substring(prevOffset, nextOffset)));
                prevOffset = nextOffset;
            }

            // Add next ArgumentSegment
            final int argumentNumber = argumentNumbers[i];
            this.segments.add(formats[i] != null ?
              FormatArgumentSegment.of(formats[i], argumentNumber, this.locale) : new StringArgumentSegment(argumentNumber));
        }

        // Add final TextSegment if needed
        if (prevOffset < pattern.length())
            this.segments.add(new TextSegment(pattern.substring(prevOffset)));
    }

// Properties

    /**
     * Get the locale associated with this message format.
     *
     * @return associated locale
     */
    public Locale getLocale() {
        return this.locale;
    }
    public void setLocale(final Locale locale) {
        this.locale = locale;
    }

    /**
     * Get the individual components of this message format.
     *
     * @return message format segments
     */
    public List<Segment> getSegments() {
        return this.segments;
    }
    public void setSegments(final List<Segment> segments) {
        this.segments = segments;
    }

// Methods

    /**
     * Build the {@link MessageFormat} represented by this instance.
     *
     * @return an equivalent {@link MessageFormat}
     */
    public MessageFormat toMessageFormat() {
        return new MessageFormat(this.toPattern(), this.locale);
    }

    /**
     * Build the {@link MessageFormat} pattern string represented by this instance.
     *
     * @return an equivalent {@link MessageFormat} pattern string
     */
    public String toPattern() {
        return this.segments.stream()
          .map(Segment::toPattern)
          .collect(Collectors.joining());
    }

// Object

    @Override
    public String toString() {
        return this.getClass().getSimpleName()
          + "[locale=" + this.locale
          + ",segments=" + this.segments
          + "]";
    }

// Segment

    /**
     * Represents one atomic portion of a {@link MessageFormat} pattern string.
     *
     * @see MessageFmt#getSegments
     */
    public abstract static class Segment {

        /**
         * Encode this segment as {@link MessageFormat} pattern text.
         *
         * <p>
         * The concatenation of the encoding of the {@link Segment}s in a {@link MessageFmt} produces the
         * corresponding {@link MessageFormat} pattern string.
         *
         * @return {@link MessageFormat} pattern string text
         */
        public abstract String toPattern();
    }

// TextSegment

    /**
     * Represents a stretch of plain text in a {@link MessageFormat} pattern string.
     */
    public static class TextSegment extends Segment {

        private String string;

    // Constructors

        public TextSegment() {
        }

        public TextSegment(String string) {
            this.string = string;
        }

    // Properties

        public String getString() {
            return this.string;
        }
        public void setString(final String string) {
            this.string = string;
        }

    // MessageFormat.Segment

        @Override
        public String toPattern() {
            return this.string
              .replaceAll("'", "''")                // escape single quotes
              .replaceAll("\\{+", "'$1'");          // escape runs of opening curly braces
        }

    // Object

        @Override
        public String toString() {
            return this.getClass().getSimpleName()
              + "[string=\"" + this.string + "\""
              + "]";
        }
    }

// ArgumentSegment

    /**
     * A {@link Segment} within a {@link MessageFormat} pattern that formats one of the arguments.
     */
    public abstract static class ArgumentSegment extends Segment {

        private int argumentNumber;

        protected ArgumentSegment() {
        }

        protected ArgumentSegment(int argumentNumber) {
            this.argumentNumber = argumentNumber;
        }

        /**
         * Get the index of the argument formatted by this segment.
         *
         * @return argument to format, zero-based
         */
        public int getArgumentNumber() {
            return this.argumentNumber;
        }
        public void setArgumentNumber(final int argumentNumber) {
            this.argumentNumber = argumentNumber;
        }

        @Override
        public final String toPattern() {
            final StringBuilder buf = new StringBuilder();
            buf.append('{').append(this.argumentNumber);
            final String suffix = this.getArgumentSuffix();
            if (suffix != null)
                buf.append(',').append(suffix);
            buf.append('}');
            return buf.toString();
        }

        /**
         * Get the {@link MessageFormat} pattern string argument suffix, if any.
         *
         * <p>
         * If present, this suffix follows the argument number and a comma, and precedes the closing curly brace.
         *
         * <p>
         * Used by {@link #toPattern}.
         *
         * @return pattern string argument suffix, or null if none needed
         */
        protected abstract String getArgumentSuffix();

    // Object

        @Override
        public String toString() {
            return this.getClass().getSimpleName()
              + "[argumentNumber=" + this.argumentNumber
              + ",pattern=\"" + this.toPattern() + "\""
              + "]";
        }
    }

// StringArgumentSegment

    /**
     * An {@link ArgumentSegment} that simply formats the argument with its {@link String} value.
     */
    public static class StringArgumentSegment extends ArgumentSegment {

        public StringArgumentSegment() {
        }

        public StringArgumentSegment(int argumentNumber) {
            super(argumentNumber);
        }

        @Override
        protected String getArgumentSuffix() {
            return null;
        }
    }

// FormatArgumentSegment

    /**
     * An {@link ArgumentSegment} that formats its argument using a {@link Format} of some kind.
     *
     * <p>
     * Use {@link FormatArgumentSegment#of FormatArgumentSegment.of()} to produce a concrete instance.
     *
     * @param <T> type of {@link Format} used to format the argument
     */
    public abstract static class FormatArgumentSegment<T extends Format> extends ArgumentSegment {

        protected FormatArgumentSegment() {
        }

        protected FormatArgumentSegment(int argumentNumber) {
            super(argumentNumber);
        }

        /**
         * Map the given argument {@link Format} into the corresponding {@link ArgumentSegment}.
         *
         * <p>
         * The optional {@code locale} parameter allows for identification of certain formats
         * when they equal the locale default. This also produces more concise {@link MessageFormat}
         * format strings (see {@link MessageFmt#toPattern}).
         *
         * @param format format to be used for argument
         * @param argumentNumber argument number
         * @param locale assumed locale, or null for none
         * @param <T> format type
         * @return an equivalent {@link FormatArgumentSegment}
         * @throws IllegalArgumentException if {@code argumentNumber} is negative
         * @throws IllegalArgumentException if {@code format} is null or cannot be deciphered
         */
        @SuppressWarnings("unchecked")
        public static <T extends Format> FormatArgumentSegment<T> of(T format, int argumentNumber, Locale locale) {

            // Sanity check
            if (format == null)
                throw new IllegalArgumentException("null format");
            if (argumentNumber < 0)
                throw new IllegalArgumentException("negative argumentNumber");

            // Decipher format
            if (locale != null) {
                if (format.equals(NumberFormat.getInstance(locale)))
                    return (FormatArgumentSegment<T>)new DefaultNumberFormatArgumentSegment(argumentNumber);
                if (format.equals(NumberFormat.getCurrencyInstance(locale)))
                    return (FormatArgumentSegment<T>)new CurrencyArgumentSegment(argumentNumber);
                if (format.equals(NumberFormat.getPercentInstance(locale)))
                    return (FormatArgumentSegment<T>)new PercentArgumentSegment(argumentNumber);
                if (format.equals(NumberFormat.getIntegerInstance(locale)))
                    return (FormatArgumentSegment<T>)new IntegerArgumentSegment(argumentNumber);
            }
            if (format instanceof DecimalFormat)
                return (FormatArgumentSegment<T>)new DecimalArgumentSegment(argumentNumber, (DecimalFormat)format);
            if (format instanceof ChoiceFormat)
                return (FormatArgumentSegment<T>)new ChoiceArgumentSegment(argumentNumber, (ChoiceFormat)format);
            if (format instanceof DateFormat) {

                // Check locale standard formats
                if (locale != null) {
                    for (DateFormatStandard standard : DateFormatStandard.values()) {
                        if (format.equals(DateFormat.getDateInstance(standard.value(), locale)))
                            return (FormatArgumentSegment<T>)new StandardDateFormatArgumentSegment(argumentNumber, standard);
                        if (format.equals(DateFormat.getTimeInstance(standard.value(), locale)))
                            return (FormatArgumentSegment<T>)new StandardTimeFormatArgumentSegment(argumentNumber, standard);
                    }
                }

                // Check SimpleDateFormat
                if (format instanceof SimpleDateFormat)
                    return (FormatArgumentSegment<T>)new SimpleDateFormatArgumentSegment(argumentNumber, (SimpleDateFormat)format);
            }

            // Nothing found
            throw new IllegalArgumentException("undecipherable format: " + format);
        }
    }

// NumberFormatArgumentSegment

    /**
     * An {@link ArgumentSegment} that formats its argument using a {@link NumberFormat}.
     */
    public abstract static class NumberFormatArgumentSegment<T extends NumberFormat> extends FormatArgumentSegment<T> {

        protected NumberFormatArgumentSegment() {
        }

        protected NumberFormatArgumentSegment(int argumentNumber) {
            super(argumentNumber);
        }
    }

// DefaultNumberFormatArgumentSegment

    /**
     * An {@link ArgumentSegment} that formats its argument using the default {@link NumberFormat} for the locale.
     *
     * @see NumberFormat#getInstance(Locale)
     */
    public static class DefaultNumberFormatArgumentSegment extends NumberFormatArgumentSegment<NumberFormat> {

        public DefaultNumberFormatArgumentSegment() {
        }

        public DefaultNumberFormatArgumentSegment(int argumentNumber) {
            super(argumentNumber);
        }

        @Override
        protected String getArgumentSuffix() {
            return "number";
        }
    }

// CurrencyArgumentSegment

    /**
     * An {@link ArgumentSegment} that formats its argument using the {@link NumberFormat}
     * {@linkplain NumberFormat#getCurrencyInstance(Locale) currency instance} for the locale.
     *
     * @see NumberFormat#getCurrencyInstance(Locale)
     */
    public static class CurrencyArgumentSegment extends NumberFormatArgumentSegment<NumberFormat> {

        public CurrencyArgumentSegment() {
        }

        public CurrencyArgumentSegment(int argumentNumber) {
            super(argumentNumber);
        }

        @Override
        protected String getArgumentSuffix() {
            return "number,currency";
        }
    }

// PercentArgumentSegment

    /**
     * An {@link ArgumentSegment} that formats its argument using the {@link NumberFormat}
     * {@linkplain NumberFormat#getPercentInstance(Locale) percent instance} for the locale.
     *
     * @see NumberFormat#getPercentInstance(Locale)
     */
    public static class PercentArgumentSegment extends NumberFormatArgumentSegment<NumberFormat> {

        public PercentArgumentSegment() {
        }

        public PercentArgumentSegment(int argumentNumber) {
            super(argumentNumber);
        }

        @Override
        protected String getArgumentSuffix() {
            return "number,percent";
        }
    }

// IntegerArgumentSegment

    /**
     * An {@link ArgumentSegment} that formats its argument using the {@link NumberFormat}
     * {@linkplain NumberFormat#getIntegerInstance(Locale) integer instance} for the locale.
     *
     * @see NumberFormat#getIntegerInstance(Locale)
     */
    public static class IntegerArgumentSegment extends NumberFormatArgumentSegment<NumberFormat> {

        public IntegerArgumentSegment() {
        }

        public IntegerArgumentSegment(int argumentNumber) {
            super(argumentNumber);
        }

        @Override
        protected String getArgumentSuffix() {
            return "number,integer";
        }
    }

// DecimalArgumentSegment

    /**
     * An {@link ArgumentSegment} that formats its argument using a {@link DecimalFormat}.
     */
    public static class DecimalArgumentSegment extends NumberFormatArgumentSegment<DecimalFormat> {

        private String pattern;

        public DecimalArgumentSegment() {
        }

        public DecimalArgumentSegment(int argumentNumber, DecimalFormat format) {
            super(argumentNumber);
            if (format == null)
                throw new IllegalArgumentException("null format");
            this.pattern = format.toPattern();
        }

        public String getPattern() {
            return this.pattern;
        }
        public void setPattern(final String pattern) {
            this.pattern = pattern;
        }

        @Override
        protected String getArgumentSuffix() {
            return "number," + this.pattern;
        }
    }

// ChoiceArgumentSegment

    /**
     * An {@link ArgumentSegment} that formats its argument using a {@link ChoiceFormat}.
     *
     * <p>
     * When a {@link MessageFormat} contains a choice argument, each choice option is itself
     * treated as a nested {@link MessageFormat} pattern, so recursion is possible; see
     * {@link ChoiceArgumentSegment.Option}.
     */
    public static class ChoiceArgumentSegment extends NumberFormatArgumentSegment<ChoiceFormat> {

        private List<Option> options;

    // Constructors

        public ChoiceArgumentSegment() {
            this.options = new ArrayList<>(0);
        }

        public ChoiceArgumentSegment(int argumentNumber, ChoiceFormat format) {
            super(argumentNumber);

            // Sanity check
            if (format == null)
                throw new IllegalArgumentException("null format");

            // Get limits and formats
            final double[] limits = format.getLimits();
            final String[] formats = (String[])format.getFormats();
            assert limits.length == formats.length;
            this.options = new ArrayList<>(limits.length);
            for (int i = 0; i < limits.length; i++)
                this.options.add(new Option(limits[i], new MessageFmt(new MessageFormat(formats[i]))));
        }

    // Properties

        /**
         * Get the possible options.
         *
         * @return options for choice
         */
        public List<Option> getOptions() {
            return this.options;
        }
        public void setOptions(final List<Option> options) {
            this.options = options;
        }

        @Override
        protected String getArgumentSuffix() {
            return "choice," + this.toChoiceFormat().toPattern();
        }

    // Methods

        /**
         * Build the {@link ChoiceFormat} represented by this instance.
         *
         * @return an equivalent {@link ChoiceFormat}
         */
        public ChoiceFormat toChoiceFormat() {
            final double[] limits = new double[this.options.size()];
            final String[] formats = new String[this.options.size()];
            for (int i = 0; i < this.options.size(); i++) {
                final Option option = this.options.get(i);
                limits[i] = option.getLimit();
                formats[i] = option.getFormat().toMessageFormat().toPattern();
            }
            return new ChoiceFormat(limits, formats);
        }

    // Object

        @Override
        public String toString() {
            return this.getClass().getSimpleName()
              + "[argumentNumber=" + this.getArgumentNumber()
              + ",options=" + this.options
              + "]";
        }

    // Option

        /**
         * Represents one option in a {@link ChoiceArgumentSegment}.
         *
         * <p>
         * An option is chosen if the number to be formatted is no less than {@link #getLimit}
         * and also strictly less than the next option's limit (if any).
         */
        public static class Option {

            private double limit;
            private MessageFmt format;

        // Constructors

            public Option() {
            }

            public Option(double limit, MessageFmt format) {
                this.limit = limit;
                this.format = format;
            }

        // Properties

            /**
             * Get the limit for this {@link ChoiceArgumentSegment} option.
             *
             * @return argument upper bound (inclusive) for this option
             */
            public double getLimit() {
                return this.limit;
            }
            public void setLimit(final double limit) {
                this.limit = limit;
            }

            /**
             * Get the {@link MessageFmt} for this {@link ChoiceArgumentSegment} option.
             *
             * <p>
             * Note: whereas the options in a {@link ChoiceFormat} are format strings, in the context of a {@link MessageFormat}
             * a choice-formatted argument format string is interpreted as a nested {@link MessageFormat} pattern, therefore
             * this property has type {@link MessageFmt} instead of {@link String}.
             *
             * @return the {@link MessageFmt} for this option
             */
            public MessageFmt getFormat() {
                return this.format;
            }
            public void setFormat(final MessageFmt format) {
                this.format = format;
            }

        // Object

            @Override
            public String toString() {
                return this.getClass().getSimpleName()
                  + "[limit=" + this.limit
                  + ",format=" + this.format
                  + "]";
            }
        }
    }

// DateFormatArgumentSegment

    /**
     * A {@link MessageFormat} argument segment that formats the argument using a {@link DateFormat}.
     */
    public abstract static class DateFormatArgumentSegment<T extends DateFormat> extends FormatArgumentSegment<DateFormat> {

        protected DateFormatArgumentSegment() {
        }

        protected DateFormatArgumentSegment(int argumentNumber) {
            super(argumentNumber);
        }
    }

// SimpleDateFormatArgumentSegment

    /**
     * A {@link MessageFormat} argument segment that formats the argument using a {@link SimpleDateFormat}.
     */
    public static class SimpleDateFormatArgumentSegment extends DateFormatArgumentSegment<SimpleDateFormat> {

        private String pattern;

        public SimpleDateFormatArgumentSegment() {
        }

        public SimpleDateFormatArgumentSegment(int argumentNumber, SimpleDateFormat format) {
            super(argumentNumber);
            if (format == null)
                throw new IllegalArgumentException("null format");
            this.pattern = format.toPattern();
        }

        public String getPattern() {
            return this.pattern;
        }
        public void setPattern(final String pattern) {
            this.pattern = pattern;
        }

        @Override
        protected String getArgumentSuffix() {
            return "date," + this.pattern;
        }
    }

// AbstractStandardDateFormatArgumentSegment

    private abstract static class AbstractStandardDateFormatArgumentSegment extends DateFormatArgumentSegment<DateFormat> {

        private DateFormatStandard standard;

        protected AbstractStandardDateFormatArgumentSegment() {
        }

        protected AbstractStandardDateFormatArgumentSegment(int argumentNumber, DateFormatStandard standard) {
            super(argumentNumber);
            this.standard = standard;
        }

        public DateFormatStandard getStandard() {
            return this.standard;
        }
        public void setStandard(final DateFormatStandard standard) {
            this.standard = standard;
        }

        @Override
        protected String getArgumentSuffix() {
            String result = this.getKeyword();
            if (this.standard != null && !this.standard.equals(DateFormatStandard.DEFAULT))
                result += "," + this.standard.description();
            return result;
        }

        protected abstract String getKeyword();
    }

// StandardDateFormatArgumentSegment

    /**
     * An {@link ArgumentSegment} that formats its argument using one of the standard {@link DateFormat}
     * {@linkplain DateFormat#getDateInstance(int, Locale) date instances} for the locale.
     *
     * @see DateFormat#getDateInstance(int, Locale)
     */
    public static class StandardDateFormatArgumentSegment extends AbstractStandardDateFormatArgumentSegment {

        public StandardDateFormatArgumentSegment() {
        }

        public StandardDateFormatArgumentSegment(int argumentNumber, DateFormatStandard standard) {
            super(argumentNumber, standard);
        }

        @Override
        protected String getKeyword() {
            return "date";
        }
    }

// StandardTimeFormatArgumentSegment

    /**
     * An {@link ArgumentSegment} that formats its argument using one of the standard {@link DateFormat}
     * {@linkplain DateFormat#getTimeInstance(int, Locale) time instances} for the locale.
     *
     * @see DateFormat#getTimeInstance(int, Locale)
     */
    public static class StandardTimeFormatArgumentSegment extends AbstractStandardDateFormatArgumentSegment {

        public StandardTimeFormatArgumentSegment() {
        }

        public StandardTimeFormatArgumentSegment(int argumentNumber, DateFormatStandard standard) {
            super(argumentNumber, standard);
        }

        @Override
        protected String getKeyword() {
            return "time";
        }
    }

// DateFormatStandard

    /**
     * Enumerates the standard, pre-defined formats for dates and times in {@link DateFormat}.
     *
     * @see DateFormat
     */
    public enum DateFormatStandard {
        DEFAULT(DateFormat.DEFAULT),
        SHORT(DateFormat.SHORT),
        MEDIUM(DateFormat.MEDIUM),
        LONG(DateFormat.LONG),
        FULL(DateFormat.FULL);

        private final int value;

        DateFormatStandard(int value) {
            this.value = value;
        }

        public int value() {
            return this.value;
        }

        public String description() {
            return this.name().toLowerCase();
        }
    }
}
