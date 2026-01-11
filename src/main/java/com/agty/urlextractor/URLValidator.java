package com.agty.urlextractor;

import java.net.MalformedURLException;
import java.net.URL;

/**
 * Utility class for validating URLs before storing them in JobOpportunity objects.
 */
public class URLValidator {

    /**
     * Validates a URL string and returns a validation result
     *
     * @param url The URL string to validate
     * @return ValidationResult containing validation status and error message if invalid
     */
    public static ValidationResult validate(String url) {
        if (url == null || url.trim().isEmpty()) {
            return new ValidationResult(false, "URL is null or empty");
        }

        String trimmedUrl = url.trim();

        // Check for valid protocol
        if (!trimmedUrl.startsWith("http://") && !trimmedUrl.startsWith("https://")) {
            return new ValidationResult(false, "URL must start with http:// or https://");
        }

        // Try to parse the URL
        try {
            URL parsedUrl = new URL(trimmedUrl);

            // Additional checks
            if (parsedUrl.getHost() == null || parsedUrl.getHost().isEmpty()) {
                return new ValidationResult(false, "URL has no host");
            }

            // Check for common malformed patterns
            if (trimmedUrl.contains(" ")) {
                return new ValidationResult(false, "URL contains spaces");
            }

            return new ValidationResult(true, null);
        } catch (MalformedURLException e) {
            return new ValidationResult(false, "Malformed URL: " + e.getMessage());
        }
    }

    /**
     * Result of URL validation
     */
    public static class ValidationResult {
        private final boolean valid;
        private final String errorMessage;

        public ValidationResult(boolean valid, String errorMessage) {
            this.valid = valid;
            this.errorMessage = errorMessage;
        }

        public boolean isValid() {
            return valid;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        @Override
        public String toString() {
            return valid ? "Valid" : "Invalid: " + errorMessage;
        }
    }
}
