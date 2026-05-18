package Notifications;

import Core.AppPaths;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for storing and retrieving client email addresses
 * from a local text file.
 */
public class EmailStorage {

    /**
     * Base directory where application data files are stored.
     */
    private static final String BASE_DIR = AppPaths.getDataDir();

    /**
     * Full path to the email storage file.
     */
    private static final String FILE
            = BASE_DIR + File.separator + "correos.txt";

    /**
     * Ensures that the data directory exists before any file operation.
     */
    static {
        File dir = new File(BASE_DIR);
        if (!dir.exists()) {
            dir.mkdirs();
        }
    }

    /**
     * Retrieves the email associated with the specified client ID.
     *
     * @param clientId the client identifier
     * @return the stored email address, or {@code null} if not found
     */
    public static String getEmail(String clientId) {
        try (BufferedReader br = new BufferedReader(new FileReader(FILE))) {
            String line;

            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty()) {
                    continue;
                }

                String[] parts = line.split("\\|", 2);

                if (parts.length == 2 && parts[0].trim().equals(clientId)) {
                    return parts[1].trim();
                }
            }
        } catch (Exception e) {
            System.out.println("[EMAIL] Error leyendo archivo: " + 
                    e.getMessage());
        }

        return null;
    }

    /**
     * Saves a client email address if it is not already registered.
     *
     * If the client ID already exists, no changes are made.
     *
     * @param clientId the client identifier
     * @param email the email address to store
     */
    public static void saveEmail(String clientId, String email) {
        System.out.println("[EMAIL-STORAGE] Intentando guardar en: " + FILE);
        if (getEmail(clientId) != null) {
            return;
        }
        try (PrintWriter pw = new PrintWriter(new FileWriter(FILE, true))) {
            pw.println(clientId + "|" + email);
            System.out.println("[EMAIL-STORAGE] Guardado OK");
        } catch (Exception e) {
            System.out.println("[EMAIL-STORAGE] Error: " + e.getMessage());
        }
    }

    /**
     * Updates the email address associated with the specified client ID.
     *
     * If the client ID exists, its email is replaced. If it does not exist, a
     * new entry is created.
     *
     * The file is fully rewritten after applying the update.
     *
     * @param clientId the client identifier
     * @param email the new email address to store
     */
    public static void updateEmail(String clientId, String email) {
        File file = new File(FILE);
        List<String> lines = new ArrayList<>();
        boolean found = false;

        if (file.exists()) {
            try (BufferedReader br = new BufferedReader(new FileReader(file))) {
                String line;

                while ((line = br.readLine()) != null) {
                    if (line.trim().isEmpty()) {
                        continue;
                    }

                    String[] parts = line.split("\\|", 2);

                    if (parts.length == 2 && parts[0].trim().equals(clientId)) {
                        lines.add(clientId + "|" + email);
                        found = true;
                    } else {
                        lines.add(line.trim());
                    }
                }
            } catch (Exception e) {
                System.out.println("[EMAIL] Error leyendo: " + e.getMessage());
            }
        }

        if (!found) {
            lines.add(clientId + "|" + email);
        }

        try (PrintWriter pw = new PrintWriter(new FileWriter(file, false))) {
            for (String l : lines) {
                pw.println(l);
            }
        } catch (Exception e) {
            System.out.println("[EMAIL] Error escribiendo: " + e.getMessage());
        }

        System.out.println("[EMAIL] Correo actualizado: " + clientId + " → " 
                + email);
    }
}
