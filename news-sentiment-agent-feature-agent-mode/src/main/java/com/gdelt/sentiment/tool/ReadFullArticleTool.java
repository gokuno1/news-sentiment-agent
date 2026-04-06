package com.gdelt.sentiment.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gdelt.sentiment.agent.WorkingMemory;
import com.gdelt.sentiment.gdelt.ArticleContentFetcher;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReadFullArticleTool {

    private static final Logger log = LoggerFactory.getLogger(ReadFullArticleTool.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static final String NAME = "readFullArticle";

    private final ArticleContentFetcher fetcher;

    public ReadFullArticleTool(ArticleContentFetcher fetcher) {
        this.fetcher = fetcher;
    }

    public static ToolSpecification spec() {
        return ToolSpecification.builder()
                .name(NAME)
                .description("Fetch and read the full text content of a news article by URL. "
                        + "Use this when a snippet is interesting but insufficient for forming a view. "
                        + "Returns extracted plain text from the article page.")
                .parameters(JsonObjectSchema.builder()
                        .addProperty("url", JsonStringSchema.builder()
                                .description("The full URL of the article to read")
                                .build())
                        .required("url")
                        .build())
                .build();
    }

    public String execute(String arguments, WorkingMemory memory) {
        try {
            JsonNode args = MAPPER.readTree(arguments);
            String url = args.get("url").asText();

            log.info("[ReadFullArticle] Fetching: {}", url);

            String content = fetcher.fetchBody(url);
            if (content == null || content.isBlank()) {
                return "{\"url\":\"" + escapeJson(url) + "\",\"content\":null,"
                        + "\"message\":\"Could not extract content from this URL\"}";
            }

            String truncated = content.length() > 3000 ? content.substring(0, 3000) + "..." : content;

            return "{\"url\":\"" + escapeJson(url) + "\","
                    + "\"content\":\"" + escapeJson(truncated) + "\","
                    + "\"length\":" + content.length() + "}";
        } catch (Exception e) {
            log.warn("[ReadFullArticle] Failed: {}", e.getMessage());
            return "{\"error\":\"" + e.getMessage().replace("\"", "'") + "\"}";
        }
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }
}
