package com.gdelt.sentiment.model;

public record StockInfo(
        String tradingSymbol,
        String name,
        String isin,
        String instrumentKey,
        String exchange,
        double quantity,
        double averagePrice,
        double lastPrice
) {
    public StockInfo {
        if (tradingSymbol == null) tradingSymbol = "";
        if (name == null) name = "";
        if (isin == null) isin = "";
        if (instrumentKey == null) instrumentKey = "";
        if (exchange == null) exchange = "NSE";
    }
}
