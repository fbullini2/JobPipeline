package com.agty;

import com.agty.GmailEmailProcessor02.EmailInfo;

/**
 * Test to verify that Cadremploi emails are correctly processed
 * and not filtered out by mistake
 */
public class CadremployFilterTest {

    public static void main(String[] args) {
        System.out.println("╔════════════════════════════════════════════════════════════════════╗");
        System.out.println("║         Cadremploi Email Filter Test                              ║");
        System.out.println("╚════════════════════════════════════════════════════════════════════╝");
        System.out.println();

        // Create test email matching the actual Cadremploi email
        EmailInfo testEmail = new EmailInfo();
        testEmail.setFrom("offres@alertes.cadremploi.fr");
        testEmail.setSubject("8 offres à ne rater sous aucun prétexte !");
        testEmail.setContent("Développeur Dynamics 365 H/F\n" +
                "WINSEARCH BORDEAUX IT • Neuilly-sur-Seine • CDI\n" +
                "Conseil aux PME - PMI (+11), Paris (+1), CDI (+2),\n" +
                "de 60000 à 120000 euros\n\n" +
                "This is a job alert email with multiple job opportunities.");

        System.out.println("📧 TEST EMAIL:");
        System.out.println("   From: " + testEmail.getFrom());
        System.out.println("   Subject: " + testEmail.getSubject());
        System.out.println("   Content preview: " + testEmail.getContent().substring(0, Math.min(100, testEmail.getContent().length())) + "...");
        System.out.println();

        // Test domain matching
        String from = testEmail.getFrom().toLowerCase();
        System.out.println("🔍 DOMAIN MATCHING TEST:");
        System.out.println("   From (lowercase): " + from);
        System.out.println();

        String[] domains = {"cadremploi.fr", "alertes.cadremploi.fr"};
        for (String domain : domains) {
            boolean matches = from.contains(domain);
            System.out.println("   ✓ from.contains(\"" + domain + "\") = " + matches);
        }
        System.out.println();

        // Calculate relevance score using actual method
        GmailEmailProcessor02 processor = new GmailEmailProcessor02("test", "test", "test");

        int score = processor.calculateRelevanceScore(
            testEmail,
            "job",
            GmailEmailProcessor02.SENDER_DOMAIN_NAMES,
            GmailEmailProcessor02.NON_JOB_SENDER_DOMAIN_NAMES
        );

        System.out.println("📊 RELEVANCE SCORE RESULT:");
        System.out.println("   Score: " + score);
        System.out.println();

        if (score > 0) {
            System.out.println("✅ SUCCESS: Email would be ACCEPTED (score > 0)");
            System.out.println("   This email should appear in job_opportunities_emails.json");
        } else {
            System.out.println("❌ FAILURE: Email would be REJECTED (score = 0)");
            System.out.println("   This email will NOT appear in job_opportunities_emails.json");
            System.out.println();
            System.out.println("🔧 TROUBLESHOOTING:");
            System.out.println("   The email is being filtered out. Possible reasons:");
            System.out.println("   1. Domain 'alertes.cadremploi.fr' not matching 'cadremploi.fr'");
            System.out.println("   2. Subject/content contains blacklisted keywords");
            System.out.println("   3. Newsletter patterns triggering before whitelist check");
        }

        System.out.println();
        System.out.println("═".repeat(70));

        // Additional diagnostic: Check what patterns might be matching
        String fullText = (testEmail.getSubject() + " " + testEmail.getContent()).toLowerCase();

        System.out.println();
        System.out.println("🔍 PATTERN ANALYSIS:");

        String[] newsletterPatterns = {
            "newsletter", "daily digest", "weekly roundup", "hebdomadaire",
            "job alert", "job news", "recommended for you", "jobs you might like",
            "new jobs matching", "career advice", "career tips", "guide to"
        };

        boolean hasNewsletterPattern = false;
        for (String pattern : newsletterPatterns) {
            if (fullText.contains(pattern)) {
                System.out.println("   ⚠️  FOUND newsletter pattern: '" + pattern + "'");
                hasNewsletterPattern = true;
            }
        }

        if (!hasNewsletterPattern) {
            System.out.println("   ✓ No newsletter patterns found");
        }

        System.out.println();
        System.out.println("═".repeat(70));
    }
}
