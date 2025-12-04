package com.agty;


import com.agty.utils.EmailStaticLib;
import com.agty.utils.LLMCostCalculator;
import com.agty.utils.LLMUsageInfo;
import com.agty.utils.OpenAiRESTApiCaller;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import javax.mail.*;
import javax.mail.Folder;
import javax.mail.internet.*;
import javax.mail.search.*;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static com.agty.utils.EmailStaticLib.findDraftsFolder;
import static com.agty.utils.GlobalConfig.DEV_MODE;


public class GmailEmailProcessor02 {
    private final String username;
    private final String password;
    private final String agentId;
    private final LLMCostCalculator.CostSummary costSummary;

    // Topic-related keywords
    private static final Map<String, String[]> TOPIC_KEYWORDS = new HashMap<>();

    // Important job-related domains/senders (TRUSTED JOB PLATFORMS)
    public static String[] SENDER_DOMAIN_NAMES = {
        "linkedin.com", "indeed.com", "glassdoor.com", "monster.com", "hellowork.com", "apec.fr", "cadremploi.fr",
        "welcometothejungle.com", "angellist.com", "hired.com", "triplebyte.com", "talent.io",
        "dice.com", "ziprecruiter.com", "careerbuilder.com", "workday.com",
        "greenhouse.io", "lever.co", "smartrecruiters.com", "jobs.lever.co", "tekkit.io"
    };

    // NON-JOB domains (newsletters, events, promotions, etc.) - AUTO-REJECT
    public static String[] NON_JOB_SENDER_DOMAIN_NAMES = {
        // Event/Meetup platforms
        "meetup.com", "eventbrite.com", "luma.co",
        // Newsletter platforms
        "substack.com", "beehiiv.com", "ghost.io",
        // E-commerce and promotions
        "darty.com", "fnac.com", "amazon.fr", "cdiscount.com", "news.darty.com",
        "bolt.eu", "uber.com", "deliveroo.com", "skyscanner.com",
        // Financial/Investment
        "estateguru.co", "boursorama.fr", "fortuneo.fr", "ca-des-savoie.fr",
        // Education/Training (not job offers)
        "mygreatlearning.com", "coursera.org", "udemy.com", "edx.org",
        // Social/Networking (non-job)
        "facebook.com", "twitter.com", "instagram.com"
    };

    static {
        // Initialize topic keywords - FOCUSED ON ACTUAL JOB OFFERS, NOT NEWS/MARKET INFO
        // IMPORTANT: First 5 keywords are used in IMAP search - include multilingual terms!
        TOPIC_KEYWORDS.put("job", new String[]{
            // High-priority offer keywords (multilingual - used in IMAP search)
            "offer", "offre", "opportunity", "poste", "apply",
            // Additional offer keywords
            "application", "interview", "vacancy", "hiring",
            // Position-specific keywords
            "position", "role", "opening",
            // Recruiter/candidate keywords
            "candidate", "recruiter", "recruitment",
            // French additional keywords
            "candidature", "recrutement",
            // Italian equivalents
            "candidato", "opportunità", "posizione"
        });

        TOPIC_KEYWORDS.put("freelance", new String[]{
            "freelance", "contract", "consultant", "project", "gig",
            "independent", "contractor", "remote work", "consulting"
        });

        TOPIC_KEYWORDS.put("internship", new String[]{
            "internship", "intern", "stage", "stagiaire", "trainee",
            "apprenticeship", "student position"
        });
    }

    public GmailEmailProcessor02(String username, String password, String agentId) {
        this.username = username;
        this.password = password;
        this.agentId = agentId;
        this.costSummary = new LLMCostCalculator.CostSummary();
    }

    /**
     * Search for emails related to a specific topic (without incremental saving)
     * @param topic The topic to search for (default: "job")
     * @param maxResults Maximum number of results to return
     * @param daysBack How many days back to search
     * @param specificSenders Optional list of specific sender email addresses to filter
     * @return List of EmailInfo objects containing relevant emails
     */
    public List<EmailInfo> searchTopicEmails(String topic, int maxResults, int daysBack, List<String> specificSenders)
            throws MessagingException {
        return searchTopicEmails(topic, maxResults, daysBack, specificSenders, null);
    }

    /**
     * Search for emails related to a specific topic WITH incremental saving
     * @param topic The topic to search for (default: "job")
     * @param maxResults Maximum number of results to return
     * @param daysBack How many days back to search
     * @param specificSenders Optional list of specific sender email addresses to filter
     * @param outputFilePath Path to save results incrementally (null to disable incremental saving)
     * @return List of EmailInfo objects containing relevant emails
     */
    public List<EmailInfo> searchTopicEmails(String topic, int maxResults, int daysBack, List<String> specificSenders, String outputFilePath)
            throws MessagingException {

        if (topic == null || topic.isEmpty()) {
            topic = "job";
        }

        // Load existing emails from file if incremental saving is enabled
        List<EmailInfo> existingEmails = new ArrayList<>();
        if (outputFilePath != null) {
            existingEmails = loadExistingEmails(outputFilePath);
            System.out.println("📂 Loaded " + existingEmails.size() + " existing emails from file");
        }

        System.out.println("Connecting to Gmail IMAP server...");
        Properties props = new Properties();
        props.put("mail.store.protocol", "imaps");
        props.put("mail.imaps.host", "imap.gmail.com");
        props.put("mail.imaps.port", "993");

        Session session = Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(username, password);
            }
        });

        Store store = session.getStore("imaps");
        store.connect("imap.gmail.com", username, password);
        System.out.println("Connected successfully!");

        Folder inbox = store.getFolder("INBOX");
        inbox.open(Folder.READ_ONLY);
        System.out.println("Opened INBOX (total messages: " + inbox.getMessageCount() + ")");

        // Build search term
        System.out.println("Building search query for topic: " + topic + " (last " + daysBack + " days)...");
        SearchTerm searchTerm = buildTopicSearchTerm(topic, daysBack, specificSenders);

        // Search messages
        System.out.println("Executing IMAP search...with search terms=" + searchTerm);
        Message[] messages = inbox.search(searchTerm);
        System.out.println("Found " + messages.length + " messages matching search criteria");

        // Convert to EmailInfo and sort by relevance
        System.out.println("Processing and scoring messages...");
        List<EmailInfo> emailInfos = new ArrayList<>(existingEmails);  // Start with existing emails
        int processed = 0;
        int newEmailsAdded = 0;
        int maxCount=10000;
        if (DEV_MODE) {
            maxCount=5;
        }
        for (Message message : messages) {
            if (processed >=maxCount) {
                System.out.println("\n🛑 STOP - reached " + maxCount + " processed messages (DEV MODE limit)");
                break;
            }

            processed++;
            System.out.println(String.format("\n[%d/%d] Processing: %s",
                    processed, Math.min(messages.length, maxCount),
                    truncate(message.getSubject(), 60)));

            EmailInfo info = extractEmailInfo(message, topic);
            if (info != null && info.getRelevanceScore() > 0) {
                // Check for duplicates before adding
                if (!isDuplicate(info, emailInfos)) {
                    emailInfos.add(info);
                    newEmailsAdded++;
                    System.out.println("  ✓ KEPT - Score: " + info.getRelevanceScore() +
                            " | From: " + truncate(info.getFrom(), 40));

                    // Save incrementally if outputFilePath is provided
                    if (outputFilePath != null) {
                        saveJobOpportunitiesToJson(emailInfos, outputFilePath, false);  // quiet save
                        System.out.println("  💾 Saved to file (" + emailInfos.size() + " total emails)");
                    }
                } else {
                    System.out.println("  ⊘ DUPLICATE - Already exists: " + truncate(info.getSubject(), 50));
                }
            } else {
                System.out.println("  ✗ Rejected - Score: " + (info != null ? info.getRelevanceScore() : "N/A"));
            }
        }
        System.out.println("\n" + "═".repeat(70));
        System.out.println("📊 Summary: Processed " + processed + " messages | " +
                "New emails added: " + newEmailsAdded + " | Total in list: " + emailInfos.size());
        System.out.println("═".repeat(70));

        // Sort by relevance score and date
        System.out.println("Sorting by relevance and date...");
        emailInfos.sort((e1, e2) -> {
            int scoreCompare = Integer.compare(e2.getRelevanceScore(), e1.getRelevanceScore());
            if (scoreCompare == 0) {
                return e2.getSentDate().compareTo(e1.getSentDate());
            }
            return scoreCompare;
        });

        // Limit results
        if (emailInfos.size() > maxResults) {
            emailInfos = emailInfos.subList(0, maxResults);
            System.out.println("Limited to top " + maxResults + " results");
        }

        inbox.close(false);
        store.close();
        System.out.println("Disconnected from server\n");

        return emailInfos;
    }

    /**
     * Build a simplified search term for topic-based searching
     * Simplified to avoid IMAP parsing errors with complex queries
     */
    private SearchTerm buildTopicSearchTerm(String topic, int daysBack, List<String> specificSenders) {
        // Start with date constraint
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_MONTH, -daysBack);
        SearchTerm dateTerm = new ReceivedDateTerm(ComparisonTerm.GT, cal.getTime());

        // If specific senders provided, combine with date
        if (specificSenders != null && !specificSenders.isEmpty()) {
            List<SearchTerm> senderTerms = new ArrayList<>();
            for (String sender : specificSenders) {
                try {
                    senderTerms.add(new FromTerm(new InternetAddress(sender)));
                } catch (AddressException e) {
                    // Skip invalid addresses
                }
            }

            if (!senderTerms.isEmpty()) {
                SearchTerm senderTerm = senderTerms.get(0);
                for (int i = 1; i < senderTerms.size(); i++) {
                    senderTerm = new OrTerm(senderTerm, senderTerms.get(i));
                }
                return new AndTerm(dateTerm, senderTerm);
            }
        }

        // For topic search, use a simplified approach with fewer OR terms
        // Use only the most important keywords to avoid IMAP parse errors
        String[] keywords = TOPIC_KEYWORDS.getOrDefault(topic.toLowerCase(), new String[]{topic});

        // Limit to first 5 keywords to avoid complex query issues
        int keywordLimit = Math.min(5, keywords.length);
        List<SearchTerm> keywordTerms = new ArrayList<>();

        for (int i = 0; i < keywordLimit; i++) {
            String keyword = keywords[i];
            // Create OR term for subject OR body for each keyword
            SearchTerm subjectTerm = new SubjectTerm(keyword);
            SearchTerm bodyTerm = new BodyTerm(keyword);
            keywordTerms.add(new OrTerm(subjectTerm, bodyTerm));
        }

        // Combine keyword terms with OR
        SearchTerm keywordTerm = keywordTerms.get(0);
        for (int i = 1; i < keywordTerms.size(); i++) {
            keywordTerm = new OrTerm(keywordTerm, keywordTerms.get(i));
        }

        // Combine date and keywords with AND
        return new AndTerm(dateTerm, keywordTerm);
    }

    /**
     * Extract email information and calculate relevance score
     */
    private EmailInfo extractEmailInfo(Message message, String topic) {
        try {
            EmailInfo info = new EmailInfo();

            // Extract basic info
            Address[] from = message.getFrom();
            info.setFrom(from != null && from.length > 0 ? from[0].toString() : "Unknown");
            info.setSubject(message.getSubject());
            info.setSentDate(message.getSentDate());
            info.setContent(getMessageContent(message));

            // Calculate relevance score
            int score = calculateRelevanceScore(info, topic, SENDER_DOMAIN_NAMES, NON_JOB_SENDER_DOMAIN_NAMES);
            info.setRelevanceScore(score);

            // Extract sender domain
            if (from != null && from.length > 0 && from[0] instanceof InternetAddress) {
                InternetAddress addr = (InternetAddress) from[0];
                String email = addr.getAddress();
                if (email != null && email.contains("@")) {
                    info.setSenderDomain(email.substring(email.indexOf("@") + 1));
                }
            }

            return info;

        } catch (Exception e) {
            System.err.println("Error extracting email info: " + e.getMessage());
            return null;
        }
    }

    /**
     * Calculate relevance score based on keywords and source
     * FOCUSED ON ACTUAL JOB OFFERS - filters out news, alerts, market info
     *
     * Package-private for testing purposes
     */
    public int calculateRelevanceScore(EmailInfo info, String topic, String[] senderDomainNames, String[] nonJobSenderDomainNames) {
        int score = 0;
        String[] keywords = TOPIC_KEYWORDS.getOrDefault(topic.toLowerCase(), new String[]{topic});

        String subject = (info.getSubject() != null) ? info.getSubject().toLowerCase() : "";
        String content = (info.getContent() != null) ? info.getContent().toLowerCase() : "";
        String from = (info.getFrom() != null) ? info.getFrom().toLowerCase() : "";
        String fullText = subject + " " + content;

        // ===================================================================
        // STEP 1: CHECK SENDER DOMAIN - AUTO-REJECT NON-JOB SENDERS
        // ===================================================================
        for (String nonJobDomain : nonJobSenderDomainNames) {
            if (from.contains(nonJobDomain)) {
                // Auto-reject emails from non-job domains (meetup, e-commerce, etc.)
                return 0;
            }
        }

        // ===================================================================
        // STEP 1.5: CHECK IF EMAIL IS FROM WHITELISTED JOB PLATFORM
        // ===================================================================
        boolean fromTrustedJobPlatform = false;
        if ("job".equalsIgnoreCase(topic)) {
            for (String domain : senderDomainNames) {
                if (from.contains(domain)) {
                    fromTrustedJobPlatform = true;
                    break;
                }
            }
        }

        // ===================================================================
        // STEP 2: CONTENT-BASED EXCLUSIONS (not job offers)
        // ===================================================================

        // Promotional content patterns
        String[] promotionalPatterns = {
            "réduction", "discount", "promo", "bon plan", "deal", "sale",
            "coupon", "voucher", "limited offer", "special price", "prix spécial",
            "€", "$", "% off", "gratuit", "free shipping", "livraison gratuite"
        };

        for (String pattern : promotionalPatterns) {
            if (subject.contains(pattern)) {
                return 0;  // Promotional emails are not job offers
            }
        }

        // Investment/Financial content patterns
        String[] financialPatterns = {
            "investment opportunity", "invest", "etf", "trading", "crypto",
            "stock", "actions", "bourse", "dividende", "rendement"
        };

        for (String pattern : financialPatterns) {
            if (subject.contains(pattern) || (fullText.contains(pattern) && !fullText.contains("apply"))) {
                return 0;  // Financial opportunities are not job offers
            }
        }

        // Event/Meetup patterns
        String[] eventPatterns = {
            "appena programmati", "just scheduled", "upcoming event", "rsvp",
            "speaker series", "workshop", "demo night", "networking event",
            "tech talk", "conference", "webinar", "événement à venir"
        };

        for (String pattern : eventPatterns) {
            if (fullText.contains(pattern)) {
                return 0;  // Events/meetups are not job offers
            }
        }

        // Travel/Transportation patterns
        String[] travelPatterns = {
            "flight", "vol", "voyage", "booking", "reservation", "hotel",
            "baisse de prix", "price drop", "travel alert"
        };

        for (String pattern : travelPatterns) {
            if (fullText.contains(pattern)) {
                return 0;  // Travel deals are not job offers
            }
        }

        // Education/Course patterns (unless explicitly hiring)
        String[] educationPatterns = {
            "learn this", "past learners", "program enrollment", "course",
            "formation en ligne", "online learning", "certification program"
        };

        boolean hasEducationPattern = false;
        for (String pattern : educationPatterns) {
            if (fullText.contains(pattern)) {
                hasEducationPattern = true;
                break;
            }
        }

        if (hasEducationPattern && !fullText.contains("hiring") && !fullText.contains("recrut")) {
            return 0;  // Educational content without hiring context
        }

        // Newsletter/Digest patterns
        // Skip this check for trusted job platforms (they send legitimate job alerts)
        if (!fromTrustedJobPlatform) {
            String[] newsletterPatterns = {
                "newsletter", "daily digest", "weekly roundup", "hebdomadaire",
                "job alert", "job news", "recommended for you", "jobs you might like",
                "new jobs matching", "career advice", "career tips", "guide to"
            };

            for (String pattern : newsletterPatterns) {
                if (fullText.contains(pattern)) {
                    return 0;  // Newsletters/alerts are not direct job offers
                }
            }
        }

        // ===================================================================
        // STEP 3: POSITIVE SCORING - ACTUAL JOB OFFER INDICATORS
        // ===================================================================

        boolean hasOfferIndicator = false;
        boolean hasStrongOfferIndicator = false;

        // Strong job offer indicators (very specific)
        String[] strongOfferIndicators = {
            "apply now", "apply for this position", "submit your application",
            "application deadline", "apply before", "submit resume",
            "send your cv", "postuler maintenant", "envoyer votre cv",
            "join our team as", "we're hiring a", "we are looking for a"
        };

        for (String indicator : strongOfferIndicators) {
            if (fullText.contains(indicator)) {
                hasStrongOfferIndicator = true;
                hasOfferIndicator = true;
                score += 10;  // Very high bonus for strong indicators
                break;
            }
        }

        // Moderate job offer indicators
        String[] moderateOfferIndicators = {
            "interview", "screening call", "position available", "opening for",
            "vacancy", "recrut", "hiring", "join our team", "join us",
            "offre de poste", "candidature", "poste à pourvoir"
        };

        if (!hasStrongOfferIndicator) {
            for (String indicator : moderateOfferIndicators) {
                if (fullText.contains(indicator)) {
                    hasOfferIndicator = true;
                    score += 5;
                    break;
                }
            }
        }

        // Bonus for emails from TRUSTED job platforms (high confidence)
        // Use the flag we already set in STEP 1.5
        boolean fromJobPlatform = fromTrustedJobPlatform;
        if (fromJobPlatform) {
            score += 8;  // Increased bonus for trusted platforms
            hasOfferIndicator = true;  // Trust job platforms
        }

        // Very high bonus for explicit job offer phrases in SUBJECT
        if ("job".equalsIgnoreCase(topic)) {
            if (subject.contains("job offer") || subject.contains("offre d'emploi") ||
                subject.contains("offre de travail")) {
                score += 15;  // Extremely high priority
                hasStrongOfferIndicator = true;
            }
            if (subject.contains("job opening") || subject.contains("poste disponible")) {
                score += 12;
            }
            if (subject.contains("hiring") && (subject.contains("for") || subject.contains("seeking"))) {
                score += 10;
            }
        }

        // Specific position/role mentioned with action verbs
        if (fullText.contains("position:") || fullText.contains("role:") ||
            fullText.contains("poste :") || fullText.contains("ruolo:")) {
            score += 4;
        }

        // Company hiring context
        if ((fullText.contains("join") && fullText.contains("team")) ||
            (fullText.contains("work with us") || fullText.contains("travaille avec nous"))) {
            score += 3;
        }

        // ===================================================================
        // STEP 4: FINAL FILTERING - REQUIRE STRONG EVIDENCE
        // ===================================================================

        // If from trusted job platform, more lenient
        if (fromJobPlatform && score >= 5) {
            return score;
        }

        // Otherwise, require strong offer indicators OR very high score
        if (!hasOfferIndicator) {
            return 0;  // No job offer indicators at all
        }

        if (!hasStrongOfferIndicator && score < 10) {
            return 0;  // Weak indicators with low score = not a real offer
        }

        // Check for keyword relevance (at least some matching)
        int keywordMatches = 0;
        for (String keyword : keywords) {
            if (subject.contains(keyword.toLowerCase()) || content.contains(keyword.toLowerCase())) {
                keywordMatches++;
            }
        }

        if (keywordMatches == 0 && !fromJobPlatform) {
            return 0;  // No keyword matches and not from job platform = irrelevant
        }

        return Math.max(0, score);
    }

    /**
     * Get job emails with categorization
     */
    public Map<String, List<EmailInfo>> getJobEmailsCategorized(int daysBack) throws MessagingException {
        Map<String, List<EmailInfo>> categorized = new HashMap<>();

        // Search for job emails
        System.out.println("\n[Categorization] Searching for job emails...");
        List<EmailInfo> allJobEmails = searchTopicEmails("job", 100, daysBack, null);

        // Categorize
        System.out.println("[Categorization] Categorizing " + allJobEmails.size() + " job emails...");
        categorized.put("High Priority", new ArrayList<>());
        categorized.put("Direct Offers", new ArrayList<>());
        categorized.put("Job Boards", new ArrayList<>());
        categorized.put("Recruiters", new ArrayList<>());
        categorized.put("Other", new ArrayList<>());

        for (EmailInfo email : allJobEmails) {
            if (email.getRelevanceScore() >= 8) {
                categorized.get("High Priority").add(email);
            } else if (containsDirectOffer(email)) {
                categorized.get("Direct Offers").add(email);
            } else if (isFromJobBoard(email)) {
                categorized.get("Job Boards").add(email);
            } else if (isFromRecruiter(email)) {
                categorized.get("Recruiters").add(email);
            } else {
                categorized.get("Other").add(email);
            }
        }
        System.out.println("[Categorization] Complete!");

        return categorized;
    }

    private boolean containsDirectOffer(EmailInfo email) {
        String content = (email.getSubject() + " " + email.getContent()).toLowerCase();
        return content.contains("offer") || content.contains("propose") ||
               content.contains("salary") || content.contains("compensation");
    }

    private boolean isFromJobBoard(EmailInfo email) {
        if (email.getSenderDomain() == null) return false;
        for (String domain : SENDER_DOMAIN_NAMES) {
            if (email.getSenderDomain().contains(domain)) {
                return true;
            }
        }
        return false;
    }

    private boolean isFromRecruiter(EmailInfo email) {
        String content = (email.getFrom() + " " + email.getContent()).toLowerCase();
        return content.contains("recruiter") || content.contains("recruitment") ||
               content.contains("talent acquisition") || content.contains("headhunter");
    }

    // ... [Keep all existing methods like getMessageContent, createDraft, etc.]

    /**
     * Inner class to hold email information
     */
    public static class EmailInfo {
        private String from;
        private String subject;
        private Date sentDate;
        private String content;
        private int relevanceScore;
        private String senderDomain;

        // Getters and setters
        public String getFrom() { return from; }
        public void setFrom(String from) { this.from = from; }

        public String getSubject() { return subject; }
        public void setSubject(String subject) { this.subject = subject; }

        public Date getSentDate() { return sentDate; }
        public void setSentDate(Date sentDate) { this.sentDate = sentDate; }

        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }

        public int getRelevanceScore() { return relevanceScore; }
        public void setRelevanceScore(int relevanceScore) { this.relevanceScore = relevanceScore; }

        public String getSenderDomain() { return senderDomain; }
        public void setSenderDomain(String senderDomain) { this.senderDomain = senderDomain; }

        @Override
        public String toString() {
            return String.format("EmailInfo{from='%s', subject='%s', date=%s, score=%d}",
                    from, subject, sentDate, relevanceScore);
        }
    }

    private String getMessageContent(Message message) throws MessagingException {
        try {
            StringBuilder fullContent = new StringBuilder();

            // Add message metadata
            try {
                String from = Arrays.toString(message.getFrom());
                String subject = message.getSubject();
                Date sentDate = message.getSentDate();

                fullContent.append("From: ").append(from).append("\n");
                fullContent.append("Subject: ").append(subject).append("\n");
                fullContent.append("Date: ").append(sentDate).append("\n\n");
            } catch (MessagingException e) {
                System.err.println("Error reading message metadata: " + e.getMessage());
            }

            // Add message content
            Object content = message.getContent();
            if (content instanceof String) {
                fullContent.append((String) content);
            } else if (content instanceof Multipart) {
                Multipart multipart = (Multipart) content;
                for (int i = 0; i < multipart.getCount(); i++) {
                    BodyPart bodyPart = multipart.getBodyPart(i);
                    if (bodyPart.getContentType().toLowerCase().startsWith("text/plain")) {
                        fullContent.append(bodyPart.getContent().toString());
                    } else if (bodyPart.getContentType().toLowerCase().startsWith("text/html")) {
                        // Optionally handle HTML content
                        // You might want to strip HTML tags or convert to plain text
                        String htmlContent = bodyPart.getContent().toString();
                        // For now, just append as is
                        fullContent.append(htmlContent);
                    }
                }
            }
            return fullContent.toString();
        } catch (Exception e) {
            return "Error extracting content: " + e.getMessage();
        }
    }

    /**
     * Load existing emails from JSON file
     * @param filePath Path to the JSON file
     * @return List of existing EmailInfo objects, or empty list if file doesn't exist
     */
    private static List<EmailInfo> loadExistingEmails(String filePath) {
        try {
            File file = new File(filePath);
            if (!file.exists()) {
                System.out.println("📄 No existing file found, starting fresh");
                return new ArrayList<>();
            }

            ObjectMapper mapper = new ObjectMapper();
            List<EmailInfo> existingEmails = mapper.readValue(
                    file,
                    mapper.getTypeFactory().constructCollectionType(List.class, EmailInfo.class)
            );
            return existingEmails;
        } catch (IOException e) {
            System.err.println("⚠️  Warning: Could not load existing emails: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Check if an email is a duplicate based on subject, sender, and date
     * @param newEmail The new email to check
     * @param existingEmails List of existing emails
     * @return true if duplicate found, false otherwise
     */
    private static boolean isDuplicate(EmailInfo newEmail, List<EmailInfo> existingEmails) {
        for (EmailInfo existing : existingEmails) {
            // Consider it a duplicate if subject, from, and date match
            boolean subjectMatch = newEmail.getSubject() != null &&
                                   newEmail.getSubject().equals(existing.getSubject());
            boolean fromMatch = newEmail.getFrom() != null &&
                                newEmail.getFrom().equals(existing.getFrom());
            boolean dateMatch = newEmail.getSentDate() != null &&
                                existing.getSentDate() != null &&
                                newEmail.getSentDate().equals(existing.getSentDate());

            if (subjectMatch && fromMatch && dateMatch) {
                return true;
            }
        }
        return false;
    }

    /**
     * Truncate a string to a maximum length
     * @param text The text to truncate
     * @param maxLength Maximum length
     * @return Truncated string with "..." if needed
     */
    private static String truncate(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength - 3) + "...";
    }

    /**
     * Save job opportunities to JSON file
     * @param jobOpportunities List of EmailInfo objects to save
     * @param outputFilePath Path where to save the JSON file
     */
    public static void saveJobOpportunitiesToJson(List<EmailInfo> jobOpportunities, String outputFilePath) {
        saveJobOpportunitiesToJson(jobOpportunities, outputFilePath, true);
    }

    /**
     * Save job opportunities to JSON file with optional verbose output
     * @param jobOpportunities List of EmailInfo objects to save
     * @param outputFilePath Path where to save the JSON file
     * @param verbose Whether to print detailed output
     */
    public static void saveJobOpportunitiesToJson(List<EmailInfo> jobOpportunities, String outputFilePath, boolean verbose) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.enable(SerializationFeature.INDENT_OUTPUT);

            File outputFile = new File(outputFilePath);

            // Create parent directories if they don't exist
            File parentDir = outputFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
                if (verbose) {
                    System.out.println("Created directory: " + parentDir.getAbsolutePath());
                }
            }

            mapper.writeValue(outputFile, jobOpportunities);
            if (verbose) {
                System.out.println("\n✓ Successfully saved " + jobOpportunities.size() + " job opportunities to: " + outputFilePath);
                System.out.println("  File size: " + outputFile.length() + " bytes");
            }
        } catch (IOException e) {
            System.err.println("ERROR: Failed to save job opportunities to JSON: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Enhanced main method for testing
    public static void main(String[] args) {
        // Load credentials from CSV file
        String gmailUsername = "fbullini@gmail.com";  // Change this to the email you want to search
        String gmailPassword = EmailStaticLib.getGmailAppPassword(gmailUsername);
        String agentId = "EmailProcessor_001"; //App register  in Google account

        if (gmailPassword == null) {
            System.err.println("Failed to load Gmail app password. Exiting.");
            return;
        }

        // Job search criteria - configured by developer
        String jobPosition = "Chief Technology Officer";
        String jobSeniority = "Senior";
        String contractType = "Permanent Position";
        String[] preferredLocations = {"Paris", "Remote", "France", "Europe"};
        String[] requiredSkills = {"Java", "Cloud", "Architecture", "Leadership", "Strategy"};
        String[] preferredCompanies = {"Tech Startups", "Scale-ups", "SaaS"};
        int minSalary = 45000; // EUR
        boolean remoteAcceptable = true;

        // Search parameters
        int daysToSearch = 7;//7 in dev env for quicker answers and test;  30 much better in prod
        int maxResultsPerCategory = 5;
        String primaryTopic = "job"; // job, internship, freelance

        // Email thread parameters (for response generation)
        String respondToEmail = "recruiter@company.com";// not used by now
        String yourEmail = "fbullo@gmail.com"; // not used by now

        GmailEmailProcessor02 processor = new GmailEmailProcessor02(gmailUsername, gmailPassword, agentId);

        try {
            System.out.println("╔════════════════════════════════════════════════════════════════════╗");
            System.out.println("║         Gmail Job Search Processor - Configuration                 ║");
            System.out.println("╚════════════════════════════════════════════════════════════════════╝");
            System.out.println();
            System.out.println("📧 EMAIL ACCOUNT:");
            System.out.println("   Account: " + gmailUsername);
            System.out.println("   Agent ID: " + agentId);
            System.out.println("   Folder: INBOX");
            System.out.println();
            System.out.println("🔍 SEARCH CRITERIA (IMAP Level - NOT STRICT):");
            System.out.println("   Time Range: Last " + daysToSearch + " days");
            System.out.println("   Max Results per Category: " + maxResultsPerCategory);
            System.out.println("   Primary Topic: " + primaryTopic);
            System.out.println();
            System.out.println("   ⚙️  HOW KEYWORD SEARCH WORKS:");
            System.out.println("   ═══════════════════════════════════════════════════════════════");
            String[] jobKeywords = TOPIC_KEYWORDS.get("job");
            System.out.println("   Search Focus: ACTUAL JOB OFFERS (not news/alerts/market info)");
            System.out.println("   Search Logic: BROAD retrieval + SMART filtering");
            System.out.println();
            System.out.println("   IMAP Query uses FIRST 5 keywords:");
            System.out.println("   ➤ \"" + jobKeywords[0] + "\" OR \"" + jobKeywords[1] + "\" OR \"" +
                               jobKeywords[2] + "\" OR \"" + jobKeywords[3] + "\" OR \"" + jobKeywords[4] + "\"");
            System.out.println("   ➤ Searches in: SUBJECT or BODY");
            System.out.println();
            System.out.println("   ✓ INCLUDES: Actual job offers with positions & companies");
            System.out.println("   ✗ EXCLUDES: Newsletters, job alerts, career tips, market news");
            System.out.println();
            System.out.println("   Smart Filters Applied (Post-Retrieval):");
            System.out.println("   + HIGH PRIORITY: 'apply now', 'join our team', 'position available'");
            System.out.println("   + BONUS: Mentions of specific positions, company names");
            System.out.println("   - PENALTY: 'newsletter', 'job alert', 'recommended for you'");
            System.out.println("   - REJECT: Emails without offer indicators");
            System.out.println();
            System.out.println("   🌍 All " + jobKeywords.length + " keywords (multilingual):");
            System.out.println("   EN: " + String.join(", ", Arrays.copyOfRange(jobKeywords, 0, Math.min(18, jobKeywords.length))));
            if (jobKeywords.length > 18) {
                System.out.println("   FR: " + String.join(", ", Arrays.copyOfRange(jobKeywords, 18, Math.min(23, jobKeywords.length))));
            }
            if (jobKeywords.length > 23) {
                System.out.println("   IT: " + String.join(", ", Arrays.copyOfRange(jobKeywords, 23, jobKeywords.length)));
            }
            System.out.println();
            System.out.println("   Note: Only first 5 keywords in IMAP query (performance).");
            System.out.println("         All keywords used for scoring after retrieval.");
            System.out.println("   ═══════════════════════════════════════════════════════════════");
            System.out.println();
            System.out.println("📮 SENDER CONSTRAINTS:");
            System.out.println("   Basic Search: NO CONSTRAINTS (searches emails from ALL senders)");
            System.out.println("   Premium Boards Search: ONLY specific job sites (LinkedIn, Indeed, etc.)");
            System.out.println("      - jobs-noreply@linkedin.com");
            System.out.println("      - noreply@indeed.com");
            System.out.println("      - noreply@glassdoor.com");
            System.out.println("      - noreply@angellist.com");
            System.out.println("      - noreply@hired.com");
            System.out.println("      - noreply@triplebyte.com");
            System.out.println();
            System.out.println("🎯 CONTENT FILTER CONFIGURATION:");
            System.out.println("   Position Filter: DISABLED (will return all positions)");
            System.out.println("   Seniority Filter: DISABLED (will return all levels)");
            System.out.println("   Contract Type Filter: DISABLED (permanent, contract, freelance)");
            System.out.println("   Location Filter: DISABLED (all locations)");
            System.out.println("   Skills Filter: DISABLED (no skill matching)");
            System.out.println();
            System.out.println("💡 NOTE: To enable detailed content filtering, uncomment the 'TARGETED JOB");
            System.out.println("   SEARCH' section in the code and configure these criteria:");
            System.out.println("   - Position: " + jobPosition);
            System.out.println("   - Seniority: " + jobSeniority);
            System.out.println("   - Contract: " + contractType);
            System.out.println("   - Locations: " + String.join(", ", preferredLocations));
            System.out.println("   - Skills: " + String.join(", ", requiredSkills));
            System.out.println("   - Min Salary: €" + minSalary);
            System.out.println("   - Remote Work: " + (remoteAcceptable ? "Acceptable" : "Office only"));
            System.out.println();
            System.out.println("🏆 RELEVANCE SCORING:");
            System.out.println("   Job domains boost: " + String.join(", ", SENDER_DOMAIN_NAMES));
            System.out.println();
            System.out.println("════════════════════════════════════════════════════════════════════");
            System.out.println();

            // EXAMPLE 1: Default search - NO FILTERS, just get all job emails
            // NOW WITH INCREMENTAL SAVING - emails are saved as they are found!
            System.out.println("--- BASIC JOB SEARCH (WITH INCREMENTAL SAVING) ---");
            String outputPath = System.getProperty("user.dir") + "/tools_data/job_opportunities_emails.json";
            List<EmailInfo> basicResults = searchWithJobCriteria(
                    processor, null, null, null, null, null, daysToSearch, outputPath
            );
            System.out.println("\n✓ Basic job search complete: " + basicResults.size() + " total job emails");
            System.out.println("✓ All emails saved to: " + outputPath);
            displayTargetedResults(basicResults, maxResultsPerCategory);

            // EXAMPLE 2: Filtered search with specific criteria (optional)
            // Uncomment the block below to enable detailed filtering
            /*
            System.out.println("\n╔════════════════════════════════════════════════════════════════════╗");
            System.out.println("║              TARGETED JOB SEARCH (FILTERS ENABLED)                 ║");
            System.out.println("╚════════════════════════════════════════════════════════════════════╝");
            System.out.println();
            System.out.println("🎯 ACTIVE FILTERS:");
            System.out.println("   ✓ Position: " + jobPosition);
            System.out.println("   ✓ Seniority: " + jobSeniority);
            System.out.println("   ✓ Contract Type: " + contractType);
            System.out.println("   ✓ Locations: " + String.join(", ", preferredLocations));
            System.out.println("   ✓ Required Skills: " + String.join(", ", requiredSkills) + " (min 2 matches)");
            System.out.println("   ✓ Min Salary: €" + minSalary);
            System.out.println("   ✓ Remote Work: " + (remoteAcceptable ? "Acceptable" : "Office only"));
            System.out.println();
            System.out.println("Searching with detailed criteria...");
            System.out.println();

            List<EmailInfo> targetedResults = searchWithJobCriteria(
                    processor, jobPosition, jobSeniority, contractType,
                    preferredLocations, requiredSkills, daysToSearch
            );
            System.out.println("\n✓ Targeted job search found " + targetedResults.size() + " highly relevant emails");
            displayTargetedResults(targetedResults, maxResultsPerCategory);
            */

            // 2. Get categorized job emails
            System.out.println("\n--- CATEGORIZED JOB EMAILS ---");
            Map<String, List<EmailInfo>> categorized = processor.getJobEmailsCategorized(daysToSearch);

            // Display statistics
            int totalEmails = categorized.values().stream().mapToInt(List::size).sum();
            System.out.println("Total job-related emails found: " + totalEmails);

            // Show high priority emails
            List<EmailInfo> highPriority = categorized.get("High Priority");
            if (highPriority != null && !highPriority.isEmpty()) {
                System.out.println("\n★ HIGH PRIORITY OPPORTUNITIES (" + highPriority.size() + " total):");
                displayEmailList(highPriority, maxResultsPerCategory);
            }

            // 3. Search from premium job sites
            System.out.println("\n--- PREMIUM JOB BOARDS ---");
            List<String> premiumJobSites = Arrays.asList(
                    "jobs-noreply@linkedin.com",
                    "noreply@indeed.com",
                    "noreply@glassdoor.com",
                    "noreply@angellist.com",
                    "noreply@hired.com",
                    "noreply@triplebyte.com"
            );

            List<EmailInfo> premiumEmails = processor.searchTopicEmails(
                    primaryTopic, maxResultsPerCategory * 2, 7, premiumJobSites
            );

            // Filter for CTO/senior positions
            List<EmailInfo> seniorPositions = premiumEmails.stream()
                    .filter(e -> isRelevantPosition(e, jobPosition, jobSeniority))
                    .collect(Collectors.toList());

            System.out.println("Found " + seniorPositions.size() + " senior positions from premium job boards");
            displayEmailList(seniorPositions, maxResultsPerCategory);

            // 4. Generate automated responses for interesting positions (optional)
            // Uncomment to enable automated response generation
            /*
            System.out.println("\n--- AUTOMATED RESPONSE GENERATION ---");

            // Find emails that might need responses
            List<EmailInfo> needsResponse = identifyEmailsNeedingResponse(
                    basicResults, jobPosition, minSalary
            );

            if (!needsResponse.isEmpty()) {
                System.out.println("Found " + needsResponse.size() + " positions that match criteria for response");

                // Generate a response for the most relevant one
                EmailInfo topMatch = needsResponse.get(0);
                System.out.println("\nGenerating response for: " + topMatch.getSubject());

                // Create personalized system prompt based on job criteria
                String personalizedPrompt = createPersonalizedPrompt(
                        jobPosition, jobSeniority, requiredSkills, minSalary
                );

                // Generate response
                String generatedResponse = processor.generateResponse(
                        Arrays.asList(topMatch.getContent()),
                        "gpt-4o-mini",
                        ""
                );

                System.out.println("\nGenerated Response Preview:");
                System.out.println("-".repeat(50));
                System.out.println(generatedResponse.substring(0, Math.min(300, generatedResponse.length())) + "...");
                System.out.println("-".repeat(50));

                // Create draft
                Message draft = processor.createDraft(
                        topMatch.getFrom(),
                        "Re: " + topMatch.getSubject(),
                        generatedResponse
                );
                System.out.println("\n✓ Draft created successfully!");
            }
            */

            // 5. Generate daily summary report
            System.out.println("\n--- DAILY SUMMARY REPORT ---");
            generateSummaryReport(categorized, basicResults, jobPosition, jobSeniority);

            // 6. Display LLM cost summary
            processor.costSummary.printSummary();

        } catch (MessagingException e) {
            System.err.println("Error processing emails: " + e.getMessage());
            e.printStackTrace();
        }
    }
    // Original generateResponse method
    public String generateResponse(List<String> emailThread, String modelName, String cloudProvider) {
        // Prepare the system prompt
        String systemPrompt = "You are an email assistant. Analyze the email thread provided and generate a professional " +
                "response to the most recent email. Maintain a professional tone and address all points " +
                "raised in the latest email. Be concise but thorough.";

        // Prepare the user prompt from the email thread
        StringBuilder userPrompt = new StringBuilder();
        userPrompt.append("Here is an email thread, with the most recent email first. " +
                "Please analyze it and generate a response:\n\n");
        for (int i = 0; i < emailThread.size(); i++) {
            userPrompt.append("Email ").append(i + 1).append(":\n");
            userPrompt.append(emailThread.get(i)).append("\n\n");
        }
        userPrompt.append("Please generate a professional response to the most recent email (Email 1).");

        // Call OpenAI API with usage tracking
        LLMUsageInfo usageInfo = OpenAiRESTApiCaller.callerWithUsage(
                agentId,                         // Agent ID
                modelName,                      // Model name
                systemPrompt,                    // System prompt
                userPrompt.toString(),           // User prompt
                "EmailResponseGenerator",        // LLM provider tool name
                0.1,                            // Temperature
                500                             // Max output tokens
        );

        // Track cost
        costSummary.addUsage(usageInfo);

        return usageInfo.getResponse();
    }

    // Enhanced generateResponse method with custom system prompt for job searches
    public String generateResponse(List<String> emailThread, String modelName, String cloudProvider,
                                   String customSystemPrompt, Map<String, Object> jobCriteria) {

        // Use custom prompt if provided, otherwise build job-specific prompt
        String systemPrompt = customSystemPrompt;
        if (systemPrompt == null || systemPrompt.isEmpty()) {
            systemPrompt = buildJobResponseSystemPrompt(jobCriteria);
        }

        // Prepare the user prompt from the email thread
        StringBuilder userPrompt = new StringBuilder();
        userPrompt.append("Here is an email about a job opportunity. Please analyze it and generate a professional, " +
                "personalized response that shows genuine interest while maintaining professionalism:\n\n");

        for (int i = 0; i < emailThread.size(); i++) {
            userPrompt.append("Email ").append(i + 1).append(":\n");
            userPrompt.append(emailThread.get(i)).append("\n\n");
        }

        // Add specific instructions based on job criteria
        if (jobCriteria != null) {
            userPrompt.append("\nImportant points to address in your response:\n");

            if (jobCriteria.containsKey("skills")) {
                userPrompt.append("- Mention relevant experience with: ")
                        .append(jobCriteria.get("skills")).append("\n");
            }

            if (jobCriteria.containsKey("minSalary")) {
                userPrompt.append("- Politely inquire about compensation range to ensure alignment with expectations\n");
            }

            if (jobCriteria.containsKey("remote")) {
                userPrompt.append("- Confirm remote work arrangements and flexibility\n");
            }

            userPrompt.append("- Ask about the team structure and technical challenges\n");
            userPrompt.append("- Express enthusiasm while remaining professional\n");
        }

        // Call OpenAI API with usage tracking
        LLMUsageInfo usageInfo = OpenAiRESTApiCaller.callerWithUsage(
                agentId,                         // Agent ID
                modelName,                      // Model name
                systemPrompt,                    // System prompt
                userPrompt.toString(),           // User prompt
                "JobEmailResponseGenerator",     // LLM provider tool name
                0.1,                            // Temperature
                700                             // Max output tokens for longer responses
        );

        // Track cost
        costSummary.addUsage(usageInfo);

        return usageInfo.getResponse();
    }

    // Helper method to build job-specific system prompt
    private String buildJobResponseSystemPrompt(Map<String, Object> jobCriteria) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are a senior technology executive responding to job opportunities. ");

        if (jobCriteria != null) {
            if (jobCriteria.containsKey("position")) {
                prompt.append("You are interested in ").append(jobCriteria.get("position")).append(" roles. ");
            }

            if (jobCriteria.containsKey("seniority")) {
                prompt.append("You have ").append(jobCriteria.get("seniority"))
                        .append("-level experience and are looking for similar positions. ");
            }

            if (jobCriteria.containsKey("location")) {
                prompt.append("You prefer positions in ").append(jobCriteria.get("location")).append(". ");
            }
        }

        prompt.append("Generate responses that are professional yet personable, showing genuine interest ")
                .append("while subtly qualifying the opportunity to ensure it meets your senior-level expectations. ")
                .append("Be concise but thorough, and always end with a clear next step.");

        return prompt.toString();
    }

    // Original createDraft method
    public Message createDraft(String to, String subject, String messageBody) throws MessagingException {
        Properties props = new Properties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.host", "smtp.gmail.com");
        props.put("mail.smtp.port", "587");

        Session session = Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(username, password);
            }
        });

        Message message = new MimeMessage(session);
        message.setFrom(new InternetAddress(username));
        message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to));
        message.setSubject(subject);
        message.setText(messageBody);

        // Save to Drafts folder
        Store store = session.getStore("imaps");
        store.connect("imap.gmail.com", username, password);

        Folder draftsFolder = findDraftsFolder(store);
        if (draftsFolder == null) {
            throw new MessagingException("Could not find Drafts folder. Please check your Gmail settings.");
        }

        try {
            draftsFolder.open(Folder.READ_WRITE);
            message.setFlag(Flags.Flag.DRAFT, true);
            draftsFolder.appendMessages(new Message[]{message});

            // Get the appended message with its Message-ID
            Message[] messages = draftsFolder.getMessages();
            Message draftMessage = messages[messages.length - 1];

            return draftMessage;
        } finally {
            if (draftsFolder != null && draftsFolder.isOpen()) {
                draftsFolder.close(false);
            }
            if (store != null && store.isConnected()) {
                store.close();
            }
        }
    }

    // Enhanced createDraft method with HTML support and signature
    public Message createDraft(String to, String subject, String messageBody,
                               boolean useHtml, String signature) throws MessagingException {
        Properties props = new Properties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.host", "smtp.gmail.com");
        props.put("mail.smtp.port", "587");

        Session session = Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(username, password);
            }
        });

        Message message = new MimeMessage(session);
        message.setFrom(new InternetAddress(username));
        message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to));
        message.setSubject(subject);

        // Add signature if provided
        String finalBody = messageBody;
        if (signature != null && !signature.isEmpty()) {
            finalBody = messageBody + "\n\n" + signature;
        }

        // Set content based on format
        if (useHtml) {
            message.setContent(finalBody, "text/html; charset=utf-8");
        } else {
            message.setText(finalBody);
        }

        // Save to Drafts folder
        Store store = session.getStore("imaps");
        store.connect("imap.gmail.com", username, password);

        Folder draftsFolder = findDraftsFolder(store);
        if (draftsFolder == null) {
            throw new MessagingException("Could not find Drafts folder. Please check your Gmail settings.");
        }

        try {
            draftsFolder.open(Folder.READ_WRITE);
            message.setFlag(Flags.Flag.DRAFT, true);
            draftsFolder.appendMessages(new Message[]{message});

            // Get the appended message
            Message[] messages = draftsFolder.getMessages();
            Message draftMessage = messages[messages.length - 1];

            System.out.println("Draft created: " + subject + " (To: " + to + ")");

            return draftMessage;
        } finally {
            if (draftsFolder != null && draftsFolder.isOpen()) {
                draftsFolder.close(false);
            }
            if (store != null && store.isConnected()) {
                store.close();
            }
        }
    }

    // Batch draft creation for multiple opportunities
    public List<Message> createMultipleDrafts(List<EmailInfo> opportunities,
                                              Map<String, Object> jobCriteria,
                                              String modelName) throws MessagingException {
        List<Message> drafts = new ArrayList<>();

        for (EmailInfo opportunity : opportunities) {
            try {
                // Generate personalized response for each opportunity
                String response = generateResponse(
                        Arrays.asList(opportunity.getContent()),
                        modelName,
                        "",
                        null,
                        jobCriteria
                );

                // Create draft with professional signature
                String signature = "Best regards,\n" +
                        "Frank Bullo\n" +
                        "Senior Technology Executive\n" +
                        "LinkedIn: linkedin.com/in/frankbullo";

                Message draft = createDraft(
                        extractReplyEmail(opportunity),
                        "Re: " + opportunity.getSubject(),
                        response,
                        false,
                        signature
                );

                drafts.add(draft);

                // Add delay to avoid rate limiting
                Thread.sleep(1000);

            } catch (Exception e) {
                System.err.println("Error creating draft for: " + opportunity.getSubject());
                e.printStackTrace();
            }
        }

        return drafts;
    }

    // Helper method to extract reply-to email
    private String extractReplyEmail(EmailInfo emailInfo) {
        String from = emailInfo.getFrom();

        // Extract email address from "Name <email@domain.com>" format
        if (from.contains("<") && from.contains(">")) {
            int start = from.indexOf("<") + 1;
            int end = from.indexOf(">");
            return from.substring(start, end);
        }

        // Return as-is if already in email format
        return from;
    }

    // Helper method to search with specific job criteria
    // Set parameters to null to disable specific filters
    private static List<EmailInfo> searchWithJobCriteria(
            GmailEmailProcessor02 processor,
            String position,        // null = no position filter
            String seniority,       // null = no seniority filter
            String contractType,    // null = no contract type filter
            String[] locations,     // null or empty = no location filter
            String[] skills,        // null or empty = no skills filter
            int daysBack,
            String outputFilePath) throws MessagingException {  // Pass through for incremental saving

        // Search for general job emails first WITH INCREMENTAL SAVING
        System.out.println("\n[Targeted Search] Searching for general job emails...");
        List<EmailInfo> allJobs = processor.searchTopicEmails("job", 100, daysBack, null, outputFilePath);

        // Build filter description
        StringBuilder filterDesc = new StringBuilder("[Targeted Search] Active filters: ");
        boolean hasFilters = false;
        if (position != null && !position.isEmpty()) {
            filterDesc.append("position=").append(position).append(" ");
            hasFilters = true;
        }
        if (seniority != null && !seniority.isEmpty()) {
            filterDesc.append("seniority=").append(seniority).append(" ");
            hasFilters = true;
        }
        if (locations != null && locations.length > 0) {
            filterDesc.append("locations=").append(Arrays.toString(locations)).append(" ");
            hasFilters = true;
        }
        if (skills != null && skills.length > 0) {
            filterDesc.append("skills=").append(Arrays.toString(skills)).append(" ");
            hasFilters = true;
        }
        if (!hasFilters) {
            filterDesc.append("NONE (returning all job emails)");
        }
        System.out.println(filterDesc.toString());

        // Filter based on criteria
        List<EmailInfo> filtered = allJobs.stream()
                .filter(email -> {
                    String content = (email.getSubject() + " " + email.getContent()).toLowerCase();

                    int enhancedScore = email.getRelevanceScore();
                    boolean meetsRequirements = true;

                    // Check position match (optional)
                    boolean positionMatch = true;
                    if (position != null && !position.isEmpty()) {
                        positionMatch = content.contains(position.toLowerCase()) ||
                                content.contains("cto") ||
                                content.contains("chief technology") ||
                                content.contains("tech lead") ||
                                content.contains("engineering director");
                        if (positionMatch) enhancedScore += 5;
                    }

                    // Check seniority (optional)
                    boolean seniorityMatch = true;
                    if (seniority != null && !seniority.isEmpty()) {
                        seniorityMatch = content.contains(seniority.toLowerCase()) ||
                                content.contains("senior") ||
                                content.contains("executive") ||
                                content.contains("leadership");
                        if (seniorityMatch) enhancedScore += 3;
                    }

                    // Check contract type (optional)
                    boolean contractMatch = true;
                    if (contractType != null && !contractType.isEmpty()) {
                        contractMatch = contractType.equals("Permanent Position") ?
                                !content.contains("contract") && !content.contains("freelance") :
                                content.contains(contractType.toLowerCase());
                        if (contractMatch) enhancedScore += 2;
                    }

                    // Check location (optional)
                    boolean locationMatch = true;
                    if (locations != null && locations.length > 0) {
                        locationMatch = Arrays.stream(locations)
                                .anyMatch(loc -> content.contains(loc.toLowerCase()));
                        if (locationMatch) enhancedScore += 3;
                    }

                    // Check skills (optional)
                    long skillMatches = 0;
                    if (skills != null && skills.length > 0) {
                        skillMatches = Arrays.stream(skills)
                                .filter(skill -> content.contains(skill.toLowerCase()))
                                .count();
                        enhancedScore += (int)(skillMatches * 2);
                    }

                    email.setRelevanceScore(enhancedScore);

                    // Apply filters only if they are specified
                    if (position != null && !position.isEmpty()) {
                        meetsRequirements = meetsRequirements && (positionMatch || seniorityMatch);
                    }
                    if (locations != null && locations.length > 0) {
                        meetsRequirements = meetsRequirements && locationMatch;
                    }
                    if (skills != null && skills.length > 0) {
                        meetsRequirements = meetsRequirements && skillMatches >= 2;
                    }

                    return meetsRequirements;
                })
                .sorted((e1, e2) -> Integer.compare(e2.getRelevanceScore(), e1.getRelevanceScore()))
                .collect(Collectors.toList());

        System.out.println("[Targeted Search] Found " + filtered.size() + " emails matching criteria");
        return filtered;
    }

    // Helper method to check if position is relevant
    private static boolean isRelevantPosition(EmailInfo email, String targetPosition, String seniority) {
        String content = (email.getSubject() + " " + email.getContent()).toLowerCase();
        return content.contains(targetPosition.toLowerCase()) ||
                content.contains("cto") ||
                content.contains(seniority.toLowerCase()) ||
                content.contains("executive") ||
                content.contains("leadership");
    }

    // Helper method to identify emails needing response
    private static List<EmailInfo> identifyEmailsNeedingResponse(
            List<EmailInfo> emails, String position, int minSalary) {

        return emails.stream()
                .filter(email -> {
                    String content = email.getContent().toLowerCase();
                    // Check if it's a direct opportunity or invitation
                    return (content.contains("interested") ||
                            content.contains("opportunity") ||
                            content.contains("would you") ||
                            content.contains("invitation") ||
                            content.contains("opening for")) &&
                            email.getRelevanceScore() >= 10;
                })
                .limit(3)
                .collect(Collectors.toList());
    }

    // Helper method to create personalized prompt
    private static String createPersonalizedPrompt(
            String position, String seniority, String[] skills, int minSalary) {

        return String.format(
                "You are responding as a %s-level %s with expertise in %s. " +
                        "You are interested in positions with competitive compensation (minimum €%d). " +
                        "Be professional but show genuine interest if the role aligns with these criteria. " +
                        "Ask relevant questions about the tech stack, team size, and company culture.",
                seniority, position, String.join(", ", skills), minSalary
        );
    }

    // Helper method to display targeted results
    private static void displayTargetedResults(List<EmailInfo> results, int maxDisplay) {
        if (results.isEmpty()) {
            System.out.println("No emails matching your specific criteria found.");
            return;
        }

        System.out.println("Found " + results.size() + " positions matching your criteria:\n");

        int count = 0;
        for (EmailInfo email : results) {
            if (count++ >= maxDisplay) {
                System.out.println("\n... and " + (results.size() - maxDisplay) + " more matching positions");
                break;
            }

            System.out.println("📧 Score: " + email.getRelevanceScore() + " | " + email.getSubject());
            System.out.println("   From: " + email.getFrom() + " | " + email.getSentDate());

            // Extract salary if mentioned
            String content = email.getContent().toLowerCase();
            if (content.contains("€") || content.contains("eur") || content.contains("salary")) {
                System.out.println("   💰 Contains salary information");
            }
            if (content.contains("remote") || content.contains("hybrid")) {
                System.out.println("   🏠 Remote/Hybrid opportunity");
            }
            System.out.println();
        }
    }

    // Helper method to display email list
    private static void displayEmailList(List<EmailInfo> emails, int maxDisplay) {
        int count = 0;
        for (EmailInfo email : emails) {
            if (count++ >= maxDisplay) {
                System.out.println("... and " + (emails.size() - maxDisplay) + " more");
                break;
            }
            System.out.println("  • " + email.getSubject());
            System.out.println("    From: " + email.getFrom() + " | Score: " + email.getRelevanceScore());
        }
    }

    // Helper method to generate summary report
    private static void generateSummaryReport(
            Map<String, List<EmailInfo>> categorized,
            List<EmailInfo> targeted,
            String position,
            String seniority) {

        System.out.println("Summary for " + seniority + " " + position + " positions:");
        System.out.println("━".repeat(50));

        // Statistics
        int totalJobs = categorized.values().stream().mapToInt(List::size).sum();
        int highPriority = categorized.getOrDefault("High Priority", new ArrayList<>()).size();
        int directOffers = categorized.getOrDefault("Direct Offers", new ArrayList<>()).size();
        int matchingCriteria = targeted.size();

        System.out.println("📊 Total job emails: " + totalJobs);
        System.out.println("⭐ High priority: " + highPriority);
        System.out.println("💼 Direct offers: " + directOffers);
        System.out.println("🎯 Matching your criteria: " + matchingCriteria);

        // Top sources
        System.out.println("\n📮 Top sources:");
        Map<String, Integer> sourceCount = new HashMap<>();
        for (List<EmailInfo> emails : categorized.values()) {
            for (EmailInfo email : emails) {
                String domain = email.getSenderDomain();
                if (domain != null) {
                    sourceCount.merge(domain, 1, Integer::sum);
                }
            }
        }

        sourceCount.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(5)
                .forEach(e -> System.out.println("  • " + e.getKey() + ": " + e.getValue() + " emails"));
    }
}