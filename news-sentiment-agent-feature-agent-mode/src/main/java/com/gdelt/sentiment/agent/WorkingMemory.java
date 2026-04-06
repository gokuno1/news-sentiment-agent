package com.gdelt.sentiment.agent;

import com.gdelt.sentiment.model.*;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * Per-request working memory that tracks findings across macro dimensions.
 * Thread-safe for concurrent tool execution.
 */
public class WorkingMemory {

    private final Map<MacroDimension, List<Finding>> findings = new ConcurrentHashMap<>();
    private volatile boolean finalViewSubmitted = false;
    private volatile MacroView submittedView = null;
    private volatile boolean gdeltAutoFallbackUsed = false;

    public void storeFinding(Finding finding) {
        findings.computeIfAbsent(finding.dimension(), k -> new CopyOnWriteArrayList<>())
                .add(finding);
    }

    public Set<MacroDimension> coveredDimensions() {
        return findings.entrySet().stream()
                .filter(e -> !e.getValue().isEmpty())
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    public Set<MacroDimension> gaps() {
        Set<MacroDimension> covered = coveredDimensions();
        return Arrays.stream(MacroDimension.values())
                .filter(d -> !covered.contains(d))
                .collect(Collectors.toSet());
    }

    public List<String> contradictions() {
        List<String> result = new ArrayList<>();
        for (var entry : findings.entrySet()) {
            List<Finding> dimFindings = entry.getValue();
            if (dimFindings.size() < 2) continue;

            Set<String> signals = dimFindings.stream()
                    .map(Finding::signal)
                    .map(String::toLowerCase)
                    .collect(Collectors.toSet());

            boolean hasBullish = signals.stream().anyMatch(s -> s.contains("bullish") || s.contains("positive"));
            boolean hasBearish = signals.stream().anyMatch(s -> s.contains("bearish") || s.contains("negative"));
            if (hasBullish && hasBearish) {
                result.add(entry.getKey().getDisplayName() + ": conflicting signals (bullish vs bearish)");
            }
        }
        return result;
    }

    public int totalFindings() {
        return findings.values().stream().mapToInt(List::size).sum();
    }

    public CoverageReport buildCoverageReport() {
        return new CoverageReport(coveredDimensions(), gaps(), contradictions(), totalFindings());
    }

    /**
     * Human-readable coverage snapshot auto-injected into the agent conversation.
     */
    public String getCoverageSnapshot() {
        StringBuilder sb = new StringBuilder();
        Set<MacroDimension> covered = coveredDimensions();
        Set<MacroDimension> gapSet = gaps();

        sb.append("COVERED (").append(covered.size()).append("/")
                .append(MacroDimension.values().length).append(" dimensions, ")
                .append(totalFindings()).append(" total findings):\n");

        for (MacroDimension dim : MacroDimension.values()) {
            List<Finding> dimFindings = findings.getOrDefault(dim, List.of());
            if (!dimFindings.isEmpty()) {
                String signals = dimFindings.stream()
                        .map(Finding::signal)
                        .distinct()
                        .collect(Collectors.joining(", "));
                sb.append("  - ").append(dim.getDisplayName())
                        .append(": ").append(dimFindings.size())
                        .append(" findings [").append(signals).append("]\n");
            }
        }

        if (!gapSet.isEmpty()) {
            sb.append("\nGAPS (no findings yet):\n");
            for (MacroDimension dim : gapSet) {
                sb.append("  - ").append(dim.getDisplayName())
                        .append(" (").append(dim.getDescription()).append(")\n");
            }
        }

        List<String> contras = contradictions();
        if (!contras.isEmpty()) {
            sb.append("\nCONTRADICTIONS:\n");
            for (String c : contras) {
                sb.append("  - ").append(c).append("\n");
            }
        }

        return sb.toString();
    }

    public void submitFinalView(MacroView view) {
        this.submittedView = view;
        this.finalViewSubmitted = true;
    }

    public boolean isFinalViewSubmitted() {
        return finalViewSubmitted;
    }

    /**
     * Build the MacroView from accumulated findings.
     * Used when the agent calls submitMacroView or as a fallback at loop end.
     */
    public MacroView buildMacroView(String topic) {
        if (submittedView != null) {
            return submittedView;
        }

        List<DimensionAssessment> assessments = new ArrayList<>();
        for (MacroDimension dim : MacroDimension.values()) {
            List<Finding> dimFindings = findings.getOrDefault(dim, List.of());
            if (dimFindings.isEmpty()) continue;

            String dominantSignal = dimFindings.stream()
                    .collect(Collectors.groupingBy(Finding::signal, Collectors.counting()))
                    .entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse("unclear");

            double avgScore = mapSignalToScore(dominantSignal);

            String summary = dimFindings.stream()
                    .map(Finding::evidence)
                    .filter(e -> !e.isBlank())
                    .limit(3)
                    .collect(Collectors.joining("; "));

            List<String> sources = dimFindings.stream()
                    .flatMap(f -> f.sources().stream())
                    .distinct()
                    .limit(5)
                    .toList();

            assessments.add(new DimensionAssessment(dim, dominantSignal, avgScore, summary, sources, "both"));
        }

        double overallScore = assessments.isEmpty() ? 0.0 :
                assessments.stream().mapToDouble(DimensionAssessment::score).average().orElse(0.0);

        return new MacroView(
                topic,
                assessments,
                overallScore,
                "Auto-assembled from " + totalFindings() + " findings across " + assessments.size() + " dimensions.",
                List.of(),
                contradictions(),
                Instant.now()
        );
    }

    private double mapSignalToScore(String signal) {
        if (signal == null) return 0.0;
        String s = signal.toLowerCase();
        if (s.contains("bullish") || s.contains("positive") || s.contains("expansionary")) return 0.5;
        if (s.contains("bearish") || s.contains("negative") || s.contains("contractionary")) return -0.5;
        if (s.contains("hawkish") || s.contains("tightening")) return -0.3;
        if (s.contains("dovish") || s.contains("easing")) return 0.3;
        return 0.0;
    }

    public Map<MacroDimension, List<Finding>> getAllFindings() {
        return Collections.unmodifiableMap(findings);
    }

    public boolean isGdeltAutoFallbackUsed() {
        return gdeltAutoFallbackUsed;
    }

    public void markGdeltAutoFallbackUsed() {
        this.gdeltAutoFallbackUsed = true;
    }
}
