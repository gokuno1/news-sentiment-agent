package com.gdelt.sentiment.gdelt;

import com.gdelt.sentiment.config.AppConfig;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Client for GDELT Doc 2.0 API (artlist mode).
 * Enforces a configurable delay between consecutive calls to avoid rate limits.
 */
public class GdeltClient {

    private static final Logger LOG = Logger.getLogger(GdeltClient.class.getName());

    private final String baseUrl;
    private final int delaySeconds;
    private final int defaultMaxRecords;
    private final String defaultTimespan;
    private final HttpClient httpClient;

    /** Last time a request was made (per instance). */
    private long lastCallTimeMillis = 0;

    public GdeltClient() {
        this(AppConfig.getGdeltBaseUrl(), AppConfig.getGdeltDelaySeconds(),
             AppConfig.getGdeltMaxRecordsPerRequest(), AppConfig.getGdeltTimespan());
    }

    public GdeltClient(String baseUrl, int delaySeconds, int defaultMaxRecords, String defaultTimespan) {
        this.baseUrl = baseUrl == null ? "" : baseUrl.trim().replaceAll("\\?$", "");
        this.delaySeconds = Math.max(0, delaySeconds);
        this.defaultMaxRecords = Math.max(1, defaultMaxRecords);
        this.defaultTimespan = defaultTimespan != null ? defaultTimespan : "1week";
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();
    }

    /**
     * Fetch articles matching the query. Enforces delay before the request if needed.
     */
    public List<GdeltArticle> fetchArticles(String query, int maxRecords, String timespan) {
        String url = buildUrl(query, maxRecords, timespan);
        int maxAttempts = 2;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            enforceDelay();
            try {
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .timeout(Duration.ofSeconds(60))
                    .build();
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                lastCallTimeMillis = System.currentTimeMillis();

                int status = response.statusCode();
                if (status == 429 && attempt < maxAttempts) {
                    long backoffSeconds = Math.max(5L, delaySeconds);
                    LOG.warning("GDELT API returned 429 (rate limited); sleeping " + backoffSeconds
                        + " seconds before retrying (attempt " + (attempt + 1) + "/" + maxAttempts + ")");
                    try {
                        Thread.sleep(backoffSeconds * 1000L);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                    continue;
                }

                if (status != 200) {
                    LOG.warning("GDELT API returned " + status + ": " + response.body());
                    return List.of();
                }
                return parseResponse(response.body());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LOG.log(Level.WARNING, "GDELT request interrupted", e);
                return List.of();
            } catch (Exception e) {
                LOG.log(Level.WARNING, "GDELT request failed: " + url, e);
                return List.of();
            }
        }
        return List.of();
    }

    public List<GdeltArticle> fetchArticles(String query, int maxRecords) {
        return fetchArticles(query, maxRecords, defaultTimespan);
    }

    public List<GdeltArticle> fetchArticles(String query) {
        return fetchArticles(query, defaultMaxRecords, defaultTimespan);
    }

    private void enforceDelay() {
        if (delaySeconds <= 0) return;
        long now = System.currentTimeMillis();
        long elapsed = now - lastCallTimeMillis;
        long requiredWait = delaySeconds * 1000L;
        if (lastCallTimeMillis > 0 && elapsed < requiredWait) {
            try {
                Thread.sleep(requiredWait - elapsed);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private String buildUrl(String query, int maxRecords, String timespan) {
        String q = URLEncoder.encode(query, StandardCharsets.UTF_8);
        StringBuilder sb = new StringBuilder(baseUrl);
        sb.append(baseUrl.contains("?") ? "&" : "?");
        sb.append("query=").append(q);
        sb.append("&mode=artlist");
        // Use JSONFeed format so the top-level "items" array matches our parser
        sb.append("&format=jsonfeed");
        sb.append("&maxrecords=").append(Math.max(1, maxRecords));
        if (timespan != null && !timespan.isBlank()) {
            sb.append("&timespan=").append(URLEncoder.encode(timespan, StandardCharsets.UTF_8));
        }
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private List<GdeltArticle> parseResponse(String body) {
        List<GdeltArticle> out = new ArrayList<>();
        if (body == null || body.isBlank()) return out;
        try {
            Map<String, Object> json = JsonUtil.parseMap(body);
            if (json == null) return out;

            List<Map<String, Object>> items = (List<Map<String, Object>>) json.get("items");
            if (items == null) {
                // Some APIs return array at root
                if (body.trim().startsWith("[")) {
                    items = JsonUtil.parseList(body);
                }
            }
            if (items == null) return out;

            for (Map<String, Object> item : items) {
                String title = getString(item, "title");
                String url = getString(item, "url");
                if (url == null || url.isBlank()) continue;
                String snippet = getString(item, "content_text");
                if (snippet == null) snippet = getString(item, "content_html");
                if (snippet == null) snippet = getString(item, "desc");
                if (snippet == null) snippet = getString(item, "summary");
                out.add(new GdeltArticle(title, url, snippet));
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to parse GDELT response", e);
        }
        return out;
    }

    private static String getString(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (v == null) return null;
        return v.toString().trim();
    }

    /** JSON parsing via Jackson. */
    @SuppressWarnings("unchecked")
    private static final class JsonUtil {
        private static final com.fasterxml.jackson.databind.ObjectMapper MAPPER = new com.fasterxml.jackson.databind.ObjectMapper();

        static Map<String, Object> parseMap(String json) {
            try {
                return MAPPER.readValue(json, Map.class);
            } catch (Exception e) {
                return null;
            }
        }

        static List<Map<String, Object>> parseList(String json) {
            try {
                return MAPPER.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<List<Map<String, Object>>>() {});
            } catch (Exception e) {
                return null;
            }
        }
    }
}
