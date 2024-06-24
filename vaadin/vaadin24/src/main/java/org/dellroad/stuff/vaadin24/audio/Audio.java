
/*
 * Copyright (C) 2022 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.vaadin24.audio;

import com.google.common.base.Preconditions;
import com.vaadin.flow.component.HtmlComponent;
import com.vaadin.flow.component.HtmlContainer;
import com.vaadin.flow.component.Tag;
import com.vaadin.flow.component.Text;
import com.vaadin.flow.component.UI;

import java.net.URI;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Represents an HTML {@code &lt;audio&gt;} tag.
 *
 * <p>
 * By default this class enables display of the audio controls.
 */
@Tag("audio")
@SuppressWarnings("serial")
public class Audio extends HtmlContainer {

    private static final HashMap<Pattern, String> MIME_TYPES = new HashMap<>();
    static {
        MIME_TYPES.put(Pattern.compile("(?i).*\\.aif$"), "audio/x-aiff");
        MIME_TYPES.put(Pattern.compile("(?i).*\\.aifc$"), "audio/x-aiff");
        MIME_TYPES.put(Pattern.compile("(?i).*\\.aiff$"), "audio/x-aiff");
        MIME_TYPES.put(Pattern.compile("(?i).*\\.au$"), "audio/basic");
        MIME_TYPES.put(Pattern.compile("(?i).*\\.m3u$"), "audio/x-mpegurl");
        MIME_TYPES.put(Pattern.compile("(?i).*\\.m4a$"), "audio/mp4");
        MIME_TYPES.put(Pattern.compile("(?i).*\\.mid$"), "audio/mid");
        MIME_TYPES.put(Pattern.compile("(?i).*\\.mp3$"), "audio/mpeg");
        MIME_TYPES.put(Pattern.compile("(?i).*\\.mp4$"), "audio/mp4");
        MIME_TYPES.put(Pattern.compile("(?i).*\\.ogg$"), "audio/ogg");
        MIME_TYPES.put(Pattern.compile("(?i).*\\.rmi$"), "audio/mid");
        MIME_TYPES.put(Pattern.compile("(?i).*\\.snd$"), "audio/basic");
        MIME_TYPES.put(Pattern.compile("(?i).*\\.wav$"), "audio/wav");
    }

    private Text altText;

// Constructor

    /**
     * Constructor.
     */
    public Audio() {
        this.setShowControls(true);
    }

// Methods

    /**
     * Configure alternate text.
     *
     * @param text alternate text or null for none
     */
    public void setAltText(String text) {
        if (text != null) {
            if (this.altText != null)
                this.altText.setText(text);
            else {
                this.altText = new Text(text);
                this.add(text);
            }
        } else if (this.altText != null) {
            this.remove(this.altText);
            this.altText = null;
        }
    }

    /**
     * Add an audio source and attempt to automatically infer its MIME type based on the URI path suffix.
     *
     * @param uri audio source
     * @throws IllegalArgumentException if {@code uri} is null
     * @throws IllegalArgumentException if {@code uri}'s MIME type cannot be inferred
     */
    public void addSource(URI uri) {
        Preconditions.checkArgument(uri != null, "null uri");
        final String path = uri.getPath();
        Preconditions.checkArgument(path != null, "URI has no path");
        final String filename = path.substring(path.lastIndexOf("/") + 1);
        this.addSource(uri, this.inferMimeType(filename));
    }

    /**
     * Add an audio source with the specified URL and MIME type.
     *
     * @param uri audio source
     * @param mimeType audio MIME type
     * @throws IllegalArgumentException if either parameter is null
     */
    public void addSource(URI uri, String mimeType) {
        Preconditions.checkArgument(uri != null, "null uri");
        Preconditions.checkArgument(mimeType != null, "null mimeType");
        this.addSource(uri.toString(), mimeType);
    }

    /**
     * Add an audio source with the specified URL and MIME type.
     *
     * @param uri audio source
     * @param mimeType audio MIME type
     * @throws IllegalArgumentException if either parameter is null
     */
    public void addSource(String uri, String mimeType) {
        this.add(new Source(uri, mimeType));
    }

    /**
     * Remove all audio sources.
     */
    public void removeSources() {
        this.getChildren()
          .filter(Source.class::isInstance)
          .forEach(this::remove);
    }

    /**
     * Configure whether the audio should loop.
     *
     * @param loop true to loop, false to play once
     */
    public void setLoop(boolean loop) {
        this.setAttribute("loop", loop ? "true" : null);
    }

    /**
     * Configure whether the audio be muted.
     *
     * @param muted true for muted, false for unmuted
     */
    public void setMuted(boolean muted) {
        this.setAttribute("muted", muted ? "true" : null);
    }

    /**
     * Configure whether audio controls should be visible.
     *
     * @param controls true for visible audio controls, otherwise false
     */
    public void setShowControls(boolean controls) {
        this.setAttribute("controls", controls ? "true" : null);
    }

    /**
     * Configure what data to pre-load.
     *
     * @param preload data pre-load setting, or null to remove this option
     */
    public void setPreload(PreloadMode preload) {
        this.setAttribute("preload", preload);
    }

    /**
     * Start playing the audio.
     */
    public void play() {
        this.getUI()
          .map(UI::getPage)
          .ifPresent(page -> page.executeJs("$0.play()", this.getElement()));
    }

// Internal Methods

    protected void setAttribute(String name, Object value) {
        if (value != null)
            this.getElement().setAttribute(name, value.toString());
        else
            this.getElement().removeAttribute(name);
    }

    /**
     * Attempt to automatically infer an audio MIME type based on a file name.
     *
     * @param filename audio file name
     * @throws IllegalArgumentException if {@code filename}'s MIME type cannot be inferred
     * @throws IllegalArgumentException if {@code filename} is null
     */
    protected String inferMimeType(String filename) {
        Preconditions.checkArgument(filename != null, "null filename");
        return MIME_TYPES.entrySet().stream()
          .filter(entry -> entry.getKey().matcher(filename).matches())
          .map(Map.Entry::getValue)
          .findFirst()
          .orElseThrow(() -> new IllegalArgumentException("can't infer MIME type for \"" + filename + "\""));
    }

// Source

    /**
     * Represents a {@code &lt;source&gt;} sub-element in an {@code &lt;audio&gt;} element.
     */
    @Tag("source")
    @SuppressWarnings("serial")
    public static class Source extends HtmlComponent {

        public Source(String uri, String mimeType) {
            Preconditions.checkArgument(uri != null, "null uri");
            Preconditions.checkArgument(mimeType != null, "null mimeType");
            this.getElement().setAttribute("src", uri);
            this.getElement().setAttribute("type", mimeType);
        }
    }

// PreloadMode

    /**
     * Data pre-load modes for HTML {@code &lt;audio&gt;} elements.
     */
    public enum PreloadMode {
        AUTO,
        METADATA,
        NONE;

        @Override
        public String toString() {
            return this.name().toLowerCase(Locale.ROOT);
        }
    }
}
