package com.gdelt.sentiment.config;

import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * Configuration for the GDELT Sentiment Agent.
 * Loads from application.properties (classpath), then system properties, env vars, or defaults.
 */
public final class AppConfig {

    private static final Properties PROPS = new Properties();

    static {
        try (InputStream in = AppConfig.class.getResourceAsStream("/application.properties")) {
            if (in != null) PROPS.load(in);
        } catch (Exception ignored) { /* use defaults */ }
    }

    private AppConfig() {}

    public static String getOllamaBaseUrl() {
        return get("ollama.baseUrl", "http://localhost:11434");
    }

    public static String getOllamaModelName() {
        return get("ollama.modelName", "mistral");
    }

    public static String getOllamaEmbeddingModelName() {
        return get("ollama.embeddingModelName", "nomic-embed-text");
    }

    public static String getGdeltBaseUrl() {
        return get("gdelt.baseUrl", "https://api.gdeltproject.org/api/v2/doc/doc");
    }

    public static int getGdeltDelaySeconds() {
        return Integer.parseInt(get("gdelt.delaySeconds", "5"));
    }

    public static int getGdeltMaxRecordsPerRequest() {
        return Integer.parseInt(get("gdelt.maxRecordsPerRequest", "50"));
    }

    public static String getGdeltTimespan() {
        return get("gdelt.timespan", "1week");
    }

    public static Path getEmbeddingStorePath() {
        return Paths.get(get("embedding.store.path", "./data/embedding-store"));
    }

    public static int getAgentMaxIterations() {
        return Integer.parseInt(get("agent.maxIterations", "5"));
    }

    public static double getAgentConfidenceThreshold() {
        return Double.parseDouble(get("agent.confidenceThreshold", "0.9"));
    }

    public static int getRagTopK() {
        return Integer.parseInt(get("rag.topK", "25"));
    }

    private static String get(String key, String defaultValue) {
        String env = System.getenv(key.replace('.', '_').toUpperCase());
        if (env != null && !env.isBlank()) return env.trim();
        String prop = System.getProperty(key);
        if (prop != null && !prop.isBlank()) return prop.trim();
        String fromFile = PROPS.getProperty(key);
        if (fromFile != null && !fromFile.isBlank()) return fromFile.trim();
        return defaultValue;
    }
}
