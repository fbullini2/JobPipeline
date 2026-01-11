package com.agty;

import com.agty.GmailEmailProcessor02.*;
import com.agty.urlextractor.URLExtractorRegistry;
import com.agty.urlextractor.URLExtractionResult;
import com.agty.urlextractor.URLValidator;
import com.agty.utils.LLMCostCalculator;
import com.agty.utils.LLMUsageInfo;
import com.agty.utils.OpenAiRESTApiCaller;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Extracts structured job opportunity data from email content using LLM.
 * Reads job_opportunities_emails.json and creates job_opportunities.json
 * with structured JobOpportunity objects.
 */
public class JobOpportunityExtractor {

    private final String agentId;
    private final String modelName;
    private final ObjectMapper mapper;
    private final LLMCostCalculator.CostSummary costSummary;
    private final URLExtractorRegistry urlExtractorRegistry;

    public JobOpportunityExtractor(String agentId, String modelName) {
        this.agentId = agentId;
        this.modelName = modelName;
        this.mapper = new ObjectMapper();
        this.mapper.enable(SerializationFeature.INDENT_OUTPUT);
        this.costSummary = new LLMCostCalculator.CostSummary();
        this.urlExtractorRegistry = new URLExtractorRegistry();
        System.out.println("  ✓ Initialized URL Extractor Registry with " +
                urlExtractorRegistry.getExtractorCount() + " specialized extractor(s)");
    }

    /**
     * Extract job opportunities from the emails JSON file
     * 
     * @param inputFilePath  Path to job_opportunities_emails.json
     * @param outputFilePath Path where to save job_opportunities.json
     * @return List of extracted JobOpportunity objects
     */
    public List<JobOpportunity> extractJobOpportunities(String inputFilePath, String outputFilePath)
            throws IOException {

        System.out.println("╔════════════════════════════════════════════════════════════════════╗");
        System.out.println("║         Job Opportunity Extractor - Starting                       ║");
        System.out.println("╚════════════════════════════════════════════════════════════════════╝");
        System.out.println();
        System.out.println("📥 Reading emails from: " + inputFilePath);

        // Load EmailInfo list from JSON
        List<EmailInfo> emails = loadEmailsFromJson(inputFilePath);
        System.out.println("✓ Loaded " + emails.size() + " emails to process");

        // Load existing opportunities if output file exists (resume functionality)
        List<JobOpportunity> jobOpportunities = loadExistingOpportunities(outputFilePath);
        Set<String> processedEmails = new HashSet<>();
        for (JobOpportunity opp : jobOpportunities) {
            // Track already processed emails by source email subject + from
            String key = opp.getSourceEmailSubject() + "|" + opp.getSourceEmailFrom();
            processedEmails.add(key);
        }

        if (jobOpportunities.size() > 0) {
            System.out.println("✓ Loaded " + jobOpportunities.size() + " existing job opportunities from output file");
            System.out.println("  Will skip already processed emails and continue from where we left off");
        }
        System.out.println();

        int processed = 0;
        int successful = 0;
        int failed = 0;
        int skipped = 0;

        // Track errors with details for final report
        List<ErrorDetail> errors = new ArrayList<>();

        for (EmailInfo email : emails) {
            processed++;
            System.out.println("─".repeat(70));
            System.out.println(String.format("Processing [%d/%d]: %s", processed, emails.size(),
                    truncate(email.getSubject(), 60)));
            System.out.println("From: " + truncate(email.getFrom(), 60));

            // Check if already processed
            String emailKey = email.getSubject() + "|" + email.getFrom();
            if (processedEmails.contains(emailKey)) {
                skipped++;
                System.out.println("⊘ Skipped - already processed");
                continue;
            }

            try {
                List<JobOpportunity> opportunities = extractFromEmail(email);
                if (opportunities != null && !opportunities.isEmpty()) {
                    jobOpportunities.addAll(opportunities);
                    processedEmails.add(emailKey);
                    successful++;
                    System.out.println("✓ Extracted " + opportunities.size() + " opportunity(ies):");
                    for (JobOpportunity opp : opportunities) {
                        System.out.println("  - " + truncate(opp.getTitle(), 50));
                        System.out.println("    Company: " + opp.getCompany());
                        System.out.println("    Location: " + opp.getLocation());
                    }

                    // INCREMENTAL SAVE: Save after each successful extraction
                    saveJobOpportunitiesToJson(jobOpportunities, outputFilePath);
                    System.out.println("  💾 Saved to file (incremental)");
                } else {
                    failed++;
                    System.out.println("✗ Failed to extract job opportunity (empty result)");
                    errors.add(new ErrorDetail(
                            email.getSubject(),
                            email.getFrom(),
                            "LLM returned null or empty response",
                            "ExtractionFailure"));
                }
            } catch (NoSuchMethodError e) {
                failed++;
                System.err.println("✗ DEPENDENCY ERROR: " + e.getMessage());
                System.err.println("  This is likely a Jackson library version conflict.");
                System.err.println("  Please rebuild the project: mvn clean install");
                System.err.println("  Continuing with next email...");
                errors.add(new ErrorDetail(
                        email.getSubject(),
                        email.getFrom(),
                        "Jackson library version conflict: " + e.getMessage(),
                        "DependencyError"));
            } catch (Exception e) {
                failed++;
                System.err.println("✗ Error processing email: " + e.getClass().getSimpleName());
                System.err.println("  Message: " + e.getMessage());
                System.err.println("  Continuing with next email...");
                errors.add(new ErrorDetail(
                        email.getSubject(),
                        email.getFrom(),
                        e.getMessage(),
                        e.getClass().getSimpleName()));
                // Only print stack trace in verbose mode to avoid cluttering output
                if (System.getProperty("verbose") != null) {
                    e.printStackTrace();
                }
            }

            // Add delay to avoid rate limiting
            if (processed < emails.size()) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        System.out.println();
        System.out.println("═".repeat(70));
        System.out.println("📊 EXTRACTION SUMMARY:");
        System.out.println("   Total emails in input: " + emails.size());
        System.out.println("   Already processed (skipped): " + skipped);
        System.out.println("   Newly processed: " + (processed - skipped));
        System.out.println("   Successfully extracted: " + successful);
        System.out.println("   Failed extractions: " + failed);
        System.out.println("   Total opportunities in file: " + jobOpportunities.size());
        System.out.println("═".repeat(70));

        // Display detailed error report if there were failures
        if (!errors.isEmpty()) {
            System.out.println();
            System.out.println("╔════════════════════════════════════════════════════════════════════╗");
            System.out.println("║                    ERROR REPORT                                    ║");
            System.out.println("╚════════════════════════════════════════════════════════════════════╝");
            System.out.println();
            System.out.println("Total Errors: " + errors.size());
            System.out.println();

            // Group errors by type
            Map<String, List<ErrorDetail>> errorsByType = new HashMap<>();
            for (ErrorDetail error : errors) {
                errorsByType.computeIfAbsent(error.errorType, k -> new ArrayList<>()).add(error);
            }

            for (Map.Entry<String, List<ErrorDetail>> entry : errorsByType.entrySet()) {
                System.out.println("❌ " + entry.getKey() + " (" + entry.getValue().size() + " errors):");
                System.out.println("─".repeat(70));
                for (int i = 0; i < entry.getValue().size(); i++) {
                    ErrorDetail error = entry.getValue().get(i);
                    System.out.println("  [" + (i + 1) + "] " + truncate(error.emailSubject, 55));
                    System.out.println("      From: " + truncate(error.emailFrom, 50));
                    System.out.println("      Error: " + truncate(error.errorMessage, 60));
                    System.out.println();
                }
            }
            System.out.println("═".repeat(70));
        }

        // Display cost summary
        costSummary.printSummary();
        System.out.println();

        // Final save with verbose output
        System.out.println();
        saveJobOpportunitiesToJson(jobOpportunities, outputFilePath, true);

        return jobOpportunities;
    }

    /**
     * Extract JobOpportunities from a single email using URL extraction + LLM
     */
    private List<JobOpportunity> extractFromEmail(EmailInfo email) {
        // STEP 1: Try direct HTML parsing for Cadremploi (regex extraction)
        if (email.getFrom() != null && email.getFrom().toLowerCase().contains("offres@alertes.cadremploi.fr")) {
            System.out.println("  → Detected Cadremploi email - attempting REGEX extraction...");

            com.agty.urlextractor.CadreMploiURLExtractor cadreMploiExtractor =
                new com.agty.urlextractor.CadreMploiURLExtractor();

            List<JobOpportunity> regexOpportunities = cadreMploiExtractor.extractJobOpportunities(
                email.getContent(),
                email.getSubject()
            );

            if (regexOpportunities != null && !regexOpportunities.isEmpty()) {
                System.out.println("  ✓ REGEX extraction successful! Extracted " + regexOpportunities.size() + " jobs");
                System.out.println("  ℹ Skipping LLM call (faster & cheaper)");

                // Add source email metadata to each opportunity
                for (JobOpportunity opportunity : regexOpportunities) {
                    opportunity.setSourceEmailSubject(email.getSubject());
                    opportunity.setSourceEmailFrom(email.getFrom());
                    opportunity.setSourceEmailDate(email.getSentDate() != null ? email.getSentDate().toString() : null);
                }

                return regexOpportunities;
            } else {
                System.out.println("  ⚠ REGEX extraction failed or returned no results");
                System.out.println("  → Falling back to LLM extraction...");
            }
        }

        // STEP 2: Pre-process URL extraction using regex (for other sources)
        System.out.println("  → Pre-processing: Attempting URL extraction...");
        URLExtractionResult urlExtractionResult = urlExtractorRegistry.extractURLs(
                email.getFrom(),
                email.getSubject(),
                email.getContent()
        );

        // Store portal name even if URL extraction failed (for portals that delegate to LLM)
        String detectedPortalName = null;
        if (urlExtractionResult != null && urlExtractionResult.getJobPortalName() != null) {
            detectedPortalName = urlExtractionResult.getJobPortalName();
        }

        // STEP 3: Build the system prompt
        String systemPrompt = buildExtractionSystemPrompt();

        // Build the user prompt with email content
        String userPrompt = buildExtractionUserPrompt(email);

        // STEP 4: Call LLM with usage tracking
        System.out.println("  → Calling LLM for extraction...");
        LLMUsageInfo usageInfo = OpenAiRESTApiCaller.callerWithUsage(
                agentId,
                modelName,
                systemPrompt,
                userPrompt,
                "JobOpportunityExtractor",
                0.1, // Low temperature for consistent extraction
                6000 // Max tokens for response (increased to handle multiple offers with URLs from HTML emails)
        );

        // Track cost
        costSummary.addUsage(usageInfo);

        String llmResponse = usageInfo.getResponse();
        if (llmResponse == null || llmResponse.trim().isEmpty()) {
            System.err.println("  ✗ LLM returned empty response");
            return null;
        }

        // STEP 5: Parse LLM response into List of JobOpportunity
        try {
            List<JobOpportunity> opportunities = parseLLMResponse(llmResponse);

            // STEP 6: Post-process - merge URL extraction results and validate
            for (JobOpportunity opportunity : opportunities) {
                // Add source email metadata
                opportunity.setSourceEmailSubject(email.getSubject());
                opportunity.setSourceEmailFrom(email.getFrom());
                opportunity.setSourceEmailDate(email.getSentDate() != null ? email.getSentDate().toString() : null);

                // Set portal name if detected (even if URL extraction failed)
                if (detectedPortalName != null && opportunity.getJobPortalName() == null) {
                    opportunity.setJobPortalName(detectedPortalName);
                }

                // Merge URL extraction results (if regex succeeded)
                if (urlExtractionResult != null && urlExtractionResult.isExtractionSuccess()) {
                    mergeURLExtractionResults(opportunity, urlExtractionResult);
                }

                // Validate and clean URLs
                validateAndCleanURLs(opportunity);
            }

            return opportunities;
        } catch (Exception e) {
            System.err.println("  ✗ Error parsing LLM response: " + e.getMessage());
            System.err.println("  LLM Response (preview): " + truncate(llmResponse, 200));
            return null;
        }
    }

    /**
     * Merge URL extraction results from regex into the JobOpportunity
     * Regex results take precedence over LLM results for known sources
     */
    private void mergeURLExtractionResults(JobOpportunity opportunity, URLExtractionResult urlResult) {
        // Portal name from regex takes precedence
        if (urlResult.getJobPortalName() != null && opportunity.getJobPortalName() == null) {
            opportunity.setJobPortalName(urlResult.getJobPortalName());
        }

        // Merge URLs - prefer regex results for known portals
        if (urlResult.getJobOfferURLForApplyOnJobPortal() != null) {
            opportunity.setJobOfferURLForApplyOnJobPortal(urlResult.getJobOfferURLForApplyOnJobPortal());
        }
        if (urlResult.getJobOfferURLForApplyOnCompanySite() != null) {
            opportunity.setJobOfferURLForApplyOnCompanySite(urlResult.getJobOfferURLForApplyOnCompanySite());
        }
        if (urlResult.getJobOfferURLForDescriptionOnJobPortal() != null) {
            opportunity.setJobOfferURLForDescriptionOnJobPortal(urlResult.getJobOfferURLForDescriptionOnJobPortal());
        }
        if (urlResult.getJobOfferURLForDescriptionOnCompanySite() != null) {
            opportunity.setJobOfferURLForDescriptionOnCompanySite(urlResult.getJobOfferURLForDescriptionOnCompanySite());
        }
    }

    /**
     * Validate all URLs in the JobOpportunity and remove invalid ones
     */
    private void validateAndCleanURLs(JobOpportunity opportunity) {
        // Validate and clean each URL field
        opportunity.setJobOfferURLForApplyOnJobPortal(
                validateURL(opportunity.getJobOfferURLForApplyOnJobPortal(), "Apply on Portal"));

        opportunity.setJobOfferURLForApplyOnCompanySite(
                validateURL(opportunity.getJobOfferURLForApplyOnCompanySite(), "Apply on Company"));

        opportunity.setJobOfferURLForDescriptionOnJobPortal(
                validateURL(opportunity.getJobOfferURLForDescriptionOnJobPortal(), "Description on Portal"));

        opportunity.setJobOfferURLForDescriptionOnCompanySite(
                validateURL(opportunity.getJobOfferURLForDescriptionOnCompanySite(), "Description on Company"));
    }

    /**
     * Validate a single URL and return it if valid, or null if invalid
     */
    private String validateURL(String url, String fieldName) {
        if (url == null || url.trim().isEmpty()) {
            return null;
        }

        URLValidator.ValidationResult validation = URLValidator.validate(url);
        if (!validation.isValid()) {
            System.err.println("  ⚠ Invalid URL in '" + fieldName + "' field: " + validation.getErrorMessage());
            System.err.println("    Rejected URL: " + truncate(url, 80));
            return null;  // Remove invalid URL
        }

        return url.trim();
    }

    /**
     * Build the system prompt for LLM extraction
     */
    private String buildExtractionSystemPrompt() {
        return "You are an expert job opportunity analyzer. Your task is to extract structured " +
                "information from job offer emails and return it in JSON format.\n\n" +
                "Extract the following fields from the email:\n" +
                "- title: The job title/position\n" +
                "- company: Company name\n" +
                "- job_portal_name: Name of job portal (Cadremploi, LinkedIn, Indeed, Apec, HelloWork, etc.) or null if direct from company\n" +
                "- job_offer_url_apply_portal: URL to APPLY/SUBMIT APPLICATION on the job portal (intermediary site)\n" +
                "- job_offer_url_apply_company: URL to APPLY/SUBMIT APPLICATION on the company's own website\n" +
                "- job_offer_url_description_portal: URL to VIEW/READ job description on the job portal\n" +
                "- job_offer_url_description_company: URL to VIEW/READ job description on company's website\n\n" +
                "URL Classification Guidelines:\n" +
                "  - 'Apply' URLs: Allow submitting CV/application, typically have 'apply', 'postuler', 'candidater' in URL or button text\n" +
                "  - 'Description' URLs: Only show job information, typically have 'voir', 'view', 'detail', 'offre' in URL or button text\n" +
                "  - 'Portal' URLs: On intermediary sites (Cadremploi, LinkedIn, Indeed, etc.)\n" +
                "  - 'Company' URLs: On the actual employer's website (company domain)\n" +
                "  - If you're unsure whether a URL is for apply or description, prefer using the description field\n" +
                "  - Use null for missing URLs\n\n" +
                "- fit_score: Your assessment of how good this opportunity is (0.0 to 10.0)\n" +
                "- location: Job location (city, country, or 'Remote')\n" +
                "- salary: Salary range if mentioned\n" +
                "- responsibilities: Key responsibilities (brief summary)\n" +
                "- skills_required: Required skills and technologies\n" +
                "- compensation: Total compensation including benefits\n" +
                "- employment_type: 'freelance' or 'employee'\n" +
                "- contract_type: 'permanent' or 'temporary'\n" +
                "- is_startup: true if it's a startup, false otherwise\n" +
                "- company_size: Company size ('1-10', '11-50', '51-200', '201-1000', '1000+', 'unknown')\n" +
                "- team_size_to_manage: Size of team to manage if mentioned\n" +
                "- additional_experience: Additional experience requirements\n" +
                "- work_languages: Required languages for work\n\n" +
                "IMPORTANT: If the email contains multiple job offers, return a JSON Array with one object per offer. " +
                "Each offer should have its specific URLs properly classified.\n\n" +
                "Return a JSON Object (if single opportunity) or a JSON Array (if multiple opportunities) containing these fields. " +
                "Use null for missing information. Do not include any explanatory text, only the JSON.";
    }

    /**
     * Build the user prompt with email content
     */
    private String buildExtractionUserPrompt(EmailInfo email) {
        StringBuilder prompt = new StringBuilder();

        // Check if content is HTML
        String content = email.getContent();
        boolean isHtml = content != null && content.trim().startsWith("<!DOCTYPE") ||
                         (content != null && content.contains("<html"));

        if (isHtml) {
            prompt.append("Extract job opportunity information from this HTML email.\n");
            prompt.append("IMPORTANT: The email content is in HTML format. Parse the HTML to extract:\n");
            prompt.append("- Job titles (look for heading text, job names)\n");
            prompt.append("- Company names\n");
            prompt.append("- All URLs (especially 'Voir l'offre' / 'View offer' links)\n");
            prompt.append("- Ignore CSS styles, DOCTYPE, meta tags, and formatting elements\n\n");
        } else {
            prompt.append("Extract job opportunity information from this email:\n\n");
        }

        // Include email metadata
        prompt.append("Email Subject: ").append(email.getSubject()).append("\n");
        prompt.append("From: ").append(email.getFrom()).append("\n\n");

        // Include email content (limit to avoid token limits)
        // For HTML emails, allow much more content to capture all job listings
        // HTML emails from job portals can be large (80-100KB) with multiple offers
        int maxLength = isHtml ? 80000 : 10000;

        if (content != null) {
            if (content.length() > maxLength) {
                prompt.append("Email Content (truncated):\n");
                prompt.append(content.substring(0, maxLength));
                prompt.append("\n\n[...content truncated at ").append(maxLength).append(" characters...]");
            } else {
                prompt.append("Email Content:\n");
                prompt.append(content);
            }
        }

        prompt.append("\n\nExtract ALL job opportunities from this email and return as JSON.");
        if (isHtml) {
            prompt.append("\nRemember: Parse the HTML carefully to find ALL job listings in the email.");
        }

        return prompt.toString();
    }

    /**
     * Parse LLM JSON response into List of JobOpportunity objects.
     * Handles both single JSON object and JSON array.
     * Robustly strips Markdown code blocks.
     */
    private List<JobOpportunity> parseLLMResponse(String llmResponse) throws IOException {
        try {
            // 1. Robust Markdown Stripping using Regex
            // Matches ```json ... ``` or just ``` ... ``` or strictly the content inside
            String cleanedJson = llmResponse.trim();

            // Remove ```json (case insensitive) at start
            if (cleanedJson.matches("(?s)^```(?i:json)?.*")) {
                cleanedJson = cleanedJson.replaceFirst("(?s)^```(?i:json)?", "");
            }
            // Remove trailing ```
            if (cleanedJson.matches("(?s).*```$")) {
                cleanedJson = cleanedJson.substring(0, cleanedJson.lastIndexOf("```"));
            }

            cleanedJson = cleanedJson.trim();

            // 2. Determine if Array or Object
            if (cleanedJson.startsWith("[")) {
                // Parse as Array
                if (!cleanedJson.endsWith("]")) {
                    throw new IOException("Invalid JSON array structure - starts with [ but doesn't end with ]. " +
                            "This likely means the LLM response was truncated. Try increasing max_tokens parameter. " +
                            "Response length: " + cleanedJson.length() + " chars");
                }
                return mapper.readValue(cleanedJson, new TypeReference<List<JobOpportunity>>() {
                });
            } else if (cleanedJson.startsWith("{")) {
                // Parse as Single Object
                if (!cleanedJson.endsWith("}")) {
                    throw new IOException("Invalid JSON object structure - starts with { but doesn't end with }. " +
                            "This likely means the LLM response was truncated. Try increasing max_tokens parameter. " +
                            "Response length: " + cleanedJson.length() + " chars");
                }
                JobOpportunity opp = mapper.readValue(cleanedJson, JobOpportunity.class);
                List<JobOpportunity> list = new ArrayList<>();
                list.add(opp);
                return list;
            } else {
                throw new IOException("Invalid JSON structure - doesn't start with { or [");
            }

        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            System.err.println("  ✗ JSON Processing Error: " + e.getMessage());
            System.err.println("  Response length: " + llmResponse.length() + " characters");
            System.err.println("  Response preview: " + truncate(llmResponse, 500));
            throw new IOException("Failed to parse JSON response: " + e.getMessage(), e);
        } catch (Exception e) {
            System.err.println("  ✗ Unexpected error parsing response: " + e.getClass().getName());
            System.err.println("  Error message: " + e.getMessage());
            System.err.println("  Response length: " + llmResponse.length() + " characters");
            System.err.println("  Response preview: " + truncate(llmResponse, 500));
            throw new IOException("Unexpected error during JSON parsing: " + e.getMessage(), e);
        }
    }

    /**
     * Load emails from JSON file
     */
    private List<EmailInfo> loadEmailsFromJson(String filePath) throws IOException {
        File inputFile = new File(filePath);
        if (!inputFile.exists()) {
            throw new IOException("Input file not found: " + filePath);
        }

        return mapper.readValue(inputFile, new TypeReference<List<EmailInfo>>() {
        });
    }

    /**
     * Load existing job opportunities from output file if it exists
     * This allows resuming from where we left off if the process was interrupted
     */
    private List<JobOpportunity> loadExistingOpportunities(String filePath) {
        File outputFile = new File(filePath);
        if (!outputFile.exists()) {
            return new ArrayList<>();
        }

        try {
            List<JobOpportunity> existing = mapper.readValue(outputFile,
                    new TypeReference<List<JobOpportunity>>() {
                    });
            return existing;
        } catch (IOException e) {
            System.err.println("Warning: Could not load existing opportunities from " + filePath);
            System.err.println("  " + e.getMessage());
            System.err.println("  Starting fresh...");
            return new ArrayList<>();
        }
    }

    /**
     * Save job opportunities to JSON file
     */
    private void saveJobOpportunitiesToJson(List<JobOpportunity> opportunities, String outputFilePath) {
        saveJobOpportunitiesToJson(opportunities, outputFilePath, false);
    }

    private void saveJobOpportunitiesToJson(List<JobOpportunity> opportunities, String outputFilePath,
            boolean verbose) {
        try {
            File outputFile = new File(outputFilePath);

            // Create parent directories if needed
            File parentDir = outputFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
                if (verbose) {
                    System.out.println("Created directory: " + parentDir.getAbsolutePath());
                }
            }

            mapper.writeValue(outputFile, opportunities);
            if (verbose) {
                System.out.println("✓ Successfully saved " + opportunities.size() +
                        " job opportunities to: " + outputFilePath);
                System.out.println("  File size: " + outputFile.length() + " bytes");
            }
        } catch (IOException e) {
            System.err.println("ERROR: Failed to save job opportunities: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Utility method to truncate strings for display
     */
    private String truncate(String str, int maxLength) {
        if (str == null)
            return "null";
        if (str.length() <= maxLength)
            return str;
        return str.substring(0, maxLength - 3) + "...";
    }

    /**
     * Main method for testing
     */
    public static void main(String[] args) {
        String agentId = "JobOpportunityExtractor_001";
        String modelName = "gpt-4o-mini"; // Use cost-effective model for extraction

        String inputPath = System.getProperty("user.dir") + "/tools_data/job_opportunities_emails.json";
        String outputPath = System.getProperty("user.dir") + "/tools_data/job_opportunities.json";

        JobOpportunityExtractor extractor = new JobOpportunityExtractor(agentId, modelName);

        try {
            List<JobOpportunity> opportunities = extractor.extractJobOpportunities(inputPath, outputPath);

            System.out.println();
            System.out.println("╔════════════════════════════════════════════════════════════════════╗");
            System.out.println("║              EXTRACTION COMPLETE                                   ║");
            System.out.println("╚════════════════════════════════════════════════════════════════════╝");
            System.out.println();
            System.out.println("📋 Sample extracted opportunities:");

            // Display first 3 opportunities as samples
            int displayCount = Math.min(3, opportunities.size());
            for (int i = 0; i < displayCount; i++) {
                JobOpportunity opp = opportunities.get(i);
                System.out.println();
                System.out.println("  [" + (i + 1) + "] " + opp.getTitle());
                System.out.println("      Company: " + opp.getCompany());
                System.out.println("      Location: " + opp.getLocation());
                System.out.println("      Skills: " + truncateStatic(opp.getSkillsRequired(), 60));
                System.out.println("      Fit Score: " + opp.getFitScore());
            }

            if (opportunities.size() > displayCount) {
                System.out.println("\n  ... and " + (opportunities.size() - displayCount) + " more opportunities to be checked, increase the LIMIT");
            }

        } catch (Exception e) {
            System.err.println();
            System.err.println("╔════════════════════════════════════════════════════════════════════╗");
            System.err.println("║                    FATAL ERROR                                     ║");
            System.err.println("╚════════════════════════════════════════════════════════════════════╝");
            System.err.println();
            System.err.println("ERROR: Failed to extract job opportunities");
            System.err.println("Error type: " + e.getClass().getName());
            System.err.println("Error message: " + e.getMessage());
            System.err.println();
            System.err.println("Stack trace:");
            e.printStackTrace();
            System.err.println();
            System.err.println("═".repeat(70));
            System.err.println("If this is a Jackson dependency error, try:");
            System.err.println("  1. Clean and rebuild: mvn clean install");
            System.err.println("  2. Check for dependency conflicts: mvn dependency:tree");
            System.err.println("═".repeat(70));
            System.exit(1);
        }
    }

    private static String truncateStatic(String str, int maxLength) {
        if (str == null)
            return "null";
        if (str.length() <= maxLength)
            return str;
        return str.substring(0, maxLength - 3) + "...";
    }

    /**
     * Inner class to track error details for reporting
     */
    private static class ErrorDetail {
        String emailSubject;
        String emailFrom;
        String errorMessage;
        String errorType;

        public ErrorDetail(String emailSubject, String emailFrom, String errorMessage, String errorType) {
            this.emailSubject = emailSubject;
            this.emailFrom = emailFrom;
            this.errorMessage = errorMessage;
            this.errorType = errorType;
        }
    }
}
