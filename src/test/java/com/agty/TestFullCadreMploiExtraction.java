package com.agty;

import com.agty.GmailEmailProcessor02.EmailInfo;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.File;
import java.util.List;

/**
 * Test full extraction pipeline for a single Cadremploi email
 */
public class TestFullCadreMploiExtraction {

    public static void main(String[] args) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.enable(SerializationFeature.INDENT_OUTPUT);

            String inputPath = System.getProperty("user.dir") + "/tools_data/job_opportunities_emails.json";

            // Load all emails
            List<EmailInfo> emails = mapper.readValue(
                new File(inputPath),
                new TypeReference<List<EmailInfo>>() {}
            );

            System.out.println("╔════════════════════════════════════════════════════════════════════╗");
            System.out.println("║    Full Cadremploi Extraction Test (REGEX-based)                  ║");
            System.out.println("╚════════════════════════════════════════════════════════════════════╝");
            System.out.println();
            System.out.println("  ℹ USE_LLM_FOR_LONG_HTML = " +
                System.getProperty("USE_LLM_FOR_LONG_HTML", "false"));
            System.out.println("  ℹ Default: Uses REGEX extraction (fast & accurate)");
            System.out.println("  ℹ To use LLM: Run with -DUSE_LLM_FOR_LONG_HTML=true");
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

            // Create extractor and process the single email
            String agentId = "TestCadremploi_001";
            String modelName = "gpt-4o-mini";

            JobOpportunityExtractor extractor = new JobOpportunityExtractor(agentId, modelName);

            // Use reflection to call private extractFromEmail method
            java.lang.reflect.Method method = JobOpportunityExtractor.class.getDeclaredMethod(
                "extractFromEmail",
                EmailInfo.class
            );
            method.setAccessible(true);

            @SuppressWarnings("unchecked")
            List<JobOpportunity> opportunities = (List<JobOpportunity>) method.invoke(extractor, cadreMploiEmail);

            System.out.println();
            System.out.println("═".repeat(70));
            System.out.println("EXTRACTION RESULTS:");
            System.out.println("═".repeat(70));

            if (opportunities != null && !opportunities.isEmpty()) {
                System.out.println("✅ Successfully extracted " + opportunities.size() + " job opportunity(ies)");
                System.out.println();

                for (int i = 0; i < opportunities.size(); i++) {
                    JobOpportunity opp = opportunities.get(i);
                    System.out.println("─".repeat(70));
                    System.out.println("Opportunity " + (i + 1) + ":");
                    System.out.println("─".repeat(70));
                    System.out.println("  Title: " + opp.getTitle());
                    System.out.println("  Company: " + opp.getCompany());
                    System.out.println("  Location: " + opp.getLocation());
                    System.out.println("  Portal: " + opp.getJobPortalName());
                    System.out.println("  Fit Score: " + opp.getFitScore());
                    System.out.println();
                    System.out.println("  URLs:");
                    System.out.println("    Apply (Portal):       " + truncate(opp.getJobOfferURLForApplyOnJobPortal()));
                    System.out.println("    Apply (Company):      " + truncate(opp.getJobOfferURLForApplyOnCompanySite()));
                    System.out.println("    Description (Portal): " + truncate(opp.getJobOfferURLForDescriptionOnJobPortal()));
                    System.out.println("    Description (Company):" + truncate(opp.getJobOfferURLForDescriptionOnCompanySite()));
                    System.out.println();
                }

                // Save to file for inspection
                String outputPath = System.getProperty("user.dir") + "/tools_data/test_cadremploi_result.json";
                mapper.writeValue(new File(outputPath), opportunities);
                System.out.println("═".repeat(70));
                System.out.println("💾 Results saved to: " + outputPath);

            } else {
                System.err.println("❌ No opportunities extracted!");
            }

        } catch (Exception e) {
            System.err.println("❌ Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static String truncate(String str) {
        if (str == null) return "null";
        if (str.length() <= 80) return str;
        return str.substring(0, 77) + "...";
    }
}
