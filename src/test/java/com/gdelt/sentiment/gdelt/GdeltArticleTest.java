package com.gdelt.sentiment.gdelt;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GdeltArticleTest {

    @Test
    void equals_hashCode_byUrl() {
        GdeltArticle a1 = new GdeltArticle("Title A", "https://example.com/1", "snippet");
        GdeltArticle a2 = new GdeltArticle("Other title", "https://example.com/1", "other");
        GdeltArticle a3 = new GdeltArticle("Title A", "https://example.com/2", "snippet");

        assertEquals(a1, a2);
        assertEquals(a1.hashCode(), a2.hashCode());
        assertNotEquals(a1, a3);
        assertNotEquals(a1.hashCode(), a3.hashCode());
    }

    @Test
    void getTextForEmbedding_combinesTitleAndSnippet() {
        GdeltArticle a = new GdeltArticle("Gold Rises", "https://a.com", "China buying");
        assertTrue(a.getTextForEmbedding().contains("Gold Rises"));
        assertTrue(a.getTextForEmbedding().contains("China buying"));
    }

    @Test
    void getTextForEmbedding_noSnippet_returnsTitleOnly() {
        GdeltArticle a = new GdeltArticle("Title", "https://b.com", null);
        assertEquals("Title", a.getTextForEmbedding());
    }
}
