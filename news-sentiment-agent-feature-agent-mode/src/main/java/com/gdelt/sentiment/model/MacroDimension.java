package com.gdelt.sentiment.model;

public enum MacroDimension {
    GROWTH("Growth", "GDP, PMI, industrial production, retail sales, housing starts"),
    LABOR("Labor", "Employment, unemployment, wages, jobless claims, participation rate"),
    INFLATION("Inflation", "CPI, PPI, PCE, commodity prices, food/energy prices, expectations"),
    MONETARY("Monetary", "Central bank rates, QE/QT, forward guidance, yield curve"),
    FISCAL("Fiscal", "Government spending, budget deficit, stimulus, tax policy"),
    EXTERNAL_TRADE("External/Trade", "Trade balance, tariffs, imports/exports, sanctions, FX, currency"),
    FINANCIAL_CONDITIONS("Financial Conditions", "Credit spreads, equity volatility, bank lending, safe haven flows, bond yields"),
    BUSINESS_CYCLE("Business Cycle", "Leading indicators, recession probability, consumer confidence, capex, inventories"),
    COMMODITIES("Commodities", "Gold, oil, copper, agricultural commodities, metals");

    private final String displayName;
    private final String description;

    MacroDimension(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }
}
