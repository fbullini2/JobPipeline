package com.agty.urlextractor;

import com.agty.JobOpportunity;
import com.agty.utils.GlobalConfig;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses Cadremploi job pages to extract:
 * - Whether it's a "similar offers" page (expired job)
 * - "Candidature rapide" (quick apply) URLs
 * - Publication date information
 */
public class CadreMploiPageParser {

    private static final int TIMEOUT_MS = 10000;

    // Pattern to detect "similar offers" page
    private static final Pattern SIMILAR_OFFERS_PATTERN = Pattern.compile(
            "Ces autres offres similaires|Les offres similaires",
            Pattern.CASE_INSENSITIVE
    );

    // Pattern to extract "Candidature rapide" URLs from HTML
    // Format: href="/emploi/detail_offre?offreId=XXXXXX" with "Candidature rapide" nearby
    private static final Pattern CANDIDATURE_RAPIDE_PATTERN = Pattern.compile(
            "href=\"(/emploi/detail_offre\\?offreId=([0-9]+))\"[^>]*>[^<]*Candidature rapide",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );

    // Pattern to extract publication date like "Publiée il y a 5 jours"
    private static final Pattern PUBLICATION_DATE_PATTERN = Pattern.compile(
            "Publiée il y a (\\d+) (jour|jours|heure|heures|minute|minutes)",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * Extract recent job opportunities from a "similar offers" page
     * Only returns jobs published within MAX_JOB_AGE_DAYS
     *
     * @param pageUrl URL of the similar offers page (can have token or just offreId)
     * @param originalJobTitle Title of the original (expired) job
     * @return List of recent JobOpportunity objects, or empty list if none found
     */
    public static List<JobOpportunity> extractRecentJobsFromSimilarOffers(String pageUrl, String originalJobTitle) {
        List<JobOpportunity> recentJobs = new ArrayList<>();

        try {
            System.out.println("    ℹ Extracting recent jobs from similar offers page...");
            System.out.println("      MAX_JOB_AGE_DAYS = " + GlobalConfig.MAX_JOB_AGE_DAYS);

            // Try to fetch with simplified URL (without token) if 403 occurs
            String simplifiedUrl = simplifyURLForFetch(pageUrl);
            String htmlContent = fetchPageContent(simplifiedUrl);

            if (htmlContent == null || htmlContent.isEmpty()) {
                System.err.println("    ✗ Could not fetch page content for similar offers");
                return recentJobs;
            }

            // Extract all job cards from similar offers section
            List<JobCard> jobCards = extractJobCards(htmlContent);
            System.out.println("    → Found " + jobCards.size() + " job(s) on similar offers page");

            // Filter to only recent jobs
            int recentCount = 0;
            for (JobCard card : jobCards) {
                if (card.ageInDays <= GlobalConfig.MAX_JOB_AGE_DAYS) {
                    // Create JobOpportunity for this recent job
                    JobOpportunity job = new JobOpportunity();
                    job.setTitle(card.title);
                    job.setJobPortalName("Cadremploi");
                    job.setJobOfferURLForDescriptionOnJobPortal(card.url);
                    job.setUrlReferenceType("DIRECT");  // Mark as direct reference
                    job.setCompany(card.company);
                    job.setLocation(card.location);
                    job.setFitScore(5.0);  // Neutral score

                    recentJobs.add(job);
                    recentCount++;

                    System.out.println("      ✓ [" + card.ageInDays + " days] " + card.title);
                } else {
                    System.out.println("      ⊘ [" + card.ageInDays + " days] " + card.title + " (too old, skipped)");
                }
            }

            System.out.println("    ✓ Extracted " + recentCount + " recent job(s) (≤ " + GlobalConfig.MAX_JOB_AGE_DAYS + " days old)");

        } catch (Exception e) {
            System.err.println("    ✗ Error extracting recent jobs: " + e.getMessage());
            e.printStackTrace();
        }

        return recentJobs;
    }

    /**
     * Simplify URL to just offreId parameter for fetching (remove token)
     */
    private static String simplifyURLForFetch(String url) {
        if (url.contains("offreId=")) {
            Pattern pattern = Pattern.compile("offreId=([0-9]+)");
            Matcher matcher = pattern.matcher(url);
            if (matcher.find()) {
                String offreId = matcher.group(1);
                return "https://www.cadremploi.fr/emploi/detail_offre?offreId=" + offreId;
            }
        }
        return url;
    }

    /**
     * Extract job cards from HTML with title, URL, company, location, and age
     */
    private static List<JobCard> extractJobCards(String html) {
        List<JobCard> jobs = new ArrayList<>();

        // Pattern to find job card sections in HTML
        // Looking for: title, URL, company, location, publication date
        // This is a complex pattern that needs to match Cadremploi's HTML structure

        // Pattern for job title + URL (in <a> tag with href and title/text)
        Pattern jobLinkPattern = Pattern.compile(
            "<a[^>]+href=\"(/emploi/detail_offre\\?offreId=([0-9]+))\"[^>]*>\\s*" +
            "(?:<[^>]+>)*\\s*([^<]+?)\\s*(?:</[^>]+>)*\\s*</a>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
        );

        // Pattern for publication date: "Publiée il y a X jour(s)"
        Pattern datePattern = Pattern.compile(
            "Publiée il y a (\\d+) (jour|jours|heure|heures)",
            Pattern.CASE_INSENSITIVE
        );

        // Split HTML into sections (rough approach)
        String[] sections = html.split("</article>|</div>");

        for (String section : sections) {
            // Look for job link in this section
            Matcher linkMatcher = jobLinkPattern.matcher(section);
            if (linkMatcher.find()) {
                String relativePath = linkMatcher.group(1);
                String offreId = linkMatcher.group(2);
                String title = linkMatcher.group(3).trim();

                // Clean up title (remove extra whitespace, HTML entities)
                title = title.replaceAll("\\s+", " ").trim();
                title = decodeHtmlEntities(title);

                // Skip if title is too short or looks like noise
                if (title.length() < 10 || title.contains("Voir") || title.contains("Postuler")) {
                    continue;
                }

                // Look for publication date in same section
                Matcher dateMatcher = datePattern.matcher(section);
                int ageInDays = 999;  // Default to very old if not found
                if (dateMatcher.find()) {
                    int value = Integer.parseInt(dateMatcher.group(1));
                    String unit = dateMatcher.group(2);
                    if (unit.contains("heure")) {
                        ageInDays = 0;  // Same day
                    } else {
                        ageInDays = value;  // Days
                    }
                }

                // Build full URL
                String fullUrl = "https://www.cadremploi.fr" + relativePath;

                JobCard card = new JobCard();
                card.title = title;
                card.url = fullUrl;
                card.ageInDays = ageInDays;
                card.company = "";  // TODO: Extract company if needed
                card.location = "";  // TODO: Extract location if needed

                jobs.add(card);
            }
        }

        return jobs;
    }

    /**
     * Parse a Cadremploi job page and extract information
     */
    public static ParsedJobPage parsePage(String jobUrl, String expectedJobTitle) {
        ParsedJobPage result = new ParsedJobPage();
        result.originalUrl = jobUrl;

        try {
            System.out.println("    ℹ Fetching page content to check for similar offers...");
            String htmlContent = fetchPageContent(jobUrl);

            if (htmlContent == null || htmlContent.isEmpty()) {
                System.err.println("    ✗ Failed to fetch page content");
                result.fetchSuccess = false;
                return result;
            }

            result.fetchSuccess = true;

            // Check if it's a "similar offers" page (expired job)
            Matcher similarMatcher = SIMILAR_OFFERS_PATTERN.matcher(htmlContent);
            result.isSimilarOffersPage = similarMatcher.find();

            if (result.isSimilarOffersPage) {
                System.out.println("    ⚠ This is a 'similar offers' page (original job expired)");

                // Extract "Candidature rapide" URLs
                extractCandidatureRapideURLs(htmlContent, result);

                // Extract publication dates
                extractPublicationDates(htmlContent, result);
            } else {
                System.out.println("    ✓ Direct job page (not expired)");
                result.directJobUrl = jobUrl;
            }

            return result;

        } catch (Exception e) {
            System.err.println("    ✗ Error parsing page: " + e.getMessage());
            result.fetchSuccess = false;
            return result;
        }
    }

    /**
     * Fetch HTML content from URL
     */
    private static String fetchPageContent(String urlString) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(urlString);
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
            connection.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
            connection.setRequestProperty("Accept-Language", "fr-FR,fr;q=0.9,en-US;q=0.8,en;q=0.7");

            int responseCode = connection.getResponseCode();
            if (responseCode != 200) {
                System.err.println("    ⚠ HTTP response code: " + responseCode);
                return null;
            }

            // Read HTML content
            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), "UTF-8"));
            StringBuilder content = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
            reader.close();

            return content.toString();

        } catch (Exception e) {
            System.err.println("    ✗ Error fetching page: " + e.getMessage());
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /**
     * Extract "Candidature rapide" URLs from HTML
     */
    private static void extractCandidatureRapideURLs(String html, ParsedJobPage result) {
        Matcher matcher = CANDIDATURE_RAPIDE_PATTERN.matcher(html);

        while (matcher.find()) {
            String relativePath = matcher.group(1);
            String offreId = matcher.group(2);
            String fullUrl = "https://www.cadremploi.fr" + relativePath;

            result.candidatureRapideURLs.add(fullUrl);
            System.out.println("    → Found 'Candidature rapide' URL: " + fullUrl);
        }

        if (result.candidatureRapideURLs.isEmpty()) {
            System.out.println("    ⚠ No 'Candidature rapide' URLs found on similar offers page");
        }
    }

    /**
     * Extract publication date information
     */
    private static void extractPublicationDates(String html, ParsedJobPage result) {
        Matcher matcher = PUBLICATION_DATE_PATTERN.matcher(html);

        int count = 0;
        while (matcher.find() && count < 5) {  // Limit to first 5 matches
            String number = matcher.group(1);
            String unit = matcher.group(2);
            String dateInfo = "Publiée il y a " + number + " " + unit;
            result.publicationDates.add(dateInfo);
            count++;
        }

        if (!result.publicationDates.isEmpty()) {
            System.out.println("    → Found " + result.publicationDates.size() + " publication date(s)");
        }
    }

    /**
     * Decode HTML entities in text
     */
    private static String decodeHtmlEntities(String text) {
        if (text == null) return null;
        return text
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&#x27;", "'")
            .replace("&apos;", "'");
    }

    /**
     * Job card extracted from similar offers page
     */
    private static class JobCard {
        String title;
        String url;
        String company;
        String location;
        int ageInDays;  // How many days ago was it published
    }

    /**
     * Result of parsing a Cadremploi page
     */
    public static class ParsedJobPage {
        public String originalUrl;
        public boolean fetchSuccess = false;
        public boolean isSimilarOffersPage = false;
        public String directJobUrl;  // If not expired
        public java.util.List<String> candidatureRapideURLs = new java.util.ArrayList<>();
        public java.util.List<String> publicationDates = new java.util.ArrayList<>();

        /**
         * Get the best URL to use (prefer direct, fall back to first candidature rapide)
         */
        public String getBestURL() {
            if (directJobUrl != null) {
                return directJobUrl;
            }
            if (!candidatureRapideURLs.isEmpty()) {
                return candidatureRapideURLs.get(0);
            }
            return originalUrl;  // Fallback to original
        }

        /**
         * Check if we have a valid apply URL
         */
        public boolean hasValidApplyURL() {
            return !candidatureRapideURLs.isEmpty();
        }
    }
}
