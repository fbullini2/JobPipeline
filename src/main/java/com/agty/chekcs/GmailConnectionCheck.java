package com.agty.chekcs;

import com.agty.utils.EmailStaticLib;

import javax.mail.*;
import java.util.Properties;

/**
 * Simple test to verify Gmail IMAP credentials
 * Run this first before using the full email processor
 */
public class GmailConnectionCheck {

    public static void main(String[] args) {
        // Specify the email account to test
        String email = "fbullini@gmail.com";  // Change this to test different accounts

        // Load password from CSV file
        System.out.println("Loading credentials from api_keys.csv...");
        String appPassword = EmailStaticLib.getGmailAppPassword(email);

        if (appPassword == null) {
            System.err.println("\n❌ Failed to load Gmail app password from CSV file.");
            System.err.println("Make sure api_keys.csv contains an entry like:");
            System.err.println("GMAIL_APP_PASSWORD_FBULLINI,your16charpassword,");
            return;
        }

        System.out.println("╔════════════════════════════════════════════════════════════════════╗");
        System.out.println("║              Gmail IMAP Connection Test                            ║");
        System.out.println("╚════════════════════════════════════════════════════════════════════╝");
        System.out.println();
        System.out.println("Testing connection for: " + email);
        System.out.println("Password length: " + appPassword.length() + " characters");
        System.out.println("Password (masked): " + maskPassword(appPassword));
        System.out.println();

        // Check for common issues
        if (appPassword.contains(" ")) {
            System.err.println("❌ ERROR: Password contains spaces! Remove all spaces.");
            System.err.println("   Current password: '" + appPassword + "'");
            return;
        }

        if (appPassword.length() != 16) {
            System.out.println("⚠️  WARNING: Google App Passwords are usually 16 characters.");
            System.out.println("   Your password length is: " + appPassword.length());
            System.out.println("   Make sure you copied it correctly (no spaces, no extra characters)");
            System.out.println();
        }

        System.out.println("Attempting to connect to Gmail IMAP server...");
        System.out.println();

        Properties props = new Properties();
        props.put("mail.store.protocol", "imaps");
        props.put("mail.imaps.host", "imap.gmail.com");
        props.put("mail.imaps.port", "993");
        props.put("mail.imaps.ssl.enable", "true");
        props.put("mail.debug", "false"); // Set to "true" for detailed debug info

        Session session = Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(email, appPassword);
            }
        });

        Store store = null;
        Folder inbox = null;

        try {
            // Step 1: Connect to store
            System.out.println("Step 1: Connecting to IMAP store...");
            store = session.getStore("imaps");
            store.connect("imap.gmail.com", email, appPassword);
            System.out.println("✓ Successfully connected to IMAP store!");
            System.out.println();

            // Step 2: Open inbox
            System.out.println("Step 2: Opening INBOX folder...");
            inbox = store.getFolder("INBOX");
            inbox.open(Folder.READ_ONLY);
            System.out.println("✓ Successfully opened INBOX!");
            System.out.println();

            // Step 3: Get basic info
            System.out.println("Step 3: Reading inbox information...");
            int messageCount = inbox.getMessageCount();
            int unreadCount = inbox.getUnreadMessageCount();
            System.out.println("✓ Total messages: " + messageCount);
            System.out.println("✓ Unread messages: " + unreadCount);
            System.out.println();

            System.out.println("╔════════════════════════════════════════════════════════════════════╗");
            System.out.println("║                  ✓ CONNECTION SUCCESSFUL! ✓                        ║");
            System.out.println("╚════════════════════════════════════════════════════════════════════╝");
            System.out.println();
            System.out.println("Your credentials are working correctly!");
            System.out.println("You can now use this email account with GmailEmailProcessor02.");

        } catch (AuthenticationFailedException e) {
            System.err.println();
            System.err.println("╔════════════════════════════════════════════════════════════════════╗");
            System.err.println("║                  ❌ AUTHENTICATION FAILED ❌                        ║");
            System.err.println("╚════════════════════════════════════════════════════════════════════╝");
            System.err.println();
            System.err.println("Error: " + e.getMessage());
            System.err.println();
            System.err.println("TROUBLESHOOTING CHECKLIST:");
            System.err.println();
            System.err.println("1. ✓ Check email address is correct: " + email);
            System.err.println();
            System.err.println("2. ✓ Verify 2-Step Verification is enabled:");
            System.err.println("   → https://myaccount.google.com/security");
            System.err.println("   → Look for '2-Step Verification' - must show 'On'");
            System.err.println();
            System.err.println("3. ✓ Generate a NEW App Password:");
            System.err.println("   → https://myaccount.google.com/apppasswords");
            System.err.println("   → Delete old app password if exists");
            System.err.println("   → Create new: Select 'Mail' and 'Other'");
            System.err.println("   → Copy the 16-character password (remove spaces!)");
            System.err.println();
            System.err.println("4. ✓ Make sure password has NO spaces:");
            System.err.println("   WRONG: 'abcd efgh ijkl mnop'");
            System.err.println("   RIGHT: 'abcdefghijklmnop'");
            System.err.println();
            System.err.println("5. ✓ Try logging into Gmail web to check for security alerts:");
            System.err.println("   → https://mail.google.com");
            System.err.println();
            System.err.println("6. ✓ If you just changed password, wait 5-10 minutes and retry");
            System.err.println();

        } catch (MessagingException e) {
            System.err.println();
            System.err.println("╔════════════════════════════════════════════════════════════════════╗");
            System.err.println("║                    ❌ CONNECTION FAILED ❌                          ║");
            System.err.println("╚════════════════════════════════════════════════════════════════════╝");
            System.err.println();
            System.err.println("Error: " + e.getMessage());
            System.err.println();
            System.err.println("This might be a network or server issue.");
            System.err.println("Check your internet connection and try again.");
            e.printStackTrace();

        } catch (Exception e) {
            System.err.println();
            System.err.println("Unexpected error: " + e.getMessage());
            e.printStackTrace();

        } finally {
            // Cleanup
            try {
                if (inbox != null && inbox.isOpen()) {
                    inbox.close(false);
                }
                if (store != null && store.isConnected()) {
                    store.close();
                }
            } catch (MessagingException e) {
                // Ignore cleanup errors
            }
        }
    }

    private static String maskPassword(String password) {
        if (password == null || password.length() < 4) {
            return "****";
        }
        return password.substring(0, 2) + "************" + password.substring(password.length() - 2);
    }
}
