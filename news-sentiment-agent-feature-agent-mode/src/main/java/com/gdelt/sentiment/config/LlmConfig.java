package com.gdelt.sentiment.config;

import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.chat.ChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class LlmConfig {

    private static final Logger log = LoggerFactory.getLogger(LlmConfig.class);

    @Bean
    public ChatModel chatLanguageModel(
            @Value("${anthropic.api-key}") String apiKey,
            @Value("${anthropic.model-name:claude-sonnet-4-20250514}") String modelName,
            @Value("${anthropic.temperature:0.2}") double temperature,
            @Value("${anthropic.timeout-seconds:120}") int timeoutSeconds) {

        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "anthropic.api-key is required. Set ANTHROPIC_API_KEY env variable or anthropic.api-key property.");
        }

        log.info("Configuring Anthropic Claude: model={}, temperature={}, timeout={}s",
                modelName, temperature, timeoutSeconds);

        return AnthropicChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelName)
                .temperature(temperature)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .build();
    }
}
