package com.gdelt.sentiment.rag;

import com.gdelt.sentiment.config.AppConfig;
import com.gdelt.sentiment.gdelt.GdeltArticle;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * RAG service: stores news in an embedding store at a configurable path,
 * adding only unique articles by URL. Retrieves top-k segments by relevance to the query.
 */
public class NewsRagService {

    private final InMemoryEmbeddingStore<TextSegment> store;
    private final EmbeddingModel embeddingModel;
    private final Path storePath;
    private final Path urlsFilePath;
    private final Set<String> storedUrls = new HashSet<>();

    public NewsRagService() {
        this(AppConfig.getEmbeddingStorePath(),
             AppConfig.getOllamaBaseUrl(),
             AppConfig.getOllamaEmbeddingModelName());
    }

    public NewsRagService(Path basePath, String ollamaBaseUrl, String embeddingModelName) {
        this.storePath = basePath.resolve("embedding-store.json");
        this.urlsFilePath = basePath.resolve("stored-urls.txt");
        this.embeddingModel = OllamaEmbeddingModel.builder()
            .baseUrl(ollamaBaseUrl)
            .modelName(embeddingModelName)
            .build();
        this.store = loadOrCreateStore();
        loadStoredUrls();
    }

    private InMemoryEmbeddingStore<TextSegment> loadOrCreateStore() {
        if (Files.exists(storePath)) {
            try {
                return InMemoryEmbeddingStore.fromFile(storePath.toString());
            } catch (Exception e) {
                // fallback to new store
            }
        }
        return new InMemoryEmbeddingStore<>();
    }

    private void loadStoredUrls() {
        if (!Files.exists(urlsFilePath)) return;
        try {
            List<String> lines = Files.readAllLines(urlsFilePath);
            storedUrls.addAll(lines.stream().map(String::trim).filter(s -> !s.isBlank()).toList());
        } catch (IOException e) {
            // ignore
        }
    }

    private void persistStore() {
        try {
            Files.createDirectories(storePath.getParent());
            store.serializeToFile(storePath.toString());
        } catch (Exception e) {
            throw new RuntimeException("Failed to persist embedding store", e);
        }
    }

    private void persistUrls() {
        try {
            Files.createDirectories(urlsFilePath.getParent());
            Files.write(urlsFilePath, new ArrayList<>(storedUrls));
        } catch (IOException e) {
            throw new RuntimeException("Failed to persist stored URLs", e);
        }
    }

    /**
     * Add only articles whose URL is not already in the store.
     * Embeds each new article, adds to store, and persists store and URL set.
     */
    public void addNewsOnlyUnique(List<GdeltArticle> articles) {
        List<GdeltArticle> toAdd = new ArrayList<>();
        for (GdeltArticle a : articles) {
            String url = a.getUrl();
            if (url != null && !url.isBlank() && !storedUrls.contains(url)) {
                toAdd.add(a);
            }
        }
        if (toAdd.isEmpty()) return;

        for (GdeltArticle a : toAdd) {
            String text = a.getTextForEmbedding();
            if (text.isBlank()) continue;
//            TextSegment segment = TextSegment.from(text).metadata(Metadata.from("url", a.getUrl()));
            TextSegment segment = TextSegment.from(text, Metadata.from("url", a.getUrl()));
            Embedding embedding = embeddingModel.embed(segment).content();
            store.add(embedding, segment);
            storedUrls.add(a.getUrl());
        }
        persistStore();
        persistUrls();
    }

    /**
     * Retrieve top-k text segments most relevant to the query.
     */
    public List<String> retrieveRelevant(String query, int topK) {
        if (query == null || query.isBlank()) return List.of();
        Embedding queryEmbedding = embeddingModel.embed(query).content();
        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
            .queryEmbedding(queryEmbedding)
            .maxResults(Math.max(1, topK))
            .build();
        List<EmbeddingMatch<TextSegment>> matches = store.search(request).matches();
        return matches.stream()
            .map(m -> m.embedded().text())
            .collect(Collectors.toList());
    }

    public List<String> retrieveRelevant(String query) {
        return retrieveRelevant(query, AppConfig.getRagTopK());
    }
}
