package com.gdelt.sentiment.tool;

import com.gdelt.sentiment.agent.WorkingMemory;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Dispatches ToolExecutionRequests from Claude to the appropriate tool implementation.
 */
public class ToolRouter {

    private static final Logger log = LoggerFactory.getLogger(ToolRouter.class);

    private final SearchNewsTool searchNewsTool;
    private final GetEconomicIndicatorTool getEconomicIndicatorTool;
    private final ReadFullArticleTool readFullArticleTool;
    private final StoreFindingTool storeFindingTool;
    private final CheckCoverageTool checkCoverageTool;
    private final SubmitMacroViewTool submitMacroViewTool;
    private final String topic;

    public ToolRouter(
            SearchNewsTool searchNewsTool,
            GetEconomicIndicatorTool getEconomicIndicatorTool,
            ReadFullArticleTool readFullArticleTool,
            String topic) {
        this.searchNewsTool = searchNewsTool;
        this.getEconomicIndicatorTool = getEconomicIndicatorTool;
        this.readFullArticleTool = readFullArticleTool;
        this.storeFindingTool = new StoreFindingTool();
        this.checkCoverageTool = new CheckCoverageTool();
        this.submitMacroViewTool = new SubmitMacroViewTool();
        this.topic = topic;
    }

    public List<ToolSpecification> allToolSpecs() {
        return List.of(
                SearchNewsTool.spec(),
                GetEconomicIndicatorTool.spec(),
                ReadFullArticleTool.spec(),
                StoreFindingTool.spec(),
                CheckCoverageTool.spec(),
                SubmitMacroViewTool.spec()
        );
    }

    public String execute(ToolExecutionRequest request, WorkingMemory memory) {
        String name = request.name();
        String args = request.arguments();

        log.debug("[ToolRouter] Executing tool: {} with args: {}", name,
                args.length() > 200 ? args.substring(0, 200) + "..." : args);

        return switch (name) {
            case SearchNewsTool.NAME -> searchNewsTool.execute(args, memory);
            case GetEconomicIndicatorTool.NAME -> getEconomicIndicatorTool.execute(args, memory);
            case ReadFullArticleTool.NAME -> readFullArticleTool.execute(args, memory);
            case StoreFindingTool.NAME -> storeFindingTool.execute(args, memory);
            case CheckCoverageTool.NAME -> checkCoverageTool.execute(args, memory);
            case SubmitMacroViewTool.NAME -> submitMacroViewTool.execute(args, memory, topic);
            default -> {
                log.warn("[ToolRouter] Unknown tool: {}", name);
                yield "{\"error\":\"Unknown tool: " + name + "\"}";
            }
        };
    }
}
