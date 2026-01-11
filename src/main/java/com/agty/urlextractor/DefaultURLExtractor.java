package com.agty.urlextractor;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Default/fallback URL extractor for emails without a specific extractor.
 * Extracts the first valid URL found in the email content.
 * Places it in jobOfferURLForDescriptionOnJobPortal by default.
 * The LLM will handle further classification.
 */
public class DefaultURLExtractor implements URLExtractor {

    // Generic URL pattern to match http/https URLs
    private static final Pattern URL_PATTERN = Pattern.compile(
            "https?://[^\\s\"'<>]+",
            Pattern.CASE_INSENSITIVE
    );

    @Override
    public boolean canHandle(String emailFrom, String emailSubject) {
        // This is the fallback extractor, it can handle any email
        return true;
    }

    @Override
    public URLExtractionResult extractURLs(String emailContent, String emailSubject) {
        URLExtractionResult result = new URLExtractionResult();
        result.setExtractionMethod("REGEX");

        if (emailContent == null || emailContent.trim().isEmpty()) {
            result.setExtractionSuccess(false);
            result.setErrorMessage("Email content is null or empty");
            return result;
        }

        try {
            Matcher matcher = URL_PATTERN.matcher(emailContent);

            if (matcher.find()) {
                String url = matcher.group(0);

                // Clean up the URL (remove trailing punctuation that might have been captured)
                url = cleanURL(url);

                // Validate the URL
                URLValidator.ValidationResult validation = URLValidator.validate(url);
                if (!validation.isValid()) {
                    result.setExtractionSuccess(false);
                    result.setErrorMessage("URL validation failed: " + validation.getErrorMessage());
                    return result;
                }

                // Place in description field by default (LLM will reclassify if needed)
                result.setJobOfferURLForDescriptionOnJobPortal(url);
                result.setExtractionSuccess(true);

                System.out.println("  ✓ Extracted URL using default extractor: " + url);
            } else {
                result.setExtractionSuccess(false);
                result.setErrorMessage("No URLs found in email content");
            }

        } catch (Exception e) {
            result.setExtractionSuccess(false);
            result.setErrorMessage("Exception during extraction: " + e.getMessage());
            System.err.println("⚠ Default URL extraction failed with exception: " + e.getMessage());
        }

        return result;
    }

    /**
     * Clean up URL by removing trailing punctuation and other noise
     */
    private String cleanURL(String url) {
        // Remove common trailing punctuation that regex might capture
        while (url.endsWith(".") || url.endsWith(",") || url.endsWith(")") ||
               url.endsWith(";") || url.endsWith(":") || url.endsWith("!")) {
            url = url.substring(0, url.length() - 1);
        }
        return url;
    }

    @Override
    public String getExtractorName() {
        return "DefaultURLExtractor";
    }
}
