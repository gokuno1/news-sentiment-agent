package com.gdelt.sentiment.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gdelt.sentiment.tool.RateLimitedExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class TavilyClient {

    private static final Logger log = LoggerFactory.getLogger(TavilyClient.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String apiKey;
    private final String baseUrl;
    private final String searchDepth;
    private final int maxResults;
    private final HttpClient httpClient;

    public TavilyClient(String apiKey, String baseUrl, String searchDepth, int maxResults) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl != null ? baseUrl : "https://api.tavily.com";
        this.searchDepth = searchDepth != null ? searchDepth : "advanced";
        this.maxResults = maxResults > 0 ? maxResults : 10;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    public record SearchResult(String title, String url, String content, double score) {}

    /**
     * Search Tavily for the given query. Returns structured results.
     * Throws RateLimitException on 429 for retry by RateLimitedExecutor.
     */
    public List<SearchResult> search(String query, int maxResults) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("[Tavily] No API key configured, skipping search");
            return List.of();
        }

        try {
            String body = MAPPER.writeValueAsString(Map.of(
                    "api_key", apiKey,
                    "query", query,
                    "search_depth", searchDepth,
                    "max_results", maxResults > 0 ? maxResults : this.maxResults,
                    "include_answer", false
            ));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/search"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .timeout(Duration.ofSeconds(30))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 429) {
                throw new RateLimitedExecutor.RateLimitException("Tavily API rate limited (429)");
            }

            if (response.statusCode() != 200) {
                log.warn("[Tavily] API returned {}: {}", response.statusCode(), response.body());
                return List.of();
            }

            return parseResults(response.body());
        } catch (RateLimitedExecutor.RateLimitException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[Tavily] Search failed: {}", e.getMessage());
            return List.of();
        }
    }

    public List<SearchResult> search(String query) {
        return search(query, this.maxResults);
    }

    private List<SearchResult> parseResults(String responseBody) {
        List<SearchResult> results = new ArrayList<>();
        try {
            JsonNode root = MAPPER.readTree(responseBody);
            JsonNode resultsNode = root.get("results");
            if (resultsNode == null || !resultsNode.isArray()) return results;

            for (JsonNode item : resultsNode) {
                String title = item.has("title") ? item.get("title").asText() : "";
                String url = item.has("url") ? item.get("url").asText() : "";
                String content = item.has("content") ? item.get("content").asText() : "";
                double score = item.has("score") ? item.get("score").asDouble() : 0.0;
                results.add(new SearchResult(title, url, content, score));
            }
        } catch (Exception e) {
            log.warn("[Tavily] Failed to parse response: {}", e.getMessage());
        }
        return results;
    }
}
