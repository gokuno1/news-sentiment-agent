package com.gdelt.sentiment.agent;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * Agent interface for agentic mode: a macroeconomist that uses tools to
 * research GDELT news and produce a macro sentiment assessment.
 */
public interface SentimentResearchAgent {

    @Agent(
        name = "macro-sentiment-agent",
        outputKey = "macroSentiment",
        description = "Macro economist agent that uses tools to gather news from GDELT and produce a macroeconomic sentiment assessment."
    )
    @UserMessage("""
        You are a macroeconomist interpreting news for economic and financial implications.

        You MUST use the available tools to do your work, but you MUST NOT spam them:
        - First, call searchGdeltNews once (and only call it again if the initial results are clearly
          insufficient) to gather recent GDELT articles relevant to the topic. In most cases you should
          not call searchGdeltNews more than once at a time.
        - Use getCollectedArticlesSummary to inspect what you have collected.
        - When you have enough evidence, call scoreSentiment to compute a macroeconomic sentiment
          assessment for the topic.
        - Finally, you MUST call submitFinalReport with your final analysis, score, and confidence.

        Do NOT provide your final answer directly in this chat response. The Java program reads only
        the result passed to submitFinalReport as the final answer.

        While using these tools, frame findings through these macro dimensions: growth, labor,
        inflation, monetary, fiscal, external, financial conditions, and the business cycle.
        Distinguish backward-looking vs forward-looking information and note which macro dimensions
        each supports. When apparent, note regions/countries and the type of source (central bank,
        market participant, government, media).

        Topic: {{topic}}
        """)
    String run(@V("topic") String topic);
}

