package com.gdelt.sentiment.agent;

import com.gdelt.sentiment.gdelt.GdeltArticle;
import com.gdelt.sentiment.gdelt.GdeltClient;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for GdeltFetcherStep deduplication.
 */
class GdeltFetcherStepTest {

    @Test
    void fetchAndMerge_deduplicatesByUrl() {
        GdeltArticle a1 = new GdeltArticle("Title 1", "https://example.com/1", null);
        GdeltArticle a2 = new GdeltArticle("Title 2", "https://example.com/2", null);
        GdeltArticle a2dup = new GdeltArticle("Other", "https://example.com/2", "snippet");

        List<GdeltArticle> current = List.of(a1, a2);
        // Simulate fetcher returning list that includes a duplicate of a2
        // In real step, fetcher calls GDELT; here we test merge logic by using a mock that returns [a2dup, new]
        GdeltClient mockClient = new GdeltClient() {
            @Override
            public List<GdeltArticle> fetchArticles(String query, int maxRecords) {
                return List.of(
                    a2dup, // same URL as a2 - should be filtered
                    new GdeltArticle("New", "https://example.com/3", null)
                );
            }
        };
        GdeltFetcherStep step = new GdeltFetcherStep(mockClient);

        List<GdeltArticle> merged = step.fetchAndMerge("test", current, 0);

        assertEquals(3, merged.size()); // a1, a2, new
        long url2Count = merged.stream().filter(a -> "https://example.com/2".equals(a.getUrl())).count();
        assertEquals(1, url2Count, "URL 2 should appear only once");
    }

    @Test
    void fetchAndMerge_emptyCurrent_addsAll() {
        GdeltClient mockClient = new GdeltClient() {
            @Override
            public List<GdeltArticle> fetchArticles(String query, int maxRecords) {
                return List.of(
                    new GdeltArticle("A", "https://a.com", null),
                    new GdeltArticle("B", "https://b.com", null)
                );
            }
        };
        GdeltFetcherStep step = new GdeltFetcherStep(mockClient);
        List<GdeltArticle> merged = step.fetchAndMerge("q", List.of(), 0);
        assertEquals(2, merged.size());
    }
}
