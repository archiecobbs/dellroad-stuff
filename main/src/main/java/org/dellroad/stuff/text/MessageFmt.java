
/*
 * Copyright (C) 2022 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.text;

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
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import javax.validation.ConstraintValidatorContext;
import javax.validation.Valid;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

import org.dellroad.stuff.validation.SelfValidates;
import org.dellroad.stuff.validation.SelfValidating;
import org.dellroad.stuff.validation.SelfValidationException;

/**
 * POJO representing a {@link MessageFormat} that models its internal structure, supporting arbitrary recursive
 * nesting of {@link MessageFormat} with {@link ChoiceFormat}.
 *
 * <script src="https://cdnjs.cloudflare.com/ajax/libs/prism/1.27.0/prism.min.js"></script>
 * <script src="https://cdnjs.cloudflare.com/ajax/libs/prism/1.27.0/components/prism-java.min.js"></script>
 * <link href="https://cdnjs.cloudflare.com/ajax/libs/prism/1.27.0/themes/prism.min.css" rel="stylesheet"/>
 *
 * <p>
 * The point of this class is to make it easier to work with {@link MessageFormat} instances by allowing for
 * structural introspection and eliminating complex quoting/escaping issues.
 *
 * <p>
 * <b>Locales</b>
 *
 * <p>
 * Unlike the {@link MessageFormat} class, instances of {@link MessageFmt} do not have an associated {@link Locale}; they
 * represent the structure of the format string only. However, some of that structure may implicitly refer to
 * {@link Locale}-provided defaults; for example, a {@link MessageFmt.CurrencyArgumentSegment} means "format as currency
 * using {@link NumberFormat#getCurrencyInstance(Locale)}", which produces a different result depending on the {@link Locale}.
 * So, in contrast to {@link MessageFormat}, the {@link Locale} to be used is always provided separately.
 *
 * <p>
 * Having said that, it is possible to create {@link MessageFmt} instances that "capture" any {@link Locale} defaults
 * at the time of construction and therefore avoid the use of {@link Locale}-dependent segments such as
 * {@link MessageFmt.CurrencyArgumentSegment}; see {@link #MessageFmt(MessageFormat, boolean)} for details.
 *
 * <p>
 * Classes are annotated to support JSR 303 validation.
 */
@SelfValidates
public class MessageFmt implements SelfValidating {

    private List<Segment> segments = new ArrayList<>();

// Constructors

    /**
     * Default constructor.
     *
     * <p>
     * Creates an empty instance.
     */
    public MessageFmt() {
    }

    /**
     * Create an instance from explicitly given {@link Segment}s.
     *
     * @param segments message components
     * @throws IllegalArgumentException if {@code segments} or any element thereof is null
     */
    public MessageFmt(Segment... segments) {
        if (segments == null)
            throw new IllegalArgumentException("null segments");
        for (int i = 0; i < segments.length; i++) {
            final Segment segment = segments[i];
            if (segment == null)
                throw new IllegalArgumentException("null segment");
            this.segments.add(segment);
        }
    }

    /**
     * Create an instance modeling the given {@link MessageFormat}.
     *
     * <p>
     * Equivalent to: {@link #MessageFmt(MessageFormat, boolean) MessageFmt}{@code (format, false)}.
     *
     * @param format source message format
     * @throws IllegalArgumentException if {@code format} is null
     */
    public MessageFmt(MessageFormat format) {
        this(format, false);
    }

    /**
     * Create an instance modeling the given {@link MessageFormat} with optional capturing of {@link Locale} defaults.
     *
     * <p>
     * The {@code captureLocaleDefaults} parameter controls whether {@link FormatArgumentSegment}s that refer to
     * {@link Locale} defaults are allowed. Such segments produce different results depending on the locale; see
     * {@link FormatArgumentSegment#of FormatArgumentSegment.of()}. If {@code captureLocaleDefaults} is true, these
     * implicit locale-dependent formats are not allowed; instead the actual formats are captured whenever possible.
     *
     * <p>
     * Here's a concrete example:
     * <pre><code class="language-java">
     * final Object[] args = new Object[] { new Date(1590000000000L) };         // May 20, 2020
     *
     * final MessageFormat messageFormat = new MessageFormat("date = {0,date,short}", Locale.US);
     *
     * System.out.println("messageFormat -&gt; " + messageFormat.format(args));
     *
     * final MessageFmt messageFmt1 = new MessageFmt(messageFormat, false);     // leave "date,short" alone; bind to locale later
     * final MessageFmt messageFmt2 = new MessageFmt(messageFormat, true);      // capture "date,short" in Locale.US
     *
     * System.out.println("messageFmt1.toPattern() = " + messageFmt1.toPattern());
     * System.out.println("messageFmt2.toPattern() = " + messageFmt2.toPattern());
     *
     * final MessageFormat messageFormat1 = messageFmt1.toMessageFormat(Locale.FRANCE);
     * final MessageFormat messageFormat2 = messageFmt2.toMessageFormat(Locale.FRANCE);
     *
     * System.out.println("messageFormat1 -&gt; " + messageFormat1.format(args));
     * System.out.println("messageFormat2 -&gt; " + messageFormat2.format(args));
     * </code></pre>
     * This would produce the following output:
     * <blockquote><pre>
     * messageFormat -&gt; date = 5/20/20
     * messageFmt1.toPattern() = date = {0,date,short}
     * messageFmt2.toPattern() = date = {0,date,M/d/yy}
     * messageFormat1 -&gt; date = 20/05/20
     * messageFormat2 -&gt; date = 5/20/20
     * </pre></blockquote>
     *
     * <p>
     * Note that regardless of {@code captureLocaleDefaults}, some {@link MessageFormat} arguments are always
     * {@link Locale}-dependent. For example, a simple argument parameter like <code>{0}</code>, when applied
     * to a numerical argument, is always formatted using the {@link Locale} default number format.
     *
     * @param format source message format
     * @param captureLocaleDefaults true to capture locale defaults, false to allow implicit locale defaults
     * @throws IllegalArgumentException if {@code format} is null
     * @throws RuntimeException if reflective access into {@link MessageFormat} is denied
     * @see FormatArgumentSegment#of FormatArgumentSegment.of()
     */
    public MessageFmt(MessageFormat format, boolean captureLocaleDefaults) {

        // Sanity check
        if (format == null)
            throw new IllegalArgumentException("null format");

        // Get format inforation
        final Locale locale = !captureLocaleDefaults ? format.getLocale() : null;
        final Format[] formats = format.getFormats();

        // Initialize parse
        final String pattern = format.toPattern();
        final AtomicInteger pos = new AtomicInteger();

        // Create an accumulator for plain text in between nested formats
        final StringBuilder plainTextBuffer = new StringBuilder();
        final Runnable endOfPlainText = () -> {
            if (plainTextBuffer.length() > 0) {
                final String text = plainTextBuffer.toString();
                this.segments.add(new TextSegment(text));
                plainTextBuffer.setLength(0);
            }
        };

        // Create a quoted text scanner; we assume "pos" points to the first quoted character
        final Supplier<String> quotedTextScanner = () -> {
            final StringBuilder buf = new StringBuilder();
            for (int off = 0; true; off++) {
                if (pos.get() == pattern.length())
                    throw new IllegalArgumentException("invalid pattern string: unclosed quote");
                char ch = pattern.charAt(pos.getAndIncrement());
                if (ch == '\'')
                    break;
                buf.append(ch);
            }
            return buf.length() > 0 ? buf.toString() : "'";
        };

        // Parse the format pattern to identify the nested formats
        int braceDepth = 0;
        int formatIndex = 0;
        int argNum = -1;
        while (pos.get() < pattern.length()) {
            assert (braceDepth == 0) == (argNum == -1);
            char ch = pattern.charAt(pos.getAndIncrement());
            switch (ch) {
            case '\'':
                final String text = quotedTextScanner.get();
                if (braceDepth == 0)
                    plainTextBuffer.append(text);
                continue;
            case '{':

                // Already scanning a nested format?
                if (braceDepth++ > 0)
                    continue;

                // End the current run of plain text
                endOfPlainText.run();

                // Scan, parse, and remember the argument index
                int argNumEnd = pos.get();
                while (argNumEnd < pattern.length() && Character.isDigit(pattern.charAt(argNumEnd)))
                    argNumEnd++;
                final String argNumString = pattern.substring(pos.get(), argNumEnd);
                try {
                    if ((argNum = Integer.parseInt(argNumString)) < 0)
                        throw new NumberFormatException("negative argument index");
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException(String.format(
                      "invalid pattern string: invalid argument index \"%s\"", argNumString), e);
                }

                // Proceed
                pos.set(argNumEnd);
                continue;
            case '}':

                // Scanning plain text?
                if (braceDepth == 0)
                    break;

                // Check for end of nested format
                if (--braceDepth == 0) {
                    final Format nextFormat = formats[formatIndex++];
                    this.segments.add(nextFormat != null ?
                      FormatArgumentSegment.of(nextFormat, argNum, locale) : new DefaultArgumentSegment(argNum));
                    argNum = -1;
                }
                continue;
            default:

                // Scanning a nested format?
                if (braceDepth > 0)
                    continue;
                break;
            }

            // Add plain text character
            plainTextBuffer.append(ch);
        }

        // End trailing run of plain text
        endOfPlainText.run();
    }

// Properties

    /**
     * Get the individual components of this message format.
     *
     * @return message format segments
     */
    @NotNull
    @Valid
    public List<@NotNull Segment> getSegments() {
        return this.segments;
    }
    public void setSegments(final List<Segment> segments) {
        this.segments = segments;
    }

// Methods

    /**
     * Build the {@link MessageFormat} represented by this instance using the default format locale.
     *
     * <p>
     * This method is equivalent to:
     * <blockquote>
     * {@code new MessageFormat(this.}{@link #toPattern toPattern}{@code ())}.
     * </blockquote>
     *
     * @return an equivalent {@link MessageFormat}
     * @see MessageFormat#MessageFormat(String)
     */
    public MessageFormat toMessageFormat() {
        return new MessageFormat(this.toPattern());
    }

    /**
     * Build the {@link MessageFormat} represented by this instance using the specified {@link Locale}.
     *
     * <p>
     * This method is equivalent to:
     * <blockquote>
     * {@code new MessageFormat(this.}{@link #toPattern toPattern}{@code (), locale)}.
     * </blockquote>
     *
     * @param locale locale for {@link MessageFormat}
     * @return an equivalent {@link MessageFormat} using the given {@link Locale}
     * @see MessageFormat#MessageFormat(String, Locale)
     */
    public MessageFormat toMessageFormat(Locale locale) {
        return new MessageFormat(this.toPattern(), locale);
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

    /**
     * Escape {@link MessageFormat} special characters.
     *
     * @param string unescaped input string
     * @return {@code string} with characters {@link MessageFormat} considers special escaped
     */
    public static String escape(String string) {
        return string
          .replaceAll("'", "''")                    // escape single quotes by doubling them
          .replaceAll("\\{+", "'$0'");              // quote runs of opening curly braces
    }

    /**
     * Un-escape escaped {@link MessageFormat} special characters.
     *
     * <p>
     * This takes the output from {@link MessageFmt#escape MessageFmt.escape()} and returns the original string.
     *
     * @param string escaped input string
     * @return {@code string} with escaping added by {@link MessageFmt#escape MessageFmt.escape()} reverted
     */
    public static String unescape(String string) {
        return string
          .replaceAll("(?<!')'([^']+)'", "$1")      // remove lone single quotes surrounding text
          .replaceAll("''", "'");                   // unescape doubled single quote
    }

// Validation

    @Override
    public void checkValid(ConstraintValidatorContext context) throws SelfValidationException {
        try {
            this.toMessageFormat(Locale.getDefault(Locale.Category.FORMAT));
        } catch (IllegalArgumentException e) {
            throw new SelfValidationException("invalid configuration", e);
        }
    }

// Object

    @Override
    public boolean equals(Object obj) {
        if (obj == this)
            return true;
        if (obj == null || obj.getClass() != this.getClass())
            return false;
        final MessageFmt that = (MessageFmt)obj;
        return Objects.equals(this.segments, that.segments);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(this.segments);
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName()
          + "[segments=" + this.segments
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
         * Encode this segment as a {@link MessageFormat} pattern fragment.
         *
         * <p>
         * The concatenation of the encoding of the {@link Segment}s in a {@link MessageFmt} produces the
         * corresponding {@link MessageFormat} pattern string.
         *
         * <p>
         * This method is responsible for escaping single quotes and opening curly braces, if necessary.
         *
         * @return {@link MessageFormat} pattern string text
         */
        public abstract String toPattern();

        /**
         * Visit the method of the given switch corresponding to this instance's concrete type.
         *
         * @param target visitor pattern target
         */
        public abstract void visit(SegmentSwitch target);

        @Override
        public boolean equals(Object obj) {
            if (obj == this)
                return true;
            if (obj == null || obj.getClass() != this.getClass())
                return false;
            return true;
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(this.getClass());
        }
    }

// SegmentSwitch

    /**
     * Visitor pattern interface for {@link Segment} subclasses.
     *
     * @see Segment#visit Segment.visit()
     */
    public interface SegmentSwitch {
        void caseChoiceArgumentSegment(ChoiceArgumentSegment segment);
        void caseCurrencyArgumentSegment(CurrencyArgumentSegment segment);
        void caseDecimalArgumentSegment(DecimalArgumentSegment segment);
        void caseDefaultArgumentSegment(DefaultArgumentSegment segment);
        void caseDefaultNumberFormatArgumentSegment(DefaultNumberFormatArgumentSegment segment);
        void caseIntegerArgumentSegment(IntegerArgumentSegment segment);
        void casePercentArgumentSegment(PercentArgumentSegment segment);
        void caseSimpleDateFormatArgumentSegment(SimpleDateFormatArgumentSegment segment);
        void caseStandardDateFormatArgumentSegment(StandardDateFormatArgumentSegment segment);
        void caseStandardTimeFormatArgumentSegment(StandardTimeFormatArgumentSegment segment);
        void caseTextSegment(TextSegment segment);
    }

// SegmentSwitchAdapter

    /**
     * Adapter class for {@link SegmentSwitch} implementations.
     */
    public static class SegmentSwitchAdapter implements SegmentSwitch {

        /**
         * Handle a {@link ChoiceArgumentSegment}.
         *
         * <p>
         * The implementation in {@link SegmentSwitchAdapter} delegates to
         * {@link #caseNumberFormatArgumentSegment caseNumberFormatArgumentSegment()}.
         *
         * @param segment visited segment
         */
        @Override
        public void caseChoiceArgumentSegment(ChoiceArgumentSegment segment) {
            this.caseNumberFormatArgumentSegment(segment);
        }

        /**
         * Handle a {@link CurrencyArgumentSegment}.
         *
         * <p>
         * The implementation in {@link SegmentSwitchAdapter} delegates to
         * {@link #caseNumberFormatArgumentSegment caseNumberFormatArgumentSegment()}.
         *
         * @param segment visited segment
         */
        @Override
        public void caseCurrencyArgumentSegment(CurrencyArgumentSegment segment) {
            this.caseNumberFormatArgumentSegment(segment);
        }

        /**
         * Handle a {@link DecimalArgumentSegment}.
         *
         * <p>
         * The implementation in {@link SegmentSwitchAdapter} delegates to
         * {@link #caseNumberFormatArgumentSegment caseNumberFormatArgumentSegment()}.
         *
         * @param segment visited segment
         */
        @Override
        public void caseDecimalArgumentSegment(DecimalArgumentSegment segment) {
            this.caseNumberFormatArgumentSegment(segment);
        }

        /**
         * Handle a {@link DefaultArgumentSegment}.
         *
         * <p>
         * The implementation in {@link SegmentSwitchAdapter} delegates to
         * {@link #caseArgumentSegment caseArgumentSegment()}.
         *
         * @param segment visited segment
         */
        @Override
        public void caseDefaultArgumentSegment(DefaultArgumentSegment segment) {
            this.caseArgumentSegment(segment);
        }

        /**
         * Handle a {@link DefaultNumberFormatArgumentSegment}.
         *
         * <p>
         * The implementation in {@link SegmentSwitchAdapter} delegates to
         * {@link #caseNumberFormatArgumentSegment caseNumberFormatArgumentSegment()}.
         *
         * @param segment visited segment
         */
        @Override
        public void caseDefaultNumberFormatArgumentSegment(DefaultNumberFormatArgumentSegment segment) {
            this.caseNumberFormatArgumentSegment(segment);
        }

        /**
         * Handle an {@link IntegerArgumentSegment}.
         *
         * <p>
         * The implementation in {@link SegmentSwitchAdapter} delegates to
         * {@link #caseNumberFormatArgumentSegment caseNumberFormatArgumentSegment()}.
         *
         * @param segment visited segment
         */
        @Override
        public void caseIntegerArgumentSegment(IntegerArgumentSegment segment) {
            this.caseNumberFormatArgumentSegment(segment);
        }

        /**
         * Handle a {@link PercentArgumentSegment}.
         *
         * <p>
         * The implementation in {@link SegmentSwitchAdapter} delegates to
         * {@link #caseNumberFormatArgumentSegment caseNumberFormatArgumentSegment()}.
         *
         * @param segment visited segment
         */
        @Override
        public void casePercentArgumentSegment(PercentArgumentSegment segment) {
            this.caseNumberFormatArgumentSegment(segment);
        }

        /**
         * Handle a {@link SimpleDateFormatArgumentSegment}.
         *
         * <p>
         * The implementation in {@link SegmentSwitchAdapter} delegates to
         * {@link #caseDateFormatArgumentSegment caseDateFormatArgumentSegment()}.
         *
         * @param segment visited segment
         */
        @Override
        public void caseSimpleDateFormatArgumentSegment(SimpleDateFormatArgumentSegment segment) {
            this.caseDateFormatArgumentSegment(segment);
        }

        /**
         * Handle a {@link StandardDateFormatArgumentSegment}.
         *
         * <p>
         * The implementation in {@link SegmentSwitchAdapter} delegates to
         * {@link #caseDateFormatArgumentSegment caseDateFormatArgumentSegment()}.
         *
         * @param segment visited segment
         */
        @Override
        public void caseStandardDateFormatArgumentSegment(StandardDateFormatArgumentSegment segment) {
            this.caseDateFormatArgumentSegment(segment);
        }

        /**
         * Handle a {@link StandardTimeFormatArgumentSegment}.
         *
         * <p>
         * The implementation in {@link SegmentSwitchAdapter} delegates to
         * {@link #caseDateFormatArgumentSegment caseDateFormatArgumentSegment()}.
         *
         * @param segment visited segment
         */
        @Override
        public void caseStandardTimeFormatArgumentSegment(StandardTimeFormatArgumentSegment segment) {
            this.caseDateFormatArgumentSegment(segment);
        }

        /**
         * Handle a {@link TextSegment}.
         *
         * <p>
         * The implementation in {@link SegmentSwitchAdapter} delegates to {@link #caseSegment caseSegment()}.
         *
         * @param segment visited segment
         */
        @Override
        public void caseTextSegment(TextSegment segment) {
            this.caseSegment(segment);
        }

    // Roll-up Methods

        /**
         * Internal roll-up method.
         *
         * <p>
         * The implementation in {@link SegmentSwitchAdapter} delegates to
         * {@link #caseFormatArgumentSegment caseFormatArgumentSegment()}.
         *
         * @param segment visited segment
         * @param <T> date format type
         */
        protected <T extends DateFormat> void caseDateFormatArgumentSegment(DateFormatArgumentSegment<T> segment) {
            this.caseFormatArgumentSegment(segment);
        }

        /**
         * Internal roll-up method.
         *
         * <p>
         * The implementation in {@link SegmentSwitchAdapter} delegates to
         * {@link #caseFormatArgumentSegment caseFormatArgumentSegment()}.
         *
         * @param segment visited segment
         * @param <T> number format type
         */
        protected <T extends NumberFormat> void caseNumberFormatArgumentSegment(NumberFormatArgumentSegment<T> segment) {
            this.caseFormatArgumentSegment(segment);
        }

        /**
         * Internal roll-up method.
         *
         * <p>
         * The implementation in {@link SegmentSwitchAdapter} delegates to
         * {@link #caseArgumentSegment caseArgumentSegment()}.
         *
         * @param segment visited segment
         * @param <T> format type
         */
        protected <T extends Format> void caseFormatArgumentSegment(FormatArgumentSegment<T> segment) {
            this.caseArgumentSegment(segment);
        }

        /**
         * Internal roll-up method.
         *
         * <p>
         * The implementation in {@link SegmentSwitchAdapter} delegates to {@link #caseSegment caseSegment()}.
         *
         * @param segment visited segment
         */
        protected void caseArgumentSegment(ArgumentSegment segment) {
            this.caseSegment(segment);
        }

        /**
         * Internal roll-up method.
         *
         * <p>
         * The implementation in {@link SegmentSwitchAdapter} does nothing.
         *
         * @param segment visited segment
         */
        protected void caseSegment(Segment segment) {
        }
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

        /**
         * Get the associated plain text.
         *
         * @return segment plain text
         */
        @NotNull
        public String getString() {
            return this.string;
        }
        public void setString(final String string) {
            this.string = string;
        }

    // MessageFormat.Segment

        @Override
        public String toPattern() {
            return MessageFmt.escape(this.string);
        }

        @Override
        public void visit(SegmentSwitch target) {
            target.caseTextSegment(this);
        }

    // Object

        @Override
        public boolean equals(Object obj) {
            if (obj == this)
                return true;
            if (!super.equals(obj))
                return false;
            final TextSegment that = (TextSegment)obj;
            return Objects.equals(this.string, that.string);
        }

        @Override
        public int hashCode() {
            return super.hashCode() ^ Objects.hashCode(this.string);
        }

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
        @Min(0)
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
        public boolean equals(Object obj) {
            if (obj == this)
                return true;
            if (!super.equals(obj))
                return false;
            final ArgumentSegment that = (ArgumentSegment)obj;
            return this.argumentNumber == that.argumentNumber;
        }

        @Override
        public int hashCode() {
            return super.hashCode() ^ this.argumentNumber;
        }

        @Override
        public String toString() {
            return this.getClass().getSimpleName()
              + "[argumentNumber=" + this.argumentNumber
              + ",pattern=\"" + this.toPattern() + "\""
              + "]";
        }
    }

// DefaultArgumentSegment

    /**
     * An {@link ArgumentSegment} that simply formats the argument using the default formatting for its type.
     */
    public static class DefaultArgumentSegment extends ArgumentSegment {

        public DefaultArgumentSegment() {
        }

        public DefaultArgumentSegment(int argumentNumber) {
            super(argumentNumber);
        }

    // ArgumentSegment

        @Override
        protected String getArgumentSuffix() {
            return null;
        }

    // Segment

        @Override
        public void visit(SegmentSwitch target) {
            target.caseDefaultArgumentSegment(this);
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
         * when they equal the locale default. This also results in more concise {@link MessageFormat}
         * format strings (see {@link MessageFmt#toPattern}), but makes the result implicitly depend
         * on the {@link Locale} being used (because different {@link Locale}s have different defaults
         * for integer, percent, currency, etc).
         *
         * @param format format to be used for argument
         * @param argumentNumber argument number
         * @param locale assumed locale, or null to not make any locale assumptions
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

    // ArgumentSegment

        @Override
        protected String getArgumentSuffix() {
            return "number";
        }

    // Segment

        @Override
        public void visit(SegmentSwitch target) {
            target.caseDefaultNumberFormatArgumentSegment(this);
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

    // ArgumentSegment

        @Override
        protected String getArgumentSuffix() {
            return "number,currency";
        }

    // Segment

        @Override
        public void visit(SegmentSwitch target) {
            target.caseCurrencyArgumentSegment(this);
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

    // ArgumentSegment

        @Override
        protected String getArgumentSuffix() {
            return "number,percent";
        }

    // Segment

        @Override
        public void visit(SegmentSwitch target) {
            target.casePercentArgumentSegment(this);
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

    // ArgumentSegment

        @Override
        protected String getArgumentSuffix() {
            return "number,integer";
        }

    // Segment

        @Override
        public void visit(SegmentSwitch target) {
            target.caseIntegerArgumentSegment(this);
        }
    }

// DecimalArgumentSegment

    /**
     * An {@link ArgumentSegment} that formats its argument using a {@link DecimalFormat}.
     */
    @SelfValidates
    public static class DecimalArgumentSegment extends NumberFormatArgumentSegment<DecimalFormat> implements SelfValidating {

        private String pattern;

        public DecimalArgumentSegment() {
        }

        public DecimalArgumentSegment(int argumentNumber, DecimalFormat format) {
            super(argumentNumber);
            if (format == null)
                throw new IllegalArgumentException("null format");
            this.pattern = format.toPattern();
        }

    // Properties

        @NotNull
        public String getPattern() {
            return this.pattern;
        }
        public void setPattern(final String pattern) {
            this.pattern = pattern;
        }

    // ArgumentSegment

        @Override
        protected String getArgumentSuffix() {
            return "number," + this.pattern;
        }

    // Segment

        @Override
        public void visit(SegmentSwitch target) {
            target.caseDecimalArgumentSegment(this);
        }

    // Validation

        @Override
        public void checkValid(ConstraintValidatorContext context) throws SelfValidationException {
            if (this.pattern != null) {
                try {
                    new DecimalFormat(this.pattern);
                } catch (IllegalArgumentException e) {
                    throw new SelfValidationException("invalid DecimalFormat pattern \"" + this.pattern + "\"", e);
                }
            }
        }

    // Object

        @Override
        public boolean equals(Object obj) {
            if (obj == this)
                return true;
            if (!super.equals(obj))
                return false;
            final DecimalArgumentSegment that = (DecimalArgumentSegment)obj;
            return Objects.equals(this.pattern, that.pattern);
        }

        @Override
        public int hashCode() {
            return super.hashCode() ^ Objects.hashCode(this.pattern);
        }
    }

// ChoiceArgumentSegment

    /**
     * An {@link ArgumentSegment} that formats its argument using a {@link ChoiceFormat}.
     *
     * <p>
     * When a {@link MessageFormat} executes a nested {@link ChoiceFormat}, if the resulting string contains a <code>'{'</code>
     * then the string is recursively interpreted again as a {@link MessageFormat} pattern. Therefore, the semantics
     * of a {@link ChoiceFormat} differ when treated standalone vs. nested in a {@link MessageFormat}.
     *
     * <p>
     * This class models the latter scenario. Each option of the choice is modeled as a new, nested {@link MessageFormat}
     * (see {@link ChoiceArgumentSegment.Option}) regardless of whether <code>'{'</code> appeared in the original pattern for the
     * choice. This simplifies the model but also means that if the original choice pattern didn't contain <code>'{'</code>, then
     * the {@link MessageFormat} modeling that choice will have {@linkplain #escape escaping} applied to its format string,
     * preserving the net effect.
     *
     * @see ChoiceArgumentSegment.Option
     */
    public static class ChoiceArgumentSegment extends NumberFormatArgumentSegment<ChoiceFormat> {

        private List<Option> options;

    // Constructors

        public ChoiceArgumentSegment() {
            this.options = new ArrayList<>(0);
        }

        /**
         * Create an instance from explicitly given {@link Option}s.
         *
         * @param argumentNumber the index of the argument formatted by this segment
         * @param options choice options
         * @throws IllegalArgumentException if {@code options} or any element thereof is null
         */
        public ChoiceArgumentSegment(int argumentNumber, Option... options) {
            super(argumentNumber);
            if (options == null)
                throw new IllegalArgumentException("null options");
            this.options = new ArrayList<>(options.length);
            for (int i = 0; i < options.length; i++) {
                final Option option = options[i];
                if (option == null)
                    throw new IllegalArgumentException("null option");
                this.options.add(option);
            }
        }

        /**
         * Create an instance modeling the given {@link ChoiceFormat}.
         *
         * @param argumentNumber argument number
         * @param format source format
         * @throws IllegalArgumentException if {@code format} is null
         * @see #toChoiceFormat
         */
        public ChoiceArgumentSegment(int argumentNumber, ChoiceFormat format) {
            super(argumentNumber);

            // Sanity check
            if (format == null)
                throw new IllegalArgumentException("null format");

            // When a MessageFormat executes a nested ChoiceFormat, if the resulting string contains a '{' then
            // the string is recursively interpreted as a MessageFormat pattern. But in the code below, we interpret
            // all choices as MessageFormat patterns. Therefore, if a nested ChoiceFormat pattern does not contain
            // a '{', then we apply an extra level of escaping to ensure it remains as plain text when interpreted
            // as a MessageFormat pattern.

            // Get limits and formats
            final double[] limits = format.getLimits();
            final String[] formats = (String[])format.getFormats();
            assert limits.length == formats.length;
            this.options = new ArrayList<>(limits.length);
            for (int i = 0; i < limits.length; i++) {
                String choice = formats[i];
                if (choice.indexOf('{') == -1)
                    choice = MessageFmt.escape(choice);
                this.options.add(new Option(limits[i], new MessageFmt(new MessageFormat(choice))));
            }
        }

    // Properties

        /**
         * Get the possible options.
         *
         * @return options for choice
         */
        @NotNull
        @Size(min = 1)
        @Valid
        public List<@NotNull Option> getOptions() {
            return this.options;
        }
        public void setOptions(final List<Option> options) {
            this.options = options;
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
                formats[i] = option.getFormat().toPattern();
                if (formats[i].indexOf('{') == -1)                  // this is the reverse of the constructor logic (see comment)
                    formats[i] = MessageFmt.unescape(formats[i]);
            }
            return new ChoiceFormat(limits, formats);
        }

    // ArgumentSegment

        @Override
        protected String getArgumentSuffix() {
            return "choice," + this.toChoiceFormat().toPattern();
        }

    // Segment

        @Override
        public void visit(SegmentSwitch target) {
            target.caseChoiceArgumentSegment(this);
        }

    // Object

        @Override
        public boolean equals(Object obj) {
            if (obj == this)
                return true;
            if (!super.equals(obj))
                return false;
            final ChoiceArgumentSegment that = (ChoiceArgumentSegment)obj;
            return Objects.equals(this.options, that.options);
        }

        @Override
        public int hashCode() {
            return super.hashCode() ^ Objects.hashCode(this.options);
        }

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
             * Get the limit for this {@link ChoiceArgumentSegment} option in string form.
             *
             * <p>
             * This property is just an alternative view of {@link #getLimit} that uses the {@link ChoiceFormat} string syntax,
             * which includes a trailing {@code "<"} or {@code "#"} character.
             *
             * <p>
             * For example, a limit of {@code 1.0} is described as {@code "1.0#"} while a limit of
             * {@link ChoiceFormat#nextDouble ChoiceFormat.nextDouble}{@code (1.0)} is described as {@code "1.0<"}.
             *
             * @return argument upper bound (inclusive) for this option
             */
            public String getLimitDescription() {
                return new ChoiceFormat(this.limit + "#").toPattern();
            }

            /**
             * Set the limit for this {@link ChoiceArgumentSegment} option in string form.
             *
             * <p>
             * If there is no trailing {@code "<"} or {@code "#"} character, then {@code "#"} is assumed.
             *
             * @param limitDescription string description of limit
             */
            public void setLimitDescription(String limitDescription) {
                if (limitDescription == null)
                    throw new IllegalArgumentException("null limitDescription");
                if (limitDescription.indexOf('#') == -1 && limitDescription.indexOf('<') == -1)
                    limitDescription += "#";
                final double[] limits = new ChoiceFormat(limitDescription).getLimits();
                if (limits.length != 1)
                    throw new IllegalArgumentException("invalid limitDescription");
                this.limit = limits[0];
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
            @NotNull
            @Valid
            public MessageFmt getFormat() {
                return this.format;
            }
            public void setFormat(final MessageFmt format) {
                this.format = format;
            }

        // Object

            @Override
            public boolean equals(Object obj) {
                if (obj == this)
                    return true;
                if (obj == null || obj.getClass() != this.getClass())
                    return false;
                final Option that = (Option)obj;
                return Double.compare(this.limit, that.limit) == 0
                  && Objects.equals(this.format, that.format);
            }

            @Override
            public int hashCode() {
                return Double.hashCode(this.limit) ^ Objects.hashCode(this.format);
            }

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
    @SelfValidates
    public static class SimpleDateFormatArgumentSegment extends DateFormatArgumentSegment<SimpleDateFormat>
      implements SelfValidating {

        private String pattern;

        public SimpleDateFormatArgumentSegment() {
        }

        public SimpleDateFormatArgumentSegment(int argumentNumber, SimpleDateFormat format) {
            super(argumentNumber);
            if (format == null)
                throw new IllegalArgumentException("null format");
            this.pattern = format.toPattern();
        }

    // Properties

        @NotNull
        public String getPattern() {
            return this.pattern;
        }
        public void setPattern(final String pattern) {
            this.pattern = pattern;
        }

    // ArgumentSegment

        @Override
        protected String getArgumentSuffix() {
            return "date," + this.pattern;
        }

    // Segment

        @Override
        public void visit(SegmentSwitch target) {
            target.caseSimpleDateFormatArgumentSegment(this);
        }

    // Validation

        @Override
        public void checkValid(ConstraintValidatorContext context) throws SelfValidationException {
            if (this.pattern != null) {
                try {
                    new SimpleDateFormat(this.pattern);
                } catch (IllegalArgumentException e) {
                    throw new SelfValidationException("invalid SimpleDateFormat pattern \"" + this.pattern + "\"", e);
                }
            }
        }

    // Object

        @Override
        public boolean equals(Object obj) {
            if (obj == this)
                return true;
            if (!super.equals(obj))
                return false;
            final SimpleDateFormatArgumentSegment that = (SimpleDateFormatArgumentSegment)obj;
            return Objects.equals(this.pattern, that.pattern);
        }

        @Override
        public int hashCode() {
            return super.hashCode() ^ Objects.hashCode(this.pattern);
        }
    }

// AbstractStandardDateFormatArgumentSegment

    abstract static class AbstractStandardDateFormatArgumentSegment extends DateFormatArgumentSegment<DateFormat> {

        private DateFormatStandard standard;

        protected AbstractStandardDateFormatArgumentSegment() {
        }

        protected AbstractStandardDateFormatArgumentSegment(int argumentNumber, DateFormatStandard standard) {
            super(argumentNumber);
            this.standard = standard;
        }

    // Properties

        @NotNull
        public DateFormatStandard getStandard() {
            return this.standard;
        }
        public void setStandard(final DateFormatStandard standard) {
            this.standard = standard;
        }

    // ArgumentSegment

        @Override
        protected String getArgumentSuffix() {
            String result = this.getKeyword();
            if (this.standard != null && !this.standard.equals(DateFormatStandard.DEFAULT))
                result += "," + this.standard.description();
            return result;
        }

        protected abstract String getKeyword();

    // Object

        @Override
        public boolean equals(Object obj) {
            if (obj == this)
                return true;
            if (!super.equals(obj))
                return false;
            final AbstractStandardDateFormatArgumentSegment that = (AbstractStandardDateFormatArgumentSegment)obj;
            return Objects.equals(this.standard, that.standard);
        }

        @Override
        public int hashCode() {
            return super.hashCode() ^ Objects.hashCode(this.standard);
        }
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

    // AbstractStandardDateFormatArgumentSegment

        @Override
        protected String getKeyword() {
            return "date";
        }

    // Segment

        @Override
        public void visit(SegmentSwitch target) {
            target.caseStandardDateFormatArgumentSegment(this);
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

    // AbstractStandardDateFormatArgumentSegment

        @Override
        protected String getKeyword() {
            return "time";
        }

    // Segment

        @Override
        public void visit(SegmentSwitch target) {
            target.caseStandardTimeFormatArgumentSegment(this);
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
