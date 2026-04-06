package com.gdelt.sentiment.tool;

import com.gdelt.sentiment.agent.WorkingMemory;
import com.gdelt.sentiment.model.CoverageReport;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CheckCoverageTool {

    private static final Logger log = LoggerFactory.getLogger(CheckCoverageTool.class);

    public static final String NAME = "checkCoverage";

    public static ToolSpecification spec() {
        return ToolSpecification.builder()
                .name(NAME)
                .description("Check which macro dimensions have findings and which are still missing. "
                        + "Use this to evaluate your research progress and decide what to search next.")
                .parameters(JsonObjectSchema.builder().build())
                .build();
    }

    public String execute(String arguments, WorkingMemory memory) {
        CoverageReport report = memory.buildCoverageReport();
        String snapshot = memory.getCoverageSnapshot();

        log.info("[CheckCoverage] {}/{} dimensions covered, {} total findings",
                report.coveredDimensions().size(),
                report.coveredDimensions().size() + report.gaps().size(),
                report.totalFindings());

        return snapshot;
    }
}
