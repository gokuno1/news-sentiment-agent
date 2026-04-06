package com.gdelt.sentiment.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gdelt.sentiment.agent.WorkingMemory;
import com.gdelt.sentiment.client.TavilyClient;
import com.gdelt.sentiment.gdelt.GdeltArticle;
import com.gdelt.sentiment.gdelt.GdeltClient;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonEnumSchema;
import dev.langchain4j.model.chat.request.json.JsonIntegerSchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class SearchNewsTool {

    private static final Logger log = LoggerFactory.getLogger(SearchNewsTool.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static final String NAME = "searchNews";

    private final GdeltClient gdeltClient;
    private final TavilyClient tavilyClient;
    private final RateLimitedExecutor executor;

    public SearchNewsTool(GdeltClient gdeltClient, TavilyClient tavilyClient, RateLimitedExecutor executor) {
        this.gdeltClient = gdeltClient;
        this.tavilyClient = tavilyClient;
        this.executor = executor;
    }

    public static ToolSpecification spec() {
        return ToolSpecification.builder()
                .name(NAME)
                .description("Search for macroeconomic news articles. "
                        + "Use source=GDELT for raw global news volume (keyword-based: use short 2-4 word queries). "
                        + "Use source=TAVILY for broader web coverage including analysis pieces (supports natural language). "
                        + "IMPORTANT: GDELT is keyword-based, NOT semantic search. "
                        + "BAD GDELT query: 'hawkish Federal Reserve stance on rising inflation'. "
                        + "GOOD GDELT query: 'Federal Reserve interest rate'.")
                .parameters(JsonObjectSchema.builder()
                        .addProperty("query", JsonStringSchema.builder()
                                .description("Search query string")
                                .build())
                        .addProperty("source", JsonEnumSchema.builder()
                                .enumValues(List.of("GDELT", "TAVILY"))
                                .description("GDELT for keyword-based news search, TAVILY for semantic web search")
                                .build())
                        .addProperty("maxResults", JsonIntegerSchema.builder()
                                .description("Maximum articles to return (default 25)")
                                .build())
                        .required("query", "source")
                        .build())
                .build();
    }

    public String execute(String arguments, WorkingMemory memory) {
        try {
            JsonNode args = MAPPER.readTree(arguments);
            String query = args.get("query").asText();
            String source = args.has("source") ? args.get("source").asText() : "GDELT";
            int maxResults = args.has("maxResults") ? args.get("maxResults").asInt() : 25;

            log.info("[SearchNews] source={}, query='{}', maxResults={}", source, query, maxResults);

            if ("TAVILY".equalsIgnoreCase(source)) {
                return searchTavily(query, maxResults);
            } else {
                return searchGdelt(query, maxResults, memory);
            }
        } catch (Exception e) {
            log.warn("[SearchNews] Failed: {}", e.getMessage());
            return "{\"error\":\"" + e.getMessage().replace("\"", "'") + "\"}";
        }
    }

    private String searchGdelt(String query, int maxResults, WorkingMemory memory) {
        String preprocessed = GdeltQueryPreprocessor.preprocess(query);
        log.info("[SearchNews] GDELT preprocessed query: '{}'", preprocessed);

        String result = executor.executeBlocking("GDELT", () -> {
            List<GdeltArticle> articles = gdeltClient.fetchArticles(preprocessed, maxResults);

            if (articles.isEmpty()) {
                return handleEmptyGdelt(query, preprocessed, maxResults, memory);
            }

            return formatGdeltResults(articles, "GDELT", preprocessed);
        });

        return result;
    }

    private String handleEmptyGdelt(String originalQuery, String preprocessed, int maxResults, WorkingMemory memory) {
        if (!memory.isGdeltAutoFallbackUsed()) {
            memory.markGdeltAutoFallbackUsed();
            log.info("[SearchNews] GDELT returned 0 results, auto-falling back to Tavily");

            List<TavilyClient.SearchResult> tavilyResults = tavilyClient.search(originalQuery, maxResults);
            if (!tavilyResults.isEmpty()) {
                return formatTavilyResults(tavilyResults) + "\n[NOTE: GDELT returned 0 results for '"
                        + preprocessed + "', auto-retried with Tavily using original query.]";
            }
        }

        List<String> alternatives = GdeltQueryPreprocessor.suggestAlternatives(originalQuery);
        StringBuilder sb = new StringBuilder();
        sb.append("{\"articles\":[],\"count\":0,\"source\":\"GDELT\",\"query\":\"").append(preprocessed).append("\",");
        sb.append("\"message\":\"GDELT returned 0 results. This likely means the keywords don't match GDELT's index.\",");
        sb.append("\"suggestions\":[");
        sb.append("\"Try shorter, more generic keywords\",");
        sb.append("\"Use source=TAVILY which handles natural language queries\"");
        if (!alternatives.isEmpty()) {
            sb.append(",\"Try these alternative queries: ").append(String.join(", ", alternatives)).append("\"");
        }
        sb.append("]}");
        return sb.toString();
    }

    private String searchTavily(String query, int maxResults) {
        return executor.executeBlocking("TAVILY", () -> {
            List<TavilyClient.SearchResult> results = tavilyClient.search(query, maxResults);
            return formatTavilyResults(results);
        });
    }

    private String formatGdeltResults(List<GdeltArticle> articles, String source, String query) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"source\":\"").append(source).append("\",\"query\":\"").append(escapeJson(query)).append("\",");
        sb.append("\"count\":").append(articles.size()).append(",\"articles\":[");
        for (int i = 0; i < articles.size(); i++) {
            GdeltArticle a = articles.get(i);
            if (i > 0) sb.append(",");
            sb.append("{\"title\":\"").append(escapeJson(a.getTitle())).append("\",");
            sb.append("\"url\":\"").append(escapeJson(a.getUrl())).append("\",");
            sb.append("\"snippet\":\"").append(escapeJson(truncate(a.getSnippet(), 200))).append("\"}");
        }
        sb.append("]}");
        return sb.toString();
    }

    private String formatTavilyResults(List<TavilyClient.SearchResult> results) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"source\":\"TAVILY\",\"count\":").append(results.size()).append(",\"articles\":[");
        for (int i = 0; i < results.size(); i++) {
            TavilyClient.SearchResult r = results.get(i);
            if (i > 0) sb.append(",");
            sb.append("{\"title\":\"").append(escapeJson(r.title())).append("\",");
            sb.append("\"url\":\"").append(escapeJson(r.url())).append("\",");
            sb.append("\"snippet\":\"").append(escapeJson(truncate(r.content(), 200))).append("\"}");
        }
        sb.append("]}");
        return sb.toString();
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) + "..." : s;
    }
}
