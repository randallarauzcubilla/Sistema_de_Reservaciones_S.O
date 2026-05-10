package Notifications;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.PrintWriter;

/**
 * Utility class for storing and retrieving client email addresses
 * from a local text file.
 */
public class EmailStorage {

    private static final String FILE
            = System.getProperty("user.dir") + File.separator + "correos.txt";

    /**
     * Retrieves the email associated with the specified client ID.
     *
     * @param clientId the client identifier
     * @return the stored email address, or null if not found
     */
    public static String getEmail(String clientId) {
        try (BufferedReader br = new BufferedReader(new FileReader(FILE))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split("\\|", 2);
                if (parts.length == 2 && parts[0].trim().equals(clientId)) {
                    return parts[1].trim();
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /**
     * Saves a client email address if it is not already registered.
     *
     * @param clientId the client identifier
     * @param email the email address to store
     */
    public static void saveEmail(String clientId, String email) {
        // Check if already exists
        if (getEmail(clientId) != null) {
            return;
        }

        try (PrintWriter pw = new PrintWriter(
                new FileWriter(FILE, true))) {
            pw.println(clientId + "|" + email);
        } catch (Exception ignored) {
        }
    }
}