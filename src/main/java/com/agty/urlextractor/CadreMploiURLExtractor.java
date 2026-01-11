package com.agty.urlextractor;

import com.agty.JobOpportunity;
import com.agty.utils.GlobalConfig;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * URL extractor for Cadremploi job portal emails.
 * Handles emails from offres@alertes.cadremploi.fr
 *
 * Extracts job listings from HTML using regex patterns.
 * Cadremploi emails contain multiple job offers in HTML format.
 * Each job has a title and redirect URL.
 */
public class CadreMploiURLExtractor implements URLExtractor {

    // Pattern to extract job title and URL from <a> tags
    // Format: <a href="https://r.emails3.alertes.cadremploi.fr/tr/cl/..." ... title="Job Title">
    private static final Pattern JOB_LINK_PATTERN = Pattern.compile(
            "<a\\s+href=\"(https://r\\.emails[^\"]+\\.cadremploi\\.fr/tr/cl/[^\"]+)\"[^>]+title=\"([^\"]+)\"[^>]*>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );

    // False positive titles to filter out
    private static final Set<String> EXCLUDED_TITLES = new HashSet<>();
    static {
        EXCLUDED_TITLES.add("Cadremploi");
        EXCLUDED_TITLES.add("Facebook");
        EXCLUDED_TITLES.add("X");
        EXCLUDED_TITLES.add("Instagram");
        EXCLUDED_TITLES.add("Youtube");
        EXCLUDED_TITLES.add("LinkedIn");
        EXCLUDED_TITLES.add("Twitter");
    }

    @Override
    public boolean canHandle(String emailFrom, String emailSubject) {
        if (emailFrom == null) {
            return false;
        }
        return emailFrom.toLowerCase().contains("offres@alertes.cadremploi.fr");
    }

    @Override
    public URLExtractionResult extractURLs(String emailContent, String emailSubject) {
        URLExtractionResult result = new URLExtractionResult();
        result.setJobPortalName("Cadremploi");

        if (emailContent == null || emailContent.trim().isEmpty()) {
            result.setExtractionSuccess(false);
            result.setErrorMessage("Email content is null or empty");
            return result;
        }

        // Check if we should use LLM for HTML parsing
        if (GlobalConfig.USE_LLM_FOR_LONG_HTML) {
            System.out.println("  ℹ USE_LLM_FOR_LONG_HTML=true - delegating to LLM");
            result.setExtractionMethod("LLM");
            result.setExtractionSuccess(false);  // Trigger LLM fallback
            result.setErrorMessage("Configured to use LLM for HTML parsing");
            return result;
        }

        // Use regex extraction (default and preferred method)
        result.setExtractionMethod("REGEX");
        System.out.println("  → Using REGEX extraction for Cadremploi HTML email");

        // Note: This method is kept for interface compatibility,
        // but actual job extraction happens in extractJobOpportunities()
        // which returns full JobOpportunity objects
        result.setExtractionSuccess(false);  // Signal to use extractJobOpportunities() instead
        result.setErrorMessage("Use extractJobOpportunities() for complete extraction");

        return result;
    }

    /**
     * Extract complete JobOpportunity objects from Cadremploi HTML email.
     * This is the preferred method for Cadremploi emails.
     *
     * @param emailContent The HTML email content
     * @param emailSubject The email subject
     * @return List of extracted JobOpportunity objects, or null if extraction fails
     */
    public List<JobOpportunity> extractJobOpportunities(String emailContent, String emailSubject) {
        if (emailContent == null || emailContent.trim().isEmpty()) {
            System.err.println("  ✗ Email content is empty");
            return null;
        }

        List<JobOpportunity> opportunities = new ArrayList<>();

        try {
            // Extract job title + URL pairs from HTML
            Matcher matcher = JOB_LINK_PATTERN.matcher(emailContent);

            int foundCount = 0;
            while (matcher.find()) {
                String url = matcher.group(1);
                String title = matcher.group(2);

                // Decode HTML entities
                title = decodeHtmlEntities(title);

                // Filter out false positives
                if (isValidJobTitle(title)) {
                    foundCount++;

                    // Validate redirect URL
                    URLValidator.ValidationResult validation = URLValidator.validate(url);
                    if (!validation.isValid()) {
                        System.err.println("  ⚠ Invalid redirect URL for job '" + title + "': " + validation.getErrorMessage());
                        continue;
                    }

                    // Resolve redirect URL to simplified direct Cadremploi URL
                    // Pass job title to help match on "similar offers" pages
                    System.out.println("  ℹ Resolving redirect for: " + title);
                    String directUrl = URLRedirectResolver.resolveCadreMploiURL(url, title);

                    if (directUrl == null) {
                        System.err.println("  ⚠ Failed to resolve redirect URL for job: " + title);
                        System.err.println("    Using original redirect URL (may expire)");
                        directUrl = url;  // Fallback to redirect URL
                    } else {
                        System.out.println("  ✓ Resolved to: " + directUrl);

                        // Check if this is a similar offers page (expired job)
                        CadreMploiPageParser.ParsedJobPage parsed =
                            CadreMploiPageParser.parsePage(directUrl, title);

                        if (parsed.fetchSuccess && parsed.isSimilarOffersPage) {
                            System.out.println("  ⚠ Job '" + title + "' has expired - similar offers page detected");

                            // Store the original expired job with NOT_FINAL_REFERENCE marker
                            JobOpportunity expiredJob = new JobOpportunity();
                            expiredJob.setTitle(title + " (expired)");
                            expiredJob.setJobPortalName("Cadremploi");
                            expiredJob.setJobOfferURLForDescriptionOnJobPortal(directUrl);
                            expiredJob.setUrlReferenceType("NOT_FINAL_REFERENCE");
                            expiredJob.setFitScore(5.0);
                            opportunities.add(expiredJob);

                            System.out.println("  → Stored expired job reference: " + title);

                            // Extract recent jobs from similar offers page
                            List<JobOpportunity> recentJobs =
                                CadreMploiPageParser.extractRecentJobsFromSimilarOffers(directUrl, title);

                            if (recentJobs != null && !recentJobs.isEmpty()) {
                                System.out.println("  ✓ Found " + recentJobs.size() + " recent job(s) from similar offers");
                                opportunities.addAll(recentJobs);
                            } else {
                                System.out.println("  ⚠ No recent jobs found on similar offers page");
                            }

                            // Skip normal job creation - we've handled this expired job
                            continue;
                        }

                        // Verify the direct URL is accessible (only for non-expired jobs)
                        if (!URLRedirectResolver.isURLAccessible(directUrl)) {
                            System.err.println("  ⚠ Direct URL is not accessible, using redirect URL");
                            directUrl = url;
                        }
                    }

                    // Create JobOpportunity (for non-expired jobs)
                    JobOpportunity opp = new JobOpportunity();
                    opp.setTitle(title);
                    opp.setJobPortalName("Cadremploi");
                    opp.setJobOfferURLForDescriptionOnJobPortal(directUrl);
                    opp.setUrlReferenceType("DIRECT");  // Mark as direct reference

                    // Basic fit score (can be refined later)
                    opp.setFitScore(5.0);  // Neutral score

                    opportunities.add(opp);

                    System.out.println("  ✓ Extracted job: " + title);
                }
            }

            System.out.println("  ℹ Found " + foundCount + " job links, extracted " + opportunities.size() + " valid jobs");

            if (opportunities.isEmpty()) {
                System.err.println("  ⚠ No valid job opportunities found in HTML");
                return null;
            }

            return opportunities;

        } catch (Exception e) {
            System.err.println("  ✗ Error extracting jobs from HTML: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Check if a title is a valid job title (not a false positive)
     */
    private boolean isValidJobTitle(String title) {
        if (title == null || title.trim().isEmpty()) {
            return false;
        }

        // Check against excluded titles
        if (EXCLUDED_TITLES.contains(title.trim())) {
            return false;
        }

        // Filter out "Voir l'offre" and similar
        if (title.toLowerCase().contains("voir") && title.toLowerCase().contains("offre")) {
            return false;
        }

        // Must have reasonable length
        if (title.length() < 5 || title.length() > 200) {
            return false;
        }

        return true;
    }

    /**
     * Decode common HTML entities
     */
    private String decodeHtmlEntities(String text) {
        if (text == null) {
            return null;
        }

        return text
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&#x27;", "'")
                .replace("&apos;", "'");
    }

    @Override
    public String getExtractorName() {
        return "CadreMploiURLExtractor";
    }
}
