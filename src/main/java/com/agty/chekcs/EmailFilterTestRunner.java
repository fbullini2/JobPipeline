package com.agty.chekcs;

import com.agty.GmailEmailProcessor02;

import java.util.Date;

/**
 * Test runner for email filtering - validates that job offers are correctly identified
 * and non-job emails are filtered out
 */
public class EmailFilterTestRunner {

    private static GmailEmailProcessor02 processor;
    private static int totalTests = 0;
    private static int passedTests = 0;
    private static int failedTests = 0;

    public static void main(String[] args) {
        System.out.println("╔═══════════════════════════════════════════════════════════════════╗");
        System.out.println("║          Email Job Offer Filtering - Test Suite                  ║");
        System.out.println("╚═══════════════════════════════════════════════════════════════════╝");
        System.out.println();

        // Initialize processor (just for scoring, no actual Gmail connection)
        processor = new GmailEmailProcessor02("test@example.com", "dummy", "test_agent");

        // Run all tests
        runPositiveTests();
        runNegativeTests();
        runEdgeCaseTests();

        // Print summary
        printSummary();
    }

    private static void runPositiveTests() {
        System.out.println("═══════════════════════════════════════════════════════════════════");
        System.out.println("POSITIVE TESTS - Should be identified as job offers (score > 0)");
        System.out.println("═══════════════════════════════════════════════════════════════════");
        System.out.println();

        testPositive(
            "LinkedIn job offer with 'apply now'",
            "LinkedIn <jobs-noreply@linkedin.com>",
            "Francesco, apply now to 'Technical Delivery Director at EPAM Systems'",
            "We found a position that matches your profile. Technical Delivery Director at EPAM Systems. Apply now to get noticed by the hiring team.",
            15  // Expected minimum score
        );

        testPositive(
            "Indeed direct job offer",
            "Indeed <noreply@indeed.com>",
            "Senior Software Engineer - Apply Today",
            "Company XYZ is hiring a Senior Software Engineer. Position available immediately. Submit your application before the deadline.",
            10
        );

        testPositive(
            "French job offer from APEC",
            "APEC <noreply@apec.fr>",
            "Offre d'emploi: Chef de Projet Technique - Paris",
            "Une entreprise recherche un Chef de Projet Technique. CDI à Paris. Postuler maintenant.",
            10
        );

        testPositive(
            "Direct recruiter interview invitation",
            "Sarah Johnson <sarah@techrecruiters.com>",
            "Interview Invitation - CTO Position at TechCorp",
            "Dear Francesco, we are looking for a CTO to join our team at TechCorp. We would like to schedule an interview with you.",
            5
        );

        testPositive(
            "Company direct hiring",
            "HR Team <careers@google.com>",
            "We're hiring: Engineering Manager at Google",
            "Join our team as Engineering Manager. We are looking for a talented leader to join Google. Apply before December 31st.",
            10
        );

        testPositive(
            "Welcome to the Jungle job offer",
            "Welcome to the Jungle <jobs@welcometothejungle.com>",
            "Nouvelle offre: Lead Developer - Startup Paris",
            "Candidature pour le poste de Lead Developer. Startup innovante cherche un développeur senior. Postuler en ligne.",
            10
        );

        System.out.println();
    }

    private static void runNegativeTests() {
        System.out.println("═══════════════════════════════════════════════════════════════════");
        System.out.println("NEGATIVE TESTS - Should NOT be job offers (score = 0)");
        System.out.println("═══════════════════════════════════════════════════════════════════");
        System.out.println();

        testNegative(
            "Investment opportunity (Estateguru)",
            "Estateguru <info@estateguru.co>",
            "New investment opportunity!",
            "Invest in real estate projects with guaranteed returns. New opportunity available today."
        );

        testNegative(
            "E-commerce promotion (Darty)",
            "Darty <darty@news.darty.com>",
            "Gros électroménager | Les incontournables électro",
            "Découvrez nos meilleures offres sur l'électroménager. Réductions jusqu'à 50%."
        );

        testNegative(
            "Travel deal (Skyscanner)",
            "Skyscanner <no-reply@sender.skyscanner.com>",
            "Voler en classe business pas cher : nos meilleures astuces",
            "Découvrez comment voyager en business class pour moins cher. Baisse de prix sur les vols Paris-New York."
        );

        testNegative(
            "Meetup event invitation",
            "Bay Area Research <info@email.meetup.com>",
            "📅 Appena programmati: Built on Bedrock Demo night: Agentic AI",
            "Join us for an exciting demo night. RSVP now for the upcoming event on AI and ML."
        );

        testNegative(
            "Promotional discount (Bolt)",
            "Emma de Bolt <france@rides-promotions.bolt.eu>",
            "Francesco, voici une réduction rien que pour vous 💚",
            "Profitez de 20% de réduction sur votre prochain trajet. Code promo: SAVE20"
        );

        testNegative(
            "Financial bank offer",
            "Crédit Agricole <no-reply.ebu@e-ca-des-savoie.fr>",
            "Monsieur Bullini, un coup de pouce pour vos achats d'ETF !",
            "Bénéficiez d'une offre spéciale sur vos investissements ETF. Contactez votre conseiller."
        );

        testNegative(
            "Online course/education",
            "MIT IDSS DSML <dsml.mit@learn.mygreatlearning.com>",
            "Past learners recommend this AI and Data Science program",
            "Join our AI and Data Science program. Past learners highly recommend this course. Enroll now."
        );

        testNegative(
            "Newsletter/Substack",
            "Dr. Paul Fang <bayareafoundersclub@substack.com>",
            "From Buffett's Lunch to BFC's Table: Meet Billionaire Investor",
            "Join us for an exclusive VIP dinner on November 15. Network with top investors."
        );

        testNegative(
            "Tech event/conference",
            "Silicon Valley Entrepreneurs <SVE-announce@email.meetup.com>",
            "Oct 27: 250 RSVPs - Workshop, Tech talk, and Showcase",
            "Join our workshop and tech talk. Speakers include top entrepreneurs."
        );

        testNegative(
            "LinkedIn job alert/newsletter",
            "LinkedIn <jobs-listings@linkedin.com>",
            "Jobs you might like: 50 new positions matching your profile",
            "We found 50 new jobs matching your profile. Browse recommended jobs. Update your job preferences."
        );

        testNegative(
            "Career advice article",
            "Career Tips <newsletter@careertips.com>",
            "How to ace your next job interview: Top 10 tips",
            "Career advice: Learn how to prepare for interviews. Guide to writing the perfect resume."
        );

        System.out.println();
    }

    private static void runEdgeCaseTests() {
        System.out.println("═══════════════════════════════════════════════════════════════════");
        System.out.println("EDGE CASE TESTS - Borderline cases");
        System.out.println("═══════════════════════════════════════════════════════════════════");
        System.out.println();

        testPositive(
            "Education with hiring context",
            "University Careers <careers@university.edu>",
            "We're hiring: Assistant Professor position",
            "The Computer Science department is hiring an Assistant Professor. Submit your application and teaching statement.",
            5
        );

        testNegative(
            "Generic business opportunity (not job)",
            "Marketing Agency <info@marketing.com>",
            "Great opportunity for your business",
            "We have a great opportunity for your business to grow. Contact us for marketing services."
        );

        System.out.println();
    }

    private static void testPositive(String testName, String from, String subject, String content, int minExpectedScore) {
        totalTests++;
        GmailEmailProcessor02.EmailInfo email = createEmail(from, subject, content);
        int score = processor.calculateRelevanceScore(email, "job", GmailEmailProcessor02.SENDER_DOMAIN_NAMES, GmailEmailProcessor02.NON_JOB_SENDER_DOMAIN_NAMES);

        boolean passed = score > 0 && score >= minExpectedScore;

        if (passed) {
            System.out.println("✅ PASS: " + testName);
            System.out.println("   Score: " + score + " (expected >= " + minExpectedScore + ")");
            passedTests++;
        } else {
            System.out.println("❌ FAIL: " + testName);
            System.out.println("   Score: " + score + " (expected >= " + minExpectedScore + ")");
            System.out.println("   Subject: " + subject);
            failedTests++;
        }
        System.out.println();
    }

    private static void testNegative(String testName, String from, String subject, String content) {
        totalTests++;
        GmailEmailProcessor02.EmailInfo email = createEmail(from, subject, content);
        int score = processor.calculateRelevanceScore(email, "job", GmailEmailProcessor02.SENDER_DOMAIN_NAMES, GmailEmailProcessor02.NON_JOB_SENDER_DOMAIN_NAMES);

        boolean passed = (score == 0);

        if (passed) {
            System.out.println("✅ PASS: " + testName);
            System.out.println("   Score: " + score + " (expected = 0)");
            passedTests++;
        } else {
            System.out.println("❌ FAIL: " + testName);
            System.out.println("   Score: " + score + " (expected = 0, but got " + score + ")");
            System.out.println("   Subject: " + subject);
            failedTests++;
        }
        System.out.println();
    }

    private static GmailEmailProcessor02.EmailInfo createEmail(String from, String subject, String content) {
        GmailEmailProcessor02.EmailInfo email = new GmailEmailProcessor02.EmailInfo();
        email.setFrom(from);
        email.setSubject(subject);
        email.setContent(content);
        email.setSentDate(new Date());

        // Extract domain from 'from' field
        if (from.contains("<") && from.contains(">")) {
            String emailAddr = from.substring(from.indexOf("<") + 1, from.indexOf(">"));
            if (emailAddr.contains("@")) {
                email.setSenderDomain(emailAddr.substring(emailAddr.indexOf("@") + 1));
            }
        } else if (from.contains("@")) {
            email.setSenderDomain(from.substring(from.indexOf("@") + 1));
        }

        return email;
    }

    private static void printSummary() {
        System.out.println("╔═══════════════════════════════════════════════════════════════════╗");
        System.out.println("║                         TEST SUMMARY                              ║");
        System.out.println("╚═══════════════════════════════════════════════════════════════════╝");
        System.out.println();
        System.out.println("Total Tests:  " + totalTests);
        System.out.println("✅ Passed:    " + passedTests + " (" + (passedTests * 100 / totalTests) + "%)");
        System.out.println("❌ Failed:    " + failedTests + " (" + (failedTests * 100 / totalTests) + "%)");
        System.out.println();

        if (failedTests == 0) {
            System.out.println("🎉 ALL TESTS PASSED! 🎉");
            System.out.println("The email filtering system is working correctly!");
        } else {
            System.out.println("⚠️  Some tests failed. Please review the filters.");
        }
        System.out.println();
    }
}
