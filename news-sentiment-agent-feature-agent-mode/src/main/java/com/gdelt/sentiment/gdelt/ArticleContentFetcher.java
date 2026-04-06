package com.gdelt.sentiment.gdelt;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Best-effort fetcher for full article HTML content, extracting a simple text body.
 * This is intentionally minimal to avoid new dependencies; it does not try to be
 * a perfect readability implementation.
 */
public class ArticleContentFetcher {

    private static final Logger LOG = Logger.getLogger(ArticleContentFetcher.class.getName());

    private final HttpClient httpClient;
    private final Duration timeout;
    private final int maxBytes;

    public ArticleContentFetcher() {
        this(HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build(), Duration.ofSeconds(20), 512 * 1024);
    }

    public ArticleContentFetcher(HttpClient httpClient, Duration timeout, int maxBytes) {
        this.httpClient = httpClient;
        this.timeout = timeout;
        this.maxBytes = maxBytes;
    }

    /**
     * Fetch and extract plain text content for the given URL.
     * Returns null on any failure.
     */
    public String fetchBody(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .timeout(timeout)
                .header("User-Agent", "gdelt-sentiment-agent/1.0")
                .build();
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) {
                return null;
            }
            byte[] bodyBytes = response.body();
            if (bodyBytes == null || bodyBytes.length == 0) {
                return null;
            }
            if (bodyBytes.length > maxBytes) {
                byte[] truncated = new byte[maxBytes];
                System.arraycopy(bodyBytes, 0, truncated, 0, maxBytes);
                bodyBytes = truncated;
            }
            String html = new String(bodyBytes, StandardCharsets.UTF_8);
            return extractTextFromHtml(html);
        } catch (Exception e) {
            LOG.log(Level.FINE, "Failed to fetch full article content from " + url, e);
            return null;
        }
    }

    /**
     * Very simple HTML -> text extraction: strips tags and collapses whitespace.
     * This is intentionally lightweight; for better results consider using Jsoup.
     */
    private String extractTextFromHtml(String html) {
        if (html == null || html.isBlank()) {
            return null;
        }
        // Remove scripts and styles
        String withoutScripts = html.replaceAll("(?is)<script[^>]*>.*?</script>", "")
            .replaceAll("(?is)<style[^>]*>.*?</style>", "");
        // Strip all remaining tags
        String text = withoutScripts.replaceAll("(?is)<[^>]+>", " ");
        // Decode basic HTML entities
        text = text.replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'");
        // Collapse whitespace
        text = text.replaceAll("\\s+", " ").trim();
        return text.isEmpty() ? null : text;
    }
}

