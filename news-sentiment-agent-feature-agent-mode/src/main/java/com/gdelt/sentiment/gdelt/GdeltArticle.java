package com.gdelt.sentiment.gdelt;

import java.util.Objects;

/**
 * DTO for a single article from GDELT Doc 2.0 API (artlist mode).
 * Used for deduplication by URL.
 */
public final class GdeltArticle {

    private final String title;
    private final String url;
    private final String snippet;
    /** Optional full article content fetched from the article URL. */
    private String fullContent;

    public GdeltArticle(String title, String url, String snippet) {
        this.title = title != null ? title : "";
        this.url = url != null ? url : "";
        this.snippet = snippet != null ? snippet : "";
    }

    public String getTitle() {
        return title;
    }

    public String getUrl() {
        return url;
    }

    public String getSnippet() {
        return snippet;
    }

    /** Text for embedding/display: title + optional snippet. */
    public String getTextForEmbedding() {
        String body = fullContent;
        if (body == null || body.isBlank()) {
            body = snippet;
        }
        if (body != null && !body.isBlank()) {
            return title + "\n" + body;
        }
        return title;
    }

    public String getFullContent() {
        return fullContent;
    }

    public void setFullContent(String fullContent) {
        this.fullContent = fullContent;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GdeltArticle that = (GdeltArticle) o;
        return Objects.equals(url, that.url);
    }

    @Override
    public int hashCode() {
        return Objects.hash(url);
    }
}
