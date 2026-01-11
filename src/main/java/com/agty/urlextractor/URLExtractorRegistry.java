package com.agty.urlextractor;

import java.util.ArrayList;
import java.util.List;

/**
 * Registry for managing URL extractors.
 * Maintains a list of specialized extractors and selects the appropriate one
 * based on the email source. Falls back to DefaultURLExtractor if no specific
 * handler is found.
 */
public class URLExtractorRegistry {

    private final List<URLExtractor> extractors;
    private final URLExtractor defaultExtractor;

    public URLExtractorRegistry() {
        this.extractors = new ArrayList<>();
        this.defaultExtractor = new DefaultURLExtractor();

        // Register specialized extractors
        registerExtractor(new CadreMploiURLExtractor());

        // Future extractors can be added here:
        // registerExtractor(new LinkedInURLExtractor());
        // registerExtractor(new IndeedURLExtractor());
        // registerExtractor(new ApecURLExtractor());
    }

    /**
     * Register a new URL extractor
     *
     * @param extractor The extractor to register
     */
    public void registerExtractor(URLExtractor extractor) {
        extractors.add(extractor);
        System.out.println("  ℹ Registered URL extractor: " + extractor.getExtractorName());
    }

    /**
     * Extract URLs from email using the appropriate extractor
     *
     * @param emailFrom    The "from" field of the email
     * @param emailSubject The subject of the email
     * @param emailContent The full content/body of the email
     * @return URLExtractionResult containing extracted URLs
     */
    public URLExtractionResult extractURLs(String emailFrom, String emailSubject, String emailContent) {
        // Find the first extractor that can handle this email
        for (URLExtractor extractor : extractors) {
            if (extractor.canHandle(emailFrom, emailSubject)) {
                System.out.println("  → Using extractor: " + extractor.getExtractorName());
                URLExtractionResult result = extractor.extractURLs(emailContent, emailSubject);

                // If extraction failed, fall back to LLM (return null)
                if (!result.isExtractionSuccess()) {
                    System.err.println("  ⚠ " + extractor.getExtractorName() + " failed, will use LLM fallback");
                    return null;
                }

                return result;
            }
        }

        // No specific extractor found, use default
        System.out.println("  → Using default URL extractor");
        URLExtractionResult result = defaultExtractor.extractURLs(emailContent, emailSubject);

        // If default extraction failed, return null to trigger LLM extraction
        if (!result.isExtractionSuccess()) {
            System.out.println("  ℹ Default extractor found no URLs, LLM will handle extraction");
            return null;
        }

        return result;
    }

    /**
     * Get the number of registered extractors (excluding default)
     */
    public int getExtractorCount() {
        return extractors.size();
    }
}
