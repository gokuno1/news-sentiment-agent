package com.gdelt.sentiment.agent;

import com.gdelt.sentiment.config.AppConfig;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * Sentiment scorer agent: analyzes news text and returns analysis, score, and confidence.
 * Uses Ollama (Mistral) via LangChain4j AiServices with structured output.
 */
public interface SentimentScorerAgent {

    @SystemMessage("""
        You are a sentiment analyst. Your task is to analyze a set of news article excerpts (titles and snippets) \
        related to a given topic and produce:
        1. A brief analysis with reasoning in 5 to 7 sentences.
        2. A sentiment score from -1.0 (very negative) to 1.0 (very positive).
        3. A confidence score from 0.0 to 1.0 indicating how confident you are in your sentiment assessment.
        Respond only with valid JSON with exactly these keys: "analysis", "score", "confidence".
        """)
    @UserMessage("""
        Topic: {{query}}
        News to analyze:
        {{newsText}}
        """)
    SentimentResult score(@V("query") String query, @V("newsText") String newsText);

    static SentimentScorerAgent create(ChatModel model) {
        return dev.langchain4j.service.AiServices.builder(SentimentScorerAgent.class)
            .chatModel(model)
            .build();
    }

    static SentimentScorerAgent create() {
        ChatModel model = OllamaChatModel.builder()
            .baseUrl(AppConfig.getOllamaBaseUrl())
            .modelName(AppConfig.getOllamaModelName())
            .temperature(0.2)
            .timeout(java.time.Duration.ofSeconds(120))
            .build();
        return create(model);
    }
}
