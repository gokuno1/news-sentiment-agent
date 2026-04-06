package com.gdelt.sentiment.model;

import java.util.List;

public record Finding(
        MacroDimension dimension,
        String signal,
        String evidence,
        List<String> sources
) {
    public Finding {
        if (dimension == null) throw new IllegalArgumentException("dimension is required");
        if (signal == null || signal.isBlank()) signal = "unclear";
        if (evidence == null) evidence = "";
        if (sources == null) sources = List.of();
    }
}
