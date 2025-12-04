package com.agty.utils;

/**
 * Contains information about LLM API usage including tokens and costs
 */
public class LLMUsageInfo {
    private String response;
    private int inputTokens;
    private int outputTokens;
    private int totalTokens;
    private double costUSD;
    private String modelName;

    public LLMUsageInfo(String response, int inputTokens, int outputTokens, int totalTokens,
                        double costUSD, String modelName) {
        this.response = response;
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.totalTokens = totalTokens;
        this.costUSD = costUSD;
        this.modelName = modelName;
    }

    // For failed requests
    public LLMUsageInfo(String response) {
        this(response, 0, 0, 0, 0.0, "unknown");
    }

    public String getResponse() {
        return response;
    }

    public int getInputTokens() {
        return inputTokens;
    }

    public int getOutputTokens() {
        return outputTokens;
    }

    public int getTotalTokens() {
        return totalTokens;
    }

    public double getCostUSD() {
        return costUSD;
    }

    public String getModelName() {
        return modelName;
    }

    @Override
    public String toString() {
        return String.format("LLMUsageInfo{model=%s, inputTokens=%d, outputTokens=%d, totalTokens=%d, cost=$%.4f}",
                modelName, inputTokens, outputTokens, totalTokens, costUSD);
    }
}
