package com.agty.utils;

import java.util.HashMap;
import java.util.Map;

/**
 * Calculates the cost of LLM API calls based on token usage and model pricing.
 * Pricing is based on OpenAI's official pricing as of January 2025.
 *
 * Source: https://openai.com/api/pricing/
 *
 * Note: Input tokens and output tokens have different costs.
 * Output tokens are typically 2-4x more expensive than input tokens.
 */
public class LLMCostCalculator {

    // Pricing per 1 million tokens (USD)
    private static final Map<String, ModelPricing> MODEL_PRICING = new HashMap<>();

    static {
        // GPT-4o models
        MODEL_PRICING.put("gpt-4o", new ModelPricing(2.50, 10.00));
        MODEL_PRICING.put("gpt-4o-2024-11-20", new ModelPricing(2.50, 10.00));
        MODEL_PRICING.put("gpt-4o-2024-08-06", new ModelPricing(2.50, 10.00));
        MODEL_PRICING.put("gpt-4o-2024-05-13", new ModelPricing(5.00, 15.00));

        // GPT-4o-mini models (most cost-efficient)
        MODEL_PRICING.put("gpt-4o-mini", new ModelPricing(0.150, 0.600));
        MODEL_PRICING.put("gpt-4o-mini-2024-07-18", new ModelPricing(0.150, 0.600));

        // GPT-4 models
        MODEL_PRICING.put("gpt-4", new ModelPricing(30.00, 60.00));
        MODEL_PRICING.put("gpt-4-turbo", new ModelPricing(10.00, 30.00));
        MODEL_PRICING.put("gpt-4-turbo-2024-04-09", new ModelPricing(10.00, 30.00));
        MODEL_PRICING.put("gpt-4-turbo-preview", new ModelPricing(10.00, 30.00));
        MODEL_PRICING.put("gpt-4-1106-preview", new ModelPricing(10.00, 30.00));
        MODEL_PRICING.put("gpt-4-0125-preview", new ModelPricing(10.00, 30.00));

        // GPT-3.5 models
        MODEL_PRICING.put("gpt-3.5-turbo", new ModelPricing(0.50, 1.50));
        MODEL_PRICING.put("gpt-3.5-turbo-0125", new ModelPricing(0.50, 1.50));
        MODEL_PRICING.put("gpt-3.5-turbo-1106", new ModelPricing(1.00, 2.00));
        MODEL_PRICING.put("gpt-3.5-turbo-instruct", new ModelPricing(1.50, 2.00));
    }

    /**
     * Calculate cost for a single API call
     * @param modelName The model used
     * @param inputTokens Number of input tokens (prompt)
     * @param outputTokens Number of output tokens (completion)
     * @return Cost in USD
     */
    public static double calculateCost(String modelName, int inputTokens, int outputTokens) {
        ModelPricing pricing = MODEL_PRICING.get(modelName.toLowerCase());

        if (pricing == null) {
            // Default to GPT-4o-mini pricing if model not found
            System.err.println("Warning: Unknown model '" + modelName + "', using GPT-4o-mini pricing");
            pricing = MODEL_PRICING.get("gpt-4o-mini");
        }

        // Calculate cost: (tokens / 1,000,000) * price_per_million
        // Input tokens and output tokens have DIFFERENT costs
        double inputCost = (inputTokens / 1_000_000.0) * pricing.inputPricePerMillion;
        double outputCost = (outputTokens / 1_000_000.0) * pricing.outputPricePerMillion;

        return inputCost + outputCost;
    }

    /**
     * Format cost for display
     */
    public static String formatCost(double costUSD) {
        if (costUSD < 0.001) {
            return String.format("$%.6f", costUSD);
        } else if (costUSD < 0.01) {
            return String.format("$%.4f", costUSD);
        } else if (costUSD < 1.0) {
            return String.format("$%.3f", costUSD);
        } else {
            return String.format("$%.2f", costUSD);
        }
    }

    /**
     * Get pricing information for a model
     */
    public static ModelPricing getPricing(String modelName) {
        ModelPricing pricing = MODEL_PRICING.get(modelName.toLowerCase());
        if (pricing == null) {
            return MODEL_PRICING.get("gpt-4o-mini"); // default
        }
        return pricing;
    }

    /**
     * Inner class to hold pricing information
     */
    public static class ModelPricing {
        public final double inputPricePerMillion;
        public final double outputPricePerMillion;

        public ModelPricing(double inputPricePerMillion, double outputPricePerMillion) {
            this.inputPricePerMillion = inputPricePerMillion;
            this.outputPricePerMillion = outputPricePerMillion;
        }

        @Override
        public String toString() {
            return String.format("Input: $%.3f/1M tokens, Output: $%.3f/1M tokens",
                    inputPricePerMillion, outputPricePerMillion);
        }
    }

    /**
     * Aggregate multiple usage infos and calculate total cost
     */
    public static class CostSummary {
        private int totalInputTokens = 0;
        private int totalOutputTokens = 0;
        private int totalTokens = 0;
        private double totalCost = 0.0;
        private int apiCalls = 0;
        private Map<String, Integer> modelUsage = new HashMap<>();

        public void addUsage(LLMUsageInfo usage) {
            if (usage == null) return;

            totalInputTokens += usage.getInputTokens();
            totalOutputTokens += usage.getOutputTokens();
            totalTokens += usage.getTotalTokens();
            totalCost += usage.getCostUSD();
            apiCalls++;

            // Track model usage
            modelUsage.merge(usage.getModelName(), 1, Integer::sum);
        }

        public int getTotalInputTokens() { return totalInputTokens; }
        public int getTotalOutputTokens() { return totalOutputTokens; }
        public int getTotalTokens() { return totalTokens; }
        public double getTotalCost() { return totalCost; }
        public int getApiCalls() { return apiCalls; }
        public Map<String, Integer> getModelUsage() { return modelUsage; }

        public void printSummary() {
            System.out.println();
            System.out.println("╔════════════════════════════════════════════════════════════════════╗");
            System.out.println("║                    LLM COST SUMMARY                                ║");
            System.out.println("╚════════════════════════════════════════════════════════════════════╝");
            System.out.println();
            System.out.println("  💰 Total API Calls:       " + apiCalls);
            System.out.println("  📥 Total Input Tokens:    " + String.format("%,d", totalInputTokens));
            System.out.println("  📤 Total Output Tokens:   " + String.format("%,d", totalOutputTokens));
            System.out.println("  📊 Total Tokens:          " + String.format("%,d", totalTokens));
            System.out.println();
            System.out.println("  💵 TOTAL COST:            " + formatCost(totalCost) + " USD");
            System.out.println();

            if (!modelUsage.isEmpty()) {
                System.out.println("  🤖 Models Used:");
                for (Map.Entry<String, Integer> entry : modelUsage.entrySet()) {
                    System.out.println("    - " + entry.getKey() + ": " + entry.getValue() + " calls");
                }
            }

            System.out.println("═".repeat(70));
            System.out.println("Note: Pricing based on OpenAI official rates (Jan 2025)");
            System.out.println("      Output tokens cost more than input tokens");
            System.out.println("═".repeat(70));
        }
    }
}
