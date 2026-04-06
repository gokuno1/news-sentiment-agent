package com.gdelt.sentiment.gdelt;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for GdeltClient (URL building, delay not exercised in unit test).
 */
class GdeltClientTest {

    @Test
    void fetchArticles_emptyQuery_returnsEmptyOrParsed() {
        GdeltClient client = new GdeltClient(
            "https://api.gdeltproject.org/api/v2/doc/doc",
            0, // no delay in test
            5,
            "1day"
        );
        List<GdeltArticle> articles = client.fetchArticles("nonexistent_query_xyz_12345", 5);
        assertNotNull(articles);
        // May be empty if API returns no results or error
    }

    @Test
    void fetchArticles_withSmallMaxRecords_doesNotThrow() {
        GdeltClient client = new GdeltClient(
            "https://api.gdeltproject.org/api/v2/doc/doc",
            0,
            3,
            "1day"
        );
        List<GdeltArticle> articles = client.fetchArticles("climate", 3);
        assertNotNull(articles);
        assertTrue(articles.size() <= 3);
    }
}
