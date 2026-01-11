package com.agty.urlextractor;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Result of resolving a Cadremploi URL
 */
class ResolvedURL {
    public String url;
    public boolean isApplyURL;  // true if "Candidature rapide", false if just description
    public String publicationInfo;  // e.g., "Publiée il y a 5 jours"

    public ResolvedURL(String url, boolean isApplyURL, String publicationInfo) {
        this.url = url;
        this.isApplyURL = isApplyURL;
        this.publicationInfo = publicationInfo;
    }
}

/**
 * Resolves redirect URLs to their final destination and extracts job IDs.
 * Specifically designed for Cadremploi redirect URLs.
 */
public class URLRedirectResolver {

    // Pattern to extract offreId from final Cadremploi URL
    private static final Pattern OFFRE_ID_PATTERN = Pattern.compile(
            "offreId=([0-9]+)",
            Pattern.CASE_INSENSITIVE
    );

    // Maximum number of redirects to follow
    private static final int MAX_REDIRECTS = 5;

    // Connection timeout in milliseconds
    private static final int TIMEOUT_MS = 5000;

    /**
     * Resolve a Cadremploi redirect URL to a simplified direct URL.
     *
     * Strategy:
     * 1. Check if URL already contains offreId (already simplified)
     * 2. Try to decode/parse the redirect URL for offreId
     * 3. If not found, follow HTTP redirects to get final URL
     * 4. Extract offreId from final URL
     * 5. Check if job is expired (similar offers page)
     * 6. If expired, extract "Candidature rapide" URL from similar offers
     * 7. Return simplified URL: https://www.cadremploi.fr/emploi/detail_offre?offreId=<ID>
     *
     * @param redirectUrl The redirect URL to resolve
     * @return Simplified Cadremploi URL, or null if resolution fails
     */
    public static String resolveCadreMploiURL(String redirectUrl) {
        return resolveCadreMploiURL(redirectUrl, null);
    }

    /**
     * Resolve a Cadremploi redirect URL with job title context
     *
     * @param redirectUrl The redirect URL to resolve
     * @param jobTitle The expected job title (for matching on similar offers page)
     * @return Simplified Cadremploi URL, or null if resolution fails
     */
    public static String resolveCadreMploiURL(String redirectUrl, String jobTitle) {
        if (redirectUrl == null || redirectUrl.trim().isEmpty()) {
            return null;
        }

        // Step 1: Check if already a direct Cadremploi URL with offreId
        if (redirectUrl.contains("www.cadremploi.fr") && redirectUrl.contains("offreId=")) {
            return simplifyDirectURL(redirectUrl);
        }

        // Step 2: Try to parse redirect URL (in case offreId is encoded in path/query)
        // This is unlikely for encrypted redirects, but worth trying
        Matcher matcher = OFFRE_ID_PATTERN.matcher(redirectUrl);
        if (matcher.find()) {
            String offreId = matcher.group(1);
            return buildSimplifiedURL(offreId);
        }

        // Step 3: Follow HTTP redirects to get final URL
        System.out.println("    → Following redirect: " + truncate(redirectUrl, 70) + "...");
        try {
            String finalUrl = followRedirects(redirectUrl);
            if (finalUrl != null) {
                System.out.println("    ✓ Final URL: " + finalUrl);  // Show full URL

                // Step 4: Check if this is a "similar offers" page (expired job)
                if (finalUrl.contains("www.cadremploi.fr") && finalUrl.contains("offreId=")) {
                    CadreMploiPageParser.ParsedJobPage parsed =
                        CadreMploiPageParser.parsePage(finalUrl, jobTitle);

                    if (parsed.fetchSuccess && parsed.isSimilarOffersPage) {
                        System.out.println("    ⚠ Job has expired - found similar offers page");

                        if (parsed.hasValidApplyURL()) {
                            String candidatureUrl = parsed.getBestURL();
                            System.out.println("    ✓ Extracted 'Candidature rapide' URL: " + candidatureUrl);
                            return simplifyDirectURL(candidatureUrl);
                        } else {
                            System.err.println("    ✗ No valid apply URLs found on similar offers page");
                            // Fall through to return the original expired URL as fallback
                        }
                    }
                }

                return simplifyDirectURL(finalUrl);
            }
        } catch (Exception e) {
            System.err.println("    ✗ Failed to follow redirect: " + e.getMessage());
        }

        System.err.println("    ✗ Could not resolve URL to Cadremploi offreId");
        return null;
    }

    /**
     * Follow HTTP redirects to get the final destination URL
     *
     * @param startUrl Starting URL
     * @return Final destination URL after following redirects
     * @throws IOException If redirect following fails
     */
    private static String followRedirects(String startUrl) throws IOException {
        String currentUrl = startUrl;
        int redirectCount = 0;

        while (redirectCount < MAX_REDIRECTS) {
            HttpURLConnection connection = null;
            try {
                URL url = new URL(currentUrl);
                connection = (HttpURLConnection) url.openConnection();
                connection.setInstanceFollowRedirects(false);  // We handle redirects manually
                connection.setConnectTimeout(TIMEOUT_MS);
                connection.setReadTimeout(TIMEOUT_MS);
                connection.setRequestMethod("GET");  // Use GET (HEAD returns 405 for some servers)

                // Set user agent and headers to appear as a real browser
                connection.setRequestProperty("User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
                connection.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
                connection.setRequestProperty("Accept-Language", "fr-FR,fr;q=0.9,en-US;q=0.8,en;q=0.7");

                int responseCode = connection.getResponseCode();

                // Check if it's a redirect
                if (responseCode >= 300 && responseCode < 400) {
                    String location = connection.getHeaderField("Location");
                    if (location == null) {
                        System.err.println("    ⚠ Redirect response but no Location header");
                        return currentUrl;
                    }

                    // Handle relative redirects
                    if (location.startsWith("/")) {
                        URL base = new URL(currentUrl);
                        location = base.getProtocol() + "://" + base.getHost() + location;
                    }

                    System.out.println("    → Redirect " + (redirectCount + 1) + ": " + location);

                    // OPTIMIZATION: If we reached a Cadremploi URL with offreId, stop here!
                    // Don't make another request (which may return 403 due to missing cookies/session)
                    if (location.contains("www.cadremploi.fr") && location.contains("offreId=")) {
                        System.out.println("    ✓ Reached Cadremploi URL with offreId (stopping redirect chain)");
                        return location;
                    }

                    currentUrl = location;
                    redirectCount++;
                } else if (responseCode == 200) {
                    // Reached final destination
                    return currentUrl;
                } else if (responseCode == 403 && currentUrl.contains("www.cadremploi.fr")) {
                    // 403 on Cadremploi URL - this is expected if we don't have proper session
                    // But we already have the URL from previous redirect, so it's OK
                    System.out.println("    ℹ Got 403 on Cadremploi URL (expected - requires session)");
                    return currentUrl;
                } else {
                    System.err.println("    ⚠ Unexpected response code: " + responseCode);
                    return null;
                }

            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }

        System.err.println("    ⚠ Max redirects (" + MAX_REDIRECTS + ") reached");
        return currentUrl;  // Return last URL even if we hit max redirects
    }

    /**
     * Simplify a direct Cadremploi URL to only include offreId parameter
     *
     * @param directUrl Full Cadremploi URL with tracking parameters
     * @return Simplified URL with only offreId
     */
    private static String simplifyDirectURL(String directUrl) {
        if (directUrl == null) {
            return null;
        }

        // Extract offreId from URL
        Matcher matcher = OFFRE_ID_PATTERN.matcher(directUrl);
        if (matcher.find()) {
            String offreId = matcher.group(1);
            return buildSimplifiedURL(offreId);
        }

        // If we can't extract offreId, return original URL
        System.err.println("    ⚠ Could not extract offreId from URL: " + directUrl);
        return directUrl;
    }

    /**
     * Build simplified Cadremploi URL from offreId
     */
    private static String buildSimplifiedURL(String offreId) {
        return "https://www.cadremploi.fr/emploi/detail_offre?offreId=" + offreId;
    }

    /**
     * Truncate string for display
     */
    private static String truncate(String str, int maxLength) {
        if (str == null) return "null";
        if (str.length() <= maxLength) return str;
        return str.substring(0, maxLength - 3) + "...";
    }

    /**
     * Test if a URL is accessible (returns 200 OK)
     *
     * @param url URL to test
     * @return true if URL is accessible, false otherwise
     */
    public static boolean isURLAccessible(String url) {
        if (url == null || url.trim().isEmpty()) {
            return false;
        }

        HttpURLConnection connection = null;
        try {
            URL testUrl = new URL(url);
            connection = (HttpURLConnection) testUrl.openConnection();
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            connection.setRequestMethod("GET");  // Use GET (HEAD returns 405 for some servers)
            connection.setRequestProperty("User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
            connection.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");

            int responseCode = connection.getResponseCode();
            return responseCode == 200;

        } catch (Exception e) {
            return false;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
}
