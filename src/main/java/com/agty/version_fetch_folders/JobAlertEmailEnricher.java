package com.agty.version_fetch_folders;

import com.agty.JobOfferExtraction;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Enriches job offer extractions with additional details by:
 * - Extracting and cleaning portal links from email content
 * - Optionally fetching additional details from job portal pages
 * - Source-specific processing for different job boards
 */
public class JobAlertEmailEnricher {

    private static final int FETCH_TIMEOUT_MS = 10000; // 10 seconds timeout

    /**
     * Enrich a job offer extraction with source-specific details
     */
    public static void enrichJobOffer(JobOfferExtraction extraction, String emailContent) {
        if (extraction == null || emailContent == null) {
            return;
        }

        String sourceFolder = extraction.getSourceFolder();
        if (sourceFolder == null) {
            return;
        }

        try {
            if (sourceFolder.contains("CadreEmploi")) {
                enrichCadreEmploi(extraction, emailContent);
            } else if (sourceFolder.contains("APEC")) {
                enrichAPEC(extraction, emailContent);
            } else if (sourceFolder.contains("Linkedin")) {
                enrichLinkedin(extraction, emailContent);
            } else if (sourceFolder.contains("HelloWork")) {
                enrichHelloWork(extraction, emailContent);
            } else if (sourceFolder.contains("WTTJ")) {
                enrichWTTJ(extraction, emailContent);
            } else if (sourceFolder.contains("MichaelPage")) {
                enrichMichaelPage(extraction, emailContent);
            } else if (sourceFolder.contains("Tekkit")) {
                enrichTekkit(extraction, emailContent);
            }
        } catch (Exception e) {
            System.err.println("    ⚠️  Enrichment failed: " + e.getMessage());
        }
    }

    /**
     * Enrich Cadremploi job offers
     * Extracts clean portal link without tracking parameters
     */
    private static void enrichCadreEmploi(JobOfferExtraction extraction, String emailContent) {
        // Extract all Cadremploi links from email
        List<String> links = extractLinks(emailContent, "cadremploi\\.fr/emploi/detail_offre");

        System.out.println("    📊 Found " + links.size() + " Cadremploi links in email");

        if (!links.isEmpty()) {
            // Take the first link and clean it
            String rawLink = links.get(0);
            System.out.println("    🔗 Raw link: " + rawLink.substring(0, Math.min(100, rawLink.length())) + "...");

            String cleanLink = cleanCadreEmploiLink(rawLink);
            extraction.setInPortalJobOfferLink(cleanLink);

            System.out.println("    ✓ Extracted Cadremploi link: " + cleanLink);
        } else {
            System.out.println("    ⚠️  No Cadremploi links found in email");
        }
    }

    /**
     * Clean Cadremploi link by removing tracking parameters
     * Keeps only: alerteId, een, offreId
     */
    private static String cleanCadreEmploiLink(String url) {
        try {
            URI uri = new URI(url);
            String baseUrl = uri.getScheme() + "://" + uri.getHost() + uri.getPath();

            // Extract essential parameters
            String query = uri.getQuery();
            if (query == null) {
                return baseUrl;
            }

            StringBuilder cleanParams = new StringBuilder();
            String[] params = query.split("&");

            for (String param : params) {
                String[] keyValue = param.split("=", 2);
                if (keyValue.length == 2) {
                    String key = keyValue[0];
                    String value = keyValue[1];

                    // Keep only essential parameters
                    if (key.equals("alerteId") || key.equals("een") || key.equals("offreId")) {
                        if (cleanParams.length() > 0) {
                            cleanParams.append("&");
                        }
                        cleanParams.append(key).append("=").append(value);
                    }
                }
            }

            return baseUrl + (cleanParams.length() > 0 ? "?" + cleanParams : "");

        } catch (Exception e) {
            System.err.println("    ⚠️  Failed to clean Cadremploi link: " + e.getMessage());
            return url; // Return original if cleaning fails
        }
    }

    /**
     * Enrich APEC job offers
     */
    private static void enrichAPEC(JobOfferExtraction extraction, String emailContent) {
        // Extract APEC links
        List<String> links = extractLinks(emailContent, "apec\\.fr/.*offre");

        if (!links.isEmpty()) {
            String cleanLink = cleanAPECLink(links.get(0));
            extraction.setInPortalJobOfferLink(cleanLink);
            System.out.println("    🔗 Extracted APEC link: " + cleanLink);
        }
    }

    /**
     * Clean APEC link by removing tracking parameters
     */
    private static String cleanAPECLink(String url) {
        try {
            // Remove common tracking parameters
            return url.replaceAll("[?&](utm_[^&]+|xtor=[^&]+|token=[^&]+)", "")
                      .replaceAll("\\?&", "?")
                      .replaceAll("\\?$", "");
        } catch (Exception e) {
            return url;
        }
    }

    /**
     * Enrich LinkedIn job offers
     */
    private static void enrichLinkedin(JobOfferExtraction extraction, String emailContent) {
        List<String> links = extractLinks(emailContent, "linkedin\\.com/jobs/view");

        if (!links.isEmpty()) {
            String cleanLink = cleanLinkedinLink(links.get(0));
            extraction.setInPortalJobOfferLink(cleanLink);
            System.out.println("    🔗 Extracted LinkedIn link: " + cleanLink);
        }
    }

    /**
     * Clean LinkedIn link
     */
    private static String cleanLinkedinLink(String url) {
        try {
            // Keep only job ID
            Pattern pattern = Pattern.compile("(https://[^/]+/jobs/view/\\d+)");
            Matcher matcher = pattern.matcher(url);
            if (matcher.find()) {
                return matcher.group(1);
            }
            return url;
        } catch (Exception e) {
            return url;
        }
    }

    /**
     * Enrich HelloWork job offers
     */
    private static void enrichHelloWork(JobOfferExtraction extraction, String emailContent) {
        List<String> links = extractLinks(emailContent, "hellowork\\.com/.*emploi");

        if (!links.isEmpty()) {
            String cleanLink = cleanHelloWorkLink(links.get(0));
            extraction.setInPortalJobOfferLink(cleanLink);
            System.out.println("    🔗 Extracted HelloWork link: " + cleanLink);
        }
    }

    /**
     * Clean HelloWork link
     */
    private static String cleanHelloWorkLink(String url) {
        try {
            return url.replaceAll("[?&](utm_[^&]+|src=[^&]+)", "")
                      .replaceAll("\\?&", "?")
                      .replaceAll("\\?$", "");
        } catch (Exception e) {
            return url;
        }
    }

    /**
     * Enrich Welcome to the Jungle (WTTJ) job offers
     */
    private static void enrichWTTJ(JobOfferExtraction extraction, String emailContent) {
        List<String> links = extractLinks(emailContent, "welcometothejungle\\.com/.*jobs");

        if (!links.isEmpty()) {
            String cleanLink = cleanWTTJLink(links.get(0));
            extraction.setInPortalJobOfferLink(cleanLink);
            System.out.println("    🔗 Extracted WTTJ link: " + cleanLink);
        }
    }

    /**
     * Clean WTTJ link
     */
    private static String cleanWTTJLink(String url) {
        try {
            return url.replaceAll("[?&](utm_[^&]+|query=[^&]+)", "")
                      .replaceAll("\\?&", "?")
                      .replaceAll("\\?$", "");
        } catch (Exception e) {
            return url;
        }
    }

    /**
     * Enrich Michael Page job offers
     */
    private static void enrichMichaelPage(JobOfferExtraction extraction, String emailContent) {
        List<String> links = extractLinks(emailContent, "michaelpage\\.(fr|com)/job-detail");

        if (!links.isEmpty()) {
            String cleanLink = cleanMichaelPageLink(links.get(0));
            extraction.setInPortalJobOfferLink(cleanLink);
            System.out.println("    🔗 Extracted Michael Page link: " + cleanLink);
        }
    }

    /**
     * Clean Michael Page link
     */
    private static String cleanMichaelPageLink(String url) {
        try {
            return url.replaceAll("[?&](utm_[^&]+)", "")
                      .replaceAll("\\?&", "?")
                      .replaceAll("\\?$", "");
        } catch (Exception e) {
            return url;
        }
    }

    /**
     * Enrich Tekkit job offers
     */
    private static void enrichTekkit(JobOfferExtraction extraction, String emailContent) {
        List<String> links = extractLinks(emailContent, "tekkit\\.io/.*offre");

        if (!links.isEmpty()) {
            String cleanLink = cleanTekkitLink(links.get(0));
            extraction.setInPortalJobOfferLink(cleanLink);
            System.out.println("    🔗 Extracted Tekkit link: " + cleanLink);
        }
    }

    /**
     * Clean Tekkit link
     */
    private static String cleanTekkitLink(String url) {
        try {
            return url.replaceAll("[?&](utm_[^&]+|token=[^&]+)", "")
                      .replaceAll("\\?&", "?")
                      .replaceAll("\\?$", "");
        } catch (Exception e) {
            return url;
        }
    }

    /**
     * Extract all links matching a pattern from content
     */
    private static List<String> extractLinks(String content, String pattern) {
        List<String> links = new ArrayList<>();

        try {
            // Try to parse as HTML first
            if (content.trim().startsWith("<")) {
                Document doc = Jsoup.parse(content);
                Elements anchorTags = doc.select("a[href]");

                Pattern linkPattern = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE);

                for (Element anchor : anchorTags) {
                    String href = anchor.attr("href");
                    if (linkPattern.matcher(href).find()) {
                        links.add(href);
                    }
                }
            } else {
                // Plain text - use regex
                Pattern urlPattern = Pattern.compile("https?://[^\\s]+?" + pattern + "[^\\s]*", Pattern.CASE_INSENSITIVE);
                Matcher matcher = urlPattern.matcher(content);

                while (matcher.find()) {
                    links.add(matcher.group());
                }
            }
        } catch (Exception e) {
            System.err.println("    ⚠️  Failed to extract links: " + e.getMessage());
        }

        return links;
    }

    /**
     * Fetch and parse a URL to extract additional job details
     * (Optional - can be used for deep enrichment)
     */
    @SuppressWarnings("unused")
    private static Document fetchJobPage(String url) throws IOException {
        return Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .timeout(FETCH_TIMEOUT_MS)
                .get();
    }
}
