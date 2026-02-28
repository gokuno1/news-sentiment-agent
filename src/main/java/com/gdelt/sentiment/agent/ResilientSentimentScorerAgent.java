package com.gdelt.sentiment.agent;

import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.gdelt.sentiment.config.AppConfig;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.ollama.OllamaChatModel;

import java.util.List;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resilient sentiment scorer that handles LLM responses with unescaped newlines
 * in JSON string values (e.g. in "analysis"), which cause JsonParseException.
 * Uses Jackson's ALLOW_UNESCAPED_CONTROL_CHARS when parsing.
 */
public class ResilientSentimentScorerAgent implements SentimentScorerAgent {

    private static final Logger LOG = Logger.getLogger(ResilientSentimentScorerAgent.class.getName());

    private static final String SYSTEM_PROMPT = """
        You are a sentiment analyst. Your task is to analyze a set of news article excerpts (titles and snippets) \
        related to a given topic and produce:
        1. A detailed analysis (2–4 paragraphs) including:
           - Executive summary of the overall sentiment and tone.
           - Key themes, narratives, and recurring viewpoints across the coverage.
           - Notable positive vs negative angles, specific examples where relevant.
           - Geographic or source diversity if apparent (e.g., regional differences, outlet types).
           - Any notable outliers, dissenting voices, or consensus vs division.
        2. A sentiment score from -1.0 (very negative) to 1.0 (very positive).
        3. A confidence score from 0.0 to 1.0 indicating how confident you are in your sentiment assessment.
        Respond only with valid JSON with exactly these keys: "analysis", "score", "confidence".
        Use \\n for line breaks in the analysis string; do not use actual newline characters inside JSON values.
        """;

    private static final Pattern JSON_BLOCK = Pattern.compile("```(?:json)?\\s*([\\s\\S]*?)```", Pattern.CASE_INSENSITIVE);
    private static final Pattern JSON_OBJECT = Pattern.compile("\\s*\\{([\\s\\S]*)\\}\\s*");

    private final ChatModel chatModel;
    private final JsonMapper lenientMapper;

    public ResilientSentimentScorerAgent(ChatModel chatModel) {
        this.chatModel = chatModel;
        this.lenientMapper = JsonMapper.builder()
            .enable(JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS)
            .build();
    }

    @Override
    public SentimentResult score(String query, String newsText) {
        List<dev.langchain4j.data.message.ChatMessage> messages = List.of(
            SystemMessage.from(SYSTEM_PROMPT),
            UserMessage.from("Topic: " + query + "\nNews to analyze:\n" + newsText)
        );
        ChatResponse response = chatModel.chat(messages);
        String rawText = response.aiMessage().text();

        String json = extractJson(rawText);
        return parseToResult(json);
    }

    private String extractJson(String raw) {
        if (raw == null || raw.isBlank()) return "{}";
        Matcher block = JSON_BLOCK.matcher(raw);
        if (block.find()) return block.group(1).trim();
        Matcher obj = JSON_OBJECT.matcher(raw);
        if (obj.find()) return obj.group(0).trim();
        return raw.trim();
    }

    @SuppressWarnings("unchecked")
    private SentimentResult parseToResult(String json) {
        try {
            var map = lenientMapper.readValue(json, java.util.Map.class);
            String analysis = map != null && map.get("analysis") != null
                ? map.get("analysis").toString()
                : "";
            double score = map != null && map.get("score") != null
                ? toDouble(map.get("score"))
                : 0.0;
            double confidence = map != null && map.get("confidence") != null
                ? toDouble(map.get("confidence"))
                : 0.0;
            return new SentimentResult(analysis, score, confidence);
        } catch (Exception e) {
            LOG.warning("Failed to parse sentiment JSON: " + e.getMessage());
            return new SentimentResult("", 0.0, 0.0);
        }
    }

    private static double toDouble(Object o) {
        if (o instanceof Number n) return n.doubleValue();
        try {
            return Double.parseDouble(o.toString());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    static SentimentScorerAgent create() {
        ChatModel model = OllamaChatModel.builder()
            .baseUrl(AppConfig.getOllamaBaseUrl())
            .modelName(AppConfig.getOllamaModelName())
            .temperature(0.2)
            .timeout(java.time.Duration.ofSeconds(120))
            .build();
        return new ResilientSentimentScorerAgent(model);
    }
}
