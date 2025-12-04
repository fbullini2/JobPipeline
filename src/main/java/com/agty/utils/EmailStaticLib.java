package com.agty.utils;

import javax.mail.Folder;
import javax.mail.MessagingException;
import javax.mail.Store;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EmailStaticLib {

    private static final String API_KEYS_FILE = "api_keys.csv";
    private static Map<String, String> cachedApiKeys = null;

    public static Folder findDraftsFolder(Store store) throws MessagingException {
        // Common names for Gmail drafts folder
        String[] possibleDraftsFolders = {
                "[Gmail]/Drafts",
                "[Google Mail]/Drafts",
                "Drafts",
                "[Gmail]/Draft",
                "Draft",
                "[Gmail]/Borradores",  // Spanish
                "[Gmail]/Brouillons",  // French
                "[Gmail]/Bozze",       // Italian
                "[Gmail]/Entwürfe"     // German
        };

        // Try direct folder names first
        for (String folderName : possibleDraftsFolders) {
            try {
                Folder folder = store.getFolder(folderName);
                if (folder != null && folder.exists() && (folder.getType() & Folder.HOLDS_MESSAGES) != 0) {
                    return folder;
                }
            } catch (MessagingException e) {
                continue;
            }
        }

        // If not found, search all folders recursively
        Folder defaultFolder = store.getDefaultFolder();
        Folder draftsFolder = findDraftsFolderRecursively(defaultFolder);
        if (draftsFolder != null) {
            return draftsFolder;
        }

        // If still not found, print available folders and return null
        System.out.println("Available folders:");
        printFolders(defaultFolder, "");

        return null;
    }

    static void printFolders(Folder folder, String indent) throws MessagingException {
        System.out.println(indent + "- " + folder.getFullName());
        if ((folder.getType() & Folder.HOLDS_FOLDERS) != 0) {
            for (Folder subfolder : folder.list()) {
                printFolders(subfolder, indent + "  ");
            }
        }
    }

    private static Folder findDraftsFolderRecursively(Folder parent) throws MessagingException {
        if (parent == null) return null;

        // Check if current folder name contains "draft" in any language
        String folderName = parent.getFullName().toLowerCase();
        if ((parent.getType() & Folder.HOLDS_MESSAGES) != 0 &&
                (folderName.contains("draft") || folderName.contains("borrador") ||
                        folderName.contains("brouillon") || folderName.contains("bozze") ||
                        folderName.contains("entwurf"))) {
            return parent;
        }

        // Check subfolders if this is a parent folder
        if ((parent.getType() & Folder.HOLDS_FOLDERS) != 0) {
            Folder[] subfolders = parent.list();
            for (Folder subfolder : subfolders) {
                Folder found = findDraftsFolderRecursively(subfolder);
                if (found != null) {
                    return found;
                }
            }
        }

        return null;
    }

    public static String generateResponse(String agentId, List<String> emailThread, String modelName, String cloudProvider, String modelNameExplicitFallBack, String cloudProviderExplicitFallBack) {
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

        //Filtra meglio e selezione le email da risponsere; quelle che ga senso rispondere.
        // No pubblicita, no info
        //CAtegorizzale, appointment settings ,etc, usando i odssier dei contatti e le annotazioni dei vari tasks da far etc
        //see contacts tools etc

        // Call OpenAI API using the provided caller
        return OpenAiRESTApiCaller.caller(
                agentId,                         // Agent ID
                modelName,                      // Model name
                systemPrompt,                    // System prompt
                userPrompt.toString(),           // User prompt
                "EmailResponseGenerator",        // LLM provider tool name TODO Check better here <<<
                0.0,                            // Temperature
                500                             // Max output tokens
        );
    }

    /**
     * Load API keys from CSV file located at project root
     * CSV format: key,value
     * Example: GMAIL_APP_PASSWORD_FBULLO,abcd efgh ijkl mnop,
     */
    public static Map<String, String> loadApiKeys() {
        if (cachedApiKeys != null) {
            return cachedApiKeys;
        }

        Map<String, String> apiKeys = new HashMap<>();

        // Find project root directory (where api_keys.csv is located)
        Path projectRoot = findProjectRoot();
        if (projectRoot == null) {
            System.err.println("ERROR: Could not find project root directory!");
            return apiKeys;
        }

        File apiKeysFile = projectRoot.resolve(API_KEYS_FILE).toFile();

        if (!apiKeysFile.exists()) {
            System.err.println("ERROR: API keys file not found at: " + apiKeysFile.getAbsolutePath());
            return apiKeys;
        }

        System.out.println("📂 Loading API keys from: " + apiKeysFile.getAbsolutePath());

        try (BufferedReader reader = new BufferedReader(new FileReader(apiKeysFile))) {
            String line;
            boolean firstLine = true;

            while ((line = reader.readLine()) != null) {
                // Skip header line
                if (firstLine) {
                    firstLine = false;
                    continue;
                }

                // Skip empty lines
                if (line.trim().isEmpty()) {
                    continue;
                }

                // Parse CSV line: key,value,optional_date
                String[] parts = line.split(",", 3);
                if (parts.length >= 2) {
                    String key = parts[0].trim();
                    String value = parts[1].trim();

                    if (!key.isEmpty() && !value.isEmpty()) {
                        apiKeys.put(key, value);
                    }
                }
            }

            System.out.println("✓ Loaded " + apiKeys.size() + " API keys");
            cachedApiKeys = apiKeys;

        } catch (IOException e) {
            System.err.println("ERROR reading API keys file: " + e.getMessage());
            e.printStackTrace();
        }

        return apiKeys;
    }

    /**
     * Get a specific API key by name
     */
    public static String getApiKey(String keyName) {
        Map<String, String> keys = loadApiKeys();
        return keys.get(keyName);
    }

    /**
     * Get Gmail app password for a specific user
     * @param username - either full email (e.g., "fbullo@gmail.com") or just the part before @ (e.g., "fbullo")
     * @return Gmail app password with spaces removed, or null if not found
     */
    public static String getGmailAppPassword(String username) {
        // Extract username part before @ if full email provided
        String userPart = username;
        if (username.contains("@")) {
            userPart = username.substring(0, username.indexOf("@")).toUpperCase();
        } else {
            userPart = username.toUpperCase();
        }

        // Construct the key name
        String keyName = "GMAIL_APP_PASSWORD_" + userPart;
        String password = getApiKey(keyName);

        if (password == null) {
            System.err.println("ERROR: Gmail app password not found for key: " + keyName);
            System.err.println("Available Gmail password keys:");
            loadApiKeys().keySet().stream()
                .filter(k -> k.startsWith("GMAIL_APP_PASSWORD_"))
                .forEach(k -> System.err.println("  - " + k));
            return null;
        }

        // Remove all spaces from password (common in Google app passwords display)
        password = password.replace(" ", "");

        return password;
    }

    /**
     * Find the project root directory by looking for api_keys.csv
     * Searches from current directory upwards
     */
    private static Path findProjectRoot() {
        // Start from current working directory
        Path current = Paths.get("").toAbsolutePath();

        // Try current directory and up to 5 levels up
        for (int i = 0; i < 6; i++) {
            File apiKeysFile = current.resolve(API_KEYS_FILE).toFile();
            if (apiKeysFile.exists()) {
                return current;
            }

            // Move up one directory
            Path parent = current.getParent();
            if (parent == null) {
                break;
            }
            current = parent;
        }

        // Try some common project paths as fallback
        String[] commonPaths = {
            System.getProperty("user.dir"),
            "/home/fra/workspaces/mas-dialog-backbone"
        };

        for (String path : commonPaths) {
            Path testPath = Paths.get(path);
            if (testPath.resolve(API_KEYS_FILE).toFile().exists()) {
                return testPath;
            }
        }

        return null;
    }

    /**
     * Clear cached API keys (useful for testing or reloading)
     */
    public static void clearCache() {
        cachedApiKeys = null;
    }
}
