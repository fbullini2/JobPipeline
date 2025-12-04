package com.agty.utils;

import ai.djl.huggingface.tokenizers.Encoding;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import opennlp.tools.tokenize.SimpleTokenizer;

public class TokenEstimator {

    //todo use the proper OpenAI toekn estimation when using OenAI models/api

    public static int estimateTokenCount(String text) {
        try {
            // Load a pre-trained tokenizer (e.g., GPT-2 tokenizer)
            HuggingFaceTokenizer tokenizer = HuggingFaceTokenizer.newInstance("gpt2");

            // Encode the text into tokens
            Encoding encoding = tokenizer.encode(text);

            // Return the number of tokens
            return encoding.getTokens().length;
        } catch (Exception e) {
            e.printStackTrace();
            return -1; // Return an error code if something goes wrong
        }
    }

    public static int countWords(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        // Use OpenNLP's SimpleTokenizer
        SimpleTokenizer tokenizer = SimpleTokenizer.INSTANCE;

        // Tokenize the text
        String[] tokens = tokenizer.tokenize(text);

        // Return the number of tokens (words)
        return tokens.length;
    }

    public static int countNonWhitespaceCharacters(String str) {
        return str.replaceAll("\\s", "").length();
    }

    public static void main(String[] args) {
        String text = "This is an example sentence to estimate the number of tokens.";
        int estimatedTokens = estimateTokenCount(text);
        int countWords=countWords(text);
        System.out.println("Estimated token count: " + estimatedTokens+" Word Count: "+countWords);
    }
}
