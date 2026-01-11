package com.agty;

import com.agty.GmailEmailProcessor02.EmailInfo;
import com.agty.urlextractor.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Test program to verify Cadremploi URL extraction
 */
public class TestCadreMploiExtraction {

    public static void main(String[] args) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            String inputPath = System.getProperty("user.dir") + "/tools_data/job_opportunities_emails.json";

            // Load all emails
            List<EmailInfo> emails = mapper.readValue(
                new File(inputPath),
                new TypeReference<List<EmailInfo>>() {}
            );

            System.out.println("╔════════════════════════════════════════════════════════════════════╗");
            System.out.println("║         Cadremploi URL Extraction Test                            ║");
            System.out.println("╚════════════════════════════════════════════════════════════════════╝");
            System.out.println();

            // Find first Cadremploi email
            EmailInfo cadreMploiEmail = null;
            for (EmailInfo email : emails) {
                if (email.getFrom() != null &&
                    email.getFrom().toLowerCase().contains("offres@alertes.cadremploi.fr")) {
                    cadreMploiEmail = email;
                    break;
                }
            }

            if (cadreMploiEmail == null) {
                System.err.println("❌ No Cadremploi email found!");
                return;
            }

            System.out.println("📧 Testing with email:");
            System.out.println("   Subject: " + cadreMploiEmail.getSubject());
            System.out.println("   From: " + cadreMploiEmail.getFrom());
            System.out.println();

            // Test URL extraction
            URLExtractorRegistry registry = new URLExtractorRegistry();
            URLExtractionResult result = registry.extractURLs(
                cadreMploiEmail.getFrom(),
                cadreMploiEmail.getSubject(),
                cadreMploiEmail.getContent()
            );

            System.out.println("─".repeat(70));
            System.out.println("URL EXTRACTION RESULTS:");
            System.out.println("─".repeat(70));

            if (result != null && result.isExtractionSuccess()) {
                System.out.println("✅ Extraction successful!");
                System.out.println("   Method: " + result.getExtractionMethod());
                System.out.println("   Portal: " + result.getJobPortalName());
                System.out.println();
                System.out.println("   Apply on Portal: " + result.getJobOfferURLForApplyOnJobPortal());
                System.out.println("   Apply on Company: " + result.getJobOfferURLForApplyOnCompanySite());
                System.out.println("   Description on Portal: " + result.getJobOfferURLForDescriptionOnJobPortal());
                System.out.println("   Description on Company: " + result.getJobOfferURLForDescriptionOnCompanySite());
            } else {
                System.err.println("❌ Extraction failed!");
                if (result != null) {
                    System.err.println("   Error: " + result.getErrorMessage());
                }
            }

            // Also show raw URL count in email
            System.out.println();
            System.out.println("─".repeat(70));
            System.out.println("RAW URL ANALYSIS:");
            System.out.println("─".repeat(70));

            Pattern pattern = Pattern.compile(
                "https://www\\.cadremploi\\.fr/emploi/detail_offre\\?[^\\s\"'<>]*offreId=([0-9]+)",
                Pattern.CASE_INSENSITIVE
            );
            Matcher matcher = pattern.matcher(cadreMploiEmail.getContent());

            int count = 0;
            while (matcher.find()) {
                count++;
                String offreId = matcher.group(1);
                String simplified = "https://www.cadremploi.fr/emploi/detail_offre?offreId=" + offreId;
                System.out.println("   [" + count + "] " + simplified);
            }

            System.out.println();
            System.out.println("Total Cadremploi URLs found: " + count);
            System.out.println();
            System.out.println("═".repeat(70));

        } catch (Exception e) {
            System.err.println("❌ Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
