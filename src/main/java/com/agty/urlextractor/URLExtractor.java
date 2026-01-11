package com.agty.urlextractor;

/**
 * Interface for extracting URLs from job opportunity emails.
 * Different implementations handle different email sources (Cadremploi, LinkedIn, etc.)
 */
public interface URLExtractor {

    /**
     * Check if this extractor can handle the given email source
     *
     * @param emailFrom    The "from" field of the email
     * @param emailSubject The subject of the email
     * @return true if this extractor can handle this email, false otherwise
     */
    boolean canHandle(String emailFrom, String emailSubject);

    /**
     * Extract URLs from the email content
     *
     * @param emailContent The full content/body of the email
     * @param emailSubject The subject of the email
     * @return URLExtractionResult containing extracted URLs and metadata
     */
    URLExtractionResult extractURLs(String emailContent, String emailSubject);

    /**
     * Get the name of this extractor (for logging purposes)
     *
     * @return Extractor name
     */
    String getExtractorName();
}
