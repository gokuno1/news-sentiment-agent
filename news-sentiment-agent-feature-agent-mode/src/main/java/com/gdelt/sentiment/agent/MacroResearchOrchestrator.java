package com.gdelt.sentiment.agent;

import com.gdelt.sentiment.model.MacroView;
import com.gdelt.sentiment.tool.ToolRouter;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.*;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/**
 * Cursor-like ReAct loop for macroeconomic research.
 *
 * Key behaviors:
 * 1. Safety cap (not research budget) -- safetyLimit prevents infinite loops but never cuts research short.
 * 2. Explicit termination only -- loop ONLY exits on submitMacroView.
 * 3. Auto-injected coverage state -- every N tool calls, coverage snapshot is injected.
 * 4. Parallel tool execution -- concurrent API calls via cached thread pool.
 * 5. Token optimization -- result compression + sliding window summarization.
 */
@Component
public class MacroResearchOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(MacroResearchOrchestrator.class);

    private final ChatModel chatModel;
    private final ToolRouterFactory toolRouterFactory;
    private final int safetyLimit;
    private final int tokenBudget;
    private final int coverageCheckInterval;
    private final int maxNudges;
    private final ExecutorService toolExecutor = Executors.newCachedThreadPool();

    public MacroResearchOrchestrator(
            ChatModel chatModel,
            ToolRouterFactory toolRouterFactory,
            @Value("${agent.safety-limit:50}") int safetyLimit,
            @Value("${agent.token-budget:25000}") int tokenBudget,
            @Value("${agent.coverage-check-interval:4}") int coverageCheckInterval,
            @Value("${agent.max-nudges:3}") int maxNudges) {
        this.chatModel = chatModel;
        this.toolRouterFactory = toolRouterFactory;
        this.safetyLimit = safetyLimit;
        this.tokenBudget = tokenBudget;
        this.coverageCheckInterval = coverageCheckInterval;
        this.maxNudges = maxNudges;
    }

    public MacroView research(String topic) {
        log.info("[Orchestrator] Starting research on: '{}'", topic);

        WorkingMemory memory = new WorkingMemory();
        ToolRouter router = toolRouterFactory.create(topic);
        List<ToolSpecification> toolSpecs = router.allToolSpecs();

        List<ChatMessage> messages = new ArrayList<>();
        messages.add(SystemMessage.from(MacroResearchPrompt.build()));
        messages.add(UserMessage.from("Research: " + topic));

        int toolCallsSinceReflection = 0;
        int nudgeCount = 0;
        int totalToolCalls = 0;

        for (int iteration = 0; iteration < safetyLimit; iteration++) {
            // AUTO-INJECT: coverage state every N tool calls
            if (toolCallsSinceReflection >= coverageCheckInterval && totalToolCalls > 0) {
                String snapshot = memory.getCoverageSnapshot();
                messages.add(UserMessage.from(
                        "[COVERAGE UPDATE]\n" + snapshot
                                + "\nContinue researching uncovered dimensions, "
                                + "or call submitMacroView if evidence is sufficient across all relevant dimensions."));
                toolCallsSinceReflection = 0;
                log.info("[Orchestrator] Injected coverage snapshot at iteration {}", iteration);
            }

            // TOKEN OPTIMIZATION: compact history if over budget
            compactIfNeeded(messages, memory);

            // Call Claude
            log.debug("[Orchestrator] Iteration {}: sending {} messages to Claude", iteration, messages.size());
            ChatResponse response;
            try {
                response = chatModel.chat(ChatRequest.builder()
                        .messages(messages)
                        .toolSpecifications(toolSpecs)
                        .build());
            } catch (Exception e) {
                log.error("[Orchestrator] Claude call failed at iteration {}: {}", iteration, e.getMessage());
                break;
            }

            AiMessage aiMessage = response.aiMessage();
            messages.add(aiMessage);

            List<ToolExecutionRequest> toolCalls = aiMessage.toolExecutionRequests();

            // EXPLICIT TERMINATION: only stop on submitMacroView
            if (toolCalls == null || toolCalls.isEmpty()) {
                if (memory.isFinalViewSubmitted()) {
                    log.info("[Orchestrator] Final view submitted at iteration {}", iteration);
                    break;
                }

                if (nudgeCount >= maxNudges) {
                    log.warn("[Orchestrator] Max nudges ({}) reached, forcing completion", maxNudges);
                    break;
                }

                nudgeCount++;
                log.info("[Orchestrator] No tool calls at iteration {}, nudging (nudge {}/{})",
                        iteration, nudgeCount, maxNudges);
                messages.add(UserMessage.from(
                        "You haven't submitted your macro view yet. "
                                + "Review your coverage and either search for more evidence on uncovered dimensions "
                                + "or call submitMacroView to finalize your assessment. "
                                + "Current coverage:\n" + memory.getCoverageSnapshot()));
                continue;
            }

            // PARALLEL EXECUTION: run all tool calls concurrently
            log.info("[Orchestrator] Iteration {}: executing {} tool call(s)", iteration, toolCalls.size());
            List<ToolExecutionResultMessage> results = executeInParallel(toolCalls, router, memory);
            messages.addAll(results);

            totalToolCalls += toolCalls.size();
            toolCallsSinceReflection += toolCalls.size();

            if (memory.isFinalViewSubmitted()) {
                log.info("[Orchestrator] Final view submitted after tool execution at iteration {}", iteration);
                break;
            }
        }

        MacroView view = memory.buildMacroView(topic);
        log.info("[Orchestrator] Research complete: {} dimensions, overall sentiment={}, total tool calls={}",
                view.dimensions().size(), view.overallSentiment(), totalToolCalls);

        return view;
    }

    private List<ToolExecutionResultMessage> executeInParallel(
            List<ToolExecutionRequest> toolCalls, ToolRouter router, WorkingMemory memory) {

        if (toolCalls.size() == 1) {
            ToolExecutionRequest req = toolCalls.get(0);
            String result = executeSingleTool(req, router, memory);
            String compressed = compressToolResult(req.name(), result);
            return List.of(ToolExecutionResultMessage.from(req, compressed));
        }

        List<CompletableFuture<ToolExecutionResultMessage>> futures = toolCalls.stream()
                .map(req -> CompletableFuture.supplyAsync(() -> {
                    String result = executeSingleTool(req, router, memory);
                    String compressed = compressToolResult(req.name(), result);
                    return ToolExecutionResultMessage.from(req, compressed);
                }, toolExecutor))
                .toList();

        return futures.stream()
                .map(f -> {
                    try {
                        return f.get(60, TimeUnit.SECONDS);
                    } catch (Exception e) {
                        log.warn("[Orchestrator] Parallel tool execution timed out: {}", e.getMessage());
                        return null;
                    }
                })
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    private String executeSingleTool(ToolExecutionRequest req, ToolRouter router, WorkingMemory memory) {
        try {
            long start = System.currentTimeMillis();
            String result = router.execute(req, memory);
            long elapsed = System.currentTimeMillis() - start;
            log.debug("[Orchestrator] Tool {} completed in {}ms", req.name(), elapsed);
            return result;
        } catch (Exception e) {
            log.warn("[Orchestrator] Tool {} failed: {}", req.name(), e.getMessage());
            return "{\"error\":\"Tool execution failed: " + e.getMessage().replace("\"", "'") + "\"}";
        }
    }

    /**
     * Compress verbose tool results before storing in message history.
     * Full results are already processed by the tool (storeFinding sees raw data).
     */
    private String compressToolResult(String toolName, String result) {
        if (result == null) return "{\"error\":\"null result\"}";

        switch (toolName) {
            case "searchNews":
                return compressSearchResult(result);
            case "getEconomicIndicator":
                return compressIndicatorResult(result);
            default:
                return result;
        }
    }

    private String compressSearchResult(String result) {
        if (result.length() <= 1500) return result;

        try {
            var node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(result);
            int count = node.has("count") ? node.get("count").asInt() : 0;
            String source = node.has("source") ? node.get("source").asText() : "unknown";

            StringBuilder sb = new StringBuilder();
            sb.append("{\"source\":\"").append(source).append("\",\"count\":").append(count);
            sb.append(",\"articles\":[");

            var articles = node.get("articles");
            if (articles != null && articles.isArray()) {
                int limit = Math.min(5, articles.size());
                for (int i = 0; i < limit; i++) {
                    if (i > 0) sb.append(",");
                    var art = articles.get(i);
                    sb.append("{\"title\":\"").append(truncateJson(art.has("title") ? art.get("title").asText() : "", 100)).append("\"");
                    sb.append(",\"url\":\"").append(art.has("url") ? art.get("url").asText() : "").append("\"}");
                }
                if (articles.size() > limit) {
                    sb.append(",{\"note\":\"").append(articles.size() - limit).append(" more articles omitted for brevity\"}");
                }
            }
            sb.append("]}");
            return sb.toString();
        } catch (Exception e) {
            return result.length() > 1500 ? result.substring(0, 1500) + "...(truncated)" : result;
        }
    }

    private String compressIndicatorResult(String result) {
        if (result.length() <= 800) return result;

        try {
            var node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(result);
            StringBuilder sb = new StringBuilder();
            sb.append("{\"indicator\":\"").append(node.has("indicator") ? node.get("indicator").asText() : "").append("\"");
            if (node.has("trend")) sb.append(",\"trend\":\"").append(node.get("trend").asText()).append("\"");
            if (node.has("latestValue")) sb.append(",\"latestValue\":").append(node.get("latestValue").asDouble());
            if (node.has("pctChange")) sb.append(",\"pctChange\":").append(node.get("pctChange").asText());

            var dataPoints = node.get("dataPoints");
            if (dataPoints != null && dataPoints.isArray()) {
                sb.append(",\"recentDataPoints\":[");
                int limit = Math.min(4, dataPoints.size());
                for (int i = 0; i < limit; i++) {
                    if (i > 0) sb.append(",");
                    sb.append("{\"date\":\"").append(dataPoints.get(i).get("date").asText())
                            .append("\",\"value\":\"").append(dataPoints.get(i).get("value").asText()).append("\"}");
                }
                sb.append("]");
            }
            sb.append("}");
            return sb.toString();
        } catch (Exception e) {
            return result.length() > 800 ? result.substring(0, 800) + "...(truncated)" : result;
        }
    }

    /**
     * Sliding window summarization: if message history exceeds token budget,
     * compact older messages while preserving working memory state.
     */
    private void compactIfNeeded(List<ChatMessage> messages, WorkingMemory memory) {
        int estimatedTokens = estimateTokenCount(messages);
        if (estimatedTokens <= tokenBudget) return;

        log.info("[Orchestrator] Token budget exceeded (~{}), compacting history", estimatedTokens);

        ChatMessage systemMsg = messages.get(0);

        int keepRecent = Math.min(6, messages.size() - 1);
        List<ChatMessage> recentMessages = new ArrayList<>(
                messages.subList(messages.size() - keepRecent, messages.size()));

        StringBuilder summary = new StringBuilder();
        summary.append("Research conducted so far (summarized):\n");
        summary.append("- Total findings: ").append(memory.totalFindings()).append("\n");
        summary.append("- Covered dimensions: ").append(memory.coveredDimensions().size())
                .append("/").append(com.gdelt.sentiment.model.MacroDimension.values().length).append("\n");
        summary.append("- Gaps: ").append(memory.gaps()).append("\n");

        var allFindings = memory.getAllFindings();
        for (var entry : allFindings.entrySet()) {
            summary.append("- ").append(entry.getKey().getDisplayName()).append(": ");
            summary.append(entry.getValue().stream()
                    .map(f -> f.signal())
                    .distinct()
                    .collect(java.util.stream.Collectors.joining(", ")));
            summary.append("\n");
        }

        messages.clear();
        messages.add(systemMsg);
        messages.add(UserMessage.from("[RESEARCH CONTEXT - COMPACTED]\n" + summary));
        messages.add(UserMessage.from("[CURRENT COVERAGE]\n" + memory.getCoverageSnapshot()));
        messages.addAll(recentMessages);

        log.info("[Orchestrator] Compacted to {} messages (~{} estimated tokens)",
                messages.size(), estimateTokenCount(messages));
    }

    private int estimateTokenCount(List<ChatMessage> messages) {
        int chars = 0;
        for (ChatMessage msg : messages) {
            if (msg instanceof SystemMessage sm) chars += sm.text().length();
            else if (msg instanceof UserMessage um) chars += um.singleText().length();
            else if (msg instanceof AiMessage am) chars += am.text() != null ? am.text().length() : 100;
            else if (msg instanceof ToolExecutionResultMessage tm) chars += tm.text().length();
        }
        return chars / 4;
    }

    private String truncateJson(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max).replace("\"", "'") + "..." : s.replace("\"", "'");
    }
}
