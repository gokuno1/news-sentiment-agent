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
        1. A detailed analysis (2–4 paragraphs) including:
           - Executive summary of the overall sentiment and tone.
           - Key themes, narratives, and recurring viewpoints across the coverage.
           - Notable positive vs negative angles, specific examples where relevant.
           - Geographic or source diversity if apparent (e.g., regional differences, outlet types).
           - Any notable outliers, dissenting voices, or consensus vs division.
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
        return new ResilientSentimentScorerAgent(model);
    }

    static SentimentScorerAgent create() {
        return ResilientSentimentScorerAgent.create();
    }
}
