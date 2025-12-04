package com.agty.version_fetch_folders;

import com.agty.JobOfferExtraction;
import com.agty.utils.EmailStaticLib;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.github.ollama4j.OllamaAPI;
import io.github.ollama4j.models.response.OllamaResult;
import io.github.ollama4j.utils.Options;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import javax.mail.*;
import javax.mail.internet.MimeMultipart;
import javax.mail.search.ComparisonTerm;
import javax.mail.search.ReceivedDateTerm;
import javax.mail.search.SearchTerm;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Processes job offer emails from specific Gmail folders using Ollama LLM
 * for intelligent information extraction
 */
public class JobAlertEmailFoldersProcessor {

    private final String username;
    private final String password;
    private final String ollamaHost;
    private final String ollamaModel;
    private final ObjectMapper mapper;
    private final OllamaAPI ollamaAPI;

    // ========== CONFIGURATION ==========

    // LLM Configuration: Set to false to use OpenAI API (gpt-4o-mini), true for local Ollama
    private static final boolean LOCAL_LLM = false;  // Change to true for local Ollama

    // Number of days to look back for emails
    private static final int DAYS_TO_SEARCH = 2;  // Change this to search more/fewer days

    // Folders to process
    private static final String[] JOB_FOLDERS = {
        "JobOffers_CadreEmploi",
//        "JobOffers_APEC",
//        "JobOffers_Tekkit",
//        "JobOffers_MichaelPage_FR",
//        "JobOffers_Linkedin",
//        "JobOffers_HelloWork_COM",
//        "JobOffers_WTTJ"
    };

    public JobAlertEmailFoldersProcessor(String username, String password, String ollamaHost, String ollamaModel) {
        this.username = username;
        this.password = password;
        this.ollamaHost = ollamaHost;
        this.ollamaModel = ollamaModel;
        this.mapper = new ObjectMapper();
        this.mapper.enable(SerializationFeature.INDENT_OUTPUT);
        this.ollamaAPI = new OllamaAPI(ollamaHost);

        // Enable verbose mode to see what's being sent to Ollama
        this.ollamaAPI.setVerbose(true);

        // Increase timeout to 5 minutes (300 seconds) for slow responses
        this.ollamaAPI.setRequestTimeoutSeconds(300);
    }

    /**
     * Process all emails from specified folders in the last week
     */
    public List<JobOfferExtraction> processAllFolders(String outputFilePath) throws Exception {
        System.out.println("╔════════════════════════════════════════════════════════════════════╗");
        System.out.println("║           Job Offer Folder Processor with LLM                     ║");
        System.out.println("╚════════════════════════════════════════════════════════════════════╝");
        System.out.println();
        System.out.println("📧 Email Account: " + username);

        if (LOCAL_LLM) {
            System.out.println("🤖 LLM Mode: LOCAL (Ollama)");
            System.out.println("🏠 Ollama Host: " + ollamaHost);
            System.out.println("🧠 Model: " + ollamaModel);
        } else {
            System.out.println("☁️  LLM Mode: CLOUD (OpenAI API)");
            System.out.println("🧠 Model: gpt-4o-mini");
        }

        System.out.println("📅 Time Range: Last " + DAYS_TO_SEARCH + " days");
        System.out.println();

        // Check if Ollama is running and ensure model is available (only if LOCAL_LLM)
        if (LOCAL_LLM) {
            try {
                List<io.github.ollama4j.models.response.Model> availableModels = ollamaAPI.listModels();
                System.out.println("✓ Connected to Ollama successfully");
                System.out.println("📋 Available models: " + availableModels.size());

                // Check if our model is in the list
                boolean modelFound = false;
                for (io.github.ollama4j.models.response.Model model : availableModels) {
                    if (model.getName().equals(ollamaModel)) {
                        modelFound = true;
                        System.out.println("✓ Model '" + ollamaModel + "' is available");
                        break;
                    }
                }

                // If model not found, pull it
                if (!modelFound) {
                    System.out.println("⚠️  Model '" + ollamaModel + "' not found locally");
                    System.out.println("📥 Pulling model from Ollama library...");
                    System.out.println("   (This may take several minutes depending on model size)");

                    // Pull the model
                    ollamaAPI.pullModel(ollamaModel);

                    System.out.println("✓ Model '" + ollamaModel + "' downloaded successfully");

                    // Verify it's now in the list
                    availableModels = ollamaAPI.listModels();
                    modelFound = false;
                    for (io.github.ollama4j.models.response.Model model : availableModels) {
                        if (model.getName().equals(ollamaModel)) {
                            modelFound = true;
                            System.out.println("✓ Model confirmed in available models list");
                            break;
                        }
                    }

                    if (!modelFound) {
                        System.err.println("⚠️  Warning: Model was pulled but not found in list. Will try to use it anyway.");
                    }
                }
            } catch (Exception e) {
                System.err.println("❌ Ollama initialization failed: " + e.getClass().getName());
                System.err.println("   Message: " + e.getMessage());
                throw new RuntimeException("Cannot initialize Ollama at " + ollamaHost + ". Error: " + e.getMessage(), e);
            }
        }
        System.out.println();

        List<JobOfferExtraction> allExtractions = loadExistingExtractions(outputFilePath);
        Set<String> processedKeys = buildProcessedKeysSet(allExtractions);

        System.out.println("📁 Folders to process: " + Arrays.toString(JOB_FOLDERS));
        System.out.println("✓ Loaded " + allExtractions.size() + " existing extractions");
        System.out.println();

        // Connect to Gmail
        Store store = connectToGmail();

        try {
            for (String folderName : JOB_FOLDERS) {
                System.out.println("═".repeat(70));
                System.out.println("📂 Processing folder: " + folderName);
                System.out.println("═".repeat(70));
                System.out.println();

                List<JobOfferExtraction> folderExtractions = processSingleFolder(
                    store, folderName, processedKeys, allExtractions, outputFilePath
                );

                System.out.println("✓ Processed " + folderExtractions.size() + " new emails from " + folderName);
                System.out.println();
            }
        } finally {
            store.close();
        }

        // Final save
        saveExtractions(allExtractions, outputFilePath, true);

        System.out.println();
        System.out.println("╔════════════════════════════════════════════════════════════════════╗");
        System.out.println("║                    PROCESSING COMPLETE                             ║");
        System.out.println("╚════════════════════════════════════════════════════════════════════╝");
        System.out.println();
        System.out.println("📊 Total extractions: " + allExtractions.size());
        System.out.println("💾 Saved to: " + outputFilePath);

        return allExtractions;
    }

    /**
     * Process emails from a single folder
     */
    private List<JobOfferExtraction> processSingleFolder(Store store, String folderName,
                                                         Set<String> processedKeys,
                                                         List<JobOfferExtraction> allExtractions,
                                                         String outputFilePath) throws Exception {
        List<JobOfferExtraction> folderExtractions = new ArrayList<>();

        try {
            // Try to open the folder
            Folder folder = store.getFolder(folderName);
            if (!folder.exists()) {
                System.err.println("⚠️  Folder '" + folderName + "' does not exist. Skipping...");
                return folderExtractions;
            }

            folder.open(Folder.READ_ONLY);
            System.out.println("✓ Opened folder: " + folderName + " (" + folder.getMessageCount() + " total messages)");

            // Search for emails in the configured time range
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.DAY_OF_MONTH, -DAYS_TO_SEARCH);
            SearchTerm dateTerm = new ReceivedDateTerm(ComparisonTerm.GT, cal.getTime());

            Message[] messages = folder.search(dateTerm);
            System.out.println("📬 Found " + messages.length + " messages from last " + DAYS_TO_SEARCH + " days");
            System.out.println();

            int processed = 0;
            int skipped = 0;
            int extracted = 0;
            int failed = 0;

            for (Message message : messages) {
                processed++;

                try {
                    String from = getFrom(message);
                    String subject = message.getSubject();
                    Date sentDate = message.getSentDate();

                    // Create unique key
                    String key = subject + "|" + from + "|" + (sentDate != null ? sentDate.getTime() : 0);

                    System.out.println("[" + processed + "/" + messages.length + "] " +
                        truncate(subject, 60));
                    System.out.println("  From: " + truncate(from, 55));

                    // Check if already processed
                    if (processedKeys.contains(key)) {
                        skipped++;
                        System.out.println("  ⊘ Already processed - skipping");
                        System.out.println();
                        continue;
                    }

                    // Extract content - get both raw HTML and clean text
                    String rawHtmlContent = getRawEmailContent(message);
                    String content = getEmailContent(message);

                    // Debug: Check content length
                    System.out.println("  📧 Raw HTML content length: " + rawHtmlContent.length() + " chars");
                    System.out.println("  📧 Clean text content length: " + content.length() + " chars");
                    if (content.length() < 50) {
                        System.out.println("  ⚠️  Warning: Email content is very short or empty");
                        System.out.println("  Content preview: " + content);
                    } else {
                        System.out.println("  Content preview (first 200 chars): " +
                            content.substring(0, Math.min(200, content.length())));
                    }

                    // Extract using Ollama LLM
                    System.out.println("  🤖 Extracting with Ollama LLM...");
                    JobOfferExtraction extraction = extractWithOllama(
                        from, subject, sentDate, content, folderName
                    );

                    if (extraction != null) {
                        // Enrich with source-specific details (extract portal links, etc.)
                        // Use raw HTML content to extract links
                        System.out.println("  🔍 Enriching with source-specific details...");
                        JobAlertEmailEnricher.enrichJobOffer(extraction, rawHtmlContent);
                        extracted++;
                        allExtractions.add(extraction);
                        folderExtractions.add(extraction);
                        processedKeys.add(key);

                        System.out.println("  ✓ Extracted: " + truncate(extraction.getPositionTitle(), 45));
                        System.out.println("    Company: " + extraction.getCompany());
                        System.out.println("    URL: " + truncate(extraction.getApplicationUrl(), 50));

                        // Incremental save
                        saveExtractions(allExtractions, outputFilePath, false);
                        System.out.println("    💾 Saved incrementally");
                    } else {
                        failed++;
                        System.out.println("  ✗ Extraction failed");
                    }

                } catch (Exception e) {
                    failed++;
                    System.err.println("  ✗ Error: " + e.getClass().getSimpleName());
                    System.err.println("  Message: " + (e.getMessage() != null ? e.getMessage() : "(no message)"));
                    e.printStackTrace();
                }

                System.out.println();

                // Rate limiting
                Thread.sleep(1000);
            }

            System.out.println("─".repeat(70));
            System.out.println("Folder Summary:");
            System.out.println("  Processed: " + processed);
            System.out.println("  Skipped: " + skipped);
            System.out.println("  Extracted: " + extracted);
            System.out.println("  Failed: " + failed);
            System.out.println();

            folder.close(false);

        } catch (MessagingException e) {
            System.err.println("❌ Error accessing folder '" + folderName + "': " + e.getMessage());
        }

        return folderExtractions;
    }

    /**
     * Extract job offer information using LLM (Ollama or OpenAI based on LOCAL_LLM config)
     */
    private JobOfferExtraction extractWithOllama(String from, String subject, Date sentDate,
                                                 String content, String sourceFolder) {
        try {
            String prompt = buildExtractionPrompt(from, subject, content);
            System.out.println("    📝 Prompt length: " + prompt.length() + " chars");
            System.out.println("    📝 Prompt preview (first 500 chars): " +
                prompt.substring(0, Math.min(500, prompt.length())));
            String jsonResponse;

            if (LOCAL_LLM) {
                // ========== LOCAL OLLAMA LLM ==========
                System.out.println("    🤖 Using LOCAL Ollama LLM");

                // Create options Map and wrap in Options class
                Map<String, Object> optionsMap = new HashMap<>();
                optionsMap.put("temperature", 0.1);  // Low temperature for consistent JSON output
                optionsMap.put("num_predict", 2000);  // Max tokens to generate

                Options options = new Options(optionsMap);

                // Use the generate() method - most appropriate for single generation tasks
                // raw=false: use default model template
                // think=false: don't use thinking mode
                OllamaResult result = null;
                try {
                    System.out.println("    🔄 Calling Ollama generate API...");
                    System.out.println("    Model: " + ollamaModel);
                    System.out.println("    Options: " + optionsMap);
                    System.out.println("    Prompt length: " + prompt.length() + " chars");

                    result = ollamaAPI.generate(ollamaModel, prompt, false, false, options);
                    System.out.println("    ✓ Ollama API call completed");
                } catch (Exception e) {
                    // Check if it's a 404 error (model not found)
                    String errorMsg = e.getMessage() != null ? e.getMessage() : "";
                    String errorCause = e.getCause() != null && e.getCause().getMessage() != null ? e.getCause().getMessage() : "";

                    if (errorMsg.contains("404") || errorCause.contains("404") ||
                        errorMsg.contains("not found") || errorCause.contains("not found")) {

                        System.out.println("    ⚠️  Model '" + ollamaModel + "' not found locally (404)");
                        System.out.println("    📥 Pulling model from Ollama library...");
                        System.out.println("       (This may take several minutes depending on model size)");

                        // Pull the model and wait for completion
                        ollamaAPI.pullModel(ollamaModel);

                        System.out.println("    ✓ Model downloaded successfully");
                        System.out.println("    🤖 Retrying extraction with generate()...");

                        // Retry the generation
                        result = ollamaAPI.generate(ollamaModel, prompt, false, false, options);
                        System.out.println("    ✓ Retry successful");
                    } else {
                        // Not a 404 error, log and rethrow
                        System.err.println("    ❌ Ollama API error: " + e.getClass().getSimpleName());
                        System.err.println("    Message: " + errorMsg);
                        if (!errorCause.isEmpty()) {
                            System.err.println("    Cause: " + errorCause);
                        }
                        throw e;
                    }
                }

                jsonResponse = result.getResponse();

            } else {
                // ========== OPENAI API ==========
                System.out.println("    ☁️  Using OpenAI API (gpt-4o-mini)");
                System.out.println("    🔄 Calling OpenAI API...");
                System.out.println("    Prompt length: " + prompt.length() + " chars");

                // Call OpenAI using the existing utility
                jsonResponse = com.agty.utils.OpenAiRESTApiCaller.caller(
                    "JobOfferExtraction",  // Aid for logging
                    "gpt-4o-mini",         // Model name
                    null,                  // System prompt (we include everything in user prompt)
                    prompt,                // User prompt with extraction instructions
                    "JobOfferFolderProcessor",  // Tool name for logging
                    0.1,                   // Low temperature for consistent JSON
                    2000                   // Max tokens
                );

                System.out.println("    ✓ OpenAI API call completed");
            }

            // Debug: log response
            System.out.println("    📝 LLM response length: " + jsonResponse.length() + " chars");
            System.out.println("    📝 LLM response preview (first 300 chars): " +
                jsonResponse.substring(0, Math.min(300, jsonResponse.length())));

            // Clean response
            jsonResponse = cleanJsonResponse(jsonResponse);
            System.out.println("    🧹 Cleaned response length: " + jsonResponse.length() + " chars");
            System.out.println("    🧹 Cleaned response preview (first 300 chars): " +
                jsonResponse.substring(0, Math.min(300, jsonResponse.length())));

            // Parse JSON response
            JobOfferExtraction extraction = mapper.readValue(jsonResponse, JobOfferExtraction.class);

            // Debug: Check if extraction has any non-null values
            boolean hasData = extraction.getCompany() != null ||
                             extraction.getPositionTitle() != null ||
                             extraction.getLocation() != null;
            if (!hasData) {
                System.out.println("    ⚠️  Warning: LLM returned all null values!");
                System.out.println("    Full cleaned response: " + jsonResponse);
            }

            // Set metadata
            extraction.setFrom(from);
            extraction.setSubject(subject);
            extraction.setSentDate(sentDate != null ? sentDate.getTime() : null);

            // Format sent date as human readable timestamp
            if (sentDate != null) {
                SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
                extraction.setSentDateHumanReadable(dateFormat.format(sentDate));
            }

            extraction.setSourceFolder(sourceFolder);

            // Safely set email content preview
            if (content != null && content.length() > 0) {
                extraction.setEmailContentPreview(content.substring(0, Math.min(500, content.length())));
            } else {
                extraction.setEmailContentPreview("");
            }

            return extraction;

        } catch (Exception e) {
            System.err.println("    ❌ LLM extraction failed: " + e.getClass().getSimpleName());
            System.err.println("    Error: " + (e.getMessage() != null ? e.getMessage() : "(no message)"));

            if (e.getCause() != null) {
                System.err.println("    Caused by: " + e.getCause().getClass().getSimpleName());
                if (e.getCause().getMessage() != null) {
                    System.err.println("    Cause: " + e.getCause().getMessage());
                }
            }

            if (e.getMessage() != null && e.getMessage().contains("JSON")) {
                System.err.println("    💡 Hint: The LLM might not be returning valid JSON. Try a larger model.");
            }

            return null;
        }
    }

    /**
     * Build extraction prompt for LLM
     */
    private String buildExtractionPrompt(String from, String subject, String content) {
        return "You are an expert job offer analyzer. Extract structured information from the following email.\n\n" +
            "EMAIL FROM: " + from + "\n" +
            "SUBJECT: " + subject + "\n\n" +
            "EMAIL CONTENT:\n" + content.substring(0, Math.min(8000, content.length())) + "\n\n" +
            "Extract ALL relevant information and return ONLY a valid JSON object (no markdown, no explanation) with these fields:\n" +
            "{\n" +
            "  \"company\": \"company name\",\n" +
            "  \"position_title\": \"job title\",\n" +
            "  \"location\": \"location or 'Remote'\",\n" +
            "  \"contract_type\": \"CDI/CDD/Freelance/etc\",\n" +
            "  \"salary_range\": \"salary if mentioned\",\n" +
            "  \"description\": \"brief description\",\n" +
            "  \"required_skills\": [\"skill1\", \"skill2\"],\n" +
            "  \"experience_level\": \"Junior/Mid/Senior\",\n" +
            "  \"application_url\": \"URL to apply (extract from links)\",\n" +
            "  \"application_email\": \"email to apply if mentioned\",\n" +
            "  \"application_instructions\": \"how to apply\",\n" +
            "  \"application_deadline\": \"deadline if mentioned\",\n" +
            "  \"is_multiple_positions\": true/false,\n" +
            "  \"number_of_positions\": number if multiple,\n" +
            "  \"positions_list\": [\"position1\", \"position2\"] if multiple,\n" +
            "  \"contact_person\": \"name if mentioned\",\n" +
            "  \"contact_phone\": \"phone if mentioned\",\n" +
            "  \"reference_number\": \"reference if mentioned\",\n" +
            "  \"extraction_confidence\": 0.0 to 1.0\n" +
            "}\n\n" +
            "IMPORTANT:\n" +
            "- Return ONLY valid JSON, no other text\n" +
            "- Extract ALL URLs that could be application links\n" +
            "- If email contains multiple positions, set is_multiple_positions=true and list them\n" +
            "- Use null for missing fields\n" +
            "- Be thorough in extracting application information";
    }

    /**
     * Clean JSON response from LLM
     */
    private String cleanJsonResponse(String response) {
        response = response.trim();
        if (response.startsWith("```json")) {
            response = response.substring(7);
        }
        if (response.startsWith("```")) {
            response = response.substring(3);
        }
        if (response.endsWith("```")) {
            response = response.substring(0, response.length() - 3);
        }
        return response.trim();
    }

    /**
     * Connect to Gmail IMAP
     */
    private Store connectToGmail() throws MessagingException {
        Properties props = new Properties();
        props.put("mail.store.protocol", "imaps");
        props.put("mail.imaps.host", "imap.gmail.com");
        props.put("mail.imaps.port", "993");
        props.put("mail.imaps.ssl.enable", "true");

        Session session = Session.getInstance(props);
        Store store = session.getStore("imaps");
        store.connect("imap.gmail.com", username, password);

        System.out.println("✓ Connected to Gmail IMAP");
        return store;
    }

    /**
     * Get raw email content WITHOUT converting HTML to text
     * Used for link extraction by enrichers
     */
    private String getRawEmailContent(Message message) throws Exception {
        Object content = message.getContent();
        if (content instanceof String) {
            return (String) content;
        } else if (content instanceof MimeMultipart) {
            return getRawTextFromMimeMultipart((MimeMultipart) content);
        }
        return "";
    }

    /**
     * Get email content and convert HTML to text if needed
     */
    private String getEmailContent(Message message) throws Exception {
        Object content = message.getContent();
        if (content instanceof String) {
            String contentStr = (String) content;
            // Check if it's HTML and convert to text
            if (contentStr.trim().startsWith("<")) {
                Document doc = Jsoup.parse(contentStr);
                return cleanTextContent(doc.text());
            }
            return cleanTextContent(contentStr);
        } else if (content instanceof MimeMultipart) {
            return cleanTextContent(getTextFromMimeMultipart((MimeMultipart) content));
        }
        return "";
    }

    /**
     * Clean text content by removing invisible/special characters
     * Removes: Zero Width Space, Zero Width Non-Joiner, Zero Width Joiner, etc.
     */
    private String cleanTextContent(String text) {
        if (text == null) {
            return "";
        }

        return text
            // Remove Zero Width Space (U+200B)
            .replace("\u200B", "")
            // Remove Zero Width Non-Joiner (U+200C)
            .replace("\u200C", "")
            // Remove Zero Width Joiner (U+200D)
            .replace("\u200D", "")
            // Remove Left-to-Right Mark (U+200E)
            .replace("\u200E", "")
            // Remove Right-to-Left Mark (U+200F)
            .replace("\u200F", "")
            // Remove Soft Hyphen (U+00AD)
            .replace("\u00AD", "")
            // Remove multiple spaces
            .replaceAll("\\s+", " ")
            // Trim
            .trim();
    }

    /**
     * Get raw content from MimeMultipart WITHOUT converting HTML to text
     */
    private String getRawTextFromMimeMultipart(MimeMultipart mimeMultipart) throws Exception {
        StringBuilder plainText = new StringBuilder();
        StringBuilder htmlText = new StringBuilder();
        int count = mimeMultipart.getCount();

        // Extract both plain and HTML parts WITHOUT conversion
        for (int i = 0; i < count; i++) {
            BodyPart bodyPart = mimeMultipart.getBodyPart(i);
            if (bodyPart.isMimeType("text/plain")) {
                plainText.append(bodyPart.getContent());
            } else if (bodyPart.isMimeType("text/html")) {
                String html = (String) bodyPart.getContent();
                htmlText.append(html);
            } else if (bodyPart.getContent() instanceof MimeMultipart) {
                String nested = getRawTextFromMimeMultipart((MimeMultipart) bodyPart.getContent());
                if (nested.trim().startsWith("<")) {
                    htmlText.append(nested);
                } else {
                    plainText.append(nested);
                }
            }
        }

        // Prefer HTML if available (for link extraction), otherwise plain text
        if (htmlText.length() > 0) {
            return htmlText.toString();
        } else if (plainText.length() > 0) {
            return plainText.toString();
        }

        return "";
    }

    private String getTextFromMimeMultipart(MimeMultipart mimeMultipart) throws Exception {
        StringBuilder plainText = new StringBuilder();
        StringBuilder htmlText = new StringBuilder();
        int count = mimeMultipart.getCount();

        // Try to extract both plain and HTML parts
        for (int i = 0; i < count; i++) {
            BodyPart bodyPart = mimeMultipart.getBodyPart(i);
            if (bodyPart.isMimeType("text/plain")) {
                plainText.append(bodyPart.getContent());
            } else if (bodyPart.isMimeType("text/html")) {
                String html = (String) bodyPart.getContent();
                htmlText.append(html);
            } else if (bodyPart.getContent() instanceof MimeMultipart) {
                String nested = getTextFromMimeMultipart((MimeMultipart) bodyPart.getContent());
                // Check if nested content looks like HTML
                if (nested.trim().startsWith("<")) {
                    htmlText.append(nested);
                } else {
                    plainText.append(nested);
                }
            }
        }

        // Prefer plain text if available, otherwise convert HTML to text
        if (plainText.length() > 0) {
            return plainText.toString();
        } else if (htmlText.length() > 0) {
            // Convert HTML to plain text using Jsoup
            Document doc = Jsoup.parse(htmlText.toString());
            return doc.text();
        }

        return "";
    }

    /**
     * Get from address
     */
    private String getFrom(Message message) throws MessagingException {
        if (message.getFrom() != null && message.getFrom().length > 0) {
            return message.getFrom()[0].toString();
        }
        return "unknown";
    }

    /**
     * Load existing extractions
     */
    private List<JobOfferExtraction> loadExistingExtractions(String filePath) {
        File file = new File(filePath);
        if (!file.exists()) {
            return new ArrayList<>();
        }

        try {
            // Wrap in ArrayList to make it mutable (Arrays.asList returns immutable list)
            return new ArrayList<>(Arrays.asList(mapper.readValue(file, JobOfferExtraction[].class)));
        } catch (IOException e) {
            System.err.println("⚠️  Could not load existing extractions: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Build set of processed keys
     */
    private Set<String> buildProcessedKeysSet(List<JobOfferExtraction> extractions) {
        Set<String> keys = new HashSet<>();
        for (JobOfferExtraction ext : extractions) {
            String key = ext.getSubject() + "|" + ext.getFrom() + "|" + ext.getSentDate();
            keys.add(key);
        }
        return keys;
    }

    /**
     * Save extractions to file
     */
    private void saveExtractions(List<JobOfferExtraction> extractions, String filePath, boolean verbose) {
        try {
            File file = new File(filePath);
            File parentDir = file.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }

            mapper.writeValue(file, extractions);

            if (verbose) {
                System.out.println("✓ Saved " + extractions.size() + " extractions to: " + filePath);
                System.out.println("  File size: " + file.length() + " bytes");
            }
        } catch (IOException e) {
            System.err.println("❌ Error saving extractions: " + e.getMessage());
        }
    }

    /**
     * Truncate string for display
     */
    private String truncate(String str, int maxLength) {
        if (str == null) return "null";
        if (str.length() <= maxLength) return str;
        return str.substring(0, maxLength - 3) + "...";
    }

    /**
     * Main method
     */
    public static void main(String[] args) {
        String gmailUsername = "fbullini@gmail.com";
        String gmailPassword = EmailStaticLib.getGmailAppPassword(gmailUsername);

        if (gmailPassword == null) {
            System.err.println("❌ Failed to load Gmail app password. Exiting.");
            return;
        }

        String ollamaHost = "http://localhost:11434";  // Default Ollama host
        String ollamaModel = "llama3.2:latest";  // Or mistral, codellama, etc.

        String outputPath = System.getProperty("user.dir") + "/tools_data/job_offers_extracted_from_folders.json";

        JobAlertEmailFoldersProcessor processor = new JobAlertEmailFoldersProcessor(
            gmailUsername, gmailPassword, ollamaHost, ollamaModel
        );

        try {
            List<JobOfferExtraction> extractions = processor.processAllFolders(outputPath);
            System.out.println("\n🎉 Successfully extracted " + extractions.size() + " job offers!");
        } catch (Exception e) {
            System.err.println("\n❌ ERROR: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
